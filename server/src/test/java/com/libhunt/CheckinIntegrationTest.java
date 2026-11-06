package com.libhunt;

import com.libhunt.dto.CheckinStatusVO;
import com.libhunt.entity.Player;
import com.libhunt.service.CheckinService;
import com.libhunt.support.IntegrationTestBase;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisCallback;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BitMap 打卡：验证幂等、BITCOUNT 累计与连续天数。
 */
@DisplayName("到馆打卡")
class CheckinIntegrationTest extends IntegrationTestBase {

    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    @Autowired
    private CheckinService checkinService;

    @Test
    @DisplayName("新玩家未打卡：今天 false、累计 0、连续 0")
    void freshPlayerHasNoCheckin() {
        Player player = createPlayer("checkin-fresh");

        CheckinStatusVO status = checkinService.status(player.getId());

        assertThat(status.todayChecked()).isFalse();
        assertThat(status.totalDaysThisYear()).isZero();
        assertThat(status.streakDays()).isZero();
    }

    @Test
    @DisplayName("同一天重复打卡是幂等的，累计天数不会重复增加")
    void checkInIsIdempotent() {
        Player player = createPlayer("checkin-idempotent");

        CheckinStatusVO first = checkinService.checkIn(player.getId());
        CheckinStatusVO second = checkinService.checkIn(player.getId());

        assertThat(first.todayChecked()).isTrue();
        assertThat(first.totalDaysThisYear()).isEqualTo(1);
        assertThat(second.todayChecked()).isTrue();
        assertThat(second.totalDaysThisYear()).isEqualTo(1);
        assertThat(second.streakDays()).isEqualTo(1);
    }

    @Test
    @DisplayName("BitMap 存的是每天一位：手动补上昨天、前天，连续天数变 3")
    void streakCountsConsecutiveDays() {
        LocalDate today = LocalDate.now(ZONE);
        Assumptions.assumeTrue(today.getDayOfYear() > 2,
                "1 月 1 日附近无法构造「前两年内」的连续天数");

        Player player = createPlayer("checkin-streak");
        CheckinStatusVO after = checkinService.checkIn(player.getId());
        assertThat(after.streakDays()).isEqualTo(1);

        String key = "libhunt:checkin:" + player.getId() + ":" + today.getYear();
        redis.opsForValue().setBit(key, today.minusDays(1).getDayOfYear() - 1L, true);
        redis.opsForValue().setBit(key, today.minusDays(2).getDayOfYear() - 1L, true);

        CheckinStatusVO status = checkinService.status(player.getId());
        assertThat(status.streakDays()).isEqualTo(3);
        assertThat(status.totalDaysThisYear()).isEqualTo(3);
    }

    @Test
    @DisplayName("一个 bit 表示一天：365 天的数据只占 46 字节")
    void bitmapUsesOneBitPerDay() {
        Player player = createPlayer("checkin-size");
        LocalDate today = LocalDate.now(ZONE);
        String key = "libhunt:checkin:" + player.getId() + ":" + today.getYear();

        for (int day = 1; day <= today.getDayOfYear(); day++) {
            redis.opsForValue().setBit(key, day - 1L, true);
        }

        // 当天是第 N 天，底层字符串长度约为 ceil(N / 8) 字节
        Long bytes = redis.execute((RedisCallback<Long>) connection ->
                connection.stringCommands().strLen(key.getBytes(StandardCharsets.UTF_8)));
        long expectedBytes = (today.getDayOfYear() + 7L) / 8;
        assertThat(bytes).isLessThanOrEqualTo(expectedBytes);
        assertThat(checkinService.status(player.getId()).totalDaysThisYear())
                .isEqualTo(today.getDayOfYear());
    }
}
