# Sprint 297 Plan — DEF-398：前端 99 筆 ESLint warning 全部處理（99 → 0）

## 1. 起點與範圍

### 1.1 起點（使用者的明確決定，不是推論）

[Sprint 294](SPRINT_294_PLAN.md) 收尾時，我用 `AskUserQuestion` 問使用者 `DEF-398`（前端 `npm run lint` 有 99 筆既有 warning、0 errors，散在 55 個檔）要怎麼處理。使用者**本人**選了「**99 筆全部處理**」，**不是**我建議的「先做行為不變的 58 筆」。照使用者的決定做，包含那 41 筆需要逐案判斷行為的（hooks 依賴 26、`<img>` 13、整頁導向 2）。

### 1.2 成功標準（開工時定義）

1. 追蹤表登記的驗收條件：`npm run lint` 為 **0 warning**、`npm run type-check`、`npm run build` 通過、`make validate-e2e` 全綠。
2. 不得產生新警告（刪掉 interface 後某個型別 import 變未使用、`useCallback` 冒出新的缺依賴，都算）。
3. **行為不變要有論證，不是只靠「lint 變綠」**：每個 hooks 轉換都要能說明 effect 的觸發集合與修改前相同；說不通的就保留為附理由的例外。
4. 不動 `package.json`、`eslint.config`、任何建置設定。

### 1.3 先讀這個版本的官方文件

`frontend/AGENTS.md`（`frontend/CLAUDE.md` 引入）要求：這個 Next.js 版本與一般印象不同，寫碼前先讀 `node_modules/next/dist/docs/`。本專案安裝的是 Next.js 16.4.2，我對 `<img>` 這一類警告據以核對了該版本隨附的文件（`01-app/03-api-reference/02-components/image.md`、`01-app/01-getting-started/12-images.md`）：

- `src` 為絕對外部網址時「must be configured with remotePatterns」（`image.md` 第 71 行）。
- 遠端或動態圖片必須提供 `width`／`height`，除非改用 `fill`（此時父層須有 `position`）（`image.md` 第 108、125 行；`12-images.md` 第 198 行）。
- 「If the `src` image requires authentication, consider using the `unoptimized` property」（`image.md` 第 88 行）。
- `frontend/next.config.ts` 沒有 `images`／`remotePatterns`／`unoptimized` 設定（`grep` 確認）。

這支撐了第 3.4 節「`<img>` 維持原狀加註明理由」的判斷：換成 `next/image` 需要新增 `remotePatterns`（網域由店家任意輸入，無法事先列舉）並決定 `width`／`height` 或 `fill` 的版面策略。

## 2. 盤點（實測）

`cd frontend && npx eslint . -f json`：**99 筆（0 errors）、55 個檔**，與追蹤表 `DEF-398` 登記的數字完全吻合。

| 規則 | 筆數 | 處置 |
|---|---:|---|
| `@typescript-eslint/no-unused-vars` | 36 | 階段 B：刪除未使用 import／死碼／不用的賦值 |
| `react-hooks/exhaustive-deps` | 26 | 階段 C：**23 個**證明等價地改成 `useCallback`；**3 個**保留為附理由的例外 |
| `import/no-anonymous-default-export` | 22 | 階段 A：先宣告具名常數再 default export |
| `@next/next/no-img-element` | 13 | 階段 D：維持 `<img>`，加附理由的 disable |
| `@next/next/no-location-assign-relative-destination` | 2 | 階段 D：維持整頁導向，加附理由的 disable |

## 3. 做法與等價性論證

所有轉換用腳本（在 session 暫存目錄，未進 repo）執行，每項操作都有「old 必須恰好出現一次」之類的守衛；每個階段做完就跑 ESLint 與 `tsc`，以它們為最終裁判。

### 3.1 階段 A：22 個匿名 default export

全部是 `export default new XxxService()`，改為 `const xxxService = new XxxService()` 加 `export default xxxService`。同一個單例、同一個 default export，匯入端不受影響（`tsc` 乾淨為證）。命名機械式小寫首字，唯一例外 `OAuthApiService` → `oauthApiService`（機械式會變成 `oAuthApiService`）。

### 3.2 階段 B：36 個未使用變數（17 檔）

- 未使用的 import 名稱：從 import 清單移除（TypeScript 輸出本來就會略掉未使用的 import，不改變執行行為）。
- 整段死碼：`CheckoutData` interface、`formatDate`、`getStatusBadge`——刪除前確認該名稱在檔案中**只出現一次**（就是定義本身）。
- 賦值了沒用到：`const updatedPost = await updatePost(...)` → 保留 `await updatePost(...)`（右邊有副作用，不能整行刪）；`const [totalCount, setTotalCount] = useState(0)` → `const [, setTotalCount] = ...`；兩處沒用的 `const router = useRouter()` 連同變成未使用的 `useRouter` import 一併移除；E2E 規格裡一個只賦值不讀的 `roomId`。
- 刪完後重跑 ESLint：未使用變數殘留 0、沒有新增任何警告。

### 3.3 階段 C：26 個 hooks 依賴

形狀都一樣：`useEffect(() => { loadXxx() }, [deps])`，`loadXxx` 是元件內未記憶化的函式。

**等價性的做法**：`useCallback` 的依賴 = 函式本體實際引用、且**原本就在 effect 依賴裡**的值；effect 的新依賴 = 原依賴中「effect 本體仍引用」或「沒被函式依賴涵蓋」的項目，再加上函式本身。腳本內建守衛：被省略的依賴必須都已被函式依賴涵蓋，否則中止。結果 effect 重新執行的觸發集合與修改前相同。函式若定義在 effect 之後，要搬到 effect 之前（`const` 有暫時性死區；`tsc` 會抓到）。

**ESLint 是最終裁判**：函式若還讀了「別的反應式值」，`useCallback` 會冒出新的缺依賴警告——那正是不能這樣改的案例。

結果 23 個轉換通過，其中包含我一度歸為「特殊」的 `PurchaseOrderForm`（讀碼後發現 `fetchOrder` 只讀 `orderId`、`mode`，它呼叫的 `fetchSuppliers` 沒有捕獲任何反應式值，`exhaustive-deps` 對這類函式視為穩定可省略，是乾淨案例）。

**3 個保留為附理由例外的案例（不是偷懶，是改了會變壞）**：

| 檔案 | 為什麼不能列為依賴 |
|---|---|
| `components/product/ProductList.tsx`、`components/room/RoomList.tsx` | `fetchProducts`／`fetchRooms` 還讀了 `searchQuery`，它是搜尋框的**受控狀態**（`onChange` 每個按鍵都更新），只在按 Enter／搜尋鈕（`handleSearch`）時才套用。把函式或 `searchQuery` 列為依賴，都會變成每個按鍵（或每次 render，因為函式本身每次 render 都重建）重新抓取資料。機械式轉換在這裡一開始就會判錯，是我讀碼才發現的 |
| `app/cms/posts/[id]/edit/page.tsx` | `loadPost` 呼叫 `parseEmbedCards`，後者讀取 `embedCards` 狀態，而 `loadPost` 又用伺服器內容覆蓋 `formData`。我實際套用過轉換，**實測**ESLint 冒出連鎖的新警告：`React Hook useCallback has a missing dependency: 'parseEmbedCards'`（第 54 行）。若依警告補上，`loadPost` 的身分會隨 `embedCards` 改變——**依讀碼推論**（未實際執行該版本），這會讓每載完一張嵌入卡片 effect 就重跑、重新抓貼文並蓋掉使用者正在編輯的內容。該案例已從備份還原並改為附理由的例外 |

### 3.4 階段 D：13 個 `<img>`、2 個整頁導向

- **`<img>`（13）**：`src` 是店家輸入的任意網址（封面圖、精選圖、嵌入卡片圖），或 `AuthenticatedImage` 以授權標頭取得後建立的 `blob:` 物件網址。依 §1.3 核對的官方文件，外部網址須設 `remotePatterns`、需要 `width`／`height`，而目前版面是由 `className` 決定尺寸——換成 `next/image` 是版面變更，不是語法替換。維持 `<img>`，依專案既有慣例（storefront 元件的寫法）加 `eslint-disable-next-line @next/next/no-img-element -- 理由`；JSX 子節點環境用 `{/* … */}` 形式。
- **整頁導向（2）**：`lib/axios.ts` 的攔截器不在 React 元件內，規則建議的 `useRouter().push()`／`redirect()` 都用不了，只能以整頁導向回登入頁（整頁導向同時會丟棄記憶體中的前端狀態）；`services/oauth.ts` 導向 Google／GitHub 的授權頁（`authorizeUrl` 是絕對網址，已用 `grep` 確認），必須整頁跳轉，規則只是無法判斷模板字串是外部網址。

## 4. 驗證

| 檢查 | 結果 |
|---|---|
| `npm run lint` | **退出碼 0、無任何 problems 輸出**（修復前 `✖ 99 problems (0 errors, 99 warnings)`） |
| `npm run type-check` | 退出碼 0 |
| `npm run build` | 退出碼 0（`✓ Compiled successfully in 12.0s`、`Finished TypeScript`、`Generating static pages 68/68`；路由表全部是動態 `ƒ`） |
| `make validate-e2e`（完整：重建 JAR 與前端、乾淨 DB、Playwright） | **179 個案例：175 通過、4 跳過、0 失敗（5.6 分鐘）**，閘門顯示「本地 E2E 守門通過」；4 個跳過與前面幾個 Sprint 相同（既有 skip） |
| 改動規模 | 55 個前端檔，`+321／−324`；純修改，無新增或刪除檔案；23 個 `useCallback` 包裝；18 個新增 `eslint-disable`（13 `<img>`＋2 整頁導向＋3 hooks） |
| 後端 | 零變更，沒有重跑後端測試 |
| 雲端 CI | push 後回填 |

## 5. 過程中的失誤與修正（都在 commit 之前被驗收步驟抓到）

1. **階段 D 腳本把規則名拼成 `@next/next/next/…`**（我用 `split("/", 1)[1]` 取短名，得到 `next/no-img-element`，拼回去多了一個 `next/`）。結果 ESLint 報 15 個「找不到規則」的 error，原本的 15 個 warning 也還在（`30 problems (15 errors, 15 warnings)`）——這正是驗收那一步要抓的東西。用精確字串替換修正（先確認錯誤字串只出現在我剛插入的 15 行），重跑後 0 筆。
2. **自動轉換在 `cms/posts/[id]/edit` 判錯**：機械式 `useCallback` 在該案例會引入「重抓貼文覆蓋編輯內容」的行為變更，靠 ESLint 的新警告發現，並從備份還原（見 §3.3）。
3. **`PurchaseOrderForm` 一度被我過度謹慎地歸為特殊情形**，讀碼後發現其實是乾淨案例，補進轉換。
4. **誤寫檔案到錯誤路徑**：準備階段 C 腳本時，我用 `Write` 把一個 11 位元組、內容為 `placeholder` 的檔案誤寫到 `/private/tmp/claude-501/-Users-wuweihong/..data`（不在 scratchpad 內，當時我的判斷是腳本路徑打錯）。先 `ls` 與 `cat` 確認該目錄與檔案都是當下（14:40）由我建立、內容只有 `placeholder`，才刪除那個檔案並用 `rmdir`（只刪空目錄）移除目錄；沒有動其他任何東西。
5. 我的 hooks 判讀輔助腳本會把 `data.posts` 這類屬性存取誤算成「函式讀了 `posts` 狀態」；結論一律以實際讀碼與 ESLint 為準，不採信那個欄位。

## 6. 限制（誠實揭露）

1. **前端沒有單元測試**（`npm test` 只印「No unit tests configured」），行為不變的證據是：腳本內建的「觸發集合不變」守衛＋ESLint 的 `useCallback` 依賴驗證＋`tsc`＋`build`＋E2E 175 案例。E2E 並未涵蓋每一個被改動的頁面，所以這是**高度信心，不是逐頁證明**；殘留風險集中在 23 個 `useCallback` 轉換的頁面（ERP、blog、CMS、knowledge、media、notifications、posts、tenant 等）。
2. **「0 warning」不等於「0 個 lint 例外」**：新增 18 個 `eslint-disable`（13＋2＋3），每個都附理由。它們是刻意的，不是被消音的缺陷，但仍是例外。
3. `cms/posts/[id]/edit` 那個例外背後有一個**潛在的設計問題**：`parseEmbedCards` 讀取 `embedCards` 形成的過期閉包。我沒有重構它（不是 `DEF-398` 的範圍，且會動到狀態邏輯）。
4. **沒有任何東西阻止 warning 再次累積**：`lint` 就是裸的 `eslint`，CI（`ci.yml`、`act-compat.yml`）、`Makefile`、pre-commit hook 都沒有 `--max-warnings`，warning 永遠不會讓任何東西失敗——這是 99 筆能靜默累積、連 Sprint 290 都數成 10 筆的結構性原因。加守門是改政策，不替使用者決定，登記為 `DEF-400`。

## 7. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | 23 個 hooks 案例用 `useCallback`（觸發集合不變），而不是全部加 `eslint-disable` | 使用者的指示是「能安全修正的修正」；修改前 `HEAD` 的 hooks disable 有 9 處（8 檔），**全部沒附理由**（實測 `git grep`），這次能證明等價的就修、不能的才附理由保留 | 想比照舊慣例全部 disable：`git checkout` 該檔再加註解 |
| 2 | 3 個會改變行為的案例保留為附理由的例外，不重構狀態邏輯 | 重構會動到狀態邏輯，超出 `DEF-398` 的範圍 | 想重構 `parseEmbedCards`／搜尋框：另開 Sprint |
| 3 | `<img>` 維持原樣加 disable，不換 `next/image` | 換了是版面變更（需要尺寸與 `remotePatterns`），理由見 §3.4 | 想換：先在 `next.config.ts` 設 `images.remotePatterns` 並決定尺寸策略 |
| 4 | 死碼直接刪除，不加底線前綴消音 | 沒有任何引用，保留只會誤導 | `git revert` |
| 5 | 不動 `package.json`／eslint 設定／建置設定 | 範圍 | — |
| 6 | 新增 `DEF-400`（lint 守門）登記但不實作 | 改守門政策會影響之後每一次 commit／CI | 回「依照建議」即做 `--max-warnings=0` |

## 8. 雲端 CI 與狀態

push 後回填。

## 9. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `frontend/src/**`、`frontend/e2e/at-storefront-real.spec.ts` | 55 個檔，`+321／−324` |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | v3.69→v3.70：`DEF-398` 結案；新登記 `DEF-400` |
| `docs/04_planning/RELEASE_TRACKER.md` | v4.15→v4.16：Sprint 297 row；回填 Sprint 296 |
| `docs/04_planning/SPRINT_296_PLAN.md` | §5 回填雲端 CI 結果 |
| `docs/04_planning/SPRINT_297_PLAN.md` | 新增（本文件） |

後端零變更。
