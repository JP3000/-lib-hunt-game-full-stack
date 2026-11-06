package com.libhunt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.libhunt.entity.Level;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface LevelMapper extends BaseMapper<Level> {

    @Select("SELECT MAX(level_no) FROM level WHERE status = 1")
    Integer selectMaxLevelNo();
}
