package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "现场二维码校验请求")
public record QrRequest(
        @Schema(description = "扫码得到的文本", example = "IQUAN1634")
        @NotBlank(message = "二维码内容不能为空")
        String answer
) {
}
