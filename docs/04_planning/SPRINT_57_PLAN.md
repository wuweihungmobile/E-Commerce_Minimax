# Sprint 57 計劃 / Sprint 57 Plan

> **Sprint 編號**: Sprint 57
> **期間**: 2027-12-19 ~ 2028-01-01 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-04
> **基於**: `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` 活躍延後項目（AI-2202f）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 前置技術調查 | ✅ 完成：`RoomService.updateRoomOpenWindowFields`（`:249-256`）沿用全專案「非 null 才更新」慣例（`updateListingFromRequest`/`updateRoomLocationFields`/`updateRoomCapacityFields`/`updateRoomTimeFields` 皆同模式），`RoomDto.UpdateRequest` 兩欄位為純 `LocalDate`/`Integer`（非 `Optional`/`JsonNullable` 包裝），故「未提供」與「顯式清空」在 Jackson 反序列化後無法區分；全 repo 唯一「清除」慣例是 `CartController.clearCart`（`@DeleteMapping`，清空整個購物車資源，非欄位級）；前端 `RoomForm.tsx` 清空輸入框後 `undefined` 會被 `JSON.stringify` 省略，是同一缺陷的前端鏡像，非另一問題 |
| **機制選擇（工程設計，非業務決策）** | ✅ **新增專屬清除端點**（`DELETE /v2/rooms/{listingId}/open-window`），**不**將 DTO 改為 `Optional`/`JsonNullable` 包裝型別 | 依 Rule 11（配合既有慣例）：專案內「非 null 才更新」為全域一致慣例（4 處輔助方法皆同模式），若僅為這兩欄位破例改用 wrapper 型別，會造成 DTO 型別不一致、且需額外註冊 Jackson 模組（`JsonNullableModule` 或等效機制）支援反序列化，波及面遠大於效益；比照既有 `CartController.clearCart` 的獨立清除端點模式風險最低、與既有慣例最一致。此為純工程/架構判斷，非需 PO 商業知識之決策，依 Sprint 53 先例授權範圍逕行決定，已記錄理由 |
| Push 狀態 | ⏸️ 維持批次 push 決策，累積延伸至 S41~S57 | 不影響本 Sprint 開發 |

---

## 1. Sprint 57 目標

> **主題**: 開放窗清除機制——新增專屬端點，讓賣家可將 `open_until_date`/`booking_window_days` 清回 NULL（無限制）

Sprint 47（AI-2202e）建立開放窗欄位後，`updateRoom` 的「非 null 才更新」慣例導致賣家一旦設定就無法清除。本 Sprint 新增 `DELETE /v2/rooms/{listingId}/open-window` 端點，一次性將兩欄位皆設回 `null`，並同步修正前端 `RoomForm` 讓賣家可透過 UI 觸發清除（而非仰賴清空輸入框送出，那個路徑目前無效且具誤導性）。

---

## 2. User Story

### US-001：開放窗清除機制——後端端點 + 前端觸發（AI-2202f）

> **SP**: 2 | **優先級**: P4 | **狀態**: 📋 Ready

**AC-001-1**: `RoomService` 新增 `clearOpenWindow(UUID listingId)`：找到 Room，將 `openUntilDate`/`bookingWindowDays` 皆設為 `null`，儲存並回傳 `RoomDto.Response`（比照 `updateRoom` 回傳型別，方便前端沿用既有更新後刷新邏輯）。

**AC-001-2**: `RoomController` 新增 `DELETE /v2/rooms/{listingId}/open-window`，`@PreAuthorize("hasAuthority('room:update')")`（修改既有資源欄位，非刪除整個房源，故用 update 權限非 delete 權限）。

**AC-001-3**: 前端 `RoomForm.tsx` 修正清空行為的誤導性——開放窗兩欄位改為提供明確的「清除」操作（例如按鈕），呼叫新端點而非依賴清空輸入框送出（該路徑因 `JSON.stringify` 省略 `undefined` 欄位，實際上不會清除任何東西）。

**AC-001-4**: 測試——`RoomServiceTest` 新增單元測試（清除後兩欄位皆為 null；清除不影響其他欄位）；`RoomControllerE2ETest`（或既有對應真 DB 整合測試）新增端點測試（清除成功、越權/未授權拒絕）。既有 `API-M06-016`（開放窗三層一致）不退步。

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 開放窗清除機制（AI-2202f）| 2 | P4 |

> **Velocity 參考**：符合 `DEFERRED_ITEMS_TRACKER.md` 原估 2 SP（純新增端點 + 前端小幅調整，無 schema 變動、無既有邏輯行為變更）。

---

## 4. Definition of Done

- [ ] US-001：`RoomService.clearOpenWindow` + `DELETE /v2/rooms/{listingId}/open-window` 端點 + 前端清除操作
- [ ] 後端單元 + 真 DB 整合測試涵蓋清除成功、越權拒絕；既有開放窗相關測試（API-M06-016）不退步
- [ ] `make validate-schema` 無漂移（無 migration，schema-free）
- [ ] `DEFERRED_ITEMS_TRACKER.md` AI-2202f 狀態更新
- [ ] Sprint 57 Review / Retro / Release Notes + trackers

---

## 5. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端 Service | `core/room/RoomService.java`（新增 `clearOpenWindow`）|
| 後端 API | `api/controller/RoomController.java`（新增 `DELETE /{listingId}/open-window`）|
| 前端 | `frontend/src/components/room/RoomForm.tsx`（開放窗清除操作）|
| 後端測試 | `RoomServiceTest`（新增）/ 對應真 DB 整合測試（新增）|
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

> **本 Sprint 無 migration**（schema-free，欄位皆已存在，僅新增清除路徑）。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
