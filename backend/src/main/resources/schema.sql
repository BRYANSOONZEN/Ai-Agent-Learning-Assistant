-- =====================================================================
-- 建表语句：Spring Boot 启动时自动执行（application.yml 里 spring.sql.init.mode=always）
-- 表结构以 docs/database.md 为准，改表要先改文档。
-- 写法同时兼容 H2（MySQL 模式）和 MySQL 8：不写 ENGINE、CHARSET 这类只有 MySQL 认识的选项。
-- =====================================================================

-- 先删再建：H2 内存库在一次测试运行中可能被多个 Spring 上下文共用，
-- 不先删的话，第二次执行会报「表已存在」。
DROP TABLE IF EXISTS course;
DROP TABLE IF EXISTS `user`;

-- ---------------------------------------------------------------------
-- 用户（docs/database.md 4.1）
-- user 在 H2 里是关键字，所以表名加反引号；application.yml 里的 NON_KEYWORDS=USER 也是为了它
-- ---------------------------------------------------------------------
CREATE TABLE `user` (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(32)  NOT NULL,                -- 登录名
    password_hash VARCHAR(100) NOT NULL,                -- BCrypt 哈希，不存明文
    name          VARCHAR(32)  NOT NULL,                -- 显示姓名
    role          VARCHAR(16)  NOT NULL,                -- student / teacher / admin
    clazz_id      BIGINT,                               -- 学生所在班级（班级表以后再建）
    status        TINYINT      NOT NULL DEFAULT 1,      -- 1 正常，0 禁用
    created_at    DATETIME,
    updated_at    DATETIME,
    deleted       TINYINT      NOT NULL DEFAULT 0,      -- 逻辑删除：1 = 已删除
    PRIMARY KEY (id),
    CONSTRAINT uk_user_username UNIQUE (username)        -- 登录名不能重复
);

-- ---------------------------------------------------------------------
-- 课程（docs/database.md 4.1）
-- 前端要的 teacher（教师姓名）、chapterCount、studentCount 不存在这里，查询时计算
-- ---------------------------------------------------------------------
CREATE TABLE course (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    teacher_id  BIGINT       NOT NULL,                  -- 授课教师 → user.id（不建外键约束，由代码保证）
    status      VARCHAR(16)  NOT NULL DEFAULT 'draft',  -- draft 草稿 / published 已发布 / archived 已归档
    created_at  DATETIME,
    updated_at  DATETIME,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 按教师查课程会很常用，给外键字段建索引
CREATE INDEX idx_course_teacher ON course (teacher_id);
