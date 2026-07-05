# Sprint 66 計劃 / Sprint 66 Plan

> **Sprint 編號**: Sprint 66
> **期間**: 2028-04-23 ~ 2028-05-06 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-05
> **基於**: 使用者指示「廣泛盤點其他模組的隱藏測試缺口」，全面盤點後發現規模遠超單一 Sprint，本次為多 Sprint 測試強化計劃的第一階段
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 全面盤點結果 | ✅ 完成：排除已處理模組（admin/analytics/knowledge/faq/seller）後，發現 **order/auth/payment/booking/review/product/cms/chat/logistics/notification/promo/oauth 等超過 15 個核心 Service 完全零單元測試**，其中 ERP 模組（Supplier/StockMovement/PurchaseOrder/Inventory）連測試目錄都不存在。規模遠超單一 Sprint，需拆為多 Sprint 逐步清償 |
| 額外發現真實 bug | ✅ `ProductService.getProducts` 的 `keyword` 參數是死碼——關鍵字搜尋分支呼叫的方法與無篩選的 else 分支完全相同，等同「搜尋功能永遠失效，直接回傳全部上架商品」。比 `AnalyticsService.granularity` 死碼更嚴重（會誤導使用者以為搜尋有效）|
| Sprint 66 範圍界定 | ✅ 本 Sprint 優先處理：(a) `ProductService` 死碼修復（真實 bug，低成本高價值）、(b) `AuthService`（認證流程，5 方法，零測試風險最高、規模適中）。**`OrderService`/`PaymentStateService`/`BookingService`（含 RoomCalendarService）/ERP 模組因規模較大（各 7-10+ 方法或整個模組零測試），排入 Sprint 67+ 逐步處理**，避免單 Sprint 塞入過多高風險變更 |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. Sprint 66 目標

> **主題**: 多 Sprint 測試強化計劃（第一階段）——ProductService 搜尋死碼修復 + AuthService 測試從 0 建立

修正 `ProductService.getProducts` 的關鍵字搜尋死碼（真實 bug）；為零測試覆蓋的 `AuthService`（註冊/登入/換發 token/登出/取得當前使用者）建立完整單元測試。

---

## 2. User Story

### US-001：修正 ProductService.getProducts 的關鍵字搜尋死碼

> **SP**: 3 | **優先級**: P1 | **狀態**: ✅ 完成

**AC-001-1**: `ProductRepository` 新增 `searchByTenantIdAndKeyword`，依 `Listing.title`/`Listing.description`（大小寫不敏感）比對關鍵字。

**AC-001-2**: `ProductService.getProducts` 的 `keyword` 分支改用新方法，真正依關鍵字過濾（修正前與 else 分支完全相同，keyword 從未被使用）。

**AC-001-3**: 新增/補強 `ProductServiceTest`，涵蓋關鍵字命中/不命中案例，證明修正前後的行為差異（回歸測試）。

---

### US-002：AuthService 單元測試從 0 建立

> **SP**: 5 | **優先級**: P1 | **狀態**: ✅ 完成

**AC-002-1**: 新增 `AuthServiceTest.java`，涵蓋 `register`（成功、email 重複）、`login`（成功、密碼錯誤、帳號不存在）。

**AC-002-2**: 涵蓋 `refreshToken`（成功、token 失效/黑名單）、`logout`（單一 token / 全部 token 撤銷）。

**AC-002-3**: 涵蓋 `getCurrentUser`（成功、使用者不存在）。

**AC-002-4**: 測試風格比照既有 `AdminServiceTest`/`FaqServiceTest`，使用 Mockito mock repository/JWT 服務，不需真實 DB。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | ProductService 關鍵字搜尋死碼修復 | 3 | P1 |
| US-002 | AuthService 單元測試從 0 建立 | 5 | P1 |
| **合計** | | **8** | |

> **Velocity 參考**：貼近 Sprint 60-65（皆 8 SP）之單 Sprint 產能。US-001 為獨立小型 bug 修復，US-002 為認證核心邏輯測試建立，兩者互不依賴可並行驗證。

---

## 4. Definition of Done

- [x] US-001：`ProductRepository.searchByTenantIdAndKeyword` + `ProductService` 修正 + 測試（3 個測試）
- [x] US-002：`AuthServiceTest.java` 新建（16 個測試，涵蓋 5 個方法）
- [x] 後端單元 + 真 DB 整合全量回歸（`mvn verify -Pintegration-test`）**494 + 342 = 836 tests，0 fail**
- [x] `make validate-schema` 無漂移（本 Sprint 無新 migration）
- [x] Sprint 66 Review / Retro / Release Notes + trackers（含後續 Sprint 67+ 待處理清單）
- [ ]（檢查點）本 Sprint 收尾後立即 push

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端 | `domain/repository/ProductRepository.java`（新增查詢方法）、`core/product/ProductService.java`（修正死碼）|
| 後端測試 | `ProductServiceTest.java`（新增/擴充）、`AuthServiceTest.java`（新檔）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration、無前端變動**。

---

## 6. 後續 Sprint 待處理清單（多 Sprint 測試強化計劃）

依風險排序，供 Sprint 67+ 規劃參考：

1. **Sprint 67（建議）**：`PaymentStateService`（10 方法，含 Stripe 退款/對帳邏輯，金流核心）
2. **Sprint 68（建議）**：`OrderService`（7 方法，訂單狀態機核心）
3. **Sprint 69（建議）**：`BookingService` + `RoomCalendarService`（訂房核心，含 idempotency）
4. **Sprint 70+（建議）**：ERP 模組整體（Supplier/StockMovement/PurchaseOrder/Inventory，測試目錄完全不存在）
5. 其餘：`ReviewService`（14 方法）、`CmsService`（11 方法）、`ChatService`、`LogisticsService`、`NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService`

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
