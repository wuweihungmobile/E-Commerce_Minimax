# Release Notes - v2027.06.05-01 (Sprint 42)

**發布日期**: 2027-06-05（規劃）／實作完成 2026-07-02
**發布類型**: Patch（技術債收尾：工具/CI 提速 + 前端小功能 + 測試穩定性；無 production code / schema 變動）
**Sprint**: Sprint 42
**狀態**: ⏳ 待 push（本 Sprint 5 commit；承 S41 push 債，累積 S41+S42 於檢查點徵詢後完整 `make validate-release` 後 push）

> Sprint 42 主題：**收尾技術債**。清償 S41 遺留的小型技術債——加速 backend pre-commit（排除 2 個慢速 @SpringBootTest 並移除其對 test DB 的依賴）、整月日曆每日價格顯示（純前端）、清除 E2E 硬等待（waitForTimeout → 顯式等待，並根治連帶浮現的既有 flaky）。後端變動限於**測試 `@Tag` 註解**（無 production code、無 entity/migration/schema）。

---

## 新功能 ✨

- **整月日曆每日價格顯示（AI-2202c Part A，US-002）**：ROOM 詳情頁整月日曆的可訂日格由「純日號」改為「日號 + 每日價格」。無 `room_calendar` 記錄之日以 `listing.basePrice` 補齊（與 checkAvailability 一致），不可訂日維持灰刪除線。
  - 前端：`MonthCalendar` 新增 `priceByDate` map（保留原丟棄的 `d.price`）+ `basePrice`/`currency` props + `formatCellPrice()`；`ListingDetail` 傳入 `listing.basePrice`/`listing.currency`。純前端，後端 `GET /v2/bookings/calendar` 已回 `price`。

## 改進 🚀

- **backend pre-commit 提速 + 移除 test DB 依賴（AI-2302，US-001）**：為 2 個慢速 `@SpringBootTest` 核心測試（`ReviewServiceCacheIntegrationTest`、`SellerDashboardServiceCacheTest`）加 `@Tag("slow")`，`backend/hooks/pre-commit`（quick test）與 `Makefile` `check-backend` 加 `-DexcludedGroups=slow`。**CI（act `mvn test`/`mvn verify`）不加排除** → pre-push 仍完整跑這 2 測試，**零覆蓋損失**。順帶好處：pre-commit quick test 排除這 2 個需 Spring context/DB 的測試後為純單元，**backend commit 不再需 `make test-db-up`**。
- **E2E 硬等待清除（DEF-022，US-003）**：清除 5 檔 E2E 冗餘 `waitForTimeout` 固定 sleep（at-m11-cart-checkout、at-m15-e2e、at-m17-001/002），可替換者改顯式等待條件（`waitForURL`/`waitForResponse`/`expect().toBeVisible()`/`toHaveClass()`）並順帶補斷言（Rule 9）；**保留** `at-m10-chat.spec.ts` STOMP SUBSCRIBE settle（無 client 可觀察訊號，移除會 flaky，附註解）。

## Bug 修復 🐛

- **E2E flaky 根因修復（US-003 連帶）**：移除固定 sleep 後重新暴露 2 類既有 flaky，對症根治（非重跑掩蓋）——
  - `e2e/helpers/auth.ts` `registerAndLogin`：於 /login 點「註冊」連結間歇逾時。**根因**：S37 起 /login 也渲染共用 StorefrontHeader，其訪客區有 `<a href="/register">註冊</a>`，`.first()` 恆選到 header 連結，而其於失敗登入後 re-render 時不穩定 → click 逾時（at-buyer-pages BUYER-03/04 flaky 根因，Playwright 快照佐證）。**修法**：改直接 `goto('/register')`，消除歧義（確定性導航，全體共用此 helper 的 spec 更穩健）。
  - `at-m15-e2e.spec.ts` 檔案級 `dialog` beforeEach + `at-m17-002.spec.ts` approve/reject `dialog` 處理器：涵蓋 /cms 載入失敗與審核成功的原生 `alert()`，避免 dialog 於 teardown 間歇造成 session 崩潰。

## 測試 / 驗證 ✅

- **後端**：pre-commit quick test 於 **test DB DOWN** 狀態下 **455 tests 0 fail、無 DB 連線錯誤**（驗證 AI-2302 排除 slow 後無 DB 依賴）；2 個 slow 測試於 pre-push act（無 `excludedGroups`）仍完整跑。
- **前端**：`tsc --noEmit` 0 error；ESLint 0 error；`npm run build`（Turbopack）0 error。
- **本地 E2E 守門（`make validate-e2e`：真後端 JAR + 真前端 + 乾淨 DB + Playwright）**：**46 passed / 6 skipped / 0 failed**（含 E2E-ROOM-05 新增日曆價格 `calendar-price-{date}` 斷言；flaky 根因修復後 at-buyer-pages/at-m15/at-m17 全過；既有全數不退步）。
- **schema 漂移**：無（backend 以 ddl-auto=validate + Flyway 對乾淨 DB 啟動成功）。

## 技術決策 / 已知限制 ⚠️

- **後端零 production 變動**：US-001 僅動測試 `@Tag` 註解與 `pre-commit`/`Makefile`，無 entity/migration/schema/Flyway 變動。
- **AI-2202c 僅 Part A（誠實揭露 Rule 12）**：交付每日價格顯示（純前端）。**Part B「未開放 vs 可訂」顯式標記需後端新語意**（現無「開放窗」概念，`room_calendar` 的 AVAILABLE row 為偶發）→ 另立 **AI-2202d** 待評估，非本 Sprint 範圍。
- **validate-e2e 反覆 4 次才綠（誠實揭露）**：前 3 次含 2 次 Turbopack build 抓 `Inter`（Google Fonts）的**暫時性網路失敗**（非程式，同源 DEF-015），與 DEF-022 清 sleep 後浮現的既有 flaky；第 5 次全綠。全程未以 `E2E_GATE_STRICT=0` 放行。已立 AI-2303 評估 Inter 自 host 離線化。

## 資料庫遷移 🗄️

- 無（Flyway 維持 V57）。

## 內含 Commit（Sprint 42）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 42 Plan | e17d68e | 收尾技術債（3 US / 7 SP）|
| US-001 AI-2302 | fa4ee9f | backend pre-commit 提速 + 移除 DB 依賴（@Tag slow + excludedGroups）|
| US-002 AI-2202c | 09ae798 | 整月日曆每日價格顯示（Part A，純前端）|
| US-003 DEF-022 | d951ec1 | E2E 硬等待清除（5 檔 + 改顯式等待 + 補斷言，保留 STOMP）|
| US-003 flaky 修復 | b4de914 | auth helper 註冊連結碰撞改 goto + 原生 alert teardown dialog 處理器 |
| Sprint 42 收尾 | （本次）| Review / Retro / Release Notes + trackers |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**基於**: AISDLC v0.09 Release Management Workflow
