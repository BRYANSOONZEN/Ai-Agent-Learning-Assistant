# 必须掌握的知识点

> 配合 [learning-path.md](learning-path.md) 使用：视频负责「教」，这份文件负责「检查学会没有」。
> 每一节最后有**自测题**，能不看资料答出来，才算掌握。答不出的题，带着去问 Claude。

---

## 第 1 部分：Java 基础

### 1.1 类和对象

**类**是模板，**对象**是按模板造出来的具体东西。

```java
public class Course {                  // 类：描述「课程」长什么样
    private Long id;                   // 字段（属性）：private 表示外部不能直接访问
    private String name;
    private String status;

    public Course(Long id, String name, String status) {  // 构造方法：new 的时候调用
        this.id = id;                  // this.id 是字段，id 是参数
        this.name = name;
        this.status = status;
    }

    public String getName() { return name; }              // getter：读字段
    public void setName(String name) { this.name = name; } // setter：改字段
}

Course c = new Course(1L, "软件工程导论", "published");   // 对象：一门具体的课
```

| 要点 | 说明 | 项目里哪里用到 |
|---|---|---|
| 字段用 `private` + getter/setter | 封装，外部只能通过方法访问 | 每个 Entity、DTO、VO |
| 构造方法 | 和类同名、没有返回值 | — |
| `this` | 指当前对象，用来区分字段和同名参数 | 到处 |
| `Long` 和 `long` | `Long` 是包装类，可以为 `null`；数据库的 id 用 `Long` | 所有 id 字段 |

> 项目里会用 **Lombok** 的 `@Data` 注解自动生成 getter/setter，但你必须先知道它帮你生成了什么。

### 1.2 接口和实现类

**接口**只规定「能做什么」，**实现类**负责「具体怎么做」。

```java
public interface CourseService {               // 接口：只有方法签名，没有方法体
    Course getById(Long id);
}

public class CourseServiceImpl implements CourseService {   // 实现类
    @Override                                   // 表示「这是在实现接口里的方法」
    public Course getById(Long id) { /* 具体逻辑 */ }
}
```

**为什么要这样分？** 别的模块（比如 B 的「记」）只依赖 `CourseService` 接口，不关心你内部怎么实现。你改实现，别人的代码不用动。这就是 `docs/design.md` 2.3 节「只调别人的 Service 接口」的原因。

### 1.3 继承和多态（了解即可）

- `class A extends B`：A 继承 B 的字段和方法
- 多态：`CourseService s = new CourseServiceImpl();`，变量写成接口类型，实际装的是实现类对象
- 项目里：`CourseServiceImpl extends ServiceImpl<CourseMapper, Course>`，继承 MyBatis-Plus 现成的增删改查方法

### 1.4 集合和泛型

```java
List<Course> list = new ArrayList<>();   // <Course> 是泛型：这个 List 里只能放 Course
list.add(c);
Course first = list.get(0);              // 取出来直接是 Course，不用强制转换

Map<String, Integer> count = new HashMap<>();   // 键值对
count.put("published", 4);
```

| 集合 | 特点 | 项目里 |
|---|---|---|
| `List` / `ArrayList` | 有顺序、可重复 | 查询结果列表、`records` |
| `Map` / `HashMap` | 键 → 值 | 统计计数 |
| `Set` / `HashSet` | 不重复 | 去重的知识点 id |

**泛型类**：`Result<T>` 里的 `T` 是「占位类型」，用的时候再确定：`Result<Course>`、`Result<List<Course>>`。

### 1.5 Stream 和 Lambda

```java
List<Course> published = list.stream()
        .filter(c -> c.getStatus().equals("published"))   // Lambda：c -> 条件
        .toList();

List<String> names = list.stream().map(Course::getName).toList();  // 方法引用
List<Course> page2 = list.stream().skip(10).limit(10).toList();    // 分页：第 2 页，每页 10 条
```

`c -> ...` 读作「对每个 c，做……」。

### 1.6 异常

```java
public class BusinessException extends RuntimeException {   // 自定义异常
    private final int code;
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}

if (course == null) {
    throw new BusinessException(404, "课程不存在");   // 抛出：停止执行，交给上层处理
}
```

项目里**不在每个方法里 try/catch**，而是统一抛 `BusinessException`，由 `GlobalExceptionHandler` 接住并转成 `{code: 404, message: "课程不存在"}` 返回给前端。

### 1.7 注解

注解是贴在代码上的**标签**，本身不做事，由框架读取后决定怎么处理。

```java
@RestController            // 告诉 Spring：这个类处理 HTTP 请求，返回 JSON
@RequestMapping("/api/courses")
public class CourseController {
    @GetMapping("/{id}")   // 告诉 Spring：GET /api/courses/{id} 交给这个方法
    public Result<Course> get(@PathVariable Long id) { ... }
}
```

### ✏️ 第 1 部分自测题

1. `private` 字段为什么还要写 getter/setter？
2. 为什么数据库 id 用 `Long` 而不用 `long`？
3. 接口和实现类分开，好处是什么？举我们项目里的例子。
4. `List<Course>` 里的 `<Course>` 起什么作用？
5. 用 Stream 写：从课程列表里取出所有已发布课程的名称。
6. `throw` 了一个异常但没有 catch，程序会怎样？在我们项目里谁来接住它？
7. 注解自己会执行代码吗？

---

## 第 2 部分：Web 和 HTTP

### 2.1 一次请求的完整过程

```
你在页面点「搜索」
  → 前端 api/course.js 发出请求：GET /api/courses?page=1&size=10&keyword=导论
  → Vite 代理转发到 http://localhost:8080
  → Spring Boot：JwtFilter 检查登录 → CourseController → CourseService → CourseMapper → 数据库
  → 返回 JSON：{"code":200,"message":"ok","data":{"records":[...],"total":1}}
  → 前端 request.js 取出 data → 页面表格刷新
```

### 2.2 HTTP 请求和响应

| 组成 | 例子 | 在 Spring 里怎么拿 |
|---|---|---|
| 方法 | `GET` 查、`POST` 新建、`PUT` 修改、`DELETE` 删除 | `@GetMapping`、`@PostMapping`…… |
| 路径参数 | `/api/courses/1` 里的 `1` | `@PathVariable Long id` |
| 查询参数 | `?page=1&keyword=导论` | `@RequestParam` 或直接用对象接收 |
| 请求头 | `Authorization: Bearer xxx` | 在 JwtFilter 里读取 |
| 请求体 | POST/PUT 时的 JSON | `@RequestBody CourseForm form` |
| 状态码 | 200 成功、401 未登录、404 不存在、500 服务器错误 | 我们的约定见 `docs/api.md` 1.3 节 |

### 2.3 REST 风格

用 **名词表示资源**，用 **HTTP 方法表示动作**：

| ❌ 不好 | ✅ 我们的写法 |
|---|---|
| `GET /getCourseList` | `GET /api/courses` |
| `POST /deleteCourse?id=1` | `DELETE /api/courses/1` |
| `POST /updateCourse` | `PUT /api/courses/1` |

### 2.4 JSON

前后端交换数据的格式。Spring Boot 会**自动**把 Java 对象转成 JSON（这个过程叫序列化，由 Jackson 库完成）：

```java
new Course(1L, "软件工程导论", "published")
// 自动变成 →  {"id":1,"name":"软件工程导论","status":"published"}
```

### ✏️ 第 2 部分自测题

1. 新建课程应该用哪个 HTTP 方法？课程数据放在请求的哪个部分？
2. `/api/courses/1` 和 `/api/courses?id=1` 有什么区别？我们用哪种？为什么？
3. 为什么我们所有接口都返回 `{code, message, data}`，而不是直接返回数据？
4. 前端怎么知道用户已经登录？token 放在请求的哪里？

---

## 第 3 部分：Spring Boot

### 3.1 核心概念

| 概念 | 一句话解释 | 项目里 |
|---|---|---|
| **Spring Boot** | 帮你把 Web 服务器、数据库连接这些东西自动配好，写好 `main` 方法就能启动 | `PlatformApplication.java` |
| **Bean** | 由 Spring 创建和管理的对象 | 所有 Controller、Service、Mapper |
| **IOC（控制反转）** | 对象不用自己 `new`，交给 Spring 创建 | 类上加 `@Service`、`@RestController` |
| **DI（依赖注入）** | 需要别的对象时，Spring 自动「递」给你 | Controller 构造方法里的 `CourseService` 参数 |
| **Maven** | 管理依赖（第三方库）和构建项目 | `pom.xml`、`mvnw` |
| **application.yml** | 配置文件：端口、数据库地址、JWT 密钥等 | `backend/src/main/resources/` |

```java
@RestController
@RequiredArgsConstructor                    // Lombok：自动生成构造方法
public class CourseController {
    private final CourseService courseService;   // 没有 new！Spring 自动注入（DI）
}
```

### 3.2 三层架构（必须能画出来）

```
Controller  接收请求、校验参数、返回 Result         ← 只管「HTTP 进出」
    ↓
Service     业务规则、权限判断、事务                ← 只管「业务逻辑」
    ↓
Mapper      增删改查数据库                          ← 只管「数据访问」
```

**规则**：Controller 不直接调 Mapper；Service 不处理 HTTP 相关的东西。

### 3.3 Entity / DTO / VO 的区别

| 类型 | 用途 | 例子 |
|---|---|---|
| **Entity** | 和数据库表一一对应 | `Course`（有 `teacherId`、`deleted`） |
| **DTO** | 接收前端传来的参数 | `CourseForm`（只有 `name`、`description`、`status`） |
| **VO** | 返回给前端的数据 | `CourseVO`（多了 `teacher` 姓名、`chapterCount`） |

**为什么不直接用 Entity？** 防止前端传 `deleted=1` 这种不该改的字段；也防止把 `password_hash` 这种敏感字段返回给前端。

### 3.4 常用注解速查

| 注解 | 作用 |
|---|---|
| `@SpringBootApplication` | 启动类 |
| `@RestController` | 处理请求并返回 JSON 的类 |
| `@RequestMapping("/api/courses")` | 这个类下所有接口的路径前缀 |
| `@GetMapping` / `@PostMapping` / `@PutMapping` / `@DeleteMapping` | 对应 HTTP 方法 |
| `@PathVariable` / `@RequestParam` / `@RequestBody` | 取路径参数 / 查询参数 / 请求体 |
| `@Service` | 业务类 |
| `@Mapper` | 数据访问接口 |
| `@Transactional` | 方法里的多条数据库操作要么全成功，要么全回滚 |
| `@Valid` + `@NotBlank` | 参数校验 |
| `@RestControllerAdvice` + `@ExceptionHandler` | 全局异常处理 |

### ✏️ 第 3 部分自测题

1. IOC 和 DI 分别是什么意思？我们为什么不自己 `new CourseServiceImpl()`？
2. 画出三层架构，说出每层负责什么。
3. 「只能修改自己的课程」这个判断应该写在哪一层？为什么？
4. Entity、DTO、VO 各是什么？为什么返回给前端时不直接用 Entity？
5. `@Transactional` 用在哪里？举「修改习题的关联知识点」这个例子说明。

---

## 第 4 部分：数据库与 MyBatis-Plus

### 4.1 我们表设计的关键决定（来自 `docs/database.md`）

| 决定 | 原因 |
|---|---|
| 习题 ↔ 知识点**多对多**，用中间表 `exercise_knowledge_point` | 一道题可以考多个知识点 |
| 习题挂在**课程**上，不挂在章节上 | 题目和章节没有固定从属关系，通过知识点定位 |
| **逻辑删除**（`deleted = 1`），不真删 | 题目删了，历史答题记录还能关联上 |
| 不建数据库外键约束，在 Service 里保证 | 方便逻辑删除、分模块开发、导入演示数据 |
| `chapterCount` 等**不存表里**，查询时计算 | 避免冗余数据和真实数据不一致 |

### 4.2 MyBatis-Plus 常用写法

```java
public interface CourseMapper extends BaseMapper<Course> {}   // 继承后自动拥有增删改查

courseMapper.selectById(1L);
courseMapper.insert(course);
courseMapper.updateById(course);
courseMapper.deleteById(1L);                 // 配了逻辑删除后，实际执行的是 UPDATE deleted=1

// 条件查询：名称包含关键字，并且已发布
courseMapper.selectList(new LambdaQueryWrapper<Course>()
        .like(Course::getName, keyword)
        .eq(Course::getStatus, "published"));

// 分页
Page<Course> page = courseMapper.selectPage(new Page<>(1, 10), wrapper);
page.getRecords();   // 当前页数据
page.getTotal();     // 总条数
```

### ✏️ 第 4 部分自测题

1. 为什么习题和知识点之间需要中间表？
2. 逻辑删除和物理删除有什么区别？我们为什么选逻辑删除？
3. 用 `LambdaQueryWrapper` 写：查某个课程下、名称包含「模型」的知识点。
4. 分页查询返回给前端时，需要哪两个字段？

---

## 第 5 部分：登录与安全（第 5 步再学）

| 概念 | 一句话 |
|---|---|
| **BCrypt** | 密码加密算法，数据库里只存哈希值，**永远不存明文密码** |
| **JWT** | 登录成功后发给前端的「通行证」，里面有用户 id 和角色，有效期 24 小时，带签名防篡改 |
| **Bearer token** | 前端每次请求都在请求头里带上 `Authorization: Bearer <token>` |
| **Filter（过滤器）** | 请求到达 Controller 之前先经过它，我们在这里检查 token |
| **401 和 403** | 401 = 你是谁？（没登录）；403 = 我知道你是谁，但你没权限 |

---

## 第 6 部分：项目协作必须知道的

### 6.1 Git 常用命令

开始工作前，先拿到最新代码：

```bash
git pull
```

完成一块功能后，提交并推送：

```bash
git add .
git commit -m "feat(learn): 课程分页查询接口"
git push
```

查看改了什么：

```bash
git status
```

```bash
git diff
```

**提交信息格式**：`类型(模块): 说明`。类型有 `feat` 新功能、`fix` 修 bug、`docs` 文档、`test` 测试。

### 6.2 怎么运行项目

| 部分 | 怎么启动 | 地址 |
|---|---|---|
| 前端 | `cd frontend` → `npm install`（第一次）→ `npm run dev` | http://localhost:5173 |
| 后端（骨架搭好后） | IDEA 里运行 `PlatformApplication` | http://localhost:8080 |
| 前端连后端 | `frontend/.env.development` 改成 `VITE_USE_MOCK=false` | — |

### 6.3 项目文档在哪里

| 想知道…… | 看这个 |
|---|---|
| 要做哪些功能、首版做多少 | `docs/requirements.md` |
| 系统怎么分层、业务流程图 | `docs/design.md` |
| 表结构 | `docs/database.md` |
| 接口路径、参数、返回值 | `docs/api.md` |
| 怎么测试、验收标准 | `docs/test-plan.md` |
| 谁负责什么 | `docs/team-plan.md` |

### 6.4 还没定的事（要尽快和组员确认）

- [ ] 课后分工：第一课的「登录 + 学 / 记 + 诊 / 画 / 荐 + 干」，和第二次课材料里的「5E 引导引擎 / 判定与配置 / E4 难点 / 资料库 RAG」是什么关系？
- [ ] 首版场景：「软件过程模型」还是「软件测试：等价类 / 边界值」？
- [ ] 作业原文的 M1–M10 编号，填进 `docs/requirements.md` 第 7 节
- [ ] 资料表 `material` 归谁：「学」模块，还是 D 的资料库？
- [ ] B、C、D 确认自己的表和接口草案

---

## 学习进度自查

- [ ] 第 1 部分自测题全部能答出来 → 可以开始 Java 小练习
- [ ] 第 2、3 部分自测题全部能答出来 → 可以读懂后端骨架代码
- [ ] 第 4 部分自测题全部能答出来 → 可以自己写章节/知识点/习题模块
- [ ] 第 5 部分看懂 → 可以做登录
