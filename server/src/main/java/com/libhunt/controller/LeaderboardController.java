package com.libhunt.controller;

import com.libhunt.common.Result;
import com.libhunt.dto.LeaderboardEntryVO;
import com.libhunt.service.LeaderboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "排行榜")
@RestController
@RequestMapping("/api/leaderboard")
@RequiredArgsConstructor
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    @Operation(summary = "排行榜前 N 名", description = "每位玩家只保留最好的一局；Redis 不可用时自动回落 MySQL")
    @GetMapping
    public Result<List<LeaderboardEntryVO>> top(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(leaderboardService.top(limit));
    }
}
