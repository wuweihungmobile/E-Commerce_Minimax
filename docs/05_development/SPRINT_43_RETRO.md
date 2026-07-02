# Sprint 43 Retrospective / Sprint 43 回顧會議

> **Sprint 編號**: Sprint 43
> **期間**: 2027-06-06 ~ 2027-06-19
> **回顧日期**: 2026-07-02
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 10 SP（US-001~004）|
| 完成 SP | 10 SP（全數）|
| 主題 | M12 進階定價落地（早鳥/長住/末班車折扣真正生效於 ROOM）|

---

## 2. 做得好的（What went well）

- **探勘先行揭穿「假新功能」**：3 個 Explore Agent 動手前查明——三折扣的引擎、六型 enum、Dashboard/Consumer API、前端管理 UI 骨架**全都已存在**，真正缺口是「引擎沒接進實際計價鏈」+「語意錯誤」+「config 無編輯 UI」。避免了「從零重造」的浪費，把 10 SP 聚焦在接線與修正。
- **高風險項以三重保護落地**：US-002 改變 booking 計價（高風險）——以 Feature Toggle（DYNAMIC_PRICING_ENABLED）+ 向後相容 fallback（無規則維持原價）+ 整合測試三路徑（折扣/不退步/toggle）把關；並先啟 test DB 跑 36 個真 DB 整合測試確認不退步，才 commit。
- **顯示與計價同 Sprint 落地**：US-002（計價）與 US-004（顯示）綁定交付，booking 金額來源 = availability 折扣後總價，杜絕「顯示折扣卻收原價」的半吊子。
- **資料驅動的 config 表單**：US-003 以 CONFIG_FIELDS metadata 驅動動態子表單，六型別一致擴充，取代黑箱 `{}`；沿用既有受控表單模式與 React 19 async-in-effect 慣例。
- **沿用孤兒元件**：US-004 直接嵌入既有但未被引用的 `PricingCalendarPreview`，近乎零成本補齊賣家預覽。
- **誠實界線清楚**：只接 ROOM（不接 Order/Cart）、買家日曆每日折扣另立，皆明確標記另立 AI，未灌水（Rule 12）。

---

## 3. 待改善的（What to improve）

- **既有語意 bug 靠新功能才被發現**：早鳥/末班車原以 `validFrom vs 入住日` 計天數（非「今天 vs 入住日」），是既有邏輯缺陷，直到本 Sprint 要「真正生效」才揭露。→ 教訓：功能「建好但沒接線」時，其正確性未被真實使用驗證，等於未完成；backlog 應標記此類「有骨架未接線」項目為技術債，而非「已完成」。
- **本地整合測試需真實 DB（5432）**：`integration-test` profile 連 localhost:5432，未啟 test DB 時 19 個 @SpringBootTest 全 context 載入失敗（非程式問題）。→ 開發 M12/Booking 這類需真 DB 的整合測試前，先 `make test-db-up`；已於本 Sprint 遵循（跑完 `make test-db-down` 清理，避免撞 act 的 6379）。
- **兩套定價機制並存（room_calendar 手動日價 vs PricingService 規則）**：本 Sprint 以「折扣生效時規則優先」處理，但兩機制長期並存易混淆。→ 未來宜統一（例如手動日價也走 MANUAL_OVERRIDE 規則），列為架構債觀察。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| AI-1908 | 檢查點徵詢後 push S41+S42+S43 | 通過完整 validate-release 後 push；嚴禁 --no-verify | Dev David | **P1** | 檢查點 |
| AI-1903 | 買家閉環 live 走查（真人）| 於 live 環境走查真 DB 跨角色資料流 | QA Quincy | P1 | 需 live 環境 |
| AI-2403 | 進階定價接入 PRODUCT/Cart 計價鏈 | getEffectivePrice 接入 RedisCartService/結帳，使商品折扣真正生效 | SD Marcus | P2 | 後續 |
| AI-2405b | 買家整月日曆每日折扣顯示 | 擴充 getCalendar 回 rule-computed discount，MonthCalendar 顯示每日折扣後價 | Dev David | P3 | 後續 |
| AI-2406 | 定價機制統一（room_calendar 手動價 vs 規則）| 評估手動日價改走 MANUAL_OVERRIDE 規則，消除雙機制 | SD Marcus | P3 | 待評估 |

---

## 5. Sprint 42 → Sprint 43 Action Items 追蹤結果

| Action Item | 內容 | Sprint 43 達成狀態 |
|------------|------|---------------------|
| AI-2202d | 整月日曆「未開放 vs 可訂」語意 | 🟡 續留（本 Sprint 主軸為 M12 進階定價，未觸及日曆開放窗語意）|
| AI-2303 | Inter 字體離線化 | 🟡 續留（P3，未納入本 Sprint）|
| AI-1908 | 檢查點 push | 🟡 續留（S41+S42+S43 累積，待徵詢後完整守門 push）|
| AI-1903 | 買家 live 走查 | 🟡 續留（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S38 | 8 |
| S39 | 10 |
| S40 | 9 |
| S41 | 12 |
| S42 | 7 |
| **S43** | **10** |

> **觀察**：S43 = 10 SP，功能型 Sprint 回到中上區間（S42 收尾 7 SP 後回升）。品質：後端單元 15 + 真 DB 整合 36 全過、validate-e2e 47 passed/0 fail、schema 對齊、無 schema 變動。高風險的計價接線以 toggle + 向後相容 + 三路徑整合測試安全落地。

---

## 7. 下一步

> **檢查點**：Sprint 43 已完成（5 commit，本地各層驗證通過含 validate-e2e 47/6/0）。**push 債已累積 S41+S42+S43（3 Sprint）**，建議於檢查點徵詢後執行完整 `make validate-release` 並一次 push（AI-1908；嚴禁 --no-verify）。Sprint 44 候選：AI-2403 進階定價接 PRODUCT/Cart、AI-2405b 買家日曆每日折扣、AI-1903 真人 live 走查（需環境）、AI-2202d 日曆開放窗語意，或其他新功能。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
