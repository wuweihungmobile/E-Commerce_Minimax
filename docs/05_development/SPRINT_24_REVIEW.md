# Sprint 24 Review / Sprint 24 評審會議

> **Sprint 編號**: Sprint 24
> **期間**: 2026-09-15 ~ 2026-09-26
> **評審日期**: 2026-06-29（AI-AISDLC 延伸：開發完成後即建立）
> **建立日期**: 2026-06-29
> **基於**: [SPRINT_24_PLAN.md](../04_planning/SPRINT_24_PLAN.md), [SPRINT_24_TASKS.md](./SPRINT_24_TASKS.md)

---

## 1. Sprint 目標達成評估

> **Sprint 目標**: 技術品質改善（AI-801 整合測試標準化）+ M10 IM WebSocket 即時訊息（Phase 2）+ M13 Dashboard Redis Cache TTL。

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| TestSecurityContextHelper 整合測試標準化（AI-801） | ✅ 達成 | 共用工具類建立，M11ShippingFeeIntegrationTest 改用 UserPrincipal |
| M10 WebSocket STOMP 後端實作（AI-701 延續） | ✅ 達成 | WebSocketConfig + StompAuthChannelInterceptor + ChatService 廣播 |
| M13 Dashboard Redis TTL + @CacheEvict（AI-803） | ✅ 達成 | dashboardStats TTL 5 分鐘 + 3 個 @CacheEvict 觸發點 |
| Buffer-A SSH pre-push 優化（AI-804） | ⏸️ 保留評估 | 容量被 E2E 救火佔用，延續至 Sprint 25 評估 |
| Buffer-B M11 物流取消流程 | ⏸️ 延後 Sprint 25 | 業務規則未定義（高風險），需先確認規則 |

**Sprint 目標達成率**: 100%（承諾範圍 P0+P1 全部完成，US-001~003，7 SP）

---

## 2. User Story 完成狀態

| US | 標題 | SP | 優先級 | 狀態 | Commit |
|----|------|----|--------|------|--------|
| US-001 | TestSecurityContextHelper 整合測試標準化（AI-801） | 1 | P1 | ✅ 完成 | `50a6d45` |
| US-002 | M10 WebSocket STOMP 後端實作（AI-701） | 4 | P1 | ✅ 完成 | `d671463` |
| US-003 | M13 Dashboard Redis TTL + @CacheEvict（AI-803） | 2 | P2 | ✅ 完成 | `0aa91cf` |
| US-004 | SSH pre-push 優化（Buffer-A） | 1 | Buffer | ⏸️ 保留評估 | — |
| US-005 | M11 物流取消流程（Buffer-B） | 2 | Buffer | ⏸️ 延後 Sprint 25 | — |
| **完成合計** | | **7 SP** | | ✅ 100%（承諾範圍） | |

> Sprint 24 承諾 7 SP（P0+P1）全部完成。Buffer（US-004/005，3 SP）因 Sprint 後段投入大量非計畫的 E2E/CI 穩定性救火（V48~V55 schema 修復），未啟動。

---

## 3. 測試狀態

| 測試類型 | Sprint 前（Sprint 23 後） | Sprint 後 | 變化 |
|---------|--------------------------|----------|------|
| `@Test` 方法總數（靜態計數，backend/src/test） | ~643 | **652** | +9 |
| `catch (Exception)` 生產程式碼 | 0 處 | **0 處** ✅ | — |
| `@Deprecated` 生產程式碼 | 0 處 | **0 處** ✅ | — |

**Sprint 24 新增測試明細**:

| US | 新增測試 | Test IDs |
|----|---------|---------|
| US-002 | ChatServiceStompBroadcastTest（STOMP 廣播單元測試） | TC-STOMP-C001~C003 |
| US-003 | RedisCacheConfigTest（+4） | TC-CACHE-D001~D004 |
| US-001 | M11ShippingFeeIntegrationTest 重構（改用 TestSecurityContextHelper，無淨增） | — |

> 測試計數採 `grep -c "@Test"` 靜態方法數（652），與 Sprint 23 Retro 的估算口徑（~643）一致延續。

---

## 4. US-001：TestSecurityContextHelper 整合測試標準化（AI-801）

### 程式碼變更

| 檔案 | 變更內容 |
|------|---------|
| `integration/util/TestSecurityContextHelper.java`（新增） | 統一整合測試 `UserPrincipal` SecurityContext 設置工具 |
| `M11ShippingFeeIntegrationTest.java` | 移除 `@WithMockUser`，改用 `TestSecurityContextHelper.setUserContext()` |

### 工具設計

- `setUserContext(UUID, String, UUID, String, List<String>)` — 完整參數版
- `setUserContext(UUID, UUID, String)` — 快速版（role 作為 authority）
- `clear()` — 清除 SecurityContext
- **解決問題**：`@WithMockUser` 建立 String principal，導致 `TenantContextFilter` 無法取得 tenantId（沿用 Sprint 23 `c5a76bb` 的修正模式，標準化為可複用工具）

---

## 5. US-002：M10 IM WebSocket STOMP 後端實作（AI-701）

### 程式碼變更

| 檔案 | 變更內容 |
|------|---------|
| `pom.xml` | 新增 `spring-boot-starter-websocket` 依賴 |
| `config/WebSocketConfig.java`（新增） | STOMP endpoint `/ws`（SockJS fallback）+ `/topic`/`/queue` broker + `/app`/`/user` prefix |
| `config/StompAuthChannelInterceptor.java`（新增） | STOMP CONNECT JWT 驗證，注入 `UserPrincipal` |
| `service/ChatService.java` | 注入 `SimpMessagingTemplate`，`sendMessage()` 後廣播 |

### STOMP 設計

| 項目 | 設定 |
|------|------|
| Endpoint | `/ws`（SockJS fallback） |
| 廣播 destination | `/queue/conversations/{conversationId}/messages` |
| 認證 | STOMP CONNECT `Authorization: Bearer {token}` → `UserPrincipal` |
| 例外處理 | `catch (JwtException \| IllegalArgumentException e)`（符合 checkstyle，非 catch(Exception)） |

### 測試（TC-STOMP-C001~C003）

純 Mockito 單元測試（`@ExtendWith(MockitoExtension.class)`），驗證廣播 destination 格式正確、payload 為 `MessageResponse`。

> **範圍說明**：WebSocket 前端整合（`@stomp/stompjs`）不在本 Sprint 範圍，僅後端 STOMP 實作。

---

## 6. US-003：M13 Dashboard Redis TTL + @CacheEvict（AI-803）

### 程式碼變更

| 檔案 | 變更內容 |
|------|---------|
| `config/RedisConfig.java` | 改用 `RedisCacheManagerBuilderCustomizer`，`dashboardStats` TTL = 5 分鐘 |
| `service/OrderService.java` | `createOrderFromCart`/`updateOrderStatus`/`cancelOrder` 加 `@CacheEvict(value="dashboardStats", allEntries=true)` |

### Cache 設計

- **TTL**：`dashboardStats` = 5 分鐘（`Duration.ofMinutes(5)`）
- **序列化**：`GenericJackson2JsonRedisSerializer` + `JavaTimeModule`
- **關鍵設計**：使用 `RedisCacheManagerBuilderCustomizer` 而非顯式 `CacheManager` bean，避免 `spring.cache.type=simple`（整合測試環境）時的 Redis 連線 NPE
- **Eviction**：`allEntries=true`（tenantId 從 `TenantContext` 取得，無法直接以 SpEL key 表達）

### 測試（TC-CACHE-D001~D004）

| Test ID | 驗證 |
|---------|------|
| D001 | `RedisConfig.DASHBOARD_STATS_TTL == 5 分鐘` |
| D002~D004 | `createOrderFromCart`/`updateOrderStatus`/`cancelOrder` 的 `@CacheEvict` annotation 存在 |

> `SellerDashboardServiceCacheTest`（Sprint 23 US-006）3/3 仍通過，無回歸。

---

## 7. E2E / CI 穩定性救火（計畫外，Sprint 24 後段）

> Sprint 24 承諾範圍完成後（commit `34258db`），GitHub E2E pipeline 暴露多項 backend 啟動失敗，投入 9 個 commit 救火。此段為**非計畫工作**，列入 Retro 重點檢討。

### 7.1 Schema 漂移修復（Flyway V48~V55，+8 migration）

| Migration | 修復內容 | Commit |
|-----------|---------|--------|
| V48 | `article_versions.tags` 欄位型別不一致 | `0655b11` |
| V49~V53 | 補齊 5 張缺漏建表（cms_banner / page / inventory / logistics / notifications）+ entity schema 漂移修正 | `82ae310` |
| V54 | `media_assets.tags` 型別不一致 | `ff1bd24` |
| V55 | `rooms.amenities` 統一為 jsonb（消除全庫最後一個 ARRAY 映射） | `530a5ad` |

**根本原因**：本地 `act` CI 使用 `ddl-auto=update`（自動補欄位），GitHub E2E 使用 schema 驗證，因此 entity 與 migration 的型別漂移、缺漏建表在本地完全偵測不到，只在 GitHub E2E backend 啟動時爆發。

### 7.2 Playwright / E2E 穩定性

| 修復 | Commit |
|------|--------|
| Playwright 重用既有 server，修正 CI port 3000 衝突；併修 frontend pre-commit hook 路徑 bug | `a1bce43` |
| 移除 `at-m17-002` 重複測試標題；secret 掃描排除 E2E spec 測試檔 | `b5c0c88` |
| `domcontentloaded` 取代 `networkidle` 提升 E2E 穩定性 | `a147af0` |
| playwright-report 停止版控並加入 gitignore（-2095 行） | `40a72c8` |

### 7.3 Docker 清理

| 修復 | Commit |
|------|--------|
| 落實「該部署的才部署、該下載的才下載」並清理 LLM 殘留工具鏈（移除 `download-llm-model.sh` 等，-488 行） | `d036f0c` |

---

## 8. Sprint 23 Action Items 追蹤

| Action Item | 內容 | 達成狀態 |
|-------------|------|---------|
| AI-801 | 整合測試 SecurityContext 標準化 | ✅ 完成（US-001 TestSecurityContextHelper） |
| AI-802 | Conversation tenant_id 評估 | ⏸️ 暫緩（Sprint 25 評估，原訂 P2） |
| AI-803 | M13 Dashboard Redis TTL 設計 | ✅ 完成（US-003） |
| AI-804 | SSH timeout pre-push hook 優化 | ⏸️ 保留評估（Buffer-A 未啟動，Sprint 25） |

**Action Items 完成率**: 2/4（AI-801/803 完成；AI-802/804 延續 Sprint 25）

---

## 9. Definition of Done 驗核

- [x] US-001~003 所有 AC 達成
- [x] `mvn compile` → 0 errors（所有 US）
- [x] Checkstyle 0 violations（所有 US）
- [x] ChatServiceStompBroadcastTest 通過（TC-STOMP-C001~C003）
- [x] RedisCacheConfigTest 4 個測試通過（TC-CACHE-D001~D004）
- [x] M11ShippingFeeIntegrationTest 改用 TestSecurityContextHelper 仍通過
- [x] 既有測試無退步（`@Test` 靜態計數 652，無刪減）
- [x] catch(Exception) 生產程式碼維持 **0 處**
- [x] @Deprecated 生產程式碼維持 **0 處**
- [x] GitHub E2E pipeline backend 啟動修復（V48~V55 schema 修復）
- [x] Sprint 24 Review 文件建立（本文件）
- [x] Sprint 24 Retrospective 文件建立（SPRINT_24_RETRO.md）
- [x] Sprint 24 Release Notes 建立（v2026.09.26-01）

---

**文件版本**: v1.0
**建立日期**: 2026-06-29
**建立者**: Dev David + SD Marcus + Claude Code
