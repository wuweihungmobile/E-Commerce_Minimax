# M01 商品管理測試案例 / Product Center Test Cases

> **模組**: M01 商品中心
> **版本**: v1.0
> **建立日期**: 2026-04-10
> **依據**: API_M01_Product_Center.md, SRD_Database_Schema.md, SRD_System_Architecture.md
> **測試框架**: JUnit 5 + Mockito (UT), Spring Boot Test (IT), REST Assured (API)

---

## 📋 測試案例總覽

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 3 | 3 | 2 | 8 |
| IT | 2 | 2 | 1 | 5 |
| API | 3 | 3 | 1 | 7 |
| **合計** | 8 | 8 | 4 | **20** |

---

## 1. 單元測試 (Unit Tests)

### 1.1 Listing 統一抽象邏輯

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M01-001 | Listing類型區分-PRODUCT | P0 | 無 | 1. 建立 listingType = PRODUCT 的 Listing<br>2. 驗證為 Product 類型 | listingType = PRODUCT |
| UT-M01-002 | Listing類型區分-ROOM | P0 | 無 | 1. 建立 listingType = ROOM 的 Listing<br>2. 驗證為 Room 類型 | listingType = ROOM |
| UT-M01-003 | Listing狀態機-有效轉換 | P0 | DRAFT 狀態 | 1. DRAFT → ACTIVE<br>2. ACTIVE → INACTIVE<br>3. 驗證狀態正確 | 狀態轉換成功 |
| UT-M01-004 | Listing狀態機-無效轉換 | P1 | DELETED 狀態 | 1. DELETED → ACTIVE (嘗試) | 拋出 InvalidStateTransitionException |
| UT-M01-005 | Listing分類查詢-依listingType | P1 | 多個不同類型 Listing | 1. 只查詢 listingType = PRODUCT | 只回傳 PRODUCT 類型 |
| UT-M01-006 | Listing分類查詢-依status | P1 | 多個不同狀態 Listing | 1. 只查詢 status = ACTIVE | 只回傳 ACTIVE 狀態 |
| UT-M01-007 | Listing搜尋-關鍵字匹配 | P2 | 含關鍵字商品 | 1. 搜尋 "iPhone" | 回傳標題包含 "iPhone" 的商品 |
| UT-M01-008 | Listing搜尋-多關鍵字 | P2 | 含多關鍵字商品 | 1. 搜尋 "iPhone 15" | 回傳標題包含 "iPhone" 或 "15" 的商品 |

---

## 2. 整合測試 (Integration Tests)

### 2.1 商品上架/編輯/下架

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M01-001 | 商品上架-從草稿發布 | P0 | DRAFT 商品 | 1. PUT /api/v2/dashboard/listings/{id}/publish<br>2. 驗證 status = ACTIVE | 200, status 變為 ACTIVE |
| IT-M01-002 | 商品上架-缺少必填欄位 | P0 | 無 | 1. POST /api/v2/dashboard/listings (缺少 title) | 400 Bad Request |
| IT-M01-003 | 商品上架-basePrice必須大於0 | P1 | 無 | 1. POST /api/v2/dashboard/listings (basePrice = 0) | 400, basePrice 驗證失敗 |
| IT-M01-004 | 商品編輯-更新標題和價格 | P1 | ACTIVE 商品 | 1. PUT /api/v2/dashboard/listings/{id}<br>Body: {"title":"新標題","basePrice":5000} | 200, 標題和價格已更新 |
| IT-M01-005 | 商品下架-改為INACTIVE | P1 | ACTIVE 商品 | 1. PUT /api/v2/dashboard/listings/{id}/status<br>Body: {"status":"INACTIVE"} | 200, status = INACTIVE |

### 2.2 商品搜尋與過濾

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M01-006 | 商品搜尋-依分類 | P1 | 多分類商品 | 1. GET /api/v2/listings?type=PRODUCT&category=3C | 只回傳 3C 分類商品 |
| IT-M01-007 | 商品搜尋-分頁 | P1 | 超過 20 個商品 | 1. GET /api/v2/listings?page=1&limit=10 | 只回傳 10 筆，有 hasNextPage = true |

---

## 3. API E2E 測試 (API E2E Tests)

### 3.1 商品列表 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M01-001 | GET /api/v2/listings-商品列表 | P0 | 有商品資料 | 1. GET /api/v2/listings?type=PRODUCT | 200, data.items 包含商品列表 |
| API-M01-002 | GET /api/v2/listings/:id-商品詳情 | P0 | 有商品 | 1. GET /api/v2/listings/{productId} | 200, data 包含完整商品資訊 |
| API-M01-003 | GET /api/v2/listings/:id-不存在商品 | P1 | 無 | 1. GET /api/v2/listings/invalid-uuid | 404, message="Listing not found" |

### 3.2 商品搜尋 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M01-004 | GET /api/v2/listings/search-關鍵字搜尋 | P0 | 有商品 | 1. GET /api/v2/listings/search?q=iPhone | 200, 回傳包含 "iPhone" 的商品 |
| API-M01-005 | GET /api/v2/listings/search-空關鍵字 | P1 | 有商品 | 1. GET /api/v2/listings/search?q= | 回傳空結果或全量列表 |
| API-M01-006 | GET /api/v2/categories-分類列表 | P1 | 有商品 | 1. GET /api/v2/categories | 200, 回傳所有分類 |

### 3.3 店鋪商品管理 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M01-007 | POST /api/v2/dashboard/listings-建立商品 | P0 | StoreOwner 登入 | 1. POST /api/v2/dashboard/listings<br>Body: {"listingType":"PRODUCT","title":"新商品","basePrice":1000} | 201, data.status = DRAFT |
| API-M01-008 | PUT /api/v2/dashboard/listings/:id-更新商品 | P1 | StoreOwner + 自有商品 | 1. PUT /api/v2/dashboard/listings/{id}<br>Body: {"title":"更新標題"} | 200, 標題已更新 |
| API-M01-009 | DELETE /api/v2/dashboard/listings/:id-下架商品 | P1 | StoreOwner + 自有商品 | 1. DELETE /api/v2/dashboard/listings/{id} | 200, status = DELETED |
| API-M01-010 | PUT /api/v2/dashboard/listings/:id/publish-發布商品 | P0 | DRAFT 商品 | 1. PUT /api/v2/dashboard/listings/{id}/publish | 200, status = ACTIVE |

---

## 4. 商品狀態機測試

### 4.1 狀態流轉驗證

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| STATE-001 | DRAFT → ACTIVE 轉換 | P0 | DRAFT 狀態 | 1. 發布商品 | status = ACTIVE, publishedAt 更新 |
| STATE-002 | ACTIVE → INACTIVE 轉換 | P0 | ACTIVE 狀態 | 1. 下架商品 | status = INACTIVE |
| STATE-003 | INACTIVE → ACTIVE 轉換 | P1 | INACTIVE 狀態 | 1. 重新上架 | status = ACTIVE |
| STATE-004 | 任何狀態 → DELETED 轉換 | P1 | 任意狀態 | 1. 刪除商品 | status = DELETED，軟刪除 |
| STATE-005 | DELETED 無法轉換至其他狀態 | P2 | DELETED 狀態 | 1. 嘗試發布或上架 | 拋出 InvalidStateTransitionException |

---

## 📝 關鍵驗證點總結

| 驗證項目 | 測試案例 | 優先級 |
|---------|---------|--------|
| Listing Type (PRODUCT/ROOM) 正確區分 | UT-M01-001, UT-M01-002 | P0 |
| 商品狀態機: DRAFT → ACTIVE → INACTIVE → DELETED | STATE-001, STATE-002, STATE-003, STATE-004 | P0 |
| 商品搜尋與分類過濾 | IT-M01-006, API-M01-001, API-M01-004 | P0 |
| Feature Toggle RETAIL_ENABLED 檢查 | (見 TC_M17_Tenant.md) | P0 |

---

## 📝 Listing 狀態流轉圖

```
       DRAFT ──────► ACTIVE ──────► INACTIVE
         │             │               │
         ▼             ▼               ▼
      DELETED       DELETED         DELETED
```

---

**文件版本**: AISDLC v0.09
**測試框架**: JUnit 5 + Mockito, Spring Boot Test, REST Assured
**最後更新**: 2026-04-10
