# Sprint 201 Plan — 防火牆拒絕的請求回 400 而非 401（DEF-281 結案）

**Sprint**: Sprint 201
**日期**: 2026-09-26

## 1. 起點

使用者貼上 Sprint 200 收尾總結，回覆「請繼續完成任務！」。

**我的理解（推論，使用者未逐字確認）**：總結裡列的「你需要做的」（代理的 `X-Forwarded-Proto`、staging 的 CSP、子網域盤點）都是維運動作、我做不到；唯一我做得到、且明確標示「本輪沒動」的是 **DEF-281**，所以以它為本輪任務。

## 2. 成因（這輪用真實 Tomcat 加 Spring Security DEBUG 日誌查證；Sprint 199 只是推測）

請求 `GET /api/v2/x%0AFORGED` 的實際路徑：

1. `StrictHttpFirewall` 拒絕（日誌：`Rejecting request due to: … potentially malicious String "%0A"`，此時 MDC 仍有 requestId）。預設的 `HttpStatusRequestRejectedHandler` 呼叫 `sendError(400)`。
2. Servlet 容器對 `/error` 做一次 **ERROR 分派**，重新走過 Security 過濾鏈（日誌：`Securing GET /error`，MDC 已空 `[]`、`Set SecurityContextHolder to anonymous`）。
3. 這次分派身分是匿名（`JwtAuthenticationFilter` 是 `OncePerRequestFilter`，預設不處理 ERROR 分派），又不在 `permitAll` 之列，被 `.anyRequest().authenticated()` 擋下，由 `authenticationEntryPoint` 寫出 **401 `E-1000`**；requestId 取自空的 MDC，所以內容沒有。

與 Sprint 199 的推測比對：「ERROR 分派」正確；「`RequestIdFilter` 沒涵蓋 ERROR 分派」也正確，但**修法不需要動 `RequestIdFilter`**——第一輪已把 `X-Request-ID` 寫進回應標頭，容器轉送時標頭仍保留，直接讀它即可。

## 3. 修法

| 檔案 | 變更 |
|---|---|
| `SecurityConfig` | `authorizeHttpRequests` 最前面加 `.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()`。只限容器內部的 ERROR 分派；直接請求 `/error` 是 REQUEST 分派，仍要驗證 |
| `ApiErrorController`（新） | 實作 `ErrorController` 接手 `/error`，回 `ApiResponse` 封包：404→`E-4041`、其他 4xx→`E-9000`「請求格式錯誤」、其餘→`E-9900`；`requestId` 取自回應標頭；明確指定 `application/json`；**不回顯路徑或例外訊息**，也不寫日誌（路徑是呼叫端控制的字串，防火牆拒絕的正是含換行字元等日誌偽造寫法） |

**為什麼還要新增控制器、而不是只放行**：只放行的話，`/error` 由 Spring Boot 內建 `BasicErrorController` 回應——非封包格式的 JSON（缺 `success`／`code`／`requestId`），瀏覽器請求甚至會拿到 HTML 白頁，與其他所有錯誤回應形狀不一致（DEF-281 原記錄就點名「內容缺 requestId」）。

**評估過但未採用**：自訂 `RequestRejectedHandler` 直接寫 400。因為拒絕發生在過濾鏈之前，該回應不會經過任何 Security 過濾器，要自己複製標頭政策；而放行 ERROR 分派是 Spring Boot 3／Security 6 文件建議的標準做法，也一併處理其他 `sendError` 來源。

## 4. 測試與驗證

**紅燈先行**：新增 `ContainerErrorDispatchIntegrationTest`（5 個測試，`RANDOM_PORT` 真實 Tomcat + JDK `HttpClient`），修復前 **4 紅**（`expected: 400 but was: 401`；requestId `expected: … but was: ""`）。

**為什麼必須是真實伺服器**：ERROR 分派是容器行為，MockMvc 遇到 `sendError` 只設狀態碼就結束、不會再打 `/error`，所以 MockMvc 下重現不了——這也是全庫既有整合測試（全是 MockMvc）一直守不到的原因。這是 repo 內**第一個** `RANDOM_PORT` 測試（多一個 Spring context，測試啟動約 +40 秒）。

| 測試 | 守什麼 |
|---|---|
| IT-ERRDISP-01（2 案例：`%0A`、`//`） | 400 + `E-9000`，不是 401 + `E-1000` |
| IT-ERRDISP-02 | 內容 `requestId` = 回應標頭 `X-Request-ID` |
| IT-ERRDISP-03 | `Accept: text/html` 也回 JSON，不是白頁 |
| IT-ERRDISP-04 | 直接請求 `/error` 未登入仍 401（防止放行過寬） |

**突變驗證**（改壞實作，確認測試真的會紅，再還原並比對）：
- 拿掉 ERROR 放行 → 01×2、02、03 紅，04 綠。
- 放行改成 `requestMatchers("/error")`（過寬）→ 04 紅。
- 拿掉明確 Content-Type → 03 紅。拿掉 `setRequestId` → 02 紅。

**全量**：`mvn -o clean verify` **單元 1732（不變）＋ 整合 541（Sprint 200 的 536 ＋ 5），0 failed／0 errors／0 skipped**，checkstyle main／test 皆 0 違規，12:04。

**真實 Tomcat 交叉驗證**（`spring-boot:run`，**有效 JWT**：355 字元、`GET /v2/auth/me` 回 200——先前第一次嘗試因測試 DB 是空的、登入失敗、實際送的是 `Bearer null`，那次不算數）：

| 請求 | 結果 |
|---|---|
| 有效 token + `%0A` | 400 `E-9000`，`requestId` 與標頭相同 |
| 無 token + `%0A`／有效 token + `//`／POST + `%0A` | 同上 |
| `Accept: text/html` + `%0A` | 400，`Content-Type: application/json` |
| 直接 `GET /api/error`，無 token | 401 `E-1000`（未被放行） |
| 直接 `GET /api/error`，有效 token | 404 `E-4041`（無錯誤屬性視為找不到） |
| 一般 `GET /actuator/health`、一般 401 | 不受影響 |

日誌：ERROR 0 行、含 `FORGED` 0 行、堆疊 0 行（僅有的 2 個 WARN 是啟動時 MinIO 未啟動與 open-in-view，與本輪無關）。

## 5. 揭露：中途發現一個更大的、已存在的缺口 → DEF-282（未修）

我寫測試時預設「這個 400 仍帶安全標頭」，測試失敗才發現**前提是錯的**。查證：Spring Security 6.2.4 的 `HeaderWriterFilter` 沒有覆寫 `shouldNotFilterErrorDispatch()`，沿用 `OncePerRequestFilter` 預設（ERROR 分派不處理）。用「暫時移除放行」還原修復前行為對照：

| 回應 | 安全標頭（nosniff／X-Frame-Options／CSP／Referrer-Policy／Cache-Control） |
|---|---|
| 一般 401（第一輪請求，過濾鏈有跑） | 全套 |
| 防火牆拒絕（**修復前**，經 ERROR 分派的 401） | **全無**（只有 `X-Request-ID`、`Content-Type`） |
| 防火牆拒絕（修復後，400） | 全無（同上，狀態碼與內容變了，標頭狀況沒變） |

所以這**不是本輪造成的退步**，而是本來就有：**所有經 ERROR 分派的回應都沒有安全標頭**，PRD §16.4.1 要求錯誤回應帶 `nosniff`、`X-Frame-Options: DENY` 的這一類沒滿足。Sprint 197 與我的記憶記下的「200/400/401/403/500 全有」是 MockMvc 的結論——MockMvc 不做 ERROR 分派，所以一直沒看見。

我**沒有夾帶修復**，理由：修法必須在第二處複製標頭政策（`ApiErrorController` 補標頭，或多包一層在 ERROR 分派也執行的過濾器），有與 `SecurityConfig` 漂移的風險，是獨立的設計決定；且實際風險低（回應是 JSON、內容不含使用者可控字串）。已登記 `DEF-282`。

## 6. 其他揭露

- **行為變更範圍比 DEF-281 描述的大**：凡是進到 ERROR 分派的情況（`sendError`，以及依機制推斷的「過濾器內未捕捉例外」），先前都會被同一個機制誤報成 401 `E-1000`；現在依實際狀態碼回應（4xx→`E-9000`、404→`E-4041`、其餘→`E-9900`）。**我只實測了 4xx（防火牆拒絕）與「直接打 `/error`」兩條路徑；5xx 與「過濾器未捕捉例外」是依機制與程式碼推得、沒有實測**，也未找到現有程式碼中會走到它們的具體案例。
- 防火牆拒絕的種類很多（`;`、`\`、`%2e`、`%25`、`%2f` 等），我只測了 DEF-281 記錄的 `%0A` 與 `//`；它們都走同一個 `RequestRejectedHandler` → `sendError`，依機制推斷行為相同，但**沒有逐一實測**。
- 這類被拒絕的請求**不寫任何日誌**（刻意，見 §3）。代價是維運在預設日誌等級看不到掃描器的探測；若需要可觀測性，應計數而不是印路徑。
- 未動前端、未重跑 E2E；push 前守門 `make validate-release` 尚未跑。

## 7. Push

✅ **已 push**（`2a8b9fd..6c62bfa main -> main`，2026-09-26；Sprint 202 起點使用者回覆「commit + Push to main」）。當時我因授權範圍未涵蓋而未推，等使用者決定。push 前完整守門通過（含第一次因 Docker daemon 500 失敗、重啟後重跑），雲端 CI 全綠（run 36242648761）。詳見 [SPRINT_202_PLAN.md](SPRINT_202_PLAN.md) §2。

## 8. 下一步

1. ~~**DEF-282**（ERROR 分派回應缺安全標頭）：需先決定修法（見 §5）。~~ → **已於 Sprint 202 修復**（使用者選「共用政策＋控制器套用」），見 [SPRINT_202_PLAN.md](SPRINT_202_PLAN.md)。
2. 沿用 Sprint 198～200 的維運確認：前方代理有送 `X-Forwarded-Proto`；staging 首次上線看瀏覽器主控台有無 CSP 違規（有問題先設 `CSP_REPORT_ONLY=1`）；已 push 的 Sprint 198 帶過 `includeSubDomains`，若已部署且瀏覽器收過，需送 `max-age=0`；日後確認所有子網域走 https 才考慮加回 `includeSubDomains`。
