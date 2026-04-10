# M12 動態定價測試案例 / Dynamic Pricing Test Cases

> **模組**: M12 動態定價引擎
> **版本**: v1.0
> **建立日期**: 2026-04-10
> **依據**: API_M12_Dynamic_Pricing.md, SRD_Database_Schema.md, SRD_System_Architecture.md
> **測試框架**: JUnit 5 + Mockito (UT), Spring Boot Test (IT), REST Assured (API)

---

## 📋 測試案例總覽

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 4 | 3 | 2 | 9 |
| IT | 2 | 2 | 1 | 5 |
| API | 2 | 3 | 1 | 6 |
| **合計** | 8 | 8 | 4 | **20** |

---

## 1. 單元測試 (Unit Tests)

### 1.1 價格計算引擎邏輯

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M12-001 | 價格計算-平日無折扣 | P0 | basePrice=1000, 無規則 | 1. 計算平日價格 | 價格 = 1000 |
| UT-M12-002 | 價格計算-週末加成 | P0 | 週六, MULTIPLIER=1.3 | 1. 計算週六價格 | 價格 = 1300 |
| UT-M12-003 | 價格計算-節日加成 | P0 | 春節, MULTIPLIER=1.5 | 1. 計算春節價格 | 價格 = 1500 |
| UT-M12-004 | 價格計算-早鳥折扣 | P0 | 入住前 7 天, PERCENTAGE=-0.10 | 1. 計算早鳥價格 | 價格 = 原價 × 0.9 |
| UT-M12-005 | 價格計算-長住折扣7天 | P1 | 入住 7 天, PERCENTAGE=-0.10 | 1. 計算長住價格 | 價格 = 原價 × 0.9 |
| UT-M12-006 | 價格計算-長住折扣30天 | P1 | 入住 30 天, PERCENTAGE=-0.20 | 1. 計算長住價格 | 價格 = 原價 × 0.8 |
| UT-M12-007 | 價格計算-最後一刻折扣 | P2 | 入住前 3 天, PERCENTAGE=-0.15 | 1. 計算最後一刻價格 | 價格 = 原價 × 0.85 |

### 1.2 規則優先級排序

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M12-008 | 優先級-高優先級覆蓋低優先級 | P0 | 規則A(priority=100) + 規則B(priority=10) | 1. 計算價格 | 只套用規則A |
| UT-M12-009 | 優先級-同優先級時後建立覆蓋 | P1 | 同 priority 的兩個規則 | 1. 計算價格 | 後建立的規則覆蓋先建立的 |

---

## 2. 整合測試 (Integration Tests)

### 2.1 定價規則 CRUD

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M12-001 | 建立規則-成功 | P0 | StoreOwner + DYNAMIC_PRICING_ENABLED | 1. POST /api/v2/dashboard/pricing/rules<br>Body: {"ruleType":"WEEKEND","ruleName":"週末加成",...} | 201, ruleId 不為 null |
| IT-M12-002 | 建立規則-DYNAMIC_PRICING_ENABLED未啟用 | P0 | StoreOwner + DYNAMIC_PRICING_ENABLED=false | 1. POST /api/v2/dashboard/pricing/rules | 403, FEATURE_DISABLED |
| IT-M12-003 | 更新規則-成功 | P1 | 已有規則 | 1. PUT /api/v2/dashboard/pricing/rules/{id}<br>Body: {"adjustment":{"type":"MULTIPLIER","value":1.5}} | 200, 規則已更新 |
| IT-M12-004 | 刪除規則-成功 | P1 | 已有規則 | 1. DELETE /api/v2/dashboard/pricing/rules/{id} | 200, 規則已刪除 |
| IT-M12-005 | 查詢規則列表-依房源篩選 | P1 | 多個房源有規則 | 1. GET /api/v2/dashboard/pricing/rules?listingId={id} | 只回傳該房源的規則 |

### 2.2 價格計算 (含折扣)

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M12-006 | 價格計算-週末+早鳥 | P0 | 週五入住, 早鳥規則 | 1. GET /api/v2/listings/{id}/price?checkIn=週五 | 週末加成後再套用早鳥折扣 |
| IT-M12-007 | 價格計算-長住折扣套用 | P0 | 入住 10 天, 長住折扣 | 1. GET /api/v2/listings/{id}/price?checkIn=date1&checkOut=date2 (10晚) | 長住折扣正確套用 |
| IT-M12-008 | 手動覆蓋-優先於規則 | P0 | 有 Override 設定 | 1. GET /api/v2/listings/{id}/price (該日期有 Override) | 直接使用 Override 價格 |

---

## 3. API E2E 測試 (API E2E Tests)

### 3.1 取得動態價格 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M12-001 | GET /api/v2/listings/:id/price-成功 | P0 | 房源有定價規則 | 1. GET /api/v2/listings/{id}/price?checkInDate=2026-05-01&checkOutDate=2026-05-03 | 200, data 包含 breakdown 和 totalPrice |
| API-M12-002 | GET /api/v2/listings/:id/price-無規則 | P1 | 房源無定價規則 | 1. GET /api/v2/listings/{id}/price | 200, 回傳 basePrice |
| API-M12-003 | GET /api/v2/listings/:id/price-缺少必填參數 | P1 | 無 | 1. GET /api/v2/listings/{id}/price (缺少 checkInDate) | 400, 缺少必填參數 |

### 3.2 定價規則管理 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M12-004 | GET /api/v2/dashboard/pricing/rules-規則列表 | P0 | StoreOwner + 有規則 | 1. GET /api/v2/dashboard/pricing/rules | 200, data.rules 包含規則列表 |
| API-M12-005 | POST /api/v2/dashboard/pricing/rules-建立規則 | P0 | StoreOwner + DYNAMIC_PRICING_ENABLED | 1. POST /api/v2/dashboard/pricing/rules<br>Body: {"ruleType":"WEEKEND",...} | 201, data.ruleId |
| API-M12-006 | PUT /api/v2/dashboard/pricing/rules/:id-更新規則 | P1 | StoreOwner + 自有規則 | 1. PUT /api/v2/dashboard/pricing/rules/{id}<br>Body: {"adjustment":{"value":1.8}} | 200, 規則已更新 |
| API-M12-007 | DELETE /api/v2/dashboard/pricing/rules/:id-刪除規則 | P1 | StoreOwner + 自有規則 | 1. DELETE /api/v2/dashboard/pricing/rules/{id} | 200, 規則已刪除 |
| API-M12-008 | POST /api/v2/dashboard/pricing/rules/:id/override-手動覆蓋 | P0 | StoreOwner + 自有房源 | 1. POST /api/v2/dashboard/pricing/rules/{listingId}/override<br>Body: {"overrides":[{"date":"2026-05-01","price":5000}]} | 200, 覆蓋已建立 |

---

## 4. 價格計算流程測試

### 4.1 價格計算優先級

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| PRIO-001 | 優先級順序-Override > Season > Weekend > Discount | P0 | 同時有多種類型規則 | 1. 計算含 Override 的週末價格 | 使用 Override 價格 |
| PRIO-002 | 優先級順序-Season > Weekend (無 Override) | P0 | 旺季 + 週末規則 | 1. 計算旺季週末價格 | 使用較高優先級的規則 |
| PRIO-003 | 優先級順序-MULTIPLIER 疊加 | P0 | 週五 + 旺季 | 1. 計算旺季週五價格 | 旺季倍率 > 週末倍率，套用旺季 |
| PRIO-004 | 優先級順序-長住折扣套用位置 | P0 | 週五 + 長住 | 1. 計算週五長住價格 | 週五倍率套用後，再套用長住折扣 |

### 4.2 價格計算邊界條件

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| BOUND-001 | 邊界-入住前 7 天早鳥 | P1 | 入住前剛好 7 天 | 1. 計算價格 | 早鳥折扣正確套用 |
| BOUND-002 | 邊界-入住前 6 天早鳥不適用 | P1 | 入住前 6 天 | 1. 計算價格 | 早鳥折扣不套用 |
| BOUND-003 | 邊界-長住 6 天不適用 | P2 | 入住 6 天 | 1. 計算價格 | 長住折扣不套用 (需 7 天) |
| BOUND-004 | 邊界-長住 7 天適用 | P1 | 入住 7 天 | 1. 計算價格 | 長住折扣正確套用 |

---

## 📝 關鍵驗證點總結

| 驗證項目 | 測試案例 | 優先級 |
|---------|---------|--------|
| 價格計算流程: Override → Season → Weekend → Discount | PRIO-001, PRIO-002, IT-M12-008 | P0 |
| 早鳥/長住折扣正確計算 | UT-M12-004, UT-M12-005, UT-M12-006, BOUND-001 ~ BOUND-004 | P0 |
| 優先級高規則覆蓋低優先級規則 | UT-M12-008, UT-M12-009, PRIO-001 ~ PRIO-004 | P0 |
| 手動覆蓋優先於所有規則 | IT-M12-008, API-M12-008 | P0 |

---

## 📝 價格計算流程圖

```
1. 取得 basePrice（listings.base_price）
        │
        ▼
2. 檢查是否有手動覆蓋（Override）
        │ 有 → 使用覆蓋價格，直接輸出
        │ 無 → 繼續
        ▼
3. 套用規則（按 priority 從高到低）
        │
        ├── WEEKEND → basePrice × 1.3
        ├── HOLIDAY → basePrice × 1.5
        ├── PEAK_SEASON → basePrice × 2.0
        ├── EARLY_BIRD → subtotal - 10%
        ├── LAST_MINUTE → subtotal - 15%
        └── LONG_STAY → subtotal - X%
        │
        ▼
4. 套用長住折扣（如果符合條件）
        │
        ▼
5. 輸出最終價格
```

---

## 📝 動態定價規則類型說明

| 規則類型 | 說明 | 調整方式 |
|---------|------|---------|
| WEEKDAY | 平日價格 | basePrice × 1.0 |
| WEEKEND | 週末加成 | basePrice × 1.3 |
| HOLIDAY | 節日加成 | basePrice × 1.5 |
| PEAK_SEASON | 旺季加成 | basePrice × 2.0 |
| EARLY_BIRD | 早鳥折扣 | -10%（入住前 7 天）|
| LAST_MINUTE | 最後一刻 | -15%（入住前 3 天）|
| LONG_STAY | 長住折扣 | 7天-10%，30天-20% |
| OVERRIDE | 手動覆蓋 | 直接指定價格 |

---

**文件版本**: AISDLC v0.09
**測試框架**: JUnit 5 + Mockito, Spring Boot Test, REST Assured
**最後更新**: 2026-04-10
