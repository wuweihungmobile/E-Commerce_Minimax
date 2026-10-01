# Sprint 230 Plan — 同一秒簽發的 Refresh Token 位元組相同（DEF-315）

**Sprint**: Sprint 230
**日期**: 2026-10-01

## 1. 起點

使用者再次說「請繼續完成任務！」（沒有針對新項目回覆；「繼續」的解讀沿用 [SPRINT_229_PLAN.md](SPRINT_229_PLAN.md) §1：做總結自己列為「還沒做」且 AI 能獨立處理的事）。Sprint 229 總結「還沒做」的另一項就是 `DEF-315`——Sprint 223 用打包 JAR 實測發現、一直登記未修的缺陷（追蹤表：「修法小、但動到認證 token 格式，宜單獨一輪處理」）。它不是產品決策，是缺陷，所以這輪修，單獨一輪。

## 2. 查證（現況）

1. `JwtTokenService.generateRefreshToken` 只放 `subject`、`iat`（只到**秒**）、`exp`，沒有 `jti`。同一使用者、同一秒簽發的兩顆 Refresh Token 位元組完全相同。
2. `RefreshTokenService` 以 token 的 SHA-256 前綴當 Redis key，狀態是 `valid`／`used`。`AuthService.refreshToken` 的流程是：`tryRotateRefreshToken`（原子地把舊 token 的 key 由 `valid` 標成 `used`）→ `generateAuthResponse`（簽發新 token 並 `storeRefreshToken` 寫成 `valid`）。若新 token 與舊 token 同一秒簽發，兩者是同一個 key：**剛標成 `used` 的 key 被寫回 `valid`**。
3. 後果（Sprint 223 實測，打包 JAR＋真實 Redis）：同一秒內 login → refresh → 以舊 token 重放回 **200**（應為 401）；16 個併發 refresh 成功 2～5 個（應為 1）。間隔 1.1 秒後 token 不同，重放回 401 `E-1003` 並撤銷所有 refresh token、輪替正確。實際風險低（重放必須發生在簽發的同一秒內），但它讓「refresh token 只能用一次」在這個邊緣情況不成立。
4. **為什麼 Sprint 213（DEF-291）的併發測試沒抓到**：`AuthServiceRefreshConcurrencyIntegrationTest` 把 `JwtTokenService` **mock 掉**，`generateRefreshToken` 每次回傳 `"refresh-new-" + UUID`——永遠不會產生「與舊 token 相同」的新 token。真實 Redis 驗證了原子性，卻用假的 token 產生器。（與 `DEF-313` 同一類：mock 掩蓋了真實元件的行為。）
5. 全庫簽發 refresh token 只有一個生產呼叫點：`AuthService.generateAuthResponse`（登入、換發、OAuth 經 `completeLogin` 都走這裡，`DEF-296`）。Access Token 同一秒內也可能相同，但沒有任何狀態以 access token 字串為鍵，無害。

## 3. 設計

`generateRefreshToken` 加隨機 `jti`（`UUID.randomUUID()`）：每顆 token 都不同，不論簽發時間。

- **向下相容**：驗證（`validateToken`／`getClaims`）不要求 `jti`，部署前簽發、沒有 `jti` 的舊 Refresh Token 仍然有效；它們在 Redis 的 key 以整顆 token 計算，不受影響。
- 不動 Access Token、不動 Redis key 的計算方式、不動輪替與重放偵測的邏輯——問題只在「新舊 token 可能相同」。
- 沒有另外在 `storeRefreshToken` 加「已使用的 key 不可被寫回有效」的第二道防線：根因是 token 不唯一，`jti` 一次解決；追蹤表的建議也只有 `jti`。

## 4. 實作

- `JwtTokenService.generateRefreshToken`：`.id(UUID.randomUUID().toString())`（一行程式碼加註解）。

## 5. 驗證

**測試**（先在未修的程式碼上確認紅燈，再修、再確認綠燈）

- 單元 `JwtTokenServiceTest` +3：連續簽發 50 顆 token 一律不同、帶隨機 `jti`、**向下相容**（部署前簽發、沒有 `jti` 的舊 token 仍驗證通過）。
- 整合 `AuthServiceRefreshSameSecondIntegrationTest`（新，2 案例，**真實 `JwtTokenService`＋真實 Redis**、不啟動 Spring）：登入後立刻換發，新 token 與舊 token 不同、以舊 token 重放 → `E-1003`、並撤銷所有 session（重複 20 輪）；登入後立刻有 16 個請求同時換發 → 恰好 1 個成功（重複 20 輪）。
- 真實後端 E2E `E2E-RTR-01`（新，打包 JAR）：登入→立刻換發→新 token 與舊 token 不同、重放回 401 `E-1003`、新 token 也被撤銷。

**紅燈（修復前）**：4 個新測試失敗，向下相容那個本來就該通過。整合測試第 1 輪就出現「新 token 與舊 token 相同」（同一秒簽發），併發測試在第 12 輪出現 2 個成功（應為 1）；單元測試 50 顆連續簽發的 token 只有 2 種不同的值、`jti` 為 `null`。

**突變驗證**（逐一植入後跑上述單元與整合測試，**3 個全被抓到**）：

| 突變 | 失敗的測試 |
|------|-----------|
| S30-M1 拿掉修正（不加 `jti`） | 整合 2＋單元 2（＝紅燈那 4 個） |
| S30-M2 `jti` 是固定字串（每顆都一樣） | 整合 2＋單元 2 |
| S30-M3 `jti` 用毫秒時鐘（同一毫秒內仍會撞號的看似合理修法） | **只有單元 1**（連續簽發 50 顆一律不同）；整合測試在這個突變下仍通過——登入到換發之間隔了不只一毫秒 |

M3 說明單元測試那個「連續簽發 50 顆」不是多餘的：整合測試驗證的是「真實流程下新舊 token 不同」，抓不到「唯一性只做了一半」的修法。

**全量**（`mvn -o clean verify`，15 分 03 秒）：單元 1964（+3）／整合 680（+2）／0 失敗；checkstyle 0 違規（主程式碼與測試）、PMD 通過。**`make validate-e2e`**（乾淨 PostgreSQL＋Flyway、`ddl-auto=validate`、打包 JAR＋`npm start`，復用剛驗證過的 JAR）：**112 個測試，108 通過／4 略過／0 失敗**（5.3 分；比 Sprint 229 多的 1 個是新的 `E2E-RTR-01`）。

## 6. 決策與已知限制

- **舊 token 不受影響也不被修正**：部署前簽發的 Refresh Token 沒有 `jti`，仍然有效；它們各自在 Redis 有獨立的 key，這個缺陷只會發生在「新舊兩顆在同一秒簽發」的換發，部署後所有新簽發的 token 都有 `jti`。
- **Refresh Token 變長約 50 字元**：沒有長度限制擋得到它——`RefreshTokenRequest.refreshToken` 只有 `@NotBlank`、Redis key 是 token 的 SHA-256 前綴、沒有任何程式使用 V1 的 `refresh_tokens` 資料表（讀程式碼確認）。依同一個道理（`blacklistRefreshToken` 以 token 為鍵），同一使用者在同一秒內開的兩個 session 原本會共用一個 key、登出其一就連帶登出另一個，唯一性修好後不再發生（**推論，未另行測試**）。
- **Access Token 不加 `jti`**：沒有任何狀態以 access token 字串為鍵，同一秒內相同無害；加了只會讓每個請求的 token 變長。
- **`E2E-RTR-01` 在修復前不保證每次都紅**：它依賴「登入後立刻換發」落在同一秒內（幾乎必然、但不是必然）；真正確定性的紅燈是單元測試（連續簽發 50 顆）與整合測試（20 輪）。E2E 的價值是在打包後的真實後端上走一遍（`DEF-313` 教訓：只在 JAR 才壞）。

## 7. 後續

- 等使用者決定：`DEF-316`（商家端訂房管理，含權限模型）、`DEF-306`；`DEF-317`／`DEF-318`（通知的其餘缺口）。
