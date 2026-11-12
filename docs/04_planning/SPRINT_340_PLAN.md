# Sprint 340 計畫書：匿名請求放大的量測與修復（`DEF-499`）、伺服器端標題請求的實測（`DEF-487`）、`/blog` 初始 HTML 的實測（`DEF-500` ①）

> **日期**：2026-11-12
> **性質**：量測（沒有後端變更）＋前端（7 個元件、11 個連結關閉預取）＋架構守門（新增 `entity-link-prefetch.test.ts`）
> **依據**：使用者貼回 Sprint 339 總結並說「請繼續完成任務！」（沒有 `==>` 回覆、沒有互動選項的答案）；我對「繼續」範圍的解讀是**推論**，見 §1.1
> **前置**：[SPRINT_339_PLAN.md](SPRINT_339_PLAN.md) §6.2、§9（`DEF-499` 的登記）、[SPRINT_336_PLAN.md](SPRINT_336_PLAN.md) §7.2（`DEF-487` 的登記）、[SPRINT_335_PLAN.md](SPRINT_335_PLAN.md)（`generateMetadata` 的由來）

## 1. 背景與授權

### 1.1 「請繼續」的解讀（推論，可一句話推翻）

Sprint 339 總結寫「不需要你決定」，沒有點名下一個要做的項目。沿用 Sprint 325 以來的做法：做**總結自己承認沒做**、而且 AI 能獨立處理的事。總結列的是：①「列表在瀏覽器端載入，初始 HTML 預期沒有文章卡片……我沒有 `curl` 或爬蟲實測」；②「限流桶不只影響一個規格」「對照實驗沒做完整」；③「`DEF-499`（要不要先量測前台請求放大倍率）」。追蹤表 `DEF-499` 建議欄的第 ① 步就是「先量測：對店鋪前台一次完整瀏覽，數後端收到的請求……給出放大倍率」。所以範圍是：

- `DEF-499` ①：量測前台一次瀏覽放大成多少個後端請求，並分出是誰發的；
- `DEF-487`：同一族（匿名每 IP 限流桶），同樣標「讀碼、未量測」，同一次量測就能回答；
- `DEF-500` ①：`/blog` 的初始 HTML 有沒有文章卡片（總結明說未實測）。

**不碰**：限流政策本身、正式部署拓撲（有沒有反向代理）、要不要為 SEO 做伺服器端渲染——這些是產品／部署決定。

追蹤表重新盤點（腳本解析 497 列：狀態欄首字 ✅ 的 283 列；其餘 214 列大多是「不排入排程」的舊項目）：近期標「登記，未做／未修」的有 `DEF-454`、`461`～`464`、`469`、`470`、`481`、`484`、`487`～`489`、`496`、`497`、`499`、`500`。`DEF-463`、`DEF-496` 看起來也不需要產品決策（**我只讀了追蹤表，沒有逐項重新驗證**），但沒有證據顯示它們比 `DEF-499` 更緊急——`DEF-499` 是反覆讓唯一有效的守門（本機 E2E）間歇失敗的那一項——所以這輪不做，維持登記。

### 1.2 Docker 政策的合規

沒有動 `Dockerfile`、任何 `docker-compose*.yml`、image、tag、volume、`profiles`、healthcheck、`deploy.resources`、Redis 設定。量測用的 PostgreSQL（`postgres:18-alpine`）與 Redis（`redis:7-alpine`）是 `DOCKER_POLICY.md` 核准清單裡的映像，由我 `docker run` 起、以 `docker rm -fv` 清掉（沒有新增 image、沒有留下卷）。收尾核對：0 個容器；`docker volume ls -f dangling=true` 的 5 個卷全是專案既有的**具名**卷（開發用 postgres／minio／redis 資料、maven 快取、act 工具快取，建立日期都早於這次 session），沒有動。

### 1.3 `frontend/AGENTS.md` 的提醒

`frontend/AGENTS.md` 要求改 Next.js 相關程式前先讀 `node_modules/next/dist/docs/`（這個版本是 16.4.2，與一般認知不同）。**我是先試了一條路（§4.4）、重建才被擋下，之後才讀到這份提醒與 Next 原始碼**，那條路因此放棄；最後採用的 `prefetch={false}` 的語意（App Router：進入視窗與 hover 都不預取）與建議，是讀 `01-app/03-api-reference/02-components/link.md` 與 `01-app/02-guides/prefetching.md`（「Preventing too many prefetches」）確認的。

## 2. 方法

- **計數代理**（附錄 A，40 行 Node）：瀏覽器與 Next 伺服器的後端請求都先經過它再到後端，每個請求寫一行 JSON（時間、方法、路徑、狀態、`User-Agent`；`a` 欄位＝有沒有帶 `Authorization`，是做 §7.3 的規格量測時才加的，§4、§7.1 的頁面探針當時沒有這一欄）。後端沒有逐請求日誌，所以不能只看後端。`/__mark/<標籤>` 由代理自己處理，用來在日誌裡切出每一次量測。Next 伺服器的 `User-Agent` 是 `node`，瀏覽器是 `Mozilla…HeadlessChrome`，兩者分得開（預測 P9 成立）。
- **全棧**與 `scripts/validate-e2e.sh` 同形：乾淨 PostgreSQL＋Redis、`test` profile 後端 JAR、`next build`＋`next start`（正式建置，預取只在正式建置才有）。差別只有：前端的 `NEXT_PUBLIC_API_URL` 與 `API_INTERNAL_URL` 指向代理。
- **專案副本**：前端用 APFS clone（`cp -cR`，13 秒、寫時複製，實際占用沒有量）複製到 scratchpad，建置、探針規格與全部量測腳本都在副本或 scratchpad 裡，**沒有往 repo 寫任何量測產物**（repo 的變更只有 §5 的程式與文件）。
- **預測先寫進檔案**再跑（§3）。
- **每次量測前清空限流桶**（`redis-cli --scan --pattern 'ratelimit:*' | xargs redis-cli del`），讓每次量測只量「需求」，不受前一次殘留額度影響。
- **探針規格**（附錄 B）：用 `seedStore` 走真實開店流程種一間店鋪（商品＋房源）與 13 篇已發布貼文（內文含商品內嵌卡），再以匿名訪客逐頁量測：完整載入、完整載入＋捲過整頁，以及一趟用戶端導覽（店鋪首頁→貼文分頁→第一篇文章→上一頁）。每次量測同時記 Playwright 看到的瀏覽器端請求（含預取）與代理看到的全部後端請求，兩邊交叉核對（15 次量測的瀏覽器→後端請求數 Playwright 與代理**全部一致**）。

## 3. 預測 vs 結果

預測（跑量測之前寫進 `predictions.md`，沒有事後修改）：

| 編號 | 預測 | 結果 |
|---|---|---|
| P1 | `/blog` 初始 HTML 不含任何文章卡片 | **一致**（§4.6：12 張卡片 0 張） |
| P2 | 首頁完整載入：Next 伺服器→後端 0 個；瀏覽器→後端約 2～6 個 | **錯**：伺服器端 **2** 個（首頁上兩張商品卡的預取）、瀏覽器端 **1** 個。我用 `curl` 驗證了首頁本身的伺服器端渲染是 0 個，卻漏掉預取 |
| P3 | 店鋪首頁：`GET /v2/public/stores/{slug}` 2 次（伺服器 1＋瀏覽器 1） | **部分**：瀏覽器 1 次對，伺服器端是 **4** 次（本頁 1＋三個分頁連結的預取 3） |
| P4 | 文章頁：Next 伺服器 2 個；總數 ≥ 6 | **部分**：總數 10 對，但伺服器端是 **6**（本頁 2＋四個分頁連結的預取 4）、瀏覽器端 4 |
| P5 | 連結預取只在正式建置才有；捲過整頁後預取請求數接近連結數 | **部分**：預取只在正式建置才有（對）；數量約是連結數的兩倍（每個連結送 `/_tree` 與 metadata-only 兩種，§4.3），再加上不打後端的連結 |
| P6 | 預取一個動態路由會讓 Next 伺服器執行 `generateMetadata`（約 55% 信心） | **一致，而且比預期嚴重**：每張文章卡片被預取＝2 個後端請求（§4.2） |
| P7 | 一次完整載入的後端請求數：首頁 ≤ 6、店鋪首頁 4～8、店鋪貼文列表 4～8（不含預取）、文章頁 6～10 | 首頁 3、店鋪首頁 6、文章頁 10 在範圍內；**店鋪貼文列表 27**（我把「不含預取」寫進預測，但真實使用者的瀏覽器一定會預取，所以這個預測本身設錯了條件） |
| P8 | 匿名 GET 不帶自訂標頭，不會有 OPTIONS 預檢 | **一致**（15 次量測 OPTIONS 全是 0） |
| P9 | Next 伺服器的 `User-Agent` 不含 `Chrome` | **一致**（`node`） |

**這組預測的共同盲點**：都在數「一個頁面自己直接打幾個後端請求」，沒有想到預取會把每個可見連結變成一次伺服器端渲染。這與 Sprint 336 登記 `DEF-487` 時的算法（「店鋪頁 1 次、文章頁 2 次、商品頁 1 次」）是同一個盲點。

## 4. 量測與成因

### 4.1 一次瀏覽打到後端的請求（匿名訪客、正式建置）

完整的「修改前→修改後」並排表在 §7.1。修改前（`HEAD`＝Sprint 339 完成時的程式）的重點：

- **店鋪貼文列表**：一次完整載入 **27** 個後端請求，其中 **24 個來自 Next 伺服器**；捲過整頁 31／28。
- **平台 `/blog`（跨店鋪，12 張卡片）**：17 個（伺服器 16）；捲過整頁 25（伺服器 24）。
- **一趟典型瀏覽**（店鋪首頁→貼文分頁→第一篇文章→上一頁）：**39** 個（伺服器 27、瀏覽器 12）。
- 加總（六個頁面的完整載入＋那一趟瀏覽）：**111** 個，其中 **Next 伺服器 85（77%）**。

### 4.2 成因：每個被預取的連結，伺服器多跑一次 `generateMetadata`

`generateMetadata`（Sprint 335，`DEF-475`）會在伺服器端呼叫後端取店名、文章標題、商品名。Next 16 對每個進入視窗的 `<Link>` 都會送預取請求，伺服器為了回應預取也會執行目標頁的 `generateMetadata`。**這個公式把每一筆量測都對上了**：

| 被預取的連結 | 伺服器多打的後端請求 |
|---|---|
| 一張文章卡片（`/stores/{店}/blog/{文}`） | **2**（`GET /v2/public/stores/{slug}` ＋ `GET /v2/posts/{slug}`） |
| 一個店鋪分頁連結（首頁／商品／房源／貼文） | 1（`GET /v2/public/stores/{slug}`） |
| 一張商品卡（`/listings/{id}`） | 1（`GET /v2/listings/{id}`） |
| 本頁自己的伺服器端渲染 | 店鋪頁 1、文章頁 2、商品頁 1、首頁 0、`/blog` 0 |

例：店鋪貼文列表完整載入＝本頁 1＋三個分頁連結 3（貼文分頁就是目前這一頁，不預取）＋視窗內 10 張文章卡片 10×2。店鋪請求 1+3+10＝**14**、文章請求 **10**，實測正是 14 與 10。文章頁＝本頁（店鋪 1＋文章 1）＋四個分頁連結 4＝店鋪 **5**、文章 **1**，實測正是 5 與 1。`/blog` 不捲動時視窗內 8 張卡片＝16，捲過整頁 12 張＝24，實測 16 與 24。

**隔離實驗（單一請求）**：同一個文章網址，用 `curl` 分別送三種請求，數 Next 伺服器打了幾個後端請求：完整 HTML 請求 **2**（店鋪＋文章）；店鋪首頁 **1**；商品頁 **1**。再重播**瀏覽器實際送出的 metadata-only 預取請求**（取自 Playwright 的請求標頭，`Next-Router-Prefetch: 1`、`Next-Router-State-Tree: ["",{},null,"metadata-only"]`）：後端收到 **2** 個，回應（2679 bytes）裡含文章的實體標題。

### 4.3 Next 16 的預取長什麼樣

對一個進入視窗的連結，瀏覽器送**兩種**請求：①`Next-Router-Segment-Prefetch: /_tree`（路由樹預取，沒有 state tree；重播它後端收到 **0** 個）；②`Next-Router-Prefetch: 1`＋`Next-Router-State-Tree: ["",{},null,"metadata-only"]`（只要這個路由的 metadata／head；**只有這一種會讓伺服器跑 `generateMetadata`**）。官方指南（`prefetching.md`）說明沒有 `loading.js` 的路由，預取範圍是整頁、客戶端快取 5 分鐘（`staleTimes.static`）。本專案的所有頁面都是動態渲染（`proxy.ts` 每個請求產生 CSP nonce），`next.config.ts` 沒有設定 `staleTimes`。

### 4.4 我先試了、放棄的做法：在 `generateMetadata` 裡偵測預取

直覺的做法是：預取請求不需要實體標題，`fetchPublicData` 偵測到預取就不查後端（回 `null`，呼叫端本來就會退回靜態標題）。我在副本實作了（`await headers()` 讀 `next-router-prefetch`）、重建、重播同一個預取請求：**後端仍收到 2 個、回應仍含實體標題**——偵測沒有生效。讀 Next 原始碼後確認原因：伺服器在把請求交給使用者程式碼之前，會用 `stripFlightHeaders`（`node_modules/next/dist/server/app-render/strip-flight-headers.js`）刪掉 `FLIGHT_HEADERS`（含 `next-router-prefetch`、`next-router-segment-prefetch`、`rsc`、`next-router-state-tree`），`headers()` 讀不到。

即使改在 `proxy.ts` 偵測再轉發一個自訂標頭，還有第二個風險（**沒有實測**，因為做法已放棄）：客戶端的 segment cache 會把 metadata-only 預取的結果（`MetadataOnlyRequestTree`）存起來，如果預取回的是靜態標題，點擊導覽時可能被卡住用到。副本已還原（`diff -rq` 確認與 repo 的 `src` 一致）。

### 4.5 預取對這些連結有沒有好處

**點擊文章卡片到內容出現的時間**（本機、各 8 次，毫秒）：

| 建置 | 條件 | 中位數 | 範圍 |
|---|---|---|---|
| 修改前 | 預取開著（預設） | **410** | 306～537 |
| 修改前 | 用 Playwright 擋掉全部預取請求（模擬 `prefetch={false}`） | 378 | 357～430 |
| 修改後 | 預設（實體連結已關預取） | **357** | 312～437 |
| 修改後 | 再擋掉全部預取請求 | 417 | 290～499 |

四組中位數都落在 357～417 ms，**差異小於每組內部的波動**。這是 localhost（網路延遲約 0）：**真實網路下的差異沒有量**，官方指南也說明代價是「動態路由要等伺服器渲染才導覽」。另外，修改前點擊已預取的連結時，**分頁標題維持前一頁的標題約 90～200 毫秒，等導覽回應回來才一次換成文章標題**（4 次執行序列完全相同）——預取帶回來的 head 沒有被提前套用。

### 4.6 `DEF-500` ①：不執行 JavaScript 時的初始 HTML（`curl`）

| 頁面 | `<title>` | `og:title` | 初始 HTML 裡的文章／內文 |
|---|---|---|---|
| 平台 `/blog`（跨店鋪列表） | 靜態「部落格｜意象若水 RUOSHUI」 | **沒有** | 12 張文章卡片的標題 **0 次** |
| `/stores/{店}/blog`（店鋪貼文列表） | 店名｜部落格 | 有 | 文章標題 0 次，載入中骨架 |
| `/stores/{店}/blog/{文}`（文章頁） | 文章標題｜店名 | 有 | 標題與內文出現 6 次，但那是 `<title>`、description、`og:*`、`twitter:*` 等 metadata（種子文章的整段內文就是 description）；文章本體由瀏覽器端載入（載入中骨架） |
| 店鋪首頁、商品詳情 | 實體名稱 | 有 | 內容由瀏覽器端載入（載入中骨架） |

所以：**平台 `/blog` 的卡片不在初始 HTML**（P1 成立）。Googlebot 會執行 JavaScript，是否因此看得到沒有實測；不執行 JavaScript 的爬蟲與連結預覽機器人看不到。這是 `DEF-500` ① 的事實，要不要做伺服器端渲染仍是產品決定（PRD 只把「SEO 優化：Meta title/description 自訂」列為 P2，且 v1.0.2 更正時移除了 `seo_title`／`seo_description` 欄位，所以這不是 PRD 缺口）。

## 5. 實作

### 5.1 對指向實體頁的連結關閉預取（11 個連結、7 個元件）

`<Link … prefetch={false}>`（App Router 的語意：進入視窗與 hover 都不預取；Pages Router 才會在 hover 預取）：

| 檔案 | 連結 |
|---|---|
| `components/storefront/StoreNav.tsx` | 店鋪分頁（4 個，同一個 `map`） |
| `components/storefront/StoreHeroBanner.tsx` | 「查看商品」「查看房源」 |
| `components/storefront/ProductCard.tsx` | 卡片的 `Link`（`HomeContent`、`ListingTypeBrowser`、`StoreListingGrid`、`StoreFeaturedListings` 四處共用） |
| `components/storefront/PlatformBlogFeed.tsx` | 文章卡片 |
| `components/storefront/StoreBlogList.tsx` | 文章卡片；分類不存在畫面的「查看全部貼文」 |
| `components/storefront/StoreArticle.tsx` | 「返回部落格」（兩處）；分類徽章 |
| `app/(auth)/notifications/page.tsx` | 「重新預訂」 |

窮舉方式：先用 `grep` 找，再靠新守門的紅燈核對——守門在還沒改前列出的違規位置**恰好是這 11 處**（沒有多、沒有漏）。`/login`、`/register`、`/dashboard`、`/blog` 等不打後端的連結沒動。

### 5.2 守門：`src/architecture/entity-link-prefetch.test.ts`＋`detect.ts` 的 `linkElements`／`isEntityHref`

這個放大從 Sprint 335 加上 `generateMetadata` 起就存在，四個 Sprint 沒人看見；規則寫成測試，違反時 `npm test`（pre-commit、CI）就會紅燈。規則：href 指向 `/stores/…`、`/listings/…`（或 `articlePath(`、`storeBlogPath(` 產生的網址）的 `<Link>` 一律要有字面的 `prefetch={false}`；href 是變數、偵測器看不出指向哪裡的共用元件（`ProductCard`）列在清單裡，檔案內每個 `<Link>` 都要關。`href={識別字}` 會換成同檔 `const`／`let` 宣告的右式（`StoreArticle` 的 `blogHref`）。

**已知限制（刻意取捨）**：只認得 JSX 的 `<Link`；`prefetch` 只認字面的 `{false}`（`{cond ? false : null}` 會被當成沒關）；括號計數不解析字串。新守門的偵測器有 13 個單元測試，真實專案掃描 4 條（掃描範圍、無違規、不失明下限 8 個、清單檔案存在）。

## 6. 突變驗證

驅動（`mutate.py`，備份→套用→跑→一律還原→SHA-256 核對；`old` 必須恰好出現一次，否則視為沒套上並停止）：**20 個突變，19 個被擊殺，1 個依預先登記倖存**；還原核對「全部與原檔一致」。

| 組 | 突變 | 結果 |
|---|---|---|
| G1～G10 | 對真實元件：文章卡片、`ProductCard`（href 是變數）、`StoreNav`、「返回部落格」（href 是識別字）、`prefetch={false}`→`{true}`、新增一個沒寫 prefetch 的 `/listings/` 連結、通知頁、橫幅「查看商品」、`storeBlogPath` 兩處 | 10／10 **擊殺**（都是「每個指向實體頁的 `<Link>` 都有 `prefetch={false}`」紅燈） |
| D1 | `isEntityHref` 網址不必在開頭（`/admin/stores/` 也算） | 擊殺（isEntityHref 單元測試） |
| D2 | 不認 `storeBlogPath` | 擊殺 |
| D3 | 屬性大括號不整段跳過（`=>` 讓標籤提早結束） | 擊殺 |
| D4 | `prefetch={true}` 也算已關閉 | 擊殺 |
| D5 | 不解析識別字 | 擊殺 |
| D6 | `<LinkButton` 也當成 `<Link` | 擊殺 |
| D7 | 行號少算 1 | 擊殺（2 個測試） |
| D8 | 偵測器什麼都找不到 | 擊殺（4 個測試，含「不失明下限」） |
| C1 | 共用元件清單指到不存在的檔案 | 擊殺 |
| C2 | 清單拿掉 `ProductCard`，同時 `ProductCard` 拿掉 `prefetch` | **倖存（預先登記）**：證明清單條目是必要的——沒有它，`ProductCard` 的退化不會被抓到 |

**說明**：偵測器的 13 個單元測試一寫好就是綠的（沒有「紅燈先行」的階段），它們的失敗能力靠 D1～D8 證明；真實專案那條是真的先紅（11 個違規）再綠。

## 7. 後置驗證

### 7.1 同一支探針，修改前→修改後

（`compare.py` 輸出；每次量測前清空限流桶；兩次都 0 個 429。瀏覽器端請求數不變，變的是 Next 伺服器端。）

| 量測 | 後端合計 前→後 | Next 伺服器 前→後 | 瀏覽器 前→後 | 預取 RSC 請求 前→後 |
|---|---|---|---|---|
| home:load | 3 → 1 | 2 → 0 | 1 → 1 | 22 → 18 |
| home:load+scroll | 3 → 1 | 2 → 0 | 1 → 1 | 22 → 18 |
| platform-blog:load | 17 → 1 | 16 → 0 | 1 → 1 | 28 → 16 |
| platform-blog:load+scroll | 25 → 1 | 24 → 0 | 1 → 1 | 34 → 18 |
| store-home:load | 6 → 3 | 4 → 1 | 2 → 2 | 26 → 20 |
| store-home:load+scroll | 6 → 3 | 4 → 1 | 2 → 2 | 26 → 20 |
| store-products:load | 7 → 3 | 5 → 1 | 2 → 2 | 28 → 20 |
| store-products:load+scroll | 7 → 3 | 5 → 1 | 2 → 2 | 28 → 20 |
| store-blog:load | 27 → 4 | 24 → 1 | 3 → 3 | 36 → 16 |
| store-blog:load+scroll | 31 → 4 | 28 → 1 | 3 → 3 | 42 → 20 |
| article:load | 10 → 6 | 6 → 2 | 4 → 4 | 25 → 17 |
| article:load+scroll | 10 → 6 | 6 → 2 | 4 → 4 | 28 → 20 |
| listing:load | 2 → 2 | 1 → 1 | 1 → 1 | 22 → 22 |
| listing:load+scroll | 2 → 2 | 1 → 1 | 1 → 1 | 22 → 22 |
| journey（店鋪首頁→貼文分頁→第一篇文章→上一頁） | 39 → 16 | 27 → 4 | 12 → 12 | 40 → 20 |

加總（六個頁面的 `:load`＋journey）：後端合計 **111 → 36**；其中 Next 伺服器 **85 → 10**。剩下的預取請求（16～22 個）目標都是 `/login`、`/register`、`/dashboard`、`/notifications` 這類**不打後端**的路由（修改後 `/blog`、首頁的伺服器端請求是 0）。

### 7.2 標題與延遲

- **標題 8／8 正確**（修改後建置，取最終穩定值）：完整載入列表；點文章卡片；點「返回部落格」；瀏覽器上一頁（回到文章）；再上一頁；下一頁；點店鋪分頁「商品」；點商品卡到商品詳情。
- 點擊延遲見 §4.5（修改後預設 357 ms）。

### 7.3 四支真實規格（單獨跑、每支前清桶；基準＝`HEAD` 重建，同一套量測）

| 規格 | 後端合計 | 匿名需求（吃 IP 桶） | 其中 Next 伺服器 | 429 | 案例 |
|---|---|---|---|---|---|
| `at-store-blog-category-real`（Sprint 337，DEF-499 的失敗規格） | 162 → 84 | **119 → 41** | 92 → 14 | **5 → 0** | 6／6 → 6／6 |
| `at-platform-blog-real`（Sprint 339） | 153 → 87 | 92 → 26 | 68 → 2 | 0 → 0 | 5／5 → 5／5 |
| `at-article-titles-real` | 112 → 95 | 70 → 53 | 42 → 25 | 0 → 0 | 7／7 → 7／7 |
| `at-storefront-real` | 61 → 47 | 35 → 21 | 22 → 7 | 0 → 0 | 5／5 → 5／5 |
| 合計 | 488 → 313 | **316 → 141** | **224 → 48** | 5 → 0 | 23／23 → 23／23 |

「匿名需求」＝沒帶 `Authorization`、不是 `/v2/auth/*`、`/actuator/*`（`RateLimitFilter` 不計）、不是 OPTIONS 的請求，也就是會吃每來源 IP 桶（容量 100、每分鐘補滿）的需求。**`DEF-499` 的原始症狀第一次被直接重現**：`at-store-blog-category-real` 在 `HEAD` 單獨跑、桶是滿的，21 秒內就要 119 個匿名請求，超過桶容量 100，**5 個請求被 429**；它之所以還是「通過」，只是那 5 個被擋的請求剛好落在不影響斷言的預取上——再多一個並行規格（本機守門是 2～4 個 worker 共用同一個來源 IP）就會變成失敗。修改後 41 個、0 次 429。

### 7.4 本機完整驗證

| 項目 | 結果 |
|---|---|
| 前端單元測試（Vitest） | **37 檔 374 案例全過**（Sprint 339：36 檔 359；+1 檔＝新守門、+15 案例） |
| `npm run lint`（`--max-warnings 0`） | 通過（退出碼 0） |
| `tsc --noEmit` | 通過（退出碼 0） |
| 完整本機 E2E 守門（`make validate-e2e`） | **389／389 全過（9.0 分）**；守門統計的認證端點被限流 0 次（Sprint 339：389／389、9.6 分；這輪沒有新增或刪除 E2E 案例，案例數相同） |
| 後端單元／整合測試 | **未執行**：這輪沒有動後端程式（`git diff` 只有前端與文件）；pre-commit 的核心測試與雲端 CI 會跑 |
| 守門收尾 | 埠全部釋放、0 個容器；守門留下 2 個匿名卷（建立於 13:04:46～47，帶 `com.docker.volume.anonymous` 標籤），已逐一刪除；`docker volume ls -f dangling=true` 回到只剩 5 個既有具名卷 |

### 7.5 push 與雲端 CI

（待填）

## 8. 行為變更與已知限制

### 8.1 行為變更

| 項目 | 之前 | 現在 | 影響誰 |
|---|---|---|---|
| 指向店鋪頁、貼文、商品／房源詳情的連結 | 進入視窗就預取（正式建置） | **不預取**（進入視窗與 hover 都不） | 所有訪客：點擊後才向伺服器要頁面（本機量到的點擊延遲沒有可量測的差異，§4.5） |
| 一次瀏覽的 Next 伺服器→後端請求 | 店鋪貼文列表 24～28 個、`/blog` 16～24 個 | 店鋪貼文列表 1 個、`/blog` 0 個 | 後端與每來源 IP 限流桶 |
| 預取的 `/_tree` 與 metadata-only 請求 | 每個連結 2 個 | 這 11 個連結 0 個（其餘連結不變） | Next 伺服器的渲染負載 |

### 8.2 已知限制（誠實列出）

1. **只在本機量測**（localhost、4 邏輯核心機器、負載 3～7）。**真實網路下關閉預取對點擊延遲的影響沒有量**；這輪的結論只到「本機沒有可量測的差異」。
2. **預取請求數取決於視窗內的連結數**（探針用桌面 Chrome 預設視窗）；手機視窗連結較少、預取較少，沒有量。
3. **瀏覽器端的請求沒動**：每次瀏覽 1～4 個，那趟典型瀏覽 12 個，仍吃每位訪客自己的來源 IP 桶（同一個出口 IP 的多位訪客仍共用）。`StoreLayout` 每個頁面重新載入一次店鋪資料等，是另一個可以省請求的地方，沒有動。
4. **Next 伺服器端的請求仍共用同一個來源 IP 的匿名桶（`DEF-487`）**：修改後每次完整載入 1～2 個、每次用戶端導覽 1～2 個（那趟瀏覽 27 → 4）。以每趟 4 個粗算，容量 100／分約是每分鐘 25 趟（修改前約 3.7 趟）——**這是用量測到的每趟請求數做的算術，不是壓力測試**。要不要把伺服器端請求排除在限流外、轉發訪客 IP，或對標題做短快取，是政策／部署決定，沒有動。
5. **正式部署有沒有反向代理，repo 內兩處描述不一致、沒有答案**（`DEF-501`）：若有，`getRemoteAddr()` 會是代理的 IP，所有匿名訪客共用一個桶。這是 Sprint 197／234 就提出過、至今沒有答覆的待決事項。
6. **守門只認字面的 `prefetch={false}`**，且只看 `<Link` 的 href 長相；新增指向實體頁的連結但用了別的元件或 `router.prefetch`，偵測不到。
7. **剩下的預取請求**（`/login`、`/register`、`/dashboard` 等，每頁 16～22 個）仍會讓 Next 伺服器渲染這些路由；它們不打後端，沒有處理。
8. **E2E 層面的證據有兩層**：四支規格單獨跑 23 個案例前後都過（429 只在基準那次出現 5 個，**不能推論基準每次都會失敗**）；完整守門 389／389 全過，但守門只統計「認證端點」的限流（0 次），**匿名 IP 桶在整個守門套件修改前後被擋了幾次沒有量**（腳本結束會刪後端日誌，修改前也沒有跑完整守門當對照），所以不能說「守門的間歇失敗從此消失」，只能說已知的最大放大來源被拿掉了。
9. **`DEF-500` 其餘四點沒動**：沒有篩選／搜尋、卡片不連到店鋪、大量貼文的耗時沒量、`/blog/{slug}` 舊網址導向頁仍是登入者專用。

## 9. 失誤與修正（誠實列出）

1. **先設計了一條走不通的路才讀 Next 的文件**：在 `generateMetadata` 偵測預取，改副本、重建（約 3 分鐘）、重播才發現標頭被剝掉（§4.4）。`frontend/AGENTS.md` 明說這個版本與一般認知不同；應該在動手前先讀 `node_modules/next/dist/docs/` 與相關原始碼。
2. **預測的盲點**：只數「頁面直接打幾個請求」，漏了預取（§3，與 Sprint 336 登記 `DEF-487` 時同一個盲點）。
3. **我說「沒有現成 JAR」是錯的**：`ls --time-style=full-iso` 是 GNU 選項，在 macOS 報錯，又被 `2>/dev/null` 吞掉，看起來像「沒有檔案」；其實 JAR 就在，時間（02:47）晚於後端最後一次原始碼變更（02:17），可以直接用。
4. **重啟代理第一次失敗**：`lsof -ti tcp:8081` 回兩個 pid（含連到 8081 的前端客戶端），zsh 不會把 `$pids` 拆開，`kill` 報 `illegal pid`，舊代理繼續跑；是靠「新日誌行沒有 `a` 欄位」才發現。改成只找**監聽中**的行程（`lsof -iTCP:8081 -sTCP:LISTEN`）。直接殺 `lsof -ti` 的結果也可能誤殺前端。
5. **`curl` 重播 RSC 請求第一次得到 307**：Next 會驗證 `_rsc` 快取破壞參數（對不上就 307 到正確值），我手打的 `_rsc=probe1` 不對；加 `-L` 才真的處理到那個請求。第一輪「RSC 請求後端 0 個」的結果因此是無效的，沒有採用。
6. **我先前對你說突變驗證是「22 個」，實際是 20 個**（G 10＋D 8＋C 2）；以驅動輸出為準。
7. **zsh 的 `${PIPESTATUS[0]}`** 在管線後是空的（zsh 叫 `pipestatus`），第一次型別檢查與 lint「沒輸出」不等於通過；改成不接管線直接取退出碼。
8. **S339 計畫書 §6.2 的說法要更正**：「不能說這輪讓它變多或變少」。現在知道成因是預取放大（Sprint 335 的 `generateMetadata` 起就存在，不是 Sprint 339 才有）；Sprint 339 新增的 `/blog`（視窗內 8 張卡片＝16 個、捲過 12 張＝24 個伺服器端請求／次瀏覽）與 5 個改過的規格增加了這類頁面的瀏覽次數，所以**不能說 Sprint 339 完全無關**——但相對 Sprint 338 增加多少沒有量化。已在 S339 計畫書 §6.2 加更正註記。

## 10. 追蹤表的變動

| 編號 | 變動 |
|---|---|
| `DEF-499` | 🟡→✅：**放大的部分已修**（§5、§7.3）；剩下的「共用桶的政策」併入 `DEF-487`／`DEF-501`。原文留作證據 |
| `DEF-487` | 補上實測：Next 伺服器端請求在修改前佔後端請求的 77%、修改後每次完整載入 1～2 個；**仍標登記、未修**（政策／部署決定） |
| `DEF-500` | ① 補上實測：平台 `/blog` 初始 HTML 的 12 張卡片 0 張、沒有 `og:title`；其餘不變 |
| `DEF-501`（新） | 正式部署拓撲：`next.config.ts` 假設 TLS 通常在前方的代理終止，`LoginRateLimitFilter` 假設沒有反向代理；來源 IP 限流（匿名 100／分、登入 30／分）在有代理時會變成全站共用一個桶。**需要使用者提供部署拓撲**，沒有修 |

## 11. 我替使用者做的決定（可一句話推翻）

1. **對所有指向實體頁的連結關閉預取**，不只是文章卡片：店鋪分頁、橫幅按鈕、商品卡、通知的「重新預訂」、文章頁的返回與分類連結也一併。理由：同一個放大機制（每個被預取的連結都讓伺服器跑 `generateMetadata`）、量到的預取好處是零。推翻方式：拿掉該連結的 `prefetch={false}`，並調整守門。
2. **選 `prefetch={false}`，不選這些替代做法**：①預取時回靜態標題（Next 剝掉標頭、偵測不到；改走 proxy 還有客戶端快取卡住錯誤標題的風險，§4.4）；②`generateMetadata` 的後端請求加短快取（與 Sprint 335「店名改了、停權要立刻反映」的設計衝突，且擋不住 12 篇不同文章的 12 個請求）；③把伺服器端請求排除在限流外或轉發訪客 IP（要動限流與部署設定，是政策決定）。
3. **新增架構守門並把它列為強制規則**（新增指向實體頁的 `<Link>` 時，不寫 `prefetch={false}` 測試就紅燈）。理由：這個放大潛伏了四個 Sprint。推翻方式：刪除 `entity-link-prefetch.test.ts` 與 `detect.ts` 新增的兩個函式。
4. **沒有動限流政策、沒有動後端**：`DEF-499` 的政策部分與 `DEF-487` 仍待決。
5. **把 `DEF-499` 結案，剩餘併入 `DEF-487`／新登記的 `DEF-501`**：`DEF-499` 的原症狀（E2E 規格被限流擊敗）已用同一套量測重現並消除；「同一個出口 IP 的訪客共用桶」是另一個問題。
6. **沒有為 SEO 做伺服器端渲染**：只把初始 HTML 的事實量出來（§4.6）。
7. **新登記 `DEF-501`**：部署拓撲不是我能決定的，但它決定這一族限流問題在正式環境的真實影響，不該只留在兩份舊計畫書裡。

## 12. 狀態

| 項目 | 狀態 |
|---|---|
| `DEF-499` 放大的量測與修復 | ✅ 已做並實測（§7.3：目標規格匿名需求 119 → 41、429 5 → 0） |
| `DEF-487` 伺服器端標題請求 | ✅ 已量測（修改前 77% 的後端請求來自 Next 伺服器）；政策部分仍未修 |
| `DEF-500` ① 初始 HTML | ✅ 已量測（平台 `/blog` 12 張卡片 0 張） |
| 新登記 | `DEF-501`（部署拓撲） |
| 程式 commit | `0da61125`（尚未 push，見 §7.5） |
| push 與雲端 CI | （待填） |

---

## 附錄 A：計數代理 `proxy.js`（全文）

```js
'use strict';
// Sprint 340 量測用計數代理：node proxy.js <監聽埠> <目標主機> <目標埠> <日誌檔>
// 原樣轉送到後端，每個請求寫一行 JSON（時間、方法、路徑、狀態、User-Agent）。
// /__mark/<標籤> 由代理自己處理（不轉送），用來在日誌裡劃出每一次量測的起訖。
const http = require('http');
const fs = require('fs');

const [, , listenPort, targetHost, targetPort, logFile] = process.argv;
const out = fs.createWriteStream(logFile, { flags: 'a' });
const log = (o) => out.write(JSON.stringify(o) + '\n');
const agent = new http.Agent({ keepAlive: true, maxSockets: 512 });

const server = http.createServer((req, res) => {
  const t = Date.now();
  if (req.url.startsWith('/__mark/')) {
    log({ t, mark: decodeURIComponent(req.url.slice('/__mark/'.length)) });
    res.writeHead(204, { 'Access-Control-Allow-Origin': '*' });
    res.end();
    return;
  }
  const ua = req.headers['user-agent'] || '';
  const upstream = http.request(
    { host: targetHost, port: Number(targetPort), method: req.method, path: req.url, headers: req.headers, agent },
    (up) => {
      log({ t, m: req.method, u: req.url, s: up.statusCode, ua, a: req.headers['authorization'] ? 1 : 0 });
      res.writeHead(up.statusCode, up.headers);
      up.pipe(res);
    }
  );
  upstream.on('error', (e) => {
    log({ t, m: req.method, u: req.url, s: 0, err: String(e), ua });
    if (!res.headersSent) res.writeHead(502);
    res.end();
  });
  req.pipe(upstream);
});
server.keepAliveTimeout = 65000;
server.listen(Number(listenPort), () => {
  console.log(`proxy listening ${listenPort} -> ${targetHost}:${targetPort}`);
});
```

## 附錄 B：探針規格（全文；臨時規格，只放在 scratchpad 的專案副本，不進 repo）

用法：把它放進前端副本的 `e2e/zz-s340-probe.spec.ts`，起全棧（前端與 Next 伺服器的 API 網址指向代理），`NEXT_PUBLIC_API_URL=http://localhost:8081/api E2E_BACKEND_LOG=<後端日誌> PROBE_OUT=<輸出 json> npx playwright test e2e/zz-s340-probe.spec.ts --workers=1`。

```ts
import { test, expect, Browser, Page, Request } from '@playwright/test';
import { execSync } from 'node:child_process';
import { writeFileSync } from 'node:fs';
import { authHeaders, seedStore } from './helpers/store';

/**
 * Sprint 340 量測探針（臨時規格：只放在 scratchpad 的專案副本，不進 repo）。
 * 瀏覽器與 Next 伺服器的後端請求都經過計數代理（8081），代理日誌用 start:／end:／step: 標記切出每一次量測。
 * 這支規格只量「匿名訪客」造訪公開頁時，一次瀏覽放大成多少個打到後端的請求。
 */

const API = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081/api';
const PROXY_ORIGIN = new URL(API).origin;
const WEB = 'http://localhost:3000';
const OUT = process.env.PROBE_OUT || '/tmp/s340-probe.json';
const STAMP = Date.now();
const POSTS = 13;

interface BrowserReq {
  t: number;
  kind: 'api' | 'rsc-prefetch' | 'rsc-nav' | 'doc';
  method: string;
  path: string;
}
interface Measurement {
  label: string;
  ms: number;
  load: string;
  browser: BrowserReq[];
}
const results: Measurement[] = [];

const mark = async (label: string) => {
  await fetch(`${PROXY_ORIGIN}/__mark/${encodeURIComponent(label)}`);
};
// 每次量測前清掉所有限流桶，讓每次量測互不影響（只量需求量，不受上一次殘留的額度影響）
const resetBuckets = () =>
  execSync(`docker exec s340-redis sh -c "redis-cli --scan --pattern 'ratelimit:*' | xargs -r redis-cli del"`).toString().trim();

function classify(req: Request): BrowserReq | null {
  const url = new URL(req.url());
  const t = Date.now();
  if (url.origin === PROXY_ORIGIN && url.pathname.startsWith('/api/')) {
    return { t, kind: 'api', method: req.method(), path: url.pathname };
  }
  if (url.origin === WEB && url.searchParams.has('_rsc')) {
    const prefetch = Object.keys(req.headers()).some((k) => k.includes('prefetch'));
    return { t, kind: prefetch ? 'rsc-prefetch' : 'rsc-nav', method: req.method(), path: url.pathname };
  }
  if (req.resourceType() === 'document') return { t, kind: 'doc', method: req.method(), path: url.pathname };
  return null; // 靜態資源不計
}

async function measure(
  browser: Browser,
  label: string,
  run: (page: Page, step: (name: string) => Promise<void>) => Promise<void>
) {
  resetBuckets();
  const ctx = await browser.newContext();
  const page = await ctx.newPage();
  const browserReqs: BrowserReq[] = [];
  page.on('request', (r) => {
    const c = classify(r);
    if (c) browserReqs.push(c);
  });
  const load = execSync('uptime').toString().trim().replace(/^.*load averages?: /, '');
  await mark(`start:${label}`);
  const t0 = Date.now();
  await run(page, (name) => mark(`step:${label}:${name}`));
  await mark(`end:${label}`);
  results.push({ label, ms: Date.now() - t0, load, browser: browserReqs });
  await ctx.close();
}

const settle = async (page: Page, ms: number) => {
  await page.waitForLoadState('networkidle', { timeout: 20_000 }).catch(() => {});
  await page.waitForTimeout(ms);
};
const scrollThrough = async (page: Page) => {
  for (let i = 0; i < 10; i++) {
    await page.evaluate(() => window.scrollBy(0, Math.round(window.innerHeight * 0.8)));
    await page.waitForTimeout(350);
  }
};

test('S340 匿名訪客的請求放大倍率', async ({ page, browser }) => {
  test.setTimeout(25 * 60_000);

  // ---- 種資料（真實流程）：一間店鋪（商品＋房源）＋ 13 篇已發布貼文（內文含內嵌商品卡）----
  const seeded = await seedStore(page, {
    businessType: 'HYBRID',
    storeLabel: 'S340探針',
    room: { name: `探針房間${STAMP}`, price: 2000 },
    product: { name: `探針商品${STAMP}`, price: 300 },
  });
  const headers = await authHeaders(page);
  const postSlugs: string[] = [];
  for (let i = 0; i < POSTS; i++) {
    const r = await page.request.post(`${API}/v2/dashboard/posts`, {
      headers,
      data: {
        title: `探針文章${String(i).padStart(2, '0')}-${STAMP}`,
        content: `第 ${i} 篇。推薦商品 {{embed:listing:${seeded.productId}}} 與房型 {{embed:listing:${seeded.roomId}}}`,
        autoPublish: true,
      },
    });
    expect(r.status(), await r.text()).toBe(200);
    postSlugs.push((await r.json()).data.slug as string);
  }
  const slug = seeded.storeSlug;
  const newestPost = postSlugs[postSlugs.length - 1];
  const seed = { slug, productId: seeded.productId, roomId: seeded.roomId, postSlugs, stamp: STAMP };
  writeFileSync(OUT.replace(/\.json$/, '.seed.json'), JSON.stringify(seed, null, 1));

  // ---- 每個公開頁面：完整載入（不捲動）、完整載入＋捲過整頁 ----
  const targets: Array<{ name: string; path: string }> = [
    { name: 'home', path: '/' },
    { name: 'platform-blog', path: '/blog' },
    { name: 'store-home', path: `/stores/${slug}` },
    { name: 'store-products', path: `/stores/${slug}/products` },
    { name: 'store-blog', path: `/stores/${slug}/blog` },
    { name: 'article', path: `/stores/${slug}/blog/${newestPost}` },
    { name: 'listing', path: `/listings/${seeded.productId}` },
  ];
  for (const target of targets) {
    await measure(browser, `${target.name}:load`, async (p) => {
      await p.goto(WEB + target.path, { waitUntil: 'load' });
      await settle(p, 6000);
    });
    await measure(browser, `${target.name}:load+scroll`, async (p) => {
      await p.goto(WEB + target.path, { waitUntil: 'load' });
      await settle(p, 1500);
      await scrollThrough(p);
      await settle(p, 5000);
    });
  }

  // ---- 一趟典型瀏覽：店鋪首頁 → 點「貼文」分頁 → 點第一篇文章 → 上一頁（都是用戶端導覽）----
  await measure(browser, 'journey:store-home>blog>article>back', async (p, step) => {
    await p.goto(`${WEB}/stores/${slug}`, { waitUntil: 'load' });
    await settle(p, 3000);
    await step('click-blog-tab');
    await p.locator(`a[href="/stores/${slug}/blog"]`).first().click();
    await p.waitForURL(`**/stores/${slug}/blog`);
    await settle(p, 3000);
    await step('click-article');
    await p.locator(`a[href^="/stores/${slug}/blog/"]`).first().click();
    await p.waitForURL(`**/stores/${slug}/blog/*`);
    await settle(p, 3000);
    await step('back');
    await p.goBack();
    await settle(p, 3000);
  });

  writeFileSync(OUT, JSON.stringify({ seed, results }, null, 1));
});
```

## 附錄 C：對齊與分類規則（`compare.py`／`spec-demand.py` 的做法）

- 每次量測在代理日誌裡用 `start:<標籤>`／`end:<標籤>` 兩個標記切出時間窗；窗內的請求就是那次量測的後端請求。
- 來源分類：`User-Agent` 含 `Chrome` ＝瀏覽器；其餘（`node`）＝Next 伺服器（規格腳本裡自己的 API 呼叫是 `Playwright` UA，在 §7.3 歸「瀏覽器／測試」）。
- 「匿名需求」＝沒有 `Authorization`（代理記 `a:0`）、路徑不是 `/v2/auth/*` 與 `/actuator/*`、方法不是 `OPTIONS`。
- 交叉核對：每次量測，Playwright 看到的瀏覽器→後端 API 請求數必須等於代理日誌裡瀏覽器 UA 的非 OPTIONS 請求數（15 次全部一致）。
- 預取請求＝瀏覽器送出、網址帶 `_rsc`、且請求標頭含 `prefetch` 字樣（`next-router-prefetch`、`next-router-segment-prefetch`）。
- 路徑正規化：UUID→`{id}`；`/v2/public/stores/<slug>`→`/v2/public/stores/{slug}`；`/v2/posts/<slug>`→`/v2/posts/{slug}`。
