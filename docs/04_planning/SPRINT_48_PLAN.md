# Sprint 48 計劃 / Sprint 48 Plan

> **Sprint 編號**: Sprint 48
> **期間**: 2027-08-15 ~ 2027-08-28 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-03
> **基於**: Sprint 46 決策界線（AI-2406b 只做 ROOM，PRODUCT 另立 AI-2406c）+ S48 落點探勘（RedisCartService / PricingService.applyProductRule / CartDto / OrderService / 前端 cart）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者（PO）選定「**AI-2406c PRODUCT/cart 漲價（M12 進階定價 PRODUCT 側收官）**」

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者選「AI-2406c PRODUCT 漲價」 | 讓 PRODUCT 購物車/訂單也計入漲價型規則（對齊 ROOM S46）|
| S47 狀態 | ✅ 已完成（4 commit，未 push）；活躍 DEF=0 | push 債累積 S41~S47（7 Sprint）|
| 技術現況已調查 | ✅ Explore Agent 確認 PRODUCT 漲價**兩道閘門** | 與 ROOM 不同：PRODUCT 計算核心結構上就是 discount-only |
| **🔴 閘門 2（結構性，核心）** | 🔴 `PricingService.applyProductRule`（L570-582）**只認 `discountPercent`** → `1 − pct/100` 恆 ≤ basePrice → **PRODUCT 結構上算不出漲價** | 必須先讓 applyProductRule 支援漲價型規則（MANUAL_OVERRIDE price / SEASONAL multiplier），否則放寬閘門也無效 |
| **🔴 閘門 1** | 🔴 `RedisCartService.tryProductDiscount`（L457）`effectivePrice.compareTo(unitPrice) < 0` | 放寬為 `!= 0`（比照 S46 ROOM）|
| **與 ROOM 的關鍵差異** | ⚠️ ROOM 的 `calculateAdjustment` 本就支援全部漲價型 ruleType（S46 只需放寬閘門）；PRODUCT 的 `applyProductRule` 是**獨立的 discount-only 計算器** | 這是本 Sprint 比 S46 大的根因 |
| **cart 前端無定價顯示** | ⚠️ `cart/page.tsx` 目前**完全無 item 層級定價顯示**（連 S44 PRODUCT 折扣欄位都沒接來顯示，L302-315 只顯示 unitPrice/subtotal）| US-002 是「新增雙向顯示」（含折扣 + 漲價），非只加漲價變體 |
| 下單自動繼承 | ✅ `OrderService.createOrderFromCart`（L130-131）直接繼承 cart 的 unitPrice/subtotal | getCart 含漲價後，下單自動繼承漲價，OrderService 可能不需改 |
| schema 影響 | ✅ schema-free（沿用 jsonb config + pricing_rules.listing_id）| Flyway 維持 V58（S48 零 migration）|
| SP 修正（誠實揭露）| ⚠️ 初估 ~3 SP，探勘後修正為 **~8 SP** | 因（a）PRODUCT 計算核心結構性擴充、（b）cart 前端定價顯示為淨新增 |
| push 前置 | ⚠️ 本 Sprint 有實質後端 + 前端變動 → push 需完整 `make validate-release` | 承 S41~S47 push 債 |

---

## 1. Sprint 48 目標

> **主題**: PRODUCT/cart 漲價——M12 進階定價 PRODUCT 側收官（顯示與收費一致）

M12 進階定價 S43~S47 已完成 ROOM 折扣+漲價、PRODUCT 折扣，唯 **PRODUCT 漲價尚未計入購物車/訂單**——賣家對商品設漲價型規則（旺季加成、手動調高）目前不計入，且與 ROOM 不同，PRODUCT 的計價核心 `applyProductRule` **結構上只支援折扣**。本 Sprint：(1) 擴充 `applyProductRule` 支援漲價型規則、(2) 放寬購物車折扣閘門使漲價計入、(3) DTO 中性調整語意（對齊 S46）、(4) cart/checkout 新增 item 層級雙向定價顯示（折扣刪除線+綠標／漲價不刪除線+橙標，同時補上 S44 未顯示的折扣）。**只做 PRODUCT；schema-free（V58 不變）。**

---

## 2. User Stories

### US-001：後端——PRODUCT 計價支援漲價 + 閘門放寬 + DTO 中性語意（P2）（AI-2406c 後端）

> **SP**: 5 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-001-1**（🔴 核心，結構性）：擴充 `PricingService.applyProductRule`（L570-582）支援漲價型規則——除既有 `discountPercent`（折扣）外，新增（**PO 拍板三者全做**）：
- `MANUAL_OVERRIDE`：config `price`（可高於 basePrice = 漲價）；
- `SEASONAL`：config `multiplier`（>1 = 漲價）；
- `WEEKDAY_WEEKEND`：config `weekendMultiplier`，依 `checkDate` 星期判定（getEffectivePrice 為單日語意，可算星期六日）。
- 沿用 ROOM `calculateAdjustment`（L432-509）的 config key 慣例；EARLY_BIRD/LONG_STAY/LAST_MINUTE 為 stay-based（ROOM 專屬）不納入 PRODUCT。**評估共用單日規則套用邏輯以減少與 ROOM 的計算器分歧（誠實揭露此為既有技術債根因）。**

**AC-001-2**（閘門 1）：`RedisCartService.tryProductDiscount`（L457）折扣閘門 `effectivePrice.compareTo(unitPrice) < 0` 放寬為 `!= 0`（漲價計入）。**保留** toggle 短路（L449）與失敗降級（L459-463 catch → 回 null → fallback 原價）。方法/變數命名可比照 S46 中性化（如 `tryProductAdjustment`，選配）。

**AC-001-3**（DTO 中性語意）：`CartDto.CartItemResponse`（L61-82）新增 `priceAdjustmentType`（DISCOUNT/MARKUP/NONE）；`discountAmount` 維持**有號差額**（`originalUnitPrice − unitPrice` × qty，正=折扣、負=加價，L415-416 已是有號，漲價時自然為負）；方向由 `RedisCartService.toCartItemResponse` 依 original vs adjusted 計算（比照 S46 `adjustmentDirection`）。保留既有欄位名（向後相容）。

**AC-001-4**：下單繼承驗證——`OrderService.createOrderFromCart`（L130-131）確認 PRODUCT 訂單自動繼承 cart 漲價後 unitPrice/subtotal（getCart 含漲價後自動生效）；若需訂單詳情顯示 adjustment 方向再評估補 OrderDto（選配，§8）。

**AC-001-5**：新增 / 複查測試（單元 Mockito + 真 DB 整合）：
- 單元：`PricingServiceTest` 補 `applyProductRule` / `getEffectivePrice` **漲價**案例（目前 PRODUCT 路徑完全無單元測試）；`RedisCartServiceDynamicPricingTest` 新增漲價案例（unitPrice 漲、discountAmount 負、priceAdjustmentType=MARKUP），複查 `UT-CART-DP-003`（相等→不套用，閘門改 `!= 0` 後仍成立）。
- 整合（真實 DB）：`M12EffectivePriceIntegrationTest` + `M12PricingProductIntegrationTest` 新增漲價案例（effectivePrice > basePrice + 購物車漲價 + 下單繼承漲價）。

**AC-001-6**：現有 cart/order/pricing 測試全數不退步（`make test-db-up` 整合綠）；catch(Exception)=0、@Deprecated=0；**無 schema 變動**（Flyway V58）。

### US-002：前端——cart/checkout item 定價雙向顯示（P2）（AI-2406c 前端）

> **SP**: 3 | **優先級**: P2 | **狀態**: 📋 Ready

**AC-002-1**: cart service 型別（`CartItem`）補 `originalUnitPrice` / `discountAmount` / `appliedRuleName` / `priceAdjustmentType`（目前完全缺）。

**AC-002-2**: `cart/page.tsx`（L302-315）item 顯示雙向——折扣（discountAmount>0）原價刪除線 + 綠標「規則名｜省 X」；漲價（<0）原價**不刪除線** + 橙標「規則名｜加價 X」（`Math.abs`）。比照 S46 `ListingDetail.tsx:315-360` 樣板。**同時補上 S44 未曾顯示的 PRODUCT 折扣。**

**AC-002-3**: `checkout/page.tsx` item 顯示一併套用（下單頁與購物車一致）。

**AC-002-4**: E2E——`at-m11-cart-checkout.spec.ts`（或對應 cart E2E）新增 PRODUCT 折扣 + 漲價顯示案例（mock cart item 帶 originalUnitPrice/discountAmount/priceAdjustmentType）。既有案例不退步。

**AC-002-5**: 前端 `tsc` / `build`（Turbopack）/ `lint` 0 error；`make validate-e2e` 綠（含新增案例）。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 後端 PRODUCT 計價支援漲價（applyProductRule 擴充）+ 閘門放寬 + DTO（AI-2406c 後端）| 5 | P2 |
| US-002 | 前端 cart/checkout item 定價雙向顯示 + E2E（AI-2406c 前端）| 3 | P2 |
| **承諾合計** | | **8 SP** | |

> **Velocity 參考**：S43=10, S44=8, S45=5, S46=8, S47=7。**本 Sprint 8 SP**，健康區間。**誠實揭露**：初估 ~3 SP（以為比照 ROOM 只需放寬閘門），探勘後修正為 8 SP——因 PRODUCT 計價核心 `applyProductRule` 結構上是 discount-only（需先擴充才能算漲價，閘門 2），且 cart 前端無任何 item 定價顯示（US-002 為淨新增雙向顯示，非漲價變體）。**M12 進階定價自此全面收官**（ROOM+PRODUCT 折扣+漲價皆顯示=收費）。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 後端（applyProductRule 擴充漲價【閘門 2，核心】→ 單元測試漲價 → RedisCartService 放寬閘門【閘門 1】
   → CartItemResponse priceAdjustmentType → 每步 mvn 編譯 + 測試 → test-db-up 整合測試不退步 + 漲價案例）
   ↓ 後端綠（漲價可算出 + 計入購物車 + 下單繼承）
US-002 前端（CartItem 型別 → cart/page 雙向顯示 → checkout/page 一致 → E2E → tsc/build/lint）
   ↓ 前端綠
make validate-e2e（全棧：既有不退步 + PRODUCT 折扣/漲價顯示新案例）
   ↓
收尾（Review / Retro / Release Notes + trackers）
```

**強制**：US-001 **先擴充 `applyProductRule` 並用單元測試證實能算出漲價**（閘門 2 是核心，先解），再放寬 RedisCartService 閘門（閘門 1）；每步立即 mvn 編譯 + 測試；閘門改完**立即**跑 cart/order/pricing 整合測試（`make test-db-up`）確認折扣不退步 + 漲價生效，才進 US-002。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| **只改閘門 1 不夠（漲價仍算不出）** | AC-001-1 先擴充 applyProductRule（閘門 2 結構性）；單元測試先證實能產出 effectivePrice > basePrice，再放寬閘門 1 |
| PRODUCT vs ROOM 兩套計算器分歧（既有技術債）| 評估共用單日規則套用邏輯；至少對齊 config key 慣例（multiplier/price）；於 Review 誠實揭露分歧現況 |
| WEEKDAY_WEEKEND 對 PRODUCT 語意存疑（商品週末加價少見）| §8 由 PO 決策是否納入；建議先做 MANUAL_OVERRIDE + SEASONAL（商品相關漲價），WEEKDAY_WEEKEND 選配/另立 |
| 漲價改變既有商品訂單金額（商業行為變更）| toggle DYNAMIC_PRICING_ENABLED 關閉時完全不變；整合測試 seed 漲價規則證實生效、既有折扣/無規則不退步 |
| cart 前端加定價顯示引入受控元件/hooks lint 問題 | 遵循 React 19 嚴格 hooks（記憶）；比照既有 ListingDetail 純顯示邏輯，無新 effect |
| 有實質前後端變動 → push 需完整 validate-release | 承 S41~S47 push 債累積後徵詢 |

---

## 6. Definition of Done

- [ ] US-001：applyProductRule 支援漲價型規則（MANUAL_OVERRIDE/SEASONAL[/WEEKDAY_WEEKEND]）；RedisCartService 閘門 `<0`→`!=0`；CartItemResponse priceAdjustmentType + 有號 discountAmount；下單繼承驗證；單元（applyProductRule/getEffectivePrice 漲價 + cart 漲價）+ 真 DB 整合（effective-price + cart + order 漲價）；既有不退步；無 schema
- [ ] US-002：CartItem 型別補欄位；cart/page + checkout/page item 雙向顯示（漲價不刪除線 + 橙標，兼補 S44 折扣顯示）；E2E PRODUCT 折扣/漲價案例；tsc/build/lint 0 error
- [ ] `make validate-e2e` 綠、既有不退步；schema 無漂移（Flyway V58）；catch(Exception)=0、@Deprecated=0
- [ ] Sprint 48 Review / Retro / Release Notes + trackers 更新（含 PRODUCT/ROOM 計算器分歧之揭露）
- [ ]（檢查點）承 S41~S47 push 債，累積後於徵詢時完整 `make validate-release` 後 push（嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端計價核心 | `backend/.../core/pricing/PricingService.java`（applyProductRule）、`core/cart/RedisCartService.java`（閘門 + toCartItemResponse）|
| 後端 DTO | `backend/.../api/dto/CartDto.java`（CartItemResponse）、可能 `PricingDto.java` |
| 後端測試 | `backend/.../core/pricing/PricingServiceTest.java`、`core/cart/RedisCartServiceDynamicPricingTest.java`、`integration/M12EffectivePriceIntegrationTest.java`、`integration/M12PricingProductIntegrationTest.java` |
| 前端顯示 | `frontend/src/app/(auth)/cart/page.tsx`、`checkout/page.tsx`、`services/cart.ts`（型別）|
| 前端 E2E | `frontend/e2e/at-m11-cart-checkout.spec.ts`（或對應）|
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）、trackers（`docs/04_planning/`）|

---

## 8. ✅ 使用者（PO）確認點（已拍板）

1. **範圍與 SP 修正**：AI-2406c 經探勘修正為 **8 SP**（US-001 後端 5 + US-002 前端 3）——因 PRODUCT 計價核心結構上是 discount-only（閘門 2）+ cart 前端無 item 定價顯示（淨新增雙向顯示）。✅ **核准 8 SP 全做**。
2. **PRODUCT 漲價支援哪些 ruleType**：✅ **三者全做**——MANUAL_OVERRIDE（price）+ SEASONAL（multiplier）+ WEEKDAY_WEEKEND（weekendMultiplier，依 checkDate 星期）。PRODUCT 完全對齊 ROOM 漲價型規則。
3. **行為變更明示**：本 Sprint 會改變既有商品購物車/訂單金額——PRODUCT 漲價型規則將計入（toggle 開啟時）；toggle 關閉完全不變。✅ 隨主軸核准。
4. **cart/checkout 定價顯示**：✅ 納入（US-002）——首次顯示 item 層級雙向定價（含 S44 未顯示折扣 + 新漲價）。
5. **計算器分歧**：PRODUCT（單日）與 ROOM（stay-based）維持兩套計算器、對齊 config key，不強制合併（合併另立評估）。✅ 採此取捨。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
