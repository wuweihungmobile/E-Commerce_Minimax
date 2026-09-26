# Sprint 206 Plan — Tomcat 連接器層的錯誤回應改回 JSON 封包（DEF-283 結案）

**Sprint**: Sprint 206
**日期**: 2026-09-27

## 1. 起點與缺口盤點

使用者在 Sprint 203 開頭要求「依照建議，繼續完成任務！做完後，依序完成ＡＢＣ」；本輪是 **C**：`DEF-283` 加維運確認。

### 1.1 維運三件事：AI 做不到

前方代理有沒有送 `X-Forwarded-Proto`、staging 首次上線看瀏覽器主控台有無 CSP 違規、Sprint 198 若已部署則需送 `max-age=0`（HSTS 帶過 `includeSubDomains`）——這三件都需要存取實際部署環境，**仍在使用者手上**。

### 1.2 DEF-283

`GET /api/v2/x%2fy`、`%5C` 由 Tomcat 連接器在進入 Servlet 之前拒絕，回 Tomcat 內建 HTML 400 頁：沒有 JSON 封包、沒有 `X-Request-ID`、沒有安全標頭、應用日誌沒有痕跡。Sprint 202 的結論是「應用程式碼修不到，需 Tomcat 設定或前方代理處理」——**那是推論，未實測**。

## 2. 使用者決策

Sprint 203 的 `AskUserQuestion`：「C（DEF-283）怎麼處理」→ 使用者選 **「先實測可行性，再決定」**（推薦選項）：以真實 Tomcat 實測自訂 `ErrorReportValve` 是否能輸出 JSON＋標頭且不放寬任何 Tomcat 安全預設；做不到就改為只文件化並結案。

## 3. 實測與決定

環境：`mvn spring-boot:run`＋真實 PostgreSQL／Redis（配方見記憶 `security-headers-state-and-real-backend-run`），Tomcat 10.1.20。

**基準（修改前）**：`%0A`（Spring 防火牆）→ JSON＋8 個標頭 ✓（S201／S202 修復有效）；`%2f`、`%5C` → `text/html`、435 位元組、`Connection: close`、無標頭 ✗。

**E1：自訂 Host 的 `ErrorReportValve`——實測有效**：

| 請求 | 修改前 | 修改後 |
|------|--------|--------|
| `/api/v2/x%2fy`、`x%5Cy` | HTML 400、無標頭 | JSON `400 E-9000`、`X-Request-ID`（與內容一致）、8 個安全標頭與一般 401 逐值相同 |
| `/api/v2/x%zzy`（無效百分比編碼） | HTML 400 | JSON `400 E-9000` |
| 8 KB 超長路徑 | HTML 400 | JSON `400 E-9000` |
| `/`（context path `/api` 之外） | HTML 404 | JSON `404 E-4041` |
| `/api/v2/x%0AFORGED`（回歸） | JSON 400 | 不變 |
| `/api/actuator/health`（回歸） | 200 | 不變 |

**不放寬任何 Tomcat 規則**：`%2f`、`%5C` 依舊被 Tomcat 擋在應用之外；只改「拒絕之後回什麼」。應用日誌只有一行 `WARN`（狀態碼＋requestId），**不含路徑**（路徑是呼叫端控制的字串）。

**E2：設定 `ALLOW_ENCODED_SLASH` 等系統屬性讓 Spring 防火牆處理——未實測**。E1 已達成目標，而 E2 會移除 Tomcat 的一層預設拒絕、只剩 Spring 防火牆把關，沒有必要。

**決定：採用 E1**（符合使用者訂下的「可行且不放寬 Tomcat 防護」條件）。

## 4. 實作內容

- `api/config/ContainerErrorResponse`（新）：容器層錯誤封包的**單一來源**（404→`E-4041`、其他 4xx→`E-9000`、其餘→`E-9900`）。原本這段對應寫在 `ApiErrorController` 內，抽出後兩條容器路徑共用，不會漂移。
- `api/config/ApiErrorReportValve`（新）：繼承 Tomcat `ErrorReportValve`，只覆寫 `report`；前置條件與 Tomcat 原實作一致（狀態碼 < 400、已寫過內容、錯誤已回報過就不動）。重新產生 `X-Request-ID`（請求沒進過 `RequestIdFilter`）、套用 `SecurityHeaderPolicy`、寫 JSON。
- `api/config/ApiErrorReportValveCustomizer`（新）：以 `WebServerFactoryCustomizer<TomcatServletWebServerFactory>` 把 Host 的 `errorReportValveClass` 換成上述 valve。
- `ApiErrorController`：改用 `ContainerErrorResponse`（行為不變）。
- 文件：[API_Error_Codes.md](../02_architecture/API_Error_Codes.md) §2.2 改寫（原「已知限制」→ 現行行為＋評估過未採用的方案）、[SRD_System_Architecture.md](../02_architecture/SRD_System_Architecture.md) §5.4.1（v1.3）。

## 5. 測試

| 測試 | 守什麼 |
|------|--------|
| `ContainerErrorDispatchIntegrationTest` **IT-ERRDISP-07**（3 個路徑：`%2f`、`%2F`、`%5C`；真實 Tomcat＋JDK `HttpClient`） | 回 400、`application/json`、封包 `E-9000`、`requestId` 與標頭一致且為 UUID、8 個安全標頭**恰好一個值且逐值相同**、不是 HTML |
| 同上 **IT-ERRDISP-08** | 回應**不回顯**呼叫端控制的路徑；context path 之外的請求是 JSON `404 E-4041` |
| `ContainerErrorResponseTest`（7） | 狀態碼→錯誤碼對應 |

**為什麼必須是真實 Tomcat**：連接器層的拒絕發生在 Servlet 之前，MockMvc 完全模擬不到（見記憶 `mockmvc-cannot-see-container-error-dispatch`）。

**突變驗證**（暫時破壞實作，測試必須紅，之後還原並以 `diff` 確認）：不註冊 valve（3 失敗＋1 錯誤）、valve 不套用安全標頭（3 紅）、內容 `requestId` 與標頭不一致（3 紅）、404 不再對應 `E-4041`（1 紅）。**4 種全被抓到**。

**過程中我犯的一個 zsh 錯誤**：用 `path` 當 `for` 迴圈變數，它與 `PATH` 綁定，把 PATH 洗掉而出現「command not found: curl」。改用別的變數名後重跑。已寫進記憶。

## 6. 驗證結果

- **後端** `mvn -o clean verify`（真實 postgres／redis）：**單元 1771**（Sprint 204 收尾 1764，+7 即 `ContainerErrorResponseTest`）、**整合 565**（561，+4 即 IT-ERRDISP-07 三個路徑＋08），**0 failures／0 errors**；checkstyle（main＋test）**0 violations**；`BUILD SUCCESS`。
- **E2E** `make validate-e2e`：**76 passed／4 skipped／0 failed**（與 Sprint 204 收尾相同；4 個 skipped 是基準）。這次跑的是**完整的 Sprint 204＋205＋206 程式碼**，因此同時驗證了 Sprint 204 在 commit 時為避開 secret 掃描而改的前端 API 鍵名（`passwordForgot`／`passwordReset`）——見 [SPRINT_204_PLAN.md](SPRINT_204_PLAN.md) §5 的說明。
- **手動實測**：真實 Tomcat＋curl，見 §3 的對照表（含回歸：`%0A`、健康檢查）。
- **未執行**：`make validate-release` 完整守門（屬 push 流程）；本輪沒有 Flyway 遷移。

## 7. 範圍外（延後）

- **維運三件事**（見 §1.1）。
- valve 是 Tomcat 內部 API 的擴充點（`ErrorReportValve.report` 為 protected）；**Tomcat 大版本升級時須確認仍相容**（`IT-ERRDISP-07` 會在不相容時變紅）。
- 其他連接器層錯誤（如 431 標頭過大）只實測了 400／404 幾種；同一個 valve 對所有 ≥ 400 的連接器層錯誤生效，但未逐一實測。

## 8. Push

**尚未 push。** 使用者未授權；Sprint 203～206 完成後一併詢問。

## 9. 下一步

A、B、C 都已完成，等使用者決定 push 與下一個方向。仍待使用者決定的項目：`DEF-285`（PRD 全域冪等規範未落實，🟡 需產品決策）、`DEF-287`（結算單手動觸發入口，🟡）、真實寄信服務（決定 SMTP／SendGrid／SES 並提供憑證）、維運三件事。
