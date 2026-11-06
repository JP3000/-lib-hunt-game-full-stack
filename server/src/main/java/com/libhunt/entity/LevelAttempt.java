package com.libhunt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("level_attempt")
public class LevelAttempt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long runId;

    private Integer levelNo;

    private Integer score;

    private Integer attempts;

    /** 1 = 题目已答对 */
    private Integer questionPassed;

    /** 1 = 现场二维码已验过 */
    private Integer qrPassed;

    /** 1 = 已结算：计分、发道具、推进关卡只做一次 */
    private Integer settled;

    private LocalDateTime completedAt;
}
