package com.libhunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libhunt.dto.LevelDetailVO;
import com.libhunt.service.LevelCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Redis 不可用时，缓存层必须退化成「直接查库」，不能把异常抛给调用方。
 */
@DisplayName("缓存降级")
class LevelCacheFallbackTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedissonClient redisson = mock(RedissonClient.class);
    private final LevelCache cache = new LevelCache(redis, redisson, new ObjectMapper());

    @Test
    @DisplayName("Redis 读写与加锁全部失败时，仍然返回 loader 的结果")
    void fallsBackToLoaderWhenRedisIsDown() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("redis down"));
        when(redisson.getLock(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        LevelDetailVO loaded = sampleDetail();
        LevelDetailVO result = cache.detail("libhunt:level:detail:zh-Hans:1", () -> loaded);

        assertThat(result).isSameAs(loaded);
    }

    @Test
    @DisplayName("缓存不可用时，loader 返回 null 也不会额外抛异常")
    void nullLoaderResultIsReturned() {
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("redis down"));
        when(redisson.getLock(anyString())).thenThrow(new RedisConnectionFailureException("redis down"));

        assertThat(cache.detail("libhunt:level:detail:zh-Hans:999", () -> null)).isNull();
    }

    private LevelDetailVO sampleDetail() {
        return new LevelDetailVO(
                1, "标题", "剧情", null, "题目",
                List.of(), null, null, null, null,
                null, null, List.of(), false);
    }
}
