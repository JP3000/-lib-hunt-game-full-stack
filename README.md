<div align="center">

# 图书馆寻宝游戏 · Library Treasure Hunt

**图书馆实地寻宝互动游戏 · 全栈项目**

玩家用手机浏览器登录后，在馆内实地闯关 12 关 —— 读剧情、答题、扫描现场二维码，在游戏中学会使用图书馆。

[![Next.js](https://img.shields.io/badge/Next.js-16.2-black?logo=next.js)](https://nextjs.org)
[![React](https://img.shields.io/badge/React-19-61DAFB?logo=react&logoColor=white)](https://react.dev)
[![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)](https://www.typescriptlang.org)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)](https://openjdk.org)
[![MySQL](https://img.shields.io/badge/MySQL-8.4-4479A1?logo=mysql&logoColor=white)](https://www.mysql.com)
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis&logoColor=white)](https://redis.io)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[旧版线上体验（纯前端）](https://libhuntgame.com.cn)

</div>

---

## 简介

一款移动端优先的 H5 网页游戏，**无需下载安装**。以明末海上霸主郑芝龙（Nicolas Iquan）的宝藏传说为故事线，将图书馆的资源检索教学嵌入 12 个关卡：馆藏发现系统检索、索书号规则、楼层主题分区、参考咨询服务等。

项目最初只有前端（关卡数据打包在 JS 里、进度存 localStorage、成绩写 JSON 文件），现改造为 **Next.js + Spring Boot 的全栈系统**：

- **内容与答案迁到 MySQL**：正确答案、二维码内容的明文只存在于服务端；玩家请求关卡详情时拿到的是"题目 + 选项"，不含答案
- **判题、计分、结算全部由服务端完成**：前端只能提交"我选了什么""我扫到了什么"，无法改分或跳关
- **进度存服务端**：换设备、清缓存后登录即可继续；一局结束前不能重复开局刷分
- **排行榜放在 Redis**：ZSET 复合排序，Redis 不可用时自动回落 MySQL

## 游戏规则

每关有两道互相独立的验证：

1. **答题**——提交选项，服务端判对错；答错可重来，每次错误扣 20 分
2. **现场扫码**——扫描张贴在馆内的二维码（或手动输入密钥），服务端比对

两关都通过才会结算：加分、发道具、解锁下一关。**扫码保证玩家真的走到了现场**，这是游戏的核心设计。

计分：首答正确 100 分，每错一次减 20 分，最低 20 分；12 关满分 1200 分。同分时用时短的排名靠前。

## 功能特性

- 🗺️ **12 关卡地图** — 藏宝图风格的关卡选择界面，按服务端进度解锁
- 📱 **二维码实地验证** — 调用摄像头扫码，附手动输入兜底；扫码与答题顺序不限
- 🔐 **账号体系** — JWT 登录 + BCrypt 密码；管理员可创建/禁用账号、重置密码
- 🎬 **剧情视频** — 5 支 AI 生成的剧情动画，嵌入关键转折关卡
- 🌏 **三语支持** — 繁体中文 / 简体中文 / English（数据库 JSON 列存储）
- 🎒 **道具收集** — 每关解锁剧情道具卡，可查看大图与说明
- 🏆 **排行榜与数据看板** — Redis 排行榜、通关统计、平均分/平均耗时
- 📅 **每日到馆打卡** — Redis BitMap 存「一年中哪几天来过」，一天一位，全年只占约 46 字节
- ⚡ **高并发防护** — 关卡内容缓存（穿透 / 击穿 / 雪崩三重保护）、登录限流、Redis 故障自动回源 MySQL

## 架构

```
                   手机 / 桌面浏览器
                          │
                          │ 只请求同源 /api/*
                          ▼
              Next.js 16（:3000）          ← 页面、多语言、扫码
                          │
                          │ rewrite /api/*（本地）；Nginx 反代（生产）
                          ▼
              Spring Boot 3（:8080）       ← JWT、判题、结算、排行
                    │            │
                    ▼            ▼
          MySQL 8.4（:3307）   Redis 7（:6380）
          内容/对局/成绩     排行榜 ZSET / 关卡缓存 / BitMap 打卡 / 限流
```

浏览器永远只和 Next.js 通信，**没有跨域、没有前端硬编码的后端地址**。部署只是把这两个进程放到服务器上、用 Nginx 做同样的 `/api` 分流，不影响项目本身的全栈性质。

## 技术栈

| 层 | 技术 |
| --- | --- |
| 前端框架 | [Next.js 16](https://nextjs.org)（App Router）、[React 19](https://react.dev)、TypeScript 5 |
| 前端样式 / 动效 | [Tailwind CSS 4](https://tailwindcss.com)、[Framer Motion 12](https://www.framer.com/motion/) |
| 前端状态 | [Zustand 5](https://zustand-demo.pmnd.rs)（只缓存登录态，进度以服务端为准） |
| 扫码 | [html5-qrcode](https://github.com/mebjas/html5-qrcode) |
| 后端 | Java 21、[Spring Boot 3.5](https://spring.io/projects/spring-boot)、[MyBatis-Plus 3.5](https://baomidou.com) |
| 数据库 | MySQL 8.4 + Flyway 迁移 |
| 缓存 | Redis 7（排行榜 ZSET、关卡内容缓存、BitMap 打卡、限流，AOF 持久化） |
| 分布式锁 / 限流 | [Redisson 3.52](https://redisson.org)（`RLock`、`RRateLimiter`） |
| 鉴权 | JWT（jjwt 0.12）+ 拦截器 + BCrypt（spring-security-crypto） |
| 接口文档 | springdoc-openapi（Swagger UI） |

## 快速开始

### 环境要求

| 工具 | 版本 | 用途 |
| --- | --- | --- |
| Node.js | ≥ 20 | 运行 Next.js |
| JDK | 21 | 运行 Spring Boot（项目自带 Maven Wrapper，无需单独装 Maven） |
| Docker | 任意较新版本 | 一条命令拉起 MySQL 与 Redis（也可用本机安装的实例） |

### 1. 启动中间件

```bash
docker compose up -d
```

会在宿主机映射 `3307`（MySQL）和 `6380`（Redis）——故意避开常见的 `3306` / `6379`，避免与本机已有服务冲突。数据落在 Docker volume 中，重启不丢。

### 2. 启动后端

```bash
cd server
./mvnw spring-boot:run
```

首次启动时：

- Flyway 自动建表并导入 12 关内容（`db/migration/V1`–`V4`）
- 如果库里没有管理员，自动创建 `admin / Admin@123456`（可用环境变量覆盖，见[环境变量](#环境变量)）

接口文档：<http://localhost:8080/swagger-ui.html>　健康检查：<http://localhost:8080/api/health>

### 3. 启动前端

```bash
npm install
npm run dev
```

打开 <http://localhost:3000>。

### 4. 账号

- **管理员**：`admin / Admin@123456`（本地默认值）
- **学生**：由管理员创建。打开 Swagger UI → `POST /api/auth/login` 拿 token → 右上角 **Authorize** 填入 → 调 `POST /api/admin/players`：

```json
{ "studentNo": "ab123456", "nickname": "测试学生", "initialPassword": "12345678", "role": "STUDENT" }
```

也可以直接用前端 `/login` 登录管理员账号，再访问 `/admin` 查看数据看板。

## 项目结构

```
src/                              # 前端
├── app/
│   ├── (default)/page.tsx        # 序幕页（开场动画 + 语言切换）
│   ├── [lang]/                   # 多语言路由：zh-Hant | zh-Hans | en
│   │   ├── login/                #   登录（账号 + 密码）
│   │   ├── map/                  #   寻宝地图
│   │   ├── levels/01 … 12/       #   12 个关卡页（薄封装）
│   │   ├── result/               #   通关结算
│   │   └── leaderboard/          #   排行榜
│   └── admin/                    #   管理端数据看板（仅 ADMIN）
├── components/
│   ├── level-page.tsx            # 关卡核心组件（拉详情、提交答案/扫码、结算回写）
│   ├── qr-verifier.tsx           # 扫码组件（校验交给服务端）
│   ├── auth-gate.tsx             # 未登录拦截 + 从服务端恢复对局
│   └── …
├── hooks/use-level-catalog.ts    # 关卡目录缓存（地图/道具墙/结算页共用）
├── lib/
│   ├── api.ts                    # API client：拆 envelope、ApiError、401 清理
│   ├── types.ts                  # 与后端 DTO 对齐（不含答案）
│   ├── translations.ts           # 界面通用文案（三语）
│   └── i18n.ts                   # 语言定义与路由工具
├── data/levels.ts                # ★ 关卡内容源文件（仅用于生成 SQL）
└── store/game-store.ts           # 登录态 + 进度缓存

server/                           # 后端
├── src/main/java/com/libhunt/
│   ├── controller/               # 关卡 / 登录 / 对局 / 排行 / 管理端
│   ├── service/                  # GameRunService（核心）、LeaderboardService、AuthService…
│   ├── mapper/                   # MyBatis-Plus Mapper（含 FOR UPDATE、窗口函数 SQL）
│   ├── entity/ dto/              # 数据库实体 / 对外 VO
│   ├── interceptor/              # JwtInterceptor、RoleInterceptor
│   ├── common/                   # Result 包装、GameRules、异常、@RequireRole
│   └── config/                   # WebMvc、OpenAPI、安全 Bean、管理员初始化
└── src/main/resources/
    ├── application.yml
    └── db/migration/             # Flyway：建表 + 关卡种子数据

scripts/export-levels.mjs         # 从 src/data/levels.ts 生成 Flyway 种子 SQL
docker-compose.yml                # 本地 MySQL + Redis
```

## 接口一览

统一响应体：`{ "code": 0, "msg": "ok", "data": … }`；业务失败 `code != 0`，未登录 401，无权限 403。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| POST | `/api/auth/login` | 公开 | 登录，返回 JWT + 玩家信息 |
| GET | `/api/auth/me` | 登录 | 当前登录玩家信息 |
| GET | `/api/levels?locale=` | 公开 | 关卡列表（标题 + 道具） |
| GET | `/api/levels/{n}?locale=` | 公开 | 关卡详情（**不含答案**） |
| GET | `/api/leaderboard?limit=` | 公开 | 排行榜（每人取最好一局） |
| POST | `/api/runs` | 登录 | 开局；支持 `Idempotency-Key` 头 |
| GET | `/api/runs/current` | 登录 | 当前/最近一局，用于刷新恢复 |
| POST | `/api/runs/{id}/abandon` | 登录 | 放弃本局（重新开始） |
| POST | `/api/runs/{id}/levels/{n}/answer` | 登录 | 提交选项，服务端判题 |
| POST | `/api/runs/{id}/levels/{n}/qr` | 登录 | 提交现场二维码内容 |
| GET | `/api/checkin` | 登录 | 今日是否打卡、今年累计天数、连续天数 |
| POST | `/api/checkin` | 登录 | 今日打卡（幂等） |
| GET | `/api/admin/stats` | ADMIN | 玩家数、通关数、平均分/耗时 |
| GET/POST | `/api/admin/players` | ADMIN | 账号分页 / 创建 |
| PATCH | `/api/admin/players/{id}/status` | ADMIN | 启用 / 禁用 |
| POST | `/api/admin/players/{id}/password` | ADMIN | 重置密码 |
| POST | `/api/admin/leaderboard/rebuild` | ADMIN | 从 MySQL 重建 Redis 排行榜 |

完整参数与示例见 Swagger UI。

## 服务端权威是怎么保证的

- **答案不下发**：`level.qr_answer`、`level.correct_option_keys` 只供服务端判题；对外 VO 里没有这两个字段（`level-page.tsx` 曾经用过的 `correctOptionIds` 在类型层面已删除）
- **顺序由服务端管**：`game_run.last_level_no` 是唯一权威，提交非当前关卡直接拒绝
- **一关只结算一次**：`level_attempt.settled` 标记 + `UNIQUE(run_id, level_no)` 唯一索引
- **并发安全**：答题/扫码时对 `game_run` 行 `SELECT … FOR UPDATE`，同一局的请求串行执行；同一玩家并发开局用 `player` 行锁
- **幂等**：重复提交答案/扫码返回原结果，不重复加分；开局支持 `Idempotency-Key`
- **一局未结束不能再开**：`POST /api/runs` 会返回进行中的那一局，避免刷分

## 内容维护

> **关卡内容的事实来源是 `src/data/levels.ts`；数据库里的数据由脚本生成，不要手改。**

内容源文件仍是 [src/data/levels.ts](src/data/levels.ts)（三语、答案、二维码、道具都在一个文件里），脚本把它导出为 Flyway SQL：

```bash
# 默认输出 V3__seed_levels.sql（全新环境的完整种子）
node --experimental-strip-types scripts/export-levels.mjs

# 自定义文件名，必须是 Flyway 命名：V<版本号>__<描述>.sql
node --experimental-strip-types scripts/export-levels.mjs V5__update_levels.sql
```

脚本会校验：三种语言的关卡数一致、选项集合一致、语言无关字段（答案、二维码、视频地址）三语一致，不一致直接报错。

**⚠️ 两个限制**

1. **Flyway 会校验已执行脚本的 checksum**：`V3__seed_levels.sql` 在某个环境跑过之后就不能原地修改，否则下次启动报 checksum mismatch。改内容请生成新版本号的文件；临时环境也可以 `flyway repair` 或清库重来。
2. **脚本只生成 `INSERT`**，适合全新数据库。已有数据的环境（尤其是有玩家道具记录时，`item` 主键不能重建）建议把生成的语句改写成按 `level_no` / `item_key` 的 `UPDATE` 再执行——这一块目前没有自动化，属于已知限制。

常用修改位置：

| 修改目标 | 位置 |
| --- | --- |
| 题干 / 选项 / 正确答案 / 剧情 | `src/data/levels.ts` |
| 二维码内容与位置提示 | `src/data/levels.ts` → `qrAnswer`、`qrLocation`；图片在 `public/qr/` |
| 道具卡片 | `src/data/levels.ts` → `items`；图片在 `public/assets/` |
| 界面通用文案 | `src/lib/translations.ts` |
| 计分规则 | `server/.../common/GameRules.java` |
| 视频 URL / 关卡数 | `src/lib/constants.ts`（关卡数以 `level` 表实际数据为准） |

## 环境变量

**后端**（`server/`，未设置时使用开发默认值）：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `JWT_SECRET` | 开发占位值 | JWT 签名密钥，**生产环境必须改为 ≥ 32 字节随机串** |
| `ADMIN_STUDENT_NO` | `admin` | 首次启动创建的管理员账号 |
| `ADMIN_INITIAL_PASSWORD` | `Admin@123456` | 首次启动创建的管理员密码 |
| `SPRING_DATASOURCE_URL` / `..._USERNAME` / `..._PASSWORD` | `localhost:3307/libhunt`、root/root | Spring Boot 环境变量覆盖 |
| `SPRING_DATA_REDIS_HOST` / `..._PORT` | `localhost:6380` | 同上 |
| `LIBHUNT_CACHE_ENABLED` | `true` | 关卡内容缓存总开关；`false` 时每次读都回源 MySQL（排查 / 压测基线用） |

**前端**：

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `API_PROXY_TARGET` | `http://127.0.0.1:8080` | 本地开发时 `/api/*` 的转发目标 |

参考 [.env.example](.env.example)。注意前端会读取 `.env.local`，而 **Spring Boot 不读这个文件**——后端的变量要通过 shell、IDE 运行配置或 systemd 注入。

## 可用脚本

| 命令 | 说明 |
| --- | --- |
| `npm run dev` | 启动前端开发服务器 |
| `npm run build` / `npm run start` | 生产构建 / 启动 |
| `npm run lint` | ESLint 检查 |
| `cd server && ./mvnw spring-boot:run` | 启动后端 |
| `cd server && ./mvnw test` | 跑后端测试（自动拉起 MySQL / Redis 容器） |
| `cd server && ./mvnw -DskipTests package` | 打包 jar |
| `node --experimental-strip-types scripts/export-levels.mjs` | 重新生成关卡种子 SQL |

## 测试

后端有 49 个自动化用例，跑在真实的 MySQL 8.4 + Redis 7 容器上（Testcontainers），不依赖本地已启动的 docker compose：

```bash
cd server && ./mvnw test
```

| 测试类 | 覆盖内容 |
| --- | --- |
| `GameRulesTest` | 计分规则：首答 100、每错扣 20、最低 20 |
| `GameRunFlowIntegrationTest` | 开局 / 判题 / 扫码 / 结算 / 重复提交幂等 / 跳关拦截 / 放弃重开 / 12 关通关 |
| `GameRunConcurrencyIntegrationTest` | 10 个并发请求：只落一条过关记录、只结算一次、只开一局 |
| `LeaderboardIntegrationTest` | 复合排序、每人取最好一局、Redis 预热与全量重建 |
| `LeaderboardFallbackTest` | Redis 不可用时回落 MySQL；写排行榜失败不影响通关 |
| `ApiSecurityIntegrationTest` | 401 / 403 统一 JSON、BCrypt 登录、禁用账号、关卡接口不泄题 |
| `LevelCacheIntegrationTest` | 关卡详情/列表缓存命中、缓存空值标记、按语言分键 |
| `LevelCacheFallbackTest` | Redis 故障时缓存自动降级回源 |
| `LevelCacheDisabledTest` | 缓存开关关闭后只读 MySQL、不写 Redis |
| `LoginRateLimitIntegrationTest` | 同 IP 限流 429、账号级限流跨 IP 生效 |
| `CheckinIntegrationTest` | 打卡幂等、连续天数、BitMap 一天一位的存储占用 |

为什么不用 H2：判题依赖 `SELECT ... FOR UPDATE`，排行榜依赖窗口函数，迁移脚本用到 JSON 列——这些都是 MySQL 特有行为，换成 H2 既测不出真实问题，也测不出并发。

## 压测

JMeter 测试计划与结果在 [perf/](perf/) 目录，完整报告见 [perf/RESULTS.md](perf/RESULTS.md)。核心结论（本机 M3，单实例）：

| 场景 | 结果 |
| --- | --- |
| 关卡详情，缓存开 / 关 | 11,507 → 3,027 req/s（P95 7 → 43 ms），约 **3.8 倍** |
| 120 线程混合压测（关卡 / 排行榜 / 打卡 / 判题） | 62,000 次请求，0 错误，整体 **8,712 req/s** |
| 判题接口（20 线程并发抢同一批对局） | P95 26 ms，0 错误 |
| 压测期间 Redis | `keyspace_misses` 增量 **0**——50,000 次关卡详情没有一次回源 |

```bash
cd perf && jmeter -n -t libhunt-load-test.jmx -l results/results.jtl -e -o results/html
```

## 关于部署

本项目是作品集项目，**不打算部署到公网**。本地 `docker compose up -d` + 两个进程就是完整系统，面试时直接在本机演示；README 里的启动步骤与环境变量已经足够复现。

## 开发注意事项

### Next.js 16 与 AI 辅助开发

本项目使用 Next.js 16，与大量 AI 工具训练语料中的 13/14 写法存在**破坏性差异**。最常见的一项是 `params` 现为 Promise：

```tsx
// ❌ Next.js 14 及更早
export default function Page({ params }: { params: { lang: string } }) {
  const locale = params.lang;
}

// ✅ 本项目写法
export default async function Page({ params }: { params: Promise<{ lang: string }> }) {
  const { lang } = await params;
}
```

仓库根目录的 [AGENTS.md](AGENTS.md) 用于提示 AI 工具查阅 `node_modules/next/dist/docs/` 中的本地文档。遇到 AI 生成的代码报错时，先确认是否为版本 API 差异。

### 摄像头权限

浏览器仅在 **HTTPS 或 localhost** 环境下允许调用摄像头。通过局域网 IP（如 `http://192.168.x.x:3000`）访问时扫码功能不可用，真机测试请用 HTTPS 隧道工具，或使用扫码框下方的手动输入。

### 静态资源缓存

视频 URL 附带 `?v=` 查询参数用于缓存失效控制（见 `CACHE_BUSTER`）。替换视频或图片素材后**必须同步更新**，否则玩家浏览器继续加载旧资源。

### 本地缓存

前端只在 localStorage 保存登录态（`libhunt.token`）和玩家信息，key 为 `lib-hunt-game-state-v4`。**游戏进度不存本地**，刷新或换设备都以 `GET /api/runs/current` 为准。

## 已知限制

- **关卡内容仍需改代码**：内容源文件是 TS，改完要生成迁移并重启后端；没有关卡 CMS
- **二维码明文比对**：没做 HMAC 防伪，扫码内容泄露后可以被复制
- **禁用账号不能立即踢下线**：JWT 无状态，禁用后已签发的 token 在有效期（12 小时）内仍可用；要立即生效需要加 Redis 黑名单
- **Redis 故障时没有熔断**：接口仍能 fail-open 回源 MySQL、不会报错，但每个请求要等 Redis 连接失败，延迟会劣化到秒级
- **压测只在单机跑过**：JMeter 数据见 [perf/RESULTS.md](perf/RESULTS.md)，没有多机 / 云环境数据
- **排行榜读路径仍回源 MySQL**：Redis 只负责排序，昵称与分数要批量补查；压测里 9,000 次读取产生 1.8 万次 SELECT，量级不大但可以进一步缓存整张榜
- **管理端只有基础能力**：账号管理、统计、重建排行榜；没有批量导入（Excel）与可视化图表

## 相关文档

| 文档 | 内容 |
| --- | --- |
| [PLAN.md](PLAN.md) | 改造计划、阶段进度、验收记录与踩坑 |
| [docs/DATA-MODEL.md](docs/DATA-MODEL.md) | 表结构、索引、Redis 数据结构与设计取舍 |
| [docs/BEFORE-AFTER.md](docs/BEFORE-AFTER.md) | 纯前端 → 全栈的改造前后对比 |
| [docs/INTERVIEW.md](docs/INTERVIEW.md) | 每个技术点的 3 分钟讲稿与追问 |
| [perf/](perf/) | JMeter 方案与压测结果 |
| [AGENTS.md](AGENTS.md) | AI 编程工具使用约定 |
| [public/docs/](public/docs/) | 游戏剧情与媒体素材制作方案 |

## 贡献

欢迎提交 Issue 与 Pull Request。提交代码前请先阅读 [AGENTS.md](AGENTS.md)，并确认修改在三种语言下均已验证。

## 许可证

本项目以 [MIT](LICENSE) 许可证开源。游戏内的剧情文案、题目与二维码位置等内容为可配置数据，适配到其他场馆时请留意内容版权归属。
