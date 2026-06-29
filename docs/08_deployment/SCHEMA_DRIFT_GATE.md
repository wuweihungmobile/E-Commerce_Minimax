# Schema 漂移守門關卡 / Schema Drift Validation Gate

> **文件類型**: 本地 CI 驗證工具指南
> **建立日期**: 2026-06-29
> **對應**: Sprint 25 US-001 / AI-901（P0）
> **指令**: `make validate-schema`｜腳本: [scripts/validate-schema.sh](../../scripts/validate-schema.sh)

---

## 1. 這個關卡解決什麼問題？

Sprint 24 因 **entity 與 Flyway migration 的 schema 漂移**，投入 9 個計畫外 commit 救火（Flyway V48~V55：補齊缺漏建表 + 統一 ARRAY→jsonb）。根本原因是：**本地 CI 抓不到 schema 驗證錯誤，只有 GitHub E2E 才會爆。**

| 環境 | profile / 設定 | `ddl-auto` | 是否抓得到漂移 |
|------|---------------|-----------|---------------|
| 本地 `make validate-all`（act backend job） | `integration-test`（`src/test/resources`） | **`update`** | ❌ 否（Hibernate 自動補欄位/建表，遮蔽漂移） |
| 本地單元測試（`mvn test`） | `test`（`src/test/resources`） | `create-drop` | ❌ 否（由 entity 直接建表） |
| **GitHub E2E**（ci.yml `e2e` job） | 打包 JAR + `test`（JAR 內**無** profile 檔 → 套用 base `application.yml`） | **`validate`** + Flyway | ✅ 是（漂移即啟動失敗） |

> 關鍵：打包後的 JAR 只含 `src/main/resources/application.yml`（`ddl-auto: validate` + `flyway.enabled: true`）。`application-test.yml` / `application-integration-test.yml` 只存在 `src/test/resources`，**不會進 JAR**，因此 E2E 的 `test` profile 實際套用的是 base 設定（validate）。

**本守門關卡精確複製 GitHub E2E 的啟動條件**，把漂移在 **push 前**就攔下。

---

## 2. 運作原理

```
1. 啟動「全新、無 initdb」的 PostgreSQL（schema 完全由 Flyway 建立）+ Redis
2. 以 ddl-auto=validate + flyway.enabled=true 啟動打包 JAR（指向乾淨 DB、專屬 port 18080）
3. Flyway 跑完 V1~V55 → Hibernate 以 validate 比對 entity ↔ 實際 schema
4. 啟動成功（health UP） = schema 對齊 → exit 0
   啟動失敗（context init 失敗）= 漂移 → 列出錯誤 → exit 1
```

> ⚠️ 關卡的 backend 使用**專屬 port 18080**（非 8080），避免與本機既有 dev backend 衝突。
> 若 18080 已被佔用，關卡會在啟動前直接報錯（避免誤打既有 backend 造成「假通過」）。

---

## 3. 使用方式

```bash
# 標準用法：重新建置 JAR 後驗證（與 ci.yml e2e 一致）
make validate-schema

# 等同
./scripts/validate-schema.sh

# 快速重跑（重用 backend/target 既有 JAR，不重建）
SCHEMA_GATE_SKIP_BUILD=1 ./scripts/validate-schema.sh
```

### 環境變數（皆有預設）

| 變數 | 預設 | 說明 |
|------|------|------|
| `SCHEMA_GATE_SERVER_PORT` | `18080` | backend 監聽 port（避開 8080） |
| `SCHEMA_GATE_PG_PORT` | `55432` | 宿主機 PostgreSQL port |
| `SCHEMA_GATE_RD_PORT` | `56379` | 宿主機 Redis port |
| `SCHEMA_GATE_WAIT` | `150` | backend 啟動等待秒數 |
| `SCHEMA_GATE_SKIP_BUILD` | `0` | 設 `1` 跳過 `mvn package`，重用既有 JAR |

### 退出碼

| 退出碼 | 意義 |
|--------|------|
| `0` | schema 與 entity 對齊，無漂移 |
| `1` | 偵測到漂移（輸出 `Schema-validation` 錯誤與 backend log 尾段） |
| `2` | 環境/前置錯誤（docker 未啟動、port 被佔、JAR 建置失敗等） |

---

## 4. 何時該跑？

🔴 **修改以下任一項後、push 前，必須執行 `make validate-schema`**：

- 新增/修改 `@Entity` 欄位（特別是 collection / jsonb / array / enum / 型別轉換）
- 新增/修改 Flyway migration（`backend/src/main/resources/db/migration/`）
- 新增 `@Entity` 類別

> 此關卡填補了 `make validate-all`（act）的盲點——act 走 `ddl-auto=update`，**不會**抓 schema 漂移。

---

## 5. 驗證證據（Sprint 25 US-001）

關卡已雙向驗證（測試能真正失敗，符合 Rule 9）：

| 測試 | 條件 | 結果 |
|------|------|------|
| 正向 | 乾淨碼（Flyway V55 後） | ✅ `exit 0` — backend 健康啟動，schema 對齊 |
| 負向 | 故意在 `Conversation` 加無對應 migration 的欄位 | ✅ `exit 1` — `Schema-validation: missing column [schema_gate_drift_probe] in table [conversations]` |

---

## 6. 與其他驗證的關係

| 指令 | 涵蓋範圍 | 抓 schema 漂移？ |
|------|---------|-----------------|
| `make validate-schema` | backend 以 validate + Flyway 啟動（本文件） | ✅ |
| `make validate-all` | 完整 act CI（lint + compile + unit + integration） | ❌（integration-test=update） |
| `make validate-fast` | 快速 act（backend + frontend job） | ❌ |
| pre-commit hook | lint + compile + 核心測試 + secret 掃描 | ❌ |
| pre-push hook | 完整 act CI | ❌ |

> **建議**：`validate-schema` 目前為**手動關卡**（修改 entity/migration 後手動執行）。若團隊要強制化，可評估在 pre-push hook 中加入呼叫——但需權衡每次 push 增加約 1~2 分鐘（DB 啟動 + Flyway + 驗證）。

---

**文件版本**: v1.0
**建立日期**: 2026-06-29
**對應 Action Item**: AI-901（Sprint 24 Retro）
**建立者**: Dev David + Claude Code
