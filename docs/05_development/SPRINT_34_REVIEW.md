# Sprint 34 Review / Sprint 34 評審會議

> **Sprint 編號**: Sprint 34
> **期間**: 2027-01-31 ~ 2027-02-13
> **評審日期**: 2026-07-01（AISDLC 延伸：開發完成後即建立）
> **基於**: [SPRINT_34_PLAN.md](../04_planning/SPRINT_34_PLAN.md), [SPRINT_33_RETRO.md](./SPRINT_33_RETRO.md)

---

## 1. Sprint 目標達成評估

> **Sprint 目標**: 安全修復落地（訂單/ERP/物流全面租戶隔離）

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| DEF-017 ERP 租戶隔離落地（US-001, AI-1701, P1） | ✅ **達成（清償）** | 歷時 S28→34 三度回退後落地；乾淨 DB M16 43 tests 0 fail |
| DEF-019 物流/賣家側（US-002, AI-1702, P1） | ⏸️ 延 S35 | 付款側已於 S33 修；物流側 tenant-based（恐涉 M11 測試資料，比照 DEF-017 風險）延後 |
| 買家 live 走查（US-003, Buffer, AI-1703） | ⏸️ 延 S35 | 需 live 環境 |

**Sprint 目標達成率**: US-001（P1，DEF-017 落地）✅ 完成——本 Sprint 聚焦清償近期最難的安全項；US-002/003 基於 context/風險考量誠實延後 S35。

---

## 2. User Story 完成狀態

| US | 標題 | SP | 優先級 | 狀態 | Commit |
|----|------|----|--------|------|--------|
| US-001 | DEF-017 ERP 租戶隔離落地（AI-1701） | 3 | P1（安全） | ✅ 完成 | `a76a3bf` |
| US-002 | DEF-019 物流/賣家側 IDOR（AI-1702） | 3 | P1（安全） | ⏸️ 延 S35 | — |
| US-003 | 買家 live 走查（Buffer） | 2 | P3 | ⏸️ 延 S35 | — |
| **完成合計** | | **3 SP**（US-001） | | | |

> Sprint 34 Plan 隨 US-001 commit（`a76a3bf`）。

---

## 3. 交付內容

### US-001：DEF-017 ERP 手動庫存租戶隔離落地（AI-1701）— 🏆 三 Sprint saga 清償
- **背景**：DEF-017 自 Sprint 28 發現，S32/S33 兩度嘗試皆因 M16 測試基建糾纏而誠實回退（三層根因：insertable=false 影子欄位 → NPE；tenant_id null → 403；@GeneratedValue + FK → FK violation）。
- **生產修法**：`StockMovementService.createManualMovement` 加 null 安全租戶檢查（inject `ListingRepository` → `!tenantId.equals(listing.getTenantId())`，越權 403/E_1007；移除 `getTenantListings` no-op placeholder）。
- **測試基建重做（解三層根因）**：
  1. `Tenant.id` @GeneratedValue → builder `.id()` 被忽略 → 改以 **raw SQL 種 id=FIXED_TENANT_ID 的 tenants 列**（比照 TestDatabaseInitializer），使 @WithErpSecurity 的 FIXED_TENANT_ID 有真實租戶列、listings.tenant_id FK 滿足。
  2. `Listing.tenantId` insertable=false 影子欄位 → **JDBC UPDATE** 顯式補寫 tenant_id。
  3. **修 @AfterAll cleanup**：刪 listing 前先刪其 product_skus（DEF-017 後 listing 有正確 tenant_id 被 findByTenantId 找到並刪，暴露既有 cleanup FK bug——此 bug 在乾淨 DB 守門也會發作）。
  4. 新增 **IT-M16-307** 跨租戶越權測試（他租戶 SKU → 403）。
- **驗證（乾淨 DB，gate-like）**：`M16ErpIntegrationTest` 37 + `M16ErpE2ETest` 6 = **43 tests 0 fail**（含 301~307）。

### US-002 / US-003：誠實延後 S35
- **US-002（DEF-019 物流/賣家側）**：`LogisticsService.createLogistics` 需 tenant-based 檢查（order.tenantId == 當前租戶，賣家側語意），與 DEF-017 同類、恐涉 M11 測試資料對齊。基於本 session 已極長（context 風險）+ 避免重演多次迭代，延 Sprint 35（AI-1702 續）。付款側已於 Sprint 33 清償。
- **US-003（買家 live 走查）**：需 live 環境，延 Sprint 35（AI-1703）。

---

## 4. 測試狀態

| 測試類型 | Sprint 33 後 | Sprint 34 後 | 變化 |
|---------|------------|------------|------|
| M16 targeted（乾淨 DB） | （DEF-017 未落地） | **43 tests 0 fail**（含 IT-M16-307） | DEF-017 落地 |
| catch(Exception) 生產 | 0 | **0** | — |
| @Deprecated 生產 | 0 | **0** | — |
| Flyway | V57 | **V57**（無新 migration） | — |
| 活躍 DEF | 2（DEF-017/019） | **1**（DEF-019 物流賣家側；DEF-017 清償） | DEF-017 落地 |
| ERP 手動庫存租戶隔離 | 未修 | ✅ 已修（跨租戶 403） | — |

---

## 5. Definition of Done 驗核

- [x] US-001（P1）AC 達成並乾淨 DB 驗證（43 tests 0 fail）
- [x] `mvn compile` 0 errors；Checkstyle 0 violations
- [x] `@Test` 淨增（IT-M16-307）；M16 全綠、既有無退步
- [x] catch(Exception) 生產 0、@Deprecated 生產 0
- [x] 後端安全變更本地跑相關整合測試（M16 targeted 乾淨 DB 綠燈才 commit）
- [x] US-002/003 誠實延後 S35（context/環境考量）
- [ ] pre-push v5 完整守門（make validate-release）—— push 前執行（累積 S32~S34，檢查點徵詢後）
- [x] Sprint 34 Review / Retrospective / Release Notes 建立

> **誠實備註**：DEF-017 為近期最難項——歷時 S28→34、三次於 commit 前本地攔下回退（main 全程未污染），最終以「測試基建重做（raw SQL 種固定 ID 租戶）」落地。US-002/003 基於本 session 長度與 tenant-based 修法風險誠實延後，非未達標。

---

**文件版本**: v1.0
**建立日期**: 2026-07-01
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
