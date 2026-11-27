package com.se.platform.auth.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户，对应 user 表。
 * 它带着 passwordHash，所以只在后端内部使用，绝不直接返回给前端；返回给前端的是 AuthDtos 里的对象。
 * user 在部分数据库里是关键字，所以表名加反引号。
 */
@Data
@TableName("`user`")
public class User {

    /** 账号状态：1 正常 */
    public static final int STATUS_ACTIVE = 1;

    private Long id;
    private String username;
    private String passwordHash;
    private String name;
    /** student / teacher / admin */
    private String role;
    private Long clazzId;
    /** 1 正常，0 禁用 */
    private Integer status;

    /** 令牌版本：令牌里带着签发时的版本，不一样就失效（退出登录、改密码、重置密码时加 1） */
    private Integer tokenVersion;

    /** true = 第一次登录要先改密码（管理员开的账号、管理员重置过的密码） */
    private Boolean mustChangePassword;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private Integer deleted;
}
