# Sprint 92 Retrospective / Sprint 92 回顧會議

> **Sprint 編號**: Sprint 92
> **期間**: 2026-07-09
> **回顧日期**: 2026-07-09
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 6 SP（M18 客服工單子系統：店家 Dashboard + 平台 Admin 前端） |
| 完成 SP | 6 SP |
| 主軸 | Sprint 91 明確排定範圍外的後半——店家工單管理頁、平台工單列表/指派頁。收尾時發現的規格缺口（店家/平台缺單筆詳情端點）一併補上 |

---

## 2. 做得好的（What went well）

- **提前在 Sprint 91 收尾時就發現並記錄本 Sprint 所需的缺口，Sprint 92 開工即可直接動手**：Sprint 91 完成買家前端時已察覺「店家/平台若要顯示對話記錄，需要單筆工單詳情端點，但 PRD 規格表沒列」，並在收尾時明確記錄待辦。本 Sprint 開工不需要重新探查，直接依紀錄補上 2 個 Controller 端點。
- **正確判斷缺口的修復成本極低，不需要新設計**：探查確認 `SupportTicketService.getTenantTicket` 方法在 Sprint 91 就已寫好（原本只是內部呼叫用），本 Sprint只需要新增 Controller 路由接線，完全不需要新的 Service 邏輯或新測試（既有 Service 層測試已覆蓋兩種分支）。平台層更進一步重用同一方法（傳入 `tenantId=null, isSuperAdmin=true`），避免重複寫一份幾乎相同的查詢邏輯。
- **正確識別平台角色的回覆動作應重用店家端點，而非另開一個平台專屬端點**：PRD API 規格表的 `POST /dashboard/support/tickets/:id/messages` 角色欄位本就同時列出 StoreOwner/StoreStaff/**Admin**，代表平台管理者回覆工單走的是同一個端點（後端已用 `isSuperAdmin` 分支正確處理租戶篩選略過）。前端平台工單詳情頁的回覆動作因此直接呼叫 `postStaffMessage`（店家端點的 service 方法），而非另建一個平台專屬的訊息方法，避免了不必要的重複程式碼。

## 3. 待改善的（What to improve）

- **指派工單目前只能直接輸入使用者 UUID**，沒有姓名/email 搜尋介面，操作體驗較差。目前無使用者搜尋 API 可用，屬既有基礎設施缺口，非本 Sprint 範圍，已記錄待未來評估。
- **Controller 層測試持續累積技術債**：Sprint 90（`reversal-candidates`）、Sprint 91（三層路由）、本 Sprint（新增 2 個 GET 端點）皆未補 Controller/E2E 層 403 驗證測試，僅 Service 層 mock 測試覆蓋。三者可望在同一輪 QA 加強時一併處理，避免持續累積成龐大的補測試負擔。

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------|------|--------|--------|--------|
| （技術債，累積） | Controller/E2E 層 403 驗證測試補強 | 涵蓋 Sprint 90 結算逆轉、Sprint 91-92 客服工單三層路由 | QA Quincy | 🟡 中 | 待排入（建議獨立一個 QA 加強 Sprint） |
| （技術債） | 工單指派介面加入使用者搜尋（姓名/email），取代直接輸入 UUID | 需先有使用者搜尋 API | Dev David | 🟢 低 | 待排入 |
| DEF-043 | ROOM 訂房結帳流程建立預訂成功後清空整個購物車 | 延續自 Sprint 88 | Dev David | 🟡 中 | 待排入 |
| DEF-044 | 已付款訂單退款/取消時無庫存自動回補 | 延續自 Sprint 88 | PM Victoria | 🟡 中 | 待排入 |
| （技術債，延續） | `purchaseOrderApprovalThreshold` 無清除機制 | 待業務決策是否需要明確清除 API | PM Victoria | 🟢 低 | 待排入 |
| （技術債，延續自 Sprint 71-87） | `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |

---

## 5. Sprint 91 → Sprint 92 Action Items 追蹤結果

| Action Item | 內容 | Sprint 92 達成狀態 |
|------------|------|---------------------|
| 店家 Dashboard 工單管理頁 + 平台 Admin 工單列表/指派頁 | Sprint 91 排定範圍 | ✅ **已完成**（含補上發現的單筆詳情端點缺口） |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S89 | 5 |
| S90 | 6 |
| S91 | 10 |
| **S92** | **6**（2 個後端端點 + 4 個前端頁面） |

---

## 7. 下一步

> **檢查點**：Sprint 92 已完成，M18 客服工單子系統前後端全部到位（買家/店家/平台三層）。連續多 Sprint 排定的「三者最佳化順序」（M18/PRODUCT結帳/M16-M07表單）全部完成，M18 客服工單本身也已完整交付。下一輪依序進行：
> 1. 技術債清理：Controller/E2E 層 403 測試補強（建議獨立排一個 QA 加強 Sprint）；`RELEASE_TRACKER.md` 補列；DEF-043/DEF-044 排程評估。
> 2. 回歸 `E-Commerce_PRD_v1.0_Final.md` 找下一個未開發的 PRD 項目繼續。

---

**文件版本**: v1.0
**建立日期**: 2026-07-09
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
