# 第 1 课：项目结构 + 自己把后端跑起来

> **目标**：先让后端在你手上跑起来，亲眼看到它工作。原理从第 2 课开始讲。
> 返回 [课程表](README.md)

## 1. 用 IDEA 打开后端

1. 打开 IntelliJ IDEA
2. **File → Open**，选择 `se-learning-platform/backend` 文件夹
   - ⚠️ 选 `backend` 这个文件夹，**不是**整个 `se-learning-platform`
3. 如果弹出「Trust Project?」，点 **Trust Project**
4. 右下角会出现进度条（Maven 在读取依赖），**等它走完**，第一次大约 1～2 分钟

> 如果顶部提示「Project JDK is not defined」：点提示里的 **Setup SDK**，选 **21**（Eclipse Temurin）。

## 2. 认识项目结构

```
backend
├── .mvn/、mvnw、mvnw.cmd        Maven Wrapper（第 2 课讲）
├── pom.xml                      依赖清单（第 2 课讲）
└── src
    ├── main                     ← 正式代码
    │   ├── java
    │   │   └── com.se.platform
    │   │       ├── PlatformApplication   启动类（第 3 课）
    │   │       ├── common               公共部分（第 5、6 课）
    │   │       │   └── config           配置类（第 7 课）
    │   │       └── demo                 演示接口（第 4 课）
    │   └── resources
    │       └── application.yml          配置文件（第 7 课）
    └── test                     ← 测试代码（第 8 课）
```

**记住两条规则：**

- `src/main/java` 放 Java 代码，`src/main/resources` 放配置文件，`src/test` 放测试。这是 Maven 规定的目录结构，所有 Java 项目都一样
- 包名 `com.se.platform` 在磁盘上对应的是文件夹 `com/se/platform/`

## 3. 启动后端

1. 打开 `src/main/java/com.se.platform/PlatformApplication`
2. 点 `main` 方法左边的**绿色三角 ▶** → **Run 'PlatformApplication'**
3. 下方会弹出控制台，看到这样的输出就是启动成功了：

```
 :: Spring Boot ::               (v3.5.16)
...
Tomcat started on port 8080 (http)
H2 console available at '/h2-console'
Started PlatformApplication in 3.2 seconds
```

| 这一行 | 意思 |
|---|---|
| `Tomcat started on port 8080` | 内置的 Web 服务器启动了，在 8080 端口等待请求 |
| `H2 console available` | 内置数据库也启动了 |
| `Started PlatformApplication` | 整个后端准备就绪 |

## 4. 访问接口

浏览器依次打开下面 3 个地址：

| 地址 | 应该看到 |
|---|---|
| http://localhost:8080/api/hello?name=你的名字 | `{"code":200,"message":"ok","data":"hello, 你的名字"}` |
| http://localhost:8080/api/hello/courses/99 | `{"code":404,"message":"课程不存在","data":null}` |
| http://localhost:8080/api/abc | `{"code":404,"message":"接口不存在：/api/abc","data":null}` |

每次访问后，回 IDEA 控制台看看有没有新的输出。

## 5. 动手改一下（小实验）

1. 打开 `demo/HelloController.java`
2. 找到 `return Result.ok("hello, " + name);`，把 `"hello, "` 改成 `"你好，"`
3. 点控制台左边的**重启按钮**（绿色的循环箭头），或者先点红色方块停止，再点 ▶ 启动
4. 刷新第一个地址，看看返回有没有变

**你会发现**：改了代码必须**重启**才生效。这是因为 Java 要先编译成字节码再运行。前端改完能马上看到效果，后端不行。

> 实验做完记得改回 `"hello, "`，不然第 8 课的测试会失败（测试检查的就是 `hello, 张三`）。

## 6. 停止后端

点控制台左边的**红色方块 ■**。停止以后再访问那些地址，浏览器会显示「无法访问此网站」，因为已经没有程序在 8080 端口等待请求了。

---

## ✏️ 检查题

1. 打开项目时，为什么要选 `backend` 文件夹，而不是整个仓库？
2. 控制台里哪一行告诉你后端「准备好了」？
3. 改了 Java 代码以后，要做什么才能看到效果？
4. 配置文件放在哪个文件夹？测试代码放在哪个文件夹？

## 完成后交给 Claude

- [ ] 跑起来了，3 个地址都看到了正确结果
- [ ] 小实验做了，也改回去了
- [ ] 4 道检查题的答案
- 遇到报错的话，把控制台的红色文字复制过来

下一课：第 2 课 `pom.xml` 依赖清单
