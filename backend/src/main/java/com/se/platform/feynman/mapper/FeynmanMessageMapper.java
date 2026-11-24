package com.se.platform.feynman.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.se.platform.feynman.entity.FeynmanMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FeynmanMessageMapper extends BaseMapper<FeynmanMessage> {
}
