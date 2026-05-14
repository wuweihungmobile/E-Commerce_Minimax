# Sprint 11 QA 測試策略報告 / QA Test Strategy Report

**Sprint**: Sprint 11 (2026-06-01 ~ 2026-06-12)
**QA Agent**: Quincy (QA-Tester)
**版本**: v1.0
**創建日期**: 2026-06-13

---

## 文件資訊

| 項目 | 內容 |
|------|------|
| **TP-ID** | TP-S11-001 |
| **關聯模組** | M04 購物車, M06 預訂 (Phase 2) |
| **測試類型** | IT (Integration Test) + E2E Test |
| **SP** | Task-M11-109 (3), Task-M11-110 (3), Task-M11-111 (2) |

---

## 1. 測試範圍與目標

### 1.1 測試範圍

#### M04 購物車 (Cart Module)
- **API Endpoints**:
  - `GET /api/cart` - 取得當前購物車
  - `POST /api/cart/items` - 加入商品到購物車
  - `PUT /api/cart/items/:id` - 更新購物車項目數量
  - `DELETE /api/cart/items/:id` - 移除購物車項目
  - `DELETE /api/cart` - 清空購物車
  - `POST /api/cart/apply-promo` - 套用優惠券

#### M06 預訂 Phase 2 (Booking Module)
- **API Endpoints**:
  - `POST /api/v2/bookings` - 建立預訂（Phase 2）
  - `PUT /api/v2/bookings/:id` - 修改已確認的預訂
  - `POST /api/v2/bookings/:id/remind` - 預訂提醒

### 1.2 測試目標

| 指標 | 目標 | 說明 |
|------|------|------|
| IT 測試案例數 | 15+/15+ | M04 + M06 各 15+ |
| E2E 測試案例數 | 5+/5+ | 購物車+結帳流程 |
| IT 通過率 | 100% | 所有 IT 測試必須通過 |
| E2E 通過率 | 100% | 所有 E2E 測試必須通過 |

---

## 2. M04 購物車測試策略 / M04 Cart IT Test Strategy

### 2.1 測試架構

```
┌─────────────────────────────────────────────────────────────────┐
│                     M04 Cart IT Test Architecture               │
├─────────────────────────────────────────────────────────────────┤
│  Test Class: M04CartIntegrationTest                            │
│  Location: src/test/java/com/nextkey/ecommerce/integration/    │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │   MockMvc    │───▶│ CartController│───▶│RedisCartService│    │
│  │              │    │              │    │              │      │
│  └──────────────┘    └──────────────┘    └───────┬──────┘      │
│                                                   │              │
│                    ┌──────────────┐    ┌─────────┴────────┐    │
│                    │   PostgreSQL │◀───│ListingRepository │    │
│                    │  (TestDB)    │    │ProductSkuRepo   │    │
│                    └──────────────┘    └─────────────────┘     │
│                                                                 │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Redis (Testcontainers)               │   │
│  │    Key Pattern: cart:{userId}:{tenantId}               │   │
│  │    TTL: 30 days                                        │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 2.2 IT 測試案例 / Test Cases

#### 2.2.1 購物車基本操作 (IT-M04-001 ~ IT-M04-008)

| 測試案例 ID | 測試標題 | 優先級 | 測試類型 | 驗收標準 |
|-------------|----------|--------|----------|----------|
| IT-M04-001 | 取得空購物車 | P1 | Happy Path | 回傳空 items 陣列，totalAmount=0 |
| IT-M04-002 | 加入 PRODUCT 類型商品到購物車 | P1 | Happy Path | 成功加入，quantity=請求值 |
| IT-M04-003 | 加入 ROOM 類型商品到購物車（含日期） | P1 | Happy Path | 成功加入，startDate/endDate 正確設定 |
| IT-M04-004 | 加入商品時使用 SKU 價格覆寫 | P1 | Business Logic | 使用 sku.priceOverride 而非 listing.basePrice |
| IT-M04-005 | 再次加入相同商品數量疊加 | P1 | Business Logic | quantity = 原有 + 新請求 |
| IT-M04-006 | 更新購物車項目數量 | P1 | Happy Path | quantity 更新為新值，subtotal 重新計算 |
| IT-M04-007 | 移除購物車項目 | P1 | Happy Path | Redis Hash 中該 key 被刪除 |
| IT-M04-008 | 清空購物車 | P1 | Happy Path | Redis Key 被刪除 |

#### 2.2.2 Redis 操作驗證 (IT-M04-009 ~ IT-M04-011)

| 測試案例 ID | 測試標題 | 優先級 | 測試類型 | 驗收標準 |
|-------------|----------|--------|----------|----------|
| IT-M04-009 | Redis Hash 資料結構驗證 | P1 | Data Integrity | cart:{userId}:{tenantId} 格式正確 |
| IT-M04-010 | Redis TTL 設定驗證 | P1 | Non-Functional | expire 時間為 30 days |
| IT-M04-011 | Redis CartItemData 序列化/反序列化 | P1 | Data Integrity | 取出資料與存入資料一致 |

#### 2.2.3 多租戶資料隔離 (IT-M04-012 ~ IT-M04-013)

| 測試案例 ID | 測試標題 | 優先級 | 測試類型 | 驗收標準 |
|-------------|----------|--------|----------|----------|
| IT-M04-012 | 不同租戶購物車完全隔離 | P1 | Security | Tenant-A 無法看到 Tenant-B 的購物車 |
| IT-M04-013 | 跨租戶嘗試操作應被拒絕 | P1 | Security | 403 Forbidden 或 404 Not Found |

#### 2.2.4 錯誤處理 (IT-M04-014 ~ IT-M04-016)

| 測試案例 ID | 測試標題 | 優先級 | 測試類型 | 驗收標準 |
|-------------|----------|--------|----------|----------|
| IT-M04-014 | 加入不存在的 Listing | P1 | Error Handling | 400 Bad Request，Listing not found |
| IT-M04-015 | ROOM 類型缺少日期 | P1 | Validation | 400 Bad Request，Start date required |
| IT-M04-016 | EndDate 在 StartDate 之前 | P1 | Validation | 400 Bad Request，End date must be after start |

#### 2.2.5 優惠券驗證 (IT-M04-017 ~ IT-M04-018)

| 測試案例 ID | 測試標題 | 優先級 | 測試類型 | 驗收標準 |
|-------------|----------|--------|----------|----------|
| IT-M04-017 | 套用有效優惠券 | P1 | Business Logic | totalAmount 正確折扣 |
| IT-M04-018 | 套用無效/過期優惠券 | P1 | Error Handling | 400 Bad Request，Coupon invalid |

### 2.3 測試資料準備 / Test Data Setup

```java
// M04 測試資料工廠
private static final UUID TEST_TENANT_ID_A = UUID.fromString("550e8400-e29b-41d4-a716-446655440001");
private static final UUID TEST_TENANT_ID_B = UUID.fromString("550e8400-e29b-41d4-a716-446655440002");
private static final UUID TEST_USER_ID_1 = UUID.fromString("550e8400-e29b-41d4-a716-446655440003");
private static final UUID TEST_USER_ID_2 = UUID.fromString("550e8400-e29b-41d4-a716-446655440004");

// 測試用 Listing
private Listing buildProductListing(UUID tenantId, String title, BigDecimal price) {
    return Listing.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .title(title)
            .listingType(Listing.ListingType.PRODUCT)
            .basePrice(price)
            .status(Listing.ListingStatus.ACTIVE)
            .build();
}

private Listing buildRoomListing(UUID tenantId, String title, BigDecimal price) {
    return Listing.builder()
            .id(UUID.randomUUID())
            .tenantId(tenantId)
            .title(title)
            .listingType(Listing.ListingType.ROOM)
            .basePrice(price)
            .status(Listing.ListingStatus.ACTIVE)
            .build();
}

// 測試用 SKU
private ProductSku buildSku(UUID listingId, BigDecimal priceOverride) {
    return ProductSku.builder()
            .id(UUID.randomUUID())
            .skuCode("SKU-TEST-" + System.currentTimeMillis())
            .specName("Test Spec")
            .priceOverride(priceOverride)
            .build();
}
```

### 2.4 Mock 策略 / Mock Strategy

```java
// M04 IT 使用 Mock Redis，避免依賴真實 Redis
// 但需要驗證 Redis 操作語義

@MockBean
private RedisTemplate<String, Object> redisTemplate;

@MockBean
private HashOperations<String, Object, Object> hashOperations;

// 驗證 Redis 操作
verify(hashOperations).put(eq(expectedCartKey), eq(expectedItemKey), any(CartItemData.class));
verify(redisTemplate).expire(eq(expectedCartKey), eq(Duration.ofDays(30)));
```

---

## 3. M06 預訂測試策略 / M06 Booking IT Test Strategy

### 3.1 測試架構

```
┌─────────────────────────────────────────────────────────────────┐
│                  M06 Booking Phase 2 IT Architecture             │
├─────────────────────────────────────────────────────────────────┤
│  Test Class: M06BookingPhase2IntegrationTest                    │
│  Location: src/test/java/com/nextkey/ecommerce/integration/     │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │   MockMvc    │───▶│BookingController│───▶│ BookingService │    │
│  │              │    │              │    │              │       │
│  └──────────────┘    └──────────────┘    └───────┬──────┘       │
│                                                   │              │
│  ┌──────────────┐    ┌──────────────┐    ┌─────────┴────────┐  │
│  │   PostgreSQL │◀───│BookingRepository│  │RoomCalendarService│  │
│  │  (TestDB)    │    │ListingRepository│  │                  │  │
│  └──────────────┘    └─────────────────┘  └───────┬──────────┘   │
│                                                   │              │
│  ┌─────────────────────────────────────────────────────────┐  │
│  │              Redis Lock Service (Testcontainers)         │  │
│  │   Key Pattern: lock:room:{listingId}:date:{date}         │  │
│  │   Strategy: NO_WAIT (立即失敗，不等待)                   │  │
│  └─────────────────────────────────────────────────────────┘  │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 IT 測試案例 / Test Cases

#### 3.2.1 預訂建立 Phase 2 (IT-M06-001 ~ IT-M06-006)

| 測試案例 ID | 測試標題 | 優先級 | 測試類型 | 驗收標準 |
|-------------|----------|--------|----------|----------|
| IT-M06-001 | 建立預訂-基本流程 | P1 | Happy Path | 預訂建立成功，status=CREATED |
| IT-M06-002 | 建立預訂-多晚預訂 | P1 | Happy Path | 正確計算 nightsCount 和 totalAmount |
| IT-M06-003 | 建立預訂-使用 Idempotency-Key | P1 | Idempotency | 相同 key 重複請求返回相同結果 |
| IT-M06-004 | 建立預訂-客人數量驗證 | P1 | Validation | 超過房型容量時返回 400 |
| IT-M06-005 | 建立預訂-日期範圍驗證 | P1 | Validation | CheckOut <= CheckIn 時返回 400 |
| IT-M06-006 | 建立預訂-房源不存在 | P1 | Error Handling | 返回 404 Not Found |

#### 3.2.2 Redis 分散式鎖 (IT-M06-007 ~ IT-M06-010)

| 測試案例 ID | 測試標題 | 優先級 | 測試類型 | 驗收標準 |
|-------------|----------|--------|----------|----------|
| IT-M06-007 | 分散式鎖-單一日期成功取得 | P1 | Concurrency | lockDateRange() 返回非 null lockValue |
| IT-M06-008 | 分散式鎖-NOWAIT 策略失敗場景 | P1 | Concurrency | 另一事務持有鎖時立即返回 null |
| IT-M06-009 | 分散式鎖-鎖釋放驗證 | P1 | Concurrency | unlockDateRange() 正確釋放所有日期鎖 |
| IT-M06-010 | 分散式鎖-跨多日期 | P1 | Concurrency | 3 晚預訂鎖定 3 個日期的鎖 |

#### 3.2.3 並發預訂衝突 (IT-M06-011 ~ IT-M06-013)

| 測試案例 ID | 測試標題 | 優先級 | 測試類型 | 驗收標準 |
|-------------|----------|--------|----------|----------|
| IT-M06-011 | 並發預訂-同時請求相同日期 | P1 | Concurrency | 只有一個成功，另一個收到衝突錯誤 |
| IT-M06-012 | 並發預訂-部分重疊日期 | P1 | Concurrency | 後到的請求因為日期已被佔而失敗 |
| IT-M06-013 | 並發預訂-不同租戶隔離 | P1 | Security | 不同租戶的預訂不互相影響 |

#### 3.2.4 預訂修改 (IT-M06-014 ~ IT-M06-016)

| 測試案例 ID | 測試標題 | 優先級 | 測試類型 | 驗收標準 |
|-------------|----------|--------|----------|----------|
| IT-M06-014 | 修改預訂-成功修改日期 | P1 | Happy Path | 新日期範圍可用，舊日期釋放 |
| IT-M06-015 | 修改預訂-新日期不可用 | P1 | Error Handling | 400 Bad Request，新日期已被佔用 |
| IT-M06-016 | 修改預訂-已取消狀態不可修改 | P1 | State Transition | 400 Bad Request，狀態不允許更新 |

#### 3.2.5 預訂提醒 (IT-M06-017 ~ IT-M06-018)

| 測試案例 ID | 測試標題 | 優先級 | 測試類型 | 驗收標準 |
|-------------|----------|--------|----------|----------|
| IT-M06-017 | 預訂提醒-成功發送 | P1 | Happy Path | remind endpoint 返回 200 |
| IT-M06-018 | 預訂提醒-找不到預訂 | P1 | Error Handling | 404 Not Found |

### 3.3 並發測試場景 / Concurrency Test Scenarios

```java
/**
 * IT-M06-011: 並發預訂-同時請求相同日期
 *
 * 測試策略：
 * 1. 準備一個可用的房源日期範圍
 * 2. 同時發送 2 個預訂請求（使用 CompletableFuture 或 Thread）
 * 3. 驗證：
 *    - 只有 1 個成功（返回 201 Created）
 *    - 另 1 個失敗（返回 409 Conflict 或 400 Bad Request）
 *    - 資料庫只有 1 筆記錄
 */
@Test
void concurrentBooking_sameDateRange_oneSucceeds_oneFails() throws Exception {
    // Arrange
    UUID listingId = createTestRoomListing();
    LocalDate checkIn = LocalDate.now().plusDays(10);
    LocalDate checkOut = LocalDate.now().plusDays(12);

    // Act - 同時發送兩個預訂請求
    CompletableFuture<Result> future1 = CompletableFuture.supplyAsync(() ->
        makeBookingRequest(listingId, checkIn, checkOut, "user1@example.com"));
    CompletableFuture<Result> future2 = CompletableFuture.supplyAsync(() ->
        makeBookingRequest(listingId, checkIn, checkOut, "user2@example.com"));

    CompletableFuture.allOf(future1, future2).join();

    // Assert
    List<Result> results = Arrays.asList(future1.get(), future2.get());
    long successCount = results.stream().filter(r -> r.statusCode == 201).count();
    long failureCount = results.stream().filter(r -> r.statusCode != 201).count();

    assertEquals(1, successCount, "只有一個預訂應該成功");
    assertEquals(1, failureCount, "另一個預訂應該失敗");
}
```

### 3.4 Idempotency 測試案例 / Idempotency Test Cases

```java
/**
 * IT-M06-003: 建立預訂-使用 Idempotency-Key
 *
 * 測試策略：
 * 1. 在請求 header 中加入 Idempotency-Key
 * 2. 相同 key 發送 2 次請求
 * 3. 驗證：
 *    - 第一次返回 201 Created
 *    - 第二次返回 200 OK（不重複建立）
 *    - 資料庫只有 1 筆記錄
 */
@Test
void createBooking_withIdempotencyKey_secondRequestReturnsSameResult() throws Exception {
    // Arrange
    String idempotencyKey = "idem-key-" + UUID.randomUUID().toString();
    BookingDto.CreateRequest request = buildValidBookingRequest();

    // Act - 發送第一次請求
    Result firstResult = makeBookingRequestWithIdempotency(request, idempotencyKey);

    // Act - 發送第二次請求（相同 key）
    Result secondResult = makeBookingRequestWithIdempotency(request, idempotencyKey);

    // Assert
    assertEquals(201, firstResult.statusCode);
    assertEquals(200, secondResult.statusCode); // Idempotent，不重複建立
    assertEquals(firstResult.bookingId, secondResult.bookingId); // 相同 booking ID
}
```

---

## 4. E2E 測試策略 / E2E Test Strategy

### 4.1 E2E 測試架構

```
┌─────────────────────────────────────────────────────────────────┐
│                E2E: Cart + Checkout Flow Architecture           │
├─────────────────────────────────────────────────────────────────┤
│  Test Class: E2ECartCheckoutFlowTest                            │
│  Framework: REST Assured + MockMvc                              │
│  Profile: integration-test                                      │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐      │
│  │  REST Assured │───▶│  Full Stack   │───▶│   Real DB    │    │
│  │  (HTTP Client)│    │  Spring Boot  │    │  PostgreSQL  │    │
│  └──────────────┘    └──────────────┘    └──────────────┘      │
│                            │                    │               │
│                            ▼                    ▼               │
│                      ┌──────────┐         ┌──────────┐         │
│                      │   Redis  │         │ Booking  │         │
│                      │ (Real)   │         │ Service  │         │
│                      └──────────┘         └──────────┘         │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 E2E 測試案例 / E2E Test Cases

| E2E ID | 測試標題 | 優先級 | 使用者旅程 | 驗收標準 |
|--------|----------|--------|------------|----------|
| E2E-001 | 完整購物車+結帳流程 | P1 | Browse→Add to Cart→Checkout | 預訂成功建立，購物車已清空 |
| E2E-002 | 多商品購物車+結帳 | P1 | Add 3 items→Checkout | 總金額正確，預訂明細正確 |
| E2E-003 | 優惠券+結帳流程 | P1 | Add item→Apply Coupon→Checkout | 折扣正確套用，總金額更新 |
| E2E-004 | ROOM 商品預訂流程 | P1 | Browse Room→Add with dates→Checkout | 日期正確，價格計算正確 |
| E2E-005 | 預訂修改流程 | P1 | Book→Modify dates→Confirm | 日期更新，日曆狀態正確 |

### 4.3 E2E 測試詳細案例

#### E2E-001: 完整購物車+結帳流程

```java
/**
 * E2E-001: 完整購物車+結帳流程
 *
 * 測試步驟：
 * 1. SELLER 建立一個 PRODUCT 商品
 * 2. BUYER 登入系統
 * 3. BUYER 將商品加入購物車
 * 4. BUYER 查看購物車內容
 * 5. BUYER 進行結帳（建立預訂）
 * 6. 驗證預訂成功且購物車已清空
 *
 * 預期結果：
 * - 預訂 status = CONFIRMED
 * - 購物車 totalItems = 0
 * - 預訂金額 = 商品價格 × 數量
 */
@Test
@DisplayName("E2E-001: 完整購物車+結帳流程")
void fullCartCheckoutFlow_success() throws Exception {
    // Step 1: SELLER 建立商品
    UUID listingId = createProductAsSeller("Test Product", new BigDecimal("999.99"));

    // Step 2: BUYER 登入
    String buyerToken = loginAsBuyer();

    // Step 3: 加入購物車
    given()
        .header("Authorization", "Bearer " + buyerToken)
        .body(CartDto.AddItemRequest.builder()
            .listingId(listingId)
            .quantity(2)
            .build())
        .when()
        .post("/v2/cart/items")
        .then()
        .statusCode(200)
        .body("success", is(true));

    // Step 4: 驗證購物車內容
    given()
        .header("Authorization", "Bearer " + buyerToken)
        .when()
        .get("/v2/cart")
        .then()
        .statusCode(200)
        .body("data.totalItems", equalTo(2))
        .body("data.totalAmount", equalTo(1999.98));

    // Step 5: 結帳（建立預訂）
    String checkoutResponse = given()
        .header("Authorization", "Bearer " + buyerToken)
        .body(BookingDto.CreateRequest.builder()
            .roomListingId(listingId) // 這裡用 roomListingId
            .checkInDate(LocalDate.now().plusDays(1))
            .checkOutDate(LocalDate.now().plusDays(2))
            .guestCount(1)
            .guestName("Test Guest")
            .guestPhone("0912345678")
            .guestEmail("guest@test.com")
            .build())
        .when()
        .post("/v2/bookings")
        .then()
        .statusCode(201)
        .body("success", is(true))
        .body("data.status", equalTo("CONFIRMED"))
        .extract()
        .asString();

    // Step 6: 驗證購物車已清空
    given()
        .header("Authorization", "Bearer " + buyerToken)
        .when()
        .get("/v2/cart")
        .then()
        .statusCode(200)
        .body("data.totalItems", equalTo(0));
}
```

---

## 5. 測試環境需求 / Test Environment Requirements

### 5.1 測試容器架構

```
┌─────────────────────────────────────────────────────────────────┐
│                    Testcontainers Architecture                   │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────┐         ┌──────────────────┐             │
│  │  PostgreSQL      │         │     Redis        │             │
│  │  Container       │         │    Container     │             │
│  │                  │         │                  │             │
│  │  Port: 5432      │         │   Port: 6379     │             │
│  │  Database: test  │         │   Password: test │             │
│  └──────────────────┘         └──────────────────┘             │
│                                                                 │
│  ┌──────────────────────────────────────────────────┐          │
│  │              Spring Boot Test Application        │          │
│  │  Profile: integration-test                       │          │
│  │  - Mock MVC for API testing                     │          │
│  │  - Real Redis for cart operations               │          │
│  │  - Real PostgreSQL for persistent data          │          │
│  └──────────────────────────────────────────────────┘          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 測試依賴配置

```yaml
# pom.xml (testcontainers dependencies)
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

### 5.3 測試配置範例

```java
@SpringBootTest
@ActiveProfiles("integration-test")
@Testcontainers
@Container
static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
    .withDatabaseName("testdb")
    .withUsername("test")
    .withPassword("test");

@Container
static RedisContainer<?> redis = new RedisContainer>("redis:7-alpine")
    .withPassword("test-redis-password")
    .withExposedPorts(6379);
```

---

## 6. 測試成功標準 / Test Success Criteria

### 6.1 IT 測試成功標準

| 指標 | 標準 | 測試案例 |
|------|------|----------|
| 測試案例數 | 15+ (M04) + 15+ (M06) = 30+ | IT-M04-001~018, IT-M06-001~018 |
| 通過率 | 100% (30+/30+) | 所有 IT 測試必須 Pass |
| 覆蓋率 | 所有 API endpoints | GET/POST/PUT/DELETE 全覆蓋 |
| 覆蓋率 | 所有 business logic | Redis 操作、鎖機制、Validation |

### 6.2 E2E 測試成功標準

| 指標 | 標準 | 測試案例 |
|------|------|----------|
| 測試案例數 | 5+ | E2E-001~005 |
| 通過率 | 100% (5+/5+) | 所有 E2E 測試必須 Pass |
| 使用者旅程 | 100% | Cart→Checkout→Booking 完整流程 |
| 失敗率 | 0% | 不允許任何失敗 |

### 6.3 品質指標

| 指標 | 目標 | 測量方式 |
|------|------|----------|
| 測試隔離性 | 每個測試獨立運行 | Testcontainers 提供隔離 |
| 測試穩定性 | 無 flaky tests | 3 次連續運行都 Pass |
| 測試執行時間 | IT < 5 min, E2E < 3 min | Maven surefire 計時 |
| Mock 覆蓋率 | > 80% | 關鍵服務皆已 Mock |

---

## 7. 測試案例索引 / Test Case Index

### 7.1 M04 購物車 IT 測試案例

| ID | 測試標題 | 優先級 | 分組 |
|----|----------|--------|------|
| IT-M04-001 | 取得空購物車 | P1 | 基本操作 |
| IT-M04-002 | 加入 PRODUCT 類型商品到購物車 | P1 | 基本操作 |
| IT-M04-003 | 加入 ROOM 類型商品到購物車（含日期） | P1 | 基本操作 |
| IT-M04-004 | 加入商品時使用 SKU 價格覆寫 | P1 | 基本操作 |
| IT-M04-005 | 再次加入相同商品數量疊加 | P1 | 基本操作 |
| IT-M04-006 | 更新購物車項目數量 | P1 | 基本操作 |
| IT-M04-007 | 移除購物車項目 | P1 | 基本操作 |
| IT-M04-008 | 清空購物車 | P1 | 基本操作 |
| IT-M04-009 | Redis Hash 資料結構驗證 | P1 | Redis 操作 |
| IT-M04-010 | Redis TTL 設定驗證 | P1 | Redis 操作 |
| IT-M04-011 | Redis CartItemData 序列化/反序列化 | P1 | Redis 操作 |
| IT-M04-012 | 不同租戶購物車完全隔離 | P1 | 多租戶 |
| IT-M04-013 | 跨租戶嘗試操作應被拒絕 | P1 | 多租戶 |
| IT-M04-014 | 加入不存在的 Listing | P1 | 錯誤處理 |
| IT-M04-015 | ROOM 類型缺少日期 | P1 | 錯誤處理 |
| IT-M04-016 | EndDate 在 StartDate 之前 | P1 | 錯誤處理 |
| IT-M04-017 | 套用有效優惠券 | P1 | 優惠券 |
| IT-M04-018 | 套用無效/過期優惠券 | P1 | 優惠券 |

### 7.2 M06 預訂 IT 測試案例

| ID | 測試標題 | 優先級 | 分組 |
|----|----------|--------|------|
| IT-M06-001 | 建立預訂-基本流程 | P1 | Phase 2 建立 |
| IT-M06-002 | 建立預訂-多晚預訂 | P1 | Phase 2 建立 |
| IT-M06-003 | 建立預訂-使用 Idempotency-Key | P1 | Phase 2 建立 |
| IT-M06-004 | 建立預訂-客人數量驗證 | P1 | Phase 2 建立 |
| IT-M06-005 | 建立預訂-日期範圍驗證 | P1 | Phase 2 建立 |
| IT-M06-006 | 建立預訂-房源不存在 | P1 | Phase 2 建立 |
| IT-M06-007 | 分散式鎖-單一日期成功取得 | P1 | Redis 鎖 |
| IT-M06-008 | 分散式鎖-NOWAIT 策略失敗場景 | P1 | Redis 鎖 |
| IT-M06-009 | 分散式鎖-鎖釋放驗證 | P1 | Redis 鎖 |
| IT-M06-010 | 分散式鎖-跨多日期 | P1 | Redis 鎖 |
| IT-M06-011 | 並發預訂-同時請求相同日期 | P1 | 並發 |
| IT-M06-012 | 並發預訂-部分重疊日期 | P1 | 並發 |
| IT-M06-013 | 並發預訂-不同租戶隔離 | P1 | 並發 |
| IT-M06-014 | 修改預訂-成功修改日期 | P1 | Phase 2 修改 |
| IT-M06-015 | 修改預訂-新日期不可用 | P1 | Phase 2 修改 |
| IT-M06-016 | 修改預訂-已取消狀態不可修改 | P1 | Phase 2 修改 |
| IT-M06-017 | 預訂提醒-成功發送 | P1 | Phase 2 提醒 |
| IT-M06-018 | 預訂提醒-找不到預訂 | P1 | Phase 2 提醒 |

### 7.3 E2E 測試案例

| ID | 測試標題 | 優先級 | 使用者旅程 |
|----|----------|--------|------------|
| E2E-001 | 完整購物車+結帳流程 | P1 | Browse→Cart→Checkout |
| E2E-002 | 多商品購物車+結帳 | P1 | Multi-add→Checkout |
| E2E-003 | 優惠券+結帳流程 | P1 | Cart→Coupon→Checkout |
| E2E-004 | ROOM 商品預訂流程 | P1 | Browse Room→Dates→Book |
| E2E-005 | 預訂修改流程 | P1 | Book→Modify→Confirm |

---

## 8. 測試執行計畫 / Test Execution Plan

### 8.1 Sprint 11 QA 工作分配

| 任務 ID | 任務名稱 | SP | 負責人 | 預計完成 |
|---------|----------|-----|--------|----------|
| Task-M11-109 | IT: 購物車整合測試 | 3 | QA (Quincy) | Sprint 11 Day 3 |
| Task-M11-110 | IT: M06 預訂整合測試 | 3 | QA (Quincy) | Sprint 11 Day 5 |
| Task-M11-111 | E2E: 購物車+結帳流程測試 | 2 | QA (Quincy) | Sprint 11 Day 7 |

### 8.2 測試執行時間表

```
Sprint 11 Day 1-2: 測試環境準備
  - 設定 Testcontainers
  - 準備測試資料工廠
  - 驗證測試框架運作

Sprint 11 Day 3-4: M04 購物車 IT 執行
  - 執行 IT-M04-001 ~ IT-M04-018
  - 修复失敗的測試
  - 產出 M04 IT 測試報告

Sprint 11 Day 5-6: M06 預訂 IT 執行
  - 執行 IT-M06-001 ~ IT-M06-018
  - 特别关注并发测试
  - 產出 M06 IT 測試報告

Sprint 11 Day 7: E2E 測試執行
  - 執行 E2E-001 ~ E2E-005
  - 產出 E2E 測試報告

Sprint 11 Day 8-9: 最終驗證
  - 確認所有測試通過
  - 產出 Sprint 11 測試總結報告
```

---

## 9. 風險與緩解措施 / Risks and Mitigations

| 風險項目 | 等級 | 影響 | 緩解措施 |
|----------|------|------|----------|
| Redis Testcontainers 啟動慢 | 中 | 測試執行時間增加 | 使用連接池和預熱 |
| 並發測試不稳定 | 高 | 測試結果不一致 | 3 次重試機制，使用信號量控制 |
| Mock Redis 與真實行為差異 | 中 | 可能漏測真實場景 | 重要測試使用真實 Redis |
| 測試資料衝突 | 低 | 測試間互相影響 | 每個測試使用唯一 UUID |
| 優惠券服務外部依賴 | 中 | 無法完整測試 | Mock 優惠券服務 |

---

## 10. 附錄 / Appendix

### 10.1 測試類位置對照表

| 模組 | 測試類 | 路徑 |
|------|--------|------|
| M04 Cart IT | M04CartIntegrationTest | `integration/M04CartIntegrationTest.java` |
| M06 Booking IT | M06BookingPhase2IntegrationTest | `integration/M06BookingPhase2IntegrationTest.java` |
| E2E Cart+Checkout | E2ECartCheckoutFlowTest | `e2e/E2ECartCheckoutFlowTest.java` |

### 10.2 現有測試參考

- 現有 Cart E2E: `CartControllerE2ETest.java` (12 案例)
- 現有 Booking IT: `BookingIntegrationTest.java` (4 案例)
- 現有 Cart Unit: `RedisCartServiceTest.java` (19 案例)

---

**報告產生時間**: 2026-06-13
**QA Agent**: Quincy
**文件狀態**: Ready for Review