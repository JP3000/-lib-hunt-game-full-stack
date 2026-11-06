# 全栈改造计划

> 目标：把「前端项目 + 一个上报接口」改造成**服务端持有业务真相**的 Java 全栈系统。

## 五条铁律

1. UI 不重写，只改数据源
2. 服务端是唯一真相
3. 按垂直切片开发，不按层开发
4. 每个阶段有可验收的产物
5. 不做的技术，理由要能讲出来

## 当前状态（2026-11-06）

**核心闭环已跑通**：浏览器 → Next.js → Spring Boot → MySQL/Redis，登录、开局、判题、扫码、结算、进度恢复、排行榜、管理端统计全部由服务端完成。

| 阶段 | 状态 |
| --- | --- |
| 1 基建 | ✅ |
| 2 数据建模 | ✅ |
| 3 身份与安全 | ✅ |
| 4 游戏核心闭环 | ✅ |
| 5 前端对接 | ✅ |
| 6 部署上线 | ⬜ 可选，见阶段说明 |
| 7 排行榜与统计 | 🚧 Redis 已完成，POI 导出未做 |
| 8 加固与压测 | ⬜ |
| 9 文档与面试预演 | 🚧 README 已重写，面试稿未写 |

> **关于部署**：部署不是"全栈项目"的必要条件——全栈指的是前端、后端、数据存储各司其职，且业务真相在服务端。部署只是让简历上的链接点得开、顺便练习 Nginx / 进程管理，优先级低于阶段 8。

## 进度

### 阶段 1 · 基建 ✅

- [x] `git init` + 基线提交（分支 `feat/fullstack-migration`）
- [x] `docker-compose.yml`（MySQL 8 + Redis 7，Redis 映射 6380）
- [x] Spring Boot 3.5.3 + Java 21 + Maven
- [x] 地基类：`Result` / `BusinessException` / `GlobalExceptionHandler` / `UserContext`
- [x] JWT：`JwtUtil` + `JwtInterceptor` + `WebMvcConfig`
- [x] MyBatis-Plus 分页插件、springdoc 接口文档
- [x] Flyway `V1__init_player.sql`
- [x] `.env.example`、`server/README.md`
- [x] 编译通过
- [x] 启动成功，`/api/health` 返回 UP
- [x] Flyway 自动建出 `player` 表

**验收记录**：`{"code":0,"msg":"ok","data":{"status":"UP"}}`；Flyway 应用 V1，建出 `player` + `flyway_schema_history`。

**踩坑记录**
- MySQL 宿主机 3306 被占用 → 改用 3307
- 改 `application.yml` 后必须重新 `package`，否则启动的是旧 jar 里的配置
- MyBatis-Plus 3.5.7 需覆盖 mybatis-spring 到 3.0.4
- Knife4j 4.5.0 与 Boot 3.5 不兼容 → 改用 springdoc 2.8.6

### 阶段 2 · 数据建模 ✅

- [x] ER 图（见下方「数据模型」）
- [x] `V2__init_game_schema.sql`：7 张表 + 索引 + 唯一约束
- [x] 12 关内容导入（`V3__seed_levels.sql`，由 `scripts/export-levels.mjs` 从 `src/data/levels.ts` 生成）
- [x] 索引与唯一约束评审
- [x] entity / mapper / service / controller：`GET /api/levels`、`GET /api/levels/{levelNo}`
- [x] 验收：12 关全部可读，响应中不含 `qrAnswer` / `correctOptionKeys`

**验收记录**：`level=12 / level_option=54 / item=13 / level_item=13`；三语言 JSON 均 `JSON_VALID=1`；
`/api/levels?locale=zh-Hans` 返回 12 条；`/api/levels/1` 返回完整剧情/选项/道具，抓包无答案字段。

**数据模型**

```
player ──< game_run ──< level_attempt
  │            │
  │            └── level  ──< level_option
  │                          └──< level_item >── item
  └──< player_item
```

- `level` / `level_option` / `item` 的多语言文案存 JSON 列（`{"zh-Hant":…,"zh-Hans":…,"en":…}`）
- `game_run.idempotency_key` 唯一 → 完成上报幂等
- `level_attempt(run_id, level_no)` 唯一 → 同一局同一关只落一次分
- `(status, total_score DESC, duration_seconds ASC)` 复合索引 → 排行榜
- 不建物理外键：由服务端保证一致性，便于后续分库/归档（面试要能解释这个取舍）

**踩坑记录**
- 原生 SQL 里取 JSON 的连字符键必须写 `$."zh-Hans"`，写成 `$.zh-Hans` 报 3143
- `keys` 是 MySQL 保留字，不能直接当别名

### 阶段 3 · 身份与安全 ✅

- [x] 账号体系：启动时自动建管理员 + 管理端创建 / 禁用 / 重置密码（Excel 批量导入暂不做，见「明确不做」）
- [x] BCrypt（`spring-security-crypto`，不引入整套 Spring Security）
- [x] `POST /api/auth/login` + `GET /api/auth/me`
- [x] RBAC：`@RequireRole` + `RoleInterceptor`，JWT 认证之后做角色校验

**验收记录**
- 正确密码登录返回 JWT，`/api/auth/me` 可还原本人信息
- 错误密码与不存在的账号都返回「账号或密码错误」，且都跑一次 BCrypt（防计时枚举）
- 无 token / 坏 token → HTTP 401 + `{"code":401,...}`，不是空响应体
- STUDENT 访问管理端 → HTTP 403 + `{"code":403,"msg":"无权限访问"}`
- 重复学号由唯一索引拦截，返回「账号已存在」

**踩坑记录**
- 角色写在 JWT 里，改角色后需重新登录才生效；若要即时生效得每次查库或加版本号
- `docker exec mysql` 默认字符集不是 utf8mb4，中文会写成乱码；查询/写入都要带 `--default-character-set=utf8mb4`

### 阶段 4 · 游戏核心闭环 ✅

- [x] 开局 / 下发题目 / 服务端判分 / 二维码校验 / 通关结算
- [x] 越权校验、幂等落库、事务
- [x] 验收：抓包无答案、并发不重复

**接口**
| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/runs` | 开局；有进行中的局就续玩；支持 `Idempotency-Key` |
| GET | `/api/runs/current` | 刷新后恢复进度；优先进行中的局，没有则返回最近一局（用于结算页） |
| POST | `/api/runs/{runId}/abandon` | 「重新开始」：旧局标记 ABANDONED，成绩留在历史里 |
| POST | `/api/runs/{runId}/levels/{levelNo}/answer` | 服务端判题，返回对错 + 得分 + 是否可去扫码 |
| POST | `/api/runs/{runId}/levels/{levelNo}/qr` | 校验现场二维码；通过后结算本关 |

**服务端权威的几条规则**
- 答案（`correct_option_keys` / `qr_answer`）只存在于数据库，任何响应都不下发
- 「答题」与「现场扫码」是两道**相互独立**的验证，先做哪个都行（现场流程是先扫码后答题）
- 两道都通过的那一次请求才触发结算；`qrLocation` 只是地点提示，不是答案，随关卡详情下发
- 关卡顺序由 `game_run.last_level_no` 决定，跳关直接拒绝
- 计分在服务端：每关 100，答错一次 −20，最低 20（`GameRules`）
- 用时 = `finished_at - started_at`，客户端传什么都不作数

**并发与幂等**
- 答题/扫码先对 `game_run` 行加 `FOR UPDATE` 锁，同一局的请求串行执行
- `uk_attempt_run_level (run_id, level_no)` 唯一索引兜底，网络重试不会重复加分
- 结算由 `level_attempt.settled` 标记，保证「计分 + 发道具 + 推进关卡」只执行一次
- 重复提交已答对/已扫码的关卡 → 原样返回，不重复计分、不重复发道具
- 同一玩家并发开局 → 锁 `player` 行，只会产生一局

**验收记录**
- 先扫码后答题：扫码返回 `questionPassed=false`，答题后结算 `totalScore=100`、`nextLevelNo=2`
- 先答题后扫码：答题返回 `nextAction=SCAN_QR`、`settlement=null`，扫码后结算
- 结算后重复扫码：返回 `settlement=null`，分数不变
- 错 1 次再答对：第 1 关得 80 分；重复提交仍为 80
- 第 2 关并发提交 10 次错误答案 → `level_attempt` 只有 1 行、`attempts=10`
- 另一账号操作该对局 → 「无权操作该对局」
- 12 关全通：`totalScore=1200`、`durationSeconds` 有值、`status=COMPLETED`；
  `current` 返回这一局（刷新结算页用），`abandon` 之后才返回 `null`

### 阶段 5 · 前端对接 ✅

- [x] 统一 API client：`src/lib/api.ts`（拆 envelope、`ApiError`、401 自动清 token）
- [x] 同源代理：`next.config.ts` rewrite `/api/:path*` → Spring Boot（浏览器无跨域）
- [x] `types.ts` 与后端 DTO 对齐，答案字段从类型层面删除
- [x] store 降级为缓存：持久化 `player / token`，进度以 `GET /api/runs/current` 为准
- [x] 关卡页：判题、扫码、结算全部走服务端；结算由最后一个完成的动作触发
- [x] 地图 / 结算 / 积分板 / 管理端迁移到服务端数据；管理端按 `role=ADMIN` 拦截
- [x] 删除旧的 `src/app/api/auth`、`src/app/api/results`、`src/lib/db.ts`

**实现要点**
- 关卡详情只返回展示字段 + `multiple`（是否多选），`qrAnswer` / `correctOptionIds` 不出后端
- 关卡页不再本地判分；`answer` / `qr` 响应里的 `settlement` 直接写回 store（服务端已算好）
- 「完成本关并继续」只是导航按钮——分数、道具、解锁在服务端结算时已落库
- 关卡内容走 `/api/levels?locale=`，客户端只做模块级缓存（切语言分别缓存），不做持久化
- `runReady` 标记区分「还没查服务端」和「确实没有进度」，避免刷新时误跳回地图

**验收记录（经 Next 代理 3000 → Spring 8080）**
- 管理端创建账号 → 登录 → 开局 → 先扫码（`qrPassed=true`，未结算）→ 答题（100 分，`nextLevelNo=2`）
- 重复提交答案幂等：`settlement=null`，分数不重复加
- `GET /api/runs/current`：`lastLevelNo=2`、`totalScore=100`、关卡进度与库内一致
- `abandon` → `current` 返回 `null` → 再开局得到新 `runId`，从第 1 关开始
- 学生 token 访问 `/api/admin/stats` → 403；管理员 token → 200
- `next build` 通过，`/zh-Hans/login|map|levels/01|leaderboard|result`、`/admin` 均 200

### 阶段 6 · 部署上线 ⬜（可选）

> **不是"全栈"的必要条件。** 全栈系统在本地已经完整跑通；部署的收益是作品可访问、能讲 Nginx / 进程托管 / 证书，优先级排在阶段 8 之后。

- [ ] Dockerfile（前端 + 后端）
- [ ] Nginx：`/` → Next.js，`/api/` → Spring Boot
- [ ] HTTPS
- [ ] 旧版 tag 可回滚

**为什么暂缓**：简历上真正被追问的是「服务端如何防作弊、并发怎么处理、Redis 挂了怎么办」；这些在阶段 4 / 7 已经有验证记录和可复现的接口。部署属于工程化加分项，不是核心叙事。

### 阶段 7 · 排行榜与统计 🚧

- [x] MySQL 版（窗口函数 `ROW_NUMBER() OVER (PARTITION BY player_id ...)`，每人取最好一局）
- [x] Redis ZSET 版（复合排序）
- [ ] POI 导出

**实现要点**
- `ZSET` member = `runId`，score = `totalScore * 100000 + (100000 - durationSeconds)`：
  分数高的在前，同分时用时短的在前；满分 1200，乘 10 万仍在 double 精确整数范围内
- 通关后**事务提交后**才写 Redis（`TransactionSynchronization#afterCommit`），避免回滚留下假成绩
- 读取失败/Redis 为空 → 自动回落 MySQL，并顺带预热 ZSET；`ZSET` 只保留前 1000 名
- 管理端 `POST /api/admin/leaderboard/rebuild` 可从 MySQL 全量重建

**验收记录**
- 两局都是 1200 分：用时 2s 排第 1，12s 排第 2（复合排序生效）
- 停掉 Redis 容器后请求排行榜 → 仍返回正确结果（MySQL 兜底）
- 重启 Redis（AOF）后数据仍在；重建接口返回 `entries=1/2` 与库内一致

**踩坑记录**
- `ZREMRANGEBYRANK key 0 -(N+1)` 在集合元素不足 N 个时会把**第一名**删掉（负下标被钳制为 0）；
  正确做法是先 `ZCARD` 判断，再删除 rank `0 .. size-N-1`

### 阶段 8 · 加固与压测 ⬜

- [ ] 缓存三兄弟
- [ ] Redisson
- [ ] BitMap 打卡
- [ ] 限流（可选）
- [ ] JMeter 数据

### 阶段 9 · 文档与面试预演 🚧

- [x] README 重写：架构图、接口一览、服务端权威设计、内容维护流程、环境变量
- [x] `.env.example` 与 README 对齐
- [ ] 数据模型 / 表设计单独成篇（面试时可直接讲）
- [ ] 压测数据（依赖阶段 8）
- [ ] 改造前后对比（纯前端 → 全栈）
- [ ] 每个技术点脱稿 3 分钟

## 明确不做

微服务、MQ/Kafka、关卡 CMS、微信登录、WebSocket 大屏、前端重构。

## 关键决策

| 决策 | 推荐 |
| --- | --- |
| 账号体系 | ✅ 启动建管理员 + 管理端创建/禁用/重置密码；Excel 导入延后 |
| 二维码防伪 | ✅ 先明文比对（`normalize` 去空白、转小写），HMAC 留作加分项 |
| 多语言存储 | ✅ JSON 列（`{"zh-Hant":…,"zh-Hans":…,"en":…}`） |
| 排行榜 | ✅ MySQL + Redis 两版（Redis 优先，MySQL 兜底） |
| 关卡管理 | ✅ 只做数据迁移（`scripts/export-levels.mjs`），不做 CMS |
