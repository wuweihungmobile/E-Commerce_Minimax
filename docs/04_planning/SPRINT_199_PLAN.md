# Sprint 199 Plan — 呼叫端路由錯誤不可被當成伺服器錯誤（DEF-279）

**Sprint**: Sprint 199
**日期**: 2026-09-25

## 1. 起點與範圍判斷

使用者貼上 Sprint 198 的收尾總結，並要求「請確認以上合理性，並繼續完成任務」。

- **「確認合理性」**：見 §2，逐項對照程式碼與 commit `a305ae5`。
- **「繼續完成任務」**：Sprint 198 總結留下的唯一待處理程式項目是 DEF-279（總結原文：「改動很小，說一聲就能排進下一輪」）。**我把「繼續」理解為排進本輪（此為推論，使用者未逐字確認）**；其餘項目（部署確認 `X-Forwarded-Proto`、staging 看 CSP 違規訊息）是維運／使用者動作，不是我能代做的。本輪沒有使用 AskUserQuestion。

## 2. Sprint 198 總結的合理性查證

**結論：總結與程式碼一致，沒有發現誇大或不實。**

| 總結的說法 | 我查到的 |
|---|---|
| HSTS：https 或 `X-Forwarded-Proto: https` 才送、不加 preload | `SecurityConfig.isHttpsRequest`：`request.isSecure()` 或 `X-Forwarded-Proto` 第一段（`split(",")[0]`）等於 https（不分大小寫）；前端 `next.config.ts` 以 `has` 條件；兩處都沒有 `preload` ✅ |
| 嚴格 nonce CSP、`CSP_REPORT_ONLY=1` 緊急開關 | `src/proxy.ts`：每次請求產生 nonce、`script-src 'self' 'nonce-…' 'strict-dynamic'` 無 `unsafe-inline`；`CSP_REPORT_ONLY === '1'` 時改送 `Content-Security-Policy-Report-Only` ✅ |
| 帶入的 `X-Request-ID` 只有格式安全才沿用 | `RequestIdFilter`：`^[A-Za-z0-9._-]{1,64}$`，否則重新產生 UUID；MDC 於 `finally` 移除 ✅ |
| 過濾器排在 Security 之前 | `@Order(Ordered.HIGHEST_PRECEDENCE)` ✅ |
| CORS 暴露該標頭 | `SecurityConfig` 的 `setExposedHeaders` 含 `RequestId.HEADER` ✅ |
| PRD 封包形狀未對齊（`requestId` 在頂層） | `ApiResponse.requestId` 為頂層欄位，`error.requestId` 不存在 ✅（總結已誠實揭露） |
| 雲端 CI 不含 Playwright E2E | 與 `RELEASE_TRACKER` 已更正的敘述一致 ✅ |

**我沒有重新驗證的**：Sprint 198 的前端 E2E（70 passed／4 skipped）——本輪沒有改前端，未重跑；CSP 在 Safari／Firefox 的行為；Stripe／OAuth 真實供應商。

**值得使用者知道、但總結沒有特別強調的兩點**（不是缺陷，是決策的後果）：
1. **HSTS 值帶 `includeSubDomains` 且 `max-age` 一年**（Sprint 198 計畫書 §2 有記載，總結只強調「不加 preload」）。瀏覽器一旦收到，一年內該網域**及所有子網域**都只能走 https；若日後有任何子網域仍是 http，會被瀏覽器直接擋掉。後端沿用 Spring 預設格式，所以這不是我另外加的，但**它與 preload 一樣難以反悔**，只是範圍小一些。repo 內沒有子網域的使用（後端 `src/main/java` grep `subdomain`／`custom domain` 無命中；未檢查設定檔與前端），實際部署我無從得知。
2. **前端 HSTS 條件與後端不完全等價**：前端是 `x-forwarded-proto` 的正規表達式 `https.*`，後端是「第一段、不分大小寫等於 https」。大寫 `HTTPS` 等邊界寫法兩邊可能不一致；我**沒有實測**前端對大寫的行為。實務上代理送小寫 `https`，影響極小。

## 3. DEF-279：根因與實測

**DEF-279 原描述**：已登入者請求不存在的路徑回 500 而非 404。

**先做紅燈探針（不憑推測）**：`UnknownRouteIntegrationTest`（走完整過濾鏈，真實 postgres／redis）。修復前 **4 個測試（涵蓋下列 3 種請求）全部回 500**，而且每一個都在日誌寫一筆 `ERROR … GlobalExceptionHandler : Unexpected error` 加完整堆疊：

| 請求 | 修復前 |
|---|---|
| 已登入 `GET /v2/no-such-endpoint`（DEF-279 原案例） | 500 |
| 已登入 `POST /v2/auth/me`（路徑存在、方法不對） | **500**（新發現） |
| `POST /v2/auth/login`，`Content-Type: text/plain` | **500**（新發現） |

**根因**：`GlobalExceptionHandler` 有一個 `@ExceptionHandler(Exception.class)` catch-all，但沒有接住 Spring 6.1 自己宣告為 4xx 的那批例外（它們都實作 `org.springframework.web.ErrorResponse`）。只要沒有更具體的處理器，就會掉進 catch-all 變成 500。DEF-279 只是這一族裡最先被看到的一個。這與 Sprint 162（`HttpMessageNotReadableException`）、Sprint 163（`MethodArgumentTypeMismatchException`）是同一種缺陷、同一種修法。

## 4. 修法與決策

`GlobalExceptionHandler` 新增三個處理器（`backend/src/main/java/com/nextkey/ecommerce/api/dto/GlobalExceptionHandler.java`）：

| 例外 | 狀態碼 | 錯誤碼 | 日誌 |
|---|---|---|---|
| `NoResourceFoundException` | 404 | `E-4041`，訊息「找不到請求的資源」 | 一行 WARN（方法＋路徑），無堆疊 |
| `HttpRequestMethodNotSupportedException` | 405（帶 `Allow` 標頭） | `E-9000`，「不支援的請求方法」 | 一行 WARN |
| `HttpMediaTypeNotSupportedException` | 415 | `E-9000`，「不支援的內容型別」 | 一行 WARN |

**決策（皆可推翻）**
- **404 沿用 `E-4041` 而不是新增錯誤碼**：SRD 第 1267 行把 `E-4041` 定義為「資源不存在／全域」；前端沒有任何地方針對此碼分支（grep `frontend/src`、`frontend/e2e` 無命中）；不新增 `ErrorCode` 就不必動窮舉的 `mapErrorCodeToStatus` 與其全量測試表。**副作用**：`ErrorCode.E_4041` 的預設文字是「找不到店鋪」，所以這裡傳入專屬訊息而不用預設文字——但若有人只看錯誤碼，`E-4041` 在程式裡仍同時代表「店鋪不存在」與「路由不存在」。
- **405／415 沿用 `E-9000`**：與 Sprint 162 對「請求本身有問題」的處理一致；狀態碼與標頭交給例外自己（`ErrorResponse`）決定，其中 405 的 `Allow` 是 RFC 9110 的必要標頭。
- **WARN 而非 ERROR**：呼叫端的錯，不該觸發 5xx 告警或洗版堆疊。

## 5. 測試、突變驗證與驗證結果

**新增測試**

| 測試 | 數量 | 守住什麼 |
|---|---|---|
| `UnknownRouteIntegrationTest`（整合，完整過濾鏈） | 4 | 已登入未知路徑 → 404＋`E-4041`＋帶 `requestId`；**該情況不寫 ERROR 日誌**（以 logback `ListAppender` 攔截）；405 帶 `Allow`；415 |
| `GlobalExceptionHandlerTest` | +1 | 真正未預期的例外**仍**回 500＋`E-9900`（新處理器沒有吞掉 catch-all 的職責） |

**突變驗證**
- 基準（沒有任何新處理器）：4 個整合測試全紅（500）。
- 突變：同時移除 405 的 `.headers(ex.getHeaders())`、並把 404 的 `log.warn` 改成 `log.error` → **恰好 2 個變紅**（日誌等級、`Allow` 標頭），其餘 2 個維持綠；還原後 `cmp` 與備份一致。

**驗證結果**：見 §7。

## 6. 範圍外與誠實揭露

- **406（`HttpMediaTypeNotAcceptableException`）沒有處理**：我先寫了處理器與測試，但兩次都無法證明它會被走到——用 `/v2/auth/me` 時端點先丟業務例外，錯誤回應本身也要以 `Accept: application/xml` 寫出而失敗；改用公開端點 `/v2/cms/banners/active` 則被該端點自己的參數驗證先擋成 400。沒有真實案例，依「簡潔優先」把 406 處理器與測試移除，**維持原狀**。如果之後有客戶端會送出端點產不出的 `Accept`，需要另外查。
- **其他 Spring 框架 4xx 例外沒有逐一盤點**（例如 `MissingServletRequestPartException`、`HttpMessageNotWritableException` 等）；本輪只處理有實測紅燈的三種。
- 日誌的 `%5p [%X{requestId:-}]` 格式（Sprint 198）與本輪的 WARN 行一起生效；WARN 行會帶請求 ID。
- 路徑字串會被印進 WARN 日誌，路徑是呼叫端控制的，所以我在真實 Tomcat 上以 `GET /api/v2/x%0AFORGED-LOG-LINE-INJECTED` 實測：日誌**完全沒有**出現 `FORGED` 字樣（請求在到達 `GlobalExceptionHandler` 之前就被拒絕，見 DEF-281），沒有偽造日誌行。這只證明 `%0A` 這一種寫法；我沒有窮舉其他編碼（例如 `%0D`、`\r`）。
- **新發現 DEF-281（未修，只登記）**：上述被 Spring Security 防火牆拒絕的請求（`%0A`、`//`）回 **401 `E-1000`「Authentication required」**——連帶有效 token 也是——而不是 400；回應**內容**沒有 `requestId`（標頭 `X-Request-ID` 有）。呼叫端的錯被報成認證失敗；正常前端不會送出這類網址，實際影響主要是掃描器與除錯時的誤導。成因我**沒有查證**（推測是 Tomcat 的錯誤分派階段：`OncePerRequestFilter` 預設不處理 ERROR 分派、MDC 已清）；也**沒有在舊碼上對照實測**，「與本輪修改無關」是依「此階段早於 `GlobalExceptionHandler`」的判斷。

## 7. 驗證結果

- 後端 `mvn -o clean verify`（真實 postgres／redis）：**1732 單元（+1）＋534 整合（+4），0 failed／0 errors／0 skipped**，checkstyle（main+test）0 違規，BUILD SUCCESS，17:36（Sprint 198 為 8:59；這次機器較慢，測試內容沒有相應增加）。數字取自日誌的各階段總計行，不只看結束碼。
- **真實 Tomcat**（`spring-boot:run` + curl，已登入的 BUYER）：
  - `GET /api/v2/no-such-endpoint` → **404** `{"code":"E-4041","message":"找不到請求的資源",…,"requestId":…}`，標頭 `X-Request-ID` 與內容 `requestId` 相同。
  - `POST /api/v2/auth/me` → **405**，`Allow: GET, DELETE`，`E-9000`。
  - `POST /api/v2/auth/login`（`Content-Type: text/plain`）→ **415**，`E-9000`。
  - 日誌：三種情況**各只有一行 WARN**，行內帶與回應相同的 `requestId`（如 `WARN [905f8619-…] … No route matches: GET v2/no-such-endpoint`），**無堆疊、ERROR 級為 0**。
  - 未登入 `GET` 未知路徑仍是 401（Security 先擋，與 Sprint 197 記錄一致，未改變）。
- 探針使用的帳號 `s199-<時間戳>@example.com` 建立在本機**測試用**資料庫（`make test-db-up` 的 `nk-test-pg`），非正式資料。
- **沒有跑**：前端 E2E（本輪未改前端）；Playwright 以外的瀏覽器；雲端 CI（見 §8，尚未 push）。

## 8. 下一步 / Action Items

0. **push 尚未執行**：使用者 2026-07-08 的免確認 push 授權涵蓋的是「金流／租戶隔離安全修復」的 commit；本輪是錯誤處理的修正，**不在該授權明文範圍內**，所以我只 commit、不 push，等使用者決定（`RELEASE_TRACKER` 狀態欄標為「⏳ 待 push」）。
   - **後續（Sprint 200，2026-09-26）**：使用者回覆「請依照慣例commit + Push to main」，已授權推送，見 [SPRINT_200_PLAN.md](SPRINT_200_PLAN.md) §6。
1. **部署確認**（沿用 Sprint 198）：請向維運確認前方代理有送 `X-Forwarded-Proto`；staging 第一次上線時看一眼瀏覽器主控台有無 CSP 違規訊息（有問題可先設 `CSP_REPORT_ONLY=1`）。
2. **HSTS `includeSubDomains`**：請確認所有子網域都能走 https；若不確定，最保守的做法是先縮短 `max-age` 或拿掉 `includeSubDomains`，這需要你決定。
   - **後續（Sprint 200）**：無法驗證子網域，已依保守方向**拿掉 `includeSubDomains`**，並對齊前後端判斷，見 [SPRINT_200_PLAN.md](SPRINT_200_PLAN.md) §2、§3。
3. **產品決策（不急）**：錯誤畫面是否顯示 `X-Request-ID`；PRD 錯誤封包形狀要不要與實作對齊。
4. **DEF-281**（防火牆拒絕的請求回 401 而非 400、內容缺 `requestId`）：低優先，需先查證成因再決定修法；可直接排入下一輪。
