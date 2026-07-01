# Sprint 36 計劃 / Sprint 36 Plan

> **Sprint 編號**: Sprint 36
> **期間**: 2027-02-28 ~ 2027-03-13 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-01
> **基於**: [SPRINT_35_RETRO.md](../05_development/SPRINT_35_RETRO.md)、[DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md)（DEF-019 / DEF-020）、Sprint 35 四方審議（Architect 架構建議）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者選定「**安全 + 架構收尾優先**」（Sprint 36 🔴 確認點）

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者拍板「安全+架構收尾優先」 | AI-1901 買家頁套版順延 S37（依賴 DEF-020 先完成）|
| DEF-019 修法範式已備 | ✅ 比照 DEF-017 / S33 付款側 `checkOrderOwnership` | 賣家/物流側為 **tenant-based**（order.tenantId == 當前租戶），非買家擁有權 |
| 租戶測試 seeding 陷阱已知 | ✅ 參考記憶 `erp-tenant-test-seeding-gotcha` | Listing.tenantId `insertable=false` 影子欄位 + Tenant.id `@GeneratedValue` + listings FK；raw SQL + JDBC UPDATE 範式 |
| DEF-020 架構指引已備 | ✅ Architect 審查給出 3 項具體做法 | route-group layout / client 邊界下推 / URL 搜尋 |
| 累積批次狀態 | ⚠️ S32~35 共 **16 commit** 未 push（含本輪審查修復）| push 前 `make validate-release` 完整守門（AI-1906）|
| 活躍 DEF | DEF-019（安全，**本 Sprint 處理**）+ DEF-020（架構債，本 Sprint）+ DEF-021/022（技術/測試債，登記）| 安全項本 Sprint 收尾，不再順延 |

---

## 1. Sprint 36 目標

> **主題**: 安全收尾 + 版型架構債償還

清償積欠多個 Sprint 的**活躍安全項 DEF-019**（物流/賣家側 IDOR，付款側已於 S33 修，本 Sprint 補完物流/賣家側），並在 S37 買家頁套版**導入前**償還 **DEF-020 版型 Shell 架構債**（改為 App Router route-group layout、client 邊界下推、搜尋走 URL），同時補上 Sprint 35 遺留的 home-error/重試 E2E 驗收（AI-1907）。此 Sprint 不新增使用者可見功能，聚焦**安全正確性**與**架構健康度**，為 S37 全頁套版打好地基。

---

## 2. User Stories

### US-001：DEF-019 物流/賣家側 IDOR 收尾（P1 安全）（後端）

> **SP**: 3 | **優先級**: P1（安全，活躍 DEF）| **狀態**: 📋 Ready
> **承自**: AI-1902（S34→S35→S36 順延之活躍安全項）

**AC-001-1**: `LogisticsService.createLogistics` 加入 **tenant-based 擁有權檢查**——建立物流前驗證 `order.tenantId == 當前租戶`（賣家限本租戶訂單），越權回 403 / `E_1007`（比照 S33 `checkOrderOwnership` 範式，賣家/admin 角色語意）
**AC-001-2**: 盤點並修補 `PaymentService.processOrderPayment`（若有對外入口）之同類無過濾 `findById`；無對外入口則於 PR 誠實記錄「內部呼叫、無 IDOR 暴露面」
**AC-001-3**: 補整合測試——`otherTenantCannotCreateLogistics`（他租戶賣家建立他人訂單物流 → 403）+ 本租戶正常建立通過；seeding 依 `erp-tenant-test-seeding-gotcha` 範式（raw SQL 建租戶/訂單 + JDBC UPDATE 影子欄位），避免 M11 測試資料對齊踩坑
**AC-001-4**: 後端 `mvn compile` + 相關單元/整合測試 0 fail（開發-編譯-測試循環）；`make validate-schema` 無漂移
**AC-001-5**: 更新 DEFERRED_ITEMS_TRACKER：DEF-019 轉 ✅ 完成（付款側 S33 + 物流/賣家側 S36），活躍安全 DEF 歸零

### US-002：DEF-020 版型 Shell 架構重構（P1 架構債）（前端）

> **SP**: 5 | **優先級**: P1（架構債，S37 套版前置）| **狀態**: 📋 Ready
> **承自**: DEF-020（Architect 審查建議）

**AC-002-1**: 建立 App Router route-group `app/(storefront)/layout.tsx` 承載 TOP（StorefrontHeader）+ Bottom（StorefrontFooter），首頁移入該 group；Content 經 `{children}` 注入（換頁不重建版型）
**AC-002-2**: `"use client"` 邊界下推至真正需要的葉節點——`StorefrontShell` / `StorefrontFooter` / `StorefrontTools` 轉為 server component；僅 `StorefrontHeader`（含 ThemeSwitcher/SearchBar）及互動元件保留 client
**AC-002-3**: 搜尋/分類/排序/分頁改走 **URL query**（`router.push` + `useSearchParams`），移除 page-scoped callback（`onSearch` prop 串接）與 `nonce` 補丁；購物車數量以輕量方式共用
**AC-002-4**: 既有 `at-homepage.spec.ts` 4 tests 全綠（版型/主題/搜尋/內容三態不退步）；`npm run build` + `type-check` + `lint` 0 error
**AC-002-5**: 首頁功能等價（分類/排序/搜尋/分頁/未登入引導/錯誤重試）不退步；rs-* 主題與 5 色票切換不破壞

### US-003：首頁 home-error / 重試 E2E 補測（P2）（前端）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready
> **承自**: AI-1907（QA 四方審議提列——T1 重試鈕「功能已上、驗收缺席」）

**AC-003-1**: `at-homepage.spec.ts` 新增：`page.route()` 攔截 `/v2/listings` 回 500 → 斷言 `home-error` 顯示 + 「重新載入」鈕可見 → 攔截改回 200 → 點重試 → 斷言 `home-error` 消失、落入成功/空/引導狀態
**AC-003-2**: 補 401 專屬鑑別斷言（回 401 → 斷言**恰為** `home-auth-empty`，非落入其他態）+ loading 不卡死斷言（操作後 skeleton 最終消失）——緩解四態 `or()` 之 Rule 9 鑑別力缺口
**AC-003-3**: `make validate-e2e` 綠燈；既有 E2E 不退步（含 US-002 重構後之首頁）

### US-004（Buffer）：買家閉環 live 走查（P3）

> **SP**: 2 | **優先級**: Buffer/P3 | **狀態**: 📋 Ready
> **承自**: AI-1903（需 live 環境）

**AC-004-1**: live 環境走查買家閉環（瀏覽→加入購物車→結帳→訂單）；記錄缺陷。時間/環境允許則執行，否則順延並誠實記錄（不遺漏）

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | DEF-019 物流/賣家側 IDOR 收尾（後端安全）| 3 | P1（安全）|
| US-002 | DEF-020 版型 Shell 架構重構（前端）| 5 | P1（架構債）|
| US-003 | 首頁 home-error/重試 E2E 補測 | 2 | P2 |
| **P1+P2 承諾合計** | | **10 SP** | |
| US-004 | 買家 live 走查（Buffer）| 2 | P3 |

> **Velocity 參考**：S30=8, S31=8, S32=5, S33≈2, S34=3, S35=18（純前端異常高值，不作基準）。**本 Sprint 10 SP** 貼近健康 velocity（8~11）。S35 Retro 已示警「S36 涉既有頁面改造，耦合升高，SP 應回歸保守」——本規劃遵此，未貪多。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 DEF-019 物流/賣家側 IDOR（後端安全，最高優先、獨立於前端）
   ↓ mvn compile + test 綠、validate-schema 無漂移
US-002 DEF-020 Shell 架構重構（前端，S37 套版前置）
   ↓ build+type-check+lint 綠、at-homepage 4 tests 不退步
US-003 home-error/重試 E2E 補測（針對 US-002 重構後之首頁撰寫，避免 rework）
   ↓ make validate-e2e 綠
US-004（Buffer，環境允許）買家 live 走查
```

**強制**：
- 後端（US-001）：每支類別/服務修改後**立即** `mvn compile` + 相關測試，失敗立即修，絕不累積。
- 前端（US-002/003）：每個檔案完成後**立即** `npm run build` + `type-check` + `lint`。
- **US-003 必須排在 US-002 之後**——DEF-020 會改動首頁資料流（搜尋走 URL、移除 nonce），E2E 應針對最終結構撰寫。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| **租戶測試 seeding 陷阱**（DEF-019 最大風險）| 嚴格遵循記憶 `erp-tenant-test-seeding-gotcha`：raw SQL 建 Tenant（`@GeneratedValue` 需取回 id）+ 訂單，JDBC UPDATE 影子欄位（`Listing.tenantId insertable=false`）；比照 DEF-017 已驗範式 |
| DEF-019 賣家/物流側語意較付款側複雜（tenant-based 非買家擁有權）| 先讀 `LogisticsService` + Order entity tenantId 關係 + S33 `checkOrderOwnership` 實作，確認角色語意（賣家限本租戶、admin 放行）再改 |
| **DEF-020 重構破壞首頁**（架構異動風險）| route-group 遷移後逐步驗證；`at-homepage` 4 tests 為迴歸網；搜尋改 URL 後手動確認分類/排序/分頁/引導/重試等價；本地 `make validate-e2e` 把關 |
| URL 搜尋改動涉 `useSearchParams`（Next 16 需 Suspense 邊界）| 動手前讀 `frontend/node_modules/next/dist/docs/` 相關指南（AGENTS.md 鐵律）；`useSearchParams` 以 Suspense 包裹避免 build 警告 |
| 本地 act 抓不到 GitHub E2E schema-validation | 改 entity/migration 前手動 `make validate-schema`（記憶 `local-ci-cannot-catch-schema-validation`）；US-001 若動 entity 尤須注意 |
| E2E flaky（`m15-media-filter-e2e-flaky` / `at-m15` 間歇失敗）| 僅該 spec 失敗時重跑；不阻斷核心；push 守門前處理 |
| 16 commit 累積待 push | push 前 `make validate-release` 完整守門（含 at-homepage 重跑），檢查點徵詢後一起 push；嚴禁 `--no-verify`（AI-1906）|

---

## 6. Definition of Done

- [ ] US-001（DEF-019）：物流/賣家側 tenant-based 檢查完成、越權 403、補測通過；DEF-019 轉 ✅、**活躍安全 DEF 歸零**
- [ ] US-002（DEF-020）：route-group layout + client 邊界下推 + URL 搜尋完成；首頁功能等價不退步；DEF-020 轉 ✅
- [ ] US-003（AI-1907）：home-error/重試 + 401 鑑別 + loading 不卡死 E2E 補齊
- [ ] 後端 `mvn` 相關測試 0 fail、`make validate-schema` 無漂移
- [ ] 前端 `npm run build` / `type-check` / `lint` 0 error
- [ ] `make validate-e2e` 綠、既有 E2E（含 at-homepage）不退步
- [ ] 多租戶 `X-Tenant-ID` 隔離不破壞
- [ ] Sprint 36 Review / Retrospective / Release Notes 建立
- [ ] 本地把關綠燈後，累積批次（S32~36）於檢查點徵詢後 push（`make validate-release`，嚴禁 --no-verify）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| DEF-019 安全修復 | `backend/.../LogisticsService`、（若有）`PaymentService`；對應整合測試 |
| Shell 架構重構 | `frontend/src/app/(storefront)/layout.tsx`、`components/layout/`（Shell/Footer/Tools server 化）、`app/page.tsx`（URL 搜尋）|
| E2E 補測 | `frontend/e2e/at-homepage.spec.ts` |
| 追蹤更新 | `DEFERRED_ITEMS_TRACKER.md`（DEF-019/020 轉完成）|
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）|

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
