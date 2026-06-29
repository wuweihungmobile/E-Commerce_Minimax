# Release Notes - v2026.09.26-01

**發布日期**: 2026-09-26
**發布類型**: Minor（即時通訊 + 效能優化 + Schema 修復 + 整合測試標準化）
**Sprint**: Sprint 24
**Git Tag**: `v2026.09.26-01`
**基於 Commit**: `0aa91cf`（Sprint 24 最終功能 commit，US-003）
**穩定化 Commit**: `40a72c8`（E2E/CI schema 漂移修復 V48~V55 + Playwright 穩定性）
**專案**: E-Commerce Platform (B2B2C Multi-tenant)

---

## 🎯 發布摘要

Sprint 24 完成三項承諾交付：**TestSecurityContextHelper 整合測試標準化**（AI-801，固化 Sprint 23 的 UserPrincipal SecurityContext 模式）、**M10 IM WebSocket STOMP 即時訊息後端**（AI-701 延續，訊息即時廣播）、**M13 Dashboard Redis TTL + @CacheEvict**（AI-803，5 分鐘 TTL + 即時失效）。發布後段並完成大規模 **GitHub E2E schema 漂移修復**（Flyway V48~V55，補齊缺漏建表 + 統一 ARRAY→jsonb），修復 backend 在 E2E 環境的啟動失敗。

### 關鍵指標

| 項目 | 數值 |
|------|------|
| User Stories 完成 | 3 / 3 承諾（100%，P0+P1）|
| Story Points | 7 SP（Buffer 3 SP 未啟動）|
| 達成率 | 100%（承諾範圍）|
| `@Test` 方法數（靜態） | 652（Sprint 23 後 ~643，+9）|
| 新增 Flyway Migration | V48~V55（8 個，含 schema 漂移修復）|
| CI 狀態 | ✅ GitHub E2E backend 啟動修復 |
| Checkstyle 違規 | 0 |
| catch(Exception) / @Deprecated 生產程式碼 | 0 / 0 ✅ |

---

## 新功能 ✨

### M10 IM — WebSocket STOMP 即時訊息後端（US-002 / AI-701）

M10 IM 從 REST 進化為即時通訊（Phase 2），訊息發送後透過 STOMP 廣播至訂閱頻道。

**新增元件**:

| 檔案 | 說明 |
|------|------|
| `config/WebSocketConfig.java` | STOMP endpoint `/ws`（SockJS fallback）+ `/topic`/`/queue` broker + `/app`/`/user` prefix |
| `config/StompAuthChannelInterceptor.java` | STOMP CONNECT 時驗證 `Authorization: Bearer {token}`，注入 `UserPrincipal` |
| `service/ChatService.java` | 注入 `SimpMessagingTemplate`，`sendMessage()` 後廣播至 `/queue/conversations/{conversationId}/messages` |

**依賴**: `pom.xml` 新增 `spring-boot-starter-websocket`

> **範圍說明**：本版本僅後端 STOMP 實作，前端整合（`@stomp/stompjs`）規劃於 Sprint 25。

---

## 效能優化 ⚡

### M13 Dashboard Redis TTL + @CacheEvict（US-003 / AI-803）

`dashboardStats` Cache 從 Simple Cache（Sprint 23）升級為 Redis + 5 分鐘 TTL，並加上即時失效觸發點。

```java
// RedisConfig.java — 使用 RedisCacheManagerBuilderCustomizer 避免 simple cache 環境 NPE
dashboardStats TTL = Duration.ofMinutes(5)
序列化：GenericJackson2JsonRedisSerializer + JavaTimeModule
```

**@CacheEvict 觸發點**（`OrderService`）:

| 方法 | 觸發 |
|------|------|
| `createOrderFromCart()` | `@CacheEvict(value="dashboardStats", allEntries=true)` |
| `updateOrderStatus()` | 同上 |
| `cancelOrder()` | 同上 |

> `allEntries=true`：tenantId 從 `TenantContext` 取得，無法直接以 SpEL key 表達。

---

## 技術改善 🔧

### TestSecurityContextHelper 整合測試標準化（US-001 / AI-801）

將 Sprint 23 的一次性 SecurityContext 修正（`c5a76bb`）提煉為可複用工具，避免整合測試誤用 `@WithMockUser`。

```java
// integration/util/TestSecurityContextHelper.java
setUserContext(UUID userId, String email, UUID tenantId, String role, List<String> authorities) // 完整版
setUserContext(UUID userId, UUID tenantId, String role)                                          // 快速版
clear()
```

**解決問題**：`@WithMockUser` 建立 String principal，導致 `TenantContextFilter` 無法取得 tenantId。`M11ShippingFeeIntegrationTest` 已改用此工具。

---

## 資料庫變更 🗃️

> Sprint 24 後段修復 GitHub E2E backend 啟動失敗。**根因**：本地 `act` 用 `ddl-auto=update`（自動補欄位/建表），GitHub E2E 用 schema 驗證，導致 entity 與 migration 的型別漂移、缺漏建表只在 E2E 環境爆發。

| Migration | 說明 | 影響 |
|-----------|------|------|
| V48__Fix_Article_Versions_Tags_Column_Type.sql | `article_versions.tags` 型別統一 | 修復 knowledge 模組 E2E 啟動 |
| V49__Create_Cms_Banner_And_Page_Tables.sql | 補建 cms_banner / page 表 | 補齊缺漏建表 |
| V50__Create_Inventory_Tables.sql | 補建 inventory 表 | 補齊缺漏建表 |
| V51__Create_Logistics_Table.sql | 補建 logistics 表 | 補齊缺漏建表 |
| V52__Create_Notifications_Table.sql | 補建 notifications 表 | 補齊缺漏建表 |
| V53__Fix_Entity_Schema_Drift.sql | 修正多模組 entity 與 DDL 漂移 | E2E schema 驗證通過 |
| V54__Fix_Media_Assets_Tags_Column_Type.sql | `media_assets.tags` 型別統一 | 修復 media 模組 E2E 啟動 |
| V55__Fix_Rooms_Amenities_Column_Type.sql | `rooms.amenities` 統一為 jsonb | 消除全庫最後一個 ARRAY 映射 |

---

## 破壞性變更 ⚠️

**無破壞性變更。** WebSocket 為新增 endpoint；Redis TTL 與 @CacheEvict 不影響資料正確性；V48~V55 為 schema 對齊（型別統一 + 補齊缺漏建表）。

**注意事項**:
- WebSocket endpoint `/ws` 需確認部署環境（反向代理）支援 WebSocket upgrade
- Redis Cache TTL 5 分鐘：儀表板資料最多延遲 5 分鐘（新訂單/狀態變更即時失效）
- V48~V55 在既有資料庫升級時，需確認 ARRAY → jsonb 欄位（`rooms.amenities`、`*.tags`）的既有資料相容性

---

## 升級注意事項

1. **執行 Flyway Migration**（V48~V55 自動執行）
2. **Redis 可用性**：`dashboardStats` 現使用 Redis，部署環境需確認 Redis 連線（integration-test 仍用 simple cache）
3. **WebSocket 部署**：確認 Nginx / 反向代理 `proxy_set_header Upgrade` 設定
4. **E2E schema 驗證**：本次後段修復後，GitHub E2E backend 可正常啟動

---

## Commit 參考

| 項目 | Commit | 說明 |
|------|--------|------|
| US-001 | `50a6d45` | TestSecurityContextHelper 整合測試標準化（AI-801） |
| US-002 | `d671463` | M10 WebSocket STOMP 後端（AI-701） |
| US-003 | `0aa91cf` | M13 Dashboard Redis TTL + @CacheEvict（AI-803） |
| Schema 修復 | `0655b11`~`530a5ad` | Flyway V48~V55，GitHub E2E backend 啟動修復 |
| E2E 穩定性 | `a1bce43`~`40a72c8` | Playwright 重用 server / domcontentloaded / report 停版控 |
| Docker 清理 | `d036f0c` | 清理 LLM 殘留工具鏈 |

---

**版本**: v2026.09.26-01
**文件版本**: v1.0
**建立日期**: 2026-06-29
**建立者**: Dev David + SD Marcus + Claude Code
