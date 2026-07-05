# Sprint 67 Retrospective / Sprint 67 回顧會議

> **Sprint 編號**: Sprint 67
> **期間**: 2028-05-07 ~ 2028-05-20
> **回顧日期**: 2026-07-05
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 8 SP（US-001）|
| 完成 SP | 8 SP（全數）|
| 主題 | 多 Sprint 測試強化計劃第二階段：PaymentStateService（金流核心）測試補齊至 10 方法完整覆蓋 |

---

## 2. 做得好的（What went well）

- **開工前先查核前置認知，而非照單全收**：`SPRINT_66_PLAN.md` 第 6 節寫「`PaymentStateService` 10 方法零測試」，但實際盤點發現已有 `PaymentStateServiceStripeTest.java`（11 個測試）覆蓋 4 個方法。沒有照著過時措辭直接重寫已存在的測試，而是先讀完整份既有測試檔案，精準定位「真正的 6 個零測試方法」+「已測方法缺的錯誤路徑」，避免重複勞動。
- **`mvn verify` 全量回歸再次攔截 pre-commit 未涵蓋的問題**：與 Sprint 66 相同模式，測試撰寫過程中一個未使用的 import 只在全量 `checkstyle-test` execution 才被抓到，證明「提早、頻繁跑 `mvn verify`」的建議確實有效——本 Sprint 在完成全部測試撰寫後、進入收尾前就先跑過一次全量驗證，抓到問題後立即修正重跑，而非等到 push 前才發現。
- **發現授權缺口時守住範圍邊界**：撰寫 `getBookingPaymentState` 測試時發現 booking 付款側完全沒有擁有權檢查（IDOR 疑慮），且證據顯示這是「已知但未排入計畫」的系統性缺口（涉及 `BookingService`/`PaymentService`/`PaymentStateService` 三處）。沒有自行擴大範圍去修復，而是記錄為 `DEF-023` 交由人工決策，守住「只補測試」的 Sprint 授權邊界，同時沒有讓發現石沉大海。

---

## 3. 待改善的（What to improve）

- **Sprint Plan 中的「零測試」措辭應在下筆前先以程式碼驗證，而非沿用前一個 Sprint 收尾時的描述**：`SPRINT_66_PLAN.md` 第 6 節的清單是規劃當下的概略盤點，隨著程式碼演進（Sprint 50/52/56 陸續補了部分 `PaymentStateService` 測試）已經過時但未回頭更新。建議往後在「多 Sprint 測試強化計劃」的後續 Sprint 開工時，第一步固定加入「用 `find`/`grep` 重新確認目標 Service 的既有測試檔案與涵蓋方法」，不假設待辦清單當下仍準確。
- **`mvn verify -Pintegration-test` 單次全量約需 28~33 分鐘**：本 Sprint 因背景工具的自動化行為導致等待流程一度中斷，改用「前景迴圈 + 心跳輸出」的方式才穩定等到建置完成。建議往後遇到需要長時間前景等待的指令時，直接採用「短迴圈 + 定期心跳輸出」模式（而非單一超長逾時的背景指令），避免因等待機制問題而誤判建置狀態。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------------|------|--------|--------|--------|
| DEF-023 | Booking 付款擁有權檢查缺口（IDOR 疑慮） | `getBookingPaymentState`/`BookingService.getBooking/updateBooking`/`PaymentService.processBookingPayment` 皆缺擁有權檢查 | PO Victoria（決策）+ SD Marcus（修復範圍評估） | 🔴 高 | 待 PO 決策，建議 Sprint 69（與 BookingService 測試補強同批評估） |
| AI-2416 | 真實金流 Phase D-2：代收後 transfer 分潤/提現 | 需 Phase D-1 帳戶已上線 | SD Marcus | P3 | 待評估（需環境） |
| AI-1903 | 買家閉環 live 走查（真人）| 需 live 環境 | QA Quincy | P1 | 需 live 環境 |

---

## 5. Sprint 66 → Sprint 67 Action Items 追蹤結果

| Action Item | 內容 | Sprint 67 達成狀態 |
|------------|------|---------------------|
| AI-2416 | Phase D-2 代收後分潤 | 未啟動（續留，需 Phase D-1 上線）|
| AI-1903 | 買家閉環 live 走查（真人）| 未啟動（需 live 環境）|
| pre-commit 是否納入 checkstyle-test execution 評估 | Sprint 66 提出，待評估 | 未評估（本 Sprint 聚焦金流測試，未排入）|

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S63 | 8 |
| S64 | 8 |
| S65 | 8 |
| S66 | 8 |
| **S67** | **8** |

> **觀察**：S67 = 8 SP，連續八個 Sprint維持 8 SP 穩定產能。品質：後端單元 **542 tests**（新增 `PaymentStateServiceTest` 48 個）+ 完整整合（含 failsafe）**342 tests**，合計 **884 tests 0 fail**、`make validate-schema` 無漂移。**本 Sprint 是「多 Sprint 測試強化計劃」的第二階段**，未修改任何生產程式碼（純測試補強），額外產出 `DEF-023` 待決策項目。

---

## 7. 下一步

> **檢查點**：Sprint 67 已完成。依現行節奏，本 Sprint 收尾後立即 push。`docs/04_planning/SPRINT_66_PLAN.md`/`SPRINT_67_PLAN.md` 已列出後續建議排序：**Sprint 68 建議處理 `OrderService`（7 方法，訂單狀態機核心）**。Sprint 69 建議 `BookingService`+`RoomCalendarService`（並建議一併評估 `DEF-023`），Sprint 70+ 建議 ERP 模組整體（測試目錄完全不存在）。`DEF-023`（booking 付款擁有權缺口）待 PO/Security owner 決策修復時程，不因排入未來 Sprint 而拖延決策本身。

---

**文件版本**: v1.0
**建立日期**: 2026-07-05
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
