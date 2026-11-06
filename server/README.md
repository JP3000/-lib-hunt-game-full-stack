# lib-hunt-server

图书馆寻宝游戏的 Java 后端（Spring Boot 3.5 + Java 21）。

## 技术栈

| 分类 | 选型 |
| --- | --- |
| 框架 | Spring Boot 3.5.3 |
| 语言 | Java 21 |
| 持久层 | MyBatis-Plus 3.5.7（mybatis-spring 覆盖为 3.0.4，兼容 Boot 3.5） |
| 数据库 | MySQL 8.4 |
| 迁移 | Flyway 11 |
| 缓存 | Redis 7 |
| 认证 | JWT (jjwt 0.12.6) + 拦截器 |
| 接口文档 | springdoc-openapi 2.8.6 |

> 注意：没有使用 Knife4j。Knife4j 4.5.0 依赖 springdoc 2.3.0，与 Spring Boot 3.5 不兼容，会启动报错。
> 注解用法完全一致（`@Tag` / `@Operation`），后续想换 UI 也不影响业务代码。

## 本地启动

```bash
# 1. 在仓库根目录启动 MySQL + Redis
docker compose up -d

# 2. 启动后端
cd server
./mvnw spring-boot:run
```

启动后：

| 地址 | 说明 |
| --- | --- |
| `http://localhost:8080/api/health` | 健康检查（无需认证） |
| `http://localhost:8080/swagger-ui.html` | 接口文档 |
| `http://localhost:8080/actuator/health` | Spring Boot 健康端点 |

## 测试

```bash
./mvnw test
```

测试用 Testcontainers 自动拉起 `mysql:8.4` 与 `redis:7-alpine`，不依赖本机的 docker compose，也不会污染开发库。首次运行需要拉镜像，之后整套约 20 秒。

| 测试类 | 覆盖内容 |
| --- | --- |
| `GameRulesTest` | 计分规则 |
| `GameRunFlowIntegrationTest` | 开局 / 判题 / 扫码 / 结算 / 幂等 / 跳关 / 放弃 / 通关 |
| `GameRunConcurrencyIntegrationTest` | 并发答题、并发结算、并发开局（验证行锁与唯一索引） |
| `LeaderboardIntegrationTest` | Redis 排序、每人最好一局、预热与重建 |
| `LeaderboardFallbackTest` | Redis 故障时回落 MySQL |
| `ApiSecurityIntegrationTest` | 401 / 403、登录、禁用账号、答案不泄露 |

## 端口说明

| 服务 | 宿主机端口 | 说明 |
| --- | --- | --- |
| MySQL | **3307** | 宿主机 3306 被其他 MySQL 占用，改用 3307 |
| Redis | **6380** | 避开本机已有的 6379 |
| 后端 | 8080 | |

如需改端口，同步修改 `docker-compose.yml` 与 `src/main/resources/application.yml`。

## 配置

| 配置项 | 来源 | 说明 |
| --- | --- | --- |
| `JWT_SECRET` | 环境变量（默认值仅本地开发） | 至少 32 字节，生产必须覆盖 |
| `ADMIN_STUDENT_NO` / `ADMIN_INITIAL_PASSWORD` | 环境变量 | 首次启动创建的管理员账号，默认 `admin / Admin@123456` |
| `SPRING_DATASOURCE_*` / `SPRING_DATA_REDIS_*` | 环境变量 | Spring Boot 标准覆盖方式，默认指向本地 docker-compose |
| `libhunt.jwt.expire-hours` | application.yml | Token 有效期，默认 12 小时 |

## 目录结构

```
src/main/java/com/libhunt/
├── common/          Result、异常、GameRules、UserContext、@RequireRole
├── config/          MyBatis-Plus、OpenAPI、WebMvc、安全 Bean、管理员初始化
├── controller/      对外接口（关卡 / 登录 / 对局 / 排行 / 管理端）
├── dto/             对外 VO 与请求体（不含答案字段）
├── entity/          数据库实体
├── interceptor/     JwtInterceptor、RoleInterceptor
├── mapper/          MyBatis-Plus Mapper（含 FOR UPDATE、窗口函数 SQL）
├── service/         业务实现（核心为 GameRunService）
└── util/            JwtUtil、LocaleSupport
src/main/resources/
├── application.yml
└── db/migration/    Flyway 迁移脚本
src/test/java/com/libhunt/
├── support/         IntegrationTestBase（Testcontainers + 测试数据工具）
└── *.java           单元测试 / 集成测试 / 并发测试
```

## 数据库迁移

新增表结构时，在 `src/main/resources/db/migration/` 下按 `V{n}__{描述}.sql` 命名。

**不要手写 SQL 改库**——迁移脚本是版本历史，也是面试时可以展示的资产。
