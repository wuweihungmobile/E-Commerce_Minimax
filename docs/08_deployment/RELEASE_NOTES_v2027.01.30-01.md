# Release Notes - v2027.01.30-01 (Sprint 33)

**發布日期**: 2027-01-30（規劃）／實作完成 2026-07-01
**發布類型**: Minor（安全修復收尾）
**Sprint**: Sprint 33
**狀態**: ⏳ 待 push（累積 S32+S33，完整守門 + 檢查點徵詢後 push）

> Sprint 33 主題：安全修復收尾（訂單付款 IDOR 清償 + ERP 租戶隔離根因診斷）

---

## 安全修復 🔒

- **DEF-019 訂單付款 IDOR（US-002，AI-1702，P1）**：`PaymentStateService` 的 `getOrderPaymentState`（讀）+ `mockPaymentSuccess/Failure/mockRefund`（寫）原無擁有權過濾 → 任何登入者可查詢/付款/退款他人訂單（DEF-018 付款側姊妹）。新增 `checkOrderOwnership` helper（買家限本人、admin 放行，越權 **403/E_1007**），套用四個訂單付款方法。

## 品質 🛡️

- **越權 E2E**：`BuyerOrderJourneyE2ETest.otherBuyerCannotAccessOrderPayment`（買家 D 讀/付 A 訂單 → 403）。
- 後端 `@Test` 690→**691**；US-002 本地 **21 tests 0 fail**（OrderController 12 + BuyerJourney 5 + OrderPayment 4）。

## 安全（調查發現 / 部分交付）🔍

- **DEF-017 ERP 租戶隔離（US-001，AI-1701，→ Sprint 34）**：本 Sprint 三層根因完整診斷（更正 Sprint 32 誤判）——(1) `Listing.tenantId` insertable=false 影子欄位、測試未設 `.tenant` 關聯 → tenant_id null → NPE；(2) null 安全後 403；(3) JDBC 補寫 → FK violation（`Tenant.id` @GeneratedValue，@WithErpSecurity 硬編 FIXED_TENANT_ID 無 tenants 列）。**生產修法已驗證正確**，缺 M16 tenant seeding 整套重做（raw SQL 種 FIXED_TENANT_ID 租戶）→ 延 Sprint 34。三度 commit 前本地攔下、誠實回退（main 未污染）。
- **DEF-019 剩餘（部分交付）**：`LogisticsService.createLogistics`（賣家側租戶語意）+ `PaymentService.processOrderPayment` 留 Sprint 34 續修。

## 已知問題 / 後續

| 項目 | 處置 |
|------|------|
| **DEF-017 ERP 租戶隔離** | Sprint 34（AI-1701）：raw SQL 種 FIXED_TENANT_ID 租戶列使已驗證修法落地 |
| **DEF-019 物流/賣家側 IDOR** | Sprint 34（AI-1702 續，P1 安全） |
| **買家閉環 live 走查** | Sprint 34（AI-1703，需 live 環境） |

## 資料庫遷移 🗄️

- 無新 migration（Flyway 維持 V57）。

## 重大變更 ⚠️

- **行為變更（安全）**：`GET /v2/orders/{id}/payment`、`POST /v2/orders/{id}/pay`、`/pay/fail`、`/refund` 現強制訂單擁有權檢查——非擁有者且非 admin → **403**（原任何登入者可操作）。買家操作自己訂單無影響。

## 驗證狀態 ✅

- 後端 `@Test` 靜態計數：**691**（+1）
- catch(Exception) 生產 = 0、@Deprecated 生產 = 0；Checkstyle 0 violations
- US-002 付款/訂單 E2E **21 tests 0 fail**（本地）
- 活躍 DEF：2（DEF-017 ERP / DEF-019 物流賣家側；DEF-018 + DEF-019 付款側已清償）
- pre-push v5 完整守門（act + schema + e2e）—— push 前執行

## 內含 Commit（Sprint 33）

| US / 項目 | Commit |
|----------|--------|
| Sprint 33 Plan + US-001 DEF-017 三層根因診斷 | `5497347` |
| US-002 DEF-019 訂單付款 IDOR 修復（AI-1702） | `32b5590` |
| Sprint 33 收尾（Review/Retro/Release Notes + tracker） | （本次） |

> US-001（DEF-017）為調查+誠實延後，無程式碼 commit（修法於本地 commit 前攔下回退，main 未污染）。

## 貢獻者

- @wuweihungmobile（PM/PO）
- AISDLC Agents：PM Victoria / SA Amanda / SD Marcus / Dev David / QA Quincy + Claude Code

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**基於**: AISDLC v0.09 Release Management Workflow
