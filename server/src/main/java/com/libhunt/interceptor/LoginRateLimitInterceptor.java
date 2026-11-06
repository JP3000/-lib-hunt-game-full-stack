package com.libhunt.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libhunt.common.Result;
import com.libhunt.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * 登录接口按 IP 限流：同一 IP 每分钟最多 10 次登录尝试。
 * <p>
 * 账号维度的限流在 {@link com.libhunt.service.AuthService} 里（防止换 IP 打同一个账号）；
 * 两层一起才既能挡住单机脚本，也能挡住分布式撞库。
 */
@Component
@RequiredArgsConstructor
public class LoginRateLimitInterceptor implements HandlerInterceptor {

    static final int MAX_ATTEMPTS = 10;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final RateLimiterService rateLimiterService;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        if (rateLimiterService.tryAcquire(
                "libhunt:ratelimit:login:ip:" + clientIp(request), MAX_ATTEMPTS, WINDOW)) {
            return true;
        }

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                Result.fail(HttpStatus.TOO_MANY_REQUESTS.value(), "登录尝试过于频繁，请稍后再试"));
        return false;
    }

    /**
     * 生产环境经 Nginx 反代，{@code remoteAddr} 永远是 127.0.0.1，必须看转发头。
     * 前提是 Nginx 覆盖 X-Forwarded-For，且后端端口不直接暴露公网。
     */
    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
