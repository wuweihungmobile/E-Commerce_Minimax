# Sprint 38 計劃 / Sprint 38 Plan

> **Sprint 編號**: Sprint 38
> **期間**: 2027-03-28 ~ 2027-04-10 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-02
> **基於**: [SPRINT_37_RETRO.md](../05_development/SPRINT_37_RETRO.md)（AI-2103 / AI-1905 / AI-1903 / AI-1906）、[DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md)、S38 探勘報告（商品詳情頁 / 加購 API / seed 機制）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy
> **主軸決策**: 使用者選定「**買家體驗補完**」

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 主軸已確認 | ✅ 使用者選「買家體驗補完」 | 旗艦：商品詳情頁（套版後最大功能缺口）|
| 詳情頁現況已探勘 | ✅ **目前無商品詳情頁**——ProductCard/首頁連到評價頁 `/reviews/product/[id]` | 需新建 `/listings/[id]`，並改連結導向 |
| 後端 API 已備 | ✅ `GET /v2/listings/{id}`（需 product:read/room:read）、`GET /v2/listings/{id}/price`（ROOM 計價）、`POST /v2/cart/items`（加購）| 前端需補 `getListingById` + Listing interface 欄位（owner 等）|
| 版型基建已就緒 | ✅ S37 route-group + StorefrontShell + auth-aware Header | 詳情頁沿用；置於 `(storefront)` group（公開路由，401→登入引導，比照首頁）|
| ROOM/PRODUCT 差異已釐清 | ✅ PRODUCT→加購；ROOM→日期選擇 + 計價 + 加購（帶日期）| 詳情頁條件渲染 |
| 有資料 E2E 策略 | ✅ 用 **page.route mock**（比照 at-homepage E2E-HOME-05/06），**免後端 seed** | 避開 seed FK/租戶陷阱風險（記憶 `erp-tenant-test-seeding-gotcha`）|
| 累積批次狀態 | ⚠️ main 領先 origin/main **28** commit（S32~S37），**未 push**（使用者選擇本 Sprint 後再議）| push 為檢查點項目（AI-1906），非本 Sprint 自動執行 |
| 活躍 DEF | DEF-021（CJK 字體）/ DEF-022（E2E 硬等待，併入 AI-2101 後續）皆 P3 | 本 Sprint 不動 |

---

## 1. Sprint 38 目標

> **主題**: 買家體驗補完 —— 商品詳情頁

補上買家閉環最大功能缺口:**商品詳情頁**（`/listings/[id]`）。買家自首頁商品卡點入即可查看完整商品資訊並加入購物車（PRODUCT）或選日期計價加購（ROOM）,不再只能看評價。並以 **page.route mock**（免後端 seed）補「首頁有資料網格 + 分頁翻頁 + 點卡進詳情 + 加購」的 E2E 自動化（AI-1905 積欠已久）。純前端 + 前端 service 補強,**無後端/DB 變動**。

---

## 2. User Stories

### US-001：買家商品詳情頁（P1 旗艦）（前端）

> **SP**: 5 | **優先級**: P1 | **狀態**: 📋 Ready
> **承自**: AI-2103（S37 揭露之功能缺口）

**AC-001-1**: 前端 `services/listing.ts` 補 `getListingById(id)` → `GET /v2/listings/{id}`,並補 `Listing` interface 缺漏欄位（description/owner/status 等,對齊後端 DTO）
**AC-001-2**: 新建 `app/(storefront)/listings/[id]/page.tsx`（server 讀 params → client content，比照首頁範式）,套共用 route-group layout（Header/Footer）+ `StorefrontShell`（單欄）
**AC-001-3**: 詳情展示——封面圖 + 標題 + 描述 + 價格（basePrice/currency）+ 標籤 + 賣家/店鋪；三態處理:loading skeleton / 404 找不到商品 / 401→登入引導（比照首頁 home-auth-empty）
**AC-001-4**: **PRODUCT**:數量選擇 + 「加入購物車」→ `POST /v2/cart/items` { listingId, quantity } → 成功 toast + Header 購物車數更新;**ROOM**:入住/退房日期選擇 + 計價（`GET /v2/listings/{id}/price?checkIn&checkOut`）顯示 + 「加入購物車」（帶 startDate/endDate）。以 `listingType` 條件渲染
**AC-001-5**: 首頁/ProductCard 連結由 `/reviews/product/[id]` **改導向** `/listings/[id]`（詳情頁內另提供「查看評價」連結至評價頁,不遺失評價入口）
**AC-001-6**: 前端 `npm run build` + `type-check` + `lint` 0 error;無後端/DB 變動;每檔完成立即編譯,絕不累積

### US-002：商品詳情 + 首頁「有資料」E2E（P2）（前端）

> **SP**: 3 | **優先級**: P2 | **狀態**: 📋 Ready
> **承自**: AI-1905（積欠之「有資料網格 + 分頁」自動化）

**AC-002-1**: 以 **page.route mock**（比照 at-homepage E2E-HOME-05/06,免後端 seed）攔截 `/v2/listings*` 回多筆商品:斷言首頁 `product-grid` 渲染 N 張卡 + 分頁控制項出現;mock page 2 → 點下一頁 → 斷言換頁（URL query + 內容更新）
**AC-002-2**: 點商品卡 → 導向 `/listings/[id]`;mock `/v2/listings/{id}` → 斷言詳情頁渲染（標題/價格/加購鈕）;PRODUCT 點加購 → mock `POST /v2/cart/items` → 斷言成功回饋
**AC-002-3**: 詳情頁三態鑑別:mock 404 → 找不到商品;mock 401 → 登入引導（非空/錯誤態,比照 E2E-HOME-06 鑑別性）
**AC-002-4**: `make validate-e2e` 綠、既有 E2E（at-homepage/at-buyer-pages 等）不退步

### US-003（Buffer）：買家閉環 live 走查（P3）

> **SP**: 2 | **優先級**: Buffer/P3 | **狀態**: 📋 Ready
> **承自**: AI-1903（需 live 環境）

**AC-003-1**: live 環境走查買家閉環（瀏覽首頁→商品詳情→加購物車→結帳→訂單→通知）,含 S37 套版 + S38 詳情頁;記錄缺陷。時間/環境允許則執行,否則順延並誠實記錄

---

## 3. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|--------|
| US-001 | 買家商品詳情頁（PRODUCT 加購 + ROOM 計價加購）| 5 | P1（旗艦）|
| US-002 | 商品詳情 + 首頁「有資料」E2E（mock-based）| 3 | P2 |
| **P1+P2 承諾合計** | | **8 SP** | |
| US-003 | 買家 live 走查（Buffer）| 2 | P3 |

> **Velocity 參考**：S33≈2, S34=3, S35=18（純前端異常高）, S36=10, S37=10。**本 Sprint 8 SP**（新功能頁 + E2E）位於健康區間下緣,保守估——詳情頁 PRODUCT/ROOM 條件渲染 + 計價/加購整合有整合成本,不貪多。

---

## 4. 執行順序（依相依性 + 開發-編譯-測試循環）

```
US-001 商品詳情頁（service getListingById → 詳情頁 → 加購/計價 → 改連結）
   ↓ build+type-check+lint 綠
US-002 有資料 E2E（針對 US-001 詳情頁 + 首頁網格撰寫,mock-based）
   ↓ make validate-e2e 綠
US-003（Buffer,環境允許）買家 live 走查
```

**強制**：
- 前端每檔完成後**立即** `npm run build` + `type-check` + `lint`,失敗立即修。
- **US-002 排在 US-001 之後**——E2E 針對詳情頁最終結構撰寫。
- **前端鐵律**:動手前讀 `frontend/node_modules/next/dist/docs/` 相關（dynamic-routes / params）。

---

## 5. 風險與緩解

| 風險 | 緩解 |
|------|------|
| **詳情頁 ROOM 計價/日期流程複雜**（US-001 最大風險）| ROOM 收斂為「日期選擇 + 計價顯示 + 加購（帶日期）」,復用 cart（POST /v2/cart/items）,不另開 booking 建立流程;PRODUCT 先做、ROOM 後做,條件渲染 |
| 前端 Listing interface 與後端 DTO 不一致 | 動手前對照後端 `Listing.java` / ListingController DTO 補欄位;`getListingById` 回傳型別對齊 |
| **加共用元件到關鍵頁的 E2E helper 碰撞**（S37 慘痛教訓）| 詳情頁若含表單/submit（如加購鈕、日期表單）,注意勿與 E2E `button[type="submit"]` helper 碰撞;沿用 S37 `:not(:has-text("搜尋"))` 慣例,加購鈕用專屬 testid |
| 詳情頁授權（product:read/room:read）| 比照首頁:未登入/401 → 登入引導,別預期匿名可載入 |
| E2E mock 與真實 API 契約漂移 | mock 回傳結構嚴格對齊 `ApiResponse<Listing>` / `Page<Listing>`;必要時對照 service 型別 |
| 累積 28 commit 待 push | push 為檢查點項目（AI-1906）,非本 Sprint 自動執行;需完整 `make validate-release` + 徵詢,嚴禁 --no-verify |

---

## 6. Definition of Done

- [ ] US-001（AI-2103）：`/listings/[id]` 詳情頁完成（PRODUCT 加購 + ROOM 計價加購 + 三態）、`getListingById` 補齊、首頁/卡片連結改導向詳情頁
- [ ] US-002（AI-1905）：mock-based 有資料網格 + 分頁 + 詳情導覽 + 加購 E2E 補齊
- [ ] 前端 `npm run build` / `type-check` / `lint` 0 error
- [ ] `make validate-e2e` 綠、既有 E2E（at-homepage/at-buyer-pages/m10/m11/m15/m17）不退步
- [ ] rs-* 主題於詳情頁一致生效;評價入口不遺失
- [ ] 多租戶隔離不破壞（詳情頁資料抓取比照現有）
- [ ] Sprint 38 Review / Retrospective / Release Notes 建立
- [ ] （檢查點）S32~S38 累積批次於徵詢後完整守門 push（AI-1906）

---

## 7. 產出物

| 產出物 | 路徑 |
|--------|------|
| 商品詳情頁 | `frontend/src/app/(storefront)/listings/[id]/page.tsx`（+ client content 元件）|
| service 補強 | `frontend/src/services/listing.ts`（getListingById + Listing interface）|
| 連結改導向 | `frontend/src/components/storefront/HomeContent.tsx`（ProductCard href → /listings/[id]）|
| 有資料 E2E | `frontend/e2e/at-homepage.spec.ts` 或新 `at-listing-detail.spec.ts` |
| Sprint 收尾 | Review / Retro（`docs/05_development/`）、Release Notes（`docs/08_deployment/`）|

---

## 8. 🔴 待使用者確認點

1. **詳情頁路由與群組**：`app/(storefront)/listings/[id]`（公開路由 + 401 登入引導,比照首頁）。是否同意?或偏好置於 `(auth)`（強制登入才可看詳情）?
2. **AI-1905 用 mock 而非後端 seed**：以 page.route mock 達成「有資料網格 + 分頁」E2E（免 seed 風險）。是否同意?（真實 seed 另立項,見 Retro）
3. **範圍 / SP / 順序**：US-001（5）+ US-002（3）= 8 SP,US-003 Buffer;順序 US-001 → US-002 → US-003。是否核准或調整?

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
