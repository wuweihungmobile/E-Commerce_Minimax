# Sprint 94 Plan — 會員資料 Export + 帳戶刪除（被遺忘權）

**Sprint**: Sprint 94
**日期**: 2026-07-18
**主題**: PRD §1.5.1 明文承諾的會員兩項權利——「資料 Export（Phase 2）」與「帳戶刪除（Right to be Forgotten，Phase 2）」，Sprint 93 全面重新比對 PRD 全文時發現的第二個落地缺口（AI-2428）。PRD 本身未定義對應 API/資料模型，本 Sprint 先盤點程式碼影響範圍、經使用者拍板三項業務決策後直接落地實作。

---

## 1. 盤點結果與業務決策

背景 Agent 盤點 User 及所有引用 User 的 entity 後，確認以下真正需要業務/法遵判斷的分歧點，已交由使用者拍板（詳見對話記錄）：

1. **功能範圍**：帳戶自助刪除**僅開放 BUYER 角色**。盤點發現 `TenantService` 既有邏輯明確禁止移除 StoreOwner（會讓商店孤兒化），且系統對 Admin/SUPER_ADMIN/CFO 也沒有「防止刪除最後一位管理者」的機制，這些角色的刪除需求需另案處理。
2. **進行中交易**：使用者有**未結案（非終態）訂單或訂房時封鎖刪除請求**，避免留下無法追蹤的孤兒交易/金流問題。
3. **交易快照個資**：`Order.shippingRecipientName/shippingPhone`、`Booking.guestName/guestPhone/guestEmail` 這類「下單當下」的收件人姓名電話快照**保留不動**，視為交易歷史記錄的一部分（與「保留交易記錄」原則一致），記錄為已知限制，不在本次匿名化範圍內。

## 2. 架構設計

- **匿名化沿用既有慣例**：`User.status` 已有 `"ACTIVE"` 字串慣例被 `AuthService.login`/`refreshToken` 檢查依賴，改寫為 `"DELETED"` 後會自動被既有認證邏輯擋下，不需修改認證程式碼。
- **Email unique 約束處理**：改寫為 `deleted-{uuid}@anonymized.local` 佔位值，釋放原 email 供他人未來使用。
- **會話終止**：重用既有 `RefreshTokenService.blacklistAllRefreshTokens`（`AuthService.logout` 既有前例）撤銷所有 refresh token；access token 本身為無狀態 JWT，短效期內自然過期，屬既有架構限制非本 Sprint 範圍。
- **關聯資料清理**：
  - `Address`（收貨地址簿）：非交易記錄、無留存義務，直接刪除（`AddressRepository.deleteByUserId`，新增）。
  - `OAuthAccount`：直接刪除（複用既有 `deleteByUserId`）。第三方 provider 端不會同步解除授權，記錄為已知限制。
  - Order/Booking/Review/BookingReview/Notification/SupportTicket/TenantMember 等：**不刪除**，僅 User 本身被匿名化；由於評價/聊天等內容顯示作者姓名時是即時查詢 `User.fullName`（非快照），User 匿名化後這些既有內容會自動顯示為「已刪除的使用者」，不需逐一修改各模組。
- **Data Export**：新增 `UserDataExportResponse`（彙整 profile/訂單/訂房/評價/地址/通知偏好/站內通知/客服工單/OAuth 綁定/商店會員關係），透過 `Pageable.unpaged()` 取得完整資料（既有 Service 層方法多半有頁面大小上限，不適合「匯出全部」語意，故直接呼叫 Repository）。**已知限制**：聊天訊息與客服工單訊息串完整內容未包含在匯出範圍（僅列摘要），因 `MessageRepository`/`SupportMessageRepository` 目前僅有依 conversationId/ticketId 查詢的方法，無 `findBySenderId`，逐一展開需要 N+1 查詢，範圍留待未來需求明確再評估。
- **新增錯誤碼**：`E_1009`（角色不適用自助刪除，403）、`E_1010`（尚有未完成交易，409）。PRD §16.4.2 原文寫的 `E-6001` 已在 Sprint 93 確認撞碼；本 Sprint 兩個新錯誤碼延續 auth 區段（1000s）編號慣例。

## 3. 實作清單

### 後端
1. `UserPrivacyService`（新檔，`core/user/`）：`exportMyData()` + `deleteMyAccount()`。
2. `UserDataExportResponse`（新檔，`api/dto/`）：彙整 DTO，含 `knownLimitations` 欄位誠實揭露範圍外項目。
3. `AuthController`：新增 `GET /v2/auth/me/data-export`、`DELETE /v2/auth/me`。
4. `OrderRepository`/`BookingRepository`：新增 `existsByUserIdAndStatusNotIn`（檢查是否有未結案交易）。
5. `AddressRepository`：新增 `deleteByUserId`。
6. `ErrorCode`：新增 `E_1009`/`E_1010`；`GlobalExceptionHandler.mapErrorCodeToStatus` 對應 FORBIDDEN/CONFLICT。

### 測試
- `UserPrivacyServiceTest`（新檔，6 tests）：export 彙整、成功刪除、非 BUYER 拒絕、有未結案訂單/訂房拒絕、找不到使用者。
- `AuthControllerE2ETest`（+3 tests，真 DB）：data-export 成功、BUYER 成功匿名化、非 BUYER 角色 403。

## 4. 驗證結果

- 後端單元測試 6 tests 0 fail（`UserPrivacyServiceTest`）
- 真 DB E2E 測試 3 tests 0 fail（新增，`AuthControllerE2ETest` 累計 13 tests 0 fail）
- Checkstyle 0 violations、PMD 0 violations
- 全量回歸 `mvn verify -Pintegration-test`：詳見 commit 訊息
- `make validate-schema`：無 migration，schema-free

## 5. 範圍外（延後）

- StoreOwner/Staff/Admin/SUPER_ADMIN/CFO 角色的帳戶刪除需求（需先設計商店擁有權轉移/最後一位管理者防護機制）。
- 聊天訊息、客服工單訊息串完整內容納入資料匯出範圍。
- 訂單/訂房收件人姓名電話快照的匿名化（已拍板保留不動，記錄為已知限制而非待辦）。
- Access token 立即失效機制（目前僅撤銷 refresh token，access token 短效期內自然過期）。
