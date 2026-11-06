# Sprint 331 Plan — 前端結構性問題一次解決：DEF-455～DEF-459

> **狀態：進行中（2026-11-06 起）。** §1～§2（含 §2.4 起每個工作包的突變規格）在跑任何量測、突變之前寫成（預測先登記）；其餘章節在量測與修復完成後補上。

## 1. 起點與範圍

### 1.1 起點

[Sprint 330](SPRINT_330_PLAN.md) 收尾總結寫：「`DEF-455`～`DEF-459` 仍是較大的前端結構題，建議你先決定要不要做，本輪沒碰。」使用者把總結貼回來，在這一句後面用 `==>` 回覆：**「請先徹底解決前端的結構性問題！」**。

這是對「先決定要不要做」的**明確決策**（不是對「請繼續」的推論；互動模式見記憶 `inline-decision-replies-mean-proceed`）：要做，而且要徹底。

- **範圍＝`DEF-455`、`DEF-456`、`DEF-457`、`DEF-458`、`DEF-459` 五項。** `DEF-461`（設計稿 DS 元件放在 `storefront/`）同屬前端結構，但使用者只點名 455～459，追蹤表也寫「下次動這 4 個元件時順便評估」→ 不在範圍（除非工作中必然碰到）。`DEF-454`（Header 窄螢幕溢出）是設計題、`DEF-462`～`DEF-464` 是後端 → 不在範圍。
- **「徹底」的解讀（我的假設，可一句話推翻）**：每一項都要有「根因層級的結構修正＋防止復發的守門＋實測證據」，而不只是把現有清單清掉。`DEF-457` 的追蹤表自己寫「僅觀察，沒有量測效能影響」，所以它的徹底＝**先量測，依數據決定**：數據不支持的改動不做，結論與數據寫進追蹤表（不為了比例而改）。
- **長期授權（2026-10-29）**：本輪 commit 後直接 push，連同 Sprint 330 留在本機的純文件回填 commit `e7cb202f`（S330 總結寫明「刻意不 push，隨下一輪帶出」）。
- 過程中發現的**相鄰問題只登記、不順手修**（延續 Sprint 316～330 的做法），除非它是本輪某項結構修正的必要前提。

### 1.2 成功標準（開工時定義）

**`DEF-458` 前端自動化單元／元件測試**
1. `npm test` 真的跑測試（不再是 `echo … && exit 0`），接進四個地方：husky pre-commit、`make check-frontend`、`act-compat.yml` 與 `ci.yml` 的 frontend job；文件 `LOCAL_CI_VALIDATION.md` 同步。
2. 測試編碼「為什麼這個行為重要」（Rule 9），不是複製實作。每個新測試檔至少經一次突變驗證：把被測行為弄壞，該檔必須轉紅。
3. 起點範圍照追蹤表建議：`lib/*` 純函式、`components/ui/*` 契約、本輪新增的共用 hook。Node 版本約束：CI 與本機都是 Node 20，所以 Vitest 取 4.x（5.x 要 Node ≥ 22.12）、jsdom 取 29.x（30+ 要 Node ≥ 22.22）。

**`DEF-455` 頁面／元件繞過 `services/` 層**
1. `src/app`、`src/components`、`src/hooks` 內**零個**對 `axios`、`@/lib/axios`、`API_ENDPOINTS`、`API_ENDPOINTS_ERP` 的 import（`API_CONFIG` 這種純設定值除外：`proxy.ts` 與 `useChatSocket.ts` 需要它）；由 ESLint `no-restricted-imports` 守門，**不是靠文件**。
2. 請求形狀與錯誤處理行為不變：每搬一個檔跑 `tsc`＋`eslint`＋該頁相關 E2E；全部搬完跑完整守門與前後差異探針。
3. 「錯誤解析散在畫面裡」（追蹤表給的理由）一併收斂：共用 `apiErrorMessage()`，並盤點全專案手寫的 `err as { response?: … }` 斷言（見 §1.3：86 處），能機械等價替換的替換、不能的明列原因。
4. 突變驗證：在一個頁面重新加上 `import apiClient from '@/lib/axios'` → lint 必須轉紅；對照組（`proxy.ts` 的 `API_CONFIG`）必須維持綠。

**`DEF-456` 資料載入樣板重複**
1. 設計並實作共用 hook，行為涵蓋追蹤表點名的「取消、重試、競態」，且**每一項都有單元測試**（依賴變動時舊回應不得覆蓋新回應、卸載後不更新狀態、重試、取消不算錯誤、`enabled=false`…）。
2. 母體用**精確規則**定義（見 §1.3），搬完後用同一支掃描重掃，剩下的逐一說明為什麼不適用（不是「做不完」）。
3. 防止復發的守門：新增頁面再手寫「載入中→成功／失敗」樣板會被擋下（具體機制見 §2.5，在動手前選定）。
4. 行為等價的證據：完整 E2E 全綠＋前後差異探針（每條路由載入時的 API 請求多重集合、可見文字、console 錯誤）無法解釋的差異＝0。
5. 不引入新的執行期相依（自製 hook，不用 SWR／TanStack Query）；取捨寫進 ADR-002。

**`DEF-459` 29 頁沒有 `<h1>`**
1. 用真實瀏覽器對**全部路由**量「載入穩定後可見 `<h1>` 的數量」（匿名／買家／店主／管理員四種身分各走其可達的路由），列出 0 或 ≥2 的路由；靜態 grep 的 29 不當結論。
2. 逐頁決定並修正：真的沒有頁面標題的補上；有兩個的收斂成一個；標題文字不得因此改變使用者可見的既有文案（必要時用視覺隱藏的 `sr-only` 標題）。
3. 守門：自動化測試每次都量（不只這輪量一次）。

**`DEF-457` `'use client'` 93／104**
1. 量測先行：每條路由實際傳輸的 JS 位元組、「為什麼是 client」的分類（只有登入導向 effect／要讀 localStorage 才能取資料／互動表單…）。
2. 依數據決定：只做「有量測到好處」或「有結構價值」的改動；追蹤表的建議是「不建議為了比例而改」，除非數據推翻它。
3. 結論（含不改的理由）寫回追蹤表，並把量測腳本留在可重跑的位置。

**共通**
5. 完整本地 E2E 守門（`make validate-e2e`，不跳過建置）全綠；`npm run lint`（0 warning）、`tsc --noEmit`、`npm run build`、`npm test` 全綠。
6. pre-push（act）通過，雲端 CI 全綠，並核對整合測試數仍為 1033／0。
7. 每個工作包結束就 commit（pre-commit 守門照跑，不使用 `--no-verify`）；push 只在本輪收尾做一次。

### 1.3 動手前用工具核對的事實

以下每一列都是本輪讀原始碼或指令輸出得到的（HEAD＝`e7cb202f`，工作樹乾淨；`tsc --noEmit` 與 `npm run lint` 在基線都是 exit 0）。

| 說法 | 核對結果 |
|---|---|
| 頁面／元件／layout 數量 | `src/app` 下 `page.tsx` **104**、`layout.tsx` **6**；`src/components` 下 `.tsx` **71**；`src/services` 下 `.ts` **35**（含 `erp/` 4 個）；`src` 下單元測試檔 **0**；`e2e/*.spec.ts` **53** 檔 |
| `DEF-455` 的 16 個檔 | 追蹤表清單逐一對上：`(auth)/cart`、`(auth)/checkout`（`page`、`mixed`、`product`）、`admin/audit-logs`、`admin/page`、`admin/purchase-orders`、`admin/tenants`、`admin/tenants/[id]/features`、`admin/tenants/[id]/review`、`dashboard/tenants/[id]/features`、`storefront/ListingDetail`、`tenant/` 四個元件＝**16** |
| 範圍外但也 import `@/lib/api` 的 | `hooks/useChatSocket.ts`（只用 `API_CONFIG.baseUrl` 組 WebSocket URL）與 `proxy.ts`（只用 `API_CONFIG.baseUrl` 組 CSP 的 `connect-src`）——**沒有發 HTTP 請求**，所以守門規則只禁 `API_ENDPOINTS*`／`axios`，不禁 `API_CONFIG` |
| `services/` 層既有慣例 | 每個檔案是 `class XxxService` 加預設匯出的單例，方法回傳已拆掉 `response.data.data` 的資料，型別與各自的 `ApiResponse<T>` 在檔內宣告（例：`tenantMember.ts`、`order.ts`） |
| 購物車、租戶、管理台目前有沒有 service | **沒有**。`services/` 下沒有 `cart.ts`、`tenant.ts`、`admin*.ts`；`API_ENDPOINTS.cart.*` 只有 `listing.ts` 用到 `cart.count` 一處 |
| 手寫的錯誤形狀斷言 | `grep -rhoE "as \{ ?response" src` 共 **86** 處，其中 `services/auth.ts` 1 處（`getApiErrorInfo` 本身）、其餘 **85** 處散在 **59** 個檔案；最大宗的寫法是 `as { response?: { data?: { message?: string } } }`（72 處）。共用函式 `getApiErrorInfo` 已存在於 `services/auth.ts`，8 個檔案在用 |
| `DEF-456` 母體（追蹤表口徑） | 掃 `page.tsx` 104 個：`useEffect` **85**、`setError` **49**（與追蹤表相同）；loading state **66**、三者齊備 **43**（以「檔案含 `setLoading`／`setError`／`useEffect` 字串」判定；改以 `const [x, setX] = useState` 解構判定則是 46。追蹤表寫 64／41，差 2，是判定式不同，不是母體變了）。這個口徑**會漏也會多抓**，所以下一列另用精確規則 |
| `DEF-456` 母體（本輪精確規則） | 規則：檔內有一個 async 函式同時含 `setLoading(true)` 與 `catch`，且該函式被某個 `useEffect` 呼叫或列在其依賴裡。掃 `page.tsx`＋`components/**`＋`hooks/**`：符合的檔案 **45**；含 async 載入函式但不是 effect 觸發的（表單送出等）**12**。與寬鬆口徑（解構判定的 loading＋`setError`＋`useEffect`，`page.tsx` 46 個＋`components/` 18 個＝64）比：寬鬆有精確沒有 **28**（多半是 loading 與 effect 分開寫、或表單），精確有寬鬆沒有 **9**。**結論：母體約 45～73 個檔案（兩個口徑的聯集 73），邊界模糊，必須逐檔讀過才能分類** |
| 登入導向的重複 | `page.tsx` 含 `isAuthenticated()` 的 **40** 個：`dashboard` 37、`(auth)` 2、`admin` 1。`(auth)` 路由群組在 layout 層已有 `AuthGuard`（DEF-413，Sprint 302）；`SellerShell` 的檔頭註解寫「登入守門不放在這裡：這些頁面各自檢查登入並導去 /login」。許多 `dashboard` 頁是 30～40 行的薄殼：一個 `useEffect` 做登入檢查，其餘是 `<PageTitle>` 加一個領域元件 |
| `'use client'` | `page.tsx` **93／104**；`components` **49／71**。93 個 client 頁**沒有任何一個**「頁面檔自己既沒有 hook 呼叫、也沒有事件處理」（用 `use[A-Z]\w*\(` 與 `on(Click|Change|Submit…)=` 掃） |
| `<h1>` 靜態口徑 | `page.tsx` 本身沒有 `<h1>` 也沒有 `<PageTitle>` 的 **29** 個（與追蹤表相同）；`CardTitle` 渲染的是 **`<h3>`**，所以用 `CardTitle` 當頁面主標題的頁面（登入、註冊…）頁內只有 `h3` 沒有 `h1`；`StoreLayout.tsx` 另有一個寫死的 `<h1>{profile.name}</h1>`，與店鋪子頁自己的標題可能重複 |
| 文件標題 `<title>` | `export const metadata` 只出現在 `app/layout.tsx`（全站預設標題）、`privacy/page.tsx`、`terms/page.tsx` 三處——**其餘 101 個路由共用同一個標題**。Client 頁面不能 export `metadata`。這不在 `DEF-455`～`459` 內，是量測 `DEF-457` 時發現的相鄰問題（§9 登記，是否處理見 §2.2 的 P-T） |
| 測試工具鏈的版本約束 | 本機 Node 20.19.5、CI `NODE_VERSION: '20'`；`vitest@5.0.5` 的 `engines.node` 是 `^22.12.0 \|\| ^24.0.0 \|\| >=26.0.0`，`vitest@4.1.12` 是 `^20.0.0 \|\| ^22.0.0 \|\| >=24.0.0`；`jsdom@30.1.3`／`31.0.0` 要 `^22.22.2`，`jsdom@29.1.1` 是 `^20.19.0 …`。→ **`vitest@^4.1.12`＋`jsdom@^29.1.1`**（4.1.12 發布於 2026-10-15，不是剛出爐的版本） |
| Next 16 文件 | `frontend/AGENTS.md` 要求寫前端前先讀 `node_modules/next/dist/docs/`。已讀 `05-server-and-client-components.md`：Client Component 的適用情境包含 state／事件處理／`useEffect`／瀏覽器 API／**自訂 hook**；`page` 的 `params` 在 Server Component 是 `Promise`。DEF-457 動手前會再讀 `use-client` 指令與 server/client 邊界兩篇 |
| 守門與基線 | `.next` 的 `BUILD_ID`（08:46）比所有 `src` 檔案新，與 HEAD 一致；`backend/target/*.jar` 建於 11:13（S330 最後一個 commit 11:33，後端本輪不動，JAR 只當 E2E 的後端）；基線 E2E（`E2E_GATE_SKIP_BUILD=1 make validate-e2e`）在 HEAD 上先跑一次，結果見 §3.1 |

## 2. 量測設計與預測（跑之前登記）

### 2.1 做法

**基線先於一切修改**：在動任何 `src` 之前，從 `git archive HEAD` 取一份「前版副本」，建置後起一份**自己的**全棧（Postgres 55432／Redis 56379／JAR／`npm start`:3000，與 gate 的埠相同，所以**不與 gate 同時跑**），以真實 API 播種（買家、店主＋店鋪＋商品＋房源、管理員、訂單、退貨、工單…，沿用 `e2e/helpers/store.ts` 的 `seedStore`），再用一支暫放的探針規格走全部路由。這份快照同時服務三件事：①`DEF-459` 的 `<h1>` 實測；②`DEF-457` 的標題與 JS 位元組量測；③`DEF-455`／`DEF-456` 修完後的前後差異比對。探針規格依 S298 配方：每條路由 `goto`→第一觀察窗 3.5 秒→第二觀察窗 2.5 秒，記錄 API 請求多重集合（query 的時間戳正規化）、console／pageerror、HTTP ≥ 400、最終路徑、數字正規化後的可見文字、`<h1>` 數、`document.title`。**雜訊基準**：同一份建置跑兩次互相比，判讀用「前版 vs 後版第二次」。

### 2.2 預測（量測之前登記；每一條事後都要對答案，不吻合就寫出來）

| # | 預測 | 若不吻合代表 |
|---|---|---|
| P-459a | 真實瀏覽器載入穩定後，可見 `<h1>` 數為 **0** 的路由數落在 **15～29** 之間（低於靜態的 29，因為部分頁面的標題在領域元件裡）。集中在：認證頁（登入／註冊／忘記密碼／重設密碼／驗證信箱，用 `CardTitle`＝`h3`）、付款結果頁 4 個、ERP 列表頁、`/dashboard`、`/admin`、`oauth/callback` | 低於 15：靜態 grep 高估很多（標題大多在元件裡）；高於 29：還有靜態抓不到的（載入中／錯誤狀態沒有標題） |
| P-459b | 可見 `<h1>` 數 **≥ 2** 的路由 ≤ 3 個，候選是店鋪子頁（`StoreLayout` 寫死的 `<h1>` 加子頁自己的標題） | 更多：共用外殼本身就貢獻 `<h1>` |
| P-457a | 93 個 client 頁裡，**至少 30 個**是薄殼：頁面檔自己的 hook 只有 `useEffect`（登入導向）／`useRouter`／`useParams`，其餘是標題加領域元件 | 少於 30：薄殼比想像的少，layout 層統一守門的價值較小 |
| P-457b | 把**一個**薄殼頁改成 Server Component（登入導向移到別處），該路由的傳輸 JS 位元組變化 **< 2%**——因為重的程式碼在它 import 的 client 元件裡，頁面檔本身只有幾十行 | 若 ≥ 2%：薄殼拖著不該拖的相依，值得系統性處理 |
| P-457c | 需要 `useEffect`＋`localStorage` 的 JWT 才能取資料的頁面（所有登入後頁面）**不可能**改成在伺服器取資料——認證憑證在瀏覽器的 `localStorage`，伺服器端沒有；公開店面頁（約 8 個）理論上可以，但那是 SEO／效能功能，不是結構修補 | 若伺服器能取到憑證（例如 cookie）：結論要重寫 |
| P-T | 全部 104 個路由的 `document.title` 只有 **3 種**（預設＋`privacy`＋`terms`） | 若更多：有頁面在 client 端自己改了 `document.title` |
| P-456a | 差異探針：`DEF-455`／`DEF-456` 修完後，**每條路由載入時**的 API 請求多重集合與前版**完全相同**（載入階段的行為不該變）。唯一預期例外：把兩次載入合併的頁面（若有）請求數會變少，每一個都必須在 §6 解釋 | 出現未預期的請求增減＝遷移改變了行為，逐路由追 |
| P-456b | 母體裡**至少有 5 個**檔案的載入邏輯不適合直接套 hook（輪詢、多資源互相依賴、載入同時負責表單初值…），搬的時候要留下不搬的理由 | 若全部都適合：hook 設計夠通用，或我把不適合的硬塞進去了——要回頭檢查 |
| P-458a | 新增的 Vitest 測試套件（約 100 個案例）整體 < 30 秒（含 jsdom 啟動） | 更慢：換環境（happy-dom）或拆分 |
| P-455a | 16 個檔案搬完後，`apiClient` 的呼叫點（`services/`）新增 **約 35～45 個方法**，沒有任何一個方法需要改後端 | 若需要改後端：發現了契約問題，登記 |
| P-455b | 把 86 處手寫斷言換成共用函式後，`tsc` 與 `eslint` 零錯誤，且**每一處**都能機械地等價替換（`message || fallback`）；需要看 `status`／`code` 的少數處改用 `getApiErrorInfo` | 若有處不等價：該處原本就有 bug 或刻意不同，逐處登記 |

### 2.3 Hook 設計（動手前定案；ADR-002 會完整記錄）

- 名稱 `useAsyncData<T>(fetcher, deps, options)`，放 `src/hooks/useAsyncData.ts`。回傳 `{ data, error, loading, reload, setData }`。
- **`loading` 與 `error` 是衍生值，不在 effect 裡同步 `setState`**：state 只存「最近一次**完成**的請求」`{ deps, data, error }`；`loading ＝ enabled 且 (尚無完成結果 或 完成結果的 deps 與目前 deps 不同)`。依賴一變，下一次 render 就已經是 `loading`（沒有「舊資料配舊的 loading=false」的閃爍），而 effect 只負責啟動請求並在 `.then` 裡（非同步）寫入結果——符合 React 19 的 `react-hooks/set-state-in-effect`（記憶 `nextjs16-strict-react-hooks-lint`）。
- **競態與取消**：每次 effect 建一個 `AbortController`，cleanup（依賴改變／卸載／重試）時 `abort()`；`.then` 內先檢查 `signal.aborted` 再寫入——即使 fetcher 沒有把 `signal` 傳給 axios，舊回應也不會蓋掉新的。fetcher 收到 `signal`，需要真正中止網路請求的 service 方法可以轉給 axios。`CanceledError` 不算錯誤。
- **重試**：`reload()` 是遞增一個內部計數器（事件處理器裡 `setState`，合法），計數器併入依賴，所以走同一條路徑。
- **錯誤訊息**：`options.errorMessage` 可以是字串（後備文案；有伺服器訊息時優先用伺服器訊息，與現有頁面 `message || fallback` 行為相同）或函式 `(info: ApiErrorInfo) => string`（需要依 `status` 分流的頁面，例如 404／403）。
- **`enabled`**：為 `false` 時不發請求、`loading=false`。
- **`setData`**：讓頁面保留既有的「本地樂觀更新」寫法（`setCart(prev => …)`），不必每次都重新載入。
- **依賴檢查**：`eslint.config.mjs` 把 `react-hooks/exhaustive-deps` 的 `additionalHooks` 加上 `useAsyncData`，呼叫端漏列依賴會被 lint 擋下（hook 內部那一個 `useEffect` 因為依賴是展開的陣列，必須有一行帶理由的 `eslint-disable`）。
- **為什麼自製、不用 SWR／TanStack Query**：①需求只有「載入／失敗／重試／競態」，不需要快取、去重、背景重新驗證；②登入憑證在 `localStorage`，沒有 SSR 預取的場景；③不增加執行期相依與審計面（`dependency-audit` 每週掃描）；④語意貼近既有頁面的寫法（`loading`／`error` 字串／整頁替換），遷移是機械式的；⑤測試與取捨可完整掌握。代價：日後若真的要快取或去重，要換庫（hook 的呼叫介面刻意與 `useQuery` 同形，遷移面積小）。

### 2.4 工作包與順序（依相依排序）

| 工作包 | 內容 | 前置 |
|---|---|---|
| WP0 | 基線：gate 基線 E2E → 前版副本＋自己的全棧＋探針（快照 `pre`）+ 雜訊基準 | — |
| WP1 | `DEF-458`：Vitest 基礎設施＋接進四個守門＋`lib/*`、`ui/*` 的初始測試 | — |
| WP2 | `DEF-455`：`lib/api-error.ts`＋`cart`／`tenant`／`admin` 三個 service＋逐檔搬 16 個＋ESLint 守門＋86 處斷言收斂 | WP1（共用函式要有單元測試） |
| WP3 | `DEF-456`：`useAsyncData`（紅燈先行的單元測試）＋逐檔遷移＋守門 | WP1、WP2 |
| WP4 | `DEF-459`：依 WP0 實測逐頁修正＋守門 | WP0 |
| WP5 | `DEF-457`：依 WP0 實測決定；寫回結論 | WP0、WP3 |
| WP6 | 收尾：ADR-002、規範文件、追蹤表、RELEASE_TRACKER、`LOCAL_CI_VALIDATION.md`；完整 gate；前後差異；commit／push；Docker 清理；記憶 | 全部 |

突變驗證規格（每個工作包開工前補在此，驅動腳本內的 `must` 與表相同）：

#### 2.4.1 WP1（`DEF-458` 初始測試套件；跑之前登記）

做法：驅動腳本（放 scratchpad，不進 repo）逐一對來源檔套用突變——`old` 必須在檔案內**恰好出現一次**才套用——跑**完整** `vitest run`（不只相關檔，才看得到連帶紅燈），以失敗案例集合與下表 `must` **完全相同**才算吻合；全部原始檔事先備份，`finally` 還原並比對 SHA-256。突變一律用型別安全的寫法（`&& Date.now() < 0`，不用 `false &&`）。先跑無突變的 Z0，必須 80／80 綠。

| # | 檔案 | 突變 | 預期恰好紅的案例（`must`） |
|---|---|---|---|
| M01 | `lib/auth-redirect.ts` | 拿掉「正規化後以 `//` 開頭」檢查 | 正規化後變成 `//host` |
| M02 | 同上 | 拿掉「登入流程頁面不導回」檢查 | 7 個「不導回登入流程頁面 …」 |
| M03 | 同上 | 拿掉 `url.origin !== origin` 檢查 | 協定相對網址 `//host`、反斜線 `/\host` |
| M04 | 同上 | 拿掉 `startsWith('/')`（只留空值檢查） | 「不是 / 開頭的相對路徑」、「絕對網址（同源也不收…）」 |
| M05 | 同上 | `landingPathForRole` 少一個 `SUPER_ADMIN` case | `SUPER_ADMIN → /admin` |
| M06 | 同上 | `loginHref` 在登入流程頁面也帶 redirect | 人已經在登入流程頁面時不帶 redirect |
| M07 | `lib/date.ts` | 改用 `toISOString().slice(0,10)` | 台灣凌晨 00:30、月份與日期補零 |
| M08 | 同上 | 月份不補零 | 月份與日期補零 |
| M09 | `lib/status.ts` | `featureStatusEntry` 先判斷 `!enabled` 再判斷 PENDING（DEF-359 的退化） | 需要管理員審核且為 PENDING → 待審核 |
| M10 | 同上 | 知識庫文章「草稿」改 `warning` | 語意規則「草稿＝中性」 |
| M11 | 同上 | `statusEntry` 找不到時回空標籤 | `statusEntry` 未登錄原樣顯示、`StatusBadge` 未登錄原樣顯示 |
| M12 | 同上 | 採購單「已駁回」改 `warning` | 語意規則「駁回／取消…＝負面」 |
| M13 | `lib/utils.ts` | `cn` 不再 `twMerge` | 衝突的 utility 以後面的為準 |
| M14 | `ui/toast.tsx` | 錯誤通知停留時間 6000→4000 | 成功 4 秒…錯誤多留到 6 秒 |
| M15 | 同上 | `dismiss` 不清計時器 | 手動關閉…清掉計時器 |
| M16 | 同上 | `role` 一律 `status` | 錯誤用 role=alert… |
| M17 | `ui/confirm-dialog.tsx` | `settle` 不比對是哪個請求 | 同時有兩個請求… |
| M18 | 同上 | 對話框被關閉（取消／ESC）時回 `true` | 確定／取消、ESC、同時有兩個請求、破壞性請求外觀 共 4 個 |
| M19 | 同上 | `destructive` 不換變體 | 破壞性請求的確認鈕外觀… |
| M20 | `ui/pagination.tsx` | `go` 不擋「目前頁」 | 點目前這一頁不觸發 |
| M21 | 同上 | `total <= 7` 改 `<= 5` | 頁數不多（≤7）全部列出 |
| M22 | 同上 | 「上一頁」不再 disabled | 第一頁的「上一頁」… |
| M23 | `ui/page-title.tsx` | `h1`→`h2` | PageTitle 是 `<h1>` |
| M24 | `ui/card.tsx` | `CardTitle` `h3`→`h1` | CardTitle 是 `<h3>`… |
| M25 | `ui/states.tsx` | `LoadingState` 不轉傳其餘屬性 | LoadingState 轉傳 data-testid／id… |
| M26 | 同上 | `EmptyState` 不轉傳其餘屬性 | EmptyState 轉傳 data-testid… |
| M27 | 同上 | `EmptyState` 圖示拿掉 `aria-hidden` | EmptyState 轉傳 data-testid…（含圖示裝飾斷言） |
| M28 | 同上 | `LoadingState` 預設文案改英文 | LoadingState 轉傳…預設文案 |
| M29 | `ui/chip.tsx` | 拿掉 `aria-pressed` | Chip 選取態用 aria-pressed… |
| M30 | 同上 | 預設 `type` 改 `submit` | Chip 預設 type="button" |
| M31 | `ui/status-badge.tsx` | 忽略 `entry` 屬性 | StatusBadge 也可以直接給 entry |
| M32 | `ui/alert.tsx` | 拿掉 `role="alert"` | Alert 一律 role="alert" |

預測（登記）：32 個突變各自恰好擊中表中案例，**沒有連帶紅燈**；若有，代表測試之間有我沒料到的耦合，如實記錄。

#### 2.4.2 WP2／WP3（hook、錯誤解析、三個 service 的線上格式測試；跑之前登記）

做法同 §2.4.1。下表「預期紅」是**依讀碼推演**的結果；其中 H08 我推演到 4 個案例而不是直覺的 1 個（重試壞掉會連帶讓「舊請求被丟棄」「失敗時保留資料」「重新載入以伺服器為準」三個依賴重試的案例失敗），H03 推演到 4 個（沒有中止就沒有 `aborted` 旗標，三個「舊回應不得覆蓋」的案例都靠它）。**已知的弱點先講**：「被我們自己中止的請求（CanceledError）不算錯誤」這個案例不會被任何單一的 hook 突變單獨擊殺——因為被中止請求的錯誤會掛在過期的鍵上，畫面上看不到；真正守住『不顯示過期錯誤』的是「先送出的請求較晚失敗」那個案例（H02）。這個案例保留作為契約說明與整合冒煙測試，不計入擊殺數。

| # | 檔案 | 突變 | 預期恰好紅的案例（`must`） |
|---|---|---|---|
| H01 | `hooks/useAsyncData.ts` | 成功回呼不檢查 `signal.aborted` | 先送出的請求較晚回來…、重試時舊請求的回應會被丟棄 |
| H02 | 同上 | 失敗回呼不檢查 `signal.aborted` | 先送出的請求較晚「失敗」… |
| H03 | 同上 | cleanup 不呼叫 `abort()` | 先送出較晚回來、較晚失敗、重試時舊請求、依賴改變時中止前一個請求（共 4） |
| H04 | 同上 | `loading` 只看「有沒有過完成結果」（不比對鍵） | 依賴改變：該次 render 就是 loading、reload：同一個 render 就回到 loading（共 2） |
| H05 | 同上 | `error` 不比對鍵（重試後仍顯示上一輪的錯誤） | reload：同一個 render 就回到 loading、錯誤清掉 |
| H06 | 同上 | 忽略 `enabled`（停用時照樣發請求） | enabled=false 不發請求… |
| H07 | 同上 | 字串型 `errorMessage` 不再讓後端訊息優先 | 後端有訊息就顯示後端的訊息 |
| H08 | 同上 | `reload()` 變成空操作 | reload…、重試時舊請求、失敗時保留上一次成功的資料、之後重新載入以伺服器資料為準（共 4） |
| H09 | 同上 | 失敗時清掉資料 | 失敗時保留上一次成功的資料 |
| H10 | 同上 | `setData` 不處理函式更新 | 可以直接寫入或用函式更新現有資料 |
| H12 | 同上 | effect 依賴不含 `enabled` | enabled=false…變成 true 才發 |
| H13 | 同上 | 函式型 `errorMessage` 被忽略 | errorMessage 可以是函式… |
| H14 | `lib/api-error.ts` | `apiErrorMessage` 用 `??`（空字串不落到後備文案） | 後端沒給訊息（或給空字串）用後備文案 |
| H15 | 同上 | `getApiErrorInfo` 拿掉 `null` 防護 | `null → 空物件（不丟例外）` |
| H16 | `services/cart.ts` | `validatePromo` 不做 URL 編碼 | validatePromo：代碼要做 URL 編碼 |
| H17 | 同上 | `applyPromo` 一律帶 `storeId` 鍵 | applyPromo：沒有 storeId 時請求本文「只有」promoCode |
| H18 | 同上 | `removePromo` 一律帶設定物件 | removePromo：有 storeId 才帶 params… |
| H19 | `services/tenant.ts` | `listMine` 拿掉 `\|\| []` | listMine：回傳 tenants 陣列；後端沒給時是空陣列 |
| H20 | 同上 | `getMyFeatures` 拿掉配額預設值 | getMyFeatures：配額沒給時補空陣列 |
| H21 | `services/admin.ts` | `listPendingPurchaseOrders` 一律帶設定物件 | listPendingPurchaseOrders：總覽頁不帶任何設定… |
| H22 | 同上 | `listTenantApplications` 拿掉 `\|\| []` | listTenantApplications：後端沒給時是空陣列 |
| H23 | 同上 | `rejectPurchaseOrder` 把 id 當成理由送出 | 核准不帶本文；駁回本文是 { reason }（採購單） |
| H24 | `services/cart.ts` | `getCart` 不轉傳 `options` | getCart：把 signal 轉給 axios |

預測（登記）：22 個突變各自恰好擊中表中案例；第一次若有不吻合，如實記錄並說明原因。

**補登記（hook 新增 `onError`／`errorInfo` 之後、跑之前）**：

| # | 檔案 | 突變 | 預期恰好紅的案例（`must`） |
|---|---|---|---|
| H25 | `hooks/useAsyncData.ts` | `onError` 不檢查請求是否已過期就呼叫（放在 `aborted` 檢查之前） | onError：成功不呼叫；被中止或已過期的請求失敗也不呼叫 |
| H26 | 同上 | `onError` 從不呼叫 | onError：這一輪載入失敗時呼叫一次… |
| H27 | 同上 | `errorInfo` 不比對鍵（重試後仍留著上一輪的錯誤資訊） | errorInfo：失敗時有 HTTP 狀態與後端錯誤碼… |

另外 H19 第一輪（見 §3）因服務測試用 `clearAllMocks`，失敗案例沒用完的 `mockResolvedValueOnce` 佇列漏到下一個案例而多紅 1 個；已改 `resetAllMocks`，H19 會重跑。

## 3. 量測結果

（待補：§3.1 基線 E2E；§3.2 前版探針與雜訊基準；§3.3 對答案。）
