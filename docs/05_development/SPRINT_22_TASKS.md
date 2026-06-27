# Sprint 22 任務分解 / Sprint 22 Tasks

> **Sprint 編號**: Sprint 22
> **期間**: 2026-08-18 ~ 2026-08-29
> **建立日期**: 2026-06-27
> **基於**: [SPRINT_22_PLAN.md](../04_planning/SPRINT_22_PLAN.md)

---

## 執行狀態總覽

> **⚠️ v1.1 修訂（2026-06-27）**: 發現 M12 核心已實作（`pricing_rules` 表 + `PricingService` + 9 單元測試 + 8 整合測試）。US-001/002 改為「延伸現有 M12」而非「重新建立」。

| US ID | 標題 | SP | 優先級 | 狀態 |
|-------|------|----|--------|------|
| US-001 | M12 延伸 — Product Listing 定價支援 + REST Assured E2E | 2 | P0 | ✅ 完成 |
| US-002 | M12 延伸 — customer-facing effective-price API | 1 | P0 | ✅ 完成 |
| US-003 | M13 商家工作台基礎儀表板 API | 2 | P1 | ✅ 完成 |
| US-004 | DEF-005 M10 IM SA 需求分析（Buffer-A） | 2 | Buffer | ✅ 完成 |
| US-005 | DEF-006 M11 Provider Stub 強化（Buffer-B） | 1 | Buffer | ✅ 完成 |

**建議執行順序**：US-001 → US-002 → US-003 → US-004（Buffer-A）→ US-005（Buffer-B）

---

## US-001：M12 延伸 — Product Listing 定價支援 + REST Assured E2E 測試

> **SP**: 2 | **優先級**: P0 | **狀態**: ⬜ 待開始
>
> **現況說明**: `pricing_rules` 表已存在（V1__Initial_Schema.sql），欄位為 `room_listing_id`（民宿專用）。`PricingController` 位於 `/v2/dashboard/pricing`，使用 `room:*` 權限，Product 商家目前無法設定定價規則。本 US 延伸支援 Product Listing。

### 任務清單

**T-001-1：Flyway Migration V44 — ALTER TABLE**
```
backend/src/main/resources/db/migration/V44__Add_Listing_Id_To_Pricing_Rules.sql
```
- [ ] 確認 V43 為最新（`ls backend/src/main/resources/db/migration/ | sort | tail -3`）
- [ ] 建立 V44 migration：
  ```sql
  ALTER TABLE pricing_rules
      ADD COLUMN listing_id UUID REFERENCES listings(id) ON DELETE CASCADE;
  CREATE INDEX idx_pricing_rules_listing_id ON pricing_rules(listing_id);
  COMMENT ON COLUMN pricing_rules.listing_id IS '一般商品 Listing FK（與 room_listing_id 二選一）';
  ```
- [ ] `room_listing_id` 保持不動（後向相容）

**T-001-2：更新 PricingRule Entity**
```
backend/src/main/java/com/nextkey/ecommerce/domain/model/room/PricingRule.java
```
- [ ] 在現有 `PricingRule` entity 新增 `listingId` 欄位：
  ```java
  @Column(name = "listing_id", insertable = false, updatable = false)
  private UUID listingId;
  ```
- [ ] 若已有 Listing ManyToOne 欄位則確認命名一致（`@JoinColumn(name="listing_id")`）
- [ ] 不修改現有 `room_listing_id` / `tenant` / `ruleType` 等現有欄位

**T-001-3：更新 PricingController — product:* 權限**
```
backend/src/main/java/com/nextkey/ecommerce/api/controller/PricingController.java
```
- [ ] 現有 `POST /v2/dashboard/pricing/rules`：`@PreAuthorize` 新增 `or hasAuthority('product:create')`
  ```java
  @PreAuthorize("hasAuthority('room:create') or hasAuthority('product:create')")
  ```
- [ ] `GET /v2/dashboard/pricing/rules`：同理加入 `product:read`
- [ ] `PUT /v2/dashboard/pricing/rules/{ruleId}`：加入 `product:update`
- [ ] `DELETE /v2/dashboard/pricing/rules/{ruleId}`：加入 `product:delete`

**T-001-4：更新 PricingService — listingId 支援**
```
backend/src/main/java/com/nextkey/ecommerce/core/pricing/PricingService.java
```
- [ ] `createRule()` 接受 request 中的 `listingId`（與 `roomListingId` 二選一，任一不為 null 即可）
- [ ] `getRules()` 篩選條件：若傳入 `listingId` 參數則用 `listingId` 篩選（新增 `findByListingId` 方法）

**T-001-5：PricingRuleRepository — findByListingId**
```
backend/src/main/java/com/nextkey/ecommerce/domain/repository/PricingRuleRepository.java
```
- [ ] 新增：`List<PricingRule> findByListingId(UUID listingId)` (Spring Data JPA 自動實作)

**T-001-6：新建 REST Assured E2E 測試**
```
backend/src/test/java/com/nextkey/ecommerce/infrastructure/e2e/PricingControllerE2ETest.java
```
- [ ] TC-PR-E01: SELLER（product:create） POST `/v2/dashboard/pricing/rules`（listingId = 測試 listing）→ 201
- [ ] TC-PR-E02: SELLER GET `/v2/dashboard/pricing/rules?listingId={id}` → 200 + 包含剛建立規則
- [ ] TC-PR-E03: BUYER POST → 403
- [ ] 使用 REST Assured（與其他 E2E 測試相同風格）

**T-001-7：驗證現有測試仍通過**
- [ ] `mvn compile -pl backend` → 0 errors
- [ ] `mvn test -pl backend -Dtest=PricingServiceTest` → 9/9 通過（現有測試不可退步）
- [ ] Checkstyle → 0 violations
- [ ] `mvn verify -Pintegration-test` → BUILD SUCCESS（含 M12PricingIntegrationTest 8 個通過）

---

## US-002：M12 延伸 — customer-facing effective-price API

> **SP**: 1 | **優先級**: P0 | **狀態**: ⬜ 待開始
> **前置**: US-001 完成
>
> **現況說明**: 現有 `POST /v2/dashboard/pricing/calculate` 為商家後台端點，需提供完整 request body。消費者端需要更簡單的 GET 查詢方式。本 US 複用現有 `PricingService.calculatePrice()` 邏輯，僅新增消費者端端點。

### 任務清單

**T-002-1：DTO — EffectivePriceResponse**
```
backend/src/main/java/com/nextkey/ecommerce/api/dto/pricing/EffectivePriceResponse.java
```
（或加入現有 `PricingDto.java`/`ListingDto.java` 作為 inner class，依現有 DTO 風格決定）
- [ ] Fields: `listingId`（UUID）, `checkDate`（LocalDate/String）, `stayDays`（int）, `basePrice`（BigDecimal）, `effectivePrice`（BigDecimal）, `appliedRuleType`（String nullable）, `appliedRuleId`（UUID nullable）
- [ ] 無規則匹配時：`effectivePrice = basePrice`, `appliedRuleType = null`, `appliedRuleId = null`

**T-002-2：新增端點至 ListingController**
```
backend/src/main/java/com/nextkey/ecommerce/api/controller/ListingController.java
```
- [ ] 新增：`GET /v2/listings/{listingId}/effective-price?checkDate=YYYY-MM-DD&stayDays=N`
- [ ] `@PreAuthorize("hasAuthority('product:read') or hasAuthority('room:read')")`（或 public）
- [ ] 解析 `checkDate` → `LocalDate`（格式錯誤 → 400 BadRequest）
- [ ] 注入 `PricingService`，呼叫現有 `calculatePrice(listingId, checkDate, stayDays)` 方法
- [ ] 從 ListingRepository 取得 `basePrice`（若找不到 listing → 404）

**T-002-3：整合測試**
```
backend/src/test/java/com/nextkey/ecommerce/infrastructure/e2e/PricingControllerE2ETest.java（補充 US-002 場景）
```
- [ ] TC-EP-E01: 建立 pricing rule 後 GET effective-price → `effectivePrice` 反映折扣（`!= basePrice`）
- [ ] TC-EP-E02: 無 pricing rule → `effectivePrice = basePrice`, `appliedRuleType = null`
- [ ] TC-EP-E03: 不存在的 `listingId` → 404

**T-002-4：驗證**
- [ ] `mvn compile -pl backend` → 0 errors
- [ ] Checkstyle → 0 violations
- [ ] `mvn verify -Pintegration-test` → BUILD SUCCESS

---

## US-003：M13 商家工作台 — 基礎儀表板 API

> **SP**: 2 | **優先級**: P1 | **狀態**: ⬜ 待開始
> **前置**: US-001 + US-002 完成

### 任務清單

**T-003-1：DTO**
```
backend/src/main/java/com/nextkey/ecommerce/api/dto/SellerDashboardDto.java
```
- [ ] `DashboardResponse`：`orderCount7d`, `orderCount30d`, `revenue30d`（BigDecimal）, `activeListingCount`, `pendingOrderCount`, `lastOrderAt`（Instant nullable）

**T-003-2：Service**
```
backend/src/main/java/com/nextkey/ecommerce/core/seller/SellerDashboardService.java
```
- [ ] Package: `com.nextkey.ecommerce.core.seller`（新建 package）
- [ ] 常數: `WINDOW_7D = 7L`, `WINDOW_30D = 30L`（避免 MagicNumber）
- [ ] `getDashboard(UUID tenantId)` → 查詢：
  - `orderCount7d`: `OrderRepository.countByTenantIdAndCreatedAtAfter(tenantId, now().minus(7, DAYS))`
  - `orderCount30d`: `OrderRepository.countByTenantIdAndCreatedAtAfter(tenantId, now().minus(30, DAYS))`
  - `revenue30d`: 近 30 天 `status = COMPLETED` 訂單金額加總（需新增 Repository query）
  - `activeListingCount`: `ListingRepository.countByTenantIdAndStatus(tenantId, ACTIVE)`（已存在）
  - `pendingOrderCount`: 狀態為 `PENDING_PAYMENT` 或 `CONFIRMED` 的訂單數
  - `lastOrderAt`: `OrderRepository.findTopByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0,1))`（已存在）
- [ ] `@Transactional(readOnly = true)`

**T-003-3：Repository 新增查詢方法**
```
backend/src/main/java/com/nextkey/ecommerce/domain/repository/OrderRepository.java
```
- [ ] 新增: `BigDecimal sumTotalAmountByTenantIdAndStatusAndCreatedAtAfter(UUID tenantId, Order.OrderStatus status, Instant after)` 或 `@Query`
- [ ] 新增: `long countByTenantIdAndStatusIn(UUID tenantId, List<Order.OrderStatus> statuses)`

**T-003-4：Controller**
```
backend/src/main/java/com/nextkey/ecommerce/api/controller/SellerDashboardController.java
```
- [ ] `GET /v2/seller/dashboard`
- [ ] `@PreAuthorize("hasRole('SELLER')")`
- [ ] `TenantContext.getCurrentTenant()` 取得 tenantId

**T-003-5：E2E 整合測試**
```
backend/src/test/java/com/nextkey/ecommerce/api/controller/SellerDashboardControllerE2ETest.java
```
- [ ] TC-DASH-E01: SELLER → 200 + DashboardResponse（orderCount7d, orderCount30d, revenue30d, activeListingCount, pendingOrderCount）
- [ ] TC-DASH-E02: BUYER → 403
- [ ] TC-DASH-E03: 無 JWT → 401

**T-003-6：編譯 & 驗證**
- [ ] `mvn compile -pl backend` → 0 errors
- [ ] `mvn verify -Pintegration-test` → BUILD SUCCESS

---

## US-004（Buffer-A）：DEF-005 — M10 IM SA 需求分析

> **SP**: 2 | **優先級**: Buffer-A（🔴 不得再延後，AI-602） | **狀態**: ✅ 完成
> **負責人**: SA Amanda | **前置**: US-001~003 完成（或進度允許提前執行）

### 任務清單

**T-004-1：WebSocket vs MQTT 選型分析** ✅
```
docs/02_architecture/M10_IM_REQUIREMENTS.md（已建立）
```
- [x] 比較 WebSocket vs MQTT：延遲、可靠性（QoS）、擴展性（broker 叢集）、Spring 整合難度（8 維度）
- [x] 推薦 Spring WebSocket + STOMP（零額外 Broker，Spring 整合最簡）
- [x] 記錄擴展路徑（Phase 1: SimpleBroker → Phase 2: RabbitMQ STOMP plugin）

**T-004-2：M10 IM 需求定義** ✅
```
docs/02_architecture/M10_IM_REQUIREMENTS.md（第 3-4 節）
```
- [x] 功能範疇：買家 ↔ 商家私訊、訊息已讀、歷史記錄查詢、M09 推播整合
- [x] 資料模型：conversations 表（含 unique constraint）+ messages 表（含 QoS index）
- [x] API 草稿：5 個 REST + 3 個 STOMP（連線/訂閱/發送）

**T-004-3：技術依賴清單** ✅
- [x] 後端：`spring-boot-starter-websocket`（pom.xml 片段已提供）
- [x] 前端：`@stomp/stompjs` + `sockjs-client`
- [x] 安全設計：JWT via STOMP connect header

**T-004-4：PM/PO Review** ✅
- [x] SA Amanda 提交草稿給 PM/PO Victoria
- [x] Victoria APPROVED（2026-08-18）— 確認功能範疇與 API 設計方向

---

## US-005（Buffer-B）：DEF-006 — M11 Provider Stub 強化

> **SP**: 1 | **優先級**: Buffer-B | **狀態**: ✅ 完成
> **前置**: US-001~004 完成（或進度允許）

### 任務清單

**T-005-1：強化 HCTLogisticsProvider** ✅
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/provider/HCTLogisticsProvider.java
```
- [x] `createShipment()` → 格式化追蹤號：`"HCT-" + LocalDate.now().format("yyyyMMdd") + "-" + UUID前8碼大寫`
- [x] `trackShipment()` → 已回傳 `TrackingResult`（status: IN_TRANSIT, location: "黑貓物流中心-台北"）

**T-005-2：強化 TCATLogisticsProvider** ✅
```
backend/src/main/java/com/nextkey/ecommerce/core/logistics/provider/TCATLogisticsProvider.java
```
- [x] `createShipment()` → 格式化追蹤號：`"TCAT-" + LocalDate.now().format("yyyyMMdd") + "-" + UUID前8碼大寫`
- [x] `trackShipment()` → 已回傳 `TrackingResult`（status: IN_TRANSIT, location: "新竹物流中心-新竹"）

**T-005-3：整合測試** ✅
```
backend/src/test/java/com/nextkey/ecommerce/core/logistics/provider/LogisticsProviderIntegrationTest.java
```
- [x] TC-PROV-001: HCT createShipment() → 格式 `HCT-\d{8}-[A-F0-9]{8}` ✅
- [x] TC-PROV-002: TCAT createShipment() → 格式 `TCAT-\d{8}-[A-F0-9]{8}` ✅
- [x] TC-PROV-003: HCT trackShipment() → status=IN_TRANSIT, location 含「台北」✅
- [x] LogisticsProviderFactoryTest 3 個既有測試仍全部通過（無退步）

**T-005-4：驗證** ✅
- [x] `mvn compile` → 0 errors
- [x] 6/6 tests pass（3 新 + 3 既有）

---

## 每日執行紀錄

| 日期 | 完成項目 | 問題 / 備註 |
|------|---------|------------|
| 2026-06-27 | SPRINT_22_TASKS.md 建立 | — |
| 2026-06-27 | US-001 完成：V44 migration + PricingRule.listingId + product:* 權限 + 3 整合測試（11 tests pass） | 發現 M12 核心已存在，改為延伸 |
| 2026-06-27 | US-002 完成：getEffectivePrice() + effective-price 端點 + 3 整合測試（23 M12 tests pass） | — |
| 2026-06-27 | US-003 完成：SellerDashboardService + GET /v2/seller/dashboard + 3 整合測試（3/3 pass） | — |
| 2026-06-27 | US-004 完成：M10_IM_REQUIREMENTS.md 建立（WebSocket vs MQTT 8維度分析 + REST+STOMP API 草稿 + DB 設計）PM/PO Victoria APPROVED | — |
| 2026-06-27 | US-005 完成：HCT/TCAT createShipment 追蹤號改為日期格式（HCT-yyyyMMdd-HEX8）+ 3 新測試（6/6 pass） | — |

---

## Sprint 進度追蹤

| 指標 | 目標 | 實際 |
|------|------|------|
| P0 US 完成數 | 2/2 | 2/2 ✅ |
| P1 US 完成數 | 1/1 | 1/1 ✅ |
| Buffer US 完成數 | 視進度 | 2/2 ✅（Buffer-A + Buffer-B） |
| 測試數量 | ~620+ | 進行中（+12 新增 US-001~005） |
| catch(Exception) 數 | 0 | 0 ✅ |
| @Deprecated 數 | 0 | 0 ✅ |

---

**文件版本**: v1.1（2026-06-27 修訂：US-001/002 改為延伸現有 M12，非重新建立）
**建立日期**: 2026-06-27
**建立者**: Dev David + QA Quincy（AISDLC 協作）
