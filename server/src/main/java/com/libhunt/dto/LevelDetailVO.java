package com.libhunt.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * 关卡详情，字段名刻意与前端 {@code LevelConfig} 保持一致，便于后续无缝切换数据源。
 * <p>
 * <b>安全约束：</b>这里不能出现 {@code qrAnswer} 与 {@code correctOptionKeys}。
 * 答案只存在于服务端 {@code level} 表，判题接口也只返回「对/错」。
 */
@Schema(description = "关卡详情（不含答案）")
public record LevelDetailVO(
        @Schema(description = "关卡编号", example = "1")
        Integer level,
        String title,
        String story,
        String completionStory,
        String question,
        List<OptionVO> options,
        @Schema(description = "二维码藏匿地点提示（不是答案，先扫码后答题的流程需要它）")
        String qrLocation,
        @Schema(description = "地点补充说明")
        String qrLocationNote,
        String videoUrl,
        String storyImageUrl,
        String storyImageAlt,
        String storyImageCaption,
        List<ItemVO> items,
        @Schema(description = "是否多选（只暴露「有几个正确答案」的上界，不暴露是哪几个）")
        boolean multiple
) {
}
