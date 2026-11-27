# 数据库说明（MySQL）

> 软件工程智能学习平台 · 第 2 组 · 2026-11-27
> 这份文档把数据库的每一样东西都写清楚：数据存在哪些地方（第一节）、表怎么设计（第二节）、后端怎么调用（第三节）、JSON 列里面的结构（第四节）、怎么搬数据和备份（第五节）、换到 MySQL 时修好的问题（第六节）、**33 张表逐张的详细说明（第七节）**。
> 例子都是在本机 MySQL 里查出来的真实数据。第七节由 `tools/docs/gen_db_report.py` 从建表语句、后端源码和 MySQL 直接生成，重新生成：`python tools/docs/build_docs.py`。

---

## 一、所有存数据的地方

平台的数据存在下面 **7 个地方**。真正的业务数据只在 ① MySQL 里；②③ 是另外两个数据库（开发、测试用）；④⑤ 是磁盘上的文件；⑥ 是每个用户浏览器里的小东西；⑦ 是连接数据库要用的配置。

| # | 存在哪里 | 是什么 | 谁在用 | 里面有什么 |
|---|---|---|---|---|
| ① | **MySQL 8.4 库 `se_platform`** | 主数据库 | 网站正常运行（演示、录视频、答辩） | 全部业务数据，33 张表 |
| ② | H2 文件库 `backend/data/platform.mv.db`（约 3.5 MB） | 开发用的数据库 | 不加 `mysql` 配置启动后端时 | 11-27 搬到 MySQL 那一刻的数据，之后不再更新 |
| ③ | H2 内存库（只在内存里） | 测试用的数据库 | 跑自动测试和 CI 时，每个测试类一个 | 只有 `data.sql` 的演示数据，测试结束就没了 |
| ④ | 磁盘文件夹 `backend/uploads/年/月/`（现在 537 个文件，约 1.4 GB） | 资料文件本身 | 后端 | 老师上传的视频、课件 PDF、PPT、Word；PPT、Word 转好的 PDF（原文件名后加 `.pdf`） |
| ⑤ | 磁盘文件夹 `tools/material_kp/.cache/` | 资料处理服务的缓存 | Python 资料处理服务 | 每份处理过的文件一个 JSON：转出的文字和统计；按文件内容的 SHA-256 命名，同一个文件再传就直接用，不再花时间和费用 |
| ⑥ | 用户浏览器的 localStorage | 每个人自己浏览器里的小东西 | 前端 | 登录令牌、本地学习进度副本、读过的消息，见 1.6 |
| ⑦ | 配置文件 | 连接数据库用的地址、账号 | 后端启动时读 | 见 1.7 |

### 1.1 ① MySQL 主库

| 项目 | 内容 |
|---|---|
| 版本 | MySQL 8.4.11 社区版（LTS），免安装 ZIP 版 |
| 程序位置 | `C:\ZI\mysql\mysql-8.4.11-winx64\`（不在 Git 仓库里，删掉这个文件夹就卸载了，不改系统） |
| 数据文件位置 | `C:\ZI\mysql\data\`（InnoDB 的数据文件、日志都在这里） |
| 配置文件 | `C:\ZI\mysql\my.ini`：端口 3306；只监听 `127.0.0.1`（别的电脑连不进来）；字符集 `utf8mb4`、排序规则 `utf8mb4_0900_ai_ci`；时区 `+08:00`；表名不区分大小写 |
| 库 | `se_platform`，33 张表，存储引擎 InnoDB（支持事务、行锁，崩溃后能恢复） |
| 账号 | `root`：只用来管理（建库、建账号、备份）。`se_app`：后端用的账号，**只有 `se_platform` 这一个库的权限**，看不到也改不了别的库 |
| 密码放在哪 | 两个密码都是随机生成的：`C:\ZI\mysql\本机密码.txt`（仓库外）；后端用的 `se_app` 密码另外写在 `backend/src/main/resources/application-local.yml`（这个文件被 `.gitignore` 排除，不会提交）。代码、提交记录、文档里都没有密码 |
| 启动 MySQL | `C:\ZI\mysql\mysql-8.4.11-winx64\bin\mysqld.exe --defaults-file=C:\ZI\mysql\my.ini --console`（或 Claude 启动项 `se-mysql`） |
| 后端连 MySQL | 在 `backend` 里 `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=mysql"`（或启动项 `se-backend-mysql`，端口 8080） |
| 自己连上去看 | `C:\ZI\mysql\mysql-8.4.11-winx64\bin\mysql.exe -u root -p -h 127.0.0.1 --default-character-set=utf8mb4 se_platform`，然后 `SHOW TABLES;`、`DESC mastery;`、`SELECT * FROM chapter_kp;` |
| 空库第一次启动 | 后端自动执行 `schema.sql`（建 33 张表）和 `data.sql`（演示数据） |
| 已有的库再启动 | 不清空；`schema.sql` 里有、库里没有的表补建出来；`schema-upgrade.sql` 里的新列，库里没有才加（`KeepDataConfig`） |
| 现在的数据 | 11-27 从 H2 开发库整体搬过来（29 张表、664 行），之后的新数据都写在这里 |

### 1.2 ② H2 文件库

- 文件：`backend/data/platform.mv.db`（被 `.gitignore` 排除）。连接：`jdbc:h2:file:./data/platform;MODE=MySQL;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=USER;AUTO_SERVER=TRUE`，账号 `sa`、空密码。
- 用的是和 MySQL **同一份** `schema.sql`，MySQL 兼容模式，表结构一样。
- 11-27 数据搬到 MySQL 以后就不再用了；如果不加 `mysql` 配置启动后端，用的就是它，看不到 11-27 以后的新数据。
- 用这个库启动时，可以在浏览器打开 `http://localhost:8080/h2-console` 直接看表（连 MySQL 时关掉了）。

### 1.3 ③ H2 内存库（测试）

- 连接：`jdbc:h2:mem:platform;…`（`backend/src/test/resources/config/application.yml`）。
- 每个测试类启动时建表、放演示数据，测试之间互不影响；测试结束就没了。160 个自动测试和 GitHub 上的 CI 都用它，不需要装 MySQL。

### 1.4 ④ 资料文件

- 老师上传的文件存成 `backend/uploads/2026/11/<随机 uuid>.<扩展名>`，用随机名字，避免重名、也避免别人猜到文件名。
- 数据库 `material` 表只存这个相对路径、原文件名、大小、类型；学生和老师通过 `GET /api/materials/{id}/file` 取文件（要登录，视频支持拖动进度条）。
- PPT、Word 用本机 Office 转好的 PDF 放在原文件旁边：`<uuid>.pptx.pdf`，通过 `…/file?pdf=true` 取。
- 替换或删除资料时，数据库里的 `material` 记录是逻辑删除，磁盘文件先保留（可以恢复）。

### 1.5 ⑤ 资料处理服务的缓存

- 位置 `tools/material_kp/.cache/`（被 `.gitignore` 排除），文件名 = 文件内容的 SHA-256 + 类型，例如 `e87f…-pdf-img.json`、`3942…-video.json`。
- 内容：转出来的每一段文字（页码 / 起止秒）和统计（页数、图片数、耗时、看图用的 token）。
- 作用：同一个文件再上传（或重新处理）时直接用缓存，不再跑语音识别、不再花钱看图。删掉缓存不影响数据库里已经存好的文字。

### 1.6 ⑥ 浏览器里存的东西（localStorage）

这些只在用户自己的浏览器里，只是为了方便，丢了不影响任何数据（服务器上都有）。

| 键 | 存什么 | 丢了会怎样 |
|---|---|---|
| `se-platform-user` | 登录令牌和当前用户（id、名字、角色、要不要先改密码） | 要重新登录 |
| `se-platform-learn-done:<用户 id>` | 学过哪些内容项的本地副本（首页、课程列表显示进度用；打开学习页时以服务器为准覆盖） | 首页进度先显示 0，打开课程后恢复 |
| `se-platform-learn-last:<用户 id>` | 上次学到哪里（首页“接着上次学习”） | 首页不显示“接着上次学习” |
| `se-messages-read-<用户 id>` | 消息铃铛里哪些消息已读 | 消息重新显示成未读 |
| `se-assistant-seen` | 小助手里哪些新内容已经看过（角标数字用） | 角标重新出现 |
| `se-platform-dialogue-session:<章>:<项>` | 旧版 AI 对话正在进行的会话 id（刷新后接着聊） | 旧版对话从最近一次接着显示 |

### 1.7 ⑦ 连接数据库的配置

| 文件 | 内容 | 进不进 Git |
|---|---|---|
| `backend/src/main/resources/application.yml` | 默认连 H2 文件库；MyBatis-Plus 设置（列名转驼峰、逻辑删除字段、打印 SQL） | 进 |
| `backend/src/main/resources/application-mysql.yml` | 加 `mysql` 配置时用：MySQL 地址 `jdbc:mysql://127.0.0.1:3306/se_platform`、账号 `se_app`、密码从环境变量 `MYSQL_PASSWORD` 或 `application-local.yml` 读 | 进（里面没有密码） |
| `backend/src/main/resources/application-local.yml` | 本机私有：大模型 API key、MySQL 密码 | **不进**（`.gitignore`） |
| `backend/src/test/resources/config/application.yml` | 测试：H2 内存库 | 进 |
| `C:\ZI\mysql\my.ini` | MySQL 服务器自己的配置 | 不在仓库里 |

---

## 二、怎么存：表的设计

### 2.1 33 张表，按用途分组

| 用途 | 表 |
|---|---|
| 账号 | `user` |
| 课程内容 | `course` → `chapter` → `chapter_item`（视频 / 文档 / 练习 / AI 对话）；`material`（文件信息）；`knowledge_point`；`exercise`、`exercise_knowledge_point`；`enrollment`（选课） |
| 资料 → 文字 → 知识点 | `material_job`（转文字进度）、`material_text`（转出的文字，一段一行）、`chapter_kp_state`、`chapter_kp`（识别出的知识点） |
| AI 对话 | `feynman_setup`（老师确认的草稿）、`feynman_session`（一次对话及进度）、`feynman_message`（每一句）；旧版 `dialogue_*` 4 张 |
| 学习记录 | `learning_event`（看了什么、学完什么）、`item_progress`（视频看过的时间段、文档阅读秒数）、`answer_record`（每一次作答） |
| 诊断 / 画像 | `mastery`（每人每知识点一行）、`profile_snapshot`（学完时的画像快照） |
| 干预 / 小助手 | `item_analysis`、`class_issue`、`class_push`（全班分析和推送）；`remedy_round`（补练、拓展）、`assistant_message`（聊天）、`assistant_notice`（提醒）、`recommendation` |
| 运维 | `ai_call_log`（每次调用大模型的 token、耗时、费用） |

### 2.2 几条设计约定（为什么这样存）

| 约定 | 例子 | 为什么 |
|---|---|---|
| 每张表 `id BIGINT AUTO_INCREMENT` 主键 | — | 简单、插入快 |
| **不建外键约束**，引用关系写在列注释里 | `chapter.course_id → course.id` | H2 和 MySQL 两边都要能跑；删除时由业务代码决定是一起删还是拒绝 |
| “每人每项一行”用唯一约束保证 | `mastery (student_id, knowledge_point_id)`、`enrollment (course_id, student_id)`、`item_progress (student_id, item_id)` | 并发时也不会出现两行；查询直接走这个索引 |
| 结构化的内容存成 JSON 字符串（`TEXT`） | `exercise.options = ["8、12、16","7、8、16、17",…]`；`chapter_kp.key_points = [{"text":"…","sources":["D1-P4"]}]`；`feynman_session.state` 存整段对话的进度 | 这些内容总是整块读、整块写，不需要按里面的字段查；表结构简单 |
| 记录只增不改 | `answer_record` 每次作答新增一行；`learning_event` 每个行为一行 | 掌握度、画像都从记录重算，算法改了可以用原始记录重新算一遍 |
| 能现算的不存 | 解锁状态、画像四维、全班分析的大部分数字 | 避免和记录不一致；唯一例外是“学完时的画像快照”，因为它就是要固定下来 |
| 逻辑删除 | `deleted = 1` | 误删可以恢复；查询时 MyBatis-Plus 自动加 `deleted = 0` |
| 时间列自动填 | `created_at`、`updated_at` | MyBatis-Plus 插入、更新时自动填 |

### 2.3 例子：`mastery` 表在 MySQL 里长什么样

```sql
CREATE TABLE `mastery` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `student_id` bigint NOT NULL,
  `knowledge_point_id` bigint NOT NULL,
  `mastery` decimal(5,4) NOT NULL,          -- 当前掌握度
  `attempt_count` int NOT NULL,
  `correct_count` int NOT NULL,
  `first_mastery` decimal(5,4) DEFAULT NULL, -- 原始掌握度 F：练习里每道题第一次作答的平均分，之后不改
  `first_count` int NOT NULL DEFAULT '0',
  `remedy` decimal(5,4) DEFAULT NULL,        -- 补练 R
  `remedy_count` int NOT NULL DEFAULT '0',
  `remedy_status` varchar(16) DEFAULT NULL,  -- recovered 已补回来 / practicing 补练中
  `updated_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_mastery` (`student_id`,`knowledge_point_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
```

学生 08（id 12）在第 6 章的 7 个知识点（真实数据）：

| knowledge_point_id | first_mastery | remedy | mastery | remedy_status | 页面上的颜色 |
|---|---|---|---|---|---|
| 3 | 0.9167 | — | 0.9167 | — | 绿（F ≥ 0.8） |
| 4 | 1.0000 | — | 1.0000 | — | 绿 |
| 5 | 0.0833 | 1.0000 | 1.0000 | recovered | 橙（第一次没掌握，补练补回来了） |
| 6 | 0.6667 | — | 0.6667 | — | 红（还没补回来） |
| 7 | 0.6667 | — | 0.6667 | — | 红 |
| 8 | 1.0000 | — | 1.0000 | — | 绿 |
| 9 | 1.0000 | — | 1.0000 | — | 绿 |

颜色不存数据库，由 `MasteryService.color()` 按 `first_mastery` 和 `remedy_status` 现算，所以练习、小助手、画像、老师端看到的颜色永远一致。

### 2.4 例子：资料转出来的文字（`material_text`）

| id | item_id | seg_key | kind | page | start_sec | content（前 30 字） |
|---|---|---|---|---|---|---|
| 1 | 4 | P2 | pdf | 2 | — | 自动化测试概述：智能测试概述智能测试四大优化环节… |
| 2 | 4 | P4 | pdf | 4 | — | 自动化测试概述：自动化测试：把以人为驱动的测试行为… |

`seg_key` 就是出处编号：知识点的每条要点都带着这些编号，学生点“课件第 4 页”时，后端用 `item_id + page` 找到要打开的文件和页码；视频段落用 `start_sec` 跳到那一秒。视频同音字纠正改过的段落，改之前的原文存在 `original` 列。

---

## 三、怎么调用：从页面到 SQL

### 3.1 分层

```mermaid
flowchart LR
    V[页面 Vue] -->|HTTP + JWT| C[Controller]
    C --> S[Service：业务规则]
    S --> M[Mapper：MyBatis-Plus]
    M -->|JDBC 连接池 HikariCP| DB[(MySQL se_platform)]
```

- **Mapper**：每张表一个接口，继承 MyBatis-Plus 的 `BaseMapper<实体>`，增删改查不用手写 SQL，例如 `MasteryMapper extends BaseMapper<Mastery>`。
- **实体**：一张表一个类，字段和列一一对应（`student_id` ↔ `studentId`，自动转换），例如 `Mastery`、`AnswerRecord`。
- **查询条件**用 `LambdaQueryWrapper` 写，编译时就能检查字段名，例如：

```java
Mastery m = masteryMapper.selectOne(new LambdaQueryWrapper<Mastery>()
        .eq(Mastery::getStudentId, studentId)
        .eq(Mastery::getKnowledgePointId, kpId));
```

MyBatis-Plus 把它变成（后端日志里能看到实际执行的 SQL）：

```sql
SELECT id,student_id,knowledge_point_id,mastery,attempt_count,correct_count,first_mastery,first_count,
       remedy,remedy_count,remedy_status,updated_at
FROM mastery WHERE (student_id = ? AND knowledge_point_id = ?)
```

`?` 是参数占位：值由 JDBC 单独传，不拼进 SQL，所以不会被 SQL 注入。这条查询正好用上唯一索引 `uk_mastery`。

- **连接**：Spring Boot 的 HikariCP 连接池，启动时建好一批连接，请求来了借一个、用完还回去。
- **事务**：一次请求里要一起成功或一起失败的写操作（例如交卷：写作答记录 + 更新掌握度）放在 `@Transactional` 方法里。

### 3.2 例子：学生交卷时，数据库发生了什么

`POST /api/learn/items/65/submit` → `LearnService.submit`：

| 步 | 做什么 | SQL（简化） |
|---|---|---|
| 1 | 检查截止时间、选课、解锁（本章 AI 对话做完没有） | `SELECT … FROM chapter_item WHERE id=?`；`SELECT COUNT(*) FROM enrollment WHERE student_id=? AND course_id=?`；“学完”从三处查：`learning_event`（finish_item）、`answer_record`、`dialogue_session`（已结束） |
| 2 | 取这次练习的题和答案 | `SELECT … FROM exercise WHERE item_id=? AND draft=0` |
| 3 | 判分：客观题程序判；简答先程序判，再调大模型判两次，每次调用写一行日志 | `INSERT INTO ai_call_log (task, input_tokens, output_tokens, cost_cny, latency_ms, …) VALUES (…)` |
| 4 | 每道题存一行作答（交过卷本身就算这一项学完，不另外写“学完”） | `INSERT INTO answer_record (student_id, exercise_id, course_id, item_id, answer, is_correct, score, status, submitted_at, …) VALUES (…)` |
| 5 | 重算涉及的知识点：只取每道题第一次作答 | `SELECT … FROM answer_record WHERE student_id=? AND exercise_id IN (…) ORDER BY submitted_at` |
| 6 | 更新或新建掌握度那一行 | `UPDATE mastery SET first_mastery=?, mastery=?, … WHERE id=?`（没有就 `INSERT`，唯一约束保证一人一知识点一行） |

实际执行的 SQL 都会打在后端日志里（`application.yml` 里开了 MyBatis 的 SQL 日志），例如学生打开“学情诊断”时：

```text
==>  Preparing: SELECT id,student_id,knowledge_point_id,mastery,attempt_count,correct_count,first_mastery,first_count,remedy,remedy_count,remedy_status,updated_at FROM mastery WHERE (student_id = ?)
==> Parameters: 12(Long)
```

### 3.3 例子：老师上传课件，到识别出知识点

| 步 | 表 |
|---|---|
| 上传文件 | 文件写磁盘；`INSERT material`；`UPDATE chapter_item SET material_id=?`；`INSERT material_job (status='queued')` |
| 后台转文字 | `UPDATE material_job SET status='processing'` → 每段 `INSERT material_text` → `UPDATE material_job SET status='done'` |
| 老师点“上传完毕”、都转完了 | `UPDATE chapter_kp_state SET status='recognizing'`；视频同音字纠正 `UPDATE material_text SET content=?, original=?`；识别 `INSERT chapter_kp`（每个知识点一行，要点和出处是 JSON）；`UPDATE chapter_kp_state SET status='draft'` |
| 老师确认 | `INSERT/UPDATE knowledge_point`；`UPDATE chapter_kp SET knowledge_point_id=?`；`UPDATE chapter_kp_state SET status='confirmed'` |

---

## 四、JSON 列里面存的是什么

有些内容总是整块读、整块写，不需要按里面的字段查，就存成 JSON 字符串（`TEXT` 列）。下面每一个 JSON 列的结构都写出来，例子是 MySQL 里的真实数据（太长的截断了）。

### 4.1 `chapter_kp.key_points`：知识点的要点（每条带出处）

```json
[{"text": "自动化测试是把以人为驱动的测试行为转化为机器执行的过程，用工具、脚本或代码控制执行并比较实际结果与预期结果。",
  "sources": ["D1-P4", "V1-02"]},
 {"text": "自动化测试的核心价值是解决测试执行中的机械重复问题，……", "sources": ["D1-P4", "V1-02", "V1-03"]}]
```

- `text`：要点原文（老师确认后就是 AI 对话和判分的标准答案）；每个知识点最多 3 条。
- `sources`：出处编号，对应 `material_text.seg_key`：`D1-P4` = 第 1 份文档第 4 页，`D1-P13-图1` = 第 13 页的第 1 张图，`V1-02` = 第 1 个视频第 2 段。识别时程序核对，编出来的编号会被去掉。

### 4.2 `chapter_kp.misconceptions`、`chapter_kp.sources`

```json
["认为自动化测试可以完全脱离人的参与，不需要测试人员定义测试逻辑。", "认为自动化测试覆盖度天然比手工测试高，……"]
["D1-P4", "V1-02", "V1-03", "D1-P5", "D1-P6", "V1-04", "V1-05"]
```

常见误解（字符串数组，出题时用来写错误选项）；这个知识点所有出处的合集。

### 4.3 `exercise` 表里的 JSON 列

| 列 | 结构 | 例子 |
|---|---|---|
| `options` | 选项数组，按顺序对应 A、B、C、D | `["17、18、19", "18、30、60", "0、18、100", "18、19、20"]` |
| `option_notes` | 和选项一一对应：选了这个错误选项说明有什么误解；正确选项为空串 | `["", "把上下边界和中间值混在一起，没有取边界外侧的值", …]` |
| `reasons` | 两段式单选的“理由”选项数组 | `["脚本对界面元素的微小变更极其敏感，……", "脚本编写高度依赖编程能力与经验，……", …]` |
| `reason_answer` | 两段式单选的正确理由（一个字母，不是 JSON） | `A` |
| `key_points` | 简答题的得分要点数组（判分时逐条判） | `["指出自动化测试的核心价值是解决执行中的机械重复问题，故执行次数高", …]` |
| `reference` | 简答题参考答案（普通文字，不是 JSON） | `自动化测试的核心价值在于……` |
| `accepts` | 填空题可以接受的答案数组（判分时去掉空格、标点，大小写不分后比对） | 格式：`["写法一", "写法二"]`（现在库里没有填空题，没有真实例子） |

### 4.4 `feynman_setup.drafts`：AI 对话的草稿（每个知识点一份）

```json
[{"kpId": 3, "name": "智能测试四大优化环节",
  "keyPoints": ["四大优化环节是测试用例生成、……", "测试用例生成环节自动分析需求、代码与历史数据，……", …],
  "pointers": ["《课件：智能测试生成与优化》第 11 页、视频《视频 6-2 智能测试》的 00:30、……", …],
  "misconceptions": ["认为智能测试只优化测试用例生成这一个环节。", …],
  "draft": {"scene": {…}, "misconception_probe": {…},
            "key_points": [{"no": 1, "understood_if": "…", "partial_if": "…", "L1": "…", "L2": "…", "L3": "…", "pointer": "…"}, …],
            "recap": "…"},
  "checks": [{"name": "每条要点都有草稿", "ok": true, "detail": "4/4"}, …]}]
```

- `keyPoints`、`misconceptions`：生成草稿时那一刻的要点原文和误解（之后知识点改了，程序比对发现不一致，就提示草稿过期）。
- `pointers`：每条要点讲不出时让学生回看的位置（从出处编号翻译成“课件第几页、视频几分几秒”）。
- `draft`：大模型起草、老师确认的内容：场景和 AI 同学（`scene`）、带着误解的一句话（`misconception_probe`）、**每条要点一组**判断标准（`understood_if` 讲清楚、`partial_if` 不完整）和三级提示（`L1`、`L2`、`L3`）、讲不出时的指引（`pointer`）、结束时复述要覆盖的内容（`recap`）。
- `checks`：程序检查 4 项的结果（每条要点都有、各项都写了、每句不超过 80 字、提示没照抄要点原文）。

### 4.5 `feynman_session.state`：一次 AI 对话的全部进度

```json
{"idx": 0,
 "kps": [{"kpId": 3, "name": "智能测试四大优化环节", "phase": "done", "turns": 10, "attempts": 8,
          "points": [{"status": "clear", "level": 1, "how": "hint1", "quote": "生成、执行优化、缺陷检测修复、报告分析四个环节，……", "exhausted": false},
                     {"status": "partial", "level": 2, "how": "supplemented", "quote": "页面改了按钮位置，它能自己重新定位元素，叫自愈", "exhausted": false}, …],
          "target": 2, "misconAsked": true, "misconFixed": true, "attempted": true,
          "review": "《课件：……》第 11 页、……", "statusSummary": "「智能测试四大优化环节」小结：……"}]}
```

- `idx`：现在在讲第几个知识点；`kps`：每个知识点一份。
- `phase`：talk 讲解中 / done 结束；`turns`：学生说了几句（满 10 句自动结束）；`attempts`：认真尝试了几次（≥ 3 次才算“认真尝试过”，没讲清的要点才补充）。
- `points`：每条要点：`status`（clear 讲清 / partial 不完整 / wrong 讲错 / none 没讲到）、`level`（现在给到第几级提示，0 = 还没给）、`how`（怎么讲清的：self 自己 / hint1～3 第几级提示后 / pointer 看资料后 / supplemented 补充后才知道）、`quote`（学生原话，程序核对过真的说过）、`exhausted`（三级提示都用完了）。
- `target`：现在追问第几条要点；`misconAsked` / `misconFixed`：问过误解没有、学生有没有指出误解错在哪。
- `review`：建议回看的资料；`statusSummary`：学习情况小结（不给学生看，给小助手和画像用）。

### 4.6 `feynman_message.judgment`：大模型对学生每一句的判断

```json
{"kind": "explain", "attempt": true,
 "points": [{"no": 1, "status": "partial", "quote": "应该是四个环节吧：先自动生成测试用例，然后自动执行，最后出测试报告"},
            {"no": 2, "status": "none", "quote": ""}, …],
 "misconception_fixed": false, "ack": "哦，那这四个环节是怎么串起来的呀？"}
```

`kind`：explain 在讲解 / question 提问 / stuck 卡住 / chat 闲聊；`attempt`：是不是认真在讲；`points`：每条要点的判断和摘录的原话；`ack`：AI 同学的短反应。存下来是为了复核：程序按这个判断更新 `feynman_session.state`。

### 4.7 其他 JSON 列

| 列 | 结构 | 例子 |
|---|---|---|
| `item_progress.watched_ranges` | 视频真正播放过的时间段（秒），合并后的数组 | `[[0, 12.5], [14, 20]]`：快进跳过的 12.5～14 秒不算 |
| `answer_record.detail` | 简答每条要点的判定：要点序号、内容、答到没有、学生原话、两次判分是否一致、是否老师确认 | `[{"no": 1, "text": "指出……", "hit": true, "quote": "自动化测试主要是把重复执行的工作交给机器，……", "agreed": true, "teacher": true}, …]` |
| `material_job.meta` | 资料处理服务返回的统计 | `{"pages": 39, "skipped_pages": [1, 3, 8, 10, 20], "text_sec": 0.2, "images": 29, "images_failed": [], "image_sec": 209.4, "image_input_tokens": 23408, "image_output_tokens": 34231}` |
| `profile_snapshot.data` | 学完时的课程画像：参考日 `refDate` 和四维 `dimensions`（每一维：`key`、`label`、`value`、`numerator`、`denominator`、`formula`、`source`、`reason`） | 现在还没有学生学完整门课，表里没有数据；格式见 `ProfileDtos.Snapshot` |
| `course.modules`、`*_ids` 列 | 不是 JSON，是逗号分隔的字符串 | `video,document,knowledge,dialogue,exercise`；`remedy_round.exercise_ids = "101,102,103"` |

---

## 五、从 H2 搬到 MySQL、备份、恢复

| 操作 | 怎么做 |
|---|---|
| H2 → MySQL | ① 用 H2 自带工具把开发库导出成 SQL（`SCRIPT SIMPLE COLUMNS`）；② `python tools/db/h2_to_mysql.py h2dump.sql mysql_cols.tsv out.sql`：转成 MySQL 的写法（`"表"` → `` `表` ``、H2 的 Unicode 转义还原成中文、跳过已删掉的旧表）；③ 在 MySQL 里执行 `out.sql`（先清空再导入，用 `NO_BACKSLASH_ESCAPES` 保证 JSON 里的反斜杠原样存）。本机已经做过一次：29 张表、664 行全部导入，中文和 JSON 都正常 |
| 备份 | `mysqldump -u root -p --default-character-set=utf8mb4 se_platform > backup.sql` |
| 恢复 | `mysql -u root -p se_platform < backup.sql` |
| 回到干净的演示数据 | `DROP DATABASE se_platform; CREATE DATABASE se_platform DEFAULT CHARACTER SET utf8mb4;`，再启动后端（自动建表、放演示数据） |

---

## 六、换到 MySQL 时发现并修好的问题

| 问题 | 原因 | 怎么修的 |
|---|---|---|
| 已有的 MySQL 库重启时报“升级数据库失败” | 升级脚本用的 `ADD COLUMN IF NOT EXISTS` 是 H2 的写法，MySQL 不支持 | `KeepDataConfig` 认出是 MySQL 时，先查这一列在不在，不在才加 |
| 判断“表在不在”可能看错库 | 原来查所有库的 `INFORMATION_SCHEMA`，MySQL 自带的系统库里也有一张 `user` 表 | 改成只看当前连接的库（JDBC 元数据），H2 上也有测试（`KeepDataConfigTest`） |
| 上传资料后转文字报“资料不存在” | “上传后排队”放在事务提交后的回调里做，那时连接还没切回自动提交，排队记录要等连接清理时才真正写进去；后台线程抢先去读，读到了已删除的旧文件。H2 碰巧赶得上，MySQL（默认隔离级别可重复读）就读不到 | 提交后的这一步交给单独的线程、用新的连接做，写完立即生效（`MaterialPipeline.afterCommit`） |

---

## 七、33 张表逐张说明（自动生成）

每张表写：属于哪个模块、用途、什么时候写入、和哪些表关联、主键 / 唯一约束 / 索引、**哪些代码写它、哪些代码读它**（扫描后端源码里每个 Mapper 的调用，写的是类名）、现在有几行、每一列的类型和含义、一行真实数据（密码哈希打码）。

<!-- 以下由 tools/docs/gen_db_report.py 生成，重新生成：python tools/docs/build_docs.py -->

共 33 张表。每张表的“谁写 / 谁读”是扫描后端源码里每个 Mapper 的调用得出的（类名）；“现在的数据”来自本机 MySQL `se_platform`。

### `user`（账号）

- **用途**：所有登录账号：学生、老师、管理员。
- **什么时候写入**：学生注册时、管理员开通老师 / 学生账号时新增；改密码、禁用、退出登录（令牌版本 +1）时修改。
- **和哪些表关联**：被几乎所有表的 student_id / teacher_id / uploaded_by 引用。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；CONSTRAINT uk_user_username UNIQUE (username)（登录名不能重复）
- **谁写**：`AdminUserService`（修改、新增）；`AuthService`（修改、新增）
- **谁读**：`AdminUserService`、`AuthInterceptor`、`AuthService`、`ClassPushService`、`CourseService（CourseMapper.findUserName 手写 SQL）`、`DiagnosisService（CourseMapper.findUserName 手写 SQL）`、`EnrollmentService`、`ExerciseReviewService`
- **现在的数据**：15 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `username` | VARCHAR(32) | NOT NULL | 登录名 |
| `password_hash` | VARCHAR(100) | NOT NULL | BCrypt 哈希，不存明文 |
| `name` | VARCHAR(32) | NOT NULL | 显示姓名 |
| `role` | VARCHAR(16) | NOT NULL | student / teacher / admin |
| `clazz_id` | BIGINT |  | 学生所在班级（班级表以后再建） |
| `status` | TINYINT | NOT NULL DEFAULT 1 | 1 正常，0 禁用 |
| `token_version` | INT | NOT NULL DEFAULT 0 | 令牌版本：退出登录、改密码、管理员重置密码时加 1，旧令牌立即失效 |
| `must_change_password` | TINYINT | NOT NULL DEFAULT 0 | 1 = 管理员开的账号或重置过的密码，第一次登录要先改密码 |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME |  | 这一行最后修改的时间（MyBatis-Plus 自动填） |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除：1 = 已删除 |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `username` | admin |
| `password_hash` | （BCrypt 哈希，打码） |
| `name` | 管理员 |
| `role` | admin |
| `clazz_id` | NULL |
| `status` | 1 |
| `token_version` | 0 |
| `must_change_password` | 0 |
| `created_at` | 2026-11-24 21:30:08 |
| `updated_at` | 2026-11-24 21:30:08 |
| `deleted` | 0 |

### `course`（课程内容）

- **用途**：一门课：课程名、简介、授课老师、状态（草稿 / 已发布 / 已归档）、开了哪些模块。
- **什么时候写入**：老师新建、编辑、发布课程时。
- **和哪些表关联**：teacher_id → user；下面挂 chapter、chapter_item、knowledge_point、exercise、enrollment。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_course_teacher（teacher_id）
- **谁写**：`CourseService`（修改、删除、新增）
- **谁读**：`AssistantChatService`、`AssistantService`、`ClassPushService`、`CourseProfileService`、`CourseService`、`DeadlineService`、`DiagnosisService`、`EnrollmentService`、`ExerciseGenService`、`FeynmanDraftService`、`KpRecognizer`、`TeacherTodoService`
- **现在的数据**：6 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `name` | VARCHAR(100) | NOT NULL | 课程名（同一位老师的课程不能重名，业务层检查） |
| `description` | TEXT |  | 简介 |
| `teacher_id` | BIGINT | NOT NULL | 授课教师 → user.id（不建外键约束，由代码保证） |
| `status` | VARCHAR(16) | NOT NULL DEFAULT 'draft' | draft 草稿 / published 已发布 / archived 已归档 老师建课时选的模块，逗号分隔：video 视频 / document 文档 / knowledge 知识点 / dialogue AI 对话 / exercise 题目 |
| `modules` | VARCHAR(100) | NOT NULL DEFAULT 'video,document,knowledge,dialogue,exercise' | 这门课开了哪些模块，逗号分隔：video,document,knowledge,exercise,dialogue |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME |  | 这一行最后修改的时间（MyBatis-Plus 自动填） |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除：0 正常，1 已删除（查询时自动过滤） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `name` | 软件测试 |
| `description` | 等价类划分、边界值分析等黑盒测试方法，以及白盒测试与自动化测试。 |
| `teacher_id` | 2 |
| `status` | published |
| `modules` | video,document,knowledge,dialogue,exercise |
| `created_at` | 2026-09-01 09:00:00 |
| `updated_at` | 2026-09-01 09:00:00 |
| `deleted` | 0 |

### `chapter`（课程内容）

- **用途**：课程里的一章：标题、章节介绍、状态、顺序。
- **什么时候写入**：老师在建课页新建、编辑、发布章节时；发布前后端检查章节完整。
- **和哪些表关联**：course_id → course；下面挂 chapter_item、knowledge_point、chapter_kp。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_chapter_course（course_id）
- **谁写**：`ChapterService`（修改、删除、新增）
- **谁读**：`AssistantChatService`、`AssistantService`、`ChapterKpService`、`ChapterService`、`ClassAnalysisService`、`CourseProfileService`、`CourseService`、`DeadlineService`、`DiagnosisService`、`KpRecognizer`、`LearnService`、`ProgressService`、`RecommendService`、`RecordService`、`TeacherTodoService`
- **现在的数据**：4 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `course_id` | BIGINT | NOT NULL | 属于哪门课 → course.id |
| `parent_id` | BIGINT |  | 预留：NULL = 章；首版只做一级 |
| `title` | VARCHAR(100) | NOT NULL | 标题（页面上显示的名字） |
| `intro` | TEXT |  | 章节介绍（学习目标 + 概要），学生进入本章先看到 |
| `status` | VARCHAR(16) | NOT NULL DEFAULT 'draft' | draft 草稿 / published 已发布（学生可见） |
| `sort_order` | INT | NOT NULL DEFAULT 0 | 顺序号，从 0 开始，越小越靠前（新加的取“最大顺序号 + 1”） |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME |  | 这一行最后修改的时间（MyBatis-Plus 自动填） |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除：0 正常，1 已删除（查询时自动过滤） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `course_id` | 1 |
| `parent_id` | NULL |
| `title` | 第 1 章 边界值分析 |
| `intro` | 学习目标：能说出边界值分析在边界上、边界外侧、边界内侧各取什么值，并解释为什么边界附近更容易发现错误。 概要：大量错误发生在输入范围的边界上。本章用订单数量、年… |
| `status` | published |
| `sort_order` | 0 |
| `created_at` | 2026-11-24 21:30:08 |
| `updated_at` | 2026-11-24 21:30:08 |
| `deleted` | 0 |

### `chapter_item`（课程内容）

- **用途**：章节里的一项内容：视频、文档、练习、AI 对话；顺序、截止时间、过期后能不能补交。
- **什么时候写入**：老师在建课页加内容项、改标题和截止时间、上传文件（写 material_id）时。
- **和哪些表关联**：chapter_id → chapter；material_id → material；练习项下挂 exercise，AI 对话项下挂 feynman_setup；学生的学习记录都按内容项记。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_chapter_item_chapter（chapter_id）
- **谁写**：`ChapterItemService`（修改、删除、新增）；`ClassPushService`（修改）
- **谁读**：`AssistantService`、`ChapterItemService`、`ChapterKpService`、`ChapterService`、`ClassAnalysisService`、`ClassPushService`、`CompletionService`、`CourseProfileService`、`DeadlineService`、`DialogueService`、`ExerciseGenService`、`FeynmanDiagnosis`、`FeynmanDraftService`、`FeynmanEngine`、`KpRecognizer`、`LearnService`、`MaterialPipeline`、`ProgressService`、`RecommendService`、`RecordService`、`RemedyService`、`TeacherTodoService`
- **现在的数据**：11 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `course_id` | BIGINT | NOT NULL | 属于哪门课 → course.id（冗余，方便按课程查） |
| `chapter_id` | BIGINT | NOT NULL | 属于哪一章 → chapter.id |
| `type` | VARCHAR(16) | NOT NULL | video / document / exercise / dialogue |
| `title` | VARCHAR(100) | NOT NULL | 标题（页面上显示的名字） |
| `description` | TEXT |  | 说明；文档项也可以直接写正文 |
| `material_id` | BIGINT |  | 视频 / 文档项的文件 → material.id |
| `sort_order` | INT | NOT NULL DEFAULT 0 | 顺序号，从 0 开始，越小越靠前（新加的取“最大顺序号 + 1”） |
| `due_at` | DATETIME |  | 截止时间（只有练习、AI 对话可以设），NULL = 不限时 |
| `allow_late` | TINYINT(1) | NOT NULL DEFAULT 1 | 过了截止时间还能不能补做：1 能（记为迟交）/ 0 不能 |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME |  | 这一行最后修改的时间（MyBatis-Plus 自动填） |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除：0 正常，1 已删除（查询时自动过滤） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `course_id` | 1 |
| `chapter_id` | 1 |
| `type` | document |
| `title` | 阅读：边界值分析要点 |
| `description` | 1. 大量错误发生在输入范围的边界上，而不是在范围内部。 2. 选取测试值时，在边界上、边界外侧、边界内侧各取一个值。 3. 例：数量范围 1～99，下边界取 … |
| `material_id` | NULL |
| `sort_order` | 0 |
| `due_at` | NULL |
| `allow_late` | 1 |
| `created_at` | 2026-11-24 21:30:08 |
| `updated_at` | 2026-11-24 21:30:08 |
| `deleted` | 0 |

### `material`（资料）

- **用途**：上传的文件信息：原文件名、磁盘上的相对路径、大小、类型。文件本身存在磁盘 backend/uploads/，不进数据库。
- **什么时候写入**：老师给视频 / 文档项上传或替换文件时新增；替换或删除时逻辑删除旧的（deleted = 1）。
- **和哪些表关联**：被 chapter_item.material_id、material_job.material_id 引用。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_material_chapter（chapter_id）
- **谁写**：`MaterialService`（删除、新增）
- **谁读**：`MaterialService`
- **现在的数据**：9 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `course_id` | BIGINT | NOT NULL | 属于哪门课 → course.id |
| `chapter_id` | BIGINT |  | 属于哪一章 → chapter.id |
| `knowledge_point_id` | BIGINT |  | 早期按知识点挂资料用，现在资料挂在内容项上，一般为空 |
| `title` | VARCHAR(100) | NOT NULL | 资料标题 |
| `original_name` | VARCHAR(255) | NOT NULL | 上传时的原文件名（下载、预览时用） |
| `file_path` | VARCHAR(255) | NOT NULL | 相对路径，如 2026/10/uuid.pdf |
| `file_size` | BIGINT | NOT NULL | 文件大小（字节） |
| `content_type` | VARCHAR(100) |  | 文件类型（MIME），例如 application/pdf、video/mp4 |
| `uploaded_by` | BIGINT | NOT NULL | 上传的老师 → user.id |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除：0 正常，1 已删除（查询时自动过滤） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `course_id` | 1 |
| `chapter_id` | 3 |
| `knowledge_point_id` | NULL |
| `title` | 6 智能测试生成与优化 |
| `original_name` | 6 智能测试生成与优化.pdf |
| `file_path` | 2026/11/3a611ba5-913f-4ce7-b73a-b041e2c0f596.pdf |
| `file_size` | 4293296 |
| `content_type` | application/pdf |
| `uploaded_by` | 2 |
| `created_at` | 2026-11-24 21:30:19 |
| `deleted` | 0 |

### `material_job`（资料）

- **用途**：每个视频 / 文档项的“转文字”任务：排队 → 处理中 → 完成 / 失败，失败原因、处理统计、同音字纠正做过没有。
- **什么时候写入**：上传文件后马上排队；后台线程处理时改状态；识别知识点前做同音字纠正后标记 terms_checked。
- **和哪些表关联**：item_id → chapter_item；material_id → material。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；CONSTRAINT uk_material_job UNIQUE (item_id)
- **谁写**：`MaterialPipeline`（修改、删除、新增）；`VideoTermFixer`（修改）
- **谁读**：`ChapterKpService`、`MaterialPipeline`、`VideoTermFixer`
- **现在的数据**：5 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `item_id` | BIGINT | NOT NULL | chapter_item.id |
| `chapter_id` | BIGINT | NOT NULL | 哪一章 → chapter.id |
| `material_id` | BIGINT |  | 处理的是哪个文件；老师换了文件就重新处理 |
| `status` | VARCHAR(16) | NOT NULL | queued 排队 / processing 处理中 / done 完成 / failed 失败 |
| `message` | VARCHAR(500) |  | 失败原因，或完成时的简要结果（几页、几张图、几分钟） |
| `meta` | TEXT |  | 处理服务返回的统计（JSON：耗时、图片数、token） |
| `terms_checked` | TINYINT(1) |  | 视频：同音字纠正已经做过 |
| `started_at` | DATETIME |  | 后台开始处理的时间 |
| `finished_at` | DATETIME |  | 处理完成（或失败）的时间 |
| `created_at` | DATETIME | NOT NULL | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME | NOT NULL | 这一行最后修改的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `item_id` | 4 |
| `chapter_id` | 3 |
| `material_id` | 1 |
| `status` | done |
| `message` | 取到 34 页文字、29 张图的内容 |
| `meta` | {"pages":39,"skipped_pages":[1,3,8,10,20],"text_sec":0.2,"images":29,"images_fai… |
| `terms_checked` | NULL |
| `started_at` | 2026-11-24 21:30:19 |
| `finished_at` | 2026-11-24 21:33:49 |
| `created_at` | 2026-11-24 21:30:19 |
| `updated_at` | 2026-11-24 21:30:19 |

### `material_text`（资料）

- **用途**：资料转出来的文字，一段一行：课件一页、一张图、视频 30～60 秒一段，带出处编号（seg_key）、页码或起止秒；同音字纠正前的原文。
- **什么时候写入**：资料处理完成时一段一行写入；视频同音字纠正改正文、原文存进 original；重新上传时整份删掉重写。
- **和哪些表关联**：item_id → chapter_item；识别知识点时整章的文字交给大模型；知识点要点的出处编号指向这里的 seg_key。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_material_text_chapter（chapter_id, item_id）
- **谁写**：`MaterialPipeline`（删除、新增）；`VideoTermFixer`（修改）
- **谁读**：`KpRecognizer`、`VideoTermFixer`
- **现在的数据**：109 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `item_id` | BIGINT | NOT NULL | 是哪份资料的文字 → chapter_item.id |
| `chapter_id` | BIGINT | NOT NULL | 属于哪一章 → chapter.id（识别知识点按章取文字） |
| `seg_key` | VARCHAR(20) | NOT NULL | 这份资料里的编号：P4（第 4 页）、P13-图1（第 13 页的图）、01（视频第 1 段） |
| `kind` | VARCHAR(10) | NOT NULL | pdf / image / video / text（老师直接写在页面上的文字） |
| `page` | INT |  | 课件第几页 |
| `start_sec` | DECIMAL(9,1) |  | 视频片段从第几秒开始 |
| `end_sec` | DECIMAL(9,1) |  | 视频段落：结束秒 |
| `content` | TEXT | NOT NULL | 这一段转出来的文字（视频同音字纠正后是改正过的） |
| `original` | TEXT |  | 视频同音字纠正前的原文（改过才有） |
| `sort_order` | INT | NOT NULL | 顺序号，从 0 开始，越小越靠前（新加的取“最大顺序号 + 1”） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `item_id` | 4 |
| `chapter_id` | 3 |
| `seg_key` | P2 |
| `kind` | pdf |
| `page` | 2 |
| `start_sec` | NULL |
| `end_sec` | NULL |
| `content` | 自动化测试概述：智能测试概述智能测试四大优化环节各环节实验对比与分析目录 CONTENTS |
| `original` | NULL |
| `sort_order` | 0 |

### `chapter_kp_state`（知识点）

- **用途**：每章一行：识别知识点走到哪一步（等上传完 / 处理资料中 / 识别中 / 待确认 / 已确认 / 失败），老师点没点“上传完毕”。
- **什么时候写入**：老师点“上传完毕”、开始识别、识别完成、老师确认、资料有增删时。
- **和哪些表关联**：chapter_id → chapter；ai_call_id → ai_call_log。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；CONSTRAINT uk_chapter_kp_state UNIQUE (chapter_id)
- **谁写**：`ChapterKpService`（修改）；`KpRecognizer`（修改）；`MaterialPipeline`（修改、新增）
- **谁读**：`ChapterKpService`、`FeynmanDraftService`、`KpRecognizer`、`MaterialPipeline`、`TeacherTodoService`
- **现在的数据**：4 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `chapter_id` | BIGINT | NOT NULL | 哪一章 → chapter.id |
| `upload_done` | TINYINT(1) | NOT NULL DEFAULT 0 | 老师点了“上传完毕” |
| `status` | VARCHAR(16) | NOT NULL | none / waiting 等资料处理完 / recognizing 识别中 / draft 待确认 / confirmed 已确认 / failed |
| `message` | VARCHAR(500) |  | 给老师看的进度说明，例如“正在处理资料：3/5 份完成” |
| `ai_call_id` | BIGINT |  | 识别用的那次模型调用 → ai_call_log.id（看 token、费用） |
| `recognized_at` | DATETIME |  | 识别完成的时间 |
| `confirmed_at` | DATETIME |  | 老师确认的时间；为空 = 还没确认（学生看不到知识点地图） |
| `created_at` | DATETIME | NOT NULL | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME | NOT NULL | 这一行最后修改的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `chapter_id` | 3 |
| `upload_done` | 1 |
| `status` | confirmed |
| `message` | 已确认 7 个知识点 |
| `ai_call_id` | 1 |
| `recognized_at` | 2026-11-24 21:39:29 |
| `confirmed_at` | 2026-11-25 10:42:25 |
| `created_at` | 2026-11-24 21:30:19 |
| `updated_at` | 2026-11-24 21:30:19 |

### `chapter_kp`（知识点）

- **用途**：大模型识别出的知识点（老师可以改）：名称、一句话说明、重点 / 一般、要点（每条带出处，JSON）、常见误解、出处。
- **什么时候写入**：识别完成时整章写入；老师改、加、删知识点时；老师确认时写上对应的 knowledge_point_id。
- **和哪些表关联**：chapter_id → chapter；knowledge_point_id → knowledge_point（确认后）；feynman_setup.kp_ids 引用它。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_chapter_kp_chapter（chapter_id, sort_order）
- **谁写**：`ChapterKpService`（修改、删除、新增）；`KpRecognizer`（修改、删除、新增）
- **谁读**：`AssistantChatService`、`ChapterKpService`、`ClassAnalysisService`、`ClassPushService`、`ExerciseGenService`、`FeynmanDiagnosis`、`FeynmanDraftService`、`KpRecognizer`、`PracticeAnalysis`、`TeacherTodoService`
- **现在的数据**：7 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `chapter_id` | BIGINT | NOT NULL | 属于哪一章 → chapter.id |
| `sort_order` | INT | NOT NULL | 顺序号，从 0 开始，越小越靠前（新加的取“最大顺序号 + 1”） |
| `name` | VARCHAR(100) | NOT NULL | 知识点名称 |
| `summary` | VARCHAR(500) |  | 一句话说明 |
| `importance` | VARCHAR(8) |  | 重点 / 一般 |
| `key_points` | TEXT |  | JSON：[{"text": "...", "sources": ["D1-P4", "V1-02"]}] |
| `misconceptions` | TEXT |  | JSON：["...", "..."] |
| `sources` | TEXT |  | JSON：["D1-P4", "V1-02"]，编号见 material_text（D = 第几份文档，V = 第几个视频） |
| `edited` | TINYINT(1) | NOT NULL DEFAULT 0 | 老师改过：重新识别时保留 |
| `knowledge_point_id` | BIGINT |  | 确认后对应的 knowledge_point.id |
| `created_at` | DATETIME | NOT NULL | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME | NOT NULL | 这一行最后修改的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `chapter_id` | 3 |
| `sort_order` | 0 |
| `name` | 自动化测试的概念与局限 |
| `summary` | 理解自动化测试的本质、核心价值及其五大局限性，从而认识智能测试出现的必然性。 |
| `importance` | 重点 |
| `key_points` | [{"text":"自动化测试是把以人为驱动的测试行为转化为机器执行的过程，用工具、脚本或代码控制执行并比较实际结果与预期结果。","sources":["D1… |
| `misconceptions` | ["认为自动化测试可以完全脱离人的参与，不需要测试人员定义测试逻辑。","认为自动化测试覆盖度天然比手工测试高，不会遗漏边界和深层缺陷。"] |
| `sources` | ["D1-P4","V1-02","V1-03","D1-P5","D1-P6","V1-04","V1-05"] |
| `edited` | 0 |
| `knowledge_point_id` | 3 |
| `created_at` | 2026-11-24 21:39:29 |
| `updated_at` | 2026-11-24 21:39:29 |

### `knowledge_point`（知识点）

- **用途**：老师确认后的知识点：练习出题、掌握度、诊断、画像、小助手都按这张表的知识点算。
- **什么时候写入**：老师确认识别结果时从 chapter_kp 同步过来；老师也可以手动加。
- **和哪些表关联**：chapter_id → chapter；被 exercise_knowledge_point、mastery、remedy_round、class_issue、class_push、recommendation 引用。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_kp_chapter（chapter_id）
- **谁写**：`ChapterKpService`（修改、新增）；`KnowledgePointService`（修改、删除、新增）
- **谁读**：`AssistantChatService`、`AssistantService`、`ChapterKpService`、`ChapterService`、`ClassAnalysisService`、`ClassPushService`、`CourseProfileService`、`DiagnosisService`、`ExerciseGenService`、`ExerciseReviewService`、`ExerciseService`、`KnowledgePointService`、`MasteryService`、`RecommendService`、`RemedyService`
- **现在的数据**：9 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `course_id` | BIGINT | NOT NULL | 属于哪门课 → course.id |
| `chapter_id` | BIGINT | NOT NULL | 属于哪一章 → chapter.id |
| `name` | VARCHAR(100) | NOT NULL | 知识点名称 |
| `content` | TEXT |  | 说明 / 原理句 |
| `difficulty` | TINYINT | NOT NULL DEFAULT 2 | 1 易 / 2 中 / 3 难 |
| `is_key` | TINYINT(1) | NOT NULL DEFAULT 0 | 重点（E4） |
| `is_difficult` | TINYINT(1) | NOT NULL DEFAULT 0 | 难点（E4） |
| `sort_order` | INT | NOT NULL DEFAULT 0 | 顺序号，从 0 开始，越小越靠前（新加的取“最大顺序号 + 1”） |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME |  | 这一行最后修改的时间（MyBatis-Plus 自动填） |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除：0 正常，1 已删除（查询时自动过滤） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `course_id` | 1 |
| `chapter_id` | 1 |
| `name` | 边界测试值的选取 |
| `content` | 边界值分析：在边界上、边界外侧、边界内侧各取值进行测试。 |
| `difficulty` | 2 |
| `is_key` | 1 |
| `is_difficult` | 0 |
| `sort_order` | 0 |
| `created_at` | 2026-11-24 21:30:08 |
| `updated_at` | 2026-11-24 21:30:08 |
| `deleted` | 0 |

### `exercise`（练习）

- **用途**：一道题：题型（6 种）、考查类型、考第几条要点、题干、选项、答案、解析、每个选项的误解说明、简答的参考答案和要点、来源（老师手写 / 上传识别 / AI 生成 / 小助手 / 全班推送）、是不是草稿、程序检查结果。
- **什么时候写入**：老师手动出题、AI 出题（先存草稿，老师确认）、小助手补练和拓展当场出题、老师全班推送出新题时。
- **和哪些表关联**：item_id → chapter_item（练习项）；和知识点的关系在 exercise_knowledge_point；被 answer_record 引用。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_exercise_chapter（chapter_id）
- **谁写**：`ClassAnalysisService`（删除）；`ClassPushService`（修改）；`ExerciseGenService`（修改、删除）；`ExerciseService`（修改、删除、新增）
- **谁读**：`AssistantChatService`、`ChapterItemService`、`ChapterService`、`ClassAnalysisService`、`ClassPushService`、`ExerciseGenService`、`ExerciseReviewService`、`ExerciseService`、`PracticeAnalysis`、`RecommendService`、`RemedyService`、`TeacherTodoService`
- **现在的数据**：49 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `course_id` | BIGINT | NOT NULL | 属于哪门课 → course.id |
| `chapter_id` | BIGINT | NOT NULL | 属于哪一章 → chapter.id |
| `item_id` | BIGINT |  | 属于哪一次练习 → chapter_item.id（type = exercise） 题型：single_choice 单选 / two_tier 两段式单选 / multiple 多选 / judge 判断 / fill 填空 / short_answer 简答 |
| `type` | VARCHAR(16) | NOT NULL DEFAULT 'single_choice' | 题型：single_choice 单选 / two_tier 两段式单选 / multiple 多选 / judge 判断 / fill 填空 / short_answer 简答 |
| `assess_type` | VARCHAR(16) | NOT NULL DEFAULT 'concept' | 考查类型：concept 概念辨析 / scenario 情境应用 / data 数据分析 / process 流程步骤 |
| `key_point_no` | INT |  | 考的是知识点的第几条要点（AI 出题时标），可以为空 |
| `stem` | TEXT | NOT NULL | 题干 |
| `options` | TEXT | NOT NULL | JSON 数组，按顺序对应 A/B/C/D；填空、简答为 [] |
| `answer` | VARCHAR(16) | NOT NULL | 单选、判断、两段式：一个字母；多选：几个字母如 AC；填空、简答：空串 |
| `analysis` | TEXT |  | 解析（交卷后给学生看） |
| `option_notes` | TEXT |  | JSON 数组，和 options 一一对应 |
| `reasons` | TEXT |  | 两段式：理由选项 JSON 数组 |
| `reason_answer` | VARCHAR(4) |  | 两段式：正确理由的字母 |
| `reason_notes` | TEXT |  | 两段式：每个错误理由对应的误解 JSON 数组 |
| `accepts` | TEXT |  | 填空：都算对的说法 JSON 数组 |
| `reference` | TEXT |  | 简答：参考答案 |
| `key_points` | TEXT |  | 简答：判分要点 JSON 数组 |
| `source` | VARCHAR(16) | NOT NULL DEFAULT 'manual' | manual 老师手写 / upload 老师上传后识别 / ai AI 生成 |
| `draft` | TINYINT(1) | NOT NULL DEFAULT 0 | 1 = 草稿（上传或生成后老师还没确认），学生看不到 |
| `check_note` | TEXT |  | 程序检查发现的问题，给老师看 |
| `difficulty` | TINYINT | NOT NULL DEFAULT 2 | 难度 1～3（拓展题为 3） |
| `sort_order` | INT | NOT NULL DEFAULT 0 | 顺序号，从 0 开始，越小越靠前（新加的取“最大顺序号 + 1”） |
| `created_by` | BIGINT | NOT NULL | 出题的老师 → user.id |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME |  | 这一行最后修改的时间（MyBatis-Plus 自动填） |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除：0 正常，1 已删除（查询时自动过滤） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `course_id` | 1 |
| `chapter_id` | 1 |
| `item_id` | 2 |
| `type` | single_choice |
| `assess_type` | concept |
| `key_point_no` | NULL |
| `stem` | 报名年龄要求为 18～60 岁。测试下边界时，下面哪组取值最合适？ |
| `options` | ["17、18、19","18、30、60","0、18、100","18、19、20"] |
| `answer` | A |
| `analysis` | 下边界是 18：17 刚越过边界（应拒绝），18 在边界上，19 在边界内侧。 |
| `option_notes` | ["","把上下边界和中间值混在一起，没有取边界外侧的值","取了离边界很远的值，测不出边界附近的错误","只取了边界和内侧，缺少越过边界的值"] |
| `reasons` | NULL |
| `reason_answer` | NULL |
| `reason_notes` | NULL |
| `accepts` | NULL |
| `reference` | NULL |
| `key_points` | NULL |
| `source` | manual |
| `draft` | 0 |
| `check_note` | NULL |
| `difficulty` | 2 |
| `sort_order` | 0 |
| `created_by` | 2 |
| `created_at` | 2026-11-24 21:30:08 |
| `updated_at` | 2026-11-24 21:30:08 |
| `deleted` | 0 |

### `exercise_knowledge_point`（练习）

- **用途**：题目和知识点的关联（一道题可以关联多个知识点）。
- **什么时候写入**：出题时一起写入。
- **和哪些表关联**：exercise_id → exercise；knowledge_point_id → knowledge_point。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；CONSTRAINT uk_exercise_kp UNIQUE (exercise_id, knowledge_point_id)
- **谁写**：`ChapterItemService`（删除）；`ClassAnalysisService`（删除）；`ExerciseService`（删除、新增）
- **谁读**：`AssistantChatService`、`ClassAnalysisService`、`ClassPushService`、`DiagnosisService`、`ExerciseService`、`KnowledgePointService`、`MasteryService`、`RecommendService`
- **现在的数据**：32 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `exercise_id` | BIGINT | NOT NULL | 哪道题 → exercise.id |
| `knowledge_point_id` | BIGINT | NOT NULL | 哪个知识点 → knowledge_point.id |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `exercise_id` | 1 |
| `knowledge_point_id` | 1 |

### `enrollment`（选课）

- **用途**：谁加入了哪门课。加入后才能学；老师的全班分析、截止情况、课程人数只算这里的学生。
- **什么时候写入**：学生在“课程学习”里点“加入课程”时；演示数据里所有学生已加入所有课程。
- **和哪些表关联**：course_id → course；student_id → user。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；CONSTRAINT uk_enrollment UNIQUE (course_id, student_id)；索引 idx_enrollment_student（student_id）
- **谁写**：`EnrollmentService`（新增）
- **谁读**：`EnrollmentService`
- **现在的数据**：60 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `course_id` | BIGINT | NOT NULL | → course.id |
| `student_id` | BIGINT | NOT NULL | → user.id（学生） |
| `created_at` | DATETIME | NOT NULL | 加入的时间 |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `course_id` | 1 |
| `student_id` | 5 |
| `created_at` | 2026-11-27 14:49:05 |

### `learning_event`（学习记录）

- **用途**：学习行为，一个行为一行：看了一项内容（停留秒数）、学完一项。
- **什么时候写入**：学生打开内容项、离开时（停留秒数）、视频看到 90% / 文档读满 30 秒点“我已读完”、AI 对话结束时。
- **和哪些表关联**：student_id → user；target_id → chapter_item。画像的“有效学习日”、解锁规则的“学完”都读它。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_learning_event_student（student_id, created_at）
- **谁写**：`FeynmanEngine`（新增）；`ProgressService`（新增）；`RecordService`（新增）
- **谁读**：`AssistantService`、`CompletionService`、`CourseProfileService`、`DiagnosisService`、`ProfileService`、`RecordService`
- **现在的数据**：19 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `student_id` | BIGINT | NOT NULL | 哪个学生 → user.id |
| `event_type` | VARCHAR(32) | NOT NULL | 行为类型：view_item 看了一项内容 / finish_item 学完一项 |
| `target_id` | BIGINT |  | 内容项 → chapter_item.id |
| `course_id` | BIGINT |  | 冗余，方便按课程统计 |
| `duration_sec` | INT |  | view_item：这次停留了几秒 |
| `created_at` | DATETIME | NOT NULL | 这一行创建的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `student_id` | 5 |
| `event_type` | view_item |
| `target_id` | 1 |
| `course_id` | 1 |
| `duration_sec` | 540 |
| `created_at` | 2026-11-21 21:00:08 |

### `item_progress`（学习记录）

- **用途**：每人每项一行：视频看过的时间段（合并后的 JSON）、总时长、上次位置；文档累计阅读秒数；什么时候判定学完。
- **什么时候写入**：学生看视频时每约 10 秒上报一次、读文档时计时上报。
- **和哪些表关联**：student_id → user；item_id → chapter_item。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；CONSTRAINT uk_item_progress UNIQUE (student_id, item_id)
- **谁写**：`ProgressService`（修改、新增）
- **谁读**：`ProgressService`
- **现在的数据**：1 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `student_id` | BIGINT | NOT NULL | 哪个学生 → user.id |
| `item_id` | BIGINT | NOT NULL | chapter_item.id |
| `course_id` | BIGINT |  | 属于哪门课 → course.id（冗余） |
| `duration_sec` | DECIMAL(9,1) |  | 视频总时长（秒，第一次上报时定下，之后不改） |
| `watched_ranges` | VARCHAR(4000) |  | 视频真正播放过的时间段，合并后的 JSON：[[0,12.5],[14,20]] |
| `watched_sec` | DECIMAL(9,1) | NOT NULL DEFAULT 0 | 上面各段长度之和 |
| `read_sec` | INT | NOT NULL DEFAULT 0 | 文档累计阅读秒数（页面在前台才算） |
| `last_position` | DECIMAL(9,1) |  | 视频上次播放到哪里，下次接着看 |
| `completed_at` | DATETIME |  | 判定学完的时间；空 = 还没学完 |
| `created_at` | DATETIME | NOT NULL | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME | NOT NULL | 这一行最后修改的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `student_id` | 12 |
| `item_id` | 4 |
| `course_id` | 1 |
| `duration_sec` | NULL |
| `watched_ranges` | NULL |
| `watched_sec` | 0.0 |
| `read_sec` | 19 |
| `last_position` | NULL |
| `completed_at` | NULL |
| `created_at` | 2026-11-25 01:25:03 |
| `updated_at` | 2026-11-25 01:25:04 |

### `answer_record`（学习记录）

- **用途**：每一次作答一行：答案、对错、得分、蒙对概率、可信度、状态（已判 / 等老师确认）、简答每条要点的判定、谁确认的。
- **什么时候写入**：学生交卷、做补练题、做拓展题、做老师推送的新题时；老师确认或改判简答时修改。
- **和哪些表关联**：student_id → user；exercise_id → exercise；item_id → chapter_item（练习项；补练、推送题为空）。掌握度、诊断、画像、全班分析都从这里算。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_answer_record_student（student_id, submitted_at）
- **谁写**：`ExerciseReviewService`（修改）；`RecordService`（新增）
- **谁读**：`ClassAnalysisService`、`ClassPushService`、`CompletionService`、`CourseProfileService`、`DiagnosisService`、`ExerciseReviewService`、`MasteryService`、`PracticeAnalysis`、`ProfileService`、`RecommendService`、`RecordService`、`RemedyService`、`TeacherTodoService`
- **现在的数据**：47 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `student_id` | BIGINT | NOT NULL | 哪个学生 → user.id |
| `exercise_id` | BIGINT | NOT NULL | 答的是哪道题 → exercise.id |
| `course_id` | BIGINT | NOT NULL | 冗余，方便按课程统计 |
| `item_id` | BIGINT |  | 哪一次练习 → chapter_item.id；同一次交卷的几题 submitted_at 相同 |
| `answer` | VARCHAR(4000) | NOT NULL | 学生的答案，没作答存空串；两段式存 B\|C，多选存 AC |
| `is_correct` | TINYINT(1) | NOT NULL | 得分 = 1 才算对 |
| `score` | DOUBLE |  | 这题得分 0～1：客观题 0 或 1，简答 = 答到的要点数 / 要点总数 |
| `guess` | DOUBLE |  | 这道题蒙对的概率 g（单选 1/选项数，判断 0.5……） |
| `reliability` | DOUBLE |  | 可信度权重 r：程序判 1，简答两次一致 0.5，老师确认 1 |
| `status` | VARCHAR(16) | NOT NULL DEFAULT 'graded' | graded 已判 / pending 简答两次不一致，等老师确认（不算进掌握度） |
| `detail` | TEXT |  | 简答：每条要点两次判的结果和学生原话（JSON） |
| `reviewed_by` | BIGINT |  | 简答：哪位老师确认或改判的 |
| `time_spent_sec` | INT |  | 作答用时（首版不记） |
| `submitted_at` | DATETIME | NOT NULL | 交卷时间（同一次交卷的几道题时间相同；判断“第一次作答”、按时交卷都看它） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `student_id` | 5 |
| `exercise_id` | 1 |
| `course_id` | 1 |
| `item_id` | 2 |
| `answer` | B |
| `is_correct` | 0 |
| `score` | NULL |
| `guess` | NULL |
| `reliability` | NULL |
| `status` | graded |
| `detail` | NULL |
| `reviewed_by` | NULL |
| `time_spent_sec` | NULL |
| `submitted_at` | 2026-11-22 21:16:08 |

### `mastery`（诊断 / 画像）

- **用途**：每个学生每个知识点一行：原始掌握度 F（练习里每道题第一次作答的平均分）、补练 R、当前掌握度、补练状态。颜色由程序从这一行现算。
- **什么时候写入**：每次交卷、做补练题或推送的新题后重算并更新。
- **和哪些表关联**：student_id → user；knowledge_point_id → knowledge_point。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；CONSTRAINT uk_mastery UNIQUE (student_id, knowledge_point_id)
- **谁写**：`MasteryService`（修改、新增）
- **谁读**：`AssistantChatService`、`AssistantService`、`ChapterKpService`、`ClassPushService`、`CourseProfileService`、`DiagnosisService`、`MasteryService`、`RemedyService`
- **现在的数据**：15 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `student_id` | BIGINT | NOT NULL | 哪个学生 → user.id |
| `knowledge_point_id` | BIGINT | NOT NULL | 哪个知识点 → knowledge_point.id |
| `mastery` | DECIMAL(5,4) | NOT NULL | 当前掌握度 0～1：补练够 2 道用补练平均分，否则用第一次的 |
| `attempt_count` | INT | NOT NULL | 算进来的题数（第一次作答 + 补练新题） |
| `correct_count` | INT | NOT NULL | 算进来的题里全对的题数 |
| `first_mastery` | DECIMAL(5,4) |  | 原始掌握度：练习里每道题第一次作答的平均分，之后不改 |
| `first_count` | INT | NOT NULL DEFAULT 0 | 算原始掌握度用了几道题（练习里每道题的第一次作答） |
| `remedy` | DECIMAL(5,4) |  | 补练（小助手推荐、干预布置的新题）第一次作答的平均分 |
| `remedy_count` | INT | NOT NULL DEFAULT 0 | 算补练用了几道题（补练题、老师推送的新题的第一次作答） |
| `remedy_status` | VARCHAR(16) |  | recovered 已补回来 / practicing 补练中 |
| `updated_at` | DATETIME |  | 这一行最后修改的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `student_id` | 5 |
| `knowledge_point_id` | 1 |
| `mastery` | 0.0000 |
| `attempt_count` | 1 |
| `correct_count` | 0 |
| `first_mastery` | 0.0000 |
| `first_count` | 1 |
| `remedy` | NULL |
| `remedy_count` | 0 |
| `remedy_status` | NULL |
| `updated_at` | 2026-11-23 20:45:08 |

### `profile_snapshot`（诊断 / 画像）

- **用途**：学完一门课那一刻的课程画像（四维和参考日，JSON），每人每门课一份，之后不再改。
- **什么时候写入**：学生全部章节学完后第一次打开课程画像时。
- **和哪些表关联**：student_id → user；course_id → course。平时的画像不存表，每次从学习记录和 mastery 现算。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；CONSTRAINT uk_profile_snapshot UNIQUE (student_id, course_id)
- **谁写**：`CourseProfileService`（新增）
- **谁读**：`CourseProfileService`
- **现在的数据**：0 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `student_id` | BIGINT | NOT NULL | 哪个学生 → user.id |
| `course_id` | BIGINT | NOT NULL | 哪门课的画像 → course.id |
| `data` | TEXT | NOT NULL | JSON：当时的四维和每章画像 |
| `created_at` | DATETIME | NOT NULL | 保存的时间（≈ 学完的时间） |

（现在没有数据。）

### `feynman_setup`（AI 对话）

- **用途**：一个 AI 对话项的设置：老师选的知识点（最多 2 个）和大模型起草、老师确认的草稿（场景、每条要点的判断标准和三级提示，JSON）、状态（待确认 / 已确认）。
- **什么时候写入**：老师生成草稿、修改草稿、确认草稿时。
- **和哪些表关联**：item_id → chapter_item（AI 对话项）；kp_ids → chapter_kp。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；CONSTRAINT uk_feynman_setup UNIQUE (item_id)
- **谁写**：`FeynmanDraftService`（修改、新增）
- **谁读**：`ChapterItemService`、`FeynmanDraftService`、`TeacherTodoService`
- **现在的数据**：1 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `item_id` | BIGINT | NOT NULL | chapter_item.id（type = dialogue） |
| `chapter_id` | BIGINT | NOT NULL | 哪一章 → chapter.id |
| `kp_ids` | VARCHAR(100) | NOT NULL | chapter_kp.id，逗号分隔，按对话顺序 |
| `drafts` | TEXT |  | JSON：每个知识点的草稿（场景、开场白、误解、判断标准、三级提示、考一考小林） |
| `status` | VARCHAR(16) | NOT NULL | draft 待确认 / confirmed 已确认（学生能用） |
| `created_at` | DATETIME | NOT NULL | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME | NOT NULL | 这一行最后修改的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `item_id` | 33 |
| `chapter_id` | 3 |
| `kp_ids` | 3 |
| `drafts` | [{"kpId":3,"name":"智能测试四大优化环节","keyPoints":["四大优化环节是测试用例生成、测试用例执行与优化、缺陷自动检测与修复、测… |
| `status` | confirmed |
| `created_at` | 2026-11-24 21:43:19 |
| `updated_at` | 2026-11-24 21:43:19 |

### `feynman_session`（AI 对话）

- **用途**：一个学生的一次对话，进度整个存在 state（JSON）里：每个知识点每条要点的状态、提示级别、问了几次、怎么讲清的、学习情况小结。
- **什么时候写入**：学生开始对话时新增；每说一句更新 state；结束时状态改成 done；老师重新生成草稿时没聊完的改成 expired。
- **和哪些表关联**：student_id → user；item_id → chapter_item。小助手和画像里的“AI 对话情况”从这里读。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)
- **谁写**：`FeynmanDraftService`（修改）；`FeynmanEngine`（修改、新增）
- **谁读**：`FeynmanDiagnosis`、`FeynmanEngine`
- **现在的数据**：5 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `student_id` | BIGINT | NOT NULL | 哪个学生 → user.id |
| `item_id` | BIGINT | NOT NULL | 哪个 AI 对话项 → chapter_item.id |
| `status` | VARCHAR(16) | NOT NULL | open 进行中 / done 完成 / expired 老师重新生成了草稿，没聊完的作废 |
| `state` | TEXT | NOT NULL | JSON |
| `created_at` | DATETIME | NOT NULL | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME | NOT NULL | 这一行最后修改的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `student_id` | 12 |
| `item_id` | 33 |
| `status` | done |
| `state` | {"idx":0,"kps":[{"kpId":3,"name":"智能测试四大优化环节","phase":"done","turns":10,"attempt… |
| `created_at` | 2026-11-24 21:44:28 |
| `updated_at` | 2026-11-24 21:45:32 |

### `feynman_message`（AI 对话）

- **用途**：对话里的每一句：谁说的、内容；学生的话附上大模型的判断（JSON，存档、复核用）。
- **什么时候写入**：学生每说一句、AI 同学每回一句时。
- **和哪些表关联**：session_id → feynman_session；ai_call_id → ai_call_log。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_feynman_message_session（session_id）
- **谁写**：`FeynmanEngine`（修改、新增）
- **谁读**：`FeynmanEngine`
- **现在的数据**：101 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `session_id` | BIGINT | NOT NULL | 属于哪次对话（费曼对话 → feynman_session.id；旧版 → dialogue_session.id） |
| `kp_idx` | INT | NOT NULL | 第几个知识点（从 0 开始） |
| `role` | VARCHAR(10) | NOT NULL | student / ai / system |
| `content` | TEXT | NOT NULL | 这一句说的话 |
| `judgment` | TEXT |  | JSON：大模型对学生这一句的判断 |
| `ai_call_id` | BIGINT |  | 这一句对应的那次大模型调用 → ai_call_log.id |
| `created_at` | DATETIME | NOT NULL | 这一行创建的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `session_id` | 1 |
| `kp_idx` | 0 |
| `role` | system |
| `content` | 这次请你把 1 个知识点讲给同学小林听：「智能测试四大优化环节」。讲不清的地方，小林会一直追问；不用担心说错，这里不打分。 |
| `judgment` | NULL |
| `ai_call_id` | NULL |
| `created_at` | 2026-11-24 21:44:28 |

### `dialogue_topic`（AI 对话（旧版））

- **用途**：旧版“话题式”对话的话题：情境问题、参考答案、要点、三级提示、变式题。
- **什么时候写入**：老师在旧版话题页编辑时。老师没有配置费曼对话的对话项才用旧版。
- **和哪些表关联**：item_id → chapter_item；knowledge_point_id → knowledge_point。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_dialogue_topic_chapter（chapter_id）
- **谁写**：`ChapterItemService`（删除）；`DialogueTopicService`（修改、删除、新增）
- **谁读**：`ChapterItemService`、`ChapterService`、`DialogueTopicService`
- **现在的数据**：2 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `chapter_id` | BIGINT | NOT NULL | 所属章节 → chapter.id |
| `item_id` | BIGINT |  | 属于哪个 AI 对话项 → chapter_item.id（type = dialogue） |
| `knowledge_point_id` | BIGINT |  | 考哪个知识点（概念层）→ knowledge_point.id；干预按它对上练习题（应用层） |
| `sort_order` | INT | NOT NULL DEFAULT 0 | 对话里的先后顺序，小的在前 |
| `title` | VARCHAR(100) | NOT NULL | 话题名 |
| `question` | TEXT | NOT NULL | 情境问题 |
| `simple_question` | TEXT |  | 答非所问时换用的简单问法 |
| `key_points` | TEXT | NOT NULL | JSON 数组：要点，如 ["越过边界的 0","边界上的 1"] |
| `hints` | TEXT |  | JSON 数组：预写提示 L1、L2、L3，模型失败时才用 |
| `reveal_text` | TEXT | NOT NULL | 讲解原文（老师的参考答案） |
| `source_ref` | VARCHAR(100) |  | 出处，如"课件第 13 页" |
| `source_text` | TEXT |  | 依据的原理句原文 |
| `transfer_question` | TEXT |  | 变式题：换情境考同一个知识点，旧话题可以为空 |
| `transfer_points` | TEXT |  | JSON 数组：变式题的要点 |
| `transfer_reveal` | TEXT |  | 变式题的参考答案 |
| `created_by_ai` | TINYINT(1) | NOT NULL DEFAULT 0 | 要点等内容是否由 AI 起草 |
| `enabled` | TINYINT(1) | NOT NULL DEFAULT 1 | 是否启用 |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME |  | 这一行最后修改的时间（MyBatis-Plus 自动填） |
| `deleted` | TINYINT | NOT NULL DEFAULT 0 | 逻辑删除：0 正常，1 已删除（查询时自动过滤） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `chapter_id` | 1 |
| `item_id` | 3 |
| `knowledge_point_id` | 1 |
| `sort_order` | 0 |
| `title` | 下边界取哪些值 |
| `question` | 某网上商城规定每单购买数量为 1 到 99 件。测试"下限"时，你会挑哪几个数量来测？为什么？ |
| `simple_question` | 每单最少能买几件？比它少一件、多一件分别是几件？ |
| `key_points` | ["越过下边界的 0（应被拒绝）","正好在边界上的 1","边界内侧的 2","错误常发生在边界附近，所以要重点测"] |
| `hints` | ["想想边界本身、边界外面、边界里面，各取一个值。","你只测了边界本身的话，比边界少一点的输入被错误接受了，能发现吗？","如果买 0 件，系统应该接受吗？"… |
| `reveal_text` | 取 0、1、2。0 刚越过下边界，系统应拒绝；1 正好在边界上，应接受；2 在边界内侧，应接受。错误常发生在边界附近，所以要重点测这里。 |
| `source_ref` | 课件第 13 页 |
| `source_text` | 边界值分析：在边界上、边界外侧、边界内侧各取值进行测试。大量错误发生在输入或输出范围的边界上。 |
| `transfer_question` | 某图书馆规定一次可以借 3 到 10 本书。测试"下限"时，你会挑哪几个数量来测？为什么？ |
| `transfer_points` | ["越过下边界的 2（应被拒绝）","正好在边界上的 3","边界内侧的 4","错误常发生在边界附近，所以要重点测"] |
| `transfer_reveal` | 取 2、3、4。2 刚越过下边界，系统应拒绝；3 正好在边界上，应接受；4 在边界内侧，应接受。错误常发生在边界附近，所以要重点测这里。 |
| `created_by_ai` | 0 |
| `enabled` | 1 |
| `created_at` | 2026-11-24 21:30:08 |
| `updated_at` | 2026-11-24 21:30:08 |
| `deleted` | 0 |

### `dialogue_session`（AI 对话（旧版））

- **用途**：旧版对话的一次会话。
- **什么时候写入**：学生开始旧版对话、结束时。
- **和哪些表关联**：student_id → user；item_id → chapter_item。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)
- **谁写**：`DialogueService`（修改、新增）
- **谁读**：`CompletionService`、`DiagnosisService`、`DialogueService`、`RecordService`
- **现在的数据**：5 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `student_id` | BIGINT | NOT NULL | 哪个学生 → user.id |
| `chapter_id` | BIGINT | NOT NULL | 哪一章 → chapter.id |
| `item_id` | BIGINT |  | 章节里的哪个 AI 对话项 → chapter_item.id；为空 = 整章的话题 |
| `kind` | VARCHAR(16) | NOT NULL | CHAPTER 章节对话 / REVIEW 错题讲解（先只做 CHAPTER） |
| `status` | VARCHAR(16) | NOT NULL | OPEN 进行中 / PAUSED 暂停 / FINISHED 结束 |
| `current_idx` | INT | NOT NULL DEFAULT 0 | 当前第几个话题（从 0 开始） |
| `irrelevant_topics_in_row` | INT | NOT NULL DEFAULT 0 | 连续几个话题因答非所问被跳过 |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME |  | 这一行最后修改的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 101 |
| `student_id` | 7 |
| `chapter_id` | 1 |
| `item_id` | 3 |
| `kind` | CHAPTER |
| `status` | FINISHED |
| `current_idx` | 2 |
| `irrelevant_topics_in_row` | 0 |
| `created_at` | 2026-11-22 21:20:08 |
| `updated_at` | 2026-11-22 21:30:08 |

### `dialogue_topic_state`（AI 对话（旧版））

- **用途**：旧版对话里每个话题的进度：答错次数、提示级别、答到的要点、结果。
- **什么时候写入**：旧版对话每一轮。
- **和哪些表关联**：session_id → dialogue_session；topic_id → dialogue_topic。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；CONSTRAINT uk_topic_state UNIQUE (session_id, topic_idx)
- **谁写**：`DialogueService`（修改、新增）
- **谁读**：`DiagnosisService`、`DialogueService`
- **现在的数据**：10 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `session_id` | BIGINT | NOT NULL | 属于哪次对话（费曼对话 → feynman_session.id；旧版 → dialogue_session.id） |
| `topic_idx` | INT | NOT NULL | 第几个话题 |
| `topic_id` | BIGINT |  | → dialogue_topic.id |
| `wrong_count` | INT | NOT NULL DEFAULT 0 | 答错 / 部分对的次数 |
| `stuck_count` | INT | NOT NULL DEFAULT 0 | 点"我卡住了"或直接要答案的次数 |
| `irrelevant_count` | INT | NOT NULL DEFAULT 0 | 答非所问的次数 |
| `hint_level` | INT | NOT NULL DEFAULT -1 | 已给到第几级提示（0、1、2 = L1、L2、L3），-1 = 还没给 |
| `hit_points` | VARCHAR(100) |  | 已经答到的要点序号，如 "1,2" |
| `result` | VARCHAR(20) |  | SELF_CORRECT / HINT_CORRECT / REVEALED_APPLIED / REVEALED / SKIPPED_IRRELEVANT |
| `phase` | VARCHAR(16) | NOT NULL DEFAULT 'ANSWER' | ANSWER 作答 / TRANSFER 变式检验 |
| `turn_count` | INT | NOT NULL DEFAULT 0 | 当前阶段走了几轮，到上限就往下走 |
| `answer_count` | INT | NOT NULL DEFAULT 0 | 真正作答过几次，没作答过的拿不到答案 |
| `refuse_count` | INT | NOT NULL DEFAULT 0 | 不作答只要答案的次数，进入变式时清零 |
| `given_points` | VARCHAR(100) |  | 程序已告诉学生的要点序号，不算学生答到 |
| `transfer_hit_points` | VARCHAR(100) |  | 变式题已答到的要点序号 |
| `transfer_tries` | INT | NOT NULL DEFAULT 0 | 变式题作答了几次 |
| `misconception` | VARCHAR(500) |  | 模型最近发现的错误想法，不算进展 |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1001 |
| `session_id` | 101 |
| `topic_idx` | 0 |
| `topic_id` | 1 |
| `wrong_count` | 0 |
| `stuck_count` | 0 |
| `irrelevant_count` | 0 |
| `hint_level` | -1 |
| `hit_points` | NULL |
| `result` | SELF_CORRECT |
| `phase` | ANSWER |
| `turn_count` | 0 |
| `answer_count` | 0 |
| `refuse_count` | 0 |
| `given_points` | NULL |
| `transfer_hit_points` | NULL |
| `transfer_tries` | 0 |
| `misconception` | NULL |

### `dialogue_message`（AI 对话（旧版））

- **用途**：旧版对话的每一句。
- **什么时候写入**：旧版对话每一轮。
- **和哪些表关联**：session_id → dialogue_session；ai_call_id → ai_call_log。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_dialogue_message_session（session_id）
- **谁写**：`DialogueService`（修改、新增）
- **谁读**：`DialogueService`、`RecordService`
- **现在的数据**：5 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `session_id` | BIGINT | NOT NULL | 属于哪次对话（费曼对话 → feynman_session.id；旧版 → dialogue_session.id） |
| `topic_idx` | INT |  | 这一句属于第几个话题（从 0 开始） |
| `role` | VARCHAR(16) | NOT NULL | STUDENT / AI |
| `content` | TEXT | NOT NULL | 这一句说的话 |
| `source` | VARCHAR(16) |  | MODEL 模型写的 / TEMPLATE 预写文字 / RULE 程序生成 |
| `judged_type` | VARCHAR(16) |  | 学生这句被判成哪一类 |
| `ai_call_id` | BIGINT |  | 这句对应的模型调用 → ai_call_log.id |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1001 |
| `session_id` | 102 |
| `topic_idx` | 0 |
| `role` | AI |
| `content` | 我们开始吧。先来讨论「下边界取哪些值」。 |
| `source` | RULE |
| `judged_type` | NULL |
| `ai_call_id` | NULL |
| `created_at` | 2026-11-22 21:20:08 |

### `item_analysis`（老师干预）

- **用途**：每次练习的全班分析：按哪个截止时间分析、全班人数、截止前交卷人数、结果（已分析 / 交卷不到 80% 作废）。
- **什么时候写入**：练习到截止时间（或全班都交完）后，老师打开干预管理页时分析一次；延期后重新分析。
- **和哪些表关联**：item_id → chapter_item（练习项）；下面挂 class_issue。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_item_analysis（item_id）
- **谁写**：`ClassAnalysisService`（新增）
- **谁读**：`ClassAnalysisService`
- **现在的数据**：1 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `item_id` | BIGINT | NOT NULL | 分析的是哪次练习 → chapter_item.id |
| `due_at` | DATETIME | NOT NULL | 按哪一个截止时间分析的（延期后会重新分析） |
| `status` | VARCHAR(16) | NOT NULL | done 已分析 / void 交卷人数不够，作废 |
| `students` | INT | NOT NULL | 全班人数 |
| `submitted` | INT | NOT NULL | 截止前交卷的人数 |
| `analyzed_at` | DATETIME |  | 分析的时间 |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `item_id` | 2 |
| `due_at` | 2026-11-26 21:30:08 |
| `status` | void |
| `students` | 10 |
| `submitted` | 7 |
| `analyzed_at` | 2026-11-27 12:54:32 |

### `class_issue`（老师干预）

- **用途**：全班分析发现的问题卡片：规则①一道题全班 ≥ 80% 做错（题目检查）；规则②某条要点在某种考查类型上错误率 > 50%。做错的学生、老师的处理状态。
- **什么时候写入**：分析时写入；老师点“没问题 / 已修正 / 作废 / 忽略”、推送后改状态。
- **和哪些表关联**：analysis_id → item_analysis；exercise_id → exercise；knowledge_point_id → knowledge_point。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_class_issue_item（item_id）
- **谁写**：`ClassAnalysisService`（修改、删除、新增）；`ClassPushService`（修改）
- **谁读**：`ClassAnalysisService`、`ClassPushService`
- **现在的数据**：0 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `analysis_id` | BIGINT | NOT NULL | 属于哪次全班分析 → item_analysis.id |
| `item_id` | BIGINT | NOT NULL | 哪次练习 → chapter_item.id |
| `course_id` | BIGINT | NOT NULL | 哪门课 → course.id |
| `kind` | VARCHAR(16) | NOT NULL | question 题目检查 / cell 要点 × 考查类型 |
| `knowledge_point_id` | BIGINT |  | cell：哪个知识点 → knowledge_point.id |
| `key_point_no` | INT |  | 第几条要点；没标要点的旧题为空（按知识点 × 考查类型算） |
| `assess_type` | VARCHAR(16) |  | cell：哪种考查类型（concept / scenario / data / process） |
| `exercise_id` | BIGINT |  | question：哪一道题 |
| `attempts` | INT | NOT NULL | 作答人次（question：作答人数） |
| `wrong` | INT | NOT NULL | 做错的人次 |
| `student_ids` | VARCHAR(2000) |  | 做错的学生，逗号分隔（推送对象） |
| `status` | VARCHAR(16) | NOT NULL DEFAULT 'open' | open 待处理 / ok 题目没问题 / fixed 已修改 / voided 已作废 / ignored 忽略 / pushed 已推送 |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME |  | 这一行最后修改的时间（MyBatis-Plus 自动填） |

（现在没有数据。）

### `class_push`（老师干预）

- **用途**：老师对全班问题的处理：补充讲解（大模型起草或老师写）、新题、推送给哪些学生、推送前的错误率、状态（草稿 / 已推送）。
- **什么时候写入**：老师处理卡片时新增草稿；修改、重新生成、推送时更新。
- **和哪些表关联**：issue_ids → class_issue；exercise_ids → exercise（来源 class）；学生在小助手里看到。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)
- **谁写**：`ClassPushService`（修改、新增）
- **谁读**：`ClassPushService`
- **现在的数据**：0 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `course_id` | BIGINT | NOT NULL | 哪门课 → course.id |
| `item_id` | BIGINT | NOT NULL | 哪一次练习的分析 |
| `issue_ids` | VARCHAR(255) | NOT NULL | 处理的卡片（同一个知识点的几张可以合并），逗号分隔 |
| `knowledge_point_id` | BIGINT |  | 针对哪个知识点 → knowledge_point.id |
| `assess_type` | VARCHAR(16) |  | 针对哪种考查类型（决定讲解方式，例如概念辨析 → 对比讲解） |
| `explanation` | TEXT |  | 补充讲解（大模型起草、老师改过，或老师自己写的） |
| `explanation_by` | VARCHAR(16) |  | ai 大模型起草 / teacher 老师写的 |
| `exercise_ids` | VARCHAR(255) |  | 新题（来源 class），逗号分隔 |
| `student_ids` | VARCHAR(2000) | NOT NULL | 推送对象：做错的学生 |
| `before_rate` | DOUBLE |  | 推送前的错误率（卡片上的） |
| `status` | VARCHAR(16) | NOT NULL DEFAULT 'draft' | draft 老师还在改 / pushed 已推送 |
| `due_at` | DATETIME |  | 学生做新题的期限 |
| `pushed_at` | DATETIME |  | 推送的时间；为空 = 还是草稿 |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME |  | 这一行最后修改的时间（MyBatis-Plus 自动填） |

（现在没有数据。）

### `remedy_round`（小助手）

- **用途**：小助手的补练和拓展，每一轮一行：知识点、补练还是拓展、考查类型、第几轮、这一轮的题、平均分、2 轮后还没补回来。
- **什么时候写入**：学生在小助手里点“做补练题”“来点更难的”时新增；交了以后更新。
- **和哪些表关联**：student_id → user；knowledge_point_id → knowledge_point；exercise_ids → exercise（来源 assistant）。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_remedy_student（student_id, knowledge_point_id）
- **谁写**：`RemedyService`（修改、新增）
- **谁读**：`RemedyService`
- **现在的数据**：2 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `student_id` | BIGINT | NOT NULL | 哪个学生 → user.id |
| `knowledge_point_id` | BIGINT | NOT NULL | 补练 / 拓展的是哪个知识点 → knowledge_point.id |
| `kind` | VARCHAR(16) | NOT NULL DEFAULT 'remedy' | remedy 补练 / extend 拓展（E1，已掌握的学生要更难的题） |
| `assess_type` | VARCHAR(16) |  | 拓展：学生选的考查类型 |
| `round_no` | INT | NOT NULL | 第几轮（补练最多 2 轮；拓展不限） |
| `exercise_ids` | VARCHAR(255) | NOT NULL | 这一轮的补练题（小助手当场生成的），逗号分隔 |
| `status` | VARCHAR(16) | NOT NULL DEFAULT 'open' | open 还没交 / done 交了 |
| `score` | DOUBLE |  | 这一轮的平均分 |
| `escalated` | TINYINT(1) | NOT NULL DEFAULT 0 | 2 轮做完还没补回来（只记录，老师能看到，不转给老师处理） |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `finished_at` | DATETIME |  | 这一轮交卷的时间 |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `student_id` | 12 |
| `knowledge_point_id` | 5 |
| `kind` | remedy |
| `assess_type` | NULL |
| `round_no` | 1 |
| `exercise_ids` | 161,162,163 |
| `status` | done |
| `score` | 1 |
| `escalated` | 0 |
| `created_at` | 2026-11-25 20:45:07 |
| `finished_at` | 2026-11-25 20:45:18 |

### `assistant_message`（小助手）

- **用途**：小助手聊天的每一句；被程序拦下的回复记下原因和原文（给开发看，学生看到的是固定的话）。
- **什么时候写入**：学生在小助手里问一句、小助手回一句时。
- **和哪些表关联**：student_id → user；chapter_id → chapter；ai_call_id → ai_call_log。每天最多 20 句也按这张表数。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_assistant_message（student_id, chapter_id）
- **谁写**：`AssistantChatService`（新增）
- **谁读**：`AssistantChatService`
- **现在的数据**：30 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `student_id` | BIGINT | NOT NULL | 哪个学生 → user.id |
| `chapter_id` | BIGINT | NOT NULL | 哪一章 → chapter.id |
| `role` | VARCHAR(16) | NOT NULL | student / assistant |
| `content` | TEXT | NOT NULL | 这一句说的话（被拦下的回复：学生看到的固定说法） |
| `blocked` | VARCHAR(32) |  | 被程序拦下的原因（question 出题 / answer 给答案），没拦为空 |
| `original` | TEXT |  | 被拦下时大模型原来的回复（只给开发看，用来调规则） |
| `ai_call_id` | BIGINT |  | 这一句对应的那次大模型调用 → ai_call_log.id |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `student_id` | 12 |
| `chapter_id` | 3 |
| `role` | student |
| `content` | 某团队把一套原本由测试人员手工点击执行的回归用例，改写成脚本后由机器定时执行，并自动比对页面返回内容与预期值。关于这一做法，下列说法最准确的是？ 选哪个？ |
| `blocked` | NULL |
| `original` | NULL |
| `ai_call_id` | NULL |
| `created_at` | 2026-11-25 20:45:40 |

### `assistant_notice`（小助手）

- **用途**：老师给学生的提醒（例如分析作废时提醒没交卷的学生），显示在学生的小助手和消息铃铛里。
- **什么时候写入**：老师在干预管理页点“提醒没交的学生”时。
- **和哪些表关联**：student_id → user；item_id → chapter_item。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；索引 idx_assistant_notice（student_id）
- **谁写**：`ClassPushService`（新增）
- **谁读**：`ClassPushService`
- **现在的数据**：0 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `student_id` | BIGINT | NOT NULL | 哪个学生 → user.id |
| `item_id` | BIGINT |  | 提醒和哪个内容项有关 → chapter_item.id |
| `content` | VARCHAR(500) | NOT NULL | 提醒的文字 |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |

（现在没有数据。）

### `recommendation`（学习推荐）

- **用途**：学习推荐页给学生推的题，一题一行：针对哪个薄弱知识点、理由、状态（新 / 做对了 / 不想做）。
- **什么时候写入**：学生打开学习推荐页时按薄弱知识点选题写入；做对了、换一道时改状态。
- **和哪些表关联**：student_id → user；target_id → exercise；knowledge_point_id → knowledge_point。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)；CONSTRAINT uk_recommendation UNIQUE (student_id, target_type, target_id)
- **谁写**：`RecommendService`（修改、新增）
- **谁读**：`RecommendService`
- **现在的数据**：5 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `student_id` | BIGINT | NOT NULL | 哪个学生 → user.id |
| `target_type` | VARCHAR(16) | NOT NULL | exercise（首版只推题）/ material / knowledge_point |
| `target_id` | BIGINT | NOT NULL | 推荐的对象 id（target_type = exercise 时 → exercise.id） |
| `knowledge_point_id` | BIGINT |  | 针对的薄弱知识点 |
| `reason` | VARCHAR(255) |  | 如「上次做错了」「还没做过」 |
| `status` | VARCHAR(16) | NOT NULL DEFAULT 'new' | new / done 做对了 / dismissed 不想做 |
| `source` | VARCHAR(16) | NOT NULL DEFAULT 'system' | system 系统推荐 / teacher 老师布置（干预） |
| `intervention_id` | BIGINT |  | 旧版个人干预用（已去掉），现在一直为空 |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |
| `updated_at` | DATETIME |  | 这一行最后修改的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `student_id` | 7 |
| `target_type` | exercise |
| `target_id` | 1 |
| `knowledge_point_id` | 1 |
| `reason` | 系统根据你的情况布置 |
| `status` | new |
| `source` | teacher |
| `intervention_id` | 1 |
| `created_at` | 2026-11-24 21:42:58 |
| `updated_at` | 2026-11-24 21:42:58 |

### `ai_call_log`（运维）

- **用途**：每一次调用大模型：任务名、提示词版本、输入 / 输出 token、耗时、费用、成功或失败原因、程序检查结果。
- **什么时候写入**：每次调用大模型（看图、识别知识点、AI 对话、出题、判简答、聊天、补充讲解、同音字纠正）都写一行。
- **和哪些表关联**：被 chapter_kp_state、feynman_message、dialogue_message、assistant_message 的 ai_call_id 引用；管理员在“AI 调用记录”页查看。
- **主键 / 唯一约束 / 索引**：PRIMARY KEY (id)
- **谁写**：`AiCallLogService`（修改、新增）
- **谁读**：`AiCallLogService`
- **现在的数据**：97 行

| 列 | 类型 | 约束 / 默认值 | 含义 |
|---|---|---|---|
| `id` | BIGINT | NOT NULL AUTO_INCREMENT | 主键，自增 |
| `task` | VARCHAR(32) | NOT NULL | TOPIC_DRAFT 老师拆要点 / DIALOGUE_TURN 学生对话一轮 |
| `model` | VARCHAR(64) | NOT NULL | 用的哪个模型，例如 deepseek-flash |
| `prompt_version` | VARCHAR(32) | NOT NULL | 提示词模板名，如 dialogue_turn_v1 |
| `input_tokens` | INT |  | 输入用了多少 token |
| `output_tokens` | INT |  | 输出用了多少 token |
| `cost_cny` | DECIMAL(10,6) |  | 费用（元）= 输入 token × 输入单价 ÷ 100 万 + 输出 token × 输出单价 ÷ 100 万 |
| `latency_ms` | INT |  | 从发出到拿到结果的总耗时，包含重试 |
| `success` | TINYINT(1) | NOT NULL | 调用本身是否成功 |
| `passed_check` | TINYINT(1) |  | 输出是否通过检查（格式、长度） |
| `error` | VARCHAR(255) |  | 失败时的原因；成功为空 |
| `request_json` | TEXT |  | 开发阶段保存完整请求，方便排查；上线前改为只存摘要 |
| `response_json` | TEXT |  | 模型返回的文字 |
| `created_at` | DATETIME |  | 这一行创建的时间（MyBatis-Plus 自动填） |

一行真实数据（长的内容截断到 80 字）：

| 列 | 值 |
|---|---|
| `id` | 1 |
| `task` | KP_EXTRACT |
| `model` | deepseek-flash |
| `prompt_version` | kp_extract_v1 |
| `input_tokens` | 14539 |
| `output_tokens` | 2536 |
| `cost_cny` | 0.054049 |
| `latency_ms` | 10837 |
| `success` | 1 |
| `passed_check` | NULL |
| `error` | NULL |
| `request_json` | {"messages":[{"role":"user","content":"你是大学《软件测试》课程的助教。下面是第 3 章「智能测试生成与优化」的全部教学资… |
| `response_json` | {"knowledge_points":[{"name":"自动化测试的概念与局限","summary":"理解自动化测试的本质、核心价值及其五大局限性，从而认… |
| `created_at` | 2026-11-24 21:39:29 |

