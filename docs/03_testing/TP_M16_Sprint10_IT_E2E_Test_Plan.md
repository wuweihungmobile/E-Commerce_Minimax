# M16 ERP IT/E2E 測試計劃 / Test Plan

**文件版本**: v1.0
**Sprint**: Sprint 10
**建立日期**: 2026-05-07
**IT/E2E 執行時間**: Sprint Day 3-5 (2026-05-20 至 2026-05-22)

---

## 1. 測試範圍 / Test Scope

### 1.1 功能範圍

M16 ERP (進銷存系統) Backend API，包含以下模組：

- **供應商管理** (Supplier Management)
- **採購單管理** (Purchase Order Management)
- **庫存管理** (Inventory Management)
- **庫存異動** (Stock Movement)

### 1.2 API Base URL

```
/api/v2/dashboard
```

### 1.3 認證需求

- Bearer Token 認證
- 角色權限：`STORE_OWNER` 或 `SELLER`

---

## 2. 測試策略 / Test Strategy

### 2.1 測試金字塔

```
           ┌─────────────┐
           │    E2E      │  ← 完整 business workflow
           │   Tests     │
           ├─────────────┤
           │     IT      │  ← API endpoint 測試
           │   Tests     │
           ├─────────────┤
           │   Unit      │  ← Service/Repository 測試
           │   Tests     │
           └─────────────┘
```

### 2.2 測試環境

- **IT Tests**: 使用 `integration-test` profile，真實 PostgreSQL + Mock Redis
- **E2E Tests**: 使用 REST Assured，完整 HTTP 層測試

---

## 3. IT 測試案例 / Integration Test Cases

### 3.1 供應商管理 / Supplier Management

| 測試編號 | 測試描述 | 優先權 | 前置條件 |
|----------|----------|--------|----------|
| IT-M16-001 | 建立供應商-成功 | P0 | 已登入 STORE_OWNER |
| IT-M16-002 | 建立供應商-名稱必填 | P0 | 已登入 STORE_OWNER |
| IT-M16-003 | 建立供應商-Email格式錯誤 | P1 | 已登入 STORE_OWNER |
| IT-M16-004 | 更新供應商-成功 | P0 | 已存在供應商 |
| IT-M16-005 | 更新供應商-不存在 | P0 | 已登入 STORE_OWNER |
| IT-M16-006 | 查詢供應商列表-全部 | P0 | 已存在供應商 |
| IT-M16-007 | 查詢供應商列表-依狀態篩選 | P1 | 已存在多個供應商 |
| IT-M16-008 | 查詢供應商列表-無供應商 | P2 | Tenant 下無供應商 |

### 3.2 採購單管理 / Purchase Order Management

| 測試編號 | 測試描述 | 優先權 | 前置條件 |
|----------|----------|--------|----------|
| IT-M16-101 | 建立採購單-成功 | P0 | 已存在供應商和 SKU |
| IT-M16-102 | 建立採購單-supplierId 必填 | P0 | 已登入 STORE_OWNER |
| IT-M16-103 | 建立採購單-items 必填 | P0 | 已存在供應商 |
| IT-M16-104 | 查詢採購單列表-分頁 | P0 | 已存在多個採購單 |
| IT-M16-105 | 查詢採購單列表-依狀態篩選 | P1 | 已存在不同狀態採購單 |
| IT-M16-106 | 查詢採購單詳情-成功 | P0 | 已存在採購單 |
| IT-M16-107 | 查詢採購單詳情-不存在 | P0 | 已登入 STORE_OWNER |
| IT-M16-108 | 更新採購單-成功 | P0 | 狀態為 DRAFT |
| IT-M16-109 | 更新採購單-已提交不可修改 | P1 | 狀態為 SUBMITTED |
| IT-M16-110 | 提交採購單-成功 DRAFT→SUBMITTED | P0 | 狀態為 DRAFT |
| IT-M16-111 | 提交採購單-非 DRAFT 狀態 | P1 | 狀態為 SUBMITTED |
| IT-M16-112 | 收貨-成功 PARTIAL_RECEIVED | P0 | 狀態為 SUBMITTED，部分收貨 |
| IT-M16-113 | 收貨-成功 RECEIVED | P0 | 狀態為 SUBMITTED，全部收貨 |
| IT-M16-114 | 收貨-非 SUBMITTED/PARTIAL_RECEIVED 狀態 | P1 | 狀態為 RECEIVED |
| IT-M16-115 | 取消採購單-成功 DRAFT→CANCELLED | P0 | 狀態為 DRAFT |
| IT-M16-116 | 取消採購單-成功 SUBMITTED→CANCELLED | P0 | 狀態為 SUBMITTED |
| IT-M16-117 | 取消採購單-非 DRAFT/SUBMITTED 狀態 | P1 | 狀態為 PARTIAL_RECEIVED |

### 3.3 庫存管理 / Inventory Management

| 測試編號 | 測試描述 | 優先權 | 前置條件 |
|----------|----------|--------|----------|
| IT-M16-201 | 查詢庫存列表-分頁 | P0 | 已存在庫存記錄 |
| IT-M16-202 | 查詢 SKU 詳情-成功 | P0 | 已存在 SKU |
| IT-M16-203 | 查詢 SKU 詳情-不存在 | P0 | 已登入 STORE_OWNER |
| IT-M16-204 | 低庫存警報-有空壓警示 | P0 | 有低於安全存量的 SKU |
| IT-M16-205 | 低庫存警報-無警示 | P1 | 所有 SKU 高於安全存量 |

### 3.4 庫存異動 / Stock Movement

| 測試編號 | 測試描述 | 優先權 | 前置條件 |
|----------|----------|--------|----------|
| IT-M16-301 | 手動異動-INBOUND 成功 | P0 | 已存在 SKU |
| IT-M16-302 | 手動異動-OUTBOUND 成功 | P0 | 庫存充足 |
| IT-M16-303 | 手動異動-ADJUST 成功 | P0 | 已存在 SKU |
| IT-M16-304 | 手動異動-數量必填 | P0 | 已存在 SKU |
| IT-M16-305 | 手動異動-OUTBOUND 庫存不足 | P1 | OUTBOUND 數量 > 庫存 |
| IT-M16-306 | 查詢異動列表-分頁 | P0 | 已存在異動記錄 |

---

## 4. E2E 測試案例 / End-to-End Test Cases

### 4.1 採購單完整生命週期 / Purchase Order Full Lifecycle

**E2E-M16-001: 採購單完整流程**

```
測試步驟：
1. 建立供應商 (POST /suppliers)
2. 建立採購單，包含多個 items (POST /purchase-orders)
3. 提交採購單 (PUT /purchase-orders/{id}/submit)
4. 收貨（部分收貨）(PUT /purchase-orders/{id}/receive)
5. 收貨（剩餘收貨）(PUT /purchase-orders/{id}/receive)
6. 驗證庫存增加 (GET /inventory/{skuId})
7. 驗證低庫存警報消失 (GET /inventory/alerts)

預期結果：
- 採購單狀態： DRAFT → SUBMITTED → PARTIAL_RECEIVED → RECEIVED
- 庫存數量正確增加
- 警報在收貨後消失
```

### 4.2 採購單取消流程 / Purchase Order Cancellation

**E2E-M16-002: DRAFT 狀態取消**

```
測試步驟：
1. 建立供應商
2. 建立採購單
3. 取消採購單 (PUT /purchase-orders/{id}/cancel)

預期結果：狀態變為 CANCELLED
```

**E2E-M16-003: SUBMITTED 狀態取消**

```
測試步驟：
1. 建立供應商
2. 建立採購單
3. 提交採購單
4. 取消採購單

預期結果：狀態變為 CANCELLED
```

### 4.3 庫存異動流程 / Stock Movement Flow

**E2E-M16-004: 進貨異動 (INBOUND)**

```
測試步驟：
1. 建立供應商
2. 建立採購單並提交
3. 收貨完成
4. 手動調整庫存 (ADJUST)
5. 驗證庫存變化

預期結果：庫存數量正確反映異動
```

### 4.4 異常流程 / Error Scenarios

**E2E-M16-005: 庫存不足無法出貨**

```
測試步驟：
1. 建立 SKU，設定安全存量
2. 嘗試 OUTBOUND 異動，數量超過庫存

預期結果：回傳錯誤，庫存不變
```

**E2E-M16-006: 無效狀態轉換**

```
測試步驟：
1. 建立採購單
2. 嘗試直接收貨（未提交）

預期結果：回傳錯誤，狀態不變
```

---

## 5. 測試資料準備 / Test Data Setup

### 5.1 前置資料需求

| 資料類型 | 需求說明 | 建立方式 |
|----------|----------|----------|
| Tenant | 測試用 Tenant | @BeforeAll 建立 |
| User (STORE_OWNER) | 有 STORE_OWNER 角色的用戶 | @BeforeAll 建立 |
| User (SELLER) | 有 SELLER 角色的用戶 | @BeforeEach 建立 |
| Supplier | 供應商 | IT-M16-001 建立 |
| Product/Listing | 商品 Listing | @BeforeAll 建立 |
| SKU | 庫存單位 (ProductInventory) | @BeforeAll 建立 |
| Inventory | 庫存記錄 | @BeforeAll 建立 |

### 5.2 測試資料隔離策略

- 每個測試類別使用獨立的 Tenant
- 測試結束後清理所有建立的資料
- 使用 `@Transactional` 確保測試間資料隔離（IT Tests）
- E2E Tests 使用 `@AfterEach` 清理

### 5.3 安全存量配置

- 預設安全存量：`10` 單位
- 低庫存警示閾值：低於安全存量觸發

---

## 6. 測試執行順序 / Test Execution Order

### 6.1 IT 測試執行順序

```
Phase 1: 供應商管理（無依賴）
├── IT-M16-001 ~ IT-M16-008

Phase 2: 採購單管理（依賴供應商）
├── IT-M16-101 ~ IT-M16-104（建立類測試）
├── IT-M16-106 ~ IT-M16-107（查詢類測試）
└── IT-M16-108 ~ IT-M16-117（狀態轉換類測試）

Phase 3: 庫存管理（依賴採購單完成後的庫存變化）
├── IT-M16-201 ~ IT-M16-205

Phase 4: 庫存異動（依賴庫存存在）
└── IT-M16-301 ~ IT-M16-306
```

### 6.2 E2E 測試執行順序

```
E2E-M16-001: 完整生命週期（最完整，先執行）
E2E-M16-002: DRAFT 取消流程
E2E-M16-003: SUBMITTED 取消流程
E2E-M16-004: 進貨異動流程
E2E-M16-005: 庫存不足錯誤
E2E-M16-006: 無效狀態轉換錯誤
```

### 6.3 測試間依賴關係圖

```
Supplier 建立
    ↓
PurchaseOrder 建立 ← 依賴 Supplier
    ↓
PO Submit ← 依賴 PO 建立
    ↓
PO Receive ← 依賴 PO Submit
    ↓
Inventory 更新 ← 依賴 PO Receive
    ↓
Inventory Alert 驗證 ← 依賴 Inventory
    ↓
Stock Movement ← 依賴 Inventory
```

---

## 7. 測試覆蓋率目標 / Test Coverage Goals

### 7.1 API Endpoint 覆蓋率

| 端點 | HTTP Method | 測試覆蓋 |
|------|-------------|----------|
| /suppliers | GET | IT-M16-006, IT-M16-007, IT-M16-008 |
| /suppliers | POST | IT-M16-001, IT-M16-002, IT-M16-003 |
| /suppliers/{id} | PUT | IT-M16-004, IT-M16-005 |
| /purchase-orders | GET | IT-M16-104, IT-M16-105 |
| /purchase-orders | POST | IT-M16-101, IT-M16-102, IT-M16-103 |
| /purchase-orders/{id} | GET | IT-M16-106, IT-M16-107 |
| /purchase-orders/{id} | PUT | IT-M16-108, IT-M16-109 |
| /purchase-orders/{id}/submit | PUT | IT-M16-110, IT-M16-111 |
| /purchase-orders/{id}/receive | PUT | IT-M16-112, IT-M16-113, IT-M16-114 |
| /purchase-orders/{id}/cancel | PUT | IT-M16-115, IT-M16-116, IT-M16-117 |
| /inventory | GET | IT-M16-201 |
| /inventory/{skuId} | GET | IT-M16-202, IT-M16-203 |
| /inventory/alerts | GET | IT-M16-204, IT-M16-205 |
| /stock-movements | POST | IT-M16-301, IT-M16-302, IT-M16-303, IT-M16-304, IT-M16-305 |
| /stock-movements | GET | IT-M16-306 |

**目標**: 100% 端點覆蓋

### 7.2 Business Flow 覆蓋率

| Flow | 測試案例 |
|------|----------|
| 供應商 CRUD | IT-M16-001 ~ IT-M16-005, E2E-M16-002 |
| 採購單狀態機 | E2E-M16-001, E2E-M16-002, E2E-M16-003 |
| 庫存更新 | E2E-M16-001 |
| 低庫存警報 | IT-M16-204, E2E-M16-001 |
| 庫存異動 | IT-M16-301 ~ IT-M16-305 |

### 7.3 錯誤碼覆蓋率

| Error Code | 情境 | 測試案例 |
|------------|------|----------|
| E-1001 | 資源不存在 | IT-M16-005, IT-M16-107, IT-M16-203 |
| E-1002 | 參數驗證失敗 | IT-M16-002, IT-M16-003, IT-M16-102, IT-M16-103, IT-M16-304 |
| E-1003 | 狀態轉換無效 | IT-M16-109, IT-M16-111, IT-M16-114, IT-M16-117, E2E-M16-006 |
| E-1004 | 庫存不足 | IT-M16-305, E2E-M16-005 |

---

## 8. 風險與注意事項 / Risks & Notes

### 8.1 已識別風險

| 風險 | 嚴重性 | 緩解措施 |
|------|--------|----------|
| 採購單並發收貨 | 中 | 資料庫鎖定機制驗證 |
| 庫存異動並發 | 中 | 庫存乐观鎖定驗證 |
| 狀態機 race condition | 中 | 使用 `@Version` 樂觀鎖 |

### 8.2 測試環境需求

- PostgreSQL: `localhost:5432/nextkeytest`
- Redis: `localhost:6379` (可用 Mock)
- JWT Secret: 測試用 secret key

### 8.3 執行時間預估

| 測試類別 | 預估時間 |
|----------|----------|
| IT Tests (19 案例) | ~30 秒 |
| E2E Tests (6 案例) | ~45 秒 |
| **總計** | ~75 秒 |

---

## 9. 附件 / Attachments

- [M16_ERP_IT_Test_Implementation.java](./M16_ERP_IT_Test.java) - IT 測試實作
- [M16_ERP_E2E_Test.java](./M16_ERP_E2E_Test.java) - E2E 測試實作

---

**文件狀態**: Draft
**作者**: QA Engineer Agent
**下次審查日期**: 2026-05-10
