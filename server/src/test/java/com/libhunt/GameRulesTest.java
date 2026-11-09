package com.libhunt;

import com.libhunt.common.GameRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("计分规则")
class GameRulesTest {

    @Test
    @DisplayName("单选全对得 20 分")
    void singleCorrectAnswerGetsTwenty() {
        assertThat(GameRules.scoreFor(1, 1)).isEqualTo(20);
    }

    @Test
    @DisplayName("多选全对得 20 分")
    void allCorrectOptionsGetTwenty() {
        assertThat(GameRules.scoreFor(2, 2)).isEqualTo(20);
        assertThat(GameRules.scoreFor(6, 6)).isEqualTo(20);
    }

    @Test
    @DisplayName("多选少选按正确选项比例给分")
    void partialSelectionGetsPartialScore() {
        assertThat(GameRules.scoreFor(1, 2)).isEqualTo(10);
        assertThat(GameRules.scoreFor(1, 3)).isEqualTo(7);
        assertThat(GameRules.scoreFor(2, 3)).isEqualTo(13);
        assertThat(GameRules.scoreFor(2, 6)).isEqualTo(7);
        assertThat(GameRules.scoreFor(5, 6)).isEqualTo(17);
    }

    @Test
    @DisplayName("一个正确选项都没选得 0 分")
    void noCorrectOptionGetsZero() {
        assertThat(GameRules.scoreFor(0, 3)).isZero();
        assertThat(GameRules.scoreFor(0, 0)).isZero();
    }

    @Test
    @DisplayName("选到错误选项即为错选，本题不得分")
    void anyWrongOptionInvalidatesTheAnswer() {
        Set<String> correct = Set.of("b", "c");
        assertThat(GameRules.hasWrongOption(List.of("b", "c"), correct)).isFalse();
        assertThat(GameRules.hasWrongOption(List.of("b"), correct)).isFalse();
        assertThat(GameRules.hasWrongOption(List.of("b", "a"), correct)).isTrue();
        assertThat(GameRules.hasWrongOption(List.of("a"), correct)).isTrue();
        assertThat(GameRules.hasWrongOption(List.of(), correct)).isFalse();
    }
}
