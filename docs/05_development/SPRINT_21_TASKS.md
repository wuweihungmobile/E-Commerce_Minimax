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
| US-002 | Stripe SDK Phase 3 真實 SDK 整合 | 3 | P0 | ⬜ 未開始 |
| US-003 | M11 LogisticsProvider 策略抽象 | 2 | P1 | ⬜ 未開始 |
| US-004 | AI-503 getRatingStats @Cacheable | 1 | P1 | ⬜ 未開始 |
| US-005 | M14 租戶活躍統計 API | 2 | P1 | ⬜ 未開始 |
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

> **SP**: 3 | **優先級**: P0 | **狀態**: ⬜ 未開始

### 任務清單

**T-002-1：新增 Stripe SDK 依賴**
```
backend/pom.xml
```
- [ ] 新增依賴：
  ```xml
  <dependency>
      <groupId>com.stripe</groupId>
      <artifactId>stripe-java</artifactId>
      <version>24.3.0</version>
  </dependency>
  ```
- [ ] 確認無版本衝突（`mvn dependency:tree`）

**T-002-2：新增 WireMock 依賴（若未存在）**
```
backend/pom.xml（test scope）
```
- [ ] 確認 `com.github.tomakehurst:wiremock-jre8` 在 test scope 中存在
- [ ] 若未存在，新增：
  ```xml
  <dependency>
      <groupId>com.github.tomakehurst</groupId>
      <artifactId>wiremock-jre8</artifactId>
      <scope>test</scope>
  </dependency>
  ```

**T-002-3：配置 Stripe API Key**
```
backend/src/main/resources/application.properties
backend/src/test/resources/application-test.properties
```
- [ ] `application.properties` 新增：
  ```properties
  stripe.secret.key=${STRIPE_SECRET_KEY:sk_test_placeholder}
  stripe.base.url=https://api.stripe.com
  ```
- [ ] `application-test.properties` 新增：
  ```properties
  stripe.base.url=http://localhost:${wiremock.port:8089}
  ```

**T-002-4：修改 `StripePaymentGateway`**
```
backend/src/main/java/com/nextkey/ecommerce/infrastructure/payment/StripePaymentGateway.java
```
- [ ] 注入 `@Value("${stripe.secret.key}") String apiKey`
- [ ] `createPaymentIntent()` 改用真實 SDK：
  ```java
  Stripe.apiKey = apiKey;
  PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
      .setAmount(request.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
      .setCurrency(request.getCurrency().toLowerCase())
      .putMetadata("order_id", request.getOrderId().toString())
      .setIdempotencyKey(request.getOrderId().toString())
      .build();
  PaymentIntent intent = PaymentIntent.create(params);
  ```
- [ ] 捕捉 `CardException` → `ErrorCode.PAYMENT_CARD_DECLINED`
- [ ] 捕捉 `StripeException`（其他）→ `ErrorCode.PAYMENT_PROVIDER_ERROR`

**T-002-5：WireMock 測試配置**
```
backend/src/test/java/com/nextkey/ecommerce/infrastructure/payment/StripeWireMockConfig.java
backend/src/test/java/com/nextkey/ecommerce/infrastructure/payment/StripePaymentGatewayTest.java
```
- [ ] 建立 `StripeWireMockConfig` `@TestConfiguration`，啟動 WireMock server
- [ ] 在 `StripePaymentGatewayTest` 中：
  - 測試 1：`createPaymentIntent()` 成功（WireMock stub 200 + PaymentIntent JSON）
  - 測試 2：`createPaymentIntent()` CardException（WireMock stub 402 card_declined）
  - 測試 3：驗證請求 header 包含 `Idempotency-Key: {orderId}`

**T-002-6：整合測試更新**
```
backend/src/test/java/com/nextkey/ecommerce/api/controller/OrderPaymentControllerE2ETest.java
```
- [ ] 新增 Stripe PaymentIntent 建立 E2E 測試（使用 WireMock）

**T-002-7：驗證**
- [ ] `mvn test -pl backend -Dtest=StripePaymentGatewayTest` 全部通過
- [ ] `mvn verify -Pintegration-test -pl backend` BUILD SUCCESS

---

## US-003：M11 物流追蹤 — LogisticsProvider 策略抽象

> **SP**: 2 | **優先級**: P1 | **狀態**: ⬜ 未開始

### 任務清單

**T-003-1：定義 `LogisticsProvider` 介面**
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/provider/LogisticsProvider.java
```
- [ ] 建立介面：
  ```java
  public interface LogisticsProvider {
      String getProviderCode();
      LogisticsDto.ShipmentResult createShipment(LogisticsDto.CreateRequest request);
      LogisticsDto.TrackingResult trackShipment(String trackingNumber);
  }
  ```

**T-003-2：實作 `HCTLogisticsProvider`（黑貓宅急便 Stub）**
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/provider/HCTLogisticsProvider.java
```
- [ ] `@Service` + `@Slf4j`
- [ ] `getProviderCode()` 回傳 `"HCT"`
- [ ] `createShipment()` 回傳模擬追蹤號（`"HCT-" + UUID.randomUUID()`）
- [ ] `trackShipment()` 回傳模擬物流狀態（IN_TRANSIT / DELIVERED 交替）

**T-003-3：實作 `SinoPacLogisticsProvider`（新竹物流 Stub）**
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/provider/SinoPacLogisticsProvider.java
```
- [ ] `getProviderCode()` 回傳 `"SINOPAC"`
- [ ] 與 HCT 結構相同，追蹤號前綴 `"SP-"`

**T-003-4：建立 `LogisticsProviderFactory`**
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/provider/LogisticsProviderFactory.java
```
- [ ] 使用 `Map<String, LogisticsProvider>` 自動注入所有 Provider Bean：
  ```java
  @Service
  public class LogisticsProviderFactory {
      private final Map<String, LogisticsProvider> providers;
      
      public LogisticsProviderFactory(List<LogisticsProvider> providerList) {
          this.providers = providerList.stream()
              .collect(Collectors.toMap(LogisticsProvider::getProviderCode, p -> p));
      }
      
      public LogisticsProvider getProvider(String code) {
          LogisticsProvider provider = providers.get(code);
          if (provider == null) {
              throw new BusinessException(ErrorCode.LOGISTICS_PROVIDER_NOT_FOUND, code);
          }
          return provider;
      }
  }
  ```

**T-003-5：修改 `LogisticsService`**
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/LogisticsService.java
```
- [ ] 注入 `LogisticsProviderFactory`
- [ ] `createLogistics()` 透過 Factory 取得 Provider，呼叫 `provider.createShipment(request)`
- [ ] 現有對外介面（Controller 層）不變

**T-003-6：新增 ErrorCode**
```
backend/src/main/java/com/nextkey/ecommerce/shared/exception/ErrorCode.java
```
- [ ] 新增 `LOGISTICS_PROVIDER_NOT_FOUND`（若未存在）

**T-003-7：單元測試**
```
backend/src/test/java/com/nextkey/ecommerce/core/logistics/provider/LogisticsProviderFactoryTest.java
```
- [ ] 測試：`getProvider("HCT")` 回傳 `HCTLogisticsProvider`
- [ ] 測試：`getProvider("SINOPAC")` 回傳 `SinoPacLogisticsProvider`
- [ ] 測試：`getProvider("UNKNOWN")` 拋出 `BusinessException(LOGISTICS_PROVIDER_NOT_FOUND)`

**T-003-8：整合測試更新**
- [ ] 確認現有 `LogisticsControllerE2ETest` 仍通過
- [ ] 新增測試：建立物流單（provider = HCT）回傳追蹤號符合格式

**T-003-9：驗證**
- [ ] `mvn test -pl backend -Dtest=LogisticsProviderFactoryTest` 全部通過
- [ ] `mvn verify -Pintegration-test -pl backend` BUILD SUCCESS

---

## US-004：AI-503 — getRatingStats @Cacheable 效能優化

> **SP**: 1 | **優先級**: P1 | **狀態**: ⬜ 未開始

### 任務清單

**T-004-1：確認 Redis Cache 配置**
```
backend/src/main/resources/application.yml（或 application.properties）
```
- [ ] 確認 `spring.cache.type=redis` 已設定
- [ ] 新增 cache 配置（若使用 YAML）：
  ```yaml
  spring:
    cache:
      redis:
        time-to-live: 300000  # 5 分鐘（毫秒）
  ```

**T-004-2：新增 `@Cacheable` 至 `ReviewService.getRatingStats()`**
```
backend/src/main/java/com/nextkey/ecommerce/core/review/ReviewService.java（或 ReviewStatsService）
```
- [ ] 找到 `getRatingStats(UUID productId)` 方法（Sprint 20 US-006 建立）
- [ ] 加入 `@Cacheable(value = "ratingStats", key = "#productId")`

**T-004-3：新增 `@CacheEvict` 至 `ReviewService.createReview()`**
- [ ] 找到 `createReview()` 方法
- [ ] 加入 `@CacheEvict(value = "ratingStats", key = "#request.productId")`
  （調整 key 表達式以匹配實際參數名稱）

**T-004-4：單元測試**
```
backend/src/test/java/com/nextkey/ecommerce/core/review/ReviewServiceCacheTest.java
```
- [ ] 新增 `@SpringBootTest` 快取測試：
  - 第一次呼叫 `getRatingStats(productId)` → Repository 被呼叫 1 次
  - 第二次呼叫（相同 productId）→ Repository 被呼叫 0 次（命中快取）
  - 呼叫 `createReview()` 後再呼叫 `getRatingStats()` → Repository 再次被呼叫 1 次（快取失效）

**T-004-5：驗證**
- [ ] `mvn test -pl backend -Dtest=ReviewServiceCacheTest` 全部通過
- [ ] `mvn verify -Pintegration-test -pl backend` BUILD SUCCESS

---

## US-005：M14 平台管理台 — 租戶活躍統計 API

> **SP**: 2 | **優先級**: P1 | **狀態**: ⬜ 未開始

### 任務清單

**T-005-1：新增 Repository Query Method（若需要）**
```
backend/src/main/java/com/nextkey/ecommerce/domain/repository/OrderRepository.java
```
- [ ] 確認或新增：
  ```java
  long countByTenantIdAndCreatedAtAfter(UUID tenantId, Instant createdAfter);
  Optional<Instant> findTopByTenantIdOrderByCreatedAtDesc(UUID tenantId);
  ```

**T-005-2：新增 `AdminDto.TenantStatsResponse`**
```
backend/src/main/java/com/nextkey/ecommerce/api/dto/AdminDto.java
```
- [ ] 新增 inner record：
  ```java
  public record TenantStatsResponse(
      UUID tenantId,
      long orderCount30d,
      long activeUserCount,
      long activeListingCount,
      Instant lastOrderAt
  ) {}
  ```

**T-005-3：新增 `AdminService.getTenantStats()`**
```
backend/src/main/java/com/nextkey/ecommerce/core/admin/AdminService.java
```
- [ ] 新增方法：
  ```java
  @Transactional(readOnly = true)
  public AdminDto.TenantStatsResponse getTenantStats(UUID tenantId) {
      Instant thirtyDaysAgo = Instant.now().minus(30, ChronoUnit.DAYS);
      long orderCount30d = orderRepository.countByTenantIdAndCreatedAtAfter(tenantId, thirtyDaysAgo);
      long userCount = userRepository.countByTenantId(tenantId);
      long listingCount = listingRepository.countByTenantIdAndStatus(tenantId, "ACTIVE");
      Instant lastOrderAt = orderRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId)
          .orElse(null);
      return new AdminDto.TenantStatsResponse(tenantId, orderCount30d, userCount, listingCount, lastOrderAt);
  }
  ```

**T-005-4：新增 API 端點**
```
backend/src/main/java/com/nextkey/ecommerce/api/controller/AdminController.java（或適當的 Controller）
```
- [ ] 新增：
  ```java
  @GetMapping("/admin/tenants/{tenantId}/stats")
  @PreAuthorize("hasRole('SUPER_ADMIN')")
  public ResponseEntity<AdminDto.TenantStatsResponse> getTenantStats(
          @PathVariable UUID tenantId) {
      return ResponseEntity.ok(adminService.getTenantStats(tenantId));
  }
  ```

**T-005-5：整合測試**
```
backend/src/test/java/com/nextkey/ecommerce/api/controller/AdminControllerE2ETest.java
```
- [ ] 新增測試：SUPER_ADMIN token 存取 `/v2/admin/tenants/{tenantId}/stats` → 200 + `TenantStatsResponse`
- [ ] 新增測試：普通 ADMIN token 存取 → 403 Forbidden

**T-005-6：驗證**
- [ ] `mvn verify -Pintegration-test -pl backend` BUILD SUCCESS

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
| 開發開始 | — | Sprint 21 計劃確認，開始 US-001 |
| | | |

---

## Sprint 進度追蹤

| 指標 | 目標 | 實際 |
|------|------|------|
| P0 US 完成數 | 2/2 | — |
| P1 US 完成數 | 3/3 | — |
| Buffer US 完成數 | 視進度 | — |
| 測試數量 | 583 + 27 = 610+ | — |
| catch(Exception) 數 | 0 | — |
| @Deprecated 數 | 0 | — |

---

**文件版本**: v1.0
**建立日期**: 2026-06-26
**建立者**: Dev David + QA Quincy（AISDLC 協作）
