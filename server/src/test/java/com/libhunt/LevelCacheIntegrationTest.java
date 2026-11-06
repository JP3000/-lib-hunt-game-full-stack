package com.libhunt;

import com.libhunt.common.exception.BusinessException;
import com.libhunt.dto.LevelDetailVO;
import com.libhunt.dto.LevelSummaryVO;
import com.libhunt.service.LevelService;
import com.libhunt.support.IntegrationTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 关卡缓存的集成测试：真实 Redis，验证 key、TTL 与空值标记。
 */
@DisplayName("关卡缓存")
class LevelCacheIntegrationTest extends IntegrationTestBase {

    private static final String DETAIL_KEY = "libhunt:level:detail:zh-Hans:1";
    private static final String LIST_KEY = "libhunt:level:list:zh-Hans";

    @Autowired
    private LevelService levelService;

    @BeforeEach
    void clearLevelCache() {
        Set<String> keys = redis.keys("libhunt:level:*");
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    @Test
    @DisplayName("详情首次读库，之后命中缓存，TTL 带随机抖动")
    void detailIsCached() {
        LevelDetailVO first = levelService.detail(1, "zh-Hans");
        LevelDetailVO second = levelService.detail(1, "zh-Hans");

        assertThat(second).isEqualTo(first);
        assertThat(redis.opsForValue().get(DETAIL_KEY)).isNotBlank();

        Long ttl = redis.getExpire(DETAIL_KEY, TimeUnit.SECONDS);
        assertThat(ttl)
                .as("基础 30 分钟 + 最多 5 分钟抖动")
                .isBetween(29 * 60L, 35 * 60L);
    }

    @Test
    @DisplayName("缓存命中后即使库里改了内容，仍返回缓存值（证明不是每次都查库）")
    void cachedValueWinsOverDatabase() {
        LevelDetailVO first = levelService.detail(1, "zh-Hans");

        jdbc.update("UPDATE level SET title = JSON_SET(title, '$.\"zh-Hans\"', 'HACKED') WHERE level_no = 1");
        try {
            LevelDetailVO cached = levelService.detail(1, "zh-Hans");
            assertThat(cached.title()).isEqualTo(first.title()).isNotEqualTo("HACKED");
        } finally {
            jdbc.update("UPDATE level SET title = JSON_SET(title, '$.\"zh-Hans\"', ?) WHERE level_no = 1",
                    first.title());
        }
    }

    @Test
    @DisplayName("不存在的关卡缓存空值标记，挡住缓存穿透")
    void missingLevelIsCachedAsNullMarker() {
        String key = "libhunt:level:detail:zh-Hans:999";

        assertThatThrownBy(() -> levelService.detail(999, "zh-Hans"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("999");

        assertThat(redis.opsForValue().get(key)).isEqualTo("__NULL__");
        Long ttl = redis.getExpire(key, TimeUnit.SECONDS);
        assertThat(ttl).isPositive().isLessThanOrEqualTo(60);

        // 第二次仍然报错，但不会再查库（空值命中）
        assertThatThrownBy(() -> levelService.detail(999, "zh-Hans"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("关卡列表按语言分别缓存")
    void listIsCachedPerLocale() {
        List<LevelSummaryVO> list = levelService.list("zh-Hans");
        assertThat(list).hasSize(12);
        assertThat(redis.opsForValue().get(LIST_KEY)).isNotBlank();

        levelService.list("zh-Hant");
        assertThat(redis.opsForValue().get("libhunt:level:list:zh-Hant")).isNotBlank();
    }
}
