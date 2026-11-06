package com.libhunt.dto;

import lombok.Data;

/**
 * 排行榜查询的中间结果（MySQL 侧），不直接对外暴露。
 */
@Data
public class LeaderboardRow {

    private Long runId;
    private Long playerId;
    private String nickname;
    private Integer totalScore;
    private Integer durationSeconds;
}
