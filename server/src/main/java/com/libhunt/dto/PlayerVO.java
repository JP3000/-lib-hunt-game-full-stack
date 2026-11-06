package com.libhunt.dto;

import com.libhunt.entity.Player;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 对外可见的账号信息。<b>不含 passwordHash。</b>
 */
@Schema(description = "账号信息")
public record PlayerVO(
        Long id,
        String studentNo,
        String nickname,
        String role,
        @Schema(description = "1 正常 0 禁用")
        Integer status
) {

    public static PlayerVO from(Player player) {
        return new PlayerVO(
                player.getId(),
                player.getStudentNo(),
                player.getNickname(),
                player.getRole(),
                player.getStatus());
    }
}
