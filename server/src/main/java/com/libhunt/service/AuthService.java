package com.libhunt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.libhunt.common.context.UserContext;
import com.libhunt.common.exception.BusinessException;
import com.libhunt.dto.LoginRequest;
import com.libhunt.dto.LoginVO;
import com.libhunt.dto.PlayerVO;
import com.libhunt.dto.StudentLoginRequest;
import com.libhunt.entity.Player;
import com.libhunt.mapper.PlayerMapper;
import com.libhunt.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    /**
     * 账号不存在时也跑一次 BCrypt 比对，让「账号不存在」和「密码错误」
     * 的响应时间接近，避免通过耗时差异枚举学号。
     */
    private static final String DUMMY_HASH =
            "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private final PlayerMapper playerMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RateLimiterService rateLimiterService;

    public LoginVO login(LoginRequest request) {
        String studentNo = request.studentNo().trim();

        // 账号维度限流：换 IP 也挡得住针对同一账号的撞库
        if (!rateLimiterService.tryAcquire(
                "libhunt:ratelimit:login:account:" + studentNo, 10, Duration.ofMinutes(1))) {
            throw new BusinessException("登录尝试过于频繁，请稍后再试");
        }

        Player player = findByStudentNo(studentNo);

        if (player == null) {
            passwordEncoder.matches(request.password(), DUMMY_HASH);
            throw new BusinessException("账号或密码错误");
        }
        if (!passwordEncoder.matches(request.password(), player.getPasswordHash())) {
            throw new BusinessException("账号或密码错误");
        }
        if (!Integer.valueOf(1).equals(player.getStatus())) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        String token = jwtUtil.issue(player.getId(), player.getRole());
        return new LoginVO(token, jwtUtil.expireSeconds(), PlayerVO.from(player));
    }

    /**
     * 学生端学号直登：第一次输入某个学号时自动创建学生账号。
     * <p>
     * 只允许 STUDENT 角色走这条路径；管理员/职员仍然必须用密码登录，
     * 否则知道管理员账号的人就能直接拿到管理权限。
     */
    public LoginVO studentLogin(StudentLoginRequest request) {
        String studentNo = request.studentNo().trim();

        if (!rateLimiterService.tryAcquire(
                "libhunt:ratelimit:login:account:" + studentNo, 10, Duration.ofMinutes(1))) {
            throw new BusinessException("登录尝试过于频繁，请稍后再试");
        }

        Player player = findByStudentNo(studentNo);
        if (player == null) {
            player = createStudent(studentNo);
        }
        if (!"STUDENT".equals(player.getRole())) {
            throw new BusinessException("管理员请使用账号密码登录");
        }
        if (!Integer.valueOf(1).equals(player.getStatus())) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        String token = jwtUtil.issue(player.getId(), player.getRole());
        return new LoginVO(token, jwtUtil.expireSeconds(), PlayerVO.from(player));
    }

    private Player findByStudentNo(String studentNo) {
        return playerMapper.selectOne(
                new LambdaQueryWrapper<Player>()
                        .eq(Player::getStudentNo, studentNo)
                        .last("LIMIT 1"));
    }

    private Player createStudent(String studentNo) {
        Player player = new Player();
        player.setStudentNo(studentNo);
        // 学号直登不提供密码入口；这里写一个随机哈希，保证列非空且无法被猜到
        player.setPasswordHash(passwordEncoder.encode(UUID.randomUUID().toString()));
        player.setRole("STUDENT");
        player.setNickname(studentNo);
        player.setStatus(1);

        try {
            playerMapper.insert(player);
            return player;
        } catch (DuplicateKeyException e) {
            // 并发首登：唯一索引兜底，另一个请求已经把账号建好了
            Player existing = findByStudentNo(studentNo);
            if (existing == null) {
                throw e;
            }
            return existing;
        }
    }

    public PlayerVO currentPlayer() {
        Player player = playerMapper.selectById(UserContext.getPlayerId());
        if (player == null) {
            throw new BusinessException("账号不存在");
        }
        return PlayerVO.from(player);
    }
}
