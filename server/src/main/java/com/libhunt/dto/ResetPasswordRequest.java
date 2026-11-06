package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "重置密码")
public record ResetPasswordRequest(
        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 位之间")
        String password
) {
}
