# 系统设计 — 软件工程智能学习平台

> 版本 v0.2（W1 设计阶段）
> 相关文档：[需求](requirements.md) · [数据库](database.md) · [接口](api.md) · [测试计划](test-plan.md)

## 1. 系统架构

```mermaid
flowchart TB
    subgraph 客户端
        B["浏览器<br/>Vue 3 + Element Plus + ECharts"]
    end

    subgraph 前端服务
        V["Vite 开发服务器 :5173<br/>（部署时换成 Nginx）"]
    end

    subgraph 后端["Spring Boot 3 后端 :8080"]
        SEC["Spring Security + JWT 过滤器<br/>鉴权、角色校验"]
        subgraph 业务模块
            M1["auth / learn<br/>登录 + 学（A）"]
            M2["record / diagnosis<br/>记 + 诊（B）"]
            M3["profile<br/>画（C）"]
            M4["recommend / intervention<br/>荐 + 干（D）"]
        end
        COM["common<br/>统一返回 Result、全局异常、分页配置"]
    end

    DB[("MySQL 8<br/>开发期可用 H2")]
    FS[["本地磁盘 uploads/<br/>教学资料文件"]]
    AI["算法服务（可选）<br/>Python FastAPI / 大模型<br/>BKT、推荐、5E 智能体"]

    B -->|页面| V
    V -->|"/api/* 代理"| SEC
    SEC --> M1 & M2 & M3 & M4
    M1 & M2 & M3 & M4 --> COM
    M1 & M2 & M3 & M4 -->|MyBatis-Plus| DB
    M1 -->|上传/下载| FS
    M2 & M4 -.->|HTTP，待定| AI
```

| 层 | 技术 | 说明 |
|---|---|---|
| 前端 | Vue 3、Vite、Element Plus、ECharts、Pinia、Vue Router | 已完成模板，mock 模式可独立运行 |
| 后端 | Spring Boot 3、Spring Security、JWT、MyBatis-Plus、Java 21、Maven | 一个工程，按模块分包 |
| 数据库 | MySQL 8（开发期 H2） | 表结构见 [database.md](database.md) |
| 文件 | 本地磁盘 `uploads/` | 数据库只存文件路径 |
| 算法（可选） | Python FastAPI | B、C、D 的智能算法是否单独起服务，待定 |

## 2. 后端分层与包结构

### 2.1 一个请求在后端怎么走

```mermaid
flowchart LR
    REQ["HTTP 请求"] --> F["JwtFilter<br/>解析 token，得到当前用户"]
    F --> C["Controller<br/>接收参数、校验、返回 Result"]
    C --> S["Service<br/>业务逻辑、权限判断、事务"]
    S --> MP["Mapper<br/>MyBatis-Plus 数据访问"]
    MP --> DB[("数据库")]
    S -.->|"异常：BusinessException"| H["GlobalExceptionHandler<br/>转成 {code, message}"]
    C -.-> H
```

| 层 | 职责 | 不应该做的事 |
|---|---|---|
| Controller | 接收请求、校验参数格式、调用 Service、包装成 `Result` | 写业务逻辑、直接调 Mapper |
| Service | 业务规则、权限判断（如「只能改自己的课程」）、事务 | 处理 HTTP 相关的东西 |
| Mapper | 增删改查 | 写业务判断 |
| Entity | 和表一一对应的类 | 直接返回给前端（应该转成 VO） |
| DTO / VO | DTO 接收请求参数，VO 返回给前端 | — |

### 2.2 包结构

```
backend/src/main/java/com/se/platform/
├── PlatformApplication.java        启动类
├── common/                         公共（A 负责）
│   ├── Result.java                 统一返回 {code, message, data}
│   ├── PageResult.java             分页返回 {records, total}
│   ├── BusinessException.java      业务异常
│   ├── GlobalExceptionHandler.java 全局异常处理
│   └── config/                     MyBatis-Plus 分页插件、时间自动填充、跨域
├── auth/                           登录与鉴权（A）
│   ├── controller/  service/  dto/
│   └── security/                   JwtUtil、JwtFilter、SecurityConfig
├── user/                           用户与班级（A）
├── learn/                          学（A）
│   ├── controller/                 CourseController、ChapterController……
│   ├── service/                    CourseService（接口）+ impl/
│   ├── mapper/                     CourseMapper……
│   ├── entity/                     Course、Chapter、KnowledgePoint……
│   └── dto/                        CourseQuery、CourseForm、CourseVO……
├── record/       diagnosis/        记 + 诊（B）
├── profile/                        画（C）
└── recommend/    intervention/     荐 + 干（D）
```

### 2.3 模块之间的调用规则

```mermaid
flowchart LR
    A["learn（A）<br/>课程、知识点、习题"]
    U["user（A）<br/>用户、班级"]
    B["record / diagnosis（B）"]
    C["profile（C）"]
    D["recommend / intervention（D）"]

    B -->|ExerciseService 取题和答案| A
    B -->|KnowledgePointService| A
    C -->|DiagnosisService 取掌握度| B
    C -->|RecordService 取统计| B
    D -->|DiagnosisService 取薄弱点| B
    D -->|ExerciseService 选题| A
    C & D -->|UserService| U
```

- **只调别人的 Service 接口**，不直接用别人的 Mapper、不直接查别人的表、不调别人的 Controller
- 依赖只能顺着主线方向（学 → 记 → 诊 → 画/荐/干），**不能反向依赖**，避免循环
- 别人的 Service 还没写好时，先约定接口方法签名，自己写一个假实现（mock）继续开发

## 3. 关键业务流程

### 3.1 登录与鉴权（时序图）

```mermaid
sequenceDiagram
    actor U as 用户
    participant FE as 前端
    participant F as JwtFilter
    participant AC as AuthController
    participant AS as AuthService
    participant DB as 数据库

    U->>FE: 输入用户名、密码
    FE->>AC: POST /api/auth/login
    AC->>AS: login(username, password)
    AS->>DB: 按 username 查 user
    DB-->>AS: user（含 password_hash）
    alt 用户不存在或密码不匹配
        AS-->>FE: code 400「用户名或密码错误」
    else 校验通过
        AS->>AS: BCrypt 比对成功，生成 JWT（id、role，24 小时）
        AS-->>FE: {token, user}
        FE->>FE: 存入 Pinia + localStorage
    end

    Note over FE,F: 之后的每个请求
    FE->>F: GET /api/courses<br/>Authorization: Bearer token
    alt token 缺失、无效或过期
        F-->>FE: HTTP 401
        FE->>U: 清除登录，跳转登录页
    else token 有效
        F->>F: 解析出当前用户，放进 SecurityContext
        F->>AC: 放行到对应 Controller（再做角色校验）
    end
```

### 3.2 教师建课流程

```mermaid
flowchart TD
    S([教师登录]) --> A["新建课程<br/>状态：草稿"]
    A --> B["新建章节<br/>第 2 章 软件过程模型"]
    B --> C["在章节下新建知识点<br/>填正文、难度、重点/难点"]
    C --> D{"知识点都建好了？"}
    D -- 否 --> C
    D -- 是 --> E["新建习题<br/>题干、4 个选项、答案、解析"]
    E --> F["给习题关联知识点（至少 1 个）"]
    F --> G{"每个知识点都有题？"}
    G -- 否 --> E
    G -- 是 --> H["上传资料（可选）<br/>挂到课程/章节/知识点"]
    H --> I["课程状态改为：已发布"]
    I --> J([学生可以看到这门课])
```

### 3.3 学生学习流程

```mermaid
flowchart TD
    S([学生登录]) --> A["课程列表<br/>只显示已发布课程"]
    A --> B["课程详情<br/>章节 → 知识点树<br/>重点/难点高亮"]
    B --> C["知识点学习页<br/>阅读正文、下载资料"]
    C -->|"上报浏览行为（记）"| C
    C --> D["去练习：做该知识点的题"]
    D --> E["提交答案"]
    E --> F["显示对错、正确答案、解析"]
    F --> G{"继续做题？"}
    G -- 是 --> D
    G -- 否 --> H["查看诊断：各知识点掌握度"]
    H --> I{"有薄弱知识点？"}
    I -- 是 --> J["查看推荐习题"]
    J --> D
    I -- 否 --> K([学习下一个知识点])
    K --> C
```

### 3.4 答题后的数据流：记 → 诊 → 画 / 荐 / 干（时序图）

这是整条主线的核心，首版演示的就是这条链。

```mermaid
sequenceDiagram
    actor S as 学生
    participant FE as 前端
    participant R as RecordService（B）
    participant E as ExerciseService（A）
    participant DG as DiagnosisService（B）
    participant P as ProfileService（C）
    participant RC as RecommendService（D）
    participant AL as AlertService（D）
    actor T as 教师

    S->>FE: 选 B，提交
    FE->>R: POST /api/records/answers {exerciseId, answer}
    R->>E: getExerciseWithAnswer(exerciseId)
    E-->>R: 正确答案 C、关联知识点 [螺旋模型]
    R->>R: 判分：错误，写入 answer_record
    R->>DG: updateMastery(studentId, [螺旋模型])
    DG->>DG: 重新计算掌握度 0.55 → 0.48，写入 mastery
    DG-->>R: 掌握度变化
    R-->>FE: {correct: false, correctAnswer: C, analysis, masteryChanges}
    FE-->>S: 显示错误和解析

    Note over P,AL: 读取最新掌握度（首版：页面打开时实时计算，或教师点「扫描」）
    S->>FE: 打开画像页
    FE->>P: GET /api/profiles/students/{id}
    P->>DG: 读取掌握度
    P-->>FE: 雷达图中「螺旋模型」下降
    S->>FE: 打开推荐页
    FE->>RC: GET /api/recommendations
    RC->>DG: 读取薄弱知识点（< 0.6）
    RC->>E: 选该知识点下没做过/做错的题
    RC-->>FE: 推荐 3 道螺旋模型习题
    T->>AL: POST /api/alerts/scan
    AL->>DG: 读取掌握度
    AL-->>T: 新预警：该学生螺旋模型掌握度 0.48
```

### 3.5 资料上传（时序图）

```mermaid
sequenceDiagram
    actor T as 教师
    participant FE as 前端
    participant MC as MaterialController
    participant MS as MaterialService
    participant FS as 磁盘 uploads/
    participant DB as 数据库

    T->>FE: 选择文件、填写标题、选知识点
    FE->>MC: POST /api/materials（multipart）
    MC->>MS: upload(file, form)
    MS->>MS: 校验：大小 ≤ 20MB、扩展名在白名单内、课程属于当前教师
    alt 校验失败
        MS-->>FE: code 400「不支持的文件类型」
    else 校验通过
        MS->>FS: 保存为 uploads/2026/10/uuid.pdf
        MS->>DB: 插入 material（原文件名、路径、大小）
        MS-->>FE: 资料对象
    end
```

## 4. 状态图

### 4.1 课程状态

```mermaid
stateDiagram-v2
    [*] --> draft: 新建
    draft --> published: 发布
    published --> draft: 撤回修改
    published --> archived: 学期结束归档
    archived --> published: 重新开放
    draft --> [*]: 删除
    archived --> [*]: 删除

    draft: 草稿（学生不可见）
    published: 已发布（学生可学习）
    archived: 已归档（只读）
```

### 4.2 预警状态

```mermaid
stateDiagram-v2
    [*] --> open: 规则扫描触发
    open --> handled: 教师已处理（通常是创建了干预）
    open --> ignored: 教师判断无需处理
    handled --> [*]
    ignored --> [*]

    open: 待处理
    handled: 已处理
    ignored: 已忽略
```

### 4.3 干预状态

```mermaid
stateDiagram-v2
    [*] --> pending: 系统建议 / 教师创建
    pending --> approved: 教师审核通过
    pending --> rejected: 教师驳回
    approved --> done: 措施执行完毕
    rejected --> [*]
    done --> [*]

    pending: 待审核
    approved: 已通过（执行中）
    rejected: 已驳回
    done: 已完成
```

## 5. 关键算法（首版，力求简单）

### 5.1 掌握度（B）

首版用**加权近期正确率**：取该学生在这个知识点上最近 10 次作答，越近的权重越大。

```
mastery = Σ(wᵢ × correctᵢ) / Σ wᵢ ，   wᵢ = 0.8^(n-i)，   i = 1（最早）…n（最近）
```

- 一次都没做：没有掌握度记录，界面显示「未学习」
- 作答次数少于 3 次：结果乘以 `次数 / 3`，避免做对 1 道就算掌握
- W4 之后可以换成 BKT（贝叶斯知识追踪），接口不变

### 5.2 推荐（D）

1. 取学生掌握度 < 0.6 的知识点，按掌握度从低到高排序
2. 每个知识点选题，优先级：做错过的题 > 没做过的题 > 难度接近的题
3. 每次最多推荐 5 道，并写明理由（如「螺旋模型掌握度 0.48」）

### 5.3 预警与干预：两层判断（2026-11-03 实现 A、B，细节见 `docs/计划-干预A和B.md`）

**前提：两层定义。** AI 对话考基础概念（概念层），练习题考衍生、变式的应用（应用层）。每个对话话题关联一个知识点，两层按知识点对上。

| 层 | 好 | 一般 | 不好 | 未测 |
|---|---|---|---|---|
| 概念层（最近一次已结束或暂停的对话里该知识点的话题） | 都自己答出 | 有提示后答出或讲解后会用 | 讲解后仍不会，或跳过 | 没做过 |
| 应用层（掌握度） | ≥ 0.6 | — | 作答 ≥ 3 次且 < 0.6 | 作答 < 3 次 |

| 概念层 | 应用层 | 问题 | 系统建议（老师确认后才推给学生） |
|---|---|---|---|
| 好 / 一般 | 不好 | A 应用问题 | 变式练习：先挑含该学生主要误解选项的题，最多 3 道，留言点出误解 |
| 不好 | 好 / 未测 | B 概念问题 | 复习本章阅读 / 视频 + 重做本章 AI 对话 |
| 不好 | 不好 | AB | 先 B 后 A，同一条干预分两步 |

- **完成**：A 布置的题在通过后都交过一次；B 复习内容都看过且重做完一次对话；AB 两步都完成。
- **效果**：A、AB 看掌握度是否 ≥ 0.6；B 看重做后概念层是否变成好或一般（讲解后会用算有效，再次跳过算无效）。无效的干预会在之后有新数据时重新报警，并注明"上一次干预后仍未解决"。
- **跳过**：一次对话里某知识点的话题全部跳过时，先看更早的真实作答；有就沿用，没有才算概念层不好。这样跳过躲不开预警，也不会盖掉之前答对的结果。
- **不重复**：已有待处理预警或执行中的干预时不报；上一条预警之后没有新作答、新对话也不报。
- 建议和留言用模板生成，不调 AI。

还没做：C 全班共同错误（课上集中讲）、D 干预无效后线下约谈、"概念层未测 + 应用层不好"的先补测，以及原设计的不活跃（`INACTIVE`）、正确率下降（`ACCURACY_DROP`）两条规则。

## 6. 开发与部署环境

| 环境 | 前端 | 后端 | 数据库 |
|---|---|---|---|
| 个人开发 | `npm run dev`（5173） | IDEA 运行（8080） | H2 内存库 / 本机 MySQL |
| 联调（W4） | 同上，`VITE_USE_MOCK=false` | 同上 | 本机 MySQL，导入 `schema.sql` + `data.sql` + `demo-data.sql` |
| 演示 / 交付（W5） | Nginx 容器 | Spring Boot 容器 | MySQL 容器（Docker Compose 一条命令启动） |
