# 压测

用 JMeter 对四个典型路径做对比，验证「缓存 + Redis + 行锁」在并发下的表现：

| 线程组 | 请求 | 路径特征 |
| --- | --- | --- |
| 1-关卡详情（缓存） | `GET /api/levels/{1..12}?locale=zh-Hant` | 命中 Redis，正常不碰 MySQL |
| 2-排行榜（ZSET） | `GET /api/leaderboard?limit=20` | Redis ZSET 读取，失败才回落 MySQL |
| 3-打卡（BitMap） | `POST` + `GET /api/checkin` | 一天一位的写读，幂等 |
| 4-判题（MySQL 行锁） | `POST /api/runs/{id}/levels/1/answer` | `SELECT ... FOR UPDATE` + 写 `level_attempt` |

## 运行

```bash
# 1. 启动 MySQL / Redis（仓库根目录）
docker compose up -d

# 2. 启动一个专门用于压测的后端实例（避免干扰正在开发的那个）
cd server
./mvnw -DskipTests package
java -jar target/lib-hunt-server-0.1.0.jar --server.port=8081

# 3. 生成 20 个压测账号、token 和进行中的对局（写入 perf/players.csv）
cd ..
./perf/setup-load-test.sh

# 4. 跑 JMeter（非 GUI 模式，自动生成 HTML 报告）
jmeter -n -t perf/libhunt-load-test.jmx -l perf/results/results.jtl \
       -e -o perf/results/html
```

`players.csv` 和 `results/` 都是本机产物，已在 `.gitignore` 中忽略。

## 调参

在 JMX 里改线程组即可：

- 并发数：`ThreadGroup.num_threads`
- 每线程循环：`LoopController.loops`
- 目标地址：`HTTP 默认值` 的 `HTTPSampler.domain` / `port`

账号数量由 `COUNT=20 ./perf/setup-load-test.sh` 控制；线程数超过账号数时 CSV 会循环复用。
