package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "答题请求")
public record AnswerRequest(
        @Schema(description = "选中的选项 key；多选题需全部选对", example = "[\"b\"]")
        List<String> optionIds
) {
}
