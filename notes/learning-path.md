# Java + Spring Boot 学习路线（视频清单）

> 目标：能独立写「登录 + 学」模块的后端（Spring Boot 3 + MyBatis-Plus + MySQL）。
> 视频全部是 B 站**黑马程序员官方**账号发布，集数和标题已于 2026-10-12 核对。
> ★ = 必看，★★ = 最关键。勾选 `[x]` 记录进度。
> 每个概念要掌握到什么程度、自测题：见 [must-know.md](must-know.md)。

## 总览

| 站 | 内容 | 时长（1.5 倍速） | 学完能做什么 |
|---|---|---|---|
| 第 1 站 | Java 基础 | 约 8～10 小时 | Java 小练习：`Course` 类、过滤、分页 |
| 第 2 站 | JavaWeb + Spring Boot + MyBatis | 约 10 小时 | 看懂后端骨架，照着写章节/知识点/习题模块 |
| 第 3 站 | 登录 JWT、文件上传 | 约 4 小时 | 登录鉴权、资料上传 |
| 文档 | MyBatis-Plus 快速开始 | 约 0.5 小时 | 不写 SQL 的增删改查和分页 |

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

## 第 2 站：JavaWeb + Spring Boot ⭐ 最贴合我们项目

### 主课：[黑马 AI+JavaWeb：Tlias 教学管理系统](https://www.bilibili.com/video/BV1yGydYEE3H/)

共 200 集，技术栈是 Spring Boot 3 + MyBatis + Vue + Element Plus + JWT，几乎和我们项目一样。

- [ ] P1–P30 HTML/CSS/JS/Vue/Ajax：**跳过**（前端已经做好了，以后想改页面再回来看 P17–P30）
- [ ] ★ [P31–P39 Maven、JUnit 单元测试](https://www.bilibili.com/video/BV1yGydYEE3H/?p=31)
- [ ] ★★ [P40–P49 Spring Boot 入门、HTTP 协议、三层架构、IOC/DI](https://www.bilibili.com/video/BV1yGydYEE3H/?p=40)：**最关键的 10 集**，看完就能看懂我们后端的整体结构
- [ ] [P50–P61 MySQL](https://www.bilibili.com/video/BV1yGydYEE3H/?p=50)：会基本 SQL 的话，只挑「分页查询」看
- [ ] [P62–P64 JDBC](https://www.bilibili.com/video/BV1yGydYEE3H/?p=62)：了解即可
- [ ] ★ [P65–P72 MyBatis 入门、增删改查](https://www.bilibili.com/video/BV1yGydYEE3H/?p=65)
- [ ] ★★ [P73–P83 配置文件、开发规范、RESTful、工程搭建、部门管理增删改查](https://www.bilibili.com/video/BV1yGydYEE3H/?p=73)：**和你要写的课程/章节模块是同一个套路**

### 对照表：视频里的概念在我们项目里是什么

| 视频里 | 我们项目里 |
|---|---|
| 三层架构 Controller / Service / Mapper | `learn/controller`、`learn/service`、`learn/mapper` |
| 统一响应结果 Result | `common/Result.java` → `{code, message, data}` |
| 部门管理 Dept 的增删改查 | 课程 Course 的增删改查 |
| RESTful：`GET /depts`、`DELETE /depts/1` | `GET /api/courses`、`DELETE /api/courses/1`（见 `docs/api.md`） |
| MyBatis 的 XML / 注解写 SQL | 我们用 MyBatis-Plus，简单的增删改查不用写 SQL |

---

## 第 3 站：登录 / JWT / 文件上传（第 5 步时再看）

### 主课：[黑马 SpringBoot3+Vue3 全套（大事件项目）](https://www.bilibili.com/video/BV14z4y1N7pg/)

共 112 集。

- [ ] [P3–P7 Spring Boot 入门、工程创建、yml 配置、整合 MyBatis](https://www.bilibili.com/video/BV14z4y1N7pg/?p=3)：当作第 2 站的复习
- [ ] ★ [P15–P16 注册接口、参数校验](https://www.bilibili.com/video/BV14z4y1N7pg/?p=15)
- [ ] ★★ [P17–P20 登录主逻辑、JWT 令牌、登录认证](https://www.bilibili.com/video/BV14z4y1N7pg/?p=17)
- [ ] [P21–P22 获取当前用户信息、ThreadLocal](https://www.bilibili.com/video/BV14z4y1N7pg/?p=21)
- [ ] [P34 条件分页查询](https://www.bilibili.com/video/BV14z4y1N7pg/?p=34)
- [ ] ★ [P35 文件上传：本地存储](https://www.bilibili.com/video/BV14z4y1N7pg/?p=35)：对应我们的资料上传（P36–P38 讲阿里云 OSS，我们用不到）
- [ ] 可选：[P43–P45 多环境配置](https://www.bilibili.com/video/BV14z4y1N7pg/?p=43)（开发用 H2，联调用 MySQL）

> 视频里用拦截器 + JWT 做登录认证；我们按计划用 Spring Security + JWT，思路相同，写代码时由 Claude 讲解两者的区别。

---

## 文档：MyBatis-Plus

B 站上的 MyBatis-Plus 视频都是 2018–2019 年的旧版，所以改看官方文档：

- [ ] [MyBatis-Plus 快速开始](https://baomidou.com/getting-started/)：重点看 `BaseMapper`、`IService`、条件构造器 `LambdaQueryWrapper`、分页插件

---

## 看视频和写代码怎么配合

```
第 1 站 面向对象 + 接口 + ArrayList ──► 你做 Java 小练习，Claude 审查
第 2 站 P40–P49                    ──► Claude 搭 Spring Boot 骨架并逐行讲解
第 2 站 P73–P83                    ──► Claude 把「课程」模块完整做出来讲解 → 你照着写章节/知识点/习题，Claude 审查
第 3 站                            ──► 登录 JWT、资料上传、JUnit 测试
```

## 学习小建议

1. **边看边敲**：视频里的代码自己敲一遍，不要只看，一周后差别很大
2. **遇到看不懂的先记下来**，带着问题回来问 Claude，比卡在一个地方反复看效率高
3. 每看完一站，在本文件里打勾，然后 `git commit` + `git push`，另一台电脑 `git pull` 就能同步进度
