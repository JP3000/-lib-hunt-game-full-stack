package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "管理端总览")
public record AdminStatsVO(
        long playerCount,
        long totalCompletions,
        @Schema(description = "已完成对局的平均分")
        double avgScore,
        @Schema(description = "已完成对局的平均用时（秒）")
        double avgDuration
) {
}
