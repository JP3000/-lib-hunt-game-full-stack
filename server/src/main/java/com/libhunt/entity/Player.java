package com.libhunt.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("player")
public class Player {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String studentNo;

    /** BCrypt 哈希，永远不要返回给客户端 */
    private String passwordHash;

    /** STUDENT / STAFF / ADMIN */
    private String role;

    private String nickname;

    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
