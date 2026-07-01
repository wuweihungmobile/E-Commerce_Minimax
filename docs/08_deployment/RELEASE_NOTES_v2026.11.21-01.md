# Release Notes - v2026.11.21-01 (Sprint 28)

**發布日期**: 2026-11-21（規劃）／實作完成 2026-07-01
**發布類型**: Minor（品質硬化 + 後台深化 + 安全修復）
**Sprint**: Sprint 28
**狀態**: ✅ **已 push origin/main（本地優先驗證全綠）**

> Sprint 28 主題：品質硬化（補測試防護網）+ 營運後台深化，為 EPIC-BUYER 鋪路

---

## 新功能 ✨

- **商家營運總覽儀表板（US-002/003）**：`/dashboard` 由 create-next-app Welcome 佔位深化為營運總覽 —— 營收統計卡（今日/昨日成長%/本月/本年）、訂單統計卡、訂單狀態總覽、**營收趨勢（近 30 天每日長條 + 淨營收/客單價）**，串接 AnalyticsController。

## 安全（調查，US-004）🔒

- **發現 ERP 手動庫存異動租戶隔離 no-op（→ DEF-017）**：`StockMovementService.createManualMovement` 擁有權檢查為 no-op（`getTenantListings` 回傳 tenantId + 空 `if` body），未把關租戶隔離。修法（注入 `ListingRepository` 實檢）會打破 5 個 M16 整合測試（測試資料未建 listing 列），須連測試資料一併重做 → 本版**未含程式修復**，記 [DEF-017](../04_planning/DEFERRED_ITEMS_TRACKER.md) 待專門處理。

## 品質 🛡️

- **低覆蓋模組測試補強（US-001）**：M14 AnalyticsService 0→7 測試（營收/成長率/AOV/分桶/分組/計數，含邊界）、M18 FaqService 0→6 測試（slug 守門/分類刪除/關鍵字高亮/統計）。`@Test` 670→683。

## 改進 🚀

- 新增 `services/analytics.ts` + `lib/api.ts` analytics 端點（對齊 AnalyticsController `/v2/dashboard/*`）。
- 前端遵守 Next 16/React 19 嚴格 effect 規範。

## 資料庫遷移 🗄️

- 無新 Flyway migration（最新仍為 V56）。

## 重大變更 ⚠️

- 無破壞性 API 變更。

## 已知問題 / 後續

| 項目 | 處置 |
|------|------|
| **DEF-017 ERP 手動庫存異動租戶隔離 no-op（安全）** | Sprint 29+（需連 M16 整合測試資料一併修） |
| DEF-016 Admin audit log 持久化 | Sprint 29+（需 AuditLog entity + migration） |
| M18 Knowledge 測試（現 1） | 後續延展 |
| EPIC-BUYER 買家端前端閉環 | Sprint 29 起（AI-1301） |

## 驗證狀態 ✅

- `@Test` 靜態計數：**683**（+13）
- catch(Exception) 生產 = 0、@Deprecated 生產 = 0
- Checkstyle 0 violations；前端 lint 0 errors / type-check / build 通過
- 活躍 DEF：2（DEF-016 audit / DEF-017 ERP）
- pre-push v5 完整守門（act + schema + e2e）通過後 push

## 內含 Commit（Sprint 28）

| US / 項目 | Commit |
|----------|--------|
| US-001 測試補強（Analytics/FAQ） | `251b29b` |
| US-002/003 後台深化（營收/訂單/趨勢） | `c4097bb` |
| 計畫狀態更新 | `9f0879d` |
| US-004 調查（ERP→DEF-017、audit→DEF-016；ERP 程式後回退） | `b98a05c` + 回退 `ba3780a` |
| checkstyle 修復（AnalyticsServiceTest 未使用 import，完整守門攔下） | `b58c147` |

> **完整守門紀錄**：首輪 `make validate-release` 因 US-001 測試的 `[UnusedImports]`（`java.time.Instant`）checkstyle 違規 BUILD FAILURE（pre-commit 漏網），修復 `b58c147` 後重驗全綠，經 FULL 快取 push（`ec429cb..b58c147`）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**基於**: AISDLC v0.09 Release Management Workflow
