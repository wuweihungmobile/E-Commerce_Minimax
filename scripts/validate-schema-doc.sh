#!/usr/bin/env bash
###############################################################################
# validate-schema-doc.sh — SRD 資料庫文件漂移守門（DEF-062 / Sprint 111）
#
# 目的：
#   確保 docs/02_architecture/SRD_Database_Schema.md 的資料表 DDL 與
#   backend/src/main/resources/db/migration/ 的 Flyway 遷移一致。
#
# 為什麼需要：
#   這份文件自 2026-04 建立後，整整 100 個 Sprint 沒人發現它逐欄都是錯的
#   ——16 張表全部漂移、117 個欄位在實作中不存在、2 張表從未被實作、
#   product_inventory 連主鍵都寫錯（文件 id、實作 sku_id）。
#   **文件債沒有自動守門就必定重演。**
#
# 與 validate-schema.sh 的差別：
#   validate-schema.sh   檢查 entity ↔ migration（程式對得上資料庫嗎）
#   validate-schema-doc.sh 檢查 migration ↔ 文件（文件說的是真的嗎）
#
# 用法：
#   make validate-schema-doc     # 檢查（不一致則 exit 1）
#   make sync-schema-doc         # 依實際 schema 重新產生文件
#
# 退出碼：0 = 一致；1 = 偵測到漂移；2 = 環境/前置錯誤
###############################################################################
set -uo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

PG_CONTAINER="${SCHEMA_DOC_PG_CONTAINER:-schemadoc-pg}"
PG_IMAGE="postgres:18-alpine"          # 與生產/CI 一致（DOCKER_POLICY 核准清單內）
PG_PORT="${SCHEMA_DOC_PG_PORT:-55434}" # 避開 5432(test-db)、55432(validate-schema)
MIGRATION_DIR="backend/src/main/resources/db/migration"
WRITE_MODE=""
[ "${1:-}" = "--write" ] && WRITE_MODE="--write"

log() { printf '\033[0;36m[schema-doc]\033[0m %s\n' "$*"; }
err() { printf '\033[0;31m[schema-doc] ❌ %s\033[0m\n' "$*" >&2; }

cleanup() { docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

command -v docker >/dev/null 2>&1 || { err "需要 docker，但找不到指令"; exit 2; }
docker version >/dev/null 2>&1 || { err "docker daemon 未啟動"; exit 2; }

log "啟動乾淨 PostgreSQL（$PG_IMAGE）:$PG_PORT ..."
docker rm -f "$PG_CONTAINER" >/dev/null 2>&1 || true
docker run -d --rm --name "$PG_CONTAINER" \
  -e POSTGRES_USER=koala -e POSTGRES_PASSWORD=koala5 -e POSTGRES_DB=nextkeytest \
  -p "${PG_PORT}:5432" "$PG_IMAGE" >/dev/null || { err "postgres 啟動失敗"; exit 2; }

for i in $(seq 1 40); do
  docker exec "$PG_CONTAINER" pg_isready -U koala -d nextkeytest >/dev/null 2>&1 && break
  sleep 1
  [ "$i" -eq 40 ] && { err "postgres 40s 未就緒"; exit 2; }
done

log "依版本順序套用 $(ls "$MIGRATION_DIR"/*.sql | wc -l | tr -d ' ') 個 Flyway 遷移 ..."
FAILED=0
for f in $(cd "$MIGRATION_DIR" && ls *.sql | sed 's/^V//' | sort -t_ -k1,1V | sed 's/^/V/'); do
  if ! docker exec -i "$PG_CONTAINER" psql -U koala -d nextkeytest -v ON_ERROR_STOP=1 -q \
        < "$MIGRATION_DIR/$f" >/dev/null 2>&1; then
    err "遷移套用失敗：$f"
    FAILED=1
  fi
done
[ "$FAILED" -eq 1 ] && { err "遷移無法乾淨套用，無從比對文件"; exit 2; }

log "比對文件 DDL 與實際 schema ..."
SCHEMA_DOC_PG_CONTAINER="$PG_CONTAINER" python3 scripts/lib/check_schema_doc.py $WRITE_MODE
exit $?
