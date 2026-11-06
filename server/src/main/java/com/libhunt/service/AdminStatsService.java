package com.libhunt.service;

import com.libhunt.dto.AdminStatsVO;
import com.libhunt.mapper.GameRunMapper;
import com.libhunt.mapper.PlayerMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final PlayerMapper playerMapper;
    private final GameRunMapper gameRunMapper;

    public AdminStatsVO overview() {
        return new AdminStatsVO(
                playerMapper.selectCount(null),
                gameRunMapper.countCompleted(),
                round(gameRunMapper.avgCompletedScore()),
                round(gameRunMapper.avgCompletedDuration()));
    }

    private double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
