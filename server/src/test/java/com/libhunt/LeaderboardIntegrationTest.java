package com.libhunt;

import com.libhunt.dto.LeaderboardEntryVO;
import com.libhunt.entity.GameRun;
import com.libhunt.entity.Player;
import com.libhunt.mapper.GameRunMapper;
import com.libhunt.service.LeaderboardService;
import com.libhunt.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 排行榜的集成测试：Redis ZSET 与 MySQL 兜底两条路径。
 */
@DisplayName("排行榜")
class LeaderboardIntegrationTest extends IntegrationTestBase {

    private static final int DURATION_SCALE = 100_000;

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private GameRunMapper gameRunMapper;

    @Test
    @DisplayName("分数高者靠前；同分时用时短者靠前；每位玩家只保留最好的一局")
    void ordersByScoreThenDurationAndKeepsBestPerPlayer() {
        Player alice = createPlayer("lb-alice");
        Player bob = createPlayer("lb-bob");

        completedRun(alice.getId(), 240, 12);
        GameRun aliceFast = completedRun(alice.getId(), 240, 2);
        completedRun(bob.getId(), 220, 1);

        // Redis 是空的（@BeforeEach 清过），这一次走 MySQL 兜底并顺便预热
        List<LeaderboardEntryVO> board = leaderboardService.top(10);

        assertThat(board).hasSize(2);
        assertThat(board.get(0).rank()).isEqualTo(1);
        assertThat(board.get(0).nickname()).isEqualTo("测试-lb-alice");
        assertThat(board.get(0).durationSeconds()).isEqualTo(2);
        assertThat(board.get(1).nickname()).isEqualTo("测试-lb-bob");

        // 预热确实写入了复合分数：总分 * 100000 + (100000 - 用时)
        Double score = redis.opsForZSet().score(LEADERBOARD_KEY, String.valueOf(aliceFast.getId()));
        assertThat(score).isEqualTo(
                (double) 240 * DURATION_SCALE + (DURATION_SCALE - 2));
    }

    @Test
    @DisplayName("Redis 命中时直接返回 ZSET 顺序")
    void readsFromRedisAfterPreheat() {
        Player alice = createPlayer("lb-cache");
        GameRun best = completedRun(alice.getId(), 200, 5);

        leaderboardService.top(10); // 第一次：MySQL → 预热
        Long size = redis.opsForZSet().zCard(LEADERBOARD_KEY);
        assertThat(size).isEqualTo(1);

        List<LeaderboardEntryVO> second = leaderboardService.top(10); // 第二次：Redis

        assertThat(second).hasSize(1);
        assertThat(second.get(0).nickname()).isEqualTo("测试-lb-cache");
        assertThat(second.get(0).totalScore()).isEqualTo(200);
        assertThat(second.get(0).durationSeconds()).isEqualTo(5);

        Set<ZSetOperations.TypedTuple<String>> tuples =
                redis.opsForZSet().reverseRangeWithScores(LEADERBOARD_KEY, 0, -1);
        assertThat(tuples).isNotNull();
        assertThat(tuples).extracting(t -> t.getValue()).containsExactly(String.valueOf(best.getId()));
    }

    @Test
    @DisplayName("rebuild 会清空并根据 MySQL 全量重建 ZSET")
    void rebuildRebuildsZsetFromMysql() {
        Player alice = createPlayer("lb-rebuild");
        Player bob = createPlayer("lb-rebuild-2");
        completedRun(alice.getId(), 240, 3);
        completedRun(bob.getId(), 180, 1);

        // 先写一条脏数据，验证 rebuild 会把它清掉
        redis.opsForZSet().add(LEADERBOARD_KEY, "999999", 1);
        assertThat(redis.opsForZSet().zCard(LEADERBOARD_KEY)).isEqualTo(1);

        int size = leaderboardService.rebuild();

        assertThat(size).isEqualTo(2);
        assertThat(redis.opsForZSet().zCard(LEADERBOARD_KEY)).isEqualTo(2);
        assertThat(redis.opsForZSet().score(LEADERBOARD_KEY, "999999")).isNull();

        List<LeaderboardEntryVO> board = leaderboardService.top(10);
        assertThat(board).extracting(LeaderboardEntryVO::nickname)
                .containsExactly("测试-lb-rebuild", "测试-lb-rebuild-2");
    }

    @Test
    @DisplayName("limit 会被限制在 1..100，非法值不会抛异常")
    void limitIsCapped() {
        Player player = createPlayer("lb-limit");
        completedRun(player.getId(), 100, 10);

        assertThat(leaderboardService.top(0)).hasSize(1);
        assertThat(leaderboardService.top(10_000)).hasSize(1);
    }

    private GameRun completedRun(Long playerId, int totalScore, int durationSeconds) {
        GameRun run = new GameRun();
        run.setPlayerId(playerId);
        run.setStatus(GameRun.COMPLETED);
        run.setStartedAt(LocalDateTime.now().minusSeconds(durationSeconds));
        run.setFinishedAt(LocalDateTime.now());
        run.setTotalScore(totalScore);
        run.setDurationSeconds(durationSeconds);
        run.setLastLevelNo(12);
        gameRunMapper.insert(run);
        return run;
    }
}
