package com.libhunt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.libhunt.entity.PlayerItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PlayerItemMapper extends BaseMapper<PlayerItem> {

    /**
     * 重复领取时静默跳过，依靠 uk_player_item 唯一索引兜底，
     * 避免"先查再插"的竞态。
     */
    @Insert("""
            INSERT IGNORE INTO player_item (player_id, item_id, run_id)
            VALUES (#{playerId}, #{itemId}, #{runId})
            """)
    int insertIgnore(@Param("playerId") Long playerId,
                     @Param("itemId") Long itemId,
                     @Param("runId") Long runId);
}
