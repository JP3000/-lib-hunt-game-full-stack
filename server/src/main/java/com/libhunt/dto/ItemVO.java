package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "关卡奖励道具")
public record ItemVO(
        @Schema(description = "道具业务键", example = "item-01-note")
        String id,
        @Schema(description = "道具名")
        String name,
        @Schema(description = "道具描述")
        String description,
        @Schema(description = "道具图片地址")
        String imageUrl
) {
}
