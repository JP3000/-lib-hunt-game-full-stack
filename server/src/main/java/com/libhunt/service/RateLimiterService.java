package com.libhunt.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redisson 的令牌桶限流。
 * <p>
 * 用于登录接口：同一 IP / 同一账号每分钟最多 10 次尝试，
 * 把撞库的成本从「无限次」变成「每分钟 10 次」。
 * <p>
 * <b>失败放行（fail-open）</b>：Redis 不可用时选择放行而不是拒绝——
 * 限流是防滥用的辅助手段，不能因为缓存故障把正常用户挡在门外。
 * 这个取舍要在面试里讲清楚。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimiterService {

    private final RedissonClient redissonClient;

    /**
     * @param key     限流维度，例如 {@code libhunt:ratelimit:login:ip:1.2.3.4}
     * @param permits 窗口内允许的次数
     * @param window  窗口长度
     * @return true = 放行，false = 超限
     */
    public boolean tryAcquire(String key, int permits, Duration window) {
        try {
            RRateLimiter limiter = redissonClient.getRateLimiter(key);
            // 只在 key 不存在时生效；后续请求复用已有的桶
            limiter.trySetRate(RateType.OVERALL, permits, window.toMillis(), RateIntervalUnit.MILLISECONDS);
            // 给桶本身设 TTL，避免 IP / 账号维度无限增长
            limiter.expire(window.multipliedBy(2));
            return limiter.tryAcquire();
        } catch (Exception e) {
            log.warn("限流器不可用，本次放行 key={}：{}", key, e.getMessage());
            return true;
        }
    }
}
