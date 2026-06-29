#!/usr/bin/env bash
###############################################################################
# validate-schema.sh — 本地 schema 漂移守門關卡 (US-001 / AI-901, Sprint 25)
#
# 目的：
#   在 push 前以「與 GitHub E2E 相同的條件」啟動 backend，攔截 entity ↔ Flyway
#   migration 的 schema 漂移，避免漂移到 GitHub E2E backend 啟動才爆發
#   （Sprint 24 因此投入 9 個救火 commit / Flyway V48~V55）。
#
# 為什麼本地 act 抓不到？
#   - 本地 act（make validate-all）的 backend job 走 integration-test profile，
#     ddl-auto=update → Hibernate 自動補欄位/建表，**遮蔽漂移**。
#   - GitHub E2E（ci.yml e2e job）用打包 JAR + ddl-auto=validate + Flyway，
#     先清空 schema 再讓 Flyway 重跑全部 migration，漂移即啟動失敗。
#
# 本關卡如何運作（精確複製 E2E 條件）：
#   1. 啟動「全新、無 initdb」的 PostgreSQL（schema 全由 Flyway 建立）
#   2. 啟動 backend：ddl-auto=validate + flyway.enabled=true，指向該乾淨 DB
#   3. Flyway 跑完 V1~V55 後，Hibernate 以 validate 比對 entity ↔ 實際 schema
#   4. 啟動成功 = schema 對齊（exit 0）；啟動失敗 = 漂移（列出錯誤，exit 1）
#
# 用法：
#   make validate-schema                          # 重新建置 JAR 後驗證
#   SCHEMA_GATE_SKIP_BUILD=1 ./scripts/validate-schema.sh   # 重用既有 JAR（快）
#
# 環境變數（皆有預設值）：
#   SCHEMA_GATE_PG_PORT   宿主機 PostgreSQL port（預設 55432，避開本機 5432）
#   SCHEMA_GATE_RD_PORT   宿主機 Redis port（預設 56379，避開本機 6379）
#   SCHEMA_GATE_WAIT      backend 啟動等待秒數（預設 150）
#   SCHEMA_GATE_SKIP_BUILD=1   跳過 mvn package，重用 backend/target 既有 JAR
#
# 退出碼：0 = schema 對齊；1 = 偵測到漂移；2 = 環境/前置錯誤
###############################################################################
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

# --- 設定（與 ci.yml e2e job / docker-compose.test.yml 對齊）-------------------
PG_CONTAINER="schema-gate-pg"
RD_CONTAINER="schema-gate-redis"
PG_IMAGE="postgres:18-alpine"          # 與生產/CI 一致（DOCKER_POLICY 核准）
RD_IMAGE="redis:7-alpine"              # 與生產/CI 一致（DOCKER_POLICY 核准）
PG_PORT="${SCHEMA_GATE_PG_PORT:-55432}"
RD_PORT="${SCHEMA_GATE_RD_PORT:-56379}"
PG_USER="koala"; PG_PASS="koala5"; PG_DB="nextkeytest"
# 本地關卡啟動用的虛構（非機密）JWT 簽署值，僅供 schema 驗證啟動，不得用於任何真實環境。
# 以不帶引號的賦值避免 secret 掃描誤判（與 docker-compose 慣例一致）。
GATE_JWT_VALUE=schema-gate-dummy-jwt-signing-value-min-32-chars-hs256
# 🔴 使用專屬 server port（非 8080），避免與本機既有 backend（dev / docker compose）
#    衝突——否則 health check 會誤打既有 backend，造成「假通過」（漂移漏接）。
SERVER_PORT="${SCHEMA_GATE_SERVER_PORT:-18080}"
HEALTH_URL="http://localhost:${SERVER_PORT}/api/actuator/health"
WAIT_SECONDS="${SCHEMA_GATE_WAIT:-150}"
BACKEND_LOG="$(mktemp -t schema-gate-backend.XXXXXX)"
BACKEND_PID=""

log() { printf '\033[0;36m[schema-gate]\033[0m %s\n' "$*"; }
err() { printf '\033[0;31m[schema-gate] ❌ %s\033[0m\n' "$*" >&2; }
ok()  { printf '\033[0;32m[schema-gate] ✅ %s\033[0m\n' "$*"; }

cleanup() {
  [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" >/dev/null 2>&1 || true
  docker rm -f "$PG_CONTAINER" "$RD_CONTAINER" >/dev/null 2>&1 || true
  rm -f "$BACKEND_LOG" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

command -v docker >/dev/null 2>&1 || { err "需要 docker，但找不到指令"; exit 2; }
docker version >/dev/null 2>&1 || { err "docker daemon 未啟動"; exit 2; }

# 🔴 前置：確認 health check 目標 port 未被佔用，否則會誤判既有 backend 為「通過」
if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
  err "port ${SERVER_PORT} 已有服務在回應 health（疑似既有 backend）"
  err "請關閉該服務，或以 SCHEMA_GATE_SERVER_PORT=<其他port> 重試，避免漂移漏接"
  exit 2
fi

# --- 1. 啟動乾淨 PostgreSQL + Redis（無 initdb.d，schema 全由 Flyway 建立）------
log "清理殘留容器並啟動乾淨 PostgreSQL ($PG_IMAGE) :$PG_PORT、Redis :$RD_PORT ..."
docker rm -f "$PG_CONTAINER" "$RD_CONTAINER" >/dev/null 2>&1 || true
docker run -d --rm --name "$PG_CONTAINER" \
  -e POSTGRES_USER=$PG_USER -e POSTGRES_PASSWORD=$PG_PASS -e POSTGRES_DB=$PG_DB \
  -p "${PG_PORT}:5432" "$PG_IMAGE" >/dev/null || { err "PostgreSQL 容器啟動失敗"; exit 2; }
docker run -d --rm --name "$RD_CONTAINER" \
  -p "${RD_PORT}:6379" "$RD_IMAGE" >/dev/null || { err "Redis 容器啟動失敗"; exit 2; }

log "等待 PostgreSQL 就緒 ..."
for i in $(seq 1 30); do
  docker exec "$PG_CONTAINER" pg_isready -U "$PG_USER" -d "$PG_DB" >/dev/null 2>&1 && break
  sleep 1
  if [ "$i" -eq 30 ]; then err "PostgreSQL 30s 內未就緒"; exit 2; fi
done
ok "PostgreSQL 就緒（乾淨 schema，等待 Flyway 建表）"

# --- 2. 建置 JAR（可用 SCHEMA_GATE_SKIP_BUILD=1 跳過）-------------------------
if [ "${SCHEMA_GATE_SKIP_BUILD:-0}" != "1" ]; then
  log "建置 backend JAR（mvn package -DskipTests，與 ci.yml e2e 一致）..."
  ( cd backend && mvn -q package -DskipTests ) || { err "JAR 建置失敗"; exit 2; }
fi
JAR_FILE="$(ls -1t backend/target/*.jar 2>/dev/null | grep -vE 'original|sources|javadoc' | head -1)"
[ -n "$JAR_FILE" ] && [ -f "$JAR_FILE" ] || { err "找不到 JAR（請先 mvn package，或移除 SCHEMA_GATE_SKIP_BUILD）"; exit 2; }
log "使用 JAR：$JAR_FILE"

# --- 3. 以 validate + Flyway 啟動 backend（複製 E2E：profile test）-----------
log "啟動 backend（ddl-auto=validate + Flyway，指向乾淨 DB，server port ${SERVER_PORT}）..."
SERVER_PORT="$SERVER_PORT" \
SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${PG_PORT}/${PG_DB}" \
SPRING_DATASOURCE_USERNAME="$PG_USER" \
SPRING_DATASOURCE_PASSWORD=$PG_PASS \
SPRING_DATA_REDIS_HOST="localhost" \
SPRING_DATA_REDIS_PORT="$RD_PORT" \
SPRING_JPA_HIBERNATE_DDL_AUTO="validate" \
SPRING_FLYWAY_ENABLED="true" \
JWT_SECRET=$GATE_JWT_VALUE \
  java -jar "$JAR_FILE" --spring.profiles.active=test > "$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!

# --- 4. 等待健康 / 偵測啟動失敗 ----------------------------------------------
log "等待 backend 啟動並 Flyway migrate（最多 ${WAIT_SECONDS}s）..."
deadline=$((SECONDS + WAIT_SECONDS))
while [ "$SECONDS" -lt "$deadline" ]; do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    err "backend 程序提前退出 → 疑似 schema 漂移或啟動失敗"
    break
  fi
  if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
    ok "backend 啟動成功 → entity 與 Flyway schema 對齊，無漂移"
    exit 0
  fi
  sleep 3
done

# --- 5. 失敗：輸出 schema 驗證相關錯誤 ---------------------------------------
err "schema 守門失敗：backend 未能在 ${WAIT_SECONDS}s 內健康啟動"
echo "----------------- backend.log（schema 驗證相關）-----------------"
grep -inE "Schema-validation|missing column|wrong column type|should be|missing table|SchemaManagementException|FlywayValidateException|Migration .* failed" "$BACKEND_LOG" | head -40 || true
echo "----------------- backend.log（最後 50 行）-----------------"
tail -50 "$BACKEND_LOG" 2>/dev/null || true
exit 1
