# Sprint 25 任務分解 / Sprint 25 Tasks

> **Sprint 編號**: Sprint 25
> **期間**: 2026-09-27 ~ 2026-10-10（預估）
> **建立日期**: 2026-06-29
> **基於**: [SPRINT_25_PLAN.md](../04_planning/SPRINT_25_PLAN.md)

---

## 執行狀態總覽

| US ID | 標題 | SP | 優先級 | 狀態 |
|-------|------|----|--------|------|
| US-001 | 本地 schema 漂移守門關卡（AI-901） | 2 | P0 | ✅ 完成 |
| US-002 | entity ↔ migration 一致性盤點（AI-902） | 2 | P1 | ⬜ 待開始 |
| US-003 | Conversation tenant_id 補齊（AI-802） | 2 | P1 | ⬜ 待開始 |
| US-004 | M10 WebSocket 前端整合（@stomp/stompjs） | 3 | P1 | ⬜ 待開始 |
| US-005 | SSH pre-push 優化（Buffer-A，AI-804） | 1 | Buffer | ⬜ 待評估 |
| US-006 | M11 取消流程業務規則確認（Buffer-B，AI-903） | 1 | Buffer | ⬜ 待評估 |

**當前進度**: 1/4 承諾 US 完成（US-001，2 SP）。

---

## US-001：本地 schema 漂移守門關卡（AI-901）

> **SP**: 2 | **優先級**: P0 | **狀態**: ✅ 完成（2026-06-29）

### 完成交付物

- [x] **scripts/validate-schema.sh** — schema 漂移守門關卡腳本
  - 啟動乾淨 PostgreSQL（無 initdb，schema 全由 Flyway 建立）+ Redis
  - 以 `ddl-auto=validate` + `flyway.enabled=true` 啟動打包 JAR（專屬 port 18080）
  - 精確複製 GitHub E2E（ci.yml `e2e` job）啟動條件
  - 啟動前檢查 port 未被佔用，避免誤打既有 backend 造成「假通過」
  - 支援 `SCHEMA_GATE_SKIP_BUILD=1`（重用既有 JAR）等環境變數

- [x] **Makefile** — 新增 `validate-schema` target

- [x] **docs/08_deployment/SCHEMA_DRIFT_GATE.md** — 完整使用指南（原理、用法、退出碼、何時執行）
- [x] **docs/08_deployment/LOCAL_CI_VALIDATION.md** — 加入守門關卡指引（act 抓不到漂移的提醒）

### AC 驗證

- [x] **AC-001-1**: `make validate-schema` 以 `ddl-auto=validate` 對 Flyway-migrated 乾淨 DB 啟動 backend
- [x] **AC-001-2**: 負向測試通過 — 故意在 `Conversation` 加無對應 migration 的欄位，關卡以
  `Schema-validation: missing column [schema_gate_drift_probe] in table [conversations]` → **exit 1** 攔下（驗證後已還原）
- [x] **AC-001-3**: 文件化使用方式（SCHEMA_DRIFT_GATE.md）

### 驗證證據（雙向）

| 測試 | 條件 | 結果 |
|------|------|------|
| 正向 | 乾淨碼（Flyway V55 後），重建 JAR + port 18080 | ✅ exit 0，schema 對齊 |
| 負向 | 植入漂移欄位 | ✅ exit 1，missing column 明確指出 |

### 重要修正紀錄

- 初版 health check 寫死 `localhost:8080`，與本機既有 dev backend 衝突 → **假通過**（漂移漏接）。
  修正為專屬 port `18080` + 啟動前 port 佔用檢查，負向測試才正確攔下。

---

## US-002：entity ↔ migration 一致性盤點（AI-902）

> **SP**: 2 | **優先級**: P1 | **狀態**: ⬜ 待開始

**目標**: 盤點所有 `@Entity` 欄位型別 vs Flyway DDL，建立對照清單，杜絕漂移復發。建議在 US-001 完成後執行（用守門關卡掃出殘留漂移）。

---

## US-003：Conversation tenant_id 補齊（AI-802）

> **SP**: 2 | **優先級**: P1 | **狀態**: ⬜ 待開始

**目標**: Conversation entity 補 `tenant_id` + V56 migration + repository/service 租戶範圍化。

---

## US-004：M10 WebSocket 前端整合（@stomp/stompjs）

> **SP**: 3 | **優先級**: P1 | **狀態**: ⬜ 待開始

**目標**: 前端接上 Sprint 24 STOMP 後端，訂閱對話頻道即時收訊。

---

## US-005（Buffer-A）：SSH pre-push 優化（AI-804）

> **SP**: 1 | **優先級**: Buffer | **狀態**: ⬜ 待評估

---

## US-006（Buffer-B）：M11 物流取消流程業務規則確認（AI-903）

> **SP**: 1 | **優先級**: Buffer | **狀態**: ⬜ 待評估（僅調查，不含實作）
