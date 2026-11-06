package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "排行榜条目：每位玩家只保留最好的一局")
public record LeaderboardEntryVO(
        int rank,
        String nickname,
        int totalScore,
        @Schema(description = "用时（秒），越短越好")
        Integer durationSeconds
) {
}
