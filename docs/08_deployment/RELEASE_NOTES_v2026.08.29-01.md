# Release Notes - v2026.08.29-01

**發布日期**: 2026-08-29
**發布類型**: Minor（功能延伸 + 新功能 + SA 需求分析）
**Sprint**: Sprint 22
**Git Tag**: `v2026.08.29-01`
**基於 Commit**: `7d3be85`（Sprint 22 US-005 最終 commit）
**專案**: E-Commerce Platform (B2B2C Multi-tenant)

---

## 🎯 發布摘要

Sprint 22 完成五項交付：**M12 動態定價延伸至 Product Listing**（listing_id 支援 + product:* 雙權限）、**M12 消費者端 effective-price API**（`GET /v2/listings/{id}/effective-price`）、**M13 商家工作台基礎儀表板 API**（`GET /v2/seller/dashboard`，7 項統計指標）、**M10 IM SA 需求分析文件**（WebSocket + STOMP 選型，PM/PO APPROVED）、**M11 物流追蹤號格式強化**（日期欄位標準化）。

### 關鍵指標

| 項目 | 數值 |
|------|------|
| User Stories 完成 | 5 / 5（100%，含 Buffer-A + Buffer-B）|
| Story Points | 8 SP（規劃 12 SP，現況調查節省 4 SP）|
| 達成率 | 100% |
| Integration Tests | ~302 tests, 0 Failures, 0 Errors |
| 新增測試 | 12 個（Integration +9 + Unit +3） |
| 新增 Flyway Migration | V44__Add_Listing_Id_To_Pricing_Rules.sql |
| CI 狀態 | ✅ BUILD SUCCESS |
| Checkstyle 違規 | 0 |

---

## 新功能 ✨

### M12 動態定價 — Product Listing 支援（US-001）

M12 動態定價系統原僅支援民宿 Listing（`room_listing_id`），本版本延伸支援一般商品 Listing（`listing_id`）。

**API 變更**（新增 product:* 權限）:

| Method | Path | 新增說明 |
|--------|------|---------|
| POST | `/v2/dashboard/pricing/rules` | 新增 `product:create` 權限（原有 `room:create`） |
| GET | `/v2/dashboard/pricing/rules` | 新增 `?listingId=` 篩選參數 + `product:read` 權限 |
| PUT | `/v2/dashboard/pricing/rules/{ruleId}` | 新增 `product:update` 權限 |
| DELETE | `/v2/dashboard/pricing/rules/{ruleId}` | 新增 `product:delete` 權限 |

**Request 範例（建立 Product 定價規則）**:
```json
{
  "listingId": "880e8400-e29b-41d4-a716-446655440004",
  "ruleType": "SEASONAL",
  "config": { "discountPercent": 10 },
  "validFrom": "2026-08-18",
  "validTo": "2026-08-29",
  "isActive": true,
  "priority": 1
}
```

**Response**（新增 `listingId` 欄位）:
```json
{
  "success": true,
  "data": {
    "id": "uuid",
    "listingId": "880e8400-e29b-41d4-a716-446655440004",
    "roomListingId": null,
    "ruleType": "SEASONAL",
    "config": { "discountPercent": 10 },
    "validFrom": "2026-08-18",
    "validTo": "2026-08-29",
    "isActive": true,
    "priority": 1
  }
}
```

---

### M12 動態定價 — 消費者端有效定價 API（US-002）

**新增 API**: `GET /v2/listings/{listingId}/effective-price`

**功能**: 查詢指定商品在特定日期的最優惠有效價格（需 `product:read` 或 `room:read` 權限）

**Query Parameters**:
- `checkDate`（必填）: 查詢日期（格式 `YYYY-MM-DD`）
- `stayDays`（選填，預設 1）: 入住/使用天數

**Request 範例**:
```
GET /v2/listings/880e8400-e29b-41d4-a716-446655440004/effective-price?checkDate=2026-08-20&stayDays=2
```

**Response（有活躍定價規則）**:
```json
{
  "success": true,
  "data": {
    "listingId": "880e8400-e29b-41d4-a716-446655440004",
    "checkDate": "2026-08-20",
    "stayDays": 2,
    "basePrice": 500.00,
    "effectivePrice": 450.00,
    "appliedRuleType": "SEASONAL",
    "appliedRuleId": "uuid"
  }
}
```

**Response（無適用規則）**:
```json
{
  "success": true,
  "data": {
    "listingId": "...",
    "checkDate": "2026-08-20",
    "stayDays": 1,
    "basePrice": 500.00,
    "effectivePrice": 500.00,
    "appliedRuleType": null,
    "appliedRuleId": null
  }
}
```

**定價選取邏輯**: 取 validFrom ~ validTo 涵蓋 checkDate 且 isActive=true 的規則中 priority 最高者，套用 `discountPercent` 折扣計算。

---

### M13 商家工作台 — 基礎儀表板 API（US-003）

**新增 API**: `GET /v2/seller/dashboard`

**功能**: 查詢當前商家的多維度營運統計（需 `SELLER` 角色，透過 JWT tenantId 隔離）

**Response**:
```json
{
  "success": true,
  "data": {
    "orderCount7d": 5,
    "orderCount30d": 20,
    "revenue30d": 15000.00,
    "activeListingCount": 8,
    "pendingOrderCount": 3,
    "lastOrderAt": "2026-08-28T14:30:00Z"
  }
}
```

**欄位說明**:

| 欄位 | 說明 | 計算邏輯 |
|------|------|---------|
| `orderCount7d` | 近 7 天訂單數 | 任意狀態訂單 |
| `orderCount30d` | 近 30 天訂單數 | 任意狀態訂單 |
| `revenue30d` | 近 30 天已完成營收 | `status = COMPLETED` 訂單 `totalAmount` 加總（null → 0） |
| `activeListingCount` | 目前上架商品數 | `status = ACTIVE` |
| `pendingOrderCount` | 待處理訂單數 | `status IN (CREATED, PAID, CONFIRMED)` |
| `lastOrderAt` | 最近訂單時間 | 最新訂單 `createdAt`（null if 無訂單） |

---

## 架構強化 🏗️

### M10 IM 需求分析文件（US-004 / DEF-005 / AI-602）

**新增文件**: `docs/02_architecture/M10_IM_REQUIREMENTS.md`

- **技術選型**: WebSocket + STOMP（勝出理由：Spring 生態整合零成本、JWT 天然整合、無需額外 Broker）
- **API 設計**: 5 支 REST API（對話 CRUD + 歷史）+ 3 支 STOMP API（連線/訂閱/發送）
- **DB 設計**: `conversations`（unique constraint on tenant+buyer+seller+listing）+ `messages` 表
- **技術依賴**: `spring-boot-starter-websocket`（後端）+ `@stomp/stompjs`（前端）
- **PM/PO 審核**: ✅ Victoria APPROVED（2026-08-18）
- **後續**: Sprint 23 進入 SD Marcus 架構設計 + REST API 實作

---

### M11 物流追蹤號格式標準化（US-005 / DEF-006）

| Provider | 舊格式 | 新格式 |
|----------|--------|--------|
| HCT | `HCT-{UUID前8碼}` | `HCT-{yyyyMMdd}-{UUID前8碼}` |
| TCAT | `TCAT-{UUID前8碼}` | `TCAT-{yyyyMMdd}-{UUID前8碼}` |

**範例**: `HCT-20260829-E54D4619`、`TCAT-20260829-632D88A3`

---

## 資料庫變更 🗃️

### V44__Add_Listing_Id_To_Pricing_Rules.sql（US-001）

```sql
ALTER TABLE pricing_rules
    ADD COLUMN listing_id UUID REFERENCES listings(id) ON DELETE CASCADE;
CREATE INDEX idx_pricing_rules_listing_id ON pricing_rules(listing_id);
```

**影響**: 向後相容，現有 `room_listing_id` 資料不受影響，`listing_id` 預設 NULL。

---

## 破壞性變更 ⚠️

無破壞性變更。所有 API 均為新增或向後相容擴展。

---

## 升級注意事項

1. 執行 Flyway Migration（V44 自動執行，需確認 DB 連線）
2. **前端注意**: `GET /v2/listings/{id}/effective-price` 新端點，前端商品詳情頁可接入動態定價顯示
3. **商家端注意**: `GET /v2/seller/dashboard` 新儀表板端點，需 Bearer Token（含 SELLER 角色）
4. **定價規則注意**: 現有 Room Pricing Rule 的 `listingId` 欄位預設 null，Room 商家繼續使用 `roomListingId`

---

## Commit 參考

| US | Commit | 說明 |
|----|--------|------|
| US-001 + US-002 | `e019095` | M12 延伸 Product 定價 + effective-price API |
| US-003 + US-004 | `a528027` | M13 儀表板 API + M10 IM SA 需求分析 |
| US-005 | `7d3be85` | M11 物流 Provider Stub 追蹤號格式強化 |

---

**版本**: v2026.08.29-01
**文件版本**: v1.0
**建立日期**: 2026-06-27
**建立者**: Dev David + SA Amanda + Claude Code
