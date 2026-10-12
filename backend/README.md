# 后端 Backend — 软件工程智能学习平台

Spring Boot 3.5 + MyBatis-Plus 3.5 + H2（开发）/ MySQL 8（联调）· Java 21 · Maven Wrapper

## 环境要求

- **JDK 21**（`java -version` 要显示 21）
- 不用安装 Maven：项目自带 Maven Wrapper（`mvnw`），第一次运行时自动下载 Maven 和依赖（约 75 MB，存到用户目录的 `.m2` 文件夹，只下载一次）
- 不用安装数据库：开发阶段用 H2 内存数据库

## 运行

### 方法一：IntelliJ IDEA（推荐）

1. IDEA → File → Open → 选择 `backend` 文件夹（选 `backend`，不是整个仓库）
2. 等右下角的 Maven 导入进度条走完
3. 打开 `src/main/java/com/se/platform/PlatformApplication.java`，点 `main` 方法左边的绿色三角 ▶
4. 控制台出现 `Started PlatformApplication` 就是启动成功了

> 如果 IDEA 提示 SDK 没配置：File → Project Structure → SDK 选 21。

### 方法二：命令行

在 `backend` 目录下运行：

```bash
./mvnw spring-boot:run
```

Windows 的 cmd / PowerShell 用 `mvnw.cmd spring-boot:run`。

## 验证是否跑通

启动后用浏览器打开：

| 地址 | 预期结果 |
|---|---|
| http://localhost:8080/api/hello?name=张三 | `{"code":200,"message":"ok","data":"hello, 张三"}` |
| http://localhost:8080/api/hello/courses/99 | `{"code":404,"message":"课程不存在","data":null}` |
| http://localhost:8080/api/hello/courses/abc | `{"code":400,"message":"参数格式不正确：id","data":null}` |
| http://localhost:8080/h2-console | H2 数据库控制台（JDBC URL 填 `jdbc:h2:mem:platform`，用户名 `sa`，密码留空） |

## 运行测试

```bash
./mvnw test
```

或者在 IDEA 里右键 `src/test/java` → Run 'All Tests'。

## 代码结构

```
src/main/java/com/se/platform/
├── PlatformApplication.java          启动类
├── common/                           公共部分（所有模块共用）
│   ├── Result.java                   统一返回 {code, message, data}
│   ├── PageResult.java               分页返回 {records, total}
│   ├── BusinessException.java        业务异常
│   ├── GlobalExceptionHandler.java   全局异常处理：异常 → {code, message}
│   └── config/
│       ├── MybatisPlusConfig.java    分页插件、创建/更新时间自动填充
│       └── JacksonConfig.java        日期格式 yyyy-MM-dd HH:mm:ss
└── demo/HelloController.java         演示接口，课程模块完成后删除

src/main/resources/
└── application.yml                   配置：端口、数据库、MyBatis-Plus
```

各模块的包结构和分层规则见 [docs/design.md 第 2 节](../docs/design.md)。

## 和前端联调

1. 启动后端（8080）
2. 把 `frontend/.env.development` 改成 `VITE_USE_MOCK=false`
3. 启动前端 `npm run dev`（5173），前端的 `/api` 请求会被代理到 8080

## 常见问题

| 问题 | 解决 |
|---|---|
| `Port 8080 was already in use` | 已经有一个后端在运行，先关掉它（IDEA 里点红色方块停止） |
| `java -version` 显示的不是 21 | 重开终端；还不行的话，检查环境变量 `JAVA_HOME` 是不是指向 JDK 21 |
| 中文显示成乱码 | IDEA → Settings → Editor → File Encodings，全部设为 UTF-8 |
