# Sprint 33 計劃 / Sprint 33 Plan

> **Sprint 編號**: Sprint 33
> **期間**: 2027-01-17 ~ 2027-01-30 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_32_RETRO.md](../05_development/SPRINT_32_RETRO.md)（AI-1701/1702/1703/1704/1705）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認（現況調查）

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 累積批次狀態 | ⚠️ **Sprint 32（4 commit）未 push**（使用者選繼續累積到 S33） | 本 Sprint 含後端安全變更，完整守門到 push 才跑 → 逐項本地整合測試把關 |
| DEF-017 ERP 租戶隔離（AI-1701） | ✅ Sprint 32 已驗證**修法邏輯正確**（IT-M16-307 通過），缺 M16 seeding 重做 | 真因：@BeforeAll 種的 listing 在測試交易 findById 不可見 → 需重做 seeding |
| DEF-019 訂單付款/物流 IDOR（AI-1702） | ✅ Sprint 32 US-001 盤點已定位 | 讀取類（getOrderPaymentState）比照 getOrder；寫入類需角色語意設計 |
| 買家閉環 live 走查（AI-1703） | ⏸️ 需 live 環境人工 | 依 [SPRINT_32_REVIEW §6](../05_development/SPRINT_32_REVIEW.md) checklist |
| 活躍 DEF | 2（DEF-017 / DEF-019，皆安全項） | 本 Sprint 目標：清償兩者 |

> 🔴 **累積期後端安全變更風險控管**：Sprint 32 的 4 commit + 本 Sprint 後端變更，完整守門（validate-release）到 push 才跑。故每個後端安全變更後**本地跑相關整合測試**（DEF-017 → M16 targeted；DEF-019 → 訂單/付款 E2E）；沿用 Sprint 32「一次嘗試綠才留、紅則 revert 誠實延後」紀律，避免污染 main。

---

## 1. Sprint 33 目標

> **主題**: 安全修復收尾（訂單/ERP 全面租戶隔離）

延續 Sprint 32 清償 DEF-018，Sprint 33 把 Sprint 32 已**定位精確**的兩個安全項一次收尾：(1) DEF-017 ERP 手動庫存租戶隔離（修法已驗證，重做 M16 seeding）；(2) DEF-019 訂單付款/物流 IDOR（讀取類比照 getOrder、寫入類角色語意設計）。達成「訂單讀取 + ERP 庫存 + 付款/物流」全面租戶/擁有權隔離。

---

## 2. User Stories

### US-001：DEF-017 ERP 手動庫存租戶隔離（AI-1701，P1 安全）

> **SP**: 3 | **優先級**: P1（安全） | **狀態**: 🔶 調查完成 → **延 Sprint 34**（根因三層完整診斷，M16 tenant seeding 需整套重做，超出本 Sprint 容量）

> **🔴 執行結果（誠實記錄）**：本 Sprint 深入到第三層根因後，確認 M16 tenant seeding 需整套重做（見下 AC-001-1）：`@WithErpSecurity` 硬編的 FIXED_TENANT_ID 因 `Tenant.id` 為 @GeneratedValue 而在 tenants 表無對應列，listings.tenant_id 的 FK 使 listing 無法引用它。修法（null 安全租戶檢查）已驗證正確，但落地需以 raw SQL 種 FIXED_TENANT_ID 租戶列（比照 TestDatabaseInitializer）。依紀律三度於 commit 前本地攔下、誠實回退（main 未污染），延 Sprint 34 專注處理。

**目標**: `StockMovementService.createManualMovement` 擁有權檢查為 no-op。Sprint 32 曾驗證修法邏輯（跨租戶測試 IT-M16-307 通過），但套用後原 5 個同租戶 M16 測試回 500。本 US 釐清**真正根因**並重做 M16 seeding 使修法落地。

**AC-001-1（釐清根因）✅ 已完成**: Sprint 33 查 stack trace 確認——真因**非**原推測的「findById 查不到 → E_3003」，而是 **`listing.getTenantId()` 回傳 null → `.equals()` NPE → E-9900/500**。根本原因：`Listing.tenantId` 為 `@Column(insertable=false, updatable=false)` 影子欄位（`@ManyToOne tenant` 的 tenant_id），M16 @BeforeAll 用 `Listing.builder().tenantId(...)` 建 listing 但**未設 `.tenant` 關聯** → tenant_id 存成 null → 服務 fresh `findById` 讀回 tenantId=null。（IT-M16-307 當時通過，是因同交易 L1 快取回傳記憶體 builder 值，非 null。）
**AC-001-2（重做 seeding）**: M16ErpIntegrationTest / M16ErpE2ETest 的 @BeforeAll listing 改設 **`.tenant(testTenant)` 關聯**（使 tenant_id 正確寫入 DB），取代僅設無效的 `.tenantId`
**AC-001-3（落地修法）**: 套用修法（注入 ListingRepository + `!tenantId.equals(listing.getTenantId())` **null 安全**檢查 + 移除 `getTenantListings` placeholder）；加跨租戶越權測試 IT-M16-307（他租戶 SKU → 403）
**AC-001-4（不退步）**: `M16ErpIntegrationTest`（含 301~307）+ `M16ErpE2ETest` **全數通過**；順帶評估 E_3003 → 422 對應（資料完整性語意）
**AC-001-5**: 本地 `make test-db-up` + M16 targeted 測試通過；若 seeding 重做牽動過廣 → 誠實記錄部分交付（Rule 12）

---

### US-002：DEF-019 訂單付款/物流 IDOR 修復（AI-1702，P1 安全）

> **SP**: 3 | **優先級**: P1（安全） | **狀態**: 📋 Ready（讀取類低風險 / 寫入類需設計）

**目標**: Sprint 32 盤點揪出訂單付款/物流同類 IDOR。分讀/寫兩類修復。

**AC-002-1（讀取類，低風險）**: `PaymentStateService.getOrderPaymentState`（`GET /v2/orders/{id}/payment`）比照 getOrder 加擁有權檢查（買家限本人、admin 放行），越權回 403/E_1007；保留 404 not-found 語意
**AC-002-2（寫入類，角色語意）**: 盤點 `mockPaymentSuccess/Failure/Refund`（`/pay`、`/pay/fail`、`/refund`）與 `LogisticsService.createLogistics` 的呼叫者角色——買家付/取消自己的單、賣家建本租戶物流、admin 放行；設計對應擁有權/租戶檢查（**最小爆炸半徑**，避免打破既有 payment/logistics E2E）
**AC-002-3（不退步 + 越權測試）**: 補越權測試（買家 B 付/查 A 的單 → 403；跨租戶建物流 → 403）；既有 `OrderPaymentControllerE2ETest` / `BuyerOrderJourneyE2ETest` / M11 物流 E2E 全數不退步
**AC-002-4**: 本地跑訂單/付款/物流相關整合測試通過；若寫入類語意過廣或牽動過多 E2E → 讀取類先交付、寫入類誠實部分延後（Rule 12）

---

### US-003（Buffer）：買家閉環 live 走查 + 守門併發慣例（AI-1703/1704，P3）

> **SP**: 2 | **優先級**: Buffer/P3 | **狀態**: 📋 Ready

**目標**: 補齊 Sprint 32 未自動化的買家資料流驗證與流程慣例記錄。

**AC-003-1**: 依 [SPRINT_32_REVIEW §6](../05_development/SPRINT_32_REVIEW.md) checklist 於 live 環境人工走完整買家資料流（下單→付款→通知→物流→評價），記錄結果（AI-1703）
**AC-003-2**: 於流程文件記錄「validate-e2e 執行時勿 commit 前端檔（避免 pre-commit 前端 lint 與併發搶資源致 exit 128）」慣例（AI-1704）
**AC-003-3**: 僅在 US-001/002 完成且有餘裕時啟動；純文件/驗證，無生產碼風險

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 | 風險 |
|----|------|----|--------|------|
| US-001 | DEF-017 ERP 租戶隔離（AI-1701） | 3 | P1（安全） | 中（M16 seeding 重做；修法已驗證） |
| US-002 | DEF-019 付款/物流 IDOR（AI-1702） | 3 | P1（安全） | 中（寫入類角色語意 → 最小爆炸半徑） |
| US-003 | 買家 live 走查 + 慣例（AI-1703/1704，Buffer） | 2 | P3 | 低（文件/驗證） |
| **P1 合計** | | **6 SP** | | |
| **含 Buffer** | | **8 SP** | | |

> Velocity 對齊：S28~31 皆 8，S32 P1 5。本 Sprint 兩安全項皆已定位（非從零），承諾 P1 6 SP + Buffer 2 SP。

---

## 4. 執行順序建議

```
US-001（DEF-017 ERP）  → 🔴 最先：修法已驗證，重做 M16 seeding 即落地；本地 M16 targeted 把關
US-002（DEF-019 付款物流）→ 讀取類先（比照 getOrder）、寫入類角色語意設計；本地訂單/付款/物流測試把關
US-003（Buffer）        → live 走查 + 慣例文件，最後且擇機
```

---

## 5. 依賴與風險

| 風險 | 機率 | 影響 | 緩解措施 |
|------|------|------|---------|
| DEF-017 M16 seeding 根因比預期深（非單純 in-test listing） | 中 | 中 | AC-001-1 先釐清根因；比照已通過的 IT-M16-307 模式；牽動過廣則誠實部分交付 |
| DEF-019 寫入類修法打破 payment/logistics E2E | 中 | 中 | AC-002-2 最小爆炸半徑 + 盤點角色語意；讀取類先交付、寫入類可部分延後 |
| 累積期後端安全變更無完整守門即時驗證 | 高 | 中 | 每項本地跑相關整合測試（M16 / 訂單付款物流）；沿用「綠才留、紅則 revert」紀律 |
| 累積批次再擴大（S32 4 + S33） | 中 | 低 | 本 Sprint 後徵詢一起 push（完整守門一次驗證整批） |
| 完整守門偶發 M15 flaky 擋 push | 低 | 低 | 已記憶：僅該 spec 失敗時先重跑 validate-release |

---

## 6. Definition of Done（Sprint 33）

- [ ] US-001~002（P1 安全）所有 AC 達成或誠實部分交付；US-003（Buffer）完成或誠實延後
- [ ] `mvn compile` 0 errors；Checkstyle 0 violations
- [ ] `@Test` 靜態計數淨增（DEF-017 越權 + DEF-019 越權測試）；**既有測試無退步**（M16 / 訂單付款物流 E2E）
- [ ] catch(Exception) 生產 0、@Deprecated 生產 0
- [ ] 後端安全變更本地跑相關整合測試；schema 若變更跑 make validate-schema
- [ ] pre-push v5 完整守門（make validate-release）綠燈後 push（累積 S32+S33，檢查點徵詢後）
- [ ] Sprint 33 Review / Retrospective / Release Notes 建立

---

## 7. Backlog / Action Item 對應

| 來源 | 內容 | Sprint 33 US |
|------|------|-------------|
| AI-1701 / DEF-017 | ERP M16 seeding 重做 + 落地修法 | US-001（P1 安全） |
| AI-1702 / DEF-019 | 付款/物流 IDOR 修復 | US-002（P1 安全） |
| AI-1703 | 買家閉環 live 手動走查 | US-003（Buffer） |
| AI-1704 | 守門與 commit 併發慣例記錄 | US-003（Buffer） |
| AI-1705 | 守門腳本最小回歸 | 順延（P3，連八 Sprint 未動；本 Sprint 聚焦安全收尾） |

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
