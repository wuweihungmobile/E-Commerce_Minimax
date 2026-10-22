# Sprint 296 Plan — DEF-397：全域強制回應編碼，使 PRD §16.4.1 的 `Content-Type` 規定成立

## 1. 起點與範圍

### 1.1 起點（使用者的明確決定，不是推論）

[Sprint 294](SPRINT_294_PLAN.md) 收尾時，我用 `AskUserQuestion` 問使用者 `DEF-397`（PRD §16.4.1 規定所有 Error Response 的 `Content-Type` 固定為 `application/json; charset=utf-8`，實測大多數回應不帶 charset）要怎麼處理，附三個選項。使用者**本人**選了「**全域強制 charset**」（`server.servlet.encoding.force-response=true`），**不是**我建議的「修訂 PRD 措辭」。照使用者的決定做；這個選項我當時就在說明裡寫了代價（會替圖片與檔案下載也追加 `;charset=UTF-8`、已知至少一個既有測試斷言會碰到）。

### 1.2 成功標準（開工時定義）

1. **修復前先量基線**，並且先有紅燈：用真實 Tomcat 量各種回應來源的 `Content-Type`；新的契約測試對未修改的設定必須失敗。
2. **開關前後用同一個 JAR 對照**：程式碼一個字不改、只差環境變數，才能把差異歸因到這個設定。
3. 契約測試涵蓋 PRD §16.4.1 的幾種錯誤來源，修復後全綠。
4. 全量回歸：`mvn -o clean verify`（全域設定會影響所有回應，不能只跑相關測試）。
5. 被波及的既有測試要修，而且修法不能弱化測試的意圖。
6. 現行文件同步，並如實標出沒量到的部分。
7. 全域行為變更要跑 `make validate-e2e`。

## 2. 修復前後實測（真實 Tomcat，同一個 JAR）

**方法**：用現有 JAR（生產碼同 HEAD）對 `make test-db-up` 的 PostgreSQL／Redis 起後端（`SPRING_FLYWAY_ENABLED=false`、`ddl-auto=update`），以 `curl -D` 取標頭。第一次不帶開關（基線），第二次只加環境變數 `SERVER_SERVLET_ENCODING_FORCE_RESPONSE=true`（Spring Boot 的寬鬆綁定會覆寫 `application.yml`）。有 token 的探測先驗證 token 有效（長度 282／279，`GET /v2/auth/me` 回 200），避免實驗其實送的是空的 `Bearer`。量完後 `make test-db-down && make test-db-up` 重建乾淨容器（量測時註冊過使用者）。

| 回應來源 | 基線 | `force-response=true` |
|---|---|---|
| 200 正常 JSON（MVC，`GET /v2/public/listings`） | `application/json` | `application/json;charset=UTF-8` |
| 200 `actuator/health`（vendor 型別） | `application/vnd.spring-boot.actuator.v3+json` | 同上加 `;charset=UTF-8` |
| **401 未登入（`SecurityConfig.authenticationEntryPoint`）** | **`application/json;charset=ISO-8859-1`（宣告的編碼是錯的）** | `application/json;charset=UTF-8` |
| 404 未知路徑（MVC，需 token） | `application/json` | `application/json;charset=UTF-8` |
| 400 驗證失敗（MVC，login 空 body） | `application/json` | `application/json;charset=UTF-8` |
| 403 買家打管理端點（MVC） | `application/json` | `application/json;charset=UTF-8` |
| 400 防火牆拒絕 `%0A`（`ApiErrorController`） | `application/json` | `application/json;charset=UTF-8` |
| 400 連接器層拒絕 `%2f`（`ApiErrorReportValve`） | `application/json;charset=utf-8` | **不變**（小寫 `utf-8`） |
| 400 `TenantContextFilter`（SUPER_ADMIN＋非 UUID `X-Tenant-ID`） | `application/json;charset=UTF-8` | 不變 |
| 429 `LoginRateLimitFilter`（第 33 次／第 18 次） | `application/json;charset=UTF-8` | 不變 |
| 429 `RateLimitFilter`（第 142 次／第 141 次） | `application/json;charset=UTF-8` | 不變 |

基線與 Sprint 288 §2.7 當時量到的七種來源逐項吻合。**結論**：(1) 開關讓原本不帶 charset 的來源全部帶上；(2) 401 的錯誤宣告（ISO-8859-1）被修正；(3) 連接器層與三個 filter（DEF-396 修復後）原本就符合、不受影響；(4) **非 `application/json` 的型別也被追加**（actuator vendor 型別）——證實 Sprint 288 當時「依 Tomcat 語意推論、未實測」的副作用是真的。

**過程中的探測失誤**：我第一次探測 `TenantContextFilter` 用的是買家 token，回的 403 是 `application/json` 沒有 charset——如果那真是該 filter 寫的，DEF-396 修復後應該是 `;charset=UTF-8`，所以那個 403 來自別處，**沒有打到那個 filter**。登入限流 15 次也沒觸發 429。我沒有把「沒量到」當成「量到了」，回頭照 Sprint 288 §2.1 的觸發方式（SUPER_ADMIN 帶非 UUID 標頭、登入連打到限流、匿名連打公開端點到限流）補量，三個都量到了。因此第一支腳本裡「買家帶陌生 `X-Tenant-ID` → 403」那一列不是 filter 的回應（開關前後分別是 `application/json`、`application/json;charset=UTF-8`，來自授權層），上表沒有列入。

## 3. TDD：契約測試先紅後綠

新增 `ErrorResponseContentTypeIntegrationTest`（`RANDOM_PORT` 真實 Tomcat，比照既有 `ContainerErrorDispatchIntegrationTest`）：`IT-CT-001`～`005`，把 charset 值正規化（小寫、去空白）後與 PRD 字面 `application/json;charset=utf-8` 比對。

| 階段 | 結果 |
|---|---|
| **修復前**（未改設定） | **4 個失敗**：`ok200Json`／`mvcBadRequest400`／`firewallRejected400` 實際為 `application/json`；`unauthenticated401` 實際為 `application/json;charset=iso-8859-1`；`connectorRejected400` 通過（原本就符合） |
| **修復後**（加上設定） | 5／5 通過 |

## 4. 修法與被波及的既有測試

- `backend/src/main/resources/application.yml`：`server.servlet.encoding.force-response: true`，附註解說明來由、副作用與測試斷言的注意事項。一行設定，生產 Java 程式碼零變更。
- **被波及的既有測試（修復前就預測、這次實測確認）**：`M18MediaIntegrationTest.testGetFile`——不改它時實測失敗：`Content type expected:<image/jpeg> but was:<image/jpeg;charset=UTF-8>`（這也是**圖片下載回應被追加 charset 的直接證據**）。該案例的意圖是「串流回的是 JPEG 位元組」，charset 參數與意圖無關，所以把 `contentType(MediaType.IMAGE_JPEG)` 改成 `contentTypeCompatibleWith(MediaType.IMAGE_JPEG)`：仍檢查型別與子型別（`image/png` 對 `image/jpeg` 仍不相容），只忽略參數，並附註解。
- 其餘讀取回應 `Content-Type` 的既有斷言：`ContainerErrorDispatchIntegrationTest` 用 `startsWith("application/json")`、三個 filter 的單元測試用 `containsIgnoringCase("charset=UTF-8")`，都不受影響（見驗證）。
- 前端與 E2E：`grep` 查證前端程式碼沒有依賴 `Content-Type` 字串；`frontend/e2e` 裡出現 `contentType` 的只有 `route.fulfill` 的 mock，不是對後端回應的斷言。

## 5. 驗證

| 檢查 | 結果 |
|---|---|
| 契約測試 | 5／5（見 §3） |
| `M18MediaIntegrationTest.testGetFile` 修正後 | 通過（隨全量 verify，整合測試 883 個全過） |
| `mvn -o clean verify`（全量，單元＋整合） | **BUILD SUCCESS（21:04）**：單元 **2308**、整合 **883**，0 失敗／0 錯誤／0 跳過；日誌 0 個 `[ERROR]`。相對 Sprint 291 的基線（2295／878）：單元 +13（Sprint 294 的 2＋Sprint 295 的 11）、整合 +5（本 Sprint 的契約測試） |
| Checkstyle／PMD | 主程式與測試皆 0 違規；PMD（7.0.0）通過 |
| `make validate-e2e` 第一次 | 179 個案例：174 通過、4 跳過、**1 失敗**（見下） |
| `make validate-e2e` 重跑（`E2E_GATE_SKIP_BUILD=1`，同一份建置、乾淨 DB） | **175 通過、4 跳過、0 失敗（7.1 分鐘）**，閘門顯示「本地 E2E 守門通過」 |
| 雲端 CI | push 後回填 |

**E2E 第一次的 1 個失敗**：`at-buyer-pages.spec.ts:27 › E2E-BUYER-01: 我的訂單列表空狀態`（第 39／179 個，屬第一波並行）。錯誤是 `submitRegister: 註冊失敗，仍停在 http://localhost:3000/register（attempt=0, status=undefined, rateLimited=false）`；頁面快照顯示註冊表單欄位都已填好、仍在 `/register`。`status=undefined` 表示 helper 在 6 秒（`REGISTER_RESULT_TIMEOUT_MS`）內**沒有觀察到任何註冊 POST 的回應**。

我的判斷是偶發的第一波逾時（剛啟動的 JVM、並行 bcrypt 與前端 hydration 的時序），**這是推論，不是證明**。理由：(1) 沒有 HTTP 狀態碼——若是我的 `Content-Type` 變更弄壞註冊，應該每次都失敗且帶狀態碼；(2) 同一份建置、同一批測試重跑 0 失敗，該案例通過；(3) 其餘 174 個案例（含大量 `registerAndLogin`）都通過。**沒有找到根因**：`validate-e2e.sh` 的 cleanup 會刪掉後端日誌，無法確認那次 POST 是否送達；也沒有做「關掉 `force-response` 後重跑多次比較偶發率」的對照（成本太高）。

## 6. 文件

- `SRD_System_Architecture.md` v1.8→**v1.9**：新增 §5.4.5「回應 `Content-Type` 的編碼」（實測表、副作用、charset 比對不分大小寫、測試斷言注意事項、保留的明確宣告、守門）。
- `PRD`：**不改**。§16.4.1 該列的字面值（`application/json; charset=utf-8`）現在成立（差異只是空白與 charset 值的大小寫，charset 值本來就不分大小寫）。
- `DEFERRED_ITEMS_TRACKER.md` `DEF-397`：結案並附實測。

## 7. 限制（誠實揭露）

1. **真實 Tomcat 上的二進位檔案下載沒有量到**：圖片／檔案下載端點需要 MinIO 與上傳的資料才能打到，本機沒起。機制證據有兩個：真實 Tomcat 上 actuator 的 vendor 型別被追加；`MockMvc` 對 `image/jpeg` 被追加（`M18MediaIntegrationTest` 的實測失敗訊息）。屬於推論的是「真實 Tomcat 對 `image/jpeg` 的實際標頭」——兩者在同一個 `CharacterEncodingFilter` 機制下，但我沒有直接量。
2. **外部消費者未知**：專案內的前端與 E2E 沒有依賴 `Content-Type` 字串，但若有外部整合或監控以精確字串比對 `application/json`，會受影響。專案內查不到，專案外無從得知。
3. **全域副作用是使用者選擇的取捨**：所有回應（含檔案下載）的 `Content-Type` 都帶 `;charset=UTF-8`；瀏覽器對圖片與二進位內容會忽略這個參數。
4. **保留的明確宣告不是冗餘**：`ApiErrorReportValve` 與三個 filter 各自宣告 UTF-8 的程式碼不移除——單元測試靠它們（`MockHttpServletResponse` 沒有容器的編碼 filter），設定被移除時也是第二道防線。
5. **契約測試涵蓋的是 5 個來源**：`403`（`SecurityConfig.accessDeniedHandler`）與三個限流／租戶 filter 沒有真實 Tomcat 的契約案例（整合測試環境的 Redis 是 mock，限流行為不真實；filter 已有各自的單元測試）。
6. `DEF-397` 原本描述的可選項（`SecurityConfig` 兩處補 `setCharacterEncoding`）**不需要了**：401 實測已由全域設定修正為 UTF-8。
7. **E2E 第一次有 1 個案例失敗、重跑通過，根因未找到**（細節與推論依據見 §5）。若日後這個案例（或其他走 `submitRegister` 的案例）再出現同樣的 `status=undefined`，要往「註冊 POST 沒送出／回得太慢」查，並把 `force-response` 列為需要排除的變數。

## 8. 我替使用者做的決定（可一句話推翻）

| # | 決定 | 理由 | 一句話推翻 |
|---|---|---|---|
| 1 | M18 測試改用 `contentTypeCompatibleWith`，而不是把期望值改成 `image/jpeg;charset=UTF-8` | 意圖是媒體型別；寫死 charset 會讓測試綁定一個與意圖無關的參數 | 想要嚴格：改回精確相等並把期望值加上 charset |
| 2 | 保留 `ApiErrorReportValve` 與三個 filter 的明確 UTF-8 宣告 | 見 §7.4 | 想移除：同時要改它們的單元測試 |
| 3 | 契約測試比對時正規化 charset（小寫、去空白） | charset 值不分大小寫；連接器層寫小寫 | 想嚴格比對大小寫：先把 `ApiErrorReportValve` 改成大寫 |
| 4 | 不改 PRD | 字面規定現已成立 | — |
| 5 | 在 SRD 新增 §5.4.5 記錄行為與副作用 | 這是橫切 HTTP 行為，SRD §5.4 是記錄處 | — |

## 9. 後續

`DEF-398`（99 筆 ESLint warning 全部處理）是 Sprint 297。

## 10. 文件與程式異動清單

| 檔案 | 異動 |
|---|---|
| `backend/src/main/resources/application.yml` | `server.servlet.encoding.force-response: true`＋註解 |
| `backend/src/test/java/com/nextkey/ecommerce/integration/ErrorResponseContentTypeIntegrationTest.java` | 新增（5 個案例） |
| `backend/src/test/java/com/nextkey/ecommerce/integration/M18MediaIntegrationTest.java` | `testGetFile` 改用 `contentTypeCompatibleWith` |
| `docs/02_architecture/SRD_System_Architecture.md` | v1.8→v1.9：§5.4.5 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | v3.68→v3.69：`DEF-397` 結案 |
| `docs/04_planning/RELEASE_TRACKER.md` | v4.14→v4.15：Sprint 296 row；回填 Sprint 295 |
| `docs/04_planning/SPRINT_296_PLAN.md` | 新增（本文件） |
