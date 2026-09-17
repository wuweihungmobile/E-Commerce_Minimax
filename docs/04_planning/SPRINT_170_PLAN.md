# Sprint 170 Plan — Refresh token 當 Bearer token 送出會讓 JwtAuthenticationFilter 拋出未攔截 NullPointerException（DEF-222）

**Sprint**: Sprint 170
**日期**: 2026-09-17

## 1. 起點

`DEFERRED_ITEMS_TRACKER.md` 活躍延後項目已無待排程項目（Sprint 169 的 `DEF-221` 已修復）。本輪延續「找過去從未系統性檢查過的角度」的方法：`comm`/`grep` 比對 `src/main`與 `src/test` 檔名，交叉確認後找到 `JwtAuthenticationFilter`——JWT 驗證流程中最關鍵的一支類別——先前從未有專屬單元測試（先前只在其他測試的 `IntegrationTestConfiguration`/`WithErpSecurity` 等基礎設施類別中被間接引用，從未直接測試其自身邏輯）。

過程中先排除一個假警報：`shouldNotFilter` 對整個 `/actuator/**` 略過 JWT 驗證，起初懷疑會讓 `/actuator/metrics`／`/actuator/info` 對外未授權可讀；查證 `SecurityConfig` 後發現只有 `/actuator/health` 有 `permitAll()`，其餘兩者落入 `.anyRequest().authenticated()`——由於 `shouldNotFilter` 略過整個 `/actuator/**` 使 `SecurityContext` 永遠不會被設定驗證身分，這兩個端點對任何人（含持有效 token 的使用者）都會被拒絕，是**過度限制而非過度開放**，不構成漏洞，只是功能性瑕疵，不在本輪防禦性掃描範圍內，未處理。

## 2. 缺口說明

`JwtTokenService.generateRefreshToken` 只設定 `subject`（userId）、`issuedAt`、`expiration`，**不含** `email`/`role`/`tenantId` claim（與 `generateAccessToken` 明顯不同）。refresh token 與 access token 用同一把密鑰簽章，且 `validateToken(token)` 只驗證簽章有效與未過期，完全無法區分兩者用途（無 `typ`/`aud` 等區分欄位）。

`JwtAuthenticationFilter.doFilterInternal` 對任何通過 `validateToken` 的 token 都會嘗試取出 `role` claim 並呼叫 `User.UserRole.valueOf(role)`：若把合法簽章、尚未過期的 refresh token 當 `Authorization: Bearer <token>` 送出，`getRole(jwt)` 回傳 `null`，`Enum.valueOf(Class, null)` 依 JDK 規範拋出 **`NullPointerException`**（不是 `IllegalArgumentException`）。`doFilterInternal` 的 catch 子句原本只涵蓋 `IllegalArgumentException | ClassCastException | JwtException`，未攔截 `NullPointerException`——此 filter 執行於 DispatcherServlet **之前**，`GlobalExceptionHandler`（Sprint 162/163 建立的全域 400 機制）結構性攔不到，未攔截例外直接從 filter 往外拋，與 Sprint 161~166、169 已修復的「未攔截例外」家族同型，但這次發生在整個系統唯一負責身分驗證的 filter 本身。

**可觸發者**：任何完成過一次正常登入的使用者都同時持有 access token 與 refresh token；只要把自己的 refresh token 當成 Bearer token 打向任一受保護 API，就會 100% 重現。不需要特殊權限或破解簽章。

## 3. 修法決策

比照 `DEF-217`/`DEF-218` 既有前例（拓寬既有 catch 子句涵蓋新發現的未攔截例外類型），在既有 multi-catch 加入 `NullPointerException`——語意與既有處理完全一致（token 內容不可信/不完整時，記錄錯誤並讓請求以未驗證身分繼續往下走，交由後續 `SecurityConfig` 的授權規則決定是否允許），不新增例外處理機制或改變既有行為模式。

## 4. 修復內容

`JwtAuthenticationFilter.java`：catch 子句由 `IllegalArgumentException | ClassCastException | JwtException` 擴充為 `IllegalArgumentException | ClassCastException | JwtException | NullPointerException`，並補上一行註解說明觸發情境（refresh token 缺 role claim）。

## 5. 測試

- **紅燈先行**：新增本專案第一支 `JwtAuthenticationFilterTest.java`（比照既有 `TenantContextFilterTest` 手法：plain JUnit，直接建構真實 `JwtTokenService`/`RolePermissionMapping`/`JwtAuthenticationFilter`，用 `MockHttpServletRequest`/`MockHttpServletResponse` 直接呼叫 `filter.doFilter(...)`，不需 Spring 容器/DB）。修復前執行：`doFilter_refreshTokenAsBearerToken_doesNotThrowAndLeavesUnauthenticated` 案例中 `assertThatCode(...).doesNotThrowAnyException()` 失敗，實際擷取到 `NullPointerException: Name is null`，呼叫鏈明確指向 `User$UserRole.valueOf` → `JwtAuthenticationFilter.doFilterInternal:60`，證實缺口為真。
- 套用 §4 修復後重跑，2 個案例（refresh token 當 Bearer 不拋例外且不建立驗證身分、正常 access token 成功驗證）全數轉綠。

## 6. 驗證結果

- 紅燈階段：見 §5，1 個案例確認先失敗（未預期 `NullPointerException`）再轉綠。
- `mvn -o compile`：每次修改後立即編譯，通過。
- `checkstyle`（main+test）：0 違規。
- `mvn -o verify`（真實 postgres/redis，`make test-db-up`）：**1412 個單元測試（+2）+ 482 個整合測試（持平），0 failed**，`BUILD SUCCESS`。本輪起跑前多等 6 秒再啟動 `mvn verify`，未再重現 Sprint 169 遇到的 pg_isready 競態。
- 未執行 `make validate-e2e`：修復範圍完全侷限於 `JwtAuthenticationFilter` 內部例外處理，未變更任何 API 契約、正常登入/驗證流程的行為（正常 access token 驗證邏輯不受影響，已有新增測試案例覆蓋），`make validate-release` 執行前會涵蓋完整 E2E 驗證。

## 7. 更新 `DEFERRED_ITEMS_TRACKER.md`

新增 `DEF-222`（已修復）：`JwtAuthenticationFilter` 對缺 `role` claim 的合法簽章 token（典型情境：refresh token 被當 Bearer token 送出）呼叫 `User.UserRole.valueOf(null)` 拋出未攔截的 `NullPointerException`；擴充既有 catch 子句涵蓋此例外類型，比照 `DEF-217`/`DEF-218` 既有前例。

---

## 8. 誠實揭露總結

- **未驗證未攔截例外實際到達使用者的確切 HTTP 回應內容**：本輪紅燈測試證實的是「filter 層級拋出未攔截 `NullPointerException`」這個事實本身（透過直接呼叫 `filter.doFilter(...)` 觀察例外傳播），未另外起一個真實的內嵌 Tomcat/`TestRestTemplate` 端對端測試去確認容器最終回應給客戶端的確切 HTTP 狀態碼與 body（是否洩漏 stack trace 取決於 `server.error.include-stacktrace` 等既有設定，非本輪修改範圍）——比照 Sprint 166 對 `TenantContextFilterTest` 同類型 filter 測試的既有取捨（直接呼叫 `doFilter` 而非起完整容器）。
- **`/actuator/metrics`／`/actuator/info` 目前對任何人（含合法使用者）皆不可達，是功能性瑕疵而非安全缺口**：§1 已排除為假警報，記錄於此供未來若有人回報「監控儀表板讀不到 metrics」時的優先查核方向，本輪未修復（不在防禦性掃描範圍內，且修復方向涉及是否要讓這兩個端點可被存取的產品決策，非單純的安全修復）。
- **未檢查是否有其他 claim 缺失的組合會觸發同一路徑**：本輪鎖定「refresh token 缺 role claim」這個已用紅燈證實的具體案例；`email`/`tenantId` 缺失不會在這條路徑拋出例外（`UserPrincipal` 建構子與 `WebAuthenticationDetailsSource` 皆能接受 null 值），只有 `role` 缺失會撞上 `Enum.valueOf`，故修復鎖定拓寬 catch 子句這個涵蓋所有「claim 缺失」情境的通用解法，未逐一列舉每個 claim 分別驗證。
