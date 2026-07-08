# Sprint 83 Plan：M12 動態定價 — 房東後台 90 天定價日曆預覽（PRD P0）

**Sprint**: Sprint 83
**期間**: 2026-07-08
**主軸**: PRD v1.0 缺口盤點後，使用者選定的 P0 Must Have 項目

---

## 背景

PRD 983 行「定價預覽｜房東在後台預覽未來 90 天定價日曆｜P0」、2316 行 Phase 2-A 交付物明列「定價日曆預覽」。目前 `PricingController`/`DashboardPricingController` 只有 `POST /calculate`（單次試算，需輸入 checkIn/checkOut 模擬一次入住）與 `POST /calendar/price`（手動覆蓋寫入），沒有任何「唯讀瀏覽未來 90 天逐日定價」的 GET 端點。

## 架構決策：完全複用 `BookingService.getCalendar`，不重寫定價日曆邏輯

探查確認 `BookingService.getCalendar(roomListingId, startDate, endDate)`（Sprint 41/45/47 累積建置）已經是「逐日定價 + 開放窗 NOT_OPEN 判斷」的完整封裝，且已支援最長 92 天區間（`MAX_CALENDAR_RANGE_DAYS`），回傳結構含 `date/status/price/originalPrice/appliedRuleName/priceAdjustmentType`，與本次需求完全吻合。唯一缺口：此方法目前僅供買家瀏覽（無租戶擁有權檢查，因為買家瀏覽任意已上架房源的日曆本就該公開），房東後台版本需要驗證呼叫者是否為該房源所屬租戶（或 SUPER_ADMIN），避免任一租戶讀取他租戶房源的定價策略明細（`appliedRuleName` 等屬營運機密）。

修復方式：`BookingService` 新增 `getCalendarForOwner(roomListingId, startDate, endDate, isSuperAdmin)`，比照 Sprint 80-82 累積的 `checkTenantAccess` 模式驗證擁有權後，直接委派給既有 `getCalendar` 取得資料——**零重複邏輯**。

## 變更點

- `BookingService.getCalendarForOwner(...)`：新增租戶擁有權檢查 + 委派既有 `getCalendar`。
- `PricingController` 新增 `GET /v2/dashboard/pricing/calendar?roomListingId=&startDate=&endDate=`（`room:read`/`product:read` 權限，`@AuthenticationPrincipal UserPrincipal` 判斷 `isSuperAdmin`，比照 `TransferController`/`SettlementController` 既有模式）。
- 前端 `PricingCalendarPreview.tsx` 新增自動載入的 90 天逐日日曆區塊（掛載時自動打 `GET /calendar` 端點顯示未來 90 天，不需手動選日期），保留既有的「試算特定入住區間」表單不變（兩者用途不同：日曆是唯讀總覽，試算是模擬特定訂單的晚數/總額，兩者對應不同 API 語意）。

## 測試

- `BookingServiceTest`（或新檔）：非 SUPER_ADMIN 讀取他租戶房源日曆 → `E_1007`；本租戶讀取 → 成功且與 `getCalendar` 結果一致；SUPER_ADMIN 跨租戶放行。
- 全量回歸 `mvn verify -Pintegration-test` + `make validate-schema`（無 migration，純新增方法/端點）。

## 順手發現但本 Sprint 不處理的新問題（記錄為 DEF-041）

`RoomService.updateRoom`/`deleteRoom`/`clearOpenWindow` 與 `ProductService.updateProduct`/`deleteProduct` 皆透過 `findRoomByListingId`/`findProductByListingId` 直接以 `listingId` 查詢後就地修改/刪除，**完全沒有租戶擁有權檢查**。任一持有 `room:update`/`product:update` 權限的使用者，只要知道他租戶的 `listingId`，即可竄改或刪除該房源/商品（改價格、改狀態、下架）。比 DEF-034/DEF-040 更嚴重（那些是跨租戶讀取，這個是跨租戶寫入/刪除核心商業資料），記錄於 `DEFERRED_ITEMS_TRACKER.md`，本 Sprint 依範圍不修改，待下一輪優先處理。

---

**文件版本**: v1.0
**建立日期**: 2026-07-08
