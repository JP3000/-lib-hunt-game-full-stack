#!/usr/bin/env bash
# 生成本地压测所需的账号 / token / 进行中对局，输出 players.csv（token,runId,studentNo）
#
# 前置：后端已启动（默认 8081），MySQL 容器名为 libhunt-mysql。
# 用法：./perf/setup-load-test.sh
set -euo pipefail

BASE="${BASE:-http://127.0.0.1:8081}"
COUNT="${COUNT:-20}"
PASSWORD="${PASSWORD:-LoadTest@123}"
ADMIN_NO="${ADMIN_NO:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@123456}"
MYSQL_CONTAINER="${MYSQL_CONTAINER:-libhunt-mysql}"
OUT="${OUT:-$(cd "$(dirname "$0")" && pwd)/players.csv}"

field() { node -pe "JSON.parse(require('fs').readFileSync(0,'utf8'))$1" ; }

login() {
  curl -sS -X POST "$BASE/api/auth/login" \
    -H 'Content-Type: application/json' \
    -H "X-Forwarded-For: $3" \
    -d "{\"studentNo\":\"$1\",\"password\":\"$2\"}"
}

echo "==> 清理上一次的压测账号"
docker exec "$MYSQL_CONTAINER" mysql -uroot -proot -D libhunt -e "
  DELETE la FROM level_attempt la JOIN game_run gr ON la.run_id = gr.id
    JOIN player p ON gr.player_id = p.id WHERE p.student_no LIKE 'lt-%';
  DELETE pi FROM player_item pi JOIN player p ON pi.player_id = p.id
    WHERE p.student_no LIKE 'lt-%';
  DELETE gr FROM game_run gr JOIN player p ON gr.player_id = p.id
    WHERE p.student_no LIKE 'lt-%';
  DELETE FROM player WHERE student_no LIKE 'lt-%';
" 2>/dev/null

echo "==> 管理员登录"
ADMIN_TOKEN="$(login "$ADMIN_NO" "$ADMIN_PASSWORD" 10.99.255.1 | field '.data.token')"
[ -n "$ADMIN_TOKEN" ] || { echo "管理员登录失败"; exit 1; }

echo "token,runId,studentNo" > "$OUT"
for i in $(seq -w 1 "$COUNT"); do
  NO="lt-$i"
  curl -sS -o /dev/null -X POST "$BASE/api/admin/players" \
    -H "Authorization: Bearer $ADMIN_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"studentNo\":\"$NO\",\"nickname\":\"压测 $i\",\"initialPassword\":\"$PASSWORD\",\"role\":\"STUDENT\"}"

  # 每个账号用不同的 X-Forwarded-For，绕过「同 IP 10 次/分钟」的登录限流
  IP="10.99.$((10#$i / 250 % 200)).$((10#$i % 250 + 1))"
  TOKEN="$(login "$NO" "$PASSWORD" "$IP" | field '.data.token')"
  RUN_ID="$(curl -sS -X POST "$BASE/api/runs" \
    -H "Authorization: Bearer $TOKEN" \
    -H "Idempotency-Key: load-$NO" | field '.data.runId')"

  [ -n "$TOKEN" ] && [ -n "$RUN_ID" ] || { echo "$NO 准备失败"; exit 1; }
  echo "$TOKEN,$RUN_ID,$NO" >> "$OUT"
done

echo "==> 完成：$COUNT 个玩家，数据写入 $OUT"
