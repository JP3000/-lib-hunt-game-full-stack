package com.libhunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libhunt.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录限流的两层验证：IP 维度（拦截器，HTTP 429）与账号维度（服务层，业务码）。
 */
@DisplayName("登录限流")
class LoginRateLimitIntegrationTest extends IntegrationTestBase {

    private static final int MAX_ATTEMPTS = 10;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("同一 IP 第 11 次登录尝试被 429 拦住")
    void blocksEleventhAttemptFromSameIp() throws Exception {
        createPlayer("rate-ip");

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            loginExpectBusinessError("rate-ip", "wrong-password", "203.0.113.7");
        }

        mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr("203.0.113.7"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("rate-ip", "wrong-password")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value(429))
                .andExpect(jsonPath("$.msg").value("登录尝试过于频繁，请稍后再试"));
    }

    @Test
    @DisplayName("不同 IP 有各自的令牌桶")
    void differentIpsHaveIndependentBuckets() throws Exception {
        createPlayer("rate-ip-2");
        createPlayer("rate-ip-3");

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            loginExpectBusinessError("rate-ip-2", "wrong-password", "203.0.113.8");
        }

        // 另一个 IP + 另一个账号：仍然走到「密码错误」而不是限流
        loginExpectBusinessError("rate-ip-3", "wrong-password", "198.51.100.10");
    }

    @Test
    @DisplayName("换 IP 也挡得住针对同一账号的撞库")
    void accountLimitAppliesAcrossIps() throws Exception {
        createPlayer("rate-account");

        for (int i = 0; i < MAX_ATTEMPTS; i++) {
            loginExpectBusinessError("rate-account", "wrong-password", "198.51.100." + (20 + i));
        }

        mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr("198.51.100.200"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("rate-account", "wrong-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("登录尝试过于频繁，请稍后再试"));
    }

    private void loginExpectBusinessError(String studentNo, String password, String ip) throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr(ip))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(studentNo, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("账号或密码错误"));
    }

    private String loginBody(String studentNo, String password) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("studentNo", studentNo, "password", password));
    }

    private RequestPostProcessor remoteAddr(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }
}
