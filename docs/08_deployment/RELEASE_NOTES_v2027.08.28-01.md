# Release Notes - v2027.08.28-01 (Sprint 48)

**發布日期**: 2027-08-28（規劃）／實作完成 2026-07-03
**發布類型**: Minor（計價行為變更：PRODUCT 漲價計入購物車/訂單；無 schema 變更）
**Sprint**: Sprint 48
**狀態**: ⏳ 待 push（本 Sprint 3 commit；push 債累積 S41~S48，於檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 48 主題：**PRODUCT/cart 漲價——M12 進階定價 PRODUCT 側收官**。承 S46 決策界線（AI-2406b 只做 ROOM，PRODUCT 另立 AI-2406c），讓 PRODUCT 商品的購物車/訂單也計入漲價型規則。與 ROOM 不同，PRODUCT 漲價需破**兩道閘門**：閘門 2（`PricingService.applyProductRule` 結構上是 discount-only，須擴充計價核心）+ 閘門 1（購物車折扣閘門放寬）。**M12 進階定價自此全面收官**（ROOM+PRODUCT 折扣+漲價皆「顯示與收費一致」）。**schema-free（Flyway V58 不變）；只做 PRODUCT。**

---

## 新功能 / 改進 🚀

- **PRODUCT 計價支援漲價（AI-2406c，US-001）**：`PricingService.applyProductRule` 由 discount-only 擴充支援漲價型規則——`MANUAL_OVERRIDE`（config `price`）/ `SEASONAL`（`multiplier`）/ `WEEKDAY_WEEKEND`（`weekendMultiplier` 依 checkDate 星期）——對齊 ROOM `calculateAdjustment` config key；`RedisCartService` 折扣閘門由 `< 現價` 放寬為 `!= 現價`（含漲價）。保留 `discountPercent` 向後相容（S44 折扣不退步）+ toggle 短路 + 失敗降級。
- **購物車 item 定價雙向顯示（AI-2406c，US-002）**：`cart/page.tsx` **首次**顯示 item 層級定價——折扣原價刪除線 + 綠標「省 X」；漲價原價**不刪除線** + 橙標「加價 X」。兼補上 S44 以來從未在購物車顯示的 PRODUCT 折扣。

## 變更 🔧

- **DTO 中性調整語意（AI-2406c）**：`CartDto.CartItemResponse` 新增 `priceAdjustmentType`（DISCOUNT/MARKUP/NONE）；`discountAmount` 為**有號差額**（正=折扣、負=加價）。
- **下單自動繼承**：`OrderService.createOrderFromCart` 繼承 cart 調整後 unitPrice/subtotal（getCart 含漲價後自動生效，未改）。
- **⚠️ 行為變更（已 PO 拍板）**：`DYNAMIC_PRICING_ENABLED` 開啟時，PRODUCT 漲價型規則將計入購物車/訂單金額；toggle 關閉完全不變。

## 測試 / 驗證 ✅

- **後端編譯 + checkstyle**：0 error / BUILD SUCCESS。
- **後端單元**：`PricingServiceTest` 18（含 `ProductEffectivePriceTests` 4：UT-M12-015~018 漲價 3 型 + 折扣向後相容）+ `RedisCartServiceDynamicPricingTest` 4（含 UT-CART-DP-004 漲價）= **22 tests 0 fail**。
- **後端整合（真實 DB）**：`M12EffectivePriceIntegrationTest` 4（含 IT-EP-004 漲價）+ `M12PricingProductIntegrationTest` 3 + `M12PricingIntegrationTest` 8 + `M11CartPromoIntegrationTest` 10 + `CartControllerE2ETest` 12 + `OrderControllerE2ETest` 12 + `BuyerOrderJourneyE2ETest` 5 = **54 tests 0 fail**。
- **本地 E2E 守門（`make validate-e2e`）**：**53 passed / 6 skipped / 0 failed**（含新增 E2E-M11-012；相較 S47 52 passed +1；既有不退步）+ schema 無漂移。
- **前端**：tsc 0 error；lint 0 error（本次改的檔零新增 warning）。
- **catch(Exception) / @Deprecated 計數**：維持 0。

## 技術決策 / 已知限制 ⚠️

- **兩道閘門（誠實揭露 Rule 12）**：與 ROOM 不同，PRODUCT 漲價需先擴充計價核心（閘門 2，`applyProductRule` 原為 discount-only）再放寬購物車閘門（閘門 1）。SP 由初估 3 修正為 8（探勘後，經 PO 核准）。
- **checkout 未改**：`checkout/page.tsx` 為 ROOM 訂房確認頁、不逐項顯示 PRODUCT 定價，無 item 價可加 → 未改；cart 頁為 PRODUCT item 定價唯一顯示面。
- **PRODUCT/ROOM 兩套計算器**：PRODUCT（`applyProductRule`，單日）與 ROOM（`calculateAdjustment`，stay-based）維持兩套、對齊 config key，未強制合併（合併另立 AI-2409）。
- **向後相容**：既有 PRODUCT 折扣以「任意 ruleType + `discountPercent`」表達（S44）；新設計漲價型 key 優先、未命中走 discountPercent fallback，舊折扣不退步。
- **只做 PRODUCT；無 schema 變動**：沿用 jsonb config + pricing_rules.listing_id，Flyway 維持 V58。

## 資料庫遷移 🗄️

- 無（Flyway 維持 V58）。

## 內含 Commit（Sprint 48）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 48 Plan | 4e96ec1 | PRODUCT/cart 漲價（2 US / 8 SP，兩道閘門）|
| US-001 AI-2406c | b92447a | 後端 PRODUCT 計價支援漲價 + 閘門放寬 + DTO 中性語意 |
| US-002 AI-2406c | b3d0a8c | 前端購物車 PRODUCT 定價雙向顯示 + E2E-M11-012 |
| Sprint 48 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**基於**: AISDLC v0.09 Release Management Workflow
