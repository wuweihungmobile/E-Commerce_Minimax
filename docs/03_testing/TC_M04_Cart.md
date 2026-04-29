# M04 購物車測試案例 / Cart Management Test Cases

> **模組**: M04 購物車
> **版本**: v1.1
> **建立日期**: 2026-04-28
> **更新日期**: 2026-04-28
> **依據**: API_M04_Cart.md, E-Commerce_FRD_v1.0.md, SPRINT_04_PLAN.md
> **測試框架**: JUnit 5 + Mockito (UT), Spring Boot Test (IT), REST Assured (API E2E)
> **Sprint 4 範圍**: US-M04-001 ~ US-M04-004

---

## 📋 測試案例總覽

| 測試類型 | P0 | P1 | P2 | 小計 | Sprint | 狀態 |
|----------|----|----|----|------|--------|------|
| UT | 3 | 2 | 1 | 6 | Sprint 4 | ⏳ 待實現 |
| IT | 4 | 2 | 1 | 7 | Sprint 4 | ⏳ 待實現 |
| API E2E | 5 | 2 | 0 | 7 | Sprint 4 | ⏳ 待實現 |
| NFR | 2 | 0 | 0 | 2 | Sprint 4 | ⏳ 待實現 |
| ISO (多租戶隔離) | 2 | 1 | 0 | 3 | Sprint 4 | ⏳ 待實現 |
| **合計** | 16 | 7 | 2 | **25** | | |

---

## 1. AC → AT 映射表 (Requirements Traceability Matrix)

### US-M04-001: 加入購物車

**驗收標準 (AC)**:
- [ ] AC-M04-001-1: 買家可將商品加入購物車
- [ ] AC-M04-001-2: 同一商品多次加入會累加數量
- [ ] AC-M04-001-3: ROOM 類型不同日期範圍視為不同項目
- [ ] AC-M04-001-4: 非 BUYER 角色無法加入購物車 (回傳 403)

**AC → AT 映射**:
```
- [ ] AC-M04-001-1: 買家可將商品加入購物車
  → AT-M04-001-P0-01 (API E2E) - 基本加入單一商品
  → AT-M04-001-P0-02 (IT) - PRODUCT 類型加入成功
  → AT-M04-001-P0-03 (IT) - ROOM 類型加入成功
  → AT-M04-001-P1-01 (UT) - 商品價格計算正確

- [ ] AC-M04-001-2: 同一商品多次加入會累加數量
  → AT-M04-001-P0-04 (API E2E) - 同一 ListingId 累加數量
  → AT-M04-001-P0-05 (UT) - 數量累加邏輯驗證

- [ ] AC-M04-001-3: ROOM 類型不同日期範圍視為不同項目
  → AT-M04-001-P0-06 (API E2E) - 不同日期視為不同 CartItem
  → AT-M04-001-P1-02 (IT) - ROOM 缺少日期回傳 400

- [ ] AC-M04-001-4: 非 BUYER 角色無法加入購物車
  → AT-M04-001-P0-07 (IT) - STORE_OWNER 回傳 403
  → AT-M04-001-P0-08 (IT) - STAFF 回傳 403
```

---

### US-M04-002: 檢視購物車

**驗收標準 (AC)**:
- [ ] AC-M04-002-1: 買家可查看自己的購物車內容
- [ ] AC-M04-002-2: 購物車包含商品列表、數量、單價

**AC → AT 映射**:
```
- [ ] AC-M04-002-1: 買家可查看自己的購物車內容
  → AT-M04-002-P0-01 (API E2E) - 取得完整購物車
  → AT-M04-002-P0-02 (IT) - 空購物車回傳空陣列
  → AT-M04-002-P1-01 (UT) - CartDto 資料模型驗證

- [ ] AC-M04-002-2: 購物車包含商品列表、數量、單價
  → AT-M04-002-P0-03 (API E2E) - 檢視購物車包含所有欄位
  → AT-M04-002-P0-04 (IT) - 多項目購物車總金額計算
```

---

### US-M04-003: 更新數量

**驗收標準 (AC)**:
- [ ] AC-M04-003-1: 買家可更新商品數量
- [ ] AC-M04-003-2: 數量為 0 時移除商品
- [ ] AC-M04-003-3: 數量上限 999 不再累加

**AC → AT 映射**:
```
- [ ] AC-M04-003-1: 買家可更新商品數量
  → AT-M04-003-P0-01 (API E2E) - 成功更新數量
  → AT-M04-003-P0-02 (IT) - 更新至不同數值
  → AT-M04-003-P1-01 (UT) - 更新數量邏輯驗證

- [ ] AC-M04-003-2: 數量為 0 時移除商品
  → AT-M04-003-P0-03 (IT) - quantity=0 移除商品
  → AT-M04-003-P0-04 (API E2E) - 確認商品已移除

- [ ] AC-M04-003-3: 數量上限 999 不再累加
  → AT-M04-003-P1-02 (UT) - 超過 999 回傳錯誤
  → AT-M04-003-P1-03 (IT) - 數量上限驗證
```

---

### US-M04-004: 移除商品

**驗收標準 (AC)**:
- [ ] AC-M04-004-1: 買家可移除購物車商品
- [ ] AC-M04-004-2: 移除不存在商品回傳 404

**AC → AT 映射**:
```
- [ ] AC-M04-004-1: 買家可移除購物車商品
  → AT-M04-004-P0-01 (API E2E) - 成功移除商品
  → AT-M04-004-P0-02 (IT) - 移除後購物車更新

- [ ] AC-M04-004-2: 移除不存在商品回傳 404
  → AT-M04-004-P0-03 (API E2E) - 不存在商品 ID 回傳 404
  → AT-M04-004-P1-01 (IT) - CartItemNotFoundException 驗證
```

---

## 2. AT 詳細規格 (Acceptance Test Specifications)

### 2.1 US-M04-001 測試案例

#### AT-M04-001-P0-01: 基本加入單一商品 (API E2E)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-001-P0-01 |
| **測試類型** | API E2E |
| **優先級** | P0 |
| **對應 AC** | AC-M04-001-1 |
| **描述** | 買家可將單一商品加入購物車 |

**測試資料 (Test Data)**:
```json
POST /api/v2/cart/items
Authorization: Bearer {BUYER_TOKEN}
Content-Type: application/json

{
  "listingId": "550e8400-e29b-41d4-a716-446655440001",
  "quantity": 2
}
```

**預期結果 (Expected Results)**:
```
Assert 1: response.statusCode == 201
Assert 2: response.json().data.cartItemId != null
Assert 3: response.json().data.listingId == "550e8400-e29b-41d4-a716-446655440001"
Assert 4: response.json().data.quantity == 2
Assert 5: response.json().data.subtotal > 0
Assert 6: response.json().message == "Item added to cart"
```

---

#### AT-M04-001-P0-02: PRODUCT 類型加入成功 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-001-P0-02 |
| **測試類型** | IT (Integration Test) |
| **優先級** | P0 |
| **對應 AC** | AC-M04-001-1 |
| **描述** | PRODUCT 類型 Listing 可成功加入購物車 |

**測試資料 (Test Data)**:
```java
// 假設資料庫有 PRODUCT 類型 Listing
Listing productListing = listingRepository.findById("550e8400-e29b-41d4-a716-446655440001");
assert productListing.getType() == ListingType.PRODUCT;

// 加入購物車
CartAddRequest request = new CartAddRequest();
request.setListingId("550e8400-e29b-41d4-a716-446655440001");
request.setQuantity(1);
```

**預期結果 (Expected Results)**:
```
Assert 1: cartItem.getListingId() == "550e8400-e29b-41d4-a716-446655440001"
Assert 2: cartItem.getQuantity() == 1
Assert 3: cartItem.getStartDate() == null
Assert 4: cartItem.getEndDate() == null
```

---

#### AT-M04-001-P0-03: ROOM 類型加入成功 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-001-P0-03 |
| **測試類型** | IT (Integration Test) |
| **優先級** | P0 |
| **對應 AC** | AC-M04-001-1 |
| **描述** | ROOM 類型 Listing 帶日期可成功加入購物車 |

**測試資料 (Test Data)**:
```java
// 假設資料庫有 ROOM 類型 Listing
Listing roomListing = listingRepository.findById("550e8400-e29b-41d4-a716-446655440002");
assert roomListing.getType() == ListingType.ROOM;

// 加入購物車（需包含日期）
CartAddRequest request = new CartAddRequest();
request.setListingId("550e8400-e29b-41d4-a716-446655440002");
request.setQuantity(1);
request.setStartDate(LocalDate.parse("2026-06-01"));
request.setEndDate(LocalDate.parse("2026-06-03"));
```

**預期結果 (Expected Results)**:
```
Assert 1: cartItem.getListingId() == "550e8400-e29b-41d4-a716-446655440002"
Assert 2: cartItem.getStartDate() == LocalDate.parse("2026-06-01")
Assert 3: cartItem.getEndDate() == LocalDate.parse("2026-06-03")
Assert 4: cartItem.getSubtotal() == unitPrice * 2 (2 晚)
```

---

#### AT-M04-001-P0-04: 同一商品累加數量 (API E2E)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-001-P0-04 |
| **測試類型** | API E2E |
| **優先級** | P0 |
| **對應 AC** | AC-M04-001-2 |
| **描述** | 同一商品多次加入會累加數量 |

**測試資料 (Test Data)**:
```json
// 第一次加入
POST /api/v2/cart/items
{
  "listingId": "550e8400-e29b-41d4-a716-446655440001",
  "quantity": 1
}
// 預期: 201, quantity=1

// 第二次加入相同商品
POST /api/v2/cart/items
{
  "listingId": "550e8400-e29b-41d4-a716-446655440001",
  "quantity": 2
}
// 預期: 200, quantity=3 (累加)
```

**預期結果 (Expected Results)**:
```
Assert 1: firstResponse.statusCode == 201
Assert 2: firstResponse.json().data.quantity == 1

Assert 3: secondResponse.statusCode == 200
Assert 4: secondResponse.json().data.quantity == 3
Assert 5: secondResponse.json().data.cartItemId == firstResponse.json().data.cartItemId
```

---

#### AT-M04-001-P0-05: 數量累加邏輯驗證 (UT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-001-P0-05 |
| **測試類型** | UT (Unit Test) |
| **優先級** | P0 |
| **對應 AC** | AC-M04-001-2 |
| **描述** | 驗證 RedisCartService 數量累加邏輯 |

**測試資料 (Test Data)**:
```java
// Mock Redis CartData
CartData existingCart = new CartData();
existingCart.setUserId("user-123");
CartItemData existingItem = new CartItemData();
existingItem.setListingId("listing-001");
existingItem.setQuantity(1);
existingCart.getItems().add(existingItem);

when(redisTemplate.opsForValue().get("cart:user-123")).thenReturn(existingCart);
```

**預期結果 (Expected Results)**:
```
Assert 1: result.getQuantity() == 3
Assert 2: verify(redisTemplate.opsForValue()).set(eq("cart:user-123"), any())
```

---

#### AT-M04-001-P0-06: ROOM 不同日期視為不同項目 (API E2E)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-001-P0-06 |
| **測試類型** | API E2E |
| **優先級** | P0 |
| **對應 AC** | AC-M04-001-3 |
| **描述** | ROOM 類型不同日期範圍視為不同購物車項目 |

**測試資料 (Test Data)**:
```json
// 加入第一個日期範圍
POST /api/v2/cart/items
{
  "listingId": "550e8400-e29b-41d4-a716-446655440002",
  "quantity": 1,
  "startDate": "2026-06-01",
  "endDate": "2026-06-03"
}
// 預期: 201, cartItemId-1

// 加入不同日期範圍（相同 ListingId）
POST /api/v2/cart/items
{
  "listingId": "550e8400-e29b-41d4-a716-446655440002",
  "quantity": 2,
  "startDate": "2026-06-10",
  "endDate": "2026-06-12"
}
// 預期: 201, cartItemId-2 (視為不同項目)
```

**預期結果 (Expected Results)**:
```
Assert 1: firstResponse.statusCode == 201
Assert 2: secondResponse.statusCode == 201

// 兩者為不同 CartItem
Assert 3: firstResponse.json().data.cartItemId != secondResponse.json().data.cartItemId

// 檢視購物車有兩個項目
GET /api/v2/cart
Assert 4: cart.items.size() == 2
Assert 5: cart.items[0].startDate == "2026-06-01"
Assert 6: cart.items[1].startDate == "2026-06-10"
```

---

#### AT-M04-001-P0-07: STORE_OWNER 無法加入購物車 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-001-P0-07 |
| **測試類型** | IT (Integration Test) |
| **優先級** | P0 |
| **對應 AC** | AC-M04-001-4 |
| **描述** | 非 BUYER 角色（STORE_OWNER）回傳 403 |

**測試資料 (Test Data)**:
```java
// 使用 STORE_OWNER Token
String storeOwnerToken = authService.login("store_owner@test.com", "password");

CartAddRequest request = new CartAddRequest();
request.setListingId("listing-001");
request.setQuantity(1);
```

**預期結果 (Expected Results)**:
```
Assert 1: assertThrows(AccessDeniedException.class, () -> {
    cartService.addItem(storeOwnerToken, request);
})
Assert 2: 驗證回傳 HTTP 403 Forbidden
```

---

#### AT-M04-001-P0-08: STAFF 無法加入購物車 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-001-P0-08 |
| **測試類型** | IT (Integration Test) |
| **優先級** | P0 |
| **對應 AC** | AC-M04-001-4 |
| **描述** | 非 BUYER 角色（STAFF）回傳 403 |

**測試資料 (Test Data)**:
```java
// 使用 STAFF Token
String staffToken = authService.login("staff@test.com", "password");
```

**預期結果 (Expected Results)**:
```
Assert 1: assertThrows(AccessDeniedException.class, () -> {
    cartService.addItem(staffToken, request);
})
```

---

#### AT-M04-001-P1-01: 商品價格計算正確 (UT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-001-P1-01 |
| **測試類型** | UT (Unit Test) |
| **優先級** | P1 |
| **對應 AC** | AC-M04-001-1 |
| **描述** | 驗證 CartItemData.computeSubtotal() 價格計算正確 |

**預期結果 (Expected Results)**:
```
Assert 1: cartItem.computeSubtotal() == 1500.00 (unitPrice=1500, quantity=1)
Assert 2: cartItem.computeSubtotal() == 3000.00 (unitPrice=1500, quantity=2)
```

---

#### AT-M04-001-P1-02: ROOM 缺少日期回傳 400 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-001-P1-02 |
| **測試類型** | IT (Integration Test) |
| **優先級** | P1 |
| **對應 AC** | AC-M04-001-3 |
| **描述** | ROOM 類型缺少 startDate/endDate 時回傳 400 |

**測試資料 (Test Data)**:
```java
CartAddRequest request = new CartAddRequest();
request.setListingId(roomListingId);  // ROOM 類型
request.setQuantity(1);
// 未設定 startDate/endDate
```

**預期結果 (Expected Results)**:
```
Assert 1: assertThrows(IllegalArgumentException.class, () -> {
    cartService.addItem(userToken, request);
})
Assert 2: 異常訊息包含 "Start date and end date are required"
```

---

### 2.2 US-M04-002 測試案例

#### AT-M04-002-P0-01: 取得完整購物車 (API E2E)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-002-P0-01 |
| **測試類型** | API E2E |
| **優先級** | P0 |
| **對應 AC** | AC-M04-002-1 |
| **描述** | 買家可取得完整購物車內容 |

**測試資料 (Test Data)**:
```json
GET /api/v2/cart
Authorization: Bearer {BUYER_TOKEN}
```

**預期結果 (Expected Results)**:
```
Assert 1: response.statusCode == 200
Assert 2: response.json().data.cartId != null
Assert 3: response.json().data.userId != null
Assert 4: response.json().data.items is Array
Assert 5: response.json().data.totalAmount >= 0
```

---

#### AT-M04-002-P0-02: 空購物車回傳空陣列 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-002-P0-02 |
| **測試類型** | IT (Integration Test) |
| **優先級** | P0 |
| **對應 AC** | AC-M04-002-1 |
| **描述** | 空購物車回傳空陣列而非 null |

**測試資料 (Test Data)**:
```java
// 新用戶，尚無購物車項目
String newUserToken = authService.registerAndLogin("new_user@test.com", "password");
```

**預期結果 (Expected Results)**:
```
Assert 1: cart.getItems().isEmpty() == true
Assert 2: cart.getItems() instanceof List
Assert 3: cart.getTotalAmount() == BigDecimal.ZERO
```

---

#### AT-M04-002-P0-03: 檢視購物車包含所有欄位 (API E2E)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-002-P0-03 |
| **測試類型** | API E2E |
| **優先級** | P0 |
| **對應 AC** | AC-M04-002-2 |
| **描述** | 購物車包含商品列表、數量、單價、小計等完整欄位 |

**預期結果 (Expected Results)**:
```
// 每個 item 必須包含:
Assert 1: item.cartItemId != null
Assert 2: item.listingId != null
Assert 3: item.listingName != null
Assert 4: item.quantity > 0
Assert 5: item.unitPrice > 0
Assert 6: item.subtotal == unitPrice * quantity

// Cart 總計
Assert 7: cart.totalAmount == sum(item.subtotal for all items)
```

---

#### AT-M04-002-P0-04: 多項目購物車總金額計算 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-002-P0-04 |
| **測試類型** | IT (Integration Test) |
| **優先級** | P0 |
| **對應 AC** | AC-M04-002-2 |
| **描述** | 多項目購物車總金額正確計算 |

**測試資料 (Test Data)**:
```java
// 加入兩個不同商品
cartService.addItem(token, new CartAddRequest(listingId1, 2));  // subtotal=2000
cartService.addItem(token, new CartAddRequest(listingId2, 1));  // subtotal=1500
```

**預期結果 (Expected Results)**:
```
Assert 1: cart.getItems().size() == 2
Assert 2: cart.getTotalAmount() == 3500
```

---

#### AT-M04-002-P1-01: CartDto 資料模型驗證 (UT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-002-P1-01 |
| **測試類型** | UT (Unit Test) |
| **優先級** | P1 |
| **對應 AC** | AC-M04-002-1 |
| **描述** | CartDto 和 CartItemDto 欄位驗證 |

**預期結果 (Expected Results)**:
```
Assert 1: cartDto.getItems() 返回 List<CartItemDto>
Assert 2: cartItemDto.getSubtotal() == unitPrice * quantity
Assert 3: cartDto.getTotalAmount() == items.stream().map(CartItemDto::getSubtotal).reduce(0, BigDecimal::add)
```

---

### 2.3 US-M04-003 測試案例

#### AT-M04-003-P0-01: 成功更新數量 (API E2E)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-003-P0-01 |
| **測試類型** | API E2E |
| **優先級** | P0 |
| **對應 AC** | AC-M04-003-1 |
| **描述** | 買家可成功更新商品數量 |

**測試資料 (Test Data)**:
```json
PUT /api/v2/cart/items/{cartItemId}
Authorization: Bearer {BUYER_TOKEN}
Content-Type: application/json

{
  "quantity": 5
}
```

**預期結果 (Expected Results)**:
```
Assert 1: response.statusCode == 200
Assert 2: response.json().data.quantity == 5
Assert 3: response.json().data.subtotal == unitPrice * 5
```

---

#### AT-M04-003-P0-02: 更新至不同數值 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-003-P0-02 |
| **測試類型** | IT (Integration Test) |
| **優先級** | P0 |
| **對應 AC** | AC-M04-003-1 |
| **描述** | 數量可從 1 更新至 5，再更新至 3 |

**預期結果 (Expected Results)**:
```
// 更新至 5
Assert 1: updatedItem.getQuantity() == 5

// 再更新至 3
Assert 2: reUpdatedItem.getQuantity() == 3
Assert 3: reUpdatedItem.getSubtotal() == unitPrice * 3
```

---

#### AT-M04-003-P0-03: quantity=0 移除商品 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-003-P0-03 |
| **測試類型** | IT (Integration Test) |
| **優先級** | P0 |
| **對應 AC** | AC-M04-003-2 |
| **描述** | 將商品數量更新為 0 時，商品從購物車移除 |

**測試資料 (Test Data)**:
```json
PUT /api/v2/cart/items/{cartItemId}
{
  "quantity": 0
}
```

**預期結果 (Expected Results)**:
```
Assert 1: updateResponse.statusCode == 200
Assert 2: 驗證商品已從 Redis 移除

// 再次取得購物車
GET /api/v2/cart
Assert 3: cart.items 不包含該 cartItemId
```

---

#### AT-M04-003-P0-04: 確認商品已移除 (API E2E)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-003-P0-04 |
| **測試類型** | API E2E |
| **優先級** | P0 |
| **對應 AC** | AC-M04-003-2 |
| **描述** | 數量為 0 後，確認商品已從購物車完全移除 |

**預期結果 (Expected Results)**:
```
GET /api/v2/cart
Assert 1: cart.items.size() == 原始數量 - 1
Assert 2: cart.totalAmount 已重新計算（不包含已移除商品）
```

---

#### AT-M04-003-P1-01: 更新數量邏輯驗證 (UT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-003-P1-01 |
| **測試類型** | UT (Unit Test) |
| **優先級** | P1 |
| **對應 AC** | AC-M04-003-1 |
| **描述** | RedisCartService.updateQuantity() 邏輯驗證 |

**預期結果 (Expected Results)**:
```
Assert 1: service.updateQuantity(cartItemId, 5) 回傳更新後的 CartItemData
Assert 2: verify(redisTemplate.opsForValue()).set() 被呼叫
```

---

#### AT-M04-003-P1-02: 超過 999 回傳錯誤 (UT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-003-P1-02 |
| **測試類型** | UT (Unit Test) |
| **優先級** | P1 |
| **對應 AC** | AC-M04-003-3 |
| **描述** | 數量上限 999，超過時回傳錯誤 |

**測試資料 (Test Data)**:
```java
CartItemData item = new CartItemData();
item.setQuantity(999);
```

**預期結果 (Expected Results)**:
```
Assert 1: assertThrows(IllegalArgumentException.class, () -> {
    service.updateQuantity(item.getId(), 1000);
})
Assert 2: 異常訊息包含 "Quantity exceeds maximum limit"
```

---

#### AT-M04-003-P1-03: 數量上限驗證 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-003-P1-03 |
| **測試類型** | IT (Integration Test) |
| **優先級** | P1 |
| **對應 AC** | AC-M04-003-3 |
| **描述** | 整合測試驗證數量上限 999 |

**預期結果 (Expected Results)**:
```
PUT /api/v2/cart/items/{id} quantity=1000
Assert 1: response.statusCode == 400
Assert 2: response.json().error.code == "E-4000" (或類似驗證錯誤)
```

---

### 2.4 US-M04-004 測試案例

#### AT-M04-004-P0-01: 成功移除商品 (API E2E)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-004-P0-01 |
| **測試類型** | API E2E |
| **優先級** | P0 |
| **對應 AC** | AC-M04-004-1 |
| **描述** | 買家可成功移除購物車商品 |

**測試資料 (Test Data)**:
```json
DELETE /api/v2/cart/items/{cartItemId}
Authorization: Bearer {BUYER_TOKEN}
```

**預期結果 (Expected Results)**:
```
Assert 1: response.statusCode == 200
Assert 2: response.json().message == "Item removed from cart"

// 確認移除
GET /api/v2/cart
Assert 3: cart.items 不包含該 cartItemId
```

---

#### AT-M04-004-P0-02: 移除後購物車更新 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-004-P0-02 |
| **測試類型** | IT (Integration Test) |
| **優先級** | P0 |
| **對應 AC** | AC-M04-004-1 |
| **描述** | 移除商品後，購物車總金額更新 |

**預期結果 (Expected Results)**:
```
Assert 1: cart.getItems().size() == 原始數量 - 1
Assert 2: cart.getTotalAmount() == 原始總金額 - 已移除商品小計
```

---

#### AT-M04-004-P0-03: 不存在商品 ID 回傳 404 (API E2E)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-004-P0-03 |
| **測試類型** | API E2E |
| **優先級** | P0 |
| **對應 AC** | AC-M04-004-2 |
| **描述** | 移除不存在的商品回傳 404 |

**測試資料 (Test Data)**:
```json
DELETE /api/v2/cart/items/non-existent-id
Authorization: Bearer {BUYER_TOKEN}
```

**預期結果 (Expected Results)**:
```
Assert 1: response.statusCode == 404
Assert 2: response.json().error.code == "CART_ITEM_NOT_FOUND"
Assert 3: response.json().message contains "Cart item not found"
```

---

#### AT-M04-004-P1-01: CartItemNotFoundException 驗證 (IT)

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-004-P1-01 |
| **測試類型** | IT (Integration Test) |
| **優先級** | P1 |
| **對應 AC** | AC-M04-004-2 |
| **描述** | 驗證 CartItemNotFoundException 正確拋出 |

**預期結果 (Expected Results)**:
```
Assert 1: assertThrows(CartItemNotFoundException.class, () -> {
    cartService.removeItem(token, "non-existent-id");
})
Assert 2: exception.getErrorCode() == "CART_ITEM_NOT_FOUND"
```

---

## 3. 多租戶隔離測試 (Multi-Tenant Isolation Tests)

### AT-M04-ISOLATION-01: 買家 A 看不到買家 B 的購物車

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-ISOLATION-01 |
| **測試類型** | API E2E |
| **優先級** | P0 |
| **描述** | 買家 A 加入商品至購物車，買家 B 無法看到買家 A 的購物車內容 |

**測試資料 (Test Data)**:
```json
// 買家 A 加入商品
POST /api/v2/cart/items (as BuyerA)
{
  "listingId": "listing-001",
  "quantity": 2
}

// 買家 B 取得購物車
GET /api/v2/cart (as BuyerB)
```

**預期結果 (Expected Results)**:
```
// 買家 A 操作成功
Assert 1: buyerAaddResponse.statusCode == 201

// 買家 B 看不到買家 A 的商品
Assert 2: buyerBCartResponse.statusCode == 200
Assert 3: buyerBCart.items.isEmpty() == true OR
Assert 4: buyerBCart.items 不包含 listing-001
```

---

### AT-M04-ISOLATION-02: 跨租戶刪除無權限

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-ISOLATION-02 |
| **測試類型** | IT |
| **優先級** | P1 |
| **描述** | 買家 A 嘗試刪除買家 B 的購物車商品，回傳 403 或 404 |

**測試資料 (Test Data)**:
```java
// 買家 B 的 cartItemId
String buyerBCartItemId = "buyer-b-cart-item-id";

// 買家 A 嘗試刪除
cartService.removeItem(buyerAToken, buyerBCartItemId);
```

**預期結果 (Expected Results)**:
```
Assert 1: assertThrows(CartItemNotFoundException.class, () -> {
    cartService.removeItem(buyerAToken, buyerBCartItemId);
})
// 或
Assert 1: assertThrows(AccessDeniedException.class, () -> {
    cartService.removeItem(buyerAToken, buyerBCartItemId);
})
```

---

### AT-M04-ISOLATION-03: 不同租戶的 CartKey 隔離

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-ISOLATION-03 |
| **測試類型** | UT |
| **優先級** | P1 |
| **描述** | 驗證 Redis CartKey 包含 tenantId，不會混淆 |

**預期結果 (Expected Results)**:
```
Assert 1: RedisKeyGenerator.getCartKey(tenantA, userA) == "cart:tenant-a:user-a"
Assert 2: RedisKeyGenerator.getCartKey(tenantB, userB) == "cart:tenant-b:user-b"
Assert 3: tenantA key != tenantB key
```

---

## 4. NFR 測試 (Non-Functional Requirements Tests)

### AT-M04-NFR-01: Cart API 回應時間 < 200ms

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-NFR-01 |
| **測試類型** | API E2E |
| **優先級** | P0 |
| **描述** | 所有 Cart API 回應時間必須 < 200ms |

**測試資料 (Test Data)**:
```json
POST /api/v2/cart/items
GET /api/v2/cart
PUT /api/v2/cart/items/{id}
DELETE /api/v2/cart/items/{id}
```

**預期結果 (Expected Results)**:
```
Assert 1: POST /api/v2/cart/items responseTime < 200ms
Assert 2: GET /api/v2/cart responseTime < 200ms
Assert 3: PUT /api/v2/cart/items/{id} responseTime < 200ms
Assert 4: DELETE /api/v2/cart/items/{id} responseTime < 200ms
```

---

### AT-M04-NFR-02: Redis 故障時 Fallback 機制

| 欄位 | 內容 |
|------|------|
| **AT-ID** | AT-M04-NFR-02 |
| **測試類型** | IT |
| **優先級** | P0 |
| **描述** | Redis 故障時，系統應有 Fallback 機制，不應直接失敗 |

**測試資料 (Test Data)**:
```java
// Mock Redis 連線失敗
when(redisTemplate.opsForValue().get(anyString()))
    .thenThrow(new RedisConnectionException("Connection refused"));
```

**預期結果 (Expected Results)**:
```
Assert 1: cartService.getCart(token) 不拋出 ConnectionException
Assert 2: 回傳適當的錯誤回應或 Cache
Assert 3: 記錄錯誤日誌
Assert 4: 可選: 降級至記憶體購物車（若實作）
```

---

## 5. Error Code 驗證矩陣

| Error Code | 描述 | 觸發情境 | 測試案例 |
|------------|------|----------|----------|
| E-3000 | LISTING_NOT_FOUND | 加入購物車時 listingId 不存在 | AT-M04-001-P1-02 (IT) |
| E-4000 | INVALID_QUANTITY | 數量超過上限或無效 | AT-M04-003-P1-02 (UT), AT-M04-003-P1-03 (IT) |
| CART_ITEM_NOT_FOUND | CartItem not found | 更新的目標不存在 | AT-M04-004-P0-03 (API), AT-M04-004-P1-01 (IT) |
| ACCESS_DENIED | 權限不足 | 非 BUYER 角色操作 | AT-M04-001-P0-07 (IT), AT-M04-001-P0-08 (IT) |

---

## 6. 測試覆蓋矩陣 (Test Coverage Matrix)

| AC | UT | IT | API E2E | NFR | ISO | 覆蓋狀態 |
|----|----|----|---------|-----|-----|----------|
| AC-M04-001-1 | AT-M04-001-P1-01 | AT-M04-001-P0-02, AT-M04-001-P0-03 | AT-M04-001-P0-01 | - | - | ✅ |
| AC-M04-001-2 | AT-M04-001-P0-05 | - | AT-M04-001-P0-04 | - | - | ✅ |
| AC-M04-001-3 | - | AT-M04-001-P1-02 | AT-M04-001-P0-06 | - | - | ✅ |
| AC-M04-001-4 | - | AT-M04-001-P0-07, AT-M04-001-P0-08 | - | - | - | ✅ |
| AC-M04-002-1 | AT-M04-002-P1-01 | AT-M04-002-P0-02 | AT-M04-002-P0-01 | - | AT-M04-ISOLATION-01 | ✅ |
| AC-M04-002-2 | - | AT-M04-002-P0-04 | AT-M04-002-P0-03 | - | - | ✅ |
| AC-M04-003-1 | AT-M04-003-P1-01 | AT-M04-003-P0-02 | AT-M04-003-P0-01 | - | - | ✅ |
| AC-M04-003-2 | - | AT-M04-003-P0-03 | AT-M04-003-P0-04 | - | - | ✅ |
| AC-M04-003-3 | AT-M04-003-P1-02 | AT-M04-003-P1-03 | - | - | - | ✅ |
| AC-M04-004-1 | - | AT-M04-004-P0-02 | AT-M04-004-P0-01 | - | - | ✅ |
| AC-M04-004-2 | - | AT-M04-004-P1-01 | AT-M04-004-P0-03 | - | - | ✅ |

---

## 📝 關鍵驗證點總結

| 驗證項目 | 測試案例 | 優先級 |
|---------|---------|--------|
| 多租戶資料隔離 (買家 A 看不到買家 B 購物車) | AT-M04-ISOLATION-01, AT-M04-ISOLATION-02 | P0 |
| ROOM 類型日期範圍必填 | AT-M04-001-P0-03, AT-M04-001-P0-06, AT-M04-001-P1-02 | P0 |
| 同一商品累加數量 | AT-M04-001-P0-04, AT-M04-001-P0-05 | P0 |
| BUYER 角色驗證 (403) | AT-M04-001-P0-07, AT-M04-001-P0-08 | P0 |
| 數量為 0 時移除商品 | AT-M04-003-P0-03, AT-M04-003-P0-04 | P0 |
| 數量上限 999 驗證 | AT-M04-003-P1-02, AT-M04-003-P1-03 | P1 |
| 移除不存在商品回傳 404 | AT-M04-004-P0-03, AT-M04-004-P1-01 | P0 |
| API 回應時間 < 200ms | AT-M04-NFR-01 | P0 |
| Redis 故障 Fallback | AT-M04-NFR-02 | P0 |

---

**文件版本**: AISDLC v0.09
**測試框架**: JUnit 5 + Mockito, Spring Boot Test, REST Assured
**最後更新**: 2026-04-28

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 | 確認人 |
|------|------|----------|--------|
| v1.0 | 2026-04-28 | 初始版本（基於 Sprint 4 Plan + TC_M17_Tenant.md 結構） | - |
| v1.1 | 2026-04-28 | 新增完整 AC→AT 映射、詳細 AT 規格、NFR 測試、多租戶隔離測試 | - |