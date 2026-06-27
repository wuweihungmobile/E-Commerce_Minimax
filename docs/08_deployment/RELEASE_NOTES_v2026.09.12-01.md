# Release Notes - v2026.09.12-01

**發布日期**: 2026-09-12
**發布類型**: Minor（DB Migration + 功能整合 + 效能優化）
**Sprint**: Sprint 23
**Git Tag**: `v2026.09.12-01`
**基於 Commit**: `c5a76bb`（Sprint 23 最終程式碼 commit）
**文件 Commit**: `2220c56`（Sprint 23 Review + Retro）
**專案**: E-Commerce Platform (B2B2C Multi-tenant)

---

## 🎯 發布摘要

Sprint 23 完成六項交付：**M10 IM DB Migration 補齊**（V45/V46 conversations + messages 表，8 個整合測試全通過）、**M11 物流與訂單履約整合**（createLogistics 前置驗證 + 訂單狀態同步 SHIPPING/DELIVERED）、**ShippingTemplate 接入訂單結帳**（V47 Migration + shippingFee 欄位 + 免運門檻邏輯）、**M13 Dashboard @Cacheable 效能優化**（Simple Cache）。同時修正 act CI 環境下 SecurityContext + Cache 整合測試問題。**DEF 全部清零**：Sprint 21 遺留 DEF-007/008 已全數完成。

### 關鍵指標

| 項目 | 數值 |
|------|------|
| User Stories 完成 | 6 / 6（100%，含 Buffer-A + Buffer-B）|
| Story Points | 12 SP |
| 達成率 | 100% |
| Integration Tests | ~317 tests, 0 Failures, 0 Errors |
| 新增測試 | 18 個（Integration +15 + Unit +3） |
| 新增 Flyway Migration | V45/V46/V47 |
| CI 狀態 | ✅ BUILD SUCCESS（make validate-all 通過） |
| Checkstyle 違規 | 0 |
| DEF 清零 | ✅ DEF-007/008 全部完成 |

---

## 新功能 ✨

### M10 IM — DB Migration 補齊（US-001/002/003）

M10 IM 模組（ChatService/ChatController/Repository）前期已完整實作，本版本補齊 DB Migration，使 IM 功能正式可用。

#### V45__Create_Conversations.sql

```sql
CREATE TABLE conversations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    listing_id UUID NOT NULL REFERENCES listings(id) ON DELETE CASCADE,
    order_id UUID REFERENCES orders(id) ON DELETE SET NULL,
    conversation_type VARCHAR(50) NOT NULL,
    initiator_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    last_message_id UUID,
    last_message_preview VARCHAR(255),
    last_message_at TIMESTAMP WITH TIME ZONE,
    initiator_unread_count INT NOT NULL DEFAULT 0,
    recipient_unread_count INT NOT NULL DEFAULT 0,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_conversations_listing_users
        UNIQUE (listing_id, initiator_id, recipient_id)
);
```

#### V46__Create_Messages.sql

```sql
CREATE TABLE messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    conversation_id UUID NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message_type VARCHAR(50) NOT NULL,
    content TEXT,
    attachments JSONB,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP WITH TIME ZONE,
    read_by UUID[],
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
```

**現有 API 端點（本版本 DB 補齊後正式可用）**:

| Method | Path | 說明 |
|--------|------|------|
| POST | `/v2/chat/conversations` | 建立或取得對話（冪等，UNIQUE constraint 保證） |
| GET | `/v2/chat/conversations` | 取得對話列表（分頁） |
| POST | `/v2/chat/messages` | 發送訊息 |
| GET | `/v2/chat/conversations/{id}/messages` | 取得訊息歷史（分頁） |
| PUT | `/v2/chat/conversations/{id}/read` | 標記已讀 |
| DELETE | `/v2/chat/conversations/{id}` | 關閉對話 |

---

### M11 物流與訂單履約整合（US-004 / DEF-007）

訂單狀態生命週期正式連通物流系統。

**LogisticsService 新增邏輯**:

| 方法 | 新增行為 |
|------|---------|
| `createLogistics()` | 前置驗證 `order.status == CONFIRMED`（非 CONFIRMED 拋 BusinessException）；建立物流記錄後同步 `order.status = SHIPPING` |
| `updateLogisticsStatus()` | 物流狀態更新至 `DELIVERED` 時同步 `order.status = DELIVERED` |

**訂單狀態流轉**:
```
CREATED → PAID → CONFIRMED → [createLogistics] → SHIPPING → [物流DELIVERED] → DELIVERED
```

---

### ShippingTemplate 接入訂單結帳（US-005 / DEF-008）

訂單建立時自動計算運費並納入 `totalAmount`。

#### V47__Add_Shipping_Fee_To_Orders.sql

```sql
ALTER TABLE orders
    ADD COLUMN shipping_fee NUMERIC(10, 2) NOT NULL DEFAULT 0.00;
```

**運費計算邏輯**（`ShippingTemplateService.calculateFeeForTenant()`）:

| 場景 | shippingFee |
|------|-------------|
| 無運費模板 | `0`（免運） |
| 小計 >= 免運門檻 | `0`（免運） |
| 小計 < 免運門檻 | `template.fixedAmount` |

**OrderResponse 新增欄位**:
```json
{
  "data": {
    "shippingFee": 60,
    "totalAmount": 260,
    ...
  }
}
```

---

## 效能優化 ⚡

### M13 Dashboard @Cacheable（US-006 / AI-703）

`GET /v2/seller/dashboard` 加上 Spring Cache，高頻讀取時跳過 DB 查詢。

```java
@Cacheable(value = "dashboardStats", key = "#tenantId")
public DashboardStats getDashboard(UUID tenantId) { ... }
```

- **Cache key**: `tenantId`（租戶隔離）
- **Cache type**: Simple Cache（記憶體，JVM 生命週期）
- **後續**: Sprint 24+ 評估切換為 Redis TTL

---

## 技術改善 🔧

### act CI 整合測試修正

| 問題 | 修正 |
|------|------|
| `@WithMockUser` → TenantContext 為 null → 404 | 改用 `UserPrincipal` 手動建立 `SecurityContextHolder` |
| Redis 無密碼 → `@Cacheable` NPE | `application-integration-test.yml` 加入 `spring.cache.type: simple` |
| `Matchers.closeTo(double, double)` → Integer/Double 型別不符 | 改為 `.value(int)` |

---

## 資料庫變更 🗃️

| Migration | 說明 | 影響 |
|-----------|------|------|
| V45__Create_Conversations.sql | 建立 conversations 表 | M10 IM 對話功能可用 |
| V46__Create_Messages.sql | 建立 messages 表 | M10 IM 訊息功能可用 |
| V47__Add_Shipping_Fee_To_Orders.sql | orders 表新增 shipping_fee 欄位（DEFAULT 0.00） | 向後相容，現有訂單 shippingFee = 0 |

---

## 破壞性變更 ⚠️

**無破壞性變更。** 所有 API 均為新增功能或向後相容修改。

**注意事項**:
- V47 的 `shipping_fee` 欄位加入後，現有 `OrderResponse` 序列化會多一個 `shippingFee: 0` 欄位
- 前端如有 strict JSON schema validation 需確認不影響現有解析

---

## 升級注意事項

1. **執行 Flyway Migration**（V45/V46/V47 自動執行，需確認 DB 連線）
2. **M10 IM 功能啟用**：conversations + messages 表建立後，`/v2/chat/*` 端點正式可用
3. **運費欄位**：現有訂單 `shippingFee = 0.00`（DEFAULT），新訂單依 ShippingTemplate 計算
4. **儀表板快取**：`dashboardStats` 快取在 JVM 重啟前有效，不影響資料正確性

---

## Commit 參考

| US | Commit | 說明 |
|----|--------|------|
| US-001/002/003 | `7b69933` | M10 IM Flyway V45/V46 + 8 個整合測試 |
| US-004 | `1228a38` | M11 物流與訂單履約整合（DEF-007） |
| US-005 | `70c0a02` | ShippingTemplate 接入結帳（DEF-008）+ V47 |
| US-006 | `60081fc` | M13 Dashboard @Cacheable（Buffer-B） |
| CI 修正 | `c5a76bb` | act CI SecurityContext + Cache + 斷言修正 |

---

**版本**: v2026.09.12-01
**文件版本**: v1.0
**建立日期**: 2026-06-27
**建立者**: Dev David + SD Marcus + Claude Code
