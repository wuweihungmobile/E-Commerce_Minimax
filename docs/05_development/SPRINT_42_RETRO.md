# Sprint 42 Retrospective / Sprint 42 回顧會議

> **Sprint 編號**: Sprint 42
> **期間**: 2027-05-23 ~ 2027-06-05
> **回顧日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 7 SP（US-001~003）|
| 完成 SP | 7 SP（全數）|
| 主題 | 收尾技術債（pre-commit 提速 + 日曆價格 + E2E 硬等待清除）|

---

## 2. 做得好的（What went well）

- **技術債選「制度化 / 根治」而非局部修**：AI-2302 用 `@Tag("slow")` + pre-commit `-DexcludedGroups=slow`，同時達成「提速」與「移除 pre-commit 對 test DB 的依賴」；CI（act）不加排除 → 慢測仍於 pre-push 完整跑，零覆蓋損失。
- **flaky 依 CI 修復規則對症根治**：第 4 次 validate-e2e 失敗時，**先讀 Playwright 失敗快照（error-context.md）再修**，找到真根因（S37 共用 Header 的「註冊」連結與登入表單碰撞、`.first()` 恆選 header 連結且 re-render 不穩定），而非盲目重跑或放寬守門。修法（改 `goto`）確定性且讓所有共用 helper 的 spec 更穩健。
- **顯式等待取代固定 sleep 且順帶補斷言**：DEF-022 不是裸刪 sleep，而是改 `waitForURL`/`waitForResponse`/`expect().toBeVisible()` 並補對應斷言（Rule 9），提升測試意圖；同時保留 STOMP settle 例外（誠實界線）。
- **誠實界線清楚**：AI-2202c 明確只交付 Part A（純前端可行），Part B 需後端新語意 → 另立 AI-2202d，未灌水宣稱「日曆語意細化完成」（Rule 12）。
- **逐 US commit + 開發-編譯-測試循環**：每 US 完成即編譯/測試/lint/commit，隨時停在乾淨檢查點。

---

## 3. 待改善的（What to improve）

- **移除固定 sleep 會重新暴露被掩蓋的既有 flaky**：DEF-022 清 sleep 後，at-buyer-pages/at-m15/at-m17 的 dialog/導覽 flaky 浮現，validate-e2e 反覆 4 次才綠。→ 教訓：清 sleep 的 PR 應預期「連鎖 flaky」，一次把同類 helper 的等待條件與 dialog 處理器補齊（已於本 Sprint 修正 auth helper + 兩檔 dialog）。
- **validate-e2e 依賴 Turbopack build 期抓 Google Fonts**：前 3 次失敗有 2 次是 build 抓 `Inter` 的暫時性網路失敗（非程式）。→ 與既有 DEF-015（next/font 建置期外部抓取）同源；`Inter`（拉丁）仍走 next/font/google，網路抖動即 build 失敗。可評估 `next/font/local` 自 host `Inter` 子集，徹底離線化（候選，非急迫）。
- **單次 validate-e2e 成本高（清淨 DB + 後端 JAR + prod build + Playwright）**：反覆重跑耗時；宜在重跑前先靜態掃可能的 flaky（如全域 dialog 處理器覆蓋、locator 歧義），減少長循環次數（本 Sprint 已採此法：重跑前先掃 alert 站點與 dialog 覆蓋）。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1908 | 檢查點徵詢後 push S41+S42 | 通過完整 validate-release 後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-1903 | 買家閉環 live 走查（真人）| 依 BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST 於 live 環境走查真 DB 跨角色資料流 | QA Quincy | P1 | 需 live 環境 |
| AI-2202d | 整月日曆「未開放 vs 可訂」語意 | 後端新增「開放窗」語意（現 room_calendar AVAILABLE row 偶發），前端顯式標記未開放日 | SD Marcus | P3 | 待評估 |
| AI-2303 | Inter 字體自 host 離線化 | 評估 `next/font/local` 自 host Inter 子集，消除 build 期 Google Fonts 網路依賴（同源 DEF-015）| SD Marcus | P3 | 後續 |

---

## 5. Sprint 41 → Sprint 42 Action Items 追蹤結果

| Action Item | 內容 | Sprint 42 達成狀態 |
|------------|------|---------------------|
| AI-2302 | 後端 pre-commit 核心測試提速 | ✅ 完成（US-001，@Tag slow + excludedGroups，順帶移除 DB 依賴）|
| AI-2202c | 整月日曆語意細化 + 整月價格 | 🟡 部分（US-002 交付 Part A 每日價格；Part B「未開放 vs 可訂」→ AI-2202d）|
| DEF-022 | E2E 硬等待清除 | ✅ 完成（US-003，5 檔清除 + 改顯式等待 + 補斷言，保留 STOMP 例外；連帶根治 flaky）|
| AI-1908 | 檢查點 push S41 | 🟡 續留（S41+S42 累積，待檢查點徵詢後完整守門 push）|
| AI-1903 | 買家 live 走查 | 🟡 續留（需 live 環境）|
| DEF-021 | CJK 字體 | ✅ 已決策結案（S41，accepted fallback）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S37 | 10 |
| S38 | 8 |
| S39 | 10 |
| S40 | 9 |
| S41 | 12 |
| **S42** | **7** |

> **觀察**：S42 = 7 SP，收尾型 Sprint 於 S41 清償量大（12 SP）後回歸健康偏保守區間。品質未因量少而鬆：完整 validate-e2e 46 passed / 0 fail、schema 對齊、後端 quick test 455 tests 0 fail（無 DB）。技術債（pre-commit 速度、E2E 硬等待）再清一批，測試穩定性與開發體驗同步提升。

---

## 7. 下一步

> **檢查點**：Sprint 42 已完成（5 commit，本地各層驗證通過含 validate-e2e 46/6/0）。**建議於檢查點徵詢後執行完整 `make validate-release` 並一次 push S41+S42**（AI-1908；嚴禁 --no-verify）。Sprint 43 候選：AI-1903 真人 live 走查（需環境）、AI-2202d 日曆「未開放 vs 可訂」語意、AI-2303 Inter 字體離線化，或回歸新功能開發。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
