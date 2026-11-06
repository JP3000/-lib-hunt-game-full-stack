package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "管理员创建账号")
public record CreatePlayerRequest(
        @NotBlank(message = "学号不能为空")
        @Size(max = 32, message = "学号过长")
        String studentNo,

        @Size(max = 64, message = "昵称过长")
        String nickname,

        @NotBlank(message = "初始密码不能为空")
        @Size(min = 8, max = 64, message = "密码长度需在 8-64 位之间")
        String initialPassword,

        @Pattern(regexp = "STUDENT|STAFF|ADMIN", message = "角色只能是 STUDENT / STAFF / ADMIN")
        String role
) {
}
