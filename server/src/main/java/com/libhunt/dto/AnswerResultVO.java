package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "判题结果。答案本身永远不会出现在响应里。")
public record AnswerResultVO(
        @Schema(description = "本次提交是否通过本题；少选拿到部分分也算通过")
        boolean correct,
        @Schema(description = "本题累计尝试次数")
        int attempts,
        @Schema(description = "本题得分：全对 20，少选按正确选项比例给分，错选 0")
        int score,
        @Schema(description = "题目是否已通过（至少选对一个正确选项，且没有选错）")
        boolean questionPassed,
        @Schema(description = "扫码是否已通过")
        boolean qrPassed,
        @Schema(description = "下一步：RETRY / SCAN_QR / NEXT", example = "SCAN_QR")
        String nextAction,
        @Schema(description = "两道验证都完成时才有值——本关已结算")
        SettlementVO settlement
) {
}
