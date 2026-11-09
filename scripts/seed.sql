-- 纯 SQL 演示数据（MySQL 8）
--
-- 前提：
--   1. 已创建空库 libhunt
--   2. 后端至少启动过一次，Flyway 已建表，并且 admin 账号已自动创建
--
-- 执行：
--   mysql -h127.0.0.1 -P3306 -uroot -p libhunt < scripts/seed.sql
--
-- 说明：
--   * 创建 ab565601 ~ ab565610，密码直接复制 admin 的 BCrypt 哈希，
--     所以演示账号的密码与 admin 相同（默认 Admin@123456）
--   * 只写 player / game_run 两张表，不碰 Redis
--   * 排行榜在 Redis 里，执行完后需要让它重新回源 MySQL：
--       redis-cli DEL libhunt:leaderboard:global
--     或者用管理员 token 调 POST /api/admin/leaderboard/rebuild
--   * 可重复执行：会先删除 idempotency_key 以 seed-sql- 开头的旧对局
--
-- 执行完后（计分规则：每关 20 分，多选少选得部分分、错选不得分，满分 240）：
--   ab565601  240 分，420s   —— 榜首（全部选对）
--   ab565602  240 分，480s
--   ab565603  196 分，540s   —— 多选题少选，拿了部分分
--   ...
--   ab565610  164 分，960s   —— 与 ab565609 同分，按用时排序
--   ab565609 / ab565610 额外有一局进行中的对局（已解锁 3 关）
--   ab565608 额外有一局已放弃的对局

-- ---------------------------------------------------------------- 账号

INSERT INTO player (student_no, password_hash, role, nickname)
SELECT 'ab565601', password_hash, 'STUDENT', '林小舟' FROM player WHERE student_no = 'admin'
ON DUPLICATE KEY UPDATE nickname = '林小舟';

INSERT INTO player (student_no, password_hash, role, nickname)
SELECT 'ab565602', password_hash, 'STUDENT', '陈以宁' FROM player WHERE student_no = 'admin'
ON DUPLICATE KEY UPDATE nickname = '陈以宁';

INSERT INTO player (student_no, password_hash, role, nickname)
SELECT 'ab565603', password_hash, 'STUDENT', '黄嘉树' FROM player WHERE student_no = 'admin'
ON DUPLICATE KEY UPDATE nickname = '黄嘉树';

INSERT INTO player (student_no, password_hash, role, nickname)
SELECT 'ab565604', password_hash, 'STUDENT', '周若岚' FROM player WHERE student_no = 'admin'
ON DUPLICATE KEY UPDATE nickname = '周若岚';

INSERT INTO player (student_no, password_hash, role, nickname)
SELECT 'ab565605', password_hash, 'STUDENT', '何一川' FROM player WHERE student_no = 'admin'
ON DUPLICATE KEY UPDATE nickname = '何一川';

INSERT INTO player (student_no, password_hash, role, nickname)
SELECT 'ab565606', password_hash, 'STUDENT', '梁思远' FROM player WHERE student_no = 'admin'
ON DUPLICATE KEY UPDATE nickname = '梁思远';

INSERT INTO player (student_no, password_hash, role, nickname)
SELECT 'ab565607', password_hash, 'STUDENT', '苏晚晴' FROM player WHERE student_no = 'admin'
ON DUPLICATE KEY UPDATE nickname = '苏晚晴';

INSERT INTO player (student_no, password_hash, role, nickname)
SELECT 'ab565608', password_hash, 'STUDENT', '罗子谦' FROM player WHERE student_no = 'admin'
ON DUPLICATE KEY UPDATE nickname = '罗子谦';

INSERT INTO player (student_no, password_hash, role, nickname)
SELECT 'ab565609', password_hash, 'STUDENT', '许知微' FROM player WHERE student_no = 'admin'
ON DUPLICATE KEY UPDATE nickname = '许知微';

INSERT INTO player (student_no, password_hash, role, nickname)
SELECT 'ab565610', password_hash, 'STUDENT', '邓嘉禾' FROM player WHERE student_no = 'admin'
ON DUPLICATE KEY UPDATE nickname = '邓嘉禾';

-- ---------------------------------------------------------------- 已通关的对局

DELETE FROM game_run WHERE idempotency_key LIKE 'seed-sql-%';

INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 420 SECOND), NOW(), 240, 420, 12, 'seed-sql-ab565601'
FROM player WHERE student_no = 'ab565601';

INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 480 SECOND), NOW(), 240, 480, 12, 'seed-sql-ab565602'
FROM player WHERE student_no = 'ab565602';

INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 540 SECOND), NOW(), 196, 540, 12, 'seed-sql-ab565603'
FROM player WHERE student_no = 'ab565603';

INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 600 SECOND), NOW(), 192, 600, 12, 'seed-sql-ab565604'
FROM player WHERE student_no = 'ab565604';

INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 660 SECOND), NOW(), 188, 660, 12, 'seed-sql-ab565605'
FROM player WHERE student_no = 'ab565605';

INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 720 SECOND), NOW(), 179, 720, 12, 'seed-sql-ab565606'
FROM player WHERE student_no = 'ab565606';

INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 780 SECOND), NOW(), 175, 780, 12, 'seed-sql-ab565607'
FROM player WHERE student_no = 'ab565607';

INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 840 SECOND), NOW(), 172, 840, 12, 'seed-sql-ab565608'
FROM player WHERE student_no = 'ab565608';

INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 900 SECOND), NOW(), 164, 900, 12, 'seed-sql-ab565609'
FROM player WHERE student_no = 'ab565609';

INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'COMPLETED', DATE_SUB(NOW(), INTERVAL 960 SECOND), NOW(), 164, 960, 12, 'seed-sql-ab565610'
FROM player WHERE student_no = 'ab565610';

-- ---------------------------------------------------------------- 进行中 / 已放弃

-- ab565609、ab565610 各留一局进行中的对局，刷新地图能看到进度
INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'IN_PROGRESS', DATE_SUB(NOW(), INTERVAL 300 SECOND), NULL, 0, NULL, 3, 'seed-sql-ab565609-active'
FROM player WHERE student_no = 'ab565609';

INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'IN_PROGRESS', DATE_SUB(NOW(), INTERVAL 360 SECOND), NULL, 0, NULL, 3, 'seed-sql-ab565610-active'
FROM player WHERE student_no = 'ab565610';

-- ab565608 一局放弃的对局
INSERT INTO game_run
    (player_id, status, started_at, finished_at, total_score, duration_seconds, last_level_no, idempotency_key)
SELECT id, 'ABANDONED', DATE_SUB(NOW(), INTERVAL 200 SECOND), NOW(), 0, NULL, 2, 'seed-sql-ab565608-abandoned'
FROM player WHERE student_no = 'ab565608';

-- ---------------------------------------------------------------- 结果

SELECT p.student_no, p.nickname, r.total_score, r.duration_seconds, r.status
FROM game_run r
         JOIN player p ON p.id = r.player_id
WHERE r.idempotency_key LIKE 'seed-sql-%'
ORDER BY r.status, r.total_score DESC;
