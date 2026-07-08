# Sprint 84 Plan — DEF-041 修復（RoomService/ProductService 租戶擁有權缺口）

**Sprint**: Sprint 84
**日期**: 2026-07-08
**主題**: 修復 Sprint 83 開發過程中新發現的 DEF-041——`RoomService`/`ProductService` 的寫入類方法（`updateRoom`/`deleteRoom`/`clearOpenWindow`/`updateProduct`/`deleteProduct`）完全沒有租戶擁有權檢查，任一租戶可竄改/刪除他租戶的房源或商品。

---

## 1. 問題範圍（探查結果）

| Service | 方法 | 漏洞內容 |
|---------|------|----------|
| `RoomService` | `updateRoom` | 僅用 `findRoomByListingId(listingId)`（無 tenant 篩選）取出後直接寫入 |
| `RoomService` | `clearOpenWindow` | 同上 |
| `RoomService` | `deleteRoom` | 同上，軟刪除（設 `Listing.status = DELETED`） |
| `ProductService` | `updateProduct` | 同上 |
| `ProductService` | `deleteProduct` | 同上 |

對應 Controller（`RoomController`/`ProductController`）的 `@PreAuthorize` 僅檢查 `room:update`/`room:delete`/`product:update`/`product:delete` 這類**權限（authority）**，不是租戶專屬檢查——任何擁有該 authority 的使用者（無論哪個租戶）皆可通過。

## 2. 架構決策：採用 Sprint 80-83 既有模式，而非 Sprint 74 CmsService 模式

專案內存在兩種既有租戶擁有權檢查慣例：

- **模式 A**（`BookingService.checkListingTenantOwnership`，Sprint 83）：Controller 透過 `@AuthenticationPrincipal UserPrincipal` 取得 `principal.getRole()`，僅 `"SUPER_ADMIN"` 可跨租戶，其餘一律限自己租戶。
- **模式 B**（`CmsService.checkCmsTenantOwnership`/`isCurrentUserAdmin`，Sprint 74）：Service 內部透過 `SecurityContextHolder` 判斷 authorities，`ROLE_SUPER_ADMIN` 或 `ROLE_ADMIN` 皆可跨租戶。

依 CLAUDE.md Rule 7（模式衝突時選擇較新、測試覆蓋較多者，並公開衝突而非混用），本次採**模式 A**：與 Sprint 80（`SettlementReviewer`）、Sprint 83（`BookingService.getCalendarForOwner`）一致的「非 SUPER_ADMIN 限自己租戶」語意，不引入 `ROLE_ADMIN` 跨租戶的例外。

## 3. 實作方式

- `RoomService`/`ProductService` 各自新增 `checkListingTenantOwnership(Listing, boolean isSuperAdmin)` private helper（與 `BookingService` 內同名方法邏輯一致，各自獨立實作，不抽共用類別——避免無明確需求的跨模組耦合，比照既有慣例）。
- `updateRoom`/`deleteRoom`/`clearOpenWindow`/`updateProduct`/`deleteProduct` 簽名新增 `boolean isSuperAdmin` 參數，在取得 `Listing` 後、寫入前呼叫擁有權檢查，失敗拋 `BusinessException(ErrorCode.E_1007)`（HTTP 403，與既有 `BookingService`/`SettlementReviewer` 一致）。
- `RoomController`/`ProductController` 三/二個端點新增 `@AuthenticationPrincipal UserPrincipal principal` 參數，比照 `PricingController` 計算 `isSuperAdmin = SUPER_ADMIN_ROLE.equals(principal.getRole())` 後傳入 Service。

## 4. 測試計畫

- `RoomServiceTest`：既有 3 個 `clearOpenWindow` 測試補上 tenant context 設定與新參數；新增跨租戶拒絕/SUPER_ADMIN 放行測試，涵蓋 `updateRoom`/`deleteRoom`/`clearOpenWindow` 三方法。
- `ProductServiceTest`：既有 3 個關鍵字搜尋測試不變；新增 `updateProduct`/`deleteProduct` 的同租戶成功/跨租戶拒絕/SUPER_ADMIN 放行測試。
- 既有 `ProductControllerE2ETest`（真實 JWT 登入 + 同租戶操作）需保持全數通過，驗證正常路徑未被破壞。

## 5. 範圍外

- 不新增 Room 對應的 Controller E2E 測試檔案（目前不存在，非本次必要，範圍聚焦修漏洞本身）。
- 不處理其他模組是否有類似缺口（本次僅處理 DEF-041 明確記錄的 RoomService/ProductService）。
