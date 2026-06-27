# Sprint 22 Review / Sprint 22 評審會議

> **Sprint 編號**: Sprint 22
> **期間**: 2026-08-18 ~ 2026-08-29
> **評審日期**: 2026-06-27（AI-AISDLC 延伸：開發完成後即建立）
> **建立日期**: 2026-06-27

---

## 1. Sprint 目標達成評估

> **Sprint 目標（v1.1）**: 填補 M12 動態定價缺口（listing_id 延伸 + effective-price API），完成 M13 商家工作台基礎儀表板，並清償 DEF-005 M10 IM SA 需求分析（AI-602）與 DEF-006 M11 Provider Stub 強化。

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| M12 延伸 — Product listing_id 支援 + product:* 權限 | ✅ 達成 | V44 Migration + PricingRule.listingId + PricingController 雙權限 |
| M12 延伸 — customer-facing effective-price API | ✅ 達成 | GET /v2/listings/{id}/effective-price + PricingService.getEffectivePrice() |
| M13 商家工作台基礎儀表板 API | ✅ 達成 | GET /v2/seller/dashboard 含 7 項統計指標 |
| DEF-005 M10 IM SA 需求分析（AI-602 Buffer-A） | ✅ 達成 | M10_IM_REQUIREMENTS.md 建立，PM/PO Victoria APPROVED |
| DEF-006 M11 Provider Stub 強化（Buffer-B） | ✅ 達成 | HCT/TCAT 追蹤號改為 {Provider}-{yyyyMMdd}-{HEX8} 格式 |

**Sprint 目標達成率**: 100%（5/5 US 全部完成，含 Buffer-A + Buffer-B）

---

## 2. User Story 完成狀態

| US | 標題 | SP | 狀態 | Commit |
|----|------|----|------|--------|
| US-001 | M12 延伸 — Product Listing 定價支援 + product:* 權限 | 2 | ✅ 完成 | `e019095` |
| US-002 | M12 延伸 — customer-facing effective-price API | 1 | ✅ 完成 | `e019095` |
| US-003 | M13 商家工作台 — 基礎儀表板 API | 2 | ✅ 完成 | `a528027` |
| US-004 | DEF-005 M10 IM SA 需求分析（Buffer-A） | 2 | ✅ 完成 | `a528027` |
| US-005 | DEF-006 M11 Provider Stub 強化（Buffer-B） | 1 | ✅ 完成 | `7d3be85` |
| **合計** | | **8 SP** | ✅ 100% | |

> Sprint 22 原規劃 12 SP，實際因 M12 現況調查（US-001: 3→2 SP，US-002: 3→1 SP）節省 4 SP，最終以 8 SP 完成所有既定目標並啟動全部 Buffer 項目。

---

## 3. 測試狀態

| 測試類型 | Sprint 前（Sprint 21 後） | Sprint 後 | 新增 |
|---------|--------------------------|----------|------|
| Unit Tests（mvn test） | ~320 | ~323 | +3（US-005 Provider 格式測試） |
| Integration Tests（-Pintegration-test） | 293 | ~302 | +9（US-001/002/003 各 3 個） |
| **合計** | **~613** | **~625** | **+12** |

**新增測試明細**:

| US | 新增整合測試 | Test IDs |
|----|------------|----------|
| US-001 | M12PricingProductIntegrationTest (+3) | IT-M12-009/010/011 |
| US-002 | M12EffectivePriceIntegrationTest (+3) | IT-EP-001/002/003 |
| US-003 | M13SellerDashboardIntegrationTest (+3) | IT-DASH-001/002/003 |
| US-004 | — | SA 分析文件，無測試 |
| US-005 | LogisticsProviderIntegrationTest (+3) | TC-PROV-001/002/003 |
| **合計** | **+12** | |

---

## 4. 新增 API 端點

### M12 動態定價延伸（US-001/002）

| Method | Path | 說明 | 新增/修改 |
|--------|------|------|----------|
| POST | `/v2/dashboard/pricing/rules` | 建立定價規則，新增 `product:create` 權限 | 修改 |
| GET | `/v2/dashboard/pricing/rules` | 查詢規則，新增 `?listingId=` 篩選參數 | 修改 |
| PUT | `/v2/dashboard/pricing/rules/{ruleId}` | 更新規則，新增 `product:update` 權限 | 修改 |
| DELETE | `/v2/dashboard/pricing/rules/{ruleId}` | 刪除規則，新增 `product:delete` 權限 | 修改 |
| **GET** | **`/v2/listings/{listingId}/effective-price`** | **查詢商品有效定價（需 checkDate + stayDays 參數）** | **新增** |

### M13 商家工作台（US-003）

| Method | Path | 說明 |
|--------|------|------|
| **GET** | **`/v2/seller/dashboard`** | **查詢商家儀表板統計（需 SELLER 角色）** |

**Dashboard Response 欄位**:
- `orderCount7d`：近 7 天訂單數
- `orderCount30d`：近 30 天訂單數
- `revenue30d`：近 30 天已完成訂單金額加總
- `activeListingCount`：目前上架商品數
- `pendingOrderCount`：待處理訂單數（CREATED/PAID/CONFIRMED）
- `lastOrderAt`：最近一筆訂單時間

---

## 5. 資料庫變更

### V44__Add_Listing_Id_To_Pricing_Rules.sql（US-001）

```sql
ALTER TABLE pricing_rules
    ADD COLUMN listing_id UUID REFERENCES listings(id) ON DELETE CASCADE;
CREATE INDEX idx_pricing_rules_listing_id ON pricing_rules(listing_id);
```

**設計說明**:
- `listing_id`（general product）與 `room_listing_id`（民宿）互斥選填
- 向後相容：現有 Room 定價規則不受影響
- Cascade Delete：商品下架時對應定價規則自動清除

---

## 6. SA 需求分析產出（US-004）

### M10_IM_REQUIREMENTS.md（新建）

| 章節 | 內容摘要 |
|------|---------|
| 技術選型分析 | WebSocket vs MQTT 8 維度比較表，推薦 Spring WebSocket + STOMP |
| 資料模型設計 | `conversations` 表（unique constraint）+ `messages` 表（QoS index） |
| REST API 草稿 | 5 支 HTTP 端點（建立對話、列表、歷史、發送、標記已讀） |
| STOMP API 草稿 | WebSocket 連線端點 + 訂閱頻道 + 個人通知佇列 |
| 技術依賴清單 | `spring-boot-starter-websocket` + 前端 `@stomp/stompjs` |
| 安全設計 | JWT via STOMP connect header，租戶隔離，XSS 防護 |
| 開發里程碑 | Sprint 23~25 交付規劃建議 |
| PM/PO 審核 | ✅ Victoria APPROVED（2026-08-18） |

---

## 7. M11 Provider Stub 強化（US-005）

| Provider | 舊追蹤號格式 | 新追蹤號格式 | 範例 |
|----------|------------|------------|------|
| HCT | `HCT-{UUID前8碼}` | `HCT-{yyyyMMdd}-{UUID前8碼}` | `HCT-20260827-E54D4619` |
| TCAT | `TCAT-{UUID前8碼}` | `TCAT-{yyyyMMdd}-{UUID前8碼}` | `TCAT-20260827-632D88A3` |

---

## 8. Sprint 21 Action Items 追蹤

| Action Item | 內容 | 達成狀態 |
|-------------|------|---------|
| AI-601 | M12 動態定價分段啟動（US-001/002） | ✅ 完成 |
| AI-602 | M10 IM SA 需求分析不得再延後（US-004） | ✅ 完成，Victoria APPROVED |
| AI-603 | M11 物流與訂單整合評估 | 🟡 評估中，Sprint 23 規劃 |

**Action Items 完成率**: 2/3（AI-603 持續追蹤中）

---

## 9. Definition of Done 驗核

- [x] US-001~005 所有 AC 達成
- [x] `mvn compile` → 0 errors（所有 US）
- [x] Checkstyle 0 violations（所有 US）
- [x] 新增整合測試 +9 全部通過（IT-M12-009/010/011 + IT-EP-001/002/003 + IT-DASH-001/002/003）
- [x] 新增單元測試 +3 全部通過（TC-PROV-001/002/003）
- [x] LogisticsProviderFactoryTest 3 個既有測試無退步
- [x] M10_IM_REQUIREMENTS.md PM/PO Victoria APPROVED
- [x] catch(Exception) 生產程式碼維持 **0 處**
- [x] @Deprecated 生產程式碼維持 **0 處**
- [x] Sprint 22 Review 文件建立（本文件）
- [x] Sprint 22 Retrospective 文件建立（SPRINT_22_RETRO.md）

---

**文件版本**: v1.0
**建立日期**: 2026-06-27
**建立者**: Dev David + SA Amanda + Claude Code
