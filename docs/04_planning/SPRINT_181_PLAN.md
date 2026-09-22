# Sprint 181 Plan — 金額竄改風險全掃（DEF-242~243）

**Sprint**: Sprint 181
**日期**: 2026-09-22

## 1. 起點

Sprint 180 修復 `DEF-237~240` 後，`DEFERRED_ITEMS_TRACKER.md` 僅剩 `DEF-235`/`DEF-236`/`DEF-241` 三項低優先級「不排入排程」項目。「同一請求內多個關聯 ID 只驗證其中一個」這個角度經兩輪（`DEF-231` + Sprint 180）掃描後候選集可能已收斂，本輪選定全新角度：「是否有端點信任呼叫端傳入的金額欄位，而非一律由伺服器端重新計算」——這是電商平台的經典高風險缺陷類型，且尚未被系統性掃描過。

## 2. 掃描方法與結果

派出 2 個背景唯讀調查 agent：

- **Agent 1（訂單/購物車/促銷/訂房/退貨）**：找到 `DEF-242`（`PricingService` 定價規則缺租戶擁有權檢查，形同間接金額竄改）與 `DEF-243`（`PaymentService.processRefund` 退款金額無上限）。購物車/訂單/促銷/訂房/退貨其餘部分查證後排除，金額皆源自伺服器端權威計算。
- **Agent 2（ERP/結算/供應商/物流/Stripe）**：獨立發現同一個 `DEF-243`（交叉驗證），另確認採購單/供應商/結算/物流運費/Stripe Connect/Webhook 皆安全。

兩個 agent 對 `PaymentService.processRefund` 的發現完全一致（各自獨立讀完整段程式碼後得出相同結論），提高了此缺陷判定的信心。

## 3. 修復內容

### 3.1 DEF-242：定價規則租戶擁有權檢查缺失

`PricingService.createRule`/`updateRule`/`deleteRule`/`setCalendarPrice`/`overridePrice` 五個寫入方法完全沒有驗證目標 listing/既有規則是否屬於呼叫者租戶。攻擊鏈：任一有 `room:create`/`product:create` 權限的賣家，對「他租戶」的 `roomListingId`/`listingId` 呼叫 `createRule`，植入一條 `MANUAL_OVERRIDE`（`config.price=0.01`）或任何漲/跌價規則；`PricingRuleRepository` 的查詢方法（`findByListingIdAndIsActiveTrue`/`findActiveRulesForDateRange` 等）完全不過濾 `tenantId`，這條規則會在受害商品被任何買家結帳時，被 `getEffectivePrice`（PRODUCT）/`calculatePrice`（ROOM）當作權威有效售價套用——等同不需要直接竄改金額欄位，就能操縱他人商品的實際成交價。

**修法**：新增兩個私有輔助方法：
- `checkListingTenantOwnership(targetListingId, isSuperAdmin)`：建立新規則時，反查 `request.getRoomListingId()`（未提供則退回 `request.getListingId()`）對應的 `Listing.tenantId`。
- `checkRuleTenantOwnership(resourceTenantId, isSuperAdmin)`：更新/刪除/覆蓋既有規則時，直接比對已知的 `PricingRule.tenantId`，不需額外查詢。

兩者皆比照本專案既有的 `isSuperAdmin` 旁路慣例（`BookingService.checkListingTenantOwnership`、`ProductSkuService` 等），`PricingController`/`DashboardPricingController` 對應端點新增 `@AuthenticationPrincipal UserPrincipal principal` 解析 `isSuperAdmin` 並傳入 Service。

**測試與紅燈先行的特殊處理**：這次修法變更了 5 個 `PricingService` public 方法的簽章（新增 `isSuperAdmin` 參數），若直接對整個檔案 `git stash` 做紅燈驗證，還原後的舊方法簽章會讓新增的測試呼叫產生**編譯錯誤**而非**行為差異**——編譯錯誤無法證明「修復前這個檢查不存在」，只能證明「參數數量不同」。改用替代做法：暫時把兩個新增的檢查方法本體短路為立即 `return`（保留方法簽章不動），重新編譯執行 6 個新測試案例，確認全數轉紅（5 個跨租戶案例 + 驗證 SUPER_ADMIN 旁路的案例維持綠燈），證實修復前這 5 條寫入路徑完全無租戶防護；還原真正邏輯後重新確認全數轉綠。

**連帶發現並修復的測試基礎設施缺口**：`M12PricingIntegrationTest`/`M12PricingProductIntegrationTest` 原本使用 Spring Security Test 內建的 `@WithMockUser` 模擬登入身分，其 `Authentication.getPrincipal()` 並非本專案的 `UserPrincipal` 型別。新增的 `@AuthenticationPrincipal UserPrincipal principal` 在型別不符時會解析為 `null`（Spring 預設行為），`principal.getRole()` 因此在 Controller 層直接 NPE，回應 500——這在 `mvn verify` 完整跑過一次才被抓到（本輪 `PricingServiceTest` 單元測試層級因直接呼叫 Service 方法，不經過 Controller 的 `@AuthenticationPrincipal` 解析，未能提前暴露這個問題）。修法：兩個整合測試改用本專案既有的 `@WithErpSecurity`（會建立真正的 `UserPrincipal` 並同步設定 `TenantContext`），並新增 `authorities()` 屬性讓它相容原本 `@WithMockUser` 帶的細粒度權限碼字串（如 `"room:create"`），同時補上 4 個 `createRule` 測試案例先前缺少的 `listingRepository.findById` stub。

### 3.2 DEF-243：舊版退款端點的擁有權與金額上限缺失

`PaymentService.processRefund`（`/v2/payments/refund`）完全沒有擁有權檢查——同檔案的 `processOrderPayment`/`processBookingPayment` 皆有 `checkOrderPaymentOwnership`/`checkBookingPaymentOwnership`（分別是 `DEF-019`/`DEF-023` 的既有修補），唯獨 `processRefund` 遺漏；且 `request.getAmount()` 沒有任何上限驗證，可指定超過 `payment.getAmount()` 的任意退款金額。查證確認前端已改走有完整防護的 `/v2/orders/{id}/refund`（`PaymentStateService.refundOrderPayment`，有 `checkOrderOwnership` + `resolveRefundAmount` 上限驗證 + 部分退款累計追蹤），`/v2/payments/refund` 前端零呼叫點，屬「新路徑修好、舊路徑被遺忘」的雙路徑遺留（同類見 `room-booking-dual-path-gotcha`、`m17-tenant-application-dual-flow`）。但此端點的 `@PreAuthorize("hasAuthority('order:update') or hasAuthority('booking:update')")` 仍對一般賣家角色開放，是真實可達的 REST 端點，故直接修復而非僅登記。

**修法**：重用既有的 `checkOrderPaymentOwnership`/`checkBookingPaymentOwnership`（不重新實作，維持與 `processPayment` 一致的擁有權語意），置於狀態檢查之前；新增退款金額不得超過 `payment.getAmount()` 的驗證（複用既有 `ErrorCode.E_6009`，與 `PaymentStateService.resolveRefundAmount` 使用同一錯誤碼）。

**NPath 複雜度踩坑**：初版直接在 `processRefund` 方法內用 `if/else if` 內聯處理「依 orderId/bookingId 解析對象並套用擁有權檢查」，`mvn verify` 的 checkstyle 階段回報 `NPathComplexityCheck` 超標（288 > 上限 200）。抽出獨立的 `resolveAndAuthorizeRefundTarget` 方法承載這段多分支邏輯，回傳一個內部靜態容器類別 `RefundTarget`（`order`/`booking` 至多其一非 null），`processRefund` 本身的分支數因此大幅下降，checkstyle 轉綠。

**測試**：新增獨立測試檔 `PaymentServiceRefundOwnershipTest`（6 案例：他人觸發訂單/訂房退款皆拒絕、本人/`admin` 皆放行、金額超額拒絕、未帶金額預設全額）。此方法簽章未變（`processRefund(PaymentDto.RefundRequest)` 不變），紅燈先行可直接用 `git stash` 整檔還原驗證，3 個案例（他人觸發訂單/訂房退款、金額超額）修復前皆失敗。

## 4. 測試總覽

- **紅燈先行**：`DEF-243`（簽章未變）用標準 `git stash` 整檔還原驗證；`DEF-242`（簽章變更）改用暫時短路檢查方法體的替代手法，理由見 §3.1。
- **新增測試**：`PaymentServiceRefundOwnershipTest`（新檔，6 案例）、`PricingServiceTest$RuleTenantOwnershipTests`（新增巢狀類別，6 案例）。
- **既有測試更新**：`PricingServiceTest` 既有 6 個 `createRule`/`updateRule` 呼叫點補上新增的 `isSuperAdmin` 參數（`false`），並新增類別層級 `@BeforeEach` 設定 `TenantContext`，`RuleCreationLimitAndConflictTests` 巢狀類別補上 `listingRepository.findById` stub，`RuleUpdateValidationTests` 的既有 `PricingRule` fixture 補上 `.tenantId(TENANT_ID)`。
- **整合測試修復**：`M12PricingIntegrationTest`（class 層級 `@WithMockUser`→`@WithErpSecurity`，4 個 `createRule` 測試補 `listingRepository` stub）、`M12PricingProductIntegrationTest`（1 個受影響的測試方法同樣處理）。`WithErpSecurity.java` 新增 `authorities()` 屬性，向後相容既有 3 處既有用法（不影響其行為）。

## 5. 驗證結果

`mvn -o clean verify`（`make test-db-up` 真實 postgres/redis）：**BUILD SUCCESS**，**1608 個單元測試（+12）+ 484 個整合測試（持平），0 failed**；checkstyle（main+test）**0 違規**；PMD 無新增問題。

過程中第一次跑完整 `mvn verify` 才發現 `M12PricingIntegrationTest`/`M12PricingProductIntegrationTest` 的 9 個測試回歸（單元測試層級的 `PricingServiceTest` 未能提前暴露，因為它直接呼叫 Service 方法、不經過 Controller 的 `@AuthenticationPrincipal` 解析路徑）——這提示：涉及 Controller 層 `@AuthenticationPrincipal`/認證相關的修改，即使單元測試全綠，仍必須完整跑過一次 `mvn verify`（含真實 Spring Security Filter Chain 的整合測試）才能確認沒有回歸。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-242`/`DEF-243`：狀態直接記為「✅ 已修復（Sprint 181）」，發現與修復同輪完成。

## 7. 誠實揭露總結

- `DEF-242` 是本專案首次發現「租戶隔離缺失」以**間接**方式造成金額竄改效果的案例——先前的同類缺陷（`DEF-231`/`DEF-237` 等）都是直接讓攻擊者竄改另一租戶的資料本身（庫存數量），這次則是讓攻擊者「污染」另一租戶商品的**計價依據**，實際受害的是買家（付了被操縱過的價格）與被植入規則的商品所屬租戶（收到不正確的金額）。這提示未來掃描類似風險時，除了直接檢查「金額欄位是否被客戶端覆寫」，也要檢查「客戶端能否寫入某個之後會被當作金額計算依據的資料」這個更間接的攻擊面。
- `DEF-242` 的修復方式（簽章變更）讓標準的 `git stash` 紅燈驗證手法失效，本輪記錄了替代做法（暫時短路檢查方法體），供未來遇到同類情境（修復需要變更既有 public 方法簽章）參考，避免因為紅燈驗證卡關而跳過這道紀律。
- `DEFERRED_ITEMS_TRACKER.md` 目前僅剩 `DEF-235`/`DEF-236`/`DEF-241` 三項低優先級「不排入排程」項目，Sprint 182 開工時仍需自選新掃描角度。
