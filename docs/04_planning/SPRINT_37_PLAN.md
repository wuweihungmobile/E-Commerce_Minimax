# Sprint 37 計劃 / Sprint 37 Plan

> **Sprint 編號**: Sprint 37
> **期間**: 2027-03-14 ~ 2027-03-27 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-02
> **基於**: [SPRINT_36_RETRO.md](../05_development/SPRINT_36_RETRO.md)（AI-1901 / AI-1903 / AI-2001 / AI-1906）、[DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md)（活躍 DEF：DEF-021 / DEF-022 皆 P3）、S37 探勘報告（買家頁結構 + m15 flaky 定位）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者選定「**進 Sprint 37 Planning**」（S36 收尾檢查點，選項 2）——DEF-020 route-group 前置已就緒，本 Sprint 全頁套版

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者選「進 Sprint 37 Planning」 | 買家頁全頁套版（AI-1901）為 S37 旗艦 |
| DEF-020 前置已就緒 | ✅ S36 完成 route-group `(storefront)/layout.tsx` + grid-only `StorefrontShell` | 買家頁沿用同一 layout 範式，不需重造版型元件 |
| 買家頁結構已探勘 | ✅ `(auth)` route-group **無 layout.tsx**，7 個購物/帳戶頁各自手包 nav/footer | cart/checkout/orders/orders/[id]/bookings/notifications/reviews；login/register 手包但語意不同（需另議）|
| 共用元件已備 | ✅ StorefrontShell(grid-only server)/Header(client)/Footer(server)/Tools + storefront/* | API 層（listing/order/booking/notification）完備，**無需後端變動** |
| m15 flaky 已定位 | ✅ `at-m15-e2e.spec.ts:170`「媒體庫篩選功能」**無斷言（僅 console.log）**、用 `waitForTimeout(500)` | 失敗非斷言而是 teardown/session 崩潰（符記憶 `m15-media-filter-e2e-flaky`）；需先定位根因再修 |
| 累積批次狀態 | ⚠️ main 領先 origin/main **21 commit**（S32~S36 含審查修復），**未 push** | push 前 `make validate-release` 完整守門（AI-1906）；m15 為守門唯一阻礙 |
| 活躍 DEF | DEF-021（CJK 字體）/ DEF-022（E2E 硬等待）皆 P3 非安全 | 本 Sprint 不動，續登記 |
| 誠實缺口 | ⚠️ **商品詳情頁尚未實現**（首頁只連評價頁 `/reviews/product/{id}`）| 非本 Sprint 套版範圍，另立項評估 |

---

## 1. Sprint 37 目標

> **主題**: 買家頁全頁套用共用賣場版型 + 清償 push 債

沿用 S36 DEF-020 已鋪好的 App Router route-group + `StorefrontShell` 基建,將**買家購物/帳戶頁**（cart/checkout/orders/bookings/notifications/reviews）由「各頁手包 nav/footer」統一收斂到共用版型——建立 `(auth)/layout.tsx` 承載 Header/Footer、各頁改以 `StorefrontShell` 包裝 content,達成全站版型一致、換頁不重建 chrome。同時處理積壓多 Sprint 的 **m15 flaky**（`make validate-release` 唯一阻礙）並在完整守門綠燈後,於檢查點徵詢一次 push 積累的 S32~S37 批次,清償 push 債。此 Sprint 以**前端版型收斂**為主、**無後端/DB 變動**。

---

## 2. User Stories

### US-001：(auth) 買家頁全頁套用共用賣場版型（P1 旗艦）（前端）

> **SP**: 5 | **優先級**: P1 | **狀態**: 📋 Ready
> **承自**: AI-1901（S35→S36 順延,DEF-020 前置已就緒）

**AC-001-1**: 建立 `app/(auth)/layout.tsx`（server component,鏡像 `(storefront)/layout.tsx`）承載共用 `StorefrontHeader` + `StorefrontFooter`,買家購物/帳戶頁換頁不重建版型
**AC-001-2**: 重構下列頁面——移除各頁**自包的 nav/footer**,content 改以 `<StorefrontShell>`（無 sidebar → 單欄 main）包裝,保留各頁原有業務內容與 testid：
  - `/cart`、`/checkout`、`/orders`、`/orders/[id]`、`/bookings`、`/notifications`、`/reviews/product/[listingId]`
**AC-001-3**: `login` / `register` 依使用者決策（2026-07-02）**一併套用共用 StorefrontHeader/Footer**（全站完全一致）——納入 `(auth)/layout.tsx` 涵蓋範圍;需確保 `StorefrontHeader` 的 cartCount 抓取在**未登入**時不崩潰（優雅 fallback 0 / catch 401,不阻斷登入頁渲染）
**AC-001-4**: 既有 `at-buyer-pages.spec.ts`（E2E-BUYER-01/02/03：orders/notifications/bookings 空狀態）**全綠不退步**;`npm run build`（Turbopack）+ `type-check` + `lint` 0 error
**AC-001-5**: 買家頁功能等價不退步（購物車增減/結帳/訂單列表詳情/預訂/通知篩選/評價瀏覽）;rs-* 主題與 5 色票切換於買家頁一致生效;每頁改完**立即** build+type-check+lint,絕不累積

### US-002：m15 flaky 修復 + S32~S37 累積批次 push（P1 release 解鎖）

> **SP**: 3 | **優先級**: P1（阻擋 release）| **狀態**: 📋 Ready
> **承自**: AI-2001（m15 flaky）+ AI-1906（檢查點 push）

**AC-002-1**: 先**定位** `at-m15-e2e.spec.ts:170`「媒體庫篩選功能」flaky 根因——本地全棧重現 / 觀察 teardown 崩潰（該 test 無斷言,失敗屬 dialog/session teardown,非邏輯）;依 CI 修復鐵律**先看實際錯誤再修**,不盲改
**AC-002-2**: 依根因實修（如:補明確等待條件取代 `waitForTimeout`、修 dialog/context teardown、或補最小斷言使其成為真驗證）;**不可長期靠重跑掩蓋**（S36 Retro 明列）
**AC-002-3**: `make validate-e2e` 全棧 Playwright **0 failed**（含 m15 穩定通過 + 既有 at-homepage/at-buyer-pages 不退步,含 US-001 套版後之買家頁）
**AC-002-4**: 🔴 **檢查點徵詢後**,以 `make validate-release` 完整守門綠燈,一次 push S32~S37 累積批次;**嚴禁 `--no-verify`**;push 後更新 RELEASE_TRACKER「待 push」歸零

### US-003：買家頁套版後版型一致性 E2E 補測（P2）（前端）

> **SP**: 2 | **優先級**: P2 | **狀態**: 📋 Ready
> **承自**: US-001 驗收缺口補齊（QA 提列,避免「功能已上、版型驗收缺席」）

**AC-003-1**: `at-buyer-pages.spec.ts`（或新 spec）新增斷言:買家頁（至少 orders/cart/notifications 各一）套版後**共用 Header + Footer 存在**（以 storefront Header/Footer 之 testid 或穩定 selector 斷言）,確認版型一致收斂
**AC-003-2**: 補「換頁不重建版型」之基本驗證（跨頁導覽後 Header/Footer 仍在,不重複渲染兩套 nav）
**AC-003-3**: 無需後端 seed（沿用 at-buyer-pages 空資料策略,穩健不 flaky）;`make validate-e2e` 綠、既有 E2E 不退步

### US-004（Buffer）：買家閉環 live 走查（P3）

> **SP**: 2 | **優先級**: Buffer/P3 | **狀態**: 📋 Ready
> **承自**: AI-1903（需 live 環境）

**AC-004-1**: live 環境走查買家閉環（瀏覽→加入購物車→結帳→訂單→通知）,含 US-001 套版後之版型;記錄缺陷。時間/環境允許則執行,否則順延並誠實記錄（不遺漏）

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | (auth) 買家頁全頁套用共用賣場版型 | 5 | P1（旗艦）|
| US-002 | m15 flaky 修復 + S32~S37 批次 push | 3 | P1（release 解鎖）|
| US-003 | 買家頁套版後版型一致性 E2E 補測 | 2 | P2 |
| **P1+P2 承諾合計** | | **10 SP** | |
| US-004 | 買家閉環 live 走查（Buffer）| 2 | P3 |

> **Velocity 參考**：S30=8, S31=8, S32=5, S33≈2, S34=3, S35=18（純前端異常高值,不作基準）, S36=10。**本 Sprint 10 SP** 貼近健康 velocity（8~11）,延續 S36 保守回歸。US-001 雖涉 7 頁重構,但為機械性版型收斂（元件已就位、無 API 變動）,風險集中於逐頁 content 容器/間距對齊。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 (auth) 買家頁套版（前端旗艦,獨立於後端）
   ↓ 每頁改完立即 build+type-check+lint;at-buyer-pages 不退步
US-003 版型一致性 E2E 補測（針對 US-001 套版後結構撰寫,避免 rework）
   ↓ make validate-e2e 綠
US-002 m15 flaky 定位+修復 → 全 S37 work committed → make validate-release 綠
   ↓ 🔴 檢查點徵詢
US-002（後半）push S32~S37 累積批次（完整守門,嚴禁 --no-verify）
   ↓
US-004（Buffer,環境允許）買家 live 走查
```

**強制**：
- 前端（US-001/003）：每個檔案完成後**立即** `npm run build` + `type-check` + `lint`,失敗立即修,絕不累積 7 頁一次編譯。
- **US-003 必須排在 US-001 之後**——版型一致性 E2E 應針對套版後最終結構撰寫。
- **US-002 的 push 排在最後**——確保批次含完整 S37 成果;m15 修復為 push 守門前置,故緊鄰 push;push 需 🔴 檢查點徵詢 + `make validate-release` 完整綠燈。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| **7 頁逐頁套版易漏 content 容器/間距對齊**（US-001 最大風險）| 逐頁改逐頁 build/lint/手動視覺確認,絕不累積;先做 1 頁確立範式（如 orders）再套其餘;保留各頁原 testid 供 E2E 迴歸 |
| **login/register 版型語意衝突**（未登入不宜掛購物 Header）| 🔴 確認點先決策（預設不套完整 Header,維持精簡 auth 版型）;避免為登入頁硬掛搜尋/購物車 |
| **前端鐵律**（Next 16 非慣常）| 動手前讀 `frontend/node_modules/next/dist/docs/` 相關 layout/route-group 指南（AGENTS.md 鐵律）;沿用 `(storefront)/layout.tsx` 已驗範式 |
| **m15 flaky 根因未明**（US-002）| 先本地全棧重現 + 看實際 teardown 錯誤（CI 修復鐵律,先看 log 再修）;該 test 無斷言,疑為 dialog/session teardown 崩潰,勿盲改邏輯 |
| **21 commit 累積 push 風險大** | `make validate-release` 完整守門（含 at-homepage/at-buyer-pages 重跑）;🔴 檢查點徵詢後一次 push;嚴禁 `--no-verify`（歷史教訓 20+ 無效 commit）|
| 本地 act 抓不到 GitHub schema-validation | 本 Sprint **無 entity/migration 變動**,schema 風險低;仍於 push 守門 `make validate-schema` 把關（記憶 `local-ci-cannot-catch-schema-validation`）|
| 商品詳情頁未實現 | 非本 Sprint 範圍,誠實記錄於 Review;首頁連結維持現況（連評價頁）|

---

## 6. Definition of Done

- [ ] US-001（AI-1901）：`(auth)/layout.tsx` 建立、7 個購物/帳戶頁改用共用 Header/Footer + StorefrontShell、login/register 版型決策落地;買家頁功能等價不退步
- [ ] US-002（AI-2001 + AI-1906）：m15 flaky 根因定位並實修、`make validate-e2e` 0 failed;🔴 檢查點徵詢後完整守門 push S32~S37,RELEASE_TRACKER 待 push 歸零
- [ ] US-003：買家頁套版後版型一致性 E2E 補齊、`make validate-e2e` 綠
- [ ] 前端 `npm run build` / `type-check` / `lint` 0 error
- [ ] 既有 E2E（at-homepage / at-buyer-pages / m15）不退步
- [ ] 多租戶 `X-Tenant-ID` 隔離不破壞（買家頁資料抓取不變）
- [ ] rs-* 主題 5 色票於買家頁一致生效
- [ ] Sprint 37 Review / Retrospective / Release Notes 建立
- [ ] （US-002 完成後）DEFERRED_ITEMS_TRACKER 更新;push 後 RELEASE_TRACKER 反映已發布

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| 買家頁共用 layout | `frontend/src/app/(auth)/layout.tsx`（新建）|
| 買家頁套版重構 | `frontend/src/app/(auth)/{cart,checkout,orders,orders/[id],bookings,notifications,reviews/product/[listingId]}/page.tsx` |
| 版型一致性 E2E | `frontend/e2e/at-buyer-pages.spec.ts`（或新 spec）|
| m15 flaky 修復 | `frontend/e2e/at-m15-e2e.spec.ts` |
| 追蹤更新 | `DEFERRED_ITEMS_TRACKER.md`、`RELEASE_TRACKER.md`（push 後）|
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）|

---

## 8. 🔴 使用者確認點（2026-07-02 已確認）

1. ✅ **login / register 版型策略**（AC-001-3）：使用者選定「**全站完全一致**」——login/register **一併套用**共用 StorefrontHeader/Footer,納入 `(auth)/layout.tsx`。
2. ⏳ **US-002 push 檢查點**（AC-002-4）：m15 修復 + 完整守門綠燈後,push S32~S37 累積批次前**再次徵詢**（屆時跑完整 `make validate-release`）。
3. ✅ **範圍 / SP / 順序**：使用者「核准,立即開始 US-001」——US-001（5）+ US-002（3）+ US-003（2）= 10 SP,US-004 Buffer;執行順序 US-001 → US-003 → US-002(修復→push) → US-004。

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
