package com.libhunt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("game_run")
public class GameRun {

    public static final String IN_PROGRESS = "IN_PROGRESS";
    public static final String COMPLETED = "COMPLETED";
    public static final String ABANDONED = "ABANDONED";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long playerId;

    private String status;

    private LocalDateTime startedAt;

    private LocalDateTime finishedAt;

    private Integer totalScore;

    private Integer durationSeconds;

    private Integer lastLevelNo;

    private String idempotencyKey;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
