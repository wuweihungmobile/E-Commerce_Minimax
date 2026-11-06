# ADR-002：前端資料存取分層——請求只寫在 `services/`，載入狀態只寫在 `useAsyncData`，行為由單元測試守住

> **狀態**：Accepted（2026-11-06，Sprint 331）
> **決策者**：使用者在 Sprint 330 總結上回覆「`DEF-455`～`DEF-459` … ==> 請先徹底解決前端的結構性問題！」；Claude（SD／QA 角色）依實測提出，使用者的指示涵蓋採用
> **相關**：[SPRINT_331_PLAN.md](../../04_planning/SPRINT_331_PLAN.md)（量測、預測與結果）、[ADR-001](ADR-001-ui-design-system-layering.md)（UI 分層，本決策是它在「資料」面向的對應）、[LOCAL_CI_VALIDATION.md](../../08_deployment/LOCAL_CI_VALIDATION.md)（守門）
> **取代**：無

## 背景

[ADR-001](ADR-001-ui-design-system-layering.md) 把「視覺決定」收進 `components/ui`，並用 ESLint 讓它長不回來。Sprint 329 的四角色檢視同時量到**資料面**有同一類問題——「同一個決定被複製到幾十個檔案，沒有東西阻止它繼續複製」：

| 觀察（Sprint 331 動工前，HEAD＝`e7cb202f`，用工具核對） | 數字 |
|---|---|
| 頁面／元件直接 `import apiClient`／`API_ENDPOINTS` 發請求，繞過 `services/` | **16** 個檔案（`DEF-455`）；其餘頁面都走 `services/*` |
| 畫面裡手寫的 axios 錯誤形狀斷言 `err as { response?: { data?: { message?: string } } }` | **85** 處，散在 **59** 個檔案；共用的 `getApiErrorInfo` 已存在卻只有 8 個檔案在用 |
| 「載入中 → 成功／失敗 → 重試」的樣板（各頁自己宣告 loading／error／data 三個 state 與 `useEffect`） | 守門測試自己的偵測器在 **188** 個被掃描的檔案裡命中 **90** 個（`DEF-456`）。動工時用的舊掃描規則只量到 45 個——它漏掉 `async function` 宣告、`.then` 鏈，以及參數型別帶大括號的 `useCallback(async (signal?: { cancelled: boolean }) => …)`，所以母體被低估了一半 |
| 這些樣板裡的競態處理 | 沒有（`orders` 頁等少數頁手寫 `cancelled` 旗標；`audit-logs` 的「查詢」鈕會在非第 1 頁時重抓兩次、「清除」在第 1 頁時不重抓） |
| 登入守門 | 買家頁面在 layout 層（`AuthGuard`，DEF-413）；賣家 `dashboard` 的 **37** 個頁面各自在 `useEffect` 裡檢查（另有 5 個元件也各自檢查），`admin`／`cms` 幾乎沒有 |
| 前端自動化單元／元件測試 | **0**（`npm test` 是 `echo "No unit tests configured"`）；`lib/*` 的純函式靠 Playwright 規格在 Node 裡驅動 |

**根因**與 ADR-001 相同：沒有「資料層」這個單一來源，每個頁面只好自己組請求、自己解析錯誤、自己管載入狀態；而且沒有東西擋它——ESLint 只擋顏色與原生控制項。

## 決策

1. **請求只寫在 `services/`（與 `lib/`）**。頁面、元件、hook 呼叫 service 的方法，不 import `axios`／`@/lib/axios`／`API_ENDPOINTS*`，也不呼叫 `fetch`。由 ESLint `no-restricted-imports` ＋ `no-restricted-globals` 守門（`API_CONFIG` 這種純設定值不在禁止之列：`proxy.ts` 與 `useChatSocket` 用它組 URL）。service 沿用既有慣例：`class XxxService`＋預設匯出的單例、方法回傳已拆掉信封的資料、型別在檔內宣告。讀取方法可選擇接收 `{ signal }`（`RequestOptions`），讓 `useAsyncData` 能真的中止請求。
2. **錯誤解析只有一個來源**：`lib/api-error.ts` 的 `getApiErrorInfo`（取 `status`／`code`／`message`）與 `apiErrorMessage(error, fallback)`（後端訊息優先，否則後備文案）。`services/auth.ts` 轉出 `getApiErrorInfo` 讓既有 import 不受影響。
3. **載入狀態只有一個來源**：`hooks/useAsyncData(fetcher, deps, { errorMessage, enabled })` → `{ data, error, loading, reload, setData }`。
   - `loading`／`error` 是**衍生值**：state 只記「最近一次完成的請求」的鍵與結果，`loading ＝ 目前的鍵與完成的鍵不同`。依賴一變，同一個 render 就是 loading；effect 只啟動請求，結果在非同步回呼裡寫入（符合 React 19 的 `react-hooks/set-state-in-effect`）。
   - **競態與取消**：每次 effect 一個 `AbortController`，依賴改變／重試／卸載時 `abort()`；回應回來先檢查 `signal.aborted` 才寫入，所以即使 service 方法沒把 `signal` 轉給 axios，舊回應也不會蓋掉新的。被我們自己中止的請求不算錯誤。
   - 呼叫端的 deps 由 `react-hooks/exhaustive-deps` 的 `additionalHooks: useAsyncData` 檢查。
   - **守門**：`src/architecture/data-loading.test.ts` 掃 `app/`、`components/`、`hooks/` 的原始碼，只要有「effect 內發請求再 `setState`」（effect 本體自己發，或 effect 呼叫同檔宣告的 async 函式）就紅燈。例外只有 4 個，寫在檔內的 `ALLOWED` 並附理由：OAuth 回呼與驗證信箱是一次性動作、blob 圖片是資源生命週期、頁首購物車數量是事件訂閱。`ALLOWED` 每一筆必須仍然命中（改好了就要移除），所以清單只會縮短。
   - **hook 放不下的情形**：①要保留舊資料直到新資料到達的就地更新（`setData`：聊天室的 STOMP 收訊、地址簿新增後靜默重抓）；②載入完成後才把值一次帶進表單（外層元件載入、內層元件用惰性初值或「依載入物件失效的草稿」；不在 effect 裡把載入結果 `setState` 進表單）；③載入失敗仍要顯示表單與錯誤（各編輯表單）。三者都已有現成寫法（見計畫書 §4）。
4. **登入守門在外殼，不在頁面**：`SellerShell`（`dashboard`／`admin`／`cms` 共用）與買家的 `(auth)` layout 一樣包 `AuthGuard`。沒登入就不渲染頁面內容、不發請求，導去 `/login?redirect=<原頁>`。（`useAsyncData` 因此不需要「已登入才載入」的條件。）頁面與賣家專用元件不再自己檢查登入；`src/architecture/page-structure.test.ts` 守住 `dashboard`／`admin`／`cms` 底下不得出現 `isAuthenticated(`，並檢查 `SellerShell` 確實包了 `AuthGuard`。
5. **前端有單元／元件測試**：Vitest ＋ Testing Library ＋ jsdom，測試與原始碼並排（`*.test.ts(x)`），固定時區為 Asia/Taipei（本專案反覆踩 `toISOString()` 當「今天」的缺陷）。`npm test` 接進 husky pre-commit、`make check-frontend`、`act-compat.yml` 與 `ci.yml` 的 frontend job。E2E（Playwright）仍負責整合與真實瀏覽器行為。
6. **架構規則寫成測試**（`src/architecture/`，`@vitest-environment node`，讀原始碼文字）：偵測器是純函式（`detect.ts`），本身有單元測試，並用突變驗證過（把偵測改壞，測試要紅；見計畫書 §2.4.3）。規則涵蓋：手寫載入（決策 3）、頁面登入守門（決策 4）、每個 `page.tsx`（含傳遞式引用的元件）有 `<PageTitle>` 或 `<h1>`（`DEF-459` 的靜態口徑）。「頁面標題在每一種載入狀態下都存在」與「同一頁只有一個 `<h1>`」靜態看不出來，由真實瀏覽器的差異探針與完整 E2E 把關，不放進單元測試。

## 考慮的選項

| 選項 | 優點 | 缺點 | 結論 |
|---|---|---|---|
| A. 只靠文件與 code review | 零成本 | `services/` 層從 Sprint 1 就有，仍長出 16 個繞過它的檔案與 85 處手寫斷言——人工紀律擋不住複製貼上 | 否決 |
| B. 引入 TanStack Query／SWR | 快取、去重、背景重新驗證、devtools 現成 | 需求只有「載入／失敗／重試／競態」，用不到快取與去重；登入憑證在 `localStorage`，沒有 SSR 預取的場景；多一個執行期相依（`dependency-audit` 每週掃描）；重新渲染語意比既有頁面的「整頁替換成載入中」複雜，遷移不再是機械式 | 否決（日後真的需要快取／去重再換；`useAsyncData` 的呼叫介面刻意與 `useQuery` 同形） |
| C. 每頁各自改進（只補競態處理） | 不新增抽象 | 約 70 處各改一次，下次還會再各寫一次 | 否決 |
| D. 把登入守門留在 37 個頁面 | 不動既有行為 | 每個頁面一個 `useEffect`＋`useRouter`＋`loginHref`，也讓這些頁面無法在不重複守門的前提下共用載入 hook；`admin`／`cms` 本來就缺 | 否決 |
| **E. services 單一來源＋`useAsyncData`＋外殼守門＋lint＋單元測試（本決策）** | 新增頁面沒有「再寫一次」的路；行為有測試；守門讓它長不回來 | 一次性遷移面積大；hook 要維護；守門的誤報要靠明列的例外處理 | **採用** |

## 後果

- **正面**：①後端契約或錯誤形狀改變，只動 `services/` 與 `lib/api-error.ts`；②載入／重試／競態只有一份實作與一組測試；③沒登入不再發出必然 401 的請求；④前端第一次有「秒級」的回饋迴圈（pre-commit 約 20 秒的單元測試），不必等完整 E2E。
- **負面**：①遷移面積大（見計畫書）；②`useAsyncData` 是自製抽象，要有人維護；③`SellerShell` 內容區改成「hydration 之後才渲染」（與買家頁面一致）——頁面第一個 HTML 不再含「載入中…」字樣，只有外殼；④守門的偵測器是原始碼文字的啟發式（括號計數，不解析字串、註解與正規表達式；註解裡的 `<h1>` 會被誤算成有標題），括號不成對時丟錯而不是靜默漏掉——這是刻意的取捨，換來規則可讀、可測；⑤舊的 service 方法多數沒有轉傳 `signal`：遷移後競態已正確（舊回應被丟棄），但被取消的請求仍會跑完。只有本 Sprint 新增或改動的讀取方法（cart／tenant／admin／cms 公開端點）會真的取消。
- **風險與緩解**：①hook 行為與舊寫法不完全等價 → 單元測試逐項編碼意圖，加上前後差異探針（每條路由載入時的 API 請求多重集合與可見文字）與完整 E2E；②lint 規則誤報 → 例外必須逐行 `eslint-disable … -- 理由`；③`signal` 沒被舊 service 方法轉給 axios 時，舊請求仍會跑完（只是結果被丟棄）→ 讀取方法隨遷移逐步加上 `RequestOptions`，不是一次改完。

## 驗證

見 [SPRINT_331_PLAN.md](../../04_planning/SPRINT_331_PLAN.md)：ESLint 守門的探針驗證、hook 與測試套件的突變驗證（§2.4.1～§2.4.2）、架構守門測試的突變驗證（§2.4.3）、前後差異探針、完整本地 E2E 守門。
