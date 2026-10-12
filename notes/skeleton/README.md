# 后端骨架课程

> 把 `backend/` 的骨架拆成 8 课，一课只学一个部分。
> 每课：读讲解 → 动手做 → 回答检查题 → 交给 Claude 确认，再进入下一课。

## 课程表

| 课 | 内容 | 文件 | 对应视频 | 状态 |
|---|---|---|---|---|
| [第 1 课](lesson-01-run.md) | 项目结构 + 自己把后端跑起来 | 整个 `backend/` | Tlias P40 | ⬜ |
| 第 2 课 | 依赖清单：后端用了哪些库，为什么 | `pom.xml` | Tlias P31–P35 | ⬜ |
| 第 3 课 | 启动类：程序从哪里开始，注解是什么 | `PlatformApplication.java` | Tlias P40、P47–P49 | ⬜ |
| 第 4 课 | 接口：一个请求怎么找到对应的方法 | `HelloController.java` | Tlias P41–P44 | ⬜ |
| 第 5 课 | 统一返回格式：泛型、静态方法、Lombok | `Result.java`、`PageResult.java` | Tlias P73 | ⬜ |
| 第 6 课 | 异常处理：出错时怎么返回统一格式 | `BusinessException.java`、`GlobalExceptionHandler.java` | Java 下部 P52–P62 | ⬜ |
| 第 7 课 | 配置：配置文件和配置类 | `application.yml`、`config/` | Tlias P73 | ⬜ |
| 第 8 课 | 测试：用代码自动检查接口 | `HelloControllerTest.java` | Tlias P36–P39 | ⬜ |

视频链接见 [../learning-path.md](../learning-path.md)。

## 骨架全貌

```
backend/
├── pom.xml                         依赖清单（第 2 课）
├── mvnw, mvnw.cmd, .mvn/           Maven Wrapper（第 2 课）
└── src/
    ├── main/
    │   ├── java/com/se/platform/
    │   │   ├── PlatformApplication.java       启动类（第 3 课）
    │   │   ├── demo/HelloController.java      演示接口（第 4 课）
    │   │   └── common/
    │   │       ├── Result.java                统一返回（第 5 课）
    │   │       ├── PageResult.java            分页返回（第 5 课）
    │   │       ├── BusinessException.java     业务异常（第 6 课）
    │   │       ├── GlobalExceptionHandler.java 全局异常处理（第 6 课）
    │   │       └── config/                    配置类（第 7 课）
    │   └── resources/application.yml          配置文件（第 7 课）
    └── test/java/com/se/platform/demo/
        └── HelloControllerTest.java           测试（第 8 课）
```

## 一个请求怎么走（学完 8 课要能自己讲出来）

```
浏览器 GET /api/hello/courses/99
  → 内置 Tomcat（8080 端口）收到请求
  → Spring 按路径找到 HelloController.course(99)
  → id 不是 1 → throw BusinessException.notFound("课程")
  → GlobalExceptionHandler 接住异常
  → 返回 Result.fail(404, "课程不存在")
  → Jackson 转成 JSON：{"code":404,"message":"课程不存在","data":null}
```

骨架只是地基，学完以后就开始在上面盖业务功能，第一个是课程模块。
