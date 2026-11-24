package com.se.platform.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.se.platform.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

/** 继承 BaseMapper 后，selectOne、selectById 这些方法不用自己写 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
