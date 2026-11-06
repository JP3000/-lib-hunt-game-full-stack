package com.libhunt.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityBeans {

    /**
     * 只引入 spring-security-crypto，不上整套 Spring Security。
     * 认证流程由 JWT 拦截器完成，BCrypt 只负责密码哈希。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
