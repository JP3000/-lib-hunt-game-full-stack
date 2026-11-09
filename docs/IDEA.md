# 用 IntelliJ IDEA 开发与运行后端

> 这份指南解决一件事：把 `server/` 导入 IDEA，能启动、能打断点、能跑测试。
>
> 先明确分工：**IDEA 只负责后端**（`server/`）；MySQL 和 Redis 默认由仓库根目录的 `docker compose` 提供，也可以改用本机安装的实例（见第 12.9 节）；前端继续在根目录用 `npm run dev`。后端本身不是容器，在 IDEA 里就是一个普通的 Spring Boot 进程。

## 目录

- [1. 装齐三样东西](#1-装齐三样东西)
- [2. 打开项目](#2-打开项目)
- [3. 配置 JDK、Maven 与 Lombok](#3-配置-jdkmaven-与-lombok)
- [4. 启动 MySQL 和 Redis](#4-启动-mysql-和-redis)
- [5. 创建运行配置并启动](#5-创建运行配置并启动)
- [6. 断点调试](#6-断点调试)
- [7. 在 IDEA 里跑测试](#7-在-idea-里跑测试)
- [8. 查看数据库与 Redis](#8-查看数据库与-redis)
- [9. 用 HTTP Client 调接口](#9-用-http-client-调接口)
- [10. 改完代码怎么生效](#10-改完代码怎么生效)
- [11. 日常开发流程](#11-日常开发流程)
- [12. 常见问题](#12-常见问题)
- [13. 快捷键与命令行对照](#13-快捷键与命令行对照)

---

## 1. 装齐三样东西

| 工具 | 要求 | 检查命令 |
| --- | --- | --- |
| IntelliJ IDEA | 2023.3 或更新；**Community 版够用** | 菜单 `Help → About` |
| JDK 21 | 推荐 Eclipse Temurin 21 | `java -version` |
| Docker Desktop | 跑 MySQL/Redis，以及测试用的 Testcontainers | `docker info` |

说明：

- **Ultimate 与 Community 的差别**：Ultimate 有 Spring Boot 专属运行配置、Bean 依赖图、Actuator 面板；Community 用普通的 `Application` 运行配置，功能完全够跑本项目。下文对两种方式都写了。
- JDK 没装也可以：`File → Project Structure → SDKs → + → Download JDK`，选 21。
- Docker Desktop 必须处于 **Running** 状态，否则第 4 步和测试都会失败。
- 如果你用 Colima / OrbStack 代替 Docker Desktop，确认 `docker info` 能正常输出，并且 Testcontainers 能找到 Docker socket。

---

## 2. 打开项目

### 方式 A（推荐）：只打开后端

1. `File → Open`。
2. 选择 **`server` 目录**（不是仓库根目录，也不是 `pom.xml` 文件）。
3. 点 `Open`；如果弹出 "Trust Project?"，选 `Trust`。

IDEA 识别到 `pom.xml` 后会把它当作 Maven 项目导入。首次导入会在右下角下载依赖，等进度条结束、`Maven` 工具窗口出现 `lib-hunt-server` 即可。

> 这样打开的工程根目录就是 `server/`，`$PROJECT_DIR$` 指向 `server`。
> 在 IDEA 内置 Terminal 里执行 docker compose 时记得先 `cd ..` 回到仓库根目录。

### 方式 B：前后端放在同一个窗口

1. `File → Open`，选择 **仓库根目录**。
2. 打开右侧 `Maven` 工具窗口（没有的话：`View → Tool Windows → Maven`）。
3. 点 `+` → `Add Maven Project` → 选择 `server/pom.xml` → `OK`。
4. 等依赖导入完成。

前端目录只是普通文件夹，IDEA 不会把它当 Java 模块，互不影响。

> 无论哪种方式，`.idea/`、`*.iml`、`target/` 都已在 `server/.gitignore` 里，不会污染仓库。

---

## 3. 配置 JDK、Maven 与 Lombok

### 3.1 项目 SDK 设为 21

`File → Project Structure`（macOS `⌘;`）：

| 位置 | 设置 |
| --- | --- |
| `Project` → `SDK` | 21 |
| `Project` → `Language level` | 21 |
| `Modules` → `lib-hunt-server` → `Sources` | Language level 21 |

如果列表里没有 21：`SDKs → + → Download JDK`，Vendor 选 Eclipse Temurin，Version 选 21。

项目用到了 record、`instanceof` 模式匹配、文本块等较新的 Java 语法，SDK 选错会直接编译失败。

### 3.2 Maven 用 Wrapper

`Settings`（`⌘,`）→ `Build, Execution, Deployment → Build Tools → Maven`：

| 配置项 | 建议值 |
| --- | --- |
| `Maven home path` | **Use Maven wrapper**（对应仓库里的 3.9.9） |
| `JDK for importer` | 21 |
| `User settings file` | 默认即可（有公司镜像时再改） |

这样 IDEA 用的 Maven 版本和命令行 `./mvnw` 完全一致，避免"命令行能编、IDEA 不能编"。

### 3.3 注解处理（Lombok）

项目实体类用了 Lombok（`@Data`、`@Getter` 等）。

- 新版 IDEA 自带 Lombok 插件，通常无需安装。
- 确认 `Settings → Build, Execution, Deployment → Compiler → Annotation Processors` 里的 **`Enable annotation processing`** 已勾选。

如果代码里 `getId()`、`setXxx()` 飘红，先检查这里。

### 3.4 文件编码

`Settings → Editor → File Encodings`：

- `Global Encoding`、`Project Encoding`、`Default encoding for properties files` 全部设为 **UTF-8**。
- 项目里有大量中文内容和 `.sql` 种子数据，编码错会看到乱码。

---

## 4. 启动 MySQL 和 Redis

在 IDEA 里打开 Terminal（`⌥F12`），**回到仓库根目录**执行：

```bash
cd /path/to/lib-hunt-game-full-stack   # 方式 A 打开的工程需要先 cd ..
docker compose up -d
docker compose ps
```

看到 `libhunt-mysql` 和 `libhunt-redis` 都是 `healthy` 即可。

端口是刻意错开的：

| 服务 | 端口 | 原因 |
| --- | --- | --- |
| MySQL | **3307** | 宿主机 3306 常被别的 MySQL 占用 |
| Redis | **6380** | 避开本机已有的 6379 |

后端默认连本机 `3306` / `6379`。用这套 Docker 中间件时，在运行配置的环境变量里加 `SPRING_PROFILES_ACTIVE=docker`，就会加载 `application-docker.yml` 连 3307/6380。

如果不想用 Docker，直接启动后端即可，见第 12.9 节。

---

## 5. 创建运行配置并启动

### 5.1 Ultimate：直接运行主类

1. 打开 `server/src/main/java/com/libhunt/LibHuntApplication.java`。
2. 点类名左侧的绿色三角 → `Run 'LibHuntApplication'`。
3. IDEA 自动创建名为 `LibHuntApplication` 的 Spring Boot 运行配置。

### 5.2 Community：手动建 Application 配置

1. `Run → Edit Configurations…`（`⌥⌘R` 旁边的下拉 → `Edit Configurations`）。
2. 点 `+` → `Application`。
3. 按下表填写：

| 字段 | 值 |
| --- | --- |
| `Name` | `LibHuntApplication` |
| `Module` | `lib-hunt-server`（方式 A 下可能显示为 `server`） |
| `Main class` | `com.libhunt.LibHuntApplication` |
| `JRE` | 21 |
| `Working directory` | `$PROJECT_DIR$` |

`Use classpath of module` 选 `lib-hunt-server` 即可，不需要手动加依赖。

### 5.3 可选：环境变量

默认配置就能跑。需要覆盖时，在运行配置的 `Environment variables` 里加：

| 变量 | 作用 | 示例 |
| --- | --- | --- |
| `JWT_SECRET` | 覆盖开发用默认密钥（至少 32 字节） | `local-dev-secret-at-least-32-bytes-long` |
| `LIBHUNT_QR_SECRET` | 覆盖二维码 HMAC 密钥 | `dev-only-qr-secret-change-me` |
| `SPRING_PROFILES_ACTIVE` | 使用 Docker 中间件时设成 `docker` | `docker` |
| `SPRING_DATASOURCE_URL` | 换数据库地址 | `jdbc:mysql://localhost:3306/libhunt?...` |
| `SPRING_DATA_REDIS_PORT` | 换 Redis 端口 | `6379` |
| `SERVER_PORT` | 换后端端口 | `8081` |
| `ADMIN_STUDENT_NO` / `ADMIN_INITIAL_PASSWORD` | 首次启动创建的管理员账号 | 默认 `admin / Admin@123456` |

也可以不用环境变量，直接在 `Program arguments` 里写 Spring 参数：

```
--server.port=8081
```

> 管理员只在**第一次启动、库里没有该账号时**创建。之后改环境变量不会重置密码。

### 5.4 启动成功的标志

控制台依次出现：

```
Flyway ... Successfully applied ...
Tomcat started on port 8080 (http)
Started LibHuntApplication in x.xxx seconds
```

然后验证：

| 地址 | 预期 |
| --- | --- |
| <http://localhost:8080/api/health> | `{"code":0,...}` |
| <http://localhost:8080/actuator/health> | `{"status":"UP"}` |
| <http://localhost:8080/swagger-ui.html> | Swagger UI 页面 |

前端联调：根目录执行 `npm run dev`，Next.js 默认把 `/api/*` 反代到 `http://127.0.0.1:8080`，正好就是 IDEA 里跑的后端，不需要额外配置。

---

## 6. 断点调试

1. 在关键代码行号左侧点一下，出现红点，例如：
   - `GameRunService.submitAnswer` —— 看判题与结算
   - `GameRunService.start` —— 看幂等键与行锁
   - `QrTokenService.verify` —— 看二维码签名校验
2. 用 **Debug** 启动（`⌃D`），而不是 Run。
3. 触发请求后，IDEA 会停在断点。

调试技巧：

| 场景 | 做法 |
| --- | --- |
| 只想在某个用户/关卡停下 | 右键断点 → `Condition`，填 `levelNo == 1` |
| 看 SQL 参数与返回值 | 断点停在 Mapper 调用前后，`Evaluate Expression`（`⌥F8`） |
| 调试并发 | 右键断点 → `Suspend` 改为 `Thread`，否则一个请求会卡住所有线程 |
| 跳过 Spring 代理/框架代码 | 断点设置里勾选 `Emulate method filters`，或在 Call Stack 里只看 `com.libhunt` 帧 |
| 调试测试 | 测试方法左侧点绿色三角 → `Debug` |

> 断点打太多会显著拖慢并发测试；调试完记得一次清掉（`Run → View Breakpoints` → 全选删除）。

---

## 7. 在 IDEA 里跑测试

### 7.1 跑单个类

打开任意测试类，例如 `GameRunFlowIntegrationTest`，点类名旁的 ▶ → `Run 'GameRunFlowIntegrationTest'`。

### 7.2 跑全部

- Maven 工具窗口 → `lib-hunt-server` → `Lifecycle` → 双击 `test`；或
- 右键 `src/test/java` → `Run 'All Tests'`。

共 **54 个用例**，首次运行需要拉取测试镜像，之后整套约 20 秒。

### 7.3 测试环境的两个关键点

1. **必须先启动 Docker Desktop**。测试用 Testcontainers 自己拉起 `mysql:8.4` 和 `redis:7-alpine`，不依赖第 4 步的 `docker compose`。
2. **测试不会污染开发库**。容器是临时的，跑完自动清理；开发用的 `libhunt` 库保持不变。

测试类覆盖范围见 [server/README.md](../server/README.md#测试)。

> 如果 Docker Desktop 内存给得太小（默认 2 GB 左右），并发测试可能变慢或容器启动失败；调到 4 GB 更稳。

---

## 8. 查看数据库与 Redis

### 8.1 连 MySQL

1. 打开 `Database` 工具窗口（`View → Tool Windows → Database`）。
2. `+` → `Data Source → MySQL`。
3. 填写：

| 字段 | 值 |
| --- | --- |
| `Host` | `localhost` |
| `Port` | `3307` |
| `User` | `root` |
| `Password` | `root` |
| `Database` | `libhunt` |

4. 首次连接会提示下载驱动，点 `Download`。
5. `Test Connection` 通过后 `OK`。

连接串建议加上：

```
jdbc:mysql://localhost:3307/libhunt?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai
```

连上后可以展开 `libhunt → tables`，直接看 `level`、`game_run`、`level_attempt`、`player` 等表。

> **不要用 GUI 手改表结构**。结构变更请新增 `V5__xxx.sql`；直接改库会让 Flyway 校验失败（见 [12.6](#126-flyway-报-checksum-mismatch)）。

### 8.2 看 Redis

IDEA 没有内置 Redis 客户端，用两种方式：

```bash
# 进容器里的 redis-cli
docker exec -it libhunt-redis redis-cli

# 常用命令
KEYS libhunt:*                                  # 仅开发环境使用，生产禁用 KEYS
ZREVRANGE libhunt:leaderboard:global 0 9 WITHSCORES
TTL  libhunt:ratelimit:login:ip:127.0.0.1
```

或者安装图形化工具 RedisInsight，连 `localhost:6380`。

> 想看登录限流是否生效，可以在 `RateLimiterService` 打断点，或连续请求登录接口观察 429。

---

## 9. 用 HTTP Client 调接口

仓库里已经准备好 [server/http/api.http](../server/http/api.http)，覆盖学生学号直登、管理员登录、关卡、开局、判题、扫码、排行榜和管理端。

1. 用 IDEA 打开这个文件。
2. 先点第一个 `### 登录` 请求左侧的绿色三角；响应处理器会自动把 token 存进变量。
3. 再依次运行后面的请求，`{{token}}`、`{{runId}}` 会自动替换。
4. 也可以点文件顶部的 `Run All` 顺序执行。

说明：

- 请求里用的账号是首次启动自动创建的 `admin / Admin@123456`，管理员也可以玩。
- 学生端用 `### 1b. 学生学号直登`，只传学号；首次登录会自动建号，返回的 token 存在 `{{studentToken}}`。
- `POST /api/runs` 带 `Idempotency-Key: idea-{{$timestamp}}`，每次运行都会生成新值。
- 第 1 关正确答案是 `b`；先运行「8. 签发第 1 关二维码」拿到 `{{qrToken}}`，再扫码。先答题再扫码、或先扫码再答题都可以，最后完成的动作触发结算。
- 如果连续登录十几次收到 **429**，是登录限流（IP + 账号各 10 次/分钟）在起作用，等一分钟即可。
- Community 版若找不到 HTTP Client，可以改用 Swagger UI（`/swagger-ui.html`）或 curl；`.http` 文件本身也能被 VS Code 的 REST Client 插件识别。

---

## 10. 改完代码怎么生效

项目没有引入 `spring-boot-devtools`，所以：

| 改动 | 生效方式 |
| --- | --- |
| Java 业务代码 | `⌘F9` 重新编译后重启（`⌃R`）；或直接 `⌃R` 让 IDEA 先构建再启动 |
| `application.yml` | 必须重启 |
| Flyway 迁移脚本 | 必须重启（新增 `V5__*.sql`） |
| Mapper 注解 / SQL | 必须重启 |
| 前端代码 | `npm run dev` 自带热更新，后端不用动 |

> IDEA Ultimate 的 "Update classes and resources" 对改方法体一般有效，但**新增/删除 Bean、改注解、改配置**仍应完整重启，否则会出现"代码改了但行为没变"。

如果确实想要热重启，可以自行加 `spring-boot-devtools`（`optional` 依赖），并开启 `Settings → Build, Execution, Deployment → Compiler → Build project automatically`。注意：devtools 的重启行为和真实启动仍有差异，排障时以完整重启为准。

---

## 11. 日常开发流程

```text
1. 启动 MySQL 和 Redis（本机服务，或用 docker compose up -d）
2. IDEA：Run / Debug LibHuntApplication
3. 根目录：npm run dev
4. 可选：./scripts/seed-demo.sh 灌一批演示账号和通关记录
5. 改代码 → 重启 → 用 HTTP Client / 前端验证
6. 提交前：IDEA 里跑一遍全部测试（或 ./mvnw test）
```

只改前端时，第 3 步可以省略（后端保持运行即可）。

---

## 12. 常见问题

### 12.1 打开后 Java 文件全红，右侧没有 Maven 面板

**原因**：项目没有被识别为 Maven 工程。

**处理**：

- 右键 `server/pom.xml` → `Add as Maven Project`；或
- `Maven` 工具窗口 → `+` → 选择 `pom.xml`；或
- 关掉工程，按 [方式 A](#方式-a推荐只打开后端) 重新打开 `server` 目录。

### 12.2 编译报 `invalid source release: 21` / `不支持 21`

**原因**：项目 SDK 或 Language level 不是 21。

**处理**：`File → Project Structure` 里把 SDK、Language level、模块 Language level 全部改成 21；再到 `Settings → Build Tools → Maven → JDK for importer` 选 21，然后 `Maven → Reload All Maven Projects`。

### 12.3 Lombok 的 getter/setter 找不到

**原因**：注解处理没开，或 Lombok 插件未启用。

**处理**：

1. `Settings → Plugins` 搜索 `Lombok`，确保启用（新版通常已内置）。
2. `Settings → Build, Execution, Deployment → Compiler → Annotation Processors` 勾选 `Enable annotation processing`。
3. `Build → Rebuild Project`。

### 12.4 启动报 `Communications link failure`

**原因**：MySQL 没起，或端口不是 3307。

**处理**：

```bash
docker compose ps
docker compose logs mysql --tail=50
```

确认 `libhunt-mysql` 是 `healthy`，端口映射为 `3307:3306`。如果本机 3307 被占用，`docker compose up -d` 会报端口冲突，先停掉占用进程。

### 12.5 启动报 `Port 8080 was already in use`

**原因**：之前用 `java -jar` 启动的旧实例还在跑，或另一个 IDEA 窗口也启动了后端。

**处理**：

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

- 想保留旧实例：在 IDEA 运行配置里加 `SERVER_PORT=8081`，前端用 `API_PROXY_TARGET=http://127.0.0.1:8081 npm run dev`。
- 想用 8080：结束旧进程（`kill <PID>`）后重新启动。

> 这也是"我改了代码怎么没生效"的常见原因：请求其实打到了旧 jar 上。

### 12.6 Flyway 报 checksum mismatch

**原因**：修改了已经执行过的 `V1`–`V4` 脚本。

**处理**：

- 正确做法：恢复原脚本，新增 `V5__描述.sql`。
- 开发库可以直接重置（**会清空本地数据**）：

```bash
docker compose down -v
docker compose up -d
```

重启后端，Flyway 会从空库重新执行全部迁移。

### 12.7 Testcontainers 报 `Could not find a valid Docker environment`

**原因**：Docker Desktop 没启动，或 IDEA 进程拿不到 Docker socket。

**处理**：

1. 启动 Docker Desktop，等鲸鱼图标稳定。
2. 终端执行 `docker info`，确认没有报错。
3. 如果用的是 Colima / OrbStack，确认 `docker context ls` 的当前 context 正确，并在启动 IDEA 前设置好 `DOCKER_HOST`。
4. 仍不行时重启 IDEA，让它重新读取环境变量。

### 12.8 第一次跑测试很慢，或镜像拉取失败

**原因**：Testcontainers 首次需要下载 `mysql:8.4`（数百 MB）和 `redis:7-alpine`。

**处理**：

- 保持网络畅通，耐心等第一次跑完；之后镜像有缓存，整套约 20 秒。
- 国内网络可给 Docker Desktop 配置镜像加速。
- 不要同时跑压测和测试，容器资源会互相抢占。

### 12.9 不用 Docker：本机 MySQL 和 Redis

可以。后端进程本身不依赖 Docker，它只要求：

| 组件 | 是否必需 | 不可用时的表现 |
| --- | --- | --- |
| MySQL | **必需** | 启动阶段就失败：连接池和 Flyway 都需要数据库 |
| Redis | 可选 | 应用仍能启动；登录限流 fail-open、排行榜回落 MySQL |

#### 1. 启动本机 MySQL 并建库

macOS 常见两种安装方式：

```bash
# Oracle / DMG 安装（默认在 /usr/local/mysql）
sudo /usr/local/mysql/support-files/mysql.server start
# 或者在「系统设置 → MySQL」里点 Start MySQL Server

# Homebrew 安装
brew services start mysql
```

确认能连上：

```bash
mysql -h127.0.0.1 -P3306 -uroot -p -e "SELECT VERSION();"
```

创建数据库（表结构不用手建，Flyway 启动时执行 `V1`–`V4`）：

```sql
CREATE DATABASE IF NOT EXISTS libhunt
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;
```

MySQL 8.0 及以上即可；项目迁移脚本没有使用 8.4 专属语法。

#### 2. 启动本机 Redis（可选）

```bash
brew services start redis
# 或者
redis-server --daemonize yes

redis-cli -p 6379 ping   # 应返回 PONG
```

Redis 8 也可以，项目只用到 ZSET 以及 Redisson 的限流结构。

#### 3. 在 IDEA 运行配置里提供连接信息

`application.yml` 默认已经是本机 `localhost:3306` / `6379`，一般只需要提供 MySQL 密码：

```text
SPRING_DATASOURCE_PASSWORD=你的密码
```

用户名不是 `root`、或端口不是默认值时，再补：

```text
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/libhunt?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
SPRING_DATA_REDIS_PORT=6379
```

- 如果不用 Redis，删掉 `SPRING_DATA_REDIS_PORT` 也能启动，只是限流和排行榜会降级。
- 如果用的是 Docker 中间件（3307/6380），改加 `SPRING_PROFILES_ACTIVE=docker` 即可。

启动后同样用 `/api/health` 和 Swagger 验证。

#### 4. 不用 Docker 时测试怎么跑

Testcontainers 依赖 Docker，所以：

- **可以跑**只有三个纯单元测试类：`GameRulesTest`、`LeaderboardFallbackTest`、`QrTokenServiceTest`；
- **跑不了**其余 6 个集成测试类（它们会启动 MySQL/Redis 容器），也就跑不了完整的 54 个用例。

要跑完整测试，仍需启动 Docker Desktop。不要为了省 Docker 把测试改成连本机开发库——测试会写入和清理数据，容易污染开发环境。

### 12.10 应用能启动，但日志里一直有 Redis 连接错误

**这是设计行为**：Redisson 开了懒初始化，Redis 不可用时应用仍能启动。此时：

- 登录限流 fail-open（不拦截）；
- 排行榜回落 MySQL 窗口函数查询。

功能可用，但 Redis 故障期间请求会等待连接超时，延迟明显变高。修好 Redis 后无需重启应用。

### 12.11 管理员密码不是 `Admin@123456`

**原因**：账号只在首次启动、库中不存在时创建；之后修改环境变量不会覆盖已有密码。

**处理**：

- 用现有密码登录后，通过管理端接口重置；
- 或开发库直接 `docker compose down -v` 重置（会清空全部本地数据）。

### 12.12 控制台中文乱码

**处理**：

1. `Settings → Editor → File Encodings` 全部设 UTF-8；
2. `Help → Edit Custom VM Options` 追加 `-Dfile.encoding=UTF-8`；
3. 重启 IDEA。

### 12.13 IDEA 里能跑，`java -jar` 跑不起来（或反之）

**原因**：两者的配置来源可能不同（环境变量、工作目录、端口）。

**处理**：以 IDEA 控制台第一行的启动参数为准；确认 `docker compose ps`、端口、环境变量一致。打包用：

```bash
cd server
./mvnw clean package -DskipTests
java -jar target/lib-hunt-server-*.jar
```

---

## 13. 快捷键与命令行对照

### 常用快捷键（macOS）

| 操作 | 快捷键 |
| --- | --- |
| 运行当前配置 | `⌃R` |
| 调试当前配置 | `⌃D` |
| 选择运行配置 | `⌥⌘R` |
| 重新编译 | `⌘F9` |
| 项目结构 | `⌘;` |
| 设置 | `⌘,` |
| 全局搜索类/文件/符号 | `⇧⇧` |
| 当前文件搜索 | `⌘F` |
| 全局文本搜索 | `⌘⇧F` |
| 跳转声明 | `⌘B` / `⌘Click` |
| 查找用法 | `⌥F7` |
| 格式化代码 | `⌥⌘L` |
| 优化 import | `⌃⌥O` |
| 求值表达式 | `⌥F8` |
| 单步跳过 / 进入 / 跳出 | `F8` / `F7` / `⇧F8` |
| 继续运行 | `⌥⌘R`（Debug 时） |

### 与终端命令的对应关系

| 终端 | IDEA |
| --- | --- |
| `./mvnw spring-boot:run` | Run `LibHuntApplication` |
| `./mvnw test` | Maven 工具窗口 → `Lifecycle → test` |
| `./mvnw clean package -DskipTests` | Maven 工具窗口 → `Lifecycle → clean`、`package` |
| `docker compose up -d` | IDEA Terminal（仍需命令行） |
| `java -jar target/*.jar` | 一般不用；开发期直接用运行配置 |
| `curl ...` | Swagger UI 或 `server/http/api.http` |

---

## 附：一页速查

```text
1. 启动 Docker Desktop
2. 仓库根目录 docker compose up -d（或本机启动 MySQL/Redis，见 12.9）
3. IDEA 打开 server/，SDK 21，Maven 用 wrapper
4. 运行 com.libhunt.LibHuntApplication
5. 验证 http://localhost:8080/api/health
6. 调接口用 server/http/api.http 或 Swagger
7. 跑测试前确认 Docker 在运行
8. 改结构写 V5__*.sql，不要手改库
```
