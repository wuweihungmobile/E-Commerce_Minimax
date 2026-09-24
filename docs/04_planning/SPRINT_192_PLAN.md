# Sprint 192 Plan — 金額計算精度與捨入掃描（DEF-266/267/268）

**Sprint**: Sprint 192
**日期**: 2026-09-24

## 1. 起點

`DEFERRED_ITEMS_TRACKER.md` 在 Sprint 191 收尾時已無活躍待辦項目，本輪自選新的掃描角度。Sprint 130~191 已系統性涵蓋 IDOR、Mass Assignment、XSS／URL 白名單、輸入驗證、分頁、認證、金額**竄改**、狀態偽造、公開端點回傳範圍、背景排程、設定宣告與讀取不一致等角度，尚未涵蓋的是「**金額計算的精度與捨入**」——前 60 個 Sprint 計畫書全文檢索 `RoundingMode`／`setScale`／`HALF_UP` 皆為 0 筆。「金額竄改」問的是「誰能改金額」，本輪問的是「系統自己算出來的金額，對不對到分」。

起點是探測性 grep：`BigDecimal.divide(` 單參數用法、`double`/`float` 混入金額、`setScale`/`RoundingMode` 使用點、`.longValue()` 換算分（Stripe cents）、對外 HTTP 呼叫點（順帶排除 SSRF 面）。

## 2. 調查方法與結果

| 探測 | 結果 |
|---|---|
| `BigDecimal.divide(x)` 單參數（遇無限小數會拋 `ArithmeticException`） | 全庫僅 `PricingService` 一處 `pct.divide(hundred)`；除以 100 必然可整除，**無缺陷** |
| `double`/`float` 混入金額 | `TenantService`/`Tenant` 的 `DEFAULT_COMMISSION_RATE = 0.05`（經 `BigDecimal.valueOf` 進入 `SettlementCalculator`，該處 `setScale(2, HALF_UP)`，**乾淨**）；`PricingService` 的 ROOM 定價全程 `double` 運算 → **發現 `DEF-266`** |
| 捨入慣例 | `PromoService`、`SettlementCalculator`、`AnalyticsService` 一致為 2 位小數／`HALF_UP`；**唯獨 `PricingService` 從未捨入**（全庫 `setScale` 僅出現在 `SettlementCalculator` 與 `PromoService`） |
| Stripe 換算分 `amount.multiply(100).longValue()`（截斷而非捨入） | 付款金額來自 DB 載入的 `NUMERIC(_,2)` 實體，恆為 2 位小數，截斷不會出事；**退款金額由呼叫端指定且無小數位驗證** → **發現 `DEF-267`** |
| 對外 HTTP 呼叫（SSRF 面） | 全庫只有 `OAuthService` 一個 `RestTemplate`，URL 為 Google/GitHub 固定常數，無任何使用者可控的對外請求 URL；**無 SSRF 面** |
| 定價規則 `config` 內容驗證 | `createRule`/`updateRule` 只驗日期、`config` 為自由格式 `Map<String,Object>` → **發現 `DEF-268`**（待決策，見 §2.3） |

### 2.1 `DEF-266`：動態定價結果從未捨入，且 ROOM 側經 `double` 運算混入浮點雜訊

`PricingService.calculateAdjustment`（ROOM）以 `BigDecimal.valueOf(1 - discount / 100)` 計算折扣倍率——`1 - 7.0 / 100` 在 `double` 下是 `0.9299999999999999`，不是 `0.93`。以 1~99 的整數折扣百分比逐一實測：**99 個中有 40 個**產生雜訊倍率（如 7% → `0.9299999999999999`、18% → `0.8200000000000001`）；週末/旺季倍率的百分比顯示值（`(multiplier - 1) * 100`）在 1.01~3.00 的 200 個倍率中有 100 個帶雜訊（`1.07` → `7.000000000000006`）。PRODUCT 側（`applyProductRule`）以 `BigDecimal` 精確運算，沒有雜訊，但**同樣沒有捨入**（99.99 × 0.925 = `92.49075`）。

計算結果（單晚價、總額、購物車有效單價）因此帶著 4~18 位小數，只靠 Postgres `NUMERIC(_,2)` 在寫入時隱性四捨五入。實測（真實 Postgres）後果：

- **精確平手值被雜訊往下捨**：1234.50 × 7% 折扣，精確值 `1148.085`，`HALF_UP` 應為 **1148.09**；雜訊版本 `1148.084999999999876550` 落庫為 **1148.08**——少收一分錢（與專案其餘處 `HALF_UP` 慣例不一致）。
- **API 回應帶長尾小數**：`POST /v2/bookings` 回應的 `totalAmount`、`GET .../availability` 的 `totalPrice`、`calculatePrice` 的逐晚明細與稽核日誌 `BOOKING_CREATED` 的 `totalAmount=...` 皆為未捨入值（`BookingService.createBooking` 回應由記憶體實體組出，未經 DB 往返），與實際落庫值不同。
- **PRODUCT 明細與總額各自被 DB 獨立四捨五入**：`OrderItem.unitPrice`、`OrderItem.subtotal`、`Order.totalAmount` 三個欄位分別對 `92.49075`、`277.47225` 等值各自捨入，明細加總與訂單總額可差一分。

**嚴重度判定 🟡**：金額影響是分為單位的，且付款金額來自 DB 往返後的值（`PaymentService.processBookingPayment` 讀 `booking.getTotalAmount()` 為新請求載入的實體），**沒有發現大額損失或可被利用的路徑**；缺陷是「系統算出的價格與其自身慣例不一致、且顯示值與帳上值不同」。

### 2.2 `DEF-267`：退款金額未驗證小數位數，Stripe 截斷與 DB 四捨五入對同一筆退款得出不同金額

`PaymentStateService.refundOrderPayment`（`POST /v2/orders/{orderId}/refund`，`order:update`，`@RequestParam BigDecimal amount`）只驗證退款金額為正且不超過剩餘可退額度。全庫沒有任何 `@Digits`。退款 `500.005`：`StripePaymentGateway` 以 `amount × 100` 後 `longValue()` 截斷 → Stripe 退 **500.00**；`applyRefundIfUnchanged` 寫入 `payments.refunded_amount`（`NUMERIC(12,2)`）→ 落庫 **500.01**（實測 `10.005::numeric(12,2)` = `10.01`）。同一筆退款兩邊帳差一分；且以 `500.005` 累計到接近全額時，狀態判斷（`compareTo` 原值）與實際落庫值可能不一致。

**嚴重度 🟢**：一分錢級、需呼叫端給超過 2 位小數的金額；前端 `frontend/src/lib/api.ts` 只有該端點的 URL 常數、**零呼叫點**（查證 `.refund`/`refund(` 無任何使用處），目前只有直呼 API 才能觸發，未來若接上退款 UI（使用者輸入金額）此缺口才會被一般操作踩到。舊版 `PaymentService.processRefund`（`/v2/payments/refund`）是 Mock、不移動金錢，未更動。

### 2.3 `DEF-268`：定價規則 `config` 數值無範圍驗證——負價、零價、`NaN` 皆被當成有效報價（**待決策，未修**）

一次性探針（不入庫，已刪除）直接呼叫 `calculatePrice`（基準價 1000.00、1 晚）的實測結果：

| 規則 config | 結果 |
|---|---|
| `EARLY_BIRD discountPercent=150` | `adjustedTotal = -500.00` |
| `EARLY_BIRD discountPercent=100` | `0.00` |
| `EARLY_BIRD discountPercent=-20` | `1200.00`（等同漲價） |
| `SEASONAL multiplier=-2` | `-2000.00` |
| `MANUAL_OVERRIDE price=-100` | `-100.00` |
| `discountPercent="NaN"`／`"Infinity"`（字串） | `NumberFormatException`；`BookingService.tryDynamicPricing` 只攔 `BusinessException`，該房源的可用性查詢與訂房會 500 |

成因：`CreateRuleRequest.config` 只有 `@NotNull`，`validateRuleRequest` 只驗日期；DB 也沒有金額 `CHECK` 約束。`FRD v1.0` §邊界條件（第 2715、2789 行）明訂 EARLY_BIRD/LONG_STAY「`discountPercent ≤ 0` → 驗證失敗 E-4001」「`minNights ≤ 0`／`daysInAdvance ≤ 0` → 驗證失敗」，**目前程式碼未實作**；`discountPercent > 50` 為「警告但允許」，**上限未規範**（≥ 100 必然導致價格 ≤ 0）。

**為何不在本輪直接修**：FRD 只明訂下限；上限（≥ 100%？）、倍率合理區間（`multiplier ≤ 0`）、覆蓋價下限（0 是否允許免費房）、建立時拒絕 vs. 計算時略過規則（DEF-218 的既有做法是「無法解析回 null，規則不套用」）皆是**業務決策**。此外規則屬於租戶自己的房源（DEF-242 已補擁有權檢查），是賣家自我設定錯誤而非跨租戶利用，故不緊急。**建議**：建立/更新規則時對 `discountPercent ∈ (0, 100)`、`multiplier > 0`、`price ≥ 0`、`minNights/minDaysAhead ≥ 1` 回 E-4001，並讓 `tryDynamicPricing` 與 `RedisCartService` 一致地攔 `RuntimeException`——待使用者拍板。

## 3. 修復範圍與實作

### 3.1 `DEF-266`（`PricingService`）

- 新增常數 `PRICE_SCALE = 2`、`PRICE_ROUNDING = HALF_UP`、`HUNDRED`（與 `PromoService`/`SettlementCalculator` 同一慣例，捨入在計算源頭做而非交給 DB 欄位隱性處理）。
- 新增 `discountFactor(percent) = 1 - percent/100` 與 `roundToPriceScale(amount)` 兩個私有輔助方法；`calculateAdjustment`（ROOM 的 WEEKDAY_WEEKEND／SEASONAL／EARLY_BIRD／LONG_STAY／LAST_MINUTE／MANUAL_OVERRIDE 六種）與 `applyProductRule`／`applyProductMarkup`（PRODUCT）全部改為 `BigDecimal` 精確運算，結果一律 `roundToPriceScale`。百分比顯示值（`adjustmentValue`）同樣改用 `BigDecimal`，不再帶雜訊。
- LONG_STAY 的「按晚數線性遞增折扣」（`min(pct, pct × nights / 7)`）改以 `BigDecimal` 計算，非整除的中間值保留 10 位小數，僅最終價格捨入至 2 位。
- **逐晚捨入**：每晚價格各自捨入後再加總，使 `breakdown[].adjustedPrice` 加總恆等於 `adjustedTotal`（顯示一致性）。
- `getDoubleConfig` 的 `Double` 讀取介面**不變**（維持 DEF-218 的 null-safe 解析），只在進入運算的第一步 `BigDecimal.valueOf(double)`（取 `Double.toString` 最短表示，`7.0` → `7.0`，無二進位雜訊）。
- 下游（`BookingService`／`CombinedCheckoutService`／`RedisCartService`／`OrderService`）**未更動**：它們消費 `PricingService` 的輸出，源頭已是 2 位小數，訂房回應與稽核日誌的值自動與落庫值一致。

### 3.2 `DEF-267`（`PaymentStateService`）

`resolveRefundAmount` 對呼叫端指定的金額新增檢查：`amount.stripTrailingZeros().scale() > 2` → `E_6009`。以**數值**而非字面 scale 判斷（`500.500` 只是尾端補零，仍合法，測試釘住不誤擋）。未指定金額（退剩餘全額）時剩餘額度來自 DB 兩個 `NUMERIC(_,2)` 欄位相減，恆為 2 位小數，不需檢查。

## 4. 測試

**`PricingServiceTest.MoneyPrecisionTests`（新增 7 案例）**——鎖定四個行為：

1. ROOM 折扣的精確平手值：1234.50 × 7% 須為 **1148.09**（非 1148.08）。
2. ROOM 折扣非平手值不帶長尾：1234.00 × 7% 須為 1147.62 且 scale ≤ 2。
3. ROOM 長住折扣：3 晚逐晚各 914.29、總額 2742.87、逐晚加總 = 總額、折扣額 257.13。
4. ROOM 週末加成百分比顯示值：倍率 1.07 → `adjustmentValue` 恰為 7。
5. ROOM 手動覆蓋價 1234.567 → 1234.57。
6. PRODUCT 折扣：99.99 × 0.925 → 92.49。
7. PRODUCT 漲價：99.99 × 1.15 → 114.99。

**`PaymentStateServiceStripeTest`（新增 2 案例）**：`500.005` → `E_6009` 且不佔用額度、不呼叫 gateway；`500.500`（尾端補零）允許不誤擋。

**紅燈先行（修復前對未修改的正式程式碼實際執行）**：`MoneyPrecisionTests` 7/7 皆為**行為性紅燈**，失敗訊息對應實際缺陷（`expected: 1148.09 but was: 1148.084999999999876550`、`expected: 92.49 but was: 92.49075`、`expected: 7 but was: 7.000000000000006` 等）——不同於 Sprint 191 的結構性紅燈。DEF-267 的 `500.005` 案例紅燈為 `Expecting code to raise a throwable`（金額被放行）；`500.500` 案例修復前後皆綠，為刻意設計的守門案例。

## 5. 驗證結果

`mvn -o clean verify`（真實 postgres/redis，`make test-db-up`）：**1657 個單元測試（+9：定價 7 + 退款 2）+ 490 個整合測試（持平），0 failed / 0 errors / 0 skipped**，checkstyle（main+test）0 違規，`BUILD SUCCESS`，總耗時 8:41。修復完成後另單獨執行過定價相關整合／E2E 測試 45 個（`M12EffectivePriceIntegrationTest`／`M12PricingIntegrationTest`／`M12PricingProductIntegrationTest`／`M17MaintenanceWorkflowIntegrationTest`／`BookingControllerE2ETest`）全數通過，確認既有測試沒有依賴舊的未捨入值。

**過程揭露**：第一次背景 `mvn clean verify` 啟動於 DEF-267 開始之前；因隨後要修改原始碼（避免 checkstyle/編譯階段讀到半改狀態污染驗證），該次執行在編譯階段即中止，DEF-267 完成後才重新啟動**涵蓋 DEF-266＋DEF-267 的單一完整驗證**，上述數字即來自這一次。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-266`：✅ 已修復（Sprint 192）。
- 新增 `DEF-267`：✅ 已修復（Sprint 192）。
- 新增 `DEF-268`：⚠️ 已記錄，**待業務決策**（規則 `config` 範圍驗證的上下限與處置方式，見 §2.3）。

## 7. 誠實揭露總結

- 本輪未派背景 agent——掃描本身是「grep + 少量讀碼 + 數值實測」，確定性工作交給程式碼（Rule 5）。
- `DEF-266` 的金額影響是**一分錢級**，且付款金額經 DB 往返後才進入 Stripe；**未發現大額損失或可利用路徑**。「1148.085 被雜訊捨成 1148.08」與「10.005 落庫 10.01」均已用真實 Postgres `numeric(_,2)` 轉型實測，不是推論。
- 本輪對 PRODUCT 側「`OrderItem` 明細與 `Order.totalAmount` 各自被 DB 獨立四捨五入」的說明基於讀碼推導（`OrderService.buildProductOrder` 直接寫入 `cartItem.getUnitPrice()`／`getSubtotal()`），未另建整合測試重現一分錢差；修復把單價捨入在源頭，使小計（單價 × 整數數量）天然為 2 位小數。
- `DEF-268` **只記錄、未修**，並非遺漏：上下限與處置方式是業務決策（見 §2.3）。探針以呼叫 `calculatePrice` 為準，**未**端到端驗證負價訂房一路走到付款會如何（Stripe 應會拒絕負金額；Mock 閘道的行為未查證），故不對其後續影響下結論。
- 查證後刻意不更動：`PricingService.TAX_RATE = 1.2` 是誤導性命名（實為預設週末倍率，與稅無關），純命名問題；`StripePaymentGateway` 的 `.longValue()` 截斷寫法（付款金額來自 DB 故安全，`DEF-267` 已在退款入口把守）；舊版 `PaymentService.processRefund`（Mock）。
- 本輪**未查證**的鄰近角度（列為未來候選，不代表無問題）：`SecurityConfig` 沒有顯式 `headers()` 設定（HSTS／CSP／X-Frame-Options 沿用 Spring Security 預設，未逐項核對）；時區處理（`LocalDate.now()`／`Instant.now()` 與伺服器時區、訂房日期邊界）。
