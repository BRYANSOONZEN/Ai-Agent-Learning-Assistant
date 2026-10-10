# 接口文档 — 软件工程智能学习平台

> 版本 v0.2（W1 设计阶段）
> 前端 `src/api/<模块>.js` 里的函数和这里一一对应。**改接口要先改这份文档。**

## 1. 通用约定

### 1.1 基本信息

| 项目 | 约定 |
|---|---|
| 基础路径 | `/api`（开发时 Vite 把 `/api` 代理到 `http://localhost:8080`） |
| 数据格式 | 请求和响应都是 JSON（UTF-8）；文件上传用 `multipart/form-data` |
| 字段命名 | JSON 字段用驼峰 `camelCase`，如 `teacherId`、`createdAt` |
| 时间格式 | `yyyy-MM-dd HH:mm:ss`，日期 `yyyy-MM-dd` |
| 路径风格 | RESTful：名词复数 + HTTP 方法表示动作，如 `GET /api/courses`、`DELETE /api/courses/1` |
| 模块前缀 | 每个模块一个前缀：`/auth` `/courses` `/chapters` `/knowledge-points` `/exercises` `/materials`（A），`/records` `/diagnosis`（B），`/profiles`（C），`/recommendations` `/alerts` `/interventions`（D） |

### 1.2 统一响应格式

所有接口都返回：

```json
{ "code": 200, "message": "ok", "data": { } }
```

分页列表的 `data` 固定是：

```json
{ "records": [ ], "total": 123 }
```

分页参数统一用 `page`（从 1 开始）和 `size`（默认 10，最大 100）。

### 1.3 错误码

| code | 含义 | HTTP 状态码 | 前端表现 |
|---|---|---|---|
| 200 | 成功 | 200 | — |
| 400 | 参数错误（如必填项为空） | 200 | 弹出 `message` |
| 401 | 未登录或 token 过期 | **401** | 清除登录状态，跳转登录页 |
| 403 | 已登录但没有权限 | 200 | 弹出 `message` |
| 404 | 数据不存在 | 200 | 弹出 `message` |
| 409 | 业务冲突（如章节下还有知识点，不能删除） | 200 | 弹出 `message` |
| 500 | 服务器内部错误 | 200 | 弹出「服务器错误」，详细信息只写日志 |

> 为什么只有 401 用 HTTP 状态码：前端 `request.js` 只靠 HTTP 401 判断登录过期，其他错误读的是响应体里的 `code` 和 `message`。这样后端的中文错误信息能直接显示给用户。

错误响应示例：

```json
{ "code": 409, "message": "该章节下还有 3 个知识点，请先删除或移走", "data": null }
```

### 1.4 鉴权

- 除了 `POST /api/auth/login`，所有接口都要带请求头：`Authorization: Bearer <token>`
- token 是 JWT，有效期 24 小时，里面存用户 id 和角色
- 角色权限见第 7 节

## 2. 登录与用户（成员 A）

### POST /api/auth/login 登录

请求：

```json
{ "username": "teacher01", "password": "******" }
```

响应 `data`：

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "user": { "id": 2001, "name": "李老师", "role": "teacher" }
}
```

错误：用户名或密码错误 → `400`「用户名或密码错误」（不告诉是哪个错，防止猜账号）；账号被禁用 → `403`。

> ⚠️ 前端改动：目前 `api/auth.js` 发的是 `{ username, role }`（mock 阶段手选角色），接真实后端时改成 `{ username, password }`，角色由后端返回。

### GET /api/auth/me 当前用户

响应 `data`：`{ "id": 2001, "username": "teacher01", "name": "李老师", "role": "teacher", "clazzId": null }`

### 用户与班级管理（P2，管理员）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/users?role=&keyword=&page=&size=` | 用户分页列表 |
| POST | `/api/users` | 新建用户 `{username, password, name, role, clazzId}` |
| PUT | `/api/users/{id}` | 修改（不含密码） |
| DELETE | `/api/users/{id}` | 逻辑删除 |
| GET | `/api/classes` | 班级列表 |

## 3. 学：课程内容（成员 A）

### 3.1 课程

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| GET | `/api/courses?page=&size=&keyword=` | 分页列表，按名称模糊搜索 | 全部 |
| GET | `/api/courses/{id}` | 课程详情 | 全部 |
| POST | `/api/courses` | 新建 | 教师、管理员 |
| PUT | `/api/courses/{id}` | 修改 | 课程的教师、管理员 |
| DELETE | `/api/courses/{id}` | 逻辑删除 | 课程的教师、管理员 |
| GET | `/api/courses/{id}/tree` | 内容树：章节 → 知识点 | 全部 |

**可见范围**：学生只能看到 `published` 的课程；教师看到自己的全部课程；管理员看到全部。

课程对象（列表和详情返回）：

```json
{
  "id": 1,
  "name": "软件工程导论",
  "description": "软件生命周期、过程模型与项目管理基础。",
  "teacherId": 2001,
  "teacher": "李老师",
  "status": "published",
  "chapterCount": 1,
  "studentCount": 10,
  "createdAt": "2026-09-01 10:00:00"
}
```

`teacher`、`chapterCount`、`studentCount` 是计算出来的只读字段（和前端 mock 数据的结构一致）。

新建 / 修改请求：

```json
{ "name": "软件工程导论", "description": "...", "status": "draft" }
```

- `name` 必填，最长 100 字；`status` 只能是 `draft` / `published` / `archived`
- 新建时 `teacherId` 取当前登录的教师；管理员可以在请求里传 `teacherId` 指定教师

> ⚠️ 前端改动：`CourseFormDialog.vue` 里的「授课教师」输入框改成只读显示（教师由登录身份决定）。

内容树 `GET /api/courses/1/tree` 响应 `data`：

```json
[
  {
    "id": 10, "title": "第 2 章 软件过程模型", "sortOrder": 1,
    "children": [],
    "knowledgePoints": [
      { "id": 100, "name": "瀑布模型", "difficulty": 1, "isKey": true, "isDifficult": false },
      { "id": 103, "name": "螺旋模型", "difficulty": 3, "isKey": true, "isDifficult": true }
    ]
  }
]
```

### 3.2 章节

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| GET | `/api/courses/{courseId}/chapters` | 某课程的章节列表（平铺，按 `sortOrder`） | 全部 |
| POST | `/api/chapters` | 新建 `{courseId, parentId, title, sortOrder}` | 教师、管理员 |
| PUT | `/api/chapters/{id}` | 修改 `{title, sortOrder, parentId}` | 教师、管理员 |
| DELETE | `/api/chapters/{id}` | 删除；下面还有子节或知识点时返回 `409` | 教师、管理员 |

校验：`parentId` 指向的章节必须属于同一门课，并且本身是「章」（`parentId` 为空），保证最多两级。

### 3.3 知识点

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| GET | `/api/knowledge-points?courseId=&chapterId=&keyword=` | 列表（不分页，一门课的知识点数量有限）；不返回 `content` | 全部 |
| GET | `/api/knowledge-points/{id}` | 详情，含 `content` 正文、相关资料、习题数量 | 全部 |
| POST | `/api/knowledge-points` | 新建 | 教师、管理员 |
| PUT | `/api/knowledge-points/{id}` | 修改 | 教师、管理员 |
| DELETE | `/api/knowledge-points/{id}` | 删除；还有关联习题时返回 `409` | 教师、管理员 |

新建 / 修改请求：

```json
{
  "courseId": 1, "chapterId": 10, "name": "螺旋模型",
  "content": "## 螺旋模型\n\n螺旋模型由 Boehm 于 1988 年提出……",
  "difficulty": 3, "isKey": true, "isDifficult": true, "sortOrder": 4
}
```

详情响应 `data`（学生的知识点学习页用）：

```json
{
  "id": 103, "courseId": 1, "chapterId": 10, "name": "螺旋模型",
  "content": "## 螺旋模型 ...", "difficulty": 3, "isKey": true, "isDifficult": true,
  "exerciseCount": 5,
  "materials": [ { "id": 1, "title": "第 2 章课件", "contentType": "application/pdf", "fileSize": 2048000 } ]
}
```

### 3.4 习题

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| GET | `/api/exercises?courseId=&knowledgePointId=&type=&keyword=&page=&size=` | 分页列表 | 全部（学生看不到答案） |
| GET | `/api/exercises/{id}` | 详情 | 全部（学生看不到答案） |
| POST | `/api/exercises` | 新建 | 教师、管理员 |
| PUT | `/api/exercises/{id}` | 修改（关联知识点整体替换） | 教师、管理员 |
| DELETE | `/api/exercises/{id}` | 逻辑删除 | 教师、管理员 |

习题对象：

```json
{
  "id": 501,
  "courseId": 1,
  "type": "single_choice",
  "stem": "以下哪种过程模型最强调风险分析？",
  "options": ["瀑布模型", "增量模型", "螺旋模型", "原型模型"],
  "answer": "C",
  "analysis": "螺旋模型每一圈都包含风险分析阶段……",
  "difficulty": 2,
  "knowledgePoints": [ { "id": 103, "name": "螺旋模型" } ]
}
```

- **学生调用时，`answer` 和 `analysis` 不返回**（为 `null`）。提交答案后由 B 的 `POST /api/records/answers` 返回
- 新建 / 修改请求用 `knowledgePointIds: [103]` 代替 `knowledgePoints`
- 校验：单选题 `options` 必须 2～6 项，`answer` 必须是对应范围内的字母，`knowledgePointIds` 至少 1 个，且都属于同一门课

### 3.5 资料（P1）

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| GET | `/api/materials?courseId=&chapterId=&knowledgePointId=` | 列表 | 全部 |
| POST | `/api/materials` | 上传，`multipart/form-data` | 教师、管理员 |
| GET | `/api/materials/{id}/download` | 下载文件（返回文件流，不是 JSON） | 全部 |
| DELETE | `/api/materials/{id}` | 逻辑删除 | 教师、管理员 |

上传表单字段：`file`（必填）、`courseId`（必填）、`title`（不填就用文件名）、`chapterId`、`knowledgePointId`。

限制：单个文件 ≤ 20 MB；只允许 `pdf ppt pptx doc docx md txt png jpg jpeg`，按扩展名和 Content-Type 双重检查；文件存到 `uploads/yyyy/MM/<uuid>.<扩展名>`，**不用原文件名存盘**（防止重名覆盖和路径穿越攻击）。

## 4. 记 + 诊（成员 B，草案）

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| POST | `/api/records/answers` | 提交答案，自动判分并更新掌握度 | 学生 |
| GET | `/api/records/answers?courseId=&studentId=&page=&size=` | 答题记录；学生只能查自己的，教师可传 `studentId` | 全部 |
| POST | `/api/records/events` | 上报学习行为 `{eventType, targetId, durationSec}` | 学生 |
| GET | `/api/diagnosis/students/{id}?courseId=` | 学生诊断：各知识点掌握度 + 薄弱点 | 本人、教师 |
| GET | `/api/diagnosis/classes/{id}?courseId=` | 班级诊断：各知识点平均掌握度 | 教师 |

提交答案请求：`{ "exerciseId": 501, "answer": "B", "timeSpentSec": 35 }`

响应 `data`：

```json
{
  "correct": false,
  "correctAnswer": "C",
  "analysis": "螺旋模型每一圈都包含风险分析阶段……",
  "masteryChanges": [ { "knowledgePointId": 103, "name": "螺旋模型", "before": 0.55, "after": 0.48 } ]
}
```

学生诊断响应 `data`：

```json
{
  "studentId": 1001,
  "items": [ { "knowledgePointId": 103, "name": "螺旋模型", "mastery": 0.48, "attemptCount": 6, "level": "weak" } ],
  "weakPoints": [ { "knowledgePointId": 103, "name": "螺旋模型", "mastery": 0.48 } ]
}
```

`level`：`weak`（< 0.6）/ `medium`（0.6～0.8）/ `good`（≥ 0.8）。

## 5. 画（成员 C，草案）

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| GET | `/api/profiles/students/{id}?courseId=` | 学生画像（结构同前端 `mockProfile`，另加 `tags`） | 本人、教师 |
| GET | `/api/profiles/classes/{id}?courseId=` | 班级总览：学生列表 + 关键指标 | 教师 |
| POST | `/api/profiles/refresh?courseId=` | 重新计算画像快照 | 教师、管理员 |

> ⚠️ 前端改动：`api/profile.js` 现在用的是 `/students/{id}/profile`，统一改成 `/profiles/students/{id}`，和模块前缀约定一致。

## 6. 荐 + 干（成员 D，草案）

| 方法 | 路径 | 说明 | 角色 |
|---|---|---|---|
| GET | `/api/recommendations?courseId=` | 我的推荐列表 | 学生 |
| POST | `/api/recommendations/generate?courseId=&studentId=` | 为学生生成推荐 | 教师、系统 |
| POST | `/api/recommendations/{id}/feedback` | 反馈 `{status: "done" \| "dismissed"}` | 学生 |
| GET | `/api/alerts?status=&clazzId=&page=&size=` | 预警列表 | 教师 |
| PUT | `/api/alerts/{id}` | 修改状态 `{status: "handled" \| "ignored"}` | 教师 |
| POST | `/api/alerts/scan?courseId=` | 按规则扫描生成预警 | 教师、系统 |
| GET | `/api/interventions?status=&page=&size=` | 干预列表 | 教师 |
| POST | `/api/interventions` | 创建 `{alertId, studentId, content}` | 教师、系统 |
| POST | `/api/interventions/{id}/review` | 审核 `{approved: true, comment: "..."}` | 教师 |
| POST | `/api/interventions/{id}/complete` | 标记已完成 | 教师 |

## 7. 权限矩阵

| 接口 | 学生 | 教师 | 管理员 |
|---|---|---|---|
| 登录、当前用户 | ✅ | ✅ | ✅ |
| 课程/章节/知识点/习题/资料：查看 | ✅（仅已发布课程，无答案） | ✅ | ✅ |
| 课程/章节/知识点/习题/资料：增删改 | ❌ | ✅（仅自己的课程） | ✅ |
| 提交答案、上报行为 | ✅ | ❌ | ❌ |
| 答题记录、诊断、画像：本人 | ✅ | — | — |
| 答题记录、诊断、画像：他人 / 班级 | ❌ | ✅ | ✅ |
| 推荐：查看与反馈 | ✅ | ❌ | ❌ |
| 预警、干预 | ❌ | ✅ | ✅ |
| 用户与班级管理 | ❌ | ❌ | ✅ |

## 8. 变更记录

| 日期 | 版本 | 变更 | 作者 |
|---|---|---|---|
| 2026-10-10 | v0.2 | 首版：全组接口清单；登录改为用户名 + 密码；画像路径统一为 `/profiles/...` | A |
