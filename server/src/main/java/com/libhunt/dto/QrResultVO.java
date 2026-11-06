package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "扫码校验结果")
public record QrResultVO(
        boolean correct,
        boolean questionPassed,
        boolean qrPassed,
        @Schema(description = "本关已结算时返回结算信息，否则为 null")
        SettlementVO settlement
) {
}
