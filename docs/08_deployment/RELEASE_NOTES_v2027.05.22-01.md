# Release Notes - v2027.05.22-01 (Sprint 41)

**發布日期**: 2027-05-22（規劃）／實作完成 2026-07-02
**發布類型**: Minor（技術債清償 + 整月日曆功能 + 小幅 read-only 後端）
**Sprint**: Sprint 41
**狀態**: ⏳ 待 push（本 Sprint 8 commit，檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 41 主題：**S41 技術債徹底清償 + 整月日曆**。一次清償 S40 遺留全部 Action Items 與活躍 DEF-021——制度化 test DB↔act port 衝突、完全統一 E2E 登入 helper、清理 api.ts 端點契約、新增整月日曆（含 read-only 後端端點）、買家閉環走查（自動證據 + 手動 checklist）、CJK 字體技術決策。後端變動限於**新增 read-only 月端點**（無 DB/schema/Flyway 變動）。

---

## 新功能 ✨

- **整月日曆（AI-2202b，US-004）**：ROOM 詳情頁新增整月日曆——每日可訂狀況著色、月份切換、點選挑選入住/退房區間（與日期輸入框同步）。已預訂/封鎖/過去日期禁選。取代 S40 的純區間查詢，讓買家一眼看整月可訂狀況。
  - 後端：新增 `GET /v2/bookings/calendar?roomListingId=&startDate=&endDate=`（`booking:read`），**read-only**，複用既有 `room_calendar` 資料與 `RoomCalendarService.getCalendarRange`；含日期區間上限 92 天防濫用。
  - 前端：新增 `MonthCalendar` 元件 + `booking.ts` `getCalendar`；接入 `ListingDetail` ROOM 分支。

## 改進 🚀

- **test DB ↔ act port 衝突制度化（AI-2301，US-001）**：`make validate-release` 於 `validate-all`（act）前**自動 `test-db-down`**（冪等），釋放 :5432/:6379 給 act 服務容器；直接執行與 pre-push hook 兩路徑一次涵蓋，消除人工記憶依賴。文件化開發者心智模型（commit 用 test-db-up、validate-release 已自動 down）。
- **E2E 登入 helper 完全統一（AI-2101b，US-002）**：擴充 `e2e/helpers/auth.ts`（`registerAndLogin` 回傳增加 `userId`、新增 `loginOnly`）；重構 5 個 spec（at-m10-chat、at-m17-001/002/003/004）移除重複 inline 登入碼，全數改用共用 helper。
- **api.ts 端點契約清理（AI-2202a，US-003）**：`pricing.*` base path `/v2/pricing` → `/v2/dashboard/pricing`（對齊後端 PricingController）；移除死碼 `listings.update/delete`（後端無 PUT/DELETE、前端 0 使用）；`bookings.calendar` realign 至真實月端點路徑。

## Bug 修復 🐛

- 修正 `pricing.*` 前端端點 base path 與後端不符（原 `/v2/pricing/*` 指向未實作路徑）。

## 測試 / 驗證 ✅

- **後端**：`BookingControllerE2ETest` 新增 API-M06-013（整月日曆含 BOOKED 日期斷言）+ API-M06-014（未授權 401/403）→ **Tests run 18, Failures 0, Errors 0**；pre-commit checkstyle + compile + 核心測試通過。
- **前端**：`tsc --noEmit` 0 error；ESLint 0 error；`npm run build`（Turbopack）0 error。
- **本地 E2E 守門（`make validate-e2e`：真後端 JAR + 真前端 + 乾淨 DB + Playwright）**：**47 passed / 5 skipped / 0 failed**（含新增 E2E-ROOM-05 整月日曆；重構後 at-m10/at-m17-001~004 登入全過；既有全數不退步）。
- **schema 漂移**：無（backend 以 ddl-auto=validate + Flyway 對乾淨 DB 啟動成功 → entity 與 schema 對齊）。

## 技術決策 / 已知限制 ⚠️

- **後端變動 read-only**：僅**新增** `GET /v2/bookings/calendar`（用既有 repository 查詢），無 entity/migration/schema/Flyway 變動。
- **AI-1903 買家 live 走查（US-005，誠實揭露 Rule 12）**：交付**自動全棧走查證據**（`make validate-e2e` 綠）+ **手動 live 走查 checklist**（`docs/03_testing/BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md`）。**殘留**：自動 E2E 買家流程多為 `page.route` mock，未證真 DB 落地的跨角色資料流（order→pay→notify→ship→review 需 cross-role seed + 部署環境）→ AI-1903 續留待真人於 live 環境走查。
- **DEF-021 CJK 字體（US-006）決策**：維持系統字體堆疊並結案為 **accepted fallback**；`@font-face` 靜態子集 woff2 路徑（技術可行、無 CSP 阻擋）記錄為選配未來任務。P3 不逕行加入 1-3MB 字體二進位，需使用者拍板。詳見 `docs/06_quality/CJK_FONT_ASSESSMENT.md`。
- **整月日曆覆蓋範圍**：後端回傳區間內「已有 room_calendar 記錄」之日期，無記錄之日由前端視為可訂（與 checkAvailability 一致）。

## 資料庫遷移 🗄️

- 無（Flyway 維持 V57）。

## 內含 Commit（Sprint 41）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 41 Plan | 84d994c | 技術債徹底清償 + 整月日曆（6 US / 12 SP）|
| US-001 AI-2301 | 4cf4a27 | validate-release 自動 test-db-down + 文件 |
| US-002 AI-2101b | e74d860 | E2E 登入 helper 完全統一（auth.ts + 5 spec）|
| US-003 AI-2202a | 14272aa | api.ts 端點契約清理（pricing/listings/calendar）|
| US-004 AI-2202b（後端）| dd93517 | 整月日曆 read-only 端點 + 測試 |
| US-004 AI-2202b（前端）| a70619b | MonthCalendar 元件 + ListingDetail 接入 + E2E |
| US-005 AI-1903 | 427a3ea | 買家閉環 live 走查 checklist |
| US-006 DEF-021 | 8b52b72 | CJK 字體技術評估 + 決策 |
| Sprint 41 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**基於**: AISDLC v0.09 Release Management Workflow
