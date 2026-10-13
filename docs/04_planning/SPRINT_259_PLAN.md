# Sprint 259 計畫：補齊 DEF-358(a)（`/products`、`/rooms` 跨店鋪商品/房源列表）

## 1. 起點與範圍

承接 Sprint 256/257/258：DEF-358 使用者已核准 4 項全做，依「由小到大、依耦合度排列」排入 Sprint 256 起依序處理：

1. ✅ Sprint 256：(c) `/blog/category/[id]` 貼文分類篩選頁
2. ✅ Sprint 257：(b) `/auth/profile` 個人資料編輯表單
3. ✅ Sprint 258：(d) `/admin` 管理台總覽首頁
4. **Sprint 259（本輪，最後一項）**：(a) `/products`、`/rooms` 跨店鋪商品/房源列表

PRD 規格：§10.1.1 `/products`、`/rooms`（M01/M02）——「目前無跨店鋪商品列表頁，商品瀏覽僅能透過各店鋪 `/stores/{slug}/products`／`/rooms`（Sprint 252）瀏覽，無跨店鋪聚合列表」。原始登記時這項被標註「屬資訊架構決策」，範圍與耦合度皆大於前三項。

## 2. 讀碼確認範圍

**關鍵查證（決定本輪工作量）**：全庫目前沒有任何「跨租戶＋訪客可用」的商品/房源列表端點。`GET /v2/listings` 查詢層確實跨租戶，但 Controller 要求 `product:read`/`room:read` 權限（匿名 401）；`GET /v2/products`／`GET /v2/rooms` 連查詢層都不是跨租戶（用 `TenantContext.getCurrentTenant()` 過濾，匿名時是系統佔位租戶）。首頁 `HomeContent.tsx` 原本就在呼叫 `GET /v2/listings`，因此匿名訪客造訪首頁只會看到「登入後即可瀏覽精選商品」——這與 PRD §12.7「Guest 可瀏覽所有商品與房源，僅加入購物車/下單才需登入」矛盾。

**使用者決策（AskUserQuestion）**：調查後發現這個根因還牽連 2 個不在 DEF-358(a) 原始登記範圍的既有缺口——首頁登入牆、`/listings/[id]` 詳情頁登入牆——使用者選擇「全部修」：新增跨店鋪公開列表 API＋`/products`／`/rooms`頁面＋首頁改接新 API＋詳情頁對 Guest 開放。

**設計決策**：
- 新增 `PublicListingController`（`/v2/public/listings`），掛在既有 `/v2/public/**` permitAll 規則下（與 `StorefrontController` 同慣例），**不動** `/v2/listings` 本身的授權（維持服務既有登入流程，兩者職責分開，比照 Sprint 252 `StorefrontController` 當時的既有決策）。
- 跨租戶查詢必須同時排除「租戶已停權/終止」的項目（比照既有 `StorefrontController.findActiveTenantBySlug` 原則：停權店鋪的內容不應出現在任何公開瀏覽管道）。此過濾必須在 DB 查詢層完成，不能查完一頁再篩掉，否則分頁筆數會失真——新增 4 個 `ListingRepository` 查詢方法（對應既有 4 個 keyword×type 組合，皆加 `l.tenant.status = 'ACTIVE'` JOIN 條件）。
- `ListingResponse` DTO 新增 `tenantName`/`tenantSlug`（新 overload `fromEntity(Listing, tenantName, tenantSlug)`，舊呼叫點不受影響），批次查詢本頁所有租戶（`tenantRepository.findAllById`）避免 N+1。
- 新端點的 `sortBy`/`sortDir` 使用既有 `PageableUtils.sortOf` 搭配允許清單（`createdAt`/`basePrice`），避免複製 `/v2/listings`／`/v2/products`／`/v2/rooms` 三者仍帶著的 DEF-344 缺陷（亂填回 500）——該缺陷本身不在本輪修復範圍，但新寫的端點不該重蹈覆轍。
- `GET /v2/listings/{id}`（詳情）移除 `@PreAuthorize`，`SecurityConfig` 新增 `permitAll`（限定 `HttpMethod.GET` + `/v2/listings/*`，避免誤放行清單或未來寫入端點，比照既有 `/v2/cms/pages/*` 那一行的教訓）。改用 `findByIdWithTenant`（既有 JOIN FETCH 查詢）一次取得 tenant，過濾條件統一為「本身 ACTIVE 且租戶 ACTIVE」，否則一律回與「不存在」相同的 `E_3000`（避免列舉側通道）。此過濾**不分辨呼叫者是否登入**（同時適用匿名與既有認證流程）——查證全庫唯一前端呼叫點（`ListingDetail.tsx`，一般買家購物流程）本就只會查詢 ACTIVE 項目，不影響任何既有合法使用情境，且附帶修復了「任何登入買家都能用 UUID 猜測看到他人 DRAFT 項目」的既有小缺口。
- 前端：首頁 `HomeContent.tsx` 改呼叫新端點 `getPublicListings`，移除 `needsAuth` 分支與對應測試（`home-auth-empty`、E2E-HOME-06）——這個狀態不再可能出現，保留會是斷言錯誤行為。`/listings/[id]` 的 `ListingDetail.tsx` 完全不需修改：五態邏輯本就正確處理所有狀態碼，403/401 不再出現純粹是後端行為改善的自然結果。
- `/products`、`/rooms` 新頁面抽出共用元件 `ListingTypeBrowser`（非與 `HomeContent` 直接共用——首頁有分類切換側欄，新頁面類型由路由固定、不需側欄，兩者有真實行為差異，共用會混淆語意；但新頁面之間彼此 100%結構相同，抽出共用元件避免重複）。
- **刻意不做**：全站導覽（Header）未新增「商品」/「房源」主選單連結——首頁既有的側欄分類切換（`全部商品`/`嚴選商品`/`旅宿房型`）已提供等效的瀏覽路徑，`/products`／`/rooms` 主要滿足 PRD 明訂的獨立路由（可直接連結/收藏/未來 SEO），不等同「使用者原本完全無法瀏覽」的入口缺口（不同於 Sprint 256-258 的情況），故不比照前三輪額外加導覽連結。

## 3. 實作

**後端**：
- `ListingRepository`：新增 `findPubliclyVisible`／`findPubliclyVisibleByType`／`searchPubliclyVisibleByKeyword`／`searchPubliclyVisibleByTypeAndKeyword`（皆含 `tenant.status = 'ACTIVE'` 條件）。
- `ListingResponse`：新增 `tenantName`/`tenantSlug` 欄位＋新 overload。
- 新增 `PublicListingController`（`GET /v2/public/listings`，`page`/`size`/`type`/`keyword`/`sortBy`/`sortDir`），批次查詢租戶名稱/slug。
- `ListingController.getListing`：移除 `@PreAuthorize`，改用 `findByIdWithTenant`＋雙重 ACTIVE 過濾。
- `SecurityConfig`：新增 `permitAll(GET, /v2/listings/*)`。

**前端**：
- `lib/api.ts` 新增 `publicListings.list`；`services/listing.ts` 新增 `getPublicListings`，`Listing` 介面新增選填 `tenantName`/`tenantSlug`。
- 新增 `components/storefront/ListingTypeBrowser.tsx`（共用於 `/products`、`/rooms`）。
- 新增 `app/(storefront)/products/page.tsx`、`app/(storefront)/rooms/page.tsx`。
- `HomeContent.tsx`：資料來源改為 `getPublicListings`，移除 `needsAuth` 狀態與對應 UI 分支、移除不再使用的 `Link` import。

## 4. 測試

**後端**：
- `ListingControllerE2ETest`：既有 `IT-DEF093-01` 的測試固件補上 `ACTIVE` 租戶（因 `getListing` 改用 `findByIdWithTenant`，mock 需同步更新，否則會意外變成驗證「租戶不存在」情境）；新增 3 案例（`@WithAnonymousUser`：匿名存取 ACTIVE 項目成功、非 ACTIVE 項目 404、ACTIVE 項目但租戶停權 404）。
- 新增 `PublicListingControllerE2ETest`（比照既有 `StorefrontControllerE2ETest` 慣例，真實 PostgreSQL，全程不帶 `Authorization` 標頭）6 案例：跨租戶聚合、排除非 ACTIVE、排除停權租戶的 ACTIVE 項目、`type` 篩選並帶出 tenantName/tenantSlug、`sortBy` 非法值 400（驗證新端點不重蹈 DEF-344）、`type` 非法值 422。

**前端**：`npm run type-check`／`npx eslint`／`npm run build` 皆通過，`/products`、`/rooms` 確實出現在建置輸出路由清單（67 條，+2）。

**前端 E2E**：
- 新增 `frontend/e2e/at-public-listings-real.spec.ts`（真實後端，不 mock，開兩家店驗證跨店鋪聚合）5 案例：`/products` 看到兩店商品、`/rooms` 只見房源且卡片連結正確、從 `/products` 點擊可直接看到商品詳情（不被導去登入頁）、首頁不再顯示登入引導、直接造訪 `/listings/{id}` 同樣可見。
- 修改既有 `frontend/e2e/at-homepage.spec.ts`：移除已不存在狀態 `home-auth-empty` 的 E2E-HOME-06（該測試斷言的行為已被本輪移除，繼續保留會斷言錯誤行為）；E2E-HOME-05 的 `page.route` mock pattern 從 `**/v2/listings*` 改為 `**/v2/public/listings*`（既有 pattern 不會匹配新端點路徑，mock 會失效、改成打真實後端）。

## 5. 全量回歸結果

本機 `mvn -o clean verify`（真實 PostgreSQL/Redis）：BUILD SUCCESS，單元 **2221**／整合 **848**（+11）／**0 失敗**；checkstyle（main+test）0 違規。

**`make validate-release` 第一次跑揭露真實連動（非 flaky，非本輪程式碼問題，但是本輪變更的直接後果）**：act／schema-gate 皆通過，Playwright E2E 層 `at-listing-detail.spec.ts` 2 案例（E2E-DETAIL-01/02）失敗——該測試用 `page.route('**/v2/listings**', ...)` mock 首頁的清單請求，但首頁已改呼叫 `GET /v2/public/listings`，glob pattern 不再匹配新路徑（`v2/listings` 不是 `v2/public/listings` 的連續子字串），mock 失效、改打真實後端，回應內容與測試寫死的假資料（「頁1標記」等）不符。修正：mock pattern 改為 `**/v2/public/listings**`（清單）＋保留 `**/v2/listings/{id}` 不變（詳情端點路徑未變，只是後端行為變寬鬆）。同時比照 `at-homepage.spec.ts` 移除已不存在狀態的既有慣例，移除 `at-listing-detail.spec.ts` 裡沒有的對應項（確認 E2E-DETAIL-03 不需移除——它用 mock 直接驗證 `ListingDetail.tsx` 保留的 401 防禦分支，該元件本輪未修改，仍是有效的防禦性測試，與 `at-homepage.spec.ts` 的 E2E-HOME-06 情況不同，後者斷言的 UI 狀態已被整個移除）。

重跑第二次：**170 passed（S258 基準 166，+5 新增 `at-public-listings-real.spec.ts`，-1 移除 `at-homepage.spec.ts` 的 E2E-HOME-06）／4 skipped／0 failed**（7.9 分），act BUILD SUCCESS，schema 對齊無漂移。已寫入 FULL 驗證記錄。

**DEF-358 全數完成**：(c)(b)(d)(a) 四項皆已實作、測試、推送並雲端 CI 驗證（本項待下方 push 完成後更新）。
