# Sprint 341 計畫書：全專案單一的「反向代理層數」參數（`DEF-501`）、伺服器端請求的限流歸屬政策（`DEF-487`）、公開頁 SEO 伺服器端渲染（`DEF-500` ①）

> **日期**：2026-11-12
> **性質**：後端（新增 `ClientIpResolver`、一個參數）＋前端（所有公開頁改為伺服器端取首份資料；`robots.txt`／`sitemap.xml`／結構化資料／真正的 404／noindex）＋測試與守門（前端 Vitest、後端單元＋真 Redis 整合、真實後端 E2E、架構守門）＋文件
> **依據**：使用者在 Sprint 340 總結中以 `==>` 回覆三件事（見 §1.1）——**這次有明確的使用者回覆，不是對「請繼續」的推論**
> **前置**：[SPRINT_340_PLAN.md](SPRINT_340_PLAN.md)（`DEF-499` 的放大修復、`DEF-487`／`DEF-500` ① 的實測、`DEF-501` 的登記）、[SPRINT_339_PLAN.md](SPRINT_339_PLAN.md)（跨店鋪 `/blog`）、[SPRINT_336_PLAN.md](SPRINT_336_PLAN.md)（文章頁的瀏覽計數決定）、[SPRINT_335_PLAN.md](SPRINT_335_PLAN.md)（伺服器端標題）、[SPRINT_234_PLAN.md](SPRINT_234_PLAN.md) §6.4／[SPRINT_197_PLAN.md](SPRINT_197_PLAN.md)（部署拓撲的提問）
> **需要你知道的事**：①部署時**沒有代理就什麼都不用設**（預設行為與先前完全相同）；②有代理時**只有一個數字要設**（`APP_TRUSTED_PROXY_HOPS`），說明在 `.env.example`、`application.yml`、`docs/08_deployment/ENVIRONMENT_VARIABLES.md` §2.5.1；③**公開頁的首份資料現在由 Next 伺服器取**（不再是瀏覽器），所以規格裡靠 `page.route` 模擬資料的案例改在「SSR 不可用」專案執行（§5.6）；④公開頁改成伺服器端渲染之後，**沒有代理時**全站的伺服器端請求共用同一個匿名桶（§8.2 第 1 點）——上線前設代理與這個數字是實質的建議，不只是文件

## 1. 背景與授權

### 1.1 使用者的回覆（原文）

Sprint 340 總結列了待決事項，使用者以 `==>` 回覆：

1. **`DEF-501`（部署拓撲）**：「目前沒有反向代理／負載平衡器、不知道TLS 在哪終止，請幫我規劃若有時可以最容易設置的參數設計，全專案應該只有一個參數設計，請規劃實作並在參數檔有說明！」
2. **`DEF-487` 政策與 `DEF-500` SEO**：「請做全部SEO 伺服器」。
3. 通則：「以上若需要我決定，請依照最合理化，最佳化，最理想，最適當的解決方幫我決策(不需要顧慮成本)，若真需要我決定，請以最簡單的解釋，讓我互動選項決策！」

我的解讀（**可一句話推翻**）：
- 第 1 點＝新增**一個**參數，預設值對應「現在沒有代理」，有代理時只要改這個值；全專案決定來源 IP 的地方只能有一處；說明寫進參數檔。實作了（§2.1、§5.1）。
- 第 2 點的「全部 SEO 伺服器」＝**所有公開頁**（首頁、商品、房源、店鋪、部落格、商品詳情）的首份資料都由伺服器取、隨第一份 HTML 送出；`DEF-487` 的政策依第 3 點由我決定（§2.3）。我把與這件事直接相關、一般認為是 SEO 基本盤的東西一併做了：真正的 404（不是 200 加「找不到」畫面）、`robots.txt`、`sitemap.xml`、結構化資料（JSON-LD）、標準網址（canonical）、私人區域 `noindex`、分頁改成真正的連結（§5.2～§5.5）。
- 第 3 點＝這輪沒有需要你做的選擇題：兩個決定點（參數的形式、`DEF-487` 的政策）我依「最合理、最理想」選了並寫下理由（§2、§11），沒有用互動選項問你。

### 1.2 Docker 政策的合規

沒有動 `Dockerfile`、image、tag、volume、`profiles`、healthcheck、`deploy.resources`、Redis 設定。**唯一的 Docker 相關異動**：`docker-compose.yml` 的 `backend` 服務 `environment` 新增一行 `APP_TRUSTED_PROXY_HOPS=${APP_TRUSTED_PROXY_HOPS:-0}`——這是你明確指示規劃的那個參數，要讓 compose 部署也能設它就必須傳遞（與 Sprint 244 補傳 `APP_*` 變數同型，預設 0＝行為不變）。驗證用的 PostgreSQL（`postgres:18-alpine`）與 Redis（`redis:7-alpine`）都在 `DOCKER_POLICY.md` 核准清單內，用完以 `docker rm -fv` 清除；結尾的 Docker 清理見 §7.6。

### 1.3 `frontend/AGENTS.md` 的提醒

這次**動手前先讀了** `node_modules/next/dist/docs/`（`headers()`、`notFound()`＋`not-found.js`、`loading.js` 的狀態碼章節、JSON-LD 指南、`sitemap.xml`／`robots.txt`、`generateMetadata` 的 `alternates`）與 Next 原始碼（`base-server.js`、`app-render.js`）。Sprint 340 是先試了再讀、重建才發現走不通（該輪 §9 第 1 點）；這次的幾個關鍵事實都是動手前讀到的（§4.1、§4.4），也因此避開了「sitemap 預設在 `next build` 時執行並烤進建置產物」這個會讓正式站 sitemap 永遠是空的陷阱（§5.4）。

## 2. 設計與決定

### 2.1 `DEF-501`：一個參數 `APP_TRUSTED_PROXY_HOPS`（代理層數）

**意思**：訪客的請求抵達後端之前要經過幾層反向代理／負載平衡器／CDN。預設 `0`（沒有代理，與先前行為完全相同）；`1`＝一層（nginx、Traefik、Caddy、ALB…）；`2`＝兩層（例如 Cloudflare＋nginx）；範圍 0～10，超出或不是數字**啟動就失敗**（不是默默退回 0 讓操作者以為生效了）。

**怎麼決定來源 IP**（全專案唯一的一處：`api/filter/ClientIpResolver`）：
- `0`：TCP 對端位址，**完全不看** `X-Forwarded-For`（直連者可以自己填，採信就能繞過限流）。
- `N ≥ 1`：取 `X-Forwarded-For` **由右數第 N 項**（每一層代理都把它看到的對端位址附加在最右邊）；左邊更多的項目是訪客自己填的，一律不採信。項目比層數少、或選到的項目不是合法 IP（`unknown`、`_hidden`、任意文字、帶 `%` 的區域識別…）→ 退回對端位址（安全的一邊），並只印一次警告。
- IPv6 正規化（縮寫、補零、大小寫、IPv4 對應位址 `::ffff:1.2.3.4` 都得到同一個字串，同一位訪客不會因為寫法不同分到不同的桶）；IPv4 自己嚴格解析（不用 `InetAddress.getByName`：它接受 `127.1`、`0x7f.1` 這類簡寫，而且對非位址字串會做 DNS 查詢）。

**為什麼是「層數」，不是「代理的 IP 清單」，也不是多個開關**
1. 操作者一定知道「前面有幾層」；不一定知道代理的位址（雲端負載平衡器與 CDN 的位址會變、很難查）。這是「若有時最容易設置」的關鍵。
2. 我先想了 CIDR 清單（Spring 的 `internal-proxies` 那一類）：它能擋掉「直連者偽造標頭」，但本專案有**兩條**通往後端的路——「訪客→代理→後端」與「訪客→代理→**Next**→後端」（Next 伺服器端渲染）。CIDR 要讓兩條路都對，Next 也得知道哪些位址可信，就會有**第二個設定**，違反「全專案只有一個」。層數加上「Next 把收到的 `X-Forwarded-For` **原封不動**帶給後端（不附加、不改寫）」，兩條路到後端都是同樣的 N 項，**同一個數字兩條路都對，前端沒有對應的設定**。
3. TLS 在哪終止**不需要設定**：代理只要送 `X-Forwarded-Proto: https`，後端（`SecurityHeaderPolicy`）與前端（`next.config.ts`）就會送 HSTS——這兩處本來就採信該標頭（偽造它只會讓 http 回應多帶一個瀏覽器會忽略的標頭，Sprint 198 的決定）。所以「不知道 TLS 在哪終止」對你不是阻礙。

**代價與前提（寫進所有參數說明，§4.5 用真實全棧實測證明）**：設成 1 以上時，①代理必須**附加**而不是覆寫 `X-Forwarded-For`；②後端 8080 與前端 3000 **不能有繞過代理的直連入口**——直連者可以自己填標頭假冒任何 IP（實測：`H=1` 且直連後端、每次換一個偽造值，130 次請求 0 次被限流）。所以預設是 `0`，不是 `1`。

### 2.2 一個意外的事實：Next 自己會補 `X-Forwarded-For`

Next 在收到請求時，如果請求**沒帶** `x-forwarded-for`，會用 TCP 對端位址補上（`next/dist/server/base-server.js:567`，`req.headers['x-forwarded-for'] ??= socket.remoteAddress`）。所以**即使沒有任何代理**，Next 伺服器端也「知道」訪客位址（實測 `::ffff:127.0.0.1`）。但後端在 `H=0` 時刻意不採信這個標頭（直連的訪客可以自己填，Next 不會覆蓋已存在的值），所以沒有代理時伺服器端請求仍歸在 Next 自己的位址——要把它們算到訪客自己的桶，必須有代理＋設層數。這一點寫在 §8.2。

### 2.3 `DEF-487`：伺服器端請求的限流歸屬——選「轉發訪客位址」

三個候選（追蹤表登記的）：

| 候選 | 做法 | 結論 |
|---|---|---|
| 排除伺服器端請求出限流 | 後端辨認出 Next 的請求就不計匿名桶 | ❌ **移除保護**：沒有可靠的辨認方式（標頭可偽造、網路位置在 Docker Desktop／NAT 下全是私有位址），而且攻擊者能用「不停重新整理公開頁」讓後端無限被打（每次頁面載入＝1～3 個後端請求，沒有任何限流） |
| 標題短快取 | 伺服器端請求加幾秒快取 | ❌ 不解決歸屬問題；與 Sprint 335「店名改了、停權要立刻反映」的設計衝突；本機 E2E 守門 389 個案例大量「建資料→立刻開頁」，短快取會讓它們間歇失敗 |
| **轉發訪客位址** | Next 把收到的 `X-Forwarded-For` 原封不動帶給後端，後端依 §2.1 算進訪客自己的桶 | ✅ 選這個：限流的歸屬正確（訪客自己的行為算在自己頭上）、不移除任何保護、不需要快取，而且**與 §2.1 是同一個機制**（不是第二個設定） |

我另外做了兩件讓這個政策更穩的事：**同一個請求內相同路徑只打一次後端**（React `cache`，`generateMetadata`、layout、頁面本體常要同一份資料；§4.2 實測每個頁面的請求數）；**伺服器端取不到資料時退回瀏覽器自己載入**（頁面不會因為被限流或後端暫時壞了就壞掉，§2.4）。

**誠實的限制**：沒有代理時（現況、本機、CI），伺服器端的請求仍共用 Next 伺服器位址的那一個匿名桶（每分鐘 100 次）。**公開頁改成伺服器端渲染等於把「訪客自己的瀏覽器打的請求」搬到這個共用的桶**——全站約每分鐘 33～100 次頁面瀏覽（每頁 1～3 個後端請求）。這是現況下沒有答案的問題，上線前設代理與 `APP_TRUSTED_PROXY_HOPS` 才會解決（§4.5 實測：設了之後 A 訪客打滿自己的桶，B 訪客不受影響）。登記為 `DEF-502`（§10）。

### 2.4 公開頁 SEO 伺服器端渲染的架構

**資料流**：server `page.tsx` 用 `fetchPublic`（`lib/server-api.ts`）取首份資料 → 以 `initial*` 屬性傳給既有的用戶端元件 → 元件把它交給 `useAsyncData` 的新選項 `initialData`（首次渲染 `loading=false`、**不再請求**；依賴改變或 `reload()` 照常請求）。伺服器端沒取到（逾時、429、5xx、非 JSON）就不傳，元件退回**與先前完全相同**的瀏覽器端載入。換頁／換篩選時 server page 以 URL 條件為 `key`，元件重新掛載、直接用新一頁的伺服器資料。

**結果分三種**（`PublicFetchResult`）：成功、**後端明確回 404（不存在）**、其餘一切失敗（不可用）。分開是 SEO 的要求：不存在的頁面要回真正的 404（否則搜尋引擎把「找不到」畫面當成一般內容收錄，即 soft 404），但**後端只是暫時壞了（429、5xx、逾時）絕不能回 404**——那會讓搜尋引擎把好好的頁面除名。

**真正的 404**：`notFound()`。Next 的行為（§4.4 實測）：狀態碼 404、自動加 `<meta name="robots" content="noindex">`；**但回應的 HTML 是空殼**（畫面由瀏覽器端渲染，Next 設計如此；`loading.js` 的文件明說「串流開始後狀態碼固定 200」，本專案沒有 `loading.tsx`，所以 404 狀態碼有效）。對搜尋引擎重要的是狀態碼與 noindex，兩者都對。「店鋪存在嗎」**只由 `stores/[slug]/layout.tsx` 判定**，各頁用不丟例外的 `peekStoreProfile`——我先讓頁面也自己判定，結果文章頁的 `not-found`（找不到文章）把「店鋪不存在」誤說成「找不到文章」（兩個 `notFound()` 同時拋，被離頁面最近的邊界接走）；這一條現在有架構守門（§5.7）。

**文章瀏覽數**：伺服器端取文章一律帶 `countView=false`（爬蟲與連結預覽不是一次瀏覽，Sprint 336 的決定），**瀏覽器載入頁面時才計一次**並把畫面上的數字更新成含本次的值——顯示的數字與以前一樣包含這一次，後端的瀏覽數不會被爬蟲灌水（E2E 驗證兩件事）。

**分頁是真正的連結**：`Pagination` 新增 `hrefFor`，公開列表（首頁、商品、房源、店鋪商品／房源／貼文、平台部落格）的頁碼、上一頁、下一頁渲染成 `<a href>`（`prefetch={false}`，Sprint 340 的教訓：每個預取＝伺服器多渲染一頁）。分類與排序仍是按鈕：分類篩選頁是同一批貼文的子集合、排序只是另一種排列，爬蟲沿著完整列表的分頁連結就走得到全部內容。

**hydration 的決定性**（審查時發現，§4.6）：內容改由伺服器渲染之後，凡是輸出依賴時區、語系或「現在」的程式，伺服器（容器通常是 UTC、預設語系 en-US）與訪客瀏覽器（台灣 UTC+8、zh-TW）算出的字串會不同，React 就丟掉伺服器的 HTML 重新渲染。修了三處：日期固定用台灣時區（`formatPublicDate`）、文章內嵌卡片的價格指定 `zh-TW`、房源整月日曆（第一個畫面依賴「現在」）只在瀏覽器渲染（`useIsClient`）。這類缺陷在開發機上永遠看不到（伺服器與瀏覽器同一台機器、同一個時區），也不會有型別或編譯訊號——所以有架構守門（§5.7）。

### 2.5 測試策略

- **契約測試**（`lib/public-api.test.ts`）：實際呼叫瀏覽器端的 services、攔下它們送出的請求，與伺服器端組出的路徑逐項比對——兩邊各自組網址，哪天有人改了其中一邊，伺服器端渲染出的第一頁就會和瀏覽器之後載入的不一致，而且沒有任何型別或單元測試的訊號。
- **E2E 用「不執行 JavaScript」的 HTTP 請求取頁面**（`at-seo-ssr-real.spec.ts`）：用瀏覽器的話，JavaScript 會補齊內容，規格會假綠燈。
- **「SSR 不可用」Playwright 專案**（§5.6）。
- **架構守門**（`architecture/public-ssr.test.ts`）：新增公開頁卻忘了伺服器端取資料、分頁退回按鈕、頁面自己判定店鋪存在、私人區域漏了 `noindex`、輸出依賴環境，都會紅燈。
- **突變驗證**：後端 10 個、前端 23 個（§6）。

## 3. 方法

1. **動手前先讀 Next 文件與原始碼**（§1.3），把決定「會不會走冤枉路」的事實先弄清楚：`notFound()` 的狀態碼與 HTML、`loading.js` 對狀態碼的影響、`sitemap.ts` 的快取、`headers()` 是請求時 API、Next 自己補 `X-Forwarded-For`。
2. **後端**：單元（Mockito＋`MockHttpServletRequest`，涵蓋偽造標頭、層數不符、IPv6、無效值）→ 真 Redis 整合（`make test-db-up` 的 Redis，驗證「代理後的訪客共用一個桶」這個要修的問題本身，以及設了層數之後各自一桶）→ Spring 設定接線測試（真正的 `application.yml`＋環境變數名稱）→ 突變驗證。
3. **前端逐頁驗證**：每個頁面改完，用**假後端**（記錄每個請求的 `X-Forwarded-For`；可模擬 404／500／429，附錄 A）＋**正式建置** `next start`＋**不執行 JavaScript 的 HTTP 請求**看第一份 HTML、狀態碼、`<title>`、`robots`、`canonical`、JSON-LD，以及後端日誌裡每個頁面打了幾個請求；再用**真瀏覽器**看 hydration 有沒有錯誤、點分頁連結是不是用戶端導覽、瀏覽器對後端發了哪些請求。（專案副本用 APFS clone，不弄髒 repo；開發伺服器在 `rsync` 之後會留舊模組，見 §9 第 3 點，所以結論都以正式建置為準。）
4. **E2E**：完整本地守門（真實後端）。**先跑一輪找出受影響的規格**（385 通過、5 失敗、9 未執行），逐一確認是行為刻意改變造成的過時預期再修正，最後再跑一輪。
5. **突變驗證**（後端 10 個、前端 23 個）與**真實全棧實測**（§4.5）。

## 4. 實測與發現

### 4.1 Next 自己會補 `X-Forwarded-For`

請求沒帶 `x-forwarded-for` 時，Next 用 TCP 對端位址補上（`base-server.js:567`）。實測：對 Next 發一個沒帶標頭的請求，假後端收到的 `X-Forwarded-For` 是 `::ffff:127.0.0.1`（Node 的 IPv4 對應位址寫法；`ClientIpResolver` 會把它與直接送 IPv4 的同一訪客正規化成同一個字串）。訪客自己帶的值 Next 不覆蓋、原樣保留。

### 4.2 `React.cache` 把 `generateMetadata`、layout、頁面的相同請求合併

Sprint 340 實測：`fetchPublicData` 因為帶 `signal`（逾時）讓 Next 的 fetch 記憶化失效，標題請求不被合併。改用 `React.cache` 包整個抓取函式、路徑逐字相同之後，假後端日誌顯示每個頁面的請求數就是 §5.3 表裡的數字（例如 `/stores/demo` 是 2 個：店鋪＋精選，不是店鋪 3 次＋精選）。

### 4.3 瀏覽器端的請求（真瀏覽器、正式建置）

| 動作 | 瀏覽器對後端的請求 |
|---|---|
| 載入 `/blog`（第一份 HTML 已有 2 張卡片） | **0** |
| 點分頁連結「2」（用戶端導覽到 `/blog?page=2`，資料由 RSC 帶來） | **0** |
| 載入文章頁 | **1**：`GET /v2/posts/{slug}?tenantId=…`（沒有 `countView=false`＝那一次真正的瀏覽） |
| 載入商品詳情 | **0** |
| 店鋪商品頁點「下一頁」 | **0** |

console 沒有 hydration 錯誤（唯一的錯誤是我的假後端沒有 CORS 標頭，使「計瀏覽」的請求失敗——失敗被靜默吞掉、頁面不受影響，這正是設計）。

### 4.4 `notFound()` 與後端暫時不可用（正式建置）

| 情境 | HTTP | `noindex` | 第一份 HTML | 瀏覽器 hydration 之後 |
|---|---|---|---|---|
| `/stores/gone`（店鋪不存在） | **404** | ✓ | 空殼；`<title>` 找不到店鋪｜… | `store-notfound` 畫面 |
| `/stores/demo/blog/missing`（店鋪好、文章不存在） | **404** | ✓ | 空殼；`<title>` 找不到文章｜… | `store-article-notfound` 畫面 |
| `/stores/gone/blog/x`（店鋪不存在） | **404** | ✓ | 空殼；`<title>` 找不到文章｜…（最深那層 `not-found.tsx` 的 metadata） | `store-notfound` 畫面（layout 判定，§2.4） |
| `/listings/{不存在的 UUID}` | **404** | ✓ | 空殼；`<title>` 找不到商品｜… | `listing-notfound` 畫面 |
| `/listings/not-a-uuid` | **404**（**0 個後端請求**） | ✓ | 同上 | 同上 |
| 後端回 500 或 429（店鋪頁） | **200**（不是 404） | — | 載入中骨架（`store-loading`） | 瀏覽器自己載入 |

**第一份 HTML 是空殼是 Next 的設計**（`app-render.js` 的 `getErrorRSCPayload` 回 `<html id="__next_error__">`，畫面由瀏覽器渲染）；狀態碼與 `noindex` 都對，對搜尋引擎足夠（§8.2 第 3 點）。我一開始用 `curl` 數「找不到這間店鋪」出現幾次判斷畫面有沒有出現，被 RSC payload 裡各層 `notFound` 邊界的元素字串誤導，改用真瀏覽器才確認（§9 第 4 點）。

### 4.5 真實全棧實測：`APP_TRUSTED_PROXY_HOPS` 在 0 與 1 的差別

全新的 PostgreSQL＋Redis 容器、後端 JAR（Flyway 從零建表）、Next 正式建置；用 HTTP 請求自己帶 `X-Forwarded-For`（等同代理附加的那一項）。腳本見附錄 B。

| 情境 | `H=0`（現況） | `H=1` |
|---|---|---|
| A 訪客一口氣 300 次 `/products`（16 條並行）：伺服器端取到資料／被限流而退回骨架 | 111／189 | 111／189 |
| 緊接著 **B 訪客**（另一個位址）10 次：取到資料 | **0／10** | **10／10** |
| 直連後端 130 次、**每次換一個偽造的** `X-Forwarded-For`：被限流 | 29（標頭沒被採信） | **0（偽造繞過了限流）** |
| 130 次、左邊偽造值每次不同、最右邊那一項固定：被限流 | 29 | 29（只認最右邊那一項） |

解讀：
- **`DEF-487` 的政策有效**：`H=1` 時 A 訪客打滿自己的桶（189 次被擋），B 訪客完全不受影響；`H=0` 時 B 訪客 10 次全被擋（共用 Next 位址的桶）——這是沒有代理時的現況（§8.2 第 1 點）。
- **`H=1` 的兩個前提是真的**：直連後端、每次換偽造值，130 次 0 次被限流——所以預設是 `0`、說明裡明寫「後端與前端不能有繞過代理的直連入口」。
- 左邊偽造的項目確實被忽略（只認最右邊那一項）。
- 後端啟動日誌印出 `[ClientIp] trusted-proxy-hops=1（從 X-Forwarded-For 由右數第 1 項取訪客 IP；後端不可有繞過代理的直連入口）`。

### 4.6 審查發現：hydration 的決定性（沒有任何測試訊號）

內容改由伺服器渲染之後，我逐一檢查公開頁元件的輸出有沒有依賴時區、語系或「現在」：
- `PlatformBlogFeed`、`StoreArticle` 的 `toLocaleDateString('zh-TW')` 沒指定時區——伺服器（容器通常 UTC）與台灣訪客在 UTC 16:00～24:00 之間（一天八個小時）算出不同的一天；
- `StoreArticle` 內嵌卡片的 `toLocaleString()` 沒指定語系——伺服器預設 en-US、德語系瀏覽器的千分位不同；
- `MonthCalendar` 的第一個畫面用 `new Date()`（本月是幾月、今天是哪天）——房源詳情現在會在伺服器渲染它，月底與午夜前後兩邊不同。

這三個在開發機上永遠看不到（伺服器與瀏覽器在同一台機器、同一個時區），**而且 `vitest.config.mts` 把整個測試環境固定在 `Asia/Taipei`**，單元測試也看不到。修正（§5.5）與守門（§5.7）見前；測試自己切時區（`vi.stubEnv('TZ')`），並用「沒指定時區的寫法在兩個時區算出不同結果」當對照，證明這個測試切得動時區、不是空轉。

## 5. 實作

### 5.1 後端（單一參數）

| 檔案 | 異動 |
|---|---|
| `api/filter/ClientIpResolver.java`（新） | 唯一決定來源 IP 的地方（§2.1）；啟動時檢查範圍並印一行說明目前設定；不符時只印一次警告 |
| `RateLimitFilter`、`LoginRateLimitFilter` | 改由 `ClientIpResolver` 取得 IP（匿名限流只有匿名請求才解析）；原本的單參數建構子保留（層數 0），既有測試不動 |
| `application.yml`、`.env.example`、`docker-compose.yml`、`docs/08_deployment/ENVIRONMENT_VARIABLES.md` §2.5.1 | 參數與說明（含設定表、原理、兩個前提、設錯的徵兆、上線前檢查第 11 項） |
| 測試 | `ClientIpResolverTest` 30、`ClientIpWiringTest` 4（真正的 `application.yml`＋環境變數名稱；防止鍵名寫錯讓參數靜默失效）、`RateLimitFilterTest` 14（+3）、`LoginRateLimitFilterTest` 17（+2）、`RateLimitFilterIntegrationTest` 5（+2，真 Redis） |

### 5.2 伺服器端抓取層與 `useAsyncData`

- `lib/server-api.ts`：`fetchPublic`（三種結果）、`fetchPublicData`（標題用，行為不變）、`dataOrUndefined`；`React.cache` 請求內去重；轉發 `X-Forwarded-For`（只轉這一個標頭，Cookie、Authorization 絕不帶給公開端點）；2 秒逾時、不快取。
- `lib/public-api.ts`：路徑組合、每頁筆數、排序對照；`lib/public-data.ts`：`loadStoreProfile`（layout 用，不存在 → `notFound()`）、`peekStoreProfile`（頁面用，不丟例外）、`loadStoreBlog`、`loadEmbeddedCards`（上限 12 張、UUID 才送、取不到的略過）。
- `hooks/useAsyncData.ts`：新選項 `initialData`（首次渲染 `loading=false`、不請求；依賴改變或 `reload()` 照常請求；`fromInitial` 旗標只讓「第一次掛載」省掉那次請求）。

### 5.3 各公開頁（伺服器端取資料）

| 頁面 | 伺服器端後端請求（假後端日誌實測） | 傳給元件的屬性 | 404 規則 |
|---|---|---|---|
| `/` | 1 | `initialResult` | — |
| `/products`、`/rooms` | 1 | `initialResult` | — |
| `/blog` | 1 | `initialFeed` | — |
| `/listings/{id}` | 1（id 不是 UUID：0 個） | `initialListing` | 非 UUID 或後端 404 → `notFound()` |
| `/stores/{slug}` | 2（店鋪＋精選） | `initialProfile`、`initialFeatured` | 店鋪不存在 → **layout** `notFound()` |
| `/stores/{slug}/products`、`/rooms` | 2（店鋪＋清單） | `initialProfile`、`initialListings` | 同上 |
| `/stores/{slug}/blog` | 3（店鋪＋分類＋貼文）；指定了不存在的分類：2 | `initialProfile`、`initialCategories`、`initialPosts` | 同上 |
| `/stores/{slug}/blog/{post}` | 2（店鋪＋文章）＋每張內嵌卡片 1 | `initialProfile`、`initialPost`、`initialCards` | 店鋪：layout；文章不存在／草稿 → `notFound()`（`[postSlug]/not-found.tsx`） |
| 店鋪不存在／後端壞了 | 1 | — | 不存在 → 404；壞了 → 200＋退回瀏覽器載入（不是 404） |

新增 `not-found.tsx` 三個（`stores/`、`stores/[slug]/blog/[postSlug]/`、`listings/[id]/`）與共用畫面元件（`StoreNotFound`、`ArticleNotFound`、`ListingNotFound`）：用戶端備援與伺服器端 404 畫面共用同一份，文字與 `data-testid` 不變（E2E 依賴）。

### 5.4 `robots.txt`、`sitemap.xml`、結構化資料、標準網址、`noindex`

- `app/robots.ts`：開放公開內容，擋 `/api/`、`/dashboard`、`/admin`、`/oauth/`、`/health`，指向 sitemap。
- `app/sitemap.ts`＋`lib/sitemap.ts`：固定頁面、有內容的店鋪頁、每個商品／房源與文章；不含草稿、分頁、排序、搜尋結果；`force-dynamic`（§1.3）、讀全部頁時分批並行（每批 4 頁、每種來源最多 200 頁＝2 萬筆）、**只有全部讀到才在記憶體留存一小時**（後端暫時壞了回只有固定頁面的 sitemap、不留存，下一次請求重試）。
- JSON-LD（`lib/seo.ts`、`components/seo/JsonLd.tsx`）：首頁 `WebSite`（含站內搜尋）、店鋪 `Store`、商品 `Product`／房源 `HotelRoom`（沒有合法價格就不帶 `offers`）、文章 `BlogPosting`；輸出時跳脫 `<` 與 U+2028／2029（資料裡有店家輸入的文字）。
- `alternates.canonical`：店鋪、商品、文章頁（追蹤參數進來的網址收斂成同一個）。列表頁不加：可被爬到的網址本來就乾淨（排序、分類、搜尋都是按鈕，不是連結）。
- 私人區域 `noindex`：`(auth)`、`dashboard`、`admin` 三個 layout 宣告 `robots: { index: false, follow: false }`。

### 5.5 分頁連結、文章瀏覽計數、hydration 修正

- `components/ui/pagination.tsx` 新增 `hrefFor`：公開列表的頁碼、上一頁、下一頁渲染成 `<a href>`（`prefetch={false}`；目前頁是 `aria-current` 的 `span`；第一頁沒有上一頁連結）。後台列表不給 `hrefFor`，仍是按鈕。
- 文章：伺服器端取文章 `countView=false`；`StoreArticle` 在瀏覽器載入時用第二個 `useAsyncData` 計一次並更新顯示的瀏覽數（失敗靜默，登記在 `load-failure.test.ts` 的輔助資訊清單，附理由）。
- hydration：`formatPublicDate`（固定 `Asia/Taipei`）、卡片價格 `toLocaleString('zh-TW')`、房源整月日曆等 hydration 後才渲染（`hooks/useIsClient.ts`）。

### 5.6 E2E 基礎設施與規格調整

- `playwright.config.ts` 新增 `ssr-unavailable` 專案（`at-listing-detail`、`at-room-booking`、`at-homepage`、`at-load-failure-screens`，28 個案例）：這幾支靠 `page.route` 模擬資料（假編號），而伺服器端的請求是 Next 直接打後端，攔不到。專案對一台**後端位址故意不通**的 Next（`API_INTERNAL_URL=http://127.0.0.1:1/api`）執行——伺服器端一律取不到、元件退回瀏覽器載入，行為與改成伺服器端渲染之前完全相同；它驗證的正是這條備援路徑。
- `scripts/validate-e2e.sh`：啟動第二台前端（預設 :3001、同一份建置）、後端 CORS 放行該來源（`APP_CORS_ALLOWED_ORIGINS`）、結尾多印「匿名來源 IP 桶被限流次數」（Sprint 340 當時只統計認證端點）。
- 新規格 `at-seo-ssr-real.spec.ts`（10 個案例，**不執行 JavaScript** 的 HTTP 請求，§2.5）。
- 因行為刻意改變而更新的既有規格：分頁按鈕改查連結（`at-store-blog-category-real`、`at-platform-blog-real`、`at-listing-detail`）；不存在／停權／刪除的實體改驗真正的 404 與 not-found 標題（`at-entity-titles-real` 06／07、`at-article-titles-real` 04／05、`at-page-titles` 兩條路由、`at-m15-e2e` M15-004）；計瀏覽的請求在文章出現之後才送，要等回應（`at-article-titles-real` 03／06）。

### 5.7 架構守門（`src/architecture/public-ssr.test.ts`）

公開頁登記表（新增公開頁沒登記 → 紅燈；每頁要伺服器端取資料、傳 `initial*`、元件把它交給 `useAsyncData`）、店鋪存在與否只由 layout 判定（頁面不得呼叫 `loadStoreProfile`）、公開列表的 `<Pagination>` 一律帶 `hrefFor`、私人區域 `noindex` 且公開區域沒有、`robots.txt` 內容、公開頁輸出 JSON-LD、公開頁元件的輸出不依賴環境（不直接用 `toLocaleDateString`、每個 `toLocaleString(` 指定語系、依賴「現在」的元件登記並只在瀏覽器渲染）。

## 6. 突變驗證

**後端（10 個，驅動見 `mutate-backend.sh` 的做法：備份→套用→跑相關測試→還原→`cmp` 比對）**：

| 突變 | 結果（失敗＋錯誤數） |
|---|---|
| B1 層數 0 不短路（改讀 `X-Forwarded-For`） | 擊殺（12） |
| B2 取最左邊而不是由右數第 N 項 | 擊殺（5） |
| B3 項目不足的檢查放寬 | 擊殺（1） |
| B4 IPv4 解析放寬（接受簡寫） | 擊殺（4） |
| **B5 拿掉 IPv6 字元檢查** | **第一輪倖存**：我的無效值測資只有 `fe80::1%eth0`（介面不存在時 JDK 自己會拒絕）；**數字區域識別 `fe80::1%1` JDK 會接受**，同一位址會因 `%N` 後綴分到不同的限流桶。補上 `fe80::1%1`、`2001:db8::1%25` 之後擊殺（2） |
| B6／B7 兩個過濾器不用解析器 | 擊殺（各 2） |
| B8 `application.yml` 鍵名寫錯 | 擊殺（3，接線測試） |
| B9 環境變數名稱寫錯 | 擊殺（3，接線測試） |
| B10 拿掉範圍檢查 | 擊殺（2） |

**前端（23 個，`mutate-frontend.py`；每個只跑相關測試檔）**：

| 突變 | 擊殺（失敗／總數） |
|---|---|
| F01 `useAsyncData` 不省掉第一次請求 | 5／26 |
| F02 `useAsyncData` 忽略 `initialData` | 8／41 |
| F03 伺服器端把所有失敗都當成「不存在」 | 4／12 |
| F04 `X-Forwarded-For` 附加而不是原封不動 | 1／12 |
| F05 伺服器端取文章不帶 `countView=false` | 3／48 |
| F06 分頁「下一頁」連到自己 | 2／13 |
| F07 `PlatformBlogFeed` 丟掉 `initialData` | 4／64 |
| F08 `/blog` 頁面不再傳資料給元件 | 1／49 |
| F09 文章頁自己判定店鋪不存在（我踩過的缺陷） | 1／49 |
| F10 `robots.txt` 不擋 `/dashboard` | 1／49 |
| F11 JSON-LD 不跳脫 `<` | 1／4 |
| F12 `formatPublicDate` 沒指定時區 | 2／22 |
| F13 房源日曆在伺服器端渲染 | 3／52 |
| F14 沒有 canonical | 1／38 |
| F15 文章永遠不計瀏覽 | 2／13 |
| F16 sitemap 把房源當成商品 | 1／9 |
| F17 `loadStoreBlog` 不檢查分類是否存在 | 2／14 |
| F18 `loadStoreProfile` 不存在也不回 404 | 1／14 |
| F19 店鋪商品列表的分頁退回按鈕 | 1／49 |
| F20 嵌入卡片沒有數量上限 | 1／14 |
| F21 店鋪底下的頁面改回用會丟例外的版本 | 1／49 |
| F22 layout 不再檢查店鋪存在 | 1／49 |
| **F23 嵌入語法的編號格式放寬為任意字元** | **第一輪倖存**（0／25）：沒有任何測試守「格式不對的嵌入語法必須維持純文字」（舊行為，這輪把語法定義搬到 `lib/article-embeds.ts` 時沒有人守）。補上 `article-embeds.test.ts` 之後擊殺（1／31） |

## 7. 後置驗證

### 7.1 本機完整驗證

| 項目 | 結果 |
|---|---|
| 前端單元測試（Vitest） | **45 檔 518 案例全過**（Sprint 340：37 檔 374；+8 檔、+144 案例） |
| `npm run lint`（`--max-warnings 0`） | 通過（退出碼 0） |
| `tsc --noEmit` | 通過（退出碼 0） |
| 突變驗證 | 後端 10／10、前端 25／25 擊殺（§6）；各有 1 個第一輪倖存者，補測資後擊殺 |
| 後端 | 單元（`ClientIpResolverTest` 30、`ClientIpWiringTest` 4、`RateLimitFilterTest` 14、`LoginRateLimitFilterTest` 17）、真 Redis 整合（`RateLimitFilterIntegrationTest` 5）與 checkstyle 都過；後端 commit `20b5d1bf` 的 pre-commit 核心測試也過。**完整後端整合套件這輪沒有在本機重跑**（§8.2 第 11 點），留給推送時的 pre-push 與雲端 CI |
| 完整本機 E2E 守門（`make validate-e2e`） | 最終一輪（第三輪）**399／399 全過（11.2 分）**；守門統計的認證端點被限流 0 次、匿名來源 IP 桶被限流 0 次（Sprint 340：389／389；+10 案例＝新規格 `at-seo-ssr-real`）。退出碼取自守門腳本本身（`gate exit=0`），不是接在後面的 `echo` |
| 空白檢查 | `git diff --check` 在守門之後發現 6 個檔案的檔尾多一個空行（我追加內容時留下的），已修；只動檔尾換行，修完重跑 `tsc`、ESLint、Vitest 皆通過（退出碼 0、45 檔 518 案例） |

### 7.2 完整 E2E 守門三輪

| 輪次 | 結果 | 失敗的分類與處置 |
|---|---|---|
| 第 1 輪（找出受影響的規格） | 385 過／**5 失敗**／9 未執行（10.4 分） | 5 個全是「行為刻意改變」造成的預期過時（§9 第 7 點）：`E2E-ARTICLE-03`（SSR 讓文章內容比計瀏覽請求早出現 → 改成等計瀏覽的回應）、`E2E-ENTITY-06`（停權店鋪現在回真 404）、`E2E-M15-004`（不存在的文章現在回真 404）、`E2E-TITLE-01` ×2（`/listings/x`、`/stores/demo/products` 現在回 404、標題是「找不到商品」「找不到店鋪」）→ 修規格。9 個未執行：失敗的四個規格（文章標題、實體標題、M15、房源日曆）都是序列模式，同一組內失敗之後的案例不會跑；**沒有逐一核對這 9 個的名稱** |
| 第 2 輪 | 397 過／**1 失敗**／1 未執行（11.1 分） | `E2E-GUESTCAL-02`：**真缺陷**——SSR 讓房源日曆的控制項在 hydration 前可操作，輸入被丟掉。修法：hydration 完成前停用控制項（`ListingDetail`），補單元測試與突變 F24、F25 |
| 第 3 輪（最終） | **399／399 全過**（11.2 分） | 認證端點被限流 0 次、匿名來源 IP 桶被限流 0 次；沒有失敗、沒有未執行 |

### 7.3 push 與雲端 CI

- **push**：`25e25d81..665fb892`，4 個 commit——Sprint 340 的回填 `0ea5bf43`、Sprint 341 的後端 `20b5d1bf`、前端／測試／守門腳本 `99a76cfe`、文件 `665fb892`。pre-push 輕量守門（`make validate-push`：backend-unit＋schema 漂移＋frontend，act）全過（「輕量守門通過！允許 push」），沒有用 `--no-verify`。
- **雲端 CI 全綠**：run [43228131094](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/43228131094)，`headSha` `665fb892` 與本機一致，三個 job 皆 success（以 `gh run view --json` 取得 run 層級結論、逐 job 檢查**沒有非 success／skipped 的步驟**——該查詢輸出為空）：Backend Unit Tests（`mvn test -q`，雲端日誌不印總數；Checkstyle 0 違規、BUILD SUCCESS，約 3 分）、Frontend Lint & Build（`eslint --max-warnings 0`、`tsc --noEmit`、Vitest **45 檔 518／0**＝與本機完全相同、`Compiled successfully`、靜態頁面 68／68，約 2 分）、Backend Integration Tests & Package（整合 1055／0／0 跳過（比 Sprint 340 的 1053 多 2＝`RateLimitFilterIntegrationTest` 3 → 5，日誌確認該類別 5／0；Checkstyle 0 違規；Maven BUILD SUCCESS 9 分 38 秒），job 約 11 分 4 秒）；整個 run 約 14 分鐘（13:31:36Z → 13:45:49Z）。
- **雲端沒有 e2e job**（`act-compat.yml` 只有三個 job），所以完整 E2E 守門 399／399 只有本機證據（§7.2）。
- 後端單元總數不在雲端日誌（`mvn test -q` 是安靜模式），這輪也沒有在本機重跑完整單元套件（§8.2 第 11 點）；這輪新增的後端測試中，`RateLimitFilterIntegrationTest` 屬於雲端整合 job 跑的範圍。

### 7.4 守門收尾與 Docker 清理

推送（pre-push 的 act＋schema-gate 已結束）、雲端 CI 還在跑時做的（清理與雲端 run 無關；之後只剩純文件回填，不需要測試資料庫）：

- **盤點**：容器 0（pre-push 清掉了 `nk-test-*`）、映像 3 個（核准的基底映像，無懸空）、卷 15＝10 匿名＋5 具名（1.282GB）、建置快取 84 筆 8.96GB（只回報，沒有刪）。埠 3000／3001／8080／8081 都已釋放。
- **刪除**：10 個匿名卷，刪前用 `docker volume inspect` 逐一核對——都帶 `com.docker.volume.anonymous` 標籤、建立於當天 11:09～13:30Z、成 5 對（每對相差 1 秒）；逐一 `docker volume rm`（10 成功／0 失敗），卷 1.282GB → 928MB；`docker image prune -f` 回收 0B。成因沒有逐一驗證（推測：三輪完整守門、pre-commit 與 pre-push 的容器）；我自己起的量測容器一律 `docker rm -fv`，沒留卷。
- **刻意保留**：5 個具名卷（`act-toolcache`、`e-commerce_minimax_maven_cache`、`e-commerce_minimax_minio_data`、`e-commerce_minimax_postgres_data`〔你的 dev 資料庫〕、`e-commerce_minimax_redis_data`）與 3 個核准的基底映像（`postgres:18-alpine`、`redis:7-alpine`、`catthehacker/ubuntu:act-latest`）；沒有用 `docker volume prune`、沒有用 `docker image prune -a`。

## 8. 行為變更與已知限制

### 8.1 行為變更

| 項目 | 之前 | 現在 | 影響誰 |
|---|---|---|---|
| 公開頁的第一份 HTML | 載入中骨架 | 有內容（商品、房源、文章、店鋪、連結、結構化資料） | 搜尋引擎、不執行 JavaScript 的爬蟲與連結預覽；慢網路訪客的首屏 |
| 不存在的店鋪／商品／文章（含草稿、停權、已刪除） | 200＋「找不到」畫面 | **404＋noindex**（畫面仍由瀏覽器渲染，§4.4） | 搜尋引擎、監控與任何依狀態碼判斷的工具 |
| 後端暫時不可用（429、5xx、逾時） | 瀏覽器端顯示載入失敗與重試 | 伺服器端取不到就不帶資料、**不回 404**，由瀏覽器自己載入並照舊處理失敗 | 訪客（行為不變） |
| 公開列表的分頁 | 按鈕 | 連結（用戶端導覽，不整頁重載） | 搜尋引擎；E2E 要用 `link` 角色查詢（已更新三支規格） |
| 文章的瀏覽計數 | 頁面載入時的那個請求順便計一次 | 伺服器端取文章不計；瀏覽器載入後**另外**送一個請求計一次，畫面數字更新為含本次 | 後端：每次瀏覽仍只計一次；爬蟲仍不灌水；E2E 要等那個回應 |
| 有代理時的來源 IP | 一律是代理的位址（所有訪客共用一個桶） | 設了 `APP_TRUSTED_PROXY_HOPS` 之後是訪客自己的位址；沒設（預設）完全不變 | 限流（匿名、登入／註冊） |
| 沒有代理時的匿名限流桶壓力 | 頁面資料由訪客各自的瀏覽器請求（各用各的桶） | 由 Next 伺服器請求，**全站共用 Next 位址的那一個桶** | 全站容量，見 §8.2 第 1 點 |
| 公開頁的日期與卡片價格 | 瀏覽器的時區與語系 | 日期固定台灣時區、價格固定 `zh-TW` | 非台灣時區的訪客看到的是台灣日期 |
| 房源詳情的整月日曆 | 資料載入後出現 | 伺服器端放同樣大小的佔位，hydration 之後才渲染 | 房源頁訪客（訂房本來就需要 JavaScript） |
| `/robots.txt`、`/sitemap.xml` | 不存在 | 有（§5.4） | 搜尋引擎 |
| 私人區域（`(auth)`、`dashboard`、`admin`） | 沒有 robots 宣告 | `noindex` | 搜尋引擎 |

### 8.2 已知限制（誠實列出）

1. **沒有代理時，伺服器端的請求共用 Next 位址的那一個匿名桶（每分鐘 100 次）。** 公開頁改成伺服器端渲染等於把「訪客自己的瀏覽器打的請求」搬到這個共用的桶：每個頁面載入 1～3 個後端請求，全站約每分鐘 33～100 次頁面瀏覽，超過的頁面會退回載入中骨架（搜尋引擎那一次看到的是空內容；不是 404）。**這是現況（沒有代理）下沒有答案的問題**——解法是上線前設反向代理並把 `APP_TRUSTED_PROXY_HOPS` 設成層數（§4.5 實測：設了之後 B 訪客不受 A 訪客影響）。登記為 `DEF-502`。我沒有做「後端對 Next 位址放寬」或「短快取」（§2.3：前者移除保護、後者與 Sprint 335 的設計衝突且本機 E2E 會間歇失敗）。
2. **`APP_TRUSTED_PROXY_HOPS` 設成 1 以上的兩個前提不是文件裝飾，是實測的**（§4.5）：代理必須附加標頭；後端與前端不能有繞過代理的直連入口（實測直連每次換偽造值，130 次 0 次被限流）。設錯層數（太小＝所有訪客共用代理的桶；太大＝退回對端位址並印警告）沒有辦法自動偵測，只有日誌警告與 429 日誌裡全是同一個 IP 這兩個徵兆。
3. **404 的第一份 HTML 是空殼**（Next 的設計，§4.4）。狀態碼與 `noindex` 正確，搜尋引擎足夠；不執行 JavaScript 的**人類**使用者看到空白。登記為 `DEF-504`（框架行為，沒有改變它的辦法，只能知道）。
4. **sitemap**：單一檔案、每種來源最多 200 頁（2 萬筆）；超過要改用 `generateSitemaps` 切檔（登記為 `DEF-503`）。每個 Next 實例各有一份一小時的記憶體留存（多實例部署各算各的）。
5. **分類與排序不是連結**：店鋪貼文的分類篩選列與首頁的分類／排序仍是按鈕。分類頁是同一批貼文的子集合、排序只是另一種排列，爬蟲沿著完整列表的分頁連結就走得到全部內容；這是刻意取捨，登記為 `DEF-505` 以免被當成遺漏。
6. **沒有量測真實網路下的首屏／TTFB 改善**，只量了請求數與第一份 HTML 的內容。
7. **沒有用真正的搜尋引擎爬蟲驗證**：沒有公開網域、沒有 Google Search Console／Rich Results Test。我只能用「不執行 JavaScript 的 HTTP 請求」模擬爬蟲看到的第一份 HTML，結構化資料只驗了結構與欄位、與頁面內容一致，沒有送去驗證工具。
8. **`DEF-500` 的 ②③④⑤ 沒動**（沒有篩選／搜尋／排序切換、卡片不連到店鋪、大量貼文的列表查詢耗時沒量、舊網址 `/blog/{slug}` 導向頁仍是登入者專用）。
9. **`ssr-unavailable` 專案需要第二台 Next**：完整守門腳本會啟動；單獨執行那四支規格時要自己先啟動（`playwright.config.ts` 開頭有指令）。
10. **日期固定台灣時區**：這是台灣市場的網站，但非台灣時區的訪客看到的是台灣的日期。
11. **這輪沒有重跑後端完整測試套件**（`mvn verify`）：後端只新增了來源 IP 的決定點與參數，相關的單元、整合（真 Redis）、接線測試與 checkstyle 都過，pre-commit 的核心測試也過；完整整合套件在推送時由 pre-push 的 act CI 與雲端 CI 跑。
12. **React `cache` 只在同一個請求內去重**；跨請求沒有任何快取（刻意：停權與改名立刻反映）。

## 9. 失誤與修正（誠實列出）

1. **開發伺服器的假失敗**：用 `rsync` 把原始碼同步進專案副本後，`next dev`（Turbopack）報 `dataOrUndefined is not a function`／`loadStoreBlog is not a function`（`tsc` 與檔案內容都對），我一度當成程式錯誤排查；真因是已載入的模組不會更新匯出，重啟即消失。之後凡是對 SSR 行為下結論都改用正式建置（`next build`＋`next start`）。
2. **layout 與頁面同時 `notFound()`，文章頁把「店鋪不存在」說成「找不到文章」**：設計時沒想到「離頁面最近的 `not-found` 邊界」會接走 layout 丟的例外（§4.4）。解法是「店鋪存在嗎」只由 layout 判定，頁面用不丟例外的 `peekStoreProfile`，並新增架構守門與突變（F09、F21、F22）。
3. **用 `curl` 數字串判斷 404 畫面是誤判**：RSC payload 內含各層 `notFound` 邊界的元素字串，看起來像畫面文字其實沒有顯示；改用真瀏覽器並讀 `app-render.js` 才釐清（§4.4）。
4. **用 `cat >` 整檔覆蓋了既有的 `frontend/src/lib/date.test.ts`**（Rule 8：寫入前先閱讀——我沒讀就覆蓋）：靠 `git status` 顯示 `M` 才發現，`git checkout HEAD --` 還原後再追加新測試。沒有損失，但這是「不知道檔案已存在就寫」的典型失誤。
5. **新測試的寫法錯誤，全在寫的當下被紅燈抓到、沒有留下**：①`beforeEach(() => mock.mockReset())` 回傳 mock，vitest 把它當成清理函式在每個測試後呼叫，14 個假失敗（堆疊裡出現 `callCleanupHooks` 才看出）；②`hydrateRoot` 要用 React Testing Library 的 `act`，不是 `react` 的；③架構守門的規則運算式漏掉泛型呼叫 `fetchPublic<...>(`；④「用到現在時間」的登記表漏了 `ListingDetail`（只有事件處理函式用到 `new Date()`）。
6. **兩輪突變驗證各有倖存者**：後端 B5（數字區域識別 `fe80::1%1`）沒有測資；前端 F23（格式不對的嵌入語法必須維持純文字）在把嵌入語法定義搬到 `lib/article-embeds.ts` 時沒有任何測試守舊行為。都補測資、把新測試檔加進該突變的測試清單後擊殺（§6）。倖存的價值就在這裡，也代表「單元測試全過」不等於守住了行為。
7. **完整 E2E 守門第一輪 5 個失敗、第二輪 1 個，性質不同**：第一輪的 5 個全是「行為刻意改變造成的預期過時」（SSR 讓文章內容比計瀏覽請求早出現、停權／已刪除變成真 404、假編號探測標題、瀏覽器請求的等待方式），修規格；**第二輪的 1 個（`E2E-GUESTCAL-02`）是真缺陷**：SSR 讓日期欄位在 hydration 之前就可操作，輸入會被丟掉。單元測試（固定台灣時區、沒有 hydration 時序）看不到，只有真實瀏覽器的完整守門抓到。修法是 hydration 完成前把訂房控制項停用（`ListingDetail`），並補測試與突變（F24、F25）。**所以前兩輪都不是全綠，只有最後一輪才是最終證據（§7.2）。**
8. **兩處未量測的說法**：計畫書 §2.3 曾寫「店鋪首頁 3→2 個請求」——「3」（沒有 `cache` 的反事實）我沒有量過，只有「2」是實測；記憶檔也有同樣的反事實措辭。兩處都已改成只陳述實測的「2」。
9. **zsh 的 `echo ======`**：以 `=` 開頭的字在 zsh 會被當成指令路徑展開，印出錯誤而不是分隔線；無害，但再次提醒在 zsh 裡別用等號列當分隔符。

## 10. 追蹤表的變動

| 編號 | 變動 |
|---|---|
| `DEF-501` | 🟡→✅：新增單一參數 `APP_TRUSTED_PROXY_HOPS`（§2.1、§5.1、§4.5）。**待你做**：上線前確認代理形態、設定該值、確認後端與前端沒有直連入口（`ENVIRONMENT_VARIABLES.md` §6 第 11 項） |
| `DEF-487` | 🟢→✅（政策已決定並實作）：轉發訪客位址＋請求內去重＋取不到時退回瀏覽器載入（§2.3）。**沒有代理時的共用桶問題仍在**，拆出 `DEF-502` |
| `DEF-500` | ① ✅（所有公開頁伺服器端渲染＋真正的 404＋`robots.txt`＋`sitemap.xml`＋結構化資料＋標準網址＋`noindex`＋分頁連結）；②③④⑤ 仍是 🟢 未做 |
| `DEF-502`（新） | 🟡 沒有反向代理時，伺服器端的請求共用 Next 位址的匿名桶（全站約每分鐘 33～100 次頁面瀏覽）；選項：①上線前設代理與 `APP_TRUSTED_PROXY_HOPS`（現行建議）、②沒有代理的部署另行設計 |
| `DEF-503`（新） | 🟢 sitemap 超過 2 萬筆要用 `generateSitemaps` 切檔（單檔上限 5 萬個網址） |
| `DEF-504`（新） | 🟢 `notFound()` 的第一份 HTML 是空殼（Next 的設計；狀態碼與 `noindex` 正確，不執行 JavaScript 的人類使用者看到空白） |
| `DEF-505`（新） | 🟢 分類與排序仍是按鈕、不是連結（刻意；分類頁是同一批內容的子集合） |

## 11. 我替使用者做的決定（可一句話推翻）

1. **參數的形式是「代理層數」，不是 CIDR 清單、也不是多個開關**（§2.1）：操作者一定知道層數、不一定知道代理位址；CIDR 要讓「訪客→代理→Next→後端」與「訪客→代理→後端」兩條路都對，Next 也得知道哪些位址可信＝第二個設定，違反你要的「全專案只有一個」。推翻方式：改成可信位址清單，並讓 Next 也讀同一個設定（或改 Next 的轉發方式）。
2. **預設 `0`，不是 `1`**：沒有代理時什麼都不用設；設成 1 以上有兩個前提（§4.5 實測直連可偽造），不能當預設。
3. **`DEF-487` 選「轉發訪客位址」**（§2.3），不選「排除伺服器端請求出限流」（移除保護）與「標題短快取」（與 Sprint 335 設計衝突、不解決歸屬）。
4. **「全部 SEO」的範圍**：所有公開頁的首份資料由伺服器取＋真正的 404＋`robots.txt`＋`sitemap.xml`＋結構化資料＋標準網址＋私人區域 `noindex`＋分頁連結。**沒做**：分類／排序的連結（`DEF-505`）、列表頁的 canonical（可被爬到的網址本來就乾淨）、`hreflang`、預設的 `og:image`、RSS。推翻方式：告訴我要加哪一項。
5. **不存在的實體回真正的 404**（不是 200 加畫面）。這是行為變更（§8.1），也讓幾支規格的預期要改（§5.6）。
6. **後端暫時不可用時絕不回 404**：不帶資料、由瀏覽器載入（§2.4）——好好的頁面不能因為後端一時壞了被搜尋引擎除名。
7. **文章瀏覽計數搬到瀏覽器**：伺服器端取文章不計（沿用 Sprint 336 的決定），瀏覽器載入後另外送一個請求計一次；失敗靜默。
8. **公開列表的分頁改成連結並關閉預取**；後台列表不動。
9. **日期固定台灣時區、價格固定 `zh-TW`**（§4.6）。
10. **房源整月日曆只在瀏覽器渲染**（伺服器端放佔位）：日曆不是要收錄的內容，且第一個畫面依賴「現在」。
11. **靠 `page.route` 模擬資料的 28 個規格不改寫成真實資料，而是新增 `ssr-unavailable` Playwright 專案**（§5.6）：它們驗證的是「瀏覽器自己載入」這條備援路徑，原本的模擬照常生效；代價是守門腳本多啟動一台前端、後端 CORS 放行該來源。推翻方式：把這些規格改成種真實資料。
12. **sitemap 單檔、`force-dynamic`、記憶體留存一小時、每種來源上限 2 萬筆**（`DEF-503`）。
13. **新登記 `DEF-502`～`DEF-505`**（§10）。
14. **對 Docker 只新增一行環境變數傳遞**（§1.2），其餘沒動。

## 12. 狀態

| 項目 | 狀態 |
|---|---|
| `DEF-501` 單一參數 `APP_TRUSTED_PROXY_HOPS` | ✅ 已做並實測（§4.5）；**待使用者**：上線前確認代理形態並設值（`ENVIRONMENT_VARIABLES.md` §6 第 11 項） |
| `DEF-487` 限流歸屬政策 | ✅ 政策已決定並實作（轉發訪客位址）；沒有代理時的共用桶仍在，拆為 `DEF-502`（🟡） |
| `DEF-500` ① 公開頁 SEO 伺服器端渲染 | ✅ 已做；②③④⑤ 沒動 |
| 新登記 | `DEF-502`（🟡）、`DEF-503`、`DEF-504`、`DEF-505`（🟢） |
| 完整 E2E 守門 | ✅ 第三輪 399／399（§7.2） |
| 後端 commit | `20b5d1bf`（已 push） |
| 前端／測試／守門腳本 commit | `99a76cfe`（已 push） |
| 文件 commit | `665fb892`（已 push） |
| push 與雲端 CI | ✅ 已 push（`25e25d81..665fb892`）；雲端 CI 全綠：run 43228131094 三個 job 皆 success（§7.3） |
| Docker 收尾 | ✅ 刪 10 個匿名卷、保留 5 個具名卷與 3 個核准基底映像（§7.4） |

---

## 附錄 A：假後端 `stub-backend.js`（全文；只放在 scratchpad，不進 repo）

只回公開端點的假資料，並把每個請求的 method、url、`x-forwarded-for`、user-agent 寫進日誌；`gone`＝404、`boom`＝500、`limited`＝429。用法：`node stub-backend.js <埠> <日誌檔>`，Next 以 `API_INTERNAL_URL=http://127.0.0.1:<埠>/api` 指向它。

```js
// 假後端（Sprint 341 驗證用）：只回公開端點的假資料，並把每個請求的 method、url、x-forwarded-for、user-agent 寫進日誌。
// 用法：node stub-backend.js <埠> <日誌檔>
// 特殊 slug／id：gone＝404、boom＝500、limited＝429（模擬後端不存在／壞掉／被限流）。
const http = require('http')
const fs = require('fs')

const port = Number(process.argv[2] || 18080)
const logFile = process.argv[3] || '/tmp/stub-backend.log'
fs.writeFileSync(logFile, '')

const TENANT_ID = '3f2a1c9e-5b7d-4e8f-9a6b-1c2d3e4f5a6b'
const LISTING_ID = '0b135be9-bf30-4ddb-ab3f-092f3e26b845'
const NOW = '2026-11-01T08:00:00Z'

const store = { tenantId: TENANT_ID, slug: 'demo', name: '山海小店', description: '賣山也賣海的小店', logoUrl: null }
const listing = (n, type = 'PRODUCT') => ({
  id: n === 1 ? LISTING_ID : `0b135be9-bf30-4ddb-ab3f-0000000000${String(n).padStart(2, '0')}`,
  tenantId: TENANT_ID, listingType: type, title: `${type === 'ROOM' ? '房型' : '商品'}${n}號`, description: `第 ${n} 號的說明文字`,
  coverImageUrl: null, status: 'ACTIVE', basePrice: 100 * n, currency: 'TWD', tags: ['標籤甲', '標籤乙'],
  createdAt: NOW, updatedAt: NOW, tenantName: '山海小店', tenantSlug: 'demo',
})
const page = (content, extra = {}) => ({ content, totalElements: 30, totalPages: 3, number: 0, size: 12, ...extra })
const postSummary = (n) => ({
  id: `p${n}`, slug: `post-${n}`, title: `第${n}篇文章`, excerpt: `第${n}篇的摘要`, featuredImageUrl: null, publishedAt: NOW,
  tenantSlug: 'demo', tenantName: '山海小店', categoryId: null, categoryName: null,
})
const postFull = (slug) => ({
  id: 'p1', tenantId: TENANT_ID, tenantName: '山海小店', tenantSlug: 'demo', authorId: 'a1', authorName: '店長', title: `文章${slug}`, slug,
  content: `這是${slug}的內文。\n\n{{embed:listing:${LISTING_ID}}}\n\n結尾`, excerpt: '文章摘要', featuredImageUrl: '', status: 'PUBLISHED',
  categoryId: 'c1', categoryName: '旅遊筆記', tags: ['tag1'], viewCount: 7, publishedAt: NOW, createdAt: NOW, updatedAt: NOW, embeds: [],
})
const card = { listingId: LISTING_ID, listingType: 'PRODUCT', title: '嵌入的商品', coverImageUrl: '', basePrice: 100, currentPrice: 100, currency: 'TWD',
  availability: { inStock: true, availableQty: 3, available: true }, tenantName: '山海小店', ctaUrl: `/listings/${LISTING_ID}`, isActive: true, statusReason: null }

const ok = (data) => ({ success: true, data })

function route(url) {
  const u = new URL(url, 'http://stub')
  const p = u.pathname.replace(/^\/api/, '')
  const q = u.searchParams
  let m
  if ((m = p.match(/^\/v2\/public\/stores\/([^/]+)$/))) {
    const slug = decodeURIComponent(m[1])
    if (slug === 'gone') return [404, { code: 'E-2000' }]
    if (slug === 'boom') return [500, { code: 'E-9000' }]
    if (slug === 'limited') return [429, { code: 'E-9904' }]
    return [200, ok({ ...store, slug })]
  }
  if ((m = p.match(/^\/v2\/public\/stores\/([^/]+)\/listings$/))) {
    const type = q.get('type'); const featured = q.get('featured')
    const n = featured === 'true' ? 2 : 3
    return [200, ok(page(Array.from({ length: n }, (_, i) => listing(i + 1, type === 'ROOM' ? 'ROOM' : 'PRODUCT')), { number: Number(q.get('page') || 0) }))]
  }
  if (p === '/v2/public/listings') {
    const type = q.get('type')
    return [200, ok(page(Array.from({ length: 4 }, (_, i) => listing(i + 1, type === 'room' ? 'ROOM' : 'PRODUCT')), { number: Number(q.get('page') || 0) }))]
  }
  if (p === '/v2/public/posts') {
    const pg = Number(q.get('page') || 0)
    return [200, ok({ posts: [1, 2].map((n) => postSummary(n + pg * 2)), totalCount: 30, page: pg, size: 12, totalPages: 3 })]
  }
  if (p === '/v2/posts/categories') return [200, ok({ categories: [{ id: 'c1', tenantId: TENANT_ID, name: '旅遊筆記', slug: 'travel', description: '', sortOrder: 1, isActive: true, createdAt: NOW, updatedAt: NOW }], totalCount: 1 })]
  if (p === '/v2/posts') {
    const pg = Number(q.get('page') || 0)
    return [200, ok({ posts: [1, 2].map((n) => ({ ...postFull(`post-${n}`), content: `內文${n}` })), totalCount: 30, page: pg, size: 12, totalPages: 3 })]
  }
  if ((m = p.match(/^\/v2\/posts\/([^/]+)$/))) {
    const slug = decodeURIComponent(m[1])
    if (slug === 'missing') return [404, { code: 'E-4000' }]
    return [200, ok(postFull(slug))]
  }
  if ((m = p.match(/^\/v2\/listings\/([^/]+)\/card$/))) return [200, ok(card)]
  if ((m = p.match(/^\/v2\/listings\/([^/]+)$/))) {
    if (m[1].endsWith('0404')) return [404, { code: 'E-3000' }]
    return [200, ok(listing(1))]
  }
  return [404, { code: 'E-0000', note: 'stub: 沒有這個端點' }]
}

http
  .createServer((req, res) => {
    const [status, body] = route(req.url)
    fs.appendFileSync(logFile, JSON.stringify({ m: req.method, u: req.url, xff: req.headers['x-forwarded-for'] || null, ua: (req.headers['user-agent'] || '').slice(0, 30), s: status }) + '\n')
    res.writeHead(status, { 'Content-Type': 'application/json' })
    res.end(JSON.stringify(body))
  })
  .listen(port, '127.0.0.1', () => console.log(`stub backend on ${port}`))
```

## 附錄 B：不執行 JavaScript 的探針 `probe.py`（全文）

印出狀態碼、`<title>`、`robots`、`canonical`，以及指定字串在**原始 HTML** 出現的次數（模擬爬蟲）；`--log` 印出假後端日誌（每個頁面打了幾個請求）。

```python
#!/usr/bin/env python3
"""不執行 JavaScript 地取頁面，印出狀態碼、<title>、robots meta，以及指定字串在原始 HTML 出現的次數（模擬爬蟲）。
用法：probe.py <基底網址> <路徑>[,<路徑>...] <字串>[,<字串>...] [--xff 值] [--log 假後端日誌檔]
"""
import re
import sys
import time
import urllib.error
import urllib.request


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def main():
    argv = sys.argv[1:]
    xff = None
    log = None
    if '--xff' in argv:
        i = argv.index('--xff')
        xff = argv[i + 1]
        del argv[i:i + 2]
    if '--log' in argv:
        i = argv.index('--log')
        log = argv[i + 1]
        del argv[i:i + 2]
    base, paths, needles = argv[0], argv[1].split(','), (argv[2].split(',') if len(argv) > 2 and argv[2] else [])
    opener = urllib.request.build_opener(NoRedirect)
    if log:
        open(log, 'w').close()
    for path in paths:
        req = urllib.request.Request(base + path, headers={'User-Agent': 'probe-nojs'})
        if xff:
            req.add_header('X-Forwarded-For', xff)
        started = time.time()
        try:
            resp = opener.open(req, timeout=120)
            status, body, headers = resp.status, resp.read().decode('utf-8', 'replace'), resp.headers
        except urllib.error.HTTPError as e:
            status, body, headers = e.code, e.read().decode('utf-8', 'replace'), e.headers
        took = time.time() - started
        title = re.search(r'<title>(.*?)</title>', body, re.S)
        robots = re.findall(r'<meta name="robots" content="([^"]*)"', body)
        canonical = re.findall(r'<link rel="canonical" href="([^"]*)"', body)
        print(f'{status} {took:5.2f}s {path}')
        if headers.get('location'):
            print(f'   → {headers.get("location")}')
        print(f'   title={title.group(1) if title else None!r} robots={robots} canonical={canonical}')
        found = [(n, body.count(n)) for n in needles]
        print('   ' + '  '.join(f'{n!r}:{c}' for n, c in found))
    if log:
        print('--- 假後端日誌')
        for line in open(log, encoding='utf-8'):
            print('  ', line.rstrip()[:140])


main()
```

## 附錄 C：真實全棧的來源 IP 驗證 `proxy-hops-verify.py`（全文；§4.5）

前提：`sp341-pg`／`sp341-redis` 容器（`postgres:18-alpine`、`redis:7-alpine`）、後端 JAR 在 18081（`APP_TRUSTED_PROXY_HOPS` 依情境設 0 或 1）、Next 正式建置在 3102（`API_INTERNAL_URL` 指向 18081）。

```python
#!/usr/bin/env python3
"""APP_TRUSTED_PROXY_HOPS 的真實全棧驗證（Sprint 341，DEF-501／DEF-487）。

全棧：全新的 PostgreSQL＋Redis 容器、後端 JAR（Flyway 從零建表）、Next 正式建置（next start）。
用 curl 等價的 HTTP 請求，把「前面有一層反向代理」模擬成：客戶端自己帶 X-Forwarded-For（等同代理附加的那一項）。

驗證三件事（每件在 H=0 與 H=1 各做一次）：
  A. DEF-487：訪客 A 把自己的匿名桶打滿之後，另一位訪客 B 的頁面（Next 伺服器端渲染）是否還拿得到資料。
     資料取得成功→HTML 有商品列表區塊（或空列表訊息）；被限流（429）→伺服器端取不到→退回載入中骨架。
  B. 直連後端（不經 Next）時，標頭可不可以被偽造：同一個真實來源、每次換一個偽造的 X-Forwarded-For，會不會繞過限流。
  C. H=1 時，X-Forwarded-For 左邊訪客自己填的偽造項目是否被忽略（只認最右邊那一項）。
"""
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request

BACKEND = 'http://127.0.0.1:18081/api'
NEXT = 'http://127.0.0.1:3102'


def get(url, xff=None, timeout=60):
    req = urllib.request.Request(url, headers={'User-Agent': 'verify'})
    if xff:
        req.add_header('X-Forwarded-For', xff)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as r:
            return r.status, r.read().decode('utf-8', 'replace')
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'replace')


def flush_redis(port):
    subprocess.run(['docker', 'exec', 'sp341-redis', 'redis-cli', 'FLUSHALL'], capture_output=True)


def ssr_has_data(html):
    """伺服器端有取到資料：商品列表區塊或「沒有符合條件的商品」；取不到時是載入中骨架（兩者都沒有）。"""
    return 'data-testid="listing-type-grid"' in html or 'data-testid="listing-type-empty"' in html


def scenario_a(label):
    from concurrent.futures import ThreadPoolExecutor
    flush_redis(0)

    def one(_):
        status, html = get(f'{NEXT}/products', xff='203.0.113.10')
        return status == 200 and ssr_has_data(html)

    # 訪客 A 一口氣 300 次（16 條並行）：把自己的匿名桶（容量 100）打滿並壓到空
    with ThreadPoolExecutor(max_workers=16) as pool:
        results = list(pool.map(one, range(300)))
    ok = sum(results)
    # 緊接著另一位訪客 B 連續 10 次（桶是連續補充的，單一次可能剛好補到一個，所以看 10 次裡有幾次成功）
    b_results = [(lambda r: r[0] == 200 and ssr_has_data(r[1]))(get(f'{NEXT}/products', xff='203.0.113.20')) for _ in range(10)]
    b_ok = sum(b_results)
    print(f'[{label}] A 訪客一口氣 300 次 /products：伺服器端取到資料 {ok} 次、被限流而退回骨架 {300 - ok} 次；'
          f'緊接著 B 訪客（另一個位址）10 次：取到資料 {b_ok} 次')
    return ok, 300 - ok, b_ok


def scenario_b(label):
    flush_redis(0)
    limited = 0
    for i in range(130):
        status, _ = get(f'{BACKEND}/v2/public/listings?page=0&size=1', xff=f'198.51.100.{i % 250 + 1}')
        if status == 429:
            limited += 1
    print(f'[{label}] 直連後端 130 次、每次換一個偽造的 X-Forwarded-For：被限流 {limited} 次'
          f'（0 ＝偽造繞過了限流；> 0 ＝標頭沒被採信）')
    return limited


def scenario_c(label):
    flush_redis(0)
    limited = 0
    for i in range(130):
        # 左邊是訪客自己填的（每次不同），右邊那一項是「代理附加的真實訪客」（固定）
        status, _ = get(f'{BACKEND}/v2/public/listings?page=0&size=1', xff=f'6.6.6.{i % 250 + 1}, 203.0.113.77')
        if status == 429:
            limited += 1
    print(f'[{label}] 130 次、左邊偽造值每次不同、最右邊固定：被限流 {limited} 次'
          f'（> 0 ＝只認最右邊那一項，偽造的左側項目被忽略）')
    return limited


if __name__ == '__main__':
    label = sys.argv[1]
    result = {'label': label}
    result['A'] = scenario_a(label)
    result['B'] = scenario_b(label)
    result['C'] = scenario_c(label)
    print(json.dumps(result, ensure_ascii=False))
```

