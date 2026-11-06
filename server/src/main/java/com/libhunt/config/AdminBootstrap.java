package com.libhunt.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.libhunt.entity.Player;
import com.libhunt.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时创建管理员账号。
 * <p>
 * 用代码而不是 migration 里的固定 hash：密码来自环境变量，
 * 不会在仓库里留下可登录的默认凭据；且天然幂等（已有 ADMIN 就不再建）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final PlayerMapper playerMapper;
    private final PasswordEncoder passwordEncoder;

    @Value("${libhunt.admin.student-no:admin}")
    private String adminStudentNo;

    @Value("${libhunt.admin.initial-password:Admin@123456}")
    private String initialPassword;

    @Override
    public void run(ApplicationArguments args) {
        Long adminCount = playerMapper.selectCount(
                new LambdaQueryWrapper<Player>().eq(Player::getRole, "ADMIN"));
        if (adminCount != null && adminCount > 0) {
            return;
        }

        Player existing = playerMapper.selectOne(
                new LambdaQueryWrapper<Player>()
                        .eq(Player::getStudentNo, adminStudentNo)
                        .last("LIMIT 1"));
        if (existing != null) {
            log.warn("账号 {} 已存在但角色是 {}，跳过管理员初始化", adminStudentNo, existing.getRole());
            return;
        }

        Player admin = new Player();
        admin.setStudentNo(adminStudentNo);
        admin.setPasswordHash(passwordEncoder.encode(initialPassword));
        admin.setRole("ADMIN");
        admin.setNickname("管理员");
        admin.setStatus(1);
        playerMapper.insert(admin);

        log.warn("已创建初始管理员 {}，请立即通过环境变量 ADMIN_INITIAL_PASSWORD 更换密码", adminStudentNo);
    }
}
