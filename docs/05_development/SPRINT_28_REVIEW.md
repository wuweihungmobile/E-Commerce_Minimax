# Sprint 28 Review / Sprint 28 評審會議

> **Sprint 編號**: Sprint 28
> **期間**: 2026-11-08 ~ 2026-11-21
> **評審日期**: 2026-07-01（AISDLC 延伸：開發完成後即建立）
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_28_PLAN.md](../04_planning/SPRINT_28_PLAN.md), [PRODUCT_BACKLOG.md](../04_planning/PRODUCT_BACKLOG.md)

---

## 1. Sprint 目標達成評估

> **Sprint 目標**: 品質硬化（補測試防護網）+ 營運後台深化，為 EPIC-BUYER 鋪路

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| 低覆蓋模組測試補強（US-001, P0） | ✅ 達成 | M14 Analytics 0→7、M18 FAQ 0→6 |
| M13 商家工作台深化（US-002, P1） | ✅ 達成 | `/dashboard` 深化為營運總覽（營收/訂單卡 + 狀態總覽） |
| M14 分析面板（US-003, P2） | ✅ 達成 | 營收趨勢（近 30 天）併入儀表板 |
| 後端 placeholder/audit 清理（US-004, Buffer） | ✅ 達成（調查/文件化） | 揪出 ERP 租戶隔離 no-op → DEF-017；audit log → DEF-016 |

**Sprint 目標達成率**: 100%（承諾 P0+P1+P2 7 SP 全完成 + Buffer 1 SP）

---

## 2. User Story 完成狀態

| US | 標題 | SP | 優先級 | 狀態 | Commit |
|----|------|----|--------|------|--------|
| US-001 | 低覆蓋模組測試補強（M14/M18） | 2 | **P0** | ✅ 完成 | `251b29b` |
| US-002 | M13 商家工作台深化 | 3 | P1 | ✅ 完成 | `c4097bb` |
| US-003 | M14 分析面板（營收趨勢） | 2 | P2 | ✅ 完成 | `c4097bb` |
| US-004 | 後端 placeholder/audit 清理（Buffer） | 1 | Buffer | ✅ 完成（調查→DEF-016/017） | `b98a05c`（後回退 ERP 程式） |
| **完成合計** | | **8 SP** | | ✅ 100% | |

---

## 3. 測試狀態

| 測試類型 | Sprint 27 後 | Sprint 28 後 | 變化 |
|---------|------------|------------|------|
| `@Test` 方法總數（靜態計數） | 670 | **683** | +13（Analytics 7 + FAQ 6） |
| `catch (Exception)` 生產程式碼 | 0 處 | **0 處** ✅ | — |
| `@Deprecated` 生產程式碼 | 0 處 | **0 處** ✅ | — |
| M14 Analytics 測試 | 0 | **7** | 防護網補齊 |
| M18 FAQ 測試 | 0 | **6** | 防護網補齊 |
| 活躍 DEF | 0 | **2**（DEF-016 audit / DEF-017 ERP，皆低-中） | US-004 揪出並記錄 |

---

## 4. US-001：低覆蓋模組測試補強

- **AnalyticsServiceTest（+7）**：營收狀態過濾、成長率（除以零邊界）、AOV（零訂單邊界）、預設日期區間（31 天）、每日分桶、訂單狀態分組、listing 型別/狀態計數。
- **FaqServiceTest（+6）**：slug 唯一守門、含文章分類不可刪、建文章需分類存在、關鍵字高亮（大小寫不敏感 + 預設 `<mark>`）、分類統計聚合。
- M18 KnowledgeBaseService 現有 1 測試，本 US 先補真正 0 覆蓋的 Analytics/FAQ。

---

## 5. US-002 / US-003：後台深化

- `/dashboard` 由 create-next-app Welcome 佔位深化為「營運總覽」：營收統計卡（今日/昨日成長%/本月/本年）、訂單統計卡、訂單狀態總覽、**營收趨勢（近 30 天每日長條 + 淨營收/客單價）**。
- 新增 `services/analytics.ts`（DashboardStats/OrderStats/RevenueStats）+ `lib/api.ts` analytics 端點，串接 AnalyticsController `/v2/dashboard/{stats,orders,revenue}`。
- 遵守 Next 16/React 19 嚴格 effect（async fetch、setState 皆在 await 後、cleanup cancel flag）。
- **設計調整**：AnalyticsController 為租戶範圍（非平台級），故 M13/M14 以統一儀表板分析視圖呈現，避免另建重複的 `/admin` 面板。

---

## 6. US-004：後端 placeholder/audit 調查（Buffer）🔴 揪出安全隙 → DEF-017

| 項目 | 內容 |
|------|------|
| 發現（ERP） | `StockMovementService.createManualMovement` 的擁有權檢查為 no-op：`getTenantListings` 回傳 tenantId 本身、且 `if` body 為空 → 手動庫存異動未把關租戶隔離（安全隙） |
| 嘗試修復 | 曾注入 `ListingRepository` 實檢，但**打破 5 個 M16 整合測試**（M16ErpIntegrationTest ×4 + M16ErpE2ETest ×1）—— 測試資料建 SKU 未建對應 listing 列 → 回 500 |
| 處置 | **誠實回退 ERP 程式變更**（修法須連同 ERP 整合測試資料重做，非 Buffer 可容納）→ 記 **DEF-017**（🟡 安全，中） |
| audit log | `AdminService` audit 僅 `log.info`；持久化需新 entity + migration → 記 **DEF-016** |

> **價值 1**：延續「補測試/深化揪 bug」模式（Sprint 27 DEF-013）—— Buffer 調查揪出租戶隔離安全隙（DEF-017）。
> **價值 2（守門驗證）**：此回歸由 **pre-push v5 完整守門（validate-release）在 push 前攔下**（pre-commit 僅核心單元測試、抓不到整合回歸）—— 完整守門的價值再次實證。
> **教訓**：改動有廣泛呼叫者的 service 前，先於本地跑相關整合測試。

---

## 7. Definition of Done 驗核

- [x] US-001~003 所有 AC 達成（P0+P1+P2）
- [x] `mvn compile` → 0 errors；Checkstyle → 0 violations
- [x] `@Test` 靜態計數淨增（670 → 683）；既有測試無退步
- [x] catch(Exception) 生產 **0 處**、@Deprecated 生產 **0 處**
- [x] 前端 lint（0 errors）/type-check/build 通過
- [x] US-004 Buffer 完成（調查：ERP 安全隙記 DEF-017、audit log 記 DEF-016；ERP 程式已回退）
- [x] Sprint 28 Review / Retrospective / Release Notes 建立
- [x] pre-push v5 完整守門（make validate-release）通過並 push（`ec429cb..b58c147`，FULL 快取命中）
  - 🔴 首輪 validate-release 揪出 US-001 `AnalyticsServiceTest` 未使用 import（checkstyle `[UnusedImports]`）→ 修復 `b58c147` 後重驗全綠（完整守門再次攔下 pre-commit 漏網項）

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
