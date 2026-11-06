package com.libhunt.common.context;

/**
 * 请求级用户上下文。
 * <p>
 * <b>注意：</b>由 {@link com.libhunt.interceptor.JwtInterceptor} 写入，
 * 必须在 {@code afterCompletion} 中调用 {@link #clear()}。
 * ThreadLocal 不清理会导致线程池复用时的「串号」问题——A 请求的身份被 B 请求读到。
 */
public final class UserContext {

    private static final ThreadLocal<Long> PLAYER_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> ROLE = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long playerId, String role) {
        PLAYER_ID.set(playerId);
        ROLE.set(role);
    }

    public static Long getPlayerId() {
        Long id = PLAYER_ID.get();
        if (id == null) {
            throw new IllegalStateException("当前请求未认证");
        }
        return id;
    }

    public static String getRole() {
        return ROLE.get();
    }

    public static void clear() {
        PLAYER_ID.remove();
        ROLE.remove();
    }
}
