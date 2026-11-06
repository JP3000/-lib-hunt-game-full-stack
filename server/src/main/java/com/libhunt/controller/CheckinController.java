package com.libhunt.controller;

import com.libhunt.common.Result;
import com.libhunt.common.context.UserContext;
import com.libhunt.dto.CheckinStatusVO;
import com.libhunt.service.CheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "到馆打卡", description = "Redis BitMap 记录每日到馆")
@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @Operation(summary = "今日打卡", description = "幂等：同一天重复调用结果不变")
    @PostMapping
    public Result<CheckinStatusVO> checkIn() {
        return Result.ok(checkinService.checkIn(UserContext.getPlayerId()));
    }

    @Operation(summary = "打卡状态", description = "今天是否打卡、今年累计天数、连续天数")
    @GetMapping
    public Result<CheckinStatusVO> status() {
        return Result.ok(checkinService.status(UserContext.getPlayerId()));
    }
}
