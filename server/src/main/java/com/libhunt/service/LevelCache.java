package com.libhunt.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.libhunt.dto.LevelDetailVO;
import com.libhunt.dto.LevelSummaryVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 关卡读缓存（Cache-Aside）。
 * <p>
 * 关卡的读远多于写（内容只在发版时变），是典型的缓存候选。这里同时处理三种
 * 经典问题：
 * <ul>
 *   <li><b>穿透</b>：不存在的关卡也缓存空值标记，短期（1 分钟）挡住重复查询</li>
 *   <li><b>击穿</b>：缓存过期时用 Redisson 分布式锁让一个请求回源重建，其余请求等它写回</li>
 *   <li><b>雪崩</b>：TTL 加随机抖动，避免同一批 key 在整点一起过期</li>
 * </ul>
 * Redis 只是加速层：读写失败都只记日志、回源 MySQL，不影响接口可用性。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LevelCache {

    private static final String NULL_MARKER = "__NULL__";

    /** 详情：内容稳定，缓存久一点 */
    private static final Duration DETAIL_TTL = Duration.ofMinutes(30);
    private static final int DETAIL_JITTER_SECONDS = 300;

    /** 列表：地图页每次都要，但更短 */
    private static final Duration LIST_TTL = Duration.ofMinutes(5);
    private static final int LIST_JITTER_SECONDS = 60;

    /** 空值缓存时间短，既挡住穿透，又不会让新建关卡长时间不可见 */
    private static final Duration NULL_TTL = Duration.ofMinutes(1);

    /** 等锁时间要短：没抢到锁就自己回源，不能把请求拖住 */
    private static final long LOCK_WAIT_MILLIS = 200;

    private final StringRedisTemplate redis;
    private final RedissonClient redissonClient;
    private final ObjectMapper objectMapper;

    public LevelDetailVO detail(String key, Supplier<LevelDetailVO> loader) {
        return getOrLoad(key, raw -> read(raw, LevelDetailVO.class),
                DETAIL_TTL, DETAIL_JITTER_SECONDS, loader);
    }

    public List<LevelSummaryVO> list(String key, Supplier<List<LevelSummaryVO>> loader) {
        return getOrLoad(key, this::readList, LIST_TTL, LIST_JITTER_SECONDS, loader);
    }

    // ------------------------------------------------------------------ 核心

    private <T> T getOrLoad(String key, Function<String, T> deserialize,
                            Duration ttl, int jitterSeconds, Supplier<T> loader) {
        Lookup<T> cached = lookup(key, deserialize);
        if (cached.hit()) {
            return cached.value();
        }

        RLock lock = null;
        boolean locked = false;
        try {
            lock = redissonClient.getLock(key + ":lock");
            locked = lock.tryLock(LOCK_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            // Redis 不可用：不加锁直接回源，正确性不受影响
            log.warn("获取缓存重建锁失败，直接回源 key={}：{}", key, e.getMessage());
        }

        try {
            if (locked) {
                // 等锁期间可能已经有别的请求重建完成，再读一次
                cached = lookup(key, deserialize);
                if (cached.hit()) {
                    return cached.value();
                }
            }
            T value = loader.get();
            write(key, value, ttl, jitterSeconds);
            return value;
        } finally {
            if (locked) {
                try {
                    lock.unlock();
                } catch (Exception e) {
                    log.debug("释放缓存锁失败 key={}：{}", key, e.getMessage());
                }
            }
        }
    }

    private <T> Lookup<T> lookup(String key, Function<String, T> deserialize) {
        try {
            String raw = redis.opsForValue().get(key);
            if (raw == null) {
                return new Lookup<>(false, null);
            }
            if (NULL_MARKER.equals(raw)) {
                return new Lookup<>(true, null);
            }
            return new Lookup<>(true, deserialize.apply(raw));
        } catch (Exception e) {
            // 反序列化失败（例如 DTO 改过字段）也按未命中处理，靠回源覆盖旧值
            log.warn("读取缓存失败 key={}：{}", key, e.getMessage());
            return new Lookup<>(false, null);
        }
    }

    private void write(String key, Object value, Duration ttl, int jitterSeconds) {
        try {
            if (value == null) {
                redis.opsForValue().set(key, NULL_MARKER, NULL_TTL);
            } else {
                redis.opsForValue().set(key, objectMapper.writeValueAsString(value), jitter(ttl, jitterSeconds));
            }
        } catch (Exception e) {
            log.warn("写入缓存失败 key={}：{}", key, e.getMessage());
        }
    }

    private Duration jitter(Duration base, int maxSeconds) {
        return base.plusSeconds(ThreadLocalRandom.current().nextLong(maxSeconds + 1L));
    }

    private <T> T read(String raw, Class<T> type) {
        try {
            return objectMapper.readValue(raw, type);
        } catch (Exception e) {
            throw new IllegalStateException("缓存反序列化失败", e);
        }
    }

    private List<LevelSummaryVO> readList(String raw) {
        try {
            return objectMapper.readValue(raw, new TypeReference<List<LevelSummaryVO>>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("缓存反序列化失败", e);
        }
    }

    private record Lookup<T>(boolean hit, T value) {
    }
}
