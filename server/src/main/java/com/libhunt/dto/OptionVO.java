package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "选项（不含正确性标记）")
public record OptionVO(
        @Schema(description = "选项键 a/b/c/d", example = "a")
        String id,
        @Schema(description = "选项文案")
        String label
) {
}
