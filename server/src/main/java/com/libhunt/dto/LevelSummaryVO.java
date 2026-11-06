package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "关卡列表项（已按 locale 取好文案，不含答案）")
public record LevelSummaryVO(
        @Schema(description = "关卡编号，1..12", example = "1")
        Integer level,
        @Schema(description = "关卡标题", example = "第一关 · 借阅起点")
        String title,
        @Schema(description = "本关道具，用于地图/道具墙展示")
        List<ItemVO> items
) {
}
