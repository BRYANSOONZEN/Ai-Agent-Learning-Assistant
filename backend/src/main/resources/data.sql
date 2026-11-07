-- =====================================================================
-- 初始数据：在 schema.sql 之后自动执行。
-- 不手动指定 id，让数据库自增（1、2、3……）。
-- 原因：H2 里手动插入 id 后，自增计数器不会跟着往前走，之后新建数据会和已有 id 冲突。
-- =====================================================================

-- ---------------------------------------------------------------------
-- 用户：插入顺序决定 id —— 1 管理员，2～4 教师，5～14 学生
-- password_hash 暂时是占位符，登录功能完成后替换成真正的 BCrypt 哈希（现在这些账号还不能登录）
-- ---------------------------------------------------------------------
INSERT INTO `user` (username, password_hash, name, role, created_at, updated_at) VALUES
('admin',     'PENDING_LOGIN_FEATURE', '管理员', 'admin',   NOW(), NOW()),
('teacher01', 'PENDING_LOGIN_FEATURE', '李老师', 'teacher', NOW(), NOW()),
('teacher02', 'PENDING_LOGIN_FEATURE', '王老师', 'teacher', NOW(), NOW()),
('teacher03', 'PENDING_LOGIN_FEATURE', '赵老师', 'teacher', NOW(), NOW()),
('2023001',   'PENDING_LOGIN_FEATURE', '学生01', 'student', NOW(), NOW()),
('2023002',   'PENDING_LOGIN_FEATURE', '学生02', 'student', NOW(), NOW()),
('2023003',   'PENDING_LOGIN_FEATURE', '学生03', 'student', NOW(), NOW()),
('2023004',   'PENDING_LOGIN_FEATURE', '学生04', 'student', NOW(), NOW()),
('2023005',   'PENDING_LOGIN_FEATURE', '学生05', 'student', NOW(), NOW()),
('2023006',   'PENDING_LOGIN_FEATURE', '学生06', 'student', NOW(), NOW()),
('2023007',   'PENDING_LOGIN_FEATURE', '学生07', 'student', NOW(), NOW()),
('2023008',   'PENDING_LOGIN_FEATURE', '学生08', 'student', NOW(), NOW()),
('2023009',   'PENDING_LOGIN_FEATURE', '学生09', 'student', NOW(), NOW()),
('2023010',   'PENDING_LOGIN_FEATURE', '学生10', 'student', NOW(), NOW());

-- ---------------------------------------------------------------------
-- 课程：第 1 门是首版要做的「软件测试」（docs/proposal.md：软件测试单元）
-- 另外 5 门和前端 mock 数据一致，用来演示分页、搜索、不同状态
-- teacher_id：2 李老师，3 王老师，4 赵老师
-- ---------------------------------------------------------------------
INSERT INTO course (name, description, teacher_id, status, created_at, updated_at) VALUES
('软件测试',           '等价类划分、边界值分析等黑盒测试方法，以及白盒测试与自动化测试。', 2, 'published', '2026-09-01 09:00:00', '2026-09-01 09:00:00'),
('软件工程导论',       '软件生命周期、过程模型与项目管理基础。',                         2, 'published', '2026-09-02 09:00:00', '2026-09-02 09:00:00'),
('需求工程',           '需求获取、分析、建模与验证。',                                   3, 'published', '2026-09-03 09:00:00', '2026-09-03 09:00:00'),
('软件设计与体系结构', '设计原则、设计模式与架构风格。',                                 2, 'draft',     '2026-09-10 09:00:00', '2026-09-10 09:00:00'),
('软件项目管理',       '进度、成本、风险与质量管理。',                                   3, 'archived',  '2026-03-01 09:00:00', '2026-03-01 09:00:00'),
('UML 建模',           '用例图、类图、时序图与状态图。',                                 4, 'published', '2026-09-15 09:00:00', '2026-09-15 09:00:00');

-- 其他课默认开全部模块；「软件设计与体系结构」只用文档 + 知识点 + 题目（演示：老师可以不开视频和 AI 对话）
UPDATE course SET modules = 'document,knowledge,exercise' WHERE id = 4;

-- ---------------------------------------------------------------------
-- 章节：「软件测试」（course_id = 1）的两章。第 1 章 id = 1，下面的对话话题就挂在它下面
-- ---------------------------------------------------------------------
INSERT INTO chapter (course_id, title, intro, status, sort_order, created_at, updated_at) VALUES
(1, '第 1 章 边界值分析',
 '学习目标：能说出边界值分析在边界上、边界外侧、边界内侧各取什么值，并解释为什么边界附近更容易发现错误。
概要：大量错误发生在输入范围的边界上。本章用订单数量、年龄等例子练习如何选取边界测试值。',
 'published', 0, NOW(), NOW()),
(1, '第 2 章 等价类划分', NULL, 'draft', 1, NOW(), NOW());

-- 知识点：第 1 章两个（id 1、2）
INSERT INTO knowledge_point (course_id, chapter_id, name, content, difficulty, is_key, is_difficult, sort_order, created_at, updated_at) VALUES
(1, 1, '边界测试值的选取', '边界值分析：在边界上、边界外侧、边界内侧各取值进行测试。', 2, 1, 0, 0, NOW(), NOW()),
(1, 1, '边界错误的成因',   '大量错误发生在输入或输出范围的边界上，而不是在范围内部。', 2, 0, 1, 1, NOW(), NOW());

-- 习题：第 1 章两道单选。情境用"年龄 18～60"，和对话话题的"订单 1～99"分开
-- 第 1 章的内容项：先读要点，再做练习，最后 AI 对话（id 1、2、3）
INSERT INTO chapter_item (course_id, chapter_id, type, title, description, sort_order, created_at, updated_at) VALUES
(1, 1, 'document', '阅读：边界值分析要点',
 '1. 大量错误发生在输入范围的边界上，而不是在范围内部。
2. 选取测试值时，在边界上、边界外侧、边界内侧各取一个值。
3. 例：数量范围 1～99，下边界取 0、1、2，上边界取 98、99、100。', 0, NOW(), NOW()),
(1, 1, 'exercise', '练习：边界值小测', '两道单选，做完再看结果。', 1, NOW(), NOW()),
(1, 1, 'dialogue', 'AI 对话：订单数量怎么测', '学完后和 AI 聊一聊，检查自己是不是真的懂了。', 2, NOW(), NOW());

INSERT INTO exercise (course_id, chapter_id, item_id, type, stem, options, answer, analysis, option_notes, difficulty, sort_order, created_by, created_at, updated_at) VALUES
(1, 1, 2, 'single_choice', '报名年龄要求为 18～60 岁。测试下边界时，下面哪组取值最合适？',
 '["17、18、19","18、30、60","0、18、100","18、19、20"]', 'A',
 '下边界是 18：17 刚越过边界（应拒绝），18 在边界上，19 在边界内侧。',
 '["","把上下边界和中间值混在一起，没有取边界外侧的值","取了离边界很远的值，测不出边界附近的错误","只取了边界和内侧，缺少越过边界的值"]',
 2, 0, 2, NOW(), NOW()),
(1, 1, 2, 'single_choice', '程序把"年龄 >= 18"错写成"年龄 > 18"。下面哪个测试值能发现这个错误？',
 '["30","18","60","45"]', 'B',
 '只有正好等于 18 时，>= 和 > 的结果才不同，所以只有边界值 18 能发现。',
 '["中间值在两种写法下结果相同，发现不了","","这是上边界，和下边界的判断无关","中间值在两种写法下结果相同，发现不了"]',
 2, 1, 2, NOW(), NOW());

-- 题库：不属于任何一次练习（item_id 为空），给「学习推荐」挑题用（id 3～6）
INSERT INTO exercise (course_id, chapter_id, item_id, type, stem, options, answer, analysis, option_notes, difficulty, sort_order, created_by, created_at, updated_at) VALUES
(1, 1, NULL, 'single_choice', '购物车每单最多买 99 件。测试上边界时，下面哪组取值最合适？',
 '["98、99、100","1、50、99","99、199、999","97、98、99"]', 'A',
 '上边界是 99：98 在边界内侧，99 在边界上，100 刚越过边界（应拒绝）。',
 '["","把下边界、中间值和上边界混在一起，不是专门测上边界","越过边界太远，测不出差 1 的错误","只取了边界和内侧，缺少越过边界的值"]',
 1, 10, 2, NOW(), NOW()),
(1, 1, NULL, 'single_choice', '考试成绩 0～100 分为有效输入。按边界值分析，下面哪个值不需要专门测？',
 '["-1","0","50","101"]', 'C',
 '50 在范围中间，和边界无关；-1、0 测下边界，101 测上边界。',
 '["以为越过边界的无效值不用测，其实 -1 正好测下边界外侧","以为边界上的值不用测，其实 0 正好测下边界","","以为越过边界的无效值不用测，其实 101 正好测上边界外侧"]',
 2, 11, 2, NOW(), NOW()),
(1, 1, NULL, 'single_choice', '密码长度要求 8～16 位。下面哪组测试值能同时覆盖上下两个边界？',
 '["8、12、16","7、8、16、17","1、8、16、30","8、9、15、16"]', 'B',
 '7、8 测下边界（外侧和边界上），16、17 测上边界（边界上和外侧）。',
 '["只取了边界上的值和中间值，缺少越过边界的值","","1 和 30 离边界太远，测不出差 1 的错误","都在范围内，缺少越过边界的值"]',
 2, 12, 2, NOW(), NOW()),
(1, 1, NULL, 'single_choice', '为什么边界附近更容易出错？',
 '["程序员常把 < 和 <= 写混","边界上的值用得最多","中间值不会被测试","编译器对边界值有特殊处理"]', 'A',
 '比较运算差一个等号、循环多一次少一次，错误都只在边界上才表现出来。',
 '["","边界值并不一定用得最多","中间值也会测，只是不容易发现错误","编译器不会特殊处理边界值"]',
 2, 13, 2, NOW(), NOW());

INSERT INTO exercise_knowledge_point (exercise_id, knowledge_point_id) VALUES (1, 1), (2, 2), (3, 1), (4, 1), (5, 1), (6, 2);

-- ---------------------------------------------------------------------
-- 对话话题示例：第 1 章"边界值分析"的 2 个话题（代替老师录入，方便直接看到效果；先只放两个，跑通为主）
-- 情境用"订单数量 1～99"，故意不和练习题的"年龄 18～60"重复，避免提前泄露练习答案
-- ---------------------------------------------------------------------
INSERT INTO dialogue_topic (chapter_id, item_id, knowledge_point_id, sort_order, title, question, simple_question, key_points, hints, reveal_text, source_ref, source_text, transfer_question, transfer_points, transfer_reveal, created_by_ai, created_at, updated_at) VALUES
(1, 3, 1, 0, '下边界取哪些值',
 '某网上商城规定每单购买数量为 1 到 99 件。测试"下限"时，你会挑哪几个数量来测？为什么？',
 '每单最少能买几件？比它少一件、多一件分别是几件？',
 '["越过下边界的 0（应被拒绝）","正好在边界上的 1","边界内侧的 2","错误常发生在边界附近，所以要重点测"]',
 '["想想边界本身、边界外面、边界里面，各取一个值。","你只测了边界本身的话，比边界少一点的输入被错误接受了，能发现吗？","如果买 0 件，系统应该接受吗？"]',
 '取 0、1、2。0 刚越过下边界，系统应拒绝；1 正好在边界上，应接受；2 在边界内侧，应接受。错误常发生在边界附近，所以要重点测这里。',
 '课件第 13 页', '边界值分析：在边界上、边界外侧、边界内侧各取值进行测试。大量错误发生在输入或输出范围的边界上。',
 '某图书馆规定一次可以借 3 到 10 本书。测试"下限"时，你会挑哪几个数量来测？为什么？',
 '["越过下边界的 2（应被拒绝）","正好在边界上的 3","边界内侧的 4","错误常发生在边界附近，所以要重点测"]',
 '取 2、3、4。2 刚越过下边界，系统应拒绝；3 正好在边界上，应接受；4 在边界内侧，应接受。错误常发生在边界附近，所以要重点测这里。', 0, NOW(), NOW()),
(1, 3, 2, 1, '为什么要测边界附近',
 '有人说：只测一个数量的话，测 50 最好，因为它在中间、最有代表性。你觉得为什么边界附近的值反而更值得测？',
 '如果程序员把"至少 1 件"写错了，用 50 测得出来吗？',
 '["程序员常把边界条件写错，如 >= 写成 >","这类错误只有边界附近的输入才能发现","中间值发现不了这类错误"]',
 '["想想程序员写判断条件时最容易犯什么错。","把 >= 1 写成 > 1 后，分别用 50 和 1 去测，结果有什么不同？","把 >= 1 写成 > 1 后，输入 1 还能通过吗？"]',
 '程序员最常犯的错误，是把边界条件写错，比如把 >= 1 写成 > 1。这样写错后，用 50 测，两种写法结果一样，发现不了问题；只有用 1 去测，才会发现本该接受的订单被拒绝了。所以边界附近的值最容易暴露错误。',
 '课件第 13 页', '大量错误发生在输入或输出范围的边界上，而不是在范围内部。',
 '某注册页面要求密码至少 8 位，开发者把 >= 8 写成了 > 8。只用 20 位的密码测，能发现这个错误吗？为什么？应该怎么测？',
 '["20 位密码发现不了这个错误","这种写错只有边界附近才暴露","应该用正好 8 位的密码来测"]',
 '发现不了。用 20 位密码测，两种写法结果一样；这种写错只有边界附近才暴露。应该用正好 8 位的密码测，写错后它会被错误地拒绝。', 0, NOW(), NOW());

-- ---------------------------------------------------------------------
-- 学习记录示例：演示学生（5 号"学生01"）这几天学「软件测试」第 1 章的过程，打开「学习记录」就能看到效果
-- 内容项：1 阅读 / 2 练习 / 3 AI 对话；练习第 1 题答案 A，第 2 题答案 B
-- ---------------------------------------------------------------------
INSERT INTO learning_event (student_id, event_type, target_id, course_id, duration_sec, created_at) VALUES
(5, 'view_item',   1, 1, 540, TIMESTAMPADD(MINUTE, -3 * 1440 - 30, NOW())),
(5, 'finish_item', 1, 1, NULL, TIMESTAMPADD(MINUTE, -3 * 1440 - 39, NOW())),
(5, 'view_item',   2, 1, 360, TIMESTAMPADD(MINUTE, -2 * 1440 - 20, NOW())),
(5, 'view_item',   1, 1, 240, TIMESTAMPADD(MINUTE, -1440 - 60, NOW())),
(5, 'view_item',   2, 1, 300, TIMESTAMPADD(MINUTE, -1440 - 50, NOW())),
(5, 'finish_item', 2, 1, NULL, TIMESTAMPADD(MINUTE, -1440 - 45, NOW()));

-- 第一次交卷错了第 1 题（选了 B），复习后第二次全对
INSERT INTO answer_record (student_id, exercise_id, course_id, item_id, answer, is_correct, submitted_at) VALUES
(5, 1, 1, 2, 'B', 0, TIMESTAMPADD(MINUTE, -2 * 1440 - 14, NOW())),
(5, 2, 1, 2, 'B', 1, TIMESTAMPADD(MINUTE, -2 * 1440 - 14, NOW())),
(5, 1, 1, 2, 'A', 1, TIMESTAMPADD(MINUTE, -1440 - 45, NOW())),
(5, 2, 1, 2, 'B', 1, TIMESTAMPADD(MINUTE, -1440 - 45, NOW()));

-- 上面两次交卷算出来的掌握度（公式见 docs/design.md 5.1，只做了 2 次所以都乘了 2/3）：
-- 知识点 1「边界测试值的选取」先错后对：(0.8×0 + 1×1) / 1.8 × 2/3 = 0.3704，薄弱
-- 知识点 2「边界错误的成因」两次都对：1 × 2/3 = 0.6667，一般
INSERT INTO mastery (student_id, knowledge_point_id, mastery, attempt_count, correct_count, updated_at) VALUES
(5, 1, 0.3704, 2, 1, TIMESTAMPADD(MINUTE, -1440 - 45, NOW())),
(5, 2, 0.6667, 2, 2, TIMESTAMPADD(MINUTE, -1440 - 45, NOW()));

-- =====================================================================
-- 「干」演示数据（docs/计划-干预A和B.md 第 2 步）
-- 两层：概念层看 AI 对话话题结果（话题已关联知识点），应用层看掌握度。都在第 1 章知识点 1「边界测试值的选取」
--   学生02（6）：练习 3 次全错，没做对话 → 概念层未测，属于"先补测"，这次不报警
--   学生03（7）：对话自己答出，练习掌握度 0.22，两次都选"缺少越过边界的值" → A
--   学生04（8）：练习全对，对话里知识点 1 的话题系统讲解 → B
--   学生05（9）、学生06（10）：两层都好 → 不报警（对照）
--   学生07（11）：对话系统讲解，练习掌握度 0.33 → A + B
--   学生01（5）：原有数据不动，练习只做 2 次、没做对话 → 不报警
-- 题目正确答案：1 A、3 A、4 C、5 B
-- =====================================================================
INSERT INTO answer_record (student_id, exercise_id, course_id, item_id, answer, is_correct, submitted_at) VALUES
(6, 1, 1, 2, 'D', 0, TIMESTAMPADD(MINUTE, -3 * 1440 - 30, NOW())),
(6, 3, 1, NULL, 'D', 0, TIMESTAMPADD(MINUTE, -2 * 1440 - 30, NOW())),
(6, 5, 1, NULL, 'A', 0, TIMESTAMPADD(MINUTE, -2 * 1440 - 29, NOW())),
(7, 1, 1, 2, 'D', 0, TIMESTAMPADD(MINUTE, -4 * 1440 - 30, NOW())),
(7, 4, 1, NULL, 'C', 1, TIMESTAMPADD(MINUTE, -3 * 1440 - 30, NOW())),
(7, 1, 1, 2, 'D', 0, TIMESTAMPADD(MINUTE, -2 * 1440 - 30, NOW())),
(7, 3, 1, NULL, 'D', 0, TIMESTAMPADD(MINUTE, -2 * 1440 - 29, NOW())),
(8, 1, 1, 2, 'A', 1, TIMESTAMPADD(MINUTE, -3 * 1440 - 30, NOW())),
(8, 3, 1, NULL, 'A', 1, TIMESTAMPADD(MINUTE, -2 * 1440 - 30, NOW())),
(8, 4, 1, NULL, 'C', 1, TIMESTAMPADD(MINUTE, -2 * 1440 - 29, NOW())),
(9, 1, 1, 2, 'A', 1, TIMESTAMPADD(MINUTE, -3 * 1440 - 30, NOW())),
(9, 3, 1, NULL, 'A', 1, TIMESTAMPADD(MINUTE, -2 * 1440 - 30, NOW())),
(9, 4, 1, NULL, 'C', 1, TIMESTAMPADD(MINUTE, -2 * 1440 - 29, NOW())),
(10, 1, 1, 2, 'D', 0, TIMESTAMPADD(MINUTE, -4 * 1440 - 30, NOW())),
(10, 1, 1, 2, 'A', 1, TIMESTAMPADD(MINUTE, -3 * 1440 - 30, NOW())),
(10, 3, 1, NULL, 'A', 1, TIMESTAMPADD(MINUTE, -2 * 1440 - 30, NOW())),
(10, 4, 1, NULL, 'C', 1, TIMESTAMPADD(MINUTE, -2 * 1440 - 29, NOW())),
(11, 1, 1, 2, 'B', 0, TIMESTAMPADD(MINUTE, -3 * 1440 - 30, NOW())),
(11, 4, 1, NULL, 'C', 1, TIMESTAMPADD(MINUTE, -2 * 1440 - 30, NOW())),
(11, 3, 1, NULL, 'D', 0, TIMESTAMPADD(MINUTE, -2 * 1440 - 29, NOW()));

-- 掌握度按上面的作答算好（公式见 docs/design.md 5.1），例如学生03：(0.64×1) / (0.512+0.64+0.8+1) = 0.2168
INSERT INTO mastery (student_id, knowledge_point_id, mastery, attempt_count, correct_count, updated_at) VALUES
(6, 1, 0.0000, 3, 0, TIMESTAMPADD(MINUTE, -2 * 1440 - 29, NOW())),
(7, 1, 0.2168, 4, 1, TIMESTAMPADD(MINUTE, -2 * 1440 - 29, NOW())),
(8, 1, 1.0000, 3, 3, TIMESTAMPADD(MINUTE, -2 * 1440 - 29, NOW())),
(9, 1, 1.0000, 3, 3, TIMESTAMPADD(MINUTE, -2 * 1440 - 29, NOW())),
(10, 1, 0.8266, 4, 3, TIMESTAMPADD(MINUTE, -2 * 1440 - 29, NOW())),
(11, 1, 0.3279, 3, 1, TIMESTAMPADD(MINUTE, -2 * 1440 - 29, NOW()));

-- 已结束的章节对话。手动指定 id（101 起），方便下面的话题进度引用；
-- 实测 H2 会把自增计数器推到最大 id 之后，新开的对话从 106 开始编号
INSERT INTO dialogue_session (id, student_id, chapter_id, item_id, kind, status, current_idx, irrelevant_topics_in_row, created_at, updated_at) VALUES
(101, 7, 1, 3, 'CHAPTER', 'FINISHED', 2, 0, TIMESTAMPADD(MINUTE, -2 * 1440 - 10, NOW()), TIMESTAMPADD(MINUTE, -2 * 1440 - 0, NOW())),
(102, 8, 1, 3, 'CHAPTER', 'FINISHED', 2, 0, TIMESTAMPADD(MINUTE, -2 * 1440 - 10, NOW()), TIMESTAMPADD(MINUTE, -2 * 1440 - 0, NOW())),
(103, 9, 1, 3, 'CHAPTER', 'FINISHED', 2, 0, TIMESTAMPADD(MINUTE, -2 * 1440 - 10, NOW()), TIMESTAMPADD(MINUTE, -2 * 1440 - 0, NOW())),
(104, 10, 1, 3, 'CHAPTER', 'FINISHED', 2, 0, TIMESTAMPADD(MINUTE, -2 * 1440 - 10, NOW()), TIMESTAMPADD(MINUTE, -2 * 1440 - 0, NOW())),
(105, 11, 1, 3, 'CHAPTER', 'FINISHED', 2, 0, TIMESTAMPADD(MINUTE, -2 * 1440 - 10, NOW()), TIMESTAMPADD(MINUTE, -2 * 1440 - 0, NOW()));

INSERT INTO dialogue_topic_state (id, session_id, topic_idx, topic_id, wrong_count, stuck_count, irrelevant_count, hint_level, hit_points, result) VALUES
(1001, 101, 0, 1, 0, 0, 0, -1, NULL, 'SELF_CORRECT'),
(1002, 101, 1, 2, 0, 0, 0, -1, NULL, 'SELF_CORRECT'),
(1003, 102, 0, 1, 2, 0, 0, 1, NULL, 'REVEALED'),
(1004, 102, 1, 2, 1, 0, 0, 1, NULL, 'HINT_CORRECT'),
(1005, 103, 0, 1, 0, 0, 0, -1, NULL, 'SELF_CORRECT'),
(1006, 103, 1, 2, 0, 0, 0, -1, NULL, 'SELF_CORRECT'),
(1007, 104, 0, 1, 1, 0, 0, 1, NULL, 'HINT_CORRECT'),
(1008, 104, 1, 2, 0, 0, 0, -1, NULL, 'SELF_CORRECT'),
(1009, 105, 0, 1, 1, 2, 0, 1, NULL, 'REVEALED'),
(1010, 105, 1, 2, 1, 0, 0, 1, NULL, 'HINT_CORRECT');

-- 学生04 的对话记录（老师在干预页可以展开看）
INSERT INTO dialogue_message (id, session_id, topic_idx, role, content, source, judged_type, created_at) VALUES
(1001, 102, 0, 'AI', '我们开始吧。先来讨论「下边界取哪些值」。', 'RULE', NULL, TIMESTAMPADD(MINUTE, -2 * 1440 - 10, NOW())),
(1002, 102, 0, 'STUDENT', '测 50 吧，中间的数最有代表性', NULL, 'WRONG', TIMESTAMPADD(MINUTE, -2 * 1440 - 9, NOW())),
(1003, 102, 0, 'AI', '想想边界本身、边界外面、边界里面，各取一个值。', 'TEMPLATE', NULL, TIMESTAMPADD(MINUTE, -2 * 1440 - 9, NOW())),
(1004, 102, 0, 'STUDENT', '1 和 99', NULL, 'WRONG', TIMESTAMPADD(MINUTE, -2 * 1440 - 8, NOW())),
(1005, 102, 0, 'AI', '我们一起来看：取 0、1、2。0 刚越过下边界，系统应拒绝；1 正好在边界上，应接受；2 在边界内侧，应接受。', 'TEMPLATE', NULL, TIMESTAMPADD(MINUTE, -2 * 1440 - 8, NOW()));
