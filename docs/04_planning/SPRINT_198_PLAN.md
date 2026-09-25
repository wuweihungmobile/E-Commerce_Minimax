# Sprint 198 Plan — HSTS 在代理後方也送、前端嚴格 CSP、`X-Request-ID`（DEF-280）

**Sprint**: Sprint 198
**日期**: 2026-09-25

## 1. 起點與使用者決策

Sprint 197 收尾留下三項需要使用者決策的事。使用者的回覆（原文）：

| 項目 | 使用者回覆 |
|---|---|
| HSTS（TLS 在哪終止？） | 「我無法專業判斷，請依照最佳化理想化處理！」 |
| 前端 `script-src` CSP（需 nonce、全站動態渲染） | 「我無法專業判斷，請依照最佳化理想化處理！」 |
| DEF-280（`X-Request-ID`） | 「請實作」 |

前兩項是**授權我判斷**；下列決策與理由都是我做的，使用者可推翻。DEF-280 我理解為「實作 `X-Request-ID` 這個機制」，**沒有**解讀為「把錯誤封包整個改成 PRD 的 `{error:{...}}` 形狀」（見 §6）。本輪沒有使用 AskUserQuestion。

## 2. 決策一：HSTS——「送或不送」交給請求本身，不猜部署拓撲

**問題**：Spring 只在 `request.isSecure()` 時送 HSTS。TLS 由代理終止時後端收到的是 http，於是**永遠不送**。repo 內沒有 TLS 終止層，我不知道實際部署。

**決策**：後端與前端都改成「請求是 https，**或**代理以 `X-Forwarded-Proto` 告知原本是 https」才送。
- 後端：`SecurityConfig.isHttpsRequest` 作為 HSTS 的 request matcher（多層代理 `https,http` 取第一段）。
- 前端：`next.config.ts` 的 `headers()` 以 `has: x-forwarded-proto` 條件送出（Next 內建語法，無需程式碼）。
- 值 `max-age=31536000; includeSubDomains`（後端維持 Spring 預設格式 `max-age=31536000 ; includeSubDomains`，兩者依 RFC 6797 語法等價）。**不加 `preload`**：需向瀏覽器廠商登記、幾乎無法反悔。

**為何這是「理想」**：在每種拓撲下都正確且安全——TLS 在代理（有帶標頭）→ 送；應用自己終止 TLS → 送；純 http（本機開發）→ 不送。**最壞情況是代理沒帶標頭，那就維持現狀（不送）**，不會比現在更糟。標頭可被偽造，但偽造只會讓 http 回應多帶一個瀏覽器本來就忽略的標頭。

**沒選的做法**：`server.forward-headers-strategy=framework` 會連帶改動整個應用對 scheme／host 的認知（重導向、URL 產生），影響面遠大於 HSTS 本身。

## 3. 決策二：前端嚴格 CSP（nonce + `strict-dynamic`）

**為何值得付出全站動態渲染的代價**：`frontend/src/lib/axios.ts` 與 `useChatSocket.ts` 從 `localStorage` 讀取 `accessToken`／`refreshToken`（token 存於 localStorage）——XSS 一旦成功，攻擊者能直接讀走 token。這使 script 來源限制對本專案的價值特別高。代價（Next 16 文件明列）：所有頁面每次請求動態渲染、無法被 CDN 快取。build 結果從 **54 靜態 + 30 動態，變成 84 動態**。我沒有逐頁檢查那 54 個原本靜態的頁面內容，**也沒有量測改為每次請求渲染後的延遲或吞吐影響**。

**實作**：
- `src/proxy.ts`（Next 16 的 middleware 新名稱）每次請求產生 nonce，設定 CSP。指令：`default-src 'self'`；`script-src 'self' 'nonce-…' 'strict-dynamic'`（**無 `unsafe-inline`**；dev 才加 `unsafe-eval`）；`style-src 'self' 'unsafe-inline'`；`img-src 'self' data: blob: https:`；`font-src 'self' data:`；`connect-src 'self'` + 後端來源 + 對應的 `ws(s)`（SockJS）；`object-src 'none'`；`base-uri 'self'`；`form-action 'self'`；`frame-ancestors 'none'`。
- 後端來源直接取自 `API_CONFIG.baseUrl`（與實際呼叫的常數同一個，不會漂移）；後端是明文 http（本機／內網）時才連帶放行 http 圖片。
- `layout.tsx` 改為 async，讀 `x-nonce`（也正是強制動態渲染的機制）；`ThemeScript`（專案自己的 inline script）改帶 nonce，否則會被擋、主題會壞。
- **緊急開關**：執行環境設 `CSP_REPORT_ONLY=1` → 改送 `Content-Security-Policy-Report-Only`（只回報不攔截），部署後若 CSP 擋到未預期的資源可不改程式先恢復功能。已實測（見 §5）。

**刻意的取捨**：`style-src` 保留 `'unsafe-inline'`（React `style` 屬性與函式庫注入的 `<style>`，樣式注入風險遠低於 script）；**不加** `upgrade-insecure-requests`（在 http 部署會把打向後端的請求也升級成 https 而全部失敗）；不設 `report-uri`（沒有收集端，等於擺設）。

## 4. 決策三：`X-Request-ID`（DEF-280）

- `RequestIdFilter`（`@Order(HIGHEST_PRECEDENCE)`，早於 Spring Security，使在鏈內就被擋下的回應也帶得到；401 與 CORS preflight 由測試證實，429 由同一機制推得、未另測）：每個回應帶 `X-Request-ID`，並寫入日誌 MDC。呼叫端帶入的值只在 `^[A-Za-z0-9._-]{1,64}$` 時沿用，否則重新產生 UUID——**值會被印進日誌，不驗證的話呼叫端可帶換行字元偽造日誌行**。
- `ApiResponse.error(...)` 兩個工廠方法自動帶入 `requestId`（我在後端 `src/main` 找到的手寫錯誤回應——Security 401／403、兩個限流過濾器的 429、`TenantContextFilter` 的 400、`GlobalExceptionHandler`、Controller——都經過這兩個方法；Spring 預設的 `/error` 回應不經過，它有標頭但內容沒有 `requestId`）；成功回應**不帶**，既有 payload 契約不變。
- CORS `exposedHeaders` 加入 `X-Request-ID`（否則跨源時前端讀不到）。
- 日誌格式改為 `%5p [%X{requestId:-}]`：使用者回報的 ID 可直接 grep 到該請求的全部日誌（已用真實 Tomcat 驗證日誌行確實帶到）。

## 5. 測試、突變驗證與驗證結果

**新增／擴充的測試**

| 測試 | 數量 | 守住什麼 |
|---|---|---|
| `RequestIdFilterTest`（單元） | 12 | 產生 UUID、合法值沿用、6 種不安全值被丟棄、64／65 字元邊界、MDC 設定與清除（含下游拋例外） |
| `ApiResponseRequestIdTest`（單元） | 3 | 兩個 error 工廠都帶、success 不帶、請求外為 null |
| `RequestIdIntegrationTest`（整合，完整過濾鏈） | 7 | 401／403／400 的**標頭與內容 requestId 相同**、200 有標頭、每請求不同、帶入值處理、CORS 暴露 |
| `SecurityHeadersIntegrationTest` | 6→9 | 新增：代理後方 HSTS、多層代理、純 http 不送 |
| `e2e/at-security-headers.spec.ts` | 3→8 | 嚴格 CSP 指令、nonce 每次不同且每個 `<script>` 都帶、**真實瀏覽器零 CSP 違規且主題 script 確實執行**、**注入的 `onerror` 被瀏覽器擋下**、HSTS 條件 |

**突變驗證（每項都先確認會紅，再還原並 `cmp` 確認一致）**
- 後端過濾器：移除輸入驗證＋MDC 清除 → 12 個中 **9 紅**。
- 後端 A 輪（移除 HSTS 採信代理、`ApiResponse` 的 requestId、CORS 暴露）→ 16 個中 **7 紅**（先前一次突變因我改壞語法變成編譯錯誤，已修正後重跑）。
- 後端 B 輪（過濾器順序改到 Security 之後）→ 7 個中 **4 紅**（被 Security 擋下的 401 與 preflight 失去標頭，證明順序被守住）。
- 前端 A（刪除 `proxy.ts`）→ 04／05／07 紅，**06 維持綠**（06 守的是「沒有誤擋」，符合預期）；B（`ThemeScript` 不帶 nonce）→ 05／06 紅；C（HSTS 拿掉條件）→ 08 紅。

**驗證結果**
- 後端 `mvn -o clean verify`（真實 postgres／redis）：**1731 單元（+15）＋530 整合（+10），0 failed／0 errors／0 skipped**，checkstyle（main+test）0 違規，8:59。
- 前端：`tsc --noEmit`、eslint 通過；`npm run build` 成功（84 動態路由、Proxy 被辨識）；production `next start` 用 curl 確認 CSP／nonce／HSTS 條件。
- **本機 E2E 守門** `make validate-e2e`：schema 無漂移；74 個列出，**70 passed／4 skipped／0 failed**。4 個 skipped，數量與上一輪相同；原始碼中的條件式 `test.skip()` 在 `at-m17-003`／`004`，但我**沒有逐一核對被跳過的是哪 4 個**，也沒有檢視它們為何被跳過。既有 65 個流程（含聊天、購物車、結帳頁、開店）都在嚴格 CSP 下通過。
- **真實 Tomcat**（`spring-boot:run` + curl）：401 的標頭與內容 `requestId` 一致；帶入 `trace-real-tomcat-1` 被沿用；含空白的帶入值被替換成 UUID；`X-Forwarded-Proto: https` 的 http 請求帶 HSTS；跨源請求暴露 `X-Request-ID`；**日誌行 `INFO [trace-real-tomcat-1] …` 確實帶到 ID**。
- **緊急開關**：production 以 `CSP_REPORT_ONLY=1` 啟動，標頭變成 `content-security-policy-report-only`；此模式下依賴「真的攔截」的 04、07 如預期變紅（不再攔截）。環境變數是啟動時才讀，build 時沒設。

## 6. 範圍外（延後）與誠實揭露

- **PRD 的錯誤封包形狀仍與實作不同**：PRD §16.4.1 是 `{error:{code,message,details,requestId}}`，實作的 `ApiResponse` 是 `{success,code,message,errors,timestamp,requestId}`——`requestId` 在**頂層**而非 `error.requestId`。我只做了加法（新增選填欄位），沒有重構封包，因為那會讓前端每個錯誤處理都要改。**PRD 文字未動**。若要完全對齊 PRD，是另一個獨立且較大的決策。
- **前端尚未顯示 `X-Request-ID`**（錯誤畫面沒有「請提供此代碼給客服」）；標頭已能被前端讀到，UI 是產品決定。
- **HSTS 依賴部署端的代理確實送 `X-Forwarded-Proto`**——我無法驗證真實部署。若沒送，維持先前狀態（不送 HSTS）。
- **CSP 只在 Chromium 驗證**（Playwright）。Safari／Firefox 沒測；為 Safari 對 `ws` 與 `http` 來源不互相比對的行為，`connect-src` 刻意列出明確的 `ws(s)://` 來源，但這是防禦性設計，未實測。
- **CSP 涵蓋範圍**：既有 E2E 走到的頁面與流程、外加新 spec 的 3 個頁面。管理後台、ERP 等深層頁面並非每個都有 E2E。Stripe 結帳與 OAuth 是整頁跳轉（`window.location.href`），依 CSP 設計不受影響，但**沒有對真實 Stripe／OAuth 供應商測過**。
- **效能未量測**：84 個路由全部改為每次請求動態渲染。
- `style-src 'unsafe-inline'` 仍在；`img-src https:` 是刻意寬鬆（使用者提供任意 https 圖片網址）。
- 日誌格式改變（多了 `[requestId]`）；未發現任何依賴日誌格式的測試或工具，但外部的日誌解析若存在會受影響。
- **DEF-279**（已登入者請求不存在路徑回 500）本輪未處理——使用者沒有提到，維持待處理。

## 7. 下一步 / Action Items

1. **部署確認**：請向維運確認前方代理有送 `X-Forwarded-Proto`；並在 staging 先看一次瀏覽器主控台有無 CSP 違規（有問題可先設 `CSP_REPORT_ONLY=1`）。
2. **DEF-279**：未知路徑 500→404，改動小，可直接排入下一輪。
3. **產品決策（不急）**：錯誤畫面是否顯示 `X-Request-ID`；PRD 錯誤封包形狀要不要與實作對齊。
