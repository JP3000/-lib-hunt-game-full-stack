package com.libhunt.util;

import java.util.List;
import java.util.Map;

/**
 * 多语言取值工具。
 * <p>
 * 数据库里 title / story 等列存的是
 * {@code {"zh-Hant":"...","zh-Hans":"...","en":"..."}}。
 * 这里负责把「请求 locale」落到「实际可用语言」上。
 */
public final class LocaleSupport {

    public static final String DEFAULT_LOCALE = "zh-Hant";

    public static final List<String> SUPPORTED = List.of("zh-Hant", "zh-Hans", "en");

    private LocaleSupport() {
    }

    /**
     * 归一化请求参数：不认识的一律回落到默认语言。
     */
    public static String normalize(String locale) {
        if (locale == null || locale.isBlank()) {
            return DEFAULT_LOCALE;
        }
        String trimmed = locale.trim();
        if (SUPPORTED.contains(trimmed)) {
            return trimmed;
        }
        // 容错：前端可能传 zh / zh-CN / en-US
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("zh")) {
            return lower.contains("cn") || lower.contains("hans") || lower.contains("sg")
                    ? "zh-Hans"
                    : "zh-Hant";
        }
        if (lower.startsWith("en")) {
            return "en";
        }
        return DEFAULT_LOCALE;
    }

    /**
     * 从 i18n JSON 列取值：目标语言 → 默认语言 → 任意一个非空值 → 空串。
     */
    public static String pick(Map<String, String> localized, String locale) {
        if (localized == null || localized.isEmpty()) {
            return "";
        }
        String value = localized.get(locale);
        if (value != null && !value.isBlank()) {
            return value;
        }
        value = localized.get(DEFAULT_LOCALE);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return localized.values().stream()
                .filter(v -> v != null && !v.isBlank())
                .findFirst()
                .orElse("");
    }
}
