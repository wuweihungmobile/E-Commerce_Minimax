# Sprint 203 Plan — 文件一致性檢查：錯誤契約、安全標頭、時區與定價規格補齊（DEF-280 結案）

**Sprint**: Sprint 203
**日期**: 2026-09-26

## 1. 缺口盤點結果

### 1.1 起點

使用者要求「回歸 AISDLC 流程，下一步該進行什麼？」。比照 Sprint 145 的盤點手法（`git`／`gh run list`／必要產出逐項核對／待辦池逐列讀）：

| 檢查 | 結果 |
|------|------|
| 交付狀態 | 工作區乾淨、與 `origin/main` 同步（`0 0`）；S202 雲端 CI 全綠 |
| 必要產出（依 [SPRINT_ARTIFACT_CONVENTION.md](../05_development/SPRINT_ARTIFACT_CONVENTION.md)） | S202 Plan、RELEASE_TRACKER 列、tracker 更新齊全 |
| 待辦池 | 無高優先級；僅 DEF-283（低）待處理，其餘全標「不排入排程」 |

**流程債沒有，但發現兩個 AISDLC 層級的問題**：(1) S182 起的 Sprint 幾乎都是「自選掃描角度」找缺陷，產品面（[PRODUCT_BACKLOG.md](PRODUCT_BACKLOG.md)）自 Sprint 100 前後就沒再追蹤；(2) **規格追不上實作**——S195～S202 的行為只存在於 Sprint 計畫與程式碼。我建議 Sprint 203 做 AISDLC 規定「實作變更後必跑」的 document-consistency-check，使用者回覆「請依照建議，繼續完成任務！做完後，依序完成ＡＢＣ」。

### 1.2 盤點方法

1. **關鍵字 grep**：對 S191～S202 的每項行為挑關鍵字，在 `docs/`（排除 Sprint 計畫與 tracker）與根目錄 README 搜尋是否已有規格。
2. **讀程式碼取事實**：文件內容全部來自 `ApiResponse`、`GlobalExceptionHandler`、`ErrorCode`、`ApiErrorController`、`SecurityHeaderPolicy`、`RequestIdFilter`、`SecurityConfig`、`proxy.ts`、`next.config.ts`、`BusinessTime`、`PricingService` 的實際內容，不憑 Sprint 計畫的敘述。
3. **腳本比對**：以 perl 抽出 PRD 錯誤碼表與 `ErrorCode.java`，逐碼比對；並機械產生錯誤碼對照表（`ErrorCode` 的 code／訊息＋`GlobalExceptionHandler.mapErrorCodeToStatus` 的 HTTP 狀態，兩邊互相驗證：138 碼、138 個狀態，無多無少）。

### 1.3 查出的差異

| # | 差異 | 證據 | 處理 |
|---|------|------|------|
| 1 | **回應封包有三種互相矛盾的定義** | SRD §4.2／§4.3：數字 `code` 加 `errors[].code`；PRD §9.17／§16／§17.2.3：巢狀 `{error:{code,message,details,requestId,timestamp}}`；實作：扁平 `{success,code,message,errors[{field,message,rejectedValue}],timestamp,requestId}` | 以實作為準（使用者拍板），新增 [API_Error_Codes.md](../02_architecture/API_Error_Codes.md)，SRD §4 改寫，PRD 加修訂註記 |
| 2 | **PRD 錯誤碼表與實作的碼字串重疊但意義不同** | PRD 錯誤碼表 36 列去重後 **23 個碼**；**19 個與 `ErrorCode.java` 的碼字串重疊，其中 18 個意義不同**（PRD `E-2001`＝JWT 無效 401，實作 `E-2001`＝租戶未啟用 403）；僅 `E-2003` 意義相同；4 個碼 PRD 獨有（`E-2020`、`E-4012`～`E-4014`） | 同上；FRD（約 95 處引用）只在檔頭加註，不逐處改寫 |
| 3 | SRD 模組設計 §6 描述的例外階層與錯誤碼表**從未存在** | `ECommerceException`、`EntityNotFoundException`、`ErrorResponse.of(...)` 全庫找不到；該節 17 個錯誤碼中 **12 個與實作不符**（意義不同或不存在）、5 個大致相符 | §6 改寫為實際架構（`BusinessException`＋`ErrorCode`＋`GlobalExceptionHandler`） |
| 4 | 安全標頭（HSTS、後端 CSP、前端 nonce CSP、`Referrer-Policy`）、`X-Request-ID`、CORS 環境變數、`CSP_REPORT_ONLY` 沒有任何規格 | `docs/`（排除 Sprint 計畫與 tracker）grep `Referrer-Policy`／`Strict-Transport`／`SecurityHeaderPolicy`／`CSP_REPORT_ONLY`／`APP_CORS_ALLOWED_ORIGINS`：**0 筆**（`X-Request-ID` 只在 PRD §16.4.1） | SRD 新增 §5.4 |
| 5 | 營運時區（Asia/Taipei）與半開區間慣例沒有規格 | grep `Asia/Taipei`／`BusinessTime`／`半開區間`：0 筆（PRD §6.2.1 結算時區已於 S195 補，不重複） | SRD 新增 §4.4 |
| 6 | 定價金額精度（`HALF_UP` 逐晚 2 位）、`config` 數值範圍（DEF-268）沒有規格；FRD 寫的驗證錯誤碼是 `E-4001`，實作是 `E-8001` | grep `E_8001`：只出現在舊 Sprint Review | SRD 模組設計新增 §5.4 |
| 7 | PRD §9.17「所有 POST/PUT/DELETE 必帶 `X-Idempotency-Key`」**沒有落實** | 實作標頭名 `Idempotency-Key`；只有 `POST /v2/bookings`、`POST /v2/checkout/mixed` 兩個端點讀取，且皆選帶 | **不是文件問題**，登記 DEF-285；PRD 該列加註現況 |
| 8 | `API_Index.md` 僅 48 個端點，controller 有約 305 個 `@*Mapping` 註解 | grep 計數 | 檔頭加註揭露，登記 DEF-286 |
| 9 | `E_1005`（電子郵件已被註冊）回 **404** | 產生對照表時 138 碼的狀態碼攤開可見；`GlobalExceptionHandlerTest` 把它釘為 404 | 登記 DEF-284，不修 |
| 10 | `PRODUCT_BACKLOG.md` #10「M07 真實金流」仍列為待開的 13 SP 大項 | 程式已於 Sprint 49~56、80 完成（`PaymentStateService`、`TenantStripeConnectService`、`TransferService`） | 更正並標註 §2／§3 為 Sprint 27 快照 |
| 11 | `DEFERRED_ITEMS_TRACKER.md` 版本歷史鏈**缺 Sprint 201 一節**（v2.90＝S200、v2.91＝S202） | grep | 本輪 v2.92 條目揭露（S201 內容記在 DEF-281 列與 [SPRINT_201_PLAN.md](SPRINT_201_PLAN.md)）；這是 [[deferred-items-tracker-drift]] 的第五次同型漂移 |

### 1.4 更正與偽陽性

- **我在向使用者提問時寫「32 個錯誤碼同碼不同義」是錯的**：32 是 join 結果的**列數**（PRD 表中同一個碼在多個小節重複出現）。正確數字是去重後 23 碼、19 重疊、18 不同義（§1.3 #2）。方向不變（仍是「文件對齊實作」），但數字已在文件中更正，不沿用錯誤版本。
- **SRD 模組設計 §6.3 一度想寫「17 個全部不符」也是過度斷言**：逐碼查後 12 個不符、5 個大致相符，文件據實記載。
- **未把 PRD 結算規格列為缺口**：S195 已補 PRD §6.2.1 與 schema 文件，重疊部分不重寫。

## 2. 使用者決策（AskUserQuestion 拍板紀錄）

| # | 問題 | 使用者裁定 |
|---|------|-----------|
| 1 | 錯誤契約（DEF-280）文件怎麼處理 | **文件對齊實作＋自動守門**（推薦選項）：不改實作成 PRD 形狀（全站 breaking change） |
| 2 | A（忘記密碼＋email 驗證）寄信怎麼處理 | 先補需求＋寄信介面，先用日誌型 Mock 實作（用於後續 A） |
| 3 | A：要在哪些操作要求已驗證 email | 只在「開店申請」要求（用於後續 A） |
| 4 | C（DEF-283）怎麼處理 | 先實測可行性，再決定（用於後續 C） |

問題 2～4 是為讓使用者一次拍板、避免後續逐項中斷而在本輪一併提出，**實作不在本輪**。

## 3. 實作內容

**新增**
- [docs/02_architecture/API_Error_Codes.md](../02_architecture/API_Error_Codes.md)：回應封包、錯誤來源對應、標準標頭、138 碼對照表（機械產生）、與 PRD 的差異、維護規則。
- [backend/src/test/java/com/nextkey/ecommerce/shared/exception/ErrorCodeDocDriftTest.java](../../backend/src/test/java/com/nextkey/ecommerce/shared/exception/ErrorCodeDocDriftTest.java)：對照表守門。

**修改**
- SRD_System_Architecture.md → v1.1：§4.2／§4.3 改寫；新增 §4.4（時間與時區）、§5.4（安全標頭／請求追蹤／CORS）。
- SRD_Module_Technical_Design.md → v1.1：§6 改寫為實際架構；新增 §5.4（價格精度與 `config` 範圍）。
- PRD v1.0 Final：§9.17 兩列、§16 開頭、§17.2 加**修訂註記**（內文不動）。
- FRD：檔頭加修訂註記。
- API_Index.md：檔頭加錯誤契約連結與完整性揭露。
- PRODUCT_BACKLOG.md：v2.2。
- DEFERRED_ITEMS_TRACKER.md：DEF-280 結案註記；新增 DEF-284／285／286。

**未動任何 `src/main`。**

## 4. 測試

`ErrorCodeDocDriftTest`（1 個測試方法）：以 `ErrorCode.values()` 的 code／訊息，加上**真的丟 `BusinessException` 給 `GlobalExceptionHandler`** 取得的 HTTP 狀態碼（不重抄它的 switch），與文件第 4 節逐列比對；只讀第 4 節（第 5 節的 PRD 對照例表也以 `| E-XXXX |` 開頭，不能混入）；文件缺檔、缺列、多列、內容不同、`ErrorCode` 的 code 字串重複、文件內重複列，都會失敗，失敗訊息直接列出可照抄的表格列。

**沒有紅燈階段**：文件先寫、測試後寫，第一次執行即綠燈。依 Rule 9 改以**突變驗證**補強（暫時改動文件，跑測試，再還原）：

| 突變 | 結果 |
|------|------|
| `E-1005` 的 HTTP 404 改 409 | 紅燈，訊息列出「內容不同」與應為的列 |
| 刪掉 `E-9906` 列 | 紅燈，「文件缺少」 |
| 多一個不存在的 `E-9999` | 紅燈，「文件多出」 |
| `E-9000` 訊息加一個 `!` | 紅燈，「內容不同」 |

四種突變後皆已還原（以 `diff` 確認與原檔逐位元相同）。

**限制（誠實揭露）**：守門只涵蓋第 4 節對照表；文件第 1～3 節（封包、來源、標頭）與 SRD／PRD 其他部分**沒有自動守門**，靠人工核對。

## 5. 驗證結果

- `mvn -o test checkstyle:check@checkstyle-test`（真實 postgres/redis 已啟動）：**單元 1733 個**（基準 1732，+1 即 `ErrorCodeDocDriftTest`），**0 failures、0 errors**；測試程式碼 checkstyle **0 violations**；`BUILD SUCCESS`。
- **第一次執行有 11 個 Error（5 個 Spring 上下文測試類別）**：根因是測試用 PostgreSQL 沒開（`Connection to localhost:5432 refused`，即 [[backend-integration-test-profile-needs-real-db]] 記載的情況），與本輪改動無關；但不當作「無關」略過，`make test-db-up` 後重跑整套，全數通過。
- **未執行**：整合測試（`mvn verify`）與前端 E2E——本輪沒有動 `src/main`、沒有動前端，只新增一個單元測試與文件。push 前的完整守門（`make validate-release`）屬 push 流程，見 §7。

## 6. 範圍外（延後）

- **未逐段核對**：SRD_System_Architecture §1～§3、§5.1～§5.3、§6～§7；SRD_Module_Technical_Design §1～§5.3；`API_M04_Cart.md`／`API_M06_Booking.md`／`api/*.md` 各 API 文件；FRD 內文約 95 處錯誤碼引用。本輪只核對 S191～S202 有變更的行為與錯誤契約。
- **DEF-284**（`E_1005`→404）、**DEF-285**（冪等鍵規範未落實，🟡 需產品決策）、**DEF-286**（API 索引不完整）已登記，未處理。
- **維運三件事**（代理 `X-Forwarded-Proto`、staging CSP、Sprint 198 已部署則送 `max-age=0`）仍在使用者手上，我做不到。

## 7. Push

**尚未 push。** 使用者本輪回覆未涵蓋 push 授權（2026-07-08 的免確認授權只涵蓋金流/租戶隔離 commit，本輪是文件與測試）。依使用者指示接著做 A → B → C，三項完成後一併詢問 push。

## 8. 下一步

依使用者指示依序處理 **A → B → C**：

1. **A**：忘記密碼＋email 驗證（DEF-252／253）。依 AISDLC 先補 PRD／FRD 需求，再實作；寄信走介面＋日誌型 Mock；email 驗證只在「開店申請」強制。
2. **B**：M07 真實金流——程式層已於 Sprint 49~56、80 完成，剩人工上線步驟；本項的 AI 可做部分是核對並更新 [STRIPE_PRODUCTION_CHECKLIST.md](../08_deployment/STRIPE_PRODUCTION_CHECKLIST.md) 的過時敘述。
3. **C**：DEF-283，先真實 Tomcat 實測可行性再決定。
