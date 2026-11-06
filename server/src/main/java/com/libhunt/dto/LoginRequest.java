package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "登录请求")
public record LoginRequest(
        @Schema(description = "学号 / 工号 / 管理员账号", example = "admin")
        @NotBlank(message = "请输入账号")
        String studentNo,

        @Schema(description = "密码", example = "Admin@123456")
        @NotBlank(message = "请输入密码")
        String password
) {
}
