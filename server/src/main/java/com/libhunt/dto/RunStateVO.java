package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "一局游戏的完整状态，前端刷新后靠它恢复进度")
public record RunStateVO(
        Long runId,
        @Schema(description = "IN_PROGRESS / COMPLETED / ABANDONED")
        String status,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Integer totalScore,
        Integer durationSeconds,
        @Schema(description = "当前应挑战的关卡号")
        Integer lastLevelNo,
        List<LevelProgressVO> levels,
        @Schema(description = "本局已获得的道具 key")
        List<String> items
) {
}
