# Release Notes - v2029.02.24-01 (Sprint 87)

**發布日期**: 2029-02-24（規劃）／實作完成 2026-07-09
**發布類型**: ✨ 新功能（收貨地址簿，PRD §14.3.1 Phase 2-B）
**Sprint**: Sprint 87（承接 Sprint 83/84/85/86 retro 連續四次列為候選的建議項目）

> Sprint 87 主題：補齊 PRD §14.3.1 明文歸類為 Phase 2-B 的「收貨地址管理」功能——買家可維護多筆常用收貨地址，下單時可選用。本次為後端 + 前端全棧交付。

---

## ✨ 新功能：收貨地址簿

- **新表 `addresses`**（Migration V67）：`recipientName`/`phone`/`postalCode`/`city`/`district`/`addressLine`/`isDefault`，屬於買家個人資料，**與租戶無關**（買家可能向多個不同租戶下單），擁有權檢查僅比對 `userId`，非本專案慣見的租戶擁有權模式（已於程式碼註記避免誤判）。
- **API**（`AddressController`，`@PreAuthorize("hasAuthority('user:read'/'user:update')")`）：
  - `GET /v2/addresses` — 列出自己的地址簿
  - `POST /v2/addresses` — 新增（第一筆自動設為預設）
  - `PUT /v2/addresses/{id}` — 更新
  - `DELETE /v2/addresses/{id}` — 刪除
  - `PUT /v2/addresses/{id}/default` — 設為預設（同使用者其餘地址預設狀態一併清除）
- **訂單整合**：`OrderDto.CreateRequest` 新增可選 `addressId`；提供時 `OrderService` 呼叫 `AddressService.getOwnedAddress`（驗證擁有權）複製地址內容覆蓋 `Order.shippingAddress`/`shippingRecipientName`/`shippingPhone` 手動輸入欄位。`Order` 既有欄位維持 snapshot 語意不變——訂單為歷史記錄，事後編輯/刪除地址簿項目不影響已建立訂單。
- **前端**：新增獨立頁面 `/addresses`（帳戶區選單新增「地址簿」連結），列表 + inline 新增/編輯表單 + 刪除確認 + 設預設，比照既有 `dashboard/faq/categories` 管理頁慣例（無彈窗元件）。

## 🔍 探查中發現、記錄但不在本次範圍處理的既有缺口

- 前端 checkout 頁面目前只服務 Booking（訂房）流程，PRODUCT（商品）訂單建立 API 在前端完全沒有任何呼叫點——即使地址簿後端能力已完成，目前買家真實購物流程中尚無法直接透過頁面選用（需前端 PRODUCT 結帳串接補齊後才能真正使用）。已列入下一輪候選並提高優先度。

## 🗄️ 資料庫變更（Migration V67）

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
```

## 測試 / 驗證 ✅

- **`AddressServiceTest`**（新檔）：10 tests（CRUD、跨使用者拒絕 E_8007、找不到地址 E_8006、第一筆自動預設、設定新預設清除舊預設）。
- **`AddressControllerE2ETest`**（新檔）：8 tests（真實 JWT 完整 CRUD 流程、跨使用者 403、未帶 Token 401/403）。
- **`OrderServiceTest`**：+2 tests（`addressId` 覆蓋收件欄位、`addressId` 屬於他人時拒絕）。
- **全量回歸**（`mvn verify -Pintegration-test`）：**單元 926 + 整合 367 = 1293 tests，0 failures，0 errors，BUILD SUCCESS**。
- **schema 漂移守門**：`make validate-schema` 無漂移（V67 已對齊）。
- **前端**：`npm run build`（含 TypeScript 型別檢查）通過，`eslint` 無 error；受限於工具集無瀏覽器自動化能力，僅完成建置/型別檢查/路由掛載確認，未做人工互動式瀏覽器操作驗證（已於 Sprint Retro 誠實記錄）。

## 內含 Commit（Sprint 87）

| 項目 | 說明 |
|------|------|
| Sprint 87 Plan | 收貨地址簿規劃（含範圍決策記錄） |
| Migration V67 | `addresses` 新表 |
| 核心實作 | `Address` entity/repository/service/controller、`ErrorCode` 新增 E_8006/E_8007、`OrderService` 整合 `addressId` |
| 前端 | `/addresses` 頁面、`AddressService`、Header 選單連結 |
| Sprint 87 收尾 | Retro / Release Notes + `DEFERRED_ITEMS_TRACKER.md`（AI-2421 移至已完成） |

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-09
**基於**: AISDLC v0.09 Release Management Workflow
