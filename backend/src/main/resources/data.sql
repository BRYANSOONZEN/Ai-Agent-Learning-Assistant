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
