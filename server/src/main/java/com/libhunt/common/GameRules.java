package com.libhunt.common;

import java.util.Collection;
import java.util.Set;

/**
 * 计分规则集中在一处，方便调参与单测。
 * <p>
 * 每关 20 分，按正确选项平分：
 * <ul>
 *   <li>全部选对：20 分；</li>
 *   <li>只选对一部分（少选）：按比例拿部分分，例如 2 个正确选项只选 1 个得 10 分；</li>
 *   <li>选到错误选项：本题 0 分，但不倒扣已有分数，可以继续尝试。</li>
 * </ul>
 * 12 关满分 240。因此并非所有玩家都能拿满分——少选会永久少拿那一部分分。
 * 尝试次数仍记录在 {@code level_attempt.attempts}，用于分析题目难度，不影响得分。
 */
public final class GameRules {

    public static final int LEVEL_BASE_SCORE = 20;

    private GameRules() {
    }

    /**
     * 按「少选得部分分」计算单题得分。
     *
     * @param selectedCorrectCount 本次选中的正确选项数（调用方需先保证没有选错）
     * @param totalCorrectCount    本题的正确选项总数
     */
    public static int scoreFor(int selectedCorrectCount, int totalCorrectCount) {
        if (selectedCorrectCount <= 0 || totalCorrectCount <= 0) {
            return 0;
        }
        if (selectedCorrectCount >= totalCorrectCount) {
            return LEVEL_BASE_SCORE;
        }
        return (int) Math.round((double) LEVEL_BASE_SCORE * selectedCorrectCount / totalCorrectCount);
    }

    /**
     * 多选题判定：只要提交里出现一个错误选项，就是「错选」，本题不得分。
     * 这是「少选得部分分、错选不得分」规则里最关键的一条，防止全选拿满分。
     */
    public static boolean hasWrongOption(Collection<String> submitted, Set<String> correctKeys) {
        return submitted.stream().anyMatch(key -> !correctKeys.contains(key));
    }
}
