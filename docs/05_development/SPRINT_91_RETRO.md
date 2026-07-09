# Sprint 91 Retrospective / Sprint 91 回顧會議

> **Sprint 編號**: Sprint 91
> **期間**: 2026-07-09
> **回顧日期**: 2026-07-09
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 10 SP（M18 客服工單子系統：後端全部三層 API + 買家前端） |
| 完成 SP | 10 SP |
| 主軸 | PRD §6.10 M18「客服系統」Phase 2-B，連續 8 個 Sprint（83-90）列為候選、Sprint 90 retro 明確排定本輪處理。截至開工前為完全 greenfield |

---

## 2. 做得好的（What went well）

- **動手前完整探查排除歷史陷阱**：`SPRINT_13_TASKS.md` 的「M18 Phase 2」任務名稱與本次工作高度相似（皆稱「M18 Phase 2」），但探查後確認 Sprint 13 做的是知識庫版本控制/排程發布，與 PRD spec 定義的「客服系統 Phase 2-B」是同名不同功能。若未探查直接假設「已經做過」會遺漏整個模組；若假設「命名衝突」則可能誤改既有知識庫程式碼。
- **實作前重新驗證計畫假設，發現無精確前例後主動收斂範圍**：規劃階段原打算「比照既有事件模式串接通知」，但動手前逐一確認發現 `ORDER_CONFIRMED`/`PAYMENT_SUCCESS`/`BOOKING_CONFIRMED` 等既有通知類型實際上**從未被任何業務服務呼叫觸發**——「業務事件自動觸發通知」在此程式碼庫沒有任何可複製的前例，這代表通知整合其實是要新發明一套架構決策，超出 PRD 明確要求的範圍（PRD §5.2 僅要求「訊息記錄」，未列「自動通知」）。判斷後主動取消此項，僅完成 PRD 明確要求的功能，並將此發現即時反映回 Plan 文件，保持文件與實作同步。
- **正確識別工單資源的雙重授權模型並精準對應到既有前例**：探查後端 `initiate/confirm`（Sprint 86 結算逆轉）與 `getPendingReviewStatements`（結算審核）兩種不同的租戶篩選慣例後，正確判斷客服工單的「店家工單管理」性質更接近後者（`isSuperAdmin ? 跨租戶 : 限自己租戶`），而非前者的「一律跨租戶」，因為工單本質上是租戶自己要處理的資源，不像結算逆轉是平台對租戶金流的獨立稽核動作。避免不加區分地套用最近一次用過的模式。
- **API 規格табле只列 9 個端點但未列「取得訊息列表」，判斷應嵌入工單詳情回應而非另闢端點**：PRD spec 的 API 表格沒有獨立的「GET messages」端點，但功能描述明確要求「訊息記錄」。判斷最貼近規格原意的做法是讓 `GET .../tickets/:id` 回應直接內嵌完整訊息串，而非自行擴充規格新增一個 API 表格未列的端點。

## 3. 待改善的（What to improve）

- **`support_tickets.tenant_id` 為 null 的平台工單，目前沒有任何角色可以「回覆」**：`postAsStaff`/`updateStatus` 兩個方法在 `isSuperAdmin=false` 時都用 `findByIdAndTenantId` 查詢，若 `callerTenantId` 為 null（工單本身就是平台工單）理論上不會匹配到任何列（因為 `tenant_id IS NULL` 用 `=` 比對查不到），只有 SUPER_ADMIN（不受此篩選限制）才能處理平台工單。這是符合 PRD 設計意圖的（平台工單本就該由平台層級處理），但目前沒有測試明確驗證這個邊界案例，記錄為技術債。
- **前端目前僅完成買家端**（比照 Sprint 89/90 的大功能拆分慣例，範圍已在 Plan 文件明確記錄），店家 Dashboard 工單管理頁與平台 Admin 工單列表/指派頁尚待 Sprint 92。
- **未新增 Controller/E2E 層測試**驗證三層路由的 `@PreAuthorize` 確實生效（例如買家角色呼叫店家端點應 403）。目前僅有 Service 層 mock 測試覆蓋業務邏輯，比照 Sprint 90 遺留的同類技術債（`reversal-candidates` 端點測試補強），兩者可在同一輪 QA 加強時一併處理。

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------|------|--------|--------|--------|
| （前端候選） | 店家 Dashboard 工單管理頁 + 平台 Admin 工單列表/指派頁 | Sprint 91 明確排定範圍外，後端 API 已於本 Sprint 全部完成 | Dev David | 🟡 中 | Sprint 92 |
| （技術債） | Controller/E2E 層補強：客服工單三層路由 `@PreAuthorize` 403 驗證、平台工單邊界案例測試 | 與 Sprint 90 `reversal-candidates` 測試補強可合併處理 | QA Quincy | 🟡 中 | 待排入 |
| （PRD Phase 2-B，延續） | M18 客服工單子系統 | 本 Sprint 已完成後端 + 買家前端 | PM Victoria | ✅ | 已完成（後端+買家端） |
| DEF-043 | ROOM 訂房結帳流程建立預訂成功後清空整個購物車 | 延續自 Sprint 88 | Dev David | 🟡 中 | 待排入 |
| DEF-044 | 已付款訂單退款/取消時無庫存自動回補 | 延續自 Sprint 88 | PM Victoria | 🟡 中 | 待排入 |
| （技術債，延續） | `purchaseOrderApprovalThreshold` 無清除機制 | 待業務決策是否需要明確清除 API | PM Victoria | 🟢 低 | 待排入 |
| （技術債，延續自 Sprint 71-87） | `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |

---

## 5. Sprint 90 → Sprint 91 Action Items 追蹤結果

| Action Item | 內容 | Sprint 91 達成狀態 |
|------------|------|---------------------|
| M18 客服工單子系統 | Sprint 90 retro 明確排定 Sprint 91 起處理 | ✅ **已完成**（後端三層 API 全部到位 + 買家前端；店家/平台前端排入 Sprint 92） |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S88 | 8 |
| S89 | 5 |
| S90 | 6 |
| **S91** | **10**（M18 客服工單：migration + entity + repository + 2 個 service + 三層路由 controller + 16 個單元測試 + 買家前端 2 頁） |

---

## 7. 下一步

> **檢查點**：Sprint 91 已完成，M18 客服工單子系統後端全部到位、買家前端可用。「三者最佳化順序」（M18/PRODUCT結帳/M16-M07表單）連同本次一併全部完成。下一輪依序進行：
> 1. Sprint 92：店家 Dashboard 工單管理頁 + 平台 Admin 工單列表/指派頁前端。
> 2. 技術債：客服工單/結算逆轉的 Controller/E2E 層 403 測試補強；`RELEASE_TRACKER.md` 補列；DEF-043/DEF-044 排程評估。
> 3. 全部完成後，回歸 `E-Commerce_PRD_v1.0_Final.md` 找下一個未開發的 PRD 項目繼續。

---

**文件版本**: v1.0
**建立日期**: 2026-07-09
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
