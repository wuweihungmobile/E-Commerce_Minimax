#!/usr/bin/env bash
###############################################################################
# validate-e2e.sh — 本地 E2E 守門關卡（複製 ci.yml 的 e2e job）  Sprint 26 / AI-1001
#
# 目的：
#   以「與 GitHub E2E 完全相同的執行方式」在本機跑端到端測試，取代雲端 e2e job：
#   打包 JAR → java -jar（host）、npm build → npm start（host）、npx playwright test，
#   全部對「Flyway 從零重建的乾淨 DB」執行。
#
# 為什麼不用 docker-compose build？
#   - backend/frontend 的 Dockerfile runner target 已 bit-rot
#     （backend: 死碼 COPY .mvn；frontend: 需 Next standalone 但 next.config 未啟用），
#     且雲端 CI 根本不經 Dockerfile（它用 mvn package + npm start + host 程序）。
#   - 本腳本忠實複製雲端 e2e job 的「host 程序」做法，繞過腐爛的 Dockerfile。
#
# 一次涵蓋兩件事：
#   1. schema 漂移：backend 以 ddl-auto=validate 對 Flyway 重建的乾淨 DB 啟動 → 漂移即啟動失敗
#   2. 端到端流程：Playwright 對真實全棧跑 frontend/e2e/*.spec.ts
#
# 用法：
#   make validate-e2e
#   E2E_GATE_SKIP_BUILD=1 ./scripts/validate-e2e.sh   # 重用既有 JAR + .next（快，供本機快速回歸）
#
# 連接埠（backend 固定 8080、frontend 固定 3000 —— 前端打包 API URL 與 playwright baseURL 對齊）：
#   - 需 8080 / 3000 空閒；DB/Redis 用非標準 port（55432/56379）避免撞本機 dev DB。
#
# 退出碼：0 = 全綠；1 = E2E 失敗 / schema 漂移；2 = 環境/前置錯誤
###############################################################################
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

PG_CONTAINER="e2e-gate-pg"
RD_CONTAINER="e2e-gate-redis"
PG_IMAGE="postgres:18-alpine"          # 與生產/CI 一致（DOCKER_POLICY 核准）
RD_IMAGE="redis:7-alpine"              # 與生產/CI 一致（DOCKER_POLICY 核准）
PG_PORT="${E2E_GATE_PG_PORT:-55432}"
RD_PORT="${E2E_GATE_RD_PORT:-56379}"
PG_USER="koala"; PG_PASS="koala5"; PG_DB="nextkeytest"
BACKEND_PORT="${E2E_GATE_BACKEND_PORT:-8080}"     # 與前端打包 NEXT_PUBLIC_API_URL 預設值對齊
FRONTEND_PORT="${E2E_GATE_FRONTEND_PORT:-3000}"   # playwright.config.ts baseURL 固定 3000
API_URL="http://localhost:${BACKEND_PORT}/api/v2"
BACKEND_HEALTH="http://localhost:${BACKEND_PORT}/api/actuator/health"
FRONTEND_URL="http://localhost:${FRONTEND_PORT}"
# 本地守門啟動用的虛構（非機密）JWT 簽署值，僅供 E2E 啟動，不得用於任何真實環境。
GATE_JWT_VALUE=e2e-gate-dummy-jwt-signing-value-min-32-chars-for-hs256-algo
WAIT_SECONDS="${E2E_GATE_WAIT:-180}"
BACKEND_LOG="$(mktemp -t e2e-gate-backend.XXXXXX)"
FRONTEND_LOG="$(mktemp -t e2e-gate-frontend.XXXXXX)"
BACKEND_PID=""; FRONTEND_PID=""

log() { printf '\033[0;36m[e2e-gate]\033[0m %s\n' "$*"; }
err() { printf '\033[0;31m[e2e-gate] ❌ %s\033[0m\n' "$*" >&2; }
ok()  { printf '\033[0;32m[e2e-gate] ✅ %s\033[0m\n' "$*"; }

cleanup() {
  # 以 port 為主清掉 host 程序（kill PID 可能殺不到 npm 的 node 子程序）
  for p in "$FRONTEND_PORT" "$BACKEND_PORT"; do
    pids="$(lsof -ti "tcp:$p" 2>/dev/null || true)"
    [ -n "$pids" ] && kill $pids >/dev/null 2>&1 || true
  done
  [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" >/dev/null 2>&1 || true
  [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" >/dev/null 2>&1 || true
  docker rm -f "$PG_CONTAINER" "$RD_CONTAINER" >/dev/null 2>&1 || true
  rm -f "$BACKEND_LOG" "$FRONTEND_LOG" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM

command -v docker >/dev/null 2>&1 || { err "需要 docker，但找不到指令"; exit 2; }
docker version >/dev/null 2>&1 || { err "docker daemon 未啟動"; exit 2; }
command -v lsof  >/dev/null 2>&1 || { err "需要 lsof（清理 host 程序用）"; exit 2; }

# --- 前置：8080 / 3000 必須空閒（否則會誤打既有 dev server → 假通過/汙染）------
if lsof -ti "tcp:${BACKEND_PORT}" >/dev/null 2>&1; then
  err "port ${BACKEND_PORT} 已被占用（疑似既有 backend）。請關閉，或設 E2E_GATE_BACKEND_PORT 重試。"; exit 2
fi
if lsof -ti "tcp:${FRONTEND_PORT}" >/dev/null 2>&1; then
  err "port ${FRONTEND_PORT} 已被占用（playwright baseURL 固定 3000）。請關閉占用該 port 的程序。"; exit 2
fi

# --- 1. 啟動乾淨 PostgreSQL + Redis（無 initdb，schema 全由 Flyway 建立）-------
log "啟動乾淨 PostgreSQL ($PG_IMAGE) :$PG_PORT、Redis :$RD_PORT ..."
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
ok "DB 就緒（乾淨 schema，等待 Flyway 建表）"

# --- 2. 建置 backend JAR（可用 E2E_GATE_SKIP_BUILD=1 跳過）---------------------
if [ "${E2E_GATE_SKIP_BUILD:-0}" != "1" ]; then
  log "建置 backend JAR（mvn package -DskipTests）..."
  ( cd backend && mvn -q package -DskipTests ) || { err "JAR 建置失敗"; exit 2; }
fi
JAR_FILE="$(ls -1t backend/target/*.jar 2>/dev/null | grep -vE 'original|sources|javadoc' | head -1)"
[ -n "$JAR_FILE" ] && [ -f "$JAR_FILE" ] || { err "找不到 JAR（請先 mvn package，或移除 E2E_GATE_SKIP_BUILD）"; exit 2; }
log "使用 JAR：$JAR_FILE"

# --- 3. 啟動 backend（ddl-auto=validate + Flyway，profile test，指向乾淨 DB）---
log "啟動 backend :$BACKEND_PORT（ddl-auto=validate + Flyway）..."
SERVER_PORT="$BACKEND_PORT" \
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

log "等待 backend 啟動並 Flyway migrate（最多 ${WAIT_SECONDS}s）..."
deadline=$((SECONDS + WAIT_SECONDS))
while :; do
  if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
    err "backend 程序提前退出 → 疑似 schema 漂移或啟動失敗"
    grep -inE "Schema-validation|missing column|wrong column type|should be|missing table|SchemaManagementException|FlywayValidateException|Migration .* failed" "$BACKEND_LOG" | head -30 || true
    echo "----- backend.log（最後 40 行）-----"; tail -40 "$BACKEND_LOG" 2>/dev/null || true
    exit 1
  fi
  if curl -fsS "$BACKEND_HEALTH" >/dev/null 2>&1; then
    ok "backend 啟動成功 → entity 與 Flyway schema 對齊，無漂移"
    break
  fi
  if [ "$SECONDS" -ge "$deadline" ]; then
    err "backend 未在 ${WAIT_SECONDS}s 內健康啟動"
    grep -inE "Schema-validation|missing column|wrong column type|missing table|SchemaManagementException" "$BACKEND_LOG" | head -30 || true
    echo "----- backend.log（最後 40 行）-----"; tail -40 "$BACKEND_LOG" 2>/dev/null || true
    exit 1
  fi
  sleep 3
done

# --- 4. 建置 + 啟動 frontend（host，與雲端 e2e job 一致）-----------------------
if [ "${E2E_GATE_SKIP_BUILD:-0}" != "1" ]; then
  log "建置 frontend（npm ci + build，NEXT_PUBLIC_API_URL=$API_URL）..."
  ( cd frontend && npm ci && NEXT_PUBLIC_API_URL="$API_URL" npm run build ) \
    || { err "frontend build 失敗"; exit 2; }
fi
log "啟動 frontend :$FRONTEND_PORT ..."
( cd frontend && NEXT_PUBLIC_API_URL="$API_URL" PORT="$FRONTEND_PORT" npm run start > "$FRONTEND_LOG" 2>&1 ) &
FRONTEND_PID=$!
log "等待 frontend 就緒 ..."
for i in $(seq 1 40); do
  curl -fsS "$FRONTEND_URL" >/dev/null 2>&1 && break
  sleep 2
  if [ "$i" -eq 40 ]; then err "frontend 未在 80s 內就緒"; tail -30 "$FRONTEND_LOG" 2>/dev/null || true; exit 1; fi
done
ok "frontend 就緒（:$FRONTEND_PORT）"

# --- 5. 執行 Playwright E2E（baseURL=localhost:3000，reuseExistingServer）------
#   到這裡為止：backend 以 ddl-auto=validate 對乾淨 DB 啟動成功 → schema 無漂移（硬性條件已過）。
#   e2e spec 的 pass/fail 政策：
#     - 預設 advisory：與雲端 ci.yml 的 e2e job（continue-on-error: true）一致 —— 報告但不阻擋。
#     - E2E_GATE_STRICT=1：視 spec 失敗為守門失敗（阻擋），供 DoD 強制化（Sprint 26 AI-1001）。
log "執行 Playwright E2E（npx playwright test）..."
set +e
( cd frontend && npx playwright test )
E2E_EXIT=$?
set -e
if [ "$E2E_EXIT" -eq 0 ]; then
  ok "本地 E2E 守門通過（schema 對齊 ✅ + e2e 全綠 ✅）"
  exit 0
fi

echo ""
err "Playwright 有 spec 失敗（exit $E2E_EXIT）。報告：frontend/playwright-report/"
if [ "${E2E_GATE_STRICT:-0}" = "1" ]; then
  err "E2E_GATE_STRICT=1 → 視為守門失敗（阻擋 release）"
  exit 1
fi
log "⚠️  預設 advisory（雲端 e2e job 本就 continue-on-error，從不阻擋）。"
log "    高價值的 schema 漂移驗證已通過（backend ddl-auto=validate 成功啟動）。"
log "    要讓 e2e 失敗阻擋 release：E2E_GATE_STRICT=1 make validate-e2e"
ok "本地 E2E 守門完成：schema ✅；e2e 有既有失敗（advisory，詳見報告）"
exit 0
