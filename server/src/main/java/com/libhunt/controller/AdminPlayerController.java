package com.libhunt.controller;

import com.libhunt.common.Result;
import com.libhunt.common.annotation.RequireRole;
import com.libhunt.dto.CreatePlayerRequest;
import com.libhunt.dto.PageVO;
import com.libhunt.dto.PlayerVO;
import com.libhunt.dto.ResetPasswordRequest;
import com.libhunt.dto.UpdateStatusRequest;
import com.libhunt.service.PlayerAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端 · 账号", description = "仅 ADMIN 可访问")
@RestController
@RequestMapping("/api/admin/players")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class AdminPlayerController {

    private final PlayerAdminService playerAdminService;

    @Operation(summary = "账号分页", description = "按学号或昵称模糊搜索")
    @GetMapping
    public Result<PageVO<PlayerVO>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return Result.ok(playerAdminService.page(keyword, page, size));
    }

    @Operation(summary = "创建账号", description = "密码立即 BCrypt 化，接口不返回密码或哈希")
    @PostMapping
    public Result<PlayerVO> create(@Valid @RequestBody CreatePlayerRequest request) {
        return Result.ok(playerAdminService.create(request));
    }

    @Operation(summary = "启用 / 禁用账号")
    @PatchMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody UpdateStatusRequest request) {
        playerAdminService.updateStatus(id, request.status());
        return Result.ok();
    }

    @Operation(summary = "重置密码")
    @PostMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id,
                                      @Valid @RequestBody ResetPasswordRequest request) {
        playerAdminService.resetPassword(id, request.password());
        return Result.ok();
    }
}
