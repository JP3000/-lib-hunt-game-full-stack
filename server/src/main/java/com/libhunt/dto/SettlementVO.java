package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 本关结算结果。只有「答题」和「扫码」都完成的那一次请求才会带上它。
 */
@Schema(description = "关卡结算")
public record SettlementVO(
        int levelScore,
        int totalScore,
        @Schema(description = "下一关关卡号；全部通关时为 null")
        Integer nextLevelNo,
        boolean runCompleted,
        @Schema(description = "本局用时（秒）；全部通关时才有值")
        Integer durationSeconds,
        @Schema(description = "本关获得的道具 key")
        List<String> collectedItems
) {
}
