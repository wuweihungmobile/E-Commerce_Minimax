# Sprint 31 計劃 / Sprint 31 Plan

> **Sprint 編號**: Sprint 31
> **期間**: 2026-12-20 ~ 2027-01-02 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_30_RETRO.md](../05_development/SPRINT_30_RETRO.md)（AI-1501/1502/1503）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認（現況調查）

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| EPIC-BUYER 狀態 | ✅ 前端閉環（#1–#6）Sprint 29+30 完成 | 重心轉「驗證 + 後端補強」 |
| 本 Sprint 性質 | ⚠️ **含後端變更**（與 S29/30 純前端不同） | 風險較高，需本地整合測試 + validate-schema |
| roomTitle 缺口（AI-1502） | ✅ 確認 `BookingService.toBookingListResponse()` 未查 listing title | 低風險小修（注意 N+1 → 批次查詢） |
| audit log（DEF-016） | ✅ 確認無 AuditLog entity/repo/migration，僅 `log.info` | 需 V57 migration（最新 V56）+ 改 AdminService |
| 買家流程 E2E（AI-1501） | ✅ 各模組獨立 E2E 齊，但**無串接的買家全流程測試** | 補 BuyerJourneyE2ETest（test-only） |
| 活躍 DEF | 2（DEF-016 本 Sprint 處理 / DEF-017 暫緩） | DEF-017 需 M16 測試資料重做，風險高，順延 |

> 🔴 **累積期風險控管**：Sprint 29+30 已累積 9 commit 未 push（使用者選繼續累積）。本 Sprint 含後端/schema 變更，完整守門（validate-release）要到 push 才跑 → 每個後端變更後**本地手動跑相關整合測試**；schema 變更後**必跑 `make validate-schema`**（依教訓：本地 act ddl-auto=update 抓不到 schema-validation）。

---

## 1. Sprint 31 目標

> **主題**: 買家閉環後端驗證 + 品質/後端小補強

EPIC-BUYER 前端閉環完成後，Sprint 31 轉向：(1) 以自動化 E2E 測試驗證買家全流程後端（AI-1501）；(2) 補後端小缺口 roomTitle（AI-1502）；(3) 落實 DEF-016 audit log 持久化。皆以「安全優先、逐項本地驗證」推進。

---

## 2. User Stories

### US-001：BookingListResponse roomTitle 填充（AI-1502，P1）

> **SP**: 2 | **優先級**: P1 | **狀態**: 📋 Ready

**目標**: `BookingService.toBookingListResponse()` 未填 roomTitle（前端 S30 以「訂房 #id」降級）。補填 room title，讓「我的預訂」列表顯示房型名稱。

**AC-001-1**: `toBookingListResponse` 填入 roomTitle（來源同 `toBookingResponse`：Listing.title）
**AC-001-2**: **避免 N+1** —— 列表以批次查詢（collect roomListingIds → `findAllById` → map）取代逐筆查詢
**AC-001-3**: 補單元/整合測試驗證列表回傳 roomTitle 非 null；既有 BookingControllerE2ETest 不退步
**AC-001-4**: 本地 `make test-db-up` + 相關 booking 整合測試通過

---

### US-002：買家閉環後端 E2E 測試（AI-1501 自動化，P1）

> **SP**: 3 | **優先級**: P1 | **狀態**: 📋 Ready

**目標**: 各模組獨立 E2E 齊，但無串接的買家全流程測試。補 `BuyerJourneyE2ETest`：下單 → 付款 → 物流 → 評價 一條龍，驗證狀態轉移與租戶/權限隔離。**test-only，不動生產碼**。

**AC-002-1**: 新增 `BuyerJourneyE2ETest`：買家註冊/登入 → 建立商品訂單 → Mock 付款（CREATED→PAID）→（賣家）建立物流 → 買家查物流軌跡 → 訂單完成後買家評價
**AC-002-2**: 驗證每步狀態轉移正確；驗證買家只能操作自己的訂單（權限/租戶隔離）
**AC-002-3**: 測試沿用既有整合測試基礎（`@ActiveProfiles("integration-test")`、TestDatabaseInitializer）；`@Test` 淨增
**AC-002-4**: 本地 `mvn verify -Pintegration-test`（相關測試）通過

---

### US-003（Buffer）：DEF-016 Admin audit log 持久化（P2）

> **SP**: 3 | **優先級**: Buffer/P2 | **狀態**: 📋 Ready（風險控管：schema 變更）

**目標**: AdminService 的 audit 僅 `log.info`（無持久化）。建 AuditLog entity + repository + V57 migration，將關鍵管理操作寫入 DB。

**AC-003-1**: 新增 `AuditLog` entity（id, tenantId, userId, action, entityType, entityId, oldValue, newValue, reason, createdAt）+ `AuditLogRepository`（分頁 + findByTenantId）
**AC-003-2**: 新增 `V57__Create_AuditLog_Table.sql`（含 index）；**entity↔migration 欄位一致**
**AC-003-3**: AdminService 關鍵操作（租戶審核/駁回/狀態更新、用戶狀態、feature toggle）寫入 AuditLog（保留既有 log.info）
**AC-003-4**: 補測試驗證 audit 寫入；**必跑 `make validate-schema`**（entity↔migration 漂移守門）+ AdminService 整合測試
**AC-003-5**: 若改動面超出 Buffer 或 schema 驗證受阻 → 誠實記錄、部分交付或延後（Rule 12），不硬塞

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 | 風險 |
|----|------|----|--------|------|
| US-001 | roomTitle 填充（AI-1502） | 2 | P1 | 低（N+1 注意） |
| US-002 | 買家閉環 E2E 測試（AI-1501） | 3 | P1 | 低（test-only） |
| US-003 | DEF-016 audit log 持久化（Buffer） | 3 | P2 | 中（schema + 廣用 service） |
| **P1 合計** | | **5 SP** | | |
| **含 Buffer** | | **8 SP** | | |

> Velocity 對齊：S28=8 / S29=8 / S30=8。承諾 P1 5 SP + Buffer 3 SP。後端 Sprint 保守估計，Buffer 視風險決定。

---

## 4. 執行順序建議

```
US-001（roomTitle）    → 🔴 最先：安全小修，立即驗證循環
US-002（E2E 測試）     → test-only 不動生產碼，安全補覆蓋
US-003（DEF-016 audit）→ Buffer：schema 變更，最後做且逐步 validate-schema
```

---

## 5. 依賴與風險

| 風險 | 機率 | 影響 | 緩解措施 |
|------|------|------|---------|
| 累積期後端變更無完整守門即時驗證 | 高 | 中 | 每個後端變更本地跑相關整合測試；schema 變更跑 make validate-schema |
| DEF-016 schema 漂移（entity↔migration 不一致） | 中 | 中 | AC-003-4 強制 make validate-schema；對照 entity 欄位與 V57 |
| 改 AdminService 打破既有整合測試（如 US-004 ERP 教訓） | 中 | 中 | 保留既有行為（log.info 不動）僅「新增」寫入；跑 AdminServiceIntegrationTest |
| BuyerJourneyE2ETest 測試資料/權限設定複雜 | 中 | 低 | 沿用既有 E2E 測試的註冊/租戶設定模式 |
| roomTitle N+1 效能 | 低 | 低 | 批次 findAllById |

---

## 6. Definition of Done（Sprint 31）

- [ ] US-001~002（P1）所有 AC 達成；US-003（Buffer）完成或誠實記錄
- [ ] `mvn compile` 0 errors；Checkstyle 0 violations
- [ ] `@Test` 靜態計數淨增（US-002 E2E + US-001/003 測試）；既有測試無退步
- [ ] catch(Exception) 生產 0、@Deprecated 生產 0
- [ ] 後端變更本地跑相關整合測試；**US-003 schema 變更跑 make validate-schema**
- [ ] pre-push v5 完整守門（make validate-release）綠燈後 push（累積 S29+30+31）
- [ ] Sprint 31 Review / Retrospective / Release Notes 建立

---

## 7. Backlog / Action Item 對應

| 來源 | 內容 | Sprint 31 US |
|------|------|-------------|
| AI-1502 | roomTitle 填充 | US-001 |
| AI-1501 | 買家閉環 E2E 驗證（自動化） | US-002 |
| DEF-016 / AI-1503 | Admin audit log 持久化 | US-003（Buffer） |
| DEF-017 | ERP 租戶隔離 | 順延（高風險，需 M16 測試資料重做） |

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
