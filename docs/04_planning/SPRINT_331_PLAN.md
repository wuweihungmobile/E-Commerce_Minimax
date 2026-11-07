# Sprint 331 Plan — 前端結構性問題一次解決：DEF-455～DEF-459

> **狀態：完成（2026-11-06 起，2026-11-07 收尾）。** §1～§2（含 §2.4.1～§2.4.3 每個工作包的突變規格）在跑任何量測、突變之前寫成（預測先登記）；§3 之後是量測、實作與驗證的結果。push 與雲端 CI 結果見 §6.3。

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
3. 防止復發的守門：新增頁面再手寫「載入中→成功／失敗」樣板會被擋下（具體機制見 ADR-002 決定 3 與 6、本計畫 §4.3；動手前選定的是『把規則寫成讀原始碼的測試』，而不是 ESLint 自訂規則）。
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

#### 2.4.3 WP3／WP4 守門測試與後補的服務測試（跑之前登記；檢查點二提交之後、突變驗證之前寫下）

**為什麼有這一節**：DEF-456 的守門（`src/architecture/data-loading.test.ts`）與 DEF-459／登入守門的靜態守門（`page-structure.test.ts`）是在 WP3 後半才寫的，原先的 §2.4.1／§2.4.2 沒有涵蓋。守門測試如果自己不會紅，等於沒有守門，所以照同一套做法驗證：把受守護的原始碼或守門的偵測器弄壞，指定的案例要恰好紅。突變驅動腳本要求目標檔在 git 上是乾淨的，所以先提交檢查點二（`38c5e47a`）再跑。

預測的紅燈集合是**依讀碼推演**：G12 推演到 2 個（偵測器拿掉 `.then` 之後，單元案例 1 紅；而 `ALLOWED` 裡兩筆只靠 `.then` 命中的例外——購物車數量事件訂閱、blob 圖片——不再命中，「還在命中」紅）、G13 推演到 2 個（縮小掃描範圍：「掃描範圍」與「還在命中」）、G14 推演到 4 個（掃描函式變成空：兩份守門的「掃描範圍」共 3 個，加上 `ALLOWED` 的「還在命中」；其餘規則案例會**假綠燈**——這正是需要「掃描範圍」案例的原因）。

| # | 檔案 | 突變 | 預期恰好紅的案例（`must`，子字串比對） |
|---|---|---|---|
| G01 | `app/(auth)/addresses/page.tsx` | 在 `useAsyncData` 之後加一個 `useEffect(() => { AddressService.list().then((r) => setData(r)) })`（形狀 1：effect 自己發請求再 setState） | 沒有手寫的「在 effect 裡載入資料再 setState」 |
| G02 | `app/(auth)/support/tickets/page.tsx` | 加 `async function loadAgain()`（內含 `setData(await …)`）並在 `useEffect` 呼叫它（形狀 2：載入函式寫在 effect 外面） | 同上 |
| G03 | `app/(auth)/orders/page.tsx` | 加 `useCallback(async (signal?: { cancelled: boolean }) => {…setPage(await …)})` 並由 `useEffect` 呼叫（曾是偵測器盲點的形狀） | 同上 |
| G04 | `architecture/data-loading.test.ts` | 把 `ALLOWED` 的 `StorefrontHeader` 鍵名改錯一個字 | 沒有手寫的…、ALLOWED 的每一筆都還在命中（共 2） |
| G05 | 同上 | 在 `ALLOWED` 加一筆假例外（`components/ui/page-title.tsx`，它沒有任何手寫載入） | ALLOWED 的每一筆都還在命中 |
| G06 | 同上 | 把一筆例外的理由改成「略」 | 每一筆例外都寫了理由 |
| G07 | `app/dashboard/products/page.tsx` | 加回 `if (!AuthService.isAuthenticated()) return null` | 賣家區域的頁面不再自己檢查登入 |
| G08 | `components/layout/SellerShell.tsx` | 拿掉 `<AuthGuard>…</AuthGuard>`（內容直接渲染） | SellerShell 用 AuthGuard 包住頁面內容 |
| G09 | `components/product/ProductForm.tsx` | 把唯一的 `<PageTitle>` 換成 `<p>`（`products/new` 的標題只在這個元件裡） | 每個 page.tsx 自己或它（傳遞式）引用的元件裡有 <PageTitle> 或 <h1> |
| G10 | `architecture/detect.ts` | `hasHeadingTransitively` 不再追 import | 追得到元件裡的標題、每個 page.tsx 自己或它…（共 2） |
| G11 | 同上 | `asyncFunctions` 用「參數後第一個大括號」找函式本體（回到盲點） | 參數型別裡有大括號的 useCallback…、括號不成對：丟錯而不是靜默漏掉（共 2；第二個是寫表時漏推演、跑之前補上：這個突變讓參數括號不再被配對，也就不會丟「括號不成對」） |
| G12 | 同上 | 效果偵測拿掉 `.then(` 這一條 | effect 內 .then 之後 setState：違規、ALLOWED 的每一筆都還在命中（共 2） |
| G13 | `architecture/data-loading.test.ts` | `isScanned` 不掃 `components/` | 掃描範圍涵蓋頁面、元件與 hooks、ALLOWED 的每一筆都還在命中（共 2） |
| G14 | `architecture/source.ts` | `listSourceFiles` 永遠回空（`accept` 被忽略） | 掃描範圍涵蓋頁面…（data-loading）、ALLOWED 的每一筆都還在命中、掃描範圍涵蓋賣家三個區域、掃描範圍涵蓋全部頁面（共 4） |
| G15 | `architecture/detect.ts` | 追 import 時不排除 `components/ui/page-title.tsx`（回到 G09 發現的有洞版本）——**G09 第一輪不吻合之後補登記**：G09 把 `ProductForm` 的 `<PageTitle>` 換成 `<p>`，守門卻沒紅，原因是 `ProductForm` 還留著 `PageTitle` 的 import，追進它的定義檔就撿到那裡面的 `<h1>`；修正是追 import 時跳過定義檔，並加單元案例 | 只 import PageTitle 卻沒有渲染它：不算有標題（1） |
| C01 | `services/cms.ts` | `getPublishedPosts` 不轉傳 `options` | getPublishedPosts：tenantId、分頁、分類篩選都放在 query；signal 原樣轉給 axios |
| C02 | 同上 | `getListingCard` 不轉傳 `options` | getListingCard：沒有 query；signal 原樣轉給 axios |
| A01 | `services/authStore.ts` | `getSelectedTenantIdSnapshot` 改讀登入者資料（user）裡的 tenantId | 讀 localStorage 的 tenantId、不看登入者資料（user）裡的 tenantId（共 2） |

預測（登記）：17 個突變各自恰好擊中表中案例，沒有連帶紅燈（G15 是 G09 失敗後補登記的第 18 個；修正偵測器後 G09、G10 重跑，G10 的 `old` 隨修正過的那一行改寫）。G01～G03 與 G09 是對**真實頁面**下手的，其餘是對守門自己下手。已知弱點先講：偵測器只做括號計數（不解析字串、註解、正規表達式），遇到括號不成對會丟錯而不是靜默漏掉；`HEADING` 用文字比對，註解裡的 `<h1>` 會被誤算成有標題——這兩點是刻意的取捨，不是遺漏。

## 3. 量測結果（前版基線）

### 3.1 基線 E2E

HEAD＝`e7cb202f`、工作樹乾淨：`E2E_GATE_SKIP_BUILD=1 make validate-e2e` → **310 項全過（9.4 分）**；後端記錄到認證端點被限流 **0** 次。

### 3.2 前版探針與雜訊基準

做法見 §2.1。前版副本是 `git archive e7cb202f` 取出後自行 `npm ci`＋`next build`，與自己的全棧（Postgres 55432／Redis 56379／JAR／`npm start`:3000）一起跑；以真實 API 播種（買家、店主＋店鋪＋商品＋房源、管理員、供應商、採購單、貼文、訂單、訂房、退貨、工單），把資料庫快照成 `probe.dump`，讓前後兩輪從同一份資料起跑。四種身分走全部可達路由，共 **111 次造訪**，涵蓋 **104 個不重複的目的頁面**（與 `page.tsx` 數相同；5 次未登入的造訪被導去 `/login`，併入登入頁）。

**雜訊基準**：同一份前版建置連跑兩次（A、B），逐路由比對 API 請求多重集合、數字正規化後的可見文字、標題、`<h1>`、console 與 HTTP 錯誤，**差異 0**。所以後面「前版 vs 後版」出現的任何差異都不是時間或亂數造成的。

穩定後（第二個觀察窗結束時）的 `<h1>` 數：

| 身分 | 造訪數 | `<h1>`＝0 | `<h1>`＝1 | `<h1>`≥2 |
|---|---|---|---|---|
| anon | 26 | 17 | 9 | 0 |
| buyer | 23 | 5 | 18 | 0 |
| owner | 53 | 11 | 42 | 0 |
| admin | 9 | 1 | 8 | 0 |
| 合計 | 111 | 34 | 77 | 0 |

- **沒有 `<h1>` 的不重複目的頁面：29 個**——`/`、`/products`、`/rooms`、`/login`、`/register`、`/forgot-password`、`/reset-password`、`/verify-email`、`/oauth/callback/[provider]`、`/bookings/[id]/payment/{cancel,success}`、`/orders/[id]/payment/{cancel,success}`、`/returns/new`、`/blog/[slug]`、`/blog/category/[id]`、`/dashboard`、`/dashboard/erp/{inventory,inventory/[skuId],purchase-orders,stock-movements,suppliers}`、`/dashboard/products/{new,[id]/edit}`、`/dashboard/rooms/{new,[id]/edit}`、`/dashboard/support/tickets/[id]`、`/admin`、`/admin/tenants`。靜態口徑（頁面本身或它傳遞式引用的元件有沒有 `<PageTitle>`／`<h1>`）是 24 個；多出的 5 個是「程式裡有標題、但探針穩定後的畫面沒有」：`/blog/[slug]`（匿名身分沒有選用店鋪，永遠停在「載入中...」）、`/blog/category/[id]`（匿名身分只看到登入提示，標題只在分類載入成功時才有）、`/returns/new`（缺訂單參數的錯誤畫面）、`/dashboard/support/tickets/[id]`（載入／錯誤畫面）、`/admin/tenants`（未登入也沒被導走，只看到錯誤畫面）。這就是 `DEF-459` 要求「用真實瀏覽器量、不只靠 grep」的原因。
- **`<h1>` ≥ 2 的頁面：0 個**。
- **`document.title`**：111 次造訪裡 109 次是同一個標題（`意象若水 RUOSHUI｜生活減法，無負擔的購物體驗`），只有 `/privacy`、`/terms` 有自己的（`export const metadata`）。沒標 `'use client'` 的頁面其實有 11 個（`/`、`/products`、`/rooms`、`/listings/[id]`、`/stores/[slug]` 與它的 `products`／`rooms`／`blog` 子頁、`/privacy`、`/terms`、`/tenant/apply`），其中只有那兩個 export 了 `metadata`。

### 3.3 JS 傳輸量（冷快取，前版；單位 KB＝1024 bytes）

| 身分 | 路由 | 腳本數 | 傳輸量（KB） | 解壓後（KB） |
|---|---|---|---|---|
| anon | `/` | 11 | 191.5 | 606.5 |
| anon | `/products` | 11 | 191.2 | 605.2 |
| anon | `/listings/<uuid>` | 11 | 193.8 | 612.5 |
| anon | `/login` | 11 | 190.3 | 603.0 |
| anon | `/privacy` | 10 | 187.0 | 594.9 |
| buyer | `/orders` | 11 | 190.4 | 603.6 |
| buyer | `/cart` | 11 | 192.4 | 611.3 |
| owner | `/dashboard/products` | 12 | 209.0 | 655.0 |
| owner | `/dashboard/orders` | 12 | 208.5 | 653.3 |
| owner | `/dashboard/erp/suppliers/<uuid>` | 11 | 190.1 | 602.4 |
| owner | `/dashboard/products/new` | 12 | 209.2 | 655.5 |
| owner | `/dashboard/returns` | 11 | 189.4 | 600.0 |
| admin | `/admin` | 11 | 188.8 | 597.7 |
| admin | `/admin/tenants` | 11 | 192.2 | 609.3 |
| admin | `/admin/audit-logs` | 11 | 190.2 | 602.2 |

最小的 `/privacy`（Server Component，10 支腳本）187.0 KB，最大的 `/dashboard/products/new`（12 支腳本，多了 Radix Select）209.2 KB；其餘 11 支腳本的路由都落在 188.8～193.8 KB。**路由之間的差距主要來自共用的 chunk（約 190 KB），不是頁面檔本身。**

## 4. 實作紀錄

### 4.1 WP1：Vitest 與測試基礎（`DEF-458`）

- 工具：`vitest@^4.1.12`＋`jsdom@^29.1.1`＋Testing Library（Node 20 相容的最高版本，理由見 §1.3）。`vitest.config.mts` 固定時區 `Asia/Taipei`、`css: false`、`testTimeout: 20_000`（見 §5 的 M16：機器忙時 5 秒太緊）；`vitest.setup.ts` 手動掛 `cleanup`（不開 globals）。
- 接進四個守門：`frontend/.husky/pre-commit` 的 `[3/3]`、`make check-frontend`、`act-compat.yml` 與 `ci.yml` 的 frontend job；`LOCAL_CI_VALIDATION.md` 同步。`npm test` 不再是 `echo`。
- 測試檔（每個都是「為什麼這個行為重要」，不是複製實作）：`lib/{auth-redirect,date,status,utils,api-error}`、`components/ui/{toast,confirm-dialog,pagination,semantics}`、`hooks/useAsyncData`、`services/{cart,tenant,admin,cms,authStore}`、`architecture/{data-loading,page-structure}`。檢查點一 135 案例／13 檔；現在 **166 案例／17 檔**。
- `lib/*` 裡原本靠 Playwright 規格在 Node 裡驅動的純函式（例如 `at-auth-redirect.spec.ts`）這輪沒搬——它們仍然有效，搬不搬是 §9 的相鄰項目。

### 4.2 WP2：services 分層、錯誤解析、守門（`DEF-455`）

- 新增三個 service（共 **25** 個方法）：`cart`（7）、`tenant`（6）、`admin`（12）；讀取方法可選擇接收 `{ signal }`（`RequestOptions`）。16 個繞過 `services/` 的檔案全部改走它們。
- `lib/api-error.ts` 成為錯誤解析的單一來源（`getApiErrorInfo`、`apiErrorMessage`，含後端逐欄位驗證錯誤 `fieldErrors`）；原本 **85 處**（散在 59 個檔案）手寫的 `err as { response?: … }` 斷言收斂成它。現在整個 `src` 裡 `as { response` 只剩 `lib/api-error.ts` 自己 2 處。
- ESLint 守門（`eslint.config.mjs`）：`no-restricted-imports`（`axios`、`@/lib/axios`、`@/lib/api_erp`、`API_ENDPOINTS`，含相對路徑寫法）、`no-restricted-globals`（`fetch`）、`no-restricted-syntax`（手寫的 axios 錯誤形狀斷言 `as { response… }`），只套在 `src/app`、`src/components`、`src/hooks`；另把 `react-hooks/exhaustive-deps` 的 `additionalHooks` 加上 `useAsyncData`，呼叫端漏列依賴會被擋下。用 stdin 探針驗證：7 個壞範例全紅、5 個好範例（含 `proxy.ts` 的 `API_CONFIG`）全綠。

### 4.3 WP3：`useAsyncData` 與全面遷移（`DEF-456`）

**hook**：`hooks/useAsyncData.ts`，行為與設計取捨見 §2.3 與 ADR-002。實作後比 §2.3 的草案多了三件事，都是遷移時發現需要的：①`onError(message, error)`（失敗只用 toast 通知、沒有頁面級錯誤畫面的頁面）；②`errorInfo`（HTTP 狀態與後端錯誤碼，頁面要依 403／404 分流時用）；③`errorMessage` 函式型收到原始錯誤（`err instanceof Error` 之類的判斷）。

**母體與結果**：用守門測試自己的偵測器量（它比動工時的 45 個那條舊規則寬，原因見 ADR-002）：Sprint 開始前，188 個被掃描的檔案（`app/`、`components/`、`hooks/`）裡命中 **90** 個；檢查點一之後 58 個；現在 **4 個**，全是「不是載入資料」的例外（§4.3.2）。遷移後 **84 個檔案、97 個呼叫點**使用 `useAsyncData`（其中 18 個呼叫點把 `signal` 轉給 service，其餘靠 hook 丟棄過期回應；見 §9 `DEF-469`）。每一批都過 `tsc --noEmit` 與 `eslint --max-warnings 0`，每個檢查點跑完整本地 E2E 守門。

#### 4.3.1 遷移手法（這些寫法都已經出現在程式裡，之後的人照著用）

| 情形 | 寫法 | 例 |
|---|---|---|
| 清單／詳情，載入失敗顯示固定文案 | `useAsyncData(fetcher, deps, { errorMessage: () => '固定文案' })`；字串形式表示「後端訊息優先」 | 買家的預訂／訂單／工單／退貨／地址清單 |
| 失敗要依 HTTP 狀態分流 | `errorMessage` 函式或 `errorInfo.status` 衍生 `loading／ok／notfound／error／auth` | `StoreLayout`、`ListingDetail`、`TenantEditForm` |
| 篩選／分頁 | 條件放進 deps；`enabled` 控制「還沒有條件／沒有權限就不發請求」 | 管理端店鋪列表（一個 hook 依 `filter` 切換成申請或店鋪）、`members` |
| 寫入後就地更新 | `setData`（不重抓、不閃載入畫面） | 訂單取消只改 `order`／`logs`；聊天室 STOMP 收訊；地址簿、工單新增後 `setData(await list())` |
| 寫入後重抓 | `reload()` | 預訂取消後、付款後 |
| 載入完成要帶入表單 | 編輯表單：外層元件載入、內層元件用惰性初值一次帶入（載入失敗仍顯示表單與錯誤） | `RoomForm`、`ProductForm`、`SupplierForm`、`PurchaseOrderForm`、`TenantEditForm` |
| 頁面層的表單 | 欄位值＝`使用者輸入 ?? 載入值`（`null`＝沒改過），送出成功後清掉；或把草稿綁在「它是針對哪一份載入物件寫的」，新物件一到草稿自動失效 | 個人資料頁；cms 貼文編輯頁（儲存後重抓失敗時仍保留使用者輸入） |
| 登入狀態、選用店鋪、使用者 id | `useSyncExternalStore`（`authStore` 的快照），不在 effect 裡讀 `localStorage` 再 `setState` | 部落格三頁、聊天室、`members` |
| 載入結果衍生出的狀態 | 在 render 裡衍生，不在 effect 裡補寫 | 結帳頁的預設收件地址（沒操作過就用預設地址；沒有任何地址就改手動輸入） |
| 每個子項自己載入 | 子元件各自 `useAsyncData` | 文章內的嵌入商品卡（`EmbeddedListingCard`）、cms 編輯頁的預覽（`EmbeddedCardsPreview`） |

#### 4.3.2 保留 `useEffect` 的 4 個檔案（`ALLOWED`，每筆有理由）

| 檔案 | 為什麼不是「載入資料」 |
|---|---|
| `app/oauth/callback/[provider]/page.tsx` | 一次性登入流程：消耗只能用一次的 OAuth state、以授權碼換 token、導頁；被中止或重跑會讓登入失敗 |
| `app/(auth)/verify-email/page.tsx` | 一次性動作：以信裡的 token 送出信箱驗證，用 ref 擋掉 StrictMode 的重複送出 |
| `components/ui/authenticated-image.tsx` | 管理 blob object URL 的生命週期（建立與 revoke），是資源管理 |
| `components/layout/StorefrontHeader.tsx` | 訂閱「購物車變更」事件、事件一來就重抓數量；重抓期間要保留舊數字（不閃回 0），失敗不影響頁首 |

#### 4.3.3 遷移途中學到的

1. **遷移會讓元件變得「可編譯」，React Compiler 的規則才開始檢查它**：`TenantList`、`TenantDetail` 搬進 hook 之後，原本藏在 effect 裡的同步 `setState` 才冒出 `react-hooks/set-state-in-effect` 錯誤，改成 `useSyncExternalStore` 快照或衍生值（沒有用 `eslint-disable`）。
2. **`exhaustive-deps` 會對「只給 `enabled` 用的值」報 unnecessary dependency**：值只用來決定要不要發請求時，不能放進 deps；要嘛放進 fetcher、要嘛不列。
3. **第一版母體掃描低估了一半**：動工時的規則只認 `const x = async` 形式，漏了 `async function x()` 宣告、`.then` 鏈、以及參數型別帶大括號的 `useCallback(async (signal?: { cancelled: boolean }) => …)`。數字對照：Sprint 前，第一版規則看到 45 個、偵測器看到 90 個（多出 45 個）；檢查點一時，第一版規則還看得到 13 個、偵測器命中 58 個——多出的 45 個裡 4 個是上述例外，其餘 **41 個**是補掃才發現的。守門測試的偵測器把這三個盲點都寫成單元案例（`參數型別裡有大括號…`），並用突變驗證過（G11）。
4. **機械式批次改寫要靠 `tsc` 兜底**：用「從這行到那行」的區間取代時，曾一口氣吞掉區間中間的其他 state（個人資料頁的 `saving`／`saveError`／`saveSuccess`），是 `tsc` 的 `Cannot find name` 抓到的。

### 4.4 登入守門移到外殼（ADR-002 決定 4）

- `SellerShell`（`dashboard`／`admin`／`cms` 共用）的內容區包 `AuthGuard`（買家 `(auth)` layout 用同一個元件，`DEF-413`）：沒登入就不渲染頁面內容、**不發任何請求**，導去 `/login?redirect=<原頁>`。`admin`（只有首頁有檢查）與 `cms`（完全沒有）原本都沒守門。
- 頁面與元件裡各自的 `isAuthenticated()`＋`router.push(loginHref())`（Sprint 前：dashboard **37** 個頁面、`admin` 首頁 1 個、8 個元件〔`RoomList`、`RoomForm`、`ProductList`、`ProductForm`、`PricingCalendarPreview`、`PricingRuleList`、`PurchaseOrderForm`、`SupplierForm`〕）：檢查點一之後 dashboard 還剩 **27** 個頁面、5 個元件（只有當時動到的檔案順手移了）；檢查點二全部移除，現在 dashboard／`admin`／`cms` 與 `components/` 都是 0 個。檢查點一的提交說明寫「由 37 個頁面上移」，**當時實際只移了 10 個**；檢查點二的提交說明寫「21 個頁面」，實測是 27 個（21 個用批次改寫，另 6 個在遷移載入邏輯時一併移除）——兩處都以這裡的數字為準。
- 守門：`e2e/at-seller-auth-wall.spec.ts`（真實瀏覽器：9 條受保護路由沒登入都被導去登入頁且沒對後端發任何請求；已登入的人整頁重新載入不被誤踢）＋`architecture/page-structure.test.ts`（賣家區域的原始碼不得出現 `isAuthenticated(`，`SellerShell` 必須包 `AuthGuard`）。
- 連帶：`E2E-REVENUE-01` 原本假設「賣家頁面沒有登入守門」，在第一次完整守門時紅燈（319／320），改用假登入（`asMockLoggedInBuyer`）；這是決策 4 的預期後果，不是退步。

### 4.5 WP4：頁面標題（`DEF-459`）

- 依 §3.2 的實測逐頁處理：標題數量有變動的檔案 **24 個**（認證頁 5 個、付款回跳頁 4 個、ERP 五個列表頁、`/dashboard`、`/admin`、`/oauth/callback`、`RoomForm`、`HomeContent`、`ListingTypeBrowser`、`/returns/new`、`/blog/[slug]`、`/blog/category/[id]`、`/dashboard/support/tickets/[id]`）。做法：原本用 `<h2>` 當頁面主標題的 5 個認證頁、用 `CardTitle`（渲染成 `<h3>`）的 4 個付款回跳頁，把**同一行文字**換成 `<PageTitle>` 並帶上原本的字級 class（畫面看起來一樣，語意從 h2／h3 變成 h1）；本來就沒有標題的頁面補 `<PageTitle>`，視覺上不想多一行字的用 `<PageTitle className="sr-only">`（首頁、商品／房源瀏覽）；載入中／錯誤／空狀態的畫面也要有標題（`/returns/new`、`/dashboard/support/tickets/[id]`、部落格分類頁與文章頁）。
- 守門：`architecture/page-structure.test.ts`——每個 `page.tsx` 自己或它（傳遞式）引用的元件要有 `<PageTitle>` 或 `<h1>`（修正前 24 個沒有，現在 0 個）。**這是靜態口徑**；「每一種載入狀態下都有標題」與「同一頁只有一個」靜態看不出來，由 §6 的探針後測與完整 E2E 把關。

### 4.6 `DEF-457` 的結論：量測後不改

四個問題，各有數據：

1. **93 個 client 頁是為了什麼？**（頁面檔自己的內容，`scan_client_pages.py`）：Sprint 前 90 個有 state／載入類 hook、3 個只有路由 hook（`useParams`／`useRouter`）、**0 個什麼 client 功能都沒有**；現在 71／12／10。登入後頁面不可能在伺服器取資料：憑證在 `localStorage`（`lib/axios.ts` 的請求攔截器），伺服器端沒有；根 layout 為了 CSP nonce 讀 `x-nonce`，建置輸出的 106 條路由全是 `ƒ`（動態），沒有任何靜態 `○`。**所以 93 這個數字的主因是認證模型，不是「沒評估」。**
2. **有多少薄殼？**（hook 只有 `useEffect`／`useRouter`／`useParams`、沒有事件處理與瀏覽器 API）：**22 個**，預測是 ≥ 30，落空。
3. **改成 Server Component 省多少？**實驗：把現在已完全沒有 hook 的 10 個 dashboard 頁面（`erp`、`erp/purchase-orders/new`、`erp/suppliers/new`、`pricing/rules`、`products`、`products/new`、`rooms`、`rooms/new`、`shipping`、`tenants`）移掉 `'use client'`，在 scratch 副本重建後用同一支腳本量：受影響的 `/dashboard/products` −144 B（−0.07%）、`/dashboard/products/new` −44 B（−0.02%）。**遠低於預測的 2%**——重的程式碼在被 import 的 client 元件裡，頁面檔本身只有幾十行。
4. **這一輪實際改變了什麼？**登入守門移到外殼（§4.4），所以這些薄殼頁不再有「`useEffect` 做登入導向」這個非轉不可的理由；其中 10 個現在連 hook 都沒有，要轉隨時可以（刪一行）。但**位元組收益在量測誤差內，行為也不會變**，所以不為了比例而轉；真正有結構價值的是『每頁自己的 `<title>`』（`DEF-465`，Server Component 才能 `export const metadata`），到那時再一起轉。

追蹤表的原建議（「不建議為了比例而改」）因此成立，現在有數據支持。

## 5. 突變驗證結果

做法見 §2.4.1～§2.4.3：把被測的原始碼（或守門的偵測器）弄壞，指定的案例要**恰好**紅、不多不少；每個突變的 `must` 在跑之前就登記在計畫書裡。

| 輪次 | 範圍 | 結果 |
|---|---|---|
| WP1 第 1 輪 | M01～M32（`lib/*`、`components/ui/*`） | 基線 80 案例；**30／32 吻合**。M08、M16 不吻合（下方說明）；改後重跑 5／5 吻合 |
| WP2／WP3 第 1 輪 | H01～H24（hook、`api-error`、三個 service） | 基線 131 案例；**22／23 吻合**（H11 不存在，編號保留）。H19 不吻合（下方說明）；修正後重跑吻合 |
| WP3 補登記 | H25～H27（`onError`、`errorInfo`）＋H19 重跑 | 基線 134 案例；4／4 吻合 |
| 守門與後補的服務測試 | G01～G14、C01、C02、A01（§2.4.3） | 基線 165 案例；第 1 輪 17 個突變 **16／17 吻合**，G09 不吻合（見下）；修正守門、補登記 G15 後基線 166 案例，G09、G10、G15 重跑 **3／3 吻合**。最終 18 個守門突變各自恰好擊中登記的案例，沒有連帶紅燈 |

**不吻合的案例，逐個說明（沒有一個是「改預測湊結果」）**：

- **M08（`lib/date.ts`）**：我登記的 `must` 不完整——被測行為弄壞後紅的是 3 個案例，我只登記了 1 個。另外 2 個（台灣凌晨 00:30、深夜 23:59）本來就是這個行為的邊界案例，紅是對的。修正的是**登記**，不是測試。
- **M16（`components/ui/toast.tsx`）**：多紅 4 個 `confirmDialog` 案例。原因是突變驗證當下同一台機器正在跑 `next build`，Radix 對話框的案例逾時，再經由模組層級的單例連鎖紅燈——與被測行為無關。處置：`testTimeout` 放寬到 20 秒、Testing Library 的 `asyncUtilTimeout` 放寬到 5 秒、`confirm-dialog` 測試在 `afterEach` 清空殘留請求；之後重跑吻合。**教訓**：突變驗證期間不要同時跑建置。
- **H19（`services/tenant.ts`）**：服務測試用了 `clearAllMocks`，失敗案例沒用完的 `mockResolvedValueOnce` 佇列漏到下一個案例，多紅 1 個。改成 `resetAllMocks` 後吻合。
- **G09（`ProductForm` 的標題）**：守門**失明**——把 `ProductForm` 唯一的 `<PageTitle>` 換成 `<p>`，標題守門沒有紅。原因：`ProductForm` 還留著 `PageTitle` 的 import，傳遞式追蹤 import 時追進 `components/ui/page-title.tsx`（定義檔，裡面本來就有 `<h1>`），於是**任何檔案只要 import 了 PageTitle 就算有標題**。這是守門的真實缺陷，不是登記錯誤。修正：追 import 時跳過定義檔，並加單元案例（G15 證明這個案例會紅）；修正後真實專案的掃描仍然全過，表示原本沒有因為這個洞而漏掉任何頁面。G09、G10 重跑吻合

G01～G08、G11～G14、C01、C02、A01 第 1 輪就吻合（G11 的預期集合是跑之前補推演的，見 §2.4.3 該列）。這一節最重要的收穫是 G09：**守門測試自己也要被突變驗證**——如果沒有跑它，標題守門會一直是綠的，卻對「標題被拿掉但 import 還在」的情形失明。

## 6. 後置驗證

### 6.1 差異探針（後版 vs 前版）

做法同 §3.2：同一份資料庫快照、同一個後端 JAR、同一支走訪腳本；前版是 `e7cb202f` 的建置，後版是檢查點二＋守門補洞（`fa244f63`）的建置。**注意日期**：前版兩輪在 2026-11-06 晚上跑，後版在 2026-11-07 凌晨 00:48 之後跑——中間跨過午夜，有三處差異純粹是日期造成（下表）。

**結果**：111 次造訪全部完成、沒有走訪錯誤；雜訊基準（前版 A vs B）差異 0；**後版 vs 前版 B 有 38 次造訪不同**，沒有一個是未解釋的：

| 類別（`classify_diff.py` 的分類） | 次數 | 說明 |
|---|---|---|
| 只有 `<h1>` 不同 | 21 | `DEF-459` 的預期變更：前版 0 個 → 後版 1 個；API 請求、標題與其餘文字完全不變 |
| `<h1>` 加上文字多了那一行標題 | 12 | 其中 10 次只多了標題文字那一行。例外兩個：①匿名開 `/blog/[slug]`（沒有選用店鋪）：「載入中...」→ 登入提示（§7.1 #2，預期變更）；②店主 `/dashboard`：營收卡片的「今日／昨日」互換（今日 `$900`→`$0`、昨日 `$0`→`$900`，訂單數 1→0）——播種的訂單建立在 11-06，後版跑在 11-07，是日期造成 |
| 賣家外殼守門 | 2 | 匿名開 `/admin/tenants`、`/cms/media`：前版先渲染整頁、發出必然 401 的請求（`/admin/tenants` 5 個請求、5 個 401、6 筆 console 錯誤；`/cms/media` 1 個請求、1 個 401、2 筆 console 錯誤）；後版直接導去 `/login?redirect=…`，**請求 0 個、HTTP 錯誤 0 個、console 錯誤 0 個**（`at-seller-auth-wall.spec.ts` 守住） |
| 只有 API 請求不同 | 2 | ①店主 `/dashboard/revenue`：預設區間 `startDate=2026-10-07&endDate=2026-11-06` → `2026-10-08&endDate=2026-11-07`（日期）；②買家首次造訪 `/account`：後版多出落地頁（首頁）的 `cart/count` 與 `public/listings`，**兩次重跑都出現，不是偶發**。查因：`loginOnly` 在導向落地頁 `commit` 時就回傳，落地頁的請求可能落進下一次造訪的觀察窗，後版的落地頁恰好較早發出。加上「登入後先等落地頁跑完」（`PROBE_SETTLE=1`）對**前版與後版各重跑一次**，`/account` 兩邊的請求完全相同（`cart/count`、`tenants/invites/my`）——是探針的競態，不是應用程式的行為差異 |
| 只有文字不同 | 1 | 匿名 `/listings/[id]`：月曆少了一行 `$1,800`——房源月曆對過去的日期不顯示價格，前版跑的當天 11-06 還是「今天」，後版跑的 11-07 已是「昨天」 |

其他全面性檢查：**新增的 HTTP ≥ 400 與 console 錯誤：0**（前版 7 筆 HTTP 錯誤、9 筆 console 錯誤；後版 1 筆、1 筆——少掉的全是上面匿名開賣家頁的 401；剩下的那筆是店主開 `/dashboard/support/tickets/[id]` 的 404：播種的工單屬於買家，前後版都有）；`document.title` 仍是 3 種（`DEF-465`，本輪沒動）；`<h1>`：**後版 111 次造訪全部恰好 1 個**（前版 0 個 34 次、1 個 77 次），104 個不重複目的頁面全部恰好 1 個。

JS 傳輸量（同一支腳本、冷快取）：15 條路由後版比前版多 **0.0～0.9 KB（0.0～+0.45%）**——hook 與新增的程式碼進了共用 chunk；沒有路由明顯變小：

| 身分 | 路由 | 前版（KB） | 後版（KB） | 差（KB） | 差（%） |
|---|---|---|---|---|---|
| anon | `/` | 191.5 | 192.0 | +0.5 | +0.29% |
| anon | `/products` | 191.2 | 191.7 | +0.5 | +0.28% |
| anon | `/listings/<uuid>` | 193.8 | 194.5 | +0.7 | +0.34% |
| anon | `/login` | 190.3 | 190.4 | +0.1 | +0.07% |
| anon | `/privacy` | 187.0 | 187.0 | -0.0 | -0.00% |
| buyer | `/orders` | 190.4 | 190.8 | +0.4 | +0.22% |
| buyer | `/cart` | 192.4 | 192.9 | +0.5 | +0.28% |
| owner | `/dashboard/products` | 209.0 | 209.5 | +0.5 | +0.23% |
| owner | `/dashboard/orders` | 208.5 | 209.0 | +0.4 | +0.20% |
| owner | `/dashboard/erp/suppliers/<uuid>` | 190.1 | 190.6 | +0.5 | +0.27% |
| owner | `/dashboard/products/new` | 209.2 | 209.8 | +0.6 | +0.27% |
| owner | `/dashboard/returns` | 189.4 | 189.8 | +0.5 | +0.24% |
| admin | `/admin` | 188.8 | 189.7 | +0.9 | +0.45% |
| admin | `/admin/tenants` | 192.2 | 193.0 | +0.8 | +0.44% |
| admin | `/admin/audit-logs` | 190.2 | 191.0 | +0.8 | +0.44% |

### 6.2 完整本地 E2E 守門

`make validate-e2e`（**不跳過建置**：重建後端 JAR 與前端；乾淨資料庫由 Flyway 重建；`ddl-auto=validate`）：**320 項全過（Playwright 7.9 分）**，entity 與 Flyway schema 對齊、無漂移。

- 320 ＝ 基線 310 ＋ `at-seller-auth-wall.spec.ts` 新增的 10 項（9 條受保護路由沒登入都被導去登入頁且不發請求，另 1 項已登入不被誤踢）。
- `E2E-REVENUE-01` 在檢查點一的第一次守門紅燈過（319／320）——它假設『賣家頁面沒有登入守門』；改用假登入（`asMockLoggedInBuyer`）之後這次通過。這是決策 4 的預期後果，不是退步。
- 後端記錄到認證端點被限流 **5** 次（基線 0 次）。E2E 輔助函式遇到 HTTP 429 會依 `Retry-After` 重試，全綠代表都已自癒；新增的規格只多 1 次表單登入，這 5 次的原因我沒有逐一追，歸入已登記的登入密度風險（`DEF-420`）。

### 6.3 pre-push 與雲端 CI

`git push` 前由 pre-push hook 批次跑一次完整 act CI（約 10～15 分鐘）；雲端 CI 的 run、三個 job 的結果與整合測試數（應仍為 1033／0，後端本輪不動）由收尾的回填 commit 補記在這裡。

## 7. 行為變更與已知限制

### 7.1 行為變更（逐項；其餘載入行為由 §6 的差異探針與完整 E2E 證明沒變）

| # | 變更 | 原因／影響 |
|---|---|---|
| 1 | **賣家外殼未登入**：導去 `/login?redirect=<原頁>`，不渲染頁面內容、不發請求。`/admin/*`、`/cms/*` 原本沒有守門（先渲染、先發必然 401 的請求，再由攔截器導走） | ADR-002 決定 4；`at-seller-auth-wall.spec.ts` 守住。賣家頁面第一個 HTML 不再含「載入中…」字樣，只有外殼 |
| 2 | **`/blog/[slug]` 沒有選用店鋪的訪客**：顯示「請先登入並設定店鋪以瀏覽部落格」（與 `/blog` 清單頁一致）；原本永遠停在「載入中...」 | 遷移時 `enabled` 讓這個狀態必須有明確的畫面；順帶補上它的 `<h1>`（`DEF-459`） |
| 3 | **文章內嵌的商品卡**：每張卡自己載入（平行）；同一篇文章重複嵌入同一個商品時，每個位置各查一次（原本整篇只查一次、依序載入） | 子元件各自 `useAsyncData` 是最不需要額外狀態的寫法；重複嵌入同一商品很罕見。`at-embed-card-status.spec.ts`（真實後端）守住三種狀態的顯示 |
| 4 | **cms 貼文編輯頁的嵌入預覽**：跟著編輯中的內文，以「內文裡的 listingId 集合」為鍵重新載入；移除嵌入後預覽跟著消失（原本只增不減）。也不再有 500ms 的手寫去抖 | 集合沒變就不重抓，所以輸入其他文字不會多發請求 |
| 5 | **重試時的畫面**：`StoreListingGrid` 點「重新載入」後，重試期間顯示載入骨架（原本繼續顯示錯誤畫面直到結果回來；同類的 `StoreLayout`、`ListingDetail` 原本就會先切回載入中） | hook 的 `loading` 在重試時為真；更貼近使用者預期 |
| 6 | **`ReviewList` 移除 `reloadKey` 屬性** | 全專案只有一個呼叫端，沒有傳入它 |
| 7 | **`PricingCalendarPreview`**：預設入住／退房日期在第一次 render 就有（原本先空白、effect 補上）；不再自己檢查登入 | 登入守門在外殼；惰性初值取代 effect 內同步 `setState` |
| 8 | **部落格三頁的「選用店鋪」**改由外部 store 快照讀取（`getSelectedTenantIdSnapshot`，讀 localStorage 的 `tenantId`），在 `notifyAuthChange()`／`storage` 事件之後會重新讀取；原本只在 mount 時讀一次 | 與 axios 的 `X-Tenant-ID` 同一把鍵；`authStore.test.ts` 守住 |
| 9 | **dashboard 貼文編輯頁**：儲存成功後若重新載入失敗，表單顯示的是伺服器上儲存前的值（原本保留使用者輸入） | 邊角中的邊角，且該頁另有已知缺陷（見 §9 `DEF-467`：儲存成功後按鈕永遠停在「儲存中...」），使用者無法再次送出 |
| 10 | **結帳頁**的預設收件地址改為 render 時衍生（沒操作過就用預設地址）；行為與原本相同，差別是不再有「載入完成後多一次 render 才選到地址」 | 不是使用者看得到的變更，列出是因為這是 effect 補寫 state 改衍生值的典型 |

### 7.2 已知限制（誠實列出）

1. **差異探針只涵蓋「載入階段」**：每條路由 `goto` 後的 API 請求多重集合、數字正規化後的可見文字、標題、`<h1>`、console 與 HTTP 錯誤。互動（送出表單、點按鈕、換頁後的重抓、重試）沒有逐條比對，靠完整 E2E（310 項）與 `tsc`／單元測試覆蓋。
2. **守門的偵測器是文字啟發式**：只做括號計數，不解析字串、註解與正規表達式；`HEADING` 用文字比對，註解裡的 `<h1>` 會被誤算成有標題。括號不成對時丟錯而不是靜默漏掉。
3. **舊 service 方法多數沒有轉傳 `signal`**：競態已正確（hook 丟棄過期回應），但被取消的請求仍會跑完。目前只有 cart／tenant／admin／cms 公開端點轉傳（見 §9 `DEF-469`）。
4. **`useAsyncData` 沒有快取與去重**（ADR-002 的有意取捨）：同一個畫面上兩個元件載入同一份資料會發兩次請求。
5. **真實外部服務**（Stripe、OAuth 提供者、郵件）本輪沒有驗證——前端遷移沒有改它們的呼叫形狀，但 E2E 對這些也是用模擬或測試金鑰。
6. **85 處錯誤斷言的替換沒有逐處審計是否與原行為等價**（P-455b）：多數是機械替換成 `apiErrorMessage(err, 後備)`（`message || 後備`），少數需要 `status`／`code`／`fieldErrors` 的改用 `getApiErrorInfo`；證據是 `tsc`／`eslint` 零錯誤、完整 E2E 與差異探針，而不是逐處比對。

## 8. 預測對答案

| # | 預測 | 實測 | 吻合？ |
|---|---|---|---|
| P-459a | 穩定後 `<h1>`＝0 的路由 15～29 個 | **29 個**（§3.2；靜態口徑 24 個，多出的 5 個是載入中／錯誤／匿名畫面沒有標題） | ✅ 落在區間上緣。預測集中的位置（認證頁、付款結果頁 4 個、ERP 列表頁、`/dashboard`、`/admin`、`oauth/callback`）全部命中 |
| P-459b | `<h1>`≥2 的路由 ≤ 3 個（候選：店鋪子頁） | **0 個**（`StoreLayout` 的 `<h1>{profile.name}</h1>` 與子頁沒有重複標題） | ✅ |
| P-457a | 93 個 client 頁裡至少 30 個是薄殼（hook 只有 `useEffect`／`useRouter`／`useParams`） | **22 個**（Sprint 前、檢查點一、現在都是 22；現在其中 10 個已完全沒有 hook、12 個只剩路由 hook） | ❌ **落空**：薄殼比預想的少，「layout 層統一守門」的價值主要不在這裡 |
| P-457b | 把薄殼頁改成 Server Component，該路由傳輸 JS 位元組變化 < 2% | 實驗（scratch 副本，不進版本控制）：把 10 個已完全沒有 hook 的 dashboard 頁面改成 Server Component、重新建置，用同一支腳本量 15 條路由。受影響的 `/dashboard/products` **−144 B（−0.07%）**、`/dashboard/products/new` **−44 B（−0.02%）**；未受影響的 13 條路由位元組完全相同（1 條差 1 byte） | ✅ 遠低於 2%（<0.1%）：重的程式碼在被 import 的 client 元件裡，頁面檔本身只有幾十行 |
| P-457c | 登入後頁面不可能改成在伺服器取資料（憑證在 `localStorage`） | `lib/axios.ts` 的請求攔截器 `localStorage.getItem('accessToken')`；根 layout 讀 `x-nonce` 使全部路由動態渲染（建置輸出 106 條路由全是 `ƒ`（沒有靜態 `○`）） | ✅ |
| P-T | 104 個路由的 `document.title` 只有 3 種 | 111 次造訪恰 **3 種**（預設 109 次＋`/privacy`＋`/terms`） | ✅ |
| P-456a | 修完後每條路由載入時的 API 請求多重集合與前版完全相同（例外必須在 §6 解釋） | 後版 vs 前版 B：111 次造訪中，載入階段 API 請求多重集合不同的只有 **4 次**（含 late 請求），全部已解釋（§6.1）：匿名開賣家頁 ×2（守門之後請求歸 0）、日期 ×1、探針競態 ×1（加「先等落地頁」之後前後版一致）。其餘 107 次與前版完全相同 | ✅ 例外都在 §6.1 逐一解釋，沒有未解釋的差異 |
| P-456b | 母體裡至少 5 個檔案的載入邏輯不適合直接套 hook | 保留 `useEffect` 的只有 **4 個**（§4.3.2）；但另有一批需要非機械式的寫法才能套進去（§4.3.1：外層載入＋內層一次帶入初值、依載入物件失效的草稿、`setData` 就地更新、子元件各自載入） | ➖ 嚴格說 4 < 5（略低於預測）；「hook 夠通用」成立，但沒有「不適合」到全部留舊寫法 |
| P-458a | 新增的 Vitest 套件（約 100 案例）整體 < 30 秒 | 166 案例（含架構掃描）整體 **36～46 秒**（pre-commit 實測 46.05 秒）；其中案例本身只花 5～9 秒，其餘是每個測試檔啟動 jsdom（累計 57～78 秒，平行） | ❌ **落空**：案例更多、時間也更久；瓶頸是 jsdom 啟動而不是案例。兩份架構測試已改用 `node` 環境 |
| P-455a | 16 個檔案搬完後 `services/` 新增約 35～45 個方法，沒有任何一個需要改後端 | **25 個**方法（cart 7、tenant 6、admin 12）；沒有改後端 | ❌ 方法數低於預測（頁面之間共用的方法比我估的多）；「不用改後端」✅ |
| P-455b | 86 處手寫斷言全部能機械地等價替換，`tsc`／`eslint` 零錯誤 | `tsc`、`eslint` 零錯誤 ✅；85 處收斂（`as { response` 現在只剩 `lib/api-error.ts` 自己 2 處）。多數處替換為 `apiErrorMessage(err, 後備)`，少數需要 `status`／`code`／`fieldErrors` 的改用 `getApiErrorInfo`。**沒有逐處做「是否等價」的審計**——證據只有 `tsc`／`eslint`／完整 E2E | ➖ 結果吻合，等價性的證據偏弱（見 §7.2） |

## 9. 新登記的相鄰缺陷（只登記，不在本輪修）

沿用 S316～S330 的慣例：遷移途中發現的相鄰缺陷只登記到追蹤表，不順手修。

| ID | 發現 | 證據 | 嚴重度與建議 |
|---|---|---|---|
| `DEF-465` | **整站 111 次造訪（104 個頁面）中 109 次共用同一個 `document.title`** | 前版探針逐條記錄 `document.title`：只有 `/privacy`、`/terms` 有自己的。`export const metadata` 只出現在根 `layout.tsx` 與這兩頁；93 個 client 頁不能 export。**11 個頁面本來就是 Server Component**（`/`、`/products`、`/rooms`、`/listings/[id]`、`/stores/[slug]` 與 `products`／`rooms`／`blog` 子頁、`/tenant/apply`、`/privacy`、`/terms`），其中 9 個沒有 export | 🟡 影響瀏覽器分頁／歷史／書籤的辨識、螢幕閱讀器換頁時念出的標題、公開頁的搜尋結果標題。**建議**：①先做公開的 Server Component 頁面（成本最低、SEO 價值最高）；②22 個薄殼頁（其中 10 個已完全沒有 hook）改成 Server Component 後 export；③其餘 client 頁面需要 server 薄殼或在外殼依路徑設定，範圍請使用者決定 |
| `DEF-466` | **兩套並行的貼文／媒體管理介面** | `/dashboard/posts`（列表、新增、`[id]/edit`、`categories`）＋`/dashboard/media`，與 `/cms`（首頁列表、`posts/new`、`posts/[id]/edit`、`media`）；`sellerNav.ts` 兩個區域的側欄都各自有「貼文管理」「媒體」入口。行為也不一樣：`/cms/posts/[id]/edit` 有嵌入商品卡預覽、儲存後保留輸入，`/dashboard/posts/[id]/edit` 沒有預覽（且見 `DEF-467`） | 🟡 結構性重複，維護成本加倍，使用者看到兩套。**需要產品決定**保留哪一套（或合併），所以不在本輪動 |
| `DEF-467` | **`/dashboard/posts/[id]/edit`：儲存成功後按鈕永遠停在「儲存中...」且不可再按** | **讀碼，尚未實測**：`handleSubmit` 只在 `catch` 裡 `setSaving(false)`，成功路徑不重設；元件沒有卸載（只有回傳的 JSX 換成載入畫面），所以 `saving` 一直是 `true`。Sprint 331 遷移時保留了原行為 | 🟢 一行可修（`finally { setSaving(false) }`）。建議先寫一個 Playwright 案例重現（mock API 即可）再修 |
| `DEF-468` | **載入失敗被畫成「沒有資料」** | 部落格清單頁與分類頁的文章清單（失敗只 `console.error`，畫面顯示「尚無已發布的文章」）、`StoreFeaturedListings`（失敗當作沒有精選）。Sprint 331 遷移刻意保留原行為 | 🟢 使用者分不出「真的沒有」與「載入失敗」。**建議**：用 `useAsyncData` 的 `error` 顯示錯誤畫面與重試 |
| `DEF-469` | **`useAsyncData` 的 97 個呼叫點裡只有 18 個把 `signal` 轉給 service** | 精確掃描（排除測試與 hook 本身）：97 個呼叫點、84 個檔案、18 個轉傳。其餘的 service 方法沒有 `options` 參數，過期回應會被 hook 丟棄（競態正確），但被取消的請求仍會跑完 | 🟢 效率問題，不是正確性問題。**建議**：動到那個 service 時順手加 `options?: RequestOptions`；不值得專門排一輪 |
| `DEF-470` | **`at-auth-redirect.spec.ts` 是全專案唯一一支「不開瀏覽器、只驅動純函式」的 Playwright 規格** | `grep "from '../src"`：只有它與 `at-ui-design-system.spec.ts`（後者有開瀏覽器）；`lib/auth-redirect.test.ts` 已有 Vitest 版本，兩者是否重複**沒有逐案比對** | 🟢 可搬進 Vitest（更快、不依賴 Playwright 啟動）。比對後再決定刪或留 |

## 10. 我替使用者做的決定（可一句話推翻）

1. **「徹底」的定義**：根因修復＋防止復發的守門＋實測證據，而不是「把五項都做到最大」。`DEF-457` 依實測決定**不改**（§4.6），這與追蹤表原本的建議一致，但現在有數據。
2. **登入守門放外殼**（ADR-002 決定 4）：連帶讓 `admin`／`cms` 第一次有守門。這是行為變更（§7.1 #1），我認為是修正；若要退回，只需拿掉 `SellerShell` 裡的 `<AuthGuard>`，各頁已不再自己檢查。
3. **自製 hook，不引入 TanStack Query／SWR**（ADR-002 選項 B）：需求只有「載入／失敗／重試／競態」。
4. **`/blog/[slug]` 沒有店鋪的訪客顯示登入提示**，而不是保留「永遠載入中」（§7.1 #2）。
5. **相鄰缺陷只登記不修**（§9），包括「儲存後按鈕卡住」這種一行就能修的——為了讓這輪的變更面積可以被完整驗證。
6. **不把純函式型的 Playwright 規格搬進 Vitest**（§4.1）：它們仍然有效，搬了也不會讓這輪的守門更強。

## 11. 狀態

**完成（2026-11-07）。** 五項處置：`DEF-455` ✅、`DEF-456` ✅、`DEF-457` ✅（量測後結案，不改）、`DEF-458` ✅、`DEF-459` ✅；新登記 `DEF-465`～`DEF-470`（§9）。

**本機驗證**：`tsc --noEmit` 0 錯誤；`eslint --max-warnings 0` 全專案 0 警告；Vitest **166 案例／17 檔**全過；`next build` 成功；完整本地 E2E 守門 **320／320**（§6.2）；差異探針 111 次造訪的 `<h1>` 全部恰好 1 個、38 次差異全部有解釋（§6.1）；76 個突變驗證最終全部各自恰好擊中登記的案例（§5，第一次不吻合的 4 個如實記錄）。

**提交（本機，依序）**：`e7cb202f`（Sprint 330 文件回填）→ `4ce91ca2`（檢查點一）→ `38c5e47a`（檢查點二）→ `fa244f63`（標題守門補洞）→ 收尾文件 commit。push 與雲端 CI 結果見 §6.3（由回填 commit 補記）。

**需要使用者決定的產品問題**（不影響本輪完成）：①`DEF-466`——`/cms` 與 `/dashboard` 兩套並行的貼文／媒體管理介面，保留哪一套；②`DEF-465`——每頁自己的 `<title>` 要做到哪裡（只做公開的 Server Component 頁，或連 22 個薄殼頁一起轉）。**不需要決定、下次可直接做**：`DEF-467`（一行修，先寫重現案例）、`DEF-468`（失敗顯示錯誤畫面）。

**關於本輪的誠實說明**：登記的預測落空 3 個（薄殼頁數 22 而非 ≥ 30、Vitest 套件 36～46 秒而非 < 30 秒、service 方法 25 個而非 35～45 個），部分吻合 2 個；動工時的母體掃描低估了一半（45 vs 90）——是靠守門測試自己的偵測器才量準的；85 處錯誤斷言的替換沒有逐處審計是否等價；差異探針只涵蓋載入階段。
