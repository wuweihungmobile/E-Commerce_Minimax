# Sprint 46 Review / Sprint 46 評審會議

> **Sprint 編號**: Sprint 46
> **期間**: 2027-07-18 ~ 2027-07-31
> **評審日期**: 2026-07-03
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 定價機制真正統一——漲價型規則計入 ROOM booking（M12 進階定價收官）

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | 後端 ROOM 計價全面走 adjustedTotal（含漲價）+ 優雅降級（AI-2406b）| 5 | ✅ 完成 |
| US-002 | 前端 ROOM 漲價雙向顯示 + E2E（AI-2406b 前端）| 3 | ✅ 完成 |

**承諾 8 SP（US-001~002）全數完成**。承 S45 決策文件（PRICING_MECHANISM_UNIFICATION.md §3.2），使用者拍板 **選項 B**：讓漲價型規則（WEEKDAY_WEEKEND 週末加成、SEASONAL 旺季加成、MANUAL_OVERRIDE 調高）計入 ROOM booking 總價與 availability/calendar 顯示，消除 S43~S44 遺留的「顯示加價、下單原價」不一致。**M12 進階定價自此真正收官**（折扣 + 漲價皆「顯示與收費一致」）。**全部 schema-free**（連續 S42~S46 零 migration，Flyway 維持 V57）。

---

## 2. 交付內容

- **US-001（後端，AI-2406b，commit `20a399b`）**：
  - **放寬三處折扣閘門**（核心變更）：`tryDynamicPricing` 折扣閘門由 `adjustedTotal < baseTotal` 放寬為 `!= 0`（含漲價）；`getCalendar` 逐日閘門由 `< 0` 放寬為 `!= 0`；`calculateTotalAmount` 於 toggle 開啟時一律採 adjustedTotal（含漲價），僅 toggle 關 / 計算失敗才 fallback `calendarBaseTotal`。**保留** `DYNAMIC_PRICING_ENABLED` toggle 關閉短路 + 失敗 catch 降級（向後相容，不破 UT-BK-DP-002）。
  - **PricingService 優雅降級**：抽出 `resolveListingForPricing(UUID)` private helper——room 記錄缺失時 fallback 用 listing 取 basePrice（不因無 room 即 404）；listing/basePrice 缺失回 `E_4000`（4xx）而非 NPE→500。`calculatePrice` 開頭改用它。（抽 helper 順帶解決 `calculatePrice` NPath 240>200 checkstyle 超標。）
  - **DTO 中性調整語意**：`discountAmount` 改為**有號差額** `baseTotal − adjustedTotal`（正=折扣、負=加價），**不再取** `PricingService` 被 clamp 為非負的 `discount` 欄位；新增 `priceAdjustmentType`（`DISCOUNT`/`MARKUP`/`NONE`）於 `AvailabilityResponse` + `CalendarResponse` 明示方向；方向由 BookingService 新增的 `adjustmentDirection(original, adjusted)` private static helper 計算。保留既有欄位名（向後相容），僅擴語意 + 加方向欄位。
  - **測試**：`BookingServiceDynamicPricingTest` +漲價案例 `UT-BK-DP-004`（weekend markup → totalPrice 含漲價、discountAmount 負、priceAdjustmentType=MARKUP）、`UT-BK-DP-001` 補 DISCOUNT 斷言；`PricingServiceTest` +Nested `MarkupAndFallbackTests`（`UT-M12-013` MANUAL_OVERRIDE 漲價 / `UT-M12-014` 無 Room 但 listing 存在 → 降級不 throw）；`BookingControllerE2ETest` +漲價整合 `API-M06-015`（真 DB seed MANUAL_OVERRIDE 漲價 rule → availability totalPrice=6000/original=3000/MARKUP/discountAmount=-3000 + 建單 booking.totalAmount=6000）。
- **US-002（前端，AI-2406b 前端，commit `151ce2d`）**：
  - `ListingDetail` availability 雙向顯示：閘門由 `>0` 放寬為 `!== 0`；折扣（discountAmount>0）維持原價刪除線 + 綠 badge「規則名｜省 X」；漲價（<0）原價**不刪除線** + 橙 badge（`bg-rs-warning/10 text-rs-warning`）「規則名｜加價 X」（`Math.abs`）。
  - `MonthCalendar` 每日格：僅「調整後價 < 原價」（折扣）才 `line-through`，漲價日原價不刪除線；保留 `calendar-price-{date}` / `calendar-original-price-{date}` testid。
  - `booking.ts`：`AvailabilityResponse` + `CalendarDay` 各加 `priceAdjustmentType?: string | null`，更新為中性「調整」語意註解。
  - **E2E**：新增 `E2E-ROOM-08`（availability 漲價 mock：totalPrice 7,680／原價 6,400 不刪除線／加價 1,280 橙標）+ `E2E-ROOM-09`（calendar 每日漲價 mock：3,840／原價 3,200 不刪除線）。既有折扣案例（ROOM-06/07）保留不退步。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯（mvn）| ✅ 0 error |
| checkstyle | ✅ BUILD SUCCESS（`calculatePrice` NPath 曾 240>200，抽 `resolveListingForPricing` 後解決）|
| 後端單元（`BookingServiceDynamicPricingTest` 4 + `PricingServiceTest` 14）| ✅ **18 tests 0 fail**（本次評審 session 複驗 BUILD SUCCESS）|
| 後端整合（真實 DB：`BookingControllerE2ETest` 19 + `M12PricingIntegrationTest` 8 + `M12PricingProductIntegrationTest` 3 + `BookingIntegrationTest` 4）| ✅ **34 tests 0 fail**（US-001 開發 session 驗證；push 時 `make validate-release` 完整複驗）|
| 本地 E2E 守門（`make validate-e2e`）| ✅ **50 passed / 6 skipped / 0 failed**（含新增 E2E-ROOM-08/09 漲價變體；相較 S45 48 passed +2；折扣案例不退步）|
| schema 漂移 | ✅ 無（backend 以 ddl-auto=validate 對齊 Flyway 重建 DB 啟動成功）；**無 schema 變動**（Flyway V57，連續 S42~S46 零 migration）|
| 前端 tsc / lint | ✅ tsc 0 error；lint 0 error（既有 94 個 `import/no-anonymous-default-export` warning 與本次無關；本次改的 3 檔零 warning）|
| catch(Exception) / @Deprecated 計數 | ✅ 維持 0（保留 catch(BusinessException) 失敗降級，未引入寬泛 catch）|

---

## 4. 誠實揭露（Rule 12）

1. **本 Sprint 改變既有訂房金額行為（已 PO 拍板）**：`DYNAMIC_PRICING_ENABLED` 開啟時，賣家設的漲價型規則（週末/旺季/手動調高）將開始計入訂房總價與下單金額。toggle 關閉則**完全不變**（向後相容），計算失敗亦降級 basePrice。整合測試 `API-M06-015` seed 漲價規則證實生效，既有折扣/無規則案例不退步。
2. **E2E 編號與計劃不同**：計劃（§AC-002-3）原寫「新增 E2E-ROOM-06/07 漲價變體」，但 ROOM-06/07 早於 S43/S44 建立為**折扣**案例（保留不動）。實際漲價變體以 **E2E-ROOM-08/09** 新增，避免覆蓋既有折扣案例。行為與計劃一致，僅編號順延。
3. **只做 ROOM，PRODUCT/cart 明確 out-of-scope**：`RedisCartService.tryProductDiscount` 僅套折扣、漲價不計入購物車，本 Sprint 未動。如需 PRODUCT 漲價另立。
4. **`bestRule` priority 治理落差（記錄不修）**：規則選取採「最高 priority 勝出」而非「最低價勝出」，漲價/折扣規則可能互蓋。屬既有 priority 治理語意（折扣時期即存在），本 Sprint 不修；測試未暴露非預期。逐日精準規則選取另立評估。
5. **`findActiveRulesForDateRange` 部分晚數落差（記錄不修）**：查詢要求規則涵蓋整段區間，部分晚數 SEASONAL 會漏套（折扣亦受同限）。屬既有查詢語意，本 Sprint 不修；逐日精準查詢另立評估。
6. **後端整合 34 tests 於開發 session 驗證**：本評審 session 已就地複驗後端單元 18 + validate-e2e 50/6/0 + schema 無漂移；真 DB 整合 34 tests 於 US-001 開發 session 全綠，並將於 push 前 `make validate-release`（等價雲端 ci.yml）完整複驗。
7. **push 債累積 S41~S46（6 Sprint）**：本 Sprint 有實質後端 + 前端變動（計價行為變更）→ push 需完整 `make validate-release`。承 S41~S45 累積，於檢查點徵詢後一次守門 push（AI-1908；嚴禁 --no-verify）。
8. **上一對話環境故障事件**：US-001 開發 session 曾遇 Bash 工具「每命令重複輸出」故障（`printf` 3 行→6 行、commit EXIT=0 但未落盤），使用者重啟 Mac 後恢復。本評審 session 開工即以 probe（3 行→3 行）確認環境乾淨，並交叉驗證 US-001 commit（`20a399b`）內容正確（6 檔）、幽靈變更（PricingDto/BookingServiceTest）已清、`priceAdjustmentDirection` 殘留 0 筆後才續作。已寫入專案記憶避免重蹈。

---

## 5. Demo 重點

- **顯示 = 收費（M12 收官）**：向團隊展示週末/旺季/手動調高規則生效下，availability 查詢、月曆每日價、實際建單 `totalAmount` 三者**全部含漲價且一致**，終結 S43~S44 的「顯示加價、下單原價」不一致。
- **雙向顯示**：折扣（綠 badge「省 X」+ 原價刪除線）vs 漲價（橙 badge「加價 X」+ 原價不刪除線），由後端 `priceAdjustmentType` / 有號 `discountAmount` 驅動。
- **優雅降級**：展示無 Room 記錄 / null basePrice 情境不再 500，而是降級或 4xx。
- **不退步證明**：validate-e2e 50/6/0（既有折扣 ROOM-06/07 + 全買家閉環全綠）、後端單元 18 + 整合 34 全過。

---

**文件版本**: v1.0
**建立日期**: 2026-07-03
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
