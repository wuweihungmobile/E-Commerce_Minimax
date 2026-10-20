# Sprint 289 Plan — E2E 限流偵測改看 HTTP 狀態碼；DEF-373 殘留文件校正

## 1. 起點與範圍

### 1.1 起點

使用者貼回 Sprint 288 總結，回覆「請繼續完成任務！」，沒有任何 `==>` 逐項回覆。依既有慣例（[[inline-decision-replies-mean-proceed]] 的「無 `==>` 變體」）：這**不是**對「等你決定」項目的授權，做的是總結自己列為「還沒做」、且 AI 能獨立處理的項目。**以上對「繼續」的解讀是推論**，不是使用者明說的範圍。

開工時先核對上一輪的收尾狀態，不採信貼回的總結：`git status` 乾淨、`main` 與 `origin/main` 同為 `42c35421`；上一輪最後一個 docs 回填 commit `42c35421` 的雲端 CI（run 39459486329）開工時還在跑（Backend Unit／Frontend 兩個 job 已綠、整合測試 job 進行中），等它結束才碰任何需要 push 的東西（`cancel-in-progress`）；結束後以 `gh run view --json status,conclusion,headSha,jobs` 查得 headSha 與該 commit 一致、三個 job 皆 success（`2026-10-20T16:40:17Z`→`16:53:08Z`，約 13 分鐘）。

Sprint 288 總結的「下一輪候選」逐項評估：

| 候選 | 本輪處置 | 理由 |
|---|---|---|
| `DEF-373` | **排入（但範圍與總結說的不同，見 §2.1）** | 總結寫「FRD BR-M08-07 與 `createReply` 註解互相矛盾，未核對」。核對後發現追蹤表的 `DEF-373` 早在 Sprint 264 結案；殘留的只是 FRD 兩處過期敘述，屬純文件校正 |
| E2E helper 的限流偵測不依賴訊息文字 | **排入** | Sprint 288 自己登記的已知限制；`DEF-396` 證明文字比對會靜默失效；不涉產品決策 |
| `DEF-397`（PRD §16.4.1 `Content-Type`） | 不排入，只補強決策依據（§5） | 政策決定，總結已標「等你裁決」；沒有 `==>` 回覆就不替使用者決定 |
| `targetTenantId` 不驗證租戶存在 | 不排入 | Sprint 287 自己留下的低風險收尾，且是後端改動（要跑全量 `mvn verify`），不在總結的候選清單內 |
| `DEF-394` 前端管理介面、`getCurrentTenant()` 語意稽核 | 不排入 | 需求不明／規模大 |

### 1.2 成功標準

1. FRD 不再把 `DEF-373` 寫成未修復；現行文件（FRD、`API_M08_Review.md`、`SRD_Module_Technical_Design.md`）對評價回覆授權的描述彼此一致，且與程式碼一致。
2. `API_M08_Review.md` 不再把 `DEF-392` 寫成未修復（核對 `DEF-373` 時順帶發現的同型過期敘述）。
3. E2E helper 偵測限流**不依賴訊息文字**，證據要有對照：(a) 改前 helper 在「狀態碼 429、本文 `???????`」下失敗，改後通過；(b) 真實後端的真實 429 改前改後都通過（無回歸）；(c) 把偵測弄壞的變異使探針轉紅。
4. `make validate-e2e` 無回歸（Sprint 288 基準 175 passed／4 skipped／0 failed）。

### 1.3 helper 怎麼偵測限流

Sprint 288 當時沒採用「看 HTTP 狀態碼」，理由記在 [[rate-limit-response-garbled-charset-def396]]：要掛 `response` 監聽，是另一種同步訊號，有 [[playwright-text-sync-and-role-login-pitfalls]] 的競態風險。這次逐一處理那個風險，而不是繼續把脆弱點留著：

| 方案 | 取捨 |
|---|---|
| (A) 點擊前先掛 `waitForResponse`，看那個 POST 回應的狀態碼 | 狀態碼是契約（429），不受訊息改寫或編碼損毀影響；不改產品程式碼。競態風險靠三個做法收斂：**先掛監聽再點擊**（順序反過來快速回應會漏接）、**先等這一次送出的回應，再等畫面**（避免把上一輪失敗遺留在畫面上的錯誤區塊當成這一次的結果）、**監聽的 Promise 永不 reject**（逾時回 `undefined`，不產生未處理的拒絕） |
| (B) 在登入／註冊頁的錯誤區塊加 `data-error-code` 之類屬性 | 為了測試而改產品程式碼；且註冊頁連 `data-testid` 都沒有 |
| (C) 維持文字比對，把 `???????` 也加進關鍵字 | 把「壞掉的症狀」寫進偵測條件，等於替 `DEF-396` 類缺陷背書 |

**選 (A)**。

## 2. 執行紀錄

### 2.1 DEF-373 殘留：誰對，文件還是程式碼

| 來源 | 說法 |
|---|---|
| 追蹤表 `DEF-373` | ✅ Sprint 264（2026-10-14）完成，使用者以 `AskUserQuestion` 拍板放寬 `createReply` 為「同租戶或管理員」 |
| 程式碼 `ReviewReplyService.createReply`（第 68～76 行） | 呼叫者租戶等於 listing 租戶，或為管理員，否則 `E_1007` |
| 測試 | `ReviewReplyServiceTest.createReply_SameTenantNonOwner_Succeeds`（同租戶非確切 owner 成功）、平台管理員跨租戶成功、不同租戶拒絕；`ReviewAuthorizationRealDbIntegrationTest` 的 DEF-373 區段（真實 PostgreSQL） |
| `API_M08_Review.md`、`SRD_Module_Technical_Design.md` §7 | 已於 Sprint 264 同步為「同租戶或管理員」 |
| **FRD `BR-M08-07`、§6D.2「商家／房東回覆」列** | **仍寫「三處不一致…確切擁有者…DEF-373 未修復」← 過期** |

**結論：程式碼是對的，FRD 兩處過期。** Sprint 268（FRD v1.12）校正 `BR-M08-10` 與 §6D.6 時漏了這兩處。這個矛盾由 Sprint 287 實作 `DEF-392` 時發現並記錄，本輪核對後校正。這是同型漂移的第二次：`SRD_Module_Technical_Design.md` 也曾因「Sprint 264 修復時忘了同步這份文件」在 Sprint 265 被補過一次（該文件 v1.5 變更紀錄），三份文件（API_M08、SRD、FRD）裡 FRD 是最後一個被補的。

校正時順手更正原文**一處不精確的描述**（讀碼確認，不是照抄追蹤表）：原寫「三者共用同一組 `@PreAuthorize` 權限字串」。實際是 `POST /v2/reviews/{id}/replies` 與 `PUT /v2/reviews/{id}/handle` 為 `room:update` 或 `product:update`，`POST /v2/booking-reviews/{id}/reply` 為 `room:update` 或 `booking:update`；`STORE_STAFF` 的權限清單裡沒有這三個 update 權限（`RolePermissionMapping` 第 157～186 行的區塊），在權限註解這一層就被擋下，並非「通過後才被回覆評價拒絕」。追蹤表原先也寫「`STORE_STAFF` 無任何 `*:update` 權限」，那句過寬（它有 `SUPPORT_TICKET_UPDATE`、`CMS_UPDATE`），改寫成精確版本。

**核對 `DEF-373` 時順帶發現的第二處**：`API_M08_Review.md` §7 仍有一列「**DEF-392（未修復）**：賣家回覆評價『有後端、零前端』」。`DEF-392` 已於 Sprint 287 修復（`frontend/src/app/dashboard/reviews/page.tsx` 存在，含處理狀態篩選＋分頁、回覆表單、標記已/未處理切換；`services/review.ts` 有 `getReplies`／`createReply`／`setHandled`／`getManagedReviews`；`ReviewList.tsx` 顯示店家回覆；`at-review-reply-real.spec.ts` 5 個案例）。Sprint 287 兩個 commit 改過的 API 文件只有 `API_M09_Notification.md` 與 `API_M11_Logistics.md`，沒有同步 M08。這一列改為刪除線＋「✅ Sprint 287 已修復」並寫明修復內容。

**修改清單**

| 文件 | 變更 |
|---|---|
| `E-Commerce_FRD_v1.0.md` v1.15→**v1.16** | 檔頭兩處版本號；§6D.2「商家／房東回覆」一列；`BR-M08-07` 全文改寫；變更紀錄新增 v1.16 列 |
| `API_M08_Review.md` v1.2→**v1.3** | 檔頭版本與最後更新日；§7 的 `DEF-392` 一列；文件狀態行 |
| `DEFERRED_ITEMS_TRACKER.md` | `DEF-373`、`DEF-392`、`DEF-396`、`DEF-397` 四列尾端補記；文件版本鏈 v3.64→v3.65 |
| `RELEASE_TRACKER.md` | 新增 Sprint 289 列、統計區、版本歷史 v4.07 |

掃描確認：現行文件中沒有其他地方把 `DEF-373` 或 `DEF-392` 寫成未修復；剩下的命中都是歷史變更紀錄、追蹤表與 RELEASE_TRACKER 對當時狀態的歷史敘述，以及本輪新寫的校正說明本身。掃描方法備註：第一版用 `grep -v` 過濾「行」，會把含計畫書連結的現行敘述一併濾掉，所以改用 Python 逐檔掃描、以「檔案名」排除 `SPRINT_*_PLAN.md`（歷史計畫書），再逐筆讀前後文判斷是現況還是歷史。

### 2.2 helper 驗證：一次性探針（跑完即刪，不入版控）

起自己的全棧（`postgres:18-alpine`＋`redis:7-alpine`、打包 JAR 以 `ddl-auto=validate` 對 Flyway 乾淨 DB 啟動、`next start`；沿用既有建置產物，JAR 的修改時間是 2026-10-20 23:30、`.next` 是 23:50，皆非本輪重新建置，本輪後端與前端 src 無變動），探針規格暫放 `frontend/e2e/`，用同一組探針先打**未修改的 helper**（對照組），再打修改後的 helper。

| 案例 | 設計 | 非空轉斷言 |
|---|---|---|
| PROBE-0（前提確認，不經過 helper） | 用 `page.route` 把登入 POST 的回應換成「狀態碼 429、本文 `message` 為 7 個問號」，送出表單後讀 `login-error` | 實際文字須含 `?` 且不含「速率限制」 |
| PROBE-1 | 既有帳號，前 2 次登入 POST 回上述偽造 429，之後放行到真實後端；呼叫 `registerAndLogin(page, email)` | 偽造的 429 須被 helper 自己的請求撞到恰好 2 次；耗時 ≥ 2×2.5 秒；取得 userId、不在 `/login` |
| PROBE-2 | 全新帳號，前 2 次註冊 POST 回上述偽造 429；呼叫 `registerAndLogin(page)` | 同上 |
| PROBE-3 | **真實**登入限流：另開分頁從瀏覽器持續打登入端點把 token bucket 維持在空，helper 撞到第一個真實 429 後停手 | helper 自己的登入 POST 須看到 ≥1 次 429、最後一次 200、耗時 ≥ 2.5 秒 |
| PROBE-4 | **真實**註冊限流，同上 | 同上（最後一次狀態 <300） |

**前提確認**：PROBE-0 實測登入頁的錯誤區塊文字是 `"???????"`，與 `DEF-396` 修復前的真實症狀一致——所以案例 1、2 的偽造回應等於「訊息不可讀，但狀態碼是真的 429」。

**對照組（改前 helper，工作區 `frontend/e2e/helpers/auth.ts` 與 HEAD 相同）**

| 案例 | 結果 |
|---|---|
| PROBE-1 | ❌ 失敗：`submitRegister: 註冊失敗…attempt=0, rateLimited=false`——登入遇到不可讀的 429 被當成「帳號不存在」，helper 對既有 email 改走註冊（與 Sprint 288 記錄的修復前樣貌相同） |
| PROBE-2 | ❌ 失敗：`submitRegister: 註冊失敗…attempt=0, rateLimited=false`，立刻拋錯不重試 |
| PROBE-3 | ✅ 通過（helper 看到 `[429,200]`，5.6 秒）——真實 429 的訊息已可讀，文字比對本來就認得 |
| PROBE-4 | ✅ 通過（`[429,201]`，12.6 秒） |

### 2.3 修改（`frontend/e2e/helpers/auth.ts`，+45／−18）

- 新增 `watchAuthPostStatus(page, apiPath, timeoutMs)`：回傳該認證端點 POST 回應的 HTTP 狀態碼，逾時或頁面關閉回 `undefined`，永不 reject；**必須在點擊之前呼叫**。
- `submitLogin`：點擊前掛監聽；點擊後**先 `await` 這一次送出的回應**，再照原樣等「離開 `/login` 或錯誤區塊出現」；`rateLimited = (狀態碼 === 429)`。
- `submitRegister`：同樣點擊前掛監聽；`rateLimited` 改看狀態碼；等待上限抽成 `REGISTER_RESULT_TIMEOUT_MS`（6 秒，與原本 `waitForURL` 的字面值相同，同時也是等回應的上限）；拋出的錯誤訊息新增 `status=`，下次失敗能直接分辨「收到 429 卻沒當限流」與「別的錯誤」。
- 刪除 `RATE_LIMIT_ERROR_TEXT`（兩處文字比對不再存在）；註解同步更新，保留 Sprint 286 的來龍去脈並註明 Sprint 289 的改動。

`tsc --noEmit` 與 `eslint e2e/helpers/auth.ts` 皆 exit 0。

### 2.4 修改後（同一組探針）

| 案例 | 最終檔案的結果 |
|---|---|
| PROBE-0 | ✅ `login-error` 文字 `"???????"` |
| PROBE-1 | ✅ 偽造 429 被撞到 **2** 次，6.6 秒，取得 userId，停在 `/dashboard` |
| PROBE-2 | ✅ 偽造 429 被撞到 **2** 次，20.4 秒（每次註冊失敗固定先等 6 秒的 `waitForURL`，既有行為未動，見 §5） |
| PROBE-3 | ✅ `[429,200]`，4.4 秒 |
| PROBE-4 | ✅ `[429,201]`，11.6 秒 |

對照組失敗的兩個案例（1、2）全部轉綠；對照組本來就通過的兩個案例（3、4）維持通過，沒有回歸。

### 2.5 變異驗證

把 `HTTP_TOO_MANY_REQUESTS` 由 429 改成 428（偵測永遠不成立）：**四個案例全部轉紅**。新加的 `status=` 診斷當場派上用場：案例 2、4 直接顯示 `status=429, rateLimited=false`；案例 1、3 顯示 `status=409`（登入悄悄返回後，helper 對既有 email 走註冊，收到 409；推論為既有 email 重複註冊，沒有讀回應本文）。還原後檔案 md5 與保存的版本相同。

未做「把監聽移到點擊之後」的順序變異：快速回應與監聽掛上的先後是競態，單次實驗無法決定性重現，不拿它當證據；順序要求只寫在 `watchAuthPostStatus` 的註解裡。

### 2.6 順帶實測：Playwright 看不到 CORS 預檢

我註解初稿寫「限定 POST 是因為瀏覽器對跨來源請求會先送 OPTIONS 預檢，網址相同」。用一個事件傾印案例量測：登入網址上 Playwright 只看得到 `requests=["POST"]`、`responses=["POST:401"]`，**預檢根本不會出現在事件裡**。初稿那句是臆測，已改寫成實測事實：POST 限定保留（語意上明確指向「送出表單的那個請求」，對未來 Playwright 版本也是防禦），但註解註明 Sprint 289 實測目前是防禦性的。

### 2.7 刻意不做的事

- 不把註冊失敗時的 6 秒 `waitForURL` 與狀態碼合併成「收到非 2xx 就提前返回」：能省時間，但那是另一個改動（Rule 3），且探針 2 已證明現行流程正確。
- 不動後端、不動限流訊息、不動 `createReply` 的訊息字串（見 §5）。
- 不處理 `DEF-397`。
- 不把一次性探針留成常駐測試：它需要打滿真實限流桶，放進 `make validate-e2e` 會干擾其他規格的登入額度（見 [[e2e-gate-flake-rate-limiters]]）。

## 3. 全量驗證

| 項目 | 結果 |
|---|---|
| `tsc --noEmit`、`eslint e2e/helpers/auth.ts` | 皆 exit 0（修改後與最終檔案各驗一次；第一次我誤把 `head` 的退出碼當成 tsc 的，發現後改為不經管線重跑） |
| `make validate-e2e`（乾淨 DB → Flyway 重建 → 重新建置 JAR 與前端 → 全棧 → Playwright） | **175 passed／4 skipped／0 failed**（6.7 分鐘），**只跑一次、一次就綠**；腳本印出「本地 E2E 守門通過（schema 對齊 ✅ + e2e 全綠 ✅）」；backend 以 `ddl-auto=validate` 對 Flyway 乾淨 DB 啟動成功＝schema 無漂移；內含 `mvn package` 與 `npm ci && npm run build` 重新建置成功。與 Sprint 288 基準（175／4／0）相同 |
| 後端 | 本輪沒有後端變動（`git status` 只有 `frontend/e2e/helpers/auth.ts` 與 `docs/`），未重跑全量 `mvn verify`；Sprint 288 基準為單元 2293／整合 873、0 failed |

**誠實限制**

- 守門只跑一次就全綠，**不能當成間歇失敗已消失的證據**（Sprint 287 曾連敗三次）。
- 守門日誌不記錄限流次數，且後端日誌在腳本結束時被刪除，所以**無法得知這次守門有沒有走到 helper 的重試路徑**；重試路徑的證據只來自 §2 的探針，不是守門。
- 守門通知的退出碼我沒有直接採信，另外讀了日誌內容核對（`175 passed (6.7m)`、`4 skipped`、0 筆失敗標記、腳本的通過訊息）。

## 4. 文件更新

見 §2.1 的修改清單。`DEFERRED_ITEMS_TRACKER.md`：`DEF-373`／`DEF-392`／`DEF-396` 三列補記、`DEF-397` 補上 RFC 8259 查證（§5）、文件版本 v3.65、`最後更新` 改寫、前一版降為「歷史版本 v3.64」；`RELEASE_TRACKER.md`：新增 Sprint 289 列（Release Tag `v2030.04.146-01`）、統計區、版本歷史 v4.07。

## 5. 已知限制／留給未來 Sprint

**限制**

- **探針是一次性的，常駐守門只有 `make validate-e2e` 本身**，而守門只有真的撞到限流時才會走到重試路徑——一次全綠不能證明重試路徑被走到。重試路徑的證據來自上面的探針，不是守門。
- 探針跑在自起的全棧，用的是既有建置產物（JAR 2026-10-20 23:30、`.next` 23:50；本輪後端與前端 src 無變動，用 `git status` 確認過），不是本輪重新建置的；守門那次是重新建置的。
- 每個探針案例各跑一次對照＋一次改後（案例 1～4 另有一次變異）；沒有重複跑來量間歇性。
- 註冊遇到限流時，每次失敗固定先等 6 秒（`waitForURL` 的上限）再看狀態碼，加上 2.5 秒重試等待——連續被限流直到放棄（9 次嘗試）約 9×6＋8×2.5＝74 秒。這是既有行為，本輪沒動；若要縮短，可改成「收到非 2xx 回應就不再等 `waitForURL`」。
- 降低登入密度仍是第一道對策；本輪沒有量化「套件對登入密度的容忍度」。
- `ReviewReplyService.createReply` 拋 `E-1007` 時的訊息字串仍是 `Only the listing owner can reply`，與「同租戶或管理員」的判斷不完全吻合（Sprint 264 只改了判斷、沒改訊息）；僅措辭，沒動。
- FRD 檔頭「文檔元數據」表的「最後更新」欄位是 `2026-04-09`，自 v1.0 起從未維護（版本史在檔尾變更紀錄）；既有的長期漂移，本輪沒動。

**需要使用者裁決：`DEF-397`（PRD §16.4.1 `Content-Type` 固定值 `application/json; charset=utf-8`）——本輪補強了決策依據，仍未替使用者決定**

- **查證了追蹤表當時「未另行查證」的 RFC 說法**（本輪從 rfc-editor.org 取得原文）：RFC 8259 §8.1：「JSON text exchanged between systems that are not part of a closed ecosystem MUST be encoded using UTF-8」；§11 的 `application/json` 登記：「Optional parameters: n/a」，並註明「No "charset" parameter is defined for this registration. Adding one really has no effect on compliant recipients.」——所以沒有 charset 的 JSON 回應不會有損毀風險，PRD 這一行是「與字面的落差」，不是資料問題。
- PRD §16.4.1 那張表其實有四個標頭（`X-Request-ID`、`X-Content-Type-Options`、`X-Frame-Options`、`Content-Type`）。另三個在程式碼裡都有對應 PRD §16.4.1 的實作（`RequestIdFilter`、`SecurityHeaderPolicy`）；本輪只確認程式碼存在，沒有重新量測回應標頭。落差範圍因此只有 `Content-Type` 的 charset。
- **建議選 (3) 修訂 PRD 措辭**（改為 `application/json`，註明編碼固定 UTF-8、依 RFC 8259 不另帶 charset 參數），理由：(1) 全域 `force-response=true` 影響面最大（所有回應含圖片與檔案下載的 `Content-Type`，且已知至少一處既有測試斷言會碰到）；(2) 逐處補要對抗 Spring MVC 的預設行為（Sprint 288 實測它產生的 JSON 回應本來就不帶 charset），改動多、收益是「與 PRD 字面一致」而非修正缺陷。**可選的小動作**：`SecurityConfig` 的兩個 handler（401／403）與 `DEF-396` 同款寫法，補上 `setCharacterEncoding(UTF_8)` 並加單元測試，只為了防止將來有人把訊息改成中文而重蹈覆轍（目前訊息是英文，無實害）。
- 回「依照建議」即可：我會改 PRD 措辭＋補 `SecurityConfig` 兩處（含測試），並結案 `DEF-397`。
