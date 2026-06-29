# Sprint 24 計劃 / Sprint 24 Plan

> **Sprint 編號**: Sprint 24
> **期間**: 2026-09-15 ~ 2026-09-26 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-27
> **基於**: [Sprint 23 Retrospective](../05_development/SPRINT_23_RETRO.md) + [DEFERRED_ITEMS_TRACKER.md](./DEFERRED_ITEMS_TRACKER.md)

---

## 🔴 前置條件確認（AI-701 現況調查）

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 23 全部 US 完成 | ✅ 6/6 US，12 SP | commit `c5a76bb`，push `2220c56` |
| DEF 延後項目 | ✅ 全部清零 | DEF-007/008 於 Sprint 23 完成 |
| Sprint 23 Retro Action Items | AI-801~804 共 4 項 | 帶入 Sprint 24 |
| M10 WebSocket 現況調查（AI-701） | ✅ 已有 ChatService/ChatController REST API | WebSocket STOMP 部分尚未實作 |
| M11 物流取消流程現況 | ⚠️ 尚未評估 | LogisticsService 無 cancelShipment() |
| Spring Cache Redis 現況 | ✅ Redis 已設定（dev profile） | integration-test 使用 Simple Cache |

---

## 1. Sprint 24 目標

> **主題**: 技術品質改善（AI-801 整合測試標準化）+ M10 IM WebSocket 即時訊息（Phase 2）+ M13 Dashboard Redis Cache TTL

Sprint 23 成功清零所有 DEF 技術債。Sprint 24 聚焦：
1. **整合測試品質**（AI-801）：建立 `TestSecurityContextHelper` 標準化 `UserPrincipal` SecurityContext 設置
2. **M10 IM Phase 2**：WebSocket STOMP 即時訊息廣播（後端訂閱 + 發送，無前端）
3. **M13 Cache 優化**（AI-803）：Dashboard 切換 Redis TTL，配合 `@CacheEvict`

---

## 2. Sprint 目標對齊

| 目標 | 對應 Action Item | 類型 |
|------|----------------|------|
| TestSecurityContextHelper 建立 | AI-801 | 技術改善 |
| M10 WebSocket STOMP 後端實作 | Sprint 23 Retro 預告 | P1 新功能 |
| M13 Dashboard Redis TTL | AI-803 | 效能優化 |
| SSH pre-push 優化評估 | AI-804 | DevOps |

---

## 3. Deferred Items 審查

> **參考**: [DEFERRED_ITEMS_TRACKER.md](./DEFERRED_ITEMS_TRACKER.md)

| 狀態 | 說明 |
|------|------|
| 高優先級 DEF | 無（DEF 全部清零） |
| 中優先級 DEF | 無 |
| 本 Sprint 新增潛在 DEF | M11 物流取消流程（尚未評估，若 SP 不足可列 DEF） |

---

## 4. User Stories

### US-001：TestSecurityContextHelper 整合測試標準化（AI-801）

> **SP**: 1 | **優先級**: P1 | **狀態**: ⬜ 待開始

**目標**: 建立共用工具類，統一整合測試中 `UserPrincipal` SecurityContext 設置，避免 `@WithMockUser` 誤用。

**AC-001-1**: `TestSecurityContextHelper.setUserContext(userId, email, role, tenantId)` 可正確設置 SecurityContextHolder
**AC-001-2**: `TestSecurityContextHelper.clear()` 可清除 SecurityContext
**AC-001-3**: M11ShippingFeeIntegrationTest 改用 TestSecurityContextHelper（驗證 3 個測試仍通過）

**技術方向**:
```java
// 建立位置：backend/src/test/java/com/nextkey/ecommerce/integration/util/TestSecurityContextHelper.java
public class TestSecurityContextHelper {
    public static void setUserContext(UUID userId, String email, String role, UUID tenantId) {
        UserPrincipal principal = new UserPrincipal(userId, email, role, tenantId.toString());
        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
            principal, null, List.of(new SimpleGrantedAuthority(role)));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
```

---

### US-002：M10 IM WebSocket STOMP 後端實作

> **SP**: 4 | **優先級**: P1 | **狀態**: ⬜ 待開始

**目標**: 實作 M10 IM Phase 2 — WebSocket STOMP 即時訊息廣播（後端），使用者發送訊息後透過 STOMP 推送到對方訂閱頻道。

**前置調查**（AI-701）:
- `spring-boot-starter-websocket` 是否已在 pom.xml
- 是否已有 `WebSocketConfig` 配置
- 確認現有 `ChatController.sendMessage()` 回傳型態

**AC-002-1**: `WebSocketConfig.java` 配置 STOMP endpoint（`/ws`）+ `/topic`/`/queue` broker
**AC-002-2**: `POST /v2/chat/messages` 發送成功後，透過 `SimpMessagingTemplate` 推送到 `/queue/conversations/{conversationId}`
**AC-002-3**: JWT 認證整合（STOMP connect header 驗證）
**AC-002-4**: 整合測試驗證 STOMP 訊息廣播（使用 `StompClient` 測試接收）

**注意**: WebSocket 前端整合（`@stomp/stompjs`）不在本 Sprint 範圍，僅後端 STOMP 實作。

---

### US-003：M13 Dashboard Redis TTL + @CacheEvict（AI-803）

> **SP**: 2 | **優先級**: P2 | **狀態**: ⬜ 待開始

**目標**: 將 `dashboardStats` Cache 從 Simple Cache 切換為 Redis，並設定 5 分鐘 TTL 與 @CacheEvict 觸發點。

**前置調查**（AI-701）:
- 確認 `application.yml` Redis Cache 設定（TTL 配置方式）
- 確認 `OrderService.createOrderFromCart()` 是否可加 `@CacheEvict`
- 確認 `ListingService.updateListingStatus()` 是否可加 `@CacheEvict`

**AC-003-1**: `application.yml` 加入 `spring.cache.redis.time-to-live: 300000`（5 分鐘）
**AC-003-2**: `SellerDashboardService.getDashboard()` Cache 在 Redis 中有 TTL 5 分鐘
**AC-003-3**: 新訂單建立（`OrderService.createOrderFromCart()`）觸發 `@CacheEvict(value="dashboardStats")`
**AC-003-4**: `SellerDashboardServiceCacheTest` 仍通過（調整 `@TestPropertySource` 為 Redis 或保留 Simple Cache 做單元測試）

---

### US-004（Buffer-A）：SSH pre-push 優化（AI-804）

> **SP**: 1 | **優先級**: Buffer | **狀態**: ⬜ 待評估

**目標**: 評估並改善 SSH timeout 問題，避免 act CI 長時間執行導致 push SIGPIPE（exit 141）。

**AC-004-1**: 調查 SSH ControlMaster 配置可行性（`~/.ssh/config`）
**AC-004-2**: 評估切換 HTTPS push 的可行性（無 SSH timeout 問題）
**AC-004-3**: 選擇方案並實作，確保下次 push 不再有 exit 141

---

### US-005（Buffer-B）：M11 物流取消流程評估

> **SP**: 2 | **優先級**: Buffer | **狀態**: ⬜ 待評估（依容量決定）

**目標**: 評估並實作 M11 物流取消流程（訂單取消後觸發 cancelShipment()）。

**AC-005-1**: `LogisticsService.cancelLogistics(orderId)` 實作
**AC-005-2**: 訂單狀態 `SHIPPING → CANCELLED`（需業務規則確認）
**AC-005-3**: 整合測試驗證

---

## 5. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | TestSecurityContextHelper 標準化 | 1 | P1 |
| US-002 | M10 WebSocket STOMP 後端實作 | 4 | P1 |
| US-003 | M13 Dashboard Redis TTL | 2 | P2 |
| US-004 | SSH pre-push 優化（Buffer-A） | 1 | Buffer |
| US-005 | M11 物流取消流程（Buffer-B） | 2 | Buffer |
| **P0+P1 合計** | | **7 SP** | |
| **含 Buffer 合計** | | **10 SP** | |

> Sprint 23 以 12 SP 完成 100%，Sprint 24 規劃保守（7 SP P0+P1），Buffer 容量充足。
> US-002 WebSocket 複雜度較高，SP 估算偏保守，若發現現況調查後更簡單可快速完成 Buffer。

---

## 6. 執行順序建議

```
US-001（TestSecurityContextHelper）→ 最先，基礎工具，後續 US 可複用
US-002（M10 WebSocket STOMP）    → 最大塊，P1 主題
US-003（M13 Dashboard Redis TTL）→ 相對獨立，可在 US-002 後執行
US-004（Buffer-A：SSH）          → Buffer，US-001~003 完成後啟動
US-005（Buffer-B：M11 取消）     → Buffer，最後評估容量
```

---

## 7. 依賴與風險

| 風險 | 機率 | 影響 | 緩解措施 |
|------|------|------|---------|
| M10 WebSocket STOMP 實作比預期複雜 | 中 | 中 | 先做現況調查（pom.xml + WebSocketConfig），若 4 SP 不足可切 Sprint 25 |
| Redis TTL 設定在 integration-test 環境的影響 | 中 | 低 | 保留 integration-test 的 `spring.cache.type: simple`，不影響 act CI |
| SSH ControlMaster 在 macOS 的兼容性 | 低 | 低 | 備案：切換 HTTPS push |
| M11 物流取消流程業務規則未定義 | 高 | 中 | Buffer-B 需先確認業務規則，若無法確認則延後 |

---

## 8. Definition of Done（Sprint 24）

- [x] US-001~003 所有 AC 達成
- [x] `mvn compile` → 0 errors
- [x] Checkstyle → 0 violations
- [x] 所有新增測試通過
- [x] 既有測試無退步（`@Test` 靜態計數 652，無刪減）
- [x] catch(Exception) 生產程式碼 **0 處**
- [x] @Deprecated 生產程式碼 **0 處**
- [x] Sprint 24 Review 文件建立（[SPRINT_24_REVIEW.md](../05_development/SPRINT_24_REVIEW.md)）
- [x] Sprint 24 Retrospective 文件建立（[SPRINT_24_RETRO.md](../05_development/SPRINT_24_RETRO.md)）
- [x] Sprint 24 Release Notes 建立（[v2026.09.26-01](../08_deployment/RELEASE_NOTES_v2026.09.26-01.md)）
- [x] GitHub E2E backend 啟動修復（計畫外，Flyway V48~V55 schema 漂移修復）

---

## 9. Action Items 追蹤（來自 Sprint 23 Retro）

| AI ID | 內容 | Sprint 24 對應 US |
|-------|------|-----------------|
| AI-801 | 整合測試 SecurityContext 標準化 | US-001（P1） |
| AI-802 | Conversation tenant_id 評估 | 暫緩（Sprint 25 評估） |
| AI-803 | M13 Dashboard Redis TTL 設計 | US-003（P2） |
| AI-804 | SSH timeout pre-push hook 優化 | US-004（Buffer-A） |

---

**文件版本**: v1.0
**建立日期**: 2026-06-27
**建立者**: PM Victoria + SA Amanda + Dev David + Claude Code
