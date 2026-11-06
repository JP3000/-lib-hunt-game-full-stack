-- V2：游戏核心表
--
-- 设计约定：
--   1. 多语文案用 JSON 列，键为 locale（zh-Hant / zh-Hans / en）。
--      语言固定三种、只读为主、不存在"按语言过滤"的查询，JSON 列最干净。
--   2. 不建物理外键。原因：外键在分库分表、批量导入、软删除场景下会变成障碍；
--      本项目用应用层校验 + 索引保证引用完整性，这也是多数互联网团队的默认做法。
--   3. 所有表统一 utf8mb4，时间字段由数据库默认值兜底，避免应用忘记赋值。

/* ============================================================
   关卡
   ============================================================ */
CREATE TABLE `level`
(
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    level_no            INT         NOT NULL COMMENT '关卡序号 1..12',
    title               JSON        NOT NULL COMMENT '标题，按 locale 键存储',
    story               JSON        NOT NULL COMMENT '剧情正文',
    completion_story    JSON                 DEFAULT NULL COMMENT '过关剧情',
    question            JSON        NOT NULL COMMENT '题目',
    correct_option_keys JSON        NOT NULL COMMENT '正确选项 key 数组，如 ["b"]',
    qr_answer           VARCHAR(128) NOT NULL COMMENT '二维码内容，仅服务端持有',
    qr_location         JSON                 DEFAULT NULL COMMENT '二维码所在位置',
    qr_location_note    JSON                 DEFAULT NULL COMMENT '位置说明',
    video_url           VARCHAR(255)         DEFAULT NULL,
    story_image_url     VARCHAR(255)         DEFAULT NULL,
    story_image_alt     JSON                 DEFAULT NULL,
    story_image_caption JSON                 DEFAULT NULL,
    status              TINYINT     NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_level_no (level_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '关卡主数据（含答案，不下发前端）';

/* ============================================================
   关卡选项
   ============================================================ */
CREATE TABLE level_option
(
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    level_id   BIGINT      NOT NULL,
    option_key VARCHAR(8)  NOT NULL COMMENT 'a / b / c / d',
    label      JSON        NOT NULL COMMENT '选项文案，按 locale 键存储',
    sort_order INT         NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_level_option (level_id, option_key),
    KEY idx_level_option_level (level_id, sort_order)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '关卡选项';

/* ============================================================
   道具
   ============================================================ */
CREATE TABLE item
(
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    item_key    VARCHAR(64)  NOT NULL COMMENT '稳定的业务键，如 item-01-note',
    name        JSON         NOT NULL,
    description JSON         NOT NULL,
    image_url   VARCHAR(255) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_key (item_key)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '剧情道具';

/* ============================================================
   关卡 ↔ 道具
   ============================================================ */
CREATE TABLE level_item
(
    level_id   BIGINT NOT NULL,
    item_id    BIGINT NOT NULL,
    sort_order INT    NOT NULL DEFAULT 0,
    PRIMARY KEY (level_id, item_id),
    KEY idx_level_item_item (item_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '关卡奖励道具关联';

/* ============================================================
   一局游戏
   ============================================================ */
CREATE TABLE game_run
(
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    player_id        BIGINT      NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'IN_PROGRESS' COMMENT 'IN_PROGRESS / COMPLETED / ABANDONED',
    started_at       DATETIME    NOT NULL COMMENT '服务端记录，客户端不可篡改',
    finished_at      DATETIME             DEFAULT NULL,
    total_score      INT         NOT NULL DEFAULT 0,
    duration_seconds INT                  DEFAULT NULL COMMENT '服务端计算 finished_at - started_at',
    last_level_no    INT         NOT NULL DEFAULT 1 COMMENT '已解锁到第几关',
    idempotency_key  VARCHAR(64)          DEFAULT NULL COMMENT '客户端幂等键，防重复开局',
    created_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_run_idempotency (idempotency_key),
    KEY idx_run_player (player_id, status),
    -- 排行榜主查询：WHERE status='COMPLETED' ORDER BY total_score DESC, duration_seconds ASC
    -- MySQL 8 支持降序索引，避免 filesort
    KEY idx_run_leaderboard (status, total_score DESC, duration_seconds ASC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '玩家的一局游戏';

/* ============================================================
   过关记录
   ============================================================ */
CREATE TABLE level_attempt
(
    id              BIGINT   NOT NULL AUTO_INCREMENT,
    run_id          BIGINT   NOT NULL,
    level_no        INT      NOT NULL,
    score           INT      NOT NULL DEFAULT 0,
    attempts        INT      NOT NULL DEFAULT 1 COMMENT '答题尝试次数',
    question_passed TINYINT  NOT NULL DEFAULT 0,
    qr_passed       TINYINT  NOT NULL DEFAULT 0,
    completed_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 幂等的核心：同一局同一关只允许一条记录。
    -- 并发重复提交时由存储引擎抛出 DuplicateKeyException，而不是"先查再插"。
    UNIQUE KEY uk_attempt_run_level (run_id, level_no),
    KEY idx_attempt_run (run_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '过关记录';

/* ============================================================
   玩家已收集道具
   ============================================================ */
CREATE TABLE player_item
(
    id           BIGINT   NOT NULL AUTO_INCREMENT,
    player_id    BIGINT   NOT NULL,
    item_id      BIGINT   NOT NULL,
    run_id       BIGINT            DEFAULT NULL COMMENT '在哪一局获得',
    collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_player_item (player_id, item_id),
    KEY idx_player_item_player (player_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT = '玩家道具收藏';
