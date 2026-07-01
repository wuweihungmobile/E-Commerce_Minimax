# Release Notes - v2027.02.13-01 (Sprint 34)

**發布日期**: 2027-02-13（規劃）／實作完成 2026-07-01
**發布類型**: Minor（安全修復落地）
**Sprint**: Sprint 34
**狀態**: ⏳ 待 push（累積 S32+S33+S34 共 8 commit，完整守門 + 檢查點徵詢後 push）

> Sprint 34 主題：安全修復落地 —— DEF-017 ERP 手動庫存租戶隔離清償（歷時 S28→34）

---

## 安全修復 🔒

- **DEF-017 ERP 手動庫存租戶隔離落地（US-001，AI-1701，P1）**：`StockMovementService.createManualMovement` 原擁有權檢查為 no-op → 任何租戶使用者可異動任意 SKU 庫存。修法加 null 安全租戶檢查（inject `ListingRepository` → `!tenantId.equals(listing.getTenantId())`，越權回 **403/E_1007**；移除 `getTenantListings` no-op placeholder）。
  - **歷時 Sprint 28→34**：S32/S33 兩度嘗試因 M16 測試基建三層根因（insertable=false 影子欄位 / @GeneratedValue / FK）而誠實回退；S34 以測試基建重做落地。

## 品質 🛡️

- **M16 測試基建重做**：raw SQL 種 id=FIXED_TENANT_ID 租戶列（比照 TestDatabaseInitializer）+ JDBC UPDATE listing tenant_id + 修 @AfterAll cleanup（先刪 product_skus 再刪 listing）+ 新增 `IT-M16-307` 跨租戶越權測試（他租戶 SKU → 403）。
- **驗證（乾淨 DB）**：`M16ErpIntegrationTest` 37 + `M16ErpE2ETest` 6 = **43 tests 0 fail**（含 301~307）。

## 已知問題 / 後續

| 項目 | 處置 |
|------|------|
| **DEF-019 物流/賣家側 IDOR** | Sprint 35（AI-1801）：createLogistics tenant-based 檢查（先盤點 M11 seeding）。付款側已於 S33 清償 |
| **買家閉環 live 走查** | Sprint 35（AI-1802，需 live 環境） |

## 資料庫遷移 🗄️

- 無新 migration（Flyway 維持 V57）。

## 重大變更 ⚠️

- **行為變更（安全）**：`POST /v2/dashboard/stock-movements` 現強制租戶擁有權檢查——SKU 所屬 listing 非當前租戶 → **403**（原任何租戶可異動）。賣家異動本租戶 SKU 無影響。

## 驗證狀態 ✅

- 乾淨 DB M16 targeted **43 tests 0 fail**（含 IT-M16-307 跨租戶）
- catch(Exception) 生產 = 0、@Deprecated 生產 = 0；Checkstyle 0 violations
- 活躍 DEF：**1**（DEF-019 物流賣家側；DEF-017 清償、DEF-018 + DEF-019 付款側已清償）
- pre-push v5 完整守門（act + schema + e2e）—— push 前執行

## 內含 Commit（Sprint 34）

| US / 項目 | Commit |
|----------|--------|
| Sprint 34 Plan + US-001 DEF-017 ERP 租戶隔離落地（AI-1701） | `a76a3bf` |
| Sprint 34 收尾（Review/Retro/Release Notes + tracker） | （本次） |

> US-002（DEF-019 物流）+ US-003（買家 live）延 Sprint 35。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**基於**: AISDLC v0.09 Release Management Workflow
