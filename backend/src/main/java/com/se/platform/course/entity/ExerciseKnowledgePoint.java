package com.se.platform.course.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/** 习题和知识点的多对多关联，对应 exercise_knowledge_point 表（没有 deleted 列，删除就是真删） */
@Data
@TableName("exercise_knowledge_point")
public class ExerciseKnowledgePoint {

    private Long id;
    private Long exerciseId;
    private Long knowledgePointId;

    public static ExerciseKnowledgePoint of(Long exerciseId, Long knowledgePointId) {
        ExerciseKnowledgePoint link = new ExerciseKnowledgePoint();
        link.setExerciseId(exerciseId);
        link.setKnowledgePointId(knowledgePointId);
        return link;
    }
}
