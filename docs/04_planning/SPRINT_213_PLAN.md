# Sprint 213 Plan — Refresh Token 輪替的併發缺口（DEF-291）

**Sprint**: Sprint 213
**日期**: 2026-09-28

## 1. 起點

Sprint 212 收尾後，`DEFERRED_ITEMS_TRACKER.md` 沒有 AI 可獨立處理的活躍待辦（`myTodoList.md` 三項仍需使用者操作實際環境；其餘皆標「不排入排程」）。延續「自選掃描角度」的既有模式，使用者指示「請接續 212 繼續完成任務」。

## 2. 掃描角度與查證

角度：**密碼重設之後，舊 session 是否確實失效；重設連結的網域是否可被偽造**。

- 重設／驗證連結的網域來自設定 `app.frontend-base-url`，不取自請求標頭——無 Host header 注入風險。
- `resetPassword` 成功後呼叫 `refreshTokenService.blacklistAllRefreshTokens(userId)`，`AuthService.refreshToken` 隨後以 `isRefreshTokenValid` 拒絕已刪除的 key——撤銷有效（已簽發的 Access Token 無法撤銷，FRD BR-M03-003 已載明）。

沿著「撤銷靠 Redis 狀態」往下讀 `RefreshTokenService`，發現輪替本身的問題：

### 2.1 缺陷（DEF-291）：同一個 Refresh Token 併發換發可全部成功

`AuthService.refreshToken` 的順序是：`isRefreshTokenReused`（讀）→ `isRefreshTokenValid`（讀）→ `rotateRefreshToken`（寫「已使用」）→ 簽發並儲存新 token。檢查與標記是**兩個獨立的 Redis 指令**，兩個同時到達的請求都能在對方標記之前通過檢查，各自換發出一組新 Refresh Token。

DEF-219（Sprint 167）加的輪替＋重放偵測只擋「先後」兩次使用；被竊取的 token 與真正擁有者在同一瞬間使用，兩邊都拿得到新的有效 token，輪替機制形同虛設。Sprint 167 自己也記下「未新增後端整合測試模擬兩個並發 HTTP 請求打同一個 refresh token（`MockMvc`/`TestRestTemplate` 難以可靠製造微秒級競態）」——這條路徑從來沒有被驗證過。本輪改為**繞過 HTTP 層、直接對服務層施壓並連真實 Redis**，競態即可穩定重現。

**紅燈實測**（真實 Redis，生產序列化設定，未修改程式碼）：16 個執行緒同時以同一個 token 呼叫 `AuthService.refreshToken`——第 0 輪就是 **16 個全部成功、0 個失敗**。不是窄窗口的偶發，而是必然。（測試裡使用者查詢等步驟是 Mockito 替身、幾乎不耗時；真實環境該步驟是資料庫查詢，依此**推論**窗口只會更寬，但未在真實環境量測。）

## 3. 修復

- `RefreshTokenService`：以 Lua 腳本 `tryRotateRefreshToken(userId, token)` 取代 `rotateRefreshToken`——在 Redis 內「若值為有效則改為已使用並保留 30 天 TTL」，回傳是否由本次呼叫完成輪替。Redis 單執行緒保證只有一個呼叫者看得到「有效」。舊的非原子方法直接移除（不並存兩種矛盾寫法）。
- `AuthService.refreshToken`：輪替失敗（通過有效檢查後才輸掉競爭）→ 撤銷該使用者全部 Refresh Token 並回 `E-1003`，與既有「先後兩次使用」的重放處理一致。

**實作上的兩個坑**（都被測試守住）：

1. 生產環境的 `RedisTemplate` 用 JSON 序列化值，字串在 Redis 內是 `"valid"`（帶引號），腳本若寫死字面值 `'valid'` 永遠比對不到——所有 Refresh Token 都會換發失敗。腳本改為把 `valid`／`used` 當參數交給 `RedisTemplate` 的值序列化器處理，位元組與 `storeRefreshToken` 寫入的完全一致。
2. TTL 不能當參數傳（會被序列化成帶引號的字串，`EX` 不接受），改為把 `DEFAULT_TTL` 的秒數直接寫進腳本文字。

## 4. 測試

- `AuthServiceRefreshConcurrencyIntegrationTest`（新，真實 Redis，不啟動 Spring）：20 輪、每輪 16 個執行緒同時以同一個 token 換發，斷言**恰好 1 個成功、其餘一律 `E-1003`**（不可出現未分類的技術性例外）。
- `RefreshTokenServiceIntegrationTest`（新，真實 Redis，4 案例）：有效 token 輪替一次後變為「已使用」；第二次輪替失敗；**已撤銷（key 不存在）的 token 輪替失敗且不會被腳本重新建立成「已使用」**（否則之後再出現一次會被誤判為重放而撤銷全部 session）；輪替後保留 30 天 TTL。
- `RefreshTokenServiceTest`（改，單元）：釘住腳本回傳值 1／0／null 對應 true／false／false（回 null 時不可換發）。
- `AuthServiceTest`（改＋新增 1）：新增 `refreshToken_lostRotationRace_revokesAllSessionsAndThrows`；既有成功路徑測試補 stub。`AuthServiceRefreshTokenTest`（3 案例補 stub）、`IntegrationTestConfiguration`（mock 補 `tryRotateRefreshToken` 回 true）。
- **紅燈先行**：未修復程式碼上，併發測試 16/16 成功而失敗。
- **突變驗證**（3 種，皆被抓到，已還原並與備份逐位元組比對）：
  - M2 腳本改比對寫死字面值 `'valid'`：併發測試 0 成功、`validTokenRotatesOnce`／`secondRotationFails` 紅。
  - M4 條件改為「只要不是已使用就輪替」：僅「已撤銷不得復活」案例紅（隔離乾淨）。
  - M3 `AuthService` 忽略輪替結果：`lostRotationRace` 單元測試紅。

## 5. 驗證結果

- 新增的兩個真實 Redis 整合測試類別（共 5 案例）：0 fail。
- 全量驗證 `mvn -o verify`（`make test-db-up` 已啟動真實 postgres/redis）：單元 **1794**（+3：`RefreshTokenServiceTest` 由 1 案例換成 3 案例、`AuthServiceTest` +1）／整合 **574**（+5）／0 failures／0 errors／0 skipped；checkstyle（main+test）0 violations；PMD 通過；`BUILD SUCCESS`（14:27 min）。
- **過程中的失敗，如實揭露**：第一次全量驗證有 1 個單元測試失敗（`AuthServiceTest$RefreshToken.refreshToken_validToken_returnsNewAuthResponse`）——那是我漏改的舊測試：舊的 `void rotateRefreshToken` 不需要 stub，改成回傳 boolean 的 `tryRotateRefreshToken` 後，Mockito 預設回 `false`，測試走進「輸掉競爭」分支。補上 stub 後重跑全綠。這個失敗也順帶證明「輪替失敗就不換發」的行為在單元層確實生效。
- 未變更 entity/migration，未跑 `make validate-schema`
- 未跑 E2E／`make validate-release`

## 6. 決策與已知限制

- **輸掉競爭者撤銷全部 session，而非只拒絕該請求**：與既有「先後兩次使用」的重放處理（DEF-219，使用者採「推薦」方案）一致；「同時」與「先後」是同一種外洩訊號。**代價**：使用者若開多個瀏覽器分頁、恰好在同一瞬間各自用同一個 refresh token 換發（S167 已記為「已知且刻意接受的殘留風險（跨分頁）」的情境），現在確定性地會被登出所有裝置；修復前這種「恰好同時」反而兩邊都成功。先後幾毫秒的同情境本來就會被登出，故此改變只是讓結果不再取決於運氣。若日後想放寬，可改為僅拒絕輸家（不撤銷全部），代價是失去這個外洩訊號。
- **不保證競爭後 session 的存續**：贏家在 `AuthResponse` 產生後才 `storeRefreshToken(新 token)`，輸家的「撤銷全部」與它之間沒有順序保證，故競爭後贏家的新 token 可能已被撤銷、也可能存活。安全面上不比不撤銷差（撤銷只可能多刪不可能少刪），但測試刻意不斷言此點。
- **未變更的相關觀察**：`blacklistAllRefreshTokens` 使用 Redis `KEYS` 指令（O(N)，會阻塞整個 Redis），現在輸掉競爭的每個請求都會呼叫一次。實際影響取決於生產 keyspace 大小，**未量測**，已登記為 `DEF-292` 不排入排程。
- 未變更 entity/migration，未跑 `make validate-schema`；未跑 E2E／`make validate-release`。

## 7. 後續

- `myTodoList.md` 三項人工待辦狀態不變，仍在使用者手上。
- 已知未涵蓋：對 `/v2/auth/refresh` 端點本身的 HTTP 層併發測試（`MockMvc` 不易製造微秒級競態且 `IntegrationTestConfiguration` 把 Redis 換成 mock）；本輪以直接呼叫 `AuthService` ＋真實 Redis 驗證，覆蓋競態發生的那一層。
