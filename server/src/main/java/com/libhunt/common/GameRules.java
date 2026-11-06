package com.libhunt.common;

/**
 * 计分规则集中在一处，方便调参与单测。
 * <p>
 * 每关满分 100；答错一次扣 20；最低 20 分。
 * 这样「答对」始终有收益，但反复试错的代价可量化。
 */
public final class GameRules {

    public static final int LEVEL_BASE_SCORE = 100;
    public static final int WRONG_ATTEMPT_PENALTY = 20;
    public static final int MIN_LEVEL_SCORE = 20;

    private GameRules() {
    }

    /**
     * @param attempts 包含答对那次在内的总尝试次数（从 1 开始）
     */
    public static int scoreFor(int attempts) {
        int used = Math.max(1, attempts) - 1;
        return Math.max(MIN_LEVEL_SCORE, LEVEL_BASE_SCORE - WRONG_ATTEMPT_PENALTY * used);
    }
}
