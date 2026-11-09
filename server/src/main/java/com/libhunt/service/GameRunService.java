package com.libhunt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.libhunt.common.GameRules;
import com.libhunt.common.exception.BusinessException;
import com.libhunt.dto.AnswerResultVO;
import com.libhunt.dto.LevelProgressVO;
import com.libhunt.dto.QrResultVO;
import com.libhunt.dto.RunStateVO;
import com.libhunt.dto.SettlementVO;
import com.libhunt.entity.GameRun;
import com.libhunt.entity.Item;
import com.libhunt.entity.Level;
import com.libhunt.entity.LevelAttempt;
import com.libhunt.entity.LevelItem;
import com.libhunt.entity.PlayerItem;
import com.libhunt.mapper.GameRunMapper;
import com.libhunt.mapper.ItemMapper;
import com.libhunt.mapper.LevelAttemptMapper;
import com.libhunt.mapper.LevelItemMapper;
import com.libhunt.mapper.LevelMapper;
import com.libhunt.mapper.PlayerItemMapper;
import com.libhunt.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 游戏核心闭环：开局 → 答题 / 现场扫码（两道验证相互独立）→ 结算 → 通关。
 * <p>
 * <b>服务端权威：</b>答案、二维码内容、关卡顺序、得分、用时全部由服务端决定，
 * 客户端只能提交「我选了什么」「我扫到了什么」。
 * <p>
 * <b>并发控制：</b>答题/扫码时对 {@code game_run} 行加 {@code FOR UPDATE} 锁，
 * 同一局的请求串行执行；{@code uk_attempt_run_level} 唯一索引作为最后一道保险。
 * 结算由 {@code level_attempt.settled} 标记，保证只发生一次。
 */
@Service
@RequiredArgsConstructor
public class GameRunService {

    private final PlayerMapper playerMapper;
    private final GameRunMapper gameRunMapper;
    private final LevelAttemptMapper levelAttemptMapper;
    private final PlayerItemMapper playerItemMapper;
    private final LevelMapper levelMapper;
    private final LevelItemMapper levelItemMapper;
    private final ItemMapper itemMapper;
    private final LeaderboardService leaderboardService;
    private final QrTokenService qrTokenService;

    // ------------------------------------------------------------------ 开局

    @Transactional
    public RunStateVO start(Long playerId, String idempotencyKey) {
        // 锁玩家行：同一玩家并发点两次「开始」，只会有一局被创建
        if (playerMapper.lockById(playerId) == null) {
            throw new BusinessException("账号不存在");
        }

        String key = trimToNull(idempotencyKey);
        if (key != null) {
            GameRun existing = gameRunMapper.selectOne(
                    new LambdaQueryWrapper<GameRun>()
                            .eq(GameRun::getIdempotencyKey, key)
                            .last("LIMIT 1"));
            if (existing != null) {
                if (!existing.getPlayerId().equals(playerId)) {
                    throw new BusinessException("幂等键已被占用");
                }
                return toState(existing);
            }
        }

        GameRun active = findActiveRun(playerId);
        if (active != null) {
            // 有未完成的对局就续玩，而不是开新局——避免刷分
            return toState(active);
        }

        GameRun run = new GameRun();
        run.setPlayerId(playerId);
        run.setStatus(GameRun.IN_PROGRESS);
        run.setStartedAt(LocalDateTime.now());
        run.setTotalScore(0);
        run.setLastLevelNo(1);
        run.setIdempotencyKey(key);
        gameRunMapper.insert(run);
        return toState(run);
    }

    /**
     * 当前对局：优先进行中的一局；没有则返回最近一局（用于通关后刷新页面恢复结果）。
     * 放弃的对局不算。
     */
    public RunStateVO current(Long playerId) {
        GameRun run = findActiveRun(playerId);
        if (run == null) {
            run = gameRunMapper.selectOne(
                    new LambdaQueryWrapper<GameRun>()
                            .eq(GameRun::getPlayerId, playerId)
                            .ne(GameRun::getStatus, GameRun.ABANDONED)
                            .orderByDesc(GameRun::getId)
                            .last("LIMIT 1"));
        }
        return run == null ? null : toState(run);
    }

    /**
     * 放弃当前对局（前端「重新开始」）。已结算的关卡与分数保留在历史里，
     * 但不再参与 current 查询；下次开局会创建新的一局。
     */
    @Transactional
    public RunStateVO abandon(Long playerId, Long runId) {
        GameRun run = lockRun(playerId, runId);
        if (GameRun.IN_PROGRESS.equals(run.getStatus())) {
            run.setStatus(GameRun.ABANDONED);
            gameRunMapper.updateById(run);
        }
        return toState(run);
    }

    // ------------------------------------------------------------------ 判题

    @Transactional
    public AnswerResultVO submitAnswer(Long playerId, Long runId, int levelNo,
                                       List<String> optionIds) {
        GameRun run = lockRun(playerId, runId);
        Level level = requireLevel(levelNo);
        LevelAttempt attempt = findAttempt(runId, levelNo);

        // 幂等：已通过本题就直接返回原结果，不重复计分
        if (attempt != null && isTrue(attempt.getQuestionPassed())) {
            return answerResult(attempt, null);
        }
        if (!GameRun.IN_PROGRESS.equals(run.getStatus())) {
            throw new BusinessException("本局已结束，请重新开始");
        }
        requireCurrentLevel(run, levelNo);

        Set<String> submitted = normalizeOptions(optionIds);
        Set<String> correctKeys = new HashSet<>(level.getCorrectOptionKeys());

        // 少选得部分分、错选不得分：选到任何错误选项本题都是 0 分；
        // 只选对一部分正确选项则按比例得分；全部选对得满分。
        int score = GameRules.hasWrongOption(submitted, correctKeys)
                ? 0
                : GameRules.scoreFor(submitted.size(), correctKeys.size());
        boolean questionPassed = score > 0;

        int attempts = (attempt == null ? 0 : attempt.getAttempts()) + 1;

        if (attempt == null) {
            attempt = newAttempt(runId, levelNo);
            attempt.setAttempts(attempts);
            attempt.setScore(score);
            attempt.setQuestionPassed(questionPassed ? 1 : 0);
            levelAttemptMapper.insert(attempt);
        } else {
            attempt.setAttempts(attempts);
            if (questionPassed) {
                attempt.setScore(score);
                attempt.setQuestionPassed(1);
            }
            levelAttemptMapper.updateById(attempt);
        }

        SettlementVO settlement = questionPassed ? maybeSettle(run, attempt, levelNo, level) : null;
        return answerResult(attempt, settlement);
    }

    // -------------------------------------------------------------- 扫码验证

    @Transactional
    public QrResultVO submitQr(Long playerId, Long runId, int levelNo, String qrToken) {
        GameRun run = lockRun(playerId, runId);
        Level level = requireLevel(levelNo);
        LevelAttempt attempt = findAttempt(runId, levelNo);

        // 幂等：已经扫过的码直接返回成功，不重复结算
        if (attempt != null && isTrue(attempt.getQrPassed())) {
            return new QrResultVO(true, isTrue(attempt.getQuestionPassed()), true, null);
        }
        if (!GameRun.IN_PROGRESS.equals(run.getStatus())) {
            throw new BusinessException("本局已结束，请重新开始");
        }
        requireCurrentLevel(run, levelNo);

        if (!qrTokenService.verify(levelNo, qrToken, level.getQrAnswer())) {
            // 扫码失败不扣分：现场找错位置已经够惨了
            return new QrResultVO(false,
                    attempt != null && isTrue(attempt.getQuestionPassed()), false, null);
        }

        // 先扫码后答题是正常顺序，此时还没有 attempt 记录
        if (attempt == null) {
            attempt = newAttempt(runId, levelNo);
            attempt.setAttempts(0);
            levelAttemptMapper.insert(attempt);
        }
        attempt.setQrPassed(1);
        levelAttemptMapper.updateById(attempt);

        SettlementVO settlement = maybeSettle(run, attempt, levelNo, level);
        return new QrResultVO(true, isTrue(attempt.getQuestionPassed()), true, settlement);
    }

    // ------------------------------------------------------------------ 结算

    /**
     * 两道验证都通过后结算一次：计分、发道具、推进关卡、必要时写排行榜。
     * 由 {@code settled} 标记保证幂等——最后一个完成的动作不管重试多少次，
     * 都只有第一次会加分。
     */
    private SettlementVO maybeSettle(GameRun run, LevelAttempt attempt, int levelNo, Level level) {
        if (!isTrue(attempt.getQuestionPassed())
                || !isTrue(attempt.getQrPassed())
                || isTrue(attempt.getSettled())) {
            return null;
        }

        attempt.setSettled(1);
        levelAttemptMapper.updateById(attempt);

        run.setTotalScore(run.getTotalScore() + attempt.getScore());
        List<String> collected = grantItems(run.getPlayerId(), run.getId(), level.getId());

        Integer maxLevelNo = levelMapper.selectMaxLevelNo();
        boolean runCompleted = maxLevelNo != null && levelNo >= maxLevelNo;

        if (runCompleted) {
            LocalDateTime now = LocalDateTime.now();
            run.setStatus(GameRun.COMPLETED);
            run.setFinishedAt(now);
            run.setDurationSeconds((int) Math.max(0,
                    Duration.between(run.getStartedAt(), now).getSeconds()));
        } else {
            run.setLastLevelNo(levelNo + 1);
        }
        gameRunMapper.updateById(run);

        if (runCompleted) {
            // 事务提交后再写 Redis：回滚了就不能留下成绩
            leaderboardService.recordCompletedRunAfterCommit(run);
        }

        return new SettlementVO(
                attempt.getScore(),
                run.getTotalScore(),
                runCompleted ? null : run.getLastLevelNo(),
                runCompleted,
                run.getDurationSeconds(),
                collected);
    }

    // ------------------------------------------------------------------ 内部

    private LevelAttempt newAttempt(Long runId, int levelNo) {
        LevelAttempt attempt = new LevelAttempt();
        attempt.setRunId(runId);
        attempt.setLevelNo(levelNo);
        attempt.setAttempts(0);
        attempt.setScore(0);
        attempt.setQuestionPassed(0);
        attempt.setQrPassed(0);
        attempt.setSettled(0);
        return attempt;
    }

    private AnswerResultVO answerResult(LevelAttempt attempt, SettlementVO settlement) {
        boolean questionPassed = isTrue(attempt.getQuestionPassed());
        boolean qrPassed = isTrue(attempt.getQrPassed());
        String nextAction = !questionPassed ? "RETRY" : (qrPassed ? "NEXT" : "SCAN_QR");
        // correct 表示「本次提交是否通过本题」；少选拿到部分分也算通过，但不一定满分
        return new AnswerResultVO(questionPassed, attempt.getAttempts(), attempt.getScore(),
                questionPassed, qrPassed, nextAction, settlement);
    }

    private GameRun lockRun(Long playerId, Long runId) {
        GameRun run = gameRunMapper.selectByIdForUpdate(runId);
        if (run == null) {
            throw new BusinessException("对局不存在");
        }
        if (!run.getPlayerId().equals(playerId)) {
            throw new BusinessException("无权操作该对局");
        }
        return run;
    }

    private void requireCurrentLevel(GameRun run, int levelNo) {
        if (levelNo != run.getLastLevelNo()) {
            throw new BusinessException("请按顺序通关，当前是第 " + run.getLastLevelNo() + " 关");
        }
    }

    private Level requireLevel(int levelNo) {
        Level level = levelMapper.selectOne(
                new LambdaQueryWrapper<Level>()
                        .eq(Level::getLevelNo, levelNo)
                        .eq(Level::getStatus, 1)
                        .last("LIMIT 1"));
        if (level == null) {
            throw new BusinessException("关卡不存在：" + levelNo);
        }
        return level;
    }

    private LevelAttempt findAttempt(Long runId, int levelNo) {
        return levelAttemptMapper.selectOne(
                new LambdaQueryWrapper<LevelAttempt>()
                        .eq(LevelAttempt::getRunId, runId)
                        .eq(LevelAttempt::getLevelNo, levelNo)
                        .last("LIMIT 1"));
    }

    private GameRun findActiveRun(Long playerId) {
        return gameRunMapper.selectOne(
                new LambdaQueryWrapper<GameRun>()
                        .eq(GameRun::getPlayerId, playerId)
                        .eq(GameRun::getStatus, GameRun.IN_PROGRESS)
                        .orderByDesc(GameRun::getId)
                        .last("LIMIT 1"));
    }

    private List<String> grantItems(Long playerId, Long runId, Long levelId) {
        List<LevelItem> links = levelItemMapper.selectList(
                new LambdaQueryWrapper<LevelItem>()
                        .eq(LevelItem::getLevelId, levelId)
                        .orderByAsc(LevelItem::getSortOrder));
        if (links.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = links.stream().map(LevelItem::getItemId).toList();
        itemIds.forEach(itemId -> playerItemMapper.insertIgnore(playerId, itemId, runId));

        Map<Long, String> keyById = itemMapper.selectBatchIds(itemIds).stream()
                .collect(Collectors.toMap(Item::getId, Item::getItemKey));
        return links.stream()
                .map(link -> keyById.get(link.getItemId()))
                .filter(Objects::nonNull)
                .toList();
    }

    private RunStateVO toState(GameRun run) {
        List<LevelAttempt> attempts = levelAttemptMapper.selectList(
                new LambdaQueryWrapper<LevelAttempt>()
                        .eq(LevelAttempt::getRunId, run.getId())
                        .orderByAsc(LevelAttempt::getLevelNo));

        List<LevelProgressVO> progress = attempts.stream()
                .map(a -> new LevelProgressVO(a.getLevelNo(), a.getScore(), a.getAttempts(),
                        isTrue(a.getQuestionPassed()), isTrue(a.getQrPassed())))
                .toList();

        return new RunStateVO(run.getId(), run.getStatus(), run.getStartedAt(), run.getFinishedAt(),
                run.getTotalScore(), run.getDurationSeconds(), run.getLastLevelNo(),
                progress, collectedItemKeys(run.getId()));
    }

    private List<String> collectedItemKeys(Long runId) {
        List<PlayerItem> owned = playerItemMapper.selectList(
                new LambdaQueryWrapper<PlayerItem>().eq(PlayerItem::getRunId, runId));
        if (owned.isEmpty()) {
            return List.of();
        }
        List<Long> itemIds = owned.stream().map(PlayerItem::getItemId).toList();
        return new ArrayList<>(itemMapper.selectBatchIds(itemIds).stream()
                .map(Item::getItemKey)
                .toList());
    }

    private Set<String> normalizeOptions(List<String> optionIds) {
        if (optionIds == null) {
            return Set.of();
        }
        return optionIds.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean isTrue(Integer flag) {
        return Integer.valueOf(1).equals(flag);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
