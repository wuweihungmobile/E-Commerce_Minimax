# Sprint 48 Review / Sprint 48 評審會議

> **Sprint 編號**: Sprint 48
> **期間**: 2027-08-15 ~ 2027-08-28
> **評審日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: PRODUCT/cart 漲價——M12 進階定價 PRODUCT 側收官（顯示與收費一致）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 後端 PRODUCT 計價支援漲價 + 閘門放寬 + DTO 中性語意（AI-2406c 後端）| 5 | ✅ 完成 |
| US-002 | 前端 cart item 定價雙向顯示 + E2E（AI-2406c 前端）| 3 | ✅ 完成 |

**承諾 8 SP（US-001~002）全數完成**。承 S46 決策界線（AI-2406b 只做 ROOM，PRODUCT 另立 AI-2406c），本 Sprint 讓 PRODUCT 商品的購物車/訂單也計入漲價型規則。**M12 進階定價自此全面收官**（ROOM+PRODUCT 折扣+漲價皆「顯示與收費一致」）。**schema-free（Flyway V58 不變）。只做 PRODUCT。**

---

## 2. 交付內容

- **US-001（後端，AI-2406c，commit `b92447a`）**：
  - **閘門 2（結構性核心）**：`PricingService.applyProductRule` 由 discount-only 擴充支援漲價型規則——抽 `applyProductMarkup`：`MANUAL_OVERRIDE`（config `price`）/ `SEASONAL`（`multiplier`）/ `WEEKDAY_WEEKEND`（`weekendMultiplier` 依 `checkDate` 星期五六日），對齊 ROOM `calculateAdjustment` config key；**保留 `discountPercent` 向後相容**（S44 PRODUCT 折扣不退步，含 SEASONAL+discountPercent 舊資料）。`getEffectivePrice` 傳入 `checkDate` 供週末判定。
  - **閘門 1**：`RedisCartService.tryProductDiscount` → `tryProductAdjustment`，折扣閘門 `effectivePrice < 現價` 放寬為 `!= 0`（含漲價，對齊 ROOM S46）；保留 toggle 短路 + 計算失敗降級。
  - **DTO 中性語意**：`CartDto.CartItemResponse` 新增 `priceAdjustmentType`（DISCOUNT/MARKUP/NONE）；`discountAmount` 為有號差額（原價−調整後 × qty，正=折扣、負=加價）；抽 `adjustmentDirection` helper（對齊 S46）。
  - **下單自動繼承**：`OrderService.createOrderFromCart` 直接繼承 cart 的調整後 unitPrice/subtotal（getCart 含漲價後自動生效，**OrderService 未改**）。
  - **測試**：`PricingServiceTest` +Nested `ProductEffectivePriceTests` 4（UT-M12-015~018：MANUAL_OVERRIDE/SEASONAL/WEEKDAY_WEEKEND 漲價 + SEASONAL+discountPercent 折扣向後相容）；`RedisCartServiceDynamicPricingTest` +`UT-CART-DP-004`（cart 漲價 → MARKUP + 有號負差額）+ UT-CART-DP-001 補 DISCOUNT 斷言；`M12EffectivePriceIntegrationTest` +`IT-EP-004`（端點 MANUAL_OVERRIDE 漲價 effectivePrice>base）。
- **US-002（前端，AI-2406c 前端，commit `b3d0a8c`）**：
  - `cart/page.tsx`：`CartItem` 型別補 `originalUnitPrice`/`discountAmount`/`appliedRuleName`/`priceAdjustmentType`；item **首次**顯示 item 層級定價——折扣（>0）原價刪除線 + 綠標「規則名｜省 X」；漲價（<0）原價**不刪除線** + 橙標「規則名｜加價 X」（`Math.abs`）。**兼補上 S44 以來從未在購物車顯示的 PRODUCT 折扣。**
  - `checkout/page.tsx`：訂單摘要為 ROOM 訂房確認頁、不逐項顯示 PRODUCT 定價 → **無 item 價可加，未改**。
  - **E2E**：新增 `E2E-M11-012`（mock cart 含折扣 + 漲價兩項，斷言刪除線/綠標「省」vs 不刪除線/橙標「加價」）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 + checkstyle | ✅ 0 error / BUILD SUCCESS |
| 後端單元（`PricingServiceTest` 18 含 ProductEffectivePriceTests 4 + `RedisCartServiceDynamicPricingTest` 4）| ✅ **22 tests 0 fail** |
| 後端整合（真實 DB）| ✅ **54 tests 0 fail**（`M12EffectivePriceIntegrationTest` 4 含 IT-EP-004 漲價 + `M12PricingProductIntegrationTest` 3 + `M12PricingIntegrationTest` 8 + `M11CartPromoIntegrationTest` 10 + `CartControllerE2ETest` 12 + `OrderControllerE2ETest` 12 + `BuyerOrderJourneyE2ETest` 5）|
| schema 漂移守門（`make validate-schema` — 由 validate-e2e 內含）| ✅ 無漂移（entity 與 Flyway 對齊，V58 不變）|
| 本地 E2E 守門（`make validate-e2e`）| ✅ **53 passed / 6 skipped / 0 failed**（含新增 E2E-M11-012；相較 S47 52 passed +1；既有不退步）|
| 前端 tsc / lint | ✅ tsc 0 error；lint 0 error（本次改的檔零新增 warning）|
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0 |

---

## 4. 誠實揭露（Rule 12）

1. **SP 由初估 3 修正為 8（探勘後）**：規劃初期以為 PRODUCT 比照 ROOM 只需放寬閘門，探勘揭露 PRODUCT 漲價有**兩道閘門**——閘門 2（`applyProductRule` 結構上是 discount-only，數學上算不出漲價，須先擴充計價核心）比 ROOM 難（ROOM 的 calculateAdjustment 本就支援漲價）；且 cart 前端無任何 item 定價顯示（US-002 為淨新增）。已於計劃 §3/§8 誠實揭露並經 PO 核准 8 SP。
2. **行為變更（已 PO 拍板）**：`DYNAMIC_PRICING_ENABLED` 開啟時，PRODUCT 漲價型規則計入購物車/訂單金額；toggle 關閉完全不變；計算失敗降級原價。整合測試（IT-EP-004）+ 單元（UT-CART-DP-004）證實生效，既有折扣/無規則不退步。
3. **checkout 未改（誠實揭露）**：`checkout/page.tsx` 為 ROOM 訂房確認頁，訂單摘要不逐項顯示 PRODUCT 定價，無 item 價可加漲價顯示——故 US-002 只改 cart 頁（PRODUCT item 定價唯一顯示面）。計劃 AC-002-3 原列 checkout，實際確認無 itemize 後不改。
4. **PRODUCT/ROOM 兩套計算器（既有技術債，未合併）**：PRODUCT（`applyProductRule`，單日）與 ROOM（`calculateAdjustment`，stay-based）仍為兩套計算器，本 Sprint 對齊 config key（price/multiplier/weekendMultiplier）使 PRODUCT 支援漲價，但未強制合併（合併風險較高，PO 拍板另立評估）。
5. **向後相容策略**：既有 PRODUCT 折扣以「任何 ruleType + config `discountPercent`」表達（S44），本 Sprint 保留此 fallback——漲價型 key 優先、未命中才走 discountPercent，故 SEASONAL+discountPercent 舊折扣不退步（IT-EP-001 + M12PricingProductIntegrationTest 佐證）。
6. **push 債累積 S41~S48（8 Sprint）**：本 Sprint 有實質後端 + 前端變動 → push 需完整 `make validate-release`。承 S41~S47 累積，於檢查點徵詢後一次守門 push（AI-1908；嚴禁 --no-verify）。

---

## 5. Demo 重點

- **M12 全面收官**：展示賣家對商品設漲價型規則（旺季 multiplier / 手動調高 price / 週末加成）後，購物車 item 顯示漲價後單價 + 橙標「加價 X」、原價不刪除線；下單 totalAmount 含漲價（顯示=收費）。對照折扣（綠標「省 X」+ 原價刪除線）。
- **首次顯示 PRODUCT item 定價**：購物車自 S44 起雖後端已回折扣欄位，前端從未顯示；本 Sprint 首次呈現，同時涵蓋折扣與漲價。
- **不退步證明**：validate-e2e 53/6/0、後端單元 22 + 整合 54 全過；既有 PRODUCT 折扣（SEASONAL+discountPercent）不受影響。

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
