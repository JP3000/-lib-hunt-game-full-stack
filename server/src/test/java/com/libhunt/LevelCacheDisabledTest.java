package com.libhunt;

import com.libhunt.dto.LevelDetailVO;
import com.libhunt.service.LevelService;
import com.libhunt.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存总开关：关闭后每次读都回源 MySQL，也不写 Redis。
 * 压测里的「无缓存」基线就是走的这条路径。
 */
@DisplayName("缓存开关")
@TestPropertySource(properties = "libhunt.cache.enabled=false")
class LevelCacheDisabledTest extends IntegrationTestBase {

    private static final String DETAIL_KEY = "libhunt:level:detail:zh-Hant:1";
    private static final String LIST_KEY = "libhunt:level:list:zh-Hant";

    @Autowired
    private LevelService levelService;

    @Test
    @DisplayName("详情与列表都直接读库，Redis 里不会出现缓存 key")
    void readsDatabaseWithoutWritingCache() {
        redis.delete(List.of(DETAIL_KEY, LIST_KEY));

        LevelDetailVO detail = levelService.detail(1, "zh-Hant");

        assertThat(detail.title()).isNotBlank();
        assertThat(detail.options()).isNotEmpty();
        assertThat(levelService.list("zh-Hant")).isNotEmpty();
        assertThat(redis.hasKey(DETAIL_KEY)).isFalse();
        assertThat(redis.hasKey(LIST_KEY)).isFalse();
    }
}
