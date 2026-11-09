package com.libhunt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libhunt.entity.Player;
import com.libhunt.support.IntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 学生端「只输学号」登录：首次自动建号，管理员/禁用账号仍被拦住。
 */
@DisplayName("学生学号直登")
class StudentLoginIntegrationTest extends IntegrationTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("首次输入学号自动创建学生账号并返回 token")
    void createsStudentOnFirstLogin() throws Exception {
        login("s2026001")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.player.studentNo").value("s2026001"))
                .andExpect(jsonPath("$.data.player.role").value("STUDENT"));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM player WHERE student_no = ?", Integer.class, "s2026001");
        assertThat(count).isEqualTo(1);

        String passwordHash = jdbc.queryForObject(
                "SELECT password_hash FROM player WHERE student_no = ?", String.class, "s2026001");
        assertThat(passwordHash).isNotBlank();
    }

    @Test
    @DisplayName("已有学生账号直接登录，不会重复创建")
    void reusesExistingStudent() throws Exception {
        createPlayer("s2026002");

        login("s2026002")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM player WHERE student_no = ?", Integer.class, "s2026002");
        assertThat(count).isEqualTo(1);
    }

    @Test
    @DisplayName("管理员账号不能用学号直登")
    void rejectsAdminAccount() throws Exception {
        createPlayer("admin-2", "ADMIN");

        login("admin-2")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("管理员请使用账号密码登录"));
    }

    @Test
    @DisplayName("被禁用的学生账号不能登录")
    void rejectsDisabledStudent() throws Exception {
        Player player = createPlayer("s2026003");
        player.setStatus(0);
        playerMapper.updateById(player);

        login("s2026003")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("账号已被禁用，请联系管理员"));
    }

    @Test
    @DisplayName("学号含非法字符时返回参数校验错误")
    void rejectsInvalidStudentNo() throws Exception {
        login("bad id!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(1))
                .andExpect(jsonPath("$.msg").value("学号只能包含字母、数字、点、下划线和连字符"));
    }

    private ResultActions login(String studentNo) throws Exception {
        return mockMvc.perform(post("/api/auth/student-login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("studentNo", studentNo))));
    }
}
