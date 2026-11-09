package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "学生学号直登请求")
public record StudentLoginRequest(
        @Schema(description = "学号", example = "ab565666")
        @NotBlank(message = "请输入学号")
        @Size(max = 32, message = "学号长度不能超过 32 个字符")
        @Pattern(regexp = "^[A-Za-z0-9._-]+$", message = "学号只能包含字母、数字、点、下划线和连字符")
        String studentNo
) {
}
