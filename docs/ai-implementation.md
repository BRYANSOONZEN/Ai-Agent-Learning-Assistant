# AI 接入实现方案（首版：AI 对话）

> 适用版本：新版规划（老师 7 步、学生 6 个模块）。
> 首版只实现**学生侧的 AI 对话**：章节对话和错题讲解，共用一套代码。老师侧的 AI 起草（片段分类、话题起草、概要起草、题目配知识点）先用手动录入代替，第二阶段再接。
> **2026-11-07 更新：** 对话规则已改成"不直接给解"，现行规则见下面的规则表和 `docs/计划-AI对话重新设计.md`。标了"第一版"的章节和提示词 v1 的内容只作历史记录；现行提示词是 `dialogue_turn_v3`、`topic_draft_v3`。
> 技术栈：Spring Boot 3.5 + Java 21 + MyBatis-Plus，不另起 Python 服务。

---

## 0. 一句话架构

```
Vue 前端 ──HTTP──▶ Spring Boot 后端 ──HTTPS──▶ 模型 API（DeepSeek / 通义千问，OpenAI 兼容格式）
                        │
                        ├─ DialogueController      接收学生的一句话
                        ├─ DialogueTurnService     一轮对话的总流程
                        │     ├─ PromptBuilder     拼提示词（规则 + 话题 + 状态 + 最近对话）
                        │     ├─ AiClient          调模型（超时、重试、读 token 用量）
                        │     ├─ JudgementParser   把模型返回的 JSON 变成 Java 对象
                        │     ├─ OutputValidator   检查格式、字数、泄露
                        │     └─ DialogueRules     按规则决定下一步（纯 Java，不调模型）
                        ├─ AiCallLogService        每次调用记一条日志（token、费用、耗时）
                        └─ MyBatis-Plus Mapper     读写对话相关的表
```

**三条原则**
1. 前端只调我们的后端；API key 只在后端，放环境变量。
2. 模型每轮只做两件事：判断学生这句属于哪一类、写一句回复。**下一步做什么由 `DialogueRules` 决定。**
3. 练习题原题、正确答案、课件例题**永远不进提示词**。

---

## 1. 包结构

```
backend/src/main/java/com/se/platform/
├── ai/                                   ← 通用的"调模型"能力，和业务无关
│   ├── config/LlmProperties.java         读 application.yml 里的 llm 配置
│   ├── client/AiClient.java              接口：chat(请求) → 回复
│   ├── client/OpenAiCompatibleClient.java  实现：用 RestClient 调 /chat/completions
│   ├── client/ChatRequest.java           请求体（record）
│   ├── client/ChatResponse.java          响应体（record）
│   ├── client/AiCallException.java       超时、报错、格式不对
│   ├── prompt/PromptTemplate.java        读模板文件，替换 {{占位符}}
│   └── log/AiCallLog.java + AiCallLogMapper.java + AiCallLogService.java
│
└── dialogue/                             ← AI 对话这个业务
    ├── controller/DialogueController.java
    ├── service/DialogueTurnService.java  一轮的总流程
    ├── service/DialogueRules.java        规则：决定下一步（重点写单元测试）
    ├── service/PromptBuilder.java
    ├── service/JudgementParser.java
    ├── service/OutputValidator.java
    ├── service/SummaryService.java       按记录生成总结（不调模型）
    ├── model/Judgement.java              模型判断结果（type、hitPoints、reply）
    ├── model/NextAction.java             枚举：CONTINUE / HINT / REVEAL / NEXT_TOPIC / SKIP / PAUSE / FINISH
    ├── entity/*.java + mapper/*.java     4 张表
    └── dto/*.java                        给前端的请求和响应

backend/src/main/resources/
├── prompts/dialogue_turn_v1.md           对话一轮的提示词模板
└── application.yml                       加 llm 配置段
```

---

## 2. 数据表

### 2.1 老师确认的话题（老师侧先手动录入）

```sql
CREATE TABLE dialogue_topic (
  id            BIGINT AUTO_INCREMENT PRIMARY KEY,
  chapter_id    BIGINT       NOT NULL,
  sort_order    INT          NOT NULL DEFAULT 0,
  title         VARCHAR(100) NOT NULL,           -- 每个边界测哪三个位置
  question      TEXT         NOT NULL,           -- 情境问题
  simple_question TEXT,                          -- 无关回答后换的简单问法
  key_points    TEXT         NOT NULL,           -- JSON 数组：["越过边界 17","正好在边上 18","边界内侧 19"]
  hints         TEXT,                            -- JSON 数组：预写提示 L0、L1（AI 失败时兜底）
  reveal_text   TEXT         NOT NULL,           -- 讲解原文（老师确认）
  source_ref    VARCHAR(100),                    -- 课件第 13 页 S2-1
  source_text   TEXT,                            -- 依据的原理句原文
  created_by_ai TINYINT(1)   NOT NULL DEFAULT 0,
  enabled       TINYINT(1)   NOT NULL DEFAULT 1,
  created_at DATETIME, updated_at DATETIME, deleted TINYINT DEFAULT 0
);
```

### 2.2 一次对话

```sql
CREATE TABLE dialogue_session (
  id           BIGINT AUTO_INCREMENT PRIMARY KEY,
  student_id   BIGINT      NOT NULL,
  chapter_id   BIGINT      NOT NULL,
  kind         VARCHAR(16) NOT NULL,             -- CHAPTER 章节对话 / REVIEW 错题讲解
  status       VARCHAR(16) NOT NULL,             -- OPEN / PAUSED / FINISHED
  current_idx  INT         NOT NULL DEFAULT 0,   -- 当前第几个话题（0 开始）
  irrelevant_topics_in_row INT NOT NULL DEFAULT 0,
  created_at DATETIME, updated_at DATETIME
);
```

### 2.3 每个话题的进度（程序状态，总结和掌握度都从这里来）

```sql
CREATE TABLE dialogue_topic_state (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id  BIGINT      NOT NULL,
  topic_idx   INT         NOT NULL,
  topic_id    BIGINT,                            -- CHAPTER 时指向 dialogue_topic
  exercise_id BIGINT,                            -- REVIEW 时指向错题
  wrong_count INT NOT NULL DEFAULT 0,
  stuck_count INT NOT NULL DEFAULT 0,
  irrelevant_count INT NOT NULL DEFAULT 0,
  hint_level  INT NOT NULL DEFAULT -1,           -- -1 还没给提示；0、1
  hit_points  VARCHAR(100),                      -- 已经答到的要点序号，如 "1,2"
  result      VARCHAR(16),                       -- SELF_CORRECT / HINT_CORRECT / REVEALED / SKIPPED_IRRELEVANT
  variant_correct TINYINT(1),                    -- REVIEW：变式题对错，NULL 表示没做
  UNIQUE (session_id, topic_idx)
);
```

### 2.4 对话消息

```sql
CREATE TABLE dialogue_message (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id  BIGINT      NOT NULL,
  topic_idx   INT,
  role        VARCHAR(16) NOT NULL,              -- STUDENT / AI / SYSTEM
  content     TEXT        NOT NULL,
  source      VARCHAR(16),                       -- MODEL 模型写的 / TEMPLATE 预写文字 / RULE 程序生成
  judged_type VARCHAR(16),                       -- 学生这句被判为哪一类
  ai_call_id  BIGINT,                            -- 对应 ai_call_log
  created_at  DATETIME
);
```

### 2.5 调用日志（所有 AI 任务共用）

```sql
CREATE TABLE ai_call_log (
  id             BIGINT AUTO_INCREMENT PRIMARY KEY,
  task           VARCHAR(32) NOT NULL,           -- DIALOGUE_TURN / FRAGMENT_CLASSIFY / ...
  model          VARCHAR(64) NOT NULL,
  prompt_version VARCHAR(32) NOT NULL,           -- dialogue_turn_v1
  input_tokens   INT, output_tokens INT,
  cost_cny       DECIMAL(10,6),
  latency_ms     INT,
  success        TINYINT(1) NOT NULL,            -- 调用本身是否成功
  passed_check   TINYINT(1),                     -- 输出是否通过检查
  error          VARCHAR(255),
  request_json   TEXT,                           -- 开发阶段保存，方便排查；上线前改为只存摘要
  response_json  TEXT,
  created_at     DATETIME
);
```

---

## 3. 实现步骤（按顺序做，每步都能单独验证）

### 总览

| # | 步骤 | 产出 | 怎么验证 |
|---|---|---|---|
| 1 | 配置 | `application.yml` 里的 `llm:` 段 + `LlmProperties` | 启动打印模型名；没设 key 时只提示、不崩溃 |
| 2 | 调模型 | `AiClient`、`OpenAiCompatibleClient`、`FakeAiClient` | P0：真实调用一次，打印回复、token、耗时 |
| 3 | 调用日志 | `ai_call_log` 表 + `AiCallLogService` | H2 里能看到记录，费用算得对 |
| 4 | 提示词模板 | `dialogue_turn_v1.md` + `PromptTemplate` | 占位符替换正确，漏传时报错 |
| 5 | 解析和检查 | `Judgement`、`JudgementParser`、`OutputValidator` | 单元测试：正常、缺字段、超长、泄露、矛盾 |
| 6 | 规则 | `DialogueRules` + `NextAction` | 规则表每一行一个测试 |
| 7 | 对话数据表 | 第 2 节的 4 张表 + 实体 + Mapper + 1 章示例话题 | 能读写 |
| 8 | 一轮总流程 | `PromptBuilder` + `DialogueTurnService` | 用 FakeAiClient 走完一整段对话 |
| 9 | 总结 | `SummaryService` | 输出和预期一致 |
| 10 | 接口 | `DialogueController`，7 个接口 | MockMvc 集成测试 |
| 11 | 前端 | `api/dialogue.js` + `DialogueView.vue` | 浏览器里走完一遍 |
| 12 | P6 探查 | 5 种模拟学生 × 3 个话题 | 双人标注，对照指标 |

- 第 1–3 步是"能调模型"的地基，放在 `ai/` 包，第二阶段老师侧的 AI 起草可以直接复用；第 4–11 步是对话模块本身。
- 只有第 2 步的 P0 和第 12 步要用真实 API key，其余都用 `FakeAiClient`。
- 测试跟着每一步写，不单独留到最后，汇总见本节末尾的"测试汇总"。

### 第 1 步：配置

`application.yml` 追加：

```yaml
llm:
  base-url: https://api.deepseek.com          # 千问：https://dashscope.aliyuncs.com/compatible-mode/v1
  model: deepseek-flash                       # 以官网文档的模型名为准，P0 时确认
  api-key: ${LLM_API_KEY:}                    # 从环境变量读，不写进代码
  connect-timeout-ms: 5000
  read-timeout-ms: 15000
  max-retries: 1
  price-input-per-million: 2.0                # 元 / 百万 token，按选定模型的官网价格填
  price-output-per-million: 8.0
  extra-body: {}                              # 千问 qwen3 系列填 {enable_thinking: false}
```

`LlmProperties.java`：用 `@ConfigurationProperties(prefix = "llm")` 读这些值。

**验证**：启动时打印模型名；没设 `LLM_API_KEY` 时给出明确提示，不让程序直接崩溃。

### 第 2 步：AiClient（只管发请求）

```java
public interface AiClient {
    ChatResult chat(ChatRequest request);       // 失败抛 AiCallException
}

public record ChatMessage(String role, String content) {}          // role: system / user / assistant
public record ChatRequest(List<ChatMessage> messages, double temperature, int maxTokens, boolean jsonMode) {}
public record ChatResult(String content, int inputTokens, int outputTokens, long latencyMs, String model) {}
```

`OpenAiCompatibleClient` 的要点：
- 用 Spring 自带的 `RestClient`，给它设连接超时 5 秒、读超时 15 秒；
- 请求 `POST {base-url}/chat/completions`，请求头 `Authorization: Bearer {api-key}`；
- 请求体：`model`、`messages`、`temperature`、`max_tokens`，`jsonMode` 时加 `response_format: {"type":"json_object"}`，再合并 `extra-body`；
- 从响应中取 `choices[0].message.content` 和 `usage.prompt_tokens / completion_tokens`；
- 超时或 5xx 错误时重试 1 次；4xx（key 错误、余额不足）不重试，直接抛异常。

**测试替身**：再写一个 `FakeAiClient implements AiClient`，按预设返回固定的 JSON。除了第 12 步，所有测试都用它，不花钱、结果固定。

**验证（P0）**：一个只在本地手动运行的测试（`@Tag("live")`，默认不跑），发一句"用一句话解释边界值分析"，打印回复、token 数和耗时。

### 第 3 步：调用日志

- 建 `ai_call_log` 表，写实体类和 Mapper；
- `AiCallLogService.record(task, promptVersion, request, result 或 exception, passedCheck)`；
- 费用 = 输入 token × 输入单价 ÷ 1,000,000 ＋ 输出 token × 输出单价 ÷ 1,000,000。

**验证**：调用一次后，H2 控制台里能看到一条记录，费用算得对。

### 第 4 步：提示词模板

文件 `resources/prompts/dialogue_turn_v1.md`：

```
你是《软件工程》软件测试单元的辅导者，正在和一名本科生做巩固对话。
规则：
1. 只根据【依据资料】说话，不补充资料以外的事实。
2. 回复不超过 80 个汉字，只问一个问题；语气友好、中性，不夸大学生的回答。
3. 先肯定学生说对的部分，再针对没说到的要点追问或提示。
4. 不要直接说出全部要点；本轮要求见【本轮要求】。
5. 只输出 JSON，格式如下，不要输出其他内容：
{"type": "...", "hit_points": [...], "reply": "..."}
type 只能是：correct（要点全部答到）、partial（答到一部分）、wrong（相关但答错）、
irrelevant（和问题无关，如"哈哈"、表情、闲聊）、want_answer（直接要答案）、off_topic（问资料以外的问题）。
hit_points 填学生这句话答到的要点编号（从 1 开始）。

【话题】{{topic_title}}
【情境问题】{{question}}
【要点】
{{key_points}}
【依据资料】{{source_ref}}：{{source_text}}
【程序状态】本话题已尝试 {{attempts}} 次，已用提示 {{hints_used}} 次，此前已答到的要点：{{hit_before}}
【本轮要求】{{instruction}}
【最近对话】
{{recent}}
【学生这一句】{{student_text}}
```

`{{instruction}}` 由程序填，例如：
- 默认："如果没答全，给出一个更具体的提示，但不要说出要点原文。"
- 学生点了"我卡住了"：不经过模型，直接用预写提示（见第 8 步）。

`PromptTemplate`：启动时读取文件，`render(Map<String,String>)` 替换占位符。占位符没填就报错，防止漏传。

### 第 5 步：判断结果的解析和检查

```java
public record Judgement(JudgedType type, List<Integer> hitPoints, String reply) {}
public enum JudgedType { CORRECT, PARTIAL, WRONG, IRRELEVANT, WANT_ANSWER, STRUGGLING, OFF_TOPIC, CLARIFY }
```

`JudgementParser`：用 Jackson 解析；字段缺失或 type 不在 6 种之内，抛 `AiCallException("格式不对")`。

`OutputValidator` 检查：

| 检查 | 不通过时 |
|---|---|
| reply 不超过 120 个字符 | 换成预写提示 |
| hit_points 的编号在 1 到要点数之间 | 去掉越界的编号 |
| reply 不含本章练习题的题干片段、正确选项文字（禁用词表由程序生成） | 换成预写提示，记为泄露 |
| type 和 hit_points 自相矛盾（如 CORRECT 但没答全要点） | 以 hit_points 为准重新计算 type |

最后一条很重要：**是否答全由程序数 hit_points 决定**，不完全相信模型的 type。

### 第 6 步：规则 DialogueRules（纯 Java，重点写测试）

输入：当前话题状态 + 本轮事件（学生发言的判断结果，或"我卡住了"）＋ 规则参数。
输出：下一步动作 `NextAction`，以及要更新的状态。

| 这一句 | 条件 | 系统做什么 | 话题结果 |
|---|---|---|---|
| correct | 要点全齐，之前没错、没卡住、没用提示 | 模型一句肯定，进入下一个话题 | SELF_CORRECT 自己答对 |
| correct | 要点全齐，其他情况 | 模型一句肯定，进入下一个话题 | HINT_CORRECT 提示后答对 |
| wrong / partial | 有新要点 | 模型肯定并追问，不升级 | — |
| wrong / partial | 没有新要点 | 升一级提示（L1→L2→L3），L3 之后一次给一条要点；要点都有了就出变式题 | — |
| want_answer | 作答过 | 同上：升一级，L3 之后给要点 | — |
| want_answer | 从没作答 | 模型提示，请学生先试；累计 3 次跳过，不给答案 | 第 3 次 SKIPPED_IRRELEVANT |
| struggling（说不会、太难，或点"我卡住了"） | 作答过 | 同上：升一级，L3 之后给要点 | — |
| struggling | 从没作答 | 模型鼓励并提示，最多到 L3，不给要点，不累计跳过 | — |
| irrelevant | 第 1 次 | 模型接话并提醒回到问题 | — |
| irrelevant | 第 2 次 | 跳过，不给答案 | SKIPPED_IRRELEVANT |
| clarify / off_topic | 不计次 | 模型解释题意，或说明资料里没有 | — |
| 到 8 轮上限 | 作答过进入变式；没作答过跳过 | 都不显示答案 | 没作答过 SKIPPED_IRRELEVANT |
| 变式题作答 | 答全 | 显示完整讲解 | REVEALED_APPLIED 讲解后会用 |
| 变式题作答 | 第 2 次仍没答全 | 显示变式答案和完整讲解 | REVEALED 讲解后仍不会 |
| 变式阶段 want_answer / irrelevant | 累计 3 次 | 结束，不显示答案 | REVEALED |
| 变式阶段 struggling | 不累计 | 继续提示，到 8 轮上限结束 | — |
| 跳过之后 | 连续跳过 2 个话题（且不是最后一个） | 暂停，等学生点"继续对话" | — |

规则参数是 `DialogueRules` 里的常量，现在还不能按章节修改。

**验证**：`DialogueRulesTest` 把上表每一行写成一个测试用例。这一步完全不需要 API。

### 第 7 步：对话数据表

- 按第 2 节建 `dialogue_topic`、`dialogue_session`、`dialogue_topic_state`、`dialogue_message`，写进 `schema.sql`；
- 每张表写实体类和 Mapper；
- 在 `data.sql` 里录入 1 章示例话题（边界值分析，3 个话题），代替老师侧的手动录入。

**验证**：一个简单的 Mapper 测试，能插入和读出。

### 第 8 步：一轮对话的总流程 DialogueTurnService

学生发一句话时：

```
1. 读 session 和当前话题的 state；status 不是 OPEN 就返回错误
2. 保存学生的消息
3. 用 PromptBuilder 拼提示词（话题、要点、依据、状态、最近 6 条消息、学生这句）
4. 调 AiClient（jsonMode=true，temperature=0.2，maxTokens=300）
   ├─ 成功 → JudgementParser 解析 → OutputValidator 检查
   └─ 失败（超时、报错、格式不对）→ 走兜底：本轮按 WRONG 处理，回复用预写提示
5. 记 ai_call_log
6. DialogueRules 决定下一步，更新 state
7. 按动作生成要显示的消息：
   HINT        → 模型的 reply（检查不通过就用预写提示）
   CONTINUE    → 固定开头＋简单问法（预写，不用模型）
   REVEAL/SKIP → 老师确认的讲解原文（预写，不用模型）
   NEXT_TOPIC  → 一句肯定（可用模型的 reply）＋下一个话题的情境问题（预写）
   FINISH      → "所有话题都完成了"
8. 保存这些消息，返回给前端：新消息列表、当前话题序号、每个话题的结果、是否结束或暂停
```

学生点"我卡住了"时：**不调模型**，直接走上面的 6、7、8，显示下一条预写提示。

**验证**：用 FakeAiClient 走完一整段对话：开始 → 答对 → 部分对 → 卡住 → 无关两次 → 结束。

**为什么讲解、换话题用预写文字**：这几步内容最关键，用老师确认过的文字最稳、不花钱、不会泄露。模型只负责最需要"针对学生这句话"的部分：判断和提示。

### 第 9 步：总结 SummaryService（不调模型）

按 `dialogue_topic_state` 生成：
- 完成 x / N 个话题；
- 做得好的：SELF_CORRECT、HINT_CORRECT 的话题；
- 需要改进的：REVEALED 的话题，并附上对应的 `source_ref`；
- 没有作答的：SKIPPED_IRRELEVANT 的话题；
- REVIEW 还要列出变式题的对错。

### 第 10 步：接口

沿用现有的统一返回格式 `Result {code, message, data}`。

| 方法 | 路径 | 用途 |
|---|---|---|
| POST | `/api/dialogue/sessions` | 开始对话：`{chapterId, kind}`，返回开场信息（话题列表、规则说明） |
| POST | `/api/dialogue/sessions/{id}/messages` | 学生发一句：`{text}`，返回本轮新消息和状态 |
| POST | `/api/dialogue/sessions/{id}/stuck` | 我卡住了 |
| POST | `/api/dialogue/sessions/{id}/variant` | 错题讲解里提交变式题：`{topicIdx, option}` |
| POST | `/api/dialogue/sessions/{id}/resume` | 暂停后继续 |
| GET  | `/api/dialogue/sessions/{id}` | 恢复对话（刷新页面后） |
| GET  | `/api/dialogue/sessions/{id}/summary` | 总结 |

错题讲解开始时，后端根据学生第一次作答的错题生成话题：情境问题和要点来自题目的 `review` 配置和每个错误选项的误解说明，讲解原文来自题目解析。

### 第 11 步：前端

- `frontend/src/api/dialogue.js`：封装上面 7 个接口；
- `DialogueView.vue`：照着 `proposal-lesson3/页面原型.html` 里的对话页做，分开场页、对话、总结三种状态；
- 等待模型回复时显示"正在思考…"，并禁用发送按钮，防止重复发送。

### 测试汇总（跟着各步写）

| 层 | 用什么 | 测什么 |
|---|---|---|
| 单元测试 | JUnit | DialogueRules（每条规则）、JudgementParser（正常、缺字段、乱码）、OutputValidator（超长、泄露、矛盾） |
| 集成测试 | MockMvc + FakeAiClient | 一整段对话：开始 → 答对 → 部分对 → 卡住 → 无关两次 → 结束 → 总结 |
| 真实冒烟 | `@Tag("live")`，手动运行 | P0：真实调用一次，看延迟、token、费用 |

### 第 12 步：对话探查 P6（真实 API）

写一个命令行小程序（也可以是 live 测试），用 5 种模拟学生把"边界值分析"的 3 个话题各跑一遍：

| 模拟学生 | 每个话题的回答 |
|---|---|
| 好学生 | 一次答全要点 |
| 部分对 | 只答到一部分，再补全 |
| 答错 | 两次答错 |
| 无关 | "哈哈"、表情 |
| 要答案 | "直接告诉我" |

输出一张表：学生这句、模型判断的 type 和 hit_points、程序的动作、显示的回复。小组双人标注"type 判得对不对""回复好不好（1–5 分）"。

候选指标：type 判断一致率 ≥ 85%；回复平均分 ≥ 4；泄露 0 次；每轮 p95 ≤ 3 秒；每次对话费用 ≤ 0.1 元。

---

## 4. 一轮对话的时序（以"17 和 18"为例）

```
前端                 DialogueController     DialogueTurnService         AiClient / 模型
 │ POST messages {"17 和 18"}  │                      │                          │
 │───────────────────────────▶│ turn(sessionId,text) │                          │
 │                            │─────────────────────▶│ 读 session、state        │
 │                            │                      │ 存学生消息                │
 │                            │                      │ 拼提示词                  │
 │                            │                      │─────── chat() ──────────▶│
 │                            │                      │◀── {"type":"partial",    │
 │                            │                      │     "hit_points":[1,2],   │
 │                            │                      │     "reply":"17 和 18 选得对…"}
 │                            │                      │ 解析、检查、记日志         │
 │                            │                      │ 规则：错 1 次 < 2 → HINT   │
 │                            │                      │ 存 AI 消息、更新 state     │
 │◀────────── {messages, topicIdx, results, finished:false} ─────────────────────│
```

---

## 5. 失败与降级

| 情况 | 学生看到 | 记录 |
|---|---|---|
| 模型超时（15 秒）或报错，重试后仍失败 | 预写提示，或话题问题原文 | ai_call_log.success=0 |
| 返回不是 JSON，或字段不对 | 同上 | error="格式不对" |
| 回复超长、含练习题内容 | 预写提示 | passed_check=0 |
| API key 没配或余额不足 | 对话页提示"AI 暂时不可用"，其他页面照常使用 | 启动时和调用时都告警 |
| 连续 3 次调用失败 | 本次对话转成"只用预写提示"模式 | 告警 |

---

## 6. 成本估算

- 每轮：约 1,500–2,500 输入 token（规则、话题、依据、最近对话），约 100 输出 token；
- 一次章节对话：3 个话题，约 8–12 轮；
- 按 deepseek-flash 高峰价（输入 2 元、输出 8 元 / 百万 token）：每轮约 0.005 元，**一次对话约 0.06 元**；
- "我卡住了"、讲解、换话题都不调模型，实际费用还会更低。

---

## 7. 分工和工时（三人版）

| 部分 | 负责人 | 估时 |
|---|---|---|
| 第 1–3 步：配置、AiClient、调用日志 | B（AI 与评测） | 6h |
| 第 4–5 步：提示词模板、解析、检查 | B | 5h |
| 第 6 步：规则 | A（E1 引导引擎） | 4h |
| 第 7 步：数据表、Mapper、示例话题 | D（数据与接口） | 4h |
| 第 8–9 步：总流程、总结 | A | 6h |
| 第 10 步：接口 | A | 4h |
| 第 11 步：前端对话页 | A | 8h |
| 测试（跟着各步写） | 各自写自己模块的单元测试；集成测试 A | 6h |
| 第 12 步：探查 P6 和标注 | B 跑，B、D 标注，A 裁决 | 5h |
| **合计** |  | **约 48h** |

---

## 8. 完成标准

- [ ] 不设 API key 时，系统能启动，对话页提示"AI 暂时不可用"
- [ ] P0：真实调用一次成功，日志里有 token、费用、耗时
- [ ] DialogueRules 的每条规则都有测试并通过
- [ ] 用 FakeAiClient 能走完一整段对话，总结和预期一致
- [ ] 提示词里不含练习题原题、答案和例题（写一个测试检查拼好的提示词）
- [ ] P6：5 种模拟学生跑完，指标达到候选值，或者记录下未达标的原因
