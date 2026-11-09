# 数据模型

数据库：MySQL 8.4，库名 `libhunt`，字符集统一 `utf8mb4`。
建表语句在 [server/src/main/resources/db/migration/](../server/src/main/resources/db/migration/)，由 Flyway 按版本号顺序执行。

## 关系概览

```
player 1 ──< game_run 1 ──< level_attempt
  │              │
  │              └──< player_item >── item
  │
  └──< player_item

level 1 ──< level_option
level 1 ──< level_item >── item
```

没有物理外键。引用完整性由应用层校验 + 索引保证，原因见文末「为什么不建外键」。

## 内容表（发版时写入，运行期只读）

### `level` — 关卡主数据

| 列 | 说明 |
| --- | --- |
| `level_no` | 关卡序号 1..12，唯一索引；前端路由用这个值 |
| `title` / `story` / `question` / `completion_story` | JSON 列，键为 locale（`zh-Hant` / `zh-Hans` / `en`） |
| `correct_option_keys` | 正确答案 key 数组，如 `["b"]`；**只给服务端判题** |
| `qr_answer` | 二维码明文；**只给服务端校验** |
| `qr_location` / `qr_location_note` | 藏匿地点提示，可以下发（不是答案） |
| `status` | 1 启用 / 0 停用 |

对外 VO（`LevelDetailVO`）不包含 `correct_option_keys`、`qr_answer` 两列，由 `ApiSecurityIntegrationTest` 断言。

### `level_option` — 选项

`UNIQUE (level_id, option_key)`；`option_key` 是 `a/b/c/d`，判题时与 `correct_option_keys` 比对。
文案 `label` 同样是 JSON 列。

### `item` / `level_item` — 道具与关卡奖励

`item_key` 是稳定的业务键（如 `item-01-note`），`level_item` 用复合主键 `(level_id, item_id)`，`sort_order` 控制展示顺序。

## 业务表（运行期频繁读写）

### `player` — 账号

| 列 | 说明 |
| --- | --- |
| `student_no` | 学号 / 工号 / 管理员账号，唯一索引 |
| `password_hash` | BCrypt（`spring-security-crypto`），绝不存明文 |
| `role` | `STUDENT` / `STAFF` / `ADMIN` |
| `status` | 1 正常 / 0 禁用；登录时校验（已签发的 JWT 在过期前不会立即失效） |

`UNIQUE (student_no)` 是并发创建账号的兜底：两个管理员同时建同一个学号，第二个请求会拿到 `DuplicateKeyException`，而不是"先查再插"漏过去（TOCTOU）。

### `game_run` — 一局游戏

| 列 | 说明 |
| --- | --- |
| `status` | `IN_PROGRESS` / `COMPLETED` / `ABANDONED` |
| `started_at` / `finished_at` | 服务端时间，客户端无法篡改 |
| `total_score` | 结算时累加 |
| `duration_seconds` | 服务端用 `finished_at - started_at` 计算，用于同分排序 |
| `last_level_no` | **进度的唯一权威**：提交非当前关卡直接拒绝 |
| `idempotency_key` | 客户端开局时带的幂等键，唯一索引 |

索引：

- `idx_run_player (player_id, status)`：查"当前进行中的一局"
- `idx_run_leaderboard (status, total_score DESC, duration_seconds ASC)`：排行榜主查询直接走索引，避免 filesort（MySQL 8 支持降序索引）

### `level_attempt` — 过关记录

| 列 | 说明 |
| --- | --- |
| `run_id` + `level_no` | `UNIQUE`，一局一关只允许一条记录 |
| `attempts` | 答题次数，用于分析题目难度（答错不扣分，也不提示） |
| `question_passed` / `qr_passed` | 两道验证各自独立，顺序不限 |
| `settled` | 本关是否已经结算过 |

`settled` 是 V4 加的：答题和扫码谁最后完成都行，没有这个标记就无法区分"第一次结算"和"重复提交"，会重复加分。

`UNIQUE (run_id, level_no)` 同时是并发幂等的核心：10 个并发请求同时第一次答题，只有一条能插入成功，其余在应用层捕获后转为更新。

### `player_item` — 道具收藏

`UNIQUE (player_id, item_id)`，重复获得不会产生重复行；`run_id` 记录在哪一局拿到。

## Redis 里的数据

Redis 不是真相来源，全部可以从 MySQL 重建（排行榜有 `POST /api/admin/leaderboard/rebuild`）。

| key | 类型 | 用途 | 失效策略 |
| --- | --- | --- | --- |
| `libhunt:leaderboard:global` | ZSET | 排行榜，member = `runId` | 只保留前 1000 名 |
| `libhunt:ratelimit:login:ip:{ip}` | RRateLimiter | 登录限流 | 1 分钟 10 次 |
| `libhunt:ratelimit:login:account:{studentNo}` | RRateLimiter | 账号级限流 | 1 分钟 10 次 |

排行榜的 ZSET score 用 `totalScore * 100000 + (100000 - durationSeconds)` 编码复合排序：分数高的在前，同分时用时短的在前。满分 240，乘 10 万仍在 double 的精确整数范围内。

## 设计决策

### 为什么多语言用 JSON 列

语言固定三种、以读为主、没有"按语言过滤"的查询。拆成 `level_translation(locale, ...)` 子表会让每次读取多一次 join，收益只是"多一种语言时不用改表"——而多语言名单本身就是产品决策，不是运行时数据。JSON 列在这个场景下更简单，12 关数据量小，直接读 MySQL 已经足够快。

### 二维码为什么用签名而不是存 token 表

二维码内容 = 关卡号 + 过期时间 + HMAC 签名，服务端不需要存一张 token 表；校验是纯计算，过期和伪造都能挡住。签名密钥由服务端私钥与该关的 `qr_answer` 组合而成，两者都不出现在任何对外 DTO 里。管理员通过 `GET /api/admin/levels/{n}/qr` 签发后打印张贴。

### 为什么不建物理外键

- 批量导入、数据修复、分库分表时外键会变成障碍（要先删子表、要按顺序迁移）
- 外键检查会加锁，高并发写入下有额外开销
- 引用完整性由应用层保证：写入前校验，查询用索引，删除走软删除或先清子表（压测脚本就是这么清理测试数据的）

这是多数互联网团队的默认做法，不是偷懒。

### 为什么不存"最终成绩"单独的表

一局就是一条成绩。`game_run` 同时承担"进行中的状态"和"完成的成绩"，少一张表、少一次同步。排行榜只需要 `status = 'COMPLETED'` 的行走索引。

### 时间为什么都由数据库/服务端给

客户端时间可以被改。`started_at` 在开局时由服务端写入，`finished_at` 在最后一关结算时写入，`duration_seconds` 由服务端计算——改前端拿不到更短的用时。

## 内容更新

内容源文件是 TS，改完执行：

```bash
node --experimental-strip-types scripts/export-levels.mjs
```

会重新生成 `V3__seed_levels.sql`。已有库不会重跑 V3，所以**改内容需要新增一条迁移**（如 V5）或手动执行 SQL——当前刻意不做关卡 CMS，见 [PLAN.md](../PLAN.md)「明确不做」。
