package com.se.platform.common.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import java.time.LocalDateTime;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 配置。
 *
 * <p>{@code @Configuration}：告诉 Spring 这是配置类。
 * 里面带 {@code @Bean} 的方法，返回的对象会交给 Spring 管理，其他地方可以直接使用。
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 分页插件：有了它，mapper.selectPage(new Page<>(1, 10), wrapper) 才会自动加上 LIMIT 语句，
     * 并额外查一次总数。没配这个插件的话，selectPage 会把全部数据查出来，分页不生效。
     *
     * <p>H2 开启了 MySQL 兼容模式，所以这里统一按 MySQL 的语法生成分页 SQL。
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        pagination.setMaxLimit(100L);   // 每页最多 100 条，和 docs/api.md 的约定一致，防止前端一次查太多
        interceptor.addInnerInterceptor(pagination);
        return interceptor;
    }

    /**
     * 自动填充 created_at / updated_at：插入和更新时不用手动 setCreatedAt(...)。
     * 实体类的字段上要加 @TableField(fill = FieldFill.INSERT) 之类的注解，这里才会生效。
     */
    @Bean
    public MetaObjectHandler timeFillHandler() {
        return new MetaObjectHandler() {
            @Override
            public void insertFill(MetaObject metaObject) {
                LocalDateTime now = LocalDateTime.now();
                strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
            }

            @Override
            public void updateFill(MetaObject metaObject) {
                strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
            }
        };
    }
}
