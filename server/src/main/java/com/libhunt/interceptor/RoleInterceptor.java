package com.libhunt.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libhunt.common.Result;
import com.libhunt.common.annotation.RequireRole;
import com.libhunt.common.context.UserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * 在 {@link JwtInterceptor} 之后执行：JWT 负责「你是谁」，这里负责「你能不能」。
 */
@Component
@RequiredArgsConstructor
public class RoleInterceptor implements HandlerInterceptor {

    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole required = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (required == null) {
            required = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (required == null) {
            return true;
        }

        String role = UserContext.getRole();
        if (role != null && Arrays.asList(required.value()).contains(role)) {
            return true;
        }

        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(),
                Result.fail(HttpStatus.FORBIDDEN.value(), "无权限访问"));
        return false;
    }
}
