# Sprint 32 計劃 / Sprint 32 Plan

> **Sprint 編號**: Sprint 32
> **期間**: 2027-01-03 ~ 2027-01-16 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_31_RETRO.md](../05_development/SPRINT_31_RETRO.md)（AI-1601/1602/1603/1605）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認（現況調查）

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 累積批次狀態 | ✅ **Sprint 29+30+31（14 commit）已 push，工作樹乾淨、與 origin/main 同步** | 脫離累積期，**本 Sprint 可即時跑完整守門** |
| 本 Sprint 性質 | ⚠️ **含後端生產碼變更（安全修復）** | 每個後端變更本地跑整合測試；push 前 make validate-release |
| DEF-018 getOrder IDOR（AI-1601） | ✅ 確認 `OrderService.getOrder`→`findOrderById` 僅 `findById(orderId)`，無擁有權檢查 | P1 安全，之前因「累積期不宜無完整守門即改」而延後，**現條件成熟** |
| DEF-017 ERP 租戶隔離（AI-1603） | ✅ 確認 `StockMovementService.createManualMovement` 擁有權檢查為 no-op | 高風險，修法須連 M16 測試資料一併重做，列 Buffer 擇機 |
| 買家閉環前端驗證（AI-1602） | ✅ S29/30 前端頁面（訂單/通知/預訂/評價/物流）已建，但**無瀏覽器 E2E 覆蓋** | 補前端 e2e spec 或手動走查 checklist |
| 活躍 DEF | 2（DEF-017 ERP / DEF-018 IDOR） | 本 Sprint 目標：清償 DEF-018（P1），擇機清 DEF-017 |

> 🔴 **本 Sprint 與 S29~31 的關鍵差異**：已脫離「累積未 push」狀態，可在每次高風險變更後即時跑完整守門（make validate-release）。故將延後最久、風險最高、但價值最高的**安全修復**（DEF-018 P1）列為首要——正是「在可跑完整守門的節奏下逐一清償安全發現」的時機。

---

## 1. Sprint 32 目標

> **主題**: 安全修復 + 買家閉環驗證

延續「補測試揪 bug」揪出的安全發現（DEF-013/017/018），Sprint 32 轉向**清償安全技術債**：(1) 修 DEF-018 getOrder IDOR（P1 安全，最小爆炸半徑設計 + 完整守門）；(2) 補買家閉環前端 E2E 驗證（AI-1602）；(3) 擇機清 DEF-017 ERP 租戶隔離（Buffer，高風險）。全程「安全優先、逐項本地驗證、完整守門把關」。

---

## 2. User Stories

### US-001：DEF-018 getOrder 擁有權修復（AI-1601，P1 安全）

> **SP**: 3 | **優先級**: P1（安全） | **狀態**: 📋 Ready（風險控管：廣用方法）

**目標**: `OrderService.getOrder`→`findOrderById` 僅 `findById(orderId)`、僅需 `order:read` 權限、無 per-user/tenant 過濾 → 任何登入者可取任意訂單詳情（含 state logs）= IDOR。修復擁有權檢查，杜絕越權讀取。

**AC-001-1（盤點先行）**: 盤點 `getOrder`/`findOrderById` 所有呼叫者（買家 `OrderController`、賣家取單、admin、`OrderPaymentController`）與相關 E2E，記錄於 commit message / 程式註解（比照 DEF-017/ERP 教訓：先懂耦合再改）
**AC-001-2（最小爆炸半徑）**: 於**買家取單入口**加擁有權檢查（買家限本人 `userId.equals(order.getUserId())`、admin 放行、賣家依盤點結果）。**優先不動共用 `findOrderById`**——改在 controller 層或新增 buyer-scoped 方法，避免波及 payment/admin/賣家流程
**AC-001-3**: 未授權存取回 **403**（沿用 `cancelOrder` 的 `E_1007` 擁有權錯誤碼或適當錯誤碼），與既有取消流程一致
**AC-001-4（不退步）**: 補 E2E —— 買家 A 無法取買家 B 的訂單（403）；買家取自己訂單成功；**既有訂單 E2E（`OrderControllerE2ETest` / `BuyerOrderJourneyE2ETest` / payment 相關）全數不退步**
**AC-001-5**: 本地 `make test-db-up` + 訂單相關整合測試通過；**push 前 `make validate-release` 完整守門綠燈**

---

### US-002：買家閉環前端 E2E 驗證（AI-1602，P1）

> **SP**: 2 | **優先級**: P1 | **狀態**: 📋 Ready

**目標**: S29/30 建的買家頁面（訂單列表/詳情、通知收件匣、我的預訂、評價、物流追蹤）**從未於瀏覽器端到端驗證**。補前端 e2e spec 覆蓋核心買家頁面渲染與空/認證狀態，納入 validate-e2e strict 守門。**前端 test-only，不動生產碼。**

**AC-002-1**: 新增前端 e2e spec（Playwright，`frontend/e2e/`），覆蓋買家頁面：訂單列表/詳情、通知收件匣、我的預訂、評價、物流追蹤 —— 已登入渲染、空狀態、無 crash（沿用既有 `at-m*.spec.ts` 登入 helper 與慣例）
**AC-002-2**: 納入 `make validate-e2e` strict 守門（0 failed 基準）
**AC-002-3（誠實降級）**: 若後端資料 seeding 過重致無法穩定自動化 → 降級為**手動走查 checklist + 頁面載入 smoke**（下單→付款→通知→物流→評價），誠實記錄未自動化部分（Rule 12），不硬塞不穩定測試（比照 M15 flaky 教訓）
**AC-002-4**: 對照既有 e2e 慣例，不引入新的不穩定 spec；本地 `make validate-e2e` 通過

---

### US-003（Buffer）：DEF-017 ERP 手動庫存租戶隔離（AI-1603，P2 安全）

> **SP**: 3 | **優先級**: Buffer/P2（安全） | **狀態**: 📋 Ready（風險控管：需 M16 測試資料重做）

**目標**: `StockMovementService.createManualMovement` 擁有權檢查為 no-op（`getTenantListings` 回 tenantId + 空 if body），未把關租戶隔離。修復擁有權檢查 —— 但 US-004 曾實作 `ListingRepository` 檢查即**打破 5 個 M16 整合測試**（測試資料建 SKU 未建對應 listing），故須連測試資料一併重做。

**AC-003-1（盤點先行）**: 釐清 ERP `inventory→sku→listing→tenant` 關聯與 M16 測試資料建置方式（為何回 500）
**AC-003-2**: 設計正確的租戶擁有權檢查（庫存異動限本租戶之 listing）+ **重做 M16 測試資料**（補齊對應 listing 列）
**AC-003-3（不退步）**: `M16ErpIntegrationTest ×4` + `M16ErpE2ETest ×1` 全數通過；未授權跨租戶異動回適當錯誤碼
**AC-003-4（誠實延後）**: 若測試資料重做超出 Buffer 容量或牽動過廣 → **誠實延後**（Rule 12），部分交付或回退，不硬塞
**AC-003-5**: 僅在 US-001/002 完成且有餘裕時啟動；schema 若有變更必跑 `make validate-schema`

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 | 風險 |
|----|------|----|--------|------|
| US-001 | DEF-018 getOrder IDOR 修復（AI-1601） | 3 | P1（安全） | 中（廣用方法 → 最小爆炸半徑設計） |
| US-002 | 買家閉環前端 E2E 驗證（AI-1602） | 2 | P1 | 低（test-only；seeding 過重則降級） |
| US-003 | DEF-017 ERP 租戶隔離（AI-1603，Buffer） | 3 | P2（安全） | 高（M16 測試資料重做） |
| **P1 合計** | | **5 SP** | | |
| **含 Buffer** | | **8 SP** | | |

> Velocity 對齊：S27=5 / S28=8 / S29=8 / S30=8 / S31=8。承諾 P1 5 SP + Buffer 3 SP。兩項安全修復皆高風險，Buffer（DEF-017）視 P1 完成後餘裕決定。

---

## 4. 執行順序建議

```
US-001（DEF-018 IDOR）  → 🔴 最先：延後最久、價值最高的 P1 安全修復；盤點呼叫者 → 最小爆炸半徑 → 完整守門
US-002（買家 E2E）      → test-only 前端驗證，補瀏覽器端覆蓋
US-003（DEF-017 ERP）   → Buffer：高風險，需 M16 測試資料重做，最後且擇機；不硬塞
```

---

## 5. 依賴與風險

| 風險 | 機率 | 影響 | 緩解措施 |
|------|------|------|---------|
| DEF-018 修法波及 payment/admin/賣家取單與多個訂單 E2E | 中 | 中 | AC-001-1 盤點先行；AC-001-2 最小爆炸半徑（不動共用 findOrderById，改 controller/buyer-scoped 方法）；完整守門攔驗 |
| DEF-017 M16 測試資料重做牽動過廣（重演 US-004 打破 5 測試） | 高 | 中 | 列 Buffer 擇機；AC-003-4 誠實延後不硬塞；US-001/002 完成才啟動 |
| 買家前端 E2E 資料 seeding 複雜致 spec flaky | 中 | 低 | AC-002-3 降級手動 checklist；不引入不穩定 spec（M15 教訓） |
| 完整守門偶發 M15 媒體庫 spec flaky 擋 push | 低 | 低 | 已記憶：僅該 spec 失敗時先重跑 validate-release（非本變更所致） |
| 安全修復回歸未被本地測試覆蓋 | 中 | 中 | 每個後端變更本地跑訂單/ERP 整合測試；push 前 make validate-release（act + schema + e2e） |

---

## 6. Definition of Done（Sprint 32）

- [ ] US-001（P1 安全）所有 AC 達成；US-002（P1）完成或誠實降級；US-003（Buffer）完成或誠實延後
- [ ] `mvn compile` 0 errors；Checkstyle 0 violations
- [ ] `@Test` 靜態計數淨增（US-001 越權 E2E + 前端 e2e spec）；**既有測試無退步**（訂單/payment/ERP E2E）
- [ ] catch(Exception) 生產 0、@Deprecated 生產 0
- [ ] 後端安全變更本地跑相關整合測試；schema 若變更跑 make validate-schema
- [ ] **pre-push v5 完整守門（make validate-release）綠燈後 push**（本 Sprint 脫離累積期，可即時把關）
- [ ] Sprint 32 Review / Retrospective / Release Notes 建立

---

## 7. Backlog / Action Item 對應

| 來源 | 內容 | Sprint 32 US |
|------|------|-------------|
| AI-1601 / DEF-018 | getOrder 擁有權修復（IDOR） | US-001（P1 安全） |
| AI-1602 | 買家閉環前端 E2E 驗證 | US-002（P1） |
| AI-1603 / DEF-017 | ERP 手動庫存租戶隔離 | US-003（Buffer，高風險） |
| AI-1604 | audit 覆蓋擴展評估 | 順延（P3，後續） |
| AI-1605 | 守門腳本最小回歸 | 順延（P3，連六 Sprint 未動；本 Sprint 聚焦安全） |

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
