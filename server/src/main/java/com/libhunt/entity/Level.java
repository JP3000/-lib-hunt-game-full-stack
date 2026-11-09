package com.libhunt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 关卡主数据。
 * <p>
 * {@code title / story / question} 等是 JSON 列，值为 {@code {"zh-Hant": "...", ...}}。
 * 注意：{@code qrAnswer} 是二维码签名密钥，{@code correctOptionKeys} 是答案，
 * <b>任何对外 DTO 都不得包含它们</b>。
 */
@Data
@TableName(value = "level", autoResultMap = true)
public class Level {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Integer levelNo;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> title;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> story;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> completionStory;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> question;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private List<String> correctOptionKeys;

    private String qrAnswer;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> qrLocation;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> qrLocationNote;

    private String videoUrl;

    private String storyImageUrl;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> storyImageAlt;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> storyImageCaption;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
