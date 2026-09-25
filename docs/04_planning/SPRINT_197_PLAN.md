# Sprint 197 Plan — SecurityConfig 安全標頭逐項實測與補強（DEF-278）

**Sprint**: Sprint 197
**日期**: 2026-09-25

## 1. 起點

Sprint 192、193、196 的收尾都把「`SecurityConfig` 沒有顯式 `headers()` 設定（HSTS／CSP／X-Frame-Options 沿用 Spring Security 預設，未逐項核對）」列為未查證的鄰近角度。使用者貼上 Sprint 196 的總結（結尾問「要繼續做安全標頭，還是指定別的方向」），並回覆「請繼續完成任務！」。**我把「繼續」理解為做該次選方向，這是我的推論，使用者沒有逐字確認。**

## 2. 方法：先讀規格，再實測，不憑「預設應該有」

**規格**（讀原文，非記憶）：
- PRD v1.0 §16.4.1「Standard Headers（所有 Error Response 皆包含）」：`X-Request-ID`、`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Content-Type`。
- SRD 威脅表 XSS 列：「輸出編碼 + CSP Header」。

**實測**：
- 後端：探針測試以 MockMvc 走完整 Spring Security 過濾鏈，印出 10 種請求的實際回應標頭（200、400、401、403、未知路徑、CORS preflight、SockJS、HTTPS、代理後方 `X-Forwarded-Proto: https`）。
- 前端：`next dev` 起服務後以 curl 取得頁面標頭，修改前後各一次。

## 3. 實測發現

| 面向 | 實測結果 | 處置 |
|---|---|---|
| 後端 `nosniff`／`X-Frame-Options: DENY`／`Cache-Control: no-store` | Spring Security 預設值，在 200／400／401／403／500／preflight／SockJS info 全部都有 → PRD §16.4.1 這兩項**已滿足，非缺陷** | 不動；改以測試守住 |
| 後端 CSP、`Referrer-Policy` | **完全沒有**（SRD 要求 CSP） | 補上（§4） |
| 後端 HSTS | 只在 `request.isSecure()` 時送；**代理後方**（`X-Forwarded-Proto: https` 的 http 請求）不送——`server.forward-headers-strategy` 未設定 | **未動**：repo 內沒有 TLS 終止層（compose 直接對外開 :8080／:3000），是否加取決於未記錄的部署拓撲，見 §7 |
| **前端頁面**（登入、結帳等） | **沒有任何安全標頭**，且回 `X-Powered-By: Next.js` → 任何網站都能以 iframe 嵌入登入頁（點擊劫持） | 修復（§4） |
| `X-Request-ID`（PRD §16.4.1） | 後端 `src/main` 無任何實作（grep 確認），`ApiResponse.error(code, message)` 也沒有 PRD 規定的 `error.requestId` | **不實作**，登記 DEF-280 待決策 |
| 探針意外發現：已登入使用者請求**不存在的路徑** | 回 **500**（非 404），日誌每次寫一筆 ERROR 加完整堆疊 | 與標頭無關，**不夾帶修復**，登記 DEF-279 |

## 4. 修復

**後端**（`SecurityConfig.filterChain` 新增 `.headers(...)`，7 行）：
- `Content-Security-Policy: default-src 'none'; frame-ancestors 'none'`——本服務只回 JSON、無 Swagger／HTML 頁（pom 與 yml 確認），不會擋到功能。
- `Referrer-Policy: no-referrer`。
- 不重複設定 `nosniff`／`X-Frame-Options`（已是預設，重複是 speculative 內容）；由測試守住。

**前端**（`next.config.ts`）：`X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: strict-origin-when-cross-origin`（source `/:path*`），並 `poweredByHeader: false`。只放**已查證不影響功能**者：`src` 內無 `<iframe>`／`frame-src`、無 `navigator.geolocation`／`mediaDevices` 等瀏覽器功能 API、無第三方 `<Script>`；OAuth 為整頁跳轉（`services/oauth.ts`）不受影響。

**刻意不做**：
- 前端 `script-src` 型 CSP：Next 16 文件指出需 nonce，而 nonce **強制所有頁面動態渲染**（放棄靜態最佳化），是架構取捨，需使用者決定。
- `Permissions-Policy`：查證後專案沒用到那些功能、也沒有第三方嵌入，邊際價值低；未來若要用 geolocation 會被無聲擋掉，弊大於利。
- HSTS、`X-Request-ID`：見 §3。

## 5. 測試與突變驗證

- `SecurityHeadersIntegrationTest`（6）：401（authenticationEntryPoint 產生）、403（accessDeniedHandler 產生）、400（GlobalExceptionHandler 產生）、200、CORS preflight 皆斷言 `nosniff`／`DENY`／CSP／`Referrer-Policy`；HTTPS 請求斷言 HSTS。刻意**不斷言**代理後方的 HSTS 行為——鎖死它等於替未決的基礎設施決策背書。
- `frontend/e2e/at-security-headers.spec.ts`（3）：登入頁、404 頁帶標頭，且無 `X-Powered-By`（Playwright `request` 直接打 HTTP，不開瀏覽器、不依賴 seed 資料）。
- **突變驗證**：後端移除 `.headers(...)` 區塊（等同修復前）→ 6 個中 **5 個紅燈**（HSTS 那個是 Spring 預設，維持綠燈，符合預期）；前端還原成原始 `next.config.ts` → **3/3 紅燈**。皆還原並確認與備份一致。

## 6. 驗證結果

**後端全量**（預設時區）`mvn -o clean verify`（真實 postgres/redis）：**1716 個單元測試（持平）+ 520 個整合測試（+6），0 failed / 0 errors / 0 skipped**，checkstyle（main+test）0 違規，`BUILD SUCCESS`，總耗時 9:13。未做跨時區重跑：本輪無任何時間邏輯，沒有理由預期時區影響結果（這是判斷，不是實測）。

**後端真實 Tomcat 交叉驗證**（`spring-boot:run` + curl，非 MockMvc）：`/api/v2/auth/me`（401）、`/api/actuator/health`（200）、`/api/ws/info`（200）皆帶 `nosniff`／`X-Frame-Options: DENY`／CSP／`Referrer-Policy: no-referrer`／`Cache-Control: no-store`；`X-Forwarded-Proto: https` 的 http 請求**沒有** HSTS，證實 MockMvc 探針的結論。啟動前三次失敗，皆為本機環境問題而非程式：缺 `JWT_SECRET`（啟動防護正確擋下，改用一次性隨機值）、主設定指向區網 `192.168.1.133` 連不上、測試 DB 由 Hibernate `ddl-auto` 建立而與 Flyway 歷史衝突（改以環境變數指向本機測試 DB 並關閉 Flyway）。結束後已關閉該行程，8080 無殘留。

**前端**：`tsc --noEmit` 與 `eslint next.config.ts` 通過；`npm run build` 成功；**production** `next start` 以 curl 驗證 `/login`（200）、`/_next/static/nonexistent.js`（404）、`/no-such-page`（404）皆帶三個標頭；`at-security-headers.spec.ts` 對 production server 3/3 通過。

**本機 E2E 守門** `make validate-e2e`（乾淨 DB → Flyway 重建 → host 全棧、`ddl-auto=validate` → Playwright）：schema 對齊無漂移；列出 69 個，**65 passed、4 skipped、0 failed**，log 中無 flaky／retry 字樣；含 `at-m10-chat`（聊天 STOMP happy path）與 `at-security-headers` 的 3 個。4 個 skipped 是既有 `at-m17-003`／`at-m17-004` 的條件式 `test.skip()`，非本輪引入，我沒有檢視它們為何被跳過。

**commit／push**：pre-commit（checkstyle、編譯、核心測試、ESLint、tsc）通過，commit `8dbf5d5`；pre-push 輕量守門（Backend Unit Tests、Frontend Lint & Build、schema-gate 三段皆出現在輸出中）整體通過，push `d404f0b..8dbf5d5`；**雲端 CI**（push 觸發的 `act-compat.yml`）run 36087142091 **三個 job 全綠**：Backend Unit Tests 2m54s／Backend Integration Tests & Package 5m32s／Frontend Lint & Build 1m05s。

**更正（過程揭露）**：本文件初稿曾寫「聊天 E2E 會在 push 後的雲端 CI 跑」，這是錯的——push 觸發的雲端 CI **不含 Playwright E2E**，E2E 只有本機 `make validate-e2e` 與手動觸發的 `ci.yml`（`Makefile` 中 `validate-push` 的註解「E2E 交給雲端 push 觸發」與現況不符，此為既有的文件落差，本輪未動）。我查證後改為實際跑本機 E2E 守門，結果如上。

## 7. 誠實揭露

- **後端正式的回歸測試是 MockMvc；真實 Tomcat 只交叉驗證了 3 個端點**（401／200／200）。403、400、CORS preflight 只有 MockMvc 的證據——兩者走同一條過濾鏈，但不完全等價。
- **前端「修改前」的基準是 `next dev` 的實測**（登入頁只有 `Cache-Control` 與 `X-Powered-By`）；修改後另以 production 建置驗證（§6），但「修改前」沒有對 production 再測一次。
- **SockJS iframe 後備傳輸未驗證**：探針對 `/ws/iframe.html` 回 404（我沒有追查原因）。推論是 iframe 傳輸原本就被既有的 `X-Frame-Options: DENY` 擋下，新增的 `default-src 'none'` 不會讓它更糟；此為推論，沒有實測。聊天實際走 SockJS 的哪一種傳輸我沒有確認；但本機 E2E 守門中 `at-m10-chat`（聊天 STOMP happy path）通過（§6），代表新標頭沒有破壞聊天連線。
- **HSTS 在代理後方不送**是實測事實，但是否構成問題取決於 TLS 在哪終止（repo 內沒有），需要使用者告知部署方式才能決定要設 `server.forward-headers-strategy` 還是在邊緣層加。
- **前端仍沒有限制 script 來源的 CSP**，XSS 縱深防禦這一層是缺的；SRD 寫的「CSP Header」對前端頁面尚未完全落實。
- `X-Request-ID` 與 PRD 的 `error.requestId` 都沒有實作；PRD 的錯誤封包（`{error:{code,message,details,requestId}}`）與實作的 `ApiResponse` 形狀本身就不同，這比單一標頭大，需決策。
- 工作樹另有一個與本輪無關的舊 stash（Sprint 17，`On develop`），我沒有動它。

## 8. 使用者決策與下一步 / Action Items

> **後續（Sprint 198）**：下列第 1～3 項已由使用者回覆處理（HSTS、前端 CSP 授權我依「最佳化理想化」判斷；DEF-280 要求實作），結果見 [SPRINT_198_PLAN.md](SPRINT_198_PLAN.md)。第 4 項（DEF-279）仍待處理。以下維持當時的原文。

**使用者決策**：本輪沒有向使用者提問（沒有 AskUserQuestion）。下列是我自行做的判斷，使用者可推翻：
1. 把「請繼續完成任務」解讀為 Sprint 196 建議的安全標頭方向（見 §1）。
2. 範圍從 `SecurityConfig` 延伸到前端 `next.config.ts`——實測發現前端才是缺口最大處，且已查證改動不影響功能（§4）。
3. 不做：前端 `script-src` CSP、`Permissions-Policy`、HSTS、`X-Request-ID`（理由見 §3、§4）。
4. 不夾帶修復 DEF-279（未知路徑回 500）——與標頭無關，登記後由使用者排序。

**下一步 / Action Items**（需使用者決策者在前）：
1. **HSTS**：請告知 TLS 在哪終止（邊緣代理／雲端負載平衡／其他）。若在應用前方的代理，需設 `server.forward-headers-strategy` 或在邊緣層加 HSTS；不知道拓撲前我不會動。
2. **前端 `script-src` CSP**：選項是 nonce（強制所有頁面動態渲染）、或先以 `Content-Security-Policy-Report-Only` 觀察，或暫不做。
3. **DEF-280**：錯誤封包契約（`X-Request-ID`／`error.requestId`）要讓實作向 PRD 靠攏，還是修 PRD 反映現況。
4. **DEF-279**：未知路徑 500→404，改動小，可直接排入下一輪。
5. 本輪已 commit／push，雲端 CI 全綠；`RELEASE_TRACKER.md` 狀態欄已於同日回填。
