# Sprint 25 計劃 / Sprint 25 Plan

> **Sprint 編號**: Sprint 25
> **期間**: 2026-09-27 ~ 2026-10-10 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-29
> **基於**: [Sprint 24 Retrospective](../05_development/SPRINT_24_RETRO.md) + [DEFERRED_ITEMS_TRACKER.md](./DEFERRED_ITEMS_TRACKER.md)

---

## 🔴 前置條件確認（現況調查）

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 24 承諾 US 完成 | ✅ 3/3 US，7 SP（P0+P1） | US-001~003 全數達成 |
| Sprint 24 收尾 | ✅ Review/Retro/Release Notes 完成 | commit `1562e28`（docs-only） |
| DEF 延後項目 | ✅ 無活躍 DEF | DEF 全部清零 |
| Sprint 24 Retro Action Items | AI-901/902/802/804/903 共 5 項 | 帶入 Sprint 25 |
| Makefile validate target | ⚠️ 有 `validate-all/fast/backend/frontend`，**無專門 schema 守門 target** | AI-901 待新增 |
| ddl-auto 設定落差（根因） | 🔴 `application-integration-test.yml`=**update**（遮蔽漂移）；`application.yml`=**validate**；`application-test.yml`=**create-drop** | 本地 act 抓不到、GitHub E2E 才爆的根本原因 |
| 最新 Flyway 版本 | V55（新 migration 從 **V56** 起） | Sprint 24 救火結束於 V55 |
| Conversation entity tenant_id | ⚠️ **無 tenant_id 欄位**（已確認） | AI-802 屬實，需補欄位 + migration |
| frontend STOMP 依賴 | ⚠️ frontend/package.json **無 `@stomp/stompjs`/`sockjs`** | M10 前端整合為 greenfield |

---

## 1. Sprint 25 目標

> **主題**: schema 漂移防線（品質守門）+ 多租戶隔離技術債清償 + M10 即時通訊前端化

Sprint 24 因本地 CI 無法偵測 schema 驗證錯誤，投入 9 個計畫外 commit 救火（Flyway V48~V55）。Sprint 25 的首要任務是**補上守門機制**，讓同類漂移在 push 前被攔下，避免重蹈覆轍。三大重點：

1. **schema 守門關卡**（AI-901，**P0**）：本地以 `ddl-auto=validate` 對 Flyway-migrated DB 啟動 backend，push 前攔截 entity↔migration 漂移
2. **技術債清償**（AI-902 / AI-802）：一次性盤點 entity↔migration 一致性；補齊 Conversation 多租戶隔離
3. **M10 IM Phase 2 前端**：前端接上 Sprint 24 的 STOMP 後端，實現即時收訊

---

## 2. Sprint 目標對齊

| 目標 | 對應 Action Item | 類型 |
|------|----------------|------|
| 本地 schema 漂移守門關卡 | AI-901（P0） | DevOps / 品質 |
| entity ↔ migration 一致性盤點 | AI-902（P1） | 技術改善 |
| Conversation tenant_id 補齊 | AI-802（P1） | 技術債 |
| M10 WebSocket 前端整合 | Sprint 24 Retro 預告 | P1 新功能 |
| SSH pre-push 優化 | AI-804（Buffer） | DevOps |
| M11 物流取消流程業務規則確認 | AI-903（Buffer） | 調查 |

---

## 3. Deferred Items 審查

> **參考**: [DEFERRED_ITEMS_TRACKER.md](./DEFERRED_ITEMS_TRACKER.md)

| 狀態 | 說明 |
|------|------|
| 高優先級 DEF | 無（DEF 全部清零） |
| 中優先級 DEF | 無 |
| 延續 Action Items | AI-802（Conversation tenant_id）、AI-804（SSH）、AI-903（M11 取消）自 Sprint 24 延續 |

---

## 4. User Stories

### US-001：本地 schema 漂移守門關卡（AI-901，P0）

> **SP**: 2 | **優先級**: P0 | **狀態**: ⬜ 待開始

**目標**: 新增一個本地驗證關卡，以生產相同的 `ddl-auto=validate` 模式對「Flyway 已 migrate 的資料庫」啟動 backend，讓 entity 與 migration 的型別漂移／缺漏建表在 **push 前**就被攔截，不再到 GitHub E2E backend 啟動時才爆發。

**根因回顧**（現況調查）:
- `application-integration-test.yml` 使用 `ddl-auto: update` → Hibernate 自動補欄位/建表，**遮蔽漂移**
- `application.yml`（生產 / E2E profile）使用 `ddl-auto: validate` → 嚴格驗證，漂移即啟動失敗
- 本地 `act` 跑的整合測試走 update profile，因此抓不到漂移

**AC-001-1**: 新增 Makefile target（建議 `validate-schema`）：啟動 PostgreSQL（沿用既有 `postgres` image，遵守 Docker 政策不新增 image）→ Flyway migrate → Spring Boot 以 `ddl-auto=validate` 啟動，啟動成功即代表 schema 對齊
**AC-001-2**: 守門有效性驗證 — 故意製造一處 entity/migration 漂移，該 target 必須**失敗**並指出漂移欄位（負向測試）
**AC-001-3**: 文件化使用方式（何時執行、是否整合進 pre-push hook、與 `validate-all` 的關係），更新至 [docs/08_deployment/](../08_deployment/) 或 [docs/06_quality/](../06_quality/)

**技術方向**:
- 優先重用現有 docker-compose 的 postgres 服務 + Flyway，避免新增基礎設施
- 評估是否將此關卡輕量化後納入 pre-push（注意 push 時間成本，可先做為獨立 `make` 指令）

---

### US-002：entity ↔ migration 一致性盤點（AI-902，P1）

> **SP**: 2 | **優先級**: P1 | **狀態**: ⬜ 待開始

**目標**: 一次性盤點所有 `@Entity` 欄位型別與 Flyway migration DDL 是否吻合，建立對照清單，杜絕 ARRAY/jsonb、tags 型別等漂移復發。與 US-001 互補：US-001 是**守門機制**，US-002 是**一次性清盤 + 修復殘留**。

**AC-002-1**: 盤點所有 `@Entity`，重點檢查 collection / jsonb / array / enum / 型別轉換欄位對照 migration DDL
**AC-002-2**: 產出 entity↔migration 對照清單文件（[docs/06_quality/](../06_quality/) 或 [docs/02_architecture/](../02_architecture/)）
**AC-002-3**: 若 US-001 守門關卡掃出殘留漂移，補 migration 修復（從 **V56** 起）並使 `validate-schema` 通過

**依賴**: 建議在 US-001 完成後執行（用守門關卡掃出全部殘留漂移點）

---

### US-003：Conversation 多租戶隔離 — tenant_id 補齊（AI-802，P1）

> **SP**: 2 | **優先級**: P1 | **狀態**: ⬜ 待開始

**目標**: Conversation entity 補 `tenant_id` 欄位 + migration + repository/service 租戶範圍化。Sprint 24 WebSocket 廣播上線後，跨租戶訊息洩漏風險上升，租戶隔離更為關鍵。

**現況**（已確認）: `Conversation.java` 目前**無 tenant_id 欄位**（僅 listingId/orderId/initiatorId/recipientId 等）。

**AC-003-1**: Conversation entity 新增 `tenantId` 欄位 + **V56** migration（含既有資料回填策略，預設租戶或由 initiator 推導）
**AC-003-2**: `ChatService` 建立 conversation 時自 `TenantContext` 設定 `tenantId`
**AC-003-3**: 查詢與 STOMP 廣播以 `tenantId` 範圍隔離；整合測試驗證跨租戶不可見（使用 `TestSecurityContextHelper`）

**風險**: 既有 conversation 資料無 tenant_id，回填策略需先確認（預設 system tenant vs 由 initiator user 的 tenant 推導）

---

### US-004：M10 WebSocket 前端整合（@stomp/stompjs，P1）

> **SP**: 3 | **優先級**: P1 | **狀態**: ⬜ 待開始

**目標**: 前端接上 Sprint 24 的 STOMP 後端，訂閱 `/queue/conversations/{conversationId}/messages` 即時收訊，完成 M10 IM Phase 2 的前端閉環。

**現況**（已確認）: frontend 無 `@stomp/stompjs` / `sockjs-client` 依賴，前端 STOMP 為 greenfield。

**AC-004-1**: frontend 新增 `@stomp/stompjs`（+ `sockjs-client` fallback，對應後端 SockJS endpoint）
**AC-004-2**: 建立 STOMP client：CONNECT 時帶 `Authorization: Bearer {token}`，訂閱對話頻道
**AC-004-3**: 收到訊息即時更新對話 UI；處理斷線自動重連
**AC-004-4**: 元件測試或 Playwright E2E 驗證即時收訊（至少 happy path）

**注意**: 沿用 Sprint 24 後端的 destination 格式與 JWT 驗證機制，不變更後端契約。

---

### US-005（Buffer-A）：SSH pre-push 優化（AI-804，P2）

> **SP**: 1 | **優先級**: Buffer | **狀態**: ⬜ 待評估

**目標**: 評估並改善 SSH timeout 問題，避免 act CI 長時間執行導致 push SIGPIPE（exit 141）。

**AC-005-1**: 調查 SSH ControlMaster / ServerAliveInterval 配置可行性
**AC-005-2**: 評估切換 HTTPS push 的可行性
**AC-005-3**: 選擇方案並實作，確保下次 push 不再有 exit 141

---

### US-006（Buffer-B）：M11 物流取消流程業務規則確認（AI-903，P2）

> **SP**: 1 | **優先級**: Buffer | **狀態**: ⬜ 待評估（僅調查，不含實作）

**目標**: 與 PM 確認 M11 物流取消（`cancelShipment()`）業務規則，作為後續實作前置。**本 Sprint 僅調查與規則確認，不含實作**（規則未定，貿然實作風險高）。

**AC-006-1**: 確認 `CONFIRMED → CANCELLED`、`SHIPPING → CANCELLED` 的物流單狀態轉換規則
**AC-006-2**: 確認是否需呼叫物流供應商 API（HCT/TCAT stub）取消
**AC-006-3**: 產出規則確認結論；若規則明確且容量足夠，再決定是否納入實作或延後 Sprint 26

---

## 5. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 本地 schema 漂移守門關卡（AI-901） | 2 | **P0** |
| US-002 | entity ↔ migration 一致性盤點（AI-902） | 2 | P1 |
| US-003 | Conversation tenant_id 補齊（AI-802） | 2 | P1 |
| US-004 | M10 WebSocket 前端整合 | 3 | P1 |
| US-005 | SSH pre-push 優化（Buffer-A） | 1 | Buffer |
| US-006 | M11 取消流程業務規則確認（Buffer-B） | 1 | Buffer |
| **P0+P1 合計** | | **9 SP** | |
| **含 Buffer 合計** | | **11 SP** | |

> Velocity 對齊：Sprint 21/23 = 12 SP（100%），Sprint 24 = 7 SP 承諾（100%，但 Buffer 被 E2E 救火佔用）。
> Sprint 25 承諾 9 SP，介於兩者之間且保留 2 SP Buffer。US-001（P0 守門）為最高優先，必須先完成。

---

## 6. 執行順序建議

```
US-001（schema 守門關卡，P0）  → 最先，本 Sprint 最高優先，建立防線
US-002（entity↔migration 盤點）→ 用 US-001 守門關卡掃出全部殘留漂移並修復
US-003（Conversation tenant_id）→ 多租戶隔離技術債，WebSocket 廣播後更關鍵
US-004（M10 WebSocket 前端）    → P1 新功能，接上 Sprint 24 後端
US-005（Buffer-A：SSH）         → Buffer，P0+P1 完成後啟動
US-006（Buffer-B：M11 取消調查）→ Buffer，最後評估容量
```

---

## 7. 依賴與風險

| 風險 | 機率 | 影響 | 緩解措施 |
|------|------|------|---------|
| schema 守門關卡需起本地 PostgreSQL + Flyway，環境配置複雜 | 中 | 中 | 優先重用既有 docker-compose `postgres` 服務（遵守 Docker 政策，不新增 image），先做獨立 `make` 指令再評估整合 pre-push |
| US-002 盤點掃出大量殘留漂移，修復量超估 | 中 | 中 | 先跑 US-001 守門關卡量化漂移數，若過多則分批，剩餘列 DEF |
| Conversation tenant_id 既有資料回填策略未定 | 中 | 中 | AC-003-1 先確認回填策略（system tenant vs initiator 推導）再寫 migration |
| M10 前端 SockJS fallback / JWT header 傳遞相容性 | 中 | 中 | 沿用後端既有契約，先 happy path E2E，複雜場景可切 Sprint 26 |
| M11 物流取消業務規則未定義 | 高 | 中 | Buffer-B 僅調查確認，不貿然實作 |

---

## 8. Definition of Done（Sprint 25）

- [ ] US-001~004 所有 AC 達成（P0+P1）
- [ ] `mvn compile` → 0 errors
- [ ] Checkstyle → 0 violations
- [ ] 所有新增測試通過
- [ ] 既有測試無退步（`@Test` 靜態計數 ≥ 652，無刪減）
- [ ] catch(Exception) 生產程式碼 **0 處**
- [ ] @Deprecated 生產程式碼 **0 處**
- [ ] **schema 守門關卡（US-001）可實際攔截漂移**（負向測試通過）
- [ ] `make validate-all` 完整 act CI 通過
- [ ] Sprint 25 Review 文件建立（SPRINT_25_REVIEW.md）
- [ ] Sprint 25 Retrospective 文件建立（SPRINT_25_RETRO.md）
- [ ] Sprint 25 Release Notes 建立

---

## 9. Action Items 追蹤（來自 Sprint 24 Retro）

| AI ID | 內容 | Sprint 25 對應 US |
|-------|------|-----------------|
| AI-901 | 本地補 schema 驗證關卡 | US-001（**P0**） |
| AI-902 | entity ↔ migration 一致性盤點 | US-002（P1） |
| AI-802 | Conversation tenant_id 評估 | US-003（P1） |
| AI-804 | SSH timeout pre-push hook 優化 | US-005（Buffer-A） |
| AI-903 | M11 物流取消流程業務規則確認 | US-006（Buffer-B） |

---

**文件版本**: v1.0
**建立日期**: 2026-06-29
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + Claude Code
