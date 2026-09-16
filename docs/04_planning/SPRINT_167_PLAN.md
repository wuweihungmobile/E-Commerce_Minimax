# Sprint 167 Plan — `AuthService.refreshToken()` 未做 rotation，同一 refresh token 可在效期內被重複使用（DEF-219）

## 1. 起點

`DEFERRED_ITEMS_TRACKER.md` 活躍延後項目已無待排程項目（Sprint 166 的 `DEF-217`/`DEF-218` 已修復）。Sprint 166 §6 誠實揭露的唯一線索（既有 24+3 處手動解析 try/catch 的 fallback 值是否合理）範圍過於發散，本輪改自選新角度：對認證流程（`AuthService`/`RefreshTokenService`/`JwtTokenService`）做防禦性掃描。

過程中先排除多個假警報後（檔案上傳大小/MIME 驗證、日誌是否外洩敏感資料、購物車數量驗證、CORS 設定、JWT 演算法/簽章驗證、STOMP CONNECT/SUBSCRIBE 授權），在 `AuthService.refreshToken()` 找到一個真實缺口。

## 2. 缺口說明

`RefreshTokenService` 原本是「白名單」設計：`storeRefreshToken` 在 Redis 寫入 `valid` 狀態（TTL 30 天），`isRefreshTokenValid` 檢查 key 是否存在。但 `AuthService.refreshToken()` 每次驗證通過後呼叫 `generateAuthResponse()` 換發新的 access/refresh token 組合時，只會呼叫 `storeRefreshToken` 儲存新 token，**從未對本次用來換發的舊 token 呼叫任何失效機制**。

後果：同一個 refresh token 在其 7 天效期（`application.yml: jwt.refresh-token-expiration=604800000`）內可被重複使用無限次換發新的 access token，且新舊 token 會同時並存有效。若 refresh token 外洩（裝置遭入侵、日誌外洩、XSS），攻擊者可與合法使用者同時並行持續取得存取權，直到 7 天到期或使用者手動登出；系統對此重放行為沒有任何偵測機制。

這與已修復的 `DEF-185`（前端 21 處「登出」按鈕從未呼叫後端登出端點）是不同問題——即使登出機制完全正常運作，本缺口依然存在，因為問題發生在 refresh 流程本身而非 logout 流程。

**使用者決策**：透過 `AskUserQuestion` 呈現三個選項（Rotation + 重放偵測 / 僅 Rotation / 本輪不修僅記錄），使用者選擇「Rotation + 重放偵測（推薦）」——符合 OWASP 對 refresh token 的建議做法。

## 3. 修復內容

### 3.1 `RefreshTokenService`：狀態機從「存在=有效」改為 `valid`/`used` 兩態

新增常數 `STATUS_VALID`/`STATUS_USED`。

- `isRefreshTokenValid`：改為檢查值是否等於 `valid`（原本只檢查 key 是否存在）。
- 新增 `isRefreshTokenReused(userId, token)`：值等於 `used` 時回傳 `true`（重放訊號）。
- 新增 `rotateRefreshToken(userId, token)`：將舊 token 的值改寫為 `used`（保留 key 而非刪除，這樣之後若再被使用可被 `isRefreshTokenReused` 偵測到）。
- `blacklistRefreshToken`（logout 用）維持原行為直接 `delete` key——手動登出的 token 之後再被使用時會落在「key 不存在」分支（等同既有的「已撤銷」錯誤），不需要重放偵測升級為全域撤銷，語意上與「被竊後又被拿來重放」不同。

### 3.2 `AuthService.refreshToken()`：加入重放偵測 + rotation

在既有的 `isRefreshTokenValid` 檢查之前新增：

```java
if (refreshTokenService.isRefreshTokenReused(userId, refreshToken)) {
    refreshTokenService.blacklistAllRefreshTokens(userId);
    log.warn("Refresh token reuse detected for user: {}, all sessions revoked", userId);
    throw new BusinessException(ErrorCode.E_1003, "Refresh token reuse detected; all sessions revoked");
}
```

`isRefreshTokenValid` 檢查通過、`resolveTenantForUser` 之後、`generateAuthResponse` 之前新增：

```java
refreshTokenService.rotateRefreshToken(userId, refreshToken);
```

即：驗證通過 → 立即讓舊 token 失效（rotation）→ 才換發新 token。若舊 token 已經失效過（`used`）又再次出現，視為外洩訊號，撤銷該使用者名下所有 refresh token，強制全裝置重新登入。

### 3.3 前端 `axios.ts`：補上單一飛行（single-flight）refresh，避免併發 401 誤觸重放偵測

**這是本輪查證中額外發現、且必須一併修復的風險**：後端加入 rotation 後，若前端有多個 API 請求「同時」收到 401（例如一個頁面平行呼叫多個 widget API，恰好在 access token 過期那一刻全部失敗），既有的 `frontend/src/lib/axios.ts` 攔截器**沒有任何併發保護**——每個 401 都獨立讀取 `localStorage` 裡同一個 refresh token 並各自呼叫 `POST /v2/auth/refresh`。第一個請求成功後舊 token 被 rotate 成 `used`，第二個（甚至第三、第四個）請求緊接著用同一個已經變成 `used` 的舊 token 送出，會被新加入的重放偵測誤判為攻擊，導致合法使用者被強制登出——這在正常使用情境下（而非攻擊情境）就會頻繁發生，是一個會被本輪後端修復直接引爆的既有前端架構缺口。

修法：新增模組層級的共用 `refreshPromise`，同一批 401 只觸發一次真正的 `/v2/auth/refresh` 呼叫，其餘並發的 401 等待並共用同一個 Promise 的結果，而不是各自發送重複請求。

## 4. 測試

- 紅燈先行（`git stash push -- backend/.../AuthService.java` 隔離修復本體，保留新增測試）：`AuthServiceTest$RefreshToken` 新增的兩案例對修復前程式碼皆如預期失敗——
  - `refreshToken_validToken_rotatesOldRefreshToken`：`Wanted but not invoked: refreshTokenService.rotateRefreshToken(...)`。
  - `refreshToken_reusedToken_revokesAllSessionsAndThrows`：`Wanted but not invoked: refreshTokenService.blacklistAllRefreshTokens(...)`。
- `git stash pop` 還原修復後兩案例皆綠。
- 新增 `RefreshTokenServiceTest.java`（此類別先前完全零測試覆蓋，比照 `IdempotencyServiceTest` 的 `RedisTemplate`/`ValueOperations` mock 手法）9 案例，涵蓋 `storeRefreshToken`/`isRefreshTokenValid`（valid/used/absent 三態）/`isRefreshTokenReused`（三態）/`rotateRefreshToken`/`blacklistRefreshToken`。
- 前端：`frontend/src/lib/axios.ts` 無既有單元測試框架（`package.json` 的 `test` script 為 `"echo \"No unit tests configured\" && exit 0"`，全站僅 Playwright E2E），比照既有前端 timing/併發類修復的驗證慣例，以 `npx tsc --noEmit` + `eslint` 靜態驗證正確性，並確認既有 E2E 套件（無任何案例會自然觸發 15 分鐘 access token 過期或 refresh 流程）不受影響。

## 5. 驗證結果

- 紅燈階段：見 §4，兩案例確實先失敗再轉綠。
- `mvn -o -q test -Dtest=AuthServiceTest,AuthServiceRefreshTokenTest,RefreshTokenServiceTest`：全數綠燈（含既有的 `AuthServiceRefreshTokenTest`，此為 Sprint 66 遺留的另一個獨立 refreshToken 測試檔案，新增的重放/rotation 邏輯對其既有案例無破壞性影響，因其未曾對 `isRefreshTokenReused`/`rotateRefreshToken` 做否定性驗證）。
- `checkstyle:check`（main+test）：0 違規。
- `mvn -o verify`（真實 postgres/redis，`make test-db-up`）：**1388 個單元測試（+11：AuthServiceTest 2 + RefreshTokenServiceTest 9）+ 480 個整合測試（持平），0 failed**，`BUILD SUCCESS`。
- 前端：`npx tsc --noEmit` 0 error；`npx eslint src/lib/axios.ts` 0 warning/error。
- `make validate-e2e`（乾淨 DB + host 全棧 + Playwright，複製雲端 e2e job）：**62 passed / 4 skipped / 0 failed**（3.2m），與既有基準一致，schema 對齊無漂移。既有套件沒有任何案例會自然觸發 15 分鐘 access token 過期或 `/v2/auth/refresh` 呼叫，故本輪修法未被既有 E2E 直接行使，但確認未造成任何回歸。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-219`（已修復）：`AuthService.refreshToken()` 缺 rotation + 重放偵測，同一 refresh token 可在 7 天效期內被重複使用；連帶修復前端 `axios.ts` 併發 401 單一飛行缺口（後端修復的必要前提，否則會誤傷正常使用者）。

---

## 7. 誠實揭露總結

- **已知且刻意接受的殘留風險（跨分頁）**：本輪的單一飛行修法只解決「同一個分頁內」多個並發請求共用同一次 refresh 呼叫的問題。若使用者開啟**多個瀏覽器分頁**，兩個分頁各自的 JS 執行環境擁有各自獨立的 `refreshPromise` 模組狀態（不共用記憶體），仍可能出現分頁 A 與分頁 B 幾乎同時各自讀到 localStorage 裡同一個舊 refresh token、各自發起 refresh 請求的競態，導致其中一個分頁的請求被判定為重放而觸發全域撤銷。這是業界已知的 rotation 常見代價（OWASP 也承認此取捨），真正根除需要跨分頁協調機制（如 `BroadcastChannel`/`localStorage` 鎖），評估後認為超出本輪合理範圍（單一分頁內的併發已是最常見、最容易誤傷使用者的情境，已修復；跨分頁同時 401 的時間窗遠比同分頁多個 API 請求窄很多），記錄於此供未來若使用者回報「偶爾被強制登出」時的優先排查方向。
- 未新增後端整合測試模擬「兩個並發 HTTP 請求打同一個 refresh token」的真實網路競態（`MockMvc`/`TestRestTemplate` 難以可靠製造微秒級競態且會引入 flaky 風險），本輪僅以單元測試驗證 `AuthService`/`RefreshTokenService` 的邏輯正確性（給定「已使用」狀態必定觸發重放偵測），前端修法本身也未寫自動化測試（無單元測試框架、且真實網路時序無法在現有 Playwright E2E 中穩定重現），依賴程式碼邏輯推理（`refreshPromise` 在 JS 單執行緒模型下的同步賦值時機）而非執行時實測驗證此併發修法。
- `RefreshTokenService.DEFAULT_TTL`（30 天）長於 `jwt.refresh-token-expiration`（7 天）的既有落差本輪未一併修正——因為 `refreshToken()` 本身已透過 `jwtTokenService.isTokenExpired()` 檢查 JWT 自身的 7 天到期宣告，Redis key 多存活的 23 天不會被實際採信為有效，純屬 Redis 記憶體的次要浪費，非本輪缺口的成因，維持現狀不擴大修復範圍。
