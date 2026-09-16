# Sprint 168 Plan — `/v2/auth/login` 零節流機制，可無限次密碼嘗試（DEF-220）

**Sprint**: Sprint 168
**日期**: 2026-09-17

## 1. 起點

`DEFERRED_ITEMS_TRACKER.md` 活躍延後項目已無待排程項目（Sprint 167 的 `DEF-219` 已修復）。本輪改自選新角度：對認證流程做防禦性掃描時，注意到 `RateLimitFilter.shouldNotFilter` 明文排除整個 `/v2/auth/**`（Sprint 93 建立每租戶限流機制時的工程範圍決策，理由是「登入前無租戶身分可綁定」），順著這條線查證 `AuthService.login()` 本身是否有其他節流/鎖定機制把關。

## 2. 缺口說明

- `RateLimitFilter`（Sprint 93）刻意排除 `/v2/auth/**`：`shouldNotFilter` 對此路徑一律回傳 `true`，且從未評估過排除後的安全後果。
- `AuthService.login()`（`backend/.../core/auth/AuthService.java`）本身沒有任何帳號鎖定或嘗試次數限制：密碼比對失敗只拋 `BusinessException(E_1001)`，不記錄、不累計、不限制。
- 兩者疊加結果：`POST /v2/auth/login` 對任一已知 email 完全零延遲、零阻擋，可被無限次自動化嘗試——典型的 brute force / credential stuffing 攻擊面，且系統沒有任何偵測或減速機制。

## 3. 修法決策

經 `AskUserQuestion` 徵詢使用者，在三個選項（IP+帳號雙層防護／僅 IP 節流／僅帳號鎖定）中選擇**「IP + 帳號雙層防護（Recommended）」**：per-IP 節流先擋掉高速自動化攻擊，per-account 暫時鎖定則防止攻擊者跨多個 IP 輪流對同一帳號緩慢嘗試，兩者互為必要的雙層防護（單一層都無法同時涵蓋兩種攻擊模式）。

## 4. 修復內容

### 4.1 新增 `LoginAttemptService`（per-account 暫時鎖定）

新檔 `backend/.../infrastructure/security/LoginAttemptService.java`：以 `StringRedisTemplate`（原生 `INCR`，非既有 `RedisTemplate<String, Object>`——後者的 `GenericJackson2JsonRedisSerializer` 帶 default typing 會把整數包成 JSON 字串，與 Redis 原生 `INCR` 指令不相容）追蹤每個 email 的連續登入失敗次數：達 5 次即在 15 分鐘窗口內鎖定該帳號，登入成功後重設計數。

### 4.2 `AuthService.login()`：整合鎖定檢查

- 密碼比對**之前**先查 `loginAttemptService.isLocked(email)`，命中直接拋 `BusinessException(E_1004)`（既有錯誤碼，原訊息模板「帳號已被鎖定」語意完全吻合，不新增錯誤碼），不執行 bcrypt 比對，避免鎖定機制本身可被繞過或被用來做計時側信道推斷。
- 帳號不存在／密碼錯誤兩種失敗情境皆呼叫 `recordFailedAttempt(email)`——刻意讓不存在的 email 也計入，避免攻擊者用「打亂大小寫/不存在帳號不計次」的方式繞過鎖定計數，也避免兩種失敗情境的處理時序產生可觀測差異洩漏帳號是否存在。
- 登入成功呼叫 `resetAttempts(email)`。

### 4.3 新增 `LoginRateLimitFilter`（per-IP 節流）

新檔 `backend/.../api/filter/LoginRateLimitFilter.java`：Token Bucket + 單支 Lua script，演算法與既有 `RateLimitFilter` 完全比照（同樣的原子讀取-補充-扣除、Redis 故障 fail-open），但獨立成新檔而非重構 `RateLimitFilter`（後者運作正常且已有完整測試覆蓋，依 Rule 3「不重構沒有壞的東西」不做非必要改動）。只套用於 `POST /v2/auth/login`，以 `request.getRemoteAddr()` 為 key（本專案 `docker-compose.yml` 無 nginx/traefik 等反向代理終止連線，故不信任 `X-Forwarded-For`——這是 client 可自訂的 header，信任它等於讓攻擊者每次換一個偽造值就繞過節流）。`SecurityConfig` 新增 `.addFilterAfter(loginRateLimitFilter, RateLimitFilter.class)` 掛載。

**容量調整（實測驅動的修正）**：初版容量設為 10 次/分鐘，`make validate-e2e` 實測揭露 8 個既有 spec 因此被誤擋（`accessToken` 取得為 `null`）——根因是 Playwright 4 workers 平行對同一 host（同一來源 IP）發出約 50 次 `registerAndLogin`/`loginOnly` 呼叫，遠超 10/分鐘的初版容量，也精準反映了「同一來源 IP 短時間內有多位合法使用者同時登入」的真實情境（公司 NAT、校園/公用 Wi-Fi、行動網路 CGNAT）。調整為 **30 次/分鐘**：仍遠低於真正自動化 brute force 的量級（每秒數十次），同時足以涵蓋前述合法併發情境；真正防止「鎖定單一帳號」的是 §4.2 的 per-account 機制，此 IP 節流只負責擋掉高速自動化流量，屬合理取捨而非放棄防護。

### 4.4 前端

無需改動。`ApiResponse.error(code, message)` 已內含 `message`，前端 `frontend/src/app/(auth)/login/page.tsx` 本就泛用讀取 `response.data.message` 顯示錯誤（不特判錯誤碼），`E_1004`／`E_9904` 的既有訊息模板（「帳號已被鎖定」／「已超過速率限制」）會直接呈現，無需額外處理。

## 5. 測試

- **紅燈先行**（`git stash push -- AuthService.java` 隔離修復本體，保留新增測試）：`AuthServiceTest$Login` 新增 4 案例對修復前程式碼皆失敗——鎖定案例斷言拋 `E_1004` 卻收到 `E_1001`，其餘三案例斷言 `recordFailedAttempt`/`resetAttempts` 被呼叫卻是 `Wanted but not invoked`。`git stash pop` 還原後全數轉綠。
- 新增 `LoginAttemptServiceTest.java`（7 案例）：首次失敗設定 TTL、後續失敗不重設 TTL、達門檻/超過門檻/未達門檻的 `isLocked` 三態、key 不存在、`resetAttempts` 刪除 key。
- 新增 `LoginRateLimitFilterTest.java`（8 案例，比照既有 `RateLimitFilterTest` 手法，Mockito 模擬 script 回傳值）：非登入路徑排除、非 POST 方法排除、允許/拒絕分支、Redis 故障與 script 回傳 null 的 fail-open、無 `StringRedisTemplate` bean 時放行。
- 新增 `LoginRateLimitFilterIntegrationTest.java`（2 案例，真 Redis，比照既有 `RateLimitFilterIntegrationTest`）：同一 IP 燒光 30 次配額後必然出現拒絕、不同 IP 配額互相獨立。
- **順帶修復兩處既有測試因新增建構子參數而中斷編譯**：`AuthServiceLoginTest.java`（另一支獨立的 `AuthService.login()` 單元測試檔案，`@InjectMocks` 缺 `LoginAttemptService` mock 導致 `NPE: this.loginAttemptService is null`）補上 mock；`SecurityConfigCorsTest.java`（直接 `new SecurityConfig(...)` 缺第 4 個建構子參數）補上 `null`。

## 6. 驗證結果

- 紅燈階段：見 §5，4 個新案例確實先失敗再轉綠。
- `mvn -o verify`（真實 postgres/redis，`make test-db-up`）：**1407 個單元測試（+19）+ 482 個整合測試（+2），0 failed**，`BUILD SUCCESS`。
- `checkstyle`（main+test）：0 違規。
- `make validate-e2e`（乾淨 DB + host 全棧 + Playwright，複製雲端 e2e job）：初版（容量 10/分鐘）**8 failed**（`AuthController`/`Admin`/`Feature Toggle`/`店鋪 Profile` 等多個既有 spec 因 `accessToken` 取不到而連鎖失敗）；調整容量為 30/分鐘後重跑 **62 passed / 4 skipped / 0 failed**，與既有基準一致。

## 7. 更新 `DEFERRED_ITEMS_TRACKER.md`

新增 `DEF-220`（已修復）：`/v2/auth/login` 缺 per-IP 節流與 per-account 鎖定，可無限次密碼嘗試；新增 `LoginAttemptService`（per-account 暫時鎖定）+ `LoginRateLimitFilter`（per-IP 節流），使用者經 `AskUserQuestion` 選擇雙層防護方案。

---

## 8. 誠實揭露總結

- **IP 節流容量的取捨是主動調整，非使用者原始核可的確切數字**：`AskUserQuestion` 徵詢時提出的方案描述僅寫「如 10 次/分鐘」作為方案說明的示例數字，並非使用者逐一核准的精確門檻；後續 `make validate-e2e` 實測證明 10 過緊會誤傷合法併發情境，調整為 30 是本輪工程判斷（詳見 §4.3 理由），不是重新詢問使用者的架構決策——比照 Sprint 165 對 `PageableUtils` 具體邊界值的處理方式。
- **未對 per-IP 節流與 per-account 鎖定的交互做真實網路併發測試**：`LoginRateLimitFilterIntegrationTest`/`LoginAttemptServiceTest` 各自獨立驗證兩層機制，但沒有整合測試模擬「多個真實併發 HTTP 請求同時命中兩層防護」的競態（同 Sprint 167 對 refresh token 重放偵測的既有取捨，`MockMvc`/`TestRestTemplate` 難以可靠製造微秒級競態且會引入 flaky 風險）。
- **`getRemoteAddr()` 在未來若導入反向代理／CDN 會需要重新設計**：目前假設請求直接抵達 Spring Boot（無 nginx/traefik 終止連線），若未來架構改變，`LoginRateLimitFilter` 需要改讀受信任代理層寫入的標頭並限定信任範圍，否則所有請求的來源 IP 會統一顯示為代理節點 IP，節流失去意義；此檔案的 Javadoc 已記錄此前提。
