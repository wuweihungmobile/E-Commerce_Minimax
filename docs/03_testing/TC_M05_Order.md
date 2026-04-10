# M05 訂單管理測試案例 / Order Management Test Cases

> **模組**: M05 訂單履約系統
> **版本**: v1.0
> **建立日期**: 2026-04-10
> **依據**: API_M05_Order.md, SRD_Database_Schema.md, SRD_System_Architecture.md
> **測試框架**: JUnit 5 + Mockito (UT), Spring Boot Test (IT), REST Assured (API)

---

## 📋 測試案例總覽

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 4 | 3 | 2 | 9 |
| IT | 3 | 3 | 1 | 7 |
| API | 3 | 3 | 2 | 8 |
| **合計** | 10 | 9 | 5 | **24** |

---

## 1. 單元測試 (Unit Tests)

### 1.1 訂單狀態機邏輯

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M05-001 | 訂單狀態機-CREATED→PAID轉換 | P0 | 訂單狀態 = CREATED | 1. 模擬付款完成事件 | 狀態變為 PAID |
| UT-M05-002 | 訂單狀態機-PAID→SHIPPING轉換 | P0 | 訂單狀態 = PAID | 1. 觸發出貨 | 狀態變為 SHIPPING |
| UT-M05-003 | 訂單狀態機-SHIPPING→DELIVERED轉換 | P0 | 訂單狀態 = SHIPPING | 1. 觸發送達確認 | 狀態變為 DELIVERED |
| UT-M05-004 | 訂單狀態機-DELIVERED→COMPLETED轉換 | P0 | 訂單狀態 = DELIVERED | 1. 買家確認完成 | 狀態變為 COMPLETED |
| UT-M05-005 | 訂單狀態機-無效轉換CREATED→COMPLETED | P1 | 訂單狀態 = CREATED | 1. 嘗試直接完成 | 拋出 InvalidStateTransitionException |
| UT-M05-006 | 訂單狀態機-CANCELLED後不可改變 | P1 | 訂單狀態 = CANCELLED | 1. 嘗試任何狀態轉換 | 拋出 InvalidStateTransitionException |
| UT-M05-007 | 訂單狀態機-退款流程 | P1 | 訂單狀態 = DELIVERED | 1. 買家申請退款 | 狀態變為 REFUNDING → REFUNDED |
| UT-M05-008 | 訂單狀態機-取消並回滾庫存 | P2 | 訂單狀態 = CREATED | 1. 取消訂單<br>2. 驗證庫存已回滾 | 狀態 CANCELLED，庫存恢復 |

### 1.2 庫存扣減邏輯 (樂觀鎖)

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M05-009 | 庫存扣減-成功扣減 | P0 | 庫存 = 10 | 1. 扣減庫存 3 | 庫存 = 7，version + 1 |
| UT-M05-010 | 庫存扣減-庫存不足 | P0 | 庫存 = 5 | 1. 嘗試扣減庫存 10 | 拋出 InsufficientInventoryException |
| UT-M05-011 | 庫存扣減-併發衝突 | P1 | 兩個同時扣減請求 | 1. 第一個成功<br>2. 第二個因 version 不符失敗 | 第一個成功，第二個需重試或失敗 |
| UT-M05-012 | 庫存扣減-預留數量更新 | P2 | 訂單建立中 | 1. 建立訂單時預留庫存 | reserved_quantity 增加 |

---

## 2. 整合測試 (Integration Tests)

### 2.1 建立訂單流程

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M05-001 | 建立訂單-成功 | P0 | Buyer 登入 + 商品有庫存 | 1. POST /api/v2/orders<br>Body: {"items":[{"listingId":"xxx","quantity":2}]} | 201, status = CREATED (= PAID) |
| IT-M05-002 | 建立訂單-庫存不足 | P0 | 商品庫存 = 3 | 1. POST /api/v2/orders (quantity = 10) | 422, INSUFFICIENT_INVENTORY |
| IT-M05-003 | 建立訂單-併發超賣防護 | P0 | 商品庫存 = 5 | 1. 同時發送 2 個訂單請求 (各 3 件) | 一個成功，一個失敗 |
| IT-M05-004 | 建立訂單-Idempotency Key防重 | P1 | 無 | 1. POST /api/v2/orders (相同 Idempotency Key 兩次) | 只建立一個訂單 |
| IT-M05-005 | 建立訂單-商品不存在 | P1 | 無 | 1. POST /api/v2/orders (listingId = invalid) | 404, Listing not found |

### 2.2 取消訂單 + 回滾

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M05-006 | 取消訂單-CREATED狀態 | P0 | 狀態 = CREATED 訂單 | 1. POST /api/v2/orders/{id}/cancel | 200, status = CANCELLED, 庫存已回滾 |
| IT-M05-007 | 取消訂單-SHIPPING狀態不可取消 | P0 | 狀態 = SHIPPING 訂單 | 1. POST /api/v2/orders/{id}/cancel | 400, Cannot cancel |
| IT-M05-008 | 取消訂單-DELIVERED狀態不可取消 | P1 | 狀態 = DELIVERED 訂單 | 1. POST /api/v2/orders/{id}/cancel | 400, 需走退款流程 |
| IT-M05-009 | 取消訂單-非訂單擁有者 | P1 | 其他 Buyer 的訂單 | 1. 以其他Buyer嘗試取消 | 403 Forbidden |

### 2.3 訂單狀態更新

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M05-010 | 更新訂單狀態-賣家出貨 | P0 | 狀態 = PAID/CREATED | 1. PUT /api/v2/dashboard/orders/{id}/status<br>Body: {"status":"SHIPPING"} | 200, status = SHIPPING |
| IT-M05-011 | 更新訂單狀態-買家確認完成 | P1 | 狀態 = DELIVERED | 1. PUT /api/v2/orders/{id}/status<br>Body: {"status":"COMPLETED"} | 200, status = COMPLETED |
| IT-M05-012 | 更新訂單狀態-無效轉換 | P2 | 狀態 = CREATED | 1. PUT /api/v2/dashboard/orders/{id}/status<br>Body: {"status":"COMPLETED"} | 400, Invalid state transition |

---

## 3. API E2E 測試 (API E2E Tests)

### 3.1 建立訂單 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M05-001 | POST /api/v2/orders-成功 | P0 | Buyer 登入 + 商品有庫存 | 1. POST /api/v2/orders<br>Body: {"items":[{"listingId":"xxx","quantity":1}]} | 201, data.orderNumber 不為 null |
| API-M05-002 | POST /api/v2/orders-缺少必填 | P0 | Buyer 登入 | 1. POST /api/v2/orders (缺少 items) | 400, errors 包含 items 錯誤 |
| API-M05-003 | POST /api/v2/orders-未授權 | P1 | 未登入 | 1. POST /api/v2/orders | 401, Unauthorized |

### 3.2 查詢訂單 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M05-004 | GET /api/v2/orders-買家訂單列表 | P0 | Buyer 登入 | 1. GET /api/v2/orders | 200, data.items 包含買家訂單 |
| API-M05-005 | GET /api/v2/orders/:id-訂單詳情 | P0 | Buyer + 自己的訂單 | 1. GET /api/v2/orders/{orderId} | 200, data 包含完整訂單資訊 |
| API-M05-006 | GET /api/v2/orders/:id-非擁有者 | P1 | Buyer + 其他人的訂單 | 1. GET /api/v2/orders/{otherOrderId} | 403 Forbidden |
| API-M05-007 | GET /api/v2/orders-依狀態篩選 | P1 | 有多狀態訂單 | 1. GET /api/v2/orders?status=CREATED | 只回傳 CREATED 狀態訂單 |

### 3.3 取消訂單 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M05-008 | POST /api/v2/orders/:id/cancel-成功 | P0 | CREATED 訂單 | 1. POST /api/v2/orders/{id}/cancel<br>Body: {"reason":"改變心意"} | 200, data.status = CANCELLED |
| API-M05-009 | POST /api/v2/orders/:id/cancel-SHIPPING狀態 | P0 | SHIPPING 訂單 | 1. POST /api/v2/orders/{id}/cancel | 400, Cannot cancel |
| API-M05-010 | POST /api/v2/orders/:id/cancel-非擁有者 | P1 | 其他人的訂單 | 1. POST /api/v2/orders/{otherId}/cancel | 403 Forbidden |

---

## 4. 訂單狀態機測試

### 4.1 Phase 1 狀態流轉

```
    CREATED (= PAID) ──→ SHIPPING ──→ DELIVERED ──→ COMPLETED
           │                  │              │
           └──→ CANCELLED ←───┘              └──→ REFUNDING ──→ REFUNDED
```

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| FLOW-001 | 完整流程-商品訂購 | P0 | Buyer + 商品 | 1. 建立訂單 → 付款 → 出貨 → 送達 → 完成 | 最終狀態 COMPLETED |
| FLOW-002 | 取消流程-CREATED取消 | P0 | CREATED 訂單 | 1. 取消訂單 | 狀態 CANCELLED，庫存回滾 |
| FLOW-003 | 退款流程-DELIVERED退款 | P1 | DELIVERED 訂單 | 1. 申請退款 → 完成退款 | 狀態 REFUNDED |
| FLOW-004 | 取消限制-SHIPPING不可取消 | P0 | SHIPPING 訂單 | 1. 嘗試取消 | 400, Cannot cancel |
| FLOW-005 | 取消限制-COMPLETED不可取消 | P0 | COMPLETED 訂單 | 1. 嘗試取消 | 400, Cannot cancel |

---

## 📝 關鍵驗證點總結

| 驗證項目 | 測試案例 | 優先級 |
|---------|---------|--------|
| 訂單狀態機轉換正確 | UT-M05-001 ~ UT-M05-007, FLOW-001 ~ FLOW-005 | P0 |
| 並發庫存扣減不超賣 (樂觀鎖) | IT-M05-003, UT-M05-011 | P0 |
| 取消訂單後庫存回滾 | IT-M05-006, UT-M05-008 | P0 |
| Idempotency Key 防重 | IT-M05-004 | P1 |

---

## 📝 訂單狀態流轉圖

```
    CREATED ──────┬──────────────────────┬──────────────┐
        │         │                      │              │
        │         │                      │              ▼
        │         │                      │         CANCELLED
        ▼         │                      │              │
     PAID ◄───────┘                      │              │
        │                                 │              │
        ▼                                 │              │
    SHIPPING ────────────────────────────┼──────────────┤
        │                                 │              │
        ▼                                 │              │
    DELIVERED ────────────────────────────┼──────────────┤
        │                                 │              │
        ├──────────────┬──────────────────┘              │
        ▼              ▼                                 │
   COMPLETED      REFUNDING                              │
        │              ▼                                 │
        │          REFUNDED                              │
        │                                                 │
        ▼                                                 ▼
   ┌─────────────────────────────────────────────────────────────┐
   │                    狀態流轉說明                             │
   ├─────────────────────────────────────────────────────────────┤
   │ CREATED → PAID:     付款完成（Phase 1 MOCK 立即完成）      │
   │ CREATED/PAID → CANCELLED: 買家或管理員取消                │
   │ PAID/SHIPPING → DELIVERED: 物流確認送達                   │
   │ DELIVERED → COMPLETED: 買家確認完成                       │
   │ DELIVERED → REFUNDING: 買家申請退款                       │
   │ REFUNDING → REFUNDED: 退款完成                            │
   └─────────────────────────────────────────────────────────────┘
```

---

**文件版本**: AISDLC v0.09
**測試框架**: JUnit 5 + Mockito, Spring Boot Test, REST Assured
**最後更新**: 2026-04-10
