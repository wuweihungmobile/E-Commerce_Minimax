# Sprint 34 計劃 / Sprint 34 Plan

> **Sprint 編號**: Sprint 34
> **期間**: 2027-01-31 ~ 2027-02-13 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_33_RETRO.md](../05_development/SPRINT_33_RETRO.md)（AI-1701/1702/1703）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| 累積批次狀態 | ⚠️ Sprint 32+33（7 commit）未 push | 本 Sprint 續累積；後端安全變更逐項本地把關 |
| DEF-017（AI-1701） | ✅ 三層根因完整診斷、修法已驗證 | 修法路徑明確：raw SQL 種 FIXED_TENANT_ID 租戶列 |
| DEF-019 物流/賣家側（AI-1702） | ✅ 付款側已修（S33）；剩 createLogistics（租戶語意） | 賣家/admin 角色檢查 |
| 活躍 DEF | 2（DEF-017 / DEF-019 物流賣家側） | 本 Sprint 目標：落地 DEF-017、清 DEF-019 剩餘 |

---

## 1. Sprint 34 目標

> **主題**: 安全修復落地（訂單/ERP/物流全面租戶隔離）

把 Sprint 33 診斷/部分完成的兩個安全項收尾：(1) DEF-017 以 raw SQL 種 FIXED_TENANT_ID 租戶列使已驗證修法落地；(2) DEF-019 物流/賣家側 IDOR。

---

## 2. User Stories

### US-001：DEF-017 ERP 租戶隔離落地（AI-1701，P1 安全）

> **SP**: 3 | **優先級**: P1（安全） | **狀態**: 📋 Ready（修法已驗證，seeding 路徑明確）

**AC-001-1**: M16ErpIntegrationTest @BeforeAll 以 raw SQL 種 id=FIXED_TENANT_ID 的 tenants 列（比照 TestDatabaseInitializer），取代被 @GeneratedValue 忽略的 `.id()` builder
**AC-001-2**: 測試 listing 以 JDBC UPDATE 設 tenant_id（insertable=false 影子欄位）；M16ErpE2ETest 同法（其 tenant 為既有 generated id，已存在於 tenants）
**AC-001-3**: 套用已驗證的生產修法（inject ListingRepository + `!tenantId.equals(listing.getTenantId())` null 安全檢查 + 移除 getTenantListings placeholder）+ 跨租戶測試 IT-M16-307（403）
**AC-001-4**: `M16ErpIntegrationTest` + `M16ErpE2ETest` **全數通過**（含 301~307）；本地 M16 targeted 綠才 commit，紅則 revert 誠實延後

### US-002：DEF-019 物流/賣家側 IDOR（AI-1702，P1 安全）

> **SP**: 3 | **優先級**: P1（安全） | **狀態**: 📋 Ready（賣家側租戶語意）

**AC-002-1**: 盤點 `LogisticsService.createLogistics` 呼叫者角色（賣家建本租戶訂單物流、admin 放行）；設計租戶/擁有權檢查（最小爆炸半徑）
**AC-002-2**: `PaymentService.processOrderPayment`（若有對外入口）一併評估
**AC-002-3**: 補越權測試（跨租戶建物流 → 403）；既有 M11 物流 E2E 不退步
**AC-002-4**: 本地跑物流/訂單相關整合測試；若賣家側語意牽動過廣 → 誠實部分交付（Rule 12）

### US-003（Buffer）：買家閉環 live 走查（AI-1703，P3）

> **SP**: 2 | **優先級**: Buffer/P3

**AC-003-1**: 依 [SPRINT_32_REVIEW §6](../05_development/SPRINT_32_REVIEW.md) checklist 於 live 環境走查（需 live 環境；無則記錄延後）

---

## 3. Story Points 規劃

| US | SP | 優先級 | 風險 |
|----|----|--------|------|
| US-001 DEF-017 落地 | 3 | P1（安全） | 中（seeding 已明確；三度失敗後路徑清楚） |
| US-002 DEF-019 物流/賣家側 | 3 | P1（安全） | 中（賣家側語意） |
| US-003 買家 live 走查（Buffer） | 2 | P3 | 低（需 live 環境） |
| **P1 合計 / 含 Buffer** | **6 / 8** | | |

---

## 4. 執行順序

```
US-001（DEF-017 落地）→ 修法已驗證、seeding 路徑明確，最先收尾
US-002（DEF-019 物流）→ 賣家側租戶語意
US-003（Buffer）      → live 走查，擇機
```

---

## 5. Definition of Done

- [ ] US-001~002（P1 安全）AC 達成或誠實部分交付；US-003（Buffer）完成或延後
- [ ] mvn compile 0 errors；Checkstyle 0 violations；catch(Exception)/@Deprecated 生產 = 0
- [ ] `@Test` 淨增；M16 + 物流/訂單 E2E 無退步
- [ ] 後端安全變更本地把關；schema 若變更跑 make validate-schema
- [ ] pre-push v5 完整守門後 push（累積 S32+S33+S34，檢查點徵詢後）
- [ ] Sprint 34 Review / Retro / Release Notes 建立

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
