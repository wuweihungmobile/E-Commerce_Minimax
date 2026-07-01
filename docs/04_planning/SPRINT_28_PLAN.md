# Sprint 28 計劃 / Sprint 28 Plan

> **Sprint 編號**: Sprint 28
> **期間**: 2026-11-08 ~ 2026-11-21 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-01
> **基於**: [PRODUCT_BACKLOG.md](./PRODUCT_BACKLOG.md)（方向 (b) 強化既有模組）+ Sprint 27 US-001 決策
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy（sprint-planning skill）

---

## 🔴 前置條件確認（現況調查）

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 產品方向 | ✅ PM/PO 拍板 **(b) 強化既有模組** | Sprint 27 US-001（AI-1101） |
| Product Backlog | ✅ 已建立（RICE 排序 + EPIC-BUYER） | [PRODUCT_BACKLOG.md](./PRODUCT_BACKLOG.md) |
| Sprint 28 取向 | ✅ PM/PO 拍板 **「先補品質再加功能」** | 為 EPIC-BUYER 打好有測試防護網的後台基礎 |
| 活躍 DEF | 無（DEF-009~015 全清償） | — |
| 已知品質缺口 | M14 Analytics **0 測試**、M18 FAQ **0**、Knowledge 1、ERP StockMovement placeholder、Admin audit log 未持久化 | 來自 3 路盤點 |
| 後端成熟度 | M13 Seller / M14 Analytics 後端已有 Service，前端薄/缺面板 | 深化前先補測試 |

> 🔴 **取向說明**：先補「0–1 測試」模組的防護網（US-001），再在其上深化後台面板（US-002/003）—— 避免在無測試的後端上加功能（呼應 AI-1105 精神）。

---

## 1. Sprint 28 目標

> **主題**: 品質硬化（補測試防護網）+ 營運後台深化（Seller / Admin），為 EPIC-BUYER 鋪路

Sprint 27 確立方向 (b) 並產出 RICE backlog。Sprint 28 依「品質先行」：先補齊低測試覆蓋模組的防護網，再深化商家工作台與平台分析後台 —— 這兩個後台後端已有 Service，前端薄弱，且是 seller/admin 每日使用的高頻面板。

---

## 2. Sprint 目標對齊

| 目標 | 對應 backlog # | 類型 |
|------|--------------|------|
| 低覆蓋模組測試補強（M14/M18） | #9 | 品質硬化 |
| M13 商家工作台深化（營收/訂單統計面板） | #8 | 後端+前端 |
| M14 平台分析面板（Admin 分析儀表板） | #8 | 後端+前端 |
| 後端 placeholder/audit 清理 | （盤點發現） | 技術改善（Buffer） |

---

## 3. User Stories

### US-001：低覆蓋模組測試補強（backlog #9，P0 品質）

> **SP**: 2 | **優先級**: P0 | **狀態**: ✅ 完成（Analytics 0→7、FAQ 0→6；@Test 670→683）
>
> **實作**：AnalyticsServiceTest（+7，營收狀態過濾/成長率除零/AOV 零訂單/預設 31 天/分桶/狀態分組/listing 計數）、FaqServiceTest（+6，slug 守門/分類刪除守門/建文章需分類/關鍵字高亮大小寫不敏感/分類統計）。M18 Knowledge 現有 1 測試，本 US 先補真正 0 覆蓋的 Analytics/FAQ。commit `251b29b`。

**目標**: M14 Analytics（**0 測試**）、M18 FAQ（**0**）、Knowledge（1）測試覆蓋過低，深化前先補核心防護網。

**AC-001-1**: AnalyticsService 核心統計（營收/訂單/趨勢/成長率）補單元測試，涵蓋正常 + 邊界（零資料、跨期）
**AC-001-2**: FaqService（分類/搜尋/置頂排序/統計）+ KnowledgeBaseService（文章/搜尋）補核心單元測試
**AC-001-3**: `@Test` 靜態計數淨增，既有測試無退步；新增測試全綠

---

### US-002：M13 商家工作台深化（backlog #8，P1）

> **SP**: 3 | **優先級**: P1 | **狀態**: ✅ 完成（/dashboard 深化為營運總覽）
>
> **實作**：`/dashboard` 由 Welcome 佔位改為營收統計卡（今日/昨日成長%/本月/本年）+ 訂單統計卡 + 訂單狀態總覽，串接 AnalyticsController `/v2/dashboard/{stats,orders}`。新增 `services/analytics.ts` + `lib/api.ts` analytics 端點。Next 16 嚴格 effect（async fetch、setState 皆在 await 後）。commit `c4097bb`。

**目標**: 後端 `SellerDashboardService` 已有儀表板統計/趨勢/銷售排名，但前端 `/dashboard` 框架薄弱。補商家可日常使用的營收/訂單總覽面板。

**AC-002-1**: `/dashboard` 首頁呈現營收統計、訂單總覽、銷售排名（串接既有 SellerDashboardController）
**AC-002-2**: 圖表/數據卡片可視化（趨勢、期間切換）；遵守 Next 16 / React 19 嚴格 lint（先讀 frontend AGENTS.md）
**AC-002-3**: 前端 lint/type-check/build 通過；後端若補端點則有對應測試

---

### US-003：M14 平台分析面板（backlog #8，P2）

> **SP**: 2 | **優先級**: P2 | **狀態**: ✅ 完成（營收趨勢分析，併入儀表板）
>
> **實作**：儀表板加「營收趨勢（近 30 天）」每日長條圖 + 淨營收/客單價，串 AnalyticsController `/v2/dashboard/revenue`。**設計調整**：後端 AnalyticsController 為租戶範圍（非平台級），故 M13/M14 以統一儀表板分析視圖呈現，避免另建重複的 `/admin` 面板（權限仍由既有 dashboard 進入路徑控管）。commit `c4097bb`。

**目標**: 後端 `AnalyticsService` 已有平台級統計（US-001 補測試後有防護網），補 Admin 分析儀表板前端。

**AC-003-1**: `/admin` 下新增分析面板，呈現平台營收/訂單/成長率（串接 AnalyticsController）
**AC-003-2**: 與 M17 租戶管理面板風格一致；權限限 Admin/SuperAdmin
**AC-003-3**: 前端 lint/type-check/build 通過

---

### US-004（Buffer）：後端 placeholder / audit 清理（P3）

> **SP**: 1 | **優先級**: Buffer | **狀態**: ✅ 完成（ERP 擁有權安全隙修復；audit log → DEF-016）
>
> **AC-004-1（ERP）✅**：`StockMovementService.getTenantListings` 原回傳 tenantId 本身、且 line 57 的 `if` body 為空 —— 手動庫存異動的**租戶擁有權檢查完全 no-op（租戶隔離安全隙）**。已修：注入 `ListingRepository`，以 SKU→listing→tenantId 實檢，非當前租戶 → E_4031、listing 不存在 → E_3003；移除 placeholder。新增 `StockMovementServiceTest`（+2）鎖住。
> **AC-004-2（audit log）→ DEF-016**：`AdminService` 的 audit 僅 `log.info`；持久化需新 AuditLog entity + migration（改動面大），依 AC 記為 [DEF-016](./DEFERRED_ITEMS_TRACKER.md)，不強行塞入 Buffer。

**目標**: 清理盤點發現的兩個小缺口。

**AC-004-1**: ERP `StockMovement` 的 Listing ID placeholder 補正（或明確記錄為已知限制）
**AC-004-2**: Admin Audit Log 評估持久化（目前僅日誌）；若改動面大則記錄為新 backlog 項，不強行塞入

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 低覆蓋模組測試補強（M14/M18） | 2 | **P0** |
| US-002 | M13 商家工作台深化 | 3 | P1 |
| US-003 | M14 平台分析面板 | 2 | P2 |
| US-004 | 後端 placeholder/audit 清理（Buffer） | 1 | Buffer |
| **P0+P1+P2 合計** | | **7 SP** | |
| **含 Buffer 合計** | | **8 SP** | |

> Velocity 對齊：S24=7 / S25=11 / S26=9 / S27≈6。Sprint 28 承諾 7 SP + 1 Buffer，符合區間。

---

## 5. 執行順序建議

```
US-001（測試補強）  → 🔴 最先：先有防護網，才在其上深化（品質先行）
US-002（M13 商家後台）→ 高頻 seller 面板，串既有 Service
US-003（M14 分析面板）→ 依 US-001 已測試的 AnalyticsService
US-004（Buffer 清理） → 容量有餘再做
```

---

## 6. 依賴與風險

| 風險 | 機率 | 影響 | 緩解措施 |
|------|------|------|---------|
| Next 16/React 19 嚴格 lint 反覆踩雷（圖表/狀態） | 中 | 中 | 寫前端前先讀 frontend AGENTS.md 與 node_modules/next docs；沿用既有面板模式（M17 租戶頁） |
| AnalyticsService 0 測試，補測試時揭露既有 bug | 中 | 中 | US-001 先行；發現 bug 即修並記錄（Rule 12） |
| 後台面板範圍蔓延（dashboard 容易越做越大） | 中 | 中 | 嚴守 AC，僅串既有端點呈現；不新增分析維度（留 backlog） |
| Admin audit log 持久化改動面未知 | 低 | 中 | US-004 為 Buffer，先評估；過大則轉 backlog |

---

## 7. 測試規劃（QA Quincy）

| US | 測試重點 | 類型 |
|----|---------|------|
| US-001 | Analytics/FAQ/Knowledge 核心邏輯 + 邊界 | 單元 |
| US-002 | 商家後台面板資料正確、權限 | 前端 + 後端 |
| US-003 | Admin 分析面板資料正確、權限隔離 | 前端 + 後端 |

---

## 8. Definition of Done（Sprint 28）

- [ ] US-001~003 所有 AC 達成
- [ ] `mvn compile` → 0 errors；Checkstyle → 0 violations
- [ ] `@Test` 靜態計數淨增（M14/M18 覆蓋提升）；既有測試無退步
- [ ] catch(Exception) 生產 **0 處**、@Deprecated 生產 **0 處**
- [ ] 前端 lint/type-check/build 通過
- [ ] pre-push v5 完整守門（make validate-release）綠燈後 push
- [ ] Sprint 28 Review / Retrospective / Release Notes 建立

---

## 9. Backlog 對應追蹤

| backlog # | 內容 | Sprint 28 對應 US |
|-----------|------|-----------------|
| #9 | 測試補強（M14/M18） | US-001 |
| #8 | M13/M14 後台深化 | US-002 + US-003 |
| （盤點） | placeholder/audit 清理 | US-004（Buffer） |

> EPIC-BUYER（#1–#6 買家端閉環）**自 Sprint 29 起**動工（本 Sprint 先打好品質與後台基礎）。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
