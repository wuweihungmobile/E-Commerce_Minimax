# Sprint 39 Retrospective / Sprint 39 回顧會議

> **Sprint 編號**: Sprint 39
> **期間**: 2027-04-11 ~ 2027-04-24
> **回顧日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 10 SP（US-001~004）|
| 完成 SP | 10 SP（全數）|
| 主題 | ROOM 訂房閉環補完 |

---

## 2. 做得好的（What went well）

- **前置探勘避免做白工**：規劃前探勘揭露「ROOM 訂房閉環已存在（checkout 內聯建 booking）」,及時把 Sprint 從「從零建訂房」重新定位為「補強」,避免重造既有功能。
- **實作前抓出 API 陷阱**：動手前 grep 發現 `GET /v2/bookings/availability` 是 GET+@RequestBody（瀏覽器無法呼叫），在寫錯程式前就攔下,與使用者改採免後端路徑——沒有事後才發現前端呼不動。
- **借鑒前 Sprint 教訓連續奏效**：ROOM E2E 用 mock（S38 模式，免 seed）、加購/日期用專屬 testid（S37 教訓，未撞 submit helper）、错误碼對應集中於 service——一次到位、無連鎖失敗。
- **測試基建收斂並收 DEF-022**：共用 helper 以 waitForURL 取代固定 sleep,順帶修掉通知 flaky（5s→15s）;登入等價經全棧 E2E 驗證。
- **誠實界定 US-004 範圍**：發現實際 local helper 僅 5 份、其中 at-m10 不相容,如實收斂 4 份而非硬湊破壞 chat 登入語意。

---

## 3. 待改善的（What to improve）

- **後端 API 契約品質**：`/v2/bookings/availability` GET+@RequestBody 是明顯設計瑕疵（瀏覽器不可用）;前端 api.ts 又定義了未實作的 `/v2/listings/{id}/calendar`。應盤點並修正這類「前端定義但後端無/不可用」的端點契約。
- **可用性檢查缺席**：因 availability 端點不可用,詳情頁無法即時擋下已訂走日期,只能在 checkout 才回饋 409。UX 次佳,待後端修 endpoint 後補「詳情頁即時可用性」。
- **ROOM 購買路徑分叉未解**：cart→checkout→booking 與 order 兩條平行路徑長期並存、未文檔化決策,潛在混淆。
- **登入 helper 尚未完全統一**：at-m10（userId）、at-m17（beforeEach 含 admin）仍各自為政;完全統一需擴充共用 helper 支援回傳 userId / 指定帳號。
- **push 債達 37 commit（S32~S39）**：技術阻礙早已清除,遲遲未在檢查點完成 push,批次風險持續累積。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1906 | 檢查點徵詢後 push S32~S39 累積批次 | `make validate-release` 綠燈後 push;嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-2201 | 修 availability 端點 + 詳情頁即時可用性 | 後端 `/v2/bookings/availability` 改 @RequestParam（或 POST）;前端詳情頁補即時可用性檢查/整月日曆 | SD Marcus + Dev | P2 | S40 規劃 |
| AI-2202 | 清理「前端定義但後端無」端點契約 | 盤點 api.ts（如 `/v2/listings/{id}/calendar`）與後端對齊,移除或實作 | SD Marcus | P3 | 後續 |
| AI-1903 | 買家閉環 live 走查 | 順延（需 live 環境）;含 S37~S39 全成果 | QA Quincy | P1 | S40 |
| AI-2101b | 登入 helper 完全統一 | 擴共用 helper 支援回傳 userId / 指定帳號,收斂 at-m10 / at-m17 | QA Quincy | P3 | 後續 |
| AI-2203 | ROOM 購買路徑決策文檔化 | 明確 booking vs order 路徑,或統一 | PM + SD | P3 | 後續 |
| — | DEF-021（CJK 字體）| 續延後（P3）| — | P3 | 後續 |

---

## 5. Sprint 38 → Sprint 39 Action Items 追蹤結果

| Action Item | 內容 | Sprint 39 達成狀態 |
|------------|------|---------------------|
| AI-2103b | ROOM 完整訂房 | ✅ 補強完成（US-001，衝突優雅處理;full 可用性因端點不可用改另立 AI-2201）|
| AI-2104 | 登入態真實加購/下單 E2E | ✅ 完成（US-003，ROOM 閉環 mock E2E）|
| AI-2101 | E2E 共用 helper 抽取（含 DEF-022）| ✅ 完成（US-004，收斂 4 檔;剩 at-m10/m17 → AI-2101b）|
| AI-1903 | 買家 live 走查 | ⏸️ 順延 S40（需 live 環境）|
| AI-1906 | 檢查點 push | ⏳ 待檢查點 |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S34 | 3 |
| S35 | 18（純前端異常高）|
| S36 | 10 |
| S37 | 10 |
| S38 | 8 |
| **S39** | **10** |

> **觀察**：S39 = 10 SP，健康區間。主題「補強既有閉環」，前置探勘 + API 陷阱提早攔截使實作一次到位、無返工。因 availability 端點不可用而縮減 US-001、提 US-004 補足，展現規劃彈性。

---

## 7. 下一步

> **Sprint 40（規劃中）**: 買家 live 走查（AI-1903，驗證 S37~S39 全成果真實閉環）+ availability 端點修復 + 詳情頁即時可用性（AI-2201）。並在檢查點徵詢後,把 S32~S39 累積批次經完整守門一次 push（AI-1906）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
