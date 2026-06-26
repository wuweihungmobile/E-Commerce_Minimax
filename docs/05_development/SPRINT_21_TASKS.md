# Sprint 21 任務分解 / Sprint 21 Tasks

> **Sprint 編號**: Sprint 21
> **期間**: 2026-08-04 ~ 2026-08-15
> **建立日期**: 2026-06-26
> **基於**: [SPRINT_21_PLAN.md](../04_planning/SPRINT_21_PLAN.md)

---

## 執行狀態總覽

| US ID | 標題 | SP | 優先級 | 狀態 |
|-------|------|----|--------|------|
| US-001 | AI-502 notification_history MQ Consumer 一致性 | 2 | P0 | ✅ 完成 |
| US-002 | Stripe SDK Phase 3 真實 SDK 整合 | 3 | P0 | ✅ 完成 |
| US-003 | M11 LogisticsProvider 策略抽象 | 2 | P1 | ✅ 完成 |
| US-004 | AI-503 getRatingStats @Cacheable | 1 | P1 | ✅ 完成 |
| US-005 | M14 租戶活躍統計 API | 2 | P1 | ✅ 完成 |
| US-006 | M11 運費模板 CRUD API（Buffer-A） | 2 | Buffer | ⬜ 未開始 |

**建議執行順序**：US-001 → US-002 → US-003 → US-004 → US-005 → US-006（Buffer）

---

## US-001：AI-502 — notification_history MQ Consumer 端一致性改善

> **SP**: 2 | **優先級**: P0 | **狀態**: ✅ 完成（2026-06-26）

### 任務清單

**T-001-1：分析現有程式碼**
- [x] 閱讀 `NotificationService.sendNotification()`，確認舊 try-catch history 寫入位置
- [x] 閱讀 `NotificationConsumerService.processMessage()`，確認新增 createHistory 的正確位置
- [x] 確認 `NotificationHistoryService` Bean 可被 `NotificationConsumerService` 注入（無循環依賴）

**T-001-2：修改 `NotificationConsumerService`**
```
backend/src/main/java/com/nextkey/ecommerce/infrastructure/mq/NotificationConsumerService.java
```
- [x] 新增 `NotificationHistoryService historyService` 欄位（`@RequiredArgsConstructor`）
- [x] 在 `processMessage()` 成功處理通知後，呼叫：
  ```java
  try {
      historyService.createHistory(
          notification.getUserId(), notification.getTenantId(),
          notification.getType().name(), "IN_APP",
          notification.getTitle(), notification.getContent());
  } catch (RuntimeException e) {
      log.warn("Failed to write notification history: {}", e.getMessage());
      // 不 re-throw，確保 ACK 正常執行
  }
  ```

**T-001-3：修改 `NotificationService`**
```
backend/src/main/java/com/nextkey/ecommerce/core/notification/NotificationService.java
```
- [x] 移除 `sendNotification()` 中的 try-catch history 寫入區塊（避免雙寫）
- [x] 保留其他邏輯不變

**T-001-4：單元測試**
```
backend/src/test/java/com/nextkey/ecommerce/infrastructure/mq/NotificationConsumerServiceTest.java
```
- [x] 新增測試：`processMessage()` 成功後，`historyService.createHistory()` 被呼叫一次（TC-C001）
- [x] 新增測試：`historyService.createHistory()` 拋出 `RuntimeException` 時，`processMessage()` 仍正常完成（TC-C002）
- [x] 確認現有測試仍全數通過（2/2 passed）

**T-001-5：整合測試更新**
```
backend/src/test/java/com/nextkey/ecommerce/integration/NotificationMQIntegrationTest.java
```
- [x] 建立整合測試：IT-AI502-001 processMessage → notification_history 寫入 DB
- [x] 建立整合測試：IT-AI502-002 容錯設計驗收（不拋例外）
- [x] 2/2 通過（使用 ReflectionTestUtils.invokeMethod 直接測 processMessage）

**T-001-6：驗證**
- [x] `mvn test -Dtest=NotificationConsumerServiceTest` 全部通過（2/2）
- [x] `mvn verify` BUILD SUCCESS（單元 298/298 + 整合 2/2）

---

## US-002：Stripe SDK Phase 3 — 真實 Stripe Java SDK 整合

> **SP**: 3 | **優先級**: P0 | **狀態**: ✅ 完成（2026-06-26）

### 任務清單

**T-002-1：新增 Stripe SDK 依賴**
```
backend/pom.xml
```
- [x] 新增 `com.stripe:stripe-java:24.3.0`（main scope）
- [x] 確認無版本衝突

**T-002-2：新增 WireMock 依賴**
```
backend/pom.xml（test scope）
```
- [x] 新增 `org.wiremock:wiremock-standalone:3.5.4`（test scope）
  - 注意：使用 standalone variant（內建 Jetty），避免與 Spring Boot Tomcat 的 classpath 衝突

**T-002-3：配置 Stripe API Key**
```
backend/src/main/resources/application.yml
```
- [x] 新增 `stripe.secret.key: ${STRIPE_SECRET_KEY:sk_test_placeholder}`
- [x] WireMock 測試中使用 `Stripe.overrideApiBase()` 靜態方法重導向至 WireMock（無需 application-test.properties）

**T-002-4：修改 `StripePaymentGateway`**
```
backend/src/main/java/com/nextkey/ecommerce/infrastructure/payment/StripePaymentGateway.java
```
- [x] 注入 `@Value("${stripe.secret.key:sk_test_placeholder}") String stripeApiKey`
- [x] `createPaymentIntent()` 改用真實 SDK + `RequestOptions` per-request API key（執行緒安全）
- [x] 捕捉 `CardException` → `ErrorCode.E_6006`（400 Bad Request）
- [x] 捕捉 `StripeException` → `ErrorCode.E_6007`（503 Service Unavailable）

**T-002-4b：新增 ErrorCode + GlobalExceptionHandler 對應**
```
backend/src/main/java/com/nextkey/ecommerce/shared/exception/ErrorCode.java
backend/src/main/java/com/nextkey/ecommerce/api/dto/GlobalExceptionHandler.java
```
- [x] 新增 `E_6006("E-6006", "Payment card declined")`
- [x] 新增 `E_6007("E-6007", "Payment provider error")`
- [x] E_6006 → 400 BAD_REQUEST、E_6007 → 503 SERVICE_UNAVAILABLE

**T-002-5：WireMock 單元測試**
```
backend/src/test/java/com/nextkey/ecommerce/infrastructure/payment/StripePaymentGatewayTest.java
```
- [x] TC-S001：`createPaymentIntent()` 成功（WireMock stub 200 + PaymentIntent JSON）
- [x] TC-S002：`createPaymentIntent()` 卡片拒絕（WireMock stub 402 card_error → E_6006）
- [x] TC-S003：驗證請求 header 包含 `Idempotency-Key: {orderId}`
- [x] 3/3 通過（`@WireMockTest` + `Stripe.overrideApiBase()`）

**T-002-6：OrderPaymentController E2E 測試**
```
backend/src/test/java/com/nextkey/ecommerce/api/controller/OrderPaymentControllerE2ETest.java
```
- [x] API-PAY-001: 無 JWT → 401（GET /payment）
- [x] API-PAY-002: 有效 JWT + 不存在 orderId → 404（GET /payment）
- [x] API-PAY-003: 無 JWT → 401（POST /pay）
- [x] API-PAY-004: 有效 JWT + 不存在 orderId → 404（POST /pay）
- [x] 4/4 通過

**T-002-7：驗證**
- [x] `mvn test -pl backend -Dtest=StripePaymentGatewayTest` 3/3 通過
- [x] `mvn verify` BUILD SUCCESS（Checkstyle 0 violations + PMD pass + Failsafe 全部通過）

---

## US-003：M11 物流追蹤 — LogisticsProvider 策略抽象

> **SP**: 2 | **優先級**: P1 | **狀態**: ✅ 完成（2026-06-27）

### 任務清單

**T-003-1：定義 `LogisticsProvider` 介面**
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/provider/LogisticsProvider.java
```
- [x] 建立介面（getProviderCode / createShipment / trackShipment）
- [x] 新增 `LogisticsDto.ShipmentResult` + `LogisticsDto.TrackingResult` inner class

**T-003-2：實作 `HCTLogisticsProvider`**
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/provider/HCTLogisticsProvider.java
```
- [x] `@Service` + `@Slf4j`，`getProviderCode()` → "HCT"
- [x] `createShipment()` → "HCT-{UUID前8碼}"，`trackShipment()` → IN_TRANSIT + 黑貓物流中心-台北

**T-003-3：實作 `TCATLogisticsProvider`（新竹物流）**
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/provider/TCATLogisticsProvider.java
```
- [x] `getProviderCode()` → "TCAT"（對應既有 `Logistics.LogisticsProvider.TCAT` enum）
- [x] `createShipment()` → "TCAT-{UUID前8碼}"，`trackShipment()` → IN_TRANSIT + 新竹物流中心-新竹

**T-003-4：建立 `LogisticsProviderFactory`**
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/provider/LogisticsProviderFactory.java
```
- [x] `List<LogisticsProvider>` constructor injection → `Map<String, LogisticsProvider>`
- [x] `getProvider(code)` 找不到時拋出 `BusinessException(E_7503)`

**T-003-5：修改 `LogisticsService`**
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/LogisticsService.java
```
- [x] 注入 `LogisticsProviderFactory`
- [x] `createLogistics()` 改呼叫 `factory.getProvider(...).createShipment(request)` 取得追蹤號
- [x] 移除 `generateMockTrackingNumber()` 私有方法（邏輯移至 Provider）

**T-003-6：新增 ErrorCode + GlobalExceptionHandler**
```
backend/src/main/java/com/nextkey/ecommerce/shared/exception/ErrorCode.java
backend/src/main/java/com/nextkey/ecommerce/api/dto/GlobalExceptionHandler.java
```
- [x] 新增 `E_7503("E-7503", "Logistics provider not found")`
- [x] E_7503 → 404 NOT_FOUND（GlobalExceptionHandler）
- [x] 補正 E_7500/E_7501 → 404 NOT_FOUND（原本未在 handler 中，一併修正）

**T-003-7：單元測試**
```
backend/src/test/java/com/nextkey/ecommerce/core/logistics/provider/LogisticsProviderFactoryTest.java
```
- [x] TC-L001: `getProvider("HCT")` → HCTLogisticsProvider
- [x] TC-L002: `getProvider("TCAT")` → TCATLogisticsProvider
- [x] TC-L003: `getProvider("UNKNOWN")` → BusinessException E_7503
- [x] 3/3 通過

**T-003-9：驗證**
- [x] `mvn test -Dtest=LogisticsProviderFactoryTest` 3/3 通過
- [x] `mvn verify` BUILD SUCCESS（Checkstyle 0 + PMD pass + Failsafe 285/285）

---

## US-004：AI-503 — getRatingStats @Cacheable 效能優化

> **SP**: 1 | **優先級**: P1 | **狀態**: ✅ 完成（2026-06-27）

### 任務清單

**T-004-1：啟用 Spring Cache + 設定 TTL**
```
backend/src/main/java/com/nextkey/ecommerce/EcommerceApplication.java
backend/src/main/resources/application.yml
```
- [x] 新增 `@EnableCaching` 至 `EcommerceApplication`
- [x] `application.yml` 新增 `spring.cache.redis.time-to-live: 300000`（5 分鐘）
- [x] 生產環境自動使用 `RedisCacheManager`；測試環境用 `spring.cache.type=simple` 覆蓋

**T-004-2：新增 `@Cacheable` 至 `ReviewService.getRatingStats()`**
```
backend/src/main/java/com/nextkey/ecommerce/core/review/ReviewService.java
```
- [x] `@Cacheable(value = "ratingStats", key = "#listingId")`（實際參數名為 `listingId` 非 `productId`）

**T-004-3：新增 `@CacheEvict` 至 `ReviewService.createReview()`**
```
backend/src/main/java/com/nextkey/ecommerce/core/review/ReviewService.java
```
- [x] `@CacheEvict(value = "ratingStats", key = "#request.listingId")`

**T-004-4：整合測試**
```
backend/src/test/java/com/nextkey/ecommerce/core/review/ReviewServiceCacheIntegrationTest.java
```
- [x] TC-R001: 第一次呼叫 → Repository 被呼叫一次
- [x] TC-R002: 第二次呼叫（同 listingId）→ 快取命中，Repository 不呼叫
- [x] TC-R003: 快取清除後 → Repository 再次被呼叫
- [x] 3/3 通過（`@TestPropertySource(properties = "spring.cache.type=simple")`）

**T-004-5：驗證**
- [x] `mvn failsafe:integration-test -Dit.test=ReviewServiceCacheIntegrationTest` 3/3 通過
- [x] `mvn verify` BUILD SUCCESS（Failsafe 288/288）

---

## US-005：M14 平台管理台 — 租戶活躍統計 API

> **SP**: 2 | **優先級**: P1 | **狀態**: ✅ 完成（2026-06-27）

### 任務清單

**T-005-1：新增 Repository Query Method**
```
backend/src/main/java/com/nextkey/ecommerce/domain/repository/OrderRepository.java
backend/src/main/java/com/nextkey/ecommerce/domain/repository/UserRepository.java
backend/src/main/java/com/nextkey/ecommerce/domain/repository/ListingRepository.java
```
- [x] `OrderRepository`: 新增 `countByTenantIdAndCreatedAtAfter(UUID, Instant)` + `findTopByTenantIdOrderByCreatedAtDesc(UUID, Pageable)`
- [x] `UserRepository`: 新增 `countByTenantId(UUID)`
- [x] `ListingRepository`: 新增 `countByTenantIdAndStatus(UUID, Listing.ListingStatus)`

**T-005-2：新增 `AdminDto.TenantStatsResponse`**
```
backend/src/main/java/com/nextkey/ecommerce/api/dto/AdminDto.java
```
- [x] 新增 inner class（@Data @Builder @NoArgsConstructor @AllArgsConstructor）：
  - `UUID tenantId`, `long orderCount30d`, `long activeUserCount`, `long activeListingCount`, `Instant lastOrderAt`

**T-005-3：新增 `AdminService.getTenantStats()`**
```
backend/src/main/java/com/nextkey/ecommerce/core/admin/AdminService.java
```
- [x] 新增方法：查詢近 30 天訂單數（STATS_ORDER_WINDOW_DAYS 常數）、活躍用戶數、活躍商品數、最後訂單時間

**T-005-4：新增 API 端點**
```
backend/src/main/java/com/nextkey/ecommerce/api/controller/AdminController.java
```
- [x] `GET /v2/admin/tenants/{tenantId}/stats`，`@PreAuthorize("hasRole('SUPER_ADMIN')")`

**T-005-5：E2E 測試**
```
backend/src/test/java/com/nextkey/ecommerce/api/controller/AdminControllerE2ETest.java
```
- [x] TC-US005-01: SUPER_ADMIN → 200 + TenantStatsResponse（tenantId, orderCount30d, activeUserCount, activeListingCount）
- [x] TC-US005-02: BUYER → 403

**T-005-6：驗證**
- [x] `mvn verify` BUILD SUCCESS（290 個測試，0 失敗，Checkstyle 0 violations）

---

## US-006（Buffer-A）：M11 物流追蹤 — 運費模板 CRUD API

> **SP**: 2 | **優先級**: Buffer-A | **狀態**: ⬜ 未開始
> **啟動條件**: US-001~005 全部完成，且剩餘 Sprint 時間充足

### 任務清單

**T-006-1：Flyway Migration V43**
```
backend/src/main/resources/db/migration/V43__Create_Shipping_Templates.sql
```
- [ ] 建立 SQL：
  ```sql
  CREATE TABLE shipping_templates (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      tenant_id UUID NOT NULL REFERENCES tenants(id),
      name VARCHAR(100) NOT NULL,
      fee_type VARCHAR(20) NOT NULL CHECK (fee_type IN ('FIXED', 'FREE_THRESHOLD')),
      fixed_amount DECIMAL(10,2),
      free_threshold DECIMAL(10,2),
      created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
      updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
  );
  CREATE INDEX idx_shipping_templates_tenant_id ON shipping_templates(tenant_id);
  ```

**T-006-2：Domain Model**
```
backend/src/main/java/com/nextkey/ecommerce/domain/model/logistics/ShippingTemplate.java
```
- [ ] `@Entity`, `@Table(name = "shipping_templates")`，對應 V43 欄位

**T-006-3：Repository**
```
backend/src/main/java/com/nextkey/ecommerce/domain/repository/ShippingTemplateRepository.java
```
- [ ] `JpaRepository<ShippingTemplate, UUID>`
- [ ] 新增 `findByTenantId(UUID tenantId)`

**T-006-4：Service**
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/ShippingTemplateService.java
```
- [ ] CRUD 方法 + `calculateFee(UUID templateId, BigDecimal orderAmount)`
  - `FIXED`：直接回傳 `fixedAmount`
  - `FREE_THRESHOLD`：`orderAmount >= freeThreshold ? 0 : fixedAmount`

**T-006-5：DTO + Controller**
```
backend/src/main/java/com/nextkey/ecommerce/api/dto/ShippingTemplateDto.java
backend/src/main/java/com/nextkey/ecommerce/api/controller/ShippingTemplateController.java
```
- [ ] `POST/GET/PUT/DELETE /v2/shipping-templates`

**T-006-6：測試**
- [ ] 單元測試：`calculateFee()` FIXED 類型 + FREE_THRESHOLD 達標/未達標
- [ ] 整合測試：CRUD 操作 + 運費計算 API

**T-006-7：驗證**
- [ ] `mvn verify -Pintegration-test -pl backend` BUILD SUCCESS

---

## 每日執行紀錄

| 日期 | 完成項目 | 問題 / 備註 |
|------|---------|------------|
| 2026-06-26 | US-001 完成 | Spring Boot 3.2.x RedisTemplate Micrometer proxy 問題，改用 ReflectionTestUtils 直接測 processMessage |
| 2026-06-26 | US-002 完成 | wiremock-standalone 解決 Jetty classpath 衝突；RequestOptions per-request API key 確保執行緒安全 |
| 2026-06-27 | US-003 完成 | LogisticsProvider 策略模式；TCAT 對應既有 enum（非 SINOPAC）；E_7500/E_7501 補正至 GlobalExceptionHandler |
| 2026-06-27 | US-004 完成 | @EnableCaching + @Cacheable(ratingStats) + @CacheEvict；測試用 spring.cache.type=simple 避免 Redis 依賴 |

---

## Sprint 進度追蹤

| 指標 | 目標 | 實際 |
|------|------|------|
| P0 US 完成數 | 2/2 | 2/2 ✅ |
| P1 US 完成數 | 3/3 | 2/3 |
| Buffer US 完成數 | 視進度 | — |
| 測試數量 | 610+ | 進行中 |
| catch(Exception) 數 | 0 | 0 ✅ |
| @Deprecated 數 | 0 | 0 ✅ |

---

**文件版本**: v1.0
**建立日期**: 2026-06-26
**建立者**: Dev David + QA Quincy（AISDLC 協作）
