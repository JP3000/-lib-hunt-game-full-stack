# 压测结果（2026-11-06）

## 环境

| 项 | 值 |
| --- | --- |
| 机器 | Apple M3，16 GB，macOS 26.6 |
| JDK | Temurin 21.0.10 |
| 应用 | `lib-hunt-server-0.1.0.jar`，单实例，Tomcat 默认线程池 |
| MySQL / Redis | Docker 容器，`mysql:8.4` / `redis:7` |
| 压测工具 | JMeter 5.6（120 线程混合场景）+ `ab`（单接口对照） |
| 客户端 | 与本机应用同机（含 loopback 开销，数值偏保守） |

所有数字都是**同一 JVM 预热之后**测的：先跑 2 万次请求让 Jackson / MyBatis / Tomcat 完成 JIT 编译，再开始计时。冷启动第一次测量会低一个数量级，不能拿来对比。

## 一、缓存到底值不值：同一接口开/关缓存

`GET /api/levels/1?locale=zh-Hant`，`ab -k -n 5000 -c 50`，各跑 3 轮取中位数：

| 模式 | 吞吐（req/s） | P50 | P95 | P99 |
| --- | ---: | ---: | ---: | ---: |
| 缓存开启（Redis） | **11,507** | 4 ms | 7 ms | 10 ms |
| 缓存关闭（每次回源 MySQL，`libhunt.cache.enabled=false`） | **3,027** | 12 ms | 43 ms | 71 ms |

**约 3.8 倍吞吐，P95 低 6 倍。** 关缓存不是模拟故障，而是走同一个接口的真实 MySQL 路径，所以这是干净的对照。

## 二、混合场景：JMeter 120 线程

线程组同时运行，共 62,000 次请求：

| 请求 | 样本 | 错误率 | 平均 | P50 | P95 | P99 | 最大 | 吞吐（req/s） |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| `GET /api/levels/{n}`（50 线程） | 50,000 | 0% | 3.8 ms | 4 ms | 7 ms | 10 ms | 55 ms | 7,026 |
| `GET /api/leaderboard`（30 线程） | 9,000 | 0% | 7.4 ms | 6 ms | 16 ms | 24 ms | 130 ms | 1,357 |
| `POST /api/checkin`（20 线程） | 1,000 | 0% | 17.6 ms | 17 ms | 33 ms | 42 ms | 101 ms | 216 |
| `GET /api/checkin`（20 线程） | 1,000 | 0% | 12.0 ms | 12 ms | 23 ms | 28 ms | 70 ms | 218 |
| `POST .../answer`（20 线程） | 1,000 | 0% | 13.0 ms | 10 ms | 26 ms | 42 ms | 79 ms | 275 |

整体：**62,000 次请求 / 7.1 秒 / 8,712 req/s，0 错误。**

同期 Redis 与 MySQL 的计数变化：

| 指标 | 压测前 | 压测后 | 增量 |
| --- | ---: | ---: | ---: |
| Redis `keyspace_hits` | 93,204 | 160,204 | +67,000 |
| Redis `keyspace_misses` | 48 | 48 | **+0** |
| MySQL `Com_select` | 67,259 | 89,260 | +22,001 |

- 5 万次关卡详情**没有产生一次缓存未命中**，MySQL 的 2.2 万次 SELECT 全部来自排行榜与判题：排行榜每次读取要用 `selectBatchIds` 补昵称和分数（9,000 次 × 2），判题每次要锁对局、查关卡、查过关记录（1,000 次 × 约 3）。
- 这也暴露了一个可优化点：Redis 目前只承担**排序**，读的时候仍要回 MySQL 补玩家信息。把昵称和分数直接编码进 member（或缓存整张榜）就能把读路径彻底留在 Redis——没做，因为榜单只有 20 条、批量查询在毫秒级，当前不值得引入一致性负担。
- 判题接口 20 线程并发更新同一批对局，P95 26 ms 且 0 错误——`SELECT ... FOR UPDATE` 把同一局的请求串行化，但没有把连接池拖垮（Hikari 上限 10）。

## 三、Redis 故障时的降级

把 Redis 换成不可达端口后，接口**仍然返回 200、不报错**，但每个请求会等待 Redis 连接失败，单请求约 1.8 秒（JVM 冷启动时实测）。可用性保住了，延迟掉得很厉害。

> 这是已知短板：目前是「fail-open + 每次重试」，没有熔断。生产上应加 Resilience4j 之类的熔断器，或者在连续失败后本地短路一段时间，避免 Redis 故障把 P99 拖成秒级。见 `PLAN.md` 阶段 8 的待办。

同时这次排查暴露并修复了一个启动期问题：Redisson 默认在 `Redisson.create()` 时就要连上 Redis，Redis 没起来应用直接启动失败。现在 `RedissonConfig` 开了 `setLazyInitialization(true)`，应用可以先起来，锁与限流各自在运行时 fail-open。

## 四、复现

```bash
docker compose up -d
cd server && ./mvnw -DskipTests package
java -jar target/lib-hunt-server-0.1.0.jar --server.port=8081 \
  --logging.level.com.libhunt=warn \
  --mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl
./perf/setup-load-test.sh

# 混合场景
cd perf && jmeter -n -t libhunt-load-test.jmx -l results/results.jtl -e -o results/html
node summarize.mjs results/results.jtl

# 单接口对照：重启应用时追加 --libhunt.cache.enabled=false 即可
ab -k -n 20000 -c 50 "http://127.0.0.1:8081/api/levels/1?locale=zh-Hant"   # 预热
ab -k -n 5000  -c 50 "http://127.0.0.1:8081/api/levels/1?locale=zh-Hant"
```
