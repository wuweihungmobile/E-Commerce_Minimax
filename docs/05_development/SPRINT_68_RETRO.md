# Sprint 68 Retrospective / Sprint 68 回顧會議

> **Sprint 編號**: Sprint 68
> **期間**: 2026-07-05（緊急插入）
> **回顧日期**: 2026-07-05
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 6 SP（US-001 5 + US-002 1）|
| 完成 SP | 6 SP（全數）|
| 主題 | 🔴 緊急安全修復：Booking 付款/預訂擁有權檢查缺口（IDOR，`DEF-023`），優先於例行測試強化排程插隊執行 |

---

## 2. 做得好的（What went well）

- **開工前先讀完整份既有修復 diff，而非憑記憶重寫模式**：撰寫修復前先用 `git log`/`git show` 找到 `DEF-018`（commit `be89014`）、`DEF-019`（commit `32b5590`、`0cd5bbc`）的實際程式碼變更，確認 Order 側 `checkOrderOwnership`/`checkOrderPaymentOwnership` helper 的精確寫法（欄位比對邏輯、`SecurityContextHolder` 取得 admin 判斷方式、`E_1007` 錯誤碼、檢查置於狀態檢查之前的順序），確保 Booking 側修復與既有慣例逐字對齊（Rule 11：配合程式碼庫慣例）。
- **守住「精準改動」邊界，同時誠實記錄擴大範圍的發現**：盤點過程中發現 `BookingService.cancelBooking` 同樣缺擁有權檢查、`HOST` 角色的 `booking:update` 權限與修復後行為可能有落差，兩者皆未列入原始 `DEF-023` 記錄與本次任務明確授權範圍。沒有自行擴大修復範圍去「順便修好」，而是在 `SPRINT_68_PLAN.md`/`SPRINT_68_REVIEW.md` 明確記錄為殘留事項待人工決策，避免安全修復 Sprint 範圍蔓延失控。
- **權限調整低風險路徑判斷正確**：修復前先以 `grep` 確認 `RolePermissionMapping.java` 為純記憶體 Java 常數、無對應 DB 權限表，避免了在不確定情況下貿然新增 Flyway migration（任務明確要求的風險控管），改採一行程式碼常數變更完成 `booking:read` 收斂。
- **開發-編譯-測試循環確實逐項執行**：四處修復（`getBookingPaymentState`/`getBooking`/`updateBooking`/`processBookingPayment`）與權限調整皆各自完成後立即 `mvn -o compile` + 針對性 `mvn -o test -Dtest=...` 驗證通過才進入下一項，未累積到最後才一次驗證。
- **背景長時間指令等待機制的教訓被吸收**：Sprint 66/67 皆發生「背景指令啟動後結束回合、與流程失聯」的問題，本 Sprint 在收到提醒後改用 Monitor 工具搭配 `grep` 輪詢 log 檔案的 until-loop，在同一任務脈絡下親自等到 `mvn verify -Pintegration-test` 的 `BUILD SUCCESS` 結果，而非依賴外部系統通知或直接結束回合空等。

---

## 3. 待改善的（What to improve）

- **這是第三次發生「背景長時間指令 + 結束回合等待」的問題**（Sprint 66、67 皆發生過）：儘管本次任務指示已明確提醒「絕對不要用 run_in_background 啟動後結束回合」，仍因 Bash 工具前景逾時上限（10 分鐘）低於 `mvn verify -Pintegration-test` 實際所需時間（25-30 分鐘），導致指令被系統自動轉為背景執行，第一次仍下意識地用文字說明「等待通知」而結束了回合。往後應在啟動任何預期超過 10 分鐘的指令**之前**就先規劃好用 Monitor + until-loop（輪詢 log 檔案關鍵字）的等待方式，而非等指令變成背景執行後才臨時補救。
- **Sprint 追蹤文件的「Sprint 歷史紀錄」章節在 Sprint 61-67 期間未持續維護**：`DEFERRED_ITEMS_TRACKER.md` 的「Sprint 歷史紀錄」章節最後一筆是 Sprint 60，中間 Sprint 61-67 的條目缺失。本 Sprint 依 Rule 3（精準改動）僅新增 Sprint 68 條目、未回頭補齊缺失的歷史記錄，但這是一個文件維護債，建議未來 Sprint 收尾時養成固定補上此章節條目的習慣，避免缺口持續擴大。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 内容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| ~~（待決策1）~~ | ~~`BookingService.cancelBooking` 擁有權檢查~~ | ✅ **已於 Sprint 68 收尾並 push 後追加修復解決**（使用者看到揭露後立即授權，沿用同一個 `checkBookingOwnership` helper） | — | — | ✅ 完成（Sprint 68 追加） |
| （待決策2） | `HOST` 角色 `booking:update` 權限與 `updateBooking` 修復後行為落差 | 若 HOST 對自己房源訂房有合法更新需求，修復後將收到 403，需評估是否需比照 `LogisticsService` 補租戶側檢查 | PO Victoria（決策）+ SD Marcus（設計） | 🟡 中 | 待決策 |
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |
| （文件債） | `DEFERRED_ITEMS_TRACKER.md`「Sprint 歷史紀錄」章節補齊 Sprint 61-67 缺失條目 | 該章節維護中斷，建議未來收尾時固定補上 | 全體 | 🟢 低 | 待排入 |

---

## 5. Sprint 67 → Sprint 68 Action Items 追蹤結果

| Action Item | 內容 | Sprint 68 達成狀態 |
|------------|------|---------------------|
| `DEF-023` | Booking 付款擁有權檢查缺口 | ✅ **本 Sprint 完成修復並結案**（原建議 Sprint 69 評估，因使用者明確授權緊急插隊提前於 Sprint 68 處理） |
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S64 | 8 |
| S65 | 8 |
| S66 | 8 |
| S67 | 8 |
| **S68** | **6**（緊急安全修復插隊，不計入例行測試強化排程節奏）|

> **觀察**：S68 為緊急插入 Sprint，SP 低於例行節奏（連續八個 Sprint 8 SP）屬預期——安全修復範圍精準（4 處程式碼修改 + 1 處權限常數調整），非刻意壓低估點。品質：後端單元 **557 tests**（新增/擴充 15 個擁有權相關測試）+ 完整整合 **342 tests**，合計 **899 tests 0 fail**、`make validate-schema` 無漂移。**Booking 模組 IDOR 缺口清零**，活躍高優先級延後項目回到 0。

---

## 7. 下一步

> **檢查點**：Sprint 68 已完成並 push。**收尾後追加**：使用者看到 `cancelBooking` 殘留揭露後立即授權追加修復，已沿用同一 helper 解決並完成全量回歸，DEF-023 徹底結案（僅剩 `HOST` 角色更新權限落差待決策，未變更行為）。恢復例行測試強化排程：**Sprint 69 建議處理 `OrderService`（7 方法，訂單狀態機核心，`SPRINT_67_PLAN.md` 第 6 節原建議之 Sprint 68 順延至此）**，並建議一併評估 `HOST` 角色更新權限落差。Sprint 70+ 建議 `BookingService`+`RoomCalendarService` 測試強化、Sprint 71+ ERP 模組整體。

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
