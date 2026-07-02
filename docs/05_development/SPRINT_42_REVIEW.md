# Sprint 42 Review / Sprint 42 評審會議

> **Sprint 編號**: Sprint 42
> **期間**: 2027-05-23 ~ 2027-06-05
> **評審日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 收尾技術債

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | backend pre-commit 提速（AI-2302）| 2 | ✅ 完成 |
| US-002 | 整月日曆每日價格顯示（AI-2202c Part A）| 2 | ✅ 完成 |
| US-003 | E2E 硬等待清除（DEF-022）| 3 | ✅ 完成（含 flaky 根因修復）|

**承諾 7 SP（US-001~003）全數完成**。收尾型 Sprint，回歸健康偏保守區間。

---

## 2. 交付內容

- **US-001（工具/CI，AI-2302）**：為 2 個慢速 `@SpringBootTest` 核心測試（`ReviewServiceCacheIntegrationTest`、`SellerDashboardServiceCacheTest`）加 `@Tag("slow")`；`backend/hooks/pre-commit`（quick test 步驟）與 `Makefile` `check-backend` 加 `-DexcludedGroups=slow`。CI（act `mvn test`/`mvn verify`）**不加** `excludedGroups` → pre-push 仍完整跑這 2 測試（零覆蓋損失）。順帶好處：backend pre-commit quick test 不再需 test DB。`LOCAL_CI_VALIDATION.md` 同步更新（心智模型 + 維護記錄 3.2）。
- **US-002（前端，AI-2202c Part A）**：`MonthCalendar` 新增 `priceByDate` map（保留原丟棄的 `d.price`）+ `basePrice`/`currency` props；`ListingDetail` 傳入 `listing.basePrice`/`listing.currency`；可訂日格顯示每日價格（無 room_calendar 記錄之日以 basePrice fallback，cell 由純日號 → 日號 + 小字價格）；不可訂日維持灰刪除線。E2E-ROOM-05 補 `calendar-price-{date}` 斷言。
- **US-003（測試，DEF-022）**：清除 5 檔 E2E 冗餘 `waitForTimeout` 固定 sleep（at-m11-cart-checkout、at-m15-e2e、at-m17-001/002），可替換者改顯式等待（`waitForURL`/`waitForResponse`/`expect().toBeVisible()`/`toHaveClass()`）並順帶補斷言（Rule 9）；**保留** `at-m10-chat.spec.ts` STOMP SUBSCRIBE settle（無 client 可觀察訊號，移除會 flaky，附註解）。
  - **計畫外必要工作（flaky 根因修復）**：移除固定 sleep 後重新暴露兩類既有 flaky，對症根治（非重跑掩蓋）——(1) `helpers/auth.ts` `registerAndLogin` 於 /login 點「註冊」連結逾時：S37 共用 StorefrontHeader 於 /login 也有 `<a href="/register">註冊</a>`，`.first()` 恆選到 header 連結且其於失敗登入後 re-render 不穩定 → 改**直接 `goto('/register')`** 消除歧義（at-buyer-pages BUYER-03/04 flaky 根因，Playwright 快照佐證）；(2) at-m15-e2e 檔案級 `dialog` beforeEach + at-m17-002 approve/reject `dialog` 處理器，涵蓋 /cms 載入失敗與審核成功的原生 `alert()`（teardown session 崩潰根因）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端 pre-commit quick test（排除 slow + **test DB DOWN**）| ✅ 455 tests 0 fail、無 DB 連線錯誤（驗證 AI-2302 無 DB 依賴）|
| 後端 slow 測試於 pre-push act | ✅ 仍完整跑（act 無 `excludedGroups`，零覆蓋損失）|
| 前端 tsc / ESLint / build | ✅ 0 error（Turbopack build 通過）|
| 本地 E2E 守門（make validate-e2e）| ✅ **46 passed / 6 skipped / 0 failed**（含 E2E-ROOM-05 日曆價格斷言；flaky 根因修復後 at-buyer-pages/at-m15/at-m17 全過）|
| schema 漂移 | ✅ 無（ddl-auto=validate 對齊，backend 啟動成功）|
| DB/migration | 無變動（Flyway V57）；**無 production code 變動**（後端僅測試 `@Tag` 註解）|

---

## 4. 誠實揭露（Rule 12）

1. **AI-2202c 僅交付 Part A**：每日價格顯示（純前端，後端已回 `price`）。**Part B「未開放 vs 可訂」顯式標記需後端新語意**（現無「開放窗」概念，`room_calendar` 的 AVAILABLE row 為偶發）→ 另立 **AI-2202d**，非本 Sprint 範圍。誠實界線於計劃已宣告。
2. **DEF-022 觸發連鎖 flaky**：移除固定 sleep 後重新暴露 2 類既有 flaky（auth helper 註冊連結碰撞、原生 alert teardown）。第 4 次 validate-e2e 於 at-buyer-pages BUYER-03/04 失敗——**非本 Sprint 新 bug，而是既有 flaky 被 sleep 掩蓋**；已找到真根因（Playwright 快照佐證）並根治，非放寬守門。
3. **validate-e2e 反覆 4 次**：前 3 次分別因 Turbopack build 抓 Google Fonts 的**暫時性網路失敗**（非程式問題）與上述 flaky；第 5 次全綠。過程誠實記錄，未以 `E2E_GATE_STRICT=0` 放行。
4. **後端零 production 變動**：US-001 僅動測試 `@Tag` 與 hook/Makefile，無 entity/migration/schema。
5. **承 S41 push 債**：S41（10 commit）+ S42（5 commit）皆本地驗證通過但未 push；本 Sprint 有後端（測試註解）變動 → push 需完整 `make validate-release`，累積後於檢查點徵詢。

---

## 5. Demo 重點

- ROOM 詳情頁整月日曆：可訂日格顯示「日號 + 每日價格」（無記錄日以 basePrice 補齊），不可訂日維持灰刪除線。
- 開發者體驗：`backend` commit 不再需 `make test-db-up`（quick test 已無 DB 依賴），pre-commit 更快；2 個慢測仍於 pre-push act 完整把關。
- 測試穩定性：E2E 冗餘固定 sleep 清除，改顯式等待條件；auth helper 改 goto、alert 頁補 dialog 處理器，flaky 根治。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
