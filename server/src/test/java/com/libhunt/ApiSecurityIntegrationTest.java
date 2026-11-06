package com.libhunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libhunt.entity.Player;
import com.libhunt.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 走真实 HTTP 栈（MockMvc + 拦截器）验证认证与 RBAC：
 * 401 / 403 必须返回统一 JSON，而不是空白响应。
 */
@DisplayName("HTTP 接口与权限边界")
class ApiSecurityIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${libhunt.admin.initial-password}")
    private String adminPassword;

    @Test
    @DisplayName("未登录访问受保护接口 → HTTP 401 + JSON 错误体")
    void unauthenticatedRequestReturns401() throws Exception {
        mockMvc.perform(get("/api/runs/current"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.msg").value("未登录或登录已过期"));
    }

    @Test
    @DisplayName("伪造的 token 同样按未登录处理")
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/runs/current")
                        .header("Authorization", "Bearer not-a-real-jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("学生访问管理端统计 → HTTP 403")
    void studentCannotAccessAdminApi() throws Exception {
        createPlayer("api-student");
        String token = login("api-student", "password123");

        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("无权限访问"));
    }

    @Test
    @DisplayName("管理员可以访问管理端统计")
    void adminCanAccessStats() throws Exception {
        String token = login("admin", adminPassword);

        mockMvc.perform(get("/api/admin/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.playerCount").isNumber())
                .andExpect(jsonPath("$.data.totalCompletions").isNumber())
                .andExpect(jsonPath("$.data.avgScore").isNumber());
    }

    @Test
    @DisplayName("错误密码与不存在的账号返回同一句提示")
    void wrongPasswordReturnsGenericError() throws Exception {
        createPlayer("api-wrong-pass");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("api-wrong-pass", "not-the-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("账号或密码错误"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("no-such-user", "whatever")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("账号或密码错误"));
    }

    @Test
    @DisplayName("被禁用的账号不能登录")
    void disabledAccountCannotLogin() throws Exception {
        Player player = createPlayer("api-disabled");
        jdbc.update("UPDATE player SET status = 0 WHERE id = ?", player.getId());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody("api-disabled", "password123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("账号已被禁用，请联系管理员"));
    }

    @Test
    @DisplayName("关卡详情公开可读，但响应中没有任何答案字段")
    void levelDetailIsPublicAndHasNoAnswers() throws Exception {
        String body = mockMvc.perform(get("/api/levels/1").param("locale", "zh-Hans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.level").value(1))
                .andExpect(jsonPath("$.data.options").isArray())
                .andExpect(jsonPath("$.data.multiple").isBoolean())
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContain("correctOptionKeys")
                .doesNotContain("qrAnswer")
                .doesNotContain("passwordHash");
    }

    @Test
    @DisplayName("登录成功后 /api/auth/me 能还原本人信息")
    void meReturnsCurrentPlayer() throws Exception {
        createPlayer("api-me");
        String token = login("api-me", "password123");

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentNo").value("api-me"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    private String login(String studentNo, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(studentNo, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(body).path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private String loginBody(String studentNo, String password) throws Exception {
        return objectMapper.writeValueAsString(
                Map.of("studentNo", studentNo, "password", password));
    }
}
