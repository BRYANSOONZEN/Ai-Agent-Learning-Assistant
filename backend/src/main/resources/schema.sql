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
    knowledge_point_id BIGINT,                          -- 考哪个知识点（概念层）→ knowledge_point.id；干预按它对上练习题（应用层）
    sort_order      INT          NOT NULL DEFAULT 0,    -- 对话里的先后顺序，小的在前
    title           VARCHAR(100) NOT NULL,              -- 话题名
    question        TEXT         NOT NULL,              -- 情境问题
    simple_question TEXT,                               -- 答非所问时换用的简单问法
    key_points      TEXT         NOT NULL,              -- JSON 数组：要点，如 ["越过边界的 0","边界上的 1"]
    hints           TEXT,                               -- JSON 数组：预写提示 L1、L2、L3，模型失败时才用
    reveal_text     TEXT         NOT NULL,              -- 讲解原文（老师的参考答案）
    source_ref      VARCHAR(100),                       -- 出处，如"课件第 13 页"
    source_text     TEXT,                               -- 依据的原理句原文
    transfer_question TEXT,                             -- 变式题：换情境考同一个知识点，旧话题可以为空
    transfer_points TEXT,                               -- JSON 数组：变式题的要点
    transfer_reveal TEXT,                               -- 变式题的参考答案
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
    hint_level       INT         NOT NULL DEFAULT -1,           -- 已给到第几级提示（0、1、2 = L1、L2、L3），-1 = 还没给
    hit_points       VARCHAR(100),                              -- 已经答到的要点序号，如 "1,2"
    result           VARCHAR(20),                               -- SELF_CORRECT / HINT_CORRECT / REVEALED_APPLIED / REVEALED / SKIPPED_IRRELEVANT
    phase            VARCHAR(16) NOT NULL DEFAULT 'ANSWER',         -- ANSWER 作答 / TRANSFER 变式检验
    turn_count       INT         NOT NULL DEFAULT 0,            -- 当前阶段走了几轮，到上限就往下走
    answer_count     INT         NOT NULL DEFAULT 0,            -- 真正作答过几次，没作答过的拿不到答案
    refuse_count     INT         NOT NULL DEFAULT 0,            -- 不作答只要答案的次数，进入变式时清零
    given_points     VARCHAR(100),                              -- 程序已告诉学生的要点序号，不算学生答到
    transfer_hit_points VARCHAR(100),                           -- 变式题已答到的要点序号
    transfer_tries   INT         NOT NULL DEFAULT 0,            -- 变式题作答了几次
    misconception    VARCHAR(500),                              -- 模型最近发现的错误想法，不算进展
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
    source             VARCHAR(16)  NOT NULL DEFAULT 'system', -- system 系统推荐 / teacher 老师布置（干预）
    intervention_id    BIGINT,                             -- 老师布置时：哪条干预 → intervention.id
    created_at         DATETIME,
    updated_at         DATETIME,
    PRIMARY KEY (id),
    CONSTRAINT uk_recommendation UNIQUE (student_id, target_type, target_id)
);

-- ---------------------------------------------------------------------
-- 「干」：预警（docs/计划-干预A和B.md）
-- 按两层判断：概念层看 AI 对话话题结果，应用层看掌握度。一人一个知识点同时最多一条没处理完的
-- type：A 应用问题 / B 概念问题 / AB 两层都不好
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS alert;
CREATE TABLE alert (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    course_id          BIGINT       NOT NULL,
    student_id         BIGINT       NOT NULL,
    knowledge_point_id BIGINT       NOT NULL,
    type               VARCHAR(8)   NOT NULL,              -- A / B / AB
    level              VARCHAR(8)   NOT NULL,              -- high / medium
    message            VARCHAR(255) NOT NULL,              -- 给老师看的一句话
    evidence           TEXT,                               -- 判断时的数据快照（JSON），数据以后变了也能看到当时为什么报警
    status             VARCHAR(16)  NOT NULL DEFAULT 'open', -- open 待处理 / handled 已处理 / ignored 已忽略
    handled_at         DATETIME,
    created_at         DATETIME,
    updated_at         DATETIME,
    PRIMARY KEY (id)
);
CREATE INDEX idx_alert_course ON alert (course_id, status);

-- ---------------------------------------------------------------------
-- 「干」：干预。系统按预警写好建议（pending），老师通过后才推给学生
-- type：PRACTICE 变式练习（A）/ REVIEW 复习 + 重做对话（B）/ REVIEW_PRACTICE 先复习后练习（AB，step 1→2）
-- before_value / after_value：A 是掌握度；B 是话题结果
-- ---------------------------------------------------------------------
DROP TABLE IF EXISTS intervention;
CREATE TABLE intervention (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    alert_id           BIGINT,
    course_id          BIGINT       NOT NULL,
    student_id         BIGINT       NOT NULL,
    knowledge_point_id BIGINT       NOT NULL,
    type               VARCHAR(16)  NOT NULL,
    step               INT          NOT NULL DEFAULT 1,     -- REVIEW_PRACTICE 进行到第几步
    exercise_ids       VARCHAR(255),                       -- 布置的题，逗号分隔，如 "3,5"
    item_ids           VARCHAR(255),                       -- 要复习的内容项，逗号分隔
    note               VARCHAR(500),                       -- 老师给学生的话
    source             VARCHAR(16)  NOT NULL DEFAULT 'system', -- system 系统建议 / teacher 老师新建
    status             VARCHAR(16)  NOT NULL DEFAULT 'pending', -- pending 待审核 / approved 执行中 / rejected 已驳回 / done 已完成
    before_value       VARCHAR(32),
    after_value        VARCHAR(32),
    effective          TINYINT(1),                         -- 完成时判断：1 有效 / 0 无效
    reviewer_id        BIGINT,
    reviewed_at        DATETIME,
    step2_at           DATETIME,                           -- AB 进入第 2 步的时间（之后的作答才算第 2 步的）
    completed_at       DATETIME,
    created_at         DATETIME,
    updated_at         DATETIME,
    PRIMARY KEY (id)
);
CREATE INDEX idx_intervention_student ON intervention (student_id, status);
