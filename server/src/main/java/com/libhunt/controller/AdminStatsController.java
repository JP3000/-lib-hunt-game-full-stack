package com.libhunt.controller;

import com.libhunt.common.Result;
import com.libhunt.common.annotation.RequireRole;
import com.libhunt.dto.AdminStatsVO;
import com.libhunt.service.AdminStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "管理端 · 统计", description = "仅 ADMIN 可访问")
@RestController
@RequestMapping("/api/admin/stats")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class AdminStatsController {

    private final AdminStatsService adminStatsService;

    @Operation(summary = "总览数据")
    @GetMapping
    public Result<AdminStatsVO> overview() {
        return Result.ok(adminStatsService.overview());
    }
}
