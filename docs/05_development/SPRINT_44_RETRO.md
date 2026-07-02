# Sprint 44 Retrospective / Sprint 44 回顧會議

> **Sprint 編號**: Sprint 44
> **期間**: 2027-06-20 ~ 2027-07-03
> **回顧日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 8 SP（US-001~003）|
| 完成 SP | 8 SP（全數）|
| 主題 | 完成 M12 進階定價全覆蓋（PRODUCT/Cart + 買家日曆折扣）+ Inter 離線化 |

---

## 2. 做得好的（What went well）

- **找到更簡潔的單一計價來源**：US-001 原規劃在 OrderService 接線，實作時發現「getCart 讀取時重算折扣」即可讓訂單金額（createOrderFromCart 讀 cart）與購物車顯示同源一致，**OrderService 完全不需改**。單一 recompute 點，降低爆炸半徑（Rule 2/3）。
- **沿用 S43 範式一致落地**：三項折扣接線（ROOM booking S43、PRODUCT cart S44、日曆 S44）皆用同一 pattern——Feature Toggle gating + 向後相容 fallback + 只套折扣型 + 計算失敗降級。一致性讓風險可預期。
- **schema-free 貫徹**：S42~S44 連續三個 Sprint 零 migration（沿用 jsonb config + DTO-only 折扣欄位），降低 schema 漂移與 push 風險。
- **每 US 先跑真 DB 整合再 commit**：US-001 啟 test DB 跑 cart/order/M12 45 tests、US-002 跑 calendar 26 tests 確認不退步才 commit，貫徹開發-測試循環。
- **AI-2303 以正確工作流離線化**：一次性取得 Inter woff2 並 commit → next/font/local，正是「fetch once, build offline」的標準做法，根治 build 期網路 flakiness，而非規避。

---

## 3. 待改善的（What to improve）

- **font 資產取得依賴一次性網路**：AI-2303 需一次性連 Google Fonts 取 woff2。雖屬合理（之後離線），但若環境完全無網路則無法取得資產。→ 教訓：字體/資產類技術債，資產來源應事先備妥（或文件記錄取得方式），避免執行時卡在資產。
- **PRODUCT 折扣 N 次查詢**：getCart 對每個 PRODUCT 項呼叫 getEffectivePrice（N 次規則查詢）。目前購物車項數少可接受，若未來大購物車需批次化。→ 觀察，未達優化門檻前不過早優化（Rule 2）。
- **兩套定價機制仍並存**：room_calendar 手動日價 vs PricingService 規則，本 Sprint 未統一（AI-2406 續留）。折扣生效時手動價不參與的行為需在 UI/文件對賣家清楚說明。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1908 | 檢查點徵詢後 push S41~S44 | 通過完整 validate-release 後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-1903 | 買家閉環 live 走查（真人）| 於 live 環境走查真 DB 跨角色資料流（含 PRODUCT 折扣下單）| QA Quincy | P1 | 需 live 環境 |
| AI-2406 | 定價機制統一（room_calendar 手動價 vs 規則）| 評估手動日價改走 MANUAL_OVERRIDE 規則，消除雙機制 | SD Marcus | P3 | 待評估 |
| AI-2202d | 整月日曆「未開放 vs 可訂」語意 | 後端開放窗語意 + 前端顯式標記 | SD Marcus | P3 | 待評估 |

---

## 5. Sprint 43 → Sprint 44 Action Items 追蹤結果

| Action Item | 內容 | Sprint 44 達成狀態 |
|------------|------|---------------------|
| AI-2403 | 進階定價接 PRODUCT/Cart | ✅ 完成（US-001）|
| AI-2405b | 買家日曆每日折扣 | ✅ 完成（US-002）|
| AI-2303 | Inter 字體離線化 | ✅ 完成（US-003）|
| AI-1908 | 檢查點 push | 🟡 續留（S41~S44 累積，待徵詢後完整守門 push）|
| AI-1903 | 買家 live 走查 | 🟡 續留（需 live 環境）|
| AI-2202d | 日曆開放窗語意 | 🟡 續留（P3）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S39 | 10 |
| S40 | 9 |
| S41 | 12 |
| S42 | 7 |
| S43 | 10 |
| **S44** | **8** |

> **觀察**：S44 = 8 SP，健康區間。品質：後端單元 23 + 真 DB 整合 71（cart/order/M12/calendar）全過、validate-e2e 48 passed/0 fail、schema 對齊、無 schema 變動。M12 進階定價完成全覆蓋（ROOM + PRODUCT + 買家顯示），連續 3 Sprint（S42~S44）零 migration。

---

## 7. 下一步

> **檢查點**：Sprint 44 已完成（5 commit，本地各層驗證通過含 validate-e2e 48/6/0）。**push 債已累積 S41+S42+S43+S44（4 Sprint）**，建議於檢查點徵詢後執行完整 `make validate-release` 並一次 push（AI-1908；嚴禁 --no-verify）。Sprint 45 候選：AI-2406 定價機制統一、AI-2202d 日曆開放窗語意、AI-1903 真人 live 走查（需環境）、真實金流評估（#10），或新功能。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
