#!/usr/bin/env bash
#
# 生成本地演示数据（只应该对本地开发环境使用）。
#
# 会创建：
#   - 10 个学生账号 ab565601 .. ab565610（符合「2 个字母 + 6 个数字」的学号规则，
#     可直接学号直登；创建时也写入密码 Demo@123456）
#   - 每人一局完整的通关记录，排行榜前 10 名不会空着
#   - 计分规则：每关 20 分，多选按正确选项平分；少选得部分分，错选不得分
#     脚本让不同玩家在多选题里「少选」不同比例，因此总分自然拉开（不是人人满分）
#     同时用不同的停留时间模拟不同的通关用时
#   - ab565608 额外一局已放弃的对局
#   - ab565609 / ab565610 额外一局进行中的对局（已过 3 关，方便看进度恢复）
#
# 前提：
#   - 后端已经启动：默认 http://127.0.0.1:8080
#   - MySQL 和 Redis 可用
#   - 管理员账号可登录：默认 admin / Admin@123456
#   - 本机有 curl、jq
#
# 用法：
#   ./scripts/seed-demo.sh
#
# 可重复执行：
#   - 学生账号已存在会复用
#   - 会先放弃演示账号当前进行中的对局，再重新造数据
#   - 已完成的旧对局会保留，因此重复执行会增加通关记录和统计数量
#
# 注意：脚本会通过真实接口跑完 12 关，所以用的是 GameRunService 的真实判题、
#       结算和排行榜写入逻辑，不是直接 INSERT 数据库。

set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
ADMIN_STUDENT_NO="${ADMIN_STUDENT_NO:-admin}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-Admin@123456}"
DEMO_PASSWORD="${DEMO_PASSWORD:-Demo@123456}"
PLAYER_COUNT="${PLAYER_COUNT:-10}"
LEVEL_DELAY="${LEVEL_DELAY:-0}"

command -v curl >/dev/null 2>&1 || { echo "需要 curl" >&2; exit 1; }
command -v jq >/dev/null 2>&1 || { echo "需要 jq（brew install jq）" >&2; exit 1; }

die() {
    echo "❌ $*" >&2
    exit 1
}

request() {
    local method="$1" path="$2" token="${3:-}" data="${4:-}" idem="${5:-}" xff="${6:-}"
    local args=(-sS -X "$method" "$BASE_URL$path")
    [[ -n "$token" ]] && args+=(-H "Authorization: Bearer $token")
    [[ -n "$data" ]] && args+=(-H "Content-Type: application/json" -d "$data")
    [[ -n "$idem" ]] && args+=(-H "Idempotency-Key: $idem")
    [[ -n "$xff" ]] && args+=(-H "X-Forwarded-For: $xff")
    curl "${args[@]}"
}

assert_ok() {
    local resp="$1" what="$2"
    if ! jq -e '.code == 0' >/dev/null 2>&1 <<<"$resp"; then
        local msg
        msg=$(jq -r '.msg // empty' <<<"$resp" 2>/dev/null || true)
        die "$what 失败：${msg:-$resp}"
    fi
}

login() {
    local student="$1" password="$2" xff="$3"
    request POST /api/auth/login "" \
        "{\"studentNo\":\"$student\",\"password\":\"$password\"}" "" "$xff"
}

# 学生端只传学号；即使演示账号是学号直登自动创建的（密码未知）也能登录
student_login() {
    local student="$1" xff="$2"
    request POST /api/auth/student-login "" \
        "{\"studentNo\":\"$student\"}" "" "$xff"
}

echo "==> 检查后端：$BASE_URL"
curl -fsS "$BASE_URL/api/health" >/dev/null \
    || die "后端没启动，先运行 LibHuntApplication，再执行本脚本"

echo "==> 登录管理员 $ADMIN_STUDENT_NO"
ADMIN_RESP=$(login "$ADMIN_STUDENT_NO" "$ADMIN_PASSWORD" "10.99.0.1")
assert_ok "$ADMIN_RESP" "管理员登录"
ADMIN_TOKEN=$(jq -r '.data.token // empty' <<<"$ADMIN_RESP")
[[ -n "$ADMIN_TOKEN" ]] || die "管理员登录成功但没拿到 token"

# ---------------------------------------------------------------- 固定数据

NAMES=(
    "林小舟" "陈以宁" "黄嘉树" "周若岚" "何一川"
    "梁思远" "苏晚晴" "罗子谦" "许知微" "邓嘉禾"
)

# 每个玩家的总错题次数（数组下标 0 对应 ab565601，依次类推）。
# 答错不扣分，错题次数只用于模拟不同的答题表现。
WRONG_TOTAL=(0 0 12 18 24 30 36 42 44 46)

# 每个玩家在多选题里选中「多少个正确选项」的比例（%），下标 0 对应 ab565601。
# 100 = 全选，单题满分；数值越低，少选越多、部分分越少，总分越低。
# 单选题只有一个正确选项，不受这个比例影响。
ANSWER_RATIO=(100 100 90 80 70 60 50 40 30 20)

# 第 1~12 关正确答案（与 V3__seed_levels.sql 保持一致）
CORRECT=()
CORRECT[1]='["b"]'
CORRECT[2]='["b","c"]'
CORRECT[3]='["d"]'
CORRECT[4]='["d"]'
CORRECT[5]='["b","d"]'
CORRECT[6]='["c"]'
CORRECT[7]='["a","b"]'
CORRECT[8]='["a"]'
CORRECT[9]='["b"]'
CORRECT[10]='["a","b","c","d","e","f"]'
CORRECT[11]='["a","b","d","e","f"]'
CORRECT[12]='["a","b","c"]'

# 现场二维码内容由服务端签发（关卡号 + 过期时间 + HMAC 签名），这里先取 12 关的 token。
# 默认有效期 7 天；token 过期后重新执行本脚本即可，不需要改代码。
QR=()
echo "==> 签发 12 关二维码（有效期 7 天）"
for level in $(seq 1 12); do
    qr_resp=$(request GET "/api/admin/levels/$level/qr?ttlSeconds=604800" "$ADMIN_TOKEN")
    assert_ok "$qr_resp" "签发第 $level 关二维码"
    QR[$level]=$(jq -r '.data.token // empty' <<<"$qr_resp")
    [[ -n "${QR[$level]}" ]] || die "第 $level 关二维码为空"
done

# 故意答错时用的选项：必须是该关正确答案之外的选项（错选本题直接 0 分）
WRONG=()
WRONG[1]='a'
WRONG[2]='a'
WRONG[3]='a'
WRONG[4]='a'
WRONG[5]='a'
WRONG[6]='a'
WRONG[7]='c'
WRONG[8]='b'
WRONG[9]='a'
WRONG[10]='g'
WRONG[11]='c'
WRONG[12]='d'

# ---------------------------------------------------------------- 工具函数

# 学生学号规则：2 个字母 + 6 个数字（与前端 STUDENT_ID_REGEX 一致）
demo_student_no() {
    printf "ab5656%02d" "$1"
}

find_player_id() {
    local student="$1" resp
    resp=$(request GET "/api/admin/players?keyword=$student&page=1&size=5" "$ADMIN_TOKEN")
    jq -r --arg s "$student" \
        '.data.records[]? | select(.studentNo == $s) | .id' <<<"$resp" | head -1
}

create_or_get_player() {
    local student="$1" nickname="$2" resp id
    resp=$(request POST /api/admin/players "$ADMIN_TOKEN" \
        "{\"studentNo\":\"$student\",\"nickname\":\"$nickname\",\"initialPassword\":\"$DEMO_PASSWORD\",\"role\":\"STUDENT\"}")
    if jq -e '.code == 0' >/dev/null 2>&1 <<<"$resp"; then
        jq -r '.data.id' <<<"$resp"
        return
    fi
    # 已存在（或并发创建）时回查一次
    id=$(find_player_id "$student")
    [[ -n "$id" ]] || die "创建 $student 失败：$(jq -r '.msg // empty' <<<"$resp")"
    printf '%s\n' "$id"
}

abandon_current_run() {
    local token="$1" resp run_id status
    resp=$(request GET /api/runs/current "$token")
    run_id=$(jq -r '.data.runId // empty' <<<"$resp")
    [[ -n "$run_id" ]] || return 0
    status=$(jq -r '.data.status // empty' <<<"$resp")
    if [[ "$status" == "IN_PROGRESS" ]]; then
        request POST "/api/runs/$run_id/abandon" "$token" >/dev/null
    fi
}

# answer_count 省略时提交全部正确选项（满分）；
# 传更小的值则只提交前 N 个正确选项，拿部分分（少选）。
play_level() {
    local token="$1" run_id="$2" level="$3" wrong_count="$4" answer_count="${5:-}"
    local k=1 resp selected

    while [[ "$k" -le "$wrong_count" ]]; do
        resp=$(request POST "/api/runs/$run_id/levels/$level/answer" "$token" \
            "{\"optionIds\":[\"${WRONG[$level]}\"]}")
        assert_ok "$resp" "第 $level 关故意答错"
        k=$((k + 1))
    done

    if [[ -n "$answer_count" ]]; then
        selected=$(jq -c --argjson n "$answer_count" '.[0:$n]' <<<"${CORRECT[$level]}")
    else
        selected="${CORRECT[$level]}"
    fi

    resp=$(request POST "/api/runs/$run_id/levels/$level/answer" "$token" \
        "{\"optionIds\":$selected}")
    assert_ok "$resp" "第 $level 关答题"
    [[ "$(jq -r '.data.correct' <<<"$resp")" == "true" ]] \
        || die "第 $level 关答案不正确：$(jq -c '.data' <<<"$resp")"

    resp=$(request POST "/api/runs/$run_id/levels/$level/qr" "$token" \
        "{\"answer\":\"${QR[$level]}\"}")
    assert_ok "$resp" "第 $level 关扫码"
    [[ "$(jq -r '.data.correct' <<<"$resp")" == "true" ]] \
        || die "第 $level 关二维码不正确：$(jq -c '.data' <<<"$resp")"

    LAST_SETTLEMENT="$resp"
    if [[ "$LEVEL_DELAY" != "0" ]]; then
        sleep "$LEVEL_DELAY"
    fi
}

# ---------------------------------------------------------------- 主流程

TOKENS=()
LAST_SETTLEMENT=""

i=1
while [[ "$i" -le "$PLAYER_COUNT" ]]; do
    student=$(demo_student_no "$i")
    nickname="${NAMES[$((i - 1))]:-演示玩家$i}"

    echo
    echo "==> [$i/$PLAYER_COUNT] $student $nickname"
    create_or_get_player "$student" "$nickname" >/dev/null

    login_resp=$(student_login "$student" "10.99.$i.1")
    assert_ok "$login_resp" "$student 登录"
    token=$(jq -r '.data.token // empty' <<<"$login_resp")
    [[ -n "$token" ]] || die "$student 登录成功但没拿到 token"
    TOKENS[$i]="$token"

    abandon_current_run "$token"

    start_resp=$(request POST /api/runs "$token" "" \
        "seed-$student-$(date +%s)-$RANDOM")
    assert_ok "$start_resp" "$student 开局"
    run_id=$(jq -r '.data.runId // empty' <<<"$start_resp")
    [[ -n "$run_id" ]] || die "$student 开局没有返回 runId"

    level=1
    while [[ "$level" -le 12 ]]; do
        # 把该玩家的总错题数尽量均匀地摊到 12 关：
        # 前 total_wrong % 12 关比其余关多错一次。
        total_wrong=${WRONG_TOTAL[$((i - 1))]:-46}
        wrong_count=$((total_wrong / 12))
        if (( level <= total_wrong % 12 )); then
            wrong_count=$((wrong_count + 1))
        fi

        # 多选少选：按 ANSWER_RATIO 只提交一部分正确选项
        total_options=$(jq 'length' <<<"${CORRECT[$level]}")
        ratio=${ANSWER_RATIO[$((i - 1))]:-20}
        answer_count=$((total_options * ratio / 100))
        if (( answer_count < 1 )); then
            answer_count=1
        fi
        if (( answer_count > total_options )); then
            answer_count=$total_options
        fi

        if [[ "$level" -eq 12 ]]; then
            # 用停留时间模拟不同玩家的通关用时（同分时按用时排序）
            sleep "$((i % 5))"
        fi

        play_level "$token" "$run_id" "$level" "$wrong_count" "$answer_count"
        level=$((level + 1))
    done

    total=$(jq -r '.data.settlement.totalScore // 0' <<<"$LAST_SETTLEMENT")
    duration=$(jq -r '.data.settlement.durationSeconds // 0' <<<"$LAST_SETTLEMENT")
    echo "    ✅ 通关：总分 ${total}，用时 ${duration}s"

    i=$((i + 1))
done

# ---------------------------------------------------- 额外对局：进行中 / 已放弃

if [[ "$PLAYER_COUNT" -ge 10 ]]; then
    echo
    echo "==> 额外对局：$(demo_student_no 8) 已放弃，$(demo_student_no 9) / $(demo_student_no 10) 进行中"

    for i in 8 9 10; do
        token="${TOKENS[$i]:-}"
        [[ -n "$token" ]] || continue
        student=$(demo_student_no "$i")

        abandon_current_run "$token"
        start_resp=$(request POST /api/runs "$token" "" \
            "seed-extra-$i-$(date +%s)-$RANDOM")
        assert_ok "$start_resp" "$student 额外开局"
        run_id=$(jq -r '.data.runId // empty' <<<"$start_resp")
        [[ -n "$run_id" ]] || die "$student 额外开局没有返回 runId"

        if [[ "$i" -eq 8 ]]; then
            play_level "$token" "$run_id" 1 1
            request POST "/api/runs/$run_id/abandon" "$token" >/dev/null
            echo "    $student：已通过第 1 关后放弃"
        else
            for level in 1 2 3; do
                play_level "$token" "$run_id" "$level" 0
            done
            echo "    $student：进行中，已通过 3 关（刷新地图可看到进度）"
        fi
    done
fi

# ---------------------------------------------------------------- 结果汇总

echo
echo "==> 排行榜前 10"
leaderboard=$(request GET "/api/leaderboard?limit=10" "$ADMIN_TOKEN")
assert_ok "$leaderboard" "读取排行榜"
jq -r '.data[] | "  \(.rank). \(.nickname)\t\(.totalScore) 分\t\(.durationSeconds)s"' <<<"$leaderboard"

echo
echo "==> 管理端统计"
stats=$(request GET "/api/admin/stats" "$ADMIN_TOKEN")
assert_ok "$stats" "读取管理端统计"
jq -r '.data | "  玩家：\(.playerCount)\n  通关次数：\(.totalCompletions)\n  平均分：\(.avgScore | floor)\n  平均用时：\(.avgDuration | floor)s"' <<<"$stats"

echo
echo "完成。演示账号：ab565601 ~ ab5656$(printf "%02d" "$PLAYER_COUNT")，密码：$DEMO_PASSWORD"
echo "登录后可以看：地图进度、排行榜、管理端 /admin"
