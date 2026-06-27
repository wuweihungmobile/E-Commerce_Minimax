# Sprint 22 計劃 / Sprint 22 Plan

> **Sprint 編號**: Sprint 22
> **期間**: 2026-08-18 ~ 2026-08-29 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-27
> **基於**: [Sprint 21 Retrospective](../05_development/SPRINT_21_RETRO.md) + PM/PO Victoria M12 決策 + [DEFERRED_ITEMS_TRACKER.md](./DEFERRED_ITEMS_TRACKER.md)

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 21 開發完成 | ✅ 6/6 US 完成（100% SP） | US-001~006 全部 AC 達成 |
| Sprint 21 測試狀態 | ✅ 293 integration tests, 0 Failures | `mvn verify -Pintegration-test` BUILD SUCCESS |
| Sprint 21 Review | ✅ [SPRINT_21_REVIEW.md](../05_development/SPRINT_21_REVIEW.md) | 建立於 2026-06-27 |
| Sprint 21 Retrospective | ✅ [SPRINT_21_RETRO.md](../05_development/SPRINT_21_RETRO.md) | 3 個 Action Items（AI-601~603） |
| Sprint 21 Release | ✅ [RELEASE_NOTES_v2026.08.15-01.md](../08_deployment/RELEASE_NOTES_v2026.08.15-01.md) | Tag `v2026.08.15-01` 已建立 |
| Sprint 21 Action Items | ✅ AI-601~603 已建立 | 納入 Sprint 22 規劃 |
| Deferred Items 審查 | ✅ DEF-005/006/007/008 已登記 | DEF-005 M10 SA 分析 Sprint 22 必須執行 |

### 🔴 M12 動態定價現況說明（2026-06-27 調查後更新）

> **調查日期**: 2026-06-27（Sprint 22 Tasks 建立時發現）
> **結論**: **M12 核心 CRUD 已實作，Sprint 22 改為「延伸」而非「新建」**

| 現況 | 說明 |
|------|------|
| `pricing_rules` 表 | ✅ V1__Initial_Schema.sql 已建立（Room 用途，JSONB config） |
| `PricingService` (470 行) | ✅ CRUD + calculatePrice + setCalendarPrice + overridePrice |
| `PricingController` | ✅ `/v2/dashboard/pricing` 6 個端點（`room:*` 權限） |
| `PricingServiceTest` (9 tests) | ✅ 單元測試存在 |
| `M12PricingIntegrationTest` (8 tests) | ✅ 整合測試存在（Mock 模式） |
| **缺口 1** | ❌ 無 Product listing 定價支援（僅 `room_listing_id`，無 general `listing_id`） |
| **缺口 2** | ❌ 無 customer-facing effective-price API（`GET /v2/listings/{id}/effective-price`） |
| **缺口 3** | ❌ 無 REST Assured E2E 測試（現有為 MockMvc + Mock Repository） |

**Sprint 22 M12 策略調整**：
- US-001（原 3 SP → 調整為 2 SP）：M12 延伸 — `listing_id` 支援 + REST Assured E2E 測試
- US-002（原 3 SP → 調整為 1 SP）：M12 延伸 — customer-facing effective-price API
- **節省 3 SP** → 轉移至 M13 商家工作台（擴充）或其他新功能

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 22 |
| **開始日期** | 2026-08-18 (週一) |
| **結束日期** | 2026-08-29 (週五) |
| **Sprint 容量** | 15 SP |
| **規劃 SP** | 12 SP |
| **Buffer** | 3 SP（20%，維持 AI-501 目標） |
| **團隊** | 2 人 Dev Team |

> **容量說明**: Sprint 21 Velocity 12 SP（含 Buffer-A）。Sprint 22 維持 12 SP 規劃，Buffer 3 SP（20%）。

---

## 2. Sprint 目標（v1.1 更新：M12 現況調查後修訂）

> **目標**: 填補 M12 動態定價缺口（listing_id 延伸 + effective-price API + E2E 測試），完成 M13 商家工作台基礎儀表板，並執行 DEF-005 M10 SA 需求分析（AI-602）。

### 具體目標

#### 🎯 P0 必須完成

| 功能 | 優先級 | 依據 |
|------|--------|------|
| M12 延伸 — `listing_id` 支援 + REST Assured E2E 測試 | P0 | 填補現有 Room-only 限制，AI-601 |
| M12 延伸 — customer-facing effective-price API | P0 | AI-601，對買家/商家公開計算結果 |

#### 🏗️ P1 重要項目

| 功能 | 優先級 | 依據 |
|------|--------|------|
| M13 商家工作台 — 基礎儀表板 API | P1 | Phase 1 P1 功能，商家運營需求 |
| M14 Feature Toggle 商家自管 API | P1 | 平台管理功能延伸 |

#### 🔄 Buffer 候選項目

| 功能 | 優先級 | 預估 SP |
|------|--------|---------|
| DEF-005: M10 IM SA 需求分析（文件，不開發） | Buffer-A（必須執行，AI-602） | 2 SP |
| DEF-006: M11 Provider Stub 強化 | Buffer-B | 1 SP |

---

## 3. User Stories（v1.1：M12 現況調查後修訂）

> **⚠️ 重要修訂**: Sprint 22 Planning 時發現 M12 核心已實作（`PricingService` + `PricingController` + 9 單元測試 + 8 整合測試）。US-001/US-002 改為「延伸現有 M12」而非「重新建立」。

### 📋 P0 必須完成

---

#### US-001: M12 延伸 — Product Listing 定價支援 + REST Assured E2E 測試

> **優先級**: P0 | **Story Points**: 2 SP | **負責人**: Dev David

**現況**: `pricing_rules` 表僅有 `room_listing_id`（民宿/房源專用）。`PricingController` 使用 `room:*` 權限，Product 商家無法設定定價規則。

**User Story**:
```
作為商品商家
我想要為我的商品設定動態定價規則（如節慶促銷、大量折扣）
以便系統在計算訂單時自動套用正確售價
```

**驗收標準**:
- [ ] AC-001: Flyway V44 — `ALTER TABLE pricing_rules ADD COLUMN listing_id UUID REFERENCES listings(id)`（新增一般 listing 欄位，保留 `room_listing_id` 後向相容）
- [ ] AC-002: `PricingController` 新增 `product:create/read/update/delete` 權限支援（與現有 `room:*` 並存）
- [ ] AC-003: `PricingService.createRule()` 接受 `listingId` 參數（與 `roomListingId` 二選一）
- [ ] AC-004: 新增 `PricingControllerE2ETest.java`（REST Assured）：
  - SELLER 建立 Product 定價規則（listingId）→ 200
  - SELLER 查詢 listingId 規則 → 200 + 列表
  - BUYER 建立規則 → 403
- [ ] AC-005: 現有 `M12PricingIntegrationTest` 8 個測試仍全部通過

**技術備註（SD Marcus）**:
- ALTER TABLE 比 CREATE TABLE 安全，`room_listing_id` 保持 nullable，`listing_id` 也 nullable
- `PricingService` 需以 `listingId` 作為篩選條件的等效邏輯
- 現有 `room:*` 權限路徑保持不變（後向相容）

---

#### US-002: M12 延伸 — customer-facing effective-price API

> **優先級**: P0 | **Story Points**: 1 SP | **負責人**: Dev David

**現況**: 現有 `POST /v2/dashboard/pricing/calculate` 需要完整 Request Body，且為商家後台端點。買家/消費者端無法以簡單 GET 查詢特定 listing 的有效售價。

**User Story**:
```
作為消費者
我想要查詢某商品/房源在指定日期的有效售價（含折扣規則）
以便做出正確的購買決策
```

**驗收標準**:
- [ ] AC-001: 新增 `GET /v2/listings/{listingId}/effective-price?checkDate=YYYY-MM-DD&stayDays=N`
- [ ] AC-002: 回傳：`{ listingId, checkDate, stayDays, basePrice, effectivePrice, appliedRuleType, appliedRuleId }`（無規則時 effectivePrice = basePrice，appliedRuleType = null）
- [ ] AC-003: 無需登入（Public endpoint，或 `product:read`）
- [ ] AC-004: 整合測試：建立規則後查詢 → effectivePrice 反映折扣；無規則 → effectivePrice = basePrice

**技術備註**:
- 端點加入 `ListingController`（已有 `GET /v2/listings/{id}` 等端點）
- 複用現有 `PricingService.calculatePrice()` 邏輯
- 不重複建立計算邏輯，僅包裝成 REST 端點

---

### 🏗️ P1 重要項目

---

#### US-003: M13 商家工作台 — 基礎儀表板 API

> **優先級**: P1 | **Story Points**: 2 SP | **負責人**: Dev David

**User Story**:
```
作為商家
我想要查看我的銷售摘要儀表板（近期訂單、營收、活躍商品數）
以便快速掌握店舖營運狀況
```

**驗收標準**:
- [ ] AC-001: 新增 `GET /v2/seller/dashboard` API（需 SELLER 角色）
- [ ] AC-002: Response 包含：`{orderCount7d, orderCount30d, revenue30d, activeListingCount, pendingOrderCount, lastOrderAt}`
- [ ] AC-003: `orderCount7d`：近 7 天訂單數；`orderCount30d`：近 30 天訂單數；`revenue30d`：近 30 天已完成訂單總金額
- [ ] AC-004: 資料來源：`OrderRepository`（訂單數/營收）、`ListingRepository`（商品數）
- [ ] AC-005: 整合測試：SELLER 存取 → 200 + 各欄位存在；BUYER 存取 → 403

**技術備註**:
- 使用 `TenantContext.getCurrentTenant()` 取得 SELLER 的 tenantId（同 ShippingTemplateController 模式）
- `revenue30d` 僅計算 `status = COMPLETED` 的訂單金額
- 無需新增 Flyway migration（使用既有 tables）
- 新增常數：`DASHBOARD_7D_WINDOW = 7L`、`DASHBOARD_30D_WINDOW = 30L`（避免 MagicNumber）

---

### 🔄 Buffer 候選項目

---

#### US-004（Buffer-A）: DEF-005 — M10 IM SA 需求分析

> **優先級**: Buffer-A（🔴 必須執行，AI-602） | **Story Points**: 2 SP | **負責人**: SA Amanda
> **啟動條件**: US-001~003 全部完成（或 Sprint 進度允許）；此 Buffer 已連續延後兩次，**不得再延後**

**User Story**:
```
作為平台架構師
我想要有完整的 M10 IM 需求文件（WebSocket/MQTT 選型 + API 設計草稿）
以便 Sprint 23+ 可直接啟動開發
```

**驗收標準**:
- [ ] AC-001: 建立 `docs/02_architecture/M10_IM_REQUIREMENTS.md` — WebSocket vs MQTT 選型分析（至少比較 3 個維度：延遲、可靠性、擴展性）
- [ ] AC-002: 文件包含 M10 IM API 草稿（連線端點、訊息格式、房間/對話模型）
- [ ] AC-003: 文件包含技術依賴清單（Spring WebSocket / Stomp / MQTT Broker 選型建議）
- [ ] AC-004: PM/PO Victoria 對需求文件 Review 並 APPROVED

**注意**:
- 本項目為**文件工作**，不產生可運行程式碼
- SA Amanda 主導，Dev David 提供技術可行性意見
- Sprint 23 的 SD Marcus 架構設計依賴本文件

---

#### US-005（Buffer-B）: DEF-006 — M11 Provider Stub 強化

> **優先級**: Buffer-B | **Story Points**: 1 SP | **負責人**: Dev David
> **啟動條件**: US-001~004 全部完成

**驗收標準**:
- [ ] AC-001: `HCTLogisticsProvider.createShipment()` 回傳符合黑貓格式的追蹤號（格式：`HCT-YYYYMMDD-XXXXXXXX`）
- [ ] AC-002: `SinoPacLogisticsProvider.trackShipment()` 回傳模擬狀態流（CREATED → IN_TRANSIT → DELIVERED）
- [ ] AC-003: 新增 `LogisticsProviderIntegrationTest`，覆蓋 HCT/SINOPAC 的 Stub 輸出格式驗證

---

## 4. Sprint 容量分配摘要

| 類別 | US ID | 標題 | SP |
|------|-------|------|----|
| P0 | US-001 | M12 PricingRule 資料模型 + CRUD API | 3 |
| P0 | US-002 | M12 PriceCalculator + Listing 整合 | 3 |
| P1 | US-003 | M13 商家工作台基礎儀表板 API | 2 |
| Buffer-A | US-004 | DEF-005 M10 IM SA 需求分析 | 2 |
| Buffer-B | US-005 | DEF-006 M11 Provider Stub 強化 | 1 |
| **P0+P1 小計** | | | **8 SP** |
| **含 Buffer 合計** | | | **11 SP** |
| **未分配 Buffer** | | 技術債 / 突發狀況 | **1 SP** |
| **Sprint 容量** | | | **15 SP** |

---

## 5. 技術依賴與風險

### 5.1 新增資料庫表

| 表名 | Migration | 用途 |
|------|-----------|------|
| `pricing_rules` | V44__Create_Pricing_Rules.sql | M12 動態定價規則 |

### 5.2 風險矩陣

| 風險 | 可能性 | 影響 | 緩解措施 |
|------|--------|------|---------|
| M12 規則優先級邏輯複雜 | 中 | 中 | 先建立 PricingRuleTest 單元測試覆蓋所有費型，再實作 PriceCalculator |
| M13 多個 Repository 查詢效能 | 低 | 低 | 使用 `@Transactional(readOnly = true)` + 各 Repository 現有索引 |
| DEF-005 M10 SA 分析品質不足 | 低 | 中 | SA Amanda 提供草稿後由 SD Marcus Review 技術可行性 |

### 5.3 架構影響

| 模組 | 影響說明 |
|------|---------|
| `domain.model.pricing` | 新增 `PricingRule` Entity（新 package） |
| `domain.repository` | 新增 `PricingRuleRepository`（JPA） |
| `core.pricing` | 新增 `PricingRuleService`、`PriceCalculatorService` |
| `api.controller` | 新增 `PricingRuleController`、擴充 `ListingController`（effective-price） |
| `api.controller` | 新增 `SellerDashboardController` |

---

## 6. 測試策略（QA Quincy）

### 6.1 Sprint 22 測試計劃

| US | 單元測試 | 整合測試 | 新增測試數（預估）|
|----|---------|---------|-----------------|
| US-001 | `PricingRuleServiceTest` (+4) | `PricingRuleControllerE2ETest` (+4) | +8 |
| US-002 | `PriceCalculatorServiceTest` (+5) | `ListingControllerE2ETest` (+2) | +7 |
| US-003 | - | `SellerDashboardControllerE2ETest` (+3) | +3 |
| US-004 | 文件工作，無測試 | 文件工作，無測試 | 0 |
| US-005 | - | `LogisticsProviderIntegrationTest` (+3) | +3 |
| **合計** | | | **+21（預估）** |

> **Sprint 後預計測試總數**: ~613 + 21 = **~634+ tests**

### 6.2 DoD（Definition of Done）

- [ ] 所有 AC 通過整合測試驗證
- [ ] `mvn verify -Pintegration-test` BUILD SUCCESS（0 failures, 0 errors）
- [ ] `catch(Exception)` 生產程式碼維持 **0 處**
- [ ] `@Deprecated` 生產程式碼維持 **0 處**
- [ ] MagicNumber Checkstyle 違規 **0 處**
- [ ] M12 PriceCalculator 規則優先級邏輯有單元測試覆蓋
- [ ] DEF-005 M10 SA 需求文件建立並獲 PM/PO 確認

---

## 7. Sprint 22 Action Items（來自 Sprint 21 Retro）

| Action Item | 說明 | Sprint 22 處理方式 |
|-------------|------|------------------|
| AI-601 | M12 動態定價分段啟動 | ✅ US-001 + US-002（P0，分段實作） |
| AI-602 | M10 IM SA 需求分析不得再延後 | ✅ US-004（Buffer-A，標記為必須執行） |
| AI-603 | M11 物流與訂單整合評估 | ✅ 評估後納入 DEF-007（Sprint 23+ 規劃） |

---

## 8. M10 即時通訊模組路線圖（更新）

| 時程 | 活動 | 負責人 |
|------|------|--------|
| Sprint 22 | **US-004 Buffer-A**: SA Amanda 完成 M10 需求分析文件 | SA Amanda |
| Sprint 23 | SD Marcus 技術架構設計（WebSocket/MQTT 選型） | SD Marcus |
| Sprint 24+ | Dev David 開發啟動 | Dev David |

> **注意**: Sprint 22 如 US-001~003 提前完成，Buffer-A US-004 **必須啟動**，不得再次延後（AI-602）。

---

## 9. M12 動態定價模組路線圖

| 時程 | 活動 | US |
|------|------|---|
| Sprint 22 | PricingRule CRUD + PriceCalculator + effective-price API | US-001, US-002 |
| Sprint 23 | 訂單結帳整合（calculatePrice 接入訂單建立流程） | TBD |
| Sprint 24+ | 旺季/節假日自動排程 + 價格預覽日曆 | TBD |

---

## 10. 相關文件

| 文件 | 路徑 |
|------|------|
| Sprint 21 Retrospective | [SPRINT_21_RETRO.md](../05_development/SPRINT_21_RETRO.md) |
| Sprint 21 Review | [SPRINT_21_REVIEW.md](../05_development/SPRINT_21_REVIEW.md) |
| Sprint 22 Tasks | [SPRINT_22_TASKS.md](../05_development/SPRINT_22_TASKS.md)（待建立） |
| Release Notes Sprint 21 | [RELEASE_NOTES_v2026.08.15-01.md](../08_deployment/RELEASE_NOTES_v2026.08.15-01.md) |
| Deferred Items Tracker | [DEFERRED_ITEMS_TRACKER.md](./DEFERRED_ITEMS_TRACKER.md) |

---

**文件版本**: v1.0
**建立日期**: 2026-06-27
**建立者**: PM/PO Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy（AISDLC 協作）
**下一步**: 開始開發 US-001（M12 PricingRule CRUD），依序執行 P0 → P1 → Buffer-A → Buffer-B
