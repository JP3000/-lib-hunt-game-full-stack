package com.libhunt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("player_item")
public class PlayerItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long playerId;

    private Long itemId;

    private Long runId;

    private LocalDateTime collectedAt;
}
