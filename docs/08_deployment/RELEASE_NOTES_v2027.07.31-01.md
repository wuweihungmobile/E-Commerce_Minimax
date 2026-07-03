# Release Notes - v2027.07.31-01 (Sprint 46)

**發布日期**: 2027-07-31（規劃）／實作完成 2026-07-03
**發布類型**: Minor（計價行為變更：漲價型規則計入 ROOM booking；toggle 開啟時改變既有訂房金額；無 schema 變動）
**Sprint**: Sprint 46
**狀態**: ⏳ 待 push（本 Sprint 3 commit；push 債累積 S41+S42+S43+S44+S45+S46，於檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 46 主題：**定價機制真正統一——漲價型規則計入 ROOM booking（M12 進階定價收官）**。承 S45 決策文件（PRICING_MECHANISM_UNIFICATION.md §3.2）與使用者拍板 **選項 B**，讓漲價型規則（WEEKDAY_WEEKEND 週末加成、SEASONAL 旺季加成、MANUAL_OVERRIDE 調高）**計入 ROOM booking 總價與 availability/calendar 顯示**，消除 S43~S44 遺留的「顯示加價、下單原價」不一致。**M12 進階定價自此真正收官**（折扣 + 漲價皆「顯示與收費一致」）。後端變動限於**放寬三處折扣閘門 + 優雅降級 + DTO 中性調整語意**，無 entity 新增 / migration / schema 變動（Flyway 維持 V57；連續 S42~S46 零 migration）。**只做 ROOM，PRODUCT/cart 另立。**

---

## 改進 🚀

- **ROOM 計價全面走 adjustedTotal 含漲價（AI-2406b，US-001）**：`BookingService` 放寬三處折扣閘門——`tryDynamicPricing`（`adjustedTotal < baseTotal` → `!= 0`）、`getCalendar` 逐日（`< 0` → `!= 0`）、`calculateTotalAmount`（toggle 開啟即採 adjustedTotal）——使 availability 查詢、月曆每日價、實際建單 `totalAmount` 三者一律採 `calculatePrice` 的 adjustedTotal（含漲價乘數）。**保留** `DYNAMIC_PRICING_ENABLED` toggle 關閉短路（完全向後相容）與計算失敗 catch(BusinessException) 降級 basePrice。**計算核心不動**（adjustedTotal 本就含 weekend/seasonal/manual 加成）。
- **PricingService 優雅降級（AI-2406b，US-001）**：抽出 `resolveListingForPricing(UUID)` helper——room 記錄缺失時 fallback 用 listing 取 basePrice（不因無 room 即 404）；listing/basePrice 缺失回 `E_4000`（4xx）而非 NPE→500。（順帶解決 `calculatePrice` NPath 240>200 checkstyle 超標。）
- **前端漲價雙向顯示（AI-2406b 前端，US-002）**：`ListingDetail` availability 與 `MonthCalendar` 每日格支援雙向——折扣維持「原價刪除線 + 綠 badge 省 X」；漲價改「原價**不刪除線** + 橙 badge 加價 X」（`Math.abs`），由後端 `priceAdjustmentType` / 有號 `discountAmount` 驅動。

## 變更 🔧

- **DTO 中性調整語意（AI-2406b，US-001）**：`AvailabilityResponse` / `CalendarResponse` 的 `discountAmount` 改為**有號差額** `baseTotal − adjustedTotal`（正=折扣、負=加價），不再取 `PricingService` 被 clamp 為非負的 `discount`；新增 `priceAdjustmentType`（`DISCOUNT`/`MARKUP`/`NONE`）明示方向。**保留既有欄位名（向後相容），僅擴語意 + 加方向欄位。**
- **⚠️ 行為變更（已 PO 拍板）**：`DYNAMIC_PRICING_ENABLED` 開啟時，賣家設的漲價型規則將**開始計入訂房總價與下單金額**。toggle 關閉則完全不變。

## 測試 / 驗證 ✅

- **後端編譯**：mvn 0 error；checkstyle BUILD SUCCESS。
- **後端單元**：`BookingServiceDynamicPricingTest` 4 + `PricingServiceTest` 14（含 `MarkupAndFallbackTests` 漲價 + 降級 UT-M12-013/014）= **18 tests 0 fail**（發布 session 複驗）。
- **後端整合（真實 DB）**：`BookingControllerE2ETest` 19（含漲價 API-M06-015）+ `M12PricingIntegrationTest` 8 + `M12PricingProductIntegrationTest` 3 + `BookingIntegrationTest` 4 = **34 tests 0 fail**（US-001 開發 session 驗證；push 前 `make validate-release` 完整複驗）。
- **本地 E2E 守門（`make validate-e2e`）**：**50 passed / 6 skipped / 0 failed**（含新增 E2E-ROOM-08/09 漲價變體；相較 S45 48 passed +2；折扣案例不退步）。
- **schema 漂移**：無（backend 以 ddl-auto=validate 對齊 Flyway 重建 DB 啟動成功）。
- **前端**：tsc 0 error；lint 0 error（本次改的 3 檔零 warning；既有 94 anonymous-default-export warning 無關）。
- **catch(Exception) / @Deprecated 計數**：維持 0。

## 技術決策 / 已知限制 ⚠️

- **只做 ROOM，PRODUCT/cart out-of-scope（誠實揭露 Rule 12）**：`RedisCartService.tryProductDiscount` 僅套折扣、漲價不計入購物車，本 Sprint 未動；PRODUCT 漲價另立 AI-2406c。
- **`bestRule` priority 治理落差（記錄不修）**：規則採「最高 priority 勝出」非「最低價勝出」，漲價/折扣可能互蓋（既有語意，折扣期即存在）；逐日精準選取另立 AI-2407。
- **`findActiveRulesForDateRange` 部分晚數落差（記錄不修）**：查詢要求規則涵蓋整段區間，部分晚數 SEASONAL 會漏套（折扣亦受同限）；逐日精準查詢另立 AI-2407。
- **E2E 編號順延**：計劃寫「ROOM-06/07 漲價變體」，但該編號已是折扣案例（保留不動），漲價變體實際以 **ROOM-08/09** 新增（行為與計劃一致，僅編號順延）。
- **後端無 schema 變動**：US-001 為放寬閘門 + 降級 + DTO 欄位擴充，沿用既有 schema，Flyway 維持 V57（連續 S42~S46 零 migration）。

## 資料庫遷移 🗄️

- 無（Flyway 維持 V57）。

## 內含 Commit（Sprint 46）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 46 Plan | 6a6c0ab | 定價機制真正統一，漲價計入 booking（2 US / 8 SP，實作型）|
| US-001 AI-2406b | 20a399b | 後端 ROOM 計價全面走 adjustedTotal（含漲價）+ 優雅降級 |
| US-002 AI-2406b | 151ce2d | 前端賣場漲價雙向顯示 + E2E-ROOM-08/09 漲價變體 |
| Sprint 46 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**基於**: AISDLC v0.09 Release Management Workflow
