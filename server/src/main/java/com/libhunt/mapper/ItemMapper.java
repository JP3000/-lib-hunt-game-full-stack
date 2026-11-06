package com.libhunt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.libhunt.entity.Item;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ItemMapper extends BaseMapper<Item> {
}
