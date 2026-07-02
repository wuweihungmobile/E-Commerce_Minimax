# Release Notes - v2027.04.24-01 (Sprint 39)

**發布日期**: 2027-04-24（規劃）／實作完成 2026-07-02
**發布類型**: Minor（ROOM 訂房閉環補強 + 測試基建）
**Sprint**: Sprint 39
**狀態**: ⏳ 待 push（累積 S32~S39，完整守門 make validate-release + 檢查點徵詢後 push）

> Sprint 39 主題：ROOM 訂房閉環補完。ROOM 訂房骨幹（詳情帶日期加購 → checkout 建立 booking → bookings 列表）**原已存在**;本 Sprint 補強衝突優雅處理、服務抽取、E2E 覆蓋與測試基建。純前端 + service，**無後端/DB 變動**。

---

## 改進 🚀

- **ROOM 訂房衝突優雅處理（AI-2103b）**：checkout 建立 booking 時,日期衝突（409 / `E-4001`）顯示「所選日期已被預訂,請返回修改日期」;房源未開放（E-3002）、超容量（E-4005）、無效日期（E-4003）等亦給對應可讀訊息（取代通用「預訂失敗」）。
- **BookingService.createBooking 抽取**：checkout 內聯的 booking 建立收斂為 `bookingService.createBooking`（含 Idempotency-Key）;移除重複型別定義。
- **詳情頁 ROOM 日期驗證**：加購前檢查「入住不早於今日、退房晚於入住」。
- **E2E 共用登入 helper（AI-2101 / DEF-022）**：新增 `e2e/helpers/auth.ts`（單一 `registerAndLogin`,以 `waitForURL` 條件等待取代固定 sleep、submit 排除搜尋鈕）;4 份 spec 改用共用 helper。

## Bug 修復 🐛

- **at-buyer-pages E2E-BUYER-02 通知 flaky**：「尚無通知」斷言 timeout 5s→15s（通知 fetch 慢時的既有間歇失敗）。

## 測試 / 驗證 ✅

- **前端**：`npm run build`（Turbopack）通過、`type-check` / `lint` 0 error。
- **E2E**：`make validate-e2e`（乾淨 DB 全棧 Playwright）**44 passed / 6 skipped / 0 failed**：
  - 新增 US-003 ROOM 訂房閉環（AI-2104，page.route mock）：E2E-ROOM-01 詳情選日期+計價+加購、E2E-ROOM-02 checkout 建 booking 成功、E2E-ROOM-03 日期衝突 409 優雅提示。
  - 既有 E2E（at-homepage / at-buyer-pages / at-listing-detail / m10 / m11 / m15 / m17）全數不退步（含共用 helper 重構後登入等價）。

## 技術決策 / 已知限制 ⚠️

- **誠實揭露：ROOM 訂房閉環原已存在**——checkout 頁早已內聯建立 booking,S38 詳情頁已能帶日期加購;本 Sprint 為**補強**（衝突處理/抽取/E2E）而非從零建。
- **availability 端點不可用（免後端 reframe）**：`GET /v2/bookings/availability` 為 GET+@RequestBody（瀏覽器 GET 無法送 body、前端無法呼叫）。故未做「詳情頁即時可用性檢查」,日期衝突回饋改在 checkout 建立時處理 409。完整可用性/整月日曆（需後端修 endpoint + 整月日曆 UI）另立項。
- **US-004 範圍誠實修正**：原估 9 份 spec,實際僅 5 份有 local `registerAndLogin`;`at-m10-chat`（helper 回傳 userId、簽名不同）保留專屬、`at-m17-*`（beforeEach 內聯,含固定 admin 帳號）未強改以免破壞登入語意 → 實收斂 4 份。
- **ROOM 購買路徑未統一**：維持現況 cart → checkout → 建立 booking（與 order 路徑平行,不統一）。

## 資料庫遷移 🗄️

- 無（純前端 + service 變更,Flyway 維持 V57）。

## 內含 Commit（Sprint 39）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 39 Plan | 47fb60f | ROOM 訂房閉環補完 |
| Plan 調整 | 85abb6e | availability GET+body 不可用 → 免後端 reframe |
| US-001+US-002 | 3e998f7 | booking service 抽取 + 衝突優雅處理 + 日期驗證 |
| US-003 AI-2104 | be80978 | ROOM 訂房閉環 E2E（mock）|
| US-004 AI-2101 | 5204300 | E2E 共用登入 helper 抽取 + 通知 flaky 修 |
| Sprint 39 收尾 | （本次）| Review / Retro / Release Notes + tracker |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**基於**: AISDLC v0.09 Release Management Workflow
