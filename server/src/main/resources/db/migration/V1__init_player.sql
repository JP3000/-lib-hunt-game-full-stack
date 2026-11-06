-- V1：账号表
--
-- 设计要点：
--   * student_no 唯一索引：重复导入 / 并发创建时由数据库兜底，而不是先查再插（TOCTOU）
--   * password_hash 存 BCrypt，绝不存明文
--   * role 取值：STUDENT / STAFF / ADMIN

CREATE TABLE player (
    id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    student_no    VARCHAR(32)  NOT NULL COMMENT '学号或职员用户名',
    password_hash VARCHAR(100) NOT NULL COMMENT 'BCrypt 密码哈希',
    role          VARCHAR(16)  NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT/STAFF/ADMIN',
    nickname      VARCHAR(64)           DEFAULT NULL COMMENT '昵称',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '1 正常 0 禁用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_player_student_no (student_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '玩家/管理员账号';
