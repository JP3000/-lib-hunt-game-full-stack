package com.libhunt.controller;

import com.libhunt.common.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.Map;

@Tag(name = "系统")
@RestController
@RequestMapping("/api")
public class HealthController {

    @Operation(summary = "健康检查（无需认证）")
    @GetMapping("/health")
    public Result<Map<String, Object>> health() {
        return Result.ok(Map.of(
                "status", "UP",
                "time", OffsetDateTime.now().toString()
        ));
    }
}
