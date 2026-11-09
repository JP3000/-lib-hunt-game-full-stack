# FACT SHEET · 图书馆寻宝游戏（Library Treasure Hunt / LibHunt）

> 生成日期：2026-11-09
>
> **口径说明**
> - 以下以**当前工作树**为准，不是 git HEAD。`git status` 显示大量未提交修改：签到/Checkin 与 `LevelCache` 相关文件被删除，同时新增了二维码签发、学号直登、管理端二维码、测试和脚本。HEAD 与工作树不是同一状态。
> - 只写能从代码、迁移、构建文件、测试、脚本、压测原始结果直接验证的内容；文档自述会标注“文档声称”。
> - 本次整理**没有复跑构建和测试**（后续命令执行被权限模式拦截），所以“当前代码测试通过/能编译”一律标 **[待确认]**。`PLAN.md` 里的实跑记录单独注明。
> - **“依赖存在”不等于“已实现”**：例如只引入了 `spring-security-crypto` 做 BCrypt，不是整套 Spring Security；Actuator 只开了默认端点；Redisson 只用于 `RRateLimiter`。
> - **“代码里有”不等于“测试验证过”**：下面会尽量标注验证强度。

---

## 1. 一句话定位

面向高校图书馆信息素养/新生入馆教育场景的 **12 关移动端 H5 寻宝游戏**：学生在馆内按剧情线索答题、到指定位置扫二维码过关，馆员管理账号并查看统计；它把旧版纯前端项目里“答案、判分、进度、成绩都在浏览器里”的模式改成**服务端持有业务真相**，解决答案可篡改、换设备丢进度、成绩无法统计的问题。

---

## 2. 技术栈与版本（只列构建文件/依赖清单里真实存在的）

| 层 | 技术与版本 | 证据 |
| --- | --- | --- |
| 前端框架 | Next.js **16.2.0**、React / React DOM **19.2.4** | [package.json](../package.json) |
| 前端语言/样式 | TypeScript **5.9.3**、Tailwind CSS **4.2.2** | package.json / 依赖安装结果 |
| 前端状态 | Zustand **5.0.12** | package.json |
| 前端动效 | framer-motion **12.38.0** | package.json |
| 扫码 | html5-qrcode **2.3.8** | package.json |
| 前端其他 | canvas-confetti **1.9.4**、ESLint 9、eslint-config-next **16.2.0** | package.json |
| 后端框架 | Spring Boot **3.5.3**、Java **21** | [server/pom.xml](../server/pom.xml) |
| 持久层 | MyBatis-Plus **3.5.7**，显式覆盖 `mybatis-spring` 到 **3.0.4** | server/pom.xml |
| 数据库 | MySQL **8.4**（Docker 镜像 `mysql:8.4`） | [docker-compose.yml](../docker-compose.yml) |
| 迁移 | Flyway core + `flyway-mysql`；版本由 Spring Boot BOM 管理，`pom.xml` 未固定版本号。`server/README.md` 写“Flyway 11”，这是文档口径 | server/pom.xml / [server/README.md](../server/README.md) |
| Redis | Redis **7**（`redis:7-alpine`），开启 AOF | docker-compose.yml |
| Redis 客户端 | Redisson **3.52.0** | server/pom.xml |
| JWT | jjwt **0.12.6**（api / impl / jackson） | server/pom.xml |
| 密码哈希 | `spring-security-crypto`，仅使用 BCrypt；**没有引入完整 Spring Security** | server/pom.xml / [AuthService.java](../server/src/main/java/com/libhunt/service/AuthService.java) |
| 接口文档 | springdoc-openapi **2.8.6**，Swagger UI `/swagger-ui.html` | server/pom.xml / [application.yml](../server/src/main/resources/application.yml) |
| 可观测 | spring-boot-starter-actuator，暴露 `health,info,metrics` | server/pom.xml / application.yml |
| 测试 | JUnit 5（Spring Boot test starter）、Testcontainers（JUnit Jupiter + MySQL） | server/pom.xml |
| 压测 | JMeter **5.6**、ApacheBench **2.3** | [perf/RESULTS.md](../perf/RESULTS.md) / [perf/results/ab-nocache-1.txt](../perf/results/ab-nocache-1.txt) |

**版本口径补充**
- MySQL Connector/J、Flyway、Actuator、Testcontainers 的版本没有在 `pom.xml` 里硬编码，由 Spring Boot 3.5.3 BOM 管理；不要写成某个具体小版本。
- README 要求 Node.js ≥ 20，但这不是构建文件里的 `engines` 锁定，属于文档运行要求。
- Docker 只跑 MySQL/Redis，后端不是容器，直接 `java -jar` / `./mvnw spring-boot:run`。

---

## 3. 数据表、关键字段、唯一约束、索引

迁移脚本共 **V1–V4**，当前 **8 张业务表**：

| 表 | 作用 | 关键字段 | 唯一约束 / 索引 |
| --- | --- | --- | --- |
| `player` | 玩家/管理员账号 | `id`、`student_no`、`password_hash`、`role`（STUDENT/STAFF/ADMIN）、`nickname`、`status`、`created_at`、`updated_at` | `UNIQUE uk_player_student_no(student_no)` |
| `level` | 关卡主数据，含答案 | `id`、`level_no`、`title/story/question` JSON、`correct_option_keys` JSON、`qr_answer`、`qr_location` JSON、`video_url`、`status` | `UNIQUE uk_level_no(level_no)` |
| `level_option` | 关卡选项 | `id`、`level_id`、`option_key`、`label` JSON、`sort_order` | `UNIQUE uk_level_option(level_id, option_key)`；`KEY idx_level_option_level(level_id, sort_order)` |
| `item` | 剧情道具 | `id`、`item_key`、`name/description` JSON、`image_url` | `UNIQUE uk_item_key(item_key)` |
| `level_item` | 关卡→道具奖励关联 | `level_id`、`item_id`、`sort_order` | `PRIMARY KEY(level_id, item_id)`；`KEY idx_level_item_item(item_id)` |
| `game_run` | 一局游戏 | `id`、`player_id`、`status`（IN_PROGRESS/COMPLETED/ABANDONED）、`started_at`、`finished_at`、`total_score`、`duration_seconds`、`last_level_no`、`idempotency_key` | `UNIQUE uk_run_idempotency(idempotency_key)`；`KEY idx_run_player(player_id, status)`；`KEY idx_run_leaderboard(status, total_score DESC, duration_seconds ASC)` |
| `level_attempt` | 一局中某一关的过关记录 | `id`、`run_id`、`level_no`、`score`、`attempts`、`question_passed`、`qr_passed`、`settled`、`completed_at` | `UNIQUE uk_attempt_run_level(run_id, level_no)`；`KEY idx_attempt_run(run_id)` |
| `player_item` | 玩家已收集道具 | `id`、`player_id`、`item_id`、`run_id`、`collected_at` | `UNIQUE uk_player_item(player_id, item_id)`；`KEY idx_player_item_player(player_id)` |

**数据规模（来自种子迁移与验收记录）**
- `level = 12`
- `level_option = 54`
- `item = 13`
- `level_item = 13`
- 三语：`zh-Hant` / `zh-Hans` / `en`
- 来源：[V3__seed_levels.sql](../server/src/main/resources/db/migration/V3__seed_levels.sql)、[PLAN.md](../PLAN.md) 阶段 2 验收记录。

**设计事实**
- **不建物理外键**：引用完整性由应用层校验 + 索引保证；直接 SQL 写入可能产生孤儿记录。设计理由见 [V2__init_game_schema.sql](../server/src/main/resources/db/migration/V2__init_game_schema.sql) 与 [docs/DATA-MODEL.md](DATA-MODEL.md)。
- 多语言文案存 JSON 列，不做“按语言拆表”。
- `level_attempt.settled` 由 [V4__add_attempt_settled.sql](../server/src/main/resources/db/migration/V4__add_attempt_settled.sql) 增加，并回填“两关都已通过”的历史记录。
- `player_item` 是**玩家级唯一**，不是每局唯一；`run_id` 只记录“在哪一局获得”。
- 索引观察：`idx_attempt_run(run_id)` 与 `idx_player_item_player(player_id)` 分别被相邻唯一键的最左前缀覆盖，属于可删的冗余索引；不影响正确性。

---

## 4. 接口数量与分类

实际 Controller mapping 共 **19 个**（18 个业务接口 + 1 个健康检查）。README 接口表列了 17 行，其中 `/api/admin/players` 一行含 GET/POST 两个端点，另加 `/api/health`。

### 公开接口（6）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/student-login` | 学号直登；首次自动建 STUDENT |
| POST | `/api/auth/login` | 账号密码登录 |
| GET | `/api/levels?locale=` | 关卡列表 |
| GET | `/api/levels/{levelNo}?locale=` | 关卡详情，不含答案 |
| GET | `/api/leaderboard?limit=` | 排行榜，每人取最好一局 |
| GET | `/api/health` | 健康检查 |

### 登录后可用（6）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/auth/me` | 当前账号 |
| POST | `/api/runs` | 开局，支持 `Idempotency-Key` |
| GET | `/api/runs/current` | 当前/最近一局 |
| POST | `/api/runs/{runId}/abandon` | 放弃本局 |
| POST | `/api/runs/{runId}/levels/{levelNo}/answer` | 提交答案，服务端判题 |
| POST | `/api/runs/{runId}/levels/{levelNo}/qr` | 提交二维码内容，服务端验签 |

### ADMIN 专用（7）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/admin/levels/{levelNo}/qr?ttlSeconds=` | 签发带过期时间的二维码 |
| GET | `/api/admin/players` | 账号分页/搜索 |
| POST | `/api/admin/players` | 创建账号 |
| PATCH | `/api/admin/players/{id}/status` | 启用/禁用 |
| POST | `/api/admin/players/{id}/password` | 重置密码 |
| GET | `/api/admin/stats` | 玩家数、通关数、平均分/耗时 |
| POST | `/api/admin/leaderboard/rebuild` | 从 MySQL 重建 Redis 排行榜 |

**响应语义（重要）**
- 成功：HTTP 200，`{"code":0,"msg":"ok","data":…}`。
- 业务异常、参数校验失败、未预期异常：由 [GlobalExceptionHandler.java](../server/src/main/java/com/libhunt/common/exception/GlobalExceptionHandler.java) 返回 **HTTP 200 + `code=1`**，不是 4xx。
- 只有拦截器设置的 **401 / 403 / 429** 会同时改变 HTTP 状态码和 `code`。
- 前端 [api.ts](../src/lib/api.ts) 需要同时处理 HTTP 错误和 envelope 里的 `code`；401 会清理本地 token。

---

## 5. 认证与权限模型

### 登录方式

**学生学号直登**
- `POST /api/auth/student-login`，只需 `studentNo`。
- 后端校验：非空、≤32 字符、匹配 `^[A-Za-z0-9._-]+$`。
- 学号不存在时自动创建 `STUDENT`，密码写随机 BCrypt 哈希（学生端不提供密码入口）。
- 只允许 `STUDENT` 走这条路；`STAFF` / `ADMIN` 必须用密码登录，否则知道管理员账号的人可以直接拿管理权限。
- 禁用账号拒绝登录。
- 并发首登由 `student_no` 唯一索引兜底，捕获 `DuplicateKeyException` 后复用已存在账号。

**账号密码登录**
- `POST /api/auth/login`，任意角色。
- 账号不存在时也执行一次 BCrypt 比对（固定 dummy hash），让“账号不存在”和“密码错误”耗时接近。
- 账号不存在/密码错误统一提示“账号或密码错误”；禁用账号提示“账号已被禁用”。
- 登录成功返回 JWT + 玩家信息。

### JWT
- 使用 jjwt 0.12.6，`Keys.hmacShaKeyFor(secret)` 做 HMAC 签名。
- `sub = playerId`，`role` 为自定义 claim，默认有效期 **12 小时**（`libhunt.jwt.expire-hours`）。
- 密钥来自 `JWT_SECRET`，默认值仅本地开发；README 要求至少 32 字节。
- 无 refresh token、无 token 黑名单、无服务端会话。

### 拦截器链
执行顺序见 [WebMvcConfig.java](../server/src/main/java/com/libhunt/config/WebMvcConfig.java)：

1. `LoginRateLimitInterceptor`（order -1）：只拦截 `/api/auth/login`、`/api/auth/student-login`
2. `JwtInterceptor`（order 0）：拦截 `/api/**`
3. `RoleInterceptor`（order 1）：读取方法或类上的 `@RequireRole`

`JwtInterceptor` 排除：
- `/api/auth/login`
- `/api/auth/student-login`
- `/api/health`
- `/api/levels`、`/api/levels/**`
- `/api/leaderboard`

`JwtInterceptor` 校验失败统一返回 **HTTP 401 + JSON**；`afterCompletion` 清理 `UserContext` ThreadLocal。

### RBAC
- `@RequireRole` 可标在类或方法上，默认值是 `ADMIN`。
- `RoleInterceptor` 先看方法注解，再看类注解；不匹配返回 **HTTP 403 + `{"code":403,"msg":"无权限访问"}`**。
- 管理端 Controller 全部标注 `@RequireRole("ADMIN")`：`AdminQrController`、`AdminPlayerController`、`AdminStatsController`、`AdminLeaderboardController`。
- 角色枚举：`STUDENT` / `STAFF` / `ADMIN`。`STAFF` 可以密码登录并使用普通玩家接口，但**没有 STAFF 专属功能**；访问管理端会 403。

### 其他
- CORS 只允许 `http://localhost:3000`、`http://127.0.0.1:3000`，支持 credentials。
- 没有引入完整 Spring Security 过滤器链；BCrypt 来自 `spring-security-crypto`。
- 首次启动自动创建默认管理员 `admin / Admin@123456`，可用 `ADMIN_STUDENT_NO` / `ADMIN_INITIAL_PASSWORD` 覆盖。

---

## 6. 核心业务不变量

### 身份与对局
- **一个学号一个账号**：`player.student_no` 唯一。
- **同一玩家同时只有一局进行中**：`POST /api/runs` 先锁 `player` 行，再查 `IN_PROGRESS` 对局；已有则直接返回，不创建新局。
- **开局幂等**：客户端可带 `Idempotency-Key`；`game_run.idempotency_key` 唯一。同 key 同玩家返回原局；同 key 其他玩家报“幂等键已被占用”。
- **同一局同一关只有一条过关记录**：`UNIQUE(run_id, level_no)`。
- **对局归属校验**：`lockRun` 用 `SELECT ... FOR UPDATE` 锁行后比对 `player_id`；跨玩家操作报“无权操作该对局”。

### 计分与通关
- 每关 **20 分**，12 关满分 **240 分**（[GameRules.java](../server/src/main/java/com/libhunt/common/GameRules.java)）。
- 多选计分：
  - 全部选对：20 分。
  - 只选对一部分：`round(20 × 选中正确数 / 正确总数)`，例如 2 个正确选项只选 1 个得 10 分。
  - 选到任何错误选项：本题 0 分，**不倒扣已有分数**。
- `questionPassed = score > 0`：少选拿到部分分也算通过，但分数低于满分。
- **一旦本题通过，分数锁定**：后续重复提交直接返回原结果，不能重试补分。
- 答错不提示正确答案，只增加 `attempts`；通过后不再增加。
- 两道验证（答题、扫码）相互独立，先做哪个都行；**两道都通过才结算**。
- 结算只发生一次：`level_attempt.settled` 标记；结算时加分、发道具、推进关卡。
- 关卡顺序由 `game_run.last_level_no` 控制；提交非当前关卡直接拒绝，不能跳关。
- 最后一关结算后 `game_run.status = COMPLETED`，写入 `finished_at` 和服务端计算的 `duration_seconds`；否则 `last_level_no = levelNo + 1`。

### 状态机
`game_run.status`：
- `IN_PROGRESS` → `COMPLETED`：最后一关结算。
- `IN_PROGRESS` → `ABANDONED`：主动放弃。
- 没有反向转换；已完成/已放弃对局不会被 `abandon` 改回。
- `GET /api/runs/current`：优先返回进行中的局；没有则返回最近一局非 `ABANDONED` 对局（用于通关后刷新恢复结果）。

### 二维码
- 不是固定明文密码，格式：`v1.<levelNo>.<expireEpochSecond>.<hex HMAC-SHA256>`。
- 签名密钥 = 服务端密钥 + `|` + 该关 `qr_answer`，两者都不下发客户端。
- 校验：版本、关卡号、过期时间、常量时间签名比较；容忍大小写和首尾空白。
- 默认 TTL **604800 秒（7 天）**，管理端签发时限制在 **60 秒 ~ 30 天**。
- 扫码失败不扣分、不结束对局，可以继续试。
- **本项目没有“一次性二维码”**；同一关二维码在 TTL 内可重复使用，也可被拍照转发。若“一人一码”指“一个学号一个账号”，由 `uk_player_student_no` 保证；若指“一次性二维码”，**没有实现**。

### 道具与排行榜
- 结算时按 `level_item` 发放道具，使用 `INSERT IGNORE` 配合 `UNIQUE(player_id, item_id)` 防重复。
- 排行榜只统计 `COMPLETED` 对局；读取时每个玩家只保留最好一局。
- Redis ZSET 分数编码：`totalScore × 100000 + (100000 - durationSeconds)`；同分用时短者靠前。
- 通关后在**事务提交后**写 Redis；Redis 写失败只记日志，不影响通关。
- Redis 不可用/为空时回落 MySQL 窗口函数查询，并顺手预热。
- ZSET 最多保留 **1000 条 run 记录**；接口 `limit` 限制 1–100；重建时从 MySQL 取最多 **100,000** 条每人最好成绩。

### 管理端
- 管理员不能禁用当前登录账号（`PlayerAdminService.updateStatus`）。
- 账号分页 `size` 限制 1–100。
- 重置密码不校验旧密码（管理员操作）。
- 没有账号删除接口。

---

## 7. 已实现的非功能点与验证强度

### 幂等与并发
- **开局幂等**：`Idempotency-Key` + `uk_run_idempotency` 唯一索引。
- **答题/扫码幂等**：已通过则直接返回原结果，不重复加分。
- **结算幂等**：`level_attempt.settled` 标记；`UNIQUE(run_id, level_no)` 兜底。
- **并发控制**：答题/扫码对 `game_run` 行 `SELECT ... FOR UPDATE`；开局对 `player` 行加锁。
- **验证强度**：`GameRunFlowIntegrationTest`、`GameRunConcurrencyIntegrationTest` 覆盖重复提交、10 线程并发、并发开局、并发结算。属于**集成测试验证**。

### 限流
- IP 维度：`LoginRateLimitInterceptor`，`/api/auth/login` 与 `/api/auth/student-login`，**10 次/分钟**，超限 **HTTP 429**。
- 账号维度：`AuthService`，key 为 `libhunt:ratelimit:login:account:<studentNo>`，**10 次/分钟**；超限抛业务异常，HTTP 200 + `code=1`。
- 实现：Redisson `RRateLimiter`，`trySetRate(OVERALL, permits, window)`；桶 TTL 为窗口的 2 倍。
- **失败放行（fail-open）**：Redis 不可用时返回 `true`，记警告日志。
- IP 取值优先 `X-Forwarded-For` 第一段，否则 `remoteAddr`；前提是反向代理覆盖该头且后端不直接暴露公网。
- **验证强度**：`LoginRateLimitIntegrationTest` 覆盖 IP 429 与账号级跨 IP 限流。

### 缓存
- **当前后端没有关卡内容缓存**：`LevelCache` 相关文件已在工作树中删除，关卡详情每次回源 MySQL。
- Redis 当前承担：排行榜 ZSET、登录限流；**不是关卡内容缓存**。
- 前端有 `use-level-catalog.ts` 的模块级内存目录缓存（按 locale、inflight 去重），只减少重复请求，不是服务端缓存。
- **验证强度**：前端无自动化测试；缓存删除后的性能只有 `ab` 数据，没有缓存一致性测试。

### 事务与一致性
- `start`、`abandon`、`submitAnswer`、`submitQr` 使用 `@Transactional`；MySQL InnoDB。
- 结算更新 attempt、run、道具、排行榜副作用在同一事务内；排行榜写入注册到 `afterCommit`，避免回滚后留下 Redis 成绩。
- 没有物理外键；没有乐观锁版本号；并发主要靠行锁 + 唯一索引。
- **验证强度**：集成测试间接覆盖事务行为；没有专门的“事务回滚后 Redis 不写入”故障注入测试。

### 排行榜
- Redis ZSET + MySQL 窗口函数兜底 + 管理端全量重建 + 1000 条裁剪。
- 读路径 Redis 只负责排序，昵称/分数仍批量回源 MySQL；Redis 为空时会回落 MySQL 并预热。
- **验证强度**：`LeaderboardIntegrationTest` 覆盖排序、每人最好一局、预热与重建；`LeaderboardFallbackTest` 用 Mockito 覆盖 Redis 故障回源。

### 安全
- BCrypt 密码哈希；账号不存在也跑 dummy BCrypt；登录失败统一提示。
- JWT HMAC 签名、12 小时过期；`@RequireRole` + 拦截器做 RBAC。
- 二维码 HMAC + 过期时间；对外 VO 不含 `correctOptionKeys` / `qrAnswer`。
- CORS 白名单。
- **验证强度**：`ApiSecurityIntegrationTest` 覆盖 401/403、登录、禁用账号、答案不泄露；`QrTokenServiceTest` 覆盖签名校验。**管理端账号 CRUD 和二维码签发 Controller 没有直接 HTTP 测试**。

### 可观测性
- Spring Boot Actuator 暴露 `health,info,metrics`；`health.show-details: always`。
- 开发期 MyBatis SQL 打到 stdout；`com.libhunt` 日志级别 debug。
- **没有**自定义业务指标、Tracing、告警、日志聚合、审计表。
- Actuator 不在 `/api/**` 拦截范围，且项目没有完整 Spring Security 过滤器链；`/actuator/health` 默认公开，`show-details: always`。这是本地项目的边界，不是生产加固方案。

### 异步/审计
- **没有 MQ、没有异步任务、没有定时任务**；唯一的“提交后副作用”是排行榜 `afterCommit` 写 Redis。
- **没有操作审计表**：管理员创建账号、禁用、重置密码等动作没有独立审计记录，只有应用日志（且未看到专门的审计日志语句）。

### 性能与运维
- Hikari 最大连接池 **10**，最小空闲 2，连接超时 3000ms。
- Redis 连接超时 3s；Redisson 开启 `setLazyInitialization(true)`，Redis 未启动时应用可以先起来。
- Flyway 启动时自动迁移 V1–V4；默认开发库 `localhost:3306/libhunt`，Docker profile 用 3307/6380。
- **没有 CI 配置**（仓库内未发现）；没有 CD。
- **验证强度**：`ab` 单接口 + JMeter 混合场景有原始结果；数据库执行时间没有 profiling。

---

## 8. 测试与冒烟脚本

### 自动化测试：9 个测试类 / 54 个 `@Test`

| 测试类 | 用例数 | 覆盖内容 |
| --- | ---: | --- |
| `GameRulesTest` | 5 | 每关 20 分、部分分、错选不得分 |
| `GameRunFlowIntegrationTest` | 16 | 开局、判题、答题/扫码顺序、幂等、错码、跳关、跨玩家、放弃重开、12 关通关 240 分、部分分锁定 |
| `GameRunConcurrencyIntegrationTest` | 5 | 10 线程并发答题、并发答题+扫码只结算一次、两玩家互不阻塞、10 线程并发开局、同幂等键只开一局 |
| `LeaderboardIntegrationTest` | 4 | 排序、每人最好一局、预热、重建 |
| `LeaderboardFallbackTest` | 2 | Redis 故障回源 MySQL、写排行榜失败不影响通关 |
| `ApiSecurityIntegrationTest` | 8 | 401/403、管理员统计 200、登录失败统一提示、禁用账号、关卡详情不泄题、`/me` |
| `StudentLoginIntegrationTest` | 5 | 自动建号、复用账号、管理员/禁用账号拦截、非法学号 |
| `QrTokenServiceTest` | 6 | 有效、过期、关卡不符、篡改、密钥错误、大小写/空白 |
| `LoginRateLimitIntegrationTest` | 3 | IP 429、账号级限流跨 IP |
| **合计** | **54** | |

**运行方式**
```bash
cd server
./mvnw test
```
- Testcontainers 自动拉起真实 `mysql:8.4` + `redis:7-alpine`，不依赖本机 docker compose，也不污染开发库。
- 测试基类使用静态共享容器；每个测试前清理玩家道具、过关记录、对局、非管理员玩家和 `libhunt:*` Redis key。
- 不使用 H2，因为判题依赖 `FOR UPDATE`、排行榜依赖窗口函数、迁移使用 JSON 列，这些都是 MySQL 特有行为。

**当前状态**
- `@Test` 计数为 **54**，与 README / `server/README.md` / `PLAN.md` 一致。
- `PLAN.md` 记录 **2026-11-06** 实跑结果：`Tests run: 54, Failures: 0, Errors: 0`，约 20 秒。
- **本次会话未复跑**，当前工作树（含删除 Checkin/LevelCache、新增二维码/学号直登等未提交改动）是否仍然全绿：**[待确认]**。
- 没有覆盖率报告（未发现 JaCoCo 配置），覆盖率百分比：**[待确认]**。
- 前端没有测试脚本；`package.json` 只有 `dev/build/start/lint`。前端测试、E2E：**未实现**。

### 脚本与手动冒烟

| 脚本/文件 | 性质 | 关键数字/说明 |
| --- | --- | --- |
| [scripts/seed-demo.sh](../scripts/seed-demo.sh) | API 驱动演示数据 | 10 个学生 `ab565601`–`ab565610`，每人 12 关、12 个二维码，额外进行中/已放弃对局；可重复执行；需要后端已启动、curl、jq |
| [scripts/seed.sql](../scripts/seed.sql) | 直接 SQL 演示数据 | 10 个玩家；10 条通关记录（240/240/196/192/188/179/175/172/164/164）+ 2 条进行中 + 1 条已放弃；需先清理 `game_run` 并重建/删除 Redis 排行榜 |
| [perf/setup-load-test.sh](../perf/setup-load-test.sh) | 压测数据准备 | 创建 20 个 `lt-XX` 账号，分别登录并发起对局，输出 `perf/players.csv` |
| [perf/summarize.mjs](../perf/summarize.mjs) | 压测结果汇总 | 解析 JMeter JTL 生成 Markdown 指标 |
| [scripts/export-levels.mjs](../scripts/export-levels.mjs) | 内容→SQL 导出 | 从 `src/data/levels.ts` 生成 Flyway SQL；校验三语关卡数、选项集合、语言无关字段一致 |
| [server/http/api.http](../server/http/api.http) | IDEA HTTP Client 手动调试 | 16 个请求块；不是自动断言测试 |

**启动命令（文档口径）**
```bash
# 中间件（只跑 MySQL/Redis）
docker compose up -d

# 后端
cd server
SPRING_PROFILES_ACTIVE=docker ./mvnw spring-boot:run

# 前端
npm install
npm run dev

# 构建/检查
npm run build
npm run lint
```
不使用 Docker 中间件时，后端默认连 `localhost:3306` / `localhost:6379`，不需要 `SPRING_PROFILES_ACTIVE=docker`。

---

## 9. 可量化指标（含口径与来源）

### 9.1 数据量与配置上限

| 指标 | 数值 | 口径/来源 |
| --- | ---: | --- |
| 关卡数 | 12 | V3 种子；PLAN 阶段 2 验收 |
| 选项数 | 54 | V3 种子；PLAN 阶段 2 验收 |
| 道具数 / 关卡道具关联 | 13 / 13 | V3 种子；PLAN 阶段 2 验收 |
| 语言数 | 3 | `zh-Hant` / `zh-Hans` / `en` |
| 每关满分 / 总满分 | 20 / 240 | `GameRules.LEVEL_BASE_SCORE`；前端常量 |
| 排行榜 ZSET 上限 | 1,000 条 run | `LeaderboardService.ZSET_MAX_SIZE` |
| 排行榜接口 limit | 1–100 | `LeaderboardService.top` |
| 排行榜重建扫描上限 | 100,000 条每人最好成绩 | `selectTopBestPerPlayer(100_000)` |
| 登录限流 | 10 次/分钟（IP + 账号两层） | `LoginRateLimitInterceptor` / `AuthService` |
| JWT 有效期 | 12 小时 | `libhunt.jwt.expire-hours` |
| 二维码 TTL | 默认 7 天；限制 60 秒–30 天 | `AdminQrController` |
| Hikari 连接池 | 最大 10，最小空闲 2，连接超时 3000ms | `application.yml` |
| Redis 连接超时 | 3s | `application.yml` |
| 账号分页 size | 1–100 | `PlayerAdminService.page` |
| CORS 允许源 | 2 个本地源 | `WebMvcConfig` |

### 9.2 单接口压测（`ab`）

**口径**：`GET /api/levels/1?locale=zh-Hant`，`ab -k -n 5000 -c 50`，JVM 预热后跑 3 轮取中位数；单实例、同机客户端、Apple M3 16GB、Temurin 21.0.10、Tomcat 默认线程池。

| 模式 | 吞吐 | P50 | P95 | P99 | 来源 |
| --- | ---: | ---: | ---: | ---: | --- |
| 无缓存（当前代码） | **3,027 req/s** | 12 ms | **43 ms** | 71 ms | [perf/RESULTS.md](../perf/RESULTS.md)、[ab-nocache-1](../perf/results/ab-nocache-1.txt) / [2](../perf/results/ab-nocache-2.txt) / [3](../perf/results/ab-nocache-3.txt) |
| 缓存开启（历史，缓存已移除） | **11,507 req/s** | 4 ms | **7 ms** | 10 ms | [perf/RESULTS.md](../perf/RESULTS.md)、[ab-cache-1](../perf/results/ab-cache-1.txt) / [2](../perf/results/ab-cache-2.txt) / [3](../perf/results/ab-cache-3.txt) |

- 无缓存 3 轮原始吞吐：3026.84 / 3195.00 / 2938.68；中位数 3026.84。
- 缓存 3 轮原始吞吐：11506.74 / 12027.38 / 10345.97；中位数 11506.74。
- 历史结论“约 3.8 倍吞吐、P95 低 6 倍”来自这组数据。
- **当前代码对应的只有“无缓存”一行**；缓存数字不能当成当前系统指标。

### 9.3 混合场景压测（JMeter，历史）

**口径**：JMeter 5.6，120 线程，单实例；当次共 62,000 次请求，包含一个后来移除的接口，且关卡详情仍走缓存。**这是历史结果，不是当前代码的整体吞吐。**

| 请求 | 样本 | 错误率 | 平均 | P95 | P99 | 最大 | 吞吐 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `GET /api/levels/{n}`（50 线程） | 50,000 | 0% | 3.8 ms | 7 ms | 10 ms | 55 ms | 7,026 req/s |
| `GET /api/leaderboard`（30 线程） | 9,000 | 0% | 7.4 ms | 16 ms | 24 ms | 130 ms | 1,357 req/s |
| `POST .../answer`（20 线程） | 1,000 | 0% | 13.0 ms | 26 ms | 42 ms | 79 ms | 275 req/s |
| **整体** | **62,000** | **0%** | — | — | — | — | **8,712 req/s / 7.1s** |

来源：[perf/RESULTS.md](../perf/RESULTS.md)。

**同期 Redis / MySQL 计数增量**
- Redis `keyspace_hits`：+67,000
- Redis `keyspace_misses`：+0
- MySQL `Com_select`：+22,001
- 口径：压测前后 `INFO stats` / `Com_select` 差值；这组数据说明当时 5 万次关卡详情没有缓存未命中，2.2 万次 SELECT 主要来自排行榜昵称补查和判题。
- 它**不是数据库执行时间**，只是 SQL 次数。

### 9.4 Redis 故障降级
- 文档记录：Redis 换成不可达端口后，排行榜与登录接口仍返回 200，不报错；涉及 Redis 的请求单次约 **1.8 秒**（JVM 冷启动实测）。
- 原始文件 [ab-redis-down.txt](../perf/results/ab-redis-down.txt) 记录：`/api/levels/1`，2000 请求，**60.76 req/s**，P50 781ms，P95 1021ms，P99 1033ms，最大 1066ms。
- **口径提醒**：该 ab 文件是历史故障记录，路径为关卡详情，数字与“当前关卡详情不依赖 Redis”的结论不能直接对齐；应作为“Redis 不可达时延迟劣化到百毫秒~秒级”的历史证据，而不是当前关卡接口的基准。
- 没有熔断器；失败后每次请求仍会尝试连接 Redis。

### 9.5 测试与运行时间
- 测试用例数：**54**（计数，不是文档转述）。
- `PLAN.md` 记录实跑：54/54 通过，约 20 秒；**本次未复测 [待确认]**。
- Testcontainers 首次运行需拉取 `mysql:8.4`、`redis:7-alpine` 镜像。

### 9.6 没有的数据
- **数据库执行时间 / 慢查询统计 / EXPLAIN 实测耗时：没有单独 profiling 数据 [待确认]**。
- **多实例 / 多机 / 云环境压测：没有**；只有单机数据。
- **前端性能指标（LCP/CLS/打包体积）：没有 [待确认]**。
- **生产环境指标：没有**；项目刻意不部署。

---

## 10. 明确没做的、已知边界

### 产品与内容
- **没有关卡 CMS**：内容源是 `src/data/levels.ts`，改完要重新生成 Flyway SQL 并重启后端。
- **没有 Excel/POI 批量导入**（PLAN 阶段 7 明确“POI 导出未做”）。
- **没有可视化图表**：管理端只有基础统计数字。
- **没有账号删除**；没有学生自助改密码，只有管理员重置。
- **没有密码找回/邮箱验证/短信验证**。

### 安全边界
- 二维码**不是一次性**：TTL 内可拍照转发；没有绑定玩家/对局/nonce。HMAC 只防伪造，不防转发。
- JWT 无状态：禁用账号或改角色后，已签发的 token 在 12 小时内仍然有效；没有 Redis 黑名单/版本号。
- Token 存在浏览器 `localStorage`（`libhunt.token`），不是 HttpOnly Cookie；XSS 面存在。
- 没有操作审计表；管理员敏感操作没有独立审计记录。
- 登录接口有限流，但**答题、扫码、管理端接口没有应用层限流**。
- `X-Forwarded-For` 直接取第一段；如果后端端口直接暴露公网，客户端可伪造该头绕过 IP 限流。
- Swagger UI 和 Actuator 没有额外鉴权；Actuator `health` 还配置了 `show-details: always`。
- 没有 CSRF 防护配置（当前用 Authorization header，不是 Cookie 会话，但 localStorage token 仍有 XSS 风险）。
- 前后端学号规则不一致：前端是 `^[A-Za-z]{2}\d{6}$`，后端学号直登是 `^[A-Za-z0-9._-]+$`（≤32）；管理员创建账号甚至没有学号格式 pattern。

### 可靠性与架构
- **没有熔断器**：Redis 故障时 fail-open + 每次重试，延迟会劣化到百毫秒~秒级。
- **没有 MQ / 异步削峰 / 最终一致性机制**；排行榜写 Redis 是 `afterCommit` 同步副作用，失败只记日志，不重试。
- **没有分布式事务**；只保证单库事务内的幂等和结算一次。
- **没有物理外键**；直接 SQL 写入可能产生孤儿数据。
- **没有多实例部署验证**；行锁方案理论上支持多实例，但没有实测。
- **没有备份/容灾/灰度/回滚方案**。
- 排行榜读路径仍回源 MySQL 补昵称和分数；Redis 只承担排序。
- Redis 排行榜 ZSET 只保留 1000 条 run；被裁掉的成绩仍可从 MySQL 重建，但重建只扫每人最好成绩的前 100,000 条。

### 质量与运维
- **没有前端自动化测试、E2E 测试**；`npm run lint` 不是测试。
- **没有 CI/CD**；没有覆盖率报告。
- 管理端账号 CRUD、二维码签发 Controller 没有直接 HTTP 测试；`QrTokenService` 单测覆盖签名逻辑，但不等于 Controller 集成测试。
- 没有数据库执行时间 profiling、慢查询分析、EXPLAIN 基准。
- 压测只在单机跑过，没有多机/云环境数据。
- `scripts/export-levels.mjs` 只生成 `INSERT`，适合全新库；已有数据环境需要手工改成按 `level_no` / `item_key` 的 `UPDATE`，没有自动化。
- Flyway 会校验已执行脚本 checksum；V3 跑过后不能原地改，否则 checksum mismatch。

### 明确不做（不是待办）
- **公网部署**：作品集项目刻意不部署，本地 `docker compose` + 两个进程就是完整系统。
- 微服务、MQ、关卡 CMS、为了技术栈而保留的缓存：都明确不做，理由在 [PLAN.md](../PLAN.md) 和 [docs/BEFORE-AFTER.md](BEFORE-AFTER.md)。

---

## 11. 最能体现工程判断的 5 个点

| # | 判断点 | 具体做法 | 代价/边界 |
| --- | --- | --- | --- |
| 1 | **把“业务真相”迁到服务端，而不是修前端** | 答案、二维码密钥只存 MySQL；对外 VO 不含 `correctOptionKeys` / `qrAnswer`；前端只提交选项 key 和扫码内容；判题、计分、结算、进度全部服务端完成 | 前端从“本地即时算分”变成“每次请求服务端”，增加网络与错误处理；但换来了不可篡改、多设备进度和可统计成绩 |
| 2 | **三层幂等 + 数据库行锁，而不是 JVM 锁/Redis 锁** | 开局：`Idempotency-Key` + 唯一索引；答题/扫码：已通过直接返回；结算：`settled` 标记 + `UNIQUE(run_id, level_no)`；并发：`SELECT ... FOR UPDATE` 锁 `game_run`，开局锁 `player` | 行锁会把同一局请求串行化，极端并发下吞吐受限于单局；但粒度是“一局”，比 `synchronized` 的 JVM 级锁更细，也不引入 Redis 锁的超时/续期问题 |
| 3 | **排行榜是派生索引，不是真相源** | Redis ZSET 只负责排序；通关后 `afterCommit` 写入；Redis 失败/为空回落 MySQL 窗口函数；管理端可从 MySQL 全量重建；ZSET 裁剪到 1000 条 | 读路径仍要回 MySQL 补昵称/分数，Redis 不是“整张榜缓存”；但 Redis 故障不影响通关正确性，重建路径明确 |
| 4 | **用测量结果删缓存，而不是为了技术栈保留** | 先测出缓存开启 11,507 req/s、P95 7ms，无缓存 3,027 req/s、P95 43ms；评估数据只有 12 关 × 3 语言后，删掉 `LevelCache`，接受回源 MySQL | 吞吐下降约 3.8 倍，P95 变差；但少了一套缓存失效逻辑，当前规模下 3,027 req/s 已足够。这是“删除”而非“堆技术”的判断 |
| 5 | **对失败模式做显式取舍，并用真实基础设施验证** | 限流 Redis 故障时 fail-open；排行榜 Redis 故障回落 MySQL；二维码用 HMAC + TTL 防伪造；测试不用 H2，而用 Testcontainers 起真实 MySQL 8.4 + Redis 7 验证 `FOR UPDATE`、窗口函数、JSON 列和并发 | 没有熔断器，Redis 故障时延迟会劣化；二维码在 TTL 内仍可转发；这些短板都写进 README/PLAN，而不是假装“高可用” |

---

**一句话总结**：这个仓库的硬事实是“一个 12 关、3 语言、19 个 HTTP 接口、8 张表、54 个后端测试、单机 3,027 req/s（关卡详情，无缓存）的全栈 H5 游戏”；它的工程价值不在功能数量，而在**服务端权威、并发幂等、排行榜降级、按测量结果删缓存**这四处取舍。
