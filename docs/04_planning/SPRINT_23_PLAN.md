# Sprint 23 計劃 / Sprint 23 Plan

> **Sprint 編號**: Sprint 23
> **期間**: 2026-09-01 ~ 2026-09-12 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-27
> **基於**: [Sprint 22 Retrospective](../05_development/SPRINT_22_RETRO.md) + [DEFERRED_ITEMS_TRACKER.md](./DEFERRED_ITEMS_TRACKER.md) + M10_IM_REQUIREMENTS.md

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 22 開發完成 | ✅ 5/5 US 完成（100% SP） | US-001~005 全部 AC 達成 |
| Sprint 22 測試狀態 | ✅ ~302 integration tests, 0 Failures | `mvn compile` BUILD SUCCESS |
| Sprint 22 Review | ✅ [SPRINT_22_REVIEW.md](../05_development/SPRINT_22_REVIEW.md) | 建立於 2026-06-27 |
| Sprint 22 Retrospective | ✅ [SPRINT_22_RETRO.md](../05_development/SPRINT_22_RETRO.md) | 4 個 Action Items（AI-701~704） |
| Sprint 22 Release | ✅ [RELEASE_NOTES_v2026.08.29-01.md](../08_deployment/RELEASE_NOTES_v2026.08.29-01.md) | Tag `v2026.08.29-01` |
| Deferred Items 審查 | ✅ DEF-005/006 已關閉，DEF-007/008 活躍 | DEF-007 M11 物流整合為 Sprint 23 P1 |
| M10 SA 需求分析 | ✅ [M10_IM_REQUIREMENTS.md](../02_architecture/M10_IM_REQUIREMENTS.md) | Victoria APPROVED（2026-08-18） |

### 🔴 AI-701 現況調查（Sprint Planning 前必做）

> **依據**: AI-701 — 每個 Sprint 規劃時先盤點相關模組現況，避免估算偏差

| 模組 | 現況調查結果 |
|------|------------|
| M10 IM（Conversation + Message）| ❌ 尚無任何後端實作，`conversations` 表和 `messages` 表均未建立 |
| M11 物流與訂單整合 | ✅ `LogisticsProvider` 介面 + `HCTLogisticsProvider`/`TCATLogisticsProvider` Stub 已建立；`LogisticsService` 存在但**未被 OrderService 呼叫** |
| DEF-008 ShippingTemplate | ✅ `ShippingTemplateService` 存在，但**未整合訂單結帳**；`Order.shippingFee` 欄位待確認是否存在 |

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 23 |
| **開始日期** | 2026-09-01 (週一) |
| **結束日期** | 2026-09-12 (週五) |
| **Sprint 容量** | 15 SP |
| **規劃 SP** | 12 SP |
| **Buffer** | 3 SP（20%，維持 AI-501 目標） |
| **團隊** | 2 人 Dev Team |

> **容量說明**: Sprint 22 Velocity 8 SP（因 M12 現況節省 4 SP）。基礎容量維持 12 SP，有完整的 M10 IM 需求分析作為輸入。

---

## 2. Sprint 目標

> **目標**: 實作 M10 IM 後端 REST API（對話管理 + 訊息歷史），完成 M11 物流與訂單履約整合（DEF-007/AI-704），確保 Phase 1 平台功能閉環。

### 具體目標

#### 🎯 P0 必須完成

| 功能 | 優先級 | 依據 |
|------|--------|------|
| M10 IM — SD 架構設計 + Flyway Migration（V45+） | P0 | AI-702，基於 M10_IM_REQUIREMENTS.md |
| M10 IM — Conversation REST API（建立/列表） | P0 | AI-702，對話管理核心 |
| M10 IM — Message REST API（發送/歷史查詢） | P0 | AI-702，訊息核心 |

#### 🏗️ P1 重要項目

| 功能 | 優先級 | 依據 |
|------|--------|------|
| M11 物流與訂單整合（DEF-007/AI-704） | P1 | 訂單確認 → createShipment()，追蹤號回寫 |

#### 🔄 Buffer 候選項目

| 功能 | 優先級 | 預估 SP |
|------|--------|---------|
| DEF-008: ShippingTemplate 接入訂單結帳流程 | Buffer-A | 2 SP |
| M13 儀表板 @Cacheable 優化（AI-703） | Buffer-B | 1 SP |

---

## 3. User Stories

### 📋 P0 必須完成

---

#### US-001: M10 IM — SD 架構設計 + DB Migration（V45）

> **優先級**: P0 | **Story Points**: 2 SP | **負責人**: SD Marcus + Dev David

**User Story**:
```
作為後端工程師
我想要有完整的 M10 IM 架構設計文件與 DB Schema
以便直接開始實作 ConversationController 與 MessageController
```

**驗收標準**:
- [ ] AC-001: 建立 `docs/02_architecture/SRD_M10_IM.md`（SD Marcus 主導）：
  - 模組依賴圖（M10 → M01 Listing / M05 Order / M09 Notification）
  - Conversation + Message Entity 設計（含 JPA 關係說明）
  - 安全設計（訂閱者驗證：只有對話成員可訂閱頻道）
- [ ] AC-002: Flyway V45 建立 `conversations` 表：
  ```sql
  CREATE TABLE conversations (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      tenant_id UUID NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
      buyer_id UUID NOT NULL REFERENCES users(id),
      seller_id UUID NOT NULL REFERENCES users(id),
      listing_id UUID REFERENCES listings(id) ON DELETE SET NULL,
      last_message TEXT,
      last_message_at TIMESTAMP WITH TIME ZONE,
      buyer_unread_count INTEGER DEFAULT 0,
      seller_unread_count INTEGER DEFAULT 0,
      created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
      updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
      CONSTRAINT uq_conversation UNIQUE (tenant_id, buyer_id, seller_id, listing_id)
  );
  ```
- [ ] AC-003: Flyway V46 建立 `messages` 表：
  ```sql
  CREATE TABLE messages (
      id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
      conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
      sender_id UUID NOT NULL REFERENCES users(id),
      sender_role VARCHAR(10) NOT NULL CHECK (sender_role IN ('BUYER', 'SELLER')),
      content TEXT NOT NULL,
      content_type VARCHAR(20) NOT NULL DEFAULT 'TEXT' CHECK (content_type IN ('TEXT', 'IMAGE')),
      is_read BOOLEAN DEFAULT FALSE,
      created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
  );
  ```
- [ ] AC-004: `mvn compile` → 0 errors，Flyway migration 本地驗證通過

**技術備註**:
- `conversations.listing_id` 可為 null（支援不綁定商品的一般諮詢）
- `messages.sender_role` 用 CHECK 約束取代 enum，避免 Flyway enum 複雜性
- 索引：`idx_conversations_tenant_buyer`, `idx_conversations_tenant_seller`, `idx_messages_conversation_created`

---

#### US-002: M10 IM — Conversation REST API（建立/列表）

> **優先級**: P0 | **Story Points**: 2 SP | **負責人**: Dev David

**User Story**:
```
作為買家或商家
我想要建立或查詢與對方的對話
以便開始溝通商品諮詢或售後問題
```

**驗收標準**:
- [ ] AC-001: 新增 `POST /v2/conversations`（建立或取得對話，POST 冪等：同條件再呼叫回傳現有對話）
  - Request: `{ buyerId, listingId?, orderId? }`
  - Response: `{ conversationId, buyerId, sellerId, listingId, createdAt }`
  - 權限：`hasAuthority('product:read') or hasAuthority('room:read')`（買家或商家皆可）
- [ ] AC-002: 新增 `GET /v2/conversations`（查詢我的對話列表，分頁）
  - Response: `{ content: [ { conversationId, otherPartyName, lastMessage, lastMessageAt, unreadCount } ], totalElements }`
  - 根據 JWT 中 userId 自動篩選（買家看自己的，商家看自己的）
- [ ] AC-003: 整合測試（M10ConversationIntegrationTest）：
  - IT-CONV-001: 建立對話 → 201 + conversationId
  - IT-CONV-002: 同條件再呼叫 → 200 + 回傳同一個 conversationId
  - IT-CONV-003: 查詢列表 → 200 + 包含剛建立的對話
  - IT-CONV-004: 無授權 → 401

**技術備註**:
- Conversation Entity：`@ManyToOne` buyer/seller/listing + `@Column` unreadCount
- 冪等處理：`conversationRepository.findByTenantIdAndBuyerIdAndSellerIdAndListingId()` 先查再建

---

#### US-003: M10 IM — Message REST API（發送/歷史查詢）

> **優先級**: P0 | **Story Points**: 2 SP | **負責人**: Dev David

**User Story**:
```
作為買家或商家
我想要在對話中發送訊息，並查詢歷史訊息
以便追蹤完整的溝通記錄
```

**驗收標準**:
- [ ] AC-001: 新增 `POST /v2/conversations/{conversationId}/messages`（發送訊息）
  - Request: `{ content, contentType? }`（contentType 預設 TEXT）
  - Response: `{ messageId, senderId, senderRole, content, createdAt }`
  - 驗證：發送者必須是對話成員（buyer 或 seller）
- [ ] AC-002: 新增 `GET /v2/conversations/{conversationId}/messages`（查詢歷史，分頁倒序）
  - Response: `{ content: [ { messageId, senderId, senderRole, content, isRead, createdAt } ], totalElements }`
  - 查詢後自動更新當前用戶的 unreadCount = 0
- [ ] AC-003: 新增 `PUT /v2/conversations/{conversationId}/read`（標記已讀）
- [ ] AC-004: 整合測試（M10MessageIntegrationTest）：
  - IT-MSG-001: 發送訊息 → 201 + messageId
  - IT-MSG-002: 查詢歷史 → 200 + 包含已發送訊息
  - IT-MSG-003: 非成員發送 → 403
  - IT-MSG-004: 不存在的 conversationId → 404

**技術備註**:
- Message Entity：`@ManyToOne conversation` + `sender_role` enum 型別（對應 DB CHECK 約束）
- 分頁：`Pageable`，預設按 `createdAt DESC`
- 發送後觸發：`conversation.lastMessage = content`、`lastMessageAt = now()`、對方的 `unreadCount++`

---

### 🏗️ P1 重要項目

---

#### US-004: DEF-007 — M11 物流與訂單履約整合（AI-704）

> **優先級**: P1 | **Story Points**: 3 SP | **負責人**: Dev David
> **前置**: US-001~003 完成（或 Sprint 進度允許）

**User Story**:
```
作為商家
我想要在訂單確認後自動建立物流單
以便系統追蹤出貨狀態，買家可即時查詢追蹤號
```

**驗收標準**:
- [ ] AC-001: `OrderService.confirmOrder()` 或 `OrderService.updateStatus(SHIPPING)` 時，呼叫 `LogisticsService.createShipment()`，追蹤號回寫至訂單（新增 `Order.trackingNumber` 欄位或 `logistics_shipments` 表）
- [ ] AC-002: 新增 `GET /v2/orders/{orderId}/tracking`（查詢追蹤資訊）
  - Response: `{ orderId, trackingNumber, provider, status, location }`
  - 呼叫 `LogisticsProvider.trackShipment(trackingNumber)`
- [ ] AC-003: 整合測試：
  - IT-LOG-001: 訂單 → SHIPPING 狀態 → 自動建立追蹤號，格式符合 `{Provider}-\d{8}-[A-F0-9]{8}`
  - IT-LOG-002: 查詢追蹤 → 200 + status=IN_TRANSIT
  - IT-LOG-003: 無物流資料的訂單查詢追蹤 → 404
- [ ] AC-004: 編譯 + Checkstyle 0 violations + 所有既有測試無退步

**技術備註**:
- 決策點（SD Marcus 確認）：追蹤號儲存在 `orders.tracking_number` 欄位（簡單）或獨立 `logistics_shipments` 表（可支援多次出貨）
- 建議：V47 `ALTER TABLE orders ADD COLUMN tracking_number VARCHAR(50)`（初期單一出貨足夠）
- `logisticsProvider` 選擇：從 Order/Tenant 設定讀取，預設 HCT

---

### 🔄 Buffer 候選項目

---

#### US-005（Buffer-A）: DEF-008 — ShippingTemplate 接入訂單結帳

> **優先級**: Buffer-A | **Story Points**: 2 SP | **負責人**: Dev David
> **啟動條件**: US-001~004 全部完成

**User Story**:
```
作為買家
我想要在結帳時看到正確的運費（而非固定 Mock 值）
以便做出正確的消費決策
```

**驗收標準**:
- [ ] AC-001: `OrderService.createOrder()` 或 `CheckoutService` 呼叫 `ShippingTemplateService.calculateFee(tenantId, orderAmount)`，運費寫入 `Order.shippingFee`
- [ ] AC-002: 若商家未設定 ShippingTemplate，使用預設運費（系統設定或 0）
- [ ] AC-003: 整合測試：建立設有 FREE_THRESHOLD 模板的商家訂單，orderAmount 達門檻 → shippingFee = 0

---

#### US-006（Buffer-B）: M13 儀表板 @Cacheable 優化（AI-703）

> **優先級**: Buffer-B | **Story Points**: 1 SP | **負責人**: Dev David
> **啟動條件**: US-001~004 完成

**驗收標準**:
- [ ] AC-001: `SellerDashboardService.getDashboard()` 加入 `@Cacheable(value = "sellerDashboard", key = "#tenantId")`，TTL 300 秒
- [ ] AC-002: 訂單/商品變更後 `@CacheEvict` 失效（或接受定時失效）
- [ ] AC-003: Unit Test 驗證快取命中（第二次呼叫不觸發 Repository 查詢）

---

## 4. Sprint 技術決策

### M10 IM 技術選型（來自 M10_IM_REQUIREMENTS.md）

| 決策項目 | 選擇 | 理由 |
|---------|------|------|
| 通訊協議 | Spring WebSocket + STOMP | Spring 生態整合最簡，JWT 天然整合 |
| Broker 類型 | SimpleBroker（初期） | < 5K 並發，無需額外 Broker |
| 前端 Client | `@stomp/stompjs` | 成熟，React 整合簡單 |
| 持久化 | PostgreSQL（conversations + messages） | 租戶隔離、查詢、備份一致 |

> **注意**: Sprint 23 僅實作 REST API（對話 CRUD + 訊息歷史），WebSocket 即時廣播部分放 **Sprint 24**。

### M11 物流追蹤號儲存方案

| 方案 | 優點 | 缺點 | 選擇 |
|------|------|------|------|
| `orders.tracking_number` 欄位 | 簡單，單一 join | 不支援多次出貨 | ✅ **Sprint 23 採用**（MVP） |
| `logistics_shipments` 表 | 支援多次出貨、完整歷史 | 複雜，過度設計 | 留待 Sprint 25+ |

---

## 5. 依賴與風險

| 風險 | 機率 | 影響 | 緩解措施 |
|------|------|------|---------|
| M10 IM REST API 整合測試耗時 | 中 | 中 | US-001 DB 先完成，US-002/003 可並行開發 |
| `conversations` unique constraint 設計複雜 | 低 | 低 | UPSERT 邏輯先用 find-or-create 實作 |
| M11 物流整合影響現有 OrderService 測試 | 中 | 中 | 使用 MockBean LogisticsProvider 隔離 |
| Sprint 23 SP 估算再次不準確 | 低 | 低 | AI-701 現況調查已提前完成 |

---

## 6. Definition of Done

- [ ] US-001~003 所有 AC 達成（P0）
- [ ] US-004 所有 AC 達成（P1）
- [ ] `mvn compile` → 0 errors
- [ ] Checkstyle → 0 violations
- [ ] 所有新增整合測試通過（預期 +12 以上）
- [ ] 所有既有 ~302 整合測試無退步
- [ ] catch(Exception) 生產程式碼 **0 處**
- [ ] @Deprecated 生產程式碼 **0 處**
- [ ] Sprint 23 Review 文件建立
- [ ] Sprint 23 Retrospective 文件建立
- [ ] Sprint 23 Release Notes 建立（v2026.09.12-01）

---

## 7. Action Items 追蹤（來自 Sprint 22 Retro）

| AI ID | 內容 | Sprint 23 對應 |
|-------|------|--------------|
| AI-701 | Sprint 規劃前執行現況調查 | ✅ 已在前置條件完成 M10/M11/DEF-008 調查 |
| AI-702 | M10 IM SD 架構設計 + REST API 實作 | ✅ US-001~003（主題 Sprint） |
| AI-703 | M13 儀表板 @Cacheable 優化評估 | 🔄 US-006 Buffer-B |
| AI-704 | M11 物流與訂單整合（AI-603 延續） | ✅ US-004 P1 |

---

**文件版本**: v1.0
**建立日期**: 2026-06-27
**建立者**: PM/PO Victoria + SD Marcus + Dev David（AISDLC 協作）
**審核狀態**: ✅ Sprint 23 Planning 完成
