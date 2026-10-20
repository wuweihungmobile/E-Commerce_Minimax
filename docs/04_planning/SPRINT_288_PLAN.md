# Sprint 288 Plan — DEF-396：限流與租戶標頭檢查回應的中文訊息被編碼成 `?`

## 1. 起點與範圍

### 1.1 起點

使用者貼回 Sprint 287 總結，回覆「請繼續完成任務！」，沒有任何 `==>` 逐項回覆。依既有慣例（[[inline-decision-replies-mean-proceed]] 的「無 `==>` 變體」）：這**不是**對「等你決定」項目的授權，做的是總結自己列為「還沒做」、且 AI 能獨立處理的項目。Sprint 287 總結的「建議下一步」只有一項：處理 `DEF-396`。它是缺陷（回應內容被損毀），不涉及產品決策，因此排入本輪。**以上對「繼續」的解讀是推論，不是使用者的明確指示。**

開工時先核對上一輪的收尾狀態，不採信貼回的總結：`git status` 乾淨、`main` 與 `origin/main` 同為 `a5abf913`；以 `gh run list --json` 查得 `a5abf913` 的 Local CI（run 39441553186）、`3be2eab6` 的 Local CI（run 39438935574）與手動觸發的依賴安全審計（run 39439129009）皆為 success。

其他候選（`DEF-373`、`targetTenantId` 不驗證租戶存在、`DEF-394` 前端介面、`getCurrentTenant()` 語意稽核）本輪不排入：`DEF-373` 要先核對文件與程式碼誰對，其餘是低風險或需求不明。`DEF-396` 則有可測量的證據、直接影響使用者看到的錯誤文字與 E2E 守門的可靠度，也是總結自己的建議。

### 1.2 成功標準

1. 三個直接寫回應的位置在**真實 Tomcat** 上回 `charset=UTF-8`，`message` 逐位元組等於原文（修復前 0/3 → 修復後 3/3）。
2. 每處都有單元測試：修復前轉紅、修復後轉綠；並以突變驗證確認「標頭宣告」與「實際位元組」兩個斷言各自抓得到問題。
3. E2E 輔助函式的限流重試對**真實 429** 確實生效——要有「修復前失敗／修復後通過」的對照，不是只靠推論。
4. 全量 `mvn -o clean verify` 與 `make validate-e2e` 無回歸。

### 1.3 修法選擇

`DEF-396` 登記了兩個修法，註明「處理時決定」：

| | (a) 三處各自宣告 UTF-8 | (b) `application.yml` 設 `server.servlet.encoding.force-response=true` |
|---|---|---|
| 影響面 | 只有這三個回應本體 | 全部回應的 `Content-Type` 都會被追加 `;charset=UTF-8`，含圖片與檔案下載（依 Tomcat `setCharacterEncoding` 的語意推論，**未實測**） |
| 既有測試 | 不需變動 | 後端測試中讀取回應 `Content-Type` 的既有斷言只有 1 處：`M18MediaIntegrationTest:579` 比對 `image/jpeg`（正是「圖片回應被追加 charset」會碰到的那種斷言）；是否真的翻紅**未實驗**（取決於 MockMvc 是否帶入強制編碼的 filter） |
| 專案內先例 | `ApiErrorReportValve`（Sprint 206）已是 `setContentType("application/json")` ＋ `setCharacterEncoding("utf-8")` | 無 |
| 好處 | 精準、可逐處驗證 | 涵蓋日後新增的寫回應位置；順便讓所有回應符合 PRD §16.4.1「`Content-Type` 固定值 `application/json; charset=utf-8`」 |

**選 (a)**。理由：缺陷只發生在三個位置，(a) 的改動與驗證範圍和缺陷範圍一致，且延續 `ApiErrorReportValve` 的既有寫法（Rule 3、Rule 11）；(b) 的影響面是整個回應層（含非 JSON 的圖片與檔案下載，已知至少一處既有斷言會碰到），需要另外評估，不該為了修一個三處的缺陷順手承擔。

**更正紀錄**：這張表的第一版把「後端測試有 152 處以 `MediaType.APPLICATION_JSON` 精確比對 `Content-Type`」當成 (b) 的主要代價。那是 grep 寫得太寬——`.contentType(MediaType.APPLICATION_JSON)` 在 MockMvc 是**請求**的建構寫法，152 處幾乎全是請求端，真正讀取回應 `Content-Type` 的既有斷言只有上表那 1 處。(b) 的測試面比我最初寫的小得多；選 (a) 的結論不變，但依據是「範圍相稱＋既有先例＋非 JSON 回應的副作用」，不是測試改動量。

取捨如實揭露：
- 選 (a) 等於接受「將來有人新增第六處直接寫回應的位置、又忘了宣告編碼，會重蹈覆轍」。本輪不加原始碼掃描型守門測試（Rule 2：目前以 `getWriter()` 直接寫回應的只有五處——三處本輪修復、`SecurityConfig` 兩處為英文訊息；另有 `ApiErrorReportValve` 用 Tomcat 的 `getReporter()`，早已宣告 UTF-8；三個新單元測試也已示範該寫什麼）。
- `SecurityConfig` 的 `authenticationEntryPoint`／`accessDeniedHandler` 是同款寫法，但訊息是英文（`Authentication required`／`Insufficient permissions`），不會損毀，**刻意不動**（Rule 3）。
- (b) 的「順便對齊 PRD」是真實的好處，但它針對的是另一個問題（沒有 charset 參數的 JSON 不會損毀資料），應獨立評估影響面後再決定，見 §5。

## 2. 執行紀錄

### 2.1 修復前實測：三處全部在真實 Tomcat 上證實

Sprint 287 只實測過 `LoginRateLimitFilter`，另外兩處是依相同寫法推論。本輪補齊：以 `spring-boot:run`（開發設定：`ddl-auto=update`、Flyway 關閉）對 `make test-db-up` 的真實 PostgreSQL／Redis 起後端，用同一份腳本逐位元組檢查回應（`curl -D` 取標頭、`jq -j .message | xxd -p` 取訊息位元組，與預期 UTF-8 位元組比對）。

| 位置 | 觸發方式 | 狀態 | `Content-Type` | `message` 位元組 |
|---|---|---|---|---|
| `TenantContextFilter`（`writeInvalidTenantIdResponse`） | SUPER_ADMIN（註冊後直接改 `users.role`）帶 `X-Tenant-ID: not-a-uuid` 打 `GET /v2/auth/me` | 400 | `application/json;charset=ISO-8859-1` | `X-Tenant-ID ????`（前 12 個 ASCII 位元組保留，「格式錯誤」四字變 4 個 `0x3F`） |
| `LoginRateLimitFilter` | 對 `POST /v2/auth/login` 連打，第 32 次起 429 | 429 | `application/json;charset=ISO-8859-1` | 7 個 `0x3F` |
| `RateLimitFilter` | 匿名連打 `GET /v2/public/listings`，第 118 次 429（回應帶 `X-RateLimit-Limit`，確認是租戶／IP 限流而非登入限流） | 429 | `application/json;charset=ISO-8859-1` | 7 個 `0x3F` |

**0/3 通過。** `RateLimitFilter` 與 `TenantContextFilter` 從「推論」變成「實測」，結果與讀碼一致。

### 2.2 TDD：先寫會失敗的測試

為三個 filter 各加一個測試（`LoginRateLimitFilterTest.deniedRequest_writesChineseMessageAsUtf8`、`RateLimitFilterTest.deniedRequest_writesChineseMessageAsUtf8`、`TenantContextFilterTest.superAdminInvalidTenantHeaderFormat_writesChineseMessageAsUtf8`），各有兩個斷言：

1. `response.getContentType()` 含 `charset=UTF-8`（用戶端依標頭宣告的編碼解碼）。
2. 把 `response.getContentAsByteArray()` **明確以 UTF-8 解碼**後含原文。刻意不用 `getContentAsString()`：它依回應自己宣告的編碼解碼，「寫入與解碼用同一個錯誤編碼」的缺陷也會通過。

為什麼單元層足以重現：`MockHttpServletResponse` 與 Tomcat 一樣預設 ISO-8859-1，`getWriter()` 同樣把中文寫成 `?`。

**為什麼缺陷能活這麼久（Rule 9）**：既有的 429 測試只斷言 `contains("E-9904")`，從來沒有比對過中文訊息本身。

三個測試跑在修復前的生產程式碼上：36 個案例中 33 通過、3 失敗，三個失敗都停在第一個斷言（「回應必須宣告 UTF-8」）。

### 2.3 修復

三個檔案各加 `import java.nio.charset.StandardCharsets;` 與兩行（註解＋宣告），在 `setContentType(...)` 之後、`getWriter()` 之前：

```java
response.setContentType(MediaType.APPLICATION_JSON_VALUE);
// DEF-396：須在 getWriter() 之前宣告編碼；容器預設 ISO-8859-1，否則中文訊息會被不可逆地寫成 '?'
response.setCharacterEncoding(StandardCharsets.UTF_8.name());
response.getWriter().write(...);
```

沿用 `ApiErrorReportValve` 的字串寫法（`setCharacterEncoding(String)`）。三個測試類別修復後 36/36 通過。

### 2.4 突變驗證

- **拿掉修復 → 轉紅**：§2.2 的紅燈階段就是這個突變（紅燈時的生產程式碼＝拿掉修復），三處各自轉紅。
- **把宣告放到寫入之後**（`LoginRateLimitFilter`）：標頭斷言通過、位元組斷言失敗，失敗訊息是 `"message":"???????"`——證明第二個斷言不是多餘的。真實容器對遲到的 `setCharacterEncoding` 同樣忽略，這是順序放錯時最可能出現的退化。還原後以 `diff` 確認與備份逐字相同。

### 2.5 修復後實測

同一份腳本、同一台機器、同一個資料庫：三處都回 `Content-Type: application/json;charset=UTF-8`，`message` 位元組與預期完全一致（例如 429 的 `e5b7b2e8b685e9818ee9809fe78e87e99990e588b6`＝「已超過速率限制」）。**3/3 通過。**

全量 `mvn -o clean verify`（javac 從零重編）之後，另用它打出的 JAR 副本再量一次（`java -jar`、Flyway 關閉、`ddl-auto=update`，對同一組測試 DB）：同樣 **3/3 通過**（登入限流第 32 次、租戶限流第 121 次起 429）——確認修復在打包產物裡，不只在 `spring-boot:run` 的 class 目錄裡。

### 2.6 E2E 限流重試的首次真實驗證（含對照）

**動機**：`frontend/e2e/helpers/auth.ts` 的 `submitLogin`（Sprint 234）與 `submitRegister`（Sprint 286，`DEF-395`）都以訊息文字偵測限流並重試，但文字被寫成 `???????`，重試從未對真實 429 觸發過——Sprint 287 的總結說「修好後重試才會真正生效」，這是推論，從未被觀察到。

**靜態確認**：登入頁與註冊頁都直接顯示 `err.response.data.message`；前端對登入／註冊的 429 沒有任何轉換（只有忘記密碼、重設密碼兩頁有自己的英文 429 訊息，與這兩個 helper 無關）。

**探針設計**（一次性 Playwright 規格，跑完即刪、未入版控）：從瀏覽器自己用 `fetch` 把登入（或註冊）限流桶打到第一個 429，再呼叫真正的 `registerAndLogin`。用瀏覽器發請求，確保與 UI 動作是同一個來源位址。**探針本身要能失敗**：斷言 helper 自己的請求至少撞到 1 次真實 429、且耗時 ≥ 2.5 秒（至少等過一次重試間隔），否則視為沒驗到重試。

| | 登入重試 | 註冊重試 |
|---|---|---|
| **修復後後端（處理）** | 第 30 次打到 429；helper 撞到 1 次 429，等待後重試成功，3.7 秒 | 第 31 次打到 429；helper 撞到 1 次 429，重試成功，10.8 秒（含 helper 自己的 6 秒 `waitForURL` 等待） |
| **修復前後端（對照）** | **失敗**。後端日誌：helper 的登入撞到 429，但訊息是 `???????`，helper 認不出是限流，誤判成「帳號不存在」，對**既有 email** 走註冊 → `E-1005 電子郵件已被註冊` → 拋出 `submitRegister: 註冊失敗…rateLimited=false` | **失敗**。註冊頁錯誤區塊顯示 `"???????"`，helper 判成 `rateLimited=false, attempt=0`，**立刻拋錯、不重試** |

**過程中的兩個修正（如實記錄）**：
1. 對照組的登入案例第一次失敗時，我先把它讀成「準備階段」失敗（頁面快照顯示的是「電子郵件已被註冊」，不是限流），讀後端日誌才還原出真正順序：準備階段成功（登入 401 → 註冊成功 → 登入成功），失敗發生在打滿登入桶之後的第二次 `registerAndLogin`——上表的「誤導性錯誤」正是缺陷在 helper 上的真實樣貌：**錯誤訊息怪的是註冊，根因卻是限流**。
2. 對照組的註冊案例第一次執行**沒有結論**：helper 的請求送出時桶已補回 token，0 次 429，被探針自己的「至少撞到 1 次 429」斷言擋下。改成另開一個分頁每 100 毫秒補打註冊、把桶維持在空的狀態約 4 秒後，對照才成立。

**限制**：處理組的註冊案例用的是第一版（較不確定的）探針，剛好撞進時間窗；對照組用的是改良版。兩者測的是同一份 helper 與同一組頁面，差別只在怎麼維持桶為空，沒有用改良版重跑處理組。探針跑在開發設定的後端（`spring-boot:run`、`ddl-auto=update`），不是守門腳本的打包 JAR ＋ Flyway；每個案例只跑一次，沒有重複執行來估計間歇失敗率。

### 2.7 順帶量測：其他回應的 `Content-Type`（PRD §16.4.1）

修法選擇時發現 PRD §16.4.1 規定「所有 Error Response 皆包含 … `Content-Type` 固定值 `application/json; charset=utf-8`」。為了不憑印象下結論，用同一個 JAR 對各種回應來源各量一次（`curl -D`）：

| 回應 | 來源 | `Content-Type` |
|---|---|---|
| 200 正常 JSON | MVC | `application/json` |
| 401 未登入 | `SecurityConfig.authenticationEntryPoint` | `application/json;charset=ISO-8859-1`（訊息 `Authentication required` 是 ASCII，不會損毀，但**宣告的編碼是錯的**） |
| 403 買家打管理端點 | MVC（訊息是「權限不足」，不是 `SecurityConfig` 的 `Insufficient permissions`） | `application/json` |
| 404 | MVC（`GlobalExceptionHandler`） | `application/json` |
| 400 驗證失敗 | MVC | `application/json` |
| 400 防火牆拒絕（`%0A`） | `ApiErrorController` | `application/json` |
| 400 連接器層拒絕（`%2f`） | `ApiErrorReportValve` | `application/json;charset=utf-8` |

**結論**：PRD 這一行目前只有連接器層（Sprint 206）與本輪修好的三處符合；其餘 JSON 錯誤回應不帶 charset（資料不會損毀：JSON 本身即 UTF-8），`SecurityConfig` 的 401 宣告成 `ISO-8859-1`（與 `DEF-396` 同款寫法，目前因訊息是 ASCII 而無害）。這是**與 PRD 字面的落差，不是資料損毀**，且該怎麼處理需要決定（見 §5），故登記為 `DEF-397`、本輪不修。

### 2.8 刻意不做的事

- **不把 helper 改成以 HTTP 429 狀態碼偵測**。Sprint 287 總結建議「順手」做，但本輪驗證顯示文字偵測在後端修好後已能運作；改成狀態碼偵測要在 `Page` 上掛 `response` 監聽，是另一種同步訊號，有重蹈 Sprint 287 §2.3 第 3 點（用畫面文字當完成訊號的競態）的風險，且需要整套 E2E 重驗。若將來有人改動限流訊息文字，這個依賴會再次靜默失效——這是已知的脆弱點，不是已解決。
- **不新增常駐的真實容器整合測試**。單元測試已涵蓋「宣告 UTF-8」與「實際位元組」兩層；真實 Tomcat 的位元組證據是一次性的（§2.1／§2.5）。
- **不動 `SecurityConfig`、不動前端。**

## 3. 全量驗證

- **後端 `mvn -o clean verify`**（2026-10-20；驗證開始後後端程式碼未再變動，期間只動 `docs/` 與暫存檔）：`BUILD SUCCESS`，耗時 18:13；單元測試 **2293**（0 failed／0 errors／0 skipped；較 Sprint 287 基準 2290 增加 3＝三個新測試），整合測試 **873**（0／0／0，與基準相同），Checkstyle main／test 皆 0 違規，PMD `check` 通過。生命週期從 `clean` 起跑，javac 從零重編，不依賴 IDE 預編譯的 class（§2.3 的單元測試是 IDE 先編的，這一次才是 Maven 自己的編譯）。
- **前端**：本輪沒有改動任何前端原始碼，故未另跑 `tsc`／`eslint`／`npm audit`；`make validate-e2e` 內含的 `npm ci && npm run build`（Next.js 16.4.2）成功。
- **`make validate-e2e`**（2026-10-20；重新 `mvn package` ＋ `npm ci && npm run build`，不是重用既有產物）：**175 passed／4 skipped／0 failed**（6.2 分，與 Sprint 287 基準相同的 179 個案例）；backend 以 `ddl-auto=validate` 對 Flyway 乾淨 DB 啟動成功（schema 無漂移）。**如實揭露**：只跑了一次完整守門，一次就綠。Sprint 287 的守門曾連敗三次，這次沒有失敗**不代表**限流造成的間歇失敗已消失——限流重試現在對真實 429 有效（§2.6），但本輪沒有量化「套件對登入密度的容忍度改善多少」，也沒有重複執行來估計剩餘的間歇失敗機率。本輪沒有新增任何 E2E 規格（探針是一次性的）。
- **push 與雲端 CI**（2026-10-21，補記）：`4da22d39` 推上 `main`（`a5abf913..4da22d39`）；pre-push 輕量守門（act Backend Unit ＋ Frontend Lint & Build ＋ schema 漂移守門）全過；雲端 CI run 39457350591 三個 job 皆 success（Backend Unit Tests／Backend Integration Tests & Package／Frontend Lint & Build），以 `gh run view --json status,conclusion,jobs,headSha` 結構化查詢交叉確認 `headSha` 與本機 commit 一致。

## 4. 文件更新

- `DEFERRED_ITEMS_TRACKER.md`：`DEF-396` 改為 ✅ 已修復並附修復驗證與限制；`DEF-395` 補 Sprint 288 補述（其重試路徑首次用真實 429 驗證）；新增 `DEF-397`；頁尾版本 v3.63 → v3.64（Sprint 287 的「最後更新」降為歷史版本 v3.63）。
- `RELEASE_TRACKER.md`：新增 Sprint 288 row（`v2030.04.145-01`）、統計區與歷史版本 v4.06；push 狀態與雲端 CI 結果於 push 後回填。
- **核對後不需更動**：`API_M03_Auth.md`、`API_Error_Codes.md` 的 `E-9904` 條目只描述狀態碼、觸發條件與訊息文字，沒有任何 `Content-Type` 編碼的敘述，修復是讓實際行為對齊文件；`API_Error_Codes.md` §3 寫「`Content-Type: application/json` ｜ 所有回應」，只聲明媒體型別、沒談 charset，與 §2.7 的實測不矛盾。PRD §16.4.1 是本輪的對照基準，不改。

## 5. 已知限制／留給未來 Sprint

- **`DEF-397`（PRD §16.4.1 的 `Content-Type` 固定值未全面落實）待決策**：三個選項（全域 `force-response=true`／逐處補 charset／修訂 PRD 措辭）與各自的代價見追蹤表。選哪個是政策決定，沒有替使用者決定。
- **E2E 輔助函式的限流偵測仍是文字比對**（`includes('速率限制')`、`getByText('速率限制')`）：若將來改動限流訊息文字，會再次靜默失效。本輪驗證了「後端修好後文字偵測能運作」，沒有把它改成更不脆弱的訊號（§2.8）。
- **`SecurityConfig` 的兩個 handler 與 `DEF-396` 同款寫法**：401 已實測宣告為 `ISO-8859-1`（§2.7），訊息是英文所以無害；`accessDeniedHandler` 在哪些情況會被走到（403 的實測回應是 MVC 產生的「權限不足」）本輪沒有查。
- **真實容器層沒有常駐測試**：單元測試用 `MockHttpServletResponse`（與 Tomcat 同樣預設 ISO-8859-1），真實 Tomcat 的位元組證據是一次性的（§2.1／§2.5）。
- **探針的限制**（§2.6）：跑在開發設定的後端、每案例只跑一次、處理組的註冊案例用較不確定的第一版探針。
- 承接 Sprint 287 仍未處理的候選：`DEF-373`（回覆評價擁有權檢查的文件與程式碼註解互相矛盾）、`DEF-394` 的 `targetTenantId` 不驗證租戶存在（低風險）、`DEF-394` 前端管理介面（視需求）、150+ 個 `getCurrentTenant()` 呼叫處的語意稽核。
