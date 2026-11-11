# Sprint 338 計畫書：正式前端映像建得出來（`DEF-493`）、平台層部落格暫時入口（`DEF-491`）、貼文分類同名規則（`DEF-492`）、API 索引過期列（`DEF-494`）

> **日期**：2026-11-11
> **性質**：前端（`next.config.ts` 的 `output`、頁尾「部落格」連結、`/blog` 的分類篩選列、分類管理頁的錯誤訊息）＋後端（`PostCategoryService` 的同名規則與網址代稱、新錯誤碼 `E-8021`）＋ Docker 設定（`docker-compose.yml` 前端健康檢查的指令，使用者以互動選項明確同意）＋文件。
> **依據**：使用者貼回 Sprint 337 收尾總結，在「要你決定」三項後以 `==>` 內嵌回覆——`DEF-491`：『請先製作暫時入口暫代』；`DEF-492`：『請提供我優缺點情境比較』；`DEF-493`：『請執行』——並說明『若有需要我決策情況，請依照最合理化、最佳化、最理想、最適當的解決方幫我決策（不需要顧慮成本），若真需要我決定，請以最簡單的解釋，讓我互動選項決策』。
> **前置**：[SPRINT_337_PLAN.md](SPRINT_337_PLAN.md) §6.3、§7.2、§9（`DEF-491`～`494` 的登記與證據）

## 1. 背景與授權

| 項目 | 使用者的回覆 | 我的處理 |
|---|---|---|
| `DEF-491`：平台層 `/blog`、`/blog/category/[id]` 成了沒有入口的孤兒頁 | `==> 請先製作暫時入口暫代` | 做：頁尾「部落格」連結＋`/blog` 上方的分類篩選列（兩頁都有入口）；正式去留另登記 `DEF-495`。為什麼這樣解讀、放在哪裡，見 §3.2、§10 |
| `DEF-492`：分類名稱只差中文時網址代稱重複；『完全同名』要不要擋是產品語意 | `==> 請提供我優缺點情境比較` | 用 `AskUserQuestion` 給兩個選項（每個選項附情境對照表），**使用者本人選了建議項「擋下並提示」**（真實的互動輸入，不是我的推論）。代稱撞到自動加後綴不需要決定（兩個選項都有），先實作；同名規則依選擇實作 |
| `DEF-493`：正式前端映像建不出來 | `==> 請執行` | 做：`next.config.ts` 加 `output: 'standalone'`，並實測正式與 CI 模擬的 compose。實測發現容器永遠 unhealthy（健康檢查的 `localhost` 在容器內解析成 IPv6），**這超出『請執行』的範圍**（要改 `docker-compose.yml` 的健康檢查指令），所以另用 `AskUserQuestion` 取得同意，**使用者本人選了「改，套用」** |
| `DEF-494`：`API_Index.md` 一列過期 | 不需要決定 | 直接做，並順手核對索引其他列（§3.5） |
| 全域授權：需要決策時依最合理、最佳方案決策 | （上面那段話） | 細節我自己決定，列在 §10，可一句話推翻；只有產品語意（同名分類）與 Docker 設定（政策要求明確指示）兩件事用互動選項問 |

### 1.1 Docker 政策的合規

- **改了什麼**：`frontend/next.config.ts`（不是 Docker 檔，但決定映像能不能建出來）；`docker-compose.yml` 前端服務的健康檢查**指令**（`localhost` → `127.0.0.1`，一個字；使用者以互動選項明確同意）。
- **沒有動**：image、tag、volume、`profiles`、健康檢查的間隔／逾時／重試／啟動寬限、`deploy.resources`、Redis 設定。`DOCKER_POLICY.md` 沒改（它的架構圖寫『frontend ← node:20-alpine (runner target，standalone build)』，修復後與現況一致了，不需要校正）。
- **實驗隔離**：所有實驗用專案副本（`rsync` 排除 `.git`、`node_modules`、`.next`、`target`、`.env*`、`docs`）＋獨立專案名稱 `s338check`，結束用 `down -v --remove-orphans` 與 `docker rmi s338check-frontend s338check-backend`（指名刪，不加 `-a`），沒有留下卷或映像。

## 2. 方法

沿用 Sprint 316～337：先查證再動手；**紅燈先行並確認紅的原因**（`DEF-493` 先重現建置失敗；各項測試都先對沒改的程式跑，看失敗訊息對不對）；守門本身用**突變驗證**（前端 10 個、後端 11 個，預期紅燈集合在跑之前寫進驅動腳本）；把『偶然擊殺』與『專門的案例擊殺』分開看（§5.2）；最後對**正式映像堆疊**跑真實規格與對照組。每個開發單元遵守『開發→編譯→單元測試』循環（CLAUDE.md）。

## 3. 查證（動手前）

### 3.1 基準

Sprint 337 收尾（雲端 CI 全綠，run 42882950938）：後端單元 2479／整合 1051、前端 Vitest 31 檔 337 案例、本地 E2E 守門 387／387；`flyway` 最新遷移 `V98`。起始時 `git status` 乾淨，本機領先 `origin/main` 1 個 commit（`ac6203b9`，Sprint 337 的純文件回填）。Docker：0 個容器、5 個具名卷、3 個核准映像、建置快取 5.093 GB。

### 3.2 `DEF-491`：怎麼解讀『暫時入口』、放哪裡

- **孤兒狀態重新 `grep` 確認**（生產碼，不含測試）：指向 `/blog` 的連結只剩兩處，都在這兩個保留頁內部（分類頁的『← 返回部落格』、舊文章網址 `/blog/[slug]` 導向頁的『返回部落格』）；`StoreNav` 的 `/blog` 是店鋪層 `/stores/{店鋪}/blog` 的後綴，不是它。分類頁沒有任何連結指向它。
- **解讀**：使用者原話是『請先製作暫時入口暫代』。我解讀為『先讓這兩頁在畫面上點得到，正式的去留之後再決定』（不是移除，也不是現在就做跨店鋪部落格）。這也解釋了為什麼不選我原本建議的『保留現狀』。
- **放哪裡**：
  - `/blog`：**前台頁尾**加『部落格』連結。頁尾是每個前台頁（含賣家外殼）都有的全站區塊，慣例上放平台層資訊連結（隱私權政策、服務條款、聯絡客服在同一排），視覺份量低，符合『暫時』，也不動 Sprint 328 統一過的頁首。
  - `/blog/category/[id]`：`/blog` 上方加**分類篩選列**（『全部』＋店鋪的分類，點分類進分類頁）。兩頁才都算有入口。
- **為什麼用 `Chip`＋`router.push` 而不是連結**：`Chip` 是 `<button>`，包 `<Link>` 會變成互動元素巢狀；`StoreBlogList`（Sprint 337）就是這個做法，一致。
- **撞字盤點**（Sprint 328 起共用頁首／頁尾的文字碰撞是已知坑）：`grep` 全部 E2E 與單元測試對『部落格』的引用，沒有任何一個用 `getByText('部落格')`／`getByRole('link', { name: '部落格' })` 去找頁首或頁尾（有的是 `toHaveTitle`、整句『請先登入並設定店鋪以瀏覽部落格』、『返回部落格』）；頁尾連結已有前例並用 `getByRole('article')` 或 `getByTestId('storefront-footer')` 限定範圍（`at-embed-card-status`、`at-legal-pages`、`at-page-titles`）。
- **限制（誠實）**：這兩頁仍是登入者專用（`tenantId` 取自 localStorage），所以匿名訪客點頁尾連結只會看到『請先登入並設定店鋪以瀏覽部落格』。這是『暫時』的代價，不是疏漏；改善要等 `DEF-495` 的決定。

### 3.3 `DEF-492`：現況其實不一致，以及兩個選項

讀 `PostCategoryService`：`createCategory` 用 `generateSlug`（只留 `[a-z0-9\s-]`，中文被濾掉），代稱已存在就丟 `E-8014`；`updateCategory` 用 `generateUniqueSlug`（撞到接 8 碼隨機後綴）。資料庫只有 `UNIQUE (tenant_id, slug)`，沒有名稱唯一約束。推論現況：

| 名稱 | 代稱 | 現況 |
|---|---|---|
| `旅遊筆記2026`、`咖啡手帖2026`（名稱不同，只差中文） | 都是 `2026` | **第二個起 409 `E-8014`**（缺陷本身） |
| `Food` 連建兩次 | 都是 `food` | 第二次 409（代稱相同，順便擋住同名） |
| `美食` 連建兩次（全中文） | 各自 `category-<隨機>` | **都能建立**（Sprint 334 `DEF-480` 之後；同名不被擋） |

所以『完全同名』現況是**英文被擋、全中文不擋**，不一致。給使用者的兩個選項（每個附情境對照）：

| 選項 | 內容 | 結果 |
|---|---|---|
| **擋下並提示（建議）** | 名稱相同（忽略大小寫與前後空白）→ 409，提示『分類名稱已存在』；名稱不同但代稱撞到 → 自動加後綴 | **使用者本人選了這一項** |
| 允許同名 | 只處理代稱；名稱不限制。程式改動最小，但前台篩選列可能出現兩個一模一樣的名稱，英文同名由『擋』變『允許』（行為變更） | — |

**先例**：`MediaService` 的媒體分類同名檢查（`E_8017`「媒體分類名稱重複」，建立與改名都擋）；比對方式不同（它是精確比對、靠資料庫部分唯一索引兜底），我依使用者的描述改成『忽略大小寫與前後空白』。

### 3.4 `DEF-493`：重現、Next 文件與原始碼查證

- **重現（紅燈基準）**：對專案副本 `docker compose -p s338check -f docker-compose.yml build frontend` → **結束碼 1**：`Dockerfile:49 COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./ … "/app/.next/standalone": not found`。前面的 `deps`／`builder` 階段是 Sprint 337 的快取命中，所以幾秒就失敗，與 Sprint 337 的實測一致。
- **Next 16.4.2 內附文件（`node_modules/next/dist/docs/…/output.md`）**：`output: 'standalone'` 會產生 `.next/standalone`（含最小的 `server.js`）；`public` 與 `.next/static` 預設不複製，要自己複製——`frontend/Dockerfile` 已經這麼做。
- **`next start` 的相容性（原始碼 `server/next.js`）**：設了 `output: 'standalone'` 時，`next start` 只 `log.warn`（『does not work with "output: standalone" configuration』），**只有 `output: 'export'` 才丟錯**。這很重要，因為本地 E2E 守門（`scripts/validate-e2e.sh`）與雲端 CI 都是 `npm run build` ＋ `npm run start`，不經 Dockerfile。實測見 §6.1。
- **其他引用**：全專案搜尋 `standalone` 只有 `validate-e2e.sh` 檔頭的註解（『需 Next standalone 但 next.config 未啟用』）與 `DOCKER_POLICY.md` 的架構圖；沒有別的腳本依賴 `.next/standalone`。
- **`rewrites` 的寫死主機**：`next.config.ts` 的 `rewrites` 把 `/api/:path*` 轉到 `http://ecommerce-backend-dev:8080/api/…`（只在開發堆疊成立）。`standalone` 會把設定序列化進 `server.js`，但前端所有 API 請求都用絕對網址 `NEXT_PUBLIC_API_URL`（`lib/api.ts`，`grep` 沒有相對 `/api` 的請求），所以這條規則在正式環境不會被用到；**不在本輪範圍，只記錄**（§7.2）。

### 3.5 `DEF-494`：核對索引其他列

寫了一支腳本（`scratchpad/index_check.py`，不進專案）：抽出 `API_Index.md` 的 121 個 `/api/v2/…` 路徑，檢查後端 controller 的 `*Mapping` 字串有沒有對應。標出 12 個；**逐一用固定字串 `grep` 驗證後，11 個是腳本誤判**（`:id` 寫法、類別層＋方法層路徑拼接，後端都有對應的 Mapping），**只有 `/api/v2/dashboard/media/*` 真的過期**（Sprint 334 `DEF-474` 移除）。順帶發現 `docs/01_requirements/M18_Knowledge_Management_SPEC.md` 的端點表也寫 `/api/v2/dashboard/media/*`（需求規格，不是索引，不在 `DEF-494` 範圍）→ 登記 `DEF-496`。

## 4. 實作

### 4.1 `DEF-493`：`output: 'standalone'`、守門測試、健康檢查

- `frontend/next.config.ts`：加 `output: 'standalone'`＋註解（原因、與守門測試的關係、`next start` 的警告）。
- **守門測試** `src/architecture/docker-standalone.test.ts`（3 案例）：①Dockerfile 的 `runner` 階段有一行真的 `COPY --from=… /.next/standalone`（註解掉的不算）；②`next.config.ts` 匯出的設定 `output === 'standalone'`（直接匯入設定，不用正規表示式掃原始碼，註解裡出現這幾個字不會讓它誤過）；③偵測器自己的正反例。**為什麼要有它**：這兩個檔案不一致了約半年沒人發現（Dockerfile 最後修改 2026-05-10），因為沒有任何自動化流程會建那個映像。
- `docker-compose.yml` 前端服務的健康檢查指令 `localhost` → `127.0.0.1`（§6.1 的根因與實測；使用者以互動選項同意）。
- `scripts/validate-e2e.sh`：只改檔頭註解——原本說 Dockerfile `runner` target 已 bit-rot，現在已修並實測，所以只留『雲端 CI 不經 Dockerfile』這個理由，並註記 `next start` 會印警告。

### 4.2 `DEF-491`：暫時入口

- `StorefrontFooter.tsx`：『部落格』連結（排在隱私權政策前面）＋註解（暫時、`DEF-495`）。
- `blog/page.tsx`（平台層清單頁）：多載入一份分類（`getPublicCategories`），有分類才在清單上方顯示篩選列（『全部』為選取態＋各分類，點分類 `router.push('/blog/category/{id}')`）；**分類是附加功能**：載入失敗或沒有分類就不顯示篩選列，文章照常，不為它另畫錯誤畫面。`StoreBlogList` 的 `sortCategories` 改為匯出共用（依 `sortOrder` 再依名稱）。
- `load-failure.test.ts`（Sprint 332 的架構守門）：這個新的載入點失敗是靜默的，被守門抓到（§8.2）；依它的規則**登記為『輔助資訊』**（同類先例：`faq`／`knowledge`／`media` 頁的分類篩選選項），數量 1、理由寫明暫時與 `DEF-495`。

### 4.3 `DEF-492`：同名規則與代稱

- **後端**：`ErrorCode` 新增 `E_8021`（『分類名稱已存在』，409；`E_8016`～`E_8020` 已被占用，`E_8021` 全專案查過沒人用）＋`GlobalExceptionHandler`（窮舉 switch，不加編譯不過）＋`GlobalExceptionHandlerTest` 的列舉。`PostCategoryService`：
  - `createCategory`／`updateCategory` 先 `requireNameNotTaken`：讀同店所有分類，名稱經 `strip()`（含全形空白）＋`toLowerCase(Locale.ROOT)` 後相同就丟 `E_8021`；改名時排除分類自己；**名稱沒改不檢查**（規則上線前已存在的同名分類不會被鎖死）。**在 Java 端比對、不用 SQL**（不依賴資料庫語系；同店分類很少）。
  - 代稱：`generateUniqueSlug` 新增『新建』的路徑（`excludeId == null` 走 `existsByTenantIdAndSlug`）。**不能把 null 傳給 `existsByTenantIdAndSlugAndIdNot`**：JPQL 的 `id != NULL` 恆為未知，查詢永遠回 false，撞到也不會加後綴，建立時才由唯一約束炸成 500（B7 突變證明這個分支有人守）。`createCategory` 改用它，`E-8014` 因此不再由任何端點回傳（錯誤碼保留）。
- **前端**：儀表板『貼文分類管理』儲存失敗時，`E-8021` 顯示『分類名稱已存在，請換一個名稱』，其他失敗維持『儲存分類失敗，請稍後再試』（原本不論什麼原因都是後者，店主會一再重試）。

### 4.4 `DEF-494` 與文件

- `API_Index.md` 刪掉 `/api/v2/dashboard/media/*` 那一列。
- `API_Error_Codes.md`（`ErrorCodeDocDriftTest` 會逐列比對，不加會紅）＋`API_M15_CMS.md`（§4.22～4.25 新增『名稱與代稱』說明、§7 錯誤碼表與已知限制）＋PRD v1.0.16（版本註記、`/blog`、`/blog/category/[id]`、`/dashboard/posts/categories` 三列）。

## 5. 突變驗證結果

驅動腳本 `scratchpad/mutate.py`（只用 ASCII 的突變字串、每次突變後必定還原並逐位元比對、改檔後 `touch` 避免 IDE 的 Java 服務搶先編譯舊狀態）。

### 5.1 前端單元層：10 個突變，全數擊殺（基準：相關 10 檔 87 案例全綠）

| # | 突變 | 擊殺它的案例 |
|---|---|---|
| F1 | `output: 'standalone'` → `'export'` | 守門測試的 `output` 案例 |
| F2 | 刪掉 `output` 那一行 | 同上 |
| F3 | Dockerfile 的 `COPY …/.next/standalone` 註解掉 | 守門測試的前提案例 |
| F4 | 頁尾連結 `/blog` → `/blogs` | `StorefrontFooter` 2 案例 |
| F6 | 分類 chip 導向 `/blog/categories/{id}` | 『點分類 → 導向分類頁』 |
| F7 | 篩選列永遠不渲染 | `blog-entry` 3 案例 |
| F8 | 不排序 | 排序案例 |
| F9 | 『全部』不是選取態 | 選取態案例 |
| F10 | 分類管理頁判斷 `E-8014` 而不是 `E-8021` | 3 案例（兩個同名＋『別的 409 不冒充』） |
| F11 | 一律顯示『名稱已存在』 | 2 案例（『其他失敗不冒充』） |

### 5.2 後端：11 個突變，全數擊殺（基準：`PostCategoryServiceTest`＋`GlobalExceptionHandlerTest`）

| # | 突變 | 擊殺 |
|---|---|---|
| B1 | 建立時不檢查同名 | 同名 9 案例 |
| B2 | 改名時不檢查同名 | 改名案例 |
| B3 | 比對區分大小寫 | 同名變體案例 |
| B4 | 比對不去前後空白 | 同名變體案例 |
| B5 | 改名時不排除自己 | 『只改自己名稱的大小寫』 |
| B6 | `nameChanged` 恆為真（名稱沒改也檢查） | 『名稱沒改不重查』＋原本的『名稱沒改保留代稱』 |
| B7 | `generateUniqueSlug` 拿掉新建的路徑 | 4 案例，**含專門的 `slugTakenByAnotherName…`** |
| B8 | 名稱檢查不限同一家店（`findAll`） | 同名案例（stub 沒被呼叫） |
| B9 | 錯誤碼用 `E_8014` | 同名案例 |
| B10 | `E_8021` 映成 400 | `GlobalExceptionHandlerTest` 的逐碼對應 |
| B11 | 建立時不處理代稱撞名 | 4 案例，**含專門的 `slugTakenByAnotherName…`** |

**偶然擊殺 vs 專門擊殺**（Sprint 317 教訓）：B7、B11 第一次只列前 3 個失敗案例，都是 `createCategory_success` 這類靠 Mockito 嚴格 stub 的順帶擊殺；放寬上限重跑後確認**專門為它寫的案例也會紅**，不是只靠偶然。
**整合層『修復前必紅』**：把 `PostCategoryService` 暫時換回 HEAD 的舊版，`IT-M15-031`（真實 PostgreSQL＋HTTP）失敗，第二個只差中文的分類拿到 `409 {"code":"E-8014","message":"分類網址代稱重複"}`，就是 `DEF-492` 的症狀。

### 5.3 E2E 層（單元層看不到的：真實頁面＋真實後端）：3 個突變一批

三個突變同時套用，重建 JAR 與前端後，對**同一組 3 個規格**（`at-blog-category-real`、`at-m15-e2e`、`at-store-blog-category-real`）跑定向守門：**E1** 頁尾連結 `/blog` → `/blogs`；**E2** 後端建立與改名都不檢查同名；**E3** 後端建立時不處理代稱撞名。對照組是乾淨建置的同一組規格（23／23 通過，1.6 分）。

突變組結果：**12 通過、4 失敗、7 沒跑**（Playwright 序列規格前面失敗，後面的案例不執行）。

| 失敗的案例 | 對應 | 說明 |
|---|---|---|
| `E2E-BLOGCAT-06` | E1 | 點頁尾『部落格』後網址是 `/blogs`，不是 `/blog` |
| `E2E-STOREBLOGCAT-01`（`beforeAll`） | E3 | 建分類拿到 `500 {"code":"E-9900"}`：代稱撞到沒有處理，由 `(tenant_id, slug)` 唯一約束丟例外（這就是 `generateUniqueSlug` 不能少『新建』路徑的原因，與 §4.3 的推論一致）；同規格其餘 5 案例沒跑 |
| `E2E-M15-006` | E2＋E3 | 第二個只差中文的分類建不起來，`getByRole('cell', 咖啡手帖…)` 找不到；同一個序列規格後面 2 案例沒跑（5＋2＝7） |
| `E2E-M15-003 › 媒體中心篩選功能` | **與突變目標無關** | 收尾的 `waitForLoadState('networkidle')` 逾時 30 秒。同一規格在乾淨建置的定向守門通過。**推測（沒有驗證）**：E1 讓賣家外殼頁尾的連結指向不存在的 `/blogs`，Next 預取 404 路由使網路一直不閒置；我沒有單獨重現。乾淨的完整守門會再跑一次它，結果見 §6.1 |

還原：驅動用**絕對路徑**備份、套用、`cmp` 逐位元比對還原（§8.2 第 5 點是這件事的由來）；結束後 `status` 兩個檔案都是 clean。

### 5.4 合計

前端單元 10＋後端 11＋E2E 層 3（一批）＝**24 個突變**；單元層全數擊殺、E2E 層 3 個突變目標都被擊殺（另有 1 個與目標無關的失敗，見 §5.3）；整合層另以舊版服務確認 `IT-M15-031` 修復前必紅。

## 6. 後置驗證

### 6.1 容器驗證（`DEF-493`）

**A. 專案副本＋CI 模擬堆疊（Sprint 337 做不到的那一項）**——`docker compose -p s338check -f docker-compose.yml -f docker-compose.test.yml up -d --build`，前後端都是 `runner` 目標：

| # | 項目 | 結果 |
|---|---|---|
| 1 | 紅燈基準：`… -f docker-compose.yml build frontend` | **失敗，結束碼 1**：`Dockerfile:49 COPY … /app/.next/standalone … not found` |
| 2 | 修後：同一個指令 | **成功**：容器內 `npm run build` 編譯成功（24.1 秒）、`COPY …/.next/standalone` 通過，映像 303 MB |
| 3 | 堆疊啟動 | 結束碼 0；後端 `runner` 映像也建得出來；`ci-backend`／`ci-postgres`／`ci-redis` 皆 `healthy`；**`ci-frontend` 永遠 `unhealthy`**（`FailingStreak` 4336，見 §7.2 第 8 點） |
| 4 | 證據三層 | ①`printenv`：`API_INTERNAL_URL=http://backend:8080/api`、`NEXT_PUBLIC_API_URL=http://localhost:8080/api`、`HOSTNAME=0.0.0.0`、`PORT=3000`（`NODE_ENV=test` 來自測試 compose）；②容器內 `wget http://backend:8080/api/actuator/health` → `{"status":"UP"}`；③主機 `curl /` → 200（冷啟動 3.6 秒）；PID 1 是 `next-server`，以 `nextjs` 使用者執行 |
| 5 | 真實規格對正式映像跑（`at-article-titles-real` 7＋`at-blog-category-real` 4＋`at-entity-titles-real` 7＋`at-store-blog-category-real` 6） | **24／24 通過，1.7 分，預設逾時**；含 `E2E-ENTITY-04`（不執行 JavaScript 的原始 HTML 就有店名）。對照 Sprint 337 在開發模式要把 expect 逾時放寬到 20 秒 |
| 6 | **對照組**（證明是環境變數造成差異） | 正式設定 → `<title>E2E 實體標題店 …｜意象若水 RUOSHUI</title>`；把 `API_INTERNAL_URL` 改成預設值 `http://localhost:8080/api` 重建 → `<title>店鋪首頁｜意象若水 RUOSHUI</title>`；還原 → 又是店名。**Sprint 337 在正式 compose 加的 `API_INTERNAL_URL` 有效，也必要** |
| 7 | 健康檢查根因 | 容器內 `wget http://localhost:3000/` → `Connection refused`；`wget http://127.0.0.1:3000/` → 成功。`/etc/hosts` 把 `localhost` 同時對應 `::1`，BusyBox 選了 IPv6；`netstat` 只有 `0.0.0.0:3000`（IPv4）。後端的健康檢查用 `localhost` 卻正常，因為 Java 預設雙堆疊監聽 |
| 8 | 修法（先只在副本驗證，再問使用者） | 副本改成 `127.0.0.1`（`diff` 確認只差這一個字）→ 15 秒內 `healthy`；前端容器閒置記憶體 **45 MiB**（Sprint 337 開發模式 364 MiB） |

**B. 專案真實目錄與真實檔案（使用者指示的指令原話）**——`docker compose -p s338real -f docker-compose.yml build`（`-p` 只是命名，避免與開發堆疊的映像混在一起）：

- **前後端兩個映像都建得出來**（前端 333 MB、後端 674 MB，結束碼 0）。
- `up -d --no-deps frontend`（用**專案真實的** `docker-compose.yml`，含我改的健康檢查）：**15 秒內 `healthy`**；`printenv`：`NODE_ENV=production`、`NEXT_PUBLIC_API_URL=http://localhost:8080/api`、`API_INTERNAL_URL=http://backend:8080/api`；`/` 回 200；記憶體 45 MiB。
- **新發現（登記 `DEF-498`，不在本輪修）**：`frontend/` 沒有 `.dockerignore`，建置上下文傳輸 **1.07 GB、花 328 秒**（`node_modules` 600 MB＋`.next` 440 MB＋…；機器當時負載很高，空閒時會快一些，我沒量）；而且主機的 `node_modules` 經 `COPY . .` 混進建置，最終映像裡有 **macOS 的原生套件**（`/app/node_modules/@img/sharp-darwin-x64`、`sharp-libvips-darwin-x64`），是 Linux 容器裡的純累贅；映像比副本建的大約 30 MB（333 vs 303 MB）。映像能用（Linux 版的 sharp 都在），但這不是乾淨的建置。**為什麼只登記**：新增 `.dockerignore` 是 Docker 相關檔案的新增，依 Docker 政策要明確指示，使用者的『請執行』涵蓋的是 `next.config.ts` 與實測（健康檢查已另外取得同意）。
- 結束：`down -v --remove-orphans`＋`docker rmi s338real-frontend s338real-backend`（指名刪）；沒有留下卷或映像。

**C. `next start` 的相容性**（`output: 'standalone'` 下，本地守門與雲端 CI 用的啟動方式）：本機 `npm run build` 產出 `.next/standalone`（59 MB，`server.js` 在最上層，沒有被上層 lockfile 多包一層）；`PORT=3077 npm run start` 印出 `⚠ "next start" does not work with "output: standalone" configuration. Use "node .next/standalone/server.js" instead.`，**但照常服務**：`/`、靜態 CSS 資產、`/blog` 都回 200；完整 E2E 守門的啟動日誌也有同一行警告（定向守門保存的 `frontend-start.log`）。

### 6.2 本機驗證

| 項目 | 結果 |
|---|---|
| 後端 `mvn -o clean verify` | **BUILD SUCCESS**：單元 **2495／0**（Sprint 337：2479；+16＝`PostCategoryServiceTest` +15、`GlobalExceptionHandlerTest` +1）、整合 **1052／0**（Sprint 337：1051；+1＝`IT-M15-031`）、Checkstyle 0 違規、PMD 執行並通過；**46 分**（Sprint 337：23 分，原因沒查） |
| 前端 `tsc`、`eslint`（`npm run lint`） | 結束碼 0、無輸出 |
| 前端 Vitest | **35 檔 353 案例全過**（Sprint 337：31 檔 337；+4 檔＝`docker-standalone`、`StorefrontFooter`、`blog-entry`、`categories-save-error`；+16 案例＝3＋3＋5＋5） |
| 完整本地 E2E 守門（`make validate-e2e`，389 案例） | **387 通過、2 失敗**（`at-account-data-rights` 的『匯出我的資料』『刪除帳戶』，各 30 秒逾時）；**19.5 分**（Sprint 337：9.5 分）。見下 |
| 定向守門（乾淨建置，3 個相關規格） | **23／23 通過**（1.6 分），含新增的 `E2E-BLOGCAT-06`、`E2E-M15-006` |
| 定向守門（突變組） | 12 通過、4 失敗、7 沒跑（§5.3） |

**完整守門那 2 個失敗（歸因是推論＋重跑，不是根因證明）**：

- 它們不在我的改動範圍（會員資料匯出與刪除帳戶），而且是整套**最先跑**的規格（檔名字母序第一）。
- 後端日誌顯示請求**都到了後端並在 22:58:38 處理完**；Playwright 的 30 秒測試逾時在同一刻用完——時間耗在點擊**之前**（註冊＋登入＋開頁）。同一份日誌裡還有一個請求在兩個過濾器之間停了 1.6 秒。
- 機器只有 4 個邏輯核心，守門當時負載平均 59／116／111（1／5／15 分鐘），守門總時間也比 Sprint 337 慢一倍。
- **單獨重跑這個規格通過**（當時負載平均仍有 74）。
- **我沒有再跑第二次完整守門**（一次 20 分鐘，機器仍過載）；雲端 CI 的 e2e job 在乾淨的 runner 上跑整套，是獨立的確認，結果見 §6.3。**（更正，Sprint 339 回填時發現：這句話寫錯了——雲端 `act-compat.yml` 沒有 e2e job，見 §6.3 的更正。）**

### 6.3 pre-push 與雲端 CI

程式 commit 是 `6db07a0e`（pre-commit 的 Maven 核心測試、ESLint、TypeScript、單元測試、SRD schema 文件一致性檢查全過，沒有用 `--no-verify`）。**第一次 push（2026-11-11 深夜）被 pre-push 擋下**，原因是環境：`act` 的 `Backend Unit Tests` 在 `apt-get install` 時容器內 DNS 解析不了 `archive.ubuntu.com`（exit 100；細節見 [SPRINT_339_PLAN.md](SPRINT_339_PLAN.md) §1）。**後來與 Sprint 339 一起 push 成功**（2026-11-12，`03b8f314..22800e61`，5 個 commit）：pre-push 輕量守門全過；雲端 CI run [43081196174](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/43081196174) 全綠（三個 job 皆 success，headSha `22800e61`），整合 1053／0（含 Sprint 338 新增的 `IT-M15-031`）、前端 Unit Tests 36 檔 359／0——這是 Sprint 338＋339 合計的結果，雲端沒有只含 Sprint 338 的 run。

**更正**：本節與 §6.2 先前寫的『雲端 CI 含 e2e job、整套 389 案例在乾淨 runner 上獨立確認』是錯的——雲端 `act-compat.yml` 只有 Backend Unit Tests、Backend Integration Tests & Package、Frontend Lint & Build 三個 job，**沒有 e2e job**；E2E 是本機守門（`make validate-e2e`），所以 Sprint 338 的 389 案例（387 過＋2 個逾時、單獨重跑過）只有本機這一份證據。

## 7. 行為變更與已知限制

### 7.1 行為變更

| 項目 | 之前 | 現在 | 影響誰 |
|---|---|---|---|
| 貼文分類建立：名稱不同、只差中文（代稱相同） | 第二個起 409 `E-8014` | 建立成功，代稱接 8 碼隨機後綴 | 店主 |
| 貼文分類建立：英文同名（`Food`／`food`） | 409 `E-8014`（代稱相同） | 409 **`E-8021`** | 若有呼叫端依錯誤碼判斷要跟著改（前端 `grep` 沒有依 `E-8014` 判斷的地方；其他呼叫端未知） |
| 貼文分類建立：全中文同名 | 能建立（兩個同名，代稱各自隨機） | **409 `E-8021`**（新擋下） | 店主 |
| 貼文分類改名成別人的名稱 | 不檢查 | 409 `E-8021`（名稱沒改不檢查） | 店主 |
| `E-8014` | 建立分類時可能回 | 不再由任何端點回傳（錯誤碼與文件保留） | — |
| 儀表板『貼文分類管理』 | 儲存失敗一律『儲存分類失敗，請稍後再試』 | 同名顯示『分類名稱已存在，請換一個名稱』，其他不變 | 店主 |
| 前台頁尾 | 隱私權政策／服務條款／聯絡客服 | 前面多一個『部落格』連結（所有前台頁與賣家外殼） | 所有訪客 |
| 平台層 `/blog` | 只有文章清單 | 有分類時上方多一列分類篩選（點分類進 `/blog/category/{id}`） | 登入且選定店鋪的人 |
| `next.config.ts` | 沒有 `output` | `output: 'standalone'`：`next build` 多產出 `.next/standalone`（本機約 59 MB）；`next start` 印一行警告 | 開發者、本地 E2E 守門、雲端 CI |
| 正式／CI 模擬 compose 的前端 | 映像建不出來；就算造出來也永遠 unhealthy | 建得出來、`healthy`、閒置記憶體 45 MiB（開發模式 364 MiB） | 部署 |

### 7.2 已知限制（誠實列出）

1. **暫時入口的代價**：兩頁仍是登入者專用，匿名訪客點頁尾『部落格』只會看到登入提示（§3.2）。**買家登入後點它會看到什麼，我沒有實測**（推論：選用的店鋪是系統租戶，通常沒有貼文，顯示『尚無已發布的文章』）。正式化 → `DEF-495`。
2. **同名比對沒有資料庫唯一索引**：讀出再比對與寫入之間沒有鎖，兩個請求同時建立同名分類都可能成功（多一個同名分類，店主可自行處理）；刻意不加索引，因為規則上線前全中文同名的分類本來就能建立，已存在的同名資料會讓建索引的遷移失敗。**規則上線前已存在的同名分類不清理，也不鎖死**（名稱沒改的更新不檢查）。
3. **只擋整串相同**（忽略大小寫與前後空白，含全形空白）：不處理全形半形（`Ｆｏｏｄ` 與 `Food`）、內部空白（`美 食` 與 `美食`）、簡繁體。
4. **`E-8014` 變成沒有人回傳的錯誤碼**：列舉與文件保留，刪除是契約變更，沒有必要。
5. **沒有自動化流程建映像**：這輪只補了靜態守門，抓得到 Dockerfile 與 `next.config.ts` 的 `output` 不一致，抓不到別的漂移（`DEF-497`）。
6. **`rewrites` 寫死 `ecommerce-backend-dev`**：`standalone` 會把它序列化進 `server.js`；前端所有 API 請求都用絕對網址，所以正式環境不會用到，只記錄，沒動。
7. **驗證用的是 CI 模擬堆疊**（`docker-compose.test.yml`：`NODE_ENV=test`、後端 `integration-test` profile、CI 用的測試金鑰），不是 `prod` profile（要真實的 JWT／Stripe 設定）；正式 compose 的 `API_INTERNAL_URL` 值 `http://backend:8080/api` 在這個堆疊裡生效，`prod` profile 本身沒跑過。
8. **`FailingStreak` 4336 不是 4336 個獨立事件**：堆疊在等使用者回答問題時空轉約 6 小時（每 5 秒檢查一次），見 §8.2 第 1 點。
9. **後端全量驗證 46 分鐘**（Sprint 337 同一個指令 23 分鐘），原因沒查。
10. **突變組裡 `E2E-M15-003` 的失敗是推測歸因**（§5.3），沒有單獨重現。
11. **前端建置沒有 `.dockerignore`**（`DEF-498`，§6.1 B）：真實目錄建置要傳 1.07 GB 的上下文、最終映像混入 macOS 的原生套件。映像能用，但不乾淨，也慢。
12. **完整本地 E2E 守門是 387／389**，2 個在套件最開頭逾時、單獨重跑通過（§6.2）；我沒有拿到『一次 389／389』。
13. **`next start` 的警告會一直印**：本地 E2E 守門與雲端 CI 的啟動日誌每次都多一行 `⚠ "next start" does not work with "output: standalone"…`，不影響結果。若覺得吵，改法是守門與 CI 改用 `node .next/standalone/server.js`（要連同 `public`、`.next/static` 的複製一起處理），那是 CI 設定的改動，我沒有動。

## 8. 預測對答案與失誤

### 8.1 預測 vs 結果

**先講證據力**：這輪我沒有像 Sprint 337 那樣把『每個突變預期紅的案例集合』先寫進驅動腳本（驅動只判斷擊殺與否並列出失敗案例）。下面的預測是我規劃時心裡有的判斷、事後對照，**證據力比 Sprint 337 弱**。

| 預測 | 結果 |
|---|---|
| 紅燈基準重現與 Sprint 337 相同的建置失敗 | **一致**（`Dockerfile:49`；前面階段快取命中，幾秒就失敗） |
| 補上 `output` 後正式映像建得出來、能啟動 | 建得出來；**啟動後永遠 unhealthy——我沒預見**（§6.1，健康檢查的 `localhost` 解析成 IPv6） |
| 對正式映像跑真實規格，不會再有開發模式的『點擊後網址逾時』 | **24／24 通過**，預設逾時、1.7 分（Sprint 337 在開發模式要把 expect 逾時放寬到 20 秒才過）。這**支持但不證明**『開發模式的即時編譯造成逾時』的推測 |
| 新測試（頁尾、分類篩選）實作前 7 個紅、1 個綠（『原本三個連結沒被擠掉』） | **一致** |
| 既有 `blog-load-failure.test.tsx` 會因頁面多用 `useRouter` 而壞（4 案例） | **一致** |
| 後端新案例實作前多數紅，少數是 Mockito 嚴格 stub 的『沒用到的 stub』錯誤 | **一致**（建立類 9 失敗＋4 錯誤、改名類 1 失敗＋1 錯誤；5 個錯誤裡 4 個是 `UnnecessaryStubbingException`，1 個就是 `DEF-492` 在單元層的重現：`BusinessException: 分類網址代稱重複`） |
| 前端 10、後端 11 個突變全數擊殺 | **一致**；但 B7／B11 第一次只看到偶然擊殺，放寬上限才確認專門案例也紅 |
| 前端全量測試一次全綠 | **沒有**：`load-failure.test.ts` 紅（§8.2 第 3 點） |

### 8.2 失誤與修正（誠實列出）

1. **問問題時讓堆疊空轉**：我啟動 CI 模擬堆疊後立刻問 `DEF-492` 的問題，使用者約 6 小時後才回覆，堆疊空轉了 6 小時（`FailingStreak` 4336 這麼來）。沒有影響結果，但浪費資源；之後要問使用者前，先拆掉不必要的堆疊或先做完需要它的驗證。
2. **排序測試綁了語系**：我預期『甲』排在『乙』前，但 `zh-Hant` 的排序是筆畫序，『乙』在前。改用 ASCII 名稱驗『先 `sortOrder`、再名稱』，測試不該綁語系。
3. **沒預見 `load-failure.test.ts`**：新增的分類載入失敗是靜默的，全量 Vitest 才抓到。我第一次登記時寫成佔位內容（路徑缺 `app/`、數量與理由都是假的），**下一個動作立刻改正，沒有跑那個佔位版本，但寫了不該寫的東西**。依規則登記為『輔助資訊』並寫真實理由。
4. **整合測試用了不存在的端點**：`IT-M15-031` 原本用 `GET /dashboard/post-categories/{id}`，控制器沒有這個端點；執行前讀控制器發現，改成用列表驗證，沒有跑壞的版本。
5. **差點弄壞原始碼**：整合層『修復前必紅』實驗用 `trap` 還原服務檔，但 `trap` 執行時目前目錄已經是 `backend/`，我寫的是相對路徑，還原失敗（腳本自己的 `cmp` 檢查印出 `RESTORE-MISMATCH`）。服務檔當時停在舊版；立刻用絕對路徑從備份還原並逐位元比對，之後的 JAR 是還原後才打包的（JAR 內含新版才有的字串為證）。此後的 E2E 突變驅動全部改用絕對路徑＋`cmp`。
6. **`pgrep "next build"` 沒比對到**：程序名稱是 `next-build`，我差點把仍在進行的建置當成失敗；改用等檔案產出的條件。
7. **後端全量驗證慢了一倍**（46 分 vs 23 分），沒查原因。

## 9. 新登記的相鄰缺陷（只登記，不在本輪修）

| 編號 | 內容 | 為什麼不在本輪修 |
|---|---|---|
| `DEF-495` | 平台層 `/blog`、`/blog/category/[id]` 的正式去留（跨店鋪部落格／平台自己的部落格／移除） | 產品決定；暫時入口已解決『沒有入口』 |
| `DEF-496` | `M18_Knowledge_Management_SPEC.md` 的端點表寫 `/api/v2/dashboard/media/*`，後端沒有這些路徑（實作是 `/api/v2/media/*`） | 需求規格文件，不在 `DEF-494` 的索引範圍；不知道它與實作是什麼時候分開的 |
| `DEF-497` | 沒有任何自動化流程會建 Docker 映像（`DEF-493` 半年沒人發現的原因） | 涉及 CI 與 Docker 設定，依政策需要明確指示 |
| `DEF-498` | `frontend/` 沒有 `.dockerignore`：真實目錄建置傳輸 1.07 GB 上下文（328 秒）、最終映像混入 macOS 原生套件（`@img/sharp-darwin-x64` 等，約 30 MB）。建議：新增 `frontend/.dockerignore`（`node_modules`、`.next`、`e2e`、`test-results`、`playwright-report`、`.env*`） | 新增 Docker 相關檔案，依政策需要明確指示；映像能用，不是故障 |

## 10. 我替使用者做的決定（可一句話推翻）

1. **頁尾而不是頁首**放『部落格』連結，排在隱私權政策前面；文字用『部落格』（與頁面標題一致，店鋪層的導覽叫『貼文』，平台層沿用頁面標題）。
2. **`/blog` 加分類篩選列**讓分類頁也有入口（使用者說的是『入口』，兩頁都是孤兒，只做一頁等於只解決一半）；可一句話改成只留頁尾連結。
3. 篩選列用 **`Chip` ＋ `router.push`**，不是 `<a>` 連結：與 `StoreBlogList`（Sprint 337）一致；代價是不能右鍵開新分頁。
4. 分類載入失敗時**靜默隱藏篩選列**，並在 `load-failure.test.ts` 登記為『輔助資訊』（同類先例：`faq`／`knowledge`／`media` 的分類選項）。
5. **同名比對方式**：`strip()`（含全形空白）＋小寫，**在 Java 端比對、不加資料庫唯一索引**（理由 §7.2 第 2 點）；儲存的名稱**照使用者輸入、不去空白**（不改資料語意）。
6. **改名才檢查，名稱沒改不檢查**：規則上線前已存在的同名分類不能因此連描述、排序都改不了。
7. **新錯誤碼 `E-8021`**，不重用 `E-8014`（代稱）或 `E-8017`（媒體分類）：語意不同，前端要靠它分流訊息。
8. 分類管理頁的訊息文字『分類名稱已存在，請換一個名稱』（使用者選項裡的提示是『分類名稱已存在』，我加上下一步該做什麼）。
9. `output: 'standalone'` **只加這一行**，沒有碰 `outputFileTracingRoot`、`rewrites`；`validate-e2e.sh` **只改註解**。
10. 守門測試**直接匯入 `next.config.ts` 看 `output` 的值**，不用正規表示式掃原始碼（註解裡出現這幾個字不會讓它誤過）。
11. `DEF-494` 只刪過期的那一列；核對時發現的 M18 需求規格過期路徑**登記不修**（`DEF-496`）。
12. 健康檢查修改**沒有自己決定**，用互動選項問了（Docker 政策要求明確指示）。
13. **沒有新增 `frontend/.dockerignore`**（`DEF-498`）：映像能建能用，不是故障；它是 Docker 相關檔案的新增，與健康檢查同一個理由——政策要明確指示，而你說的『請執行』涵蓋的是 `next.config.ts` 與實測。**你一句話同意我就做**（建議內容在 `DEF-498`）。

## 11. 狀態

| 項目 | 狀態 |
|---|---|
| `DEF-493` 正式前端映像建得出來 | ✅ 已修並實測（`output: 'standalone'`＋健康檢查 `127.0.0.1`＋守門測試）；追蹤表已結案 |
| `DEF-491` 平台層部落格暫時入口 | ✅ 暫時入口已做；正式去留 → `DEF-495`（待你決定） |
| `DEF-492` 貼文分類同名規則 | ✅ 已修（你選了『擋下並提示』）；追蹤表已結案 |
| `DEF-494` `API_Index.md` 過期列 | ✅ 已修 |
| 程式 commit | `6db07a0e`（已 push，與 Sprint 339 一起） |
| 文件 commit（本計畫書、追蹤表、發布追蹤） | `edfd2b14`（已 push，與 Sprint 339 一起） |
| push、雲端 CI | ✅ 已 push（`03b8f314..22800e61`），雲端 CI 全綠（run 43081196174），結果與更正見 §6.3 |
| 待你決定 | `DEF-495`（平台層部落格的正式去留）、`DEF-498`（前端 `.dockerignore`，一句話同意就做）。**→ 已於 2026-11-12 由你以互動選項決定（`DEF-495`『做成跨店鋪部落格』、`DEF-498`『新增（建議）』），並在 Sprint 339 完成，見 [SPRINT_339_PLAN.md](SPRINT_339_PLAN.md)**（上一句保留作為當時的紀錄） |
