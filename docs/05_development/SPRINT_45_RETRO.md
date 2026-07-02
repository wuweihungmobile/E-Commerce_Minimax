# Sprint 45 Retrospective / Sprint 45 回顧會議

> **Sprint 編號**: Sprint 45
> **期間**: 2027-07-04 ~ 2027-07-17
> **回顧日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 5 SP（US-001~002）|
| 完成 SP | 5 SP（全數）|
| 主題 | 定價區技術債收斂（清死碼 + 語意決策）|

---

## 2. 做得好的（What went well）

- **探勘先行揪出死碼假象**：US-001 開工前以 Explore Agent + grep 確認 `room_calendar.price` 寫入路徑（setDatePrice/setDatePriceBulk）零呼叫者、欄位恆 NULL。原以為要「統一兩套定價機制」的架構工程，實為「移除一套死碼」——大幅降低風險與 SP，且避免了在活躍語意上動刀。**先驗證假設再動手（Rule 1/8）省下大量誤工**。
- **誠實把決策密集項界定另立，不硬做**：兩項探勘皆揭示核心是**產品/schema 決策**而非技術實作——漲價計入 booking（AI-2406b，改訂房金額行為）、開放窗 schema（AI-2202e，破零-migration）。與其在未經 PO 決策下做高風險變更，本 Sprint 只做低風險清理 + 產出決策文件，把裁決權交回 PO（Rule 1/12）。
- **行為等價清理以整合測試背書**：US-001 移除死欄位 fallback + 改寫 calendarBaseTotal 後，立即跑 booking/M12/cart/order 真 DB 整合 57 tests 全過，證實零行為影響；並順帶修正舊迴圈的 NULL→ZERO 潛在低估（防禦性）。
- **尊重專案慣例勝於個人偏好**：計劃原寫 `RoomCalendar.price` 加 `@Deprecated`，實作時改用純註解以維持 `@Deprecated=0` 度量（Rule 11），並在文件記錄取捨——避免為一個欄位破壞長期維護指標。
- **schema-free 連續四 Sprint**：S42~S45 零 migration，降低 schema 漂移與 push 風險；本 Sprint 明確把「需 migration」的選項 A 界定為另立（AI-2202e），不夾帶。

---

## 3. 待改善的（What to improve）

- **死碼存活多個 Sprint 才被發現**：room_calendar.price 機制自 V1 起即為死碼，直到 S43 折扣接線才被注意、S45 才清除。→ 教訓：新功能接線時若遇「兩套機制」，應即時盤點其中一套是否為死碼，而非長期並存標記「待統一」。
- **決策型 Sprint 的 SP 偏輕**：本 Sprint 5 SP（vs velocity 8~12）。決策/spike 型工作 SP 難估且產出為文件而非可運行功能。→ 觀察：決策型 sprint 可考慮搭配一項小型實作填充，或在規劃時就明確標為「輕量收斂 sprint」（本次已於計劃 §8 誠實揭露並經使用者核准）。
- **兩份決策文件仍待 PO 拍板才能推進**：AI-2406b（漲價計入 booking）與 AI-2202e（開放窗 schema）皆已備妥分析與建議，但實作 gate 在 PO 決策。→ 需在檢查點主動向使用者呈現這兩項決策，避免文件產出後無限期擱置。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1908 | 檢查點徵詢後 push S41~S45 | 通過完整 validate-release 後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-2406b | 漲價型規則是否計入 booking 總價 | PO 決策（選項 A 維持 / B 全面走 PricingService）；通過後實作 + QA 回歸 | PM Victoria + SD Marcus | P2 | 待 PO 決策 |
| AI-2202e | 開放窗語意實作 | PO 拍板 schema 後實作（migration + 四處 booking 邏輯 + 前端 + E2E + 既有房源 backfill）| SD Marcus | P3 | 待 PO 決策 |
| AI-2406 | room_calendar.price DROP COLUMN | `V58__Drop_Room_Calendar_Price.sql`（無資料無讀寫者，風險極低）；待 US-001 邏輯統一穩定後 | Dev David | P4 | 後續低風險 |
| AI-1903 | 買家閉環 live 走查（真人）| 於 live 環境走查真 DB 跨角色資料流 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 44 → Sprint 45 Action Items 追蹤結果

| Action Item | 內容 | Sprint 45 達成狀態 |
|------------|------|---------------------|
| AI-2406 | 定價機制統一（room_calendar 手動價 vs 規則）| ✅ 完成（US-001，實為清死碼 + 停讀；DROP COLUMN 續留低風險）|
| AI-2202d | 日曆開放窗語意 | ✅ 完成（US-002 spike，產決策文件；實作另立 AI-2202e）|
| AI-1908 | 檢查點 push | 🟡 續留（S41~S45 累積 5 Sprint，待徵詢後完整守門 push）|
| AI-1903 | 買家 live 走查 | 🟡 續留（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S40 | 9 |
| S41 | 12 |
| S42 | 7 |
| S43 | 10 |
| S44 | 8 |
| **S45** | **5** |

> **觀察**：S45 = 5 SP，決策/收斂型 sprint 偏輕（已於規劃誠實揭露並經使用者核准）。品質：後端單元 6 + 真 DB 整合 57 全過、validate-e2e 48 passed/0 fail、schema 對齊、**無 schema 變動**（連續 S42~S45 零 migration）。定價區技術債收斂：清除 room_calendar.price 死碼機制、確立 MANUAL_OVERRIDE 為唯一手動日價路徑；開放窗語意產出完整決策文件待 PO 裁決。

---

## 7. 下一步

> **檢查點**：Sprint 45 已完成（3 commit，本地各層驗證通過含 validate-e2e 48/6/0）。**push 債已累積 S41+S42+S43+S44+S45（5 Sprint）**，建議於檢查點徵詢後執行完整 `make validate-release` 並一次 push（AI-1908；嚴禁 --no-verify）。Sprint 46 候選：**AI-2406b 漲價計入 booking（需 PO 決策）**、**AI-2202e 開放窗實作（需 PO 拍板 schema）**、AI-1903 真人 live 走查（需環境）、真實金流評估（backlog #10），或回歸新功能開發。**建議檢查點主動向使用者呈現 AI-2406b / AI-2202e 兩項待決策，避免決策文件擱置。**

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
