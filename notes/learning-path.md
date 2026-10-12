# Java + Spring Boot 学习路线（视频清单）

> 目标：能独立写「登录 + 学」模块的后端（Spring Boot 3 + MyBatis-Plus + MySQL）。
> 视频全部是 B 站**黑马程序员官方**账号发布，集数和标题已于 2026-10-12 核对（Tlias 第 31～130 集逐集对照过目录）。
> ★ = 必看，★★ = 最关键。勾选 `[x]` 记录进度。
> 每个概念要掌握到什么程度、自测题：见 [must-know.md](must-know.md)。

## 总览

| 站 | 内容 | 时长（1.5 倍速） | 学完能做什么 |
|---|---|---|---|
| 第 1 站 | Java 基础 | 约 8～10 小时 | Java 小练习：`Course` 类、过滤、分页 |
| 第 2 站 | Tlias：Spring Boot + MyBatis + 增删改查 | 约 8 小时 | 看懂后端骨架，照着写课程/章节/知识点模块 |
| 第 3 站 | Tlias：分页、多对多、事务、异常、上传 | 约 5 小时 | 写习题模块（多对多 + 事务）、资料上传 |
| 第 4 站 | Tlias：登录、JWT、Filter、拦截器 | 约 3 小时 | 登录鉴权 |
| 文档 | MyBatis-Plus 快速开始 | 约 0.5 小时 | 不写 SQL 的增删改查和分页 |

> **只用 Tlias 一套课**就能覆盖你的全部模块（第 2～4 站）。之前列的「大事件」课程不再需要，放在文末作为备选。

---

## 第 1 站：Java 基础

### 主课：[黑马 2026 版 AI+Java 零基础](https://www.bilibili.com/video/BV1TJxCzSEEZ/)

2025-10 发布，共 149 集，去掉了开发中很少用的内容。

- [ ] P1–P11 入门、HelloWorld：**跳过 P3–P6**（JDK 21 和 IDEA 已经装好了）；**P8 IDEA 基本设置要看**
- [ ] P12–P55 变量、运算符、if、switch、循环：学过别的语言，**2 倍速扫一遍**
- [ ] P56–P68 数组：快速看
- [ ] ★ [P69–P72 方法](https://www.bilibili.com/video/BV1TJxCzSEEZ/?p=69)
- [ ] ★★ [P85–P93 面向对象：类和对象、this、构造方法](https://www.bilibili.com/video/BV1TJxCzSEEZ/?p=85)：**最重要**，Spring Boot 里每个类都会用到
- [ ] [P97–P103 static、final、枚举](https://www.bilibili.com/video/BV1TJxCzSEEZ/?p=97)
- [ ] ★ [P104–P113 继承](https://www.bilibili.com/video/BV1TJxCzSEEZ/?p=104)
- [ ] ★ [P114–P116 多态](https://www.bilibili.com/video/BV1TJxCzSEEZ/?p=114)
- [ ] ★★ [P117–P120 抽象类、接口](https://www.bilibili.com/video/BV1TJxCzSEEZ/?p=117)：`CourseService` 接口 + `CourseServiceImpl` 实现类，用的就是这里的知识
- [ ] ★ [P133–P134 ArrayList 集合](https://www.bilibili.com/video/BV1TJxCzSEEZ/?p=133)
- [ ] 可选：P74–P78、P94–P96 内存原理（想搞懂原理再看）

### 补充：[黑马 Java 零基础 下部](https://www.bilibili.com/video/BV1yW4y1Y7Ms/)

- [ ] ★ [P35–P42 Stream 流](https://www.bilibili.com/video/BV1yW4y1Y7Ms/?p=35)：过滤、分页常用
- [ ] ★ [P52–P62 异常](https://www.bilibili.com/video/BV1yW4y1Y7Ms/?p=52)：重点看 P56 try/catch、P62 自定义异常（对应我们的 `BusinessException`）

> 泛型（`List<Course>`、`Result<T>`）和注解（`@GetMapping` 这类）两套视频都没有专门讲，写代码时由 Claude 讲解。

### ✅ 第 1 站学完后的练习

在 IDEA 里新建练习项目 `java-practice`（不放进本仓库）：

1. 写一个 `Course` 类：`id`、`name`、`status`，带构造方法和 getter/setter
2. 用 `List<Course>` 存 6 门课，按关键字过滤
3. 写 `page(list, page, size)` 实现分页
4. 写 `CourseService` 接口和 `CourseServiceImpl` 实现类
5. 删除不存在的课程时抛出自定义异常 `BusinessException`

写完交给 Claude 审查。

---

## 第 2 站：Spring Boot 入门 + 增删改查 ⭐ 最贴合我们项目

### 主课：[黑马 AI+JavaWeb：Tlias 教学管理系统](https://www.bilibili.com/video/BV1yGydYEE3H/)

共 200 集，技术栈是 Spring Boot 3 + MyBatis + Vue + Element Plus + JWT，几乎和我们项目一样。第 2～4 站都用这一套。

- [ ] P1–P30 HTML/CSS/JS/Vue/Ajax：**跳过**（前端已经做好了，以后想改页面再回来看 P17–P30）
- [ ] ★ [P31–P34 Maven：安装、IDEA 集成、依赖管理](https://www.bilibili.com/video/BV1yGydYEE3H/?p=31)（我们用 Maven Wrapper，P32 的安装步骤可以跳过）
- [ ] ★ [P35–P38 单元测试：入门、断言、开发规范、依赖范围](https://www.bilibili.com/video/BV1yGydYEE3H/?p=35)
- [ ] [P39 Maven 常见问题](https://www.bilibili.com/video/BV1yGydYEE3H/?p=39)：遇到依赖下载失败时再看
- [ ] ★★ [P41–P42 SpringBootWeb 入门程序、入门解析](https://www.bilibili.com/video/BV1yGydYEE3H/?p=41)：对应骨架的启动类和 `HelloController`
- [ ] ★ [P43–P45 HTTP 协议：概述、请求、响应](https://www.bilibili.com/video/BV1yGydYEE3H/?p=43)
- [ ] [P46 SpringBootWeb 案例](https://www.bilibili.com/video/BV1yGydYEE3H/?p=46)
- [ ] ★★ [P47–P49 三层架构、IOC 与 DI](https://www.bilibili.com/video/BV1yGydYEE3H/?p=47)：看完就能看懂我们后端的分层
- [ ] [P50–P61 MySQL](https://www.bilibili.com/video/BV1yGydYEE3H/?p=50)：会基本 SQL 的话只看 [P53–P55 建表、数据类型、设计表案例](https://www.bilibili.com/video/BV1yGydYEE3H/?p=53) 和 [P61 分页查询](https://www.bilibili.com/video/BV1yGydYEE3H/?p=61)
- [ ] [P62–P64 JDBC](https://www.bilibili.com/video/BV1yGydYEE3H/?p=62)：了解即可
- [ ] ★ [P65–P72 MyBatis：入门、连接池、增删改查、XML 映射](https://www.bilibili.com/video/BV1yGydYEE3H/?p=65)
- [ ] ★ [P73 SpringBoot 项目配置文件](https://www.bilibili.com/video/BV1yGydYEE3H/?p=73)：对应 `application.yml`
- [ ] ★ [P74–P76 开发规范、RESTful、工程搭建](https://www.bilibili.com/video/BV1yGydYEE3H/?p=74)
- [ ] ★★ [P77–P83 部门管理：列表查询、结果封装、联调、删除、新增、修改](https://www.bilibili.com/video/BV1yGydYEE3H/?p=77)：**和课程模块是同一个套路**；P78 就是我们的 `Result`
- [ ] [P84–P85 日志 Logback](https://www.bilibili.com/video/BV1yGydYEE3H/?p=84)：了解即可

---

## 第 3 站：分页、多对多、事务、异常、上传

继续 [Tlias](https://www.bilibili.com/video/BV1yGydYEE3H/)：

- [ ] ★★ [P86–P88 多表关系：一对多、一对一、**多对多**、设计案例](https://www.bilibili.com/video/BV1yGydYEE3H/?p=86)：习题 ↔ 知识点就是多对多
- [ ] [P89–P92 多表查询：内连接、外连接、子查询](https://www.bilibili.com/video/BV1yGydYEE3H/?p=89)：你会 JOIN 的话快速看
- [ ] ★ [P93–P96 员工管理：分页查询原理、实现、PageHelper](https://www.bilibili.com/video/BV1yGydYEE3H/?p=93)：对应我们的 `PageResult`（我们用 MyBatis-Plus 分页插件代替 PageHelper，思路一样）
- [ ] ★★ [P97–P98 条件分页查询](https://www.bilibili.com/video/BV1yGydYEE3H/?p=97)：课程列表「关键字搜索 + 分页」
- [ ] [P99–P100 新增员工、批量保存](https://www.bilibili.com/video/BV1yGydYEE3H/?p=99)
- [ ] ★★ [P101–P103 事务管理](https://www.bilibili.com/video/BV1yGydYEE3H/?p=101)：修改习题时，关联的知识点要一起改，要么全成功要么全回滚
- [ ] ★ [P104–P105 文件上传：介绍、本地存储](https://www.bilibili.com/video/BV1yGydYEE3H/?p=104)：资料上传（P106–P108 讲阿里云 OSS，我们用不到）
- [ ] [P109 @Value 和 @ConfigurationProperties](https://www.bilibili.com/video/BV1yGydYEE3H/?p=109)：读取配置文件里的值，上传路径会用到
- [ ] [P110–P114 删除、修改员工](https://www.bilibili.com/video/BV1yGydYEE3H/?p=110)：P110 讲请求参数接收
- [ ] ★★ [P115 全局异常处理器](https://www.bilibili.com/video/BV1yGydYEE3H/?p=115)：对应骨架的 `GlobalExceptionHandler`
- [ ] [P116–P118 员工信息统计](https://www.bilibili.com/video/BV1yGydYEE3H/?p=116)：可选

---

## 第 4 站：登录、JWT、Filter、拦截器

继续 [Tlias](https://www.bilibili.com/video/BV1yGydYEE3H/)：

- [ ] [P119 项目实战需求说明](https://www.bilibili.com/video/BV1yGydYEE3H/?p=119)
- [ ] ★ [P120 登录功能实现](https://www.bilibili.com/video/BV1yGydYEE3H/?p=120)
- [ ] ★ [P121–P124 会话技术：Cookie、Session、令牌方案](https://www.bilibili.com/video/BV1yGydYEE3H/?p=121)：理解为什么用 JWT
- [ ] ★★ [P125–P126 JWT 生成与校验、登录成功后下发令牌](https://www.bilibili.com/video/BV1yGydYEE3H/?p=125)
- [ ] ★★ [P127–P129 Filter：入门、令牌校验、详解](https://www.bilibili.com/video/BV1yGydYEE3H/?p=127)
- [ ] ★ [P130 Interceptor 拦截器入门](https://www.bilibili.com/video/BV1yGydYEE3H/?p=130)，以及后面几集拦截器的内容

> 登录方式（拦截器 + JWT 还是 Spring Security + JWT）还没定。Tlias 讲的是 Filter/拦截器 + JWT，看完这一站再决定。

---

## 视频里的概念 ↔ 我们的后端骨架

| 视频里 | 我们项目里 | 集数 |
|---|---|---|
| 入门程序、启动类 | `PlatformApplication.java` | P41–P42 |
| `@RestController`、`@GetMapping` | `demo/HelloController.java` | P41、P77 |
| 三层架构 Controller / Service / Mapper | `learn/controller`、`learn/service`、`learn/mapper` | P47 |
| IOC / DI、组件扫描 | 代码必须放在 `com.se.platform` 包下 | P48–P49 |
| 统一响应结果 Result | `common/Result.java` → `{code, message, data}` | P78 |
| 分页结果 | `common/PageResult.java` → `{records, total}` | P94–P95 |
| 全局异常处理器 | `common/GlobalExceptionHandler.java` | P115 |
| 配置文件 | `application.yml` | P73 |
| 部门管理 Dept 的增删改查 | 课程 Course 的增删改查 | P77–P83 |
| RESTful：`GET /depts`、`DELETE /depts/1` | `GET /api/courses`、`DELETE /api/courses/1`（见 `docs/api.md`） | P75 |
| MyBatis 写 SQL | 我们用 MyBatis-Plus，简单的增删改查不用写 SQL | P65–P72 |
| PageHelper 分页插件 | MyBatis-Plus 分页插件（`config/MybatisPlusConfig.java`） | P96 |

**视频没讲、看代码注释或问 Claude 的**：Maven Wrapper、H2 数据库、创建/修改时间自动填充、日期格式配置（`JacksonConfig`）、MockMvc 接口测试、Lombok（视频里直接用，没有专门讲）。

---

## 文档：MyBatis-Plus

B 站上的 MyBatis-Plus 视频都是 2018–2019 年的旧版，所以改看官方文档：

- [ ] [MyBatis-Plus 快速开始](https://baomidou.com/getting-started/)：重点看 `BaseMapper`、`IService`、条件构造器 `LambdaQueryWrapper`、分页插件

---

## 看视频和写代码怎么配合

```
第 1 站 面向对象 + 接口 + ArrayList ──► 你做 Java 小练习，Claude 审查
第 2 站 P41–P49                    ──► 读懂后端骨架（已搭好）
第 2 站 P73–P83                    ──► Claude 一步一步做「课程」模块讲解 → 你照着写章节/知识点，Claude 审查
第 3 站 P86–P88、P101–P103          ──► 你写习题模块（多对多 + 事务）
第 3 站 P104–P105                  ──► 资料上传
第 4 站                            ──► 登录 JWT
```

---

## 备选：大事件课程（不必看）

[黑马 SpringBoot3+Vue3 全套（大事件项目）](https://www.bilibili.com/video/BV14z4y1N7pg/)，共 112 集。Tlias 已经覆盖了需要的内容，只有想换个讲法复习时再看：

- [P3–P7 Spring Boot 入门、yml 配置、整合 MyBatis](https://www.bilibili.com/video/BV14z4y1N7pg/?p=3)
- [P15–P22 注册、登录、JWT、获取当前用户](https://www.bilibili.com/video/BV14z4y1N7pg/?p=15)
- [P35 文件上传：本地存储](https://www.bilibili.com/video/BV14z4y1N7pg/?p=35)

## 学习小建议

1. **边看边敲**：视频里的代码自己敲一遍，不要只看，一周后差别很大
2. **遇到看不懂的先记下来**，带着问题回来问 Claude，比卡在一个地方反复看效率高
3. 每看完一站，在本文件里打勾，然后 `git commit` + `git push`，另一台电脑 `git pull` 就能同步进度
