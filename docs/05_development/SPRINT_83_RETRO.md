# Sprint 83 Retrospective / Sprint 83 回顧會議

> **Sprint 編號**: Sprint 83
> **期間**: 2026-07-08
> **回顧日期**: 2026-07-08
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 3 SP |
| 完成 SP | 3 SP |
| 主軸 | PRD v1.0 缺口盤點後，使用者選定的 P0 Must Have 項目：M12 動態定價房東後台 90 天定價日曆預覽 |

---

## 2. 做得好的（What went well）

- **動手前先確認是否已有可複用的既有邏輯，而非直接新寫一套**：探查發現 `BookingService.getCalendar`（Sprint 41/45/47 累積建置）早已是「逐日定價 + 開放窗判斷」的完整封裝，且已支援 92 天區間。最終實作只新增一個租戶擁有權檢查方法並委派既有邏輯，**零重複程式碼**，也因此完全不需要碰動既有已充分測試的定價/日曆核心邏輯，把改動風險降到最低。
- **比照既有 tenant-based 擁有權檢查模式，新端點從第一版就做對**：`getCalendarForOwner` 沿用 Sprint 80-82 累積的 `checkTenantAccess` 模式（非 SUPER_ADMIN 限自己租戶），紅燈測試涵蓋跨租戶拒絕/本租戶放行/SUPER_ADMIN 放行三案例。
- **前端優先確認既有元件，避免重複造輪**：發現房東後台已有 `PricingCalendarPreview.tsx` 元件（標題甚至已寫「未來 90 天內的價格計算」），只是尚未串接唯讀日曆端點；改為在既有元件內新增自動載入區塊，保留原本的「試算特定入住區間」表單（用途不同，非重複）。
- **意外發現一個比目前已修復項目都嚴重的新漏洞，如實記錄而非忽略**：實作租戶擁有權檢查時，比對 `RoomService`/`ProductService` 既有寫入方法作為參考前例，卻發現這兩個 Service 的 `update`/`delete` 系列方法完全沒有租戶檢查——任一租戶可竄改/刪除他租戶的房源或商品。這與本 Sprint 任務無直接關聯，未順手修改（避免混雜責任範圍），但立即記錄為 `DEF-041` 並在完成報告中明確提醒使用者優先考慮。

---

## 3. 待改善的（What to improve）

- 無重大待改善事項。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------|------|--------|--------|--------|
| **DEF-041**（新發現） | `RoomService`/`ProductService` 寫入方法完全無租戶擁有權檢查 | 任一租戶可竄改/刪除他租戶房源或商品，風險高於已修復的 `DEF-034`/`DEF-040`（跨租戶寫入/刪除 vs. 跨租戶讀取），無業務決策疑慮，建議直接排入下一輪修復 | Dev David | 🔴 **建議優先** | 建議 Sprint 84 |
| （待決策，延續自 Sprint 76-83）| `DEF-037`：`ShippingTemplateService.calculateFee` 跨租戶查詢 | 使用者已決策擱置 | PO Victoria（已決策） | 🟢 低 | 已結案（擱置） |
| （技術債，延續自 Sprint 71-83）| `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |

---

## 5. Sprint 82 → Sprint 83 Action Items 追蹤結果

| Action Item | 內容 | Sprint 83 達成狀態 |
|------------|------|---------------------|
| 回到 PRD v1.0 剩餘功能盤點 | Sprint 82 記錄的下一步建議 | ✅ **已完成**：盤點出 7 項缺口（2 項 P0：定價日曆預覽/採購審批金額上限；1 項規格要求：結算調整單；4 項 Phase 2-B/3：地址簿/客服工單/評論反水軍/聊天位置傳送），使用者選定定價日曆預覽為本 Sprint 主軸 |
| M12 動態定價房東後台定價日曆預覽 | PRD P0 Must Have | ✅ **已完成**：`BookingService.getCalendarForOwner` + `GET /v2/dashboard/pricing/calendar` + 前端自動載入區塊 |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S79 | 5 |
| S80 | 13 |
| S81 | 6 |
| S82 | 3 |
| **S83** | **3**（M12 定價日曆預覽）|

---

## 7. 下一步

> **檢查點**：Sprint 83 已完成。**下一輪規劃建議（依優先級）**：
> 1. 🔴 **`DEF-041`**（`RoomService`/`ProductService` 跨租戶寫入漏洞）——本 Sprint 新發現，風險高於同類已修復項目，無業務決策疑慮，建議優先排入。
> 2. PRD P0：M16 ERP 採購審批金額上限機制。
> 3. PRD 規格要求：M07 結算系統跨週期退款調整單（`adjustment_statement`）。
> 4. PRD Phase 2-B 項目：收貨地址簿、M18 客服工單子系統，視容量排入。

---

**文件版本**: v1.0
**建立日期**: 2026-07-08
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
