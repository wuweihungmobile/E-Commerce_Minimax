# Sprint 171 Plan — GlobalExceptionHandler 對 48 個 ErrorCode 誤回 500（DEF-223）

**Sprint**: Sprint 171
**日期**: 2026-09-17

## 1. 起點

`DEFERRED_ITEMS_TRACKER.md` 活躍延後項目已無待排程項目（Sprint 170 的 `DEF-222` 已修復）。延續「找過去從未系統性檢查過的角度」的方法，比照 Sprint 170 找到 `JwtAuthenticationFilter` 缺專屬單元測試的手法，本輪先盤點全庫所有 `@ControllerAdvice`/`@Scheduled`/`@EventListener`/`ConstraintValidator` 類別的測試覆蓋：發現 `GlobalExceptionHandler`（`@RestControllerAdvice`，全站唯一的例外→HTTP 狀態碼轉換機制）先前**從未有專屬單元測試**，只被其他測試的 Javadoc/註解提及，從未被直接呼叫驗證過自身邏輯。

以程式而非人工比對驗證（Rule 5「如果程式碼能回答，就用程式碼回答」）：寫 Python script 抽取 `ErrorCode.java` 全部列舉常數與 `GlobalExceptionHandler.mapErrorCodeToStatus` 的 `switch` 明確列出的 case，取差集。

## 2. 缺口說明

`mapErrorCodeToStatus` 原本用「明確列出的 case + `default -> INTERNAL_SERVER_ERROR`」寫法。程式化比對後發現：136 個 `ErrorCode` 列舉值中，只有 88 個被明確列在 `switch` 的 case 裡，其餘 **48 個落入 `default`**，一律誤回 HTTP 500。

進一步逐一比對訊息語意後確認：
- 其中 **4 個**（`E_9900` 內部伺服器錯誤 / `E_9901` 資料庫錯誤 / `E_9902` Redis 錯誤 / `E_9906` 儲存操作失敗）語意本就是「伺服器端錯誤」，落入 500 剛好正確，只是靠 `default` 隱性承接，非本輪要修的缺口。
- 其餘 **44 個是真正的缺口**：語意明顯應對應 4xx（找不到資源→404、重複/已存在→409、無效狀態/無效輸入→400 或 422、無權操作→403、外部金流服務錯誤→503），卻因不在 `switch` 明確列表中而誤回 500。

**可實際觸發、已在生產程式碼路徑確認的例子**：`SettlementGenerator.getStatementById`（商家查詢結算單詳情）對不存在或非本租戶的結算單 ID 拋出 `new BusinessException(ErrorCode.E_5013, "Settlement statement not found")`；`E_5013`（「找不到對帳單」）正是 48 個缺口之一，實際會回應 HTTP 500 而非語意正確的 404。這代表 client 端若依 HTTP 狀態碼分支（本專案前端已有多處 `error.response?.status === 404`/`=== 403` 的既有慣例，見 `TenantDetail.tsx`/`TenantEditForm.tsx`/`ListingDetail.tsx`），對這類端點會誤判為伺服器錯誤而非「找不到資源」，且會污染錯誤監控（APM 誤報為 5xx 伺服器錯誤，而非真正的用戶端請求問題）。回應 body 本身的 `code`/`message`（如 `E-5013`「找不到對帳單」）不受影響，只有 HTTP 狀態碼header錯誤。

## 3. 修法決策

比照 Sprint 164（分頁上限）/165（`PageableUtils`）「找到規律後系統性一次修復，而非逐一詢問」的既有前例：以訊息文字語意 + 同數字區塊既有兄弟碼（sibling code）的既有分類為依據，將 44 個真正缺口逐一歸類到既有六個狀態碼分組：

| 狀態碼 | 判斷依據 | 新增碼 |
|---|---|---|
| 404 NOT_FOUND | 訊息以「找不到」開頭，比照同區塊既有 NOT_FOUND 兄弟碼 | E_1087, E_1090, E_1092, E_3007, E_4041, E_4102, E_4103, E_5013, E_7003, E_7007, E_8000, E_8003, E_8004, E_8005, E_8008（15） |
| 403 FORBIDDEN | 「無權操作他人X」語意，比照既有 `E_8007`（無權操作他人地址） | E_1091, E_7008, E_8009（3；`E_7008`「供應商已停用」比照 `E_2001`/`E_2002` 租戶未啟用/已停權同組） |
| 409 CONFLICT | 「已有/已存在/已被使用/已評價過/重複」等重複性衝突，或「無效的…狀態轉換」比照既有 `E_4106` 同型措辭 | E_1086, E_1093, E_1094, E_3005, E_4091, E_4092, E_5014, E_7006, E_8010（9） |
| 400 BAD_REQUEST | 業務規則允許但目前狀態/輸入不可行，比照既有 `E_5002`「此訂單無法取消」等同型措辭；`E_9009` 比照同區塊 `E_9000`~`E_9008` 驗證錯誤組 | E_1088, E_1089, E_1095, E_2009, E_4007, E_5015, E_7502, E_9009（8） |
| 422 UNPROCESSABLE_ENTITY | 「無效的X狀態/數量/設定」，比照既有 `E_5001`/`E_5006`/`E_7002`/`E_7010`/`E_4008` 同型措辭 | E_5010, E_5011, E_5012, E_6009, E_7005, E_7009, E_8001（7） |
| 503 SERVICE_UNAVAILABLE | Stripe 外部金流整合錯誤，比照既有 `E_6007`「金流服務商錯誤」同組 | E_6008, E_6010（2） |
| 500（明確列出，不再靠 default） | 語意本就是伺服器端錯誤 | E_9900, E_9901, E_9902, E_9906（4） |

**額外的設計決策**：把 `switch` 從「明確列表 + `default`」改為**完全窮舉**（移除 `default`）。Java 對 enum 的 `switch` 運算式若未覆蓋全部常數，編譯器會直接報錯（不需自行加 exhaustiveness 檢查邏輯）。這讓「新增 `ErrorCode` 卻忘記決定其 HTTP 狀態碼」這整類問題，從「靜默落入 500，等到真的有人踩到才發現」變成「編譯期直接失敗」，是本次缺口的根因防治，而非只治標。

**誠實揭露的判斷邊界**：`E_6009`（無效的退款金額）與 `E_6008`/`E_6010`（Stripe 帳戶/轉帳錯誤）的分類涉及一定程度的語意判斷（例如 `E_6009` 究竟該歸 400 或 422），非唯一無爭議的答案，已在上表寫明歸類依據供未來覆核；若判斷有誤，屬於「狀態碼語意」層級的可調整項，不影響回應 body 內容正確性。另外，本輪未觸碰任何已明確列在原 `switch` 內的既有映射，即使發現疑似語意不精確之處（例如 `E_1005`「電子郵件已被註冊」目前歸類 404 而非更符合直覺的 409，是既有既定行為且已有既有測試依賴），維持精準改動原則（Rule 3）不擴大範圍。

## 4. 修復內容

`GlobalExceptionHandler.java`：`mapErrorCodeToStatus` 的 `switch` 由「明確列表 + `default -> INTERNAL_SERVER_ERROR`」改為完全窮舉全部 136 個 `ErrorCode`，依 §3 分類表新增 48 個 case（44 個歸入對應 4xx/503 分組，4 個明確列為 500）。

## 5. 測試

- **紅燈先行**：新增本專案第一支 `GlobalExceptionHandlerTest.java`（plain JUnit，`GlobalExceptionHandler` 無任何注入依賴，直接 `new` 實例即可測試，不需 Spring 容器/DB）。核心是 `@ParameterizedTest` 逐一對全部 136 個 `ErrorCode` 呼叫 `handler.handleBusinessException(new BusinessException(code))`，斷言 HTTP 狀態碼與 §3 分類表一致（測試本身另有一個前置案例斷言期望表涵蓋全部 136 個列舉值，避免「期望表本身有缺口」）。修復前執行：**137 個案例中 44 個失敗**，失敗清單與 §3 表格的 44 個真正缺口逐一對應（如 `E_5013（找不到對帳單）不應落入 default 500 expected: 404 NOT_FOUND but was: 500 INTERNAL_SERVER_ERROR`），證實缺口為真且範圍精確。
- 套用 §4 修復後重跑：137 個案例全數轉綠，`Tests run: 137, Failures: 0`。

## 6. 驗證結果

- 紅燈階段：見 §5，44 個案例確認先失敗再轉綠，且失敗清單與程式化差集分析完全一致。
- `mvn -o compile`：每次修改後立即編譯，通過（移除 `default` 後完全窮舉仍可編譯，證實 136 個列舉值全數覆蓋）。
- `checkstyle`（main+test）：0 違規。
- **`mvn -o verify` 第一輪**（未先 `make test-db-up`）：surefire 階段 11 個既有測試因需要真實 postgres 而 `Failed to load ApplicationContext`（與本輪修改無關，屬環境前置未就緒，見 [[backend-integration-test-profile-needs-real-db]]）。啟動 Docker Desktop + `make test-db-up`（等待 6 秒讓 postgres 完成內部重啟窗口，見 [[validate-schema-doc-pg-isready-race]]）後重跑，此 11 個全數消失。
- **`mvn -o verify` 第二輪**：failsafe 階段揭露 2 個既有整合測試因本輪修復而轉紅——`M09NotificationTemplateIntegrationTest.testRenderTemplateNotFound`/`testRenderInactiveTemplate` 原本斷言 `status().is5xxServerError()`，這是「只斷言有錯誤、不斷言確切語意」的弱斷言（與既有 [[frontend-backend-contract-drift-sweep]] 記錄的「契約測試斷言非 null 幾乎無價值」同一類問題），實際掩蓋了 `E_8003`（找不到通知範本）與 `E_8001`（模板未啟用時的錯誤碼）先前誤回 500 這個事實。確認這兩個測試斷言的是**修復前的錯誤行為**後，改為精確斷言 `status().isNotFound()`/`isUnprocessableEntity()` + `jsonPath("$.code")`（比照全庫既有慣例）。
- **過程中意外發現兩個獨立缺口，皆已誠實揭露記錄**：
  1. **`NotificationTemplateService.renderTemplate` 對「模板未啟用」情境誤用 `ErrorCode.E_8001`**（該碼語意是「無效的定價規則設定」，與通知範本無關），導致使用者收到文不對題的中文錯誤訊息「無效的定價規則設定」而非「此範本已停用」之類的正確訊息。這是**選錯 ErrorCode**的問題，與本輪「`ErrorCode` 已選對但缺 HTTP 狀態碼映射」的 `DEF-223` 是不同根因，修法需要新增一個專屬 `ErrorCode`（如 `E_8011`）並決定其狀態碼，非本輪 `switch` 擴充可處理。登記為 `DEF-224`，本輪未修復，待排程。
  2. **`ErrorCode.E_8001` 的 `code` 欄位本身有錯字**：字面值是 `"E_8001"`（底線），與其餘 135 個碼一致採用連字號 `"E-8001"` 的慣例不符。撰寫 `GlobalExceptionHandlerTest`/更新 `M09NotificationTemplateIntegrationTest` 斷言 `jsonPath("$.code").value("E-8001")` 時被紅燈揪出（`expected:<E-8001> but was:<E_8001>`）。全庫 `grep` 確認無任何程式碼依賴這個錯字字面值，屬於零風險的一字元修正，已在本輪直接修復（非另開缺口單獨排程）。
- `mvn -o verify`（真實 postgres/redis，`make test-db-up`）第三輪（套用 §4 + `M09` 測試斷言修正 + `E_8001` 錯字修正後）：**1549 個單元測試（+137，含新增的 `GlobalExceptionHandlerTest`）+ 482 個整合測試（持平，`M09NotificationTemplateIntegrationTest` 2 案例斷言強化但案例數不變），0 failed**，`BUILD SUCCESS`，checkstyle（main+test）0 違規。
- 未執行 `make validate-e2e`：本輪修改僅涉及後端例外→HTTP 狀態碼映射與對應測試斷言，未變更任何前端程式碼或 API 契約的資料形狀（回應 body 的 `code`/`message` 不變，只有先前錯誤的 HTTP 狀態碼被修正），`make validate-release` 執行前會涵蓋完整 E2E 驗證。

## 7. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-223`（已修復）：`GlobalExceptionHandler.mapErrorCodeToStatus` 對 48 個 `ErrorCode`（含可實際觸發的 `E_5013` 找不到對帳單、`E_8003`/`E_8001` 通知範本渲染失敗）缺明確映射，誤回 HTTP 500；改為完全窮舉 136 個列舉值，44 個真正缺口依語意歸類到正確狀態碼，4 個本就語意正確的系統錯誤碼明確列出。`switch` 移除 `default`，日後新增 `ErrorCode` 若未同步決定狀態碼會編譯失敗。過程中意外發現並直接修復 `ErrorCode.E_8001` 的 `code` 欄位錯字（`"E_8001"` → `"E-8001"`，不符全庫連字號慣例，零風險一字元修正）。
- 新增 `DEF-224`（待排程）：`NotificationTemplateService.renderTemplate` 對「模板未啟用」情境誤用語意不符的 `ErrorCode.E_8001`（「無效的定價規則設定」），應改用專屬的新 `ErrorCode`（如 `E_8011`「此範本已停用」）並決定其狀態碼（依同型的 `E_7008`「供應商已停用」→ 403 FORBIDDEN 模式可類推）。與本輪 `DEF-223`（狀態碼映射缺口）根因不同，非本輪 `switch` 擴充可處理，故獨立登記待排程。

---

## 8. 誠實揭露總結

- **未驗證所有 44 個新增映射在生產環境的真實可觸發路徑**：僅逐一確認 `E_5013` 這一個已知可從 `SettlementGenerator.getStatementById` 觸發的具體例子；其餘 43 個是否已有既有 Service 呼叫點會實際拋出尚未逐一追蹤（部分碼如 Review 圖片模組 `E_1088`~`E_1091` 已知有呼叫點，部分如 ERP 採購單 `E_7005`~`E_7009` 需另行確認），但無論是否已有呼叫點，這都是「不應該存在的缺口」——本輪修法是把整個映射表補齊到與 `ErrorCode` 定義一致，而非只補已知會被踩到的個別碼。
- **狀態碼分類涉及有限的主觀判斷**（見 §3 邊界說明）：`E_6008`/`E_6009`/`E_6010` 等碼的歸類基於同區塊兄弟碼類比，非規格文件明文規定，未來若有更明確的業務決策應以該決策為準。
- **未觸碰既有映射中可能存在的既定语意瑕疵**（如 `E_1005` 歸 404 而非更符合直覺的 409）：屬於既有行為，非本輪「default 落入 500」缺口類型，維持精準改動原則不擴大範圍。
