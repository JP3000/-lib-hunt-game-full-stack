package com.libhunt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.libhunt.dto.LeaderboardRow;
import com.libhunt.entity.GameRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface GameRunMapper extends BaseMapper<GameRun> {

    /**
     * 行锁：把同一局的所有答题/扫码请求串行化。
     * 必须在事务内调用，锁在事务提交时释放。
     * <p>
     * 唯一索引 {@code uk_attempt_run_level} 仍然是最后一道保险——
     * 锁负责顺序，索引负责正确性，两者不互相替代。
     */
    @Select("SELECT * FROM game_run WHERE id = #{id} FOR UPDATE")
    GameRun selectByIdForUpdate(@Param("id") Long id);

    /**
     * 每位玩家只取最好的一局：先按玩家分组排名，再排序取前 N。
     * <p>
     * 排序规则与复合索引 {@code idx_run_leaderboard} 一致：分数降序、用时升序。
     * Redis 不可用时由这条 SQL 兜底。
     */
    @Select("""
            SELECT t.id               AS runId,
                   t.player_id        AS playerId,
                   t.total_score      AS totalScore,
                   t.duration_seconds AS durationSeconds,
                   p.nickname         AS nickname
            FROM (SELECT r.id,
                         r.player_id,
                         r.total_score,
                         r.duration_seconds,
                         r.finished_at,
                         ROW_NUMBER() OVER (
                             PARTITION BY r.player_id
                             ORDER BY r.total_score DESC, r.duration_seconds ASC, r.finished_at ASC
                         ) AS rn
                  FROM game_run r
                  WHERE r.status = 'COMPLETED') t
                     JOIN player p ON p.id = t.player_id
            WHERE t.rn = 1
            ORDER BY t.total_score DESC, t.duration_seconds ASC, t.finished_at ASC
            LIMIT #{limit}
            """)
    List<LeaderboardRow> selectTopBestPerPlayer(@Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM game_run WHERE status = 'COMPLETED'")
    long countCompleted();

    @Select("SELECT COALESCE(AVG(total_score), 0) FROM game_run WHERE status = 'COMPLETED'")
    double avgCompletedScore();

    @Select("SELECT COALESCE(AVG(duration_seconds), 0) FROM game_run WHERE status = 'COMPLETED'")
    double avgCompletedDuration();
}
