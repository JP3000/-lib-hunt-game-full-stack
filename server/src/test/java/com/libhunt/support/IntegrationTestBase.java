package com.libhunt.support;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.libhunt.common.context.UserContext;
import com.libhunt.entity.Level;
import com.libhunt.entity.Player;
import com.libhunt.mapper.LevelMapper;
import com.libhunt.mapper.PlayerMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * 集成测试基类：真实的 MySQL 8.4 + Redis 7 容器。
 * <p>
 * 为什么不用 H2 / 内嵌 Redis：
 * <ul>
 *   <li>判题依赖 {@code SELECT ... FOR UPDATE}，H2 的锁语义与 InnoDB 不同，测不出并发问题</li>
 *   <li>排行榜依赖窗口函数与复合索引排序</li>
 *   <li>迁移脚本里的 JSON 列、utf8mb4 都是 MySQL 特有语法</li>
 * </ul>
 * 容器在静态块里启动，同一个 JVM 里的所有测试类共用，避免每个类重启一次。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        // 并发用例会同时占用多个连接，默认池 10 刚好卡在边界上
        "spring.datasource.hikari.maximum-pool-size=20",
        // 测试输出里不需要满屏 SQL
        "mybatis-plus.configuration.log-impl=org.apache.ibatis.logging.nologging.NoLoggingImpl",
        "logging.level.com.libhunt=warn"
})
public abstract class IntegrationTestBase {

    protected static final String LEADERBOARD_KEY = "libhunt:leaderboard:global";

    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.4"))
                    .withDatabaseName("libhunt_test")
                    .withUsername("libhunt")
                    .withPassword("libhunt")
                    .withCommand("--character-set-server=utf8mb4",
                            "--collation-server=utf8mb4_0900_ai_ci");

    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                    .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected StringRedisTemplate redis;

    @Autowired
    protected PlayerMapper playerMapper;

    @Autowired
    protected LevelMapper levelMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    /**
     * 清空业务数据，但保留 AdminBootstrap 建出来的管理员账号。
     * 关卡、选项、道具是 Flyway 种子数据，不动。
     */
    @BeforeEach
    void resetBusinessData() {
        jdbc.update("DELETE FROM player_item");
        jdbc.update("DELETE FROM level_attempt");
        jdbc.update("DELETE FROM game_run");
        jdbc.update("DELETE FROM player WHERE role <> 'ADMIN'");
        // 排行榜、限流都是跨用例共享的状态，统一清空再做隔离
        Set<String> redisKeys = redis.keys("libhunt:*");
        if (redisKeys != null && !redisKeys.isEmpty()) {
            redis.delete(redisKeys);
        }
        UserContext.clear();
    }

    // ------------------------------------------------------------------ 造数据

    protected Player createPlayer(String studentNo) {
        return createPlayer(studentNo, "STUDENT");
    }

    protected Player createPlayer(String studentNo, String role) {
        Player player = new Player();
        player.setStudentNo(studentNo);
        player.setPasswordHash(passwordEncoder.encode("password123"));
        player.setRole(role);
        player.setNickname("测试-" + studentNo);
        player.setStatus(1);
        playerMapper.insert(player);
        return player;
    }

    protected Level level(int levelNo) {
        return levelMapper.selectOne(new LambdaQueryWrapper<Level>()
                .eq(Level::getLevelNo, levelNo)
                .last("LIMIT 1"));
    }

    protected int levelCount() {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM level WHERE status = 1", Integer.class);
        return count == null ? 0 : count;
    }

    protected int attemptsOf(Long runId, int levelNo) {
        Integer attempts = jdbc.queryForObject(
                "SELECT attempts FROM level_attempt WHERE run_id = ? AND level_no = ?",
                Integer.class, runId, levelNo);
        return attempts == null ? 0 : attempts;
    }

    protected int attemptRowsOf(Long runId, int levelNo) {
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM level_attempt WHERE run_id = ? AND level_no = ?",
                Integer.class, runId, levelNo);
        return rows == null ? 0 : rows;
    }

    protected int settledRowsOf(Long runId) {
        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM level_attempt WHERE run_id = ? AND settled = 1",
                Integer.class, runId);
        return rows == null ? 0 : rows;
    }

    // ------------------------------------------------------------------ 并发工具

    /**
     * 让一批任务尽量同时起跑，用于验证行锁与唯一索引。
     * 任何一个任务抛异常都会通过 {@link Future#get} 传播到测试线程。
     */
    protected <T> List<T> runConcurrently(List<Callable<T>> tasks) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        CountDownLatch ready = new CountDownLatch(tasks.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>(tasks.size());
        try {
            for (Callable<T> task : tasks) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(10, TimeUnit.SECONDS);
                    return task.call();
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            List<T> results = new ArrayList<>(tasks.size());
            for (Future<T> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    protected <T> List<T> runConcurrently(int times, Callable<T> task) throws Exception {
        List<Callable<T>> tasks = new ArrayList<>(times);
        for (int i = 0; i < times; i++) {
            tasks.add(task);
        }
        return runConcurrently(tasks);
    }
}
