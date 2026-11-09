package com.libhunt.controller;

import com.libhunt.common.Result;
import com.libhunt.dto.LoginRequest;
import com.libhunt.dto.LoginVO;
import com.libhunt.dto.PlayerVO;
import com.libhunt.dto.StudentLoginRequest;
import com.libhunt.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "登录", description = "成功返回 JWT；失败统一提示「账号或密码错误」")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        return Result.ok(authService.login(request));
    }

    @Operation(summary = "学生学号直登", description = "只需学号；首次登录自动创建学生账号。管理员仍走 /login")
    @PostMapping("/student-login")
    public Result<LoginVO> studentLogin(@Valid @RequestBody StudentLoginRequest request) {
        return Result.ok(authService.studentLogin(request));
    }

    @Operation(summary = "当前账号", description = "需要 Authorization: Bearer <token>")
    @GetMapping("/me")
    public Result<PlayerVO> me() {
        return Result.ok(authService.currentPlayer());
    }
}
