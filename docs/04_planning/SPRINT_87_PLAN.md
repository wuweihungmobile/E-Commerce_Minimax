# Sprint 87 Plan — 收貨地址簿（PRD Phase 2-B）

**Sprint**: Sprint 87
**日期**: 2026-07-09
**主題**: 補齊 PRD §14.3.1 明文歸類為 Phase 2-B、且連續四個 Sprint（83/84/85/86）retro 列為候選但未排入的「收貨地址管理」功能——買家可維護多筆常用收貨地址，下單時可選用。

---

## 1. 探查結果

- **PRD/FRD 對此功能沒有完整規格**：僅在 §14.3.1 Phase 邊界矩陣（PRD L2338）與 §12.5（L2076）兩處提及「收貨地址延至 Phase 2-B」，**沒有 User Story、AC、Schema、API 規格**。唯一貼近的既有欄位是 `user_profiles.address`（PRD §8.3，單一自由文字欄位，且**未對應任何現有 JPA entity**）。本 Sprint 需從零設計。
- **`Order` entity 已有三個收件自由文字欄位**（`shippingAddress`/`shippingRecipientName`/`shippingPhone`，`Order.java` L90-97），下單流程（`OrderService.createOrderFromCart`）直接把 `OrderDto.CreateRequest` 傳入的文字塞入，**完全沒有「選擇已存地址」的機制**。`User` entity 完全沒有地址欄位。
- **Address/AddressBook 相關程式碼完全不存在**（entity/repository/service/controller/migration 全數缺失，從零開始）。
- **意外發現的相關既有缺口（不在本次範圍內處理，僅記錄）**：前端 checkout 頁面（`frontend/src/app/(auth)/checkout/page.tsx`）目前**只服務 Booking（訂房）流程**，PRODUCT（商品）訂單建立 API（`/v2/orders`）在前端完全沒有任何呼叫點——PRODUCT 結帳流程本身尚未串接，是比地址簿更大、更早存在、且與地址簿主題不直接相關的既有缺口。本 Sprint 僅新增地址簿的後端 API + 獨立的地址簿管理頁面（帳戶設定內的 CRUD UI），**不**嘗試補齊 PRODUCT checkout 串接（超出本次範圍，留待未來獨立 Sprint 評估）。

## 2. 架構決策

- **地址簿與租戶無關，是買家帳號的個人資料**：`Address` 屬於 `User`（`userId` 擁有權檢查），**不是**多租戶擁有權模式（買家可能向多個不同租戶/賣家下單，地址簿是全域個人資料，不綁定任何 `tenantId`）。這與本專案反覆出現的「租戶擁有權檢查」模式不同，Service 層 Javadoc 會明確註記以避免未來被誤判為漏掉租戶檢查。
- **`Order` 收件欄位維持現有自由文字設計（snapshot 語意），不改為外鍵關聯**：訂單是歷史記錄，收件資訊應在下單當下「複製」進 `Order.shippingAddress`/`shippingRecipientName`/`shippingPhone`（現有欄位不變），而非用可變/可刪除的 `Address` 外鍵去關聯——避免使用者事後編輯或刪除地址簿項目時，回頭影響已建立訂單的歷史收件資訊。`OrderDto.CreateRequest` 新增可選欄位 `addressId`：有提供時，後端查出該地址（驗證擁有權）並複製其欄位值覆蓋手動輸入的文字欄位；未提供則沿用現有手動輸入行為（向下相容）。
- **Schema**：`recipientName`/`phone`/`city`/`district`（可空，非台灣地址可留空）/`addressLine`/`postalCode`（可空）/`isDefault`。
- **預設地址語意**：使用者建立第一筆地址時自動設為預設；設定某筆為預設時，同使用者其餘地址的 `isDefault` 一併清除（單一預設，交易內完成）；刪除預設地址不自動遞補下一筆為預設（維持簡單，使用者可手動再設定）。

## 3. 資料庫變更（Migration V67）

```sql
CREATE TABLE addresses (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_name VARCHAR(100) NOT NULL,
    phone VARCHAR(50) NOT NULL,
    postal_code VARCHAR(20),
    city VARCHAR(100) NOT NULL,
    district VARCHAR(100),
    address_line VARCHAR(500) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_addresses_user ON addresses(user_id);
```

## 4. API 設計

- `GET /v2/addresses` — 列出自己的地址簿（依 `isDefault` 降序、`updatedAt` 降序）
- `POST /v2/addresses` — 新增地址
- `PUT /v2/addresses/{id}` — 更新地址（僅本人）
- `DELETE /v2/addresses/{id}` — 刪除地址（僅本人）
- `PUT /v2/addresses/{id}/default` — 設為預設地址（僅本人，交易內清除其餘預設）
- `OrderDto.CreateRequest` 新增可選 `addressId`：`OrderService.createOrderFromCart` 若提供則查地址（驗證 `userId` 擁有權，否則 `E_8007`）覆蓋收件欄位

## 5. 新增錯誤碼

- `E_8006`：找不到地址
- `E_8007`：無權操作他人地址

## 6. 實作清單（依 CLAUDE.md 開發-編譯-測試循環）

1. Migration V67
2. `Address.java` entity
3. `AddressRepository.java`
4. `ErrorCode` 新增 E_8006/E_8007
5. `AddressDto.java`（Create/Update Request + Response）
6. `AddressService.java`（CRUD + 擁有權檢查 + 預設地址邏輯）
7. `AddressController.java`
8. `OrderDto.CreateRequest` 新增 `addressId`；`OrderService.createOrderFromCart` 整合
9. 前端：帳戶設定內新增「地址簿」頁面（列表/新增/編輯/刪除/設預設）

## 7. 測試計畫

- `AddressServiceTest`：CRUD 成功案例、跨使用者操作拒絕（403/`E_8007`）、找不到地址（`E_8006`）、第一筆自動預設、設定新預設清除舊預設
- `AddressControllerE2ETest`（新檔）：真實 JWT 完整 CRUD 流程
- `OrderServiceTest`：新增 `addressId` 覆蓋收件欄位案例、`addressId` 屬於他人時拒絕
- 前端：地址簿頁面基本互動（新增/編輯/刪除/設預設）人工驗證（開發伺服器）

## 8. 範圍外

- 不補齊 PRODUCT 結帳流程前端串接（既有更大缺口，與本次主題無直接關聯，留待未來獨立評估）。
- 不做地址格式驗證/地址自動完成（如串接地圖 API），僅基本必填檢查。
- 不支援「一次下單同時使用多個收件地址」（拆單寄送），沿用既有訂單模型單一收件地址假設。
