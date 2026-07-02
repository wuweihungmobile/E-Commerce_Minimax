# Sprint 37 Review / Sprint 37 評審會議

> **Sprint 編號**: Sprint 37
> **期間**: 2027-03-14 ~ 2027-03-27
> **評審日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: 買家頁全頁套用共用賣場版型 + 清償 push 債

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | (auth) 買家頁全頁套用共用賣場版型（AI-1901）| 5 | ✅ 完成 |
| US-002 | m15 flaky 修復 + S32~S37 批次 push（AI-2001/AI-1906）| 3 | ✅ m15 修復完成；push 待檢查點 |
| US-003 | 買家頁套版後版型一致性 E2E | 2 | ✅ 完成 |
| US-004（Buffer）| 買家閉環 live 走查（AI-1903）| 2 | ⏸️ 順延（需 live 環境）|

**承諾 10 SP（US-001~003）全數完成**。US-002 的 push 部分待檢查點徵詢後執行（m15 阻礙已清）。

---

## 2. 交付內容

### US-001：買家頁全頁套用共用賣場版型（AI-1901）
- 新增 `app/(auth)/layout.tsx` 承載共用 Header/Footer（route-group，換頁不重建 chrome）。
- 10 頁移除自包 nav/footer、內容改用 `StorefrontShell`（單欄）。
- `StorefrontHeader` 加 auth-aware 帳號選單（email/我的訂單/登出 · 登入/註冊），以 `useSyncExternalStore` + `authStore` 實作（避免 React 19 effect-setState、layout 不 re-render 的即時更新問題）。
- login/register 依使用者決策一併套版（全站一致）。
- 驗證：build/tsc/lint 0 error；`make validate-e2e` at-buyer-pages 不退步。

### US-002：m15 flaky 修復（AI-2001）
- 根因：媒體庫載入/篩選失敗觸發原生 `alert()`，dialog 於 teardown 間歇崩潰。
- 修法：dialog 處理器 + active class 斷言 + networkidle 等待。**解鎖 release 守門唯一阻礙**。

### US-003：版型一致性 E2E
- E2E-BUYER-04（多頁 Header/Footer 唯一）、E2E-BUYER-05（帳號選單登出 → 轉訪客）。

### 計畫外必要工作
- **E2E 登入 helper 碰撞修復**：US-001 副作用（SearchBar submit 誤中），修 7 檔登入 helper。
- **secret 掃描器正則收緊**：消除 password 表單標籤誤報（阻擋 US-001 commit 的根因）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 前端 build（Turbopack 39 頁）| ✅ 0 error |
| type-check / lint | ✅ 0 error（僅既有 warning）|
| `make validate-e2e`（乾淨 DB 全棧）| ✅ **37 passed / 6 skipped / 0 failed** |
| 後端 / DB | 無變動（Flyway V57）|
| 多租戶隔離 | 不受影響（買家頁資料抓取不變）|

---

## 4. 誠實揭露（Rule 12）

1. **測試選擇器碰撞（已修）**：US-001 加 SearchBar Header 到 /login，使 15 個依賴登入的 E2E 因 `button[type="submit"]` 誤中搜尋鈕而失敗；實測揪出並全數修復（選擇器排除搜尋鈕）。屬測試碰撞非產品缺陷。
2. **secret 掃描器誤報（已修）**：US-001 commit 首次被 pre-commit secret 掃描擋下（login/register 的 `htmlFor="password"` 被誤判）。經使用者核准，收緊掃描器正則（真實機密仍偵測）。
3. **at-homepage E2E-HOME-01 歧義（已修）**：頁尾版權文案與首頁引導標語含相同子字串 → strict 歧義（間歇失敗）；改用 `storefront-footer` testid 消除。
4. **US-004 買家 live 走查順延**：需 live 環境，誠實登記至 S38。
5. **商品詳情頁未實現**：非本 Sprint 範圍，另立項。
6. **push 未執行**：main 領先 origin/main 27（S32~S37）；push 需完整 `make validate-release` + 檢查點徵詢（AI-1906）。

---

## 5. Demo 重點

- 買家頁（購物車/訂單/通知/預訂/評價/結帳）與登入/註冊皆呈現統一「意象若水」Header/Footer；換頁不重建、主題切換一致。
- 已登入時 Header 帳號選單顯示 email + 我的訂單 + 登出；登出即時轉訪客。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
