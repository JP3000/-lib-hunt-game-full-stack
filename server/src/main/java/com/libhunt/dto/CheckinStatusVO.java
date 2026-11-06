package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 到馆打卡状态。用 Redis BitMap 按「每玩家每年一个 key、每天一个 bit」存储：
 * 一年 365 天只占 46 字节，比存 365 个字符串省得多。
 */
@Schema(description = "到馆打卡状态")
public record CheckinStatusVO(
        @Schema(description = "今天是否已打卡")
        boolean todayChecked,

        @Schema(description = "今年累计打卡天数（BITCOUNT）")
        long totalDaysThisYear,

        @Schema(description = "截至今天的连续打卡天数")
        int streakDays
) {
}
