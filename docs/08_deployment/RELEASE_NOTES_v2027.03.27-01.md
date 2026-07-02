# Release Notes - v2027.03.27-01 (Sprint 37)

**發布日期**: 2027-03-27（規劃）／實作完成 2026-07-02
**發布類型**: Minor（前端版型收斂 + 測試/工具健康度）
**Sprint**: Sprint 37
**狀態**: ⏳ 待 push（累積 S32~S37，完整守門 make validate-release + 檢查點徵詢後 push）

> Sprint 37 主題：買家頁全頁套用共用賣場版型 —— 沿用 S36 DEF-020 route-group 基建，將買家購物/帳戶頁由「各頁手包 nav/footer」統一收斂到共用 Header/Footer；並清償積壓的 m15 E2E flaky（解鎖 release 守門）。純前端 + 測試/工具，**無後端/DB 變動**。

---

## 改進 🚀

- **買家頁全頁套用共用賣場版型（AI-1901）**：
  - 新增 `app/(auth)/layout.tsx` 承載共用 `StorefrontHeader` + `StorefrontFooter`，換頁不重建 chrome。
  - 10 個買家頁（cart/checkout/orders/orders[id]/bookings/bookings[id]/notifications/reviews/login/register）移除各頁自包 nav/footer，內容改用 `StorefrontShell`（單欄）。
  - `StorefrontHeader` 新增 **auth-aware 帳號選單**：已登入顯示 email/我的訂單/登出，未登入顯示 登入/註冊（以 `useSyncExternalStore` 讀 auth 狀態，避免 React 19 禁止的 effect 內同步 setState；layout 不隨導覽 re-render → authStore 訂閱使登出後 Header 即時更新）。
  - 全站視覺一致（含 login/register，依使用者決策）；rs-* 主題 5 色票於買家頁一致生效。

## Bug 修復 🐛

- **m15「媒體庫篩選功能」E2E flaky 修復（AI-2001）**：根因為媒體庫載入/篩選失敗觸發原生 `alert()`（`loadMedia` catch），該 dialog 於 Playwright teardown 間歇造成 session 崩潰。修法：註冊 dialog 處理器自動關閉 + 以 active class 斷言取代「無斷言 + 固定 sleep」+ `waitForLoadState('networkidle')` 確保 refetch 完成才結束。**解除 `make validate-release` 嚴格守門的唯一阻礙**。
- **secret 掃描器誤報修正**：pre-commit secret 掃描正則過鬆（`(keyword).*=.*"值"` 會誤中 keyword 為屬性值的表單標籤，如 `<label htmlFor="password" className="...">`）。收緊為「keyword 緊接 `=`/`:` 再接引號值」（真實賦值）；真實硬編機密仍攔截。

## 測試 / 驗證 ✅

- **前端**：`npm run build`（Turbopack 39 頁）通過、`type-check` / `lint` 0 error。
- **E2E**：`make validate-e2e`（乾淨 DB 全棧 Playwright）**37 passed / 6 skipped / 0 failed**：
  - 新增 US-003 買家頁版型一致性：E2E-BUYER-04（多頁共用 Header/Footer 唯一）、E2E-BUYER-05（帳號選單登出 → 轉訪客）。
  - at-buyer-pages（01-03）/ at-homepage（01-06）/ m10 / m11 / m17 / m15 全數不退步。

## 技術決策 / 已知限制 ⚠️

- **測試副作用（誠實揭露）**：US-001 將含 SearchBar（`<button type="submit">搜尋`）的共用 Header 加到 `/login`，使 E2E 登入 helper 的 `click('button[type="submit"]')` 誤中搜尋鈕 → 空搜尋跳首頁 → 未登入 → 15 個依賴登入的測試連鎖失敗。已修：登入/表單 submit 選擇器排除搜尋鈕（`:not(:has-text("搜尋"))`）。屬測試選擇器碰撞，非產品缺陷（使用者已接受登入頁有搜尋列）。
- **login/register 版型**：依使用者決策「全站完全一致」，一併套共用 Header/Footer（含搜尋列/購物車 icon）。
- **商品詳情頁未實現**：首頁仍只連到評價頁 `/reviews/product/{id}`，非本 Sprint 範圍，另立項評估。
- **DEF-021（CJK 字體）/ DEF-022（E2E 硬等待）** 續延後（P3，非本 Sprint）。

## 資料庫遷移 🗄️

- 無（純前端 + 測試/工具變更，Flyway 維持 V57）。

## 內含 Commit（Sprint 37）

| US / 項目 | Commit | 說明 |
|----------|--------|------|
| Sprint 37 Plan | f4ef942 | 買家頁全頁套版 + push 債清償 |
| US-001 AI-1901 | 2ca4945 | 買家頁全頁套用共用賣場版型 |
| 工具修正 | 7e0ae7b | secret 掃描正則收緊（消除誤報）|
| US-002 AI-2001 | 0a44bba | m15 媒體庫篩選 flaky 修復 |
| US-003 | 2a8177d | 買家頁版型一致性 E2E |
| 測試修復 | de8ce12 | E2E 登入 helper SearchBar submit 碰撞 |
| Sprint 37 收尾 | （本次）| Review / Retro / Release Notes + tracker |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**基於**: AISDLC v0.09 Release Management Workflow
