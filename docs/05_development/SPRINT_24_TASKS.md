# Sprint 24 任務分解 / Sprint 24 Tasks

> **Sprint 編號**: Sprint 24
> **期間**: 2026-09-13 ~ 2026-09-26（預估）
> **建立日期**: 2026-06-28
> **基於**: [SPRINT_24_PLAN.md](../04_planning/SPRINT_24_PLAN.md)

---

## 執行狀態總覽

| US ID | 標題 | SP | 優先級 | 狀態 |
|-------|------|----|--------|------|
| US-001 | TestSecurityContextHelper 整合測試標準化（AI-801） | 1 | P1 | ✅ 完成 |
| US-002 | M10 WebSocket STOMP 後端實作（AI-701） | 4 | P1 | ✅ 完成 |
| US-003 | M13 Dashboard Redis TTL + @CacheEvict（AI-803） | 2 | P2 | ✅ 完成 |
| US-004 | SSH pre-push 優化（Buffer-A，AI-804） | 1 | Buffer | ⏸️ 延後 Sprint 25（AI-804） |
| US-005 | M11 物流取消流程評估（Buffer-B） | 2 | Buffer | ⏸️ 延後 Sprint 25（AI-903，需先確認業務規則） |

**當前進度**: 3/3 承諾 US 完成，7 SP（P0+P1 全部完成）。Buffer（US-004/005，3 SP）因 Sprint 後段投入計畫外 E2E schema 救火（V48~V55）未啟動，延續 Sprint 25。

**Sprint 24 收尾**: ✅ Review + Retro + Release Notes（v2026.09.26-01）已建立，DoD 全數通過。詳見 [SPRINT_24_REVIEW.md](./SPRINT_24_REVIEW.md) / [SPRINT_24_RETRO.md](./SPRINT_24_RETRO.md)。

---

## US-001：TestSecurityContextHelper 整合測試標準化（AI-801）

> **SP**: 1 | **優先級**: P1 | **狀態**: ✅ 完成（2026-06-28，commit `50a6d45`）

### 完成交付物

- [x] **TestSecurityContextHelper.java** — 整合測試 SecurityContext 統一設置工具
  - `setUserContext(UUID, String, UUID, String, List<String>)` — 完整參數版
  - `setUserContext(UUID, UUID, String)` — 快速版（使用 role 作為 authority）
  - `clear()` — 清除 SecurityContext
  - 解決 `@WithMockUser` 建立 String principal 導致 `TenantContextFilter` 無法取得 tenantId 的問題

- [x] **M11ShippingFeeIntegrationTest 重構** — 改用 `TestSecurityContextHelper`
  - 移除 `@WithMockUser`，改用 `UserPrincipal` 確保 TenantContext 正確設定
  - 修正 `Matchers.closeTo(double)` → `.value(int)` 解決 BigDecimal JSON 序列化型別不符

### DoD 驗證

- [x] compile 通過
- [x] 單元測試通過（312/312）
- [x] M11ShippingFeeIntegrationTest 3 個測試通過（act CI 驗證）

---

## US-002：M10 WebSocket STOMP 後端實作（AI-701）

> **SP**: 4 | **優先級**: P1 | **狀態**: ✅ 完成（2026-06-28，commit `d671463`）

### 完成交付物

- [x] **pom.xml** — 新增 `spring-boot-starter-websocket` 依賴

- [x] **WebSocketConfig.java** — STOMP 配置
  - Endpoint: `/ws`（SockJS fallback 支援）
  - Broker: `/topic`（廣播）、`/queue`（點對點）
  - Application prefix: `/app`
  - User destination prefix: `/user`
  - 注冊 `StompAuthChannelInterceptor`

- [x] **StompAuthChannelInterceptor.java** — JWT 驗證
  - STOMP CONNECT 時驗證 `Authorization: Bearer {token}`
  - 驗證通過後注入 `UserPrincipal` 至 `StompHeaderAccessor`
  - 使用 `JwtException | IllegalArgumentException` 捕獲解析錯誤

- [x] **ChatService.java 更新** — STOMP 廣播
  - 注入 `SimpMessagingTemplate`
  - `sendMessage()` 後廣播到 `/queue/conversations/{conversationId}/messages`

- [x] **ChatServiceStompBroadcastTest.java** — TC-STOMP-C001~C003
  - 使用 `@ExtendWith(MockitoExtension.class)` 純 Mockito 單元測試
  - 驗證：正確 destination 格式、payload 為 `MessageResponse`
  - 1/1 測試通過

### DoD 驗證

- [x] compile 通過
- [x] 單元測試通過（312/312）
- [x] checkstyle 通過（`catch (JwtException | IllegalArgumentException e)` 符合規範）

---

## US-003：M13 Dashboard Redis TTL + @CacheEvict（AI-803）

> **SP**: 2 | **優先級**: P2 | **狀態**: ✅ 完成（2026-06-28，commit `0aa91cf`）

### 完成交付物

- [x] **RedisConfig.java 更新** — 改用 `RedisCacheManagerBuilderCustomizer`
  - `dashboardStats` cache TTL = 5 分鐘（`Duration.ofMinutes(5)`）
  - 使用 `RedisCacheManagerBuilderCustomizer` 而非顯式 `CacheManager` bean，
    避免 `spring.cache.type=simple` 時（整合測試環境）發生 Redis 連線 NPE
  - JSON 序列化：`GenericJackson2JsonRedisSerializer` + `JavaTimeModule`

- [x] **OrderService.java 更新** — @CacheEvict 觸發
  - `createOrderFromCart()` + `updateOrderStatus()` + `cancelOrder()` 加入
    `@CacheEvict(value = "dashboardStats", allEntries = true)`
  - `allEntries = true` 因 tenantId 從 `TenantContext` 取得，無法直接用 SpEL key

- [x] **RedisCacheConfigTest.java** — TC-CACHE-D001~D004
  - D001: `RedisConfig.DASHBOARD_STATS_TTL == 5 分鐘`
  - D002~D004: `createOrderFromCart/updateOrderStatus/cancelOrder` 的 `@CacheEvict` annotation 驗證
  - 4/4 測試通過

### DoD 驗證

- [x] compile 通過
- [x] 單元測試通過（316/316，含新增 4 個）
- [x] `SellerDashboardServiceCacheTest` 3/3 仍通過（無回歸）
- [x] checkstyle 通過

---

## US-004（Buffer-A）：SSH pre-push 優化（AI-804）

> **SP**: 1 | **優先級**: Buffer | **狀態**: ⬜ 待評估

**目標**: 評估並改善 SSH timeout 問題，避免 act CI 長時間執行導致 push SIGPIPE（exit 141）。

**待評估項目**:
- SSH KeepAlive 設定（`~/.ssh/config` ServerAliveInterval）
- act CI 快取有效期調整（目前 10 分鐘）
- 是否需要在 push 前先確認快取有效期

---

## US-005（Buffer-B）：M11 物流取消流程評估

> **SP**: 2 | **優先級**: Buffer | **狀態**: ⬜ 待評估（依容量決定）

**目標**: 評估並實作 M11 物流取消流程（訂單取消後觸發 `cancelShipment()`）。

**AC-005-1**: 訂單狀態 `CONFIRMED → CANCELLED`，物流單狀態從 `PENDING → CANCELLED`
**AC-005-2**: 訂單狀態 `SHIPPING → CANCELLED`（需業務規則確認）
**AC-005-3**: `cancelShipment()` 呼叫物流供應商 API
