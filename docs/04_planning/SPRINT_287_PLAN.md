# Sprint 287 Plan — DEF-330(a)/392/394 三項使用者裁決落地 ＋ 依賴安全審計修復

## 1. 起點與範圍

### 1.1 起點

使用者貼回 Sprint 286 總結，回覆「請繼續完成未完成任務！」，沒有任何 `==>` 逐項回覆。依既有慣例（[[inline-decision-replies-mean-proceed]] 的「無 `==>` 變體」）：這**不是**對總結裡「等你決定」項目的授權。但總結裡列出的待辦（`DEF-330(a)` shipping-templates 權限設計、`DEF-392`、`DEF-394`）全部被總結自己標為「需使用者裁決」，沒有「AI 能獨立處理」的候選可做。

因此先用 `AskUserQuestion` 取得三項決定（各附具體建議與程式碼事實依據，讓使用者能快速裁決）：

1. **shipping-templates 權限範圍**：新增 `shipping_template:read` 並含 SELLER（舊角色），避免能力退化（推薦並選定）。
2. **DEF-392 範圍**：排入，一次做完整範圍（回覆顯示＋賣家後台管理列表＋標記已讀）。
3. **DEF-394**：補上功能（比照 `PricingService` 的 `isSuperAdmin` bypass 模式）。

### 1.2 過程中插入：GitHub Actions 排程失敗（依賴安全審計）

使用者在規劃三項工作期間，貼回一則獨立的 GitHub CI/CD 錯誤通知（`依賴安全審計 / Dependency Audit` 排程失敗）。依 CLAUDE.md 的 CI 修復強制規則，先執行 `gh run view <run-id> --log-failed` 取得實際錯誤，不猜測：`npm audit --omit=dev --audit-level=high` 回報 2 個 high 漏洞：

- `next` 16.0.0–16.3.7（5 個 CVE：cache poisoning、SSRF、Draft Mode 洩漏等），修復需升到 `16.4.2`。
- `source-map-js` 1.0.0–1.2.1（event-loop DoS），一般 `npm audit fix` 即可修（透過 `next` 自身依賴鏈，非 tailwindcss 鏈）。

## 2. 執行紀錄

### 2.1 依賴安全審計修復

升級 `frontend/package.json` 的 `next: 16.3.7 → 16.4.2`。重新 `npm install` 後 `npm run lint` 新增 67 個 error（全部是既有、與本次修復無關的檔案），查證後發現是 `eslint-config-next` 宣告的 `eslint-plugin-react-hooks` 依賴範圍（`^7.0.0`）在重新安裝時從鎖定的 `7.0.1` 被動帶到 `7.1.1`，新版本引入更嚴格的 hooks 規則（`react-hooks/set-state-in-effect` 等）。用 `git stash` 搭配 `npm ci` 對照原始 lockfile 確認 `7.0.1` 本身是乾淨的（0 error），證實這是單純的依賴鏈漂移而非既有問題，改用 `npm install eslint-plugin-react-hooks@7.0.1 --save-exact` 鎖回原版本（置於 `devDependencies`，避免誤落到 `dependencies`）。

驗證：`npm audit --omit=dev --audit-level=high` 回報 0 漏洞；`npm run type-check`／`npm run lint`（0 error, 99 warning，與基準一致）／`npm run build` 全部通過。

### 2.2 DEF-330(a)：shipping-templates 專屬權限

讀碼確認：`ShippingTemplateController` 的 list（`getTemplates`）與 `calculateFee` 共用 `product:read`；`RolePermissionMapping` 顯示 `BUYER`／`GUEST` 皆持有 `product:read`，而 `SELLER`（舊角色）目前持有完整 `product:*`（含 list 的事實能力）。`calculateFee` 是 `DEF-037`（Sprint 107）已拍板的買家跨店比價設計，刻意不動。

實作：

- `Permission.java` 新增 `SHIPPING_TEMPLATE_READ("shipping_template:read", ...)`。
- `RolePermissionMapping.java` 授予 `STORE_OWNER`／`STORE_STAFF`／`SELLER`／`ADMIN`（`SUPER_ADMIN` 經 `EnumSet.allOf` 自動持有）。
- `ShippingTemplateController.getTemplates` 的 `@PreAuthorize` 改為 `shipping_template:read`；create/update/delete/calculateFee 不動。
- `ShippingTemplateControllerE2ETest` 新增 2 案例：`TC-ST-E04`（BUYER list → 403）、`TC-ST-E05`（SELLER list → 200，迴歸驗證）。

**突變驗證**：還原 `@PreAuthorize` 為 `product:read` 後重新編譯重跑，`TC-ST-E04` 正確轉紅（`Expected 403 but was 200`），還原後 5/5 全綠。

**附帶文件修正**：`API_M11_Logistics.md`（v1.0，Sprint 274 建立）既有事實錯誤——該文件 §5／§7 誤稱「`BUYER`／`GUEST` 從未被授予 `product:read`」，據此誤推論試算運費端點的「買家跨店比價」設計意圖未達成。讀碼確認 `RolePermissionMapping.java:27,33` 顯示 `BUYER`／`GUEST` 皆持有 `PRODUCT_READ`，試算運費對買家從一開始即可正常呼叫。更新 §2／§4.9／§5／§7，版本升至 v1.1。

### 2.3 DEF-392：賣家回覆評價 UI（完整範圍）

讀碼調查發現範疇比字面描述更大：後端「標記已讀/未處理」（`PUT /v2/reviews/{id}/handle`、`GET /v2/reviews/managed`）其實**已經完整存在**，只是前端從未串接——不只「回覆顯示＋管理列表」要做，既有的已讀狀態切換 UI 也要一併補上。

實作：

- `frontend/src/lib/api.ts`：`API_ENDPOINTS.reviews` 新增 `replies`／`handle`／`managed`。
- `frontend/src/services/review.ts`：`Review` 介面補上既有缺漏的 `isHandled`/`handledAt`/`handledBy`；新增 `ReviewReply` 介面與 `getReplies`／`createReply`／`setHandled`／`getManagedReviews` 方法。
- `frontend/src/components/reviews/ReviewList.tsx`（買家端）：並行抓取每則評價的回覆，顯示「店家回覆」區塊。
- `frontend/src/app/dashboard/reviews/page.tsx`（新頁面）：依處理狀態篩選（未處理/已處理，無「全部」選項——後端 `/managed` 的 `isHandled` 是必填布林，無法表達「全部」）＋分頁，比照 `dashboard/orders` 樣板；回覆表單比照 `dashboard/support/tickets/[id]` 樣板；403 時顯示友善訊息而非報錯（`/managed` 與 reply/handle 共用同一組 `room:update`／`product:update` 權限，`STORE_STAFF` 連列表都看不到，不需要在單一按鈕層級做角色隱藏）。
- `frontend/src/app/dashboard/page.tsx`：`QUICK_LINKS` 新增「評價管理」入口。

驗證：`tsc --noEmit`／`eslint`／`npm run build` 全部通過。新增 `frontend/e2e/at-review-reply-real.spec.ts`（真實後端＋兩個真實角色）：消費者下單付款、店主確認出貨並送達（`PATCH .../status`→`POST /v2/logistics`→`PUT .../status?status=DELIVERED`）→ 買家對已送達商品評價 → 店主在真實瀏覽器的 `/dashboard/reviews` 送出回覆、標記為已處理 → 買家端評價頁看得到回覆，共 5 案例。

**E2E 規格在真實後端暴露的問題與修正（全部是規格自身的缺陷，沒有一項是功能缺陷）**：規格第一次對真實後端執行時，前四項依序暴露——每一項都是在上一項修好之後才輪到下一項，所以是跨好幾輪 `make validate-e2e` 才全部找出來的：

1. `POST /v2/logistics` 回傳的欄位叫 `logisticsId` 而不是 `id`，URL 變成 `/v2/logistics/undefined/status`，後端回 `E-9000`（400）。
2. 每個 `test()` 都是全新的 `page`（`serial` 模式只保證順序，不共用瀏覽器狀態）：`RVREPLY-02` 還沒導覽就讀 `localStorage`（`SecurityError`），`RVREPLY-04` 漏了還原登入。
3. **`RVREPLY-04` 的同步訊號設計錯誤**：用 `getByText('已處理').first()` 當「標記完成」的訊號，但按鈕本身就叫「標記為已處理」，按下後頁面立刻重新整理列表、按鈕隨整筆評價消失——早一點評估恆為真（比對到按鈕自己），晚一點則找不到任何「已處理」字樣（兩種結果都實際出現過：一次斷言恆真之後立刻查 API 得到 0 筆，因為 GET 搶在 `PUT /handle` 提交之前；重現時則是斷言本身逾時）。改成 `Promise.all([waitForResponse(PUT /handle), click])` 等伺服器回應，再驗畫面搬移（待處理列表變空 → 切到「已處理」篩選看到這筆與「標記為未處理」按鈕），最後用 `PUT` 回應裡的精確 `reviewId` 驗 `/v2/reviews/managed`（`toContain`／`not.toContain`，取代原本寬鬆的「任一筆不在未處理列表」）。
4. **`RVREPLY-05` 漏了還原買家登入**：`GET /v2/reviews/listing/{id}` 是 `@PreAuthorize("isAuthenticated()")`，匿名訪客得 401、頁面顯示「無法載入評價」。這是現行設計（不是本輪要改的產品決策），規格要用買家身分。這一項在前幾輪從未被執行到，因為 `RVREPLY-04` 失敗後它一律「did not run」。
5. **登入密度**：原本每個案例都重新登入，整支規格共 8 次登入（登入端點每個來源 IP、每個路徑每分鐘 30 次，整個 E2E 套件共用）。比照 Sprint 242 的作法，只在 `beforeAll` 登入（店主、買家各一次，加上開店流程內的管理員，共 4 次），各案例用 `addInitScript` 還原存下的 `localStorage`。修正前同一支規格對活著的全棧跑一次約 50 秒（含 `RVREPLY-04` 失敗案例的 5 秒逾時、`RVREPLY-05` 未執行），修正後連跑 3 次皆 5/5 通過、每次約 12 秒。

**範疇外發現（未處理，僅記錄）**：FRD `BR-M08-07`（既有 `DEF-373`）與 `ReviewReplyService.createReply` 目前程式碼註解對擁有權檢查的描述互相矛盾——文件說「需確切擁有者，未修復」，程式碼註解說「已修復成同租戶或 admin」。本輪未核對程式碼實際行為與哪一邊一致，留給未來 Sprint 處理 `DEF-373` 時一併確認並校正文件。

### 2.4 DEF-394：ADMIN 跨租戶通知模板管理

讀碼確認既有兩種 bypass 模式：`PricingService` 的 `isSuperAdmin`（**只對 SUPER_ADMIN 生效**，ADMIN 不算）、`ReturnRequestService.isAdmin()`（ADMIN／SUPER_ADMIN 皆放行）。`RolePermissionMapping` 把 `notification_template:*` 歸類為 ADMIN 的平台端權限（非 SUPER_ADMIN 專屬），故採用後者模式。另確認 `TenantContextFilter`（`DEF-038`）刻意鎖死 ADMIN 不可用 `X-Tenant-ID` header 切換 `TenantContext`，修法不能靠這個機制，必須在 Service 內部另外判斷。

實作：

- `NotificationTemplateController` 新增私有 `isAdmin(UserPrincipal)` 方法（比照 `PricingController` 的 `@AuthenticationPrincipal` 取得角色字串）。
- `getTemplate`／`updateTemplate`／`deleteTemplate`：Service 方法新增 `boolean isAdmin` 參數，為真時跳過擁有權比對；`updateTemplate` 的模板代碼唯一性檢查改比對模板**實際所屬**租戶（`isAdmin ? template.getTenantId() : tenantId`），而非管理員自己的（系統）租戶。
- `getTemplates`（list）／`createTemplate`：新增可選的 `targetTenantId`（分別為 query 參數與 `CreateRequest` 欄位），僅 `isAdmin(principal) && targetTenantId != null` 時生效，否則沿用呼叫者自己的租戶（非管理員帶這個參數會被忽略，不是安全退化）。

測試：`NotificationTemplateServiceTest` 新增 3 案例（`getTemplate`/`updateTemplate`/`deleteTemplate` 的 isAdmin bypass，含驗證 `updateTemplate` 的代碼衝突檢查確實比對模板實際租戶）；`M09NotificationTemplateIntegrationTest` 新增 6 案例（跨租戶 get/update/delete/list/create 皆以真實 ADMIN JWT 對另一個真實租戶的模板驗證，另加一個「非 ADMIN 帶 `targetTenantId` 應被忽略」的安全迴歸案例）。

**突變驗證**：暫時把 Controller 的 `isAdmin()` 改成固定回傳 `false`（同時覆蓋 Service 的 `isAdmin` 旁路與 Controller 的 `targetTenantId` 解析兩處邏輯），重新編譯重跑：新增的 8 個案例全部轉紅（3 個單元 `BusinessException: Access denied`、5 個整合測試 403／空列表／歸屬錯誤），第 9 個案例（非 ADMIN 忽略 `targetTenantId`）維持綠（不受這個 mutation 影響，符合預期）。還原後 25/25（單元）、20/20（整合）全綠。

**刻意不做的事**：未新增任何前端 UI。目前完全沒有「ADMIN 選定店鋪管理通知模板」的頁面雛形；若未來需要，`admin/tenants/[id]/features`（先選租戶、路由帶 `tenantId`）的既有樣板可直接參考。

### 2.5 過程中發現（範疇外，已登記 DEF-396，未修復）：限流回應的中文訊息被編碼成 `?`

重跑 `make validate-e2e` 時，既有規格 `at-store-member-revocation-real` 的 `E2E-MREV-02` 失敗（「應已登入並持有 accessToken」，`Received: null`）。失敗案例的登入頁快照顯示錯誤區塊內容是 `???????`，而 `helpers/auth.ts` 的 `submitLogin` 本來就有「被限流就等 2.5 秒重試」的機制，卻沒有重試。同時依 Sprint 242 的經驗（自己新增的規格登入太密會連帶拖垮不相干的規格）把新規格的登入次數由 8 降到 4（見 §2.3 第 5 點）。針對「為什麼重試沒生效」追查後：

- **實測**：起真實全棧，對 `POST /v2/auth/login` 用不存在的 email 連打 45 次，第 1~31 次回 401、第 32 次起回 429；429 回應的標頭是 `Content-Type: application/json;charset=ISO-8859-1`，本體 `message` 逐位元組確認為 7 個 `0x3F`（`?`）。`ErrorCode.E_9904` 的原文是「已超過速率限制」（7 個字）。
- **讀碼**：`LoginRateLimitFilter`／`RateLimitFilter`／`TenantContextFilter` 三處用 `setContentType("application/json")`（不含 charset）後直接 `getWriter().write(...)`，`application.yml` 沒有 `server.servlet.encoding.*`，Tomcat 預設 ISO-8859-1，中文被替換成 `?`。
- **後果**：helper 的重試以 `innerText.includes('速率限制')` 偵測限流，對 `???????` 永遠不會命中，所以任何一次限流都直接變成硬失敗。這也讓 Sprint 286 `DEF-395`（註冊端點限流重試，同樣以文字比對偵測）的修法對真實 429 無效（推論，詳見 DEF-396 與 DEF-395 的補述）。

**為什麼本輪不修**：這是生產程式碼變更，需要自己的測試、突變驗證與後端全量驗證，且不在使用者拍板的三項範圍內。本輪只做規格自身該負責的部分（降低登入密度）；建議下一輪處理 `DEF-396`，修好後 helper 的文字偵測才會真正生效（也建議順手改為以 HTTP 429 狀態碼偵測）。**只有 `LoginRateLimitFilter` 做過線上實測**，`RateLimitFilter`、`TenantContextFilter` 是依相同寫法推論。

## 3. 全量驗證

- **後端 `mvn -o clean verify`**（2026-10-19，驗證開始後後端程式碼未再變動）：`BUILD SUCCESS`，單元測試 2290 筆（0 failed／0 errors／0 skipped）、整合測試 873 筆（0／0／0；較 Sprint 286 基準 865 增加 8＝`M09NotificationTemplateIntegrationTest` +6、`ShippingTemplateControllerE2ETest` +2），Checkstyle main／test 皆 0 違規，PMD 通過，耗時 17:09。
- **前端**：`npm run type-check` exit 0；`npm run lint` 0 error／99 warning（與升級前基準一致，本輪新增或修改的檔案沒有新增 warning）；`npm audit --omit=dev --audit-level=high` 0 漏洞；`npm run build`（Next.js 16.4.2）成功（守門腳本的 `npm ci && npm run build` 步驟亦成功）。
- **`make validate-e2e`**（2026-10-20，重用已確認比所有原始碼都新的 JAR 與 `.next`）：**175 passed／4 skipped／0 failed**（6.3 分）；backend 以 `ddl-auto=validate` 對 Flyway 乾淨 DB 啟動成功（schema 無漂移）；新規格 `at-review-reply-real.spec.ts` 5 案例皆通過。**如實揭露**：含新規格的完整守門共跑了 4 次，前 3 次失敗、這是第 4 次才全綠（新規格加入前的 Next.js 升級驗證那一次是全綠的）。前 3 次的失敗原因：新規格自身缺陷（§2.3），以及既有規格 `E2E-MREV-02`（§2.5，限流回應亂碼使 helper 的重試失效）。全綠只代表「降低新規格的登入密度後這次沒撞到限流」，限流重試機制本身仍然失效（`DEF-396` 未修復），所以這個套件對登入密度的容忍度並沒有因本輪而改善；只跑了一次完整守門，沒有重複跑來估計剩餘的間歇失敗機率。

## 4. 文件更新

- `DEFERRED_ITEMS_TRACKER.md`：`DEF-330`／`DEF-392`／`DEF-394` 條目補上 Sprint 287 修復說明；補登 Sprint 286 原先缺漏的頁首版本號／摘要更新（Sprint 286 進行時只更新了個別 DEF 條目，未同步頁首，屬文件漂移，本輪一併補登）。
- `API_M11_Logistics.md`：v1.0 → v1.1，更正 `BUYER`／`GUEST` 的 `product:read` 既有事實錯誤。
- `API_M09_Notification.md`：v1.0 → v1.1（收尾時的文件一致性檢查發現，已於 push 後補登）。補登兩件事：Sprint 286（`DEF-330a`）範本列表／詳情已改為 `notification_template:read`，但文件仍寫 `isAuthenticated()`（與程式碼不符的既有漂移，§2／§4.12／§4.13／§5／§7 一併更正）；Sprint 287（`DEF-394`）的 ADMIN／SUPER_ADMIN 跨租戶管理（`targetTenantId`、擁有權旁路、更新時唯一碼檢查比對範本實際所屬租戶）。以程式碼為準逐條核對（`RolePermissionMapping` 的 `NOTIFICATION_TEMPLATE_READ` 持有者＝STORE_OWNER／STORE_STAFF／ADMIN／SUPER_ADMIN）；`ApiRouteDocDriftTest` 重跑通過（2 tests、0 failures）。
- `DEFERRED_ITEMS_TRACKER.md`（補充）：新增 `DEF-396`，並在 `DEF-395` 補述其修法同樣受影響。

## 5. 已知限制／留給未來 Sprint

- `DEF-373`（回覆評價擁有權檢查三處不一致）與本輪發現的 FRD／程式碼敘述矛盾，未在本輪核對與校正。
- `DEF-394` 僅補後端能力，前端管理介面留給未來 Sprint 視實際需求決定是否要做。
- `DEF-394` 的 `targetTenantId` **不驗證目標租戶是否存在**（讀碼確認：`notification_templates.tenant_id` 可為 `NULL` 且沒有外鍵，`createTemplate` 也沒有檢查；未實測）。ADMIN／SUPER_ADMIN 帶不存在的租戶 ID 會建出不屬於任何店鋪的範本。只有管理員角色能觸發，風險低，已寫入 `API_M09_Notification.md` §7，未登記為獨立缺陷。
- `DEF-396`（限流與租戶標頭檢查回應的中文訊息被編碼成 `?`）未修復，建議下一輪處理；修好前，E2E 套件對登入密度的容忍度沒有改善（§3 最後一點）。
- 150+ 個既有 `getCurrentTenant()` 呼叫處的語意稽核（`DEF-330(c)` 的 drift 鎖定機制之外）仍留給未來 Sprint 按需抽查。
