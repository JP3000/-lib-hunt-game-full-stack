package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "关卡二维码内容（管理员签发）")
public record QrTokenVO(
        @Schema(description = "关卡号") int levelNo,
        @Schema(description = "二维码内容，打印后张贴在现场") String token,
        @Schema(description = "过期时间（ISO-8601）") String expiresAt
) {
}
