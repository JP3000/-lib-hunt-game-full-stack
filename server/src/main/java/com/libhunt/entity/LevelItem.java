package com.libhunt.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 关卡 ↔ 道具关联（复合主键，无单列 id）。
 * 只用 selectList 查询，不通过 BaseMapper 做按主键操作。
 */
@Data
@TableName("level_item")
public class LevelItem {

    private Long levelId;

    private Long itemId;

    private Integer sortOrder;
}
