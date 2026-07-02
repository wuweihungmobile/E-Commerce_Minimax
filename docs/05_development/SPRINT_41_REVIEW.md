# Sprint 41 Review / Sprint 41 評審會議

> **Sprint 編號**: Sprint 41
> **期間**: 2027-05-09 ~ 2027-05-22
> **評審日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: S41 技術債徹底清償 + 整月日曆

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | test DB↔act port 制度化（AI-2301）| 1 | ✅ 完成 |
| US-002 | E2E 登入 helper 完全統一（AI-2101b）| 2 | ✅ 完成 |
| US-003 | api.ts 端點契約清理（AI-2202a）| 1 | ✅ 完成 |
| US-004 | 整月日曆（read-only 後端 + 前端）（AI-2202b）| 5 | ✅ 完成 |
| US-005 | 買家閉環走查（自動證據 + 手動 checklist）（AI-1903）| 2 | ✅ 完成（含誠實殘留）|
| US-006 | CJK 字體技術決策（DEF-021）| 1 | ✅ 完成（評估 + 決策）|

**承諾 12 SP（US-001~006）全數完成**。使用者指示「徹底清償全候選」達成。

---

## 2. 交付內容

- **US-001（工具/CI）**：`Makefile` `validate-release` 前插自動 `test-db-down`（冪等，涵蓋直接執行 + pre-push 兩路徑）；`LOCAL_CI_VALIDATION.md` 新增「變更四」制度化段落 + 開發者心智模型表 + 維護記錄 3.1。
- **US-002（測試）**：`e2e/helpers/auth.ts` 擴充（`registerAndLogin` 回傳 `userId`、新增 `loginOnly`）；重構 at-m10-chat（移除本地重複 helper）+ at-m17-001/002/003/004（inline → 共用）。
- **US-003（前端）**：`api.ts` `pricing.*` base path 對齊 `/v2/dashboard/pricing`、移除死碼 `listings.update/delete`、`bookings.calendar` realign。
- **US-004（後端 + 前端）**：後端 `GET /v2/bookings/calendar`（read-only）+ `BookingService.getCalendar` + 2 測試；前端 `MonthCalendar` 元件 + `booking.ts getCalendar` + `ListingDetail` 接入 + E2E-ROOM-05。
- **US-005（走查）**：`make validate-e2e` 自動全棧走查證據（綠）+ `BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md` 手動 checklist（含 DEF-014 API URL 修正、dev Flyway 無 seed 注意、C 節殘留）。
- **US-006（品質）**：`CJK_FONT_ASSESSMENT.md` 技術評估 + 決策建議（維持系統堆疊 accepted fallback）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端單元/整合（BookingControllerE2ETest）| ✅ **Tests run 18, Failures 0, Errors 0**（含新 API-M06-013/014 calendar）|
| 後端 pre-commit | ✅ checkstyle + compile + 核心測試通過 |
| 前端 tsc / ESLint / build | ✅ 0 error（Turbopack build 通過）|
| 本地 E2E 守門（make validate-e2e）| ✅ **47 passed / 5 skipped / 0 failed**（含 E2E-ROOM-05 整月日曆；重構後 m10/m17 全過）|
| schema 漂移 | ✅ 無（ddl-auto=validate 對齊，backend 啟動成功）|
| DB/migration | 無變動（Flyway V57）|

---

## 4. 誠實揭露（Rule 12）

1. **AI-1903 非完整完成**：交付自動 E2E 證據 + 手動 checklist，但真 DB 落地的跨角色資料流（order→pay→notify→ship→review）需 cross-role seed + 部署環境，**仍為殘留**，AI-1903 續留待真人 live 走查。未謊稱「買家閉環已 live 驗證」。
2. **DEF-021 為評估型 US**：交付技術評估 + 決策建議（維持系統堆疊）；未實作 `@font-face` 自 host 字體（P3、需 1-3MB 二進位 + spike，待使用者拍板）。
3. **12 SP 略高於近期均值（~9.4）**：因使用者指示徹底清償全候選；6 項中 4 項為小型（1-2 SP），僅 US-004 旗艦（5 SP）。全數如期完成。
4. **後端變動 read-only**：僅新增 calendar 端點（用既有 repository），無 entity/migration/schema。
5. **commit 中斷插曲**：US-004 後端 commit 首次因 pre-commit 核心測試（@SpringBootTest 逐一啟動 Spring）超過 2 分鐘被工具 timeout 中斷（commit 未成），以更長 timeout 重跑成功——非程式問題。

---

## 5. Demo 重點

- ROOM 詳情頁：整月日曆載入 → 已訂日灰色刪除線禁選 → 點選可訂區間帶入日期 → 查詢可用性「可預訂 · N 晚合計」→ 加入購物車。
- 月份切換（‹ ›）重新載入該月可訂狀況。
- 開發者體驗：`make validate-release` 自動清理 test DB，不再手動 test-db-down。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
