# 项目教学与面试指南

> 这份文档写给"要拿这个项目去面试"的人。目标：能在 1 小时内把项目讲清楚，能在简历上写出有分量的三行，能扛住面试官往深里追。
>
> 配套文档：技术点讲稿 [INTERVIEW.md](INTERVIEW.md)、表结构 [DATA-MODEL.md](DATA-MODEL.md)、压测数据 [perf/RESULTS.md](../perf/RESULTS.md)。

## 怎么用这份文档

| 时间 | 做什么 |
| --- | --- |
| 第 1 遍（30 分钟） | 读「项目全景」+「六个核心机制」+「一次通关的全链路」，建立整体记忆 |
| 第 2 遍（30 分钟） | 打开代码，按「代码地图」把每个机制对应的文件过一遍 |
| 第 3 遍（20 分钟） | 用「面试题速查」自测，卡住的去 INTERVIEW.md 补齐 |
| 面试前 10 分钟 | 只看「十个数字」和「自我介绍模板」 |

---

## 一、一句话介绍

**30 秒版（自我介绍里用）**

> 这是一个图书馆寻宝主题的 H5 游戏。我把它从纯前端改造成了 Spring Boot + MySQL + Redis 的全栈系统：答案和判题全部放到服务端，前端只提交"我选了什么"；进度、结算、排行榜都由服务端负责。重点做了并发下的幂等和一致性，用真实的 MySQL/Redis 容器写了 49 个测试，也做了 JMeter 压测。

**90 秒版（面试官说"介绍一下你的项目"）**

> 项目本身是一个 12 关的线下寻宝游戏，玩家在图书馆里按线索找二维码，扫码加答题过关。最初是纯前端的，答案打包在 JS 里，分数由浏览器算，进度存 localStorage——能玩，但数据不可信。
>
> 我把它改成了前后端分离：Next.js 只做页面和扫码，Spring Boot 负责鉴权、判题、计分、结算和进度，MySQL 存内容和成绩，Redis 做排行榜和缓存。核心难点是并发：同一关的答题和扫码可以任意顺序完成，还会因为网络重试重复提交，所以我用幂等键、唯一索引、行锁加一个结算标记，保证"只加一次分"。
>
> 验证上，集成测试用 Testcontainers 起真实 MySQL 和 Redis，49 个用例，包含 10 线程并发的场景；压测 120 线程混合请求 6.2 万次，0 错误，整体 8700 req/s，关卡详情开缓存比关缓存快 3.8 倍。

---

## 二、简历怎么写

### 项目标题行

```
图书馆寻宝游戏 · 全栈改造（个人项目）
Next.js 16 / React 19 · Spring Boot 3.5 / Java 21 · MySQL 8.4 / Redis 7 · MyBatis-Plus · JWT · Testcontainers
```

### 精简版（空间有限，放 3 条）

- 将纯前端 H5 游戏改造为 **Spring Boot + MySQL + Redis** 全栈系统，判题、计分、进度、结算全部迁至服务端，前端不再持有任何答案
- 用**幂等键 + 唯一索引 + 行锁 + 结算标记**解决重复提交与并发结算，10 线程并发下只落一条记录、只结算一次
- 搭建 **Testcontainers** 真实 MySQL/Redis 测试体系（49 用例）+ JMeter 压测；关卡详情接口开缓存 11,507 req/s、关缓存 3,027 req/s（**3.8 倍**），P95 从 43 ms 降到 7 ms

### 完整版（6 条，适合一页简历的项目区块）

- **架构改造**：把答案、判题、进度从 JS/localStorage 迁到服务端；Next.js 通过同源 `/api` 反代访问 Spring Boot，无跨域、无硬编码后端地址
- **并发与幂等**：开局用 `Idempotency-Key` + 唯一索引；答题/扫码用 `SELECT ... FOR UPDATE` 串行化同一局请求；`settled` 标记保证"最后完成的动作只结算一次"
- **排行榜**：Redis ZSET 用 `totalScore * 100000 + (100000 - durationSeconds)` 编码复合排序，事务提交后写入；Redis 故障自动回落 MySQL 窗口函数查询，支持一键全量重建
- **缓存与限流**：Cache-Aside 处理穿透（空值标记）/击穿（Redisson 锁）/雪崩（随机 TTL）；登录做 IP + 账号双层令牌桶限流，超限返回 429
- **Redis BitMap**：每日打卡用一天一位的位图存储，连续天数回溯统计，全年数据仅约 46 字节
- **质量保障**：49 个测试跑在真实容器上（含并发、缓存降级、限流、RBAC）；JMeter 120 线程混合压测 62,000 请求、0 错误、8,712 req/s

### 写简历的三条原则

1. **每条都要有"机制 + 结果"**：不要写"使用了 Redis"，要写"用 ZSET 复合排序 + 事务提交后写入，Redis 挂掉自动回落 MySQL"
2. **数字必须能解释**：写 3.8 倍，就要能说出对照组怎么来的（同一接口关缓存，不是"停掉 Redis"）
3. **不写自己讲不清的**：没做分布式事务，就不要写"保证分布式一致性"；没部署，就别写线上 QPS

---

## 三、项目全景

### 它是什么

12 关的图书馆寻宝游戏。每关两道验证：

1. **答题**——图书馆检索知识，首答 100 分，每错一次扣 20，最低 20
2. **扫码**——到馆内指定位置扫二维码，服务端校验内容

两者顺序不限，都通过才结算：加分、发道具、解锁下一关。12 关满分 1200，同分比用时。

### 架构

```
浏览器（手机）
  │  只请求同源 /api/*
  ▼
Next.js 16 :3000        页面 / 多语言 / 摄像头扫码
  │  rewrite（本地）
  ▼
Spring Boot :8080       JWT / 判题 / 结算 / 限流 / 缓存
  │           │
  ▼           ▼
MySQL :3307  Redis :6380
内容与成绩    排行榜 / 缓存 / 打卡 / 限流
```

**为什么浏览器只连 Next.js**：没有跨域、前端无后端地址、鉴权头和错误格式只处理一层；后端可被任何客户端复用。生产上加 Nginx 做同样的 `/api` 分流即可——但本项目不部署，这不是重点。

### 代码地图

**后端**（`server/src/main/java/com/libhunt/`）

| 目录 | 内容 | 面试要看的 |
| --- | --- | --- |
| `controller/` | 9 个 REST 控制器 | `RunController`：开局、答题、扫码 |
| `service/` | 全部业务 | `GameRunService`（判题/结算）、`LevelCache`（缓存）、`LeaderboardService`、`CheckinService` |
| `interceptor/` | JWT 鉴权、登录限流 | `JwtInterceptor`、`LoginRateLimitInterceptor` |
| `common/` | `Result`、异常、`GameRules`、`UserContext` | `GameRules`：计分规则集中一处 |
| `config/` | WebMvc、Redisson、AdminBootstrap | `RedissonConfig`：懒连接 |
| `mapper/` | MyBatis-Plus Mapper | `GameRunMapper` 的 `FOR UPDATE` |

**前端**（`src/`）

| 目录 | 内容 |
| --- | --- |
| `app/[lang]/` | 多语言页面：登录、地图、关卡、排行榜、结果 |
| `components/` | 扫码、关卡卡片、打卡卡、AuthGate |
| `store/game-store.ts` | Zustand：只缓存登录态和展示用的进度，真相在服务端 |
| `lib/api.ts` | `apiFetch`：统一响应体、token、401 清理 |
| `data/levels.ts` | 关卡内容源文件，用于生成 SQL 种子 |

---

## 四、六个核心机制（面试主线）

### 1. 服务端权威：答案不下发

**问题**：旧版答案在 JS bundle 里，DevTools 一搜就有；分数浏览器算，改一行就满分。

**设计**：关卡详情接口返回题目、选项、道具，**不含** `correct_option_keys` 和 `qr_answer`；前端只提交选项 key 和扫码内容，服务端判对错并返回 `correct` 与下一步动作。

**代码**：`LevelController` → `LevelService` → `LevelDetailVO`；`GameRunService#submitAnswer`

**一句话**：正确答案只存在于数据库和服务端内存，任何 HTTP 响应里都没有。

### 2. 幂等：重试不能多加分

**问题**：网络抖动、用户连点、网关重试都会造成重复提交。

**三层设计**：

| 层 | 机制 |
| --- | --- |
| 开局 | `Idempotency-Key` 请求头 + `game_run.idempotency_key` 唯一索引；同 key 返回同一局，换玩家使用会被拒绝 |
| 答题/扫码 | 已通过的记录直接返回原结果，不再计分 |
| 结算 | `level_attempt.settled` 标记，只有第一次会加分、发道具、推进关卡 |

**代码**：`GameRunService#start`、`#submitAnswer`、`#maybeSettle`；迁移 `V4`

**一句话**：唯一索引兜住并发插入，`settled` 兜住"两个动作先后完成"的重复结算。

### 3. 并发：行锁，不是 synchronized

**问题**：同一关的答题和扫码是两个独立请求，可能同时到达；同一玩家可能连点开局。

**设计**：答题/扫码先 `SELECT ... FOR UPDATE` 锁住 `game_run` 那一行，同一局的请求排队；开局先锁 `player` 行，保证一个玩家只创建一局。

**为什么不用 synchronized**：JVM 锁只在单实例有效，多实例部署失效；粒度是整个方法，会把所有玩家串在一起。

**为什么不用 Redis 分布式锁**：锁的粒度是"一局"，数据库行锁已经够用，还少一次网络往返和锁续期/误删问题。Redisson 只用在跨请求的缓存重建上。

**代码**：`GameRunService#lockRun`、`GameRunMapper#selectByIdForUpdate`；测试 `GameRunConcurrencyIntegrationTest`

### 4. 排行榜：一个 double 编码两个维度

**问题**：既要按分数降序，同分又要按用时升序；每人只取最好一局。

**设计**：ZSET score = `totalScore * 100000 + (100000 - durationSeconds)`，member = `runId`。读取时取前 N×3，按玩家去重取第一局。MySQL 里用窗口函数 `ROW_NUMBER() OVER (PARTITION BY player_id ...)` 算每人最好一局，作为兜底和重建来源。

**关键细节**：写 Redis 必须在**事务提交后**（`TransactionSynchronization#afterCommit`），否则回滚了 Redis 里却有假成绩。

**代码**：`LeaderboardService`；测试 `LeaderboardIntegrationTest`

### 5. 缓存：三种经典问题都有对策

| 问题 | 对策 |
| --- | --- |
| 穿透（查不存在的数据） | 缓存 `__NULL__` 空标记，1 分钟 |
| 击穿（热点 key 同时过期） | Redisson `RLock` 只放一个请求回源，其余等 200ms 后自己回源 |
| 雪崩（大批 key 同时过期） | TTL 加随机抖动：详情 30 分钟 + 0–300 秒，列表 5 分钟 + 0–60 秒 |

**原则**：Redis 只是加速层，任何异常都回源 MySQL，不把缓存故障变成接口故障。

**实测**：同一接口开/关缓存 11,507 vs 3,027 req/s，P95 7 vs 43 ms。

**代码**：`LevelCache`

### 6. Redis 的另外两种用法

**登录限流**：IP 层拦截器 10 次/分钟（429 + 统一 JSON），账号层 `AuthService` 再限 10 次/分钟，防止换 IP 撞库。Redis 故障 fail-open 放行。

**每日打卡**：`libhunt:checkin:{playerId}:{year}` 一个 BitMap，bit offset = `dayOfYear - 1`。`SETBIT` 打卡、`BITCOUNT` 累计、逐位回溯算连续天数。全年 365 天只占约 46 字节。

**代码**：`LoginRateLimitInterceptor`、`RateLimiterService`、`CheckinService`

---

## 五、一次通关的全链路（照这个讲）

1. **登录**：`POST /api/auth/login` → 查 `player` → BCrypt 校验 → 检查 `status` → 签发 JWT（subject=playerId，含 role）→ 前端存 localStorage
2. **开局**：`POST /api/runs` → 拦截器验 JWT → 锁 `player` 行 → 查幂等键 → 有进行中的局就续玩，否则建 `game_run`
3. **看题**：`GET /api/levels/{n}?locale=` → 走缓存（未命中则重建）→ 返回题目/选项/道具，无答案
4. **答题**：`POST /api/runs/{id}/levels/{n}/answer` → 锁 `game_run` 行 → 校验当前关卡 → 比对选项集合 → 写 `level_attempt`（attempts++）→ 若答题与扫码都通过则结算
5. **扫码**：`POST .../qr` → 同样锁行 → 比对二维码内容（去空白、忽略大小写）→ 更新 `qr_passed` → 可能触发结算
6. **结算**：`settled` 置 1 → 加总分 → 发道具（`player_item` 唯一索引去重）→ 推进 `last_level_no` 或标记通关并算用时 → **事务提交后**写 Redis 排行榜
7. **恢复**：刷新或换设备 → `GET /api/runs/current` → 服务端返回进行中的局；没有则返回最近一局
8. **排行榜**：`GET /api/leaderboard` → ZSET 取前 N → 批量补昵称 → 每人取最好一局；异常则 MySQL 窗口函数兜底

---

## 六、现场演示脚本（5 分钟）

```bash
docker compose up -d                      # MySQL 3307 + Redis 6380
cd server && ./mvnw spring-boot:run       # 后端 8080
npm run dev                               # 前端 3000
```

演示顺序：

1. 登录（管理员 `admin` / `Admin@123456`，或已创建的学生账号）
2. 打开地图：进度条、打卡卡片（Redis BitMap）
3. 进第 1 关：展示题目，**打开 DevTools 看 `/api/levels/1` 响应——没有答案字段**
4. 故意答错一次：分数从 100 变 80；再答对：提示去扫码
5. 用 Swagger（`http://localhost:8080/swagger-ui.html`）提交正确二维码内容，展示结算和下一关解锁
6. 排行榜：展示名次与用时
7. 打开 `perf/RESULTS.md` 讲压测；打开 `server/src/test` 讲测试
8. 如果被要求看代码：`GameRunService#maybeSettle`、`LevelCache#getOrLoad`、`IntegrationTestBase`

---

## 七、面试题速查

| 问题 | 一句话答案 | 详见 |
| --- | --- | --- |
| 怎么防止前端作弊？ | 答案不下发，判题在服务端，前端只能提交选择 | INTERVIEW 1 |
| 重复提交怎么处理？ | 幂等键 + 唯一索引 + 已通过直接返回 + settled 标记 | INTERVIEW 2 |
| 并发结算会不会加两次分？ | 先锁 `game_run` 行，再用 `settled` 标记，只有第一次生效 | INTERVIEW 3 |
| 为什么不用分布式锁？ | 锁粒度是一局，行锁够用；跨请求的缓存重建才用 Redisson | INTERVIEW 3 |
| 排行榜怎么排序？ | ZSET 复合 score 编码分数与用时；MySQL 窗口函数兜底 | INTERVIEW 4 |
| 事务回滚了 Redis 有假数据怎么办？ | 只在 afterCommit 写 Redis；写失败只记日志 | INTERVIEW 4 |
| 缓存穿透/击穿/雪崩怎么处理？ | 空值标记 / 分布式锁 / 随机 TTL | INTERVIEW 5 |
| Redis 挂了接口还能用吗？ | 能，全部回源 MySQL；但延迟会劣化，缺熔断 | INTERVIEW 5、9 |
| 限流怎么做的？ | IP + 账号两层令牌桶，超限 HTTP 429，Redis 故障放行 | INTERVIEW 6 |
| 打卡为什么用 BitMap？ | 布尔值密集存储，一天一位，一年约 46 字节 | INTERVIEW 7 |
| 怎么保证测试可信？ | Testcontainers 真实 MySQL/Redis；并发测试不加 @Transactional | INTERVIEW 8 |
| 压测数据怎么来的？ | JMeter 120 线程 + ab 对照；JVM 预热后测 | INTERVIEW 9 |
| 为什么浏览器只调 Next.js？ | 同源、无跨域、鉴权与错误格式只处理一层 | INTERVIEW 10 |
| 项目里为什么用 Docker？ | 只用来跑 MySQL/Redis 和测试容器；后端是 `java -jar`，版本固定且不污染本机 | README 快速开始 |
| 这个项目最难的地方？ | 并发下"只结算一次"：四层机制各管一段，缺一个就会在重试或压测时暴露 | INTERVIEW 通用 |
| 有什么没做好？ | 熔断、JWT 踢下线、二维码 HMAC、单机压测 | README 已知限制 |

---

## 八、被追问底层时怎么答

不要背八股，用"是什么 → 为什么用它 → 在这个项目里怎么用"三段式。

| 概念 | 你要能说的三句话 |
| --- | --- |
| JWT | 三段式：header.payload.signature，服务端用密钥验签；无状态所以不查库；代价是签发后无法立即吊销 |
| BCrypt | 加盐的慢哈希，每次结果不同；比对用 `matches`，不是解密；故意慢，抵抗离线爆破 |
| InnoDB 行锁 | `FOR UPDATE` 锁的是索引记录；命中索引才锁行，否则可能锁更多；所以 `run_id`/`player_id` 都有索引 |
| 唯一索引 | 并发插入的最终裁判；应用层"先查再插"有 TOCTOU 窗口 |
| ZSET | 有序集合，底层跳表 + 哈希；按 score 排序 O(logN)，取前 N 是 O(logN+N) |
| BitMap | Redis 的 String 按位操作；`SETBIT`/`GETBIT`/`BITCOUNT`；适合布尔密集数据 |
| 令牌桶 | 以固定速率补充令牌，请求取令牌；允许突发，长期平均受限 |
| Redisson 看门狗 | 业务未结束自动续期锁，防止业务超时锁先释放；本项目只用于缓存重建 |
| Spring 拦截器 | `preHandle` 鉴权、`afterCompletion` 清理 ThreadLocal；比 Filter 更接近业务 |
| 事务提交后回调 | `TransactionSynchronization#afterCommit`，避免"事务回滚了副作用却执行了" |

---

## 九、需要背下来的十个数字

| 数字 | 含义 |
| --- | --- |
| **12** | 关卡数，满分 1200 |
| **100 / 20 / 20** | 首答 100 分，每错扣 20，最低 20 |
| **49** | 后端自动化用例数 |
| **10** | 并发测试线程数（同一关同时答题） |
| **62,000 / 0 / 8,712** | 压测请求数 / 错误数 / 整体吞吐 |
| **11,507 vs 3,027** | 关卡详情开/关缓存（req/s），约 3.8 倍 |
| **7 vs 43 ms** | 对应的 P95 |
| **0** | 压测期间 Redis `keyspace_misses` 增量（5 万次详情零回源） |
| **10 次/分钟** | 登录限流阈值（IP 与账号各一层） |
| **46 字节** | 一年打卡数据的大小 |

---

## 十、被问住时怎么办

**标准三步**：

1. 先承认边界："这块我没做/没测过"
2. 说出现在怎么处理的："目前是……"
3. 给出如果要做会怎么设计："如果要做，我会……"

例子：

> 问：Redis 挂了，你的接口会不会雪崩？
> 答：不会报错，会回源 MySQL。但压测发现每个请求都要等 Redis 连接失败，延迟会到秒级，所以这里确实缺熔断——我原本以为 fail-open 就够，压测数据打了脸。如果补，我会在缓存层加一个本地失败计数，连续失败后短路一段时间，或者直接上 Resilience4j。

**不要说**："这个 AI 写的""应该是这样吧""我回去看看"。

---

## 十一、自测清单

面试前逐项打勾，任何一项讲不顺就回去看对应章节：

- [ ] 能用 30 秒说清项目是做什么的、解决了什么问题
- [ ] 能画出架构图，并解释浏览器为什么不直连后端
- [ ] 能说清"一局游戏"的完整生命周期（登录到排行榜）
- [ ] 能说出幂等的四层机制，以及每一层防的是什么
- [ ] 能解释为什么用行锁而不是 synchronized / Redis 锁
- [ ] 能推导排行榜的复合 score，并说清 why afterCommit
- [ ] 能分别说出穿透、击穿、雪崩的对策
- [ ] 能解释 BitMap 的 bit offset 怎么算
- [ ] 能讲清 49 个测试怎么隔离、并发测试为什么不能加事务
- [ ] 能解释压测数字的对照方法（开/关缓存，而不是停 Redis）
- [ ] 能主动说出三个已知限制和对应的改进方案
- [ ] 十个数字能脱口而出

---

## 十二、继续提升的方向（面试加分）

按"性价比"排序：

1. **熔断**——补上压测暴露的短板，能讲成"发现问题并解决"
2. **JWT 黑名单**——讲清"无状态 token 的吊销"这个经典问题
3. **二维码 HMAC**——安全设计，成本半小时
4. 分布式锁的正确使用场景——现在只用了一处，可以讲清"什么时候不该用"
5. 多机压测——有环境再说，没有就诚实说单机

---

*文档里的所有数字都可以复现：测试 `cd server && ./mvnw test`，压测见 [perf/README.md](../perf/README.md)。讲不清的地方，以代码为准。*
