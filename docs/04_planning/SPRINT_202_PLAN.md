# Sprint 202 Plan — 容器錯誤分派的回應補上安全標頭（DEF-282 結案）

**Sprint**: Sprint 202
**日期**: 2026-09-26

## 1. 起點

使用者貼上 Sprint 201 收尾總結，並在「請你決定」與「維運」兩處逐項回覆：

| 項目 | 使用者回覆 | 我的處理 |
|---|---|---|
| 要不要 push（Sprint 201） | 「commit + Push to main」 | 已 push（`2a8b9fd..6c62bfa main -> main`），見 §2 |
| DEF-282 要不要修 | 「修，請給我 sample 與互動選擇」 | 以 `AskUserQuestion` 提供三個方案（附程式碼範例）；使用者實際選了「共用政策＋控制器套用」，並選「修完後 commit 並 push」（見 §3） |
| 維運三件事 | 「不變」 | 我做不到（代理的 `X-Forwarded-Proto`、staging 的 CSP、確認 Sprint 198 有沒有部署過），照舊留給使用者 |

**授權範圍**：兩次 push 授權（Sprint 201、Sprint 202）都是使用者針對該次的明確回覆，未說明為常設；2026-07-08 的免確認授權仍只涵蓋金流／租戶隔離。

## 2. Sprint 201 的 push（含一次環境失敗）

1. 第一次 `make validate-release` 在 act 拉 image 時失敗：`docker pull postgres:18-alpine` 回 **500 Internal Server Error**。查證是 Docker daemon 掛了（連 `docker images` 都 500），與程式碼無關——記憶中已記錄的「長時間使用後 daemon 500」現象。
2. 單純 `quit` Docker Desktop 沒關乾淨（行程仍在），`pkill -f /Applications/Docker.app` 後冷啟動，約 10 秒就緒；`docker pull` 確認正常後重跑守門。
3. 重跑通過：act 三個 job 全過、後端整合 541 個 0 失敗、E2E **71 passed／4 skipped**（與基準一致）。FULL 記錄綁定 `6c62bfa` 後 push，pre-push 放行。
4. ✅ 雲端 CI 全綠（run 36242648761）。

## 3. 給使用者的選項與決定

成因見 [SPRINT_201_PLAN.md](SPRINT_201_PLAN.md) §5：Spring Security 6.2.4 的 `HeaderWriterFilter` 沿用 `OncePerRequestFilter` 預設，不處理容器 ERROR 分派，所以經 `sendError` 轉送 `/error` 的回應沒有任何安全標頭。

**為什麼不能直接「叫 Spring 的過濾器也處理 ERROR 分派」**：我用 `javap` 查了 6.2.4——`HeaderWriterFilter` 的 `headerWriters` 是 `private`、`writeHeaders` 是 package-private，拿不到 Spring 已建好的政策，必須由我們自己持有一份。這使「單一來源」成為設計重點。

| 方案 | 內容 | 取捨 |
|---|---|---|
| **共用政策＋控制器套用（使用者選定）** | 新增 `SecurityHeaderPolicy` 一份政策，`SecurityConfig` 與 `ApiErrorController` 都用它 | 單一來源、無法漂移；代價是 `SecurityConfig` 的 headers 區塊要改寫（`defaultsDisabled()`＋逐一 `addHeaderWriter`） |
| 控制器直接補標頭 | 只改 `ApiErrorController`，手動 `setHeader` | 改動最小；但政策存在兩處，只能靠對照測試事後抓漂移 |
| 共用政策＋ERROR 專用過濾器 | 同樣抽出政策，套用點改成 Security 鏈中只在 ERROR 分派執行的過濾器 | 涵蓋面最廣；多一個類別，且目前 `ApiErrorController` 已是唯一錯誤端點，額外涵蓋面暫時用不到 |

## 4. 修法

| 檔案 | 變更 |
|---|---|
| `SecurityHeaderPolicy`（新） | 整個服務安全標頭政策的**唯一定義處**：`nosniff`、`X-XSS-Protection: 0`、`Cache-Control`／`Pragma`／`Expires`、`X-Frame-Options: DENY`、CSP、`Referrer-Policy`，以及 HSTS（不含 `includeSubDomains`，只在 HTTPS 或代理告知 https 時送）。提供 `writers()` 與 `apply(request, response)`；`isHttpsRequest` 從 `SecurityConfig` 搬入 |
| `SecurityConfig` | `.headers(...)` 改為 `defaultsDisabled()` 後逐一套用 `SecurityHeaderPolicy.writers()`；移除搬走的 `isHttpsRequest` 與兩個不再使用的 import |
| `ApiErrorController` | `error()` 開頭呼叫 `SecurityHeaderPolicy.apply(request, response)` |

**行為等價的證據**（重構最怕悄悄少一個標頭）：新測試對「一般 401」逐值比對 8 個標頭，**在重構前的基準線與重構後各跑一次都綠**；既有 `SecurityHeadersIntegrationTest` 11 個也不變。真實 Tomcat 實測的一般 401 標頭集合為（8 個，與政策一致）：`Cache-Control: no-cache, no-store, max-age=0, must-revalidate`、CSP、`Expires: 0`、`Pragma: no-cache`、`Referrer-Policy: no-referrer`、`X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`X-XSS-Protection: 0`。

## 5. 測試與驗證

**紅燈先行**：在 `ContainerErrorDispatchIntegrationTest` 新增 4 個測試（`RANDOM_PORT` 真實伺服器）。修改實作前，基準線 9 個測試 **3 紅**（ERROR 分派的兩案例＋HSTS，失敗訊息是標頭清單為 `[]`），一般 401 案例綠——證明預測的標頭集合正確。

| 測試 | 守什麼 |
|---|---|
| IT-ERRDISP-05（3 案例：一般 401、`%0A`、`//`） | 三條路徑的 8 個安全標頭逐值相同，且各恰好一個值（`containsExactly`，日後若過濾器與控制器重複寫入會抓到） |
| IT-ERRDISP-06 | ERROR 分派的回應與其他回應一樣，僅在 HTTPS（含 `X-Forwarded-Proto`）才帶 HSTS |

**突變驗證**（改壞實作、確認測試會紅、還原並 `diff` 比對）：

| 突變 | 結果 |
|---|---|
| A：拿掉控制器的 `SecurityHeaderPolicy.apply(...)` | 3 紅（與基準線相同）；**MockMvc 的 11 個 `SecurityHeadersIntegrationTest` 仍綠**——再次印證 MockMvc 看不到 ERROR 分派，新測試不可或缺 |
| B＋C：政策的 CSP 字串改掉、HSTS 改成含 `includeSubDomains` | **14 紅**：真實 Tomcat 測試 4 個（**連一般 401 案例都紅**）＋既有 MockMvc 10 個。證明一般路徑與 ERROR 路徑確實吃同一份政策 |

**全量**：`mvn -o clean verify` **單元 1732（不變）＋整合 545（Sprint 201 的 541＋4），0 failed／0 errors／0 skipped**，checkstyle main／test 皆 0 違規，9:58。

**真實 Tomcat 交叉驗證**（`spring-boot:run`；有效 JWT：352 字元、`GET /v2/auth/me` 回 200，先驗證 token 才用）。「標頭」欄是與一般 401 的 8 個標頭逐值比對：

| 請求 | 結果 | 標頭 |
|---|---|---|
| `%0A`（換行） | 400 `E-9000`，`requestId` 與 `X-Request-ID` 吻合 | 與一般 401 相同 |
| `//`、`;`、`%2e`、`%25` | 同上 | 相同 |
| `%0A`／`//` ＋有效 token | 同上 | 相同 |
| 未知路徑 ＋有效 token | 404 `E-4041`（Sprint 199 路徑） | 相同 |
| 直接 `GET /api/error` 無 token | 401 `E-1000`（未被放行） | 相同 |
| `X-Forwarded-Proto: https` ＋`%0A` | 帶 `Strict-Transport-Security: max-age=31536000` | — |
| 純 http ＋`%0A` | 無 HSTS（符合預期） | — |

後端日誌：**ERROR 0 行、含 `FORGED` 0 行、堆疊 0 行**（WARN 只有 MinIO 未啟動、既有的 404 路由 WARN、open-in-view，與本輪無關）。

## 6. 揭露

**新發現 → DEF-283（未修、非本輪造成、應用程式碼修不到）**：實測各種防火牆拒絕種類時，`%2f`（編碼斜線）與 `%5C`（編碼反斜線）的表現**與其他不同**：回 Tomcat 內建的 HTML 錯誤頁（`HTTP/1.1 400`、`Content-Type: text/html`、`Connection: close`），沒有 JSON 封包、沒有 `X-Request-ID`、沒有任何安全標頭，且**應用日誌完全沒有痕跡**。這表示請求在 Tomcat 連接器層就被拒絕，根本沒進到 Servlet／Spring／Security，所以 Sprint 201 的 `ApiErrorController` 與本輪的標頭政策都碰不到。成因是**推論**（表現符合 Tomcat 對編碼斜線／反斜線的預設拒絕），我沒有查對 Tomcat 的設定項。實際風險低（靜態頁，只有「HTTP Status 400 – Bad Request」，不回顯輸入、不含版本）。要處理得動 Tomcat 設定或由前方代理統一處理，屬維運／基礎設施層的決定，已登記為 🟢 `DEF-283`。

**這也補上 Sprint 201 留下的未實測項**：Sprint 201 只測了 `%0A` 與 `//`。本輪實測 `;`、`%2e`、`%25` 也走 Spring 路徑且行為一致；`%2f`、`%5C` 則屬上述 Tomcat 層。

**仍未實測**：5xx 路徑（沒有找到現有程式碼中會走到「`sendError(5xx)` 進 ERROR 分派」的具體案例）。

**其他**：
- 政策的「單一來源」只涵蓋**後端**。前端的標頭政策在 `next.config.ts`／`proxy.ts`，針對 HTML 頁面，內容本來就不同，本輪沒動。
- 未動前端；push 前的完整守門與 push 結果，見 §7。
- `SecurityHeaderPolicy` 的靜態初始化放了一份不可變的寫入器清單；各寫入器實作無狀態（Spring 本身也跨請求共用），故共用安全。
- token 預算：CLAUDE.md 規定每任務 4,000 tokens，這輪明顯超過（環境失敗排查、紅綠燈、兩輪突變驗證、全量測試、真實 Tomcat 實測），公開揭示而非默默超支。

## 7. Push

見 [RELEASE_TRACKER.md](RELEASE_TRACKER.md) Sprint 202 列（push 後回填）。

## 8. 下一步

1. **DEF-283**（Tomcat 層拒絕編碼斜線／反斜線回 HTML 頁）：先決定值不值得處理，以及由 Tomcat 設定或前方代理處理。
2. 沿用 Sprint 198～201 的維運確認：前方代理有送 `X-Forwarded-Proto`；staging 首次上線看瀏覽器主控台有無 CSP 違規（有問題先設 `CSP_REPORT_ONLY=1`）；已 push 的 Sprint 198 帶過 `includeSubDomains`，若已部署且瀏覽器收過，需送 `max-age=0`；日後確認所有子網域走 https 才考慮加回 `includeSubDomains`。
