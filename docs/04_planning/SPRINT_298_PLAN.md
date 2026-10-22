# Sprint 298 Plan — 實證 Sprint 297 的行為不變、DEF-390 家族收尾、商品管理列表頁崩潰修復（DEF-401）

## 1. 起點與範圍

### 1.1 起點（「繼續」的解讀是推論）

使用者貼回 Sprint 297 的總結，回覆「請繼續完成任務！」，**沒有任何 `==>` 回覆**。依既有慣例，這**不是**對總結中「待你決定」項目的授權：`DEF-400`（`lint` 加 `--max-warnings`）使用者沒有回「依照建議」，這輪不做。做的是 AI 能獨立處理、且不涉產品決策的項目。這個解讀是我的推論。

開工先核對總結自己的說法，而不是照單全收：

| 總結的說法 | 核對結果 |
|---|---|
| 雲端 CI 全綠 | `gh run list`：`e75a82ed` 的 run 39742144885 為 `completed／success`，`headSha` 與本機 HEAD 一致（相符） |
| Docker 容器 0 個 | 開工時 `docker ps` 為空（相符） |
| 「`API_M17` 其餘 7 處『非該租戶成員』列還沒對照 service」 | **過期的候選**：文件註記 8 寫明 Sprint 294 已逐一核對並校正 §4.4／§4.8／§4.9／§4.13／§4.14；正文現存的「非該租戶成員」失敗列只剩 §4.5、§4.7 兩處（本來就是成員檢查，文件正確）。真正還懸著的是註記 8 自己宣告的「**未驗證**」項（見 §6） |
| （未提及）後端 JAR | `backend/target/` 的 JAR 建置於 13:28，**早於** Sprint 296 的後端 commit（14:12），是過時的；重建後（19:34）才用於任何實測 |

### 1.2 為什麼選這幾件事

1. **Sprint 297 自己揭露的最大殘留風險**：55 個前端檔、前端零單元測試、`make validate-e2e` 沒涵蓋每個被改動的頁面——23 個 `useCallback` 轉換只是「高度信心」。實際盤點：被轉換的 22 個檔案（15 頁＋7 元件）裡，`dashboard/erp`（4 頁＋兩個表單）、`dashboard/knowledge`、`dashboard/media`、`dashboard/notifications` 在整個 `frontend/e2e` 目錄**零引用**（`grep` 確認），只被 `tsc`／ESLint／`build` 驗過。這是我自己引入的風險，應該用實證關掉。
2. **DEF-390 家族的盲點**：全後端只有兩個 `@Cacheable`；`ratingStats` 在 Sprint 285 補了真實 Redis 測試，`dashboardStats` 沒有（`SellerDashboardServiceCacheTest` 用 `spring.cache.type=simple`，而且把最新訂單 stub 成 `List.of()`，`lastOrderAt` 永遠是 null）。
3. **API_M17 註記 8 的「未驗證」**：`@PreAuthorize` 先擋哪些全域角色、`ROLE_ADMIN` 過了註解後 service 層的實際結果。

沒做：`DEF-394` 前端管理介面（新功能，PRD 沒有需求）、150 多處 `getCurrentTenant()` 的語意稽核（範圍大，這輪沒有開始）、`DEF-400`（等使用者）。

### 1.3 成功標準（開工時定義）

1. 對 Sprint 297 動過的頁面，用真實全棧證明載入時可觀察行為與 Sprint 297 之前一致；能說明的差異才算差異，**先量雜訊基準**。
2. 新測試必須能因為正確的原因轉紅（突變驗證），不是只會綠。
3. 發現的缺陷：AI 能獨立處理、無產品決策者的先修並寫回歸測試；涉及產品／安全姿態／法務內容的登記並留給使用者。

## 2. A：DEF-390 家族收尾（後端）

**盤點**：`grep -rn "@Cacheable\|@CachePut\|@CacheEvict\|@Caching" backend/src/main` ——兩個 cache name（`dashboardStats`、`ratingStats`），兩者在 `RedisConfig#redisCacheManagerBuilderCustomizer` 都有專屬 Jackson 序列化設定；沒有 `@CacheConfig`／`@Caching`。

**新增**：

| 檔案 | 內容 |
|---|---|
| `core/seller/SellerDashboardRealRedisIntegrationTest`（新） | `@TestPropertySource("spring.cache.type=redis")`＋真實 Redis。兩個案例：欄位全部有值（`long`／`BigDecimal("50000.75")`／帶奈秒的 `Instant`）寫入後讀回，用 `usingRecursiveComparison` 比對且驗證第二次沒有重算；新店鋪（含 null 欄位，走真實 DB 查詢）的來回。**明確斷言 cache manager 是 `RedisCacheManager`**，覆寫若悄悄失效，測試不會變成在測記憶體 Map 而仍然全綠 |
| `infrastructure/redis/RedisCacheConfigTest` 的 `TC-CACHE-G001`（新增一個案例＋一個掃描 helper） | 把「新增 `@Cacheable` 要手動檢查 `RedisConfig`」這條原本只存在人腦中的規則改成機械檢查：掃描 `com.nextkey.ecommerce` 下所有 `@Cacheable`／`@CachePut` 的 cache name，必須都在 `RedisConfig` 註冊；掃不到任何名稱時失敗（避免守門恆真） |

**結果**：`dashboardStats` 對真實 Redis **沒有缺陷**——`BigDecimal` 小數位數、`Instant` 奈秒精度與 null 欄位都能來回。這是「補上盲點，結果是乾淨」，不是新缺陷。

**突變驗證**（驅動腳本在 scratchpad，每個突變預先登記預期的紅燈集合，用「失敗集合是否完全吻合」判讀；6／6 擊殺、集合逐一吻合、還原逐位元組一致）：

| # | 突變 | 紅燈 | 失敗原因（取自 surefire／failsafe XML） |
|---|---|---|---|
| M1 | 拿掉 `dashboardStats` 的專屬註冊 | G001＋兩個整合案例 | `SerializationException: Cannot serialize`（落回 JDK 序列化，即 DEF-390 的症狀）；G001 點名 `dashboardStats` |
| M2 | 拿掉 `ratingStats` 的專屬註冊 | 只有 G001 | G001 點名 `ratingStats` |
| M3 | `ObjectMapper` 不註冊 `JavaTimeModule` | **只有填滿案例** | `Java 8 date/time type java.time.Instant not supported by default`——空店鋪案例（`lastOrderAt` 為 null）沒紅，證明填滿案例有它沒有的鑑別力 |
| M4 | 不啟用預設型別標記 | 兩個整合案例 | `ClassCastException: LinkedHashMap cannot be cast to DashboardResponse` |
| M5 | 新增一個沒註冊的 `@Cacheable("zzUnregistered")` | 只有 G001 | G001 點名 `zzUnregistered`（DEF-390 重現情境） |
| M6 | 測試自身：`@TestPropertySource` 覆寫改成 `simple` | 兩個整合案例 | `ConcurrentMapCacheManager` 不是 `RedisCacheManager`（自我檢查斷言生效） |

**限制**：驅動每次 `mvn` 都沒有印 `Compiling`（IDE 的 Java 語言服務會搶先編譯），所以「用的是突變後的 class」靠的是紅燈原因與預期吻合，不是靠編譯訊息。最終狀態另以 `mvn clean test-compile`（真實全量重編：386 個主程式檔＋306 個測試檔）後重跑：`RedisCacheConfigTest` 9／9、新整合測試 2／2、既有的 `ReviewRatingStatsRealRedisIntegrationTest` 1／1、`SellerDashboardServiceCacheTest` 3／3。

## 3. B：Sprint 297 前後差異探針

### 3.1 方法

- **前版副本**：`git archive d2de7ffc frontend`（Sprint 297 的 parent）＋ `cp -cR node_modules`（APFS clone）；與 HEAD 的前端各自 `npm run build`（68／68 頁，皆成功）。
- **同一個後端、同一份資料**：自建的全棧（PostgreSQL 55432／Redis 56379／重建後的 JAR，`ddl-auto=validate`），前端一次只服務一份（3000）。資料全走真實流程播種：店鋪（HYBRID，含房源與商品）、供應商、SKU、採購單（草稿）、庫存異動、貼文分類、已發布與草稿貼文各一、知識庫分類與文章（knowledge／media／notifications 三頁在探針時仍是空狀態，見 §3.3）。
- **33 條路由**：Sprint 297 動過的 22 個檔案（15 頁＋7 元件）全部被至少一條路由執行（ERP 11、knowledge／media／notifications 3、posts／cms 5、blog 3、定價規則 1、商品表單 2、房源表單 2、店鋪詳情與編輯 2、管理員審核 1），再加 3 條**對照組**（Sprint 297 只加了註解、沒改行為的 `/dashboard/products`、`/dashboard/rooms`、`/cms/posts/[id]/edit`）。
- **每條路由記錄**：API 請求多重集合（query 的時間戳正規化）、載入穩定後第二個觀察窗內的額外請求（重抓迴圈的徵兆）、`console.error`／`pageerror`、HTTP ≥400、最終路徑、數字正規化後的畫面文字；表單頁另對第一個文字欄位輸入後等 2.5 秒，記錄值是否被晚到的重抓蓋掉與輸入觸發的額外請求（標記了 12 條路由，實際輸入 11 條；採購單編輯頁沒有可輸入的欄位而跳過）。

### 3.2 結果

| 比對 | 有差異的路由 |
|---|---|
| Sprint 297 之前 vs HEAD（第一次） | 1／33：管理員審核頁多了 `GET /dashboard/orders`、`/revenue`、`/stats` 三個請求 |
| **HEAD vs HEAD（同一份建置跑兩次，雜訊基準）** | **同樣只有那 1 條、同樣那 3 個請求**（第一次有、第二次沒有） |
| **Sprint 297 之前 vs HEAD（第二次）** | **0／33** |

- 那 3 個請求正是儀表板首頁（`app/dashboard/page.tsx` 第 60～62 行）經 `AnalyticsService` 發的 `/v2/dashboard/stats`、`/orders`、`/revenue`，而登入頁成功後 `router.push('/dashboard')`（`(auth)/login/page.tsx:31`）；管理員登入後被導向 `/dashboard`，那頁的請求尾巴落進第一個觀察窗。**請求出處是讀碼確認的；「為什麼只在某幾次執行出現」是時序推論**，由同版本對照組（同一份 HEAD 建置跑兩次，一次有、一次沒有）支持。這是雜訊，不是 Sprint 297 的行為差異。**沒有雜訊基準這一步，會把它誤判成行為變更。**
- 33 條路由中，除了對照組 `/dashboard/products`（崩潰，見 §4）之外，其餘 32 條在 HEAD 上都載入成功：ERP、貼文／CMS、blog、各表單等頁渲染出播種的資料；knowledge／media／notifications 是空狀態（見 §3.3）。HEAD 上沒有任何 HTTP ≥400、沒有 console 錯誤、載入穩定後沒有任何後續請求（沒有重抓迴圈）；做了輸入檢查的 11 條路由，輸入值都沒有被覆蓋、也沒有觸發額外請求。唯一的 `pageerror` 出現在對照組 `/dashboard/products`，且前後版本相同（見 §4）。

### 3.3 這個證據的範圍（不要說過頭）

- 是**載入時**的證據；**不涵蓋互動**：篩選、分頁、搜尋、送出都沒有做前後比對。輸入檢查只對每頁第一個文字欄位做了一次。這些路徑上「等價」仍然是靠 Sprint 297 的轉換守衛與 ESLint `exhaustive-deps`（0 warning），不是靠實測。
- 22 個 `useCallback` 檔案以外的 Sprint 297 改動（22 個匿名 default export 改名、36 個未使用變數刪除、18 個 `eslint-disable` 註解）只被間接執行（服務層幾乎被每頁用到），沒有逐檔探針。
- `knowledge`、`media`、`notifications` 三個列表在探針時都是**空狀態**（畫面顯示「尚無文章」「尚無媒體」「尚無模板」）：knowledge 明明播種了一篇文章卻沒出現在列表（原因**未驗證**：播種請求沒帶發布狀態，後端 DTO 也沒有 `autoPublish`，我推測是以草稿建立、而列表只回已發布者）；media 需要實際上傳檔案，沒有播種；notifications 是範本列表，沒有範本。所以這三頁「列的渲染」沒有被實測，只驗了載入時的請求與空狀態。
- 探針規格與播種規格是暫放的（用完刪除，沒有進 repo）。

## 4. 探針意外發現：店主的商品管理列表頁崩潰（DEF-401）

**現象**：對照組路由 `/dashboard/products` 在**前後兩個版本都一樣**整頁崩潰——Next 的「This page couldn’t load」，`pageerror` 為 `A <Select.Item /> must have a value prop that is not an empty string`。

**根因**（讀碼＋實測）：`ProductList.tsx` 有 `<SelectItem value="">所有類別</SelectItem>`；`@radix-ui/react-select` 2.2.6 在 `SelectItem` render 時對空字串直接 throw（`dist/index.js` 第 892 行）。`ProductList` 載入中只顯示「載入中...」，**抓完資料才 render 主畫面**，所以任何成功載入的店主都會崩潰（`Select` 的 render 與商品數量無關，所以**讀碼推論**沒有商品的店鋪也一樣；**沒有實測空店鋪**，探針的店鋪有 1 件商品）；載入失敗顯示錯誤橫幅時，依讀碼不會 render 到那個 `Select`。`git log -S` 證明那一行與 Radix 依賴都在 2026-05-10 的同一個 commit（`4ad4af6d`）進來——**這頁從來沒有成功渲染過**。既有 E2E 其實有一個案例開過這個路由（`at-m15-e2e.spec.ts:236` 的「商品頁面」），但它用剛註冊、沒有店鋪的帳號、只等 `domcontentloaded`、只 `console.log(url)`，**沒有任何斷言**，所以不可能因為崩潰而失敗——一個無法失敗的測試（同檔的「Dashboard 首頁」「房型頁面」案例是同一型，本輪沒有動它們）。全前端以 `grep` 確認只有這一處空字串 `SelectItem`。**不是 Sprint 297 造成的**（前版同樣崩潰）。

**修復**（`ProductList.tsx`，無產品決策）：「所有類別」改用哨兵值 `ALL`；`category` 狀態與送出的篩選參數仍以空字串代表不篩選，只在 `Select` 邊界轉換（`value={category || ALL_CATEGORIES}`、`onValueChange` 把 `ALL` 轉回 `''`）。

**回歸測試**：新增真實後端 E2E `at-seller-product-list-real.spec.ts`——`E2E-PRODLIST-01`（載入後看得到店主的商品、沒有 `pageerror`、沒有 Next 錯誤畫面）、`E2E-PRODLIST-02`（選「電子產品」請求帶 `category=ELECTRONICS`；選回「所有類別」請求**不帶** `category`——哨兵值若直接漏進請求會變成 `category=ALL`，這是這個修法最容易出錯的地方）。

| 驗證 | 結果 |
|---|---|
| 修復前（對未修的建置；建置時間 19:32 早於原始碼修改 19:53） | **01 紅燈**：`getByText(商品名稱)` 找不到元素（頁面崩潰）。02 在 `serial` 模式下沒有跑到，**沒有獨立的紅燈證據** |
| 修復後 | 2／2 通過 |
| 突變 M7：`onValueChange` 改回直接 `setCategory`（哨兵值外漏） | **02 紅燈**，訊息 `Received "…/v2/products?category=ALL&page=0…"`、`Expected substring: not "category="`；01 仍綠；還原逐位元組一致（sha256），重建後最終狀態 2／2 |
| `npx eslint` 對兩個改動檔 | 無任何輸出（0 problems） |

**沒做**：沒有新增 ESLint 規則防止同類寫法再出現（全前端只有這一處；改 lint 設定是政策，與 `DEF-400` 一併留給使用者）。

## 5. 全路由崩潰掃描（對修復後的建置）

崩潰是可機械判定的缺陷類別，而這次的崩潰證明既有測試不會碰到沒人寫案例的頁面。依角色（匿名 15、新註冊買家 14、店主 23、管理員 7）走訪 59 條路由，判定 Next 錯誤畫面、`pageerror`、HTTP 5xx。

**結果**：**0 條**崩潰／空白／`pageerror`／5xx／逾時（修復後的 `/dashboard/products` 也在其中，正常）。涵蓋範圍：探針與掃描合計，**102 個路由樣式中 88 個**被走訪；**未涵蓋 14 個**，全部是需要訂單／訂房／退貨／客服工單 id 的詳情頁（`/bookings/[id]`＋兩個 payment 結果頁、`/orders/[id]`＋兩個、`/returns/[id]`、`/support/tickets/[id]`、`/admin/support/tickets/[id]`、`/dashboard/{bookings,orders,returns,support/tickets}/[id]`）與 `/oauth/callback/[provider]`，這輪沒有播種對應資料。

掃描順帶看到兩個非崩潰的訊號，都查證後登記，**未擅自修**：

1. **`DEF-403`（🟢）**：登入／註冊／忘記密碼等頁的 console 出現 404。Playwright 擷取到 `/privacy?_rsc=…`、`/terms?_rsc=…`——`StorefrontFooter.tsx` 連到**不存在的**隱私權政策與服務條款頁（我的解釋是**推論**：Next 只預取進入視窗的連結，所以頁尾在視窗內的短頁面才看得到；`/` 與 `/products` 沒出現 404，但連結一樣是死的）。頁面內容屬法務／業務決定，我不擅自生成法律文字。
2. **`DEF-402`（🟡）**：匿名訪客在**每個房源頁**都看到「日曆載入失敗，仍可手動輸入日期」。PRD RTM-US-G1（MUST）寫「Guest 可完整瀏覽……房源日曆、定價」，但匿名呼叫 `GET /v2/bookings/calendar` 與 `/availability` 都是 **401**（需要 `booking:read`）。這牽涉把端點開放給匿名的安全姿態，是使用者的決定。**查證時發現**：`CalendarResponse` 含 `bookingId` 與 `appliedRuleName`，**不能把現有端點直接改成匿名可讀**，公開版本必須砍掉這兩個欄位。

## 6. C：API_M17 註記 8 的「未驗證」項——全域角色實測矩陣

對真實後端用匿名、`BUYER`、`ADMIN`（把新註冊使用者的 `users.role` 改成 `ADMIN`，只動我自己的測試 DB）、`SUPER_ADMIN`（`admin@nextkey.local`）、店主五種身分，呼叫 `TenantController` 的 9 個自助端點。完整矩陣與判讀寫進 `API_M17_Tenant.md` 的新 §2.1（文件 v2.2 → v2.3）。要點：

- 非店主角色**沒有任何一次意外成功**，沒有副作用；店主全部通過。
- `ROLE_ADMIN` 過得了註解，但 9 個端點中有 8 個被 service 層擋下（`E-4031`）；唯一的 200 是 `GET /tenants/my`，且只列自己的成員資格（空清單，不洩漏）。
- **`SUPER_ADMIN` 連註解那關都過不了**（`E-1007`）：全庫沒有 `RoleHierarchy`，`ROLE_SUPER_ADMIN` 不等於註解裡的 `ROLE_ADMIN`。
- 匿名在 §4.2、§4.4 得到 **403**、其餘端點是 401：`SecurityConfig` 的 `.requestMatchers("/v2/tenants/{id}").permitAll()` 不限 HTTP 方法，而且 `{id}` 也符合 `/v2/tenants/my`，兩者在 URL 層被放行，由方法層註解擋下。
- `SCOPE_store:read／write` 是死權限（`Permission` 枚舉沒有 `store:*`、JWT 不寫 `scope` claim）——**讀碼確認，沒有實際偽造 token 量測**。
- 範圍：只量了「該租戶的店主」與「不是成員的 `ADMIN`」；「另一間店鋪的店主」「同店 `STORE_STAFF`」沒有在這次量測。

沒有改任何程式碼：這些行為（SUPER_ADMIN 不能用自助端點、ADMIN 實質無效）看起來是設計而不是缺陷，我只把事實寫進文件，**沒有登記成 DEF**。若使用者認為 `SUPER_ADMIN`／`ADMIN` 應該能用這些端點，那是新需求。

## 7. 驗證

| 檢查 | 結果 |
|---|---|
| 後端（相關類別，`mvn clean` 後重編） | 真實全量重編 386＋306 個檔；`RedisCacheConfigTest` 9／9、`SellerDashboardRealRedisIntegrationTest` 2／2、`ReviewRatingStatsRealRedisIntegrationTest` 1／1、`SellerDashboardServiceCacheTest` 3／3；BUILD SUCCESS |
| 突變驗證（後端 6 個、前端 1 個） | 7／7 擊殺，失敗原因與預期吻合，還原逐位元組一致 |
| `npx eslint`（`ProductList.tsx`、新規格） | 無任何輸出 |
| `npm run build`（含 TypeScript 檢查） | 退出碼 0，68／68 頁 |
| `make validate-e2e`（完整：重建 JAR 與前端、乾淨 DB、全部 Playwright 規格） | **181 案例：177 通過、4 跳過、0 失敗、0 不穩定**；閘門顯示「本地 E2E 守門通過（schema 對齊 ✅ + e2e 全綠 ✅）」；新增的 `E2E-PRODLIST-01`／`02` 是第 146、147 個，皆通過；跳過數與 Sprint 297 相同（4，沒有逐一核對是否為同樣的 4 個）。**耗時 11.7 分鐘，是 Sprint 297 同一閘門（5.6 分鐘）的兩倍多，原因沒有查** |
| 文件守門（改完 API_M17 與追蹤文件後跑） | `ApiRouteDocDriftTest` 2／2、`DocMarkdownFenceTest` 9／9、`TechStackDocDriftTest` 2／2，BUILD SUCCESS |
| pre-push 守門與雲端 CI | 見 §11：pre-push 輕量守門通過；雲端 run 39783070236 三個 job 皆 success（`headSha` 一致）；新整合測試在雲端 2／2 |

## 8. 過程中的失誤與修正

1. **比對腳本用路徑當鍵**：草稿與已發布兩篇貼文的編輯頁正規化成 `<uuid>` 後路徑相同，字典式比對讓它們互相覆蓋，造成一個假差異。改成依順序比對後消失。
2. **第一次播種失敗**：知識庫分類／文章的後端 DTO 要 `slug`，我照前端型別寫沒帶（即已登記的 `DEF-170`，前端建立函式零呼叫點）。補上 `slug` 後重跑；前一次的半成品資料屬於另一個租戶，不影響探針。
3. **起前端的指令接了 `| tail`**：Next 行程撐住管線，工具 200 秒逾時被移到背景；伺服器本身正常，之後改成輸出導檔。
4. **zsh 未加引號的萬用字元**：`rm -f target/…/TEST-*.xml` 沒匹配就中止整條 `&&` 鏈，`mvn` 根本沒跑（沒有任何東西被改動）；改用 `bash -c` 重跑。
5. **用 `python3 -` 從 stdin 餵含超長中文單行的程式碼**改追蹤表，報 `Non-UTF-8 code`（腳本在解析階段失敗，沒有寫入任何東西；以 `grep` 確認追蹤表當時沒有變動）；改用 Edit 工具。
6. **兩個暫存檔誤寫到 scratchpad 以外**（`/tmp/claude-501/` 下的 `uc_files.txt`、`_o.json`），已刪除；`/tmp/claude-501/` 的其他內容是工具環境的，沒有動。
7. **我對追蹤表的第一版 DEF-402 措辭說得太滿**（「搜尋無相關條目」），而 `DEF-330` 其實有一處「公開日曆」；讀其上下文（談已登入買家的讀取）後改成如實描述。
8. **計畫書與追蹤表初稿有三處把推論寫成事實**，自審時才抓到並更正：(a)「既有 E2E 沒有任何一支開這個列表」——其實 `at-m15-e2e.spec.ts:236` 開過，只是沒有任何斷言（我第一次 `grep` 用的是前綴比對，把它和 `/new` 混在一起）；(b) 摘要把 `ADMIN` 在 9 個端點中被 service 層擋下的數量寫成 7 個，矩陣明明是 8 個；(c) 把 knowledge 列表顯示「尚無文章」的原因寫成「草稿不回傳」，其實沒驗證。三處都在 commit 前更正，措辭改成可查證的事實並標明推論。
9. **gate 執行期間我改了新規格的檔頭註解**（只有註解，測試邏輯沒變），違反自己訂的「期間不碰 `frontend/`」；gate 之後 commit 前又為了修正「含沒有商品的店鋪」那句未實測的說法改了一次檔頭註解措辭（同樣只有註解）。改動只有註解，不論 Playwright 何時載入該檔，測試程式碼都相同，所以不影響結果的有效性；但規則本來就是為了讓閘門的結果對應確定的檔案內容。

## 9. 限制（誠實揭露）

1. §3.3 的所有範圍限制：載入時證據、不含互動、其餘 Sprint 297 改動只被間接執行、列表沒有資料列的頁面只驗了空狀態。
2. 全路由掃描有 14 個路由樣式沒走到（§5）；掃描判定的是「崩潰」，不是「正確」。
3. 差異探針的「0 差異」是 Sprint 297 之前 vs HEAD 的**第二次**執行；第一次執行有 1 條路由差異，已用同版本對照證明是雜訊。
4. 雲端單元測試步驟用 `mvn test -q`，日誌不列各類別結果：`RedisCacheConfigTest`（`TC-CACHE-G001`）在雲端只能由 job success **推論**已通過，雲端日誌裡完全找不到這個類別；直接證據是本機的獨立執行（9／9）與 pre-commit 的核心層快速測試。
5. 真實 Stripe、真實外部 SMTP 等外部服務與這輪的驗證無關，仍未對真實環境驗證。

## 10. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | 不做 `DEF-400` | 使用者沒有回「依照建議」；改 lint 守門是政策 | 回「依照建議」即做 |
| 2 | 修 `DEF-401`，用哨兵值 `ALL`，不加 ESLint 規則 | 沒有產品決策；加 lint 規則是設定政策，全前端只有這一處 | 想加規則：與 `DEF-400` 一併做 |
| 3 | `DEF-402`、`DEF-403` 只登記、不修 | 前者是安全姿態（開放日曆給匿名）、後者是法務內容 | 回覆選項即可 |
| 4 | 新增一支永久 E2E（`at-seller-product-list-real.spec.ts`，約 13 秒） | 這頁 5 個月沒被任何測試碰過，回歸測試是修復的一部分 | 刪檔 |
| 5 | 新增 `TC-CACHE-G001` 守門（只涵蓋直接標在方法上的 `@Cacheable`／`@CachePut`） | 把 DEF-390 的人工規則變機械檢查；新增 65 行（含 import、說明註解與掃描 helper） | 刪該案例與 helper |
| 6 | API_M17 §2 表格的「權限」欄維持 `@PreAuthorize` 原文，改在新 §2.1 說明實際行為 | 表格是註解的忠實轉錄，改它會讓文件與程式碼的對照變難 | 直接改欄位文字 |
| 7 | 探針、播種、掃描規格與驅動腳本不進 repo | 一次性量測工具；做法寫進記憶 | 若想保留，另開 Sprint 決定放哪裡 |

## 11. 雲端 CI 與狀態

- commit `f1511436`，於 2026-10-22 push（`e75a82ed..f1511436 main -> main`）。以推送日誌，以及 `git fetch` 後本機 HEAD 與 `origin/main` 同為 `f1511436`、ahead／behind 為 0／0 核實；沒有採信背景任務通知的退出碼。
- pre-commit：Checkstyle、Maven compile、核心層快速測試（需要 test DB，commit 前以同一個指令序列確認 `nk-test` 兩個容器存在）、前端 `eslint --fix`、`tsc --noEmit`、secret 掃描全部一次通過。
- pre-push 輕量守門通過：本機 act 的 Backend Unit Tests、Frontend Lint & Build（ESLint、Type Check、Build 各步驟）皆 `Job succeeded`，schema 漂移守門通過。
- 雲端 CI run [39783070236](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39783070236)：`completed／success`，三個 job（Backend Unit Tests／Backend Integration Tests & Package／Frontend Lint & Build）皆 success。`gh run watch` 結束後另以 `gh run view --json status,conclusion,jobs,headSha` 交叉確認，`headSha` 與本機 commit 一致。下載完整雲端日誌（214209 行）確認：新的 `SellerDashboardRealRedisIntegrationTest` 在整合測試 job 中 `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`；前端 ESLint 步驟沒有 `problems (` 彙總行。
- 雲端單元測試步驟的限制見 §9 第 4 點。

## 12. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `backend/src/test/java/.../core/seller/SellerDashboardRealRedisIntegrationTest.java` | 新增 |
| `backend/src/test/java/.../infrastructure/redis/RedisCacheConfigTest.java` | 新增 `TC-CACHE-G001` 與掃描 helper |
| `frontend/src/components/product/ProductList.tsx` | 「所有類別」改用哨兵值（`DEF-401`） |
| `frontend/e2e/at-seller-product-list-real.spec.ts` | 新增 |
| `docs/02_architecture/api/API_M17_Tenant.md` | v2.2 → v2.3：註記 8 改為「當時未驗證，已實測」、新增註記 9、§1.1 多租戶隔離列校正、新增 §2.1 實測矩陣 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | v3.70 → v3.71：新增 `DEF-401`（✅）、`DEF-402`、`DEF-403` |
| `docs/04_planning/RELEASE_TRACKER.md` | v4.17 → v4.18：Sprint 298 row |
| `docs/04_planning/SPRINT_298_PLAN.md` | 新增（本文件） |

後端主程式碼零變更；前端只改 `ProductList.tsx` 一個檔。
