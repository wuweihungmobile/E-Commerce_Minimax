# Sprint 23 任務分解 / Sprint 23 Tasks

> **Sprint 編號**: Sprint 23
> **期間**: 2026-09-01 ~ 2026-09-12
> **建立日期**: 2026-06-27
> **基於**: [SPRINT_23_PLAN.md](../04_planning/SPRINT_23_PLAN.md)

---

## 執行狀態總覽

> **⚠️ v1.1 修訂（AI-701 現況調查，2026-06-27）**: M10 IM 核心程式碼已在前期 Sprint 實作完成
> （`Conversation` + `Message` Entity、`ChatService`、`ChatController`、兩個 Repository 全部存在）。
> **唯一缺口**: Flyway DB Migration（conversations + messages 表完全不存在）。
> US-001/002/003 改為「補齊 Migration + 整合測試」而非「從零開發」。

| US ID | 標題 | SP | 優先級 | 狀態 |
|-------|------|----|--------|------|
| US-001 | M10 IM — Flyway V45/V46 + 整合測試 | 2 | P0 | ✅ 完成 |
| US-002 | M10 IM — Conversation REST API 驗證 | 2 | P0 | ✅ 完成（含於 US-001 整合測試） |
| US-003 | M10 IM — Message REST API 驗證 | 2 | P0 | ✅ 完成（含於 US-001 整合測試） |
| US-004 | DEF-007 M11 物流與訂單履約整合（AI-704） | 3 | P1 | ✅ 完成 |
| US-005 | DEF-008 ShippingTemplate 接入訂單結帳（Buffer-A） | 2 | Buffer | ✅ 完成 |
| US-006 | M13 Dashboard @Cacheable（Buffer-B，AI-703） | 1 | Buffer | ✅ 完成 |

**建議執行順序**：US-001/002/003（合併交付）→ US-004 → US-005（Buffer-A）→ US-006（Buffer-B）

---

## US-001/002/003：M10 IM — Flyway Migration + 整合測試（合併交付）

> **SP**: 6 | **優先級**: P0 | **狀態**: ✅ 完成（2026-06-27）
>
> **現況說明（AI-701）**: Conversation/Message Entity、ChatService、ChatController、
> ConversationRepository、MessageRepository 全部已存在。路徑 `/v2/chat/*`，
> 使用 `initiatorId`/`recipientId`（非 buyer/seller），ConversationType ENUM。
> 唯一缺口：DB Migration 完全不存在。

### 完成交付物

- [x] **V45__Create_Conversations.sql** — conversations 表（符合現有 Conversation Entity 欄位定義）
  - `id`, `listing_id`, `order_id`, `conversation_type`, `initiator_id`, `recipient_id`
  - `last_message_id`, `last_message_preview`, `last_message_at`
  - `initiator_unread_count`, `recipient_unread_count`, `is_active`, `metadata`
  - `created_at`, `updated_at` + 3 個 INDEX

- [x] **V46__Create_Messages.sql** — messages 表（符合現有 Message Entity 欄位定義）
  - `id`, `conversation_id`（FK），`sender_id`（FK），`message_type`
  - `content`, `attachments`（JSONB）, `is_read`, `read_at`, `read_by`, `is_deleted`
  - `metadata`（JSONB）, `created_at`, `updated_at` + 2 個 INDEX

- [x] **M10ChatIntegrationTest.java** — 8 個整合測試（US-002 + US-003 全覆蓋）
  - IT-CHAT-001: POST /v2/chat/conversations → 200 + ConversationResponse
  - IT-CHAT-002: 重複建立 → 200 + 相同對話（冪等）
  - IT-CHAT-003: GET /v2/chat/conversations → 200 + 列表分頁
  - IT-CHAT-004: POST /v2/chat/messages → 200 + MessageResponse
  - IT-CHAT-005: GET /v2/chat/conversations/{id}/messages → 200 + 歷史列表
  - IT-CHAT-006: PUT /v2/chat/conversations/{id}/read → 200
  - IT-CHAT-007: DELETE /v2/chat/conversations/{id} → 200
  - IT-CHAT-008: 未帶 JWT → 401（三個端點驗證）

### 技術說明

- **現有程式碼路徑**: `/v2/chat/conversations`, `/v2/chat/messages`（與 SA 分析規劃的 `/v2/conversations` 不同）
- **不需補充功能**: ChatController + ChatService 已完整，只補 DB Migration
- **技術債記錄**: Conversation 沒有 `tenant_id` 欄位，隔離依賴 `initiator_id`/`recipient_id` 直接過濾

---

## US-004：M11 物流與訂單履約整合（DEF-007 / AI-704）

> **SP**: 3 | **優先級**: P1 | **狀態**: ⬜ 待開始
>
> **背景**: DEF-007，LogisticsProvider 策略層完成但未接入訂單流程。
> M12 定價穩定（前置需求已達成）。

### 任務清單

**T-004-1: 現況調查（AI-704 前置）**
- [ ] 讀取 `OrderService.java` 確認訂單狀態流轉（CREATED → PAID → CONFIRMED → SHIPPED → COMPLETED）
- [ ] 讀取 `LogisticsService.java` 或 `LogisticsController.java` 確認現有物流端點
- [ ] 確認 `LogisticsProviderFactory.java` 選擇策略（HCT/TCAT）
- [ ] 確認 DB 是否有 `shipments` 或 `logistics` 表

**T-004-2: 物流 + 訂單整合端點**
- [ ] 設計：`POST /v2/orders/{orderId}/ship`（觸發出貨，選擇 Provider，建立追蹤號）
- [ ] 實作：`OrderService.shipOrder(orderId, providerId)` 呼叫 `LogisticsProvider.createShipment()`
- [ ] 更新訂單狀態：`CONFIRMED → SHIPPED`，記錄 `trackingNumber`
- [ ] 決定：tracking number 存在 `orders` 表還是獨立 `shipments` 表

**T-004-3: 整合測試**
- [ ] IT-SHIP-001: POST /v2/orders/{orderId}/ship（CONFIRMED 訂單）→ 200 + 追蹤號格式驗證
- [ ] IT-SHIP-002: 已出貨訂單再次呼叫 → 409 Conflict
- [ ] IT-SHIP-003: CREATED 狀態訂單（未確認）→ 422 Unprocessable

**T-004-4: 驗證**
- [ ] `mvn compile` → 0 errors
- [ ] 執行 `LogisticsProviderIntegrationTest`（Sprint 22 US-005 所建立 3 個測試）→ 3/3 通過
- [ ] 執行新整合測試 → 3/3 通過
- [ ] Checkstyle → 0 violations

---

## US-005：ShippingTemplate 接入訂單結帳（DEF-008 / Buffer-A）

> **SP**: 2 | **優先級**: Buffer-A | **狀態**: ⬜ 待開始（前置需求：US-004 完成）

### 任務清單

**T-005-1: 現況調查**
- [ ] 讀取 `ShippingTemplateController.java` 確認現有 CRUD 端點
- [ ] 確認 `shipping_templates` 表欄位（`base_fee`, `free_shipping_threshold` 等）
- [ ] 讀取結帳流程（`CheckoutService` 或 `OrderService.createOrder()`）確認運費如何計算

**T-005-2: 運費計算接入**
- [ ] 在訂單建立或結帳時，根據商家 `shipping_template_id` 計算運費
- [ ] `totalAmount` = `subtotal` + `shippingFee`（符合 `free_shipping_threshold` 則免運）
- [ ] 設計運費計算規則（重量 / 金額 / 固定費率）

**T-005-3: 整合測試**
- [ ] IT-SHIP-FEE-001: 結帳金額未達免運門檻 → totalAmount 包含運費
- [ ] IT-SHIP-FEE-002: 結帳金額達免運門檻 → shippingFee = 0

---

## US-006：M13 Dashboard @Cacheable（AI-703 / Buffer-B）

> **SP**: 1 | **優先級**: Buffer-B | **狀態**: ⬜ 待開始

### 任務清單

**T-006-1: 快取設計**
- [ ] `GET /v2/seller/dashboard` 加上 `@Cacheable(value="dashboard", key="#tenantId")`
- [ ] TTL 設計（建議 5 分鐘）
- [ ] `@CacheEvict` 觸發條件（新訂單 / 上下架商品時）

**T-006-2: 快取整合測試**
- [ ] IT-CACHE-001: 連續兩次 GET dashboard → 第二次 response time < 第一次 50%（驗證快取有效）
- [ ] IT-CACHE-002: 建立新訂單後 GET dashboard → 數據更新（快取已失效）

---

## Sprint 23 Checkpoint

### 完成標準（DoD）

- [x] US-001/002/003：M10ChatIntegrationTest 8 個測試通過（act CI 驗證）
- [x] US-004：M11LogisticsOrderIntegrationTest 4 個整合測試通過
- [x] US-005（Buffer-A）：M11ShippingFeeIntegrationTest 3 個整合測試通過
- [x] US-006（Buffer-B）：SellerDashboardServiceCacheTest 3 個 Unit Tests 通過

### 技術健康指標（最終）

| 指標 | Sprint 22 基準 | Sprint 23 達成 |
|------|----------------|----------------|
| Integration Tests | ~302 | **~317**（+15）|
| Unit Tests | ~323 | **~326**（+3）|
| Checkstyle violations | 0 | **0** ✅ |
| CI Build | ✅ GREEN | **✅ GREEN**（act CI make validate-all 通過）|
| Flyway Migrations | V44 | **V47** |

---

**文件版本**: v1.2（Sprint 23 完成收尾）
**最後更新**: 2026-06-27
**執行工程師**: Dev David + Claude Code
