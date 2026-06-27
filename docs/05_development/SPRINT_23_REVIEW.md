# Sprint 23 Review / Sprint 23 評審會議

> **Sprint 編號**: Sprint 23
> **期間**: 2026-09-01 ~ 2026-09-12
> **評審日期**: 2026-06-27（AI-AISDLC 延伸：開發完成後即建立）
> **建立日期**: 2026-06-27

---

## 1. Sprint 目標達成評估

> **Sprint 目標（v1.1 修訂後）**: 補齊 M10 IM DB Migration + 整合測試驗證（US-001/002/003），
> 清償 DEF-007 M11 物流與訂單履約整合（US-004），並評估 Buffer 項目（US-005 DEF-008 運費接入 + US-006 Dashboard @Cacheable）。

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| M10 IM Flyway V45/V46 Migration 補齊 | ✅ 達成 | conversations + messages 表建立，與現有 Entity 完全吻合 |
| M10 IM 整合測試（8 個）全部通過 | ✅ 達成 | IT-CHAT-001~008 涵蓋 CRUD + 冪等 + 401 驗證 |
| DEF-007 M11 物流與訂單履約整合 | ✅ 達成 | createLogistics 驗前置狀態 + 同步訂單 SHIPPING/DELIVERED |
| DEF-008 ShippingTemplate 接入訂單結帳（Buffer-A） | ✅ 達成 | 免運門檻邏輯 + V47 Migration + shippingFee 欄位 |
| M13 Dashboard @Cacheable（Buffer-B） | ✅ 達成 | @Cacheable + Simple Cache + 3 個 Unit Tests |

**Sprint 目標達成率**: 100%（6 US 全部完成，含 Buffer-A + Buffer-B）

---

## 2. User Story 完成狀態

| US | 標題 | SP | 狀態 | Commit |
|----|------|----|------|--------|
| US-001 | M10 IM — Flyway V45/V46（conversations + messages 表） | 2 | ✅ 完成 | `7b69933` |
| US-002 | M10 IM — Conversation REST API 驗證 | 2 | ✅ 完成（含於 IT-CHAT-001~008） | `7b69933` |
| US-003 | M10 IM — Message REST API 驗證 | 2 | ✅ 完成（含於 IT-CHAT-001~008） | `7b69933` |
| US-004 | DEF-007 M11 物流與訂單履約整合 | 3 | ✅ 完成 | `1228a38` |
| US-005 | DEF-008 ShippingTemplate 接入訂單結帳（Buffer-A） | 2 | ✅ 完成 | `70c0a02` |
| US-006 | M13 Dashboard @Cacheable（Buffer-B，AI-703） | 1 | ✅ 完成 | `60081fc` |
| **合計** | | **12 SP** | ✅ 100% | |

> Sprint 23 規劃 12 SP，實際以 12 SP 完成所有 US（含 Buffer-A + Buffer-B）。
> 另有 `c5a76bb` 修正 act CI 失敗問題（M11ShippingFeeIntegrationTest SecurityContext + application-integration-test.yml）。

---

## 3. 測試狀態

| 測試類型 | Sprint 前（Sprint 22 後） | Sprint 後 | 新增 |
|---------|--------------------------|----------|------|
| Unit Tests（mvn test） | ~323 | ~326 | +3（US-006 SellerDashboardServiceCacheTest） |
| Integration Tests（-Pintegration-test） | ~302 | ~317 | +15（US-001~005 各自整合測試） |
| **合計** | **~625** | **~643** | **+18** |

**新增測試明細**:

| US | 新增測試 | Test IDs |
|----|---------|---------|
| US-001/002/003 | M10ChatIntegrationTest（+8） | IT-CHAT-001~008 |
| US-004 | M11LogisticsOrderIntegrationTest（+4） | IT-SHIP-001~004 |
| US-005 | M11ShippingFeeIntegrationTest（+3） | IT-SFEE-001~003 |
| US-006 | SellerDashboardServiceCacheTest（+3） | TC-DASH-C001~C003 |
| **合計** | **+18** | |

---

## 4. M10 IM Migration 詳情（US-001/002/003）

### V45__Create_Conversations.sql

```sql
CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    order_id UUID REFERENCES orders(id) ON DELETE SET NULL,
    conversation_type VARCHAR(50) NOT NULL,
    initiator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- ... last_message_*, unread_count, metadata
    CONSTRAINT uq_conversations_listing_users UNIQUE (listing_id, initiator_id, recipient_id)
);
```

### V46__Create_Messages.sql

```sql
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_type VARCHAR(50) NOT NULL,
    content TEXT,
    attachments JSONB,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    -- ...
);
```

### 整合測試涵蓋範圍（IT-CHAT-001~008）

| Test ID | 測試場景 |
|---------|---------|
| IT-CHAT-001 | POST /v2/chat/conversations → 201 + ConversationResponse |
| IT-CHAT-002 | 重複建立同對話 → 200 + 相同 id（冪等） |
| IT-CHAT-003 | GET /v2/chat/conversations → 200 + 分頁列表 |
| IT-CHAT-004 | POST /v2/chat/messages → 201 + MessageResponse |
| IT-CHAT-005 | GET /v2/chat/conversations/{id}/messages → 200 + 歷史 |
| IT-CHAT-006 | PUT /v2/chat/conversations/{id}/read → 200 |
| IT-CHAT-007 | DELETE /v2/chat/conversations/{id} → 200 |
| IT-CHAT-008 | 未帶 JWT 各端點 → 401 |

**技術說明**:
- 現有程式碼路徑：`/v2/chat/*`（與 SA 分析規劃 `/v2/conversations` 不同，以現有實作為準）
- `Conversation` 無 `tenant_id`，隔離依靠 `initiator_id`/`recipient_id` 直接過濾（技術債記錄）

---

## 5. M11 物流與訂單履約整合（US-004）

### LogisticsService 變更

| 方法 | 新增邏輯 |
|------|---------|
| `createLogistics()` | 前置驗證 `order.status == CONFIRMED`；建立物流後同步 `order.status = SHIPPING` |
| `updateLogisticsStatus()` | 物流狀態更新至 `DELIVERED` 時同步 `order.status = DELIVERED` |

### 整合測試（IT-SHIP-001~004）

| Test ID | 測試場景 |
|---------|---------|
| IT-SHIP-001 | CONFIRMED 訂單建立物流 → SHIPPING，trackingNumber 非空 |
| IT-SHIP-002 | 已出貨訂單再次建立物流 → 409 Conflict |
| IT-SHIP-003 | CREATED 狀態訂單建立物流 → 422（未確認不可出貨） |
| IT-SHIP-004 | 更新物流狀態至 DELIVERED → 訂單同步 DELIVERED |

---

## 6. ShippingTemplate 接入訂單結帳（US-005）

### 資料庫變更

**V47__Add_Shipping_Fee_To_Orders.sql**:
```sql
ALTER TABLE orders
    ADD COLUMN shipping_fee NUMERIC(10, 2) NOT NULL DEFAULT 0.00;
```

### 程式碼變更

| 檔案 | 變更內容 |
|------|---------|
| `Order.java` | 新增 `shippingFee` 欄位（`BigDecimal`，預設 0） |
| `OrderService.java` | `createOrderFromCart()` 呼叫 `ShippingTemplateService.calculateFeeForTenant()` 計算運費 |
| `ShippingTemplateService.java` | 新增 `calculateFeeForTenant(tenantId, orderAmount)` — 查詢模板 + 免運門檻邏輯 |
| `OrderDto.java` | `OrderResponse` 新增 `shippingFee` 欄位 |

### 運費計算邏輯

```
orderAmount >= freeThreshold → shippingFee = 0
orderAmount < freeThreshold  → shippingFee = template.fixedAmount
無模板                        → shippingFee = 0
```

### 整合測試（IT-SFEE-001~003）

| Test ID | 場景 | 預期 |
|---------|------|------|
| IT-SFEE-001 | 小計 200（< 免運門檻 500），固定運費 60 | totalAmount = 260，shippingFee = 60 |
| IT-SFEE-002 | 小計 600（>= 免運門檻 500） | shippingFee = 0，totalAmount = 600 |
| IT-SFEE-003 | 無運費模板 | shippingFee = 0，totalAmount = 300 |

---

## 7. M13 Dashboard @Cacheable（US-006）

### 程式碼變更

| 檔案 | 變更內容 |
|------|---------|
| `SellerDashboardService.java` | `getDashboard()` 加上 `@Cacheable(value="dashboardStats", key="#tenantId")` |

### Cache 設計

- **Cache key**: `tenantId`（每個租戶獨立 cache）
- **Cache type**: `simple`（記憶體，Spring Boot default）
- **Eviction**: 無明確 TTL（依 JVM 生命週期），後續可加 Redis TTL（Sprint 24 以後評估）

### 單元測試（TC-DASH-C001~C003）

| Test ID | 測試場景 |
|---------|---------|
| TC-DASH-C001 | 連呼叫兩次 getDashboard → Repository 只調用 1 次（Cache Hit） |
| TC-DASH-C002 | 不同 tenantId 各自獨立快取 |
| TC-DASH-C003 | @CacheEvict 清除後 Cache Miss（Repository 再次調用） |

---

## 8. Sprint 22 Action Items 追蹤

| Action Item | 內容 | 達成狀態 |
|-------------|------|---------|
| AI-701 | Sprint 規劃前執行現況調查 | ✅ 完成（Sprint 23 啟動時確認 M10 現況，節省重複開發） |
| AI-702 | M10 IM SD 架構設計 + REST API 實作 | ✅ 完成（現況調查發現 ChatService/Controller 早已完整，改為補 Migration + 整合測試） |
| AI-703 | M13 儀表板 @Cacheable 優化 | ✅ 完成（Buffer-B，使用 Simple Cache） |
| AI-704 | M11 物流與訂單整合（AI-603 延續） | ✅ 完成（LogisticsService 整合訂單狀態流轉） |

**Action Items 完成率**: 4/4（100%）

---

## 9. CI 修正記錄

**fix commit `c5a76bb`**（Push 前修正）:

| 問題 | 根本原因 | 修復 |
|------|---------|------|
| M11ShippingFeeIntegrationTest 回傳 404 | `@WithMockUser` 建立 String principal，`TenantContextFilter` 不設定 TenantContext，userId/tenantId 為 null | 改用 `UserPrincipal` 手動建立 `SecurityContextHolder` |
| SellerDashboardService NPE（act CI） | act CI Redis 無密碼，`@Cacheable` 連線失敗 | `application-integration-test.yml` 加入 `spring.cache.type: simple` |
| `Matchers.closeTo(double, double)` 斷言失敗 | JSON 序列化 `BigDecimal(60)` 為 Integer，`Matcher<Double>` 無法匹配 | 改為 `.value(int)` |

---

## 10. Definition of Done 驗核

- [x] US-001~006 所有 AC 達成
- [x] `mvn compile` → 0 errors（所有 US）
- [x] Checkstyle 0 violations（所有 US）
- [x] M10ChatIntegrationTest 8 個測試通過（IT-CHAT-001~008）
- [x] M11LogisticsOrderIntegrationTest 4 個測試通過（IT-SHIP-001~004）
- [x] M11ShippingFeeIntegrationTest 3 個測試通過（IT-SFEE-001~003）
- [x] SellerDashboardServiceCacheTest 3 個測試通過（TC-DASH-C001~C003）
- [x] act CI（make validate-all）整體通過（`c5a76bb` 驗證記錄 2026-06-27 19:48:47）
- [x] `git push origin main` 成功（所有 Sprint 20~23 commits 上傳至 GitHub）
- [x] catch(Exception) 生產程式碼維持 **0 處**
- [x] @Deprecated 生產程式碼維持 **0 處**
- [x] Sprint 23 Review 文件建立（本文件）
- [x] Sprint 23 Retrospective 文件建立（SPRINT_23_RETRO.md）

---

**文件版本**: v1.0
**建立日期**: 2026-06-27
**建立者**: Dev David + SD Marcus + Claude Code
