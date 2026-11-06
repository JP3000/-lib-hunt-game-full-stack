package com.libhunt.controller;

import com.libhunt.common.Result;
import com.libhunt.common.annotation.RequireRole;
import com.libhunt.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "管理端 · 排行榜", description = "仅 ADMIN 可访问")
@RestController
@RequestMapping("/api/admin/leaderboard")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class AdminLeaderboardController {

    private final LeaderboardService leaderboardService;

    @Operation(summary = "重建 Redis 排行榜", description = "从 MySQL 全量重建，用于 Redis 数据丢失后恢复")
    @PostMapping("/rebuild")
    public Result<Map<String, Integer>> rebuild() {
        return Result.ok(Map.of("entries", leaderboardService.rebuild()));
    }
}
