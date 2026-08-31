# Sprint 100 Plan — 優惠券機制完整斷鏈修復（折扣落單 / 額度佔用 / 每人限用 / 取消退還）

**Sprint**: Sprint 100
**日期**: 2026-09-01
**AI 編號**: AI-2434
**主題**: 第八輪 PRD 全文掃描以「孤兒錯誤碼 → 零呼叫死碼 → 欄位零讀取」三個訊號連續命中，查出優惠券機制存在一條完整斷鏈——買家在購物車看到折扣、下單卻被收全額，且券的總量與每人限用上限完全形同虛設。**本輪為目前唯一直接涉及金錢收取正確性的缺口。**

---

## 1. 缺口盤點結果

### 掃描方法

延續 Sprint 95-99 累積的判讀技巧，本輪從**孤兒錯誤碼分析**起手（`ErrorCode.java` 定義但全庫從未被引用者，共 30 個），逐一判讀後鎖定優惠券區塊，再以「零呼叫死碼」與「欄位零讀取」交叉驗證。

**排除的偽陽性（誠實記錄）**：`E_8009`「無權操作他人客服工單」一度是最可疑的訊號（M18 為 Sprint 91-92 剛交付的模組，且與 `E_8006`/`E_8007` 地址那組成對定義的慣例不符）。實際逐方法檢視 `SupportTicketService`/`SupportMessageService` 後確認**擁有權檢查完整**——每個方法都以 `findByIdAnd<Owner>` 模式驗證歸屬，只是選擇回「找不到」(`E_8008`) 而非「無權」。此碼為冗餘而非缺口。這印證 Sprint 77 的教訓：不是每個可疑訊號都是漏洞，必須先證實。

### 確認的缺口（PRD 依據）

PRD §9.5.1 明文規格（原文）：

> **promoCode 驗證邏輯**：M05 訂單建立時，系統在校驗 `totalAmount` 之後、寫入訂單之前，依以下順序驗證促銷碼：
> 1. 檢查促銷碼是否存在且狀態為 ACTIVE
> 2. 檢查是否在有效時間範圍內（valid_from ~ valid_to）
> 3. 檢查是否已達使用上限（max_uses）
> 4. 套用折扣（按 discount_type 計算）
> 5. 折扣後金額不得為負

PRD §2630（RETAIL 取消補償邏輯）另訂：

> - 優惠券：若已使用促銷碼，則退還（視優惠規則）

| # | 缺口 | 證據 | 嚴重性 |
|---|------|------|--------|
| 1 | **買家看到折扣、下單被收全額** | `OrderService.createOrderFromCart` 取購物車用 `getCart()`（不含 promo 的版本），金額為 `Σsubtotal + shippingFee`，折扣從未扣除。而前端 `cart/page.tsx` 顯示「已套用優惠券，折扣 $XXX」、`checkout/page.tsx` 顯示 `finalAmount` | 🔴 **直接多收款** |
| 2 | **總量上限形同虛設** | `PromoService.incrementUsageCount()` 在 main 程式碼**零呼叫者**（僅自身單元測試呼叫）→ `current_usage_count` 永遠是 0 → `isUsageLimitReached()` 永遠 false → 限量券可被無限次使用 | 🔴 超發 |
| 3 | **每人限用完全未實作** | `max_usage_per_user` 自 V20 建表即存在（DB 欄位 + entity 欄位，預設 1），全庫**零讀取** | 🔴 超發 |
| 4 | **下單時不重新驗證促銷碼** | 只在加入購物車時驗過一次，券碼存於 Redis（購物車 TTL 內），期間過期/停用/售罄皆不會被攔 | 🟠 |
| 5 | **Order 無折扣欄位** | Order entity 無 `discount_amount`/`promo_code`，訂單無從追溯用了哪張券——導致 PRD §2630 的「退還優惠券」在資料上根本無法實作 | 🟠 |
| 6 | **下單後購物車券碼未清除** | `applyPromoCode` 寫入 Redis，下單只 `removeItem` 品項，券碼殘留 → 同一張券被下一張訂單繼續沿用 | 🟠 |

### 為什麼存活 99 個 Sprint 沒被發現

`M11CartPromoIntegrationTest`（既有唯一的優惠券整合測試）以 `@MockBean` 隔離了 `PromoService` 與 `PromoCodeRepository`，因此它驗證的是**購物車端點的行為**，而非優惠券資料真的有被寫入與計數；且該測試完全不涉及下單。這正是 Sprint 97 記錄過的警訊模式：「**所有相關測試都用固件繞過同一段業務邏輯**」本身就是缺口能長期存活的原因。

---

## 2. 使用者決策（AskUserQuestion 拍板）

三個分歧點在動工前已請使用者裁定，未自行假設：

1. **訂單欄位**：新增 `discount_amount` + `promo_code` 欄位（而非只調整 `total_amount` 不留痕跡）——支援 PRD §2630 退款退券與對帳追溯。
2. **每人限用納入本輪**：建立 `promo_code_usages` 表（PRD 未定義此表，為落實既有 `max_usage_per_user` 欄位所需的自行設計資料模型）。
3. **ROOM 訂單延後**：本輪只修 PRODUCT 結帳路徑；PRD US-010「作為預訂買家…結帳時套用優惠碼」需新增 API 契約與訂房結帳前端，規模明顯超出「修復斷鏈」，記錄為延後項目。

---

## 3. 實作內容

### 資料層（V70 migration）

- `orders` 新增 `promo_code VARCHAR(50)`、`discount_amount DECIMAL(12,2) NOT NULL DEFAULT 0.00`。
- 新建 `promo_code_usages`（`promo_code_id`/`user_id`/`order_id`/`status`/`used_at`/`revoked_at`），索引 `(promo_code_id, user_id, status)` 支撐每人限用計數。
- **軟撤銷而非實體刪除**：取消訂單退還額度時 `status` 轉 `REVOKED` 並保留紀錄，比照 Sprint 98 `tenant_members.status` 的既有決策，保留可追溯的用券歷史。

### 服務層

1. **`RedisCartService.getAppliedPromoCode`（新增）**：只回傳已套用的券碼，**刻意不回傳折扣金額**——`getCartWithPromo` 的折扣是 fallback-tolerant 的顯示用計算（券失效時靜默回退原價），不可作為收款依據。
2. **`OrderService.applyPromoDiscount`（新增私有）**：計算折扣、套用 PRD 步驟 5「不得為負」、設定訂單欄位。抽為獨立方法是因為內嵌會使 `createOrderFromCart` 的 NPath 分支複雜度超過 checkstyle 上限（實測內嵌為 960 > 200）。
3. **`OrderService.resolveValidPromoForCheckout`（新增私有）**：依 PRD 明訂順序重新驗證（存在 → ACTIVE → 時間範圍 → 總量上限 → 每人限用）。**驗證失敗一律拒絕下單，不靜默改以原價成立**——買家看到的是折扣後金額，靜默回退等同在買家不知情下多收款。
4. **`OrderService.commitPromoUsage`（新增私有）**：訂單成立後才遞增總量次數、寫入用券紀錄、清除購物車券碼。
5. **`OrderService.refundPromoUsage`（新增私有）+ 接入 `cancelOrder`**：退還額度。與庫存釋放不同，**不限 `CREATED` 狀態**——訂單無論付款前後取消，該次用券都不應繼續佔用額度。回補採 `Math.max(0, current - 1)` 防止負數。

### 錯誤碼

沿用三個既有碼，其中兩個正是本輪掃描發現的孤兒碼——**修復缺口的同時一併消滅孤兒碼，避免留下下一個同類訊號**：

| 錯誤碼 | 用途 | 狀態 |
|--------|------|------|
| `E_5007`「無效的優惠碼」 | 券不存在/已停用/尚未開始 | 既有 |
| `E_5008`「優惠碼已過期」 | 結帳時已過期 | **原孤兒碼** |
| `E_5009`「優惠碼已達使用上限」 | 總量用罄或超過每人限用 | **原孤兒碼** |

**連帶修正**：`GlobalExceptionHandler.mapErrorCodeToStatus` 原本未涵蓋 `E_5008`/`E_5009`，會落到 `default → 500`。優惠碼過期屬使用者輸入問題，已改對應 `400 BAD_REQUEST`（比照同組的 `E_5007`）。

### 前端

- `services/order.ts` 的 `Order` 型別新增 `promoCode`/`discountAmount`。
- 訂單詳情頁金額區塊新增「優惠折扣（券碼）」列——買家在購物車看到折扣，訂單也必須看得到用了哪張券、折了多少，否則只看到總額 160 卻無說明。

---

## 4. 測試

### 新增 `OrderPromoCodeTest`（14 測試，單元）

比照 `AuthServiceRefreshTokenTest` 的「單一主題獨立檔」慣例建立，不併入已達 1070 行的 `OrderServiceTest`。**全部測試皆先以紅燈證實缺口，再行修復**（第一個紅燈實測 `expected: 160 but was: 260`）。

- `CheckoutApplyTests`（6）：折扣扣除、總量遞增、用券紀錄寫入、購物車清券、折扣大於應付時總額為 0 不得為負、**無券時行為完全不變（回歸保護）**。
- `CheckoutRevalidationTests`（5）：結帳時過期（E-5008）、已停用（E-5007）、總量用罄（E-5009）、每人限用超標（E-5009）、券已被刪除（E-5007）。
- `CancellationRefundTests`（3）：取消退還額度且紀錄轉 REVOKED、未用券訂單完全不碰優惠券資料、總量為 0 時不得回補成負數。

### 新增 `M11PromoCheckoutIntegrationTest`（1 測試，真實 DB + 真實 JWT）

**刻意不 mock `PromoCodeRepository`/`PromoService`**，直接針對既有 `M11CartPromoIntegrationTest` 的固件繞過問題。單一測試走完整迴路：套券 → 下單（驗證金額扣折扣、`current_usage_count` 真的變 1、`promo_code_usages` 真的有 ACTIVE 紀錄）→ 同買家再用同張券被每人限用擋下（400）→ 取消訂單（驗證額度回 0、紀錄轉 REVOKED）。

---

## 5. 驗證結果

- `make validate-schema`：✅ **無漂移**（V70 與 entity 對齊；backend 以 `ddl-auto=validate` + Flyway 對乾淨 DB 啟動成功，同時證明新 repository 的衍生查詢方法名合法）
- 後端全量回歸 `mvn verify -Pintegration-test`：✅ **1446 tests 0 fail**（單元 1055 + 整合 391），含 `M11PromoCheckoutIntegrationTest` 真實 DB 完整迴路
- Checkstyle：0 violations（過程中修正一次 NPath 超標）
- 前端 `tsc --noEmit`：0 errors；`eslint`：0 errors（94 warnings 皆為既有 anonymous default export，與本輪無關）
- `make validate-release`：⏳ commit 後執行（push gate；結果補記於 RELEASE_TRACKER）

---

## 6. 範圍外（延後）

- **ROOM 訂單套用優惠券**（PRD US-010）：`createRoomOrder` 不走購物車，需新增 API 契約與訂房結帳前端促銷碼輸入，規模超出本輪。
- **`FREE_SHIPPING` 折扣型別**：`PromoService.computeDiscount` 對此型別回傳 `BigDecimal.ZERO` 並註明「免運費由物流模組處理」，但物流模組實際上沒有任何對應處理——此為**本輪掃描順帶發現的既有缺口**，性質為「折扣型別未實作」而非本輪的「斷鏈」，未擴大範圍處理，記錄待評估。
- **併發下單的額度競態**：目前總量檢查與遞增非原子操作（讀後寫），高併發下限量券理論上可超發。既有 `IdempotencyService`/Redis Lua 已有前例可循，但本輪聚焦斷鏈修復，未一併處理。
- **既有 `M11CartPromoIntegrationTest` 的 `@MockBean` 繞過**：本輪以新增真實 DB 測試補位，未改寫既有測試（Rule 3 精準改動）。

---

**文件版本**: v1.0｜**建立者**: Claude Code（AISDLC v0.09 Sprint Planning）｜**基於**: PRD v1.0 Final §9.5.1 / §2630
