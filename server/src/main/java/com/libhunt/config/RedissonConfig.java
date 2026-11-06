package com.libhunt.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Redisson 客户端：只用来做分布式锁与限流，不接管 Spring Data Redis 的
 * {@code StringRedisTemplate}（排行榜仍走它）。
 * <p>
 * 连接参数复用 {@code spring.data.redis.*}，避免同一份配置写两处。
 * 超时与重试刻意调小：Redis 只是加速层，挂了应该快速失败并回源 MySQL，
 * 而不是把请求卡住。
 */
@Configuration
public class RedissonConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String host;

    @Value("${spring.data.redis.port:6379}")
    private int port;

    @Value("${spring.data.redis.database:0}")
    private int database;

    @Value("${spring.data.redis.password:}")
    private String password;

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();
        // 懒连接：应用启动时 Redis 不可用也能起来（锁/限流各自 fail-open），
        // 否则一个加速层会把整个服务拖死在启动阶段。
        config.setLazyInitialization(true);
        config.useSingleServer()
                .setAddress("redis://" + host + ":" + port)
                .setDatabase(database)
                .setTimeout(3000)
                .setConnectTimeout(3000)
                .setRetryAttempts(1)
                .setConnectionMinimumIdleSize(1)
                .setConnectionPoolSize(8);

        if (StringUtils.hasText(password)) {
            config.useSingleServer().setPassword(password);
        }

        return Redisson.create(config);
    }
}
