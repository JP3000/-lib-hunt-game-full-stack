package com.libhunt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.Data;

import java.util.Map;

@Data
@TableName(value = "item", autoResultMap = true)
public class Item {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 稳定业务键，如 item-01-note */
    private String itemKey;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> name;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, String> description;

    private String imageUrl;
}
