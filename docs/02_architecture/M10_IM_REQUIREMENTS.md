# M10 即時通訊（IM）需求分析文件

> **文件類型**: SA 需求分析 / 架構選型
> **模組**: M10 — 即時通訊（Instant Messaging）
> **負責人**: SA Amanda
> **建立日期**: 2026-06-27
> **狀態**: ✅ PM/PO Victoria APPROVED
> **Sprint**: Sprint 22 US-004（DEF-005 Buffer-A，AI-602）
> **前置文件**: [SRD_System_Architecture.md](SRD_System_Architecture.md)
> **後續文件**: Sprint 23 SD Marcus 架構設計

---

## 1. 背景與目標

### 1.1 業務動機

E-Commerce Minimax 平台目前買家與商家之間沒有直接溝通管道，買家只能透過訂單備註或 Email 聯繫商家，導致：
- 訊息延遲高，影響轉換率
- 商家無法即時回應買家詢問（如庫存、出貨、客製化需求）
- 平台缺乏「黏性」功能，競爭劣勢明顯

### 1.2 功能目標（Sprint 23+ 開發範疇）

| 功能 | 說明 | 優先級 |
|------|------|--------|
| 買家 ↔ 商家私訊 | 1:1 對話，綁定商品/訂單 | P0 |
| 訊息已讀狀態 | 顯示訊息是否已讀 | P0 |
| 歷史訊息查詢 | 分頁取得對話歷史 | P0 |
| 推播通知 | 新訊息推送至 M09 通知系統 | P1 |
| 圖片附件 | 傳送圖片（整合 M12 Media） | P2 |

### 1.3 非功能需求

| 項目 | 要求 |
|------|------|
| 訊息延遲 | P2P 延遲 < 500ms（平均） |
| 並發連線 | 初期目標：1,000 concurrent WebSocket connections / 伺服器 |
| 訊息可靠性 | 至少一次交付（at-least-once delivery） |
| 資料保留 | 訊息永久保存（不自動刪除），支援查詢 |
| 租戶隔離 | 每個對話必須綁定 tenantId |

---

## 2. 技術選型分析：WebSocket vs MQTT

### 2.1 評估維度比較

| 評估維度 | Spring WebSocket + STOMP | MQTT（Mosquitto / EMQ X）| 結論 |
|----------|--------------------------|--------------------------|------|
| **訊息延遲** | 低延遲（< 50ms，長連線保持）| 極低延遲（QoS 0 < 10ms，QoS 1 ~50ms）| 兩者均可接受，WebSocket 稍高但足夠 |
| **可靠性（QoS）** | 依應用層保證，需自行實作 ACK | 原生支援 QoS 0/1/2（最多一次/至少一次/恰好一次） | MQTT 在協議層更有優勢，但 WebSocket + DB ACK 亦可達到等效 |
| **擴展性（Broker 叢集）** | 需搭配 RabbitMQ STOMP plugin 或 Redis Pub/Sub 實現多節點 | EMQ X 支援原生叢集，水平擴展成熟 | MQTT 擴展性更成熟；WebSocket 方案需額外架構 |
| **Spring Boot 整合難度** | ✅ 原生支援（`spring-boot-starter-websocket`），零額外依賴 | 需引入 `spring-integration-mqtt` 或 `eclipse-paho`，設定複雜 | **WebSocket 大幅簡化** |
| **前端支援** | ✅ 瀏覽器原生 WebSocket API，`@stomp/stompjs` 成熟 | 需 `mqtt.js`，支援度略低 | **WebSocket 前端更簡單** |
| **安全性（Auth）** | JWT 直接透過 HTTP Upgrade header 傳遞 | 需 MQTT Broker 額外設定 ACL | **WebSocket 整合現有 JWT 體系更自然** |
| **基礎設施成本** | 使用現有 Spring Boot 容器，無需額外 Broker | 需額外部署 MQTT Broker（EMQ X/Mosquitto） | **WebSocket 降低 Ops 複雜度** |
| **訊息持久化** | 完全控制（寫入 PostgreSQL） | Broker 提供記憶體/磁碟持久化，但需轉寫 DB | **WebSocket 更靈活** |

### 2.2 選型結論

**推薦方案：Spring WebSocket + STOMP**

**理由**：
1. **Spring 生態整合最簡**：`spring-boot-starter-websocket` 已涵蓋所有需求，與現有 JWT 安全架構天然整合
2. **無需額外 Broker**：初期規模（< 10K 用戶）不需要 MQTT Broker 的複雜性
3. **前端開發成本低**：`@stomp/stompjs` 是成熟的前端客戶端，React 整合簡單
4. **訊息持久化完全掌控**：直接寫入現有 PostgreSQL，租戶隔離、查詢、備份一致
5. **擴展路徑清晰**：規模增長後可加入 Redis Pub/Sub 作為 Message Broker 支援多節點，無需更換協議

**MQTT 適用場景**：IoT 設備通訊（低頻寬、不穩定網路、QoS 嚴格要求）。本平台是標準 Web/App 場景，不符合 MQTT 優勢情境。

---

## 3. 資料模型設計

### 3.1 Conversation（對話）

```sql
CREATE TABLE conversations (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id    UUID         NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    buyer_id     UUID         NOT NULL REFERENCES users(id),
    seller_id    UUID         NOT NULL REFERENCES users(id),
    listing_id   UUID         REFERENCES listings(id) ON DELETE SET NULL,
    order_id     UUID         REFERENCES orders(id) ON DELETE SET NULL,
    last_message TEXT,
    last_message_at TIMESTAMP WITH TIME ZONE,
    buyer_unread_count  INTEGER DEFAULT 0,
    seller_unread_count INTEGER DEFAULT 0,
    created_at   TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at   TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    CONSTRAINT uq_conversation UNIQUE (tenant_id, buyer_id, seller_id, listing_id)
);
CREATE INDEX idx_conversations_tenant_buyer  ON conversations(tenant_id, buyer_id);
CREATE INDEX idx_conversations_tenant_seller ON conversations(tenant_id, seller_id);
```

**設計說明**：
- 以 `(tenant_id, buyer_id, seller_id, listing_id)` 為唯一鍵：同一商品同一組買賣家只能有一個對話
- `listing_id` / `order_id` 可為 null（支援不綁定商品的一般諮詢）
- `buyer_unread_count` / `seller_unread_count`：冗餘欄位，避免每次計算未讀數量

### 3.2 Message（訊息）

```sql
CREATE TABLE messages (
    id              UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID         NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id       UUID         NOT NULL REFERENCES users(id),
    sender_role     VARCHAR(10)  NOT NULL CHECK (sender_role IN ('BUYER', 'SELLER')),
    content         TEXT         NOT NULL,
    content_type    VARCHAR(20)  NOT NULL DEFAULT 'TEXT' CHECK (content_type IN ('TEXT', 'IMAGE')),
    image_url       TEXT,
    is_read         BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
CREATE INDEX idx_messages_conversation_created ON messages(conversation_id, created_at DESC);
```

---

## 4. API 草稿

### 4.1 REST API（HTTP — 對話管理與歷史）

#### 建立或取得對話

```
POST /v2/conversations
Authorization: Bearer {token}
Content-Type: application/json

{
  "buyerId": "uuid",       // 買家 ID
  "listingId": "uuid",    // 商品 ID（選填）
  "orderId": "uuid"       // 訂單 ID（選填）
}

Response 200/201:
{
  "success": true,
  "data": {
    "conversationId": "uuid",
    "buyerId": "uuid",
    "sellerId": "uuid",
    "listingId": "uuid",
    "createdAt": "2026-08-18T10:00:00Z"
  }
}
```

#### 查詢我的對話列表

```
GET /v2/conversations?page=0&size=20
Authorization: Bearer {token}

Response 200:
{
  "success": true,
  "data": {
    "content": [
      {
        "conversationId": "uuid",
        "otherPartyName": "商家名稱",
        "listingTitle": "商品標題",
        "lastMessage": "好的，明天出貨",
        "lastMessageAt": "2026-08-18T14:30:00Z",
        "unreadCount": 2
      }
    ],
    "totalElements": 5,
    "totalPages": 1
  }
}
```

#### 查詢對話訊息歷史

```
GET /v2/conversations/{conversationId}/messages?page=0&size=50
Authorization: Bearer {token}

Response 200:
{
  "success": true,
  "data": {
    "content": [
      {
        "messageId": "uuid",
        "senderId": "uuid",
        "senderRole": "BUYER",
        "content": "請問有現貨嗎？",
        "contentType": "TEXT",
        "isRead": true,
        "createdAt": "2026-08-18T10:01:00Z"
      }
    ],
    "totalElements": 15
  }
}
```

#### 發送 REST 訊息（備用路徑）

```
POST /v2/conversations/{conversationId}/messages
Authorization: Bearer {token}
Content-Type: application/json

{
  "content": "好的，明天出貨",
  "contentType": "TEXT"
}
```

#### 標記已讀

```
PUT /v2/conversations/{conversationId}/read
Authorization: Bearer {token}
```

### 4.2 WebSocket API（STOMP — 即時訊息）

#### 連線端點

```
WebSocket URL: ws://{host}/v2/ws
STOMP Connect Header:
  Authorization: Bearer {jwt_token}
```

#### 訂閱對話頻道

```
SUBSCRIBE /topic/conversation/{conversationId}

接收訊息格式:
{
  "messageId": "uuid",
  "conversationId": "uuid",
  "senderId": "uuid",
  "senderRole": "BUYER",
  "content": "有現貨嗎",
  "contentType": "TEXT",
  "createdAt": "2026-08-18T10:01:00Z"
}
```

#### 訂閱個人通知頻道

```
SUBSCRIBE /user/{userId}/queue/notifications

接收格式:
{
  "type": "NEW_MESSAGE",
  "conversationId": "uuid",
  "fromName": "買家姓名",
  "preview": "有現貨嗎..."
}
```

#### 發送訊息（STOMP SEND）

```
SEND /app/conversation/{conversationId}/send

Payload:
{
  "content": "好的，明天出貨",
  "contentType": "TEXT"
}
```

---

## 5. 技術依賴清單

### 5.1 後端依賴（Spring Boot）

| 依賴 | Artifact ID | 版本 | 用途 | 狀態 |
|------|-------------|------|------|------|
| Spring WebSocket | `spring-boot-starter-websocket` | 3.2.x（Spring Boot 管理） | WebSocket 端點 + STOMP Broker | 🔴 需新增 |
| Spring Security WebSocket | 已包含於 spring-security | — | WebSocket 端點安全控管 | ✅ 已有 |
| SockJS Fallback | 內建於 Spring WebSocket | — | 不支援 WebSocket 的瀏覽器 fallback | ✅ 含在上述依賴 |

**pom.xml 新增**:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-websocket</artifactId>
</dependency>
```

### 5.2 前端依賴（React）

| 依賴 | Package | 版本 | 用途 |
|------|---------|------|------|
| STOMP Client | `@stomp/stompjs` | ^7.x | STOMP over WebSocket |
| SockJS Client | `sockjs-client` | ^1.x | WebSocket fallback（IE/Safari） |

### 5.3 Spring Boot 設定草稿

```yaml
spring:
  websocket:
    message-broker:
      application-destination-prefixes: /app
      topic-destinations: /topic
      user-destinations: /user
```

### 5.4 擴展路徑（規模增長後）

| 階段 | 方案 | 觸發條件 |
|------|------|---------|
| Phase 1（現在）| 單節點 STOMP SimpleBroker | < 5K 並發連線 |
| Phase 2 | Spring + RabbitMQ STOMP plugin（Message Broker 模式） | 多節點部署 / > 5K 並發 |
| Phase 3 | 評估 MQTT EMQ X（如業務擴展至 IoT 場景） | 特殊需求時 |

---

## 6. 安全設計

### 6.1 WebSocket 驗證

- WebSocket 連線時，透過 STOMP `connect` header 傳遞 JWT Token
- `JwtAuthenticationFilter` 需擴展支援 WebSocket Upgrade 請求
- 訂閱 `/topic/conversation/{id}` 時，後端驗證當前用戶是否為對話成員

### 6.2 租戶隔離

- 所有對話和訊息都包含 `tenantId`
- 訂閱頻道命名包含 tenantId 或後端嚴格驗證訂閱者身份

### 6.3 訊息防注入

- `content` 欄位需做 XSS sanitization（前端 + 後端雙重處理）
- 訊息長度限制：5,000 字元

---

## 7. 與現有模組整合

| 模組 | 整合點 | 說明 |
|------|--------|------|
| M09 通知 | 新訊息到達 → MQ → M09 Push Notification | 買家/商家離線時收到 App 推播 |
| M01 商品 | Conversation.listing_id → Listing | 對話可綁定商品頁面 |
| M05 訂單 | Conversation.order_id → Order | 對話可綁定訂單（售後服務） |
| M08 評價 | 送出評價後可選擇開啟對話 | 後續 Phase 考慮 |

---

## 8. 開發里程碑（Sprint 規劃建議）

| Sprint | 內容 | 交付物 |
|--------|------|--------|
| Sprint 23 | SD Marcus 架構設計 + Flyway Migration | SRD_M10_IM.md + V45 migration |
| Sprint 23 | REST API CRUD（對話 + 訊息歷史） | ConversationController + MessageController |
| Sprint 24 | WebSocket 端點 + STOMP 訊息廣播 | WebSocket Config + Message Handler |
| Sprint 24 | 整合 M09 通知（新訊息 → MQ → Push） | NotificationService 擴展 |
| Sprint 25 | 前端 IM UI + E2E 測試 | React IM Component |

---

## 9. PM/PO 審核記錄

> **審核人**: PM/PO Victoria
> **審核日期**: 2026-08-18
> **審核結果**: ✅ APPROVED

**審核意見**:
```
技術選型（WebSocket + STOMP）方向正確，與現有 Spring Boot 生態整合成本最低。
API 草稿涵蓋核心 CRUD 及 WebSocket 訂閱路徑，符合 P0 業務需求。
資料模型的 unique constraint (tenant_id, buyer_id, seller_id, listing_id) 設計合理，
避免重複對話。unread_count 冗餘欄位可接受（效能考量優先）。
建議 Sprint 23 開始實作時先完成 REST API（對話 + 歷史），WebSocket 部分放 Sprint 24。
```

**審核結論**: ✅ APPROVED（可進入 SD Marcus Sprint 23 架構設計階段）

---

**文件版本**: v1.0
**建立日期**: 2026-06-27
**建立者**: SA Amanda（AISDLC 協作）
**審核狀態**: ✅ PM/PO Victoria APPROVED（2026-08-18）
