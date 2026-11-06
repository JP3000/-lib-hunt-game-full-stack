package com.libhunt.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.libhunt.common.context.UserContext;
import com.libhunt.common.exception.BusinessException;
import com.libhunt.dto.CreatePlayerRequest;
import com.libhunt.dto.PageVO;
import com.libhunt.dto.PlayerVO;
import com.libhunt.entity.Player;
import com.libhunt.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PlayerAdminService {

    private final PlayerMapper playerMapper;
    private final PasswordEncoder passwordEncoder;

    public PageVO<PlayerVO> page(String keyword, long pageNo, long size) {
        Page<Player> page = new Page<>(Math.max(1, pageNo), Math.min(100, Math.max(1, size)));
        LambdaQueryWrapper<Player> query = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            String k = keyword.trim();
            query.and(w -> w.like(Player::getStudentNo, k).or().like(Player::getNickname, k));
        }
        query.orderByAsc(Player::getId);
        return PageVO.from(playerMapper.selectPage(page, query), PlayerVO::from);
    }

    @Transactional
    public PlayerVO create(CreatePlayerRequest request) {
        String studentNo = request.studentNo().trim();
        Player player = new Player();
        player.setStudentNo(studentNo);
        player.setPasswordHash(passwordEncoder.encode(request.initialPassword()));
        player.setRole(request.role() == null ? "STUDENT" : request.role());
        player.setNickname(request.nickname() == null ? studentNo : request.nickname().trim());
        player.setStatus(1);

        try {
            playerMapper.insert(player);
        } catch (DuplicateKeyException e) {
            // 唯一索引是并发下的最终裁判；这里只负责翻译成业务提示
            throw new BusinessException("账号已存在：" + studentNo);
        }
        return PlayerVO.from(player);
    }

    public void updateStatus(Long playerId, Integer status) {
        if (playerId.equals(UserContext.getPlayerId()) && status == 0) {
            throw new BusinessException("不能禁用当前登录的账号");
        }
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException("账号不存在");
        }
        player.setStatus(status);
        playerMapper.updateById(player);
    }

    public void resetPassword(Long playerId, String rawPassword) {
        Player player = playerMapper.selectById(playerId);
        if (player == null) {
            throw new BusinessException("账号不存在");
        }
        player.setPasswordHash(passwordEncoder.encode(rawPassword));
        playerMapper.updateById(player);
    }
}
