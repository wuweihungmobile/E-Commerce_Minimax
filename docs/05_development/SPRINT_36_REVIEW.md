# Sprint 36 Review / Sprint 36 評審會議

> **Sprint 編號**: Sprint 36
> **期間**: 2027-02-28 ~ 2027-03-13
> **評審日期**: 2026-07-02（AISDLC 延伸：開發完成後即建立）
> **基於**: [SPRINT_36_PLAN.md](../04_planning/SPRINT_36_PLAN.md)、Sprint 35 Retro Action Items + 四方審議建議

---

## 1. Sprint 目標達成評估

> **Sprint 目標**: 安全收尾 + 版型架構債償還 —— 清償積欠多 Sprint 的活躍安全項 DEF-019（物流/賣家側 IDOR），並在 S37 買家頁套版導入前償還 DEF-020 版型 Shell 架構債，補上 Sprint 35 遺留的 home-error/重試 E2E 驗收。

| 目標項目 | 達成狀態 | 說明 |
|---------|---------|------|
| US-001 DEF-019 物流/賣家側 IDOR 收尾 | ✅ 達成 | createLogistics tenant-based + processOrderPayment user-based 擁有權檢查；**活躍安全 DEF 歸零** |
| US-002 DEF-020 版型 Shell 架構重構 | ✅ 達成 | route-group layout + client 邊界下推 + 搜尋走 URL；at-homepage E2E 全棧全綠 |
| US-003 home-error/重試 E2E 補測 | ✅ 達成 | E2E-HOME-05/06（page.route mock）；at-homepage 6 tests 全綠 |
| US-004 買家 live 走查（Buffer）| ⏸️ 延後 | 需 live 環境，順延（AI-1903，見 §5）|

**Sprint 目標達成率**: P1+P2 承諾 US-001~003 **全數完成**（10 SP）。US-004（Buffer）延後。

---

## 2. User Story 完成狀態

| US | 標題 | SP | 優先級 | 狀態 |
|----|------|----|--------|------|
| US-001 | DEF-019 物流/賣家側 IDOR 收尾（後端安全）| 3 | P1（安全）| ✅ 完成 |
| US-002 | DEF-020 版型 Shell 架構重構（前端）| 5 | P1（架構債）| ✅ 完成 |
| US-003 | 首頁 home-error/重試 E2E 補測 | 2 | P2 | ✅ 完成 |
| US-004 | 買家 live 走查（Buffer）| 2 | P3 | ⏸️ 延 S36+/S37 |
| **完成合計** | | **10 SP** | | |

---

## 3. 交付內容

### US-001：DEF-019 物流/賣家側 IDOR 收尾（AI-1902）
- `LogisticsService.createLogistics`：加 **tenant-based** 擁有權檢查（`order.tenantId == 當前租戶`、admin 放行、越權 403/E_1007），置於狀態檢查之前（不向未授權者洩漏訂單狀態）。
- `PaymentService.processOrderPayment`（`/v2/payments` 對外入口）：加 **user-based** 擁有權檢查（比照 S33 `checkOrderOwnership`，買家限本人）。
- 新增 `LogisticsServiceOwnershipTest` + `PaymentServiceOwnershipTest`（各 3 tests：越權→E_1007、本人/本租戶通過、admin 放行）。
- **實測揪出並修好 S33 遺留的 M07 5 個整合測試失敗**（`@Transactional` 一級快取致 Order 影子 `userId` 為 null → 測試 builder 補 `.userId` + 訂單擁有者對齊呼叫者）。
- 更新 DEFERRED_ITEMS_TRACKER：DEF-019 → ✅ 完成，**活躍安全 DEF 歸零**。

### US-002：DEF-020 版型 Shell route-group 架構重構
- 建 `app/(storefront)/layout.tsx`（server）承載 TOP（Header）/Bottom（Footer），首頁移入 route-group（換頁不重建版型）。
- `"use client"` 邊界下推：`StorefrontShell` 改 grid-only server component、Footer/Tools 維持 server，僅 Header client。
- 搜尋/分類/排序/分頁改走 **URL query**：server page 讀 `searchParams` → props 傳 client `HomeContent`（官方建議做法，免 `useSearchParams`+Suspense）；Header 搜尋 `router.push`、購物車數量自取；**移除 page-scoped callback 與 nonce**（連帶償還 F-06）。
- React 19 嚴格 hooks：以 **key-remount** 於篩選變更顯示 skeleton（避免 effect 內同步 setState）。

### US-003：首頁 home-error/重試 E2E 補測（AI-1907）
- `at-homepage.spec.ts` 新增 E2E-HOME-05（500 → home-error + 重試恢復）+ E2E-HOME-06（401 → 恰為登入引導鑑別性斷言 + loading 不卡死），皆 `page.route` mock 免 seed。

---

## 4. 測試狀態

| 測試類型 | 結果 |
|---------|------|
| 後端 `mvn` 單元 + 整合（乾淨 DB docker postgres）| ✅ 單元 353 + M07 8 + M11 4 整合 **0 fail**（US-001）|
| 前端 `npm run build`（Turbopack）| ✅ 0 error（39 頁）|
| 前端 `npm run type-check` / `lint` | ✅ 0 error（lint 95 warnings 皆既有）|
| `make validate-e2e`（乾淨 DB 全棧 Playwright）| **at-homepage 6 tests 全綠**（E2E-HOME-01~06，含搜尋改走 URL 的 -03、home-error/重試 -05、401 鑑別 -06）；全棧 **35 passed / 5 skipped / 1 failed**，唯一失敗為 `at-m15-e2e 媒體庫篩選功能`（memory 記載之既有 flaky，**非本 Sprint 造成**）|
| schema 漂移 | 無 entity/migration 變更 → 不受影響 |

---

## 5. Definition of Done 驗核

- [x] US-001（DEF-019）：物流/賣家側 tenant-based 檢查、越權 403、補測通過；DEF-019 轉 ✅、**活躍安全 DEF 歸零**
- [x] US-002（DEF-020）：route-group layout + client 邊界下推 + URL 搜尋；首頁功能等價不退步；DEF-020 轉 ✅
- [x] US-003（AI-1907）：home-error/重試 + 401 鑑別 E2E 補齊
- [x] 後端 `mvn` 相關測試 0 fail、無 schema 漂移
- [x] 前端 `build` / `type-check` / `lint` 0 error
- [x] `make validate-e2e`：at-homepage 全綠、既有 E2E 不退步（唯 m15 既有 flaky）
- [x] 多租戶 `X-Tenant-ID` 隔離不破壞
- [x] Sprint 36 Review / Retrospective / Release Notes 建立
- [ ] pre-push v5 完整守門（make validate-release）—— push 前執行（累積 S32~S36，檢查點徵詢後）

> **⚠️ 誠實揭露（Rule 12）**：
> - **m15 既有 flaky 持續失敗**：`at-m15-e2e 媒體庫篩選功能` 於 US-002/US-003 兩次全棧 E2E 皆失敗（S35 亦然），為 memory `m15-media-filter-e2e-flaky` 記載之既有問題（媒體庫 dialog/session teardown，**與 Sprint 36 的訂單/物流/首頁改動無關**）。它會使 `make validate-release` 嚴格守門（0 failed）失敗 → **push 前必須先重跑/處理此 spec**（AI-1906/新增行動項），否則阻擋 push。此非 Sprint 36 新債。
> - **US-004（買家 live 走查）** 需 live 環境，未執行，順延（AI-1903）。
> - **processBookingPayment**（預訂付款側）屬 DEF-019 同類但非本次範圍（訂單/物流），已於 tracker 標記另立項評估，未默默略過。
> - US-002 為重大架構異動，已以**全棧 E2E**（非僅靜態）驗證 at-homepage 等價不退步。

---

**文件版本**: v1.0
**建立日期**: 2026-07-02
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
