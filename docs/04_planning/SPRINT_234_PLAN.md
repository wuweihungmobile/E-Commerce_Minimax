# Sprint 234 Plan — 系統租戶假設全面稽核與止血：沒有店鋪的使用者不得碰到店家層客服工單／退貨／結算／撥款（DEF-325）

**Sprint**: Sprint 234
**日期**: 2026-10-02～2026-10-03（跨午夜）

## 1. 缺口盤點結果

### 1.1 起點與排程變更（排程順序為我的安排，屬推論）

[SPRINT_233_PLAN.md](SPRINT_233_PLAN.md) §1.1 把 DEF-319（同店結帳）排在本輪。但 Sprint 233 讀碼時發現退貨申請與客服工單有**與 Sprint 232 同型**的缺口，而 Sprint 232 的止血只修了「兩個列表方法」，它的根因——**沒有店鋪的使用者，租戶脈絡是系統租戶佔位值，不是 `null`，且所有這類使用者共用同一個**——是系統性的。同一個疏漏已經在 Sprint 151、231、232 連續出現三次，每次都是「發現一處、修一處」。所以本輪**先做全面稽核與止血**，DEF-319 順延。排程調整如下（**順序是我依「相依＋風險」排的，不是使用者指定的**）：

| Sprint | 內容 | 為何排這個位置 |
|--------|------|----------------|
| **234（本輪）** | 系統租戶假設全面稽核；止血 BUYER 可達的店家層端點（`DEF-325`）；**`DEF-328` 共用限流桶（E2E 守門實測證實後提前修復，見 §1.4）** | 外洩中的資料是其他消費者的客服工單全文、退貨申請、結算單（週結算排程若跑過，內容是全部一般消費者訂單的 GMV、抽成、退款；正式環境是否已有資料未驗證）；而且能**以店員身分在別人的工單發言** |
| 235 | `DEF-329`（JWT 租戶解析不看成員狀態）、`DEF-327`（Stripe Connect onboarding 不驗店主）（原本還有 `DEF-328`，已提前到本輪） | **`DEF-329` 必須在 DEF-319 之前**：DEF-319 做完後，真實店鋪租戶會擁有真實消費者的訂單，被移除或只是受邀的使用者若仍帶著店鋪租戶登入，就多了一條讀取真實消費者資料的路徑 |
| 236～237 | **DEF-319 根因**（同店結帳）：訂房 → 訂單 | 使用者已拍板（Sprint 232 結尾），原排 234～235，順延兩輪 |
| 238～239 | 文件對齊（比照 Sprint 203）＋ token 儲存文件改成現況（`DEF-323` (b)） | 要等 DEF-319 修完 |
| 240 | 店鋪成員管理 UI | 獨立 |
| 241 | 修 CMS 嵌入卡片連結（`DEF-321` (c)） | 小 |
| 242 | `DEF-318` 通知事件 | 獨立 |
| 243～244 | 店鋪前台 `/stores/[slug]` | 最大且最不確定，放最後 |

`DEF-326`（SELLER／HOST 共用系統租戶）是**產品決策**，不在上表，待使用者拍板。

### 1.2 稽核方法與查證程度

派了一個**唯讀**稽核 agent（prompt 明文禁止寫檔與任何 git 指令）掃描後端「同一類缺陷」：以呼叫者的租戶查詢或比對、卻沒有排除系統租戶的位置。它讀碼約 140 次工具呼叫，回報 22 個端點（A1～A22）與 7 個確認缺陷（B1～B7）。**回報後我核對 `git status`：只有我自己的改動，agent 沒有寫任何檔案**（Sprint 233 commit `7cf0e78` 的 13 個檔案也與我預期的完全一致）。

**查證程度（誰驗證了什麼）**：

| 缺陷 | 內容（agent 端點編號） | 查證程度 | 處置 |
|------|------------------------|----------|------|
| **B1** | 客服工單：BUYER 可讀他人工單全文與整串訊息，並**以 `STAFF` 身分在他人工單發言**（A1～A3） | **真實全棧實測**（打包 JAR＋PostgreSQL＋Redis＋Playwright，兩個真實消費者）：列表／詳情／發言皆 HTTP 200，A 的工單裡出現冒充的店員訊息 | ✅ 本輪修復 |
| **B4** | 退貨申請：BUYER 可讀他人退貨單；`loadForTenant` 同型寫法（A4、A5） | 讀碼；修復後的 HTTP 測試經突變驗證——拿掉守門即轉紅，等於修復前行為可由測試重現（§4） | ✅ 本輪修復 |
| **B5（財務部分）** | 結算單列表／詳情（訂單數、GMV、退款、抽成等金額欄位）、`PUT /settlements/{id}/submit`（用**讀**權限做寫入）、撥款記錄（A6～A8） | 同上 | ✅ 本輪修復 |
| B5（其餘） | 定價規則、運費模板、通知模板、賣家儀表板、ERP 九個 GET、CMS／貼文列表（A9～A13、A15、A20、A21） | **僅讀碼**；agent 未讀前端，不知買家端有無畫面呼叫 A9／A11／A12 | `DEF-326`（SELLER／HOST 可達者）、`DEF-330`（BUYER 可達的低風險設定讀取） |
| **B2** | SELLER／HOST 共用系統租戶，可互相改寫、改寫平台自營資料（改運費模板、定價規則、CMS 頁面、佔 slug、耗盡配額）（A16～A22） | 僅讀碼 | `DEF-326` 🔴 **待決定**（產品語意，見 §6） |
| **B3** | Stripe Connect onboarding 不驗呼叫者是否為店主；系統租戶首次呼叫會建立 Connect 帳戶並回傳一次性連結（A14） | 僅讀碼；**撥款流向是推論，未驗 Stripe 端**；條件：`STRIPE_CONNECT_ENABLED` 對系統租戶開啟（Flyway 無種子，缺列視為停用） | `DEF-327`（Sprint 235） |
| **B6** | 所有一般買家與匿名請求共用 `ratelimit:tenant:<系統租戶>` 一個桶（每租戶 100 次／分），任一人打滿即讓全體 429 | 讀碼（`RateLimitFilter`:47-48,117）；**本輪 `make validate-e2e` 實測證實**（見 §1.4：後端日誌 11 次系統租戶限流，失敗案例的錯誤文字＝`E-9904`） | ✅ 本輪修復（`DEF-328`，見 §1.4） |
| **B7** | `AuthService.resolveTenantForUser` 用不看狀態的 `findByUserId`：被移除的店員保留店鋪租戶與員工權限；只是受邀或拒絕邀請的買家，下次登入就帶真實店鋪租戶 | 讀碼，未重現 | `DEF-329` 🔴（Sprint 235，先於 DEF-319） |

稽核 agent 自己標註**沒有驗證**的項目（原文）：沒有任何執行期驗證；`STRIPE_CONNECT_ENABLED` 是否對系統租戶開過；前端有無買家可達畫面呼叫 A9／A11／A12；沒讀測試，不知現有測試是否固定了「系統租戶可讀」；沒審 `ADMIN` 的跨租戶放行（`ReturnRequestService.isAdmin` 與 `RolePermissionMapping` 對 ADMIN 的定義可能矛盾）。另有順手看到、不屬同型、沒深究的四點（`BookingReviewService.createBookingReview` 沒驗是本人訂房、`ReviewService.createReview` 沒驗購買、`replyToBookingReview` 比對的是訂房人而非房東、`JwtAuthenticationFilter` 直接信任 JWT 的角色與租戶不複驗）→ 登記為 `DEF-331`（**未驗證的疑慮**）。

### 1.3 我自己的查證與更正

1. **平台客服是 `SUPER_ADMIN`，守門對它放行，不會讓平台客服失能**：種子管理員 `admin@nextkey.local` 在 `V1`／`V7` 都是 `SUPER_ADMIN`；`TenantContextFilter`（DEF-038）的註解明載只有 SUPER_ADMIN 是平台級角色、ADMIN 依 `RolePermissionMapping` 是「租戶內管理」角色。所以沒有店鋪的 `ADMIN` 與買家同樣被擋——符合設計。若產品要讓 `ADMIN` 當平台客服，是另一個決定（併入 `DEF-331`）。
2. **客服工單的詳情端點有兩道守門**（`findForStaff` 與 `listMessagesAsStaff`），所以單獨拿掉 `findForStaff` 的守門，HTTP 層**觀察不到**——唯一只走 `findForStaff` 的路徑是 `PUT /dashboard/support/tickets/{id}`（狀態更新），要 `support_ticket:update`，只有 STORE_OWNER／STORE_STAFF／ADMIN／SUPER_ADMIN 持有，沒有店鋪的買家與 SELLER／HOST 都沒有。這個守門只被單元測試抓到（§4 的 M02），不是遺漏。
3. **獨立交叉核對稽核的完整性（檔案層級）**：我自己 `grep` 全庫 `getCurrentTenant()` 的呼叫處（32 個檔案），逐檔對照 agent 的端點清單（A1～A22）與「檢查過、判定沒有問題」清單——ERP／定價／運費模板／通知模板／評價／CMS／房態日曆／商品 SKU 都在 A 清單（貼文走 `PostController.getTenantIdFromUser`，不在這個 `grep` 內，由 agent 另外涵蓋）；知識庫、FAQ、媒體、Analytics 在「需 `faq:*`／`knowledge:*`／`media:*`／`dashboard:read`，BUYER／SELLER／HOST 都沒有」清單；`IdempotencyService`（key 含 userId）、`FeatureToggleService`（只檢查功能開關）、`CombinedCheckoutService`（屬 DEF-319）不是資料外洩點。**沒有發現遺漏的檔案**。這只是檔案層級的核對，不是逐行重審——agent 自己標註的未驗證項目（§1.2）仍然有效。
4. **既有的三份 `SYSTEM_TENANT_UUID` 複本**（`OrderService`:87、`BookingService`:105、`LogisticsService`:279）本輪**不收斂**：這三個檔案在 DEF-319（Sprint 236～237）會大幅改動，現在動只是增加衝突面（CLAUDE.md Rule 3）。新增的 `TenantContext.isStoreTenant` 是日後收斂的目標。登記於 `DEF-330`。

### 1.4 E2E 守門的 2 個失敗：`DEF-328` 由讀碼變成實測，本輪一併修復

`make validate-e2e` 第一次跑：**125 個測試 119 通過／4 略過／2 失敗**——失敗的是 `at-m17-002`（管理員審核開店申請）的核准與駁回兩案，**不是本輪新增的 4 個隔離案例**（它們依字母排在最後，失敗發生時還沒跑；本輪的正式碼也只動客服工單／退貨／結算／撥款，碰不到管理員審核流程）。依規則先看實際錯誤，不猜：

1. 失敗當下的頁面快照是審核頁的**錯誤狀態**（錯誤文字加「重試」鈕），錯誤文字剛好 **7 個字**；`E-9904`「已超過速率限制」也是 7 個字——這是**線索，不是證據**。
2. 同一個 JAR 在保留後端日誌的全棧手動重跑（2 個 worker，與守門同條件）：**121 通過／4 略過／0 失敗，後端日誌 0 次限流**。失敗不是確定性的，無法直接證實。
3. 改用 **4 個 worker** 加大請求密度重跑：**118 通過／3 略過／4 失敗**——同樣的 `at-m17-002` 兩案，加上 `at-account-security`（忘記密碼）、`at-m17-003`（功能開關數值）；**後端日誌同時出現 11 次 `Tenant 00000000-0000-0000-0000-000000000001 exceeded rate limit`**（另有 1 次登入端點的 IP 限流，那是另一個獨立的限流器）。系統租戶就是所有一般買家、匿名請求與管理員共用的佔位租戶——這正是稽核 agent 的 B6。**`DEF-328` 由「僅讀碼」升級為「已實測」，嚴重度由 🟡 升為 🔴**。

**為什麼這一輪才浮現**：我**推論**（未驗證）是 Sprint 233 把登入輔助函式改成「等到有結果」後 E2E 變快（7.2→5.9 分鐘；守門這次 4.4 分鐘），同一批請求擠在更短的時間內。這不是根因——根因是設計：全體一般買家加匿名訪客合用一個 100 次／分的桶。**同一種模式在 Sprint 168 出現過**（登入限流器的容量也是 `make validate-e2e` 在 4 個 worker 下實測揭露、再調整）。

**修復 `DEF-328` 後守門仍失敗：第二個、獨立的限流器**。以修復後的 JAR 重跑守門，仍有 **4 個失敗**（`at-m17-003`×2、`at-m17-004`，以及本輪新增的 `STI-04`），共同症狀是「應已登入並持有 accessToken」（登入後沒有 token）。再用 4 個 worker 對照：**系統租戶限流警告 0 次（修復前 11 次）——`DEF-328` 的修復有效；但出現 11 次 `exceeded rate limit on /v2/auth/login`**，失敗的案例（`at-m17-001`、`at-m17-002`×2）都是登入被擋。這是**登入端點的 IP 限流**（`LoginRateLimitFilter`，Sprint 168：每個來源 IP、每個路徑 30 次／分），與租戶限流無關。原因是 E2E 的登入密度：`registerAndLogin` 的 48 個呼叫點**全部是全新的隨機帳號**，卻都先試一次必然失敗的登入、再註冊、再登入——每個新帳號 2 次登入。我**估算**全套件約 130～140 次登入（48×2＋26 個 `loginOnly`＋開店流程，未逐一計數），3.6 分鐘跑完就是每分鐘約 38 次，超過每分鐘 30 次的補充速率；5.9～6.3 分鐘跑完時約 23 次，剛好沒事——這就是它偶發的原因。

修法只動**測試輔助函式**，**不動正式環境的登入限流政策**（那是 Sprint 168 的決定，且不是本輪該改的）：(1) `registerAndLogin` 在沒有指定 email（全新帳號）時直接註冊，不再先打一次必敗的登入（約省三分之一的登入）；(2) `submitLogin` 遇到「已超過速率限制」時等 2.5 秒重試（token bucket 每 2 秒補一個），最多 8 次，其他失敗（帳號不存在、密碼錯誤）維持原行為。

**為什麼不只重跑守門，也不只調低測試負載**：重跑可能剛好通過（「重跑就綠 ≠ 沒缺陷」），調低負載只是遮住它；而這個限制對真實流量也成立——兩三個並行的測試使用者就能耗盡。所以決定本輪一併修（排程原本把它放在 Sprint 235）。這是**範圍擴大**，已在計畫書與追蹤表標明，修法屬工程決策，見 §6.4。

## 2. 實作內容

**單一述詞**：`TenantContext.isStoreTenant(UUID)`——非 `null` 且不是系統租戶佔位值。Javadoc 明載 `hasTenant()` 對系統租戶也回 `true`，**不能**拿來判斷「有沒有店鋪」。

**守門位置**（沒有店鋪的呼叫者，**一律先判斷再查詢**；SUPER_ADMIN／（退貨的）admin 維持原行為）：

| 範圍 | 方法 | 沒有店鋪的呼叫者得到 |
|------|------|----------------------|
| 客服工單 | `SupportTicketService.listTenantTickets`、`findForStaff`（詳情、狀態更新共用）；`SupportMessageService.postAsStaff`、`listMessagesAsStaff`；`SupportTicketController.listTenantTickets` 多傳 `isSuperAdmin` | 空頁／`E-8008`（404，不洩漏工單是否存在） |
| 退貨申請 | `ReturnRequestService.getTenantReturnRequests`、`getReturnRequest` 的 `sameTenant`、`loadForTenant`（核准／駁回／收貨共用） | 空頁／`E-1007`（403） |
| 結算單 | `SettlementGenerator.getStatementsByTenant`、`getStatementById`；`SettlementReviewer.submitForReview` | 空清單／`E-5013`（404） |
| 撥款記錄 | `TransferService.getTransfersForCurrentTenant` | 空頁 |

**不改**：權限表（`BUYER` 仍持有 `support_ticket:read`／`create`、`return:read`、`order:read`——這些是買家看自己資料的合法權限，問題在於店家層端點拿租戶當擁有權判斷）；`submit` 端點用 `order:read` 做寫入的權限問題（A7）——守門已讓無店鋪者碰不到，權限本身是否該改成寫入權限屬於 `DEF-306` 的 RBAC 子項。

**限流（`DEF-328`，由 Sprint 235 提前）**：`RateLimitFilter` 新增 `bucketKey`——真正的店鋪租戶維持 `ratelimit:tenant:<id>`（行為不變）；沒有店鋪的使用者（共用系統租戶）改為 `ratelimit:user:<userId>`（已登入）或 `ratelimit:ip:<remoteAddr>`（匿名），容量仍是 100 次／分。來源 IP 用 `getRemoteAddr()`，刻意不採信 `X-Forwarded-For`（理由同 `LoginRateLimitFilter`，Sprint 168：client 可自訂 header，攻擊者每次帶不同的偽造值就能繞過；本專案無反向代理）。日誌訊息改為 `Bucket <key> exceeded rate limit`。

## 3. 使用者決策

本輪沒有新的使用者決策。Sprint 232 結尾的四個決定（[SPRINT_233_PLAN.md](SPRINT_233_PLAN.md) §2）照舊有效；本輪只調整**排程**（§1.1），沒有改變任何已拍板的內容。

## 4. 測試

| 層 | 內容 |
|----|------|
| 單元（+22） | `SupportTicketServiceTest` +4（列表對系統租戶／null 租戶回空且不查詢；詳情與狀態更新共用的 `findForStaff` 對系統租戶回 `E-8008` 且不查詢；SUPER_ADMIN 即使在系統租戶仍可運作）；`SupportMessageServiceTest` +3（`postAsStaff`／`listMessagesAsStaff` 對系統租戶拒絕且不寫入、不查詢；SUPER_ADMIN 仍可回覆）；`ReturnRequestServiceTest` +6（列表對系統租戶回空、店鋪租戶照常、admin 維持原行為；兩個都在系統租戶的買家不得互讀、本人仍可讀自己的；`loadForTenant` 對系統租戶拒絕）；`TransferServiceTest` +1；新類別 `SettlementTenantScopeTest` 4（列表／詳情／提交審核對系統租戶，店鋪租戶照常查詢）；新類別 `TenantContextTest` 4（null／系統租戶／其他租戶／`hasTenant()` 對系統租戶也回 true，不能拿來判斷） |
| HTTP＋真實 PostgreSQL（+9） | `SystemTenantIsolationE2ETest`：兩個真實註冊的無店鋪買家＋一間真實店鋪與店主（對照組），資料直接蓋成系統租戶（不走「下單→開工單」流程，因為 DEF-319 會改變訂單的蓋章租戶，而這道防線與資料怎麼來無關），走完整 JWT＋權限＋租戶過濾鏈。每個案例都含「店主照常看得到自己店鋪的資料」的對照，避免守門變成「對所有人都回空」。涵蓋客服工單（列表／詳情／冒充店員發言並確認無訊息列寫入／本人仍可讀）、退貨（列表／讀他人／本人與店主可讀）、結算單（列表／詳情／提交審核並確認狀態仍為 `PENDING`／店主可提交）、撥款記錄 |
| 單元（+3，限流） | `RateLimitFilterTest` +3：以 `ArgumentCaptor` 取出**實際送進 Redis 的 key**——店鋪租戶仍以租戶為單位；系統租戶下已登入者各自一個桶、不共用系統租戶那個；系統租戶下匿名請求以來源 IP 為單位（不同 IP 各自一個、同一 IP 共用） |
| 真實 Redis（+1，限流） | `RateLimitFilterIntegrationTest.systemTenantUsers_haveIndependentQuotas`：系統租戶下使用者 A 耗盡 100 次配額後，使用者 B 與匿名來源 IP 都不受影響（修復前兩人共用一個桶，B 會被拒絕） |
| 真實後端 Playwright（+4） | `at-system-tenant-isolation-real.spec.ts`（STI-01～04）：完全不 mock，資料全由真實流程建立（店主開店→商品→消費者 A 下單→A 開工單）。**修復前實測轉紅**（Sprint 233 的 JAR）：STI-02 失敗——B 的店家層列表含 A 的工單；探針副本確認 B 可讀 A 的工單全文（HTTP 200）、可以 `STAFF` 身分在 A 的工單發言（HTTP 200），A 自己的工單串出現「我是客服人員，請提供您的信用卡末四碼以便退款」（探針用完即刪）。開店流程抽成共用的 `helpers/store.ts`（`seedStore`），`at-seller-dashboard-real.spec.ts` 改用它，避免第三份複製 |

### 突變驗證（Rule 9：測試必須在守門被拿掉時失敗）

每次只拿掉一個守門（M01～M12 為系統租戶守門，R1～R4 為限流單位），跑上表全部相關測試，結束後從備份還原並用 `cmp` 驗證；全部做完再比對 `git diff` 的 `backend/`、`frontend/` 部分與突變前**逐位元組相同**。

| # | 拿掉的守門 | 轉紅的測試 |
|---|-----------|-----------|
| M01 | `SupportTicketService.listTenantTickets` | 單元 2＋HTTP `tenantlessBuyerCannotListStoreLevelTickets` |
| M02 | `SupportTicketService.findForStaff` | 單元 `staffOperations_systemTenantCaller_throwsE8008WithoutQuerying`（**僅單元**，原因見 §1.3 第 2 點） |
| M03 | `SupportMessageService.postAsStaff` 的守門 | HTTP `tenantlessBuyerCannotImpersonateStaff`＋單元 |
| M04 | `SupportMessageService.listMessagesAsStaff` 的守門 | **首次突變存活**（見下）；修正測試後單元 `listMessagesAsStaff_systemTenantCaller_throwsE8008` 轉紅（**僅單元**，同 M02 的縱深防禦原因） |
| M05 | `ReturnRequestService.getTenantReturnRequests` | 單元＋HTTP `tenantlessBuyerCannotListStoreLevelReturns` |
| M06 | `ReturnRequestService.getReturnRequest` 的 `sameTenant` | 單元＋HTTP `tenantlessBuyerCannotReadOthersReturn` |
| M07 | `ReturnRequestService.loadForTenant` | 單元 `approveReturn_systemTenantCaller_throwsE1007`（**僅單元**：核准／駁回／收貨需 `return:review`，無店鋪者沒有） |
| M08 | `SettlementGenerator.getStatementsByTenant` | 單元＋HTTP `tenantlessBuyerCannotListSettlementStatements` |
| M09 | `SettlementGenerator.getStatementById` | 單元＋HTTP `tenantlessBuyerCannotReadOrSubmitSettlementStatement` |
| M10 | `SettlementReviewer.submitForReview` | 單元＋同上 HTTP（含「狀態仍為 `PENDING`」斷言） |
| M11 | `TransferService.getTransfersForCurrentTenant` | 單元＋HTTP `tenantlessBuyerCannotListTransfers` |
| M12 | `TenantContext.isStoreTenant` 本身（改成只檢查非 null） | 20 個測試 |
| R1 | `RateLimitFilter.bucketKey`：一律以租戶為單位（＝**修復前行為**） | 真實 Redis 測試＋單元 2 |
| R2 | 同上：忽略使用者、一律以來源 IP | 真實 Redis 測試＋單元 1 |
| R3 | 同上：匿名請求不以 IP（共用一個 `user:null` 桶） | 單元 1 |
| R4 | 同上：店鋪租戶也改用使用者／IP | 既有的 `differentTenants_haveIndependentQuotas`（真實 Redis）＋單元 1 |

**M04 首次存活，是我自己寫的測試缺陷（Rule 9 的實例）**：`listMessagesAsStaff_systemTenantCaller_throwsE8008` 原本沒有為 repository 準備資料，Mockito 對 `Optional` 預設回 `Optional.empty()`——**沒有守門時也會因為「找不到工單」而拋出同一個 `E-8008`**，測試無法失敗。同一個檔案的 `postAsStaff` 案例有 `verify(never())` 所以抓得到，這個案例只驗了更下游的 `messageRepository`，沒驗到被拿掉的那一步。修正：讓 repository「找得到」那張工單（`lenient()`，守門在時不會用到），並驗證 `findByIdAndTenantId` 完全沒被呼叫；拿掉守門後轉紅。**教訓**：用「拋出了預期的例外」當斷言時，要確認沒有守門時會得到**不同**的結果，否則測不出守門。

## 5. 驗證結果

- **`mvn -o clean verify`**（checkstyle main＋test、PMD、JAR 打包），**含 `DEF-328` 修復的最終版**：**BUILD SUCCESS，17 分 19 秒**；單元 **2006**（Sprint 233 為 1981，+25＝本輪新增）／整合 **696**（686，+10＝`SystemTenantIsolationE2ETest` 9＋真實 Redis 限流測試 1）／**0 失敗**／0 略過；checkstyle 0 違規（主程式與測試）；PMD 通過。（加入 `DEF-328` 之前的第一次完整驗證：16 分 01 秒、2003／695，同樣全綠。）
- **定點執行**：`SystemTenantIsolationE2ETest` 9、`SettlementTenantScopeTest` 4、`TenantContextTest` 4、`SupportMessageServiceTest` 8、`RateLimitFilterTest` 10、`RateLimitFilterIntegrationTest` 3 全過；既有的 `M07SettlementIntegrationTest` 14 與 `TransferControllerE2ETest` 5（結算／撥款整合測試，用店鋪租戶）不受影響。16 個守門／分支各自經突變驗證（見 §4）。
- **`make validate-e2e`**（125 個測試，含本輪新增的 4 個隔離案例）——**過程比預期曲折，原因與判斷依據見 §1.4**：

  | 次 | 條件 | 結果 |
  |----|------|------|
  | 守門 1 | `DEF-328` 修復**之前** | 119 通過／4 略過／**2 失敗**（`at-m17-002` 核准與駁回；錯誤文字＝`E-9904`） |
  | 診斷 A | 同一個 JAR，手動，2 個 worker（6.3 分鐘） | 121 通過／4 略過／0 失敗；後端日誌 **0** 次限流——失敗不是確定性的 |
  | 診斷 B | 同一個 JAR，手動，**4 個 worker** | 118 通過／3 略過／**4 失敗**；後端日誌 **11 次系統租戶限流**（另 1 次登入 IP 限流） |
  | 守門 2 | `DEF-328` 修復**之後** | 120 通過／1 略過／**4 失敗**（`at-m17-003`×2、`at-m17-004`、`STI-04`；共同症狀：登入後沒有 token） |
  | 診斷 C | 修復後，手動，4 個 worker | 118 通過／4 略過／**3 失敗**；**系統租戶限流 0 次**（`DEF-328` 修復有效），但 **11 次登入 IP 限流** |
  | 守門 3 | 再加上 E2E 輔助函式修正（全新帳號直接註冊、限流時退避重試） | ✅ **121 通過／4 略過／0 失敗（3.5 分鐘）**——正是先前會失敗的快速狀態 |
  | 診斷 D | 同上，手動，4 個 worker | ✅ **121 通過／4 略過／0 失敗（3.3 分鐘）**；後端日誌 **0** 次租戶限流、**0** 次登入 IP 限流 |

  那 4 個略過是既有基準（Sprint 231～233 皆為 4 個）。守門 2 只有 1 個略過、診斷 B 只有 3 個略過，我沒有追查為什麼略過數會隨失敗變動（推測是有條件略過的案例受前面失敗影響，**未驗證**）。
- **前端**：`tsc --noEmit`、`eslint`（四個 e2e 檔案，含 `helpers/auth.ts`）皆通過。前端 `src` 本輪沒有變動，E2E 守門重用 Sprint 233 的建置。
- **push 與雲端 CI**：（push 後於回填 commit 補上）
- **耗時觀察（承 Sprint 233 §5，未歸因）**：本機全量 `mvn verify` 本輪 17 分 19 秒（加入 `DEF-328` 前為 16 分 01 秒；Sprint 233：15 分 21 秒；Sprint 232：17 分 24 秒），差距在負載雜訊內，看不出 bcrypt 12 的影響。雲端整合 job 近五次依序 5m01s（S229）／6m02s（S230）／6m00s（S231）／4m15s（S232）／7m47s（S233）。**本輪 push 後的雲端數字是第二個資料點，在它出來前不下結論**；若仍在 7～8 分鐘，才值得量測「整合測試註冊／登入的 bcrypt 總耗時」。

## 6. 範圍外（延後）、已知限制與待決定

### 6.1 待使用者決定：`DEF-326`（SELLER／HOST 共用系統租戶）

**事實（讀碼，未實測）**：自助註冊可直接得到 `SELLER` 或 `HOST`，沒有審核、不建租戶；開店核准後的角色是 `STORE_OWNER`，不是 `SELLER`。所以在正式環境，`SELLER`／`HOST` 幾乎必然是「還沒開店的人」，租戶落在系統租戶，並持有商品、房源、定價、運費模板、CMS、貼文的寫入權限。各寫入方法的擁有權檢查是「資源的租戶 == 呼叫者的租戶」，於是系統租戶下所有 `SELLER`／`HOST` 與平台自營資料彼此互通（影響清單見 `DEF-326`）。

| 選項 | 內容 | 取捨 |
|------|------|------|
| **(a) 未歸屬店鋪者不得寫入（我的建議）** | 各擁有權檢查排除系統租戶（寫入回 403／404，讀取回空） | 與 PRD M17「需先申請開店」及本輪三個 Sprint 的原則一致；代價是沒有店鋪的 `SELLER`／`HOST` 不能再建立商品／房源，需盤點以 `SELLER` 身分建資料的測試 |
| (b) 保留「自助賣家」語意並做擁有者層級隔離 | 系統租戶視為共用市集：商品／房源以 `ownerId` 隔離；租戶層設定（運費模板、定價規則、CMS、貼文）不開放給 `SELLER`／`HOST` | 保留現有流程，但要重新定義哪些設定屬於平台；工作量大，且與 PRD 的開店流程重疊 |
| (c) 暫不處理 | 只登記 | 持續暴露：任一自助註冊的 `SELLER` 可改運費模板、定價規則（影響所有買家結帳）、線上 CMS 頁面 |

### 6.2 延後項目

- **`DEF-329`（JWT 租戶解析不看成員狀態）、`DEF-327`（Stripe Connect onboarding）**：排入 Sprint 235。兩項都是僅讀碼確認、尚未重現——先以真實全棧重現再修。`DEF-329` 必須先於 DEF-319。（`DEF-328` 已提前修復，見 §1.4、§6.4。）
  - **`DEF-327` 不只是「加一個守門」**：兩個端點的 `@PreAuthorize` 是 `hasRole('SELLER')`，而核准開店後的角色是 `STORE_OWNER`，所以**真正的店主反而呼叫不了，目前只有沒有店鋪的 `SELLER` 能呼叫**（前端 `src` 完全沒有呼叫這兩個端點與 `/seller/dashboard`，連 onboarding 完成後後端指定導回的 `/seller/stripe-connect/return`、`/refresh` 頁面也不存在）。修法要同時決定角色檢查（讀碼，未實測）。
- **`DEF-330`**：低風險設定讀取、三份 `SYSTEM_TENANT_UUID` 複本收斂（隨 DEF-319）、`getCurrentTenant()` 掃描守門測試（難度與誤報量未評估）。
- **`DEF-331`**：稽核順手看到的四個疑慮與 `ADMIN` 角色語意，先驗證再決定。

### 6.3 已知限制

1. **本輪是逐點修復，沒有結構性防護**：每個店家層端點仍要靠作者記得呼叫 `isStoreTenant`。已把規則寫進 Code Review 檢查清單（`Stage8_Coding_Standards.md` §4.1）與 `TenantContext` 的 Javadoc，但真正的結構性防護（掃描 `getCurrentTenant()` 呼叫處的守門測試）尚未評估，登記於 `DEF-330` (c)。
2. **只處理「JWT 租戶是系統租戶」的呼叫者**。JWT 的租戶本身可能不正確（`DEF-329`）：被移除的店員仍帶店鋪租戶，守門擋不住。
3. **已簽發的 access token 在有效期（15 分鐘）內仍可使用**；`JwtAuthenticationFilter` 不複驗成員與帳號狀態（`DEF-331` (d)）。
4. **平台層的 `ADMIN`**：依 DEF-038 是租戶內角色，沒有店鋪的 `ADMIN` 與買家同樣被擋。若產品要讓 `ADMIN` 當平台客服，是 `DEF-331` (e) 的決定。
5. **正式環境的資料狀態未驗證**：系統租戶實際有沒有結算單、撥款記錄，取決於週結算排程是否跑過（`APP_SCHEDULING_ENABLED`，見 `SETTLEMENT_JOB_RUNBOOK.md` §6）；本輪無法也沒有查正式環境。
6. **`PUT /settlements/{id}/submit` 仍用 `order:read` 這個讀權限做寫入**（稽核 A7）：守門讓沒有店鋪的人碰不到，但權限本身是否該改成寫入權限屬於 `DEF-306` 的 RBAC 子項，本輪不動。

### 6.4 請使用者確認的工程決策：沒有店鋪的使用者的限流單位（`DEF-328`）

PRD 對限流只有一句「每租戶 100 req/min，超過回 429」，**沒有定義不屬於任何店鋪的使用者**（一般買家、匿名訪客、管理員）。`RateLimitFilter` 原本的註解也寫明「每租戶」是 PRD 唯一明確要求的維度。我的決定：已登入者以**使用者**、匿名者以**來源 IP**，容量與店鋪相同（100 次／分）。請使用者否決或調整，可能的調整點：

1. **容量**：匿名訪客與登入使用者是否該用不同上限（目前一樣）。
2. **部署在反向代理後面時**：`getRemoteAddr()` 會是代理的 IP，所有匿名訪客又會共用一個桶（已登入者不受影響，仍是各自一個）。本專案目前無反向代理，與 `LoginRateLimitFilter`（Sprint 168）的既有取捨相同，本輪沒動；若日後加反向代理，要一併設定 `server.forward-headers-strategy` 並只信任代理來源。
3. **是否另設全域上限**（防止大量不同使用者／IP 同時打滿後端）：本輪不做，PRD 沒有要求。

## 7. Velocity 紀錄

（選配，未填。）

## 8. 下一步／Action Items

1. 收尾核對三份追蹤文件（DEFERRED_ITEMS_TRACKER、RELEASE_TRACKER、本計畫書）都已更新；push 後回填雲端 CI 結果與 §5 的「整合 job 耗時」第二個資料點。
2. **Sprint 235：`DEF-329` → `DEF-327`**（`DEF-328` 已於本輪修復）。每一項先以真實全棧重現再修：
   - `DEF-329` 重現劇本（端點已查證）：店主邀請 `POST /v2/tenants/{id}/members/invite` → 買家 `POST .../invite/accept` → 重新登入確認帶店鋪租戶與 `STORE_STAFF` → 店主 `DELETE /v2/tenants/{id}/members/{userId}` → 被移除者重新登入／refresh，確認**仍**帶店鋪租戶（預期為漏洞）；另測只受邀、尚未接受就登入。**範圍要先盤點**：我另以 `grep` 發現同樣不看成員狀態的 `findByUserId`／`existsByTenantIdAndUserId` 還出現在 `TenantService`（:160、:203、:283、:377、:468、:683）與 `PostController`（:544、:581），各處語意未逐一驗證。修法方向：`resolveTenantForUser` 只採 `ACTIVE` 成員；`removeMember`／`declineInvite` 同步收回 `user.role`；移除成員時撤銷其 refresh token（已簽發的 access token 15 分鐘內仍有效，見 §6.3 第 3 點）。
   - `DEF-327`：先決定角色檢查（見 §6.2），再要求真實店鋪租戶且限店主。
3. **Sprint 236～237：DEF-319**（訂房 → 訂單）。優惠券與運費模板以哪個租戶解析，若 PRD 沒寫清楚，以 `AskUserQuestion` 請使用者拍板；順便收斂三份 `SYSTEM_TENANT_UUID`（`DEF-330` (b)）。
4. **待使用者**：`DEF-326`（見 §6.1，已附選項與我的建議）。
5. 選配的後續：整合測試的 bcrypt 強度可用設定檔覆蓋（例如 `integration-test` 設定檔用最小成本），同時保留 `PasswordEncoderTest` 對正式 bean 的 `$2a$12$` 斷言——僅在 §5 的耗時觀察被第二個資料點證實後才值得做。
