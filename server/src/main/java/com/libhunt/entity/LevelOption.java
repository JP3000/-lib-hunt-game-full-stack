package com.libhunt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.Map;

@Data
@TableName(value = "level_option", autoResultMap = true)
public class LevelOption {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long levelId;

    /** a / b / c / d */
    private String optionKey;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> label;

    private Integer sortOrder;
}
