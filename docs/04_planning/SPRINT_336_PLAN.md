# Sprint 336 計畫書：文章網址帶店鋪（匿名可讀＋實體標題）、媒體素材公開開關、開發容器的 `API_INTERNAL_URL`（DEF-482／483）

> **日期**：2026-11-10
> **性質**：後端＋前端＋資料庫遷移（`V98`）＋開發用 `docker-compose.override.yml` 的一個環境變數（使用者明確指示）
> **依據**：使用者貼回 Sprint 335 總結並對三題作答——`DEF-483`、`DEF-482`：『請給我優缺點，互動選項決定！』；`docker-compose`：『請加 `API_INTERNAL_URL=http://ecommerce-backend-dev:8080/api`』；並再次要求『請繼續完成任務！』
> **前置**：[SPRINT_335_PLAN.md](SPRINT_335_PLAN.md) §3.2、§9（`DEF-482`／`DEF-483` 登記與實測）、§10（我對 `DEF-475` 的解讀）、[SPRINT_334_PLAN.md](SPRINT_334_PLAN.md) §3.3、§7.2（媒體權限對齊與 `DEF-482` 登記）

## 1. 背景與授權

| 項目 | 使用者的指示 | 我的處理 |
|---|---|---|
| `DEF-483` 文章頁標題 | 『請給我優缺點，互動選項決定！』→ 我用 `AskUserQuestion` 給三個選項與優缺點，使用者**本人選了『文章網址帶店鋪』**（真實的互動輸入，不是我的推論） | 做：文章網址改成 `/stores/{店鋪}/blog/{文章}`（§4.2、§4.3） |
| `DEF-482` 媒體檔案公開網址 | 同上 → 四個選項，使用者**本人選了『素材逐一開關』**（每個素材可切公開／私有，預設私有） | 做：`is_public` 欄位＋匿名端點＋媒體中心開關與『複製公開網址』（§4.4、§4.5） |
| `docker-compose` 的 `API_INTERNAL_URL` | 『請加 `API_INTERNAL_URL=http://ecommerce-backend-dev:8080/api`』（Docker 政策要求的**明確指示**） | 做：只加在 `docker-compose.override.yml` 的 `frontend`（容器名 `ecommerce-backend-dev` 只存在於開發用的 override）；**正式的 `docker-compose.yml` 沒有動**（§4.1） |
| 我對 `www.nextkey.com` 的解讀（Sprint 335 §10 第 1 點） | 沒有回應 | 維持原解讀（正式站網域，用於 `metadataBase`） |

## 2. 方法

沿用 Sprint 316～335：先查證再動手；先紅後綠並確認紅的**原因**；守門本身用突變驗證（**預期紅燈集合在跑之前登記**，寫在驅動腳本裡）；相鄰缺陷只登記不順手修；驗證過的狀態＝提交的狀態。另加兩條這輪才有的：**寫選項說明前先把『被改的東西』的所有呼叫端盤點完**（吸取 Sprint 308 的教訓：當時選項說明寫『3 處』，實際漏了價格預覽端點，使用者是依不完整的說明選的），以及**這輪第一次補做目視檢查**（§6.1）。

## 3. 查證（動手前）

### 3.1 基準

Sprint 335 收尾（雲端 CI 全綠）：整合 1039／0、前端 Vitest 26 檔 277 案例、本地 E2E 守門 369／369；`flyway` 最新遷移 `V97`。

### 3.2 `DEF-483`：標題只是症狀，**文章頁匿名訪客根本打不開**（讀碼確認）

| # | 發現 | 證據（讀碼；除另註外未用瀏覽器實測） |
|---|---|---|
| 1 | 文章頁 `/blog/[slug]` 靠瀏覽器 localStorage 的 `tenantId` 取文章；沒有 `tenantId` 就顯示『請先登入並設定店鋪以瀏覽部落格』 | 舊 `page.tsx`：`useSyncExternalStore(subscribeAuth, getSelectedTenantIdSnapshot, …)`；`if (!tenantId) return …EmptyState…` |
| 2 | localStorage 的 `tenantId` **只有兩個寫入處**：登入（`services/auth.ts:139`）與店主切換店鋪（`components/tenant/TenantList.tsx:32`）——匿名訪客永遠沒有 | 搜尋所有 `setItem('tenantId'`（單雙引號都搜）；`axios.ts` 只讀不寫 |
| 3 | **公開的**店鋪貼文列表 `/stores/{店}/blog`（匿名可看）每則貼文都連到 `/blog/{slug}`——也就是匿名訪客從公開列表點進文章，看到的是登入提示 | `StoreBlogList.tsx:76` |
| 4 | 既有 E2E 的註解旁證 | `at-m15-e2e.spec.ts`：『文章詳情頁的 tenantId 取自 localStorage；沒登入過的匿名頁面永遠停在載入中』 |
| 5 | `GET /v2/posts/{slug}` **每呼叫一次瀏覽數 +1**（一個 GET 帶寫入）——伺服器端為了標題再呼叫一次會讓瀏覽數多算一倍 | `PostService.getPublishedPostBySlug`、`PostRepository.incrementViewCount` |
| 6 | 公開的貼文查詢**只看貼文狀態、不看店鋪狀態**（停權店鋪的已發布貼文仍可由 API 讀到） | `PostRepository` 的公開查詢只有 `p.status = 'PUBLISHED'`（登記 `DEF-485`） |
| 7 | `posts.slug` 全平台唯一，之後沒有遷移動過 | `V10`：`slug VARCHAR(355) NOT NULL UNIQUE`；`V40` 只加分類外鍵；`PostRepository.findBySlug` 已存在 |
| 8 | `PostResponse` 有 `tenantId`／`tenantName`，**沒有店鋪的網址代稱** | `M15Dto.PostResponse` |
| 9 | 文章網址的產生處共 **3 個**：`/blog` 清單、`/blog/category/[id]` 分類頁、`StoreBlogList`；E2E 有 6 個規格共 22 行含 `/blog/` | 見下方更正 |

> **選項說明裡的一個數字比實際多 1**：我在選項（b）的優缺點寫『6 個 E2E 規格（共 22 行含 `/blog/`）要改』。那是 `grep '/blog/'` 的命中數；實際需要改的是 **5 個**——`at-page-titles.spec.ts` 那一行只是 `/blog/category/x` 的標題路由，不受文章網址改版影響。數字偏大不影響使用者的選擇方向，但範圍說明本來就該準確（見 §2 提到的 Sprint 308 教訓），在這裡更正。
>
> **我第一次盤點漏了一處**：我先用 `grep '/blog/' … | grep -v "category/"` 找連結產生處，那條過濾把分類頁的 `href={`/blog/${post.slug}`}`（該行含 `category/`）一起濾掉，只數到 2 處。**寫選項說明之前**用不排除任何行的 `grep '/blog/\${'` 重數才抓到第 3 處。選項說明裡寫的『3 個連結產生處』是重數之後的數字。

### 3.3 `DEF-482`：媒體庫是一座孤島（讀碼確認；匿名 401 為 Sprint 335 實測）

| # | 發現 | 證據 |
|---|---|---|
| 1 | 檔案端點 `GET /v2/media/files/{id}` 需要登入且有 `media:read`；`SecurityConfig` 沒有任何 media 規則 | `MediaController.getFile`、`SecurityConfig` |
| 2 | **全專案只有媒體中心頁面在用媒體庫**，縮圖用帶憑證的 `AuthenticatedImage`（抓 blob）；`<img src>` 帶不出憑證 | `grep services/media`：只有 `dashboard/media/page.tsx` 與 `cms.ts` 的一行註解；`AuthenticatedImage` 只有媒體中心用 |
| 3 | 貼文的『精選圖片 URL』是**純文字欄位**，沒有從媒體庫挑選的功能；媒體中心也**沒有『複製網址』** | `posts/new`、`posts/[id]/edit` 的 `<Input type="text">`；媒體中心頁面沒有任何 copy／clipboard／url 顯示 |
| 4 | 所以店主上傳的圖，**沒有任何路徑能變成公開頁的圖**；而且貼文封面圖載入失敗時 `onError` 會把破圖藏起來，沒有人會發現 | 貼文編輯頁與文章頁的 `<img onError>` |
| 5 | 檔案讀取以呼叫者的店鋪為邊界（DEF-101 的跨租戶防護）：`downloadAsset` 用 `getCurrentTenant()`＋`findActiveByIdAndTenantId`＋`belongsToTenant` | `MediaService.downloadAsset` |
| 6 | 匿名請求以**來源 IP** 共用每分鐘 100 次的限流桶；公開圖片經過後端串流，一頁 12 張封面圖吃 12 次額度（未實測，登記 `DEF-488`） | `RateLimitFilter` 的類別說明 |
| 7 | 儲存層是 MinIO（預設不啟動）；**本機 E2E 守門沒有 MinIO**，瀏覽器層級驗不到實際讀圖 | `at-media-upload-precheck.spec.ts` 的說明；`validate-e2e.sh` 只起 PG＋Redis |

### 3.4 兩個題目的選項（使用者看到的）

兩題都用 `AskUserQuestion`，每個選項附優缺點與涵蓋的呼叫端（上面的盤點結果）。`DEF-483`：(a) 新增 slug 公開端點（我建議）、(b) 文章網址帶店鋪、(c) 本輪先不做。`DEF-482`：公開圖片類素材（我建議）、只公開封面圖引用的、**素材逐一開關**、不開放。**使用者兩題都選了工最大的選項（(b)、逐一開關），沒有選我建議的**——我照使用者的選擇做，沒有退回建議。

## 4. 實作

### 4.1 開發容器的 `API_INTERNAL_URL`

`docker-compose.override.yml` 的 `frontend.environment` 加一行 `API_INTERNAL_URL=http://ecommerce-backend-dev:8080/api`（附說明註解）。為什麼加在 override：`ecommerce-backend-dev` 是 override 設的 `container_name`，正式的 `docker-compose.yml` 裡後端叫 `ecommerce-backend`／服務名 `backend`，使用者指示的值只在開發堆疊成立。Docker 政策（`CLAUDE.md`）：這屬於『明確指示下更新環境變數值（非 image tag）』，沒有新增 image 或 volume，所以 `DOCKER_POLICY.md` 不需更新。`.env.example` 補了一段說明。

驗證：`docker compose config` 通過，合併後 `frontend` 帶 `API_INTERNAL_URL`、兩個容器在同一個網路 `ecommerce-network`。**容器內的實際連線驗證見 §6.3**：frontend 容器帶著該環境變數、用容器名連得到後端、文章頁標題由伺服器端生成；拿掉環境變數對照則退回靜態標題。

### 4.2 `DEF-483` 後端

- `M15Dto.PostResponse` 新增 `tenantSlug`（列表頁靠它組連結；同一個實體，沒有額外查詢）。
- `GET /v2/posts/{slug}` 新增選填 `countView`（預設 `true`＝維持原行為）；`false` 時整個方法沒有任何寫入、回應的 `viewCount` 是現況不加一。`PostService.getPublishedPostBySlug(slug, tenantId)` 保留（委派 `countView=true`），所以既有 5 個呼叫端（`PostServiceTest`、`ViewCountConcurrencyIntegrationTest`、控制器）不必動。

### 4.3 `DEF-483` 前端

- **新路由** `/stores/[slug]/blog/[postSlug]`：伺服器端 `page.tsx`（`generateMetadata`＋`StoreArticleView`）。`StoreArticleView` 套用 `StoreLayout`（載入公開店鋪資料；404＝店鋪不存在或非 ACTIVE，整頁 404），把店鋪的 `tenantId` 交給 `StoreArticle`。
- **`StoreArticle`**（由舊的 `/blog/[slug]/page.tsx` 用 `git mv` 搬來，內嵌商品卡的程式與 DEF-473 的測試原封不動）：吃 props，不再讀 localStorage；標題改 `h2`（店鋪外殼已有 `h1`＝店名）；404 用白話『此文章不存在或已下架』（原本會顯示英文的 `Request failed with status code 404`，匿名訪客現在看得到這頁，所以順手處理）；返回連結回到 `/stores/{店鋪}/blog`。**這個元件取文章不帶 `countView:false`**——它才是真正的瀏覽。
- **`lib/seo.ts` `postMetadata`**：伺服器端先查店鋪（得到 `tenantId`，順便擋掉非 ACTIVE 店鋪）再查文章；**查文章帶 `countView=false`**；標題『文章標題｜店名』；description 取摘要，沒有摘要就取內文（拿掉 `{{embed:listing:…}}` 語法）；Open Graph 為文章型，帶發布時間、絕對網址的封面圖。任何一步取不到就退回靜態標題。後端給的 `tenantId` 不像 UUID 不採用（它會被放進查詢字串）。
- **`lib/article-path.ts`**：文章網址的形狀只寫一處；三個連結產生處（`/blog`、`/blog/category/[id]`、`StoreBlogList`）都改用它。店鋪代稱缺失時退回舊網址（前端比後端先部署的那幾分鐘，不會變成 `/stores/undefined/…`）。
- **舊網址 `/blog/[slug]` 保留為導向頁**（我替使用者做的決定，§10 第 1 點）：登入且選定店鋪的人（改版前唯一打得開它的人）→ 找出文章所屬店鋪、`router.replace` 到新網址，**這次查詢帶 `countView:false`**（真正的瀏覽是新網址那一頁）；匿名訪客維持原本的登入提示；回應沒有店鋪代稱時明講而不是卡在『載入中』。
- 標題守門 `page-titles.test.ts` 的動態標題頁清單加入新頁面。

### 4.4 `DEF-482` 後端

- **`V98`**：`media_assets.is_public BOOLEAN NOT NULL DEFAULT FALSE`；既有素材全部維持私有。`MediaAsset` 實體新增 `isPublic`（預設 `false`）。
- **`PUT /v2/media/{id}`** 的 `UpdateMediaRequest` 新增選填 `isPublic`（**沒帶＝不變**）；`MediaAssetDto` 新增 `isPublic`（一律有值）與 `publicUrl`（只有公開的素材才有，`/api/v2/public/media/{id}`）。切換權限是既有的 `media:update`。
- **新增匿名端點 `GET /v2/public/media/{id}`**（`PublicMediaController`＋`PublicMediaService`）：**獨立成新類別而不是放進 `MediaService`**——`MediaService` 的每個方法都以呼叫者的店鋪為邊界，這裡是整個媒體模組唯一有意不看呼叫者的讀取路徑，分開才一眼看得出『哪些程式碼不受租戶限制』，也避免有人順手把這裡的寬鬆條件搬去需要登入的路徑。條件：`isPublic`＝true、未刪除、所屬店鋪 `ACTIVE`（JPQL `findPublicActiveById`，**不帶 tenantId 條件**）；另外儲存路徑要屬於素材自己的店鋪（縱深防護，同 DEF-101）。其餘一律同一個 404 `E-4103`（私有、已刪除、店鋪非 ACTIVE、路徑不符、不存在——不區分，避免被拿來探測素材是否存在）。回 `Cache-Control: public, max-age=300`、`Content-Disposition: inline`、`X-Content-Type-Options: nosniff`（Spring Security 預設）。
- `ApiRouteDocDriftTest.GUARDED` 登記 `PublicMediaController`；`API_M15_CMS.md` 新增 §2.5、§4.49 與 `isPublic`／`countView`／`tenantSlug` 說明。

### 4.5 `DEF-482` 前端

- 媒體中心每個素材卡新增『公開』開關（`ui/Switch`，`aria-label` 帶檔名）與狀態文字『公開／私有』；**開與關都先出確認視窗**（公開＝網址外流就收不回；取消公開＝使用中的頁面會顯示不出圖片，最多 5 分鐘的快取）；公開的素材多一個『複製公開網址』。
- 複製的是**完整網址**（`lib/media.ts` 的 `absolutePublicMediaUrl`：後端 origin＋後端給的路徑；origin 取自 `NEXT_PUBLIC_API_URL`，不是絕對網址就用頁面 origin），因為要貼到貼文的『精選圖片 URL』欄位。剪貼簿不可用時把網址放進錯誤訊息，至少能手動複製。

## 5. 突變驗證結果

**預期紅燈集合在跑之前登記在驅動腳本裡**（後端 `mutate.py backend`、前端 `mutate.py frontend`、E2E `mutate_e2e.py`；腳本在 session 的暫存區，不進版本控制）。每輪先跑**基線（不突變）必須 0 紅燈**，結束後逐檔比對還原。

### 5.1 前端單元層：13 個突變＋1 個補做（全部由 `vitest` 驅動）

| # | 突變 | 預測會紅 | 實測 |
|---|---|---|---|
| F1 | 標題請求不帶 `countView=false` | `seo.test`：查文章的網址 | ✅ 紅 1，吻合 |
| F2 | 不驗證後端給的 `tenantId` 是 UUID | `seo.test`：不像 UUID、沒有 tenantId 欄位 | ✅ 紅 2，吻合 |
| F3 | 標題順序顛倒（店名｜文章） | `seo.test`：『文章標題｜店名』 | ✅ 紅 1，吻合 |
| F4 | 文章頁（真正的瀏覽）也帶 `countView:false` | `StoreArticle.test`：不帶 `countView:false` | ✅ 紅 1，吻合 |
| F5 | 舊網址導向頁的查詢不帶 `countView:false` | `legacy-redirect.test`：不計瀏覽 | ✅ 紅 1，吻合 |
| F6 | 文章代稱不做 URL 編碼 | `article-path.test`：URL 編碼 | ✅ 紅 1，吻合 |
| F7 | 切換公開不先確認 | `media-visibility.test`：按取消不送請求 | ✅ 紅 1，吻合 |
| F8 | 送出的 `isPublic` 與意圖相反 | 開啟、取消公開兩案例 | ✅ 紅 2，吻合 |
| F9 | 複製的是路徑而不是完整網址 | 『複製的是完整網址』 | ✅ 紅 1，吻合 |
| F10 | 私有素材也顯示『複製公開網址』 | 『私有的素材沒有複製按鈕』 | ✅ 紅 2：預測的 1 個＋『後端沒有回傳 `isPublic`（舊回應）→ 當作私有』（同一個突變也破壞它，不是意外的缺口） |
| F11 | `absolutePublicMediaUrl` 的 `apiBase` 判斷由 `'apiBase' in context` 改成 `??` | 『沒有 API 基底也沒有頁面 origin』 | ⚠️ **倖存——我的預測錯了**。這個突變在測試環境裡是**等價突變**：`in` 與 `??` 只在『明確傳入 `undefined`』且『`NEXT_PUBLIC_API_URL` 有設』時才不同，測試環境沒設環境變數，兩者都得到 `undefined`；測試傳的 `apiBase: ''` 也不是 nullish。真正有意義的是 `pageOrigin` 那一行（jsdom 裡 `window.location.origin` 有值） |
| F11b | 同上，改 `pageOrigin` 那一行為 `??` | 同上 | ✅ 紅 1，吻合（補做） |
| F12 | 文章 404 不給白話說明 | 『文章不存在或未發布（後端 404）』 | ✅ 紅 1，吻合 |
| F13 | `/blog` 清單的文章連結退回舊網址 | **0 個紅燈**（單元層沒有連結的守門，由 E2E 擋） | ✅ 0 紅燈，如預測（見 §5.3 的 E4／E2） |

結果：**擊殺 11＋F11b＝12 個、等價 1 個（F11，預測錯誤）、預先登記的『單元層無守門』1 個（F13）**；每個擊殺的失敗集合都與預先登記一致（F10 多 1 個，原因如上）。檔案逐一比對還原，全部與原檔一致。

### 5.2 後端：基線 3 組＋12 個突變（`mvn` 驅動：單元用 surefire、整合用 failsafe，對真實 PostgreSQL）

**基線（不突變）三組都是 0 紅燈**：貼文單元測試、貼文整合測試（`PostControllerE2ETest`）、媒體單元＋整合測試（`PublicMediaEndpointIntegrationTest`）。

| # | 突變 | 預測會紅 | 實測 |
|---|---|---|---|
| B1 | 不管 `countView` 都遞增瀏覽數 | 單元：`countViewFalse_doesNotCountOrWrite` | ✅ 紅 1，吻合 |
| B1b | `countView=false` 時回應仍加一 | 同上 | ✅ 紅 1，吻合 |
| B2 | 控制器忽略 `countView` 參數（永遠 true） | 整合：`IT-M15-030`（單元測不到控制器） | ✅ 紅 1，吻合 |
| B3 | 回應少了 `tenantSlug` | 單元：`responseCarriesTenantSlug` | ✅ 紅 1，吻合 |
| B4 | 匿名查詢不要求 `is_public` | 整合：私有 404、開→讀→撤回往返 | ✅ 紅 2，吻合 |
| B5 | 匿名查詢不排除已刪除 | 整合：已刪除 404 | ✅ 紅 1，吻合 |
| B6 | 匿名查詢不看店鋪狀態（`OR 1 = 1`） | 整合：非 ACTIVE 店鋪 404 | ✅ 紅 1，吻合 |
| B7 | 服務問的是 `SUSPENDED` 而不是 `ACTIVE` | 單元：只查 ACTIVE、找到時回傳；整合：匿名 200 | ✅ 紅 7：預測的 3 個＋另外 4 個（整合的非 ACTIVE 店鋪、往返、單元的另兩個）——同一個突變同時破壞它們，不是缺口 |
| B8 | 不檢查儲存路徑屬於素材自己的店鋪 | 單元：路徑屬別家；整合：路徑被改壞 404 | ✅ 紅 3：預測的 2 個＋`downloadPublicAsset_found_returnsStoredObject`（Mockito 嚴格模式：`belongsToTenant` 的 stub 變成沒被用到，丟 `UnnecessaryStubbingException`，不是行為缺口） |
| B9 | 沒帶 `isPublic` 也去改公開狀態 | 單元：沒帶 `isPublic` 兩案例 | ✅ 紅 2，吻合 |
| B10 | 公開端點不回快取標頭 | 整合：匿名 200 案例（含 `Cache-Control`） | ✅ 紅 1，吻合 |
| B11 | 需要登入的檔案端點也放行匿名（**`SecurityConfig` 的 permitAll 與 `@PreAuthorize` 兩層一起拿掉**） | 整合：需登入端點仍 401 | ✅ 紅 1，吻合。**這個守門有兩層防護，單拿掉任何一層結果都不會變（匿名仍會被另一層擋下）——所以突變一定要兩層一起拿掉才有鑑別力** |
| B12 | 私有的素材也帶出 `publicUrl` | 單元：撤回後不帶、沒帶 `isPublic` 的私有 | ✅ 紅 2，吻合 |

結果：**12 個突變全部擊殺**，每個的預測紅燈都出現；B7、B8 多出來的紅燈都已說明原因。檔案逐一比對還原，全部與原檔一致。

### 5.3 真實瀏覽器（E2E）層（單元層沒有守門的連結與計數，要靠真實瀏覽器擋）

每個突變都要**重建前端**（約 1.5 分鐘），再用閘門腳本的臨時副本只跑指定案例（`-g`）——這些規格是 serial 模式，前一個案例失敗會讓後面的案例『沒有跑』，不逐案例跑會掩蓋。結束後以原始碼再建置一次前端。

| # | 突變 | 預測會紅 | 實測 |
|---|---|---|---|
| E1 | 店鋪貼文列表（`StoreBlogList`）的連結退回舊網址 | `E2E-ARTICLE-01` | ✅ 紅 1，吻合 |
| E2 | 平台 `/blog` 清單的連結退回舊網址 | `E2E-ARTICLE-07`、`E2E-POSTRULE-01` | ✅ 紅 2，吻合（`E2E-M15-005` 我預測會因舊網址導向而倖存，**這次 `-g` 沒包含它，預測沒有被驗證**） |
| E3 | 標題請求不帶 `countView=false`（伺服器端組標題會計瀏覽） | `E2E-ARTICLE-03` | ✅ 紅 1，吻合 |
| E4 | 舊網址導向頁不導向 | `E2E-ARTICLE-06` | ✅ 紅 1，吻合 |
| E5 | 分類頁（`/blog/category/[id]`）的連結退回舊網址 | `E2E-BLOGCAT-01`（新增的 `href` 斷言） | ✅ 紅 1，吻合 |

結果：**5 個全部擊殺**，失敗集合與預先登記一致。`E5` 的案例原本沒有連結斷言：設計這一層突變時我發現，三個連結產生處裡分類頁那一處在單元層與 E2E 都沒有守門（既有的分類頁案例只看畫面文字、不看連結），所以先補了一個 `href` 斷言（`at-blog-category-real.spec.ts`），再用 `E5` 證明它擋得住。

> **第一次跑這一層的結果是無效的（我的驅動腳本錯誤，不計入）**：我把 `-g` 的樣式寫成 `-g 'E2E-ARTICLE-01'`，而閘門腳本以**未加引號**的 `$E2E_SPECS` 展開，單引號變成樣式的一部分，Playwright 回『找不到符合的測試』，測試根本沒跑；驅動腳本的摘要把閘門結尾那句『基準為 0 failed』誤當成 Playwright 的結果，五個突變都顯示『倖存、0 紅燈』。發現方式：摘要裡沒有任何 `N passed`／`N failed` 的 Playwright 行。修正：拿掉引號、**沒有 Playwright 結果行就標『無效（測試沒跑）』**、保存每次的原始輸出；重跑後才是上面的結果。

## 6. 後置驗證

### 6.1 本機驗證

- **前端**：`tsc --noEmit` 0 錯誤；`npm run lint`（`eslint --max-warnings 0`，含 `e2e/`）0 警告；**Vitest 30 檔／318 案例全過**（277 ＋ 41：`media-visibility.test.tsx` 9、`StoreArticle.test.tsx` 8、`legacy-redirect.test.tsx` 5、`article-path.test.ts` 3、`seo.test.ts` +11、`lib/media.test.ts` +5，加總吻合；內嵌商品卡的 11 個案例原封不動地搬到 `StoreArticle.embedded-card.test.tsx`）。突變驗證之後在還原的原始碼上重跑一次，結果相同。
- **後端**：`mvn checkstyle:check` 0 違規；**完整 `mvn clean verify`：單元 2476／0、整合 1047／0**（25 分；對真實 PostgreSQL，`PublicMediaEndpointIntegrationTest` 的 7 案例是真實 HTTP）；`make validate-schema`（Flyway 乾淨庫＋`ddl-auto=validate`，`V98` 套用後 entity 對齊）；`make sync-schema-doc`／`make validate-schema-doc`（SRD 的 DDL 與 PRD §8.2 的欄位清單和 Flyway 實際 schema 一致——**第一次 `sync-schema-doc` 被擋下**：PRD §8.2.6 漏列 `is_public`，補上欄位與中文說明後兩個都通過）；`ApiRouteDocDriftTest` 2／2（含新控制器）。
- **完整本地 E2E 守門**（完整重建後端 JAR 與前端；Flyway 乾淨庫＋`ddl-auto=validate`，schema 對齊）：**381／381 通過（9.4 分）**，認證端點被限流 0 次。（381 ＝ 369 ＋ 7〔`at-article-titles-real`〕＋ 4〔`at-media-visibility`〕＋ 1〔`E2E-EMBED-04`〕。）
- **第一次跑新增與改動的 E2E 規格**（目標守門，重用已建置的 JAR 與前端）：65 個案例 64 通過、1 失敗——`E2E-EMBED-03` 的刪除商品請求回 404（我猜錯端點路徑，見 §8 第 5 點），改對後 `at-embed-card-status` 4／4。

**目視檢查（這輪補做，之前的 Sprint 都標註『沒做』）**：用臨時規格（用完即刪、不進版本控制）截圖：媒體中心（私有與公開的卡片並排、確認視窗）、店鋪貼文列表、店鋪外殼內的文章頁、找不到文章。版面正常：開關＋狀態文字＋『複製公開網址』在窄卡片裡沒有溢出、長檔名截斷顯示、確認視窗文案完整、文章頁在店鋪外殼內置中且『貼文』分頁為作用中。**目視看不出來的**（沒有用工具量過）：色彩對比、鍵盤操作順序、螢幕閱讀器念法。小提醒：文章頁返回連結寫『返回部落格』，店鋪外殼的分頁叫『貼文』（兩個詞在既有的店鋪前台本來就並存：分頁『貼文』、標題『店名｜部落格』），這輪沒有統一。

**目視檢查（這輪補做，之前的 Sprint 都標註『沒做』）**：用臨時規格（用完即刪、不進版本控制）截圖：媒體中心（私有與公開的卡片並排、確認視窗）、店鋪貼文列表、店鋪外殼內的文章頁、找不到文章。版面正常：開關＋狀態文字＋『複製公開網址』在窄卡片裡沒有溢出、長檔名截斷顯示、確認視窗文案完整、文章頁在店鋪外殼內置中且『貼文』分頁為作用中。**目視看不出來的**（沒有用工具量過）：色彩對比、鍵盤操作順序、螢幕閱讀器念法。

### 6.2 pre-push 與雲端 CI

【push 後回填】（pre-commit 快速檢查已通過，commit `b1305a86`；pre-push 輕量守門與雲端 CI 的結果在 push 之後補上）

### 6.3 開發容器網路內的連線驗證（Docker 政策下可行的實測方式）

為了不弄髒專案目錄（開發堆疊的後端容器 bind mount `./backend`，編譯產物會寫進 `backend/target`；專案的 `frontend` 目錄約 1 GB，多半是 `node_modules`，也沒有 `.dockerignore`），我把專案複製一份（排除 `.git`、`node_modules`、`.next`、`target`、`.env*`、`docs`）到工作暫存區，用獨立的 compose 專案名稱（`s336check`）啟動 `postgres`、`redis`、`backend`、`frontend` 四個服務。用到的映像都在 Docker 政策核准清單內（`node:20-alpine`、`maven:3.9-eclipse-temurin-21`、`postgres:18-alpine`、`redis:7-alpine`；`minio` 依 profile 沒啟動）。結束後 `docker compose down -v`、刪除這兩個建出來的映像；專案檔沒有被動到（只有本輪預期的兩處修改）。

| 驗證 | 結果 |
|---|---|
| frontend 容器的環境變數 | `API_INTERNAL_URL=http://ecommerce-backend-dev:8080/api` ✅ |
| 從 frontend 容器用容器名連後端 | `wget http://ecommerce-backend-dev:8080/api/actuator/health` → `{"status":"UP"}`；容器名解析為 `172.18.0.4` ✅ |
| 文章頁的原始 HTML（伺服器端取名稱） | `<title>文章標題測試…｜E2E 文章標題店 …｜意象若水 RUOSHUI</title>`、`og:type=article`、絕對的 `og:url`、description 取自內文 ✅ |
| **對照：拿掉 `API_INTERNAL_URL`（只改暫存副本）重建 frontend** | `<title>部落格文章｜意象若水 RUOSHUI</title>`，沒有 Open Graph——證明是這個環境變數讓容器內的標題生效，不是別的原因 ✅ |
| `at-article-titles-real` 7 個案例對容器堆疊 | **7／7 通過**（1.2 分）。**第一次跑 6 個沒跑、1 個失敗**：`E2E-ARTICLE-01` 點連結後 5 秒內網址沒變——開發模式（`next dev`）第一次導覽到沒編譯過的路由要等編譯；暖機各路由後重跑 7／7（不是產品問題，正式建置不會有） |

**這個實驗與預設開發堆疊的一個差異（誠實）**：`seedStore` 的第一步是畫面驅動的註冊／登入，而開發 compose 的 `NEXT_PUBLIC_API_URL` 帶了 `/v2`（見下），瀏覽器端的 API 呼叫會失敗。所以我把 frontend 容器的 `NEXT_PUBLIC_API_URL` 以環境變數覆蓋成 `http://localhost:8080/api`——只影響瀏覽器端，與 `API_INTERNAL_URL` 的伺服器端路徑無關。

**順帶量到的既有問題（`DEF-490`）**：開發 compose 堆疊的 `NEXT_PUBLIC_API_URL=http://localhost:8080/api/v2` 讓瀏覽器端呼叫變成 `/api/v2/v2/…`。實打：`POST /api/v2/v2/auth/login` → `E-1000 Authentication required`（沒有任何公開規則比對到）；正確的 `POST /api/v2/auth/login` → `E-1001 帳號或密碼錯誤`（真的登入端點）。同樣的值寫在 `docker-compose.yml`、`docker-compose.override.yml`（build arg）與 `.env.example`，而 `scripts/validate-e2e.sh` 的註解早就寫明『必須是 `.../api`、不可含 `/v2`』。與本輪改動無關，沒有修（改 compose 需要明確指示）。

## 7. 行為變更與已知限制

### 7.1 行為變更

1. **文章的公開網址改為 `/stores/{店鋪}/blog/{文章}`，匿名訪客可讀**（原本只有登入且選定店鋪的人讀得到）。
2. **文章頁有實體標題**（『文章標題｜店名』）、description 與文章型 Open Graph；伺服器端的標題請求**不計瀏覽**。
3. **停權（非 ACTIVE）店鋪的文章頁整頁 404**，標題退回靜態、不含文章標題與店名（`E2E-ARTICLE-05`）。**注意**：原本 `E2E-EMBED-03` 用『店鋪被停權』讓內嵌商品卡進入『此商品已下架』並以店主身分打開文章；改版後停權店鋪的文章頁不公開，那個案例改成『刪除商品』（PRD §6.6.3 對 `listing_unavailable` 的定義本來就是 Listing 進入 INACTIVE／DELETED），另新增 `E2E-EMBED-04` 守『停權店鋪整頁不公開』。
4. 舊網址 `/blog/{文章}`：登入且選定店鋪的人被導向新網址；匿名訪客維持原本的登入提示。
5. `GET /v2/posts/{slug}` 新增選填 `countView`（預設 true，行為不變）；貼文回應新增 `tenantSlug`。
6. **媒體素材有公開開關（預設私有）**；公開的素材可由匿名端點讀取；媒體中心有開關與『複製公開網址』。
7. 開發堆疊的前端容器取得 `API_INTERNAL_URL`，公開頁的實體標題在容器網路內也會生效。

### 7.2 已知限制（誠實列出）

1. **文章頁的 category 徽章仍連到 `/blog/category/{id}`**（登入才打得開）；匿名訪客點了看到登入提示。`/blog` 與分類頁仍以 localStorage 的 `tenantId` 為準。登記 `DEF-486`（需要店鋪前台的公開版分類篩選）。
2. **公開貼文 API 不看店鋪狀態**（停權店鋪的已發布貼文仍可由 `GET /v2/posts…` 讀到）；前端文章頁因為先查公開店鋪資料而不會呈現。登記 `DEF-485`。
3. **公開素材圖片經過後端串流**，匿名請求共用每 IP 每分鐘 100 次的限流桶，沒有 CDN／預簽網址；以 5 分鐘公開快取減輕，**未實測**實際影響。登記 `DEF-488`。
4. **撤回公開／刪除後，已被瀏覽器或 CDN 快取的副本最多還看得到 5 分鐘**（`max-age=300`）；伺服器端立即不再提供（`IT-PM-007` 驗證）。
5. **撤回公開不檢查是否有貼文正在使用這個網址**（與刪除不同，刪除會被 `E-8020` 擋）：撤回的目的就是快速收回；確認視窗說明後果。使用中的頁面會顯示不出圖（貼文頁的 `onError` 會把破圖藏起來，不會有提示）。
6. **公開開關不限檔案類型**（圖片、影片、PDF 都可公開）：影片上限 100 MB，經後端串流並共用限流桶；我沒有加『只有圖片能公開』的限制（§10 第 4 點）。
7. **全域 `force-response` 讓公開圖片的 `Content-Type` 變成 `image/png;charset=UTF-8`**（DEF-397 的全域設定，需要登入的檔案端點本來就是這樣）；瀏覽器對圖片忽略 charset，不影響顯示。
8. **文章頁的標題請求要打兩次後端**（店鋪→文章），各有 2 秒逾時；它們來自 Next 伺服器的 IP，**匿名限流桶是共用的**——站台尖峰時標題會退回靜態（優雅退化）。**未實測**，登記 `DEF-487`。
9. 貼文編輯頁的『精選圖片 URL』仍是純文字欄位，沒有『從媒體庫挑選』；店主要自己貼上『複製公開網址』的結果。登記 `DEF-489`。
10. 媒體上傳的真實流程（需要 MinIO）仍沒有瀏覽器層級的 E2E；公開讀取的伺服器端行為由後端整合測試（真實 PostgreSQL＋真實 HTTP，儲存層 mock）守，UI 由 mock 後端的 E2E 守。
11. 沒有 `canonical`、JSON-LD 與 sitemap。
12. **正式環境的 `docker-compose.yml` 沒有 `API_INTERNAL_URL`**：前端容器裡的 `localhost` 是它自己，正式環境的容器內標題會退回靜態——要加需要明確指示（值會是 `http://backend:8080/api`）。`next.config.ts` 的 `rewrites` 也把後端寫死成 `ecommerce-backend-dev`（只在開發堆疊成立）。
13. **開發 compose 堆疊的瀏覽器端 API 基底網址帶 `/v2`**（既有問題，§6.3，`DEF-490`），沒有修。

## 8. 預測對答案

> 突變驗證的預期紅燈集合**在跑之前寫進驅動腳本**（所以那幾列是真的事前登記）；完整測試的數字是我動手前口算、沒有寫進檔案，下表標註。

| # | 預測（動手前） | 實測 | 吻合？ |
|---|---|---|---|
| P-1 | 前端單元突變 13 個的失敗集合（預先登記） | 11 個吻合（`F10` 多紅 1 個：同一個突變也破壞『舊回應當私有』案例）、`F13` 預測 0 紅燈 → 0；**`F11` 我預測會被擊殺，實際倖存**——它是等價突變（測試環境沒設 `NEXT_PUBLIC_API_URL`，`in` 與 `??` 結果相同）；補了改 `pageOrigin` 那一行的 `F11b`，擊殺 | ✅／⚠️（1 個預測錯誤） |
| P-2 | 後端基線 3 組 0 紅燈、12 個突變的失敗集合（預先登記） | 基線 3 組 0 紅燈；12 個全擊殺，預測的紅燈全部出現；`B7` 多紅 4 個、`B8` 多紅 1 個（原因見 §5.2） | ✅ |
| P-3 | E2E 突變 5 個各被指定的案例擊殺（預先登記） | 全吻合；**第一次跑因驅動腳本的引號錯誤而無效（測試沒跑）**，重跑後吻合；`E2` 對 `E2E-M15-005`『會倖存』的預測**沒有被驗證**（`-g` 沒包含它） | ✅（1 個預測未驗證） |
| P-4 | 完整後端 `mvn clean verify`：單元 2463 ＋ 13 ＝ 2476、整合 1039 ＋ 8 ＝ 1047（執行前口算） | 單元 **2476／0**、整合 **1047／0**，BUILD SUCCESS（25 分） | ✅ |
| P-5 | 完整本地 E2E 守門：369 ＋ 7（`at-article-titles-real`）＋ 4（`at-media-visibility`）＋ 1（`E2E-EMBED-04`）＝ 381（執行前口算） | **381 通過、0 失敗**（9.4 分）；認證端點被限流 0 次 | ✅ |
| P-6 | 前端 Vitest 277 ＋ 41 ＝ 318（**事後核對**：數字是看到結果才算出來的，不是動手前登記） | 30 檔 **318**／0 | ✅（事後） |

**沒有登記的發現**（動手過程中才遇到，誠實列出）：

1. **文章頁匿名訪客根本打不開**——比『沒有標題』大得多的問題（§3.2）。這改變了兩個選項的比較，是我向使用者提問前就查出來並寫進選項說明的。
2. **我第一次盤點連結產生處漏了分類頁**（過濾條件把含 `category/` 的行一起濾掉，§3.2）；寫選項說明前重數才抓到。
3. **`API_M15_CMS.md` 的『共 48 個端點』在 Sprint 334 移除 5 個端點後沒有更新**：我一開始照舊數字寫成『49』，後來用工具重數（概覽表 44 列、『端點』宣告行 44 行）才發現實際應是 44（含本輪新增的 1 個），已更正文件與 `API_Index.md`。
4. **全域 `force-response` 讓公開圖片的 `Content-Type` 帶 `;charset=UTF-8`**：後端整合測試第一次 6／7，失敗的是我的斷言寫得太嚴（比對整個標頭）；既有的全域行為，改成比對 MIME 類型本身（§7.2 第 7 點）。
5. **第一次跑新增與改動的 E2E 規格：64 通過、1 失敗**——失敗的是我在 `E2E-EMBED-03` 猜錯的刪除商品端點路徑（`/v2/dashboard/products/{id}` 不存在，實際是 `/v2/products/{id}`，回 404 `E-4041`）；改對後 4／4。
6. **E2E 突變驅動的 `-g` 引號錯誤**（§5.3），第一輪結果作廢。
7. `Mockito` 嚴格模式讓 `B8` 多紅一個案例（§5.2）；`F11` 倖存是等價突變（§5.1）。
8. **發布追蹤表頭的版本號落後**：標頭寫 `v4.84`，版本歷史最新是 `v4.88`（Sprint 335 的回填）。這輪更新標頭時一併對齊。
9. **選項說明寫的『6 個 E2E 規格要改』實際是 5 個**（§3.2 的更正）：數的是含 `/blog/` 的規格數，其中 `at-page-titles` 只含分類頁路由。
10. **容器驗證量到開發 compose 的 `NEXT_PUBLIC_API_URL` 帶 `/v2`**（`DEF-490`，§6.3）；以及容器內第一次導覽的開發模式編譯延遲讓第一次跑失敗（暖機後 7／7）。
11. **工具坑（記憶裡都記過，我還是各踩一次）**：zsh 未加引號的萬用字元（`--include=*.ts`）讓第一個 grep 直接失敗；`python3 - <<'EOF'` 餵全形字元的腳本被誤判編碼（改成先寫成 UTF-8 檔再執行）。

## 9. 新登記的相鄰缺陷（只登記，不在本輪修）

| ID | 發現 | 證據與其性質 | 嚴重度與建議 |
|---|---|---|---|
| `DEF-485` | 公開貼文 API（`GET /v2/posts`、`/v2/posts/{slug}`、`/v2/posts/categories`）不看店鋪狀態：停權店鋪的已發布貼文仍可由 API 讀到 | **讀碼確認**（§3.2 #6）；前端文章頁已因先查店鋪而擋住，API 層未擋；**未實測** | 🟡 建議與 `GET /v2/listings/{id}`、`GET /v2/public/stores/{slug}`、`GET /v2/public/media/{id}` 一致：非 ACTIVE 店鋪一律 404 |
| `DEF-486` | 文章頁的分類徽章與平台層 `/blog`、`/blog/category/[id]` 對匿名訪客仍是登入提示 | **讀碼確認**；`at-article-titles-real` 沒有覆蓋（刻意：這輪不改分類頁） | 🟡 建議在店鋪前台做公開版分類篩選（例如 `/stores/{店鋪}/blog?category=`；後端 `GET /v2/posts?categoryId=` 已支援），徽章改連過去 |
| `DEF-487` | 伺服器端的標題請求（店鋪頁 1 次、文章頁 2 次、商品頁 1 次）來自 Next 伺服器的單一來源 IP，共用匿名限流桶（100 次／分） | **讀碼**（`RateLimitFilter` 類別說明：匿名者以來源 IP 為單位）；**未實測** | 🟢 尖峰時標題優雅退化為靜態；真要量要在正式拓撲上壓測。可考慮把伺服器端請求排除在限流外或帶使用者 IP |
| `DEF-488` | 公開素材圖片經過後端串流，匿名請求共用每 IP 每分鐘 100 次的限流桶，沒有 CDN／預簽網址 | **讀碼**；**未實測** | 🟢 流量大時改走 MinIO 預簽網址或 CDN；先量一頁 12 張封面圖在限流下的實際表現 |
| `DEF-489` | 貼文編輯頁的『精選圖片 URL』沒有『從媒體庫挑選』 | **讀碼確認**（§3.3 #3） | 🟢 體驗改善：挑選器直接填入公開網址；沒公開的素材可就地提示『先公開』 |
| `DEF-490` | 開發 compose 堆疊的 `NEXT_PUBLIC_API_URL` 帶 `/v2`，瀏覽器端 API 呼叫變成 `/api/v2/v2/…`，登入註冊等全部打到不存在的路徑 | **實測**（§6.3）：frontend 容器環境變數；對後端實打雙 `/v2` → `E-1000`、正確路徑 → `E-1001` | 🟡 建議 compose 預設值、override 的 build arg 與 `.env.example` 都改成不含 `/v2` 的 `http://localhost:8080/api`；**改 compose 需要明確指示** |

## 10. 我替使用者做的決定（可一句話推翻）

1. **舊網址 `/blog/{文章}` 保留為導向頁**（使用者選項說明裡寫的是『舊網址會失效；要保留就仍需 slug-only 查詢』）。我用『登入者的 localStorage `tenantId` 找出文章所屬店鋪再導向』的方式保留——這正好涵蓋改版前**所有打得開**舊網址的人（只有登入且選定店鋪的人），匿名訪客本來就打不開，行為不變；代價是多一個小頁面與一個測試檔。推翻：刪掉 `app/(storefront)/blog/[slug]/page.tsx`，舊網址變 404。
2. **查詢帶 `countView=false` 的做法**：用既有端點加選填參數（預設 true，行為不變），而不是另開唯讀端點。理由：改動最小、既有呼叫端不必動。
3. **匿名端點獨立成 `PublicMediaController`／`PublicMediaService`**，路徑 `/v2/public/media/{id}`（而不是讓 `/v2/media/files/{id}` 雙模式）：保持需要登入的端點完全不變，`/v2/public/**` 本來就是公開前綴。
4. **公開開關不限檔案類型**：使用者選的是『素材逐一開關』，沒有說只限圖片；我沒有替使用者加限制。風險見 §7.2 第 6 點；推翻：在 `updateAsset` 與開關 UI 加『只有圖片能公開』。
5. **撤回公開不被『有貼文在用』擋下**：撤回的目的是快速收回；以確認視窗說明後果。
6. **兩個方向都先確認**（公開與取消公開）。
7. **公開的快取是 5 分鐘**（`CACHE_SECONDS = 300`）：在『撤回後多久還看得到』與『每次載入都打後端』之間的折衷。
8. **`E2E-EMBED-03` 改成『刪除商品』**，停權店鋪的行為另開 `E2E-EMBED-04`（§7.1 第 3 點）。
9. **文章頁 404 的白話訊息**、**標題改 `h2`**：這頁現在是匿名訪客會看到的公開頁，原本顯示英文的 `Request failed with status code 404`；店鋪外殼已有 `h1`。
10. **目視檢查順便做了**（原本不在範圍內）。
11. **容器內連線驗證用專案副本與獨立的 compose 專案名稱**，並把 frontend 的 `NEXT_PUBLIC_API_URL` 覆蓋成不含 `/v2` 的值（理由見 §6.3）；沒有動專案裡的 `docker-compose*.yml`（除了使用者指示的那一行）。這個驗證不在使用者的要求內，是我為了確認『加了就真的有效』而做的；它順帶量到 `DEF-490`。
12. **不替使用者修 `DEF-490`、也不替正式 compose 加 `API_INTERNAL_URL`**：兩者都是改 `docker-compose*.yml`，Docker 政策要求明確指示。

## 11. 狀態

**完成（2026-11-10），push 與雲端 CI 結果待回填（§6.2）。** 處置：`DEF-483` ✅（文章網址帶店鋪、匿名可讀、實體標題）、`DEF-482` ✅（素材公開開關與匿名端點）、`docker-compose` 的 `API_INTERNAL_URL` ✅（只加在開發用的 override，容器內實測生效）；新登記 `DEF-485`～`DEF-490`。

**本機驗證**：`tsc` 0 錯誤；`eslint --max-warnings 0` 0 警告；Vitest **30 檔／318 案例**；後端 checkstyle 0 違規、**單元 2476／0、整合 1047／0**；`validate-schema`、`validate-schema-doc`、`ApiRouteDocDriftTest` 通過；**完整本地 E2E 守門 381／381**；突變驗證：前端 12 擊殺＋1 等價（`F11`，我的預測錯誤）、後端 12／12（基線 0 紅燈）、E2E 5／5（第一次因驅動腳本引號錯誤而無效，重跑後吻合）；容器堆疊上 `at-article-titles-real` 7／7（§6.3）；目視檢查補做。

**提交**：`b1305a86`（程式＋測試＋E2E＋docker override＋API／PRD／設計慣例文件）→ 本文件 commit（計畫書、追蹤表關閉 `DEF-482`／`DEF-483` 並登記 `DEF-485`～`DEF-490`、發布追蹤新增 Sprint 336）。

**需要使用者決定的**：①`DEF-490`（開發 compose 的 `NEXT_PUBLIC_API_URL` 帶 `/v2`，要不要修；要明確指示）；②正式的 `docker-compose.yml` 要不要也加 `API_INTERNAL_URL`（值 `http://backend:8080/api`）；③`DEF-485`（公開貼文 API 要不要也只供應 ACTIVE 店鋪）；④`DEF-486`（店鋪前台公開版分類篩選）；⑤§10 裡我替你做的決定，尤其第 1 點（舊網址導向頁）與第 4 點（公開開關不限檔案類型）。

**關於本輪的誠實說明**：兩題你都選了工最大、不是我建議的選項，我照做；文章頁匿名打不開是查證時才發現的（比標題更大）；我第一次盤點漏了分類頁的連結產生處、`API_M15` 的『48 個端點』是舊數字（重數後 44）；第一次跑 E2E 時猜錯刪除商品的端點；E2E 突變驅動的 `-g` 引號錯誤讓第一輪結果作廢；`F11` 的預測錯了（等價突變）；`E2` 對 `E2E-M15-005` 的預測沒被驗證；容器驗證第一次因開發模式編譯延遲失敗（暖機後 7／7）；容器驗證用了專案副本並覆蓋 `NEXT_PUBLIC_API_URL`；公開圖片的限流與 CDN 影響、標題請求共用匿名限流桶都**沒有實測**（`DEF-487`、`DEF-488`）；沒有量色彩對比、鍵盤順序與螢幕閱讀器；`DEF-485` 是讀碼確認、未實測；本機 E2E 守門沒有 MinIO，真實上傳流程仍沒有瀏覽器層級的 E2E。
