package com.se.platform.dialogue.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.se.platform.dialogue.entity.DialogueTopic;
import org.apache.ibatis.annotations.Mapper;

/**
 * 继承 BaseMapper 后，不用写一行 SQL 就有 insert、selectById、selectList、updateById、deleteById 等方法。
 * {@code @Mapper}：让 MyBatis 启动时为这个接口自动生成实现类，交给 Spring 管理。
 */
@Mapper
public interface DialogueTopicMapper extends BaseMapper<DialogueTopic> {
}
