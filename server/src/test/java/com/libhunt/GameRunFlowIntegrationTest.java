package com.libhunt;

import com.libhunt.common.exception.BusinessException;
import com.libhunt.dto.AnswerResultVO;
import com.libhunt.dto.LeaderboardEntryVO;
import com.libhunt.dto.QrResultVO;
import com.libhunt.dto.RunStateVO;
import com.libhunt.dto.SettlementVO;
import com.libhunt.entity.Level;
import com.libhunt.entity.Player;
import com.libhunt.service.GameRunService;
import com.libhunt.service.LeaderboardService;
import com.libhunt.service.QrTokenService;
import com.libhunt.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 核心闭环的集成测试：开局 → 答题 → 扫码 → 结算 → 通关。
 * <p>
 * 断言的是「服务端权威」的具体表现：答案不下发、顺序不可跳、重复提交不重复计分。
 */
@DisplayName("游戏核心闭环")
class GameRunFlowIntegrationTest extends IntegrationTestBase {

    @Autowired
    private GameRunService gameRunService;

    @Autowired
    private LeaderboardService leaderboardService;

    @Autowired
    private QrTokenService qrTokenService;

    @Test
    @DisplayName("开局创建一局进行中的游戏，从第 1 关开始")
    void startCreatesRunAtLevelOne() {
        Player player = createPlayer("flow-start");

        RunStateVO run = gameRunService.start(player.getId(), null);

        assertThat(run.runId()).isNotNull();
        assertThat(run.status()).isEqualTo("IN_PROGRESS");
        assertThat(run.lastLevelNo()).isEqualTo(1);
        assertThat(run.totalScore()).isZero();
        assertThat(run.levels()).isEmpty();
        assertThat(run.items()).isEmpty();
    }

    @Test
    @DisplayName("答错只累计尝试次数，不推进关卡、不结算")
    void wrongAnswerOnlyCountsAttempt() {
        Player player = createPlayer("flow-wrong");
        RunStateVO run = gameRunService.start(player.getId(), null);

        AnswerResultVO result = gameRunService.submitAnswer(
                player.getId(), run.runId(), 1, List.of("definitely-wrong"));

        assertThat(result.correct()).isFalse();
        assertThat(result.attempts()).isEqualTo(1);
        assertThat(result.score()).isZero();
        assertThat(result.nextAction()).isEqualTo("RETRY");
        assertThat(result.settlement()).isNull();

        RunStateVO current = gameRunService.current(player.getId());
        assertThat(current.lastLevelNo()).isEqualTo(1);
        assertThat(current.totalScore()).isZero();
    }

    @Test
    @DisplayName("先答题后扫码：答题不结算，扫码时一次性结算并推进关卡")
    void answerThenQrSettlesOnce() {
        Player player = createPlayer("flow-answer-first");
        RunStateVO run = gameRunService.start(player.getId(), null);
        Level level = level(1);

        // 先错一次，验证答错不扣分：答对仍得 20
        gameRunService.submitAnswer(player.getId(), run.runId(), 1, List.of("definitely-wrong"));
        AnswerResultVO answer = gameRunService.submitAnswer(
                player.getId(), run.runId(), 1, level.getCorrectOptionKeys());

        assertThat(answer.correct()).isTrue();
        assertThat(answer.attempts()).isEqualTo(2);
        assertThat(answer.score()).isEqualTo(20);
        assertThat(answer.questionPassed()).isTrue();
        assertThat(answer.qrPassed()).isFalse();
        assertThat(answer.nextAction()).isEqualTo("SCAN_QR");
        assertThat(answer.settlement()).isNull();

        QrResultVO qr = gameRunService.submitQr(
                player.getId(), run.runId(), 1, qrToken(level));

        assertThat(qr.correct()).isTrue();
        assertThat(qr.qrPassed()).isTrue();
        SettlementVO settlement = qr.settlement();
        assertThat(settlement).isNotNull();
        assertThat(settlement.levelScore()).isEqualTo(20);
        assertThat(settlement.totalScore()).isEqualTo(20);
        assertThat(settlement.nextLevelNo()).isEqualTo(2);
        assertThat(settlement.runCompleted()).isFalse();
        assertThat(settlement.durationSeconds()).isNull();

        int expectedItems = jdbc.queryForObject(
                "SELECT COUNT(*) FROM level_item WHERE level_id = ?", Integer.class, level.getId());
        assertThat(settlement.collectedItems()).hasSize(expectedItems);
    }

    @Test
    @DisplayName("先扫码后答题：扫码只标记已验，答题才触发结算")
    void qrThenAnswerAlsoSettles() {
        Player player = createPlayer("flow-qr-first");
        RunStateVO run = gameRunService.start(player.getId(), null);
        Level level = level(1);

        QrResultVO qr = gameRunService.submitQr(
                player.getId(), run.runId(), 1, qrToken(level));
        assertThat(qr.correct()).isTrue();
        assertThat(qr.qrPassed()).isTrue();
        assertThat(qr.questionPassed()).isFalse();
        assertThat(qr.settlement()).isNull();

        AnswerResultVO answer = gameRunService.submitAnswer(
                player.getId(), run.runId(), 1, level.getCorrectOptionKeys());
        assertThat(answer.correct()).isTrue();
        assertThat(answer.score()).isEqualTo(20);
        assertThat(answer.nextAction()).isEqualTo("NEXT");
        assertThat(answer.settlement()).isNotNull();
        assertThat(answer.settlement().totalScore()).isEqualTo(20);
        assertThat(answer.settlement().nextLevelNo()).isEqualTo(2);
    }

    @Test
    @DisplayName("重复提交已结算的关卡不会重复加分、重复发道具")
    void repeatedSubmissionIsIdempotent() {
        Player player = createPlayer("flow-idempotent");
        RunStateVO run = gameRunService.start(player.getId(), null);
        Level level = level(1);

        gameRunService.submitAnswer(player.getId(), run.runId(), 1, level.getCorrectOptionKeys());
        gameRunService.submitQr(player.getId(), run.runId(), 1, qrToken(level));

        QrResultVO repeatQr = gameRunService.submitQr(
                player.getId(), run.runId(), 1, qrToken(level));
        assertThat(repeatQr.correct()).isTrue();
        assertThat(repeatQr.settlement()).isNull();

        AnswerResultVO repeatAnswer = gameRunService.submitAnswer(
                player.getId(), run.runId(), 1, level.getCorrectOptionKeys());
        assertThat(repeatAnswer.correct()).isTrue();
        assertThat(repeatAnswer.settlement()).isNull();
        assertThat(repeatAnswer.score()).isEqualTo(20);

        RunStateVO current = gameRunService.current(player.getId());
        assertThat(current.totalScore()).isEqualTo(20);
        assertThat(current.lastLevelNo()).isEqualTo(2);
        assertThat(current.levels()).hasSize(1);
        assertThat(settledRowsOf(run.runId())).isEqualTo(1);

        Integer itemRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM player_item WHERE player_id = ?",
                Integer.class, player.getId());
        assertThat(itemRows).isEqualTo(current.items().size());
    }

    @Test
    @DisplayName("错误的二维码内容不会解锁本关")
    void wrongQrDoesNotAdvance() {
        Player player = createPlayer("flow-wrong-qr");
        RunStateVO run = gameRunService.start(player.getId(), null);

        QrResultVO qr = gameRunService.submitQr(
                player.getId(), run.runId(), 1, "NOT-THE-CODE");

        assertThat(qr.correct()).isFalse();
        assertThat(qr.qrPassed()).isFalse();
        assertThat(qr.settlement()).isNull();
        assertThat(gameRunService.current(player.getId()).lastLevelNo()).isEqualTo(1);
    }

    @Test
    @DisplayName("二维码比对忽略首尾空白与大小写，避免现场扫码因格式失败")
    void qrComparisonIsCaseAndSpaceInsensitive() {
        Player player = createPlayer("flow-qr-normalize");
        RunStateVO run = gameRunService.start(player.getId(), null);
        Level level = level(1);

        // 首尾空白 + 全大写仍应通过（校验前会 trim 并把十六进制签名转小写）
        String messy = "  " + qrToken(level).toUpperCase() + "  ";
        QrResultVO qr = gameRunService.submitQr(player.getId(), run.runId(), 1, messy);

        assertThat(qr.correct()).isTrue();
    }

    @Test
    @DisplayName("不能跳关：提交非当前关卡直接被拒绝")
    void cannotSkipLevels() {
        Player player = createPlayer("flow-skip");
        RunStateVO run = gameRunService.start(player.getId(), null);

        assertThatThrownBy(() -> gameRunService.submitAnswer(
                player.getId(), run.runId(), 2, List.of("a")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("第 1 关");
    }

    @Test
    @DisplayName("不能操作别人的对局")
    void cannotTouchAnotherPlayersRun() {
        Player owner = createPlayer("flow-owner");
        Player intruder = createPlayer("flow-intruder");
        RunStateVO run = gameRunService.start(owner.getId(), null);

        assertThatThrownBy(() -> gameRunService.submitAnswer(
                intruder.getId(), run.runId(), 1, List.of("a")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权操作");
    }

    @Test
    @DisplayName("带相同幂等键重复开局返回同一局")
    void sameIdempotencyKeyReturnsSameRun() {
        Player player = createPlayer("flow-idem-key");

        RunStateVO first = gameRunService.start(player.getId(), "start-key-1");
        RunStateVO second = gameRunService.start(player.getId(), "start-key-1");

        assertThat(second.runId()).isEqualTo(first.runId());
    }

    @Test
    @DisplayName("幂等键被其他玩家占用时拒绝")
    void idempotencyKeyCannotBeHijacked() {
        Player first = createPlayer("flow-key-owner");
        Player second = createPlayer("flow-key-thief");
        gameRunService.start(first.getId(), "shared-key");

        assertThatThrownBy(() -> gameRunService.start(second.getId(), "shared-key"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("幂等键");
    }

    @Test
    @DisplayName("有进行中的对局时再次开局会续玩，不会产生第二局")
    void activeRunIsReused() {
        Player player = createPlayer("flow-reuse");
        RunStateVO first = gameRunService.start(player.getId(), null);
        gameRunService.submitAnswer(player.getId(), first.runId(), 1, List.of("definitely-wrong"));

        RunStateVO second = gameRunService.start(player.getId(), null);

        assertThat(second.runId()).isEqualTo(first.runId());
        assertThat(attemptsOf(first.runId(), 1)).isEqualTo(1);
    }

    @Test
    @DisplayName("放弃对局后 current 为空，再开局是全新的一局")
    void abandonStartsFreshRun() {
        Player player = createPlayer("flow-abandon");
        RunStateVO run = gameRunService.start(player.getId(), null);
        Level level = level(1);
        gameRunService.submitAnswer(player.getId(), run.runId(), 1, level.getCorrectOptionKeys());
        gameRunService.submitQr(player.getId(), run.runId(), 1, qrToken(level));

        RunStateVO abandoned = gameRunService.abandon(player.getId(), run.runId());
        assertThat(abandoned.status()).isEqualTo("ABANDONED");
        assertThat(gameRunService.current(player.getId())).isNull();

        RunStateVO fresh = gameRunService.start(player.getId(), null);
        assertThat(fresh.runId()).isNotEqualTo(run.runId());
        assertThat(fresh.lastLevelNo()).isEqualTo(1);
        assertThat(fresh.totalScore()).isZero();
    }

    @Test
    @DisplayName("12 关全通：状态 COMPLETED、总分 240、用时由服务端计算，并写入排行榜")
    void completingAllLevelsMarksRunCompleted() {
        Player player = createPlayer("flow-complete");
        RunStateVO run = gameRunService.start(player.getId(), null);

        int total = levelCount();
        assertThat(total).isEqualTo(12);

        SettlementVO last = null;
        for (int levelNo = 1; levelNo <= total; levelNo++) {
            Level level = level(levelNo);
            AnswerResultVO answer = gameRunService.submitAnswer(
                    player.getId(), run.runId(), levelNo, level.getCorrectOptionKeys());
            assertThat(answer.correct()).as("第 %d 关答案", levelNo).isTrue();

            QrResultVO qr = gameRunService.submitQr(
                    player.getId(), run.runId(), levelNo, qrToken(level));
            last = qr.settlement();
            assertThat(last).as("第 %d 关结算", levelNo).isNotNull();
        }

        assertThat(last).isNotNull();
        assertThat(last.runCompleted()).isTrue();
        assertThat(last.nextLevelNo()).isNull();
        assertThat(last.totalScore()).isEqualTo(total * 20);
        assertThat(last.durationSeconds()).isNotNull().isGreaterThanOrEqualTo(0);

        RunStateVO finished = gameRunService.current(player.getId());
        assertThat(finished.status()).isEqualTo("COMPLETED");
        assertThat(finished.totalScore()).isEqualTo(240);
        assertThat(finished.lastLevelNo()).isEqualTo(total);

        List<LeaderboardEntryVO> board = leaderboardService.top(10);
        assertThat(board).isNotEmpty();
        assertThat(board.get(0).nickname()).isEqualTo("测试-flow-complete");
        assertThat(board.get(0).totalScore()).isEqualTo(240);
    }

    @Test
    @DisplayName("多选少选得部分分：只选 1/2 得 10 分，通过后不会补成满分")
    void partialSelectionAwardsPartialScore() {
        Player player = createPlayer("flow-partial");
        RunStateVO run = gameRunService.start(player.getId(), null);
        Level level1 = level(1);
        gameRunService.submitAnswer(player.getId(), run.runId(), 1, level1.getCorrectOptionKeys());
        gameRunService.submitQr(player.getId(), run.runId(), 1, qrToken(level1));

        Level level2 = level(2);
        assertThat(level2.getCorrectOptionKeys()).hasSize(2);

        AnswerResultVO partial = gameRunService.submitAnswer(
                player.getId(), run.runId(), 2, List.of(level2.getCorrectOptionKeys().get(0)));

        assertThat(partial.correct()).isTrue();
        assertThat(partial.score()).isEqualTo(10);
        assertThat(partial.questionPassed()).isTrue();
        assertThat(partial.nextAction()).isEqualTo("SCAN_QR");

        // 本题已通过，再提交全部正确答案也不会把 10 分补成 20：少选的分就是少拿了
        AnswerResultVO retry = gameRunService.submitAnswer(
                player.getId(), run.runId(), 2, level2.getCorrectOptionKeys());
        assertThat(retry.score()).isEqualTo(10);
        assertThat(attemptsOf(run.runId(), 2)).isEqualTo(1);

        QrResultVO qr = gameRunService.submitQr(
                player.getId(), run.runId(), 2, qrToken(level2));
        assertThat(qr.settlement()).isNotNull();
        assertThat(qr.settlement().levelScore()).isEqualTo(10);
        assertThat(qr.settlement().totalScore()).isEqualTo(30);
    }

    @Test
    @DisplayName("多选选到错误选项：本题 0 分且不通过，但不倒扣已得分数")
    void wrongOptionGetsZeroWithoutDeduction() {
        Player player = createPlayer("flow-wrong-option");
        RunStateVO run = gameRunService.start(player.getId(), null);
        Level level1 = level(1);
        gameRunService.submitAnswer(player.getId(), run.runId(), 1, level1.getCorrectOptionKeys());
        gameRunService.submitQr(player.getId(), run.runId(), 1, qrToken(level1));

        Level level2 = level(2);
        AnswerResultVO mixed = gameRunService.submitAnswer(
                player.getId(), run.runId(), 2,
                List.of(level2.getCorrectOptionKeys().get(0), "definitely-wrong"));

        assertThat(mixed.correct()).isFalse();
        assertThat(mixed.score()).isZero();
        assertThat(mixed.questionPassed()).isFalse();
        assertThat(mixed.nextAction()).isEqualTo("RETRY");

        RunStateVO current = gameRunService.current(player.getId());
        assertThat(current.totalScore()).isEqualTo(20);
        assertThat(current.lastLevelNo()).isEqualTo(2);

        // 错选不影响重试：本题重试时全部选对仍然拿 20
        AnswerResultVO retry = gameRunService.submitAnswer(
                player.getId(), run.runId(), 2, level2.getCorrectOptionKeys());
        assertThat(retry.score()).isEqualTo(20);
        assertThat(retry.questionPassed()).isTrue();
    }

    /** 按生产逻辑签发一枚有效期 1 天的关卡二维码。 */
    private String qrToken(Level level) {
        return qrTokenService.issue(level.getLevelNo(), level.getQrAnswer(), Duration.ofDays(1));
    }
}
