# Sprint 191 Plan — 設定宣告與程式讀取不一致掃描（DEF-265）

**Sprint**: Sprint 191
**日期**: 2026-09-24

## 1. 起點

`DEFERRED_ITEMS_TRACKER.md` 在 Sprint 190 收尾時已無活躍待辦項目，本輪自選新的掃描角度。Sprint 130~190 已系統性涵蓋 IDOR、Mass Assignment、XSS／URL 白名單、輸入驗證、分頁、認證、金額竄改、狀態偽造、公開端點回傳範圍、背景排程等角度，尚未涵蓋的是「**設定檔宣告的值與程式實際讀取的值是否一致**」——設定檔裡的鍵看起來可設定，實際卻沒有任何程式碼讀取它，維運人員照設定檔設了值卻完全無效，且因為伺服器端不報錯，問題長期不會被察覺。

起點是探測性 grep：`SecurityConfig.corsConfigurationSource()` 硬編碼 `localhost:3000/8080`，而 `application.yml` 卻有 `app.cors.allowed-origins`。

## 2. 調查方法與結果

此類問題可機械判定，不靠人眼：寫一支腳本（不入庫，放 session scratchpad）扁平化 `application.yml` 全部鍵，與 `src/main` 內所有 `@Value`／`getProperty`／`@ConditionalOnProperty` 讀取點比對。全庫沒有任何 `@ConfigurationProperties`，設定一律經 `@Value` 讀取，比對前提成立。Spring 自身消化的前綴（`spring.*`／`server.*`／`management.*`／`logging.*`／`springdoc.*`）排除。

結果（自訂鍵共 19 個）：16 個有讀取點；3 個沒有：

| 鍵 | 判定 |
|---|---|
| `app.cors.allowed-origins` | **發現 🟡 `DEF-265`**（見 §2.1）。同一個鍵在 `application-test.yml`、`application-integration-test.yml` 也宣告了，三處都是死設定 |
| `app.host`、`app.port` | 零引用的殘留鍵（grep 範圍：yml/java/md/Makefile/sh/Dockerfile/env/ts/json，排除 node_modules、target、AISDLC 框架目錄）；實際埠由 `server.port` 決定。無害，**未更動**（Rule 3），僅記錄 |

反向也掃了一次——`src/main` 中硬編碼的環境位址（`localhost`、正式網域）：

- `app.frontend-base-url`（`PaymentStateService`、`TenantStripeConnectService` 讀取，預設 `http://localhost:3000`）：`application.yml` 沒有宣告，部署文件也未提及。但 Spring relaxed binding 讓環境變數 `APP_FRONTEND_BASE_URL` 可直接覆寫，機制是通的，且回跳網址由伺服器設定決定、不接受請求參數（沒有 open redirect 面）。**判定為缺文件而非缺陷，未更動**。
- `OAuthService` 的 Google/GitHub 端點 URL 為常數：是第三方固定位址，合理。`client_secret` 走 form body 而非 URL，`log.error(..., url, ex)` 不會把密鑰寫進日誌。**確認無缺陷**（探測日誌敏感資料時一併排除的偽陽性）。
- `AdminService` 系統設定回傳的 `support_url`／`terms_url`／`privacy_url` 硬編碼為 `nextkey.com`：屬展示用預設值，非本輪範圍。

### 2.1 `DEF-265`：`app.cors.allowed-origins` 是沒人讀的死設定，CORS 白名單被硬編碼

`SecurityConfig.corsConfigurationSource()` 以 `setAllowedOrigins(List.of("http://localhost:3000", "http://localhost:8080"))` 硬編碼白名單。

**為何有實際後果而非單純潔癖**：前端 `frontend/src/lib/api.ts` 的 `baseUrl` 取自 `NEXT_PUBLIC_API_URL`（`docker-compose.yml` 以 `${NEXT_PUBLIC_API_URL:-...}` 開放 build-time 覆寫），瀏覽器**直接跨源**呼叫後端；`next.config.ts` 的 `/api/:path*` rewrite 目的地硬編碼為 `ecommerce-backend-dev:8080`（開發容器），不是正式環境的同源代理。因此只要正式前端網域不是 localhost，所有瀏覽器 API 呼叫都會被 CORS 檢查封鎖；維運人員照 `application.yml` 的 `app.cors.allowed-origins` 設好正式網域也完全無效，因為沒有任何程式碼讀它。伺服器端不報錯（CORS 是瀏覽器端強制），故從未被察覺。

**嚴重度 🟡 MEDIUM**：失敗方向是「封鎖」（fail-closed），不是外洩，故不是資料安全缺口；是阻擋任何非 localhost 部署的設定完整性缺陷。

**已審查、刻意不更動的鄰近項目**：`WebSocketConfig` 的 `setAllowedOriginPatterns("*")` 與 CORS 白名單政策不一致。核對前端 `useChatSocket.ts`／`axios.ts`：token 存於 `localStorage`，STOMP 驗證靠 CONNECT frame 內的 token（Sprint 75 `DEF-035` 已審查 SUBSCRIBE 授權），沒有 cookie 等環境憑證，跨站頁面拿不到 token，故 `*` 來源**沒有跨站 WebSocket 劫持（CSWSH）可利用性**。收緊它屬另一個獨立的防禦縱深決策（有破壞 E2E／非瀏覽器客戶端的風險），不併入本輪最小修復。

## 3. 修復範圍與實作

- `SecurityConfig`：新增 `@Value("${app.cors.allowed-origins}") String corsAllowedOriginsRaw`（沿用 `OAuthService`「逗號分隔 + `@Value` 欄位 + `split/trim/filter`」的既有慣例）；`setAllowedOrigins` 改用解析後的清單。Java 端**刻意不設預設值**——鍵不存在時 context 載入直接失敗（大聲失敗），單一預設值來源是 `application.yml`。
- `application.yml`：`allowed-origins` 改為 `${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:8080}`（與 `OAUTH_ALLOWED_REDIRECT_ORIGINS` 同一模式，讓環境變數名稱在設定檔中可見）；預設值與修復前的硬編碼完全相同，**未設環境變數時行為零變化**。
- 未更動 `docker-compose*.yml`（`CLAUDE.md` Docker 管理限制：AI 不得自行判斷修改 Docker 配置）。環境變數可由維運端在部署時自行注入。
- 未更動 `application-test.yml`／`application-integration-test.yml` 中原本死掉的 `allowed-origins: http://localhost:3000`——它們現在會生效；已核對後端測試（`backend/src/test`）無任何一處送出 `Origin` 標頭，故不受影響；Playwright E2E 走真實瀏覽器，`Origin` 為 `http://localhost:3000`，仍在預設白名單內。

`allowCredentials` 維持 `true`。這使得「以環境變數設成 `*`」成為最危險的誤設定：Spring 在 `allowCredentials=true` 時對 `*` 來源拋 `IllegalArgumentException`（訊息含 `allowCredentials`），等於免費的大聲失敗；測試釘住此行為，避免日後有人為了「支援任意網域」而繞過它。

## 4. 測試

新增 `SecurityConfigCorsOriginsTest`（6 案例，直接驗證 Spring 判定來源用的 `CorsConfiguration.checkOrigin`）：

1. 設定的正式網域被允許。
2. 設定值不含 localhost 時 localhost 被拒——證明名單完全由設定決定，而非「硬編碼 ∪ 設定」。
3. 逗號分隔多個來源，容忍前後空白。
4. 未列入名單的來源仍被拒（避免修法退化成全開）。
5. 設定為空字串時不允許任何來源（失敗即關閉）。
6. `allowCredentials=true` 搭配 `*` 拋 `IllegalArgumentException` 且訊息含 `allowCredentials`。

既有 `SecurityConfigCorsTest`（DEF-072，標頭白名單）僅補一個 `@BeforeEach` 注入來源欄位，斷言不變。

**紅燈先行（修復前對未修改的正式程式碼實際執行）**：5 個案例失敗（`Could not find field 'corsAllowedOriginsRaw'`）。這是**結構性紅燈**而非行為性紅燈——測試以 `ReflectionTestUtils` 注入欄位，欄位不存在時無法走到行為斷言；行為缺陷本身（硬編碼名單拒絕正式網域）由 §2.1 的讀碼直接證實。

**紅燈階段抓到並修正的一個假綠燈**：第 6 個案例在紅燈階段「通過」了——`ReflectionTestUtils` 找不到欄位時拋的也是 `IllegalArgumentException`，與預期例外型別巧合相同。這是空洞通過，遂在修復前先加上 `.hasMessageContaining("allowCredentials")`，綠燈階段才確認它是因 Spring 真正的錯誤訊息而通過。

## 5. 驗證結果

`mvn -o clean verify`（真實 postgres/redis，`make test-db-up`）：**1648 個單元測試（+6）+ 490 個整合測試（持平），0 failed / 0 errors / 0 skipped**，checkstyle（main+test）0 違規，`BUILD SUCCESS`，總耗時 14:39。`SecurityConfig` 每個 `@SpringBootTest` 都會載入，490 個整合測試全數成功載入 context，即證明 `@Value("${app.cors.allowed-origins}")` 的鍵在各 profile 下皆解析得到（Java 端刻意無預設值，鍵缺失會直接使 context 載入失敗）。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-265`：✅ 已修復（Sprint 191）。

## 7. 誠實揭露總結

- 本輪未派背景 agent——掃描本身是「腳本比對 + 少量讀碼」，確定性工作交給程式碼（Rule 5）。
- 紅燈為結構性而非行為性，見 §4。
- 「正式環境跨源直連導致 CORS 全封鎖」的推論基於程式碼與設定檔（`api.ts`、`next.config.ts`、`docker-compose.yml`、`SecurityConfig`），**本輪沒有真的部署到非 localhost 網域驗證**——專案目前只有本地驗證環境。修復的正確性由單元測試 + 全量回歸保證，「維運端實際設定環境變數後瀏覽器可通」未經實機驗證。
- `app.host`／`app.port` 殘留鍵、`app.frontend-base-url` 缺文件、`WebSocketConfig` 的 `*` 來源：三項皆已查證並決定不更動，理由見 §2。
