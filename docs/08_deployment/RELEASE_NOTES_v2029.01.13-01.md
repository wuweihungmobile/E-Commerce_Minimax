# Release Notes - v2029.01.13-01 (Sprint 84)

**發布日期**: 2029-01-13（規劃）／實作完成 2026-07-08
**發布類型**: 🔒 安全修復（DEF-041 租戶擁有權缺口 + 根因修復）；無前端變動
**Sprint**: Sprint 84（承接 Sprint 83 發現的 DEF-041）

> Sprint 84 主題：修復 `RoomService`/`ProductService` 寫入層完全缺乏租戶擁有權檢查的 IDOR 漏洞（DEF-041）。實作驗證過程中意外發現更根本的問題——房源/商品建立時從未真正寫入 `tenant_id`/`owner_id` 到資料庫——經使用者同意後一併修復。

---

## 🔒 安全修復：DEF-041 租戶擁有權檢查

- **問題**：`RoomService.updateRoom`/`clearOpenWindow`/`deleteRoom` 與 `ProductService.updateProduct`/`deleteProduct` 完全沒有租戶擁有權檢查，`RoomController`/`ProductController` 的 `@PreAuthorize` 僅檢查權限（authority）非租戶範圍，任一持有對應權限的使用者只要取得他租戶的 `listingId` 即可竄改/軟刪除該房源或商品。
- **架構決策**：採用 Sprint 80/83 既有模式（`checkListingTenantOwnership`：非 SUPER_ADMIN 限自己租戶，SUPER_ADMIN 可跨租戶），而非 Sprint 74 `CmsService` 較舊、允許 `ROLE_ADMIN` 也跨租戶的模式（依 CLAUDE.md Rule 7 衝突時選較新、測試較多者）。
- **修復**：`RoomService`/`ProductService` 各自新增 `checkListingTenantOwnership` private helper；五個寫入方法新增 `isSuperAdmin` 參數；`RoomController`/`ProductController` 對應端點新增 `@AuthenticationPrincipal UserPrincipal` 判斷角色（比照 `PricingController`）。

## 🔴 意外發現並一併修復的根因問題

- **問題**：`RoomService.createRoom`/`createRoomFromDashboard`/`ProductService.createProduct`/`createProductFromDashboard` 建立 `Listing` 時只設定唯讀影子欄位 `.tenantId(...)`，從未設定真正被 JPA 用來寫入的 `.tenant(...)`/`.owner(...)` 關聯物件，導致房源/商品的 `tenant_id`/`owner_id` 從未真正落地資料庫（直接查詢測試 DB 驗證確認）。這使租戶擁有權檢查建立在永遠是空值的欄位上，且連帶使 `getRooms`/`getProducts` 預設分支的 tenantId 過濾查詢理論上永遠查不到已建立的房源/商品。
- **修復**：四個 create 方法新增 `TenantRepository`/`UserRepository` 依賴，比照既有 `PostService` 模式新增 `fetchTenant`/`fetchOwner`（`findById` + 找不到分別拋 `E_2000`/`E_1006`），建立時正確設定 `.tenant(tenant)`/`.owner(owner)`。

## 🧹 根因修復後浮現的既有測試衛生缺口（一併修復）

- `ProductControllerE2ETest`/`CartControllerE2ETest`：`tearDown` 刪除使用者前未清理其擁有的 listings/products，根因修復後 FK 真正生效而報錯；改為先依 `ownerId` 查出 listings、先刪 `products`（`Product.listingId` 與 `Listing.id` 為同一 PK）再刪 `listings`，最後才刪使用者。
- `M01ProductIntegrationTest`/`M02RoomIntegrationTest`：新端點需要真正 `UserPrincipal`（`@AuthenticationPrincipal`），`@WithMockUser` 預設 principal 型別不符會被注入 `null` 而 NPE；改用手動建構 `Authentication`（比照 `M07SettlementIntegrationTest.authAs` 既有模式），並新增 `TenantRepository`/`UserRepository` mock。

## 測試 / 驗證 ✅

- **`RoomServiceTest`**：+8 tests（跨租戶拒絕/SUPER_ADMIN 放行 × updateRoom/deleteRoom/clearOpenWindow），共 11 tests。
- **`ProductServiceTest`**：+6 tests（同租戶成功/跨租戶拒絕/SUPER_ADMIN 放行 × updateProduct/deleteProduct），共 9 tests。
- **`ProductControllerE2ETest`**：10/10 通過（含根因修復後的 tearDown 修正）。
- **`CartControllerE2ETest`**：12/12 通過。
- **`M01ProductIntegrationTest`/`M02RoomIntegrationTest`**：6/6、3/3 通過。
- **後端全量整合回歸**（`mvn verify -Pintegration-test`，`make test-db-up` 後）：詳見 commit 訊息最終數字。
- **schema 漂移守門**：`make validate-schema` 無漂移（無 migration，純新增方法/端點/依賴）。

## 內含 Commit（Sprint 84）

| 項目 | 說明 |
|------|------|
| Sprint 84 Plan | DEF-041 修復規劃 |
| DEF-041 修復 | `RoomService`/`ProductService` 租戶擁有權檢查 + Controller 改動 |
| 根因修復 | create 系列方法正確設定 `.tenant()`/`.owner()` |
| 既有測試修復 | `ProductControllerE2ETest`/`CartControllerE2ETest`/`M01ProductIntegrationTest`/`M02RoomIntegrationTest` |
| Sprint 84 收尾 | Retro / Release Notes + `DEFERRED_ITEMS_TRACKER.md`（DEF-041 移至已完成）|

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-08
**基於**: AISDLC v0.09 Release Management Workflow
