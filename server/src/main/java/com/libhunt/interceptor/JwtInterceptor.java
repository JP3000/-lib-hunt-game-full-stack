package com.libhunt.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libhunt.common.Result;
import com.libhunt.common.context.UserContext;
import com.libhunt.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class JwtInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        // 预检请求直接放行，否则浏览器 CORS 预检会被 401 拦掉
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            reject(response, "未登录或登录已过期");
            return false;
        }

        try {
            Claims claims = jwtUtil.parse(header.substring(7));
            UserContext.set(Long.valueOf(claims.getSubject()), claims.get("role", String.class));
            return true;
        } catch (Exception e) {
            // 签名错误、过期、格式不对，统一按未认证处理
            reject(response, "未登录或登录已过期");
            return false;
        }
    }

    /**
     * 401 也返回统一的 JSON 结构，前端不用为「HTTP 错误」单独写一套分支。
     */
    private void reject(HttpServletResponse response, String msg) throws Exception {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), Result.fail(HttpStatus.UNAUTHORIZED.value(), msg));
    }

    /**
     * 无论请求成功还是抛异常，都必须清理 ThreadLocal。
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
