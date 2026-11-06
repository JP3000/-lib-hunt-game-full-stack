package com.libhunt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.libhunt.entity.Player;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface PlayerMapper extends BaseMapper<Player> {

    /**
     * 开局前锁住玩家行，防止同一玩家并发点两次「开始」产生两局。
     * 必须在事务内调用。
     */
    @Select("SELECT id FROM player WHERE id = #{id} FOR UPDATE")
    Long lockById(@Param("id") Long id);
}
