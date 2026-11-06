package com.libhunt;

import com.libhunt.dto.LeaderboardEntryVO;
import com.libhunt.dto.LeaderboardRow;
import com.libhunt.entity.GameRun;
import com.libhunt.mapper.GameRunMapper;
import com.libhunt.mapper.PlayerMapper;
import com.libhunt.service.LeaderboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis 不可用时的兜底行为。
 * <p>
 * 集成测试里 Redis 一直是健康的；这里用 Mockito 模拟连接失败，
 * 验证「排行榜读不到 Redis 就回落 MySQL」「写 Redis 失败不能拖垮通关」。
 */
@DisplayName("排行榜 Redis 兜底")
class LeaderboardFallbackTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final GameRunMapper gameRunMapper = mock(GameRunMapper.class);
    private final PlayerMapper playerMapper = mock(PlayerMapper.class);
    private final LeaderboardService service =
            new LeaderboardService(redis, gameRunMapper, playerMapper);

    @Test
    @DisplayName("读排行榜时 Redis 抛异常 → 回落 MySQL")
    void fallsBackToMysqlWhenRedisIsDown() {
        when(redis.opsForZSet()).thenThrow(new RedisConnectionFailureException("redis down"));

        LeaderboardRow row = new LeaderboardRow();
        row.setRunId(42L);
        row.setPlayerId(7L);
        row.setNickname("小明");
        row.setTotalScore(1200);
        row.setDurationSeconds(300);
        when(gameRunMapper.selectTopBestPerPlayer(10)).thenReturn(List.of(row));

        List<LeaderboardEntryVO> board = service.top(10);

        assertThat(board).hasSize(1);
        assertThat(board.get(0).rank()).isEqualTo(1);
        assertThat(board.get(0).nickname()).isEqualTo("小明");
        assertThat(board.get(0).totalScore()).isEqualTo(1200);
    }

    @Test
    @DisplayName("通关写排行榜时 Redis 抛异常 → 吞掉，不影响通关")
    void redisWriteFailureDoesNotBreakCompletion() {
        when(redis.opsForZSet()).thenThrow(new RedisConnectionFailureException("redis down"));

        GameRun run = new GameRun();
        run.setId(1L);
        run.setTotalScore(1200);
        run.setDurationSeconds(600);

        assertThatCode(() -> service.recordCompletedRun(run)).doesNotThrowAnyException();
    }
}
