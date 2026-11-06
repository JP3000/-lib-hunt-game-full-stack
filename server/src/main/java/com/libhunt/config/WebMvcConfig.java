package com.libhunt.config;

import com.libhunt.interceptor.JwtInterceptor;
import com.libhunt.interceptor.LoginRateLimitInterceptor;
import com.libhunt.interceptor.RoleInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final RoleInterceptor roleInterceptor;
    private final LoginRateLimitInterceptor loginRateLimitInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // 最先执行：限流不需要知道「你是谁」
        registry.addInterceptor(loginRateLimitInterceptor)
                .order(-1)
                .addPathPatterns("/api/auth/login");

        registry.addInterceptor(jwtInterceptor)
                .order(0)
                .addPathPatterns("/api/**")
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/health",
                        // 关卡内容是公开的（登录前也要展示），
                        // 但 DTO 已剔除 qrAnswer / correctOptionKeys，不会泄题。
                        // 判题、进度、排行榜等接口仍然必须认证。
                        "/api/levels",
                        "/api/levels/**",
                        // 排行榜是公开榜单，只暴露昵称与成绩
                        "/api/leaderboard"
                );

        registry.addInterceptor(roleInterceptor)
                .order(1)
                .addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // 生产环境走 Nginx 同域反代，不需要 CORS；
        // 这里只为本地开发时 Next.js (localhost:3000) 直连 8080 服务。
        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:3000", "http://127.0.0.1:3000")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
