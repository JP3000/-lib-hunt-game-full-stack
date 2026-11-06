package com.libhunt.controller;

import com.libhunt.common.Result;
import com.libhunt.common.context.UserContext;
import com.libhunt.dto.AnswerRequest;
import com.libhunt.dto.AnswerResultVO;
import com.libhunt.dto.QrRequest;
import com.libhunt.dto.QrResultVO;
import com.libhunt.dto.RunStateVO;
import com.libhunt.service.GameRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 游戏核心接口，全部需要登录。
 */
@Tag(name = "游戏", description = "开局 / 判题 / 扫码验证 / 进度恢复")
@RestController
@RequestMapping("/api/runs")
@RequiredArgsConstructor
public class RunController {

    private final GameRunService gameRunService;

    @Operation(summary = "开局", description = "已有进行中的对局则直接返回该局；支持 Idempotency-Key 头")
    @PostMapping
    public Result<RunStateVO> start(
            @Parameter(description = "客户端生成的幂等键，重试时带同一个值")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return Result.ok(gameRunService.start(UserContext.getPlayerId(), idempotencyKey));
    }

    @Operation(summary = "当前对局", description = "无进行中对局时 data 为 null")
    @GetMapping("/current")
    public Result<RunStateVO> current() {
        return Result.ok(gameRunService.current(UserContext.getPlayerId()));
    }

    @Operation(summary = "放弃当前对局", description = "用于「重新开始」，已结算的成绩保留在历史中")
    @PostMapping("/{runId}/abandon")
    public Result<RunStateVO> abandon(@PathVariable Long runId) {
        return Result.ok(gameRunService.abandon(UserContext.getPlayerId(), runId));
    }

    @Operation(summary = "提交答案", description = "服务端判题；响应中不含正确答案")
    @PostMapping("/{runId}/levels/{levelNo}/answer")
    public Result<AnswerResultVO> answer(
            @PathVariable Long runId,
            @PathVariable int levelNo,
            @Valid @RequestBody AnswerRequest request) {
        return Result.ok(gameRunService.submitAnswer(
                UserContext.getPlayerId(), runId, levelNo, request.optionIds()));
    }

    @Operation(summary = "提交现场二维码", description = "服务端校验二维码内容，通过后结算本关")
    @PostMapping("/{runId}/levels/{levelNo}/qr")
    public Result<QrResultVO> qr(
            @PathVariable Long runId,
            @PathVariable int levelNo,
            @Valid @RequestBody QrRequest request) {
        return Result.ok(gameRunService.submitQr(
                UserContext.getPlayerId(), runId, levelNo, request.answer()));
    }
}
