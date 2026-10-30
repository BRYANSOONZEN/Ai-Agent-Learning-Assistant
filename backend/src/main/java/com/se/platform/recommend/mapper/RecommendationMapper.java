package com.se.platform.recommend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.se.platform.recommend.entity.Recommendation;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface RecommendationMapper extends BaseMapper<Recommendation> {
}
