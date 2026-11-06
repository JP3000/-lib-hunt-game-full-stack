package com.libhunt;

import com.libhunt.common.GameRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("计分规则")
class GameRulesTest {

    @Test
    @DisplayName("首次答对得满分 100")
    void firstAttemptGetsFullScore() {
        assertThat(GameRules.scoreFor(1)).isEqualTo(100);
    }

    @Test
    @DisplayName("每错一次扣 20 分")
    void everyWrongAttemptCostsTwenty() {
        assertThat(GameRules.scoreFor(2)).isEqualTo(80);
        assertThat(GameRules.scoreFor(3)).isEqualTo(60);
        assertThat(GameRules.scoreFor(4)).isEqualTo(40);
        assertThat(GameRules.scoreFor(5)).isEqualTo(20);
    }

    @Test
    @DisplayName("最低 20 分：反复试错也有收益")
    void scoreHasFloorOfTwenty() {
        assertThat(GameRules.scoreFor(6)).isEqualTo(20);
        assertThat(GameRules.scoreFor(100)).isEqualTo(20);
    }
}
