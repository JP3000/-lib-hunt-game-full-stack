# 面试速讲稿

每个技术点都按「30 秒讲清 → 可展开的细节 → 可能的追问」整理。代码位置用来现场打开，不需要背。

---

## 1. 为什么把判题搬到服务端

**30 秒**

旧版答案打包在 JS 里，分数由浏览器算完再上报，改一行代码就能满分。现在前端只提交「我选了哪几个 key」「我扫到了什么」，判题、计分、结算、进度推进全部在服务端；关卡接口返回的 VO 里根本没有正确答案和二维码内容。

**追问：前端连答案都拿不到，怎么知道答没答对？**

服务端返回 `correct`、本题得分和下一动作（重试 / 扫码 / 下一关）；答错时前端不提示，只允许继续尝试。正确答案永远不会出现在任何响应里，`ApiSecurityIntegrationTest` 会断言 `/api/levels/{n}` 的响应不含 `correctOptionKeys`、`qrAnswer`、`passwordHash`。

**代码**：`server/.../service/GameRunService.java`、`dto/LevelDetailVO.java`、`ApiSecurityIntegrationTest`

---

## 2. 幂等：三个阶段各自怎么保证

**30 秒**

- **开局**：客户端带 `Idempotency-Key`，`game_run.idempotency_key` 唯一索引兜底；同一个 key 重试返回同一局，换玩家用同一个 key 会被拒绝（防抢占）
- **答题 / 扫码**：先看 `question_passed` / `qr_passed`，已经通过的直接返回原结果，不重复计分
- **结算**：`level_attempt.settled` 标记，谁最后完成都只有第一次会加分

**追问：为什么不能只靠「先查再插」？**

两个请求同时查，都查到"不存在"，然后都插入。所以唯一索引是必须的：`UNIQUE (run_id, level_no)`、`UNIQUE (idempotency_key)`。应用层捕获 `DuplicateKeyException`，把并发写冲突变成可控的业务分支。

**追问：答题接口重试会扣两次分吗？**

不会。本题通过后再提交，走"已通过直接返回"分支；答错重试只加 attempts，这是规则。

**追问：多选题怎么计分？为什么不是全对 20、其它 0？**

`GameRules` 把每关 20 分按正确选项平分：只选对一部分得部分分，选到任何错误选项本题 0 分（但不倒扣）。本题一旦通过就锁定分数，重试不能把少选的分补回来——否则大家都能刷到满分。前端答错时不显示任何提示，只允许继续尝试。

**代码**：`GameRunService#start/submitAnswer/submitQr`、`GameRules`、`db/migration/V2`、`V4`

---

## 3. 并发：行锁 + 唯一索引，为什么不用 synchronized

**30 秒**

同一局的答题和扫码请求会先 `SELECT ... FOR UPDATE` 锁住 `game_run` 行，把同一局的请求串行化——两次操作抢的都是同一关的结算，必须串行。不同玩家锁的是不同行，互不影响。

不用 `synchronized` 是因为：JVM 锁只在单实例内有效，部署两个实例就失效；而且锁的粒度是整个方法，会把所有玩家串在一起。

**追问：为什么不用 Redis 分布式锁？**

行锁已经够用：锁的粒度是"一局"，热点只可能出现在同一局（一个玩家不会自己并发抢同一局，但网络重试会）。Redis 锁多一次网络往返，还要处理锁超时、续期、误删，收益不如数据库行锁直接。本项目不需要分布式锁；Redisson 只用于跨实例共享的登录限流令牌桶。

**追问：10 个请求同时第一次答题会怎样？**

测试里验证过：只落一条 `level_attempt`，`attempts = 10`。行锁让它们排队，第一个插入，其余转成更新。

**代码**：`GameRunService#lockRun`、`GameRunConcurrencyIntegrationTest`

---

## 4. 排行榜：复合排序、每人最好一局、Redis 兜底

**30 秒**

Redis ZSET 的 score 编码两个维度：`totalScore * 100000 + (100000 - durationSeconds)`，分数高的在前，同分用时短的在前。member 用 `runId`，ZSET 里保留每一局，读取时按玩家去重、取排在最前面的那一局。通关时在**事务提交后**写 Redis，避免回滚留下假成绩；Redis 挂了读 MySQL，还能一键全量重建。

**追问：为什么用乘法而不是两个 ZSET？**

两个 ZSET 要两次查询再求交，还要处理"分数相同"的边界。编码成一个 double 后，`ZREVRANGE` 一次就出结果。满分 240，乘 10 万是 2400 万，远在 double 精确整数范围（2^53）内。

**追问：afterCommit 怎么做的？**

`TransactionSynchronizationManager.registerSynchronization(...)`，在 `afterCommit` 里更新 ZSET，写失败只记日志——排行榜是展示层，不能反过来把通关请求拖失败。

**追问：为什么 member 是 runId 不是 playerId？**

同一个玩家可能有多局，用 runId 能保留每一局，读取时按玩家去重取排在最前面的那一局。用 playerId 覆盖写入会在"新的一局更差"时把好成绩冲掉。

**追问：这个设计有什么代价？**

Redis 只负责排序，取前 N 个 runId 后还要 `selectBatchIds` 从 MySQL 补昵称和分数——压测里 9,000 次榜单读取产生了 1.8 万次 SELECT。要彻底把读路径留在 Redis，可以把昵称和分数编码进 member 或缓存整张榜；当前榜单只有 20 条，批量查询在毫秒级，不值得为此引入一致性负担。

**代码**：`LeaderboardService`、`LeaderboardIntegrationTest`

---

## 5. 二维码签名与 Redis 降级

**30 秒**

现场二维码不是固定密码，而是服务端签发的 `v1.<levelNo>.<expire>.<hmac>`：

- 签名密钥 = 服务端私钥 + 该关数据库里的 `qr_answer`，两者都不下发给客户端
- 过期时间写进内容，二维码被拍照转发，过了有效期也失效
- 校验时检查关卡号、过期时间，并用常量时间比较 HMAC
- 签发接口 `GET /api/admin/levels/{n}/qr` 只给管理员用，默认有效期 7 天

**追问：为什么不把二维码内容存 Redis 或 token 表？**

二维码是现场凭证，不是业务数据。签名方案不需要额外存储和清理，校验是纯计算；要吊销就换密钥或等过期。少一张表、少一个一致性负担。

**追问：Redis 挂了接口还能用吗？**

能。排行榜回落 MySQL 窗口函数，登录限流 fail-open，接口不报错。但每个请求要等 Redis 连接失败，延迟会劣化到秒级——目前没有熔断，这是已知短板。

**代码**：`service/QrTokenService.java`、`QrTokenServiceTest`、`LeaderboardService`

---

## 6. 限流：IP + 账号两层

**30 秒**

登录接口两层限流：拦截器按 IP 限 10 次/分钟，超限返回 HTTP 429 + 统一 JSON；`AuthService` 再按学号限 10 次/分钟，防止攻击者换 IP 撞同一个账号。用 Redisson `RRateLimiter` 令牌桶，Redis 故障时 fail-open（放行）。

**追问：为什么 IP 取 `X-Forwarded-For` 第一段？**

生产上前面有 Nginx，`getRemoteAddr()` 拿到的是 Nginx 的地址，所有人共用一个桶。取 XFF 第一段（客户端地址）。要注意：如果 Nginx 没有正确覆盖这个头，客户端可以伪造——所以部署时必须让 Nginx 设置而不是透传。

**追问：为什么 429 不是业务错误码？**

HTTP 状态码表达"请求太频繁"是标准语义，网关、监控、前端重试策略都能识别；业务错误码留给"账号密码错误"这类业务判断。响应体里仍然有统一的 `{code:429,msg}`，前端只写一套解析。

**代码**：`interceptor/LoginRateLimitInterceptor.java`、`service/RateLimiterService.java`、`LoginRateLimitIntegrationTest`

---

## 7. 测试：Testcontainers 与为什么不用 H2

**30 秒**

54 个用例，其中集成测试用 Testcontainers 起真实的 `mysql:8.4` 和 `redis:7`，容器在静态块里共享，整套约 20 秒。并发用例用 `CountDownLatch` 做发令枪，10 个线程同时提交。

**追问：为什么不用 H2？**

判题依赖 `SELECT ... FOR UPDATE` 的锁语义，排行榜依赖窗口函数，迁移脚本用 JSON 列——这些 H2 要么不支持、要么行为和 MySQL 不一致。用 H2 测出来的"通过"没有意义，尤其测不出并发。

**追问：并发测试为什么不能加 `@Transactional`？**

Spring 测试的 `@Transactional` 会在测试结束时回滚，但更关键的是：事务里的数据对其他线程不可见，其他线程根本看不到这一局，锁和唯一索引都测不到。所以并发用例必须让数据真正提交，跑完再清理。

**代码**：`src/test/java/com/libhunt/support/IntegrationTestBase.java`、`GameRunConcurrencyIntegrationTest`

---

## 8. 压测：怎么测、测出了什么

**30 秒**

JMeter 120 线程混合场景（关卡 / 排行榜 / 判题），62,000 次请求、0 错误、整体 8,712 req/s（当时还带关卡缓存）；移除缓存后，关卡详情单接口 3,027 req/s、P95 43 ms。

**追问：为什么数据可信？**

- 必须 JVM 预热后再测。第一次冷启动测出 61 req/s，差点以为代码写坏了，其实是 JIT 还没热
- 单接口对照走的是同一条 MySQL 路径，不是"停掉 Redis"；后者测到的是连接失败重试的耗时
- 同时看 MySQL `Com_select`，确认请求真的打到了数据库

**追问：压测发现了什么？**

两个真问题：

1. Redisson 默认在创建时连接 Redis，Redis 没起来应用直接启动失败——已改成懒连接
2. Redis 完全不可达时接口仍 200，但每个请求等连接失败，延迟劣化到秒级——缺熔断，记为待办

**代码 / 数据**：[perf/](../perf/)、[perf/RESULTS.md](../perf/RESULTS.md)

---

## 9. 全栈架构：为什么浏览器只访问 Next.js

**30 秒**

浏览器只请求同源 `/api/*`，Next.js 用 rewrite 转发到 Spring Boot（生产是 Nginx 反代）。这样没有跨域、前端没有硬编码的后端地址，`Authorization` 头和统一错误格式也只在一层处理。后端不依赖任何前端框架，换客户端（小程序、App）照样用。

**追问：为什么不让前端直接调后端？**

需要配置 CORS、把 API 地址写进构建产物、处理两套错误格式；而且浏览器直连后端意味着后端必须暴露在公网，攻击面更大。同源代理是这类"一个前端 + 一个后端"项目的标准做法。

**追问：这算"全栈"吗？**

算。全栈指的是前端、后端、数据存储各司其职，业务真相在服务端，而不是"有没有线上地址"。这是作品集项目，刻意不部署：本地 docker compose + 两个进程就是完整系统，面试时现场跑。

**追问：为什么不部署？**

部署不产生新的技术叙事，只是重复 Nginx / 进程托管。简历上被追问的是判题、并发、幂等、Redis 故障，这些在本地都有测试和压测数据可以直接复现。真要上线，架构本身就是为反代设计的（同源 `/api`），加一层 Nginx 即可。

**代码**：`next.config.ts`、`src/lib/api.ts`

---

## 通用问题

### 这个项目最难的地方

不是某个接口，是**把"并发下仍然正确"写清楚**：幂等键、唯一索引、行锁、`settled` 标记各管一段，少一层就会在压测或重试时暴露。测试用例就是设计文档。

### 最大的收获

先定"谁是真相"，再写代码。改造前每个页面都在自己维护进度，所谓"修 bug"其实是在追三份不一致的状态。

### 还没做好的地方

- Redis 故障没有熔断，延迟会劣化
- JWT 无状态，禁用账号不能立即踢下线（要加 Redis 黑名单）
- 二维码明文比对，没有 HMAC 防伪

这些都在 [README「已知限制」](../README.md#已知限制) 里，面试时主动说出来比被问出来好。
