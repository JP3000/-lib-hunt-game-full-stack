package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "单关进度")
public record LevelProgressVO(
        Integer level,
        Integer score,
        Integer attempts,
        boolean questionPassed,
        boolean qrPassed
) {
}
