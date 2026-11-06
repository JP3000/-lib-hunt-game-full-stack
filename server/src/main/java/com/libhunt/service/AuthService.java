package com.libhunt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.libhunt.common.context.UserContext;
import com.libhunt.common.exception.BusinessException;
import com.libhunt.dto.LoginRequest;
import com.libhunt.dto.LoginVO;
import com.libhunt.dto.PlayerVO;
import com.libhunt.entity.Player;
import com.libhunt.mapper.PlayerMapper;
import com.libhunt.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

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

    public LoginVO login(LoginRequest request) {
        String studentNo = request.studentNo().trim();

        Player player = playerMapper.selectOne(
                new LambdaQueryWrapper<Player>()
                        .eq(Player::getStudentNo, studentNo)
                        .last("LIMIT 1"));

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

    public PlayerVO currentPlayer() {
        Player player = playerMapper.selectById(UserContext.getPlayerId());
        if (player == null) {
            throw new BusinessException("账号不存在");
        }
        return PlayerVO.from(player);
    }
}
