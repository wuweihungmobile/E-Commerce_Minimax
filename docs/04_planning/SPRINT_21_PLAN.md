# Sprint 21 計劃 / Sprint 21 Plan

> **Sprint 編號**: Sprint 21
> **期間**: 2026-08-04 ~ 2026-08-15 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-26
> **基於**: [Sprint 20 Retrospective](../05_development/SPRINT_20_RETRO.md) + PM/PO Victoria M10 決策

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 20 開發完成 | ✅ 6/6 US 完成（100% SP） | US-001~006 全部 AC 達成 |
| Sprint 20 測試狀態 | ✅ 583+ tests, 0 Failures | `mvn verify -Pintegration-test` BUILD SUCCESS |
| Sprint 20 Review | ✅ [SPRINT_20_REVIEW.md](../05_development/SPRINT_20_REVIEW.md) | 建立於 2026-06-26 |
| Sprint 20 Retrospective | ✅ [SPRINT_20_RETRO.md](../05_development/SPRINT_20_RETRO.md) | 3 個 Action Items（AI-501~503） |
| Sprint 20 Release | ✅ [RELEASE_NOTES_v2026.08.01-01.md](../08_deployment/RELEASE_NOTES_v2026.08.01-01.md) | Tag `v2026.08.01-01` 已建立 |
| Sprint 20 Action Items | ✅ AI-501~503 已建立 | 納入 Sprint 21 規劃 |
| M10 PM/PO 決策 | ✅ Victoria 決策：DEFER to Phase 2（Sprint 23+） | 見下方「M10 決策說明」 |

### 🔴 M10 即時通訊（IM）PM/PO 決策說明

> **決策者**: PM/PO Victoria
> **決策日期**: 2026-06-26
> **決策結果**: **M10 IM 延後至 Phase 2（Sprint 23+）**

| 考量因素 | 說明 |
|---------|------|
| 技術成熟度 | WebSocket/MQTT 需全新基礎設施，無前置架構，技術風險高 |
| 前置需求 | 須先由 SA Amanda 進行完整需求分析 + SD Marcus 架構設計（計劃於 Sprint 22 Buffer） |
| Sprint 容量 | Sprint 21 已有高價值的 AI-502、Stripe Phase 3、M11 Provider 抽象 |
| Phase 1 完整性 | Stripe 真實 SDK 整合和 M11 Provider 抽象仍是 Phase 1 技術欠債 |
| PRD 定義 | M10 定義為 Phase 2+，P2，非 Phase 1 核心交付範圍 |

**下一步（M10 路線圖）**：
- Sprint 22 Buffer：SA Amanda 完成 M10 IM 需求分析（WebSocket/MQTT 選型、API 設計）
- Sprint 23+：SD Marcus 架構設計 → 開發啟動

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 21 |
| **開始日期** | 2026-08-04 (週一) |
| **結束日期** | 2026-08-15 (週五) |
| **Sprint 容量** | 15 SP |
| **規劃 SP** | 12 SP |
| **Buffer** | 3 SP（20%，達成 AI-501 目標） |
| **團隊** | 2 人 Dev Team |

> **容量說明（AI-501 落地）**: Sprint 20 Velocity 10 SP。AI-501 建議 Buffer 從 27% 降至 20%，規劃 SP 從 11 提升至 12 SP。Buffer 3 SP 用於 Buffer-B（M10 SA 需求分析）/ Buffer-C（M11 強化）候選項目。

---

## 2. Sprint 目標

> **目標**: 完成 notification_history 一致性保障（AI-502）與 Stripe SDK 真實整合（Phase 3），提升 M11 物流架構彈性（Provider 策略抽象），並達成 AI-503 效能優化。所有 P0/P1 項目須 100% 完成。

### 具體目標

#### 🎯 P0 必須完成

| 功能 | 優先級 | 依據 |
|------|--------|------|
| AI-502: notification_history MQ Consumer 端一致性改善 | P0 | AI-502（Sprint 20 Retro） |
| Stripe SDK Phase 3: 真實 Stripe Java SDK 整合 | P0 | 前序 Sprint Buffer 項目 |

#### 🏗️ P1 重要項目

| 功能 | 優先級 | 依據 |
|------|--------|------|
| M11 物流追蹤: LogisticsProvider 策略抽象（HCT/SP Stub） | P1 | Phase 1 架構完整性 |
| AI-503: getRatingStats @Cacheable（Redis TTL 5分鐘） | P1 | AI-503（Sprint 20 Retro） |
| M14 平台管理台: 租戶活躍統計 API | P1 | Phase 1 管理功能 |

#### 🔄 Buffer 候選項目

| 功能 | 優先級 | 預估 SP |
|------|--------|---------|
| US-006: M11 運費模板 CRUD API | Buffer-A | 2 SP |
| M10 IM SA 需求分析（文件，不開發） | Buffer-B | 2 SP |
| M11 第三方 Provider API Stub 強化 | Buffer-C | 1 SP |

---

## 3. User Stories

### 📋 P0 必須完成

---

#### US-001: AI-502 — notification_history MQ Consumer 端一致性改善

> **優先級**: P0 | **Story Points**: 2 SP | **負責人**: Dev David

**User Story**:
```
作為平台用戶
我想要通知歷史記錄在 MQ Consumer 確認（ACK）後才寫入
以便確保歷史不遺漏且至少一次交付語義得到保障
```

**現況問題**：`NotificationService.sendNotification()` 在發送端用 try-catch 寫入 history，若 Consumer 端未處理訊息，history 仍會被寫入（語義不正確）。

**改善方案**：將 history 寫入移至 `NotificationConsumerService.processMessage()` 成功後執行。

**驗收標準**:
- [ ] AC-001: `NotificationConsumerService.processMessage()` 成功處理訊息後，呼叫 `NotificationHistoryService.createHistory()`
- [ ] AC-002: history 寫入失敗時，訊息仍正常 ACK（容錯設計），並記錄 `log.warn`
- [ ] AC-003: `NotificationService.sendNotification()` 移除原本的 try-catch history 寫入（避免雙寫）
- [ ] AC-004: 整合測試覆蓋「MQ 發送成功 → Consumer 處理 → history 寫入」完整鏈路

**技術備註**:
- `NotificationConsumerService` 注入 `NotificationHistoryService` Bean（已存在）
- 現有 `consumeNotifications()` 呼叫 `processMessage()` → 在 `processMessage()` 成功 return 後呼叫 createHistory
- 需調整 `NotificationService` 同時移除舊寫入路徑

**測試要點（QA Quincy）**:
- 單元測試：Consumer 成功後 `createHistory` 被呼叫（Mockito verify）
- 單元測試：history 拋出 RuntimeException 時，`processMessage` 仍正常執行（不 re-throw）
- 整合測試：完整 MQ → Consumer → history 鏈路（需 Redis Testcontainer）

---

#### US-002: Stripe SDK Phase 3 — 真實 Stripe Java SDK 整合

> **優先級**: P0 | **Story Points**: 3 SP | **負責人**: Dev David + SD Marcus（WireMock 配置）

**User Story**:
```
作為平台財務管理員
我想要 Stripe 支付使用真實 Stripe Java SDK（替換 Mock）
以便實現真實的 PaymentIntent 建立與 Idempotency Key 保護
```

**現況**：`StripePaymentGateway` 為 Mock 實作（Phase 2-B 預留介面），`StripeSignatureVerifierService` 已有真實 HMAC-SHA256 驗證（Sprint 19）。

**驗收標準**:
- [ ] AC-001: `pom.xml` 新增 `com.stripe:stripe-java` 依賴（>= 24.x）
- [ ] AC-002: `StripePaymentGateway.createPaymentIntent()` 改用真實 SDK（`PaymentIntentCreateParams` + `PaymentIntent.create()`）
- [ ] AC-003: 每次 `createPaymentIntent()` 帶入 `idempotencyKey`（訂單 ID 的 UUID 字串），防止重複扣款
- [ ] AC-004: 捕捉 `CardException`（使用者錯誤 → `PAYMENT_CARD_DECLINED`）/ `ApiException`（服務錯誤 → `PAYMENT_PROVIDER_ERROR`）
- [ ] AC-005: WireMock 測試覆蓋 PaymentIntent 建立成功與 `CardException`（card_declined）場景
- [ ] AC-006: `application.properties` 新增 `stripe.secret.key=${STRIPE_SECRET_KEY:sk_test_placeholder}`

**技術備註（SD Marcus）**:
- 使用 `WireMockExtension` 搭配 `@TestConfiguration` 隔離 Stripe API
- 本機/CI 環境透過 `stripe.secret.key` 環境變數注入測試 key
- `StripePaymentGateway` 不使用 `@ConditionalOnProperty`，統一透過 `PaymentGatewayFactory` 路由

**測試要點（QA Quincy）**:
- WireMock stub：`POST /v1/payment_intents` → 200 成功回應
- WireMock stub：`POST /v1/payment_intents` → 402 CardException（card_declined）
- 驗證 Idempotency Key header 存在於 HTTP 請求中

---

### 🏗️ P1 重要項目

---

#### US-003: M11 物流追蹤 — LogisticsProvider 策略抽象

> **優先級**: P1 | **Story Points**: 2 SP | **負責人**: Dev David

**User Story**:
```
作為平台管理員
我想要物流服務支援可擴充的多業者架構
以便未來能輕鬆新增真實黑貓/新竹 API
```

**現況**：`LogisticsService` 為 Mock，無 Provider 抽象介面。

**驗收標準**:
- [ ] AC-001: 定義 `LogisticsProvider` 介面，含 `createShipment(...)`, `trackShipment(String trackingNumber)`, `getProviderCode()` 方法
- [ ] AC-002: 實作 `HCTLogisticsProvider`（黑貓宅急便 Stub），`getProviderCode()` = `"HCT"`
- [ ] AC-003: 實作 `SinoPacLogisticsProvider`（新竹物流 Stub），`getProviderCode()` = `"SINOPAC"`
- [ ] AC-004: `LogisticsProviderFactory` 依 `providerCode` 路由到對應 Provider（Spring Bean Map 注入）
- [ ] AC-005: `LogisticsService.createLogistics()` 透過 `LogisticsProviderFactory` 取得 Provider，不再硬編碼 Mock 邏輯
- [ ] AC-006: 單元測試覆蓋 Factory 路由（HCT / SINOPAC / 未知業者拋 `BusinessException`）

**技術備註（SD Marcus）**:
- Strategy Pattern：`Map<String, LogisticsProvider>` 由 Spring 自動注入所有實作 Bean
- 現有 `LogisticsService` 對外介面（Controller 層）不變，後向相容

**測試要點（QA Quincy）**:
- 單元測試：Factory 路由 HCT → `HCTLogisticsProvider`
- 單元測試：Factory 路由未知 code → `BusinessException(LOGISTICS_PROVIDER_NOT_FOUND)`
- 整合測試：`POST /v2/logistics` 建立物流單（Provider = HCT）

---

#### US-004: AI-503 — getRatingStats @Cacheable 效能優化

> **優先級**: P1 | **Story Points**: 1 SP | **負責人**: Dev David

**User Story**:
```
作為平台用戶
我想要商品評分統計 API 在高流量下快速回應
以便提升整體使用者體驗
```

**驗收標準**:
- [ ] AC-001: `ReviewService.getRatingStats(UUID productId)` 加入 `@Cacheable(value = "ratingStats", key = "#productId")`
- [ ] AC-002: `ReviewService.createReview()` 在評分新增後觸發 `@CacheEvict(value = "ratingStats", key = "#productId")`
- [ ] AC-003: `application.yml` 新增 Redis cache 配置（name: `ratingStats`, ttl: 300s）
- [ ] AC-004: 單元測試驗證第二次呼叫命中快取（`ReviewRepository` 只被呼叫一次）

**技術備註**:
- Redis 快取基礎設施已就緒，直接套用 Spring Cache Abstraction
- 確認 `spring.cache.type=redis` 已配置

---

#### US-005: M14 平台管理台 — 租戶活躍統計 API

> **優先級**: P1 | **Story Points**: 2 SP | **負責人**: Dev David

**User Story**:
```
作為平台超管（SuperAdmin）
我想要查看各租戶的近期活躍指標
以便做出平台運營決策
```

**驗收標準**:
- [ ] AC-001: 新增 `GET /v2/admin/tenants/{tenantId}/stats` API
- [ ] AC-002: 回應包含 `{tenantId, orderCount30d, activeUserCount, activeListingCount, lastOrderAt}`
- [ ] AC-003: `AdminService.getTenantStats(UUID tenantId)` 查詢真實資料（使用 `OrderRepository`、`UserRepository`、`ListingRepository`）
- [ ] AC-004: 需要 `ROLE_SUPER_ADMIN` 才能存取（`@PreAuthorize("hasRole('SUPER_ADMIN')")`）
- [ ] AC-005: 整合測試：SuperAdmin 存取 → 200 + 統計資料；一般 Admin 存取 → 403

**技術備註**:
- 不需新增 Flyway migration（使用既有 tables）
- `orderCount30d`：`OrderRepository.countByTenantIdAndCreatedAtAfter(tenantId, now().minus(30, DAYS))`
- 若需要，可新增 Repository query method（JPA 自動實作）

---

### 🔄 Buffer-A 候選項目

---

#### US-006（Buffer-A）: M11 物流追蹤 — 運費模板 CRUD API

> **優先級**: Buffer-A | **Story Points**: 2 SP | **負責人**: Dev David
> **啟動條件**: US-001~005 全部完成且剩餘 Sprint 容量充足

**User Story**:
```
作為商家
我想要設定運費模板（固定運費 / 免運門檻）
以便在訂單結帳時自動套用正確運費
```

**驗收標準**:
- [ ] AC-001: Flyway `V43__Create_Shipping_Templates.sql`，欄位：`id, tenant_id, name, fee_type(FIXED/FREE_THRESHOLD), fixed_amount, free_threshold, created_at, updated_at`
- [ ] AC-002: `POST /v2/shipping-templates`（建立）、`GET /v2/shipping-templates`（列表）、`PUT /v2/shipping-templates/{id}`（更新）、`DELETE /v2/shipping-templates/{id}`（刪除）
- [ ] AC-003: `ShippingTemplateService.calculateFee(UUID templateId, BigDecimal orderAmount)` 計算運費（FIXED 直接回費用；FREE_THRESHOLD 滿額免運）
- [ ] AC-004: 整合測試覆蓋 CRUD + 運費計算兩種類型

---

## 4. Sprint 容量分配摘要

| 類別 | US ID | 標題 | SP |
|------|-------|------|----|
| P0 | US-001 | AI-502 notification_history MQ Consumer 一致性 | 2 |
| P0 | US-002 | Stripe SDK Phase 3 真實 SDK 整合 | 3 |
| P1 | US-003 | M11 LogisticsProvider 策略抽象 | 2 |
| P1 | US-004 | AI-503 getRatingStats @Cacheable | 1 |
| P1 | US-005 | M14 租戶活躍統計 API | 2 |
| Buffer-A | US-006 | M11 運費模板 CRUD API | 2 |
| **P0+P1 小計** | | | **10 SP** |
| **含 Buffer-A 合計** | | | **12 SP** |
| **未分配 Buffer** | | Buffer-B（M10 SA 分析）/ Buffer-C（M11 強化）| **3 SP** |
| **Sprint 容量** | | | **15 SP** |

---

## 5. 技術依賴與風險

### 5.1 新增外部依賴

| 依賴 | 版本 | 用途 | 風險 |
|------|------|------|------|
| `com.stripe:stripe-java` | >= 24.x | Stripe SDK Phase 3 | 中 — 需 WireMock 配置 |
| `com.github.tomakehurst:wiremock-jre8` | 已存在或新增 | Stripe API 測試隔離 | 低 |

### 5.2 風險矩陣

| 風險 | 可能性 | 影響 | 緩解措施 |
|------|--------|------|---------|
| Stripe SDK WireMock 配置複雜 | 中 | 中 | Marcus 先搭 WireMock TestConfiguration，David 再接 SDK 邏輯 |
| `NotificationConsumerService` 注入 HistoryService 引發循環依賴 | 低 | 高 | 先 grep 現有 Bean 依賴圖；必要時用 `@Lazy` |
| US-006 Flyway V43 與現有 migration 衝突 | 低 | 中 | 確認最新 migration 為 V42，V43 安全 |

### 5.3 架構影響

| 模組 | 影響說明 |
|------|---------|
| `infrastructure.mq` | `NotificationConsumerService` 新增 `NotificationHistoryService` 依賴 |
| `infrastructure.payment` | `StripePaymentGateway` 替換 Mock 邏輯，引入 `com.stripe:stripe-java` |
| `core.logistics` | 新增 `LogisticsProvider` 介面及兩個 Stub 實作 |
| `core.review` | `ReviewService` 新增 `@Cacheable/@CacheEvict` |
| `core.admin` | `AdminService` 新增 `getTenantStats()` 方法 |

---

## 6. 測試策略（QA Quincy）

### 6.1 Sprint 21 測試計劃

| US | 單元測試 | 整合測試 | 新增測試數（預估）|
|----|---------|---------|-----------------|
| US-001 | `NotificationConsumerServiceTest` (+3) | `NotificationMQIntegrationTest` (+3) | +6 |
| US-002 | `StripePaymentGatewayTest`（WireMock）(+4) | `OrderPaymentControllerE2ETest` (+2) | +6 |
| US-003 | `LogisticsProviderFactoryTest` (+3) | `LogisticsControllerE2ETest` (+2) | +5 |
| US-004 | `ReviewServiceCacheTest` (+2) | — | +2 |
| US-005 | — | `AdminControllerE2ETest` (+2) | +2 |
| US-006 | `ShippingTemplateServiceTest` (+3) | `ShippingTemplateControllerE2ETest` (+3) | +6 |
| **合計** | | | **+27（預估）** |

> **Sprint 後預計測試總數**: 583 + 27 = **610+ tests**

### 6.2 DoD（Definition of Done）

- [ ] 所有 AC 通過整合測試驗證
- [ ] `mvn verify -Pintegration-test` BUILD SUCCESS（0 failures, 0 errors）
- [ ] `catch(Exception)` 生產程式碼維持 **0 處**
- [ ] `@Deprecated` 生產程式碼維持 **0 處**
- [ ] 新程式碼 catch 區塊使用具體例外類型（非 `Exception`）
- [ ] Stripe SDK 整合：WireMock 測試通過，無真實 API 呼叫依賴

---

## 7. Sprint 21 Action Items（來自 Sprint 20 Retro）

| Action Item | 說明 | Sprint 21 處理方式 |
|-------------|------|------------------|
| AI-501 | Buffer 降至 20%，規劃 SP 提升至 12 SP | ✅ Buffer = 3 SP（20%），規劃 12 SP |
| AI-502 | notification_history 一致性改善 | ✅ US-001（P0） |
| AI-503 | getRatingStats @Cacheable | ✅ US-004（P1） |

---

## 8. M10 即時通訊模組路線圖（PM/PO Victoria 批准）

| 時程 | 活動 | 負責人 |
|------|------|--------|
| Sprint 21 | M10 繼續 DEFER，Buffer-B 視進度由 SA 啟動需求調研 | SA Amanda |
| Sprint 22 | SA Amanda 完成 M10 需求分析（WebSocket/MQTT 選型、API 設計文件） | SA Amanda |
| Sprint 23+ | SD Marcus 架構設計 → 開發啟動 | SD Marcus + Dev David |

---

## 9. 相關文件

| 文件 | 路徑 |
|------|------|
| Sprint 20 Retrospective | [SPRINT_20_RETRO.md](../05_development/SPRINT_20_RETRO.md) |
| Sprint 20 Review | [SPRINT_20_REVIEW.md](../05_development/SPRINT_20_REVIEW.md) |
| Sprint 21 Tasks | [SPRINT_21_TASKS.md](../05_development/SPRINT_21_TASKS.md) |
| Release Notes v2026.08.01 | [RELEASE_NOTES_v2026.08.01-01.md](../08_deployment/RELEASE_NOTES_v2026.08.01-01.md) |
| Deferred Items Tracker | [DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md) |

---

**文件版本**: v1.0
**建立日期**: 2026-06-26
**建立者**: PM/PO Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy（AISDLC 協作）
**下一步**: 開始開發 US-001（AI-502），依序執行 P0 → P1 → Buffer-A
