package com.libhunt.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.libhunt.common.Result;
import com.libhunt.common.annotation.RequireRole;
import com.libhunt.common.exception.BusinessException;
import com.libhunt.dto.QrTokenVO;
import com.libhunt.entity.Level;
import com.libhunt.mapper.LevelMapper;
import com.libhunt.service.QrTokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;

@Tag(name = "管理端 · 二维码", description = "仅 ADMIN 可访问")
@RestController
@RequestMapping("/api/admin/levels")
@RequiredArgsConstructor
@RequireRole("ADMIN")
public class AdminQrController {

    private static final long MIN_TTL_SECONDS = 60;
    private static final long MAX_TTL_SECONDS = 30L * 24 * 60 * 60;

    private final LevelMapper levelMapper;
    private final QrTokenService qrTokenService;

    @Operation(summary = "签发关卡二维码",
            description = "返回带过期时间的 HMAC 签名内容；有效期默认 7 天，范围 1 分钟 ~ 30 天")
    @GetMapping("/{levelNo}/qr")
    public Result<QrTokenVO> issue(@PathVariable int levelNo,
                                   @RequestParam(defaultValue = "604800") long ttlSeconds) {
        Level level = levelMapper.selectOne(
                new LambdaQueryWrapper<Level>()
                        .eq(Level::getLevelNo, levelNo)
                        .eq(Level::getStatus, 1)
                        .last("LIMIT 1"));
        if (level == null) {
            throw new BusinessException("关卡不存在：" + levelNo);
        }
        if (level.getQrAnswer() == null || level.getQrAnswer().isBlank()) {
            throw new BusinessException("关卡未配置二维码密钥：" + levelNo);
        }
        long ttl = Math.max(MIN_TTL_SECONDS, Math.min(MAX_TTL_SECONDS, ttlSeconds));
        String token = qrTokenService.issue(levelNo, level.getQrAnswer(), Duration.ofSeconds(ttl));
        return Result.ok(new QrTokenVO(levelNo, token, Instant.now().plusSeconds(ttl).toString()));
    }
}
