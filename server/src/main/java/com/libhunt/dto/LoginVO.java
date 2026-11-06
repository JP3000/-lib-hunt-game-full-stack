package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "登录结果")
public record LoginVO(
        @Schema(description = "JWT，后续请求放在 Authorization: Bearer <token>")
        String token,
        @Schema(description = "有效期（秒）", example = "43200")
        long expiresIn,
        PlayerVO player
) {
}
