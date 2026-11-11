# Sprint 339 計畫書：跨店鋪公開部落格（`DEF-495`）、前端 `.dockerignore`（`DEF-498`）

> **日期**：2026-11-12
> **性質**：後端（新的匿名公開端點 `GET /v2/public/posts`）＋前端（平台層 `/blog` 改為跨店鋪列表、移除暫時入口與平台層分類頁、舊網址轉址）＋建置設定（`frontend/.dockerignore`，使用者明確同意）＋文件。
> **依據**：使用者在 Sprint 338 收尾時以**互動選項本人選了**（真實的輸入，不是我的推論）：`DEF-498`『新增（建議）』、`DEF-495` 平台層部落格的長期方向『做成跨店鋪部落格』（四個選項裡最大的一個）。
> **前置**：[SPRINT_338_PLAN.md](SPRINT_338_PLAN.md)（暫時入口、`DEF-495`／`DEF-498` 的登記與證據）

## 1. 背景與授權

| 項目 | 使用者的回覆 | 我的處理 |
|---|---|---|
| `DEF-498`：前端沒有 `.dockerignore`（真實目錄建置傳 1.07 GB 上下文、映像混入 macOS 套件） | 互動選項：**新增（建議）** | 做：新增 `frontend/.dockerignore`＋守門測試；用專案真實目錄實測前後對照（§3.2、§6.1） |
| `DEF-495`：平台層 `/blog` 的長期方向 | 互動選項：**做成跨店鋪部落格** | 做：新的匿名端點、重寫 `/blog`、移除 Sprint 338 的暫時入口與平台層分類頁。使用者說過『需要決策時依最合理的方案決策』，所以分類語意、欄位、排序、頁大小、轉址都是我決定的，列在 §10，可一句話推翻 |

**Sprint 338 還沒有 push**：第一次 `git push` 被 pre-push 擋下，原因是環境、不是程式——`act` 的 `Backend Unit Tests` 在 `apt-get install postgresql-client redis-tools` 時容器內 DNS 解析失敗（`Could not resolve 'archive.ubuntu.com'`，exit 100）。本機系統解析器（Tailscale `100.100.100.100`＋路由器 `192.168.0.1`）解析不了 Ubuntu 鏡像站，公共 DNS `8.8.8.8` 能解析。我沒有改你的系統 DNS、沒有用 `--no-verify`。守門失敗後殘留 3 個 `act-*` 容器（已手動清掉），hook 也把 `nk-test-*` 停掉了（之後重新 `make test-db-up`）。所以這一輪的 commit 疊在 Sprint 338 的兩個 commit 之上，DNS 恢復後一起 push。**後來（2026-11-12 約 02:00～02:20）我重測：主機端 `archive.ubuntu.com`、`security.ubuntu.com` 都解析得到（HTTP 200／301），用已核准的 `catthehacker/ubuntu:act-latest` 起容器也解析得到這兩個主機與 `ppa.launchpadcontent.net`**——也就是環境阻礙看起來已自行消失。我沒有改任何 DNS 設定，**不知道恢復的原因**，也不保證不會再發生；push 的結果補在 §6.4。

### 1.1 Docker 政策的合規

只新增 `frontend/.dockerignore`（建置上下文排除清單）。沒有動 image、tag、volume、`profiles`、健康檢查、`deploy.resources`、Redis 設定，也沒有動 `Dockerfile` 與任何 `docker-compose*.yml`。這是 Docker 相關檔案的新增，依政策要明確指示——使用者以互動選項明確同意。

## 2. 方法

沿用 Sprint 316～338：先查證再動手；紅燈先行並確認紅的原因（後端先放骨架讓測試能編譯、再看 `UnsupportedOperationException`；前端先看 `is not a function`／import 失敗）；守門本身用**突變驗證**（前端 10、後端 8），並分清楚『偶然擊殺』與『專門擊殺』；每個開發單元遵守『開發→編譯→單元測試』循環。

## 3. 查證（動手前）

### 3.1 基準

Sprint 338 收尾（本機）：後端單元 2495／整合 1052、Vitest 35 檔 353、完整本地 E2E 守門 387／389（2 個在套件最開頭逾時、單獨重跑通過）。Sprint 338 的兩個 commit（`6db07a0e`、`edfd2b14`）尚未 push。

### 3.2 `DEF-498`：量測（動手前就是 Sprint 338 的實測）

`docker compose -p s338real -f docker-compose.yml build`（專案真實目錄）：`#9 transferring context: 1.07GB 327.9s`；最終映像 333 MB，`/app/node_modules` 85.2 MB，含 `@img/sharp-darwin-x64`、`@img/sharp-libvips-darwin-x64`（macOS 原生套件）。專案根目錄本來就有 `.dockerignore`（排除 `node_modules`、`.next` 等），但 compose 的建置上下文是 `./frontend`，根目錄那一份對它**完全不生效**。`src/` 沒有 import `e2e/`（只有一處註解提到路徑），排除 `e2e` 安全；`frontend/` 沒有被 git 追蹤的 `.env*`。

### 3.3 `DEF-495`：現有程式與先例（動手前讀碼）

- **先例**：`PublicListingController`（Sprint 259，`/v2/public/listings`）——跨店鋪公開商品／房源瀏覽，掛在 `SecurityConfig` 既有的 `/v2/public/**` `permitAll` 下，只回 `ACTIVE` 店鋪的項目。新端點沿用同一個慣例，不動 `/v2/posts` 本身的規則。
- **既有的單店鋪公開列表**：`PostService.getPublishedPosts` 回完整的 `PostResponse`（含內文與作者），而且對每篇貼文再查一次嵌入（N+1）；`PageableUtils.of` 把壞參數**收斂**（page≥0、size 1～上限）而不是拒絕。跨店鋪列表要有不同的資料形狀。
- **資料庫**：`posts` 已有 `idx_posts_status` 與 `idx_posts_published_at`，不需要遷移；`Post.tenant`、`Post.category` 都是 `ManyToOne`，`JOIN FETCH` 不影響資料庫端分頁。`Tenant.TenantStatus`：`PENDING_REVIEW／ACTIVE／REJECTED／SUSPENDED／TERMINATED`。
- **安全設定**：`/v2/public/**` 已是 `permitAll`，端點放在 `PostController`（`@RequestMapping("/v2")`）底下的 `/public/posts` 就自然匿名可讀，也在 `ApiRouteDocDriftTest` 守 M15 文件的範圍內（實測：端點實作後、補文件前，這個測試紅燈，訊息是『api/API_M15_CMS.md 缺少端點：GET /v2/public/posts（Controller 有、文件沒有宣告行）』；補文件後通過，完整 `mvn verify` 的單元階段也包含它）。
- **前端依賴盤點**（`grep`）：`/blog` 與 `/blog/category/[id]` 的原始碼、測試、E2E 全部列出——**五個 E2E 案例依賴舊的『登入者專用、只看自己店鋪』語意**（`E2E-POSTRULE-01`、`E2E-M15-001`、`E2E-M15-005`、`E2E-ARTICLE-07`，以及整份 `at-blog-category-real`），都要改。

## 4. 實作

### 4.1 `DEF-498`：`frontend/.dockerignore`

排除 `node_modules`、`.next`、`tsconfig.tsbuildinfo`（主機的型別檢查快取）、`e2e`、`test-results`、`playwright-report`、`coverage`、`.env*`（Next 會讀 `.env*` 的 `NEXT_PUBLIC_*`，主機的值可能蓋掉建置參數）、`.git`、`.husky`、`*.log`、`.DS_Store`。守門測試併進 `src/architecture/docker-standalone.test.ts`（`node_modules`、`.next`、`.env*` 一定要在清單裡）。

### 4.2 `DEF-495` 後端

- **`GET /v2/public/posts`**（`PostController.getPublicPostFeed` → `PostService.getPublicPostFeed`）：匿名可讀；`page`（預設 0）、`size`（預設 12，收斂到 1～**50**）；回應帶實際使用的值。
- **查詢**（`PostRepository.findPublishedFromActiveTenants`）：`PUBLISHED` 且 `tenant.status = 'ACTIVE'`，店鋪條件**同時寫在列表查詢與計數查詢**；`JOIN FETCH` 店鋪與分類（兩個都是 `ManyToOne`，分頁仍在資料庫端），排序交給 `Pageable`：發布時間新到舊、建立時間新到舊、最後才用編號（只為了分頁穩定；編號是隨機值，不代表時間先後）。
- **只回摘要**（`M15Dto.PublicPostSummaryResponse`）：`id`、`slug`、`title`、`excerpt`、`featuredImageUrl`、`publishedAt`、`tenantSlug`、`tenantName`、`categoryId`、`categoryName`。**刻意不回**內文、作者、瀏覽數、嵌入、狀態、`tenantId`。摘要：貼文自己的 `excerpt`；沒有就取內文開頭 100 字（去掉 `{{embed:listing:…}}`、空白壓成一格、以 code point 計算不切壞表情符號）。
- 不讀貼文嵌入（卡片不需要，查了就是 N+1）。沒有快取（店鋪停權後貼文要立刻消失）。

### 4.3 `DEF-495` 前端

- **`/blog`**：`page.tsx` 改成 server page，解析 `?page=`（1-based → 0-based，壞值當第一頁，與 `/stores/[slug]/blog` 同一慣例）；新的 `PlatformBlogFeed`（client 元件）用 `useAsyncData` 載入，**不依賴登入狀態、不送 `tenantId`**。卡片：封面、標題、摘要、店鋪名稱、分類（`Badge`）、發布日期，連到 `/stores/{店鋪}/blog/{文章}`。載入失敗＝錯誤與重新載入（不畫成『沒有文章』）；**網址頁碼超出範圍**（空清單但總數大於 0）＝『這一頁沒有文章』＋回到第一頁的連結（設計時發現原本會畫成『目前還沒有已發布的文章』，是錯的）。分頁走網址 `router.push(platformBlogPath(N))`。
- **`lib/article-path.ts`**：`platformBlogPath(page)`；**`services/cms.ts`**：`getPublicPostFeed`。
- **移除**：平台層分類頁 `/blog/category/[id]`（page、layout）、Sprint 338 在 `/blog` 上的暫時分類篩選列、`blog-entry.test.tsx`、`blog-load-failure.test.tsx`（舊頁與分類頁的案例；新列表的案例在 `PlatformBlogFeed.test.tsx`）、`load-failure.test.ts` 裡 `/blog` 的登記、`StoreBlogList.sortCategories` 的 `export`（為暫時入口才匯出）。
- **轉址**：`next.config.ts` 加 `/blog/category/:id` → `/blog`（308）。為什麼不轉到對應的店鋪篩選頁：舊網址只有分類編號、沒有店鋪，匿名訪客無從查出它屬於哪間店鋪。守門：`src/architecture/next-redirects.test.ts`。
- **頁尾**：『部落格』連結成為正式入口（註解與測試改寫）。舊文章網址 `/blog/{slug}` 的導向頁**不動**（仍是登入者專用；其中的『返回部落格』現在回到公開的 `/blog`）。
- **E2E**：新規格 `at-platform-blog-real.spec.ts`（5 案例，真實後端）取代 `at-blog-category-real.spec.ts`；`E2E-POSTRULE-01`、`E2E-M15-001`、`E2E-M15-005` 改用**該店鋪自己的**貼文列表 `/stores/{店鋪}/blog`（確定性，不受全套並行時別的規格也在建立貼文的影響）；`E2E-ARTICLE-07` 改成『跨店鋪、匿名』；`at-page-titles` 的 `/blog/category/x` 條目換成 `/blog`。

### 4.4 文件

`API_M15_CMS.md`（總覽表新增 `API-M15-050`、§4.50、§7 的已解決與新限制）、`API_Index.md`、PRD v1.0.17（版本註記、`/blog` 與 `/blog/category/[id]` 路由列、§9.15 端點表）。

## 5. 突變驗證結果

驅動 `scratchpad/mutate339.py`（與 Sprint 338 同款：只用 ASCII 的突變字串、每次必定還原並逐位元比對；後端整合測試以輸出判定擊殺，因為 `failsafe -q` 有失敗時也可能回 exit 0）。

### 5.1 前端單元層：10 個突變，全數擊殺

| # | 突變 | 擊殺 |
|---|---|---|
| T1 | 卡片連結用舊形狀 `/blog/{slug}` | 『卡片連到那間店鋪的文章頁』 |
| T2 | 錯誤分支永遠不成立 | 2 案例（『載入失敗不能畫成沒有文章』『重新載入』） |
| T3 | 拿掉『頁碼超出範圍』分支 | 超出範圍案例 |
| T4 | 分頁導向錯誤頁碼 | 2 案例 |
| T5 | 每頁 12 → 20 | 2 案例 |
| T6 | server page 不做 1-based → 0-based | 3 案例 |
| T7 | 拿掉分類頁轉址 | 轉址案例 |
| T8 | `.dockerignore` 少了 `.next` | 守門案例 |
| T9 | `platformBlogPath` 把第 1 頁也當頁碼 | 工具案例 |
| T10 | `getPublicPostFeed` 打 `/v2/posts` | 服務案例 |

### 5.2 後端：8 個突變，全數擊殺（其中 1 個要先強化測試）

| # | 突變 | 擊殺 |
|---|---|---|
| S1 | 列表查詢拿掉店鋪 `ACTIVE` 條件 | `IT-M15-032`（停權後貼文要消失） |
| **S2** | **計數查詢拿掉店鋪 `ACTIVE` 條件** | **第一次倖存**；強化後 `IT-M15-032` 擊殺（見下） |
| S3 | 每頁上限 50 → 100 | 單元（收斂案例） |
| S4 | 發布時間改升冪 | 單元（排序案例） |
| S5 | 回應回請求的 size 而不是實際使用的 | 單元（收斂案例） |
| S6 | 摘要不去掉嵌入語法 | 單元（摘要案例） |
| S7 | 摘要 DTO 長出 `content` 欄位 | 單元（反射守門：回應型別不能有內文、作者、瀏覽數…） |
| S8 | 預設每頁 12 → 20 | `IT-M15-032`（預設值案例） |

**S2 倖存的原因與修正**：Spring Data 在『第一頁沒填滿』（返回筆數小於每頁大小）時，直接拿返回筆數當總數、**根本不執行計數查詢**。整合測試原本用 `size=50`，測試庫裡不到 50 篇，計數查詢從沒被執行過，所以它的店鋪條件寫錯也測不出來。修正：總數一律用 `size=1` 取（頁一定是滿的，計數查詢才會跑），停權前、停權中、恢復後各取一次。強化後的測試在沒突變時通過（完整 `mvn verify` 的整合階段 1053／0 失敗）、S2 被擊殺：**寫這份文件時重跑 `mutate339.py backend S2`，結果 `KILLED`（139 秒；`IT-M15-032` 失敗），結束後 `git status` 的 backend／frontend 為空，確認已還原**。
**偶然擊殺 vs 專門擊殺**：每個突變都對回『專門為那條規則寫的案例』（上表），不是靠順帶失敗。

## 6. 後置驗證

### 6.1 `DEF-498` 實測（專案真實目錄，`docker compose -p <名稱> -f docker-compose.yml build frontend`；第一次專案名 `s339real`、寫文件時重測專案名 `s339verify`）

| 指標 | 之前（Sprint 338 實測） | 之後 |
|---|---|---|
| 建置上下文 | **1.07 GB**，傳輸 327.9 秒 | 第一次量：**32.02 kB**，傳輸 31.3 秒（機器當時仍忙）；寫文件時用最終程式碼重測：**86.94 kB**（快取命中，0.3 秒）。後者較大，**推測**是第一次量測時前端的新程式碼與測試還沒寫完（推論，沒有逐檔核對） |
| 最終映像 | 333 MB | **303 MB**（重測時用 `docker images` 再確認；與專案副本建的相同） |
| `/app/node_modules` | 85.2 MB（**當時終端機輸出，沒有留存日誌，寫文件時無法重新核對**） | **65.4 MB**（重測時 `docker run … du -sh /app/node_modules` 再確認） |
| macOS 原生套件（`@img/sharp-darwin-x64`、`sharp-libvips-darwin-x64`） | 有（Sprint 338 實測） | **沒有**：重測時 `/app/node_modules/@img` 只有 `colour`、`sharp-libvips-linux-x64`、`sharp-libvips-linuxmusl-x64`、`sharp-linux-x64`、`sharp-linuxmusl-x64`、`sharp-wasm32`，`/app` 下 `find -iname '*darwin*'` 沒有任何結果 |
| 建置 | 成功 | 成功（第一次容器內 `next build` 編譯 113 秒、機器忙；重測 28.7 秒、機器較空） |

守門測試先紅（檔案不存在）後綠，突變 T8 擊殺。兩次實驗映像（`s339real-frontend`、`s339verify-frontend`）都已指名刪除（不加 `-a`）。

### 6.2 E2E

- **新規格與我改過的規格**（`at-platform-blog-real`、`at-article-titles-real`、`at-post-slug-and-delete-rule-real`、`at-m15-e2e`、`at-page-titles`、`at-cms-redirect`、`at-ui-design-base`）：**60 個案例全過**（1.5 分）。
- **新規格的第一個設計請求太多**：我用 `findPageOf` 逐頁（每頁 12 筆）掃公開列表，還為了證明草稿『整份列表都沒有』而掃到底——在累積很多貼文的資料庫裡會打出一長串匿名請求。後端對**每個來源 IP 有 100 次／分的限流桶**（`RateLimitFilter`：真正的店鋪租戶按租戶計桶；沒有店鋪的使用者按使用者、匿名按來源 IP；`/v2/auth/*`、`/actuator/*` 不計），E2E 的匿名請求（瀏覽器與 Next 伺服器端抓取）全從同一個 IP 出去。改成一次讀 50 筆（後端上限）、從全局位置算頁碼、讀到要的貼文就停；草稿『整份列表都不出現』留給後端整合測試驗證。
- **一個我沒碰過的規格被限流擊敗**（`at-store-blog-category-real`，Sprint 337 的店鋪層規格）：第一次定向執行（與新規格並行）`E2E-STOREBLOGCAT-04` 失敗；單獨重跑 `E2E-STOREBLOGCAT-06` 失敗——頁面顯示『載入失敗，請稍後再試』，後端日誌 `RateLimitFilter … Bucket ratelimit:ip:0:0:0:0:0:0:0:1 exceeded rate limit`（10 次）。**對照**：干淨的 Sprint 338 版本單獨跑同一個規格**通過**（130 個請求、0 次限流）。逐端點比對（**寫文件時用原始後端日誌重算過**）：Sprint 338 那次 130 個請求中 `GET /v2/public/stores/{slug}` 63、`GET /v2/posts/{slug}` 24；Sprint 339 失敗那次 159 個請求中分別是 79、38，也就是多了 `GET /v2/public/stores/{slug}` +16、`GET /v2/posts/{slug}` +14（這兩個端點是店鋪前台與文章頁的資料來源，Next 伺服器端 `generateMetadata` 也會抓；哪些是伺服器端發的、哪些是瀏覽器端發的，我沒有逐筆區分）。**再重跑**：Sprint 339 版本單獨跑**通過 2 次**，我當時記錄的是 145、128 個請求、0 次限流；其中 128 個請求那次的結果留存在對照腳本的輸出（機器負載 5.61，已重新核對），**145 那次與『失敗那次機器負載 30』的日誌沒有留存，寫文件時無法重新核對**。**結論（推論，樣本少）**：這個規格的請求量（128～159 個／20～35 秒）貼近 100 次／分的 IP 桶，是否被限流隨機器忙閒而變（推測是 Next 連結預取的完成數隨之變動，**未實測**），**沒有證據顯示 Sprint 339 讓請求量系統性變多**（這個規格相關的頁面程式碼這輪沒有改）；但樣本太少，也不能說完全無關，所以登記 `DEF-499`，沒有動限流政策。
- **連『60 案例全過』的那次，後端日誌也記到來源 IP 桶被擋 6 次**（用請求編號反查原始日誌：4 次 `GET /v2/public/stores/{slug}`、2 次 `GET /v2/posts/{slug}`），沒有造成任何案例失敗（為什麼沒失敗我沒有逐一查證）；那次共 444 個後端請求，其中 `GET /v2/public/stores/{slug}` 113、`GET /v2/posts/{slug}` 79。第一次（66 案例，含店鋪層規格）記到 9 次。所以『IP 桶被擠爆』不只發生在店鋪層規格，是整個 E2E 套件（單一來源 IP）的常態風險，多數時候不構成失敗。我沒有量測 Sprint 338 基準的完整套件在這一點上的數字（當時沒有留存後端日誌），所以**不能說這輪讓它變多或變少**。
- 我另外做的對照腳本有兩個疏失：第一版沒等埠釋放、五次執行無效；第二版的『等埠』也沒生效，又無效。最後以『339 版本 3 次、338 版本 1 次』的樣本下結論（§8.2）。

### 6.3 本機完整驗證

| 項目 | 結果 | Sprint 338 基準 |
|---|---|---|
| 後端 `mvn -o clean verify`（單元＋整合＋Checkstyle＋PMD） | 單元 **2505／0 失敗**（+10＝`PostServiceTest.GetPublicPostFeed`）、整合 **1053／0 失敗**（+1＝`IT-M15-032`）、Checkstyle（main、test）各 0 違規、PMD 通過、`BUILD SUCCESS`，總耗時 22 分 28 秒 | 單元 2495／整合 1052，耗時 46 分（兩次的機器負載不同，耗時不能直接比） |
| 前端 Vitest | 36 檔 **359 案例**全過 | 35 檔 353 案例 |
| `tsc --noEmit` | 結束碼 0 | — |
| ESLint | 結束碼 0 | — |
| 完整本地 E2E 守門（`make validate-e2e`） | **389 案例全過**（9.6 分，0 失敗），認證端點被限流 0 次；案例總數與基準相同（刪掉舊 `at-blog-category-real` 的 5 案例、新增 `at-platform-blog-real` 的 5 案例）。守門結束時腳本會刪掉後端日誌，所以**完整套件的來源 IP 限流次數沒有數字**（§6.2 的 6 次是 60 案例那次的） | 389 案例＝387 過＋2 個在套件最開頭逾時（單獨重跑過），19.5 分 |

### 6.4 push 與雲端 CI

Sprint 338 的兩個 commit 與本輪 commit 一起 push：`03b8f314..22800e61`，共 5 個 commit（Sprint 337 的純文件回填 `ac6203b9`、Sprint 338 的 `6db07a0e`＋`edfd2b14`、Sprint 339 的 `b8b1aaa1`＋`22800e61`）。pre-push 的輕量守門（`act` 的 Backend Unit Tests、Frontend Lint & Build、schema 漂移守門）這次全過，先前的 DNS 失敗沒有再出現（我沒有改任何設定，原因不明）。

**雲端 CI**：run [43081196174](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/43081196174)，`gh run view --json` 的結論是 `completed／success`，headSha `22800e61` 與本機 HEAD 一致，三個 job（Backend Unit Tests、Frontend Lint & Build、Backend Integration Tests & Package）皆 success，且各 job 都沒有非 success／skipped 的步驟（`gh run watch --exit-status` 先回報完成，但它在網路瞬斷時會提早 exit 0，所以另用結構化查詢交叉確認）。**雲端日誌的數字**：整合 **1053／0 失敗／0 略過**（Checkstyle 0 違規、`BUILD SUCCESS`、`mvn verify` 9 分 54 秒；`PostControllerE2ETest` 29／0）、前端 Unit Tests **36 檔 359／0**（ESLint `--max-warnings 0` 與 `next build` 通過）。後端單元總數不在 CI 日誌（只有本機 verify 的 2505／0）。雲端**沒有 e2e job**（`act-compat.yml` 只有上述三個 job），所以完整 E2E 守門 389／389 只有本機這一份證據（Sprint 338 計畫書曾誤寫成雲端有，已在該計畫書 §6.3 更正）。

## 7. 行為變更與已知限制

### 7.1 行為變更

| 項目 | 之前 | 現在 | 影響誰 |
|---|---|---|---|
| 平台層 `/blog` | 登入且選定店鋪的人看到**那一間店鋪**的貼文，匿名訪客只看到登入提示 | **所有營業中店鋪**的已發布貼文，匿名可讀，新到舊，每頁 12 筆，`?page=` 分頁，卡片帶店鋪名稱 | 所有訪客；店主不再用 `/blog` 看自己店鋪的貼文（用 `/stores/{店鋪}/blog`） |
| 新端點 `GET /v2/public/posts` | 無 | 匿名可讀的跨店鋪列表（只回摘要） | API 使用者 |
| 平台層分類頁 `/blog/category/[id]` | 登入者專用的分類篩選頁 | **移除**，舊網址 308 轉到 `/blog` | 有書籤的人 |
| Sprint 338 的暫時分類篩選列（`/blog` 上方） | 有 | 移除（跨店鋪列表沒有分類） | — |
| 頁尾『部落格』連結 | 暫時入口 | 正式入口 | 所有訪客 |
| 前端建置上下文 | 1.07 GB、混入 macOS 套件 | 32 kB、乾淨 | 部署／開發者 |

### 7.2 已知限制（誠實列出）

1. **列表在瀏覽器端載入**：`PlatformBlogFeed` 是 client 元件，server page 只解析 `?page=`，依 `useAsyncData` 的行為推論，初始 HTML 只有標題與載入中畫面、不含文章卡片（**這是推論：沒有 `curl` 實測初始 HTML，也沒有實測爬蟲的行為**）；不執行 JavaScript 的爬蟲預期看不到內容。要改成伺服器端渲染是另一件工作 → `DEF-500`。
2. **沒有篩選、搜尋、排序切換**：只有分頁；分類是店鋪內的概念，要篩分類請進該店鋪的 `/stores/{店鋪}/blog?category=`。
3. 卡片顯示店鋪名稱但**不連到店鋪**（整張卡片是文章連結，不能在連結裡再放連結）。
4. 摘要在沒有手寫 `excerpt` 時取內文開頭 100 字，內文若是 Markdown，標記符號會原樣出現（單店鋪列表本來就是這樣）。
5. **大量貼文下的查詢耗時沒有量測**（有 `idx_posts_published_at`，但跨租戶的 `status`＋`tenant.status` 過濾沒有專用索引）；沒有快取。
6. **舊文章網址 `/blog/{slug}` 的導向頁仍是登入者專用**（匿名訪客看到登入提示）；它沒有店鋪資訊，匿名者無從導向。
7. **匿名請求共用每來源 IP 100 次／分的限流桶，E2E 的店鋪層規格貼近上限**（§6.2，`DEF-499`）。
8. **後端整合測試的總數必須用 `size=1` 取**（§5.2 S2）：Spring Data 在第一頁沒填滿時不執行計數查詢，之後寫類似的分頁整合測試要記得。
9. 寫這份文件、建立文件 commit 時，Sprint 338 與 Sprint 339 都**還沒 push**（原因見 §1，之後環境已恢復）；push 的實際結果見 §6.4。

## 8. 預測對答案與失誤

### 8.1 預測 vs 結果

**先講證據力**：這輪也沒有把『每個突變預期紅的案例集合』先寫進驅動腳本（驅動只判斷擊殺與否並列出失敗案例）；預測是規劃時心裡的判斷、事後對照，證據力比 Sprint 337 弱。

| 預測 | 結果 |
|---|---|
| `.dockerignore` 會讓上下文降到 MB 以下、映像不再有 macOS 套件 | **一致**：32 kB、303 MB、沒有 `sharp-darwin-*`（甚至比我預期的好） |
| 後端新案例實作前紅（`UnsupportedOperationException`）、實作後綠 | **一致**（9 紅 1 綠；1 個反射守門本來就過） |
| 整合測試實作前紅（路由不存在）、實作後綠 | **一致**（`GET /public/posts` 先 404，加上端點後通過） |
| 後端 8 個突變全數擊殺 | **沒有**：S2（計數查詢）倖存，原因是 Spring Data 不執行計數查詢（§5.2） |
| 新規格一次全綠 | **一致**（5／5），但設計上請求太多，事後才發現（§6.2） |
| 我沒碰過的店鋪層規格不受影響 | **錯**：它被 IP 限流擊敗（§6.2）；結論只到『負載相關的臨界點；沒有證據顯示是我造成的，但樣本太少，不能排除』，而且我先前沒有預見整個 E2E 套件都在共用同一個來源 IP 的限流桶上 |
| 前端全量測試一次全綠 | **一致**（36 檔 359） |

### 8.2 失誤與修正（誠實列出）

1. **新規格的第一版掃整份列表**：逐頁掃、還為了證明草稿不存在而掃到底。在累積很多貼文的資料庫裡這是一長串匿名請求，擠到共用的 IP 限流桶。第一次執行全過所以我沒發現，是分析店鋪層規格被限流時才回頭看。已改成一次 50 筆、讀到要的就停。
2. **整合測試沒有執行計數查詢**：總數用 `size=50` 取，測試庫貼文不到 50 篇，Spring Data 直接用返回筆數當總數。是突變驗證（S2 倖存）才抓到；已改用 `size=1`。
3. **對照腳本寫了兩次都沒生效**：第一版沒等埠釋放，第二版的『等埠』用 `lsof -ti tcp:3000 -ti tcp:8080` 判斷，仍然在第二次執行就遇到 `port 8080 已被占用`。我最後放棄把樣本補滿，改以現有樣本（339 版本 3 次、338 版本 1 次）如實下結論，沒有把『通過 2 次、失敗 1 次』說成『沒問題』或『有問題』。
4. **`tsc` 讀到舊建置產物**：刪掉分類頁後，`tsc` 報 `.next/types/validator.ts` 找不到那個頁面——那是被 git 忽略的舊建置產物，不是原始碼問題；清掉 `.next/types` 就好。（記下來因為會讓人誤以為刪頁面破壞了型別。）
5. **沒預見 E2E 對舊 `/blog` 語意的依賴有五個案例**：是動手前 `grep` 全部 E2E 才列全的；先 `grep` 再改，沒有踩到。
6. **Sprint 338 的 push 受阻**（環境：DNS），疊了一個 Sprint 的 commit 沒推出去，風險是兩個 Sprint 的變更混在同一次 push 裡。

## 9. 新登記的相鄰缺陷（只登記，不在本輪修）

| 編號 | 內容 | 為什麼不在本輪修 |
|---|---|---|
| `DEF-499` | 匿名請求共用每來源 IP 100 次／分的限流桶；店鋪前台每次瀏覽會放大成多個後端請求（伺服器端 `generateMetadata` ＋ 客戶端載入 ＋ 可能的連結預取），`at-store-blog-category-real` 單獨跑貼近上限，隨機器忙閒而時過時敗 | 改限流政策或預取行為是產品／效能決定，要先量測放大倍率；對真實使用者的影響（同一個出口 IP 的多位訪客）只是推論 |
| `DEF-500` | 跨店鋪部落格的後續：伺服器端渲染（SEO）、篩選／搜尋、大量貼文下的耗時量測、卡片連到店鋪 | 使用者要的是跨店鋪列表本身；後續是新功能，需要產品決定 |

## 10. 我替使用者做的決定（可一句話推翻）

1. **端點叫 `GET /v2/public/posts`、放在 `PostController`**（不新增控制器）：沿用 `PublicListingController` 的 `/v2/public/**` 慣例，也在 `ApiRouteDocDriftTest` 已守的 M15 範圍內。
2. **只回摘要、不回內文與作者**：匿名可讀、橫跨所有店鋪的端點，能不公開的不公開；列表也不需要全文。
3. **每頁預設 12、上限 50**（單店鋪是 100）：12 與畫面一致；上限比單店鋪小，因為這個列表橫跨所有店鋪。
4. **排序**：發布時間新到舊、再建立時間新到舊、最後才用編號（編號只為了分頁穩定，不代表時間先後）。
5. **沒有分類篩選、沒有搜尋**：分類是店鋪內的概念，跨店鋪沒有明確語意；要篩分類進該店鋪的列表。這是你沒回答的那一半（『跨店鋪怎麼分要先定』），我選擇『不分』。
6. **移除平台層分類頁與 Sprint 338 的暫時分類篩選列**，舊分類網址 308 轉到 `/blog`（不轉到店鋪篩選頁：舊網址沒有店鋪，無從查）。
7. **沒有快取**：店鋪停權後貼文要立刻消失；代價是每次請求都查資料庫（§7.2 第 5 點）。
8. **列表在瀏覽器端載入**（不做伺服器端渲染）：與店鋪層列表同一個做法與錯誤處理；SEO 登記為 `DEF-500`。
9. **店主不再用 `/blog` 看自己店鋪的貼文**：用 `/stores/{店鋪}/blog`；三個 E2E 案例因此改用店鋪自己的列表。
10. **頁碼超出範圍**顯示『這一頁沒有文章』＋回到第一頁（而不是『目前還沒有已發布的文章』）。
11. **`.dockerignore` 的內容**由我決定（§4.1）；沒有排除 `src` 下的測試檔（排除它們可能讓型別檢查少看一些檔案，不值得冒險）。
12. **沒有動限流政策**（`DEF-499` 只登記）：那是產品決定，不是我該順手改的。

## 11. 狀態

| 項目 | 狀態 |
|---|---|
| `DEF-495` 跨店鋪公開部落格 | ✅ 已做並實測（你選了『做成跨店鋪部落格』）；追蹤表已結案；分類語意等 12 項由我決定，見 §10 |
| `DEF-498` 前端 `.dockerignore` | ✅ 已做並實測（你選了『新增（建議）』）；追蹤表已結案 |
| 新登記 | `DEF-499`（匿名來源 IP 限流桶／前台請求放大）、`DEF-500`（跨店鋪部落格後續） |
| 程式 commit | `b8b1aaa1`（已 push） |
| 文件 commit（本計畫書、追蹤表、發布追蹤、Sprint 338 計畫書的指向備註） | `22800e61`（已 push） |
| Sprint 338 的兩個 commit | `6db07a0e`、`edfd2b14`（已 push，與本輪一起） |
| push、雲端 CI | ✅ 已 push（`03b8f314..22800e61`），雲端 CI 全綠（run 43081196174），見 §6.4；本回填 commit 留在本機，隨下一個 Sprint 一起 push |
| 待你決定 | 沒有必須決定的項目。§10 的 12 項是我的預設，要推翻請一句話告訴我；`DEF-499`（要不要先量測前台請求放大倍率）與 `DEF-500`（要不要做伺服器端渲染／SEO）是之後的方向題 |
