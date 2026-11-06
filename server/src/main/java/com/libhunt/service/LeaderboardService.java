package com.libhunt.service;

import com.libhunt.dto.LeaderboardEntryVO;
import com.libhunt.dto.LeaderboardRow;
import com.libhunt.entity.GameRun;
import com.libhunt.entity.Player;
import com.libhunt.mapper.GameRunMapper;
import com.libhunt.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 排行榜：Redis ZSET 负责排序，MySQL 负责真相。
 * <p>
 * 为什么用 ZSET 而不是每次 {@code ORDER BY}？
 * <ul>
 *   <li>排行榜是读多写少，每次通关才写一次，读却是高频</li>
 *   <li>ZSET 按 score 有序，{@code ZREVRANGE} 取前 N 是 O(logN + N)</li>
 *   <li>Redis 挂了也不影响正确性：自动回落到 MySQL 的窗口函数查询</li>
 * </ul>
 * <p>
 * 复合排序怎么塞进一个 double：
 * {@code score = totalScore * 100000 + (100000 - durationSeconds)}。
 * 分数越高越靠前；同分时用时越短，「尾数」越大，排名越靠前。
 * 本项目满分 1200，乘以 10 万后仍在 double 的精确整数范围内（2^53）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LeaderboardService {

    private static final String KEY = "libhunt:leaderboard:global";
    private static final int DURATION_SCALE = 100_000;
    private static final int MAX_DURATION = DURATION_SCALE - 1;
    private static final int ZSET_MAX_SIZE = 1_000;

    private final StringRedisTemplate redis;
    private final GameRunMapper gameRunMapper;
    private final PlayerMapper playerMapper;

    /**
     * 通关后写排行榜。只在事务提交后写 Redis——
     * 否则事务回滚了，Redis 里却留下一条不存在的成绩。
     */
    public void recordCompletedRunAfterCommit(GameRun run) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    recordCompletedRun(run);
                }
            });
        } else {
            recordCompletedRun(run);
        }
    }

    public void recordCompletedRun(GameRun run) {
        try {
            redis.opsForZSet().add(KEY, String.valueOf(run.getId()), compositeScore(run));
            trim();
        } catch (Exception e) {
            // Redis 不可用不能影响通关本身；下次读排行榜会回落到 MySQL
            log.warn("排行榜写入 Redis 失败：{}", e.getMessage());
        }
    }

    public List<LeaderboardEntryVO> top(int limit) {
        int capped = Math.min(Math.max(limit, 1), 100);
        List<LeaderboardRow> rows;
        try {
            rows = topFromRedis(capped);
            if (rows.isEmpty()) {
                // Redis 刚重启或被清空时，用 MySQL 兜底并顺手预热
                rows = mysqlTop(capped);
                rows.forEach(this::recordRowQuietly);
            }
        } catch (Exception e) {
            log.warn("排行榜读取 Redis 失败，回落到 MySQL：{}", e.getMessage());
            rows = mysqlTop(capped);
        }
        return toEntries(rows);
    }

    public int rebuild() {
        List<LeaderboardRow> rows = gameRunMapper.selectTopBestPerPlayer(100_000);
        redis.delete(KEY);
        if (rows.isEmpty()) {
            return 0;
        }
        Set<ZSetOperations.TypedTuple<String>> tuples = rows.stream()
                .map(r -> ZSetOperations.TypedTuple.of(
                        String.valueOf(r.getRunId()),
                        (double) compositeScore(r.getTotalScore(), r.getDurationSeconds())))
                .collect(Collectors.toSet());
        redis.opsForZSet().add(KEY, tuples);
        trim();
        return rows.size();
    }

    // ------------------------------------------------------------------ 内部

    private List<LeaderboardRow> topFromRedis(int limit) {
        int fetch = Math.min(Math.max(limit * 3, 50), 300);
        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(KEY, 0, fetch - 1);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }

        List<Long> runIds = tuples.stream()
                .map(ZSetOperations.TypedTuple::getValue)
                .filter(v -> v != null)
                .map(Long::valueOf)
                .toList();
        Map<Long, GameRun> runById = gameRunMapper.selectBatchIds(runIds).stream()
                .collect(Collectors.toMap(GameRun::getId, Function.identity()));

        // ZSET 顺序即排名顺序；同一玩家只保留最先出现（最好）的一局
        LinkedHashMap<Long, GameRun> bestPerPlayer = new LinkedHashMap<>();
        for (Long runId : runIds) {
            GameRun run = runById.get(runId);
            if (run != null) {
                bestPerPlayer.putIfAbsent(run.getPlayerId(), run);
            }
            if (bestPerPlayer.size() >= limit) {
                break;
            }
        }
        if (bestPerPlayer.isEmpty()) {
            return List.of();
        }

        Map<Long, String> nicknameById = playerMapper.selectBatchIds(bestPerPlayer.keySet()).stream()
                .collect(Collectors.toMap(Player::getId, LeaderboardService::displayName));

        List<LeaderboardRow> rows = new ArrayList<>(bestPerPlayer.size());
        bestPerPlayer.forEach((playerId, run) -> rows.add(row(run, nicknameById.get(playerId))));
        return rows;
    }

    private List<LeaderboardRow> mysqlTop(int limit) {
        return gameRunMapper.selectTopBestPerPlayer(limit);
    }

    private void recordRowQuietly(LeaderboardRow row) {
        try {
            redis.opsForZSet().add(KEY, String.valueOf(row.getRunId()),
                    compositeScore(row.getTotalScore(), row.getDurationSeconds()));
        } catch (Exception e) {
            log.debug("排行榜预热失败：{}", e.getMessage());
        }
    }

    private void trim() {
        Long size = redis.opsForZSet().zCard(KEY);
        if (size != null && size > ZSET_MAX_SIZE) {
            // 只删除分数最低的那部分，避免排行榜无限增长
            redis.opsForZSet().removeRange(KEY, 0, size - ZSET_MAX_SIZE - 1);
        }
    }

    private List<LeaderboardEntryVO> toEntries(List<LeaderboardRow> rows) {
        List<LeaderboardEntryVO> entries = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            LeaderboardRow row = rows.get(i);
            entries.add(new LeaderboardEntryVO(
                    i + 1,
                    row.getNickname() == null ? "匿名" : row.getNickname(),
                    row.getTotalScore() == null ? 0 : row.getTotalScore(),
                    row.getDurationSeconds()));
        }
        return entries;
    }

    private LeaderboardRow row(GameRun run, String nickname) {
        LeaderboardRow row = new LeaderboardRow();
        row.setRunId(run.getId());
        row.setPlayerId(run.getPlayerId());
        row.setNickname(nickname);
        row.setTotalScore(run.getTotalScore());
        row.setDurationSeconds(run.getDurationSeconds());
        return row;
    }

    private long compositeScore(GameRun run) {
        return compositeScore(run.getTotalScore(), run.getDurationSeconds());
    }

    private long compositeScore(Integer totalScore, Integer durationSeconds) {
        int score = totalScore == null ? 0 : totalScore;
        int duration = durationSeconds == null ? MAX_DURATION : Math.min(durationSeconds, MAX_DURATION);
        return (long) score * DURATION_SCALE + (DURATION_SCALE - duration);
    }

    private static String displayName(Player player) {
        return player.getNickname() == null || player.getNickname().isBlank()
                ? player.getStudentNo()
                : player.getNickname();
    }
}
