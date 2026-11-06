package com.libhunt.controller;

import com.libhunt.common.Result;
import com.libhunt.dto.LevelDetailVO;
import com.libhunt.dto.LevelSummaryVO;
import com.libhunt.service.LevelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 关卡内容读取。
 * <p>
 * 这两个接口是公开的（登录前就要展示关卡封面/标题），
 * 但返回值中<b>不含任何答案</b>；判题与进度接口在后续 Phase 中保持需要认证。
 */
@Tag(name = "关卡", description = "只读接口，不含答案")
@RestController
@RequestMapping("/api/levels")
@RequiredArgsConstructor
public class LevelController {

    private final LevelService levelService;

    @Operation(summary = "关卡列表", description = "返回启用状态的关卡，按关卡号升序")
    @GetMapping
    public Result<List<LevelSummaryVO>> list(
            @Parameter(description = "zh-Hant / zh-Hans / en，默认 zh-Hant")
            @RequestParam(defaultValue = "zh-Hant") String locale) {
        return Result.ok(levelService.list(locale));
    }

    @Operation(summary = "关卡详情", description = "含剧情、题目、选项、道具；不含正确答案与二维码答案")
    @GetMapping("/{levelNo}")
    public Result<LevelDetailVO> detail(
            @Parameter(description = "关卡号 1..12")
            @PathVariable int levelNo,
            @Parameter(description = "zh-Hant / zh-Hans / en，默认 zh-Hant")
            @RequestParam(defaultValue = "zh-Hant") String locale) {
        return Result.ok(levelService.detail(levelNo, locale));
    }
}
