# Release Notes - v2027.05.08-01 (Sprint 40)

**發布日期**: 2027-05-08（規劃）／實作完成 2026-07-02
**發布類型**: Minor（ROOM 可用性 UX + 小幅後端修復）
**Sprint**: Sprint 40
**狀態**: ⏳ 待 push（累積 S32~S40，已通過完整 `make validate-release`；檢查點徵詢後 push）

> Sprint 40 主題：ROOM 可用性 UX 完成。修復 S39 揭露的 availability 端點缺陷（GET+@RequestBody 瀏覽器不可用）並補上詳情頁**即時可用性檢查**——買家選定日期即知「可預訂 + 總價」或「不可預訂 + 原因」,不可預訂時禁止加購。近期首次後端變動（read-only、**無 DB/schema/Flyway 變動**）。

---

## 改進 🚀

- **詳情頁 ROOM 即時可用性檢查（AI-2201）**：買家在商品詳情頁選定入住/退房日期後「查詢可用性」→ 顯示「可預訂 · N 晚合計」或「不可預訂 · {原因}」;**不可預訂時禁用「加入購物車」**——把日期衝突回饋從 checkout（S39）提前到詳情頁。
- **availability 端點修復（後端，read-only）**：`GET /v2/bookings/availability` 由 GET+@RequestBody（瀏覽器 GET 無法送 body、前端無法呼叫）改為 **@RequestParam**（roomListingId/checkInDate/checkOutDate）;service 簽名不變。

## Bug 修復 🐛

- 修正 availability 端點 REST 設計問題（GET+body）——此問題於 BookingControllerE2ETest 早有 TODO 註解自標「應改 @RequestParam」,本 Sprint 落實並更新測試。

## 測試 / 驗證 ✅

- **完整 `make validate-release` 通過**（act backend + frontend + schema + E2E）:
  - 後端 act：**Tests run 330, Failures 0, Errors 0**（含更新後的 BookingControllerE2ETest API-M06-009/010 query params + BookingIntegrationTest；M02RoomIntegrationTest 3 tests 0 fail）。
  - 前端 act：Lint & Build Job succeeded（0 error）。
  - E2E：**45 passed / 6 skipped / 0 failed**（新增 E2E-ROOM-01 可用性加購、E2E-ROOM-04 不可訂禁用;既有全數不退步）。
  - schema 漂移：無（ddl-auto=validate 對齊）。

## 技術決策 / 已知限制 ⚠️

- **首次後端變動 read-only**：僅 controller 參數綁定（@RequestBody→@RequestParam）+ 更新 3 處既有測試,無 entity/migration/schema 變動（Flyway 維持 V57）。
- **測試環境衝突（誠實揭露）**：後端 commit 的 pre-commit 核心測試需 postgres:5432/redis:6379（`make test-db-up`）;而 `make validate-release`（act）自帶 redis 需 6379 → 兩者不可同時佔用。流程：commit 時啟 test DB、validate-release 前 `make test-db-down`。
- **詳情頁即時可用性覆蓋範圍**：以 `/v2/bookings/availability` 區間查詢（可訂+總價+原因）;完整整月日曆 UI 另立項。
- **ROOM 購買路徑未統一**：維持 cart→checkout→booking（與 order 平行）。
- **US-004 買家 live 走查順延**：需 live 環境,登記 S41。

## 資料庫遷移 🗄️

- 無（Flyway 維持 V57）。

## 內含 Commit（Sprint 40）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 40 Plan | 0a33be0 | ROOM 可用性 UX 完成（含小幅後端）|
| US-001 AI-2201（後端）| c9924a0 | availability 端點 @RequestParam + 3 處測試更新 |
| US-002 AI-2201（前端）| f56b597 | 詳情頁 ROOM 即時可用性檢查 |
| US-003 | a7ca821 | availability E2E（可訂加購 / 不可訂禁用）|
| Sprint 40 收尾 | （本次）| Review / Retro / Release Notes + tracker |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**基於**: AISDLC v0.09 Release Management Workflow
