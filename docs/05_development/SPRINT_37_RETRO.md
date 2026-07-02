# Sprint 37 Retrospective / Sprint 37 回顧會議

> **Sprint 編號**: Sprint 37
> **期間**: 2027-03-14 ~ 2027-03-27
> **回顧日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP（P1+P2）| 10 SP（US-001~003）|
| 完成 SP | 10 SP（全數）|
| Buffer | US-004 買家 live 走查（延，需 live 環境）|
| 主題 | 買家頁全頁套版（DEF-020 基建延伸）+ 清償 m15 flaky 解鎖 push |
| 清償項 | AI-1901（套版）、AI-2001（m15 flaky）|

---

## 2. 做得好的（What went well）

- **DEF-020 前置投資兌現**：S36 的 route-group + grid-only StorefrontShell 基建，讓 S37 買家頁套版變成「建 layout + 逐頁去 chrome」的機械式工作，10 頁順利收斂。前置架構債償還在此 Sprint 明顯回本。
- **實測驅動揪出連鎖問題**：`make validate-e2e` 一次揪出 US-001 造成的 15 個登入連鎖失敗（SearchBar submit 碰撞），並在同一輪追出 at-homepage 的 strict 歧義——沒有靠「靜態綠」自欺，而以全棧實測逼出真問題。
- **根因導向修 flaky**：m15 依 CI 修復鐵律先看實際 error-context（dialog teardown），定位到 `loadMedia` catch 的原生 `alert()`，對症下藥（dialog 處理器 + 明確等待 + 真斷言），而非長期靠重跑掩蓋。
- **React 19 嚴格 hooks 正解**：帳號區以 `useSyncExternalStore` + 外部 store 實作，正確處理「hydration 安全 + layout 不 re-render 仍能即時更新」，未再踩 effect-setState 禁令。
- **安全工具誤報以「改進而非繞過」處理**：secret 掃描器誤報，未用 `--no-verify`，而是收緊正則（更精準），並先徵得使用者同意才動安全控制。

---

## 3. 待改善的（What to improve）

- **共用元件加到新頁面前，應先盤點測試契約衝突**：把含 SearchBar（`type=submit`）的 Header 加到 /login，直接打掛 15 個用 `button[type="submit"]` 的登入 helper。日後在「登入等關鍵流程頁」引入含表單的共用元件前，應先掃描 E2E helper 的選擇器假設。
- **E2E 登入 helper 選擇器過於寬鬆且重複**：`button[type="submit"]` + 固定 `waitForTimeout` 的登入 helper 在 9 個 spec 各有一份 copy，脆弱且難維護。應抽為共用 helper（testid 化 submit + `waitForURL` 明確等待），一次根治（含 DEF-022 硬等待）。
- **secret 掃描器早該有測試**：一個會誤判所有 password 表單頁的掃描器潛伏已久（2026-06-17 加入後未被觸發）。安全工具本身也應有正/負案例測試。
- **push 債仍在累積**：main 領先 origin/main 27（S32~S37）。本 Sprint 已清 m15（唯一守門阻礙），應把握此檢查點完成 push，勿再滾大。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1906 | 檢查點徵詢後 push S32~S37 累積批次 | m15 已清 → `make validate-release` 綠燈後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-2101 | E2E 共用登入 helper 抽取 | 9 份重複 registerAndLogin 抽為單一共用 helper：submit 用 testid、改 `waitForURL`/`waitForResponse`（連帶收斂 DEF-022 硬等待）| QA Quincy | P2 | S38 |
| AI-2102 | secret 掃描器正/負案例測試 | 為 pre-commit secret 掃描加測試（真實機密須攔、表單標籤不可誤攔）| Dev David | P3 | 後續 |
| AI-1903 | 買家閉環 live 走查 | 順延（需 live 環境）| QA Quincy | P1 | S38 |
| AI-1905 | 首頁/買家頁「有資料」E2E + 分頁 | 需 seed 商品，補自動化 | QA Quincy | P3 | 後續 |
| AI-2103 | 商品詳情頁評估 | 首頁目前只連評價頁，缺商品詳情頁；評估是否納入 backlog | PM Victoria | P2 | S38 規劃 |
| — | DEF-021（CJK 字體）/ DEF-022（E2E 硬等待）| 續延後（DEF-022 併入 AI-2101 一併處理）| — | P3 | 後續 |

---

## 5. Sprint 36 → Sprint 37 Action Items 追蹤結果

| Action Item | 內容 | Sprint 37 達成狀態 |
|------------|------|---------------------|
| AI-1901 | (auth) 買家頁套共用 Shell | ✅ 完成（US-001，10 頁全頁套版）|
| AI-2001 | m15 flaky push 前處理 | ✅ 完成（US-002，根因修復，非重跑掩蓋）|
| AI-1906 | 檢查點 push S32~36（現為 S32~37）| ⏳ 待檢查點（m15 阻礙已清）|
| AI-1903 | 買家 live 走查 | ⏸️ 順延 S38（需 live 環境）|
| AI-1905 | 首頁有資料 E2E + 分頁 | ⏸️ 續延（需 seed）|
| AI-2002 | @Transactional 測試影子欄位指引 | ⏸️ 續延（後端無變動，本 Sprint 未觸及）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S32 | 5 |
| S33 | ~2 |
| S34 | 3 |
| S35 | 18（純前端異常高）|
| S36 | 10 |
| **S37** | **10** |

> **觀察**：S37 = 10 SP，延續 S36 健康區間（8~11）。表面為機械式套版，但實測揭露的連鎖問題（15 測失敗 + 掃描器誤報 + strict 歧義）耗費相當於 SP 的隱性成本——「加共用元件到關鍵頁」的風險被低估。品質仍紮實（全棧 0 failed）。

---

## 7. 下一步

> **Sprint 38（規劃中）**: 買家閉環 live 走查（AI-1903）+ E2E 共用 helper 抽取（AI-2101，含 DEF-022）+ 商品詳情頁評估（AI-2103）。並在檢查點徵詢後，把 S32~S37 累積批次經完整守門（`make validate-release`）一次 push（AI-1906）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
