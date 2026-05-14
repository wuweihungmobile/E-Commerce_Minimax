# M04 購物車模組詳細技術設計 / M04 Cart Module Technical Design

> **文件版本**: v1.0
> **建立日期**: 2026-05-13
> **Sprint**: Sprint 11 (2026-06-01 ~ 2026-06-12)
> **負責人**: SA (Amanda)
> **Framework**: AISDLC v0.09

---

## 1. 技術可行性評估 / Technical Feasibility Assessment

### 1.1 架構影響評估

| 評估項目 | 等級 | 說明 |
|---------|------|------|
| 後端架構變更 | **低** | 現有 Redis Hash 機制已存在 (`RedisCartService`)，僅需擴展功能 |
| 前端架構變更 | **中** | 新增 `/cart` 頁面及相關元件，需與現有 listing 頁面整合 |
| 資料庫變更 | **低** | Redis 操作無需 schema 變更；優惠券機制暫不影響 DB |
| 第三方依賴 | **無** | 僅使用現有 Redis 服務，無新增外部依賴 |

### 1.2 技術風險評估

| 風險 ID | 風險描述 | 等級 | 緩解措施 |
|--------|----------|------|----------|
| R-M11-001 | Redis 連線失敗導致購物車無法使用 | **中** | 實作 fallback 機制：Redis 不可用時回傳空購物車並記錄警告 |
| R-M11-002 | 高併發下 Redis Hash 操作效能瓶頸 | **低** | 使用 Redis Pipeline 批量操作；購物車 TTL 30 天控制資料量 |
| R-M11-003 | 優惠券驗證邏輯複雜度 | **中** | Phase 1 先實作簡單折扣計算，進階規則 (疊加/互斥) 延後 |
| R-M11-004 | 跨時區日期計算錯誤 | **低** | 所有日期使用 UTC 儲存，前端轉換為在地時區顯示 |

### 1.3 實作建議

1. **復用現有程式碼**：現有 `RedisCartService.java` 已實作核心 CRUD，僅需擴展優惠券功能
2. **Redis Key 結構維持**：使用 `cart:{tenant_id}:{user_id}` 格式，確保多租戶隔離
3. **庫存連動推遲**：加入購物車不扣庫存，僅結帳時 (POST /api/v2/orders) 扣減
4. **API 版本管理**：使用 `/api/v2/cart` 前綴，與 Phase 1 API 區分

---

## 2. 詳細技術設計 / Detailed Technical Design

### 2.1 Redis Hash 操作流程

#### 2.1.1 購物車資料結構

```
Redis Key: cart:{tenant_id}:{user_id}
Type: Hash
TTL: 30 days (未登入 7 天)

Hash Fields:
├── cartId: UUID (購物車唯一識別)
├── items: JSON Array (購物車項目列表)
├── appliedPromoCode: String (已套用優惠券代碼)
├── totalAmount: Integer (購物車總金額，單位：分)
├── createdAt: Timestamp (建立時間)
└── updatedAt: Timestamp (最後更新時間)
```

#### 2.1.2 購物車項目結構 (CartItemData)

```json
{
  "itemKey": "listingId[:skuId[:startDate:endDate]]",
  "listingId": "uuid",
  "skuId": "uuid (optional)",
  "skuCode": "string (optional)",
  "specName": "string (optional)",
  "title": "商品名稱",
  "coverImageUrl": "string",
  "quantity": 1,
  "unitPrice": 1500,
  "subtotal": 1500,
  "listingType": "PRODUCT | ROOM",
  "addedAt": "2026-06-01T10:30:00Z",
  "startDate": "2026-06-01 (ROOM only)",
  "endDate": "2026-06-03 (ROOM only)"
}
```

#### 2.1.3 Redis 操作流程圖

```
┌─────────────────────────────────────────────────────────────────┐
│                        加入購物車流程                            │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  1. POST /api/v2/cart/items                                     │
│         │                                                        │
│         ▼                                                        │
│  2. 驗證 JWT + 取得 userId/tenantId                              │
│         │                                                        │
│         ▼                                                        │
│  3. 檢查 Listing 是否存在且可售 (status = ACTIVE)                │
│         │                                                        │
│         ▼                                                        │
│  4. 解析 cartItemKey = listingId[:skuId[:startDate:endDate]]   │
│         │                                                        │
│         ▼                                                        │
│  5. HGET cart:{tid}:{uid} {itemKey}  檢查是否已存在               │
│         │                                                        │
│         ▼                                                        │
│  6. 若存在：quantity += newQuantity                             │
│     若不存在：新建 CartItemData                                  │
│         │                                                        │
│         ▼                                                        │
│  7. HSET cart:{tid}:{uid} {itemKey} {CartItemData JSON}         │
│         │                                                        │
│         ▼                                                        │
│  8. EXPIRE cart:{tid}:{uid} 86400 (30 days)                     │
│         │                                                        │
│         ▼                                                        │
│  9. 計算 totalItemsInCart (所有 quantity 總和)                   │
│         │                                                        │
│         ▼                                                        │
│  10. 回傳 AddItemResponse                                        │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 CartService 類設計

#### 2.2.1 服務介面

```java
// 位置: backend/src/main/java/com/nextkey/ecommerce/core/cart/CartService.java
public interface CartService {
    // 購物車 CRUD
    CartDto.CartResponse getCart(UUID userId, UUID tenantId);
    CartDto.AddItemResponse addItem(UUID userId, UUID tenantId, CartDto.AddItemRequest request);
    CartDto.CartItemResponse updateItem(UUID userId, UUID tenantId, String cartItemKey, int quantity);
    void removeItem(UUID userId, UUID tenantId, String cartItemKey);
    void clearCart(UUID userId, UUID tenantId);
    int getCartItemCount(UUID userId, UUID tenantId);

    // 優惠券功能
    CartDto.CartResponse applyPromoCode(UUID userId, UUID tenantId, String promoCode);
    CartDto.CartResponse removePromoCode(UUID userId, UUID tenantId);
    CartDto.PromoValidationResult validatePromoCode(String promoCode, UUID tenantId);
}
```

#### 2.2.2 RedisCartService 方法簽名 (現有擴展)

```java
// 位置: backend/src/main/java/com/nextkey/ecommerce/core/cart/RedisCartService.java

/**
 * 加入購物車
 * @param userId 用戶 ID
 * @param tenantId 租戶 ID
 * @param request 加入購物車請求 (listingId, skuId, quantity, startDate, endDate)
 * @return AddItemResponse 包含成功標記、項目資料、購物車總項目數
 */
CartDto.AddItemResponse addItem(UUID userId, UUID tenantId, CartDto.AddItemRequest request);

/**
 * 更新購物車項目數量
 * @param userId 用戶 ID
 * @param tenantId 租戶 ID
 * @param cartItemKey 購物車項目鍵 (格式: listingId[:skuId[:startDate:endDate]])
 * @param quantity 新數量 (1-999)
 * @return CartItemResponse 更新後的項目資料
 * @throws CartItemNotFoundException 項目不存在
 * @throws IllegalArgumentException 數量超出範圍
 */
CartDto.CartItemResponse updateItem(UUID userId, UUID tenantId, String cartItemKey, int quantity);

/**
 * 移除購物車項目
 * @param userId 用戶 ID
 * @param tenantId 租戶 ID
 * @param cartItemKey 購物車項目鍵
 * @throws CartItemNotFoundException 項目不存在
 */
void removeItem(UUID userId, UUID tenantId, String cartItemKey);

/**
 * 清空購物車
 * @param userId 用戶 ID
 * @param tenantId 租戶 ID
 */
void clearCart(UUID userId, UUID tenantId);

/**
 * 取得購物車內容
 * @param userId 用戶 ID
 * @param tenantId 租戶 ID
 * @return CartResponse 購物車完整內容
 */
CartDto.CartResponse getCart(UUID userId, UUID tenantId);

/**
 * 套用優惠券
 * @param userId 用戶 ID
 * @param tenantId 租戶 ID
 * @param promoCode 優惠券代碼
 * @return CartResponse 更新後的購物車內容
 * @throws PromoCodeInvalidException 優惠券無效或已過期
 */
CartDto.CartResponse applyPromoCode(UUID userId, UUID tenantId, String promoCode);
```

### 2.3 CartController API 規格

#### 2.3.1 API Endpoints 總覽

| 方法 | 路徑 | 說明 | 認證 | 預期狀態碼 |
|------|------|------|------|------------|
| GET | `/api/v2/cart` | 取得購物車 | BUYER | 200 |
| POST | `/api/v2/cart/items` | 加入商品 | BUYER | 201 |
| PUT | `/api/v2/cart/items/{cartItemKey}` | 更新數量 | BUYER | 200 |
| DELETE | `/api/v2/cart/items/{cartItemKey}` | 移除項目 | BUYER | 200 |
| DELETE | `/api/v2/cart` | 清空購物車 | BUYER | 200 |
| POST | `/api/v2/cart/apply-promo` | 套用優惠券 | BUYER | 200 |
| DELETE | `/api/v2/cart/promo` | 移除優惠券 | BUYER | 200 |
| GET | `/api/v2/cart/validate-promo` | 驗證優惠券 | BUYER | 200 |

#### 2.3.2 詳細 API 規格

##### GET /api/v2/cart

**Request Headers:**
```
Authorization: Bearer {access_token}
```

**Response (200 OK):**
```json
{
  "code": 200,
  "message": "Cart retrieved",
  "data": {
    "userId": "550e8400-e29b-41d4-a716-446655440099",
    "cartId": "cart:550e8400-e29b-41d4-a716-446655440099:550e8400-e29b-41d4-a716-446655440001",
    "items": [
      {
        "cartItemKey": "550e8400-e29b-41d4-a716-446655440000:550e8400-e29b-41d4-a716-446655440001:2026-06-01:2026-06-03",
        "listingId": "550e8400-e29b-41d4-a716-446655440000",
        "listingName": "精緻雙人房",
        "coverImageUrl": "https://example.com/images/room-001.jpg",
        "skuId": "550e8400-e29b-41d4-a716-446655440001",
        "skuCode": "ROOM-STD-001",
        "specName": "標準入住",
        "quantity": 2,
        "unitPrice": 1500.00,
        "subtotal": 3000.00,
        "listingType": "ROOM",
        "addedAt": "2026-05-13T10:30:00Z",
        "startDate": "2026-06-01",
        "endDate": "2026-06-03"
      }
    ],
    "totalItems": 2,
    "totalAmount": 3000.00,
    "appliedPromoCode": null,
    "discountAmount": 0,
    "finalAmount": 3000.00,
    "currency": "TWD",
    "updatedAt": "2026-05-13T10:30:00Z"
  }
}
```

##### POST /api/v2/cart/items

**Request Body:**
```json
{
  "listingId": "550e8400-e29b-41d4-a716-446655440000",
  "skuId": "550e8400-e29b-41d4-a716-446655440001",
  "quantity": 2,
  "startDate": "2026-06-01",
  "endDate": "2026-06-03"
}
```

**Response (201 Created):**
```json
{
  "code": 201,
  "message": "Item added to cart",
  "data": {
    "success": true,
    "item": {
      "cartItemKey": "550e8400-e29b-41d4-a716-446655440000:550e8400-e29b-41d4-a716-446655440001:2026-06-01:2026-06-03",
      "listingId": "550e8400-e29b-41d4-a716-446655440000",
      "listingName": "精緻雙人房",
      "quantity": 2,
      "unitPrice": 1500.00,
      "subtotal": 3000.00,
      "listingType": "ROOM"
    },
    "totalItemsInCart": 2,
    "message": "Item added to cart successfully"
  }
}
```

##### PUT /api/v2/cart/items/{cartItemKey}

**Request Body:**
```json
{
  "quantity": 3
}
```

**Response (200 OK):**
```json
{
  "code": 200,
  "message": "Item quantity updated",
  "data": {
    "cartItemKey": "550e8400-e29b-41d4-a716-446655440000:550e8400-e29b-41d4-a716-446655440001:2026-06-01:2026-06-03",
    "listingId": "550e8400-e29b-41d4-a716-446655440000",
    "listingName": "精緻雙人房",
    "quantity": 3,
    "unitPrice": 1500.00,
    "subtotal": 4500.00,
    "listingType": "ROOM"
  }
}
```

##### POST /api/v2/cart/apply-promo

**Request Body:**
```json
{
  "promoCode": "SUMMER2026"
}
```

**Response (200 OK):**
```json
{
  "code": 200,
  "message": "Promo code applied",
  "data": {
    "appliedPromoCode": "SUMMER2026",
    "discountAmount": 300,
    "finalAmount": 2700.00,
    "discountType": "PERCENTAGE",
    "discountValue": 10
  }
}
```

### 2.4 錯誤處理策略

#### 2.4.1 錯誤碼對照表

| HTTP Status | Error Code | 說明 | 處置方式 |
|-------------|------------|------|----------|
| 400 | E-5004 | Cart is empty (優惠券套用時購物車為空) | 提示用戶先加入商品 |
| 400 | E-5005 | Cart item not found | 檢查 cartItemKey 是否正確 |
| 400 | E-5006 | Invalid quantity (不在 1-999 範圍) | 重新輸入有效數量 |
| 400 | E-5007 | Promo code invalid | 顯示「優惠券無效」提示 |
| 400 | E-5008 | Promo code expired | 顯示「優惠券已過期」提示 |
| 400 | E-5009 | Promo code usage limit reached | 顯示「優惠券已兌換完畢」提示 |
| 401 | E-1000 | Authentication required | 重新登入取得 token |
| 403 | E-1007 | Insufficient permissions | 聯繫客服 |
| 404 | E-3000 | Listing not found | 檢查商品是否已下架 |
| 404 | E-4003 | Invalid date range | ROOM 類型需提供正確日期範圍 |
| 404 | E-4004 | Check-out must be after check-in | 退房日期需晚於入住日期 |
| 503 | E-9902 | Redis error | 顯示「系統忙碌，請稍後再試」 |

#### 2.4.2 例外類別設計

```java
// 位置: backend/src/main/java/com/nextkey/ecommerce/shared/exception/

public class CartItemNotFoundException extends RuntimeException {
    private final String cartItemKey;
    public CartItemNotFoundException(String cartItemKey) { ... }
}

public class PromoCodeInvalidException extends RuntimeException {
    private final String promoCode;
    private final String reason; // INVALID, EXPIRED, USAGE_LIMIT, etc.
    public PromoCodeInvalidException(String promoCode, String reason) { ... }
}

public class CartEmptyException extends RuntimeException { ... }
```

---

## 3. 依賴關係 / Dependencies

### 3.1 前置依賴（需先完成）

| 模組 | 服務 | 說明 | 預計完成 Sprint |
|------|------|------|----------------|
| M01 | ListingService | 商品/房源查詢驗證 | Sprint 3-4 ✅ |
| M03 | AuthService | JWT 認證過濾 | Sprint 3 ✅ |
| M12 | PricingService | 動態定價查詢 (unitPrice) | Sprint 8 ✅ |
| — | Redis Service | Redis 連線與操作 | Sprint 10 ✅ |

### 3.2 被依賴（此模組提供）

| 模組 | 服務 | 說明 |
|------|------|------|
| M05 | OrderService | 結帳時讀取購物車內容 |
| M07 | PaymentService | 顯示折扣後金額 |
| FE | Cart Page | 前端購物車頁面 |

### 3.3 依賴關係圖

```
依賴來源：
M01 (ListingRepository) ──→ M04 (CartService) ──→ M05 (OrderService)
M03 (Auth)                │                    │
M12 (PricingService)       │                    │
Redis                     │                    ▼
                          │               被依賴
                          ▼
                     Frontend (Cart Page)
```

---

## 4. User Story 細化 / User Story Breakdown

### 4.1 US-M11-001: 購物車 CRUD 與庫存連動

**原始描述**：BE: 購物車 CRUD 與庫存連動

**細化後的驗收條件 (AC)**：

| AC ID | 條件描述 | 優先級 |
|-------|----------|--------|
| AC-M11-001-01 | 用戶可將 PRODUCT 類型商品加入購物車 | P0 |
| AC-M11-001-02 | 用戶可將 ROOM 類型房源加入購物車，需提供日期範圍 | P0 |
| AC-M11-001-03 | 相同商品再次加入時，數量累加而非覆蓋 | P0 |
| AC-M11-001-04 | 用戶可檢視購物車完整內容 (items, totalAmount) | P0 |
| AC-M11-001-05 | 用戶可更新購物車項目數量 (1-999) | P0 |
| AC-M11-001-06 | 用戶可移除購物車項目 | P0 |
| AC-M11-001-07 | 用戶可清空購物車 | P0 |
| AC-M11-001-08 | 加入購物車時不檢查/扣減庫存，僅驗證商品可售 | P0 |
| AC-M11-001-09 | 購物車資料依 userId + tenantId 隔離 | P0 |
| AC-M11-001-10 | 購物車 TTL 為 30 天 | P1 |

**技術約束**：

```
Constraint-M11-001: 購物車資料使用 Redis Hash 儲存
Constraint-M11-002: cartItemKey 格式為 listingId[:skuId[:startDate:endDate]]
Constraint-M11-003: quantity 範圍為 1-999
Constraint-M11-004: ROOM 類型必須提供 startDate 和 endDate，且 endDate > startDate
```

---

### 4.2 US-M11-002: 優惠券驗證與折扣計算

**原始描述**：BE: 優惠券驗證與折扣計算

**細化後的驗收條件 (AC)**：

| AC ID | 條件描述 | 優先級 |
|-------|----------|--------|
| AC-M11-002-01 | 用戶可輸入優惠券代碼並套用至購物車 | P0 |
| AC-M11-002-02 | 系統驗證優惠券有效性 (存在、未過期、未超使用次數) | P0 |
| AC-M11-002-03 | 套用成功後顯示折扣金額和最終金額 | P0 |
| AC-M11-002-04 | 用戶可移除已套用的優惠券 | P0 |
| AC-M11-002-05 | 優惠券驗證失敗時回傳明確錯誤訊息 | P0 |
| AC-M11-002-06 | 購物車套用優惠券後總金額需重新計算 | P0 |

**折扣類型支援**：

| 類型 | 公式 | 優先級 |
|------|------|--------|
| PERCENTAGE | finalAmount = totalAmount * (1 - discount/100) | P0 |
| FIXED_AMOUNT | finalAmount = totalAmount - discount | P1 |
| FREE_SHIPPING | 免除運費 (需 M11 物流整合) | P2 |

**技術約束**：

```
Constraint-M11-005: 優惠券代碼不區分大小寫
Constraint-M11-006: 同一購物車同時只能套用一組優惠券
Constraint-M11-007: 優惠券驗證需與促銷活動資料庫同步
```

---

## 5. 任務拆分建議 / Task Breakdown

### 5.1 Task-M11-101: Backend 購物車 Service (Redis Hash)

**Story Points**: 3 SP
**優先級**: P0
**負責人**: Dev

**預估工時**: 3 days

**任務內容**：

| 子任務 | 說明 | 預估 |
|--------|------|------|
| 5.1.1 | 擴展 RedisCartService.addItem() 支援 ROOM 日期驗證 | 0.5 day |
| 5.1.2 | 實作 CartService.applyPromoCode() 優惠券套用邏輯 | 1 day |
| 5.1.3 | 實作 CartService.validatePromoCode() 驗證邏輯 | 0.5 day |
| 5.1.4 | 建立優惠券資料模型 (PromoCode entity) | 0.5 day |
| 5.1.5 | 單元測試 (RedisCartServiceTest) | 0.5 day |

**交付物**：
- 擴展後的 `RedisCartService.java`
- 新增 `PromoCodeRepository.java`
- 新增 `PromoService.java`
- `RedisCartServiceTest.java`

**驗收標準**：
- [ ] addItem 可正確處理 PRODUCT 和 ROOM 類型
- [ ] applyPromoCode 折扣計算正確
- [ ] validatePromoCode 回傳正確狀態
- [ ] 單元測試覆蓋率 > 80%

---

### 5.2 Task-M11-102: Backend 購物車 API CRUD

**Story Points**: 2 SP
**優先級**: P0
**負責人**: Dev

**預估工時**: 2 days

**任務內容**：

| 子任務 | 說明 | 預估 |
|--------|------|------|
| 5.2.1 | 擴展 CartController 新增優惠券端點 | 0.5 day |
| 5.2.2 | 實作 POST /api/v2/cart/apply-promo | 0.5 day |
| 5.2.3 | 實作 DELETE /api/v2/cart/promo | 0.25 day |
| 5.2.4 | 實作 GET /api/v2/cart/validate-promo | 0.25 day |
| 5.2.5 | 更新 CartDto 新增優惠券相關 DTO | 0.25 day |
| 5.2.6 | 更新 GlobalExceptionHandler 新增優惠券例外處理 | 0.25 day |
| 5.2.7 | 整合測試 | 0.5 day |

**交付物**：
- 擴展後的 `CartController.java`
- 更新後的 `CartDto.java`
- 更新後的 `GlobalExceptionHandler.java`
- `CartControllerIT.java` (整合測試)

**API Endpoints**：

| 方法 | 路徑 | 說明 |
|------|------|------|
| POST | `/api/v2/cart/apply-promo` | 套用優惠券 |
| DELETE | `/api/v2/cart/promo` | 移除優惠券 |
| GET | `/api/v2/cart/validate-promo?code={code}` | 驗證優惠券 |

**驗收標準**：
- [ ] 所有優惠券 API 可正常運作
- [ ] 錯誤回傳符合 Error Code 表格
- [ ] 整合測試通過

---

### 5.3 Task-M11-103: Backend 優惠券驗證模組 (建議延後)

**Story Points**: 3 SP
**優先級**: P1
**說明**：Phase 1 可先實作基本版本，完整驗證模組可於 Phase 2 實作

---

## 6. 測試策略建議 / Testing Strategy

### 6.1 單元測試 (Unit Test)

| 測試類別 | 測試目標 | 覆蓋率目標 |
|---------|----------|------------|
| RedisCartServiceTest | addItem, updateItem, removeItem, getCart, applyPromoCode | > 80% |
| PromoCodeValidationTest | validatePromoCode, computeDiscount | 100% |

### 6.2 整合測試 (Integration Test)

| 測試案例 ID | 說明 |
|-------------|------|
| TC-M11-001 | 加入 PRODUCT 到購物車 |
| TC-M11-002 | 加入 ROOM 到購物車 (含日期) |
| TC-M11-003 | 更新購物車項目數量 |
| TC-M11-004 | 移除購物車項目 |
| TC-M11-005 | 清空購物車 |
| TC-M11-006 | 套用有效優惠券 |
| TC-M11-007 | 套用過期優惠券 (預期失敗) |
| TC-M11-008 | 多租戶隔離驗證 |

---

## 7. 風險與限制 / Risks & Constraints

### 7.1 已知限制

| 限制 | 說明 | 影響 |
|------|------|------|
| Phase 1 無法疊加多組優惠券 | 同一時間只能套用一組優惠券 | 中 |
| 優惠券無法跨租戶使用 | 優惠券僅對發放 tenant 有效 | 低 |
| Redis 單點故障 | 目前無 Redis Cluster | 中 |

### 7.2 緩解措施

| 風險 | 緩解措施 |
|------|----------|
| Redis 單點故障 | 監控 Redis 健康狀態；必要時降級至空購物車 |
| 優惠券並發兑換 | 使用 Redis INCR 實現原子計數 |

---

## 8. 文件追蹤 / Document Traceability

### 8.1 相關文件

| 文件 | 路徑 |
|------|------|
| PRD v1.0 | `docs/01_requirements/E-Commerce_PRD_v1.0_Final.md` |
| FRD | `docs/01_requirements/E-Commerce_FRD_v1.0.md` |
| M04 API 規格 | `docs/02_architecture/API_M04_Cart.md` |
| Sprint 11 Plan | `docs/04_planning/SPRINT_11_PLAN.md` |
| TC M04 Cart | `docs/03_testing/TC_M04_Cart.md` |

### 8.2 追蹤鏈

```
PRD v1.0 §6.3 M04 購物車與促銷
    ↓
Sprint 11 Plan → US-M11-001, US-M11-002
    ↓
本文檔 (SDD_M04_Cart_Technical_Design.md)
    ↓
RedisCartService.java (Implementation)
    ↓
TC_M04_Cart.md (Testing)
```

---

**文件版本**: AISDLC v0.09
**最後更新**: 2026-05-13

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 | 確認人 |
|------|------|----------|--------|
| v1.0 | 2026-05-13 | 初始版本（M04 購物車 Sprint 11 技術設計） | SA (Amanda) |