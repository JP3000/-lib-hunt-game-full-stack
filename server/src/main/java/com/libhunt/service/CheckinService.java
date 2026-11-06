package com.libhunt.service;

import com.libhunt.common.exception.BusinessException;
import com.libhunt.dto.CheckinStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * 到馆打卡：用 Redis BitMap 记录「哪一天来过图书馆」。
 * <p>
 * 存储模型：每个玩家每年一个 key，bit 偏移量 = 一年中的第几天（0 起）。
 * 一年最多 365 个 bit ≈ 46 字节；换成年份字符串则要 365 个 key。
 * <p>
 * 统计方式：{@code SETBIT} 写、{@code GETBIT} 查当天、{@code BITCOUNT} 算全年累计，
 * 连续天数从今天往前逐位回退（上限一年）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final int MAX_STREAK_SCAN = 366;
    private static final Duration KEY_TTL = Duration.ofDays(400);

    private final StringRedisTemplate redis;

    public CheckinStatusVO checkIn(Long playerId) {
        LocalDate today = LocalDate.now(ZONE);
        String key = key(playerId, today.getYear());
        try {
            redis.opsForValue().setBit(key, bitIndex(today), true);
            // 跨年后旧 key 自动过期；每次打卡顺延，保证当年数据不会中途丢失
            redis.expire(key, KEY_TTL);
            return status(key, today);
        } catch (Exception e) {
            log.warn("打卡写入失败 playerId={}：{}", playerId, e.getMessage());
            throw new BusinessException("打卡服务暂时不可用，请稍后再试");
        }
    }

    public CheckinStatusVO status(Long playerId) {
        LocalDate today = LocalDate.now(ZONE);
        try {
            return status(key(playerId, today.getYear()), today);
        } catch (Exception e) {
            // 读失败按未打卡处理，不能因为 Redis 抖动让地图页打不开
            log.warn("读取打卡状态失败 playerId={}：{}", playerId, e.getMessage());
            return new CheckinStatusVO(false, 0, 0);
        }
    }

    private CheckinStatusVO status(String key, LocalDate today) {
        Boolean todayChecked = redis.opsForValue().getBit(key, bitIndex(today));
        Long total = redis.execute((RedisCallback<Long>) connection ->
                connection.stringCommands().bitCount(key.getBytes(StandardCharsets.UTF_8)));

        int streak = 0;
        LocalDate cursor = today;
        while (streak < MAX_STREAK_SCAN && cursor.getYear() == today.getYear()) {
            if (!Boolean.TRUE.equals(redis.opsForValue().getBit(key, bitIndex(cursor)))) {
                break;
            }
            streak++;
            cursor = cursor.minusDays(1);
        }

        return new CheckinStatusVO(Boolean.TRUE.equals(todayChecked), total == null ? 0 : total, streak);
    }

    private String key(Long playerId, int year) {
        return "libhunt:checkin:" + playerId + ":" + year;
    }

    private long bitIndex(LocalDate date) {
        return date.getDayOfYear() - 1L;
    }
}
