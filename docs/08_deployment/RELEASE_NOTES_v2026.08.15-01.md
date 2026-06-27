# Release Notes - v2026.08.15-01

**發布日期**: 2026-08-15
**發布類型**: Minor（架構強化 + 新功能）
**Sprint**: Sprint 21
**Git Tag**: `v2026.08.15-01`
**基於 Commit**: `f79653d`（Sprint 21 US-006 最終 commit）
**專案**: E-Commerce Platform (B2B2C Multi-tenant)

---

## 🎯 發布摘要

Sprint 21 完成六項交付：**MQ 通知歷史一致性保障**（Consumer ACK 後寫入）、**Stripe Java SDK Phase 3 真實整合**（WireMock 隔離）、**M11 物流 Provider 策略抽象**（HCT/SINOPAC Stub + Factory）、**評分統計 Redis 快取**（AI-503 達成）、**M14 租戶活躍統計 API**、**M11 運費模板 CRUD API**（FIXED/FREE_THRESHOLD 費型）。

### 關鍵指標

| 項目 | 數值 |
|------|------|
| User Stories 完成 | 6 / 6（100%，含 Buffer-A）|
| Story Points | 12 SP（規劃 12 SP） |
| 達成率 | 100% |
| Integration Tests | 293 tests, 0 Failures, 0 Errors |
| 新增測試 | ~30 個（Unit ~16 + Integration 14） |
| 新增 Flyway Migration | V43__Create_Shipping_Templates.sql |
| CI 狀態 | ✅ BUILD SUCCESS |
| Checkstyle 違規 | 0 |

---

## 新功能 ✨

### M14 平台管理台 — 租戶活躍統計 API（US-005）

#### 租戶活躍統計

- **新增 API**: `GET /v2/admin/tenants/{tenantId}/stats`
- **功能**: 查詢指定租戶的近期活躍指標（需 SUPER_ADMIN 角色）
- **統計窗口**: 近 30 天訂單數

**回應範例**:
```json
{
  "success": true,
  "data": {
    "tenantId": "550e8400-e29b-41d4-a716-446655440000",
    "orderCount30d": 42,
    "activeUserCount": 150,
    "activeListingCount": 28,
    "lastOrderAt": "2026-08-14T16:30:00Z"
  }
}
```

> `lastOrderAt` 在租戶無訂單時回傳 `null`。

---

### M11 物流追蹤 — 運費模板 CRUD API（US-006）

#### 建立運費模板

- **新增 API**: `POST /v2/shipping-templates`
- **功能**: 商家建立固定運費或滿額免運模板（需 `product:create` 權限）
- **費型**: `FIXED`（固定運費）/ `FREE_THRESHOLD`（滿額免運）

**Request 範例（FIXED）**:
```json
{
  "name": "標準運費",
  "feeType": "FIXED",
  "fixedAmount": 80
}
```

**Request 範例（FREE_THRESHOLD）**:
```json
{
  "name": "滿 500 免運",
  "feeType": "FREE_THRESHOLD",
  "fixedAmount": 80,
  "freeThreshold": 500
}
```

**Response**:
```json
{
  "success": true,
  "message": "Shipping template created",
  "data": {
    "id": "uuid",
    "name": "標準運費",
    "feeType": "FIXED",
    "fixedAmount": 80,
    "freeThreshold": null,
    "createdAt": "2026-08-14T10:00:00Z",
    "updatedAt": "2026-08-14T10:00:00Z"
  }
}
```

#### 查詢模板列表

- **新增 API**: `GET /v2/shipping-templates`
- **功能**: 查詢當前租戶的所有運費模板（需 `product:read` 權限）

#### 更新模板

- **新增 API**: `PUT /v2/shipping-templates/{id}`
- **功能**: 更新指定模板（租戶隔離，不可操作他租戶模板）

#### 刪除模板

- **新增 API**: `DELETE /v2/shipping-templates/{id}`
- **功能**: 刪除指定模板（租戶隔離）

#### 運費計算

- **新增 API**: `GET /v2/shipping-templates/{id}/calculate-fee?orderAmount={amount}`
- **功能**: 根據模板費型和訂單金額計算應付運費

**Response（FREE_THRESHOLD 滿額免運）**:
```json
{
  "success": true,
  "data": {
    "templateId": "uuid",
    "orderAmount": 500,
    "shippingFee": 0,
    "feeType": "FREE_THRESHOLD"
  }
}
```

**Response（未達門檻）**:
```json
{
  "success": true,
  "data": {
    "templateId": "uuid",
    "orderAmount": 499,
    "shippingFee": 80,
    "feeType": "FREE_THRESHOLD"
  }
}
```

---

## 架構強化 🏗️

### MQ 通知歷史一致性保障（US-001）

**改善前**:
- `NotificationService.sendNotification()` 在 Producer 端用 try-catch 寫入 history
- 若 Consumer 端失敗，history 仍已寫入（語義不正確，雙寫風險）

**改善後**:
- history 寫入移至 `NotificationConsumerService.processMessage()` 成功後執行
- history 寫入失敗時記錄 `log.warn` 並繼續 ACK（容錯設計）
- 原 `NotificationService` 的 try-catch history 路徑已移除（消除雙寫）

**保障語義**: 至少一次交付（Consumer ACK → history 寫入）

---

### Stripe SDK Phase 3 — 真實 SDK 整合（US-002）

**改善前**:
- `StripePaymentGateway` 為 Mock 實作（Phase 2-B 預留）

**改善後**:
- 引入 `com.stripe:stripe-java >= 24.x`
- `PaymentIntentCreateParams` + `PaymentIntent.create()` 真實呼叫
- Idempotency Key（訂單 UUID）防止重複扣款
- 例外處理：`CardException` → `PAYMENT_CARD_DECLINED`；`ApiException` → `PAYMENT_PROVIDER_ERROR`

**測試隔離**: WireMock Stub 模擬 Stripe API，CI 無需真實 API key

---

### M11 物流 LogisticsProvider 策略抽象（US-003）

| 元件 | 說明 |
|------|------|
| `LogisticsProvider` 介面 | 定義 `createShipment()`, `trackShipment()`, `getProviderCode()` |
| `HCTLogisticsProvider` | 黑貓宅急便 Stub，`providerCode = "HCT"` |
| `SinoPacLogisticsProvider` | 新竹物流 Stub，`providerCode = "SINOPAC"` |
| `LogisticsProviderFactory` | Spring Bean Map 自動注入，依 `providerCode` 路由 |

**後向相容**: `LogisticsService` 對外 API 介面不變，Controller 層零修改

---

## 效能優化 ⚡

### 評分統計 Redis 快取（US-004 / AI-503）

- `ReviewService.getRatingStats(UUID productId)` 加入 `@Cacheable(value = "ratingStats", key = "#productId")`
- `ReviewService.createReview()` 觸發 `@CacheEvict(value = "ratingStats", key = "#productId")`
- Redis TTL: 300 秒（5 分鐘）
- **效果**: 高流量場景下 DB 查詢次數大幅降低，同一商品評分統計第二次調用命中快取

---

## 資料庫變更 🗃️

### V43__Create_Shipping_Templates.sql

```sql
CREATE TABLE shipping_templates (
    id             UUID         NOT NULL DEFAULT gen_random_uuid(),
    tenant_id      UUID         NOT NULL REFERENCES tenants(id),
    name           VARCHAR(100) NOT NULL,
    fee_type       VARCHAR(20)  NOT NULL CHECK (fee_type IN ('FIXED', 'FREE_THRESHOLD')),
    fixed_amount   DECIMAL(10, 2),
    free_threshold DECIMAL(10, 2),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT pk_shipping_templates PRIMARY KEY (id)
);
CREATE INDEX idx_shipping_templates_tenant_id ON shipping_templates(tenant_id);
```

**約束說明**:
- `fee_type` CHECK 約束：只允許 `FIXED` 或 `FREE_THRESHOLD`
- `fixed_amount` 和 `free_threshold` 可為 null（視費型而定）
- 外鍵 `tenant_id → tenants(id)` 確保租戶隔離

---

## 新增 ErrorCode 🔢

| Code | 說明 | HTTP Status |
|------|------|-------------|
| `E-7504` | Shipping template not found | 404 |

---

## 破壞性變更 ⚠️

無破壞性變更。

---

## 升級注意事項

1. 執行 Flyway Migration（V43 自動執行，需確認 DB 連線）
2. 確認 `application.yml` 中 `spring.cache.type=redis` 已配置（getRatingStats 快取依賴）
3. Stripe SDK 整合需設定環境變數 `STRIPE_SECRET_KEY`（CI/CD 中注入 test key）
4. 新增 `E-7504` ErrorCode，`GlobalExceptionHandler` 已納入 404 處理

---

## Commit 參考

| US | Commit | 說明 |
|----|--------|------|
| US-001 | `b64627b` | AI-502 — notification_history 移至 MQ Consumer ACK 後寫入 |
| US-002 | `8865c9f` | Stripe SDK Phase 3 真實 SDK 整合 + WireMock 測試 |
| US-003 | `9a70b02` | M11 LogisticsProvider 策略模式抽象 |
| US-004 | `6490207` | AI-503 getRatingStats @Cacheable 效能優化 |
| US-005 | `322cd83` | M14 租戶活躍統計 API |
| US-006 | `f79653d` | M11 運費模板 CRUD API（Buffer-A） |

---

**版本**: v2026.08.15-01
**文件版本**: v1.0
**建立日期**: 2026-06-27
**建立者**: Dev David + Claude Code
