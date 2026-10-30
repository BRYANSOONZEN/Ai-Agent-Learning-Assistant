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
    -- 老师建课时选的模块，逗号分隔：video 视频 / document 文档 / knowledge 知识点 / dialogue AI 对话 / exercise 题目
    modules     VARCHAR(100) NOT NULL DEFAULT 'video,document,knowledge,dialogue,exercise',
    created_at  DATETIME,
    updated_at  DATETIME,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

-- 按教师查课程会很常用，给外键字段建索引
CREATE INDEX idx_course_teacher ON course (teacher_id);

-- ---------------------------------------------------------------------
-- 章节（docs/database.md 4.1，按新版设计加了 intro、status）
-- 一章 = 章节介绍 + 一串内容项（chapter_item：视频、文档、练习、AI 对话，数量和顺序由老师定）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS chapter;
CREATE TABLE chapter (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    course_id         BIGINT       NOT NULL,
    parent_id         BIGINT,                               -- 预留：NULL = 章；首版只做一级
    title             VARCHAR(100) NOT NULL,
    intro             TEXT,                                 -- 章节介绍（学习目标 + 概要），学生进入本章先看到
    status            VARCHAR(16)  NOT NULL DEFAULT 'draft', -- draft 草稿 / published 已发布（学生可见）
    sort_order        INT          NOT NULL DEFAULT 0,
    created_at        DATETIME,
    updated_at        DATETIME,
    deleted           TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_chapter_course ON chapter (course_id);

-- ---------------------------------------------------------------------
-- 章节内容项：学生按 sort_order 一项一项学。一章可以有几个视频、几次练习，穿插着排
--   video    视频：material_id 指向一个视频文件，description 是说明
--   document 文档：material_id 指向一个文件，或者不传文件、直接在 description 里写正文
--   exercise 练习：题目挂在这一项下（exercise.item_id）
--   dialogue AI 对话：话题挂在这一项下（dialogue_topic.item_id）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS chapter_item;
CREATE TABLE chapter_item (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    course_id   BIGINT       NOT NULL,
    chapter_id  BIGINT       NOT NULL,
    type        VARCHAR(16)  NOT NULL,                  -- video / document / exercise / dialogue
    title       VARCHAR(100) NOT NULL,
    description TEXT,                                   -- 说明；文档项也可以直接写正文
    material_id BIGINT,                                 -- 视频 / 文档项的文件 → material.id
    sort_order  INT          NOT NULL DEFAULT 0,
    created_at  DATETIME,
    updated_at  DATETIME,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_chapter_item_chapter ON chapter_item (chapter_id);

-- ---------------------------------------------------------------------
-- 知识点（docs/database.md 4.1）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS knowledge_point;
CREATE TABLE knowledge_point (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    course_id    BIGINT       NOT NULL,
    chapter_id   BIGINT       NOT NULL,
    name         VARCHAR(100) NOT NULL,
    content      TEXT,                                      -- 说明 / 原理句
    difficulty   TINYINT      NOT NULL DEFAULT 2,           -- 1 易 / 2 中 / 3 难
    is_key       TINYINT(1)   NOT NULL DEFAULT 0,           -- 重点（E4）
    is_difficult TINYINT(1)   NOT NULL DEFAULT 0,           -- 难点（E4）
    sort_order   INT          NOT NULL DEFAULT 0,
    created_at   DATETIME,
    updated_at   DATETIME,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_kp_chapter ON knowledge_point (chapter_id);

-- ---------------------------------------------------------------------
-- 习题（docs/database.md 4.1，加了 chapter_id 和 option_notes）
-- option_notes：每个选项的误解说明，学生选错时错题讲解用；正确选项那一项留空
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS exercise;
CREATE TABLE exercise (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    course_id    BIGINT      NOT NULL,
    chapter_id   BIGINT      NOT NULL,
    item_id      BIGINT,                                   -- 属于哪一次练习 → chapter_item.id（type = exercise）
    type         VARCHAR(16) NOT NULL DEFAULT 'single_choice', -- 首版只有单选
    stem         TEXT        NOT NULL,
    options      TEXT        NOT NULL,                     -- JSON 数组，按顺序对应 A/B/C/D
    answer       VARCHAR(16) NOT NULL,                     -- 单选：A～F
    analysis     TEXT,
    option_notes TEXT,                                     -- JSON 数组，和 options 一一对应
    difficulty   TINYINT     NOT NULL DEFAULT 2,
    sort_order   INT         NOT NULL DEFAULT 0,
    created_by   BIGINT      NOT NULL,
    created_at   DATETIME,
    updated_at   DATETIME,
    deleted      TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_exercise_chapter ON exercise (chapter_id);

DROP TABLE IF EXISTS exercise_knowledge_point;
CREATE TABLE exercise_knowledge_point (
    id                 BIGINT NOT NULL AUTO_INCREMENT,
    exercise_id        BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_exercise_kp UNIQUE (exercise_id, knowledge_point_id)
);

-- ---------------------------------------------------------------------
-- 教学资料（docs/database.md 4.1）：课件、文档、视频。文件存磁盘，表里只存路径
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS material;
CREATE TABLE material (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    course_id          BIGINT       NOT NULL,
    chapter_id         BIGINT,
    knowledge_point_id BIGINT,
    title              VARCHAR(100) NOT NULL,
    original_name      VARCHAR(255) NOT NULL,
    file_path          VARCHAR(255) NOT NULL,              -- 相对路径，如 2026/10/uuid.pdf
    file_size          BIGINT       NOT NULL,
    content_type       VARCHAR(100),
    uploaded_by        BIGINT       NOT NULL,
    created_at         DATETIME,
    deleted            TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_material_chapter ON material (chapter_id);

-- ---------------------------------------------------------------------
-- 对话话题（docs/ai-implementation.md 2.1）：老师确认过的巩固问题，学生的 AI 对话从这里取题
-- 老师填写情境问题和参考答案，AI 拆出要点、简单问法和提示，老师修改后保存
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS dialogue_topic;
CREATE TABLE dialogue_topic (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    chapter_id      BIGINT       NOT NULL,              -- 所属章节 → chapter.id
    item_id         BIGINT,                             -- 属于哪个 AI 对话项 → chapter_item.id（type = dialogue）
    sort_order      INT          NOT NULL DEFAULT 0,    -- 对话里的先后顺序，小的在前
    title           VARCHAR(100) NOT NULL,              -- 话题名
    question        TEXT         NOT NULL,              -- 情境问题
    simple_question TEXT,                               -- 答非所问时换用的简单问法
    key_points      TEXT         NOT NULL,              -- JSON 数组：要点，如 ["越过边界的 0","边界上的 1"]
    hints           TEXT,                               -- JSON 数组：预写提示 L0、L1
    reveal_text     TEXT         NOT NULL,              -- 讲解原文（老师的参考答案）
    source_ref      VARCHAR(100),                       -- 出处，如"课件第 13 页"
    source_text     TEXT,                               -- 依据的原理句原文
    created_by_ai   TINYINT(1)   NOT NULL DEFAULT 0,    -- 要点等内容是否由 AI 起草
    enabled         TINYINT(1)   NOT NULL DEFAULT 1,    -- 是否启用
    created_at      DATETIME,
    updated_at      DATETIME,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);
CREATE INDEX idx_dialogue_topic_chapter ON dialogue_topic (chapter_id);

-- ---------------------------------------------------------------------
-- 一次对话（docs/ai-implementation.md 2.2）：学生在某一章开始的一次巩固对话
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS dialogue_session;
CREATE TABLE dialogue_session (
    id                       BIGINT      NOT NULL AUTO_INCREMENT,
    student_id               BIGINT      NOT NULL,
    chapter_id               BIGINT      NOT NULL,
    item_id                  BIGINT,                            -- 章节里的哪个 AI 对话项 → chapter_item.id；为空 = 整章的话题
    kind                     VARCHAR(16) NOT NULL,              -- CHAPTER 章节对话 / REVIEW 错题讲解（先只做 CHAPTER）
    status                   VARCHAR(16) NOT NULL,              -- OPEN 进行中 / PAUSED 暂停 / FINISHED 结束
    current_idx              INT         NOT NULL DEFAULT 0,    -- 当前第几个话题（从 0 开始）
    irrelevant_topics_in_row INT         NOT NULL DEFAULT 0,    -- 连续几个话题因答非所问被跳过
    created_at               DATETIME,
    updated_at               DATETIME,
    PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------
-- 每个话题的进度（2.3）：程序状态，规则和总结都看这里，不看模型
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS dialogue_topic_state;
CREATE TABLE dialogue_topic_state (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    session_id       BIGINT      NOT NULL,
    topic_idx        INT         NOT NULL,                      -- 第几个话题
    topic_id         BIGINT,                                    -- → dialogue_topic.id
    wrong_count      INT         NOT NULL DEFAULT 0,            -- 答错 / 部分对的次数
    stuck_count      INT         NOT NULL DEFAULT 0,            -- 点"我卡住了"或直接要答案的次数
    irrelevant_count INT         NOT NULL DEFAULT 0,            -- 答非所问的次数
    hint_level       INT         NOT NULL DEFAULT -1,           -- 已给到第几级预写提示，-1 = 还没给
    hit_points       VARCHAR(100),                              -- 已经答到的要点序号，如 "1,2"
    result           VARCHAR(20),                               -- SELF_CORRECT / HINT_CORRECT / REVEALED / SKIPPED_IRRELEVANT
    PRIMARY KEY (id),
    CONSTRAINT uk_topic_state UNIQUE (session_id, topic_idx)
);

-- ---------------------------------------------------------------------
-- 对话消息（2.4）
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS dialogue_message;
CREATE TABLE dialogue_message (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    session_id  BIGINT      NOT NULL,
    topic_idx   INT,
    role        VARCHAR(16) NOT NULL,                           -- STUDENT / AI
    content     TEXT        NOT NULL,
    source      VARCHAR(16),                                    -- MODEL 模型写的 / TEMPLATE 预写文字 / RULE 程序生成
    judged_type VARCHAR(16),                                    -- 学生这句被判成哪一类
    ai_call_id  BIGINT,                                         -- 这句对应的模型调用 → ai_call_log.id
    created_at  DATETIME,
    PRIMARY KEY (id)
);
CREATE INDEX idx_dialogue_message_session ON dialogue_message (session_id);

-- ---------------------------------------------------------------------
-- AI 调用日志（docs/ai-implementation.md 2.5）：每次调模型记一条，老师拆要点和学生对话共用
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS ai_call_log;
CREATE TABLE ai_call_log (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    task           VARCHAR(32)   NOT NULL,                  -- TOPIC_DRAFT 老师拆要点 / DIALOGUE_TURN 学生对话一轮
    model          VARCHAR(64)   NOT NULL,
    prompt_version VARCHAR(32)   NOT NULL,                  -- 提示词模板名，如 dialogue_turn_v1
    input_tokens   INT,
    output_tokens  INT,
    cost_cny       DECIMAL(10,6),                           -- 费用（元）= 输入 token × 输入单价 ÷ 100 万 + 输出 token × 输出单价 ÷ 100 万
    latency_ms     INT,                                     -- 从发出到拿到结果的总耗时，包含重试
    success        TINYINT(1)    NOT NULL,                  -- 调用本身是否成功
    passed_check   TINYINT(1),                              -- 输出是否通过检查（格式、长度）
    error          VARCHAR(255),
    request_json   TEXT,                                    -- 开发阶段保存完整请求，方便排查；上线前改为只存摘要
    response_json  TEXT,                                    -- 模型返回的文字
    created_at     DATETIME,
    PRIMARY KEY (id)
);

-- ---------------------------------------------------------------------
-- 「记」：答题记录（docs/database.md 4.2）。每次作答都插新记录，重做也保留历史
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS answer_record;
CREATE TABLE answer_record (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    student_id     BIGINT       NOT NULL,
    exercise_id    BIGINT       NOT NULL,
    course_id      BIGINT       NOT NULL,                  -- 冗余，方便按课程统计
    item_id        BIGINT,                                 -- 哪一次练习 → chapter_item.id；同一次交卷的几题 submitted_at 相同
    answer         VARCHAR(255) NOT NULL,                  -- 学生的答案，没作答存空串
    is_correct     TINYINT(1)   NOT NULL,
    time_spent_sec INT,                                    -- 作答用时（首版不记）
    submitted_at   DATETIME     NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_answer_record_student ON answer_record (student_id, submitted_at);

-- ---------------------------------------------------------------------
-- 「记」：学习行为（docs/database.md 4.2）
-- event_type：view_item 看了一项内容（duration_sec = 停留秒数）/ finish_item 学完一项
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS learning_event;
CREATE TABLE learning_event (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    student_id   BIGINT      NOT NULL,
    event_type   VARCHAR(32) NOT NULL,
    target_id    BIGINT,                                   -- 内容项 → chapter_item.id
    course_id    BIGINT,                                   -- 冗余，方便按课程统计
    duration_sec INT,
    created_at   DATETIME    NOT NULL,
    PRIMARY KEY (id)
);
CREATE INDEX idx_learning_event_student ON learning_event (student_id, created_at);

-- ---------------------------------------------------------------------
-- 「诊」：知识点掌握度（docs/database.md 4.2，算法见 docs/design.md 5.1）
-- 每人每知识点一行，交卷后重算；画像和预警都读这张表
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS mastery;
CREATE TABLE mastery (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    student_id         BIGINT       NOT NULL,
    knowledge_point_id BIGINT       NOT NULL,
    mastery            DECIMAL(5,4) NOT NULL,              -- 0～1
    attempt_count      INT          NOT NULL,              -- 该知识点累计作答次数
    correct_count      INT          NOT NULL,
    updated_at         DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT uk_mastery UNIQUE (student_id, knowledge_point_id)
);

-- ---------------------------------------------------------------------
-- 「荐」：学习推荐（docs/database.md 4.4，算法见 docs/design.md 5.2）
-- 打开推荐页时按薄弱知识点选题，一题一行；学生做对了 done，点"换一道" dismissed，以后不再推
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS recommendation;
CREATE TABLE recommendation (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    student_id         BIGINT       NOT NULL,
    target_type        VARCHAR(16)  NOT NULL,              -- exercise（首版只推题）/ material / knowledge_point
    target_id          BIGINT       NOT NULL,
    knowledge_point_id BIGINT,                             -- 针对的薄弱知识点
    reason             VARCHAR(255),                       -- 如「上次做错了」「还没做过」
    status             VARCHAR(16)  NOT NULL DEFAULT 'new', -- new / done 做对了 / dismissed 不想做
    created_at         DATETIME,
    updated_at         DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT uk_recommendation UNIQUE (student_id, target_type, target_id)
);
