package com.libhunt;

import com.libhunt.dto.AnswerResultVO;
import com.libhunt.dto.RunStateVO;
import com.libhunt.dto.SettlementVO;
import com.libhunt.entity.Level;
import com.libhunt.entity.Player;
import com.libhunt.service.GameRunService;
import com.libhunt.service.QrTokenService;
import com.libhunt.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 并发用例：验证「行锁 + 唯一索引 + settled 标记」三件套真的生效。
 * <p>
 * 这些用例不能加 {@code @Transactional}——事务必须真正提交，
 * 否则其他线程看不到数据，锁也就测不出来。
 */
@DisplayName("并发与幂等")
class GameRunConcurrencyIntegrationTest extends IntegrationTestBase {

    @Autowired
    private GameRunService gameRunService;

    @Autowired
    private QrTokenService qrTokenService;

    @Test
    @DisplayName("10 个并发的错误答案只落一行记录，但累计 10 次尝试")
    void concurrentWrongAnswersProduceSingleRow() throws Exception {
        Player player = createPlayer("conc-wrong");
        RunStateVO run = gameRunService.start(player.getId(), null);

        List<AnswerResultVO> results = runConcurrently(10,
                () -> gameRunService.submitAnswer(
                        player.getId(), run.runId(), 1, List.of("definitely-wrong")));

        assertThat(results).hasSize(10);
        assertThat(results).allSatisfy(r -> assertThat(r.correct()).isFalse());
        assertThat(attemptRowsOf(run.runId(), 1)).isEqualTo(1);
        assertThat(attemptsOf(run.runId(), 1)).isEqualTo(10);
        assertThat(gameRunService.current(player.getId()).lastLevelNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("答题与扫码并发提交，本关只结算一次")
    void concurrentAnswerAndQrSettleExactlyOnce() throws Exception {
        Player player = createPlayer("conc-settle");
        RunStateVO run = gameRunService.start(player.getId(), null);
        Level level = level(1);
        List<String> correct = level.getCorrectOptionKeys();
        String qrAnswer = qrToken(level);

        List<Callable<SettlementVO>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> gameRunService.submitAnswer(
                    player.getId(), run.runId(), 1, correct).settlement());
            tasks.add(() -> gameRunService.submitQr(
                    player.getId(), run.runId(), 1, qrAnswer).settlement());
        }

        List<SettlementVO> settlements = runConcurrently(tasks);

        // 10 个请求里只有触发「最后一道验证」的那一个带回结算结果
        assertThat(settlements.stream().filter(Objects::nonNull)).hasSize(1);

        RunStateVO current = gameRunService.current(player.getId());
        assertThat(current.totalScore()).isEqualTo(20);
        assertThat(current.lastLevelNo()).isEqualTo(2);
        assertThat(settledRowsOf(run.runId())).isEqualTo(1);
    }

    @Test
    @DisplayName("两个不同玩家同时通关，各自锁自己的 game_run 行，互不阻塞")
    void twoPlayersCompleteConcurrentlyWithoutBlockingEachOther() throws Exception {
        Player alice = createPlayer("conc-player-a");
        Player bob = createPlayer("conc-player-b");
        RunStateVO runA = gameRunService.start(alice.getId(), null);
        RunStateVO runB = gameRunService.start(bob.getId(), null);
        Level level = level(1);

        List<Callable<SettlementVO>> tasks = List.of(
                () -> {
                    gameRunService.submitAnswer(
                            alice.getId(), runA.runId(), 1, level.getCorrectOptionKeys());
                    return gameRunService.submitQr(
                            alice.getId(), runA.runId(), 1, qrToken(level)).settlement();
                },
                () -> {
                    gameRunService.submitAnswer(
                            bob.getId(), runB.runId(), 1, level.getCorrectOptionKeys());
                    return gameRunService.submitQr(
                            bob.getId(), runB.runId(), 1, qrToken(level)).settlement();
                });

        List<SettlementVO> settlements = runConcurrently(tasks);

        assertThat(settlements).allSatisfy(s -> assertThat(s).isNotNull());
        assertThat(settlements).extracting(SettlementVO::totalScore)
                .containsExactlyInAnyOrder(20, 20);
        assertThat(gameRunService.current(alice.getId()).totalScore()).isEqualTo(20);
        assertThat(gameRunService.current(bob.getId()).totalScore()).isEqualTo(20);
    }

    @Test
    @DisplayName("10 个并发开局只会产生一局")
    void concurrentStartCreatesSingleRun() throws Exception {
        Player player = createPlayer("conc-start");

        List<RunStateVO> runs = runConcurrently(10,
                () -> gameRunService.start(player.getId(), null));

        Long runId = runs.get(0).runId();
        assertThat(runs).extracting(RunStateVO::runId).containsOnly(runId);

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM game_run WHERE player_id = ?",
                Integer.class, player.getId());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    @DisplayName("10 个并发请求带同一个幂等键，仍然只有一局")
    void concurrentStartWithSameIdempotencyKeyCreatesSingleRun() throws Exception {
        Player player = createPlayer("conc-idem");

        List<RunStateVO> runs = runConcurrently(10,
                () -> gameRunService.start(player.getId(), "concurrent-start-key"));

        assertThat(runs).extracting(RunStateVO::runId).containsOnly(runs.get(0).runId());

        Integer rows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM game_run WHERE player_id = ?",
                Integer.class, player.getId());
        assertThat(rows).isEqualTo(1);
    }

    /** 按生产逻辑签发一枚有效期 1 天的关卡二维码。 */
    private String qrToken(Level level) {
        return qrTokenService.issue(level.getLevelNo(), level.getQrAnswer(), Duration.ofDays(1));
    }
}
