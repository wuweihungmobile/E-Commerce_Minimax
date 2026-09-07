# Deferred Items Tracker / 延後項目追蹤器

> **用途**: 追蹤所有被延後到未來 Sprint 的工作項目
> **更新原則**: 每個 Sprint Review 後必須更新此文件
> **審查時機**: Sprint Planning 前必須先閱讀此文件

---

## 活躍延後項目 / Active Deferred Items

### 🔴 高優先級 - 下一 Sprint 應優先處理

| ID | 標題 | 原始 Sprint | 延後原因 | 前置需求 | 預估 SP | 狀態 |
|----|------|-------------|---------|---------|---------|------|
| （目前無高優先級待辦項目） | — | — | — | — | — | — |

> **DEF-080~091**（12 項契約漂移，含 M09 通知模板建立/編輯 UI、房源搜尋 keyword 失效＋跨租戶範圍缺口）已於 Sprint 131 修復並結案；**DEF-093**（`ListingController` 裸實體序列化外洩 `passwordHash`）已於 Sprint 130 修復並結案；**DEF-092**（`cms:*`/`notification:create` 5 個權限碼孤兒）已於 Sprint 130 修復並結案，詳見下方「已完成延後項目」。
>
> **DEF-023**（Booking 付款擁有權檢查缺口，IDOR）已於 Sprint 68 修復並結案；**DEF-024**（`OrderService.updateOrderStatus` 跨租戶 IDOR）已於 Sprint 70 修復並結案；**DEF-038**（`TenantContextFilter` ADMIN 跨租戶 `X-Tenant-ID` header 無驗證信任）已於多 Sprint 測試強化計劃後的獨立追蹤任務修復並結案；**DEF-040**（`SettlementController` 結算單審核租戶過濾缺口）已於 Sprint 81 修復並結案；**DEF-034**（`CmsService` 公開瀏覽端點租戶範圍設計）已於 Sprint 82 修復並結案；**DEF-041**（`RoomService`/`ProductService` 寫入層租戶擁有權缺口，含根因修復）已於 Sprint 84 修復並結案；**AI-2419**（PRD §6.7.2 M16 採購審批金額上限機制，Sprint 10 遺漏）已於 Sprint 85 修復並結案；**AI-2420**（PRD §6.2.1 M07 跨週期退款調整單機制 + CREDIT_NOTE 雙重授權）與**DEF-042**（`admin:read`/`admin:write` 權限缺口導致結算審核流程 403 不可達）已於 Sprint 86 修復並結案；**AI-2421**（PRD §14.3.1 Phase 2-B 收貨地址簿）已於 Sprint 87 完成並結案；**AI-2422**（PRODUCT 商品訂單結帳流程前端串接 + 混合購物車修復 + 庫存預扣/扣帳/釋放）已於 Sprint 88 完成並結案；**AI-2423**（PRD §6.7.2 M16 採購審批門檻前端串接）已於 Sprint 89 完成並結案；**AI-2424**（PRD §6.2.1 M07 結算逆轉發起/確認表單）已於 Sprint 90 完成並結案；**AI-2425**（PRD §6.10 M18 客服工單子系統，後端全部 + 買家前端）已於 Sprint 91 完成並結案；**AI-2426**（M18 客服工單子系統，店家/平台前端）已於 Sprint 92 完成並結案，詳見下方「已完成延後項目」。 **DEF-050**（`ProductInventoryService` 三段式庫存操作的讀後寫競態）已於 Sprint 103 修復並結案——⚠️ **修復過程中以紅燈實測推翻了本表原先的定性**：`ProductInventory` 帶 `@Version` 樂觀鎖（`PromoCode` 沒有），故併發下**不會超賣、也沒有 lost update**，實際失效模式是 10 筆併發請求只有 2 筆成功、其餘 8 筆拋 `ObjectOptimisticLockingFailureException`：預扣端讓買家看到 500 且庫存賣不完、釋放端讓取消的庫存還不回去、扣帳端被 `PaymentStateService.deductStockSafely` 靜默吞掉而帳實不符（**後者才是真正通往超賣的路徑，間接且延遲**）。原記錄「嚴重性高於 DEF-046」不成立，但維持高優先級的結論正確。詳見 [SPRINT_103_PLAN.md](SPRINT_103_PLAN.md)。

---

### 🟡 中優先級 - 未來 Sprint 處理

| ID | 標題 | 原始 Sprint | 延後原因 | 前置需求 | 預估 SP | 狀態 |
|----|------|-------------|---------|---------|---------|------|
| DEF-021 | CJK 字體品牌一致性（技術債） | Sprint 35（Turbopack 限制發現） | S35 因 Turbopack 無法 self-host next/font CJK（Noto Sans TC 大量 unicode-range 子集無法解析），改用系統 CJK 字體堆疊；系統堆疊跨平台字重/字距不一，品牌字體一致性下降。後續評估 `next/font/local` + 預先子集化 Noto Sans TC woff2 以恢復品牌字體一致性 | 需 woff2 子集化工具鏈 + 驗證 Turbopack 相容性 | 2 | ✅ 已決策（S41 US-006）：維持系統字體堆疊為 **accepted fallback**；選項 B（`@font-face` 自 host 子集 woff2，技術可行、無 CSP 阻擋）記錄為選配未來任務，待品牌一致性需求由使用者拍板。詳見 [CJK_FONT_ASSESSMENT.md](../06_quality/CJK_FONT_ASSESSMENT.md) **2026-09-03 使用者再次確認：維持現狀，不啟動選項 B。** |
---

### 🟢 低優先級 - 技術債（非急迫，記錄備查）

| ID | 標題 | 原始 Sprint | 延後原因 | 前置需求 | 預估 SP | 狀態 |
|----|------|-------------|---------|---------|---------|------|
| DEF-105 | `backend/.../core/cms/post/PostService.java` 的 `createPost`/`updatePost` 對 `title`/`content` 零 HTML 消毒 | Sprint 135（儲存型 XSS 全掃，Workflow 對抗性驗證發現） | 3 位獨立懷疑者一致確認：資料流事實成立（無消毒、可寫入任意 HTML/script），但目前全代碼庫渲染路徑（`frontend/src/app/blog/[slug]/page.tsx` 的 `renderContentWithEmbeds`、`cms/posts/[id]/edit` 編輯器、`cms/page.tsx` 列表）皆為 JSX 純文字插值或 `<textarea value=...>`，未使用 `dangerouslySetInnerHTML`，React 預設跳脫，非可利用漏洞。屬潛伏性弱點：程式碼註解本身已暗示未來方向是 Markdown 內嵌渲染，屆時若改用 `dangerouslySetInnerHTML` 或 markdown-to-HTML pipeline 且未同步補消毒，會立即變成可利用的儲存型 XSS | 無（可在 `PostService.createPost`/`updatePost` 寫入前加 HTML allowlist 消毒，不需等待渲染端變更；若未來真的改為 rich-text/Markdown 渲染，屆時務必一併檢查） | 1 | ⚠️ 已記錄，**不排入排程**（未達可利用門檻，屬 defense-in-depth 建議） |
| DEF-104 | `backend/.../cms/Banner.java` 的 `linkUrl` 欄位（`CreateBannerRequest`/`UpdateBannerRequest`）無協定白名單驗證，`GET /cms/banners/active` 為公開端點 | Sprint 135（儲存型 XSS 全掃，Workflow 對抗性驗證發現） | 3 位獨立懷疑者一致確認：資料流事實成立（無驗證、可寫入任意字串、讀取端公開），但全代碼庫查證目前**沒有任何前端消費端**讀取或渲染此欄位，sink 尚不存在，無法建構端到端 XSS 攻擊鏈，非可利用漏洞，僅為潛伏性架構弱點。建議在真正串接此 API（例如前端做 banner 管理介面）之前，於 DTO 層先補上 `http`/`https`/相對路徑的協定白名單 | 無（DTO 層新增 `@Pattern` 即可，不需等待消費端） | 1 | ⚠️ 已記錄，**不排入排程**（未達可利用門檻，待未來真正串接該 API 時一併處理） |
| DEF-103 | `Listing.coverImageUrl`（含 `CreateListingRequest`）等商品/房源封面圖網址欄位無協定白名單驗證，`frontend/src/app/(auth)/cart/page.tsx` 等處以 `<img src={item.coverImageUrl}>` 直接綁定 | Sprint 135（儲存型 XSS 全掃，Workflow 對抗性驗證發現） | 3 位獨立懷疑者一致確認：任何賣家皆可填入任意字串且無驗證，但現行主流瀏覽器對 `<img src>` 屬性不會執行 `javascript:`/`data:text/html` 協定（僅 `<a href>`/`<iframe src>`/表單 action 等少數 sink 才會），無法達成 XSS 必要的任意 JavaScript 執行，判定非可利用漏洞，僅為輸入驗證缺口 | 無（DTO 層新增 `@Pattern`/`@URL` 即可） | 1 | ⚠️ 已記錄，**不排入排程**（未達可利用門檻，純輸入驗證品質建議） |
| DEF-025 | `BookingService.createBooking` 的 `idempotencyKey` 參數未被使用（死碼參數） | Sprint 71（撰寫 createBooking 單元測試時發現） | `createBooking(BookingDto.CreateRequest request, String idempotencyKey)` 的 `idempotencyKey` 參數在方法本體內完全未被引用；真正的 idempotency 由 `BookingController.createBooking` 於呼叫前經 `IdempotencyService`（Redis-backed，`checkAndMark`/`markCompleted`/`remove`）把關並快取回應，`BookingService` 層此參數為傳遞後即棄置的死碼，非安全缺口（機制本身正確運作，`BookingControllerE2ETest` 已有 `createBooking_duplicateIdempotencyKey_returnsCachedResponse` 等測試涵蓋）。使用者已審閱並**明確決定**此為低優先級技術債，僅記錄不清理 | 無（純程式碼清潔度考量，清理時機自由） | 1 | ⚠️ 已記錄，**不排入排程**（使用者已決策不清理） |
| DEF-031 | `ReviewService` 2 項業務邏輯疑點：`markHelpful` 可重複投票、`createReview`/`BookingReviewService.createBookingReview` 未驗證訂單/訂房歸屬 | Sprint 73（探查 `ReviewService` 範圍時發現，即 D 項） | (1) `markHelpful` 同一使用者重複呼叫會無限累加 `helpfulVotes`/`helpfulCount`（`votes.getOrDefault(userIdStr, 0) + 1` 每次呼叫皆 +1，並非「已投過就擋」），是否為預期行為需業務規則確認；(2) `ReviewService.createReview`/`BookingReviewService.createBookingReview` 皆未驗證傳入的 `orderId`/`bookingId` 是否真的屬於呼叫者本人，理論上可用他人的 `orderId`/`bookingId` 建立評價（僅受限於「同一 orderId+listingId 僅能評價一次」的唯一性約束，非歸屬驗證）。皆非跨租戶 IDOR（不涉及讀取/竄改他租戶資料），性質為業務規則疑點，使用者已審閱並**明確決定**擱置，不併入 Sprint 73 修復範圍 | 需業務規則確認（是否允許重複投票累加、是否要求訂單/訂房須屬本人才能評價） | 3 | ⚠️ 已記錄，**不排入排程**（使用者已決策擱置） |
---

## 已完成延後項目 / Completed Deferred Items

| ID | 標題 | 移出 Sprint | 完成 Sprint | 備註 |
|----|------|-------------|-------------|------|
| DEF-113 | `PaymentWebhookService` 唯一持久化產物是去重用的 `ProcessedStripeEvent`（僅 event_id/event_type），與實際受影響的 order/tenant/transfer 無關聯，稽核追蹤斷鏈 | Sprint 135（稽核日誌覆蓋率掃描，Workflow 對抗性驗證發現） | Sprint 135 | 不另修改程式碼：DEF-111（`PaymentStateService`）/DEF-112（`TenantStripeConnectService`）修復後，同一次 webhook 呼叫觸發的下游狀態轉換本身即落地 `order_state_log`/`audit_log`，可由 order_id/tenant_id 反查回溯；另建 webhook-to-entity 專屬稽核表屬更大範圍的架構決策，非本輪必要，隨 DEF-111/112 一併結案。詳見 [SPRINT_135_PLAN.md](SPRINT_135_PLAN.md) §7 |
| DEF-112 | `TenantStripeConnectService`：`initiateOnboarding`/`getAccountStatus`/`syncAccountStatusFromWebhook` 控制金流撥款去向的 Connect 帳戶狀態變更零稽核 | Sprint 135（稽核日誌覆蓋率掃描，Workflow 對抗性驗證發現） | Sprint 135 | 注入 `AuditService`，三方法補 `CONNECT_ONBOARDING_INITIATED`/`CONNECT_STATUS_SYNCED` 稽核；webhook 觸發路徑 actor 明確傳 `null`（無使用者情境）。新增/修改測試（`TenantStripeConnectServiceTest`，+2 含先前零覆蓋的 `syncAccountStatusFromWebhook`，另 2 案例補稽核斷言）。詳見 [SPRINT_135_PLAN.md](SPRINT_135_PLAN.md) §7 |
| DEF-111 | `PaymentStateService`：`mockPaymentSuccess`/`refundOrderPayment`/`markStripeRefunded`/`markStripePaymentSucceeded` 直接改 `Order.status`，繞過 `OrderService` 既有的 `order_state_log` 稽核軌跡，同一張訂單狀態史缺漏付款/退款觸發的轉換 | Sprint 135（稽核日誌覆蓋率掃描，Workflow 對抗性驗證發現） | Sprint 135 | 注入 `OrderStateLogRepository`，比照 `OrderService.recordStateLog` 邏輯寫回既有 `order_state_log`（沿用同一張表而非另建稽核表）；webhook 路徑 `changedBy` 明確傳 `null`。新增/修改測試（`PaymentStateServiceTest`/`PaymentStateServiceStripeTest` 既有成功路徑測試補 `order_state_log` 斷言）。詳見 [SPRINT_135_PLAN.md](SPRINT_135_PLAN.md) §7 |
| DEF-110 | `ReturnRequestService.approveReturn`/`rejectReturn`（退貨核准/駁回，影響退款資格）零稽核 | Sprint 135（稽核日誌覆蓋率掃描，Workflow 對抗性驗證發現） | Sprint 135 | 注入 `AuditService`，補 `RETURN_APPROVED`/`RETURN_REJECTED` 稽核。新增 `core/returns/ReturnRequestServiceTest`（該服務先前零測試覆蓋，本輪僅聚焦 approve/reject 稽核，+2）。詳見 [SPRINT_135_PLAN.md](SPRINT_135_PLAN.md) §7 |
| DEF-109 | `SettlementReviewer.approveStatement`/`rejectStatement`：(a) 零稽核；(b) `SettlementStatement.reviewedBy`/`approvedAt` 兩個為此而生的欄位從未被寫入，永遠是 dead field，審核金流動作的實際執行者無從查證 | Sprint 135（稽核日誌覆蓋率掃描，Workflow 對抗性驗證發現） | Sprint 135 | 注入 `UserRepository`+`AuditService`：兩方法皆補 `setReviewedBy`（`approveStatement` 另補 `setApprovedAt`）+ `SETTLEMENT_APPROVED`/`SETTLEMENT_REJECTED` 稽核。修改測試（`SettlementReviewerTest` 既有 2 個成功路徑測試補斷言）。詳見 [SPRINT_135_PLAN.md](SPRINT_135_PLAN.md) §7 |
| DEF-108 | `UserPrivacyService.deleteMyAccount()` 與 `AdminService.updateUserStatus`（`USER_STATUS_UPDATED`）相同欄位變更，自助刪除帳戶零稽核 | Sprint 135（稽核日誌覆蓋率掃描，Workflow 對抗性驗證發現） | Sprint 135 | 注入 `AuditService`，補 `USER_STATUS_UPDATED` 稽核（reason 標註「self-service account deletion」以區分管理員觸發）。修改測試（既有成功路徑測試補斷言）。詳見 [SPRINT_135_PLAN.md](SPRINT_135_PLAN.md) §7 |
| DEF-107 | `TenantService`：`updateFeatureToggle`/`updateMemberRole`/`removeMember`/`inviteMember`（店主自助端點）與 `AdminService` 對應管理端動作屬同類別，全數零稽核 | Sprint 135（稽核日誌覆蓋率掃描，Workflow 對抗性驗證發現） | Sprint 135 | 注入 `AuditService`，4 個方法各補 `record(...)`（`FEATURE_TOGGLE_SELF_SERVICE_UPDATED`/`STORE_MEMBER_ROLE_CHANGED`/`STORE_MEMBER_REMOVED`/`STORE_MEMBER_INVITED`）。新增/修改測試（`TenantServiceTest` 新增 `UpdateFeatureToggleTests`+2、`updateMemberRole` 相關+2，既有 `inviteMember`/`removeMember` 測試補斷言）。詳見 [SPRINT_135_PLAN.md](SPRINT_135_PLAN.md) §7 |
| DEF-106 | `AdminService.reviewTenant()` 與同檔案 `approveTenant`/`rejectTenant` 相同類別的租戶審核動作，唯獨此舊版方法零稽核 | Sprint 135（稽核日誌覆蓋率掃描，Workflow 對抗性驗證發現） | Sprint 135 | 補上 `recordAudit("TENANT_APPROVED"/"TENANT_REJECTED", ...)`（沿用既有私有方法，不需新建 `AuditService`）。新增 `AdminServiceTest.ReviewTenant`（該方法先前零測試覆蓋，+3）。詳見 [SPRINT_135_PLAN.md](SPRINT_135_PLAN.md) §7 |
| DEF-102 | `FaqService.highlightKeyword()` 對 `question`/`answer` 原始文字從未 HTML escape，且呼叫端可用 `highlightPrefix`/`highlightSuffix` 查詢參數任意覆蓋包裹標籤，經前端 `dangerouslySetInnerHTML` 原樣執行，構成儲存型 XSS（可提權至 ADMIN、竊取 `localStorage` JWT） | Sprint 135（儲存型 XSS 全掃，Workflow 對抗性驗證發現） | Sprint 135 | 使用者拍板：Sprint 134 結尾記錄的兩個候選掃描角度（儲存型 XSS 全掃、稽核日誌覆蓋率）依序都做，本輪先做前者。Workflow 三階段掃描（Discover→Analyze→Verify）找出 18 筆候選，僅 1 筆confirmed。修法：`highlightKeyword()` 改為先用 Spring `HtmlUtils.htmlEscape()` 跳脫原始文字，再包上固定 `<mark>`/`</mark>`；直接移除 `highlightPrefix`/`highlightSuffix` 這兩個可自訂標籤內容的參數（`FaqArticleController`/`FaqService`/`frontend/src/services/faq.ts` 同步移除，全代碼庫確認零消費端使用非預設值，移除攻擊面比另做白名單驗證更簡單）；前端 `dashboard/faq/page.tsx` 改為條件渲染，只有存在後端保證已跳脫的 `highlightedQuestion`/`highlightedAnswer` 才用 `dangerouslySetInnerHTML`，否則以一般 JSX 插值渲染純文字（原本無論是否觸發高亮，`article.question`/`answer` 這個從未跳脫的 fallback 值都會走 `dangerouslySetInnerHTML`，是比 escape 缺失更根本的第二個問題）。新增 `FaqServiceTest.searchWithHighlight_escapesHtmlInStoredText`（+1）。詳見 [SPRINT_135_PLAN.md](SPRINT_135_PLAN.md) |
| DEF-101 | Metadata-only 媒體上傳端點（`/v2/media/upload`、`/v2/dashboard/media/upload-by-path`）允許呼叫端自報任意 `filePath`，`StorageService.getObject`/`objectExists` 無租戶前綴檢查，構成跨租戶 IDOR（任意物件讀取） | Sprint 134（檔案上傳驗證安全掃描，Workflow 對抗性驗證發現） | Sprint 134 | 使用者拍板：開新一輪系統性掃描聚焦「檔案上傳驗證繞過」角度，掃描結果登記 DEF-099~101 並同輪修復。根因：`core.media.MediaService.uploadAsset()`／`core.cms.media.MediaUploadService.uploadMedia(String 版)` 兩個 metadata-only 端點對 client 自報的 `filePath` 完全不做擁有權/存在性檢查即寫入 `MediaAsset.filePath`；`StorageService.getObject()` 下載時也只信任傳入字串，未驗證是否落在呼叫者租戶前綴下（「多租戶隔離」僅是 `buildObjectName()` 寫入時的命名慣例，從未在讀取時被強制驗證）。修法：`StorageService` 新增 `belongsToTenant(String objectName, UUID tenantId)`；寫入端（`uploadAsset`/`uploadMedia(String版)`）新增 `belongsToTenant` + `objectExists`（Sprint 132/133 已修好但零呼叫端的既有方法）雙重檢查，非本租戶或物件不存在一律拒絕（`E_9000`）；讀取端（`core.media.MediaService.downloadAsset`／`core.cms.media.MediaService.downloadMedia`）新增獨立的 `belongsToTenant` 檢查作為縱深防禦，即使未來出現其他寫入路徑繞過亦不影響下載端把關（分別回傳與既有「資產不存在」相同的 `E_4000`/`E_4103`，不額外洩漏「filePath 不屬於本租戶」這個內部判斷結果）。新增 `core.media.MediaServiceTest`（+4：`uploadAsset` 合法/非本租戶/物件不存在 3 案例 + `downloadAsset` 跨租戶阻擋 1 案例）、`MediaUploadServiceTest`（+1）、`core.cms.media.MediaServiceTest`（+1）。詳見 [SPRINT_134_PLAN.md](SPRINT_134_PLAN.md) §3 |
| DEF-100 | 媒體下載端點（`/v2/media/files/{assetId}`、`/v2/dashboard/media/{id}/file`）把使用者上傳時自訂的原始檔名未跳脫即字串拼接進 `Content-Disposition` header，可能被注入額外的 `filename*=` 參數 | Sprint 134（檔案上傳驗證安全掃描，Workflow 對抗性驗證發現） | Sprint 134 | `MediaController.getFile()`／`PostController.getMediaFile()` 皆為 `"inline; filename=\"" + file.fileName() + "\""` 原始字串拼接，未使用 Spring 內建的 `ContentDisposition` builder 做跳脫/RFC 6266 編碼。兩端點皆改用 `ContentDisposition.inline().filename(name, UTF_8).build()`，交由框架處理特殊字元跳脫與非 ASCII 檔名編碼。無需新增測試（既有 `M18MediaIntegrationTest`/`PostControllerE2ETest` 下載端點案例已覆蓋此程式碼路徑，且不斷言 header 具體格式，純屬程式碼健壯性修正，非行為變更）。詳見 [SPRINT_134_PLAN.md](SPRINT_134_PLAN.md) §2 |
| DEF-099 | 檔案上傳驗證（`MediaValidationService.determineFileType()`/`validateFileSize()`）僅信任用戶端可任意偽造的 multipart `Content-Type` header，全程無檔案實際內容（magic bytes）比對，允許清單與大小分級皆可被偽造繞過 | Sprint 134（檔案上傳驗證安全掃描，Workflow 對抗性驗證發現） | Sprint 134 | 系統性掃描候選角度之一（另兩項為儲存型 XSS 全掃、稽核日誌覆蓋率，使用者拍板僅本輪處理此項，其餘留待未來排程）。`MediaValidationService` 為 `core.media`/`core.cms.media` 兩模組共用單例 bean，修一次即覆蓋 `/v2/media/upload-multipart` 與 `/v2/dashboard/media/upload-multipart` 兩端點。新增 `validateActualContent(MultipartFile, String)`：讀取檔案開頭 16 bytes，比對 8 種允許型別（JPEG/PNG/GIF/WEBP/PDF/MP4/QuickTime/AVI）各自已知的 magic number 常數，不符即拋 `E_9000`；不新增第三方函式庫（無 Tika 等依賴），純手寫位元組比對。`uploadAssetMultipart()`/`uploadMedia(MultipartFile)` 於既有 `determineFileType` 後插入呼叫。新增 `MediaValidationServiceTest$ValidateActualContent`（+12：8 種型別各自的正確簽章通過案例 + 4 個偽造/空檔案拒絕案例）。詳見 [SPRINT_134_PLAN.md](SPRINT_134_PLAN.md) §1 |
| DEF-098 | `dashboard/media/page.tsx`（M18 媒體中心，DEF-096 修復）的 `<img src={item.url}>` 直接指向需 Bearer token 的授權端點，但 `<img>` 標籤無法附加 Authorization header，真實瀏覽器渲染時圖片顯示可能仍失敗 | Sprint 133（修 DEF-097 時查證發現） | Sprint 133 | 與 DEF-097 根因相同，經使用者確認同輪一併修復（而非另開排程）。改用與 DEF-097 相同的共用 `AuthenticatedImage` 元件 + `services/media.ts` 新增 `getMediaAssetFileBlob()`（apiClient 帶 token 抓 blob 建立 object URL），取代原本無法送出 Authorization header 的裸 `<img src={item.url}>`。詳見 [SPRINT_133_PLAN.md](SPRINT_133_PLAN.md) §2 |
| DEF-097 | `/cms/media` 頁面圖片預覽疑似從未正確渲染：`filePath` 為 MinIO 內部物件路徑，瀏覽器無法直接存取；`StorageService.objectExists(UUID, String)` 有與 DEF-096 修復前的 `getObject` 相同的路徑重新推導設計缺陷 | Sprint 132（修 DEF-096 時查證發現） | Sprint 133 | 經與使用者確認採用「後端授權串流端點 + 前端 blob fetch」方案，而非 presigned URL——目前 `STORAGE_ENDPOINT` 為 docker 內部 hostname `http://minio:9000`，瀏覽器無法解析，presigned URL 在本機 docker-compose 環境不可行。後端：`core.cms.media.MediaService` 新增 `downloadMedia()`（比照既有 `getMedia`/`deleteMedia` 的 tenant 擁有權檢查模式）+ `MediaFile` record；`PostController` 新增 `GET /v2/dashboard/media/{id}/file`（串流回應）；一併修正 `StorageService.objectExists(UUID,String)` 同款路徑重新推導缺陷（全庫零呼叫端，改簽章為 `objectExists(String)`，比照 DEF-096 的 `getObject`/`deleteObject` 慣例）。前端：新增共用 `AuthenticatedImage` 元件（`components/ui/authenticated-image.tsx`），改用 apiClient 帶 token 抓 blob 建立 object URL，取代原本無法送出 Authorization header 的 `<img src={media.filePath}>`。查證過程中發現 DEF-096 自己的前端修復也有相同根本問題（`<img>` 標籤無法附加 Authorization header），經使用者確認另登記 DEF-098 同輪一併修復。新增 `core.cms.media.MediaServiceTest$DownloadMedia`（+3）、`PostControllerE2ETest.getMediaFile_shouldSucceed`（+1）。詳見 [SPRINT_133_PLAN.md](SPRINT_133_PLAN.md) §1 |
| DEF-096 | M18 媒體資產庫（`/v2/media/upload` JSON 端點）後端與前端 service 皆已完整實作，但從未被任何頁面串接使用 | Sprint 131（修 DEF-089 時查證發現） | Sprint 132 | 使用者拍板：繼續開發串接（非移除）。查證後發現缺口比「補上傳按鈕」更深——既有 JSON 端點要求呼叫端已持有 `filePath`，**完全沒有真正的二進位上傳能力**；`StorageService.getObject(UUID,String)` 透過 `buildObjectName()` 重新產生亂數 UUID，與 `uploadFile()` 實際回傳路徑不符，全庫零呼叫端、從未被正確使用過。修復：`StorageService.getObject` 改簽章為 `getObject(String objectName)` 直接用完整路徑取檔（比照既有 `deleteObject(String)` 正確模式）；`core.media.MediaService` 新增 `uploadAssetMultipart()`（真實寫入 `StorageService`，分類/標籤/替代文字/標題皆選填）與 `downloadAsset()`；`MediaController` 新增 `POST /v2/media/upload-multipart`（`media:create`）與 `GET /v2/media/files/{assetId}`（`media:read`，串流回應）。前端 `dashboard/media/page.tsx` 新增上傳區塊（分類/標籤選填）+ 快速新增分類，圖片預覽改用新端點組出的 `item.url` 取代不可存取的 `item.filePath`。新增 `core.media.MediaServiceTest`（+6，此 Service 先前完全零測試覆蓋）+ `M18MediaIntegrationTest`（+2，實際二進位上傳／檔案串流）。調查過程發現 `/cms/media` 頁面圖片預覽有同源但獨立的問題，另開 DEF-097。詳見 [SPRINT_132_PLAN.md](SPRINT_132_PLAN.md) §3 |
| DEF-095 | ERP 採購單編輯頁「更新訂單」按鈕是空實作：`onClick={() => {/* update */}}`，點擊完全無反應 | Sprint 131（修 DEF-090 時查證發現） | Sprint 132 | 使用者拍板：擴充後端支援編輯 `expectedDeliveryDate`（非改唯讀）。`PurchaseOrderUpdateRequest` 新增 `expectedDeliveryDate: LocalDate`；`PurchaseOrderService.updatePurchaseOrder()` 比照既有 notes 的 null-guard 慣例新增賦值（僅 DRAFT 可更新）；前端補上 `handleUpdate()` 接線。已知限制：與既有 notes 欄位相同，非 null 才更新，暫無法透過編輯表單清空已設定的到貨日期。新增 `PurchaseOrderServiceTest` 1 案例。詳見 [SPRINT_132_PLAN.md](SPRINT_132_PLAN.md) §2 |
| DEF-094 | `checkout/page.tsx`（純訂房結帳頁）與 DEF-084 相同結構的電話驗證/送出不一致 bug | Sprint 131（修 DEF-084 時查證發現） | Sprint 132 | 無需決策的純機械性修復，套用與 DEF-084 完全相同的修法：送出前補 `.replace(/\s/g,'')`。詳見 [SPRINT_132_PLAN.md](SPRINT_132_PLAN.md) §1 |
| DEF-087 | 房源搜尋的 `keyword` 參數後端完全不使用，且連帶使 `location` 過濾失效 | Sprint 128（契約漂移掃描） | Sprint 131 | 調查發現比原描述更嚴重：`keyword` 分支主體與預設分支呼叫完全相同查詢（等同回傳未過濾全部房源），且 `location`/`maxGuests` 兩分支**完全沒有 tenantId 過濾**（疑似跨租戶資料外洩，與 DEF-023/024/038/040/041 同類）。改寫 `RoomService.getRooms()` 為 Specification 組合查詢（比照 `AdminService.getTenants` 既有慣例），keyword/location/maxGuests 可同時 AND 套用，且統一以 tenantId+ACTIVE 為共用基礎條件，一次修復兩個症狀。`RoomRepository` 新增 `JpaSpecificationExecutor`，移除變成死碼的 `findByMinGuests`/`findByLocation`/`findByListingTenantIdAndListingStatus`。新增 `M02RoomSearchIntegrationTest`（真實 PostgreSQL，6 案例：keyword 過濾、跨租戶不外洩、keyword+location AND、location 跨租戶防護、maxGuests 跨租戶防護、無條件僅回傳當前租戶）。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md) §1 |
| DEF-086 | M09 通知模板管理實際只有唯讀列表：前端完全沒有建立與編輯模板的 UI | Sprint 128（修 DEF-073 時發現） | Sprint 131 | 調查發現後端 API（`notification_template:create`/`update` 權限碼、DTO 驗證）與既有可套用的表單 pattern（`faq/categories`、`posts/categories`）都已齊備，不需要全新 UX 設計；只改 `dashboard/notifications/page.tsx` 一個檔案，新增建立/編輯共用 modal（比照既有 pattern），MVP 不做手動 variables 欄位（後端已能從 contentTemplate 自動萃取）、不做富文本編輯器（內部後台工具）。實際工作量遠小於原估 5 SP。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md) §2 |
| DEF-082 | ERP 採購單列表「供應商」永遠空白：`supplierName` 後端 mapper 從不填 | Sprint 128（契約漂移掃描） | Sprint 131 | 後端 `PurchaseOrderService.toDto()` 補查 `supplierRepository`；單筆情境用 `findById`，列表分頁改用 `findAllById` 批次查詢後組 Map 避免 N+1（比照 DEF-064 先例的既有教訓）。新增 2 案例（單筆/批次皆正確填入 supplierName）。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md) §3 |
| DEF-081 | ERP 採購單列表卡片標題永遠空白：前端讀 `orderNumber`，後端只產生 `poNumber` | Sprint 128（契約漂移掃描） | Sprint 131 | 統一改前端對齊後端既有的 `poNumber`（後端全路徑與同專案 `admin/purchase-orders/page.tsx` 皆已用此命名，不新增後端別名欄位）。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md) §4 |
| DEF-083 | 知識庫文章標籤 `tags` 全鏈路未接線：後端 mapper 從不填 → 列表頁標籤區塊永遠不渲染 | Sprint 128（契約漂移掃描） | Sprint 131 | 調查發現不只讀取端沒接、寫入端（create/update）也完全沒讀取 request 的 tags 就丟棄。沿用既有「逗號分隔 TEXT 欄位」設計（`createVersionSnapshot()` 已有前例），在 `KnowledgeBaseService` 的 create/update/toDto 三處補上賦值/讀回，不做 JSONB 型別升級、不清理孤兒的 `knowledge_article_tags` 正規化資料表（範圍外，留待未來獨立處理）。新增 2 案例。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md) §5 |
| DEF-080 | 知識庫建立文章必定 400：後端 `@NotNull` 要 `authorId`、`@NotBlank` 要 `slug`，前端兩者皆無 | Sprint 128（契約漂移掃描） | Sprint 131 | 使用者拍板：順便修後端 authorId 設計缺陷（雖為死路徑，零使用者曝險）。`authorId` 改由伺服器端 `TenantContext.getCurrentUser()` 推導（比照 `CmsService.createPage` 既有慣例），不再信任呼叫端傳入的 UUID；`CreateKnowledgeArticleRequest` 移除 `authorId` 欄位。`slug` 前端型別/接線維持現狀不動（前端仍無建立文章 UI，屬功能缺口非本次範圍）。同步調整 3 個既有測試改用 `TenantContext.setCurrentUser`。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md) §6 |
| DEF-084 | 合併結帳含空白的電話必定 400：前端驗證時剝除空白但送出時不剝除 | Sprint 128（契約漂移掃描） | Sprint 131 | `checkout/mixed/page.tsx` 送出邏輯補上與驗證邏輯相同的 `.replace(/\s/g,'')`，兩者正規化運算式維持一致。調查發現 `checkout/page.tsx`（非合併結帳）有完全相同結構的姊妹缺陷，依 Rule 3 精準改動另開 DEF-094 追蹤，不併入本次修復。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md) §7 |
| DEF-085 | 更新媒體只改 tags 必定 400：後端 `UpdateMediaRequest.categoryId` 為 `@NotNull`，前端宣告為選填 | Sprint 128（契約漂移掃描） | Sprint 131 | 移除 `@NotNull`，改為選填——與 `MediaService.updateAsset()` 既有 partial-update null-check 邏輯、全庫同類 Update DTO 慣例（`UpdateFaqArticleRequest`/`UpdateKnowledgeArticleRequest`）、資料庫欄位可為 null 三者一致。新增「只送 tags 不帶 categoryId 應成功」整合測試案例。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md) §8 |
| DEF-088 | 冪等衝突回應硬編碼 `"E_6005"`（底線），與 `ErrorCode` 正式 wire code `"E-6005"`（連字號）不符 | Sprint 128（契約漂移掃描） | Sprint 131 | `BookingController`/`CheckoutController` 改為引用 `ErrorCode.E_6005.getCode()`（既有 `GlobalExceptionHandler`/`RateLimitFilter` 已有此慣例），不再重新拼字串。同步修正一個原本斷言底線版本（等同鎖定此 bug）的既有 E2E 測試。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md) §9 |
| DEF-089 | `cms.ts` 的 `uploadMedia` 以 multipart FormData 打 `/v2/media/upload`，但該端點只吃 JSON 且有必填欄位 | Sprint 128（契約漂移掃描） | Sprint 131 | 確認此函式零呼叫點、且與已在生產路徑正常運作的 `uploadMediaMultipart()`（打既有的 `/v2/dashboard/media/upload-multipart`）功能完全重複，直接刪除。調查發現其背後對應的整套「M18 媒體資產庫」JSON 端點+前端 service 皆為完整實作但從未串接使用的獨立半成品功能，另開 DEF-096 追蹤是否要繼續開發或移除。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md) §10 |
| DEF-090 | 更新採購單時 `expectedDeliveryDate`／`items` 被靜默丟棄：後端 `UpdateRequest` 只有 `notes` | Sprint 128（契約漂移掃描） | Sprint 131 | 確認後端刻意設計（註解已寫明 items 需取消重建），僅縮小前端型別為 `{notes?: string}`，零風險（呼叫端本來就不存在，`PurchaseOrderForm.tsx` 的「更新訂單」按鈕是空實作）。調查發現此按鈕空實作範圍明顯更大（含 notes 都存不了），另開 DEF-095 追蹤。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md) §11 |
| DEF-091 | `listing.ts` 的 `PriceBreakdown` 宣告 `price`，後端只產生 `basePrice`/`adjustedPrice` | Sprint 128（契約漂移掃描） | Sprint 131 | 修正型別使其完整對齊後端 DTO（保留兩個欄位，非二選一取代），因為目前 `getListingPrice()` 零呼叫點，尚無顯示邏輯需要決定用哪個欄位。不合併與 `services/pricing.ts` 重複的定義（範圍外）。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md) §12 |
| DEF-093 | `ListingController.getListings()`/`getListing()` 直接序列化裸 `Listing` 實體，`owner`（LAZY → `User`）懶載入會把整個 `User`（含 `passwordHash`，無 `@JsonIgnore`）序列化進 API 回應 | Sprint 129（查證 DEF-076 既有 listing 端點時意外發現） | Sprint 130 | 新增 `ListingResponse` DTO（比照 Sprint 129 `ListingOptionDto` 慣例），一律用 `Listing.getTenantId()`/`getOwnerId()` 影子欄位取值，完全不觸碰 LAZY 的 `owner`/`tenant` 關聯；`ListingController.getListings()`/`getListing()` 改回傳此 DTO。**紅燈實測確認漏洞屬實**：修復前呼叫兩端點，回應 JSON 明文含 `"passwordHash":"$2a$10$..."`；修復後 `owner`/`passwordHash` 鍵值完全消失。新增 `ListingControllerE2ETest`（2 案例，`@MockBean ListingRepository` + `@WithMockUser`，斷言回應不含 `owner`/`passwordHash` 且保留前端依賴欄位）。前端 TS `Listing` 介面欄位（`id/tenantId/listingType/title/description/coverImageUrl/status/basePrice/currency/tags/createdAt/updatedAt`）全數保留，無破壞性變更。詳見 [SPRINT_130_PLAN.md](SPRINT_130_PLAN.md) §1 |
| DEF-092 | 5 個權限碼僅 SUPER_ADMIN 到得了：`cms:read/create/update/publish`、`notification:create` | Sprint 128（修 DEF-073 時做全庫檢查發現） | Sprint 130 | 依 Sprint 129 已拍板的細緻方案實作（非 DEF-073/075 的 OWNER全權/STAFF唯讀/ADMIN全權 統一樣板）：`cms:read/create/update` 比照 `PostController` 既有 CRUD 開放範圍，授予 `STORE_OWNER`/`STORE_STAFF`/`SELLER`/`HOST`（無唯讀限制）；`cms:publish`／`notification:create` 限制較高層級，僅 `STORE_OWNER`+`ADMIN`（`SUPER_ADMIN` 經 `EnumSet.allOf` 自動取得）。**過程中發現本輪原先問使用者的選項不完整**——調查階段一開始只問「比照 PostController」vs「沿用 DEF-073/075 慣例」兩個籠統選項，未即時發現本表原記錄的細緻拆分方案，使用者選了 DEF-073/075 式統一方案並已實作＋測試通過後才發現此落差，經誠實揭露後由使用者改判採用本表原方案，重寫 `RolePermissionMapping` 角色分派與 `RolePermissionMappingTest` 斷言。`PreAuthorizePermissionCoverageTest.SUPER_ADMIN_FALLBACK_ONLY` 清空（原列 5 碼皆已修復）；新增 `def092CodesAreDefined()`＋`RolePermissionMappingTest` 5 案例（STORE_OWNER 全權、STORE_STAFF/SELLER/HOST 僅 CRUD 無 publish、ADMIN 全權跨租戶、SUPER_ADMIN 全權）。詳見 [SPRINT_130_PLAN.md](SPRINT_130_PLAN.md) §2 |
| DEF-075 | 13 個權限碼在 `Permission` 枚舉不存在，`dashboard`/`faq`/`knowledge`/`media` 四模組端點對所有角色（含 SUPER_ADMIN）必定 403 | Sprint 128（修 DEF-073 時做全庫檢查發現） | Sprint 129 | 根因同 DEF-073：`RolePermissionMapping.getAuthorities()` 只發枚舉內的碼，`SUPER_ADMIN` 的 `EnumSet.allOf` 也給不了枚舉沒有的東西。角色分派比照 `NOTIFICATION_TEMPLATE_*`/`SUPPORT_TICKET_*`/`RETURN_*` 既有先例：`STORE_OWNER` 13 碼全授、`STORE_STAFF` 僅 4 個 `*:read`、`ADMIN` 13 碼全授（跨租戶）、`SUPER_ADMIN` 經 `allOf` 自動取得。`PreAuthorizePermissionCoverageTest.KNOWN_UNMAPPED_PENDING_DEF_075` 清冊清空（原列 13 碼皆已修復，依既有規則「清冊不得包含已修好的碼」同步移除）。新增 `RolePermissionMappingTest`（4 案例）＋ `PreAuthorizePermissionCoverageTest.def075CodesAreDefined`。詳見 [SPRINT_129_PLAN.md](SPRINT_129_PLAN.md) §2 |
| DEF-076 | ERP 建立採購單：後端品項 `@NotNull` 要 `listingId`，前端從不送出 → 建單必定 400 | Sprint 128（契約漂移掃描） | Sprint 129 | 產品決策拍板：新增下拉選擇器送出 `listingId`。查證發現既有 `GET /v2/listings` 不過濾租戶、且直接序列化裸 `Listing` 實體（連帶發現 DEF-093 潛在密碼雜湊洩漏面），故新增專用端點 `GET /v2/dashboard/purchase-orders/listing-options`（`ErpController`，依 `TenantContext` 過濾 + 回傳輕量 `ListingOptionDto`，不夾帶 `owner`/`tenant` 關聯）。路徑刻意避開 `/v2/dashboard/listings`——紅燈實測發現該路徑已被 `AnalyticsController#getListingStats()` 佔用（`Ambiguous mapping`）。前端 `PurchaseOrderForm.tsx` 新增商品下拉選擇器並移除原本綁在自由文字輸入框、從未對映到有效 UUID 的 `skuId` 送出邏輯。新增 `M16ErpIntegrationTest` IT-M16-012（租戶過濾 + 狀態過濾）。詳見 [SPRINT_129_PLAN.md](SPRINT_129_PLAN.md) §3 |
| DEF-077 | ERP 建立採購單：前端送 `unitPrice`，後端 `@NotNull` 要 `unitCost`（無 `@JsonProperty` 對映）→ 同樣必定 400 | Sprint 128（契約漂移掃描） | Sprint 129 | 與 DEF-076 同一段 `items[]` payload 一起修復。前端 create-request 型別欄位名改為 `unitCost`（讀取端 `unitPrice` 欄位名維持不變，僅建立請求的欄位對齊）。`PurchaseOrderService.java:98` 的 `.multiply()` NPE 疑慮隨欄位名對齊後由既有 `@NotNull`/`@Valid` 驗證正常擋下（400 而非 500），未需額外程式碼變更。詳見 [SPRINT_129_PLAN.md](SPRINT_129_PLAN.md) §3 |
| DEF-078 | ERP 採購單檢視／編輯頁必定顯示「載入採購訂單失敗」：回應的 `expectedDeliveryDate` 恆為 null，前端對 null 呼叫 `.split()` | Sprint 128（契約漂移掃描） | Sprint 129 | 產品決策拍板：補進實體/mapper（而非前端改容忍 null）。`PurchaseOrder` 新增 `expectedDeliveryDate`（`LocalDate`）+ `V78` migration；`PurchaseOrderCreateRequest` 新增選填欄位並由 `createPurchaseOrder`/`toDto` 讀寫；前端額外補上 null 防呆（既有採購單此欄位仍可能是 null，防呆是必要的，非取代根因修復的權宜之計）。新增 `PurchaseOrderServiceTest`（3 案例）。`make validate-schema` 通過，無漂移。詳見 [SPRINT_129_PLAN.md](SPRINT_129_PLAN.md) §4 |
| DEF-079 | ERP 供應商「檢視／編輯」兩頁必定失敗：`GET /v2/dashboard/suppliers/{id}` 後端只有 PUT 沒有 GET mapping | Sprint 128（契約漂移掃描） | Sprint 129 | 純粹缺一支 controller mapping，`SupplierService.getSupplier()` 與前端呼叫端皆早已存在且正確。`ErpController` 補 `GET /suppliers/{id}`。新增 `M16ErpIntegrationTest` IT-M16-009~011（成功、不存在回 404、他租戶回 404 租戶隔離）。詳見 [SPRINT_129_PLAN.md](SPRINT_129_PLAN.md) §5 |
| DEF-072 | `Idempotency-Key` 未列入 CORS `allowedHeaders`，訂房結帳與合併結帳在瀏覽器上必定失敗 | Sprint 128（前後端契約漂移全掃） | Sprint 128 | **本輪後果最重、修復最小的一項**。`SecurityConfig.java:120` 白名單缺此標頭，前端 `booking.ts:253`（`POST /v2/bookings`）與 Sprint 126 新建的 `checkout.ts:72`（`POST /v2/checkout/mixed`）都會帶它。⚠️ **紅燈實測更正了機制的原始推測**：`checkHeaders` 實回傳 `["authorization","content-type"]` 而**非 null**（Spring 實作為 `result.isEmpty() ? null : result`），故**伺服器不會 403**，是**瀏覽器**依缺漏的 `Access-Control-Allow-Headers` 自行封鎖真正的 POST，`err.response` 為 undefined、只顯示通用文案，真因完全不可見——這正是它長期存活的原因。既有測試抓不到：後端零 preflight 測試、前端 E2E 用 `page.route` 攔截回固件繞過瀏覽器 CORS。新增 `SecurityConfigCorsTest`（4 案例，含「不得退化成 `*` 全開」的反向斷言）。詳見 [SPRINT_128_PLAN.md](SPRINT_128_PLAN.md) §3 |
| DEF-073 | 通知模板四個權限碼在 `Permission` 枚舉不存在，create/update/delete/render 對所有角色（含 SUPER_ADMIN）必定 403 | Sprint 128（前後端契約漂移全掃） | Sprint 128 | 生產唯一授權來源是 `JwtAuthenticationFilter:61` → `RolePermissionMapping.getAuthorities()`，只發枚舉內的碼；`SUPER_ADMIN` 的 `EnumSet.allOf(Permission.class)` 給不了枚舉沒有的東西。使用者可見後果：刪除必定失敗；預覽的 403 被 catch 吞掉改寫成「請檢查變數設定」，**誤導使用者去查變數，真因是權限碼缺漏**。**存活原因再次命中專案已記錄的警訊模式**：`IntegrationTestConfiguration` 是手工維護的 mapping 複本（註解還寫「與真實實作保持一致」），用 Mockito spy 整個替換掉真實 mapping，且把 `notification_template:*` 錯掛在 **BUYER** 底下，M09 整合測試用買家帳號跑因而一路綠燈。修復：枚舉新增 4 常數 + STORE_OWNER/ADMIN 全授、STORE_STAFF 僅 READ；固件改掛 STORE_OWNER；M09 改為提升 `User.role` 後登入，走生產授權路徑。防復發：新增 `PreAuthorizePermissionCoverageTest`（反射掃全部 `@PreAuthorize`，斷言每個碼都在枚舉內）。⚠️ **誠實揭露**：M09 通過不能證明生產枚舉正確（spy 會整個替換 mapping），真正守住生產事實的是該單元測試。詳見 [SPRINT_128_PLAN.md](SPRINT_128_PLAN.md) §4 |
| DEF-074 | `/cms/media` 媒體庫永遠列不出檔案且頁面崩潰：前端讀 `media`、後端產生 `items` | Sprint 128（前後端契約漂移全掃） | Sprint 128 | `M15Dto.java:315` 為 `items`，前端 `cms.ts:130` 宣告 `media`；`setMediaList(data.media)` 得 undefined，`page.tsx:178` 對 undefined 取 `.length` 拋 TypeError。`totalCount`/`totalPages` 欄位名相符會正常顯示，畫面呈現「全部 (N) 有數字但格子區崩潰」的自相矛盾狀態，證明此路徑從未運作過。改前端對齊後端（後端 `items` 為 DTO 真實欄位名、本專案各列表 DTO 自訂欄位名無統一慣例、前端僅兩個消費端）。ℹ️ 前端實打 `/v2/dashboard/media`（`PostController`，`hasAnyRole`），非 `MediaController` 的 `/v2/media`，故不受 DEF-075 權限缺口阻擋。詳見 [SPRINT_128_PLAN.md](SPRINT_128_PLAN.md) §5 |
| DEF-071 | ERP 收貨確認頁（`/dashboard/erp/purchase-orders/:id/receive`）完全無法使用（三缺陷疊加） | Sprint 127（修 DEF-070 時檢視前端呼叫端發現） | Sprint 127 | **AskUserQuestion 拍板併入本輪**。三個問題疊加：(1) 前端型別 `PurchaseOrderReceiveRequest.items[]` 送出 `skuId`，後端 DTO 要求的是 `itemId`（`@NotNull`），請求必被 400 拒絕；(2) 畫面「已收」是唯讀文字，整個收貨模式的表格沒有任何可編輯的收貨數量欄位；(3) `handleReceive` 把讀到的既有累計已收量原樣送回，即使修好 (1) 對全新採購單也等於「這次收 0 件」。三者疊加代表這條路徑從未真正運作過——`grep` 全專案 `*.spec.ts` 對 `purchase-order`/`erp` 零命中，ERP 模組本身零 E2E 涵蓋，符合「測試（或這裡是完全沒有測試）繞過同一段邏輯」的既有警訊模式。修法：`purchaseOrder.ts` 型別改 `itemId`；`PurchaseOrderForm.tsx` 新增 `id`／`receiveQty` 欄位、`fetchOrder` 預設帶入剩餘量、新增可編輯「本次收貨」欄位（`[0,剩餘量]` 區間限制）、`handleReceive` 改送 `itemId`+`receiveQty` 並過濾空清單。`tsc --noEmit` 0 error、`eslint` 0 新增 warning、`npm run build` 成功。**誠實揭露**：本輪未新建 Playwright E2E（純前端契約修正無新業務邏輯，依 Sprint 126 同一判準），記錄為未來 Sprint 候選：仿 `at-m17-002.spec.ts` 的固件鏈補齊整個 ERP 模組的 E2E 缺口。詳見 [SPRINT_127_PLAN.md](SPRINT_127_PLAN.md) |
| DEF-070 | `PurchaseOrderService.receivePurchaseOrder` 收貨數量無上限驗證，單次或分批累計皆可超收，虛增庫存 | Sprint 127（本輪 `ErrorCode` 孤兒碼掃描發現） | Sprint 127 | 第九輪掃描改採「131 個 `E_XXXX` 逐一比對是否曾被 `ErrorCode.E_XXXX` 字面拋出」找到 27 個候選，多數為已審視過的偽陽性（`E_8009`／`E_4091` 等），`E_7009`（無效的收貨數量）為真缺口：`newReceivedQty = item.getReceivedQuantity() + receiveItem.getReceivedQuantity()` 直接寫回，從未檢查是否超過 `item.getQuantity()`（訂購量），單次超收與分批累計超收皆無阻擋，會虛增 `product_inventory.total_qty` 且讓 PO 狀態機判定失真。**紅燈先行**：`PurchaseOrderServiceTest` 新增 2 個測試（單次超收、分批累計超收）修復前皆失敗（意外落到未 mock 的 `createInboundMovement` 路徑回傳 `E_3003`，證實無任何阻擋），修復後正確拋 `E_7009` 且不觸碰 `productInventoryRepository`/`stockMovementRepository`/`save`。修法：寫回前新增 `newReceivedQty > item.getQuantity()` 檢查即拋錯。`mvn -o test` 全量 1074 個 0 Failures（3 Errors 為 `SellerDashboardServiceCacheTest` 既有環境缺口，`make test-db-up` 後重跑確認與本輪無關）；checkstyle 0 violations。過程中順帶檢視前端呼叫端發現範圍外的 DEF-071（見上）。詳見 [SPRINT_127_PLAN.md](SPRINT_127_PLAN.md) |
| DEF-048 | 混合購物車（PRODUCT + ROOM）的優惠券折扣基數，購物車顯示與結帳實收不一致 | Sprint 101 | Sprint 126 | 查證後發現比原記錄更根本：「一次結帳同時結清 PRODUCT 與 ROOM」這個使用者流程根本不存在（`cart/page.tsx` 二選一導頁），兩邊促銷碼邏輯零共用。使用者兩輪拍板**擴大範圍為真的新建合併結帳流程**（非純顯示端修正），並拍板三項規則：FIXED_AMOUNT 依小計比例分攤、合併結帳用一張券算 1 次額度、兩側都取消才退還。**後端**：`PromoService` 新增 `resolveValidPromoForCheckout`／`computeCappedDiscount`／`allocateDiscount`／`releaseOrderSide`／`releaseBookingSide` 共用方法（消除 `OrderService`／`BookingService` 原本各自複製一份的重複邏輯）；`OrderService.buildProductOrder`／`BookingService.buildBookingCore` 抽出可重用核心（回傳 Response DTO 而非實體）；新 `CombinedCheckoutService`（單一 `@Transactional` 內依序建立 Order+Booking，任一邊失敗兩邊自動回滾，不需 saga）＋`POST /v2/checkout/mixed`；`V77` migration 放寬 `promo_code_usages` CHECK 約束為「至少一個非 null」＋新增 `order_released_at`／`booking_released_at` 部分取消追蹤欄位。**前端**：新 `checkout/mixed` 頁面（整合既有兩頁的地址/旅客表單＋一個共用促銷碼輸入框）、`cart/page.tsx` 結帳按鈕改三分支路由。單元測試 1072 個全綠、新增真實 DB 整合測試 4 案例（成功／交易回滾／單側取消不退款／雙側取消才退款）全綠；前端 `tsc`/`eslint`/`npm run build` 全過。**明確不變**：未新增 Playwright E2E 規格（風險最高的交易正確性已由後端整合測試涵蓋，純前端 UI 組裝沿用已驗證頁面的既有模式，留待日後有需求時再排入，非靜默跳過）；`validate-schema-doc.sh` 的 `pg_isready` 就緒判斷 flakiness（過程中意外發現，與本次改動無關）已記錄於 [[validate-schema-doc-pg-isready-race]] 但不在本次範圍修復。詳見 [SPRINT_126_PLAN.md](SPRINT_126_PLAN.md) |
| DEF-056 | `ChatService` 未讀計數為讀後寫，併發訊息會少計 | Sprint 105 | Sprint 125 | **使用者授權依序處理三項待辦時排第三項，無需業務決策**。`conversation.setRecipientUnreadCount(...+1)` 後接 `save()` 是讀-改-寫，同一對話的併發訊息會互相覆蓋計數。修法：新增 `ConversationRepository.recordNewMessage` 單一原生 UPDATE，混合兩種語意——`last_message_id`／`last_message_preview`／`last_message_at` 絕對賦值（後寫入者覆蓋語意正確）、`initiator_unread_count`／`recipient_unread_count` 相對遞增（`COALESCE(...,0) + :delta`），取代原本混在一起的 JPA 實體讀-改-寫。**負向測試證實有效**：暫時換回讀-改-寫寫法，新增的 `M10ChatUnreadCountConcurrencyIntegrationTest`（20 執行緒併發呼叫 `sendMessage`）如期失敗（期望 20、實得 3），換回原子 UPDATE 後精準等於 20。`ChatServiceStompBroadcastTest` 新增 2 個遞增方向驗證（initiator/recipient 互換），既有測試移除已不成立的 `save(Conversation)` stub。**明確不變**：`markAsRead` 的計數歸零維持原樣，非本缺陷描述的「相對遞增互相覆蓋」問題（絕對設 0 不依賴讀到的值），且與 `sendMessage` 之間的極窄交錯窗口非 DEF-056 記錄範圍，未擴大處理。無 migration，`mvn -o verify` 全量（與 Sprint 124 合併跑一次，見 SPRINT_125_PLAN.md 說明）全綠。詳見 [SPRINT_125_PLAN.md](SPRINT_125_PLAN.md) |
| DEF-047 | ROOM（訂房）訂單無法套用優惠券（PRD US-010 未落地） | Sprint 100 | Sprint 124 | **使用者授權依序處理三項待辦時排第二項**。**原記錄的修復目標查證後被推翻**：原記指向 `OrderService.createRoomOrder`（`POST /v2/orders` 的 ROOM 分支），但動工前查證 frontend 對 `orderType: 'ROOM'` 的送出點——**零命中**（唯一送出者是 `OrderServiceTest.java`），該路徑從未被任何前端頁面呼叫。買家實際的訂房結帳頁（`cart/page.tsx:495` 導入 `/checkout`）呼叫的是 `bookingService.createBooking` → `POST /v2/bookings` → `BookingController`／`core.booking.BookingService`——與 `OrderService` 完全獨立的服務與實體（`Booking`，非 `Order`），`bookings` 表從未有促銷碼欄位。PRD US-010 開頭「作為**預訂**買家」也與 `BookingService` 語意一致。若只修 `OrderService.createRoomOrder`，PRD US-010 實際上仍是斷鏈的。**改為修復真正被使用的 `BookingService` 路徑**，`OrderService.createRoomOrder` 維持原樣不動（確認為前端零呼叫的既有架構分裂，非本次範圍）。修法比照 Sprint 100 `OrderService` 已建立的模式（`resolveValidPromoForCheckout`／`commitPromoUsage`／`refundPromoUsage`／`applyPromoDiscount`）移植：`V76` 新增 `bookings.promo_code`／`discount_amount`，`promo_code_usages.order_id` 放寬為可空並新增 `booking_id`（FK `bookings`）+ CHECK 約束恰好一個來源非 null（`order_id` 原有 FK 到 `orders`，不能塞入 booking id）。`createBooking` 建立訂房時驗證並套用折扣（訂房無運費，`FREE_SHIPPING` 券折扣基數傳 0，非新增缺陷）；`cancelBooking` 取消時退還額度。**Booking 特有風險並已一併處理**：`updateBooking` 的日期異動路徑（`Order` 沒有此功能）若只重算總額不重算折扣，改個日期會讓已算好的折扣憑空消失，修法為依已記錄的券別對新總額重算（不重新驗證/不重佔額度）。新增 `BookingPromoCodeTest`（12 案例，比照 `OrderPromoCodeTest` 同一套結構）；前端 `checkout/page.tsx` 新增優惠碼輸入框＋成功畫面顯示折扣，`services/booking.ts` 補齊型別與錯誤訊息。`createBooking` 抽出 `applyPromoDiscount` 避免 NPath 複雜度衝到 checkstyle 上限（同 Sprint 100 理由）。`mvn -o verify` 全量（單元+整合+checkstyle+PMD）全綠；前端 `tsc --noEmit` 0 error、`npm run build` 成功。詳見 [SPRINT_124_PLAN.md](SPRINT_124_PLAN.md) |
| DEF-068 | **schema 文件守門只涵蓋「文件裡已經寫到的表」**：`SRD_Database_Schema.md` §2 只有 14 張表的 DDL，`check_schema_doc.py` 逐一走訪文件區塊比對——文件沒寫的表，漂移完全不會被發現 | Sprint 117 | Sprint 123 | **使用者授權依序處理三項待辦時排第一項**。三選一原記錄 (a)/(b)/(c)，**拍板選 (b)+(c) 混合，放棄純 (a)**：查證後實作共 65 張表，SRD+PRD 只覆蓋 26 張、**39 張完全未被任何文件提到**，純 (a) 补齊全部 DDL 規模遠超原估 3 SP 且需人工審閱語意，不做。改為新增 SRD §2.6「已知範圍外資料表」清單（39 張依模組分類，非逐欄 DDL）+ `check_schema_doc.py` 新增 `check_coverage()` 涵蓋率守門——任何實作表不在「SRD §2」∪「PRD §8.2」∪「§2.6 清單」任一處即報錯，堵住**未來**新表零文件的路徑；§2.6 清單本身若列了已不存在的表也報錯（防清單漂移）。**負向測試證實有效**：暫時從 §2.6 移除 `addresses` 一列，`make validate-schema-doc` 如預期點名報錯，還原後恢復綠燈。**明確不變**：現有 39 張表缺正式 DDL 仍是技術債，如實記錄不假裝解決；`audit_log`/`audit_logs` 兩表並存是否重複、`_media_assets_backup`/`listings_tags_backup` 兩張遷移殘留備份表是否清除，均非本次範圍僅存查。純 `docs/`+`scripts/lib/` 變更，未動 `backend/`/`frontend/`。詳見 [SPRINT_123_PLAN.md](SPRINT_123_PLAN.md) |
| DEF-052 | `-Pintegration-test` 是一個**不存在的 Maven profile** | Sprint 104 | Sprint 122 | **使用者拍板選此項作為 Sprint 122 主題**（3 個候選中最低風險、可立即動工的一項）。修正 `Makefile:102`／`backend/pom.xml` surefire+failsafe 註解／`docs/08_deployment/LOCAL_CI_VALIDATION.md` 共 3 處誤導性文字，各自加註「為何是誤導」而非只刪字，避免下一個人重新引入同樣的誤解。**查證全庫約 80 個匹配後的假陽性排除**：歷史 Sprint 記錄（`SPRINT_*_REVIEW`／`RETRO`／`RELEASE_NOTES_*`／`FLYWAY_EVALUATION.md`）屬時間點快照依 Rule 3 不回頭改寫；`docker-compose.test.yml`／`scripts/validate-schema.sh` 的「integration-test profile」查證後是**真實存在的 Spring profile**（`SPRING_PROFILES_ACTIVE=integration-test`），非本缺陷所指的 Maven `-P` 旗標，非誤觸範圍。純註解／文件變更，`mvn -o compile` BUILD SUCCESS，未跑全量回歸（不影響任何被測程式碼行為）。詳見 [SPRINT_122_PLAN.md](SPRINT_122_PLAN.md) |
| DEF-067 | `inventory_checks` 表與 `InventoryCheck` entity 是孤兒：庫存盤點為 PRD §6.7.2 **P1** 功能，實體與資料表都在（`V50` 建），但**沒有任何生產程式碼讀或寫** | Sprint 116 | Sprint 121 | **使用者拍板選 (a)：移除孤兒骨架**（技術債清理），而非啟動完整功能設計——PRD 對此功能只有一行摘要、無 US/AC/API 規格，且骨架本身也不完整（`InventoryCheck` 只有單頭表彙總欄位，沒有逐 SKU 明細子表，接上 CRUD 也做不出「盤盈/盤虧記錄」）。比照 DEF-066（`V72`）模式：刪除 `InventoryCheck.java`（無 repository/service，無需連帶移除）+ 新增 `V75` `DROP TABLE IF EXISTS inventory_checks`。確認無殘留引用（後端/前端 grep 僅命中被刪除的 entity 自身）。`make validate-schema`／`validate-schema-doc` 皆通過；後端全量回歸見 SPRINT_121_PLAN.md。**明確不變**：PRD §14.2.5 `BV-2A-04`（ERP 庫存準確率）KPI 仍無法量測，此為既有事實不因本次移除而改變；DEF-068（SRD 涵蓋率缺口）與本次無關，仍待拍板。詳見 [SPRINT_121_PLAN.md](SPRINT_121_PLAN.md) |
| DEF-069 | 店家後台「退貨審核台」看不到商品名稱／規格，只能顯示 `品項 #{orderItemId 前8碼}` | Sprint 119 | Sprint 120 | **使用者拍板排入排程，選項 (a)**：`ProductSkuRepository` 新增 `SkuDisplayInfo` 投影（`LEFT JOIN` `product_skus`／`listings`，比照 DEF-064 教訓——SKU 或商品被刪除／下架時顯示留白而非整列消失）；`ReturnDto.ItemResponse` 新增 `skuCode`／`specName`／`productName`；`ReturnRequestService.toResponse()` 對單一退貨單的品項批次查詢一次（`Set<UUID>` 去重後單次 `IN`），避免逐品項 lazy load。前端 `/dashboard/returns/[id]` 改顯示品名，無值時退回原本截斷 ID 顯示。新增 `IT-M05-RETURN-012` 斷言 `skuCode`／`productName` 與種子資料一致。**已知取捨**：批次僅止於單一退貨單內（通常 1-3 品項），未如 DEF-064 做整頁列表層級的 JOIN 投影——這個功能流量低，多一次小查詢換簡單程式碼是划算的，若退貨量成長到需要優化，屆時再比照 `StockMovementRow` 模式重構。後端全量回歸 `mvn -o verify` 單元 1034 / 整合 454，0 失敗；checkstyle／PMD 0 violations。詳見 [SPRINT_120_PLAN.md](SPRINT_120_PLAN.md) |
| DEF-044 | 已付款訂單（`PAID`/`REFUNDED`）退款/取消時無庫存自動回補（restock-on-refund） | Sprint 88 | Sprint 119 | **Sprint 118 完成後端核心**：狀態機（`REQUESTED→APPROVED→RECEIVED`，🔴 唯一動庫存的一步）、與退款完全獨立、不可售數量記錄但不回補（同交易 `RETURN(+收到)` + `SCRAP(-不可售)` 兩筆抵銷）、`return:read/create/review` 三權限掛四角色、買家層 4 端點 + 店家層 4 端點、11 個整合測試。**Sprint 119 完成前端串接**：買家 `/returns`（列表）／`/returns/new`（申請，帶入訂單品項供勾選數量）／`/returns/[id]`（詳情＋撤回，join 買家自己的訂單補品名顯示）；店家 `/dashboard/returns`（審核列表）／`/dashboard/returns/[id]`（核准／駁回／收貨確認表單，可售/不可售數量輸入）；訂單詳情頁加「申請退貨」入口（僅 DELIVERED/COMPLETED 顯示）、Header 加「退貨申請」連結、dashboard 首頁加「退貨審核」快速入口。⚠️ **純串接的已知限制**：店家審核頁因無法呼叫買家專屬的訂單詳情端點（IDOR 防護），品項僅能顯示截斷 `orderItemId`、無商品名稱，已記錄為 **🟢 DEF-069**（低優先，待評估是否 join 補齊）。詳見 [SPRINT_118_PLAN.md](SPRINT_118_PLAN.md)、[SPRINT_119_PLAN.md](SPRINT_119_PLAN.md) |
| DEF-064 | M16 ERP 異動列表的 **SKU／品名／參考單號三欄永遠空白**，且新增表單的「參考單號」輸入送出後被靜默丟棄 | Sprint 114 | Sprint 117 | **使用者拍板選項 C——兩件事分兩欄**：「來源單據」（系統推導、唯讀，採購收貨為採購單號／訂單異動為訂單 id 前八碼）與「參考單號」（店家自填、可存）不得混為一談。`V73` 新增 `stock_movements.reference_number`；新增 `StockMovementRow` 投影（`LEFT JOIN` `product_skus`／`listings`／`purchase_orders`）一次補齊 `skuCode`／`productName`／`sourceDocument`，三個查詢方法（分頁列表／依 SKU／依時間範圍）全部改走投影；`InventoryService` 原本自己重複一份同樣缺欄位的 `toMovementDto`，改委派 `StockMovementService`，順帶移除重複映射。紅燈先行：`M16ErpStockMovementDisplayIntegrationTest` 修復前 6 failed，修復後 6 passed（含「手動異動的 `sourceDocument` 必須是 null，不得把店家自填單號誤當來源單據」的不變量）。前端列表新增「來源單據」欄、表單參考單號真的送得出去。**本表原記錄「待排程」為過時漂移**——2026-09-04 盤點 Sprint 121 候選項目時發現 Sprint 117（同日 2026-09-03 拍板即動工）已完整實作並上線，此列直到今天才回補移至已完成，之前三個 Sprint（118-120）本表一直誤報為活躍待辦。詳見 [SPRINT_117_PLAN.md](SPRINT_117_PLAN.md) |
| DEF-066 | ERP 庫存台帳讀的是一張永遠空白的表：真正的庫存數字全在 `product_inventory`，但 ERP 庫存列表／明細／低庫存預警與賣場商品卡讀的是無人寫入的 `inventory` | Sprint 115 | Sprint 116 | **挑選理由**：使用者拍板列為下一輪優先——PRD §6.7.2 列為 **P0** 的庫存台帳在生產環境上完全不能用。`V50` 檔頭自承 `inventory` 是「entity 存在但沒有建表 migration，導致 ddl-auto=validate 失敗」才補建的**空殼**。⚠️ **不只是讀錯表**：即使表裡有資料，列表每一欄仍是空的——前端讀 `quantity`／`reservedQuantity`／`availableQuantity`，後端 `InventoryLedgerDto` 卻叫 `totalQty`／`reservedQty`／`availableQty`，而 `skuCode`／`productName`／`location` 三欄從未填值。方向依 S114 的「數證據」法：後端**自己的** `InventoryDetailDto` 與前端一致，只有 ledger／alert 兩個 DTO 是例外，故往多數對齊。**為什麼一直沒被發現**：既有 M16 測試**同時種兩張表**（要種兩張才測得起來，本身就是徵兆），且 `IT-M16-201`／`204` 只斷言 `isArray()`／`success`——**空台帳照樣通過**（承 S114 `anyOf(400,500)` 的同族問題）。**紅燈先行**：新增 `M16ErpInventoryLedgerIntegrationTest`，刻意只寫 `product_inventory`；因修復必然改 DTO 欄位名而無法對 HEAD 編譯，改以「暫時把資料來源換回舊表、保留新欄位名」的混合版取得紅燈 → **5 failed + 1 error，台帳回傳 `[]`**，修復後 6 passed。**修法**：四個讀取點全改讀 `product_inventory`（投影查詢一次撈齊 SKU 編號／品名／租戶）；DTO 對齊並移除無來源欄位（`location`／`reorderPoint`／`safetyStock`）；最近進出庫時間改由 `stock_movements` 推導（S115 補齊流水帳後才有意義）；`INBOUND_TYPES`／`OUTBOUND_TYPES` 抽為 `StockMovement` 常數避免 SQL 再寫一份清單（DEF-063 就是這樣來的）；移除 `Inventory` entity／`InventoryRepository`／`inventory` 表（V72）——留一張名字像「庫存」的空表，下一個人同樣會理所當然地讀它。🔴 **差點自己再踩一次同型陷阱**：第一版讀 `available_qty`，但該欄在 Flyway 是 GENERATED、在 ddl-auto 的測試 DB 只是普通可空欄位而**永遠 NULL**，靠它會「生產正確、測試全空」；改為 SQL 直接相減。**連帶修掉**：`IT-M16-201`／`204` 的空斷言、庫存頁寫死的 `<Badge>正常</Badge>`（`getStockStatus` 定義了從未被呼叫）、預警卡假裝有兩種門檻。**新記錄 🟡 DEF-067**（`inventory_checks` 同款孤兒）。詳見 [SPRINT_116_PLAN.md](SPRINT_116_PLAN.md)。 |
| DEF-065 | 訂單流程完全不寫庫存流水帳：PRD §6.7.3 明訂下單產生 `RESERVE`、出貨產生 `OUTBOUND`、取消產生 `RELEASE`，但 `ProductInventoryService` 對 `StockMovement` 零引用 | Sprint 114 | Sprint 115 | **挑選理由**：高優先級區空，中優先級四項中**唯一不需要任何產品決策**的——PRD §6.7.3／§6.7.4 已明訂三個寫入點與各自方向（DEF-064 需設計決策、DEF-044 需業務決策、DEF-021 為 S41 已決策的外觀技術債）。**紅燈先行**：新增 `M12OrderStockLedgerIntegrationTest`（真實 PostgreSQL），對未修復程式碼 **5 failed / 2 passed**——2 個通過的是刻意留的對照組（斷言「零筆流水帳」，今天當然成立），證明失敗的是機制缺失而非環境問題。**修法**：三段操作各在「受影響筆數 > 0」時寫一筆流水帳（方向依 PRD §6.7.4：RESERVE `+reserved`／OUTBOUND `-total,-reserved`／RELEASE `-reserved`），前後數量比照 S113 回讀 DB 後由帶號變化量反推；新增 `findReservedQtyBySkuId`，讓 S113 起記為「存在但從未填值」的 `before/after_reserved_qty` 對這三型真正有值（不填的話 RESERVE／RELEASE 會是 total 前後相同、毫無資訊的列）。🔴 **踩到兩個陷阱**：①`Order.tenantId`／`userId` 是 `insertable=false` 影子欄位，建單時只設 `.tenant()`／`.user()`，剛建立的訂單 `getTenantId()` 是 **null** 而 `stock_movements.tenant_id` 是 NOT NULL——實作必須走 `order.getTenant().getId()`，測試固件也刻意只設關聯物件以比照生產物件形狀；②預扣必須移到 `orderRepository.save()` **之後**，否則 `reference_id`／`order_item_id`（皆 `@GeneratedValue`）都是 null，寫出的是查不到來源的孤兒列。移位不影響超賣防護——保證本來就來自交易回滾（原註解即寫「拋例外交易回滾」），並加 `requirePersisted()` 大聲失敗防止改回去。⚠️ **固件差點繞過本輪自己改的那一段**：前 7 個案例以 raw SQL 種訂單，完全沒跑到 `OrderService` 的呼叫順序，故補兩個走真實 `createOrderFromCart` 的案例（008 斷言 `reference_id`／`order_item_id` 非 null；009 斷言庫存不足時 orders 為空、`reserved_qty` 歸零、流水帳無殘留）。**既有測試處置**：`OrderServiceTest` 移除 `verify(orderRepository, never()).save(...)`——那驗的是機制而非不變量，真不變量改由 IT-M12-LEDGER-009 以真實 DB 驗證（覆蓋更強）；`M12InventoryConcurrencyIntegrationTest` 固件更新後 **7 案例全綠**，確認同交易多寫一筆流水帳不影響 S103 的原子 UPDATE 併發保證。詳見 [SPRINT_115_PLAN.md](SPRINT_115_PLAN.md)。 |
| DEF-063 | M16 ERP 手動庫存異動的前後端異動類型枚舉是兩組不同的值：前端 7 個選項有 5 個後端不認識（含預設選中的那個），送出必定 E_7005；另 `RETURN` 型別為靜默 no-op | Sprint 113 | Sprint 114 | **挑選理由**：高優先級區空，中優先級三項中唯一「使用者可見且必然發生」的（DEF-044 條目自身寫明需業務決策、DEF-021 為 S41 已決策的外觀技術債）。**方向由使用者拍板**：三個選項（改後端／改前端／加轉換層）攤開後選 (a) **改後端對齊 PRD**，`RETURN` 裁示為「保留但明確拒絕手動建立」。⚠️ **本表原記錄漏了一點：光改枚舉名字修不好這個缺陷**——`INBOUND`／`OUTBOUND` 後端從第一天起就禁止手動建立（PRD §6.7.3 明訂那四型由採購單與訂單流程產生），只對齊命名的話缺陷只會從 5/7 失敗變成 **2/7 失敗**。故本輪修的是兩件事：枚舉對齊 PRD ＋ 下拉只保留後端實際接受的 5 型（預設值由 `INBOUND` 改為 `ADJUST_PLUS`）。對照關係全部取自**舊枚舉自己的中文註解**而非英文字面（故 `THEFT → SCRAP`、`DAMAGE → ADJUST_MINUS`）。`V71` 另補上 `movement_type` 的 CHECK 約束——DEF-063 能存活到被使用者撞見，正是因為該欄是裸 `VARCHAR(20)`、兩邊怎麼漂都沒有東西喊。**順帶修掉一個假綠燈**：`E2E-M16-005「OUTBOUND 庫存不足時回傳錯誤」`拿到的一直是 E-7005 而非庫存不足，`anyOf(400, 500)` 的寬鬆斷言讓它一路是綠的，「庫存不足」路徑**一次都沒被驗到**（S97「固件繞過同一段邏輯」的同型問題，這次繞過的是寬鬆斷言）。**新記錄 🟡 DEF-064／DEF-065**（見上方活躍項目）。詳見 [SPRINT_114_PLAN.md](SPRINT_114_PLAN.md)。 |
| DEF-051 | M16 ERP 的 `StockMovementService.createManualMovement` / `PurchaseOrderService.createInboundMovement` 對 `product_inventory` 仍是「載入 → `addStock()`/`deductStock()` → `save()`」讀後寫 | Sprint 103 | Sprint 113 | **挑選理由**：高優先級區空，中優先級三項中唯一工程可獨立完成的（DEF-044 需業務決策、DEF-021 為 S41 已決策的外觀技術債）。⚠️ **本條目原記載「沒有資料正確性風險，只有可用性問題」——併發部分完全正確，但漏掉了一個與併發無關的正確性缺陷**：`ProductInventory.deductStock()` 是**訂單出貨**語意（同時扣 `total_qty` 與 `reserved_qty`），M16 的 `DAMAGE`／`TRANSFER_OUT`／`THEFT` 共用了它，但 PRD §6.7.4 明訂盤虧／調撥出庫／報廢**只動 `total_qty`**，只有 OUTBOUND 才扣預留量。後果是**可售量被灌水**：總量 10／預留 4（可售 6），報廢 3 件後應為 7／4（可售 3），實際變成 7／1（**可售 6**）——已被買家訂走的 3 件被放回可售池，直接通往超賣，且**單執行緒就會發生**。過去四輪讀後寫掃描都沒碰到它，因為掃描找的是讀後寫樣式（承 S103「樣式比對找對位置卻沒看那段程式碼實際在做什麼」，差別在這次不是推錯後果，而是**站對位置卻看漏旁邊那一行**）。**紅燈實測**（新增 `M16ErpInventoryConcurrencyIntegrationTest`，5 案例／真實 PostgreSQL／10 執行緒各自獨立交易）對未修復程式碼 **4 failed / 1 passed**：①盤盈 `granted=3` + 7 筆樂觀鎖；②扣減 `granted=2`、**`insufficientStock=0`** ← 本輪最有資訊量的數字，庫存 3 遇上 10 筆併發扣減，**沒有任何一筆走到「庫存不足」分支**，10 條全數通過充足性檢查（各自讀到的都還是別人尚未扣掉的數字），最後是靠樂觀鎖擋掉——那道 `beforeTotalQty < quantity` 檢查在這場競賽裡**一次都沒生效過**；③採購收貨 `granted=2` + 8 筆樂觀鎖（貨到了、庫存沒加、採購單狀態也沒推進）；④預留量 `expected: 4 but was: 1`。**刻意留的單執行緒對照組通過**，證明失敗的是機制不是環境（承 S106 教訓）。**修法**：`increaseTotalQty`／`decreaseTotalQtyIfSufficient`（條件式）兩條原生 UPDATE + `findTotalQtyBySkuId` 回讀；扣減敘述**只動 `total_qty`**；條件刻意寫 `total_qty >= :quantity` 而非可售量（報廢針對實體庫存，被預留的那幾件同樣可能破損，維持既有語意）；仍帶 `version = version + 1`（ERP 改原生 UPDATE 後 S103 那條覆蓋路徑已不存在，但版號欄位存在就必須維護）。流水帳前後數量改為**回讀 DB 再由 after 反推 before**（原子 UPDATE 後實體快照已過期），回讀與 UPDATE 同交易、行鎖未釋放故必為本次結果——修復後可斷言 10 筆異動的 `after_total_qty` 恰好走完 1..10 不重複不跳號。`addStock()`／`deductStock()` 成零呼叫死碼後**移除**（`deductStock()` 另有非移除不可的理由：它就是上述缺陷的來源），至此 `product_inventory` 所有數量寫入都在 Repository 的原生 UPDATE 中。**既有單元測試改寫而非新增**：原本斷言 mock 之上的記憶體算術，缺陷存在時照樣全綠（承 S97 教訓），改為斷言派送行為並新增守衛 `never()).save(...)` + `never()).deductReserved(...)`（後者防止把剛修掉的缺陷換個寫法搬回來）。**新記錄 DEF-063**（ERP 手動異動 UI 5/7 選項後端不認識）。詳見 [SPRINT_113_PLAN.md](SPRINT_113_PLAN.md) |
| DEF-043 | ROOM 訂房結帳成功後呼叫 `DELETE /v2/cart` 清空整車，誤刪未結帳項目 | Sprint 88 | Sprint 112 | **挑選理由**：高優先級區已空，中優先級 4 項中唯一「產品決策已做過、只是沒套用到這一側」的項目（DEF-044 條目自身寫明需業務決策、DEF-051 需併發條件且記為低風險、DEF-021 為外觀技術債）。⚠️ **實際缺陷比本條目原記載更廣**：原記「PRODUCT 項目會被誤刪」，讀 `checkout/page.tsx` 後發現 L124 只為 `roomItems[0]` 建立預訂（註解自承 `assuming one at a time for now`）、L137 卻清空整車，**第二個以後的 ROOM 項目同樣被靜默刪除**——使用者看到「預訂成功」，實際只訂到一間、另一間連購物車紀錄都沒了。**修法**：改用 `API_ENDPOINTS.cart.remove(roomItem.cartItemKey)`，並在本地 `BookingItem` 介面補宣告 `cartItemKey`（後端 `CartDto.CartItemResponse` 本就回傳）。**刻意修前端而非把清車搬進後端**：`BookingService.createBooking` 是通用訂房 API（`BookingController` 兩處呼叫，可不經購物車直接訂房），耦合購物車會讓它變成購物車感知的；前端修法 2 行且與 `cart/page.tsx:133` 既有慣例一致。**紅燈先行**：新增 `E2E-ROOM-12`（沿用該 spec 既有 `page.route` mock，不需後端 seed），對未修復程式碼如期失敗（`/v2/cart/items/k-room` 從未被呼叫）；修復後 `at-room-booking.spec.ts` **12 passed**。斷言的是**機制不是畫面**——此缺陷在畫面上完全看不出來（照樣顯示「預訂成功」）。**同型掃描**：修復後 `cart.clear` 在前端零呼叫點，PRODUCT 側本就正確，無其他實例。**範圍外**：「只訂 roomItems[0]」的多房功能缺口未動（修完後第二間房會留在購物車，從資料遺失變成可恢復）。詳見 [SPRINT_112_PLAN.md](SPRINT_112_PLAN.md) |
| DEF-062 | `SRD_Database_Schema.md` 與 PRD §8.2 的資料表定義與 Flyway 實際 schema 全面不符 | Sprint 110 | Sprint 111 | ⚠️ **原記錄嚴重低估範圍**：S110 記為「`tenants` 有 8 處落差、3 SP，可能不只一張表、未查證」，實際量測後是 **兩份文件、29 張表、254 個欄位級錯誤**——SRD **16/16 張全漂移**（117 幽靈欄位、55 缺載、`user_profiles`／`pricing_overrides` **兩張表從未實作**、`product_inventory` **連主鍵都寫錯**），PRD §8.2 **12/13 張漂移**（21 幽靈、61 缺載、12 處改名）。**方法**：起乾淨 `postgres:18-alpine` 套完 70 個 Flyway 遷移（0 失敗、65 張表）作為權威基準，逐表比對。**使用者拍板「重寫 + 原稿存 archive + 自動守門」**。SRD → v2.0 全文重寫（§2 的 14 張表 DDL 全部自 DB 匯出、§1 ERD 重繪、§3.1 補上遺漏的 `CONFIRMED`、§3.3 租戶狀態機原本是**第三個版本**、§5/§6 改為實測清單），原稿封存於 `docs/02_architecture/archive/SRD_Database_Schema_designdraft.md`；PRD → v1.0.2（勘誤 ER-005）。🔴 **本輪最有價值的發現不是欄位錯**：原 §4 宣稱「所有查詢自動附加 tenant_id」「Hibernate 會自動轉換」，查證後 **`@FilterDef`/`@Filter` 全 codebase 零命中、`TenantAwareEntity` 不存在**——實際靠每個方法自己記得過濾（約 226 處明確呼叫）。**追蹤表上 DEF-023/024/037/040/041/057 那一整串****「某某方法沒有租戶過濾」的 IDOR，正是這個假陳述會導致的結果**；§4 已整節重寫。**止血**：新增 `make validate-schema-doc`／`sync-schema-doc`（`scripts/validate-schema-doc.sh` + `scripts/lib/`），接進 pre-commit（只在動到遷移／SRD／PRD 時觸發）。與既有 `validate-schema` 分工：後者管 entity↔migration，新的管 migration↔文件。**紅燈四組對照**（竄改 SRD／新增遷移不同步／重新宣告未實作表／PRD 刪欄位）全部如期攔截，復原後回綠。詳見 [SPRINT_111_PLAN.md](SPRINT_111_PLAN.md) |
| AI-2444 | PRD §4.3（第 340 行）租戶生命週期與實作分歧：規格描述「申請時即建立 `Tenant`（`PENDING_REVIEW`），審核只改狀態」，實作是「申請只寫 `tenant_applications`，`Tenant` 於核准當下才建立且建立即 `ACTIVE`」 | Sprint 109（修完斷鏈後明確記為未處理） | Sprint 110 | **使用者於 S108 已拍板以實作為準**，故本輪方向是改文件而非改實作。同步 8 份文件、零程式碼變更：PRD → v1.0.1（§4.3 改寫為兩表兩階段、§6.8 初始化觸發點、§7.4.1 補齊 5 項資料表變更、**新增 §8.2.1-A `tenant_applications`**（PRD 原本完全沒有這張表）、§9.10.2 審核入口更正為 `/admin/tenant-applications/*` 且角色更正為 `SUPER_ADMIN`、新增勘誤 ER-004）；FRD → v1.2（§8.3 補資料模型、BR-M17-001／002、US-M17-001/007/008 的 AC 與錯誤碼 `E-2006`/`E-2007`/`E-2008`/`E-4092`、§8.6）；`API_Index.md` 與 `API_M17_Tenant.md` 新增 `API-M17-APP-001~003` 並把 `API-M17-008/009` 標為生產不可達的舊流程（**保留編號不刪**，Sprint 03／03-A 的歷史測試案例仍引用）；兩份 SRD 採**加註不改寫**（設計稿是決策的歷史紀錄，改掉等於抹除「當初想的和後來做的不一樣」這個資訊）。⚠️ **值得記的一筆**：FRD v1.1（2026-04-22）曾把「租戶狀態 PENDING_REVIEW → PENDING」列為修正項——**改對了名稱卻改錯了實體**，申請狀態根本不在 `tenants` 上；一次無效的維護讓分歧看起來已被處理，反而多存活 4 個月。反向掃描另抓到三處「文件寫的端點不存在」（`API-M17-002` 的 `/tenants` 實為 `/tenants/my`、FRD §8.6 的 `/dashboard/tenant/profile` 完全不存在），已一併更正。新記錄 **DEF-062**（SRD schema 全表漂移）。詳見 [SPRINT_110_PLAN.md](SPRINT_110_PLAN.md) |
| DEF-061 | `TenantController`／`ErpController` 的 class-level `@RequestMapping` 自帶 `/api`，疊上 `server.servlet.context-path: /api` 後端點實際落在 `/api/api/v2/**`——**M17 開店申請與 M16 ERP 的前端呼叫全部打不到後端** | Sprint 109（當輪發現當輪修復，修 DEF-060 時追出的上游根因） | Sprint 109 | **實測而非推理**（啟動真實 JAR + curl）：修復前 `POST /api/v2/tenants/apply` → **401**（與 `/api/definitely/not/a/route` 同一個回應）、`POST /api/api/v2/tenants/apply` → **201**；修復後完全反轉。對照組是關鍵——401 是 Spring Security 對**未知路徑**的回應，單看 401 不能證明端點不存在；因 `apply` 屬 permitAll，它若存在就該回 400/201，兩相對照才成立。**為何長期未被發現**：`SecurityConfig` 的兩條 permitAll 規則也用加倍路徑，後端內部自洽；而**所有既有測試都用 MockMvc，MockMvc 不套用 context-path**，於是 5 個測試類別沿用 `"/api/v2"` 一路全綠——測試與生產走的不是同一條路。M17 E2E 長期 skip 即此根因的下游症狀。**修法**：2 支 controller mapping + 2 條 security 規則 + 5 個測試 base URL 常數。**防回歸**：新增 `ControllerRequestMappingConventionTest`（掃描 class-level mapping，禁止以 `/api` 開頭），並**實測驗證守衛會紅燈**（暫時還原缺陷 → `Expecting empty but was: ["TenantController -> "/api/v2""]`），另加掃描數下界避免「掃不到而空過」。詳見 [SPRINT_109_PLAN.md](SPRINT_109_PLAN.md) §2 |
| DEF-060 | Admin 開店審核台前端接在失效流程上：網友送出的開店申請 Admin 在 UI 上永遠看不到、也無法核准 | Sprint 108 | Sprint 109 | **使用者於 S108 拍板走 (a)**（前端改接 `tenant-applications`，不推翻 S97 設計）。`/admin/tenants`「審核中」分頁改查 `GET /v2/admin/tenant-applications`，並提供**行內核准／駁回**；刻意不新增後端詳情端點也不另開審核頁——摘要 DTO 已含店名／描述／經營類型／聯絡方式／送出時間，資訊比原本的租戶審核頁更完整（Rule 2）。`/admin/tenants/[id]/review` 移除永遠會拋 `E_2005` 的核准／駁回鈕，收斂為唯讀詳情頁並留註解防止被加回。**兩個刻意選擇**：訊息改用畫面內元素而非原生 `alert()`（alert 會讓 Playwright teardown 崩潰，且畫面內訊息才可斷言）；關鍵元素加 `data-testid`（S107 教訓：`:has-text("核准")` 是子字串比對，會命中「已核准 (N)」tab）。詳見 [SPRINT_109_PLAN.md](SPRINT_109_PLAN.md) §3 |
| DEF-058 | `at-m17-002` E2E 的前提是一次性固件，被測試自己消耗掉 | Sprint 107 | Sprint 109 | DEF-061 + DEF-060 修好後，前提終於能由測試自建——**買家送申請走的就是前端走的那條路**。改寫為：註冊新買家 → 以其 token 送申請 → admin 登入 → 切「審核中」→ 依**本次專屬店名**定位自己那張卡（不用 `.first()`，避免併行干擾）→ 核准／駁回 → 斷言 API 200 + 訊息含店名 + 卡片消失。**已移除全部 `test.skip()`**：三態擺盪（S105 記 54/6、S106 記 53/7、S107 首跑 1 failed）到此結束，每次執行都真的在測。詳見 [SPRINT_109_PLAN.md](SPRINT_109_PLAN.md) §4 |
| DEF-059 | `getPlatformStats().pendingTenantReviews`（Admin Dashboard 待審核店鋪數）**恆為 0** | Sprint 108（當輪發現當輪修復） | Sprint 108 | **自相矛盾為判定依據**（沿用 S107 判準）：`AdminService` **同一個類別**裡，`getPendingTenantApplications()` 回傳 N 筆待審核申請，`getPlatformStats()` 卻回報「待審核 0」。根因是後者計數 `Tenant.status=PENDING_REVIEW`，而**生產環境沒有任何路徑會產生該狀態**——已逐一查證 9 個 `tenantRepository.save` 呼叫點，唯一的建立點 `approveTenantApplication`（S97）寫死 `status=ACTIVE`，其餘 8 個皆為更新。此欄位**從未被任何測試斷言過**，故長期未被發現。**修法**：改以 `tenantApplicationRepository.countByStatus(PENDING)` 計數。**紅燈**：差分斷言（送申請前後計數應 +1）→ `expected: 1 but was: 0`。刻意不用 `>= 1`，因測試 DB 經 Flyway V7 播有 `PENDING_REVIEW` 租戶固件，會讓測試假綠。另補一個辨別方向的單元測試：mock 令 tenants 側存在 1 筆 `PENDING_REVIEW`、applications 側 3 筆，斷言結果為 3——有人改回數 Tenant 即失敗。詳見 [SPRINT_108_PLAN.md](SPRINT_108_PLAN.md) |
| DEF-057 | `KnowledgeBaseService.incrementViewCount` 是本類別唯一沒有租戶範圍的方法，他租戶可遞增其瀏覽數而操縱熱門排名 | Sprint 106（修復 DEF-055 改寫該方法時發現） | Sprint 107 | **判定為疏漏而非設計的關鍵證據是「自相矛盾」而非「不一致」**：同一個類別對他租戶的同一篇文章給出三種反應——列表看不到、詳情 `findByIdAndTenantId` **404**、但瀏覽數端點 `findById` **回 200 且真的 +1**。**一篇你被禁止閱讀的文章，你可以幫它衝瀏覽數**，而 `viewCount` 正是 `KnowledgeArticleRepository.findPopularByCategoryId`（`ORDER BY a.viewCount DESC`）的排序欄位。**加租戶條件不會弄壞任何走得到的流程**（已查證：知識庫無任何公開端點、每個方法都要 `knowledge:*` 權限；前端只有後台管理頁一個入口；列表已隔離故正常使用者點不到別家文章，要走這條路只能手刻 API 呼叫）。姊妹服務 `FaqService.incrementViewCount` 本來就有租戶範圍，兩支同型方法只有一支漏掉。**語意由使用者於 Sprint 107 拍板**：收斂為與同類別其餘端點一致。**修法**：租戶條件下沉到 UPDATE 的 WHERE 子句（`WHERE id = :articleId AND tenant_id = :tenantId`），沿用 S106 為 FAQ 建立的同一形狀，0 筆即拋 `E_4000`。**紅燈**：他租戶遞增本應被拒 → `Expecting code to raise a throwable`（沒被拒）；同時加一個「本租戶正常 +1」的守衛型案例，修復前本就通過，如實記錄不計入紅燈。詳見 [SPRINT_107_PLAN.md](SPRINT_107_PLAN.md) §2 |
| DEF-037 | `ShippingTemplateService.calculateFee` 未做租戶過濾，可查詢他租戶運費模板設定 | Sprint 76（探查 `LogisticsService`/`ShippingTemplateService` 範圍時發現） | Sprint 107（**結案為刻意設計，不修改行為**） | **使用者於 Sprint 107 拍板：維持開放。** 與同輪的 DEF-057 表面同型（同類別其他方法都隔離、只有這支沒有），但**結論相反**——它有說得通的正當用途：**買家跨店比價／試算運費**，只憑 `templateId` 即可取得運費計算結果；回傳內容僅 `feeType`／`orderAmount`／`shippingFee` 等計算參數，**不含 PII、無任何寫入或竄改路徑**。**本輪的交付物是 javadoc 而非行為變更**，這正是重點：此項目自 Sprint 76 起被記錄，此後每一輪租戶範圍橫向掃描都會把它撿起來重新評估一次，因為決策只存在追蹤表裡。已在 `calculateFee` 上明確寫出「刻意不做租戶過濾」「**請勿順手加上租戶過濾**」與改變此設計的前提（買家比價情境不再需要），並交叉引用 DEF-057 說明兩者為何結論相反。承 S106 §8 的教訓：**記錄下來 ≠ 送達使用現場**，決策要放到下一個人一定會讀到的地方。詳見 [SPRINT_107_PLAN.md](SPRINT_107_PLAN.md) §3、§4 |
| DEF-055 | `incrementViewCount` ×3（`PostService` / `FaqService` / `KnowledgeBaseService`）為讀後寫，瀏覽數系統性少計 | Sprint 105（讀後寫模式全專案系統性掃描時發現） | Sprint 106 | ⚠️ **紅燈實測推翻了本表原先的定性**。本記錄稱三處「實作完全同型」、後果是「靜默丟失更新／系統性少計」——對 FAQ 與知識庫成立（10 執行緒併發瀏覽 `expected: 10 but was: 1`，**只存活 1 次**，與 DEF-053 完全相同的 90% 漏失比例，且零例外零日誌），但對 `PostService` **低估了**：那一路根本不是競態。`getPublishedPostBySlug` 標成 `@Transactional(readOnly = true)`，Hibernate 在唯讀交易下是 `FlushMode.MANUAL`、提交時不做自動 flush，而 `save()` 對受管實體只是 `merge()` 不會自己發 SQL——**遞增只存在於記憶體，交易結束就消失**。刻意設計的**單執行緒**案例證實了這點：瀏覽一次仍 `expected: 1 but was: 0`，**每一次瀏覽都完全沒計**。從 API 完全看不出異常（回應數字是對的，因為 DTO 由記憶體物件建出），只有重新整理頁面或直接查 DB 才看得到。**修法**：三支原生 `@Modifying` UPDATE（`view_count = COALESCE(view_count, 0) + 1`）；`PostService` 一併移除 `readOnly = true`；FAQ 的租戶條件下沉到 WHERE 子句，0 筆即拋 `E_4000`（錯誤語意不變，且少一次 SELECT）。三個實體的 `incrementViewCount()` **已移除**（成為零呼叫死碼，留著等於留一個無徵兆退回讀後寫的入口——與 S101/S102/S103 同一個「大聲失敗」理由）。`@Modifying` 刻意不加 `clearAutomatically`（呼叫端仍要用同一個 `Post` 實體組回應）。**回應語意維持不變**：`viewCount` 仍包含本次瀏覽，但改為在 DTO 上補值而非碰實體 setter（碰了會讓髒檢查整列寫回而覆蓋原子 UPDATE）。**瀏覽不再更動 `updated_at`**（原生 UPDATE 不觸發 `@PreUpdate`）——刻意為之，已查證無任何排序或業務邏輯依賴該欄位。**既有測試為何沒抓到**：`FaqServiceTest` / `KnowledgeBaseServiceTest` 的對應案例都 mock 掉 repository 並斷言「記憶體物件上的數字 +1 並 save()」，正是缺陷存在時照樣全綠的斷言，本輪已改為斷言原子委派本身。新增 `ViewCountConcurrencyIntegrationTest`（紅燈 4/4 → 綠燈 4/4）。新增 🟡 DEF-057（知識庫側無租戶範圍）。詳見 [SPRINT_106_PLAN.md](SPRINT_106_PLAN.md) |
| DEF-053 | 結算單退款扣除為讀後寫，併發退款**靜默漏計金額** | Sprint 105（讀後寫模式全專案系統性掃描時發現） | Sprint 105 | `SettlementAdjustmentService.applyDirectDeduction` 對 `total_refunds` / `net_settlement_amount` 做「讀出 → 記憶體加減 → `save()`」，由 `PaymentStateService:223` 於退款成功後呼叫。同一結算期間內**不同訂單**的退款會找到**同一列**結算單，形成讀後寫窗口。**與 DEF-050（庫存）的關鍵差異**：`ProductInventory` 是全專案 59 個實體中僅有的 2 個帶 `@Version` 者之一，所以那裡的失效是「大量樂觀鎖例外」——會拋、看得見；`SettlementStatement` **沒有** `@Version`，失效模式是**靜默丟失更新**。**紅燈實測比預期嚴重得多**：10 條執行緒對同一張 PENDING 結算單各送一筆 10.00 退款，`expected: 100.00 but was: 10.00`——**10 筆只存活 1 筆**（全部讀到 0、全部寫回 10.00），且**全程零例外零日誌**，賣家因此拿到本應扣除的 90% 退款金額。同測試的 APPROVED 路徑（產生調整單）修復前即通過，證實純 INSERT 路徑安全，也證明測試有鑑別力。**修法**：`SettlementStatementRepository.applyRefundDeduction` 單一原子 UPDATE，兩個金額欄位相對增減；本表無 `version` 欄位，故**不需**比照 DEF-050 推進版號（該規則是條件性的）。服務層刻意不再呼叫任何 setter——實體仍在持久化上下文中，碰了 setter 會讓 Hibernate 髒檢查在提交時整列寫回、覆蓋原生 UPDATE，等於白修。**既有測試為何沒抓到**：`SettlementAdjustmentServiceTest` 6 個案例全部 mock 掉 repository，其中兩個斷言的是「記憶體物件上的數字對不對」——**正是在缺陷存在時照樣全綠的斷言**，本輪已改為斷言原子呼叫本身。詳見 [SPRINT_105_PLAN.md](SPRINT_105_PLAN.md) §3 |
| DEF-054 | 評價「有幫助」投票無去重（可被任意登入者灌高排名）＋ 併發投票互相覆蓋 | Sprint 105（同上掃描） | Sprint 105 | **兩個各自獨立的缺陷**。(1) **無去重**：`votes.put(userId, currentVotes + 1)` 讓同一人可無限次遞增，欄位註解自己就寫 `// userId -> vote count`，端點為 `@PreAuthorize("isAuthenticated()")`。**判定為缺陷而非「規格未定義」的關鍵證據是前端**：`ReviewList.tsx:101` 顯示「{helpfulCount} **人**覺得有幫助」（人數語意），而 `helpfulCount` 又是 `ReviewSearchCriteria.HELPFUL_COUNT` 的排序欄位——UI 宣稱的「N 人」可被單一使用者灌到任意大並改變排名。規格 `IT-M08-203` 只寫「POST 一次 → +1」，未涵蓋重複投票；**每人一票（冪等）的語意由使用者拍板**。補充：前端目前**沒有投票按鈕**，只顯示數字，故屬 API 層操縱向量而非一般使用路徑。(2) **讀後寫競態**：整份 JSON map 讀出改完寫回，`Review` 無 `@Version`。**紅燈實測**：同一人連投 5 次 `expected: 1 but was: 5`；10 位相異使用者併發 `expected: 10 but was: 2`（**8 票被靜默覆蓋**）。**修法**：`ReviewRepository.registerHelpfulVote` 以單一 JSONB 敘述同時解決兩者——`||` 合併對同一 key 覆寫成 1（**天然冪等**），`helpful_count` 取合併後 key 數（＝相異投票人數，與前端一致），單一敘述由 DB 序列化。**踩到的坑**：`'{}'::jsonb` 在帶具名參數的原生查詢裡會被 Hibernate 把 `::` 首個冒號當參數前綴吃掉而報語法錯誤，必須用 `CAST('{}' AS jsonb)`（已寫入 javadoc）。詳見 [SPRINT_105_PLAN.md](SPRINT_105_PLAN.md) §4 |
| DEF-049 | 整合測試套件執行時間持續成長，已撞穿雲端 CI 的 job 逾時預算 | Sprint 101（雲端 CI run 33466882327 於 25m19s 被 cancel 時發現） | Sprint 104 | 連續三輪（S101/S102/S103）列為候選未動。Sprint 104 動它，但**先查證前提，結果推翻了本記錄的兩個關鍵敘述**。**(1) `reuseForks=false` 的理由從未被查證過**：它由 commit `d444b2d`（2026-06-15）引入，該 commit 標題是「fix(DB): V30 migration 移除不存在的 tenant_id 索引」，與測試 fork 毫無關係，是夾帶的順手改動，時間點正落在 CLAUDE.md 記載「20+ 次盲目修復」的期間。**(2) 那句註解指錯了對象**：稽核 138 個測試類別後，`SecurityContext` 是唯一**沒有**缺口的（全部都有對應 `clearContext()`）；真正會洩漏的是 `WithErpSecurity` 的 factory 順手寫入卻無人清理的 `TenantContext`（Spring Security 的 listener 只認得 SecurityContext）。**(3) 實測：顧慮不成立**——`reuseForks=true` 且完全不裝任何清理機制，404 個整合測試**零失敗**。**(4) 本記錄稱 `reuseForks` 是「單一設定變更，影響最大」也不準確**：單獨翻它會撞上 27 種 context 設定 × HikariCP 預設池 10 = 270 條連線（測試 postgres `max_connections=100`）與 2 GB 堆的物理限制，必須配套 `runOrder=alphabetical`（filesystem 順序是分散的，且在 macOS 與 Linux 上不同）與 `spring.test.context.cache.maxSize=3`。**(5) 本記錄從未提及最大的一支槓桿**：CI 整合 job 跑的是 `mvn verify`，而 `verify` 含 `test` 階段——逐 plugin 取時間戳實測，22m36s 裡有 **4m25s 在重跑 Backend Unit Tests job 已經跑完的同一批單元測試**，且零額外覆蓋（87 個單元類別中唯一帶 Spring context 的自帶 `@ActiveProfiles`）。**修法**：surefire + failsafe 皆改 `reuseForks=true` + `runOrder=alphabetical`；新增全域 `ThreadLocalIsolationExtension`（護欄，非修 bug）+ 一個會因護欄失效而失敗的註冊驗證測試；新增 `skipUnitTests` property（預設 false，只讓 CI 整合 job 用）。**本機實測 `mvn -o verify` 32:54 → 7:06（−78%）**，測試數完全不變。**主要效益來源與預測不同**：模擬預測「省 18 次 context 啟動 ≈ 561 秒」，實際省 1298 秒——共用 JVM 後第一個 context 仍要 33 秒，後續每個只要 5～8 秒（類別載入／JIT 暖機只付一次），真正的成本結構是「冷 JVM 裡的啟動特別貴」而非「啟動次數」。**連帶更正**：`reuseForks=false` 會重複計入 `@Nested` 測試，S100~S103 記錄的單元測試數偏高約 60（正確量級 1014，以原始碼 1003 個 `@Test` + 4 個參數化展開 14 次核對）；S102 記下的「差 1」不是差 1，是計數方法結構性不可靠。**雲端效益尚未實測，待下一輪回填**。詳見 [SPRINT_104_PLAN.md](SPRINT_104_PLAN.md) |
| DEF-046 | 優惠券額度檢查與遞增為非原子操作（讀後寫），高併發下限量券可超發 | Sprint 100（修復總量上限形同虛設時一併識別出的殘留風險） | Sprint 102 | 原記錄列為「🟢 低優先級 / 理論上可超發」，Sprint 102 以真實 PostgreSQL + 10 條執行緒實測後確認**不是理論風險**：舊實作下上限 3 的券被 10 條執行緒全數領走（超發 233%），不限量券的 10 次遞增在 DB 只累計為 1（9 次寫入互相覆蓋）。修法採 **DB 條件式 UPDATE**（`UPDATE ... WHERE max_usage_count IS NULL OR current_usage_count < max_usage_count`）而非原記錄建議的 Redis Lua：後者會讓額度出現兩個真相來源（Redis 計數 vs `promo_codes.current_usage_count`），退還／對帳／Redis 重啟都得再補協調機制。該敘述取得的行鎖同時把每人限用的重查序列化，故一併修掉缺口 3（`max_usage_per_user` 讀後寫）與缺口 4（`refundPromoUsage` 退還時的 lost update）。原記錄的前置需求「確認業務上可接受的超發容忍度」**不需要業務拍板**——本修法的容忍度是零且無效能取捨，沒有可讓使用者權衡的選項。詳見 [SPRINT_102_PLAN.md](SPRINT_102_PLAN.md) |
| DEF-045 | `FREE_SHIPPING` 折扣型別在全系統無任何對應處理（選此型別的優惠券，買家拿不到任何優惠） | Sprint 100（掃描順帶發現，性質與該輪「斷鏈」不同類，未擴大範圍） | Sprint 101 | 計算規則 PRD 全文未定義，經 AskUserQuestion 由使用者裁定「**全額折抵運費**，`maxDiscountAmount` 若有設仍為上限」。`PromoService.computeDiscount` 改為 3 參數簽章 `(promo, itemsTotal, shippingFee)` 並**刻意不保留 2 參數多載**——留下多載等於留下「呼叫端無徵兆拿到 0 折扣」的同一個陷阱，移除後編譯器一次列出全部呼叫點。**連帶修復兩個相鄰缺口**：(1) `getCartWithPromo` 在 main 程式碼是零呼叫死碼，`GET /v2/cart` 走的是不含 promo 的 `getCart`，買家套券後重新整理折扣即消失（第四次由「Service 方法零呼叫者」訊號命中缺口）；(2) 購物車完全不顯示運費，改為一律回填 `shippingFee` 且 `finalAmount` 含運費。與滿額免運的疊加不需另做排除（運費本為 0 時券自然算出 0 折扣）。新增 12 個測試（單元 11 + 真實 DB 整合 1），並實際執行紅燈驗證確認 3 個行為型斷言在舊實作下失敗。詳見 [SPRINT_101_PLAN.md](./SPRINT_101_PLAN.md) |
| AI-2426 | M18 客服工單子系統——店家 Dashboard 工單管理頁 + 平台 Admin 工單列表/指派頁前端 | Sprint 91 明確排定範圍外 | Sprint 92 | 收尾時發現並補上探查缺口：PRD 規格表未列「店家/平台取得單筆工單詳情」端點，但沒有它前端無法顯示對話記錄；新增 `GET /v2/dashboard/support/tickets/{id}`、`GET /v2/admin/support/tickets/{id}`（重用 Sprint 91 既有但未接線的 `SupportTicketService.getTenantTicket`，不需新 Service 方法）。前端新增店家/平台各「列表+詳情」兩頁（狀態更新/回覆/指派）。平台角色回覆重用店家端點（後端 `isSuperAdmin` 分支已處理跨租戶）。951 個後端單元測試（excludedGroups=slow）全過 |
| AI-2425 | PRD §6.10 M18「客服系統」Phase 2-B——客服工單子系統，後端三層 API（買家/店家/平台）+ 買家前端 | Sprint 83-90 retro 連續 8 次列為候選但未排入，Sprint 90 retro 明確排定 Sprint 91 起處理 | Sprint 91 | 探查排除歷史陷阱：`SPRINT_13_TASKS.md` 的「M18 Phase 2」任務名稱雖與本次工作相似，但實際是知識庫版本控制/排程發布，非本次的客服系統，屬同名不同功能。截至開工前完全 greenfield（後端零程式碼、前端零資產）。新增資料模型 `support_tickets`/`support_messages`（Migration V68，`tenant_id` nullable 表示平台工單）；新增 4 個 RBAC 權限（`support_ticket:read/create/update/manage:all`）依 PRD 矩陣指派各角色；三層 REST API 共 9 端點（`/support/tickets`、`/dashboard/support/tickets`、`/admin/support/tickets`）。**規劃修正**：實作前重新驗證「比照既有事件模式串接通知」的計畫假設，逐一確認 `ORDER_CONFIRMED`/`PAYMENT_SUCCESS`/`BOOKING_CONFIRMED` 等既有 `NotificationType` 從未被任何業務服務實際呼叫觸發（此程式碼庫無「業務事件自動觸發通知」的可複製前例），判斷此為超出 PRD §5.2 明確要求範圍的獨立架構決策，主動取消原通知整合計畫，僅完成 PRD 明確要求的「訊息記錄」本身。買家前端：提交工單、我的工單列表、工單詳情含訊息串（比照既有 `(auth)/addresses` inline 表單慣例），並補上 `StorefrontHeader` 導覽連結。新增 16 個單元測試 + 全量整合回歸（935 單元 + 367 整合）皆通過，checkstyle/PMD 皆過 |
| AI-2424 | PRD §6.2.1 M07「跨週期退款調整單機制」剩餘前端——結算逆轉發起/確認表單 | Sprint 88 retro 排定「三者最佳化順序」第二項後半 | Sprint 90 | 探查發現後端 Sprint 86 完成的 `initiate`/`confirm` 端點雖已就位，但沒有任何列表端點可供 SUPER_ADMIN/CFO 跨租戶查詢待操作（PAID/REVERSAL_PENDING）結算單——若不補上表單形同無法使用。新增 `GET /v2/admin/settlements/reversal-candidates`（`SettlementStatementRepository` 2 個新查詢方法 + `SettlementReversalService.getReversalCandidateStatements` + `SettlementController` 新端點），刻意不比照 `getPendingReviewStatements` 的「非 SUPER_ADMIN 限自己租戶」分支——因為 `initiate`/`confirm` 對 SUPER_ADMIN/CFO 本就一律跨租戶操作（`@PreAuthorize` 已鎖死入口），列表理應對等。前端新增 `/admin/settlements/reversal` 單頁（卡片內建發起/確認動作，無獨立詳情頁，因後端無單筆詳情端點）。全量整合回歸（367 整合 + 935 單元）通過 |
| AI-2423 | PRD §6.7.2 M16「採購審批金額上限機制」剩餘前端——門檻設定表單 + SuperAdmin 審批清單頁 | Sprint 88 retro 排定「三者最佳化順序」第二項前半 | Sprint 89 | 探查發現 `GET /v2/tenants/{id}`（`TenantDetailsResponse`）從未回傳 Sprint 85 新增的 `purchaseOrderApprovalThreshold` 欄位——StoreOwner 進編輯表單時無法看到目前已設定的門檻值，只能盲寫覆蓋；補上此既有欄位（非新設計）。同時發現並修正前端 `POStatus` 型別與後端 enum 長期不同步的缺陷（`PARTIAL_RECEIVED` 拼字錯誤、缺 `PENDING_APPROVAL`/`APPROVED`/`REJECTED`）。新增 SuperAdmin 審批清單頁 `/admin/purchase-orders`（單頁卡片內建核准/駁回，因後端無單筆詳情端點）；`TenantEditForm` 新增門檻欄位。探查 `/admin/*` 既有頁面慣例後，撤回原計畫的 client-side 角色守衛，改依實際慣例（純依賴後端 403）。933 個後端測試通過 |
| AI-2422 | PRODUCT 商品訂單結帳流程前端串接——`services/order.ts` 新增 `createOrder`、新頁面 `/checkout/product`、購物車「前往結帳」依內容分流 | Sprint 87 retro 提高優先度的候選項目，本輪「三者最佳化順序」使用者裁示第一項 | Sprint 88 | 探查中發現並經使用者裁示併入本 Sprint 的兩項關聯缺口：(1) **混合購物車誤處理**——`OrderService.createOrderFromCart`（PRODUCT 分支）先前對 `cart.getItems()` 完全不過濾類型，會把購物車內 ROOM 項目誤併入 PRODUCT 訂單，且建單後 `clearCart()` 清空整個購物車；因前端從未呼叫此路徑而處於休眠狀態，本 Sprint 補上前端串接後即成真實可觸發路徑，故一併修復：改為僅取用 `listingType==PRODUCT` 項目建單，建單後僅 `removeItem` 已處理項目（保留 ROOM 項目供另外結帳）；(2) **庫存從未扣減（超賣風險）**——探查發現 `ProductInventory` entity（`totalQty`/`reservedQty`/`hasAvailableStock`/`reserve`/`release`/`deductStock`，含 `@Version` 樂觀鎖）設計完整但僅 M16 ERP 模組使用，訂單/付款流程從未呼叫；已詢問使用者是否併入本 Sprint，使用者選擇「並入本 Sprint：訂單建立時檢查+預扣庫存（推薦）」。新增 `core/product/ProductInventoryService`（`reserveForOrder`/`releaseForOrder`/`deductForOrder`），採 reserve-at-creation（`createOrderFromCart` 建單前檢查+預扣，不足拋既有但從未使用的 `E_3004`）/deduct-at-payment（`PaymentStateService.mockPaymentSuccess`/`markStripePaymentSucceeded` 轉 PAID 後扣帳，try-catch 不中斷付款主流程）/release-on-cancel-before-payment（`cancelOrder` 僅當取消前為 `CREATED` 才釋放，已付款不釋放）三段式；SKU 若無庫存資料列視為未啟用追蹤、不限量略過檢查（向下相容既有 SKU）。探查修正：初步 Explore agent 報告誤判 Stripe 回跳頁面 `/orders/{id}/payment/success`、`.../cancel` 不存在，實際確認後發現兩頁皆已存在且實作完整（沿用既有能力，未重做）。新增測試：`ProductInventoryServiceTest`（新檔 6 tests）、`OrderServiceTest`（+5，混合購物車/庫存不足/取消釋放-不釋放）、`PaymentStateServiceTest`/`PaymentStateServiceStripeTest`（+3，扣帳呼叫/扣帳失敗容錯/未轉 PAID 不重複扣帳）。全量回歸 `mvn verify -Pintegration-test` 0 fail（詳見 commit 訊息數字），`make validate-schema` 無漂移，前端 `npm run build`/`eslint` 通過 + `curl` 確認 `/checkout/product`、`/cart` 無伺服器崩潰（受限於工具集無瀏覽器自動化能力，未做人工互動式瀏覽器操作驗證）。範圍外並記錄於 DEF-043（ROOM 側同類 clearCart 缺陷）、DEF-044（退款回補庫存） |
| AI-2421 | PRD §14.3.1 Phase 2-B「收貨地址管理」——買家可維護多筆常用收貨地址簿（新增/編輯/刪除/設預設），下單時可選用 | Sprint 83/84/85/86 retro 連續四次列為候選但未排入 | Sprint 87 | 使用者選擇優先處理收貨地址簿（另一候選 M18 客服工單留待下輪）。PRD 對此功能僅有 Phase 邊界分類（歸類 Phase 2-B），無 User Story/AC/Schema/API 規格，從零設計。新表 `addresses`（Migration V67），`Address` entity 與租戶無關（僅比對 `userId` 擁有權，買家可能向多個不同租戶下單，非本專案慣見的租戶擁有權模式，已於 Service Javadoc 註記避免誤判）。`AddressService` 提供 CRUD + 設預設（同使用者僅一筆預設，交易內清除其餘）+ 第一筆自動設為預設。`Order` 既有收件自由文字欄位（`shippingAddress`/`shippingRecipientName`/`shippingPhone`）維持不變（snapshot 語意，訂單為歷史記錄不應被地址簿事後編輯/刪除影響）；`OrderDto.CreateRequest` 新增可選 `addressId`，提供時由 `OrderService` 呼叫 `AddressService.getOwnedAddress`（驗證擁有權）複製地址內容覆蓋手動輸入欄位，避免重複實作擁有權判斷邏輯。新增 `ErrorCode.E_8006`/`E_8007`。**意外發現的既有更大缺口（不在本次範圍內處理，僅記錄）**：前端 checkout 頁面目前只服務 Booking 訂房流程，PRODUCT 商品訂單建立 API 在前端完全沒有任何呼叫點——PRODUCT 結帳串接本身尚未實作，是比地址簿更早存在、範圍更大的既有缺口，留待未來獨立 Sprint 評估。前端新增獨立的「地址簿」頁面（`/addresses`，帳戶區選單新增連結），採用既有 inline 表單管理慣例（無彈窗元件，比照 `dashboard/faq/categories` 既有模式）。新增測試：`AddressServiceTest`（新檔 10 tests）、`AddressControllerE2ETest`（新檔 8 tests）、`OrderServiceTest`（+2，addressId 覆蓋收件欄位/跨使用者拒絕）。全量回歸 `mvn verify -Pintegration-test` 1293 tests（926 單元+367 整合）0 fail，`make validate-schema` 無漂移，前端 `npm run build`/`eslint` 皆通過（受限於工具集無瀏覽器自動化能力，僅完成後端真實 HTTP E2E 全流程驗證 + 前端建置/型別檢查/路由掛載確認，未做人工互動式瀏覽器操作驗證） |
| AI-2420 | PRD §6.2.1 M07「結算系統跨結算週期退款處理機制」——`adjustment_statement`（APPROVED/PAID 結算單涉及退款自動生成調整單）+ CREDIT_NOTE（PAID 結算單逆轉，SuperAdmin+財務長雙重授權） | Sprint 83/84/85 retro 連續三次列為候選但未排入 | Sprint 86 | 使用者授權「完整三層」範圍（adjustment_statement + CREDIT_NOTE + 雙重授權），並就「財務長雙重授權如何落地」選擇「新增 CFO 角色，需兩種不同角色各批一次」。探查中發現兩個更深層問題並經使用者同意併入本 Sprint：(1) `SettlementCalculator.calculateTotalRefunds` 對已過濾為 COMPLETED/DELIVERED 的訂單再篩選 status==REFUNDED，但該篩選已排除 REFUNDED，故永遠回傳 0——全額退款訂單本已被 GMV 排除故無影響，但**部分退款**訂單（`Order.status` 不變）從未被扣除，改為依 `Payment.refundedAmount` 建立退款對照表正確扣除；(2) `SettlementController`/`TransferController` 既有 4 個端點的 `admin:read`/`admin:write` 權限字串從未被 `RolePermissionMapping` 授予任何角色（含 SUPER_ADMIN），真實環境 100% 回 403 不可達，見 DEF-042。核心設計：`SettlementAdjustmentService.handleOrderRefund` 由 `PaymentStateService.refundOrderPayment` 退款成功後呼叫（try-catch 容錯），依訂單所屬結算單狀態分流——PENDING/PENDING_REVIEW 直接 delta 扣除 `totalRefunds`/`netSettlementAmount`；APPROVED/PAID 產生 `adjustment_statements`（新表，Migration V66），於下一結算週期由 `SettlementGenerator` 折入並標記 APPLIED；REJECTED/FAILED/找不到對應結算單則不做事。`SettlementReversalService.initiateReversal`/`confirmReversal`：PAID→`REVERSAL_PENDING`（新增中間態，PRD 未定義但避免「發起=已生效」歧義）→`REVERSED`，確認角色須與發起角色不同（`SUPER_ADMIN`/`CFO` 二擇一，同角色拒絕），確認後建立 `CreditNote`（沿用既有孤兒 entity/repository，V36 migration 建表後首次啟用）沖銷原結算金額，不涉及實際銀行資金收回（沿用 Sprint 80/81 既定「不處理 clawback」範圍界線）。`User.UserRole` 新增 `CFO`；`Permission` 新增 `SETTLEMENT_REVERSE`，`RolePermissionMapping` 授予 CFO 最小權限集合（`TENANT_READ/ORDER_READ/ADMIN_READ/SETTLEMENT_REVERSE`，不給一般 `ADMIN_WRITE`）。新增測試：`SettlementCalculatorTest`（+4）、`SettlementAdjustmentServiceTest`（新檔 7 tests）、`SettlementReversalServiceTest`（新檔 7 tests）、`SettlementScheduledJobIntegrationTest`（+2，adjustment 折入 + refund map）、`PaymentStateServiceStripeTest`（+2，hook 呼叫 + 容錯）、`M07SettlementIntegrationTest`（+4，雙重授權 E2E）、`TenantServiceTest`/既有測試因簽名變更同步修正。全量回歸 `mvn verify -Pintegration-test` 1273 tests（914 單元+359 整合）0 fail，`make validate-schema` 無漂移 |
| DEF-042 | `SettlementController`/`TransferController` 共 4 個既有端點（結算單待審清單/批准/駁回、Transfer 駁回重試）的 `@PreAuthorize("hasAuthority('admin:read'/'admin:write')")` 檢查權限字串從未被 `RolePermissionMapping`/`Permission` enum 定義或授予任何角色（含 SUPER_ADMIN），真實環境任何人呼叫皆回 403，Sprint 80/81 建立的結算審核流程完全不可達（既有測試手動塞入字面值字串繞過真實授權邏輯而未發現） | Sprint 86（探查 M07 跨週期退款調整單需求、動手前確認既有結算審核程式碼時發現） | Sprint 86 | 使用者選擇「並入本 Sprint 一併修復」。`Permission` enum 新增 `ADMIN_READ("admin:read", ...)`/`ADMIN_WRITE("admin:write", ...)`；`RolePermissionMapping` 授予 `ADMIN`/`SUPER_ADMIN`（與既有 `SettlementReviewer.checkTenantAccess` 的「ADMIN 限自己租戶、SUPER_ADMIN 跨租戶」語意一致，該邏輯早已假設 ADMIN 可呼叫這些端點，只是權限字串從未真正授予）。不修改 Controller（既有 `@PreAuthorize` 字串本身正確，只是缺少授予端）。修復後既有測試全數維持通過（測試本身用字面值注入不受影響，但真實 JWT 簽發流程現在能正確產生這些權限） | PRD §6.7.2 M16 ERP「採購審批金額上限機制」——StoreOwner 可設定採購單金額上限，超過需 SUPER_ADMIN 核准；Sprint 10 M16 正式交付時遺漏，截至 Sprint 84 從未被任何追蹤文件記錄 | Sprint 10（M16 正式交付時遺漏，PRD §6.7.2 第 903 行原文存在但 Sprint 10 任務清單、`DEFERRED_ITEMS_TRACKER.md`、`PRODUCT_BACKLOG.md` 皆未記錄） | Sprint 85 | 使用者指示「兩者依序進行」承接 Sprint 84 retro 記錄的候選項目之一。探查確認 `PurchaseOrder.POStatus` 已有懸空多年未使用的 `APPROVED` enum 值，予以啟用；新增 `PENDING_APPROVAL`/`REJECTED`。門檻儲存於 `Tenant.purchaseOrderApprovalThreshold`（nullable，null=不啟用，向下相容既有租戶），透過既有 `PUT /v2/tenants/{id}` StoreOwner 自助端點設定（比照既有 `commissionRate` 欄位慣例）。審批動作**刻意不比照** Sprint 80/81 `SettlementReviewer` 的「非 SUPER_ADMIN 限自己租戶」雙層模式——PRD 原文明確寫「需 SuperAdmin 核准」，故完全放在既有 `AdminController`/`AdminService`（`/v2/admin/**`，全數 `hasRole('SUPER_ADMIN')`），比照既有 `approveTenant`/`rejectTenant` 精確模式（含 `recordAudit` 稽核）。`PurchaseOrderService.submitPurchaseOrder`：金額嚴格大於門檻才轉 `PENDING_APPROVAL`，等於門檻仍視為未超過。`PurchaseOrder.canReceive()`/`canCancel()` 各自新增對 `APPROVED`（可收貨/可取消）與 `PENDING_APPROVAL`（可取消）的支援。新增 Migration V65（`tenants.purchase_order_approval_threshold` + `purchase_orders.reviewed_by`/`reviewed_at`/`rejection_reason`）。**IDOR 自我檢查**：`AdminService` 三個新方法刻意不做租戶篩選並於 Javadoc 註明理由（呼叫路徑已被 `@PreAuthorize("hasRole('SUPER_ADMIN')")` 鎖死，跨租戶查看/核准正是本功能目的，非漏洞）。新增測試：`PurchaseOrderServiceTest`（+8 tests，門檻判斷 3 案例 + APPROVED/PENDING_APPROVAL/REJECTED 狀態機案例）、`AdminServiceTest`（+6 tests，approve/reject 成功+失敗+跨租戶查詢）、`AdminControllerE2ETest`（+4 tests，SUPER_ADMIN 成功 + StoreOwner 403）、`TenantServiceTest`（+1 test，門檻設定）。全量回歸 `mvn verify -Pintegration-test` 通過（詳見 commit 訊息數字），`make validate-schema` 無漂移 |
| DEF-041 | `RoomService`/`ProductService` 的 `update`/`delete`/`clearOpenWindow` 完全不做租戶擁有權檢查，可跨租戶竄改/刪除他租戶房源或商品（IDOR，寫入層，已確認） | Sprint 83（開發「房東後台定價日曆預覽」新功能，比對既有房源查詢慣例時發現） | Sprint 84 | 使用者指示「請接著處理」承接 Sprint 83 記錄的建議優先項目。修復採用 Sprint 80/83 既有模式（`checkListingTenantOwnership`：非 SUPER_ADMIN 限自己租戶，SUPER_ADMIN 可跨租戶），而非 Sprint 74 `CmsService` 較舊、允許 `ROLE_ADMIN` 也跨租戶的模式（依 CLAUDE.md Rule 7 衝突時選較新、測試較多者）。`RoomService`/`ProductService` 各自新增 `checkListingTenantOwnership` private helper，`updateRoom`/`deleteRoom`/`clearOpenWindow`/`updateProduct`/`deleteProduct` 新增 `isSuperAdmin` 參數；`RoomController`/`ProductController` 對應端點新增 `@AuthenticationPrincipal UserPrincipal` 判斷角色（比照 `PricingController`）。**實作過程中意外發現比 DEF-041 本身更嚴重的根因問題並停下請示使用者**：`RoomService.createRoom`/`createRoomFromDashboard`/`ProductService.createProduct`/`createProductFromDashboard` 建立 `Listing` 時只設定唯讀影子欄位 `.tenantId(...)`，從未設定真正被 JPA 用來寫入的 `.tenant(...)`/`.owner(...)` 關聯物件，導致房源/商品的 `tenant_id`/`owner_id` 從未真正落地資料庫（直接查詢測試 DB 驗證確認），使租戶擁有權檢查建立在永遠是空值的欄位上、形同虛設，且連帶使 `getRooms`/`getProducts` 預設分支的 `tenantId` 過濾查詢理論上永遠查不到已建立的房源/商品（既有測試僅斷言非 null、未斷言非空，故未被抓到）。使用者選擇「一併修復（推薦）」：四個 create 方法新增 `TenantRepository`/`UserRepository` 依賴，比照 `PostService` 既有模式 `fetchTenant`/`fetchOwner`（`findById` + 找不到分別拋 `E_2000`/`E_1006`）後正確設定 `.tenant(tenant)`/`.owner(owner)`。根因修復後，多個既有測試因「以前 owner_id 永遠是 null、FK 從未真正生效」而露出的既有測試衛生缺口一併浮現並修復：`ProductControllerE2ETest`/`CartControllerE2ETest` 的 `tearDown` 刪除使用者前需先清理其擁有的 listings/products（否則違反外鍵約束，兩者皆改為依 `ownerId` 查詢並先刪 `products` 再刪 `listings`，因 `Product.listingId` 與 `Listing.id` 為同一 PK）；`M01ProductIntegrationTest`/`M02RoomIntegrationTest` 的 `@WithMockUser` 因新端點需要真正 `UserPrincipal`（`principal.getRole()` 對 `@WithMockUser` 預設型別解析為 `null` 而 NPE）而改用手動建構 `Authentication`（比照 `M07SettlementIntegrationTest.authAs` 既有模式），並新增 `TenantRepository`/`UserRepository` mock 搭配 stub。新增測試：`RoomServiceTest`（+8 tests，跨租戶拒絕/SUPER_ADMIN 放行 × updateRoom/deleteRoom/clearOpenWindow）、`ProductServiceTest`（+6 tests，同類案例 × updateProduct/deleteProduct）。`ListingRepository` 新增 `findByOwnerId`（測試清理用途）。全量回歸 `mvn verify -Pintegration-test` 通過（詳見 commit 訊息數字），`make validate-schema` 無漂移（無 migration） |
| DEF-034 | `CmsService.getPageBySlug`/`getActiveBanners`/`recordBannerClick`（公開瀏覽端點）完全不做租戶過濾，跨租戶內容混雜（設計缺口，已確認） | Sprint 74（範圍探查時發現） | Sprint 82 | 使用者指示「兩者依序進行」承接 Sprint 81 retro 記錄的候選項目。動手前重新探查程式碼發現與原始記錄有出入：這三個端點目前**沒有** `SecurityConfig` 的 `permitAll()`，實際上落入 `.anyRequest().authenticated()`（需登入才能呼叫，但登入後任何租戶使用者皆看到混在一起的所有租戶內容）；且前端**完全沒有任何地方**呼叫這三個端點，修改零風險。**業務決策**（使用者拍板）：這些 CMS 頁面/橫幅定位為「訪客可瀏覽的公開行銷內容」（如首頁橫幅、關於我們頁），比照既有 `PostController`/`PostService.getPublishedPostBySlug` 前例（`/v2/posts`，同屬 CMS 領域已採用「呼叫端明確傳入 `tenantId` query 參數」模式）。修復：`ContentPageRepository` 新增 `findByTenantIdAndSlug`；`BannerRepository` 新增 `findActiveByPositionAndTenantId`/`findAllActiveByTenantId`/`incrementClickCountForTenant`；`CmsService` 三方法新增 `tenantId` 參數 + `null` 檢查（`E_1002`，比照 `PostService` 模式）；`CmsController` 三端點新增必填 `@RequestParam UUID tenantId`；`SecurityConfig` 新增 3 條 `permitAll()`。**修復過程中自我糾正一個潛在嚴重錯誤**：原本打算直接對 `/v2/cms/pages/*` 整段 `permitAll()`，但 `PUT /v2/cms/pages/{pageId}`（Admin 更新頁面）與 `GET /v2/cms/pages/{slug}`（公開查詢）恰好是同一個 path pattern 只差 HTTP method，若不限定 method 會誤將 Admin 寫入端點也一併公開放行；改用 `requestMatchers(HttpMethod.GET, "/v2/cms/pages/*")` 限定方法後才套用。`CmsServiceTest` 新增/更新 32 tests（含 tenantId 必填、跨租戶不外洩案例），全量回歸 `mvn verify -Pintegration-test` 通過（詳見 commit 訊息數字），`make validate-schema` 無漂移（無 migration） |
| DEF-040 | `SettlementController` 的 `/v2/admin/settlements/pending`/`approve`/`reject` 完全不做租戶過濾，任一租戶的 ADMIN 可審核/批准/駁回他租戶結算單（安全，已確認） | Sprint 80（開發 AI-2416 Transfer 分潤時發現） | Sprint 81 | 使用者明確指示「一起處理」，與 US-005（webhook 修正版）併入同一 Sprint。採用架構決策：套用 `DEF-038`/`TransferService.checkTenantAccess` 既有慣例——非 `SUPER_ADMIN` 僅能操作自己租戶結算單，`SUPER_ADMIN` 可跨租戶（放棄追蹤器原記錄的「平台級財務團隊」選項，理由是與 Sprint 80 剛完成的 `TransferController` 保持一致，避免同一結算領域內出現兩種不同的租戶隔離規則）。修復：`SettlementStatementRepository` 新增 `findByTenantIdAndStatusOrderByGeneratedAtDesc`；`SettlementReviewer.approveStatement`/`rejectStatement` 新增 `isSuperAdmin` 參數 + `checkTenantAccess` helper（越權拋 `E_1007`）；`getPendingReviewStatements` 新增 `isSuperAdmin`/`tenantIdOverride` 參數（非 SUPER_ADMIN 強制自己租戶，SUPER_ADMIN 可選填指定租戶或維持跨租戶總覽）；`SettlementController` 三個 admin 端點改用 `@AuthenticationPrincipal UserPrincipal` 判斷角色（比照 `TransferController`）。新增 `SettlementReviewerTest`（8 tests：跨租戶批准/駁回拒絕、自己租戶放行、SUPER_ADMIN 跨租戶放行、查詢範圍化三案例）；修復既有 `M07SettlementIntegrationTest` 三個 admin 測試因 `@WithMockUser` 預設 principal 型別不符 `@AuthenticationPrincipal UserPrincipal` 而需改用手動建構 `Authentication`（比照 `TransferControllerE2ETest` 的 `authAs` 模式），並新增 2 個跨租戶案例（ADMIN 403 / SUPER_ADMIN 放行），共 10 tests |
| AI-2416（US-005 修正版，改稱 US-102） | Stripe `transfer.reversed` webhook 同步（原記錄為 `transfer.paid`/`transfer.failed`，經查證 Stripe 官方文件確認此二事件不存在） | Sprint 80（US-005 容量緊繃順延） | Sprint 81 | 🔴 **規劃修正**：Sprint 80 retro 記錄的 US-005 原文「補 `transfer.paid`/`transfer.failed` webhook」為錯誤假設，實際查閱 `docs.stripe.com/api/events/types` 確認 `Transfer` 物件僅有 `transfer.created`/`transfer.reversed`/`transfer.updated` 三種事件（`transfer.paid`/`transfer.failed` 屬 `Payout` 物件，是不同資源）。修正後範圍：僅處理 `transfer.reversed`（transfer 完成後被撤銷，代表資金真的被拿回，是唯一有決策價值的資金狀態異動信號；`transfer.created` 已在 `TransferService.createTransferForStatement` 同步處理、`transfer.updated` 僅 metadata 變更不影響資金狀態，依簡潔優先不處理）。實作：`Transfer.TransferStatus` 新增 `REVERSED`；`TransferRepository` 新增 `findByStripeTransferId`；`TransferService.handleTransferReversedWebhook`（查無記錄僅 log warn 不拋例外，因可能是本系統外觸發的 transfer；查有記錄則轉 `REVERSED` + 結算單轉 `FAILED` 供人工重新處理，不自動重新分潤、不處理資金收回邏輯——沿用 Sprint 80「不處理 clawback」的既有範圍界線）；`PaymentWebhookService` 新增 `TransferService` 依賴 + `case "transfer.reversed"` dispatch。測試：`TransferServiceTest` +2（查有/無記錄）、`PaymentWebhookServiceTest` +2（dispatch + 事件去重重送 skip）。全量回歸 `mvn verify -Pintegration-test` 通過（詳見下方彙總），`make validate-schema` 無漂移（無 migration，僅 enum 新增值） |
| AI-2416 | 真實金流 Phase D-2：結算單審核通過後 transfer 分潤給賣家 + 對帳機制 | Sprint 53（Phase D-1 拆分） | Sprint 80 | 多 Sprint 測試強化計劃（Sprint 66-79）+ DEF-038 修復完成後，PO 選定為下一輪主軸；解除 Sprint 55 記錄的「需正式環境上線才能評估」前提（全案自始至終皆 Stripe test mode 開發，此前提已無意義）。**規劃過程中重大發現**：`core/settlement/`（Sprint 15 建立）已有完整結算單生成+人工審核狀態機（`PENDING→PENDING_REVIEW→APPROVED/REJECTED`），`SettlementReviewer` Javadoc 早已記載但從未實作的 `APPROVED→PAID` 轉換正是本 Sprint 的實作點；原規劃「逐筆訂單付款成功即轉帳」的初稿因此改為「結算單審核通過後才轉帳」，理由：(1) 避免 `SettlementCalculator` 硬編碼 10% 抽成與原計劃 `tenant.commissionRate`（5%）雙口徑衝突；(2) 保留 Settlement 既有人工審核關卡，不讓錢在 Admin 審核前就轉出去。PO 拍板架構：**Separate charge + Transfer**（付款時仍 100% 進平台帳戶，`SettlementReviewer.approveStatement` 觸發後才用 `Transfer.create` 分步轉給賣家）。實作：US-000 `SettlementCalculator.calculateCommission` 簽名改為吃 `commissionRate` 參數，`SettlementGenerator` 改傳入 `tenant.getCommissionRate()`（取代硬編碼 `COMMISSION_RATE=0.10`）；US-001 新增 `transfers` 表（V64，`settlement_statement_id` 唯一索引冪等）+ `Transfer` entity/repository；US-002 `PaymentGateway.createTransfer`（比照既有 `createConnectAccount` 模式新增 default method）+ `StripePaymentGateway` 實作（`Transfer.create`，separate charges and transfers）；US-003 新增 `TransferService.createTransferForStatement`：僅 `APPROVED` 結算單可觸發、`STRIPE_TRANSFER_ENABLED` toggle 保護（新增 `FeatureToggleService.isFeatureEnabledForTenant` 不依賴 `TenantContext` 的變體，因為 Admin 審核他人結算單時呼叫當下的 `TenantContext` 是 Admin 自己租戶，非結算單所屬租戶）、賣家 Connect 帳戶未就緒記錄 `SKIPPED_ONBOARDING_INCOMPLETE`（結算單維持 `APPROVED`，可事後補建）、Stripe 失敗記錄 `FAILED`（結算單同步轉 `FAILED`）且不拋例外中斷「審核通過」這個已持久化的 Admin 決策、🔴 **從第一版就落實租戶隔離**（比照 DEF-038 教訓）：查詢/重試皆檢查呼叫者租戶，`SUPER_ADMIN` 可跨租戶；`SettlementReviewer.approveStatement` 接線 `transferService.createTransferForStatement`（try-catch 隔離，transfer 失敗不回滾審核）。US-004 新增 `TransferController`：`GET /v2/transfers`（對帳查詢，一般/ADMIN 僅自己租戶，`SUPER_ADMIN` 可帶 `tenantId` 查任意租戶）+ `POST /v2/admin/transfers/{statementId}/retry`（管理端重試）。US-005（webhook `transfer.paid`/`transfer.failed` 同步）容量緊繃順延至候選，未列入本 Sprint。測試：`TransferServiceTest`（10 tests，涵蓋狀態守門/冪等/toggle 跳過/前置條件跳過/Stripe 成功轉 PAID/Stripe 失敗轉 FAILED/🔴 跨租戶重試拒絕/SUPER_ADMIN 跨租戶重試/範圍化查詢）、`TransferControllerE2ETest`（5 tests，含 🔴 跨租戶 API 存取拒絕、SUPER_ADMIN 跨租戶查詢/重試）、`StripePaymentGatewayTest` 新增 TC-S011/S012（WireMock `Transfer.create` 成功/失敗）、既有 `SettlementCalculatorTest`/`SettlementScheduledJobIntegrationTest` 因 US-000 簽名變更同步更新（新增「不同租戶 commissionRate 算出不同抽成」測試，確保變更真的生效而非巧合通過）。全量回歸 `mvn verify -Pintegration-test` **1218 tests 0 fail**，`make validate-schema` 無漂移（V64 對齊）。過程中發現一個**非本 Sprint 範圍但值得記錄的潛在缺口**：`SettlementController` 的 `/v2/admin/settlements/pending`/`approve`/`reject` 三個端點僅檢查 `admin:read`/`admin:write` 權限，完全沒有租戶過濾（`SettlementReviewer.approveStatement`/`rejectStatement`/`getPendingReviewStatements` 皆為跨租戶查詢，無 `tenantId` 篩選），代表任一租戶的 ADMIN（只要有 `admin:write` 權限）理論上可審核/批准/駁回其他租戶的結算單——這是否為刻意設計（平台級財務審核本就該跨租戶）或應收斂為租戶範圍，未在本 Sprint 判斷或修改，記錄為新的待評估項目供下一輪規劃參考 |
| DEF-038 | `TenantContextFilter` 對 `ADMIN` 角色的 `X-Tenant-ID` header 無驗證信任，可任意切換 `TenantContext` 租戶（架構審查項目，已確認為真實漏洞） | Sprint 77（探查 `NotificationService` 範圍時發現，記錄待決策未修復） | 多 Sprint 測試強化計劃（Sprint 66-79）完成後的獨立架構追蹤任務（2026-07-06） | 多 Sprint 測試強化計劃全部完成後，使用者決定針對此唯一未結案的架構級項目另開追蹤任務深入調查。重新讀碼確認漏洞成立：`TenantContextFilter.resolveEffectiveTenantId()` 將 `"SUPER_ADMIN".equals(role) \|\| "ADMIN".equals(role)` 同等處理，只要請求帶 `X-Tenant-ID` header 即直接信任其值為 effectiveTenantId，完全未驗證是否等於該使用者自己的 `userTenantId`；而 `RolePermissionMapping.java:109` 註解明確定義 `ADMIN` 為「租戶內管理」角色（非平台級），確認 `ADMIN` 不應具備跨租戶能力，僅 `SUPER_ADMIN` 才是平台級角色。此缺口使任一租戶的 `ADMIN` 帳號可偽造 header 完全接管其他租戶資料，讓過去 `DEF-023/024/026/027/028/029/030/032/033/035/036/039` 等 12 個 DEF 修復所建立的「isAdmin 放行」擁有權檢查，其背後「TenantContext 對 ADMIN 而言可信」的假設失效。修復方案沿用同檔案既有前例：`resolveEffectiveTenantId()` 判斷條件改為僅 `"SUPER_ADMIN".equals(role)` 才允許用 header 指定任意租戶，`ADMIN` 落回下方「一般使用者強制用自己 `userTenantId`（無則系統租戶）」的既有邏輯，與一般使用者同待遇。新增 `TenantContextFilterTest`（3 tests）：紅燈測試 `adminCannotImpersonateAnotherTenantViaHeader`/`adminWithoutHeaderUsesOwnTenant` 修復前執行皆失敗（分別斷言 effectiveTenant 應為 ADMIN 自己租戶，實際卻被 header 覆蓋 / 錯誤 fallback 為系統租戶），證實漏洞成立；`superAdminCanSpecifyAnyTenantViaHeader` 確認 SUPER_ADMIN 平台級能力不受影響 → 修復 → 3 tests 全數轉綠。因修改核心租戶隔離 filter，影響全站每個 endpoint，執行完整全量回歸 `mvn verify -Pintegration-test`（非僅 `mvn test`）**1194 tests 0 fail**（單元+整合，含新增 3 tests，較 Sprint 79 的 1191 增加 3），過去 12 個 DEF 修復相關測試全數通過，證實 ADMIN 綁定自己租戶後，各 Service 既有「isOwner \|\| isAdmin」邏輯在語意上更加穩固（ADMIN 的跨租戶放行範圍現已被正確限縮在自己租戶內，不再是無界的跨租戶放行）。第一次執行全量回歸時因本機測試 DB（`make test-db-up`）未啟動導致 `SellerDashboardServiceCacheTest` 3 個 context-load 錯誤（`Connection refused: localhost:5432`），確認與本次修改無關（純環境前置問題）後啟動測試 DB 重跑，確認乾淨通過 |
| DEF-039 | `IdempotencyService` Redis key 未做租戶/使用者範圍化，跨租戶重放相同 Idempotency-Key 可讀到他租戶已儲存的回應（安全，已確認） | Sprint 79（探查 `IdempotencyService`/`FeatureToggleService` 範圍時發現） | Sprint 79 | 探查確認 `IdempotencyService`（6 個 public 方法）的 Redis key 僅為 `"idempotency:" + idempotencyKey`，完全沒有依 `TenantContext` 做租戶/使用者範圍化；`idempotencyKey` 由客戶端經 `Idempotency-Key` HTTP header 提供（唯一呼叫端 `BookingController`），僅驗證 UUID v4 格式（`isValidUuidV4`），不具備任何機密性——不同租戶/使用者巧合、UUID 產生器熵不足、或用戶端重放皆可能送出相同值。與既有 `RedisCartService.getCartKey(userId, tenantId)`（`core/cart/RedisCartService.java:374`）同一類「Redis key 必須含租戶/使用者範圍」問題，但 `IdempotencyService` 未依此既有前例設計。實際影響：租戶 B 若送出與租戶 A 相同的 Idempotency-Key，`checkAndMark` 會誤判為重複請求（回傳 `false`），且 `getStoredResponse` 會回傳租戶 A 先前儲存的完整訂房回應內容，屬跨租戶資料洩漏。`FeatureToggleService`（2 個 public 方法）探查確認乾淨：兩個方法皆一律使用 `TenantContext.getCurrentTenant()` 查詢，無可由呼叫端覆寫的參數，未發現缺口。修復：新增 `buildKey(idempotencyKey)` private helper，比照 `RedisCartService` 前例將 `TenantContext.getCurrentTenant()`/`getCurrentUser()` 納入 Redis key（`"idempotency:" + tenantId + ":" + userId + ":" + idempotencyKey`），`checkAndMark`/`markCompleted`/`getStoredResponse`/`isStillProcessing`/`remove` 五個方法統一改用此 helper（`isValidUuidV4` 為純格式檢查，無需修改）。紅燈測試 `IdempotencyServiceTenantIsolationTest.checkAndMark_sameKeyDifferentTenant_mustNotLeakStoredResponse`（修復前執行：租戶 B 的 `checkAndMark` 回傳 `false`，斷言「應視為全新請求」失敗，證實漏洞成立）→ 修復 → 轉綠，2 tests 0 fail；另新增 `IdempotencyServiceTest`（12 tests，涵蓋全部 6 個 public 方法一般行為）與 `FeatureToggleServiceTest`（7 tests，涵蓋 2 個方法 + 租戶隔離確認），單元測試合計 21 tests 0 fail。因修改生產程式碼，全量回歸 `mvn verify -Pintegration-test` 執行確認 **1191 tests 0 fail**（單元 849 + 整合 342），`make validate-schema` 無漂移（無 entity/migration 變更）。附加修正：本 Sprint 首次執行 `mvn verify -Pintegration-test`（Sprint 77/78 為純測試 Sprint 依政策僅需 `mvn test`，未觸發 verify phase 的 `checkstyle-test`）時發現 Sprint 77 遺留的 `NotificationServiceTest.java` 3 個未使用 import（`anyInt`/`Page`/`PageRequest`），確認與本 Sprint 變更無關後一併清除，重跑全量回歸確認乾淨 |
| DEF-036 | `LogisticsService` 除 `createLogistics` 外其餘 6 個方法皆缺租戶擁有權檢查（安全，已確認） | Sprint 76（探查 `LogisticsService` 範圍時發現） | Sprint 76 | 依使用者指示特別留意 `LogisticsService` 是否除 `createLogistics`（DEF-019 已修復）外仍有缺口，探查確認 `getLogistics`/`getLogisticsByOrderId`/`trackLogistics`/`getTrackingDetail`/`updateLogisticsStatus`/`cancelLogistics` 全部 6 個方法皆直接 `findById` 後即讀取/寫入，完全沒有租戶擁有權檢查；`LogisticsController` 僅以 `hasAuthority('order:read')`/`order:update'` 把關（權限分散於各租戶角色，非租戶範圍限制），任一租戶持有對應權限者即可讀取他租戶物流單詳情/追蹤歷史，或竄改其物流狀態（`updateLogisticsStatus`/`cancelLogistics` 屬寫入操作，`DELIVERED` 狀態變更會連動竄改他租戶訂單狀態為 `DELIVERED`），屬跨租戶 IDOR，與 `DEF-019`/`DEF-024`/`DEF-028`/`DEF-032` 同一 tenant-based 模式。修復：新增 `checkLogisticsTenant(Logistics)` helper（依 `logistics.getOrderId()` 反查 `Order` 後委派既有 `checkOrderTenant`），6 個方法皆於狀態/業務邏輯檢查**之前**呼叫（IDOR 正確順序），`checkOrderTenant` 訊息由「create」改為通用措辭；`getLogisticsByOrderId` 因無 `Logistics` 實體可查，改為先 `orderRepository.findById(orderId)` 取得 `Order` 後直接呼叫 `checkOrderTenant`。紅燈測試：先暫時還原 `LogisticsService.java` 至修復前版本執行新增的 `LogisticsServiceTenantAccessTest`（5 個跨租戶案例），5 個「預期拋 E_1007」斷言皆因未拋出例外而失敗，另 3 個成功案例因 `orderRepository` stub 未被呼叫觸發 `UnnecessaryStubbingException`，證實漏洞成立且測試 fixture 正確接線 → 還原修復 → 轉綠，8 tests 0 fail；同步更新 `LogisticsServiceCancelTest`（`cancelLogistics` 新增的擁有權檢查需要 `orderRepository`/`TenantContext` stub，補上本租戶情境 fixture 後 3 tests 0 fail）。單元測試 `mvn test` 全量 0 fail |
| DEF-035 | `StompAuthChannelInterceptor` SUBSCRIBE 授權缺失，任何連線者可竊聽他人對話（安全，已確認） | Sprint 75（探查 `ChatService` 範圍時發現） | Sprint 75 | 依使用者指示特別留意 `ChatService` 涉及的 WebSocket/STOMP 即時通訊，探查發現 `ChatService` 本身 6 個 public 方法（`createConversation`/`sendMessage`/`getUserConversations`/`getMessages`/`markAsRead`/`deleteConversation`）皆已透過 `ConversationRepository.findByIdAndUserId`（initiator OR recipient）正確做 participant-scoping，REST 層無缺口；但 `StompAuthChannelInterceptor`（`api/filter/`）僅在 STOMP CONNECT 時驗證 JWT，對後續 SUBSCRIBE frame 完全未檢查目的地。`WebSocketConfig` 用 `enableSimpleBroker("/topic", "/queue")`，`/queue/conversations/{id}/messages` 只是命名慣例並非 Spring per-user 目的地（那需要 `/user/**` 前綴），任何已連線者皆可訂閱**任意** conversationId 的 queue；且 CONNECT 時 JWT 缺失/無效僅記錄警告仍放行連線，匿名連線亦可訂閱。任何人取得或猜到一個 conversationId（UUID）即可用 raw STOMP client 竊聽該對話即時訊息，完全繞過 REST 層已正確實作的 participant-scoping 保護（CWE-862 Missing Authorization）。修復：`StompAuthChannelInterceptor` 新增 SUBSCRIBE 攔截邏輯，比對目的地 pattern `/queue/conversations/{id}/messages`，注入 `ConversationRepository` 查詢訂閱者是否為該對話 initiator/recipient，不符或未認證則回傳 `null` 阻斷訂閱（未變更既有 CONNECT 邏輯）。紅燈測試 `StompAuthChannelInterceptorTest`（新檔，6 tests）：先加入 `ConversationRepository` 依賴但未加 SUBSCRIBE 邏輯時執行，非參與者/匿名/對話不存在三案例皆未被攔截（回傳非 null），證實漏洞成立 → 修復 → 轉綠，6 tests 0 fail；因未認證案例修復後於取得 userId 階段即短路而非查 repository，移除變多餘的 stub（Mockito `UnnecessaryStubbingException`）。全量回歸 `mvn verify -Pintegration-test` 1083 tests 0 fail（單元 741 + 整合 342），`make validate-schema` 無漂移（無 entity/migration 變更） |
| DEF-033 | `CmsService.getPages`/`getBanners`（Admin 列表）跨租戶讀取洩漏（安全，已確認） | Sprint 74（範圍探查時已確認） | Sprint 74 | 探查確認 `getPages`/`getBanners` 呼叫 `ContentPageRepository.findByStatusOrderBySortOrderAsc`/`BannerRepository.findByStatusOrderBySortOrderAsc` 完全無租戶過濾，即使兩個 Repository 皆已有 `findByTenantIdAndStatus(OrderBySortOrderAsc)` 方法卻從未被使用（與 Sprint 66 `ProductService` 關鍵字搜尋死碼、Sprint 65 `getRevenueStats` granularity 死碼同一類「查詢方法寫好卻沒接上」模式）。任一持有 `cms:read` 權限的租戶管理者皆可透過 `GET /v2/cms/pages`、`GET /v2/cms/banners` 取得系統中所有租戶的頁面/橫幅列表。修復：比照 `DEF-026`/`DEF-029` 保留舊查詢 + 新增租戶過濾分支模式，`isCurrentUserAdmin()`（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN`）沿用舊查詢維持跨租戶總覽，非 admin 一律改用既有的 `findByTenantIdAndStatusOrderBySortOrderAsc`/`findByTenantIdAndStatus`。紅燈測試 `CmsServiceTest.getPages_nonAdmin_onlyOwnTenant`/`getBanners_nonAdmin_onlyOwnTenant`（修復前執行因未 stub 到實際呼叫的錯誤 repository 方法而 NullPointerException 失敗，證實漏洞成立：修復前程式碼完全未依租戶區分呼叫路徑）→ 修復 → 轉綠，單元 27 tests 0 fail，全量回歸 `mvn verify -Pintegration-test` 0 fail |
| DEF-032 | `CmsService.updatePage`/`publishPage`/`updateBanner`/`publishBanner` 跨租戶寫入 IDOR（安全，已確認） | Sprint 74（範圍探查時已確認） | Sprint 74 | 探查確認四個寫入方法皆呼叫 `findById` 後直接修改/發布，完全沒有擁有權/租戶檢查；Controller 端僅要求 `cms:update`/`cms:publish` 權限，此權限可能分散於各租戶的管理者角色，任一租戶管理者可竄改/發布其他租戶的頁面或橫幅，屬跨租戶寫入 IDOR，與 `DEF-019`（`LogisticsService.createLogistics`）/`DEF-024`/`DEF-028` 同一 tenant-based 模式。比照既有前例簡化為「本租戶 or admin（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN`）」放行，新增 `checkCmsTenantOwnership`/`isCurrentUserAdmin` 兩個 helper（`CmsService.java`），越權拋 `E_1007`。紅燈測試 `CmsServiceTest.updatePage_crossTenant_mustBeRejected`/`publishPage_crossTenant_mustBeRejected`/`updateBanner_crossTenant_mustBeRejected`/`publishBanner_crossTenant_mustBeRejected`（修復前執行皆因程式碼直接往下執行到 `toPageResponse`/`toBannerResponse` 對未 stub 的 `save()` 回傳值解參考而 NullPointerException 失敗，證實修復前完全沒有攔截跨租戶寫入）→ 修復 → 轉綠，單元 27 tests 0 fail，全量回歸 `mvn verify -Pintegration-test` 0 fail |
| DEF-030 | `ReviewService.getUserReviews` 匿名保護繞過（IDOR/隱私，安全，已確認） | Sprint 73（範圍探查時已確認，C 項） | Sprint 73 | 探查確認 `getUserReviews(userId, ...)`（`core/review/ReviewService.java`）未檢查 `userId` 是否等於 `TenantContext.getCurrentUser()`；`BUYER` 角色持有 `order:read` 權限，任意登入買家可在 `GET /v2/reviews/user/{userId}` 代入任意他人 `userId`，取得該使用者完整評價內容（`toReviewResponse` 僅依 `isAnonymous` 隱藏 `userId`/`userFullName`/`userAvatarUrl`，`content`/`rating`/`listingId` 一律回傳），等於繞過匿名評價的身分保護設計。比照 `DEF-018` 買家自助模式修復為 owner-or-admin：非 admin 且 `userId` 不等於當前使用者拋 `E_1007`。紅燈測試 `ReviewServiceTest.getUserReviews_otherUser_mustBeRejected`（修復前執行失敗，實測證實漏洞）→ 修復 → 轉綠，全量回歸 0 fail |
| DEF-029 | `ReviewService.getReviewsByHandlingStatus` 跨租戶讀取洩漏（安全，已確認） | Sprint 73（範圍探查時已確認，B 項） | Sprint 73 | 探查確認 `getReviewsByHandlingStatus`（`/v2/reviews/managed`）呼叫 `ReviewRepository.findByIsHandled(isHandled, pageable)` 完全無 listingId/tenant 過濾；`reviews` 資料表本身無 `tenant_id` 欄位，需經 `listing.tenant.id` 二層 join 取得。任一持有 `room:update`/`product:update` 權限的賣家（分散於各租戶）皆可取得系統中所有租戶的評價列表。修復：新增 `ReviewRepository.findByIsHandledAndTenantId`（`@Query` JPQL `r.listing.tenant.id = :tenantId`，比照 `DEF-026` 保留舊方法 + 新增租戶過濾方法模式），`getReviewsByHandlingStatus` 改為 admin（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN`）沿用舊查詢（跨租戶總覽），非 admin 一律改用租戶過濾查詢。紅燈測試 `ReviewServiceTest.getReviewsByHandlingStatus_mustNotLeakOtherTenantReviews`（修復前執行失敗，實測證實漏洞）→ 修復 → 轉綠，全量回歸 0 fail |
| DEF-028 | `ReviewService.markAsHandled`/`markAsUnhandled` 跨租戶寫入（IDOR，安全，已確認） | Sprint 73（範圍探查時已確認，A 項） | Sprint 73 | 探查確認 `markAsHandled`/`markAsUnhandled`（`core/review/ReviewService.java`）完全沒有擁有權/租戶檢查；Controller 端僅要求 `room:update`/`product:update` 權限，`SELLER`/`HOST`/`STORE_OWNER`/`ADMIN` 角色皆持有且分散於各租戶，任一租戶賣家可呼叫 `PUT /v2/reviews/{reviewId}/handle` 竄改其他租戶商品評價的處理狀態，屬跨租戶寫入 IDOR，與 `DEF-024`/`DEF-026`/`DEF-027` 同一模式。此操作無「本人」語意（操作者是管理商店的賣家，非評價作者），比照 `DEF-024` 三選一模式簡化為「本租戶（`review.getListing().getTenantId()`）or admin（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN`）」放行，新增 `checkReviewManagementAuthorization`/`isCurrentUserAdmin` 兩個 helper，越權拋 `E_1007`。紅燈測試 `ReviewServiceTest.markAsHandled_crossTenantReview_mustBeRejected`/`markAsUnhandled_crossTenantReview_mustBeRejected`（修復前執行失敗，實測證實漏洞）→ 修復 → 轉綠，全量回歸 0 fail |
| DEF-027 | `PurchaseOrderService.createPurchaseOrder` 品項未驗證 listing 租戶歸屬（跨租戶庫存挪用，安全，已驗證成立） | Sprint 72（範圍探查時標記為「待驗證」） | Sprint 72 | Sprint 72 探查階段標記為疑似漏洞（B 項），使用者決策「先寫測試驗證，成立才修」。撰寫 `PurchaseOrderServiceTest.createPurchaseOrder_crossTenantListing_mustBeRejected` 測試後**實測證實漏洞成立**：攻擊者租戶可用自己合法的 supplier，搭配指向他租戶 listing/SKU 的品項建立採購單，`createPurchaseOrder` 完全未驗證品項 `listingId` 的租戶歸屬（同檔案已注入但標記 `@SuppressWarnings("unused")` 的 `ListingRepository` 從未被使用，是明顯的驗證缺漏跡象）。比照 `StockMovementService.createManualMovement` 既有的 DEF-017 修復模式，新增 `validateListingOwnership(listingId, tenantId)`，於建立每個品項前驗證其 `listing.getTenantId()` 與當前租戶相符，不符拋 `E_1007`；移除 `ListingRepository` 欄位的 `@SuppressWarnings("unused")`。紅燈測試（修復前執行會失敗）→ 修復 → 轉綠，全量回歸 0 fail |
| DEF-026 | `InventoryService.getInventoryBySku` 跨租戶讀取洩漏（安全，已確認） | Sprint 72（範圍探查時已確認） | Sprint 72 | Sprint 72 探查階段確認：`getInventoryBySku(skuId)` 呼叫 `InventoryRepository.findBySkuId(skuId)` 未帶入 `tenantId` 過濾，即使 `Inventory` entity 已有 `tenantId` 欄位；`ErpController.getInventoryDetail` 雖取得 `tenantId` 但僅用於 log。任何登入的 `STORE_OWNER`/`SELLER` 可讀取他租戶 SKU 的庫存數量/儲位/安全庫存/再訂購點。修復：新增 `InventoryRepository.findBySkuIdAndTenantId`，`InventoryService.getInventoryBySku` 改用此方法查詢，跨租戶查無結果回傳 `null`（維持既有「不存在回傳 null」語意，`ErpController` 404 分支不受影響）。紅燈測試 `InventoryServiceTest.getInventoryBySku_crossTenantSku_mustNotLeakOtherTenantData`（修復前執行失敗，實測證實漏洞）→ 修復 → 轉綠，全量回歸 0 fail |
| DEF-001 | Booking E2E 完整預訂流程測試 | Sprint 5 | Sprint 6 | 已實作統一端點 + Feature Toggle + 測試資料，E2E 測試通過 |
| DEF-004 | listings.tags 欄位類型修復 | Sprint 7 | Sprint 10 | V8__Fix_Listings_Tags_Column_Type.sql migration 已建立，測試通過 (6/6) |
| DEF-005 | M10 IM SA 需求分析 | Sprint 21 Buffer-B | Sprint 22 | M10_IM_REQUIREMENTS.md 建立，PM/PO Victoria APPROVED |
| DEF-006 | M11 Provider Stub 強化 | Sprint 21 Buffer-C | Sprint 22 | HCT/TCAT 追蹤號改為 {Provider}-{yyyyMMdd}-{HEX8} 格式 |
| DEF-007 | M11 物流與訂單履約流程整合 | Sprint 21 | Sprint 23 | createLogistics 前置驗證 + 訂單狀態同步 SHIPPING/DELIVERED，4 個整合測試通過 |
| DEF-008 | ShippingTemplate 接入訂單結帳流程 | Sprint 21 | Sprint 23 | V47 Migration + shippingFee 欄位 + 免運門檻邏輯，3 個整合測試通過 |
| DEF-009 | Logistics.logisticsData jsonb 映射慣例統一 | Sprint 25 | Sprint 26 | US-006：統一為 Map + @JdbcTypeCode(SqlTypes.JSON)，validate-schema 通過 |
| DEF-010 | OrderStateMachine 一致性清理（SHIPPING→CANCELLED） | Sprint 25 | Sprint 26 | US-004：移除 SHIPPING→CANCELLED + 轉換表↔canCancel 一致性不變量測試 |
| DEF-011 | cancelLogistics 錯誤碼修正（→ E_7500 系列） | Sprint 25 | Sprint 26 | US-004：改 E_7500（not found）/E_7502（delivered）+ 3 單元測試 |
| DEF-012 | ChatService.toMessageResponse 廣播 conversationId 補正 | Sprint 25 | Sprint 26 | US-003：改由 conversation 關聯取 id + 廣播 payload 測試 |
| DEF-014 | e2e 乾淨 DB 註冊回 401 致 10 spec 失敗 | Sprint 26 | Sprint 26 | 真因為 validate-e2e.sh 誤設 NEXT_PUBLIC_API_URL（雙 /v2），非產品 bug；修正後 27 passed/5 skip/0 fail，e2e 改 strict 預設 |
| DEF-013 | M09 MQ 通知缺端到端驗證 | Sprint 26 | Sprint 27 | US-003：補 produce→佇列→consume 端到端測試（NotificationProduceConsumeTest，真實 ObjectMapper + 共用佇列）。**揪出並修復真 bug**：producer 用 Stream(XADD)、consumer 用 List(RPOP) 同 key 型別不相容 → 通知永不被消費；改為兩端一致 List（leftPush/rightPop） |
| DEF-015 | 前端 next/font/google 建置期外部抓取 | Sprint 26 | Sprint 27 | US-002：layout.tsx 的 Geist 變數從未被 CSS/Tailwind 消費（死碼），移除 next/font/google import → 離線 build 不再抓 Google，零視覺影響 |
| DEF-016 | Admin Audit Log 持久化 + 查詢 | Sprint 28 | Sprint 61 | **歷時 Sprint 28→31→61**。Sprint 31：US-003 建 AuditLog entity + AuditLogRepository + V57 migration；AdminService 6 個關鍵操作寫入 audit_log（與 log.info 並存，catch 不中斷主流程）；make validate-schema 無漂移，AdminServiceIntegrationTest 稽核測試通過。**Sprint 61（US-001）補完查詢管道**：只完成寫入、缺查詢 API/頁面的缺口清償——`GET /v2/admin/audit-logs`（SUPER_ADMIN，分頁+action/日期範圍篩選）+ `AdminService.getAuditLogs`（`JpaSpecificationExecutor` 動態組合，避免 PostgreSQL 對純 null 參數的型別推斷限制）+ 前端 `admin/audit-logs/page.tsx`。AdminServiceTest +3、AdminControllerE2ETest +2（18/18 通過） |
| DEF-018 | 訂單 getOrder 無擁有權檢查（IDOR，安全） | Sprint 31 | Sprint 32 | US-001（AI-1601）：getOrder 加 owner/admin 擁有權檢查（比照同類 cancelOrder/getOrderStateLogs 的 inline pattern），越權回 403/E_1007；findOrderById 不動保留 404 not-found 語意，最小爆炸半徑不影響 payment/賣家/admin 內部流程；補 BuyerOrderJourneyE2ETest.otherBuyerCannotGetOrder（買家 C 讀 A 訂單→403）；本地訂單 E2E 20 tests 0 fail |
| DEF-020 | 版型 Shell 架構債（route-group layout + client 邊界下推 + 全站狀態走 URL） | Sprint 35 | Sprint 36 | US-002：Architect 審查建議三項全數償還——(a) 建 App Router route-group `app/(storefront)/layout.tsx` 承載 TOP（Header）/Bottom（Footer），首頁移入 group（換頁不重建版型）；(b) `"use client"` 邊界下推——StorefrontShell 改 grid-only server component、Footer/Tools 維持 server，僅 Header（含 SearchBar/ThemeSwitcher）client；(c) 搜尋/分類/排序/分頁改走 URL query（server page 讀 searchParams → props 傳 client `HomeContent`，官方建議免 useSearchParams+Suspense；Header 搜尋 router.push、購物車數量自取），移除 page-scoped callback 與 nonce 補丁（連帶償還 F-06）。以 key-remount 於篩選變更顯示 skeleton（避免 effect 內同步 setState，符 React 19 嚴格 hooks）。build/type-check/lint 0 error；**at-homepage E2E 4 tests 全棧全綠**（含搜尋改走 URL 的 E2E-HOME-03），既有 E2E 不退步（唯 m15 既有 flaky） |
| DEF-019 | 訂單付款/物流讀寫無擁有權檢查（IDOR 姊妹，安全） | Sprint 32 | Sprint 36 | **歷時 S32→33→36**。S33 修訂單付款側（checkOrderOwnership，買家限本人）。**S36（AI-1902）補完物流/賣家側**：LogisticsService.createLogistics 加 tenant-based 擁有權檢查（`order.tenantId==當前租戶`、admin 放行、越權 403/E_1007，賣家側租戶語意）；PaymentService.processOrderPayment（`/v2/payments` 對外入口）加 user-based 擁有權檢查（比照 checkOrderOwnership）。**測試**：新增 LogisticsServiceOwnershipTest + PaymentServiceOwnershipTest（各 3 tests，越權→E_1007、本人/本租戶通過、admin 放行）；對齊 M07 整合測試（`@Transactional` 一級快取致影子 `userId` 為 null → builder 明確設 `.userId` + 訂單擁有者=呼叫者，**順帶修好 S33 遺留的 5 個 M07 失敗**）。乾淨 DB：M07 8 + M11 4 整合測試 0 fail、單元 353 tests 0 fail。**活躍安全 DEF 歸零**。備註：processBookingPayment（預訂付款側）非 DEF-019 範圍，如需擁有權檢查另立項評估 |
| DEF-017 | ERP 手動庫存異動租戶隔離（安全） | Sprint 28 | Sprint 34 | **歷時 Sprint 28→34（三度誠實回退後落地）**。US-001（AI-1701）：StockMovementService.createManualMovement 加 null 安全租戶檢查（inject ListingRepository → `!tenantId.equals(listing.getTenantId())`，越權 403/E_1007，移除 getTenantListings no-op）。**三層根因**：Listing.tenantId insertable=false 影子欄位（需 tenant 關聯）+ Tenant.id @GeneratedValue 使 @WithErpSecurity 硬編 FIXED_TENANT_ID 無 tenants 列 + listings FK。**修法**：M16 以 raw SQL 種 FIXED_TENANT_ID 租戶列（比照 TestDatabaseInitializer）+ JDBC UPDATE listing tenant_id + 修 @AfterAll cleanup 先刪 product_skus + IT-M16-307 跨租戶測試。乾淨 DB 43 tests 0 fail |
| AI-2403 | 進階定價接入 PRODUCT/Cart 計價鏈 | Sprint 43 | Sprint 44 | US-001（AI-2403）：`RedisCartService.getCart` 讀取時對 PRODUCT 項以 `getEffectivePrice`（今日基準）套折扣（toggle+向後相容），unitPrice/subtotal 折扣後 + CartItemResponse transient 折扣欄位；`OrderService` 未改（訂單繼承 getCart 折扣後 subtotal，顯示與下單一致）；getEffectivePrice 補 appliedRuleName。schema-free。單元 3 + 真 DB 整合 45 tests 0 fail |
| AI-2405b | 買家整月日曆每日折扣顯示 | Sprint 43 | Sprint 44 | US-002（AI-2405b）：`getCalendar` 以 `calculatePrice` 逐日 breakdown merge 折扣（checkOut exclusive→endDate+1、只可訂日、只折扣型）；CalendarResponse + CalendarDay 補 originalPrice/appliedRuleName；MonthCalendar 原價刪除線（保留 calendar-price testid + 新增 calendar-original-price）+ E2E-ROOM-07。無 schema |
| AI-2303 | Inter 字體建置期 Google Fonts 依賴 | Sprint 42 | Sprint 44 | US-003（AI-2303）：`layout.tsx` next/font/google → next/font/local，committed Inter latin variable woff2（48KB OFL，一次性自 Google Fonts 取得）；消 build 期網路依賴（同源 DEF-015）；CJK 系統堆疊不動。build 0 error、無 next/font/google 引用 |
| AI-2406 | 定價機制統一（room_calendar 手動日價 vs 規則） | Sprint 43 | Sprint 45 | US-001（AI-2406）：探勘揭穿「雙定價機制」實為死碼假象——`room_calendar.price` 寫入路徑 setDatePrice/setDatePriceBulk **零呼叫者**、欄位恆 NULL。移除死碼兩方法；BookingService 三處讀取（checkAvailability/getCalendar/calendarBaseTotal）移除死欄位 fallback 改直取 basePrice（**行為等價**，順帶修正 calendarBaseTotal NULL→ZERO 潛在低估）；RoomCalendar.price 註解標記停用（未加 @Deprecated 以維持 @Deprecated=0 慣例）；確立 MANUAL_OVERRIDE 為唯一手動日價路徑。決策文件 PRICING_MECHANISM_UNIFICATION.md。單元 6 + 真 DB 整合 57 tests 0 fail、schema-free。**漲價計入 booking 行為變更另立 AI-2406b（PO 決策）；DROP COLUMN 另立後續低風險** |
| AI-2202d | 整月日曆「未開放 vs 可訂」語意 | Sprint 42 | Sprint 45 | US-002（AI-2202d，spike）：產決策文件 CALENDAR_OPEN_WINDOW_ASSESSMENT.md——記錄「無記錄=可訂」為 availability/booking/calendar 三層硬語意、room_calendar 稀疏 lazy 建立；三選項比較（A Room 層級 open_until_date【推薦，需 migration】/ B CLOSED 狀態【高風險】/ C 純前端【不建議單用】）+ 既有房源 NULL 安全過渡。**不改 production code**。**實作另立 AI-2202e（需 PO 拍板 schema）** |
| AI-2406b | 漲價型規則計入 ROOM booking 總價（計價行為變更） | Sprint 45 | Sprint 46 | **PO 拍板選項 B**。US-001（後端）：BookingService 放寬三處折扣閘門（tryDynamicPricing `<baseTotal`→`≠0`、getCalendar 逐日 `<0`→`≠0`、calculateTotalAmount toggle 開即採 adjustedTotal）使 availability/月曆/建單 totalAmount 一律含漲價乘數；保留 toggle 關短路+失敗降級（向後相容）；計算核心不動；PricingService 抽 `resolveListingForPricing` 優雅降級（無 Room fallback basePrice、null 回 4xx 非 NPE→500）；DTO 中性調整語意（discountAmount 改有號差額，新增 priceAdjustmentType DISCOUNT/MARKUP/NONE）。US-002（前端）：ListingDetail/MonthCalendar 折扣維持刪除線+綠 badge、漲價改不刪除線+橙 badge「加價 X」；E2E-ROOM-08/09 漲價變體。後端單元 18 + 真 DB 整合 34、validate-e2e **50 passed/0 fail**、schema-free（V57）。**M12 進階定價收官**（折扣+漲價皆顯示=收費）。只做 ROOM（PRODUCT 另立 AI-2406c）；priority/range 查詢落差另立 AI-2407 |
| AI-2202e | 開放窗語意實作（未開放 vs 可訂） | Sprint 45 | Sprint 47 | **PO 拍板選項 A + 追加滾動視窗 + host UI**。US-001（後端）：V58 migration rooms 加 open_until_date DATE + booking_window_days INT（皆 nullable、既有列 NULL=無限制、backfill 免異動、ADD COLUMN IF NOT EXISTS 冪等）；抽 Room.resolveOpenUntil（取最早生效 min）三層一律呼叫；getCalendar 超窗無記錄日補 NOT_OPEN（計算產物非持久化，抽 appendNotOpenDays 控 NPath）、checkAvailability 超窗 available=false+原因、createBooking+reschedule 超窗擋訂 E-3002（422）；RoomCalendarService 未改（擋在 caller 層）；兩欄 NULL 維持現狀。US-002（前端）：MonthCalendar NOT_OPEN 灰底禁選不刪除線+data-not-open+圖例；ListingDetail 沿用既有不可訂路徑；booking.ts type；room.ts+RoomForm 雙欄位；E2E-ROOM-10/11。後端單元 9 + 真 DB 整合 38（含 API-M06-016）、validate-schema **無漂移**、validate-e2e **52 passed/0 fail**。⚠️ V58 結束 S42~S46 連續零-migration。只做 ROOM；清窗機制另立 AI-2202f、reason i18n 另立 AI-2408 |
| AI-2406c | PRODUCT/cart 漲價（M12 進階定價 PRODUCT 側收官） | Sprint 46 | Sprint 48 | **兩道閘門**（比 ROOM 難）。US-001（後端）：閘門 2（結構性）applyProductRule 由 discount-only 擴充支援漲價型 MANUAL_OVERRIDE price/SEASONAL multiplier/WEEKDAY_WEEKEND weekendMultiplier（對齊 ROOM config key，保留 discountPercent 向後相容）；閘門 1 RedisCartService 折扣閘門 `<現價`→`≠現價`；CartItemResponse 加 priceAdjustmentType + 有號 discountAmount；下單自動繼承（OrderService 未改）。US-002（前端）：cart/page 首次顯示 item 定價（折扣刪除線+綠標/漲價不刪除線+橙標），checkout 不 itemize 未改；E2E-M11-012。後端單元 22 + 真 DB 整合 54（含 IT-EP-004）、validate-e2e **53 passed/0 fail**、schema-free（V58）。**M12 進階定價全面收官**。誠實：SP 初估 3→探勘修正 8（兩道閘門）；PRODUCT/ROOM 兩套計算器對齊 key 未合併（另立 AI-2409）|
| AI-2410 | 真實金流 Phase A：卡片付款 MVP（Stripe Checkout hosted，平台代收）| Sprint 49 | Sprint 50 | 承 S49 評估 + PO 拍板（Checkout hosted + 先平台代收）。V59 payments 加 Stripe 欄位 + STRIPE method + PROCESSING；接回孤兒 gateway——StripePaymentGateway 補 createCheckoutSession（Session.create 平台代收）+ retrieveCheckoutSession；STRIPE_PAYMENT_ENABLED toggle（預設關=mock 不變）；PaymentStateService initiateStripeCheckout（建 PROCESSING+Session）+ confirmStripeCheckout（回跳 retrieve，paid→SUCCESS+Order PAID，冪等）；端點 /pay/checkout + /return；前端 orders/[id] 重導 + success/cancel + E2E-M11-013。後端單元 9（WireMock TC-S004/005）+ 真 DB 整合 25（mock 不退步）、validate-schema 無漂移、validate-e2e **54 passed/0 fail**。誠實：Phase A 僅回跳 retrieve、webhook 權威狀態留 Phase B（AI-2411）；平台代收分帳留 Phase D；⚠️ V59 打破 schema-free |
| AI-2411 | 真實金流 Phase B：webhook 事件驅動權威狀態 | Sprint 49 | Sprint 51 | 補 Phase A「買家未回跳」缺口。PaymentWebhookService 解析 Stripe 事件（checkout.session.completed[paid]→SUCCESS+Order PAID 權威、payment_intent.payment_failed→FAILED、未知→記錄不 dispatch）；StripeWebhookController 驗簽後委派一律回 2xx；PaymentStateService 抽 markStripePaymentSucceeded/Failed 共用核心（回跳 Phase A 與 webhook 雙路徑一致）；V60 processed_stripe_events 去重 + event id 去重；雙層冪等。後端單元 9（UT-WH-001~005）+ 真 DB 整合 21（mock/Phase A 不退步）、validate-schema V60 無漂移、validate-e2e **54 passed/0 fail**。誠實：只做成功/失敗（退款留 Phase C）；失敗路徑 best-effort（pi 惰性）；測試模式跳驗簽，生產須配 secret（上線 checklist AI-2414）|
| AI-2412 | 真實金流 Phase C：退款真串接 | Sprint 49 | Sprint 52 | StripePaymentGateway.processRefund 由 stub 改真 Refund.create（以 payment_intent 全額退款）；PaymentStateService.mockRefund 重構 refundOrderPayment toggle-aware（stripe 真退款+存 stripe_refund_id / mock 保留）；PaymentWebhookService 加 charge.refunded 權威 REFUNDED（冪等+V60 去重）；createCheckoutSession 補 pi metadata order_id（補 Phase B best-effort）；V61 payments 加 stripe_refund_id。後端單元 19（TC-S006 + 退款 005~007 + UT-WH-006）+ 真 DB 整合 21（mock/Phase A/B 不退步）、validate-schema V61 無漂移、validate-e2e **54 passed/0 fail**。誠實：只做全額退款（partial 另立 AI-2415）；測試不打真 Stripe（上線 checklist AI-2414）|
| AI-2414 | 真金流上線 checklist | Sprint 51 | Sprint 53 | 產出 `docs/08_deployment/STRIPE_PRODUCTION_CHECKLIST.md`：金鑰/環境變數、webhook 端點註冊事件清單、Connect Platform Profile（Phase D-1 起）、測試模式端到端人工驗證項目（付款成功/失敗/退款/Connect onboarding）、正式金鑰切換順序、已知限制揭露。**文件本身為完成交付**；文件內列出的人工驗證步驟仍需使用者於測試模式親自執行（誠實揭露，非本項範圍） |
| AI-2413 | 真實金流 Phase D-1：Stripe Connect Express 帳戶 onboarding | Sprint 49 | Sprint 53 | **PO 決策 Stripe Connect（非手動撥款）+ Express（非 Standard）**。US-001：V62 migration（tenants 加 stripe_connect_account_id/connect_onboarding_status/connect_charges_enabled/connect_payouts_enabled）；StripePaymentGateway 新增 createConnectAccount（Account.create type=express）/createAccountLink（AccountLink.create type=account_onboarding）/getConnectAccountStatus（Account.retrieve）；TenantStripeConnectService（onboarding 發起/複用既有 accountId/狀態查詢，`STRIPE_CONNECT_ENABLED` toggle 保護）；SellerDashboardController 新增 onboarding/status 端點。US-002：PaymentWebhookService 擴充 `account.updated` dispatch（反查 tenant 回填狀態，沿用 V60 去重）。後端單元 20（WireMock TC-S007~010 + UT-CONNECT-001~006 + UT-WH-007~008）+ 真 DB 整合 4（IT-CONNECT）+ 全量回歸 536 tests 0 fail、validate-schema V62 無漂移。誠實：**只做帳戶 onboarding，不做代收後 transfer 分潤**（另立 AI-2416，Phase D-2）；不含前端（賣家後台按鈕）；只做 Express（非 Standard/Custom）|
| DEF-022 | E2E 硬等待（waitForTimeout 固定 sleep） | Sprint 35 | Sprint 42 | **歷時 S35→39→42**。S39（AI-2101）已收斂登入 helper 部分；S42（US-003）完成餘下清除：5 檔（at-m11-cart-checkout、at-m15-e2e、at-m17-001/002）冗餘 `waitForTimeout` 刪除、可替換者改顯式等待（`waitForURL`/`waitForResponse`/`expect().toBeVisible()`/`toHaveClass()`）並順帶補斷言（Rule 9）；**保留** at-m10-chat STOMP SUBSCRIBE settle 例外（無 client 可觀察訊號）。**連帶根治**移除 sleep 後浮現的既有 flaky：auth helper 與 S37 共用 Header「註冊」連結碰撞（`.first()` 恆選 header 連結 + re-render 不穩定 → 改 `goto('/register')`，Playwright 快照佐證）+ 原生 alert teardown（at-m15 檔案級 dialog beforeEach + at-m17-002 dialog 處理器）。validate-e2e 46 passed/0 fail |
| AI-2407 | 定價規則選取語意修正——overlap 查詢 + 同優先級 tie-break | Sprint 46（記錄不修） | Sprint 54 | **PO 決策同優先級「後建立者優先」**。US-001：`PricingRuleRepository.findActiveRulesForDateRange` JPQL 由 containment（`validFrom<=startDate AND validTo>=endDate`）改 overlap（`validFrom<=endDate AND validTo>=startDate`），修正部分晚數 SEASONAL 等規則被整批排除的漏套問題；`isRuleApplicable` 逐日精準判斷不變，overlap 僅放寬候選前置篩選。US-002：`calculatePrice`/`getEffectivePrice` 排序 Comparator 補 `.thenComparing(createdAt, reverseOrder())` 落實 tie-break（免 migration，`PricingRule.createdAt` 既有欄位）；修正既有空斷言測試 UT-M12-009（標題聲稱驗證「後建立覆蓋」但斷言僅 `isNotNull()`）為真斷言；新增 `getEffectivePrice` tie-break 單元測試（UT-M12-019）+ 真 DB 整合測試（API-M06-017，驗證部分晚數 SEASONAL 規則於 overlap 修正後正確生效）。後端單元 508 + 真 DB 整合 415 全量回歸 0 fail、`make validate-schema` 無漂移（schema-free）。誠實：只修正查詢語意與排序次鍵，未重寫計價核心邏輯（`isRuleApplicable`/`calculateAdjustment` 均不動）|
| AI-2409 | 定價計算器統一評估 | Sprint 48（兩道閘門根因） | Sprint 55 | **Spike，不改 production code**。產出決策文件 `PRICING_CALCULATOR_UNIFICATION_ASSESSMENT.md`：確認 `MANUAL_OVERRIDE`/`SEASONAL`/`WEEKDAY_WEEKEND` 三型別自 S48 起已語意等價（僅程式碼重複）；`EARLY_BIRD`/`LONG_STAY`/`LAST_MINUTE` 對 PRODUCT 本無自然語意（無「目標日期」概念），非未對齊缺陷；發現 `applyProductRule` 對此三型別會靜默落入通用 `discountPercent` fallback、完全略過原 gating（`minDaysAhead`/`minNights`/`maxDaysAhead`），但確認後端 `validateRuleRequest` 無搭配驗證、前端 `PricingRuleList.tsx` 又僅支援 `roomListingId`（無 PRODUCT 定價規則管理 UI），故此落差目前僅能經直接 API 觸發，實際曝險低。比較 3 個合併選項（維持現狀 / 抽共用 helper 統一三對齊型別【建議，2-3 SP】/ 全面合併含 stay-based【不建議】），結論**非急迫，可視未來容量排入，不需 PO 決策**。誠實：純評估無程式碼變動，無需回歸測試 |
| AI-2415 | 部分退款（任意金額，運費不退）| Sprint 52（Phase C 只做全額退款） | Sprint 56 | **PO 決策**：(1) 部分退款粒度＝任意金額（非選品項）；(2) 運費不退。US-001：V63 migration `payments` 加 `refunded_amount`；`PaymentStatus` 加 `PARTIALLY_REFUNDED`；`PaymentStateService.refundOrderPayment` 擴充 `amount` 參數（null=剩餘全額向後相容，指定需正數且不超剩餘可退額度，逾越丟 `E_6009`）；累計 `refundedAmount`，達全額轉 `REFUNDED`+`Order.REFUNDED`，未達轉 `PARTIALLY_REFUNDED`（Order 狀態不變）；`OrderPaymentController`/`OrderPaymentStateDto` 同步擴充；抽 `resolveRefundAmount`/`executeStripeRefund` 兩輔助方法消 NPathComplexity。後端單元 512（新增 UT-PAY-STRIPE-008~011 + 更新 005/006）+ 真 DB 整合 419（`M07PaymentMockIntegrationTest` 不退步）全量回歸 0 fail、`make validate-schema` 無漂移（V63）。誠實：只修真 Stripe 退款路徑（`refundOrderPayment`），**未動平行的純 Mock 路徑**（`PaymentService.processRefund`，技術債現況記錄不修）；`stripeRefundId` 僅存最後一次退款 id，多次部分退款完整歷史需獨立子表（本次不做）；webhook（`charge.refunded`）路徑仍假設全額，未解析部分退款金額 |
| AI-2202f | 開放窗清除機制 | Sprint 47（部分更新慣例限制） | Sprint 57 | **工程設計決策（非業務）**：新增專屬清除端點，不改全域 DTO 慣例。US-001：`RoomService.clearOpenWindow` 一次性將 `openUntilDate`/`bookingWindowDays` 清回 null；`RoomController` 新增 `DELETE /v2/rooms/{listingId}/open-window`（`room:update` 權限，比照既有 `CartController.clearCart` 模式）；前端 `RoomForm.tsx` 新增「清除開放窗設定」按鈕呼叫新端點（修正清空輸入框送出不會清除的誤導性行為）。後端單元新增 `RoomServiceTest`（3 tests：清除成功/不影響其他欄位/找不到房源）+ 全量單元 515 + 真 DB 整合 422 全量回歸 0 fail、`make validate-schema` 無漂移（schema-free）、前端 build 0 error。誠實：未新增 RoomController 層級 E2E 測試（專案內 Room CRUD 端點原本就無此類測試，與既有慣例一致，非本次降低覆蓋率）|
| AI-2408 | availability reason 錯誤碼化 | Sprint 47（未開放原因為英文字串） | Sprint 58 | **工程範圍決策（非業務）**：確認前端無任何 i18n 框架、全站純中文介面，不導入完整多語系框架，改採最小方案。US-001：`BookingDto` 新增 `AvailabilityReasonCode` enum（`INVALID_DATE_RANGE`/`NOT_OPEN_FOR_BOOKING`/`BOOKED`/`BLOCKED`/`MAINTENANCE`）；`BookingService.checkAvailability` 三處 `unavailableReason` 賦值改用 code（`RoomCalendarStatus` 三態與 code 同名，直接 `.name()`）；前端 `ListingDetail.tsx` 新增 code→中文訊息對照表，查無對應 code 時原樣顯示（向後相容）。後端單元 515（修正 `BookingServiceOpenWindowTest` 舊字串斷言）+ 真 DB 整合 422（`BookingControllerE2ETest` 21 tests，更新舊斷言）全量回歸 0 fail、`make validate-schema` 無漂移（schema-free）、前端 tsc/eslint/build 0 error（`at-room-booking.spec.ts` 同步更新 mock/斷言）。誠實：只碼化 `checkAvailability` 回傳欄位，建單/改期超窗拋出的 `BusinessException(E_3002)` 例外訊息不在本次範圍（全站 BusinessException 訊息一律英文的更大範圍問題）；開發-編譯-測試循環於全量回歸階段攔截到 `BookingServiceOpenWindowTest` 遺漏更新的舊字串斷言 |
| AI-2417 | 退款路徑整合評估 | Sprint 56（AI-2415 部分退款探勘時發現） | Sprint 59 | **Spike，不改 production code**。產出決策文件 `REFUND_PATH_CONSOLIDATION_ASSESSMENT.md`：確認 `PaymentService`（Mock，涵蓋 Order+Booking）與 `PaymentStateService`（僅 Order，真 Stripe）是整個服務類別層級平行，非單一方法重複；關鍵發現這不是意外重複，而是 Sprint 49 金流真實化計畫自始只涵蓋 Order、從未觸及 Booking 的既定範圍；確認兩條退款路徑**目前皆無前端呼叫端**（僅後端測試涵蓋）；另發現 Mock 路徑的部分退款未同步 `refundedAmount`/`PARTIALLY_REFUNDED` 狀態的潛在資料不一致陷阱（無實際風險因無呼叫端）。結論**維持現狀**（選項 A）：整合/刪除任一方屬無需求驅動的臆測性變更；若未來要讓 Booking 也走真實 Stripe，屬於擴大金流範圍的新功能決策（估 15+ SP），需 PO 評估優先級，非本次技術債清理範疇 |
| AI-2418 | 全站 BusinessException 英文訊息中文化 | Sprint 58（AI-2408 探勘時發現） | Sprint 60 | **PO 決策（2026-07-04）**：完整修復——翻譯 130 個 `ErrorCode` 常數為繁體中文，動態英文細節改為僅供伺服器端 log（不再回傳前端）。探勘確認全站 383 處 `BusinessException` 呼叫、前端 90%+ 流程無中文化放行層，使用者真的會看到英文錯誤訊息（非僅 log）。US-001：`ErrorCode.java` 130 常數中文化（含 3 個帶格式化佔位符）；`BusinessException` 新增 `getUserMessage()`（僅回傳 ErrorCode 基礎訊息，不含動態細節）；`GlobalExceptionHandler` 改用 `getUserMessage()` 組成 API 回應（`getMessage()` 仍含細節供 log 用）+ 7 處硬編碼英文字串中文化；更新 4 處依賴舊英文文字的 API 層級測試斷言（其餘 19 處檢查 details 的 `.getMessage()` 斷言不受影響）。後端單元 422 + 真 DB 整合（含 failsafe `*IntegrationTest.java`/`*E2ETest.java`）**全量 890 tests 0 fail**、`make validate-schema` 無漂移（schema-free）。誠實：不修改 383 個呼叫點中 320 處的動態英文 details 字面值本身（僅不回傳前端）；若未來需在使用者訊息保留具體細節需另立結構化錯誤欄位方案，非本次範圍 |
| DEF-023 | Booking 付款/預訂擁有權檢查缺口（IDOR，安全） | Sprint 67（撰寫 PaymentStateService 測試時發現） | Sprint 68 | 🔴 **緊急安全修復 Sprint（使用者明確授權插隊）**。US-001：比照 Order 側 `DEF-018/019` 既有模式，五處補齊擁有權檢查——`PaymentStateService.getBookingPaymentState`（新增 `checkBookingOwnership`）、`BookingService.getBooking`/`updateBooking`/`cancelBooking`（共用同一 `checkBookingOwnership` helper）、`PaymentService.processBookingPayment`（新增 `checkBookingPaymentOwnership`）：買家限本人（`booking.getUserId()`），`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 放行，越權回 403/`E_1007`，檢查置於狀態檢查之前避免洩漏資源狀態。US-002：`RolePermissionMapping.java` 移除 `GUEST` 角色的 `Permission.BOOKING_READ`（純 Java 常數變更，**無 migration**，已確認無對應 DB 權限表）。**測試**：`PaymentStateServiceTest` 新增 2 個（非本人 403 / admin 放行）、`BookingServiceOwnershipTest`（新檔，10 個：getBooking/updateBooking/cancelBooking 各 3 + cancelBooking 額外 1 個狀態機錯誤路徑）、`PaymentServiceOwnershipTest` 擴充 3 個（processBookingPayment）、新增 `RolePermissionMappingTest`（4 個：GUEST 無 booking:read、GUEST 保留 product/room:read、BUYER/ADMIN 不受影響）。**追加修復**（Sprint 68 收尾並 push 後，使用者看到誠實揭露的殘留問題後決定立即追加）：`BookingService.cancelBooking` 原本同樣完全沒有擁有權檢查（任何登入使用者可取消他人訂房），已沿用同一個 `checkBookingOwnership` helper 補上，不重複造新方法。全量回歸 `mvn verify -Pintegration-test` 0 fail；`make validate-schema` 無漂移。**殘留待決策（未變更行為）**：`HOST` 角色持有 `booking:update` 權限，修復後若非買家本人將收到 403，若日後需房源方管理自己房源訂房的合法更新流程，需另評估租戶側擁有權判斷（比照 `DEF-019` 收尾時 `LogisticsService` 的租戶側檢查） |
| DEF-024 | OrderService.updateOrderStatus 無擁有權/租戶檢查（IDOR，安全） | Sprint 69（撰寫 OrderService 測試時發現） | Sprint 70 | 🔴 **緊急安全修復 Sprint（使用者明確授權插隊，比照 Sprint 68 DEF-023 模式）**。與 DEF-023 不同，本方法有兩種正當呼叫情境並存（買家自助付款 + 賣家管理自己租戶訂單），不可直接套用單純的 owner-or-admin 模式，動手前先完成業務情境分析並取得使用者確認。US-001：新增 `checkOrderStatusUpdateAuthorization` helper，採「訂單擁有者本人（比照 `getOrder`/`cancelOrder`/`getOrderStateLogs` 的 `DEF-018` 模式，對應買家透過付款流程觸發 CREATED→PAID）or 本租戶（比照 `LogisticsService.checkOrderTenant` 的 `DEF-019` 模式，對應賣家/店主透過 `PATCH /v2/orders/{orderId}/status` 管理自己租戶訂單，如標記出貨）or admin（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN`）」三者其一放行，越權回 403/`E_1007`，置於 `OrderStateMachine.canTransition` 狀態機檢查之前避免洩漏訂單狀態。**測試**：`OrderServiceTest` 新增 3 個（他租戶賣家 403 且先於狀態機檢查觸發、本租戶賣家放行續走狀態機轉換、admin 跨租戶放行），既有買家付款流程回歸測試（`updateOrderStatus_validTransition`/`updateOrderStatus_invalidTransition_throwsE5001`）不受影響。全量回歸 `mvn verify -Pintegration-test` 606 + 342 = 948 tests 0 fail；`make validate-schema` 無漂移（本次無 entity/migration 變更） |


---

## Sprint 歷史紀錄

### Sprint 71 (2026-07-06)

**主題**: 多 Sprint 測試強化計劃（恢復例行排程）——`BookingService` + `RoomCalendarService` 訂房核心單元測試強化（1 US / 8 SP）

**完成**:
- **US-001 → ✅ 完成**：`RoomCalendarService`（先前零單元測試覆蓋）新建 `RoomCalendarServiceTest.java`（17 個測試），聚焦 `bookDateRange` 的 idempotency 核心邏輯（同 bookingId 重複呼叫跳過、不同 bookingId 衝突擋 E_4001、unique constraint/悲觀鎖並發衝突）、`isDateRangeAvailable`、`releaseDateRange`/`blockDateRange`/`unblockDateRange`、`lockDateRangeNoWait`/`unlockDateRange`（含部分取鎖失敗回滾已取得鎖）。`BookingService.createBooking`（先前僅由 E2E/整合測試間接涵蓋）新建 `BookingServiceCreateBookingTest.java`（12 個測試），涵蓋正常路徑 + 前置驗證錯誤路徑 + 鎖定/並發控制（含 finally 區塊無論成功失敗皆釋放鎖的健壯性）。`BookingService.updateBooking` 日期變更流程新建 `BookingServiceUpdateDateChangeTest.java`（5 個測試）。三檔合計新增 **34 個單元測試**，與 Sprint 68 `BookingServiceOwnershipTest`（10 個，涵蓋 getBooking/updateBooking/cancelBooking 擁有權檢查）互補、不重複。
- **`DEF-025`（🟢 低優先級技術債，已記錄）**：撰寫 `createBooking` 測試時發現 `idempotencyKey` 參數為死碼（真正 idempotency 由 Controller 層 `IdempotencyService` 處理），使用者審閱後決定不清理，僅記錄備查。

**驗證**:
- 本 Sprint **僅新增測試檔案，未修改任何生產程式碼**，依既定政策（純補測試 Sprint）僅需 `mvn test`（不需全量 `mvn verify -Pintegration-test`）：**640 tests，0 fail**（含本 Sprint新增 34 個）。
- `make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）。

**下一 Sprint 候選**:
- ERP 模組整體測試（Supplier/StockMovement/PurchaseOrder/Inventory，測試目錄完全不存在，Sprint 72+）。

---

### Sprint 70 (2026-07-05)

**主題**: 🔴 緊急安全修復——`OrderService.updateOrderStatus` 跨租戶 IDOR（`DEF-024`，3 SP，US-001 完成）

**完成**:
- **`DEF-024` → ✅ 完成（US-001）**：詳見「已完成延後項目」表格。動手前先完成業務情境分析（`updateOrderStatus` 有買家自助付款＋賣家管理自己租戶訂單兩種正當呼叫情境並存，與 Booking 側單純 owner-or-admin 案例不同），取得使用者確認後，合成既有兩個修復前例（`DEF-018` 買家 owner-or-admin 模式 + `DEF-019` `LogisticsService.checkOrderTenant` 的賣家 tenant-based 模式）為「owner OR same-tenant OR admin」三選一放行邏輯，補上 `OrderService.updateOrderStatus` 完全缺失的擁有權/租戶檢查。

**驗證**:
- 後端單元 + 真 DB 整合（`mvn verify -Pintegration-test`，含 failsafe）**606 + 342 = 948 tests，0 fail**（含本 Sprint 新增 `OrderServiceTest` 3 個擁有權/租戶案例）。
- `make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）。

**下一 Sprint 候選**:
- 安全缺口已清零，恢復例行測試強化排程：`BookingService`/`RoomCalendarService` 其餘業務邏輯方法測試強化（Sprint 71）；ERP 模組整體測試（Sprint 72+）。

**里程碑**：**Order 模組跨租戶 IDOR 缺口清零**——與 `DEF-018/019`（Order 側既有修復）、`DEF-023`（Booking 側，Sprint 68）合計完成 Order+Booking 雙模組付款/資源/租戶擁有權隔離；活躍高優先級延後項目回到 0。

---

### Sprint 69 (2026-07-05)

**主題**: OrderService（訂單狀態機核心）單元測試從 0 建立（8 SP，US-001 完成）

**完成**:
- **US-001 → ✅ 完成**：新增 `OrderServiceTest.java`（42 個測試），涵蓋 `OrderService` 7 個 public 方法（`createOrderFromCart`【PRODUCT+ROOM 兩分支】、`createBooking`、`getUserOrders`、`getOrder`、`updateOrderStatus`、`cancelOrder`、`getOrderStateLogs`）的正常/邊界/錯誤路徑。先前該 Service 完全沒有 Mockito 單元測試（僅有間接涵蓋部分流程的 Controller 層 E2E/整合測試 + 不觸及 Service 本身的 `OrderStateMachineTest`）。

**新增延後項目**:
- **DEF-024（🔴 高優先級，✅ 已決策）**：撰寫測試時發現 `updateOrderStatus` 完全沒有訂單擁有權/租戶檢查（同檔案 `getOrder`/`cancelOrder`/`getOrderStateLogs` 皆有），而持有 `order:update` 權限的 SELLER/STORE_OWNER 角色分散於各租戶，形同跨租戶 IDOR，性質與 `DEF-018/019/023` 系列相同。本 Sprint 依範圍僅記錄不修改，未撰寫「證明漏洞存在」的測試。使用者收尾時看到本發現，**決定比照 Sprint 68（DEF-023）模式另立 Sprint 70 緊急修復**。

**驗證**:
- 後端單元 + 真 DB 整合（`mvn verify -Pintegration-test`，含 failsafe）**603 + 342 = 945 tests，0 fail**。
- `make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）。
- 誠實揭露：`Order.tenantId` 為 insertable=false/updatable=false 影子欄位，純 mock 測試無法透過 Response DTO 觀察租戶回退結果，改以 `ArgumentCaptor<Order>` 驗證實際傳入 repository 的 `Order.getTenant()`（比照既有「erp-tenant-test-seeding-gotcha」教訓，本次為單元測試層級的等價陷阱，已在測試註解中記錄）。

**下一 Sprint 候選**:
- 🔴 **Sprint 70（已決策，緊急插隊）**：DEF-024（跨租戶 IDOR 修復）；其後恢復例行排程：`BookingService`/`RoomCalendarService` 其餘業務邏輯方法測試強化（Sprint 71）；ERP 模組整體測試（Sprint 72+）。

**里程碑**：**Order 模組單元測試缺口清零**——與 `PaymentStateService`（Sprint 67）、`Booking` 擁有權隔離（Sprint 68）合計完成金流/訂單/訂房三大核心模組的測試強化第一輪；活躍高優先級延後項目新增 DEF-024，已決策排入 Sprint 70 緊急修復。

---

### Sprint 68 (2026-07-05)

**主題**: 🔴 緊急安全修復——Booking 付款/預訂擁有權檢查缺口（IDOR，DEF-023，6 SP，US-001+US-002 完成）

**完成**:
- **DEF-023 → ✅ 完成（US-001+US-002）**：詳見「已完成延後項目」表格。比照 Order 側 `DEF-018/019` 既有修復模式，補齊 `PaymentStateService.getBookingPaymentState`、`BookingService.getBooking`/`updateBooking`/`cancelBooking`、`PaymentService.processBookingPayment` 五處擁有權檢查；`RolePermissionMapping.java` 移除 `GUEST` 角色的 `BOOKING_READ` 權限。
- **追加修復（收尾並 push 後）**：Sprint 68 收尾時誠實揭露 `BookingService.cancelBooking` 同樣缺擁有權檢查但當時不在授權範圍，使用者看到後決定立即追加授權修復；沿用同一個 `checkBookingOwnership` helper 補上，不重複造新方法，殘留事項清單同步收斂為僅剩 `HOST` 角色權限落差一項。

**驗證**:
- 後端單元 + 真 DB 整合（`mvn verify -Pintegration-test`，含 failsafe）**0 fail**（含 `BookingServiceOwnershipTest` 10 個【getBooking/updateBooking/cancelBooking 各 3 + 1 個狀態機錯誤路徑】、`RolePermissionMappingTest` 4 個、`PaymentStateServiceTest`/`PaymentServiceOwnershipTest` 各擴充 2/3 個）；`make validate-schema` 無漂移（本 Sprint 無 entity/migration 變更）。
- 誠實揭露（收斂後僅剩一項）：`HOST` 角色 `booking:update` 權限修復後若非買家本人將 403，日後若需房源方管理流程需另評估租戶側檢查。

**下一 Sprint 候選**:
- 安全缺口已清零，恢復例行測試強化排程：`OrderService`（7 方法，訂單狀態機核心，`SPRINT_67_PLAN.md` 第 6 節原建議之 Sprint 68 順延至此）。

**里程碑**：**Booking 模組 IDOR 缺口清零**——與 `DEF-018/019`（Order 側）合計完成 Order+Booking 雙模組付款/資源擁有權隔離；活躍高優先級延後項目回到 0。

---

### Sprint 60 (2026-07-04)

**主題**: 全站 BusinessException 英文訊息中文化（8 SP，US-001 完成）

**完成**:
- **AI-2418 → ✅ 完成（US-001）**：詳見「已完成延後項目」表格。PO 決策完整修復後，`ErrorCode` 130 常數中文化 + `BusinessException.getUserMessage()` + `GlobalExceptionHandler` 改用，全站錯誤訊息不再對使用者顯示英文字面值。

**驗證**:
- 後端單元 422 + 真 DB 整合（`mvn verify -Pintegration-test`，含 failsafe）**全量 890 tests 0 fail**；`make validate-schema` 無漂移（schema-free）。catch(Exception)/@Deprecated=0。
- 誠實：只改 `ErrorCode` 基礎訊息 + 隱藏動態 details，未逐一改寫 383 個呼叫點的英文細節字面值本身（僅不外洩前端）；webhook/log 場景仍可見完整英文細節（伺服器端debug用途，非使用者可見）。

**下一 Sprint 候選**:
- 活躍延後項目已收斂至僅剩需外部環境/正式上線的項目：AI-2416（P3，需 Phase D-1 正式上線）、AI-1903（需 live 環境）。**目前無其他可自主執行的技術債項目**。

**里程碑**：**全站錯誤訊息語言一致性達成**——與 AI-2408（availability reason）合計完成使用者可見錯誤訊息的中文化。活躍延後：AI-2416（P3，需環境）+ AI-1903（需環境）；活躍 DEF=0；**可自主執行的延後項目已再次清空**。

---

### Sprint 59 (2026-07-04)

**主題**: 退款路徑整合評估（3 SP，US-001 完成，Spike）

**完成**:
- **AI-2417 → ✅ 完成（US-001，Spike）**：詳見「已完成延後項目」表格。產出 `docs/06_quality/REFUND_PATH_CONSOLIDATION_ASSESSMENT.md`，結論維持現狀，不改 production code。

**驗證**:
- 純文件產出，不動 code；catch(Exception)/@Deprecated=0；schema-free。
- 誠實：spike 型（決策文件非可運行功能，SP 偏輕）；發現的「Booking 從未有真實 Stripe」現況為既有事實而非新缺陷，不代表需要立即行動。

**下一 Sprint 候選**:
- AI-2418（全站 BusinessException 英文訊息碼化評估，P4）、AI-2416（P3，需 Phase D-1 正式上線）、AI-1903（需 live 環境）。

**里程碑**：**退款路徑重疊已釐清為既定範圍而非技術債意外**。活躍延後：AI-2418（P4）/ AI-2416（P3，需環境）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 58 (2026-07-04)

**主題**: Availability reason 錯誤碼化（2 SP，US-001 完成）

**完成**:
- **AI-2408 → ✅ 完成（US-001）**：詳見「已完成延後項目」表格。`checkAvailability` 回傳結構化 reason code 取代英文字串，前端對照表顯示中文。

**驗證**:
- 後端單元 515 tests 0 fail + 真 DB 整合 422 tests 0 fail；`make validate-schema` 無漂移（schema-free）；前端 tsc/eslint/build 0 error。catch(Exception)/@Deprecated=0。
- 誠實：只碼化 availability 欄位，未擴及全站 BusinessException 英文訊息（更大範圍問題）；範圍決策（不導入 i18n 框架）為工程判斷，未徵詢 PO；開發-編譯-測試循環於全量回歸時攔截到 `BookingServiceOpenWindowTest` 一處遺漏更新的舊字串斷言，當場修正未累積到下個 Sprint。

**下一 Sprint 候選**:
- 活躍延後項目已收斂至僅剩需外部環境/正式上線的項目：AI-2416（P3，需 Phase D-1 正式上線）、AI-1903（需 live 環境）；另有未立案的純 Mock 退款路徑整合評估（P4，技術債）。**目前無其他可自主執行的技術債項目**。

**里程碑**：**M06 訂房可用性 UX 細節收尾**——reason 碼化完成，中文顯示一致性達成。活躍延後：AI-2416（P3，需環境）+ AI-1903（需環境）；活躍 DEF=0；**可自主執行的延後項目已清空**。

---

### Sprint 57 (2026-07-04)

**主題**: 開放窗清除機制（2 SP，US-001 完成）

**完成**:
- **AI-2202f → ✅ 完成（US-001）**：詳見「已完成延後項目」表格。新增 `DELETE /v2/rooms/{listingId}/open-window` 端點 + `RoomForm.tsx` 清除按鈕，讓賣家可將開放窗欄位清回無限制。

**驗證**:
- 後端單元 515 tests 0 fail（新增 `RoomServiceTest` 3 tests）+ 真 DB 整合 422 tests 0 fail；`make validate-schema` 無漂移（schema-free）；前端 `npm run build` 0 error。catch(Exception)/@Deprecated=0。
- 誠實：機制選擇（專屬端點 vs DTO wrapper 型別）為純工程設計決策，依既有「非 null 才更新」全域慣例判斷新增端點風險最低，未徵詢 PO（非業務語意事項）；未新增 RoomController 層級 E2E 測試，因專案內 Room CRUD 端點本就無此類既有測試（非本次降低覆蓋率）。

**下一 Sprint 候選**:
- AI-2408 availability reason 錯誤碼化 + i18n（P4）、AI-2416 真實金流 Phase D-2 分潤（P3，需 Phase D-1 上線，目前不可執行）、（未立案）純 Mock 退款路徑與真 Stripe 路徑整合評估（P4）、AI-1903 真人 live 走查（需環境，目前不可執行）。

**里程碑**：**開放窗語意缺口收尾**（AI-2202e 建立語意 + AI-2202f 補上清除機制，M06 開放窗功能完整）。活躍延後：AI-2416（P3）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 56 (2026-07-04)

**主題**: 部分退款——任意金額，運費不退（5 SP，US-001 完成）

**完成**:
- **AI-2415 → ✅ 完成（US-001）**：詳見「已完成延後項目」表格。PO 決策（任意金額粒度 + 運費不退）後，`PaymentStateService.refundOrderPayment` 支援部分退款，V63 migration + `PARTIALLY_REFUNDED` 狀態。

**驗證**:
- 後端單元 512 tests 0 fail（含新增 UT-PAY-STRIPE-008~011 + 更新 005/006）+ 真 DB 整合 419 tests 0 fail（`M07PaymentMockIntegrationTest` 8 tests 不退步）；`make validate-schema` 無漂移（V63）。catch(Exception)/@Deprecated=0。
- 誠實：只修真 Stripe 退款路徑，未動平行的純 Mock 退款路徑（技術債，記錄不修）；`stripeRefundId` 僅存最後一次退款 id；webhook 路徑仍假設全額退款。

**下一 Sprint 候選**:
- AI-2202f 開放窗清除機制（P4）、AI-2408 availability reason 錯誤碼化 + i18n（P4）、AI-2416 真實金流 Phase D-2 分潤（P3，需 Phase D-1 上線，目前不可執行）、AI-1903 真人 live 走查（需環境，目前不可執行）、（技術債，未立案）純 Mock 退款路徑與真 Stripe 路徑整合評估。

**里程碑**：**退款機制擴充支援部分金額**——與全額退款（Phase C）並存，PO 決策的兩項業務語意（任意金額粒度、運費不退）已落地。活躍延後：AI-2416（P3）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 55 (2026-07-04)

**主題**: 定價計算器統一評估——PRODUCT/ROOM 兩套計算器分歧探勘（3 SP，US-001 完成，Spike）

**完成**:
- **AI-2409 → ✅ 完成（US-001，Spike）**：詳見「已完成延後項目」表格。產出 `docs/06_quality/PRICING_CALCULATOR_UNIFICATION_ASSESSMENT.md`，結論：三個已對齊型別（MANUAL_OVERRIDE/SEASONAL/WEEKDAY_WEEKEND）僅程式碼重複、無語意問題；三個 stay-based 型別（EARLY_BIRD/LONG_STAY/LAST_MINUTE）對 PRODUCT 無自然語意；發現的靜默 gating 略過落差因前端無 PRODUCT 定價規則管理 UI 而實際曝險低。**不改 production code**。

**驗證**:
- 純文件產出，不動 code；既有測試狀態沿用 S54（後端單元 508 + 整合 415，0 fail）；catch(Exception)/@Deprecated=0；schema-free。
- 誠實：spike 型（決策文件非可運行功能，SP 偏輕）；建議選項（抽共用 helper）非急迫，不強制排入後續 Sprint。

**下一 Sprint 候選**:
- AI-2415 部分退款評估（P4）、AI-2202f 開放窗清除機制（P4）、AI-2408 availability reason 錯誤碼化 + i18n（P4）、AI-2416 真實金流 Phase D-2 分潤（P3，需 Phase D-1 上線）、AI-1903 真人 live 走查（需環境）。

**里程碑**：**定價計算器分歧已釐清**——確認現況為合理分歧非缺陷，無急迫技術債。活躍延後：AI-2415（P4）/ AI-2416（P3）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 54 (2026-07-04)

**主題**: 定價規則選取語意修正——Range 查詢 overlap 化 + 同優先級 tie-break 落實（6 SP，US-001~002 全數完成 + US-003 文件更新）

**完成**:
- **AI-2407 → ✅ 完成（US-001+US-002）**：詳見「已完成延後項目」表格。PO 決策同優先級 tie-break 業務語意為「後建立者優先」；range 查詢 containment→overlap 修正部分晚數規則漏套問題；排序 Comparator 補 createdAt 次鍵；修正既有空斷言測試 UT-M12-009。
- **US-003 → ✅ 完成**：本文件 AI-2407 狀態更新（活躍延後移至已完成）。

**驗證**:
- 後端單元 508 tests 0 fail（含 UT-M12-009 修正 + UT-M12-019 新增）+ 真 DB 整合 415 tests 0 fail（含 API-M06-017 新增，`BookingControllerE2ETest` 21 tests 0 fail）；`make validate-schema` 無漂移（schema-free，無 migration）。catch(Exception)/@Deprecated=0。
- 誠實：只修正查詢語意（候選規則前置篩選）與排序次鍵（tie-break），`isRuleApplicable`/`calculateAdjustment` 逐日精準計算核心不動；本 Sprint 無 migration、無前端變動。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S54（本 Sprint 決策先規劃後 push，收尾時徵詢）、AI-2416 真實金流 Phase D-2 分潤（需 Phase D-1 上線）、AI-2409 定價計算器統一評估、AI-2202f 開放窗清除、AI-2408 reason i18n、AI-1903 真人 live 走查（需環境）。

**里程碑**：**定價規則選取語意缺口收斂**——range 查詢語意正確化 + 同優先級行為明確化（不再依賴資料庫未定義回傳順序）。活躍延後：AI-2409（P4）/ AI-2415（P4）/ AI-2416（P3）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 53 (2026-07-04)

**主題**: 真實金流 Phase D-1——Stripe Connect Express 帳戶 onboarding（8 SP，US-001~002 全數完成 + US-003/US-004 不計點文件產出）

**完成**:
- **AI-2413 → ✅ Phase D-1 完成（US-001+US-002，後端）**：Connect Express 帳戶 onboarding。V62 migration（tenants 加 4 個 Connect 欄位）；StripePaymentGateway 新增 createConnectAccount/createAccountLink/getConnectAccountStatus；TenantStripeConnectService（onboarding 發起/複用/狀態查詢，toggle 保護）；SellerDashboardController onboarding/status 端點；PaymentWebhookService 擴充 account.updated dispatch。
- **AI-2414 → ✅ 完成（US-003）**：`STRIPE_PRODUCTION_CHECKLIST.md` 產出（金鑰/webhook/Connect/測試模式端到端驗證項目/正式金鑰切換順序）。
- **AI-1903 → 🟡 部分（US-004，同 Sprint 41 模式）**：更新既有 `BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md`（新增真 Stripe toggle 走查項 + 連結上線 checklist）；真人 live 走查本身仍續留待使用者執行。

**驗證**:
- 後端單元 20（StripePaymentGateway +4 含 TC-S007~010、TenantStripeConnectService 6 含 UT-CONNECT-001~006、PaymentWebhookService +2 含 UT-WH-007~008）+ 真 DB 整合 4（M13SellerStripeConnectIntegrationTest，IT-CONNECT-001~004）+ **全量回歸 536 tests 0 fail**（含 M16 ERP 37，因 TestDatabaseInitializer/M16ErpIntegrationTest 兩處 raw SQL tenant 種子需同步補新增 NOT NULL 欄位，屬既有「erp-tenant-test-seeding-gotcha」模式再現，已修正）；`make validate-schema` 無漂移（V62）。catch(Exception)/@Deprecated=0。
- 誠實：只做帳戶 onboarding，**不做代收後 transfer 分潤**（另立 AI-2416，Phase D-2，Sprint 55）；不含前端（賣家後台按鈕串接視後續容量）；只做 Express（非 Standard/Custom）；正式上線需人工確認 Stripe Connect Platform Profile 已完成平台資料送審（非本 Sprint 可代查）。
- **範圍決策**（PO 授權 Claude Code 逕行決策，2026-07-04）：原規劃 US-001~004 共 10 SP 高於歷史區間，下修為正式承諾 8 SP（US-001+US-002），US-003/US-004 降為不計點附屬產出；AI-2407 定價規則語意評估延後至 Sprint 54（前置調查發現規模達 5-8 SP 且需 tie-break 業務語意決策，非小型修正）。

**新增延後項目**:
- **AI-2416（P3）**：真實金流 Phase D-2，代收後 transfer 分潤/提現（需 Phase D-1 帳戶已上線）。

**下一 Sprint 候選**:
- AI-2407 定價規則選取語意評估（range 查詢 containment→overlap 修正 + 同優先級 tie-break 業務語意決策，Sprint 54 主軸）、AI-2416 Phase D-2 分帳、AI-1903 真人 live 走查（需環境）。

**里程碑**：**真實金流閉環擴展至賣家 onboarding**——付款（Phase A）+ 權威狀態（Phase B）+ 退款（Phase C）+ 賣家 Connect 帳戶開通（Phase D-1）皆真實。剩代收後分潤（Phase D-2）。活躍延後：AI-2407（P3）/ AI-2409（P4）/ AI-2416（P3）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 52 (2026-07-03)

**主題**: 真實金流 Phase C——退款真串接（Stripe Refund）（5 SP，US-001~002 全數完成）

**完成**:
- **AI-2412 → ✅ 完成（US-001+US-002，後端）**：退款由 stub/mock 改真實。StripePaymentGateway.processRefund 真 Refund.create（payment_intent 全額退款）；PaymentStateService refundOrderPayment toggle-aware（stripe 真退款+存 stripe_refund_id / mock 保留）；PaymentWebhookService charge.refunded 權威 REFUNDED；createCheckoutSession 補 pi metadata order_id；V61 stripe_refund_id。

**驗證**:
- 後端單元 19（StripePaymentGateway 6 含 TC-S006 + PaymentStateServiceStripe 7 含退款 005~007 + PaymentWebhookService 6 含 UT-WH-006）+ 真 DB 整合 21（mock/Phase A/B 不退步）全過；mvn 0 error、checkstyle 綠；`make validate-schema` 無漂移（V61）；`make validate-e2e` **54 passed / 6 skipped / 0 failed**（持平，後端聚焦）。catch(Exception)/@Deprecated=0。
- 誠實：只做全額退款（partial 另立 AI-2415）；測試以 WireMock（真退款端到端於測試模式人工驗證，AI-2414）；餘 confirmPayment/getPaymentStatus stub（Checkout 未用）；⚠️ V61 schema。

**新增延後項目**:
- **AI-2415（P4）**：部分退款（partially_refunded）評估。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S52、AI-2414 真金流上線 checklist、AI-2413 Phase D 分帳、AI-2407 定價語意、AI-1903 真人 live 走查（需環境）。

**里程碑**：**真實金流付款閉環完整**——付款（Phase A）+ 權威狀態（Phase B）+ 退款（Phase C）皆真實。剩分帳（Phase D）+ 上線 checklist。活躍延後：AI-2413（P3）/ AI-2414（P2 上線前）/ AI-2415（P4）/ AI-2407（P3）/ AI-2409（P4）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 51 (2026-07-03)

**主題**: 真實金流 Phase B——webhook 事件驅動的權威付款狀態（5 SP，US-001~002 全數完成）

**完成**:
- **AI-2411 → ✅ 完成（US-001+US-002，後端）**：補 Phase A「買家未回跳」缺口。PaymentWebhookService 解析 Stripe 事件（checkout.session.completed[paid]→SUCCESS+Order PAID 權威、payment_intent.payment_failed→FAILED）；StripeWebhookController 驗簽後委派一律回 2xx；抽 markStripePaymentSucceeded/Failed 共用核心（回跳與 webhook 雙路徑一致）；V60 processed_stripe_events 去重 + 雙層冪等。

**驗證**:
- 後端單元 9（PaymentWebhookServiceTest 5 UT-WH-001~005 + PaymentStateServiceStripe 4）+ 真 DB 整合 21（mock/Phase A 不退步 + Spring context 載入新 bean）全過；mvn 0 error、checkstyle 綠；`make validate-schema` 無漂移（V60）；`make validate-e2e` **54 passed / 6 skipped / 0 failed**（持平，後端聚焦無新前端 E2E）。catch(Exception)/@Deprecated=0。
- 誠實：只做付款成功/失敗（退款留 Phase C/AI-2412）；失敗路徑 best-effort（Checkout 惰性建 pi）；測試模式 secret 空跳驗簽，生產須配 STRIPE_WEBHOOK_SECRET（上線 checklist AI-2414）；⚠️ V60 schema。

**新增延後項目**:
- **AI-2414（P2，上線前）**：真金流上線 checklist（webhook secret/端點、Stripe 測試模式端到端人工驗證、真金鑰）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S51、AI-2412 Phase C 退款、AI-2414 上線 checklist、AI-2413 Phase D 分帳、AI-2407 定價語意、AI-1903 真人 live 走查（需環境）。

**里程碑**：**真實金流付款閉環具上線基礎**——Phase A 卡片付款 + Phase B webhook 權威狀態。活躍延後：AI-2412（P3）/ AI-2413（P3）/ AI-2414（P2 上線前）/ AI-2407（P3）/ AI-2409（P4）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 50 (2026-07-03)

**主題**: 真實金流 Phase A——卡片付款 MVP（Stripe Checkout hosted，平台代收）（8 SP，US-001~002 全數完成）

**完成**:
- **AI-2410 → ✅ 完成（US-001 後端 + US-002 前端）**：承 S49 評估分階段路線 + PO 拍板（Checkout hosted + 先平台代收）。V59 payments 加 Stripe 欄位；接回孤兒 gateway（createCheckoutSession/retrieve）；STRIPE_PAYMENT_ENABLED toggle（預設關=mock）；initiateStripeCheckout + confirmStripeCheckout（回跳 retrieve，冪等）；端點 /pay/checkout + /return；前端 orders/[id] 重導 + success/cancel + E2E-M11-013。真實卡片收款閉環（灰度）。

**驗證**:
- 後端單元 9（StripePaymentGateway 5 含 WireMock TC-S004/005 + PaymentStateService 4）+ 真 DB 整合 25（mock 路徑不退步 + Spring context 載入新依賴）全過；mvn 0 error、checkstyle 綠；`make validate-schema` 無漂移（V59）；`make validate-e2e` **54 passed / 6 skipped / 0 failed**（+1 E2E-M11-013）。catch(Exception)/@Deprecated=0。
- 誠實：Phase A 僅回跳 retrieve（買家未回跳狀態滯後，webhook 權威狀態留 Phase B/AI-2411，上線前必要）；平台代收、分帳/提現留 Phase D；confirm/refund/getStatus 仍為既有 stub（退款留 Phase C）；測試以 WireMock + 前端 mock（不打真 Stripe）；⚠️ V59 打破 schema-free。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S50、**AI-2411 Phase B webhook 驅動狀態（P2，上線前必要，建議緊接）**、AI-2412 Phase C 退款、AI-2413 Phase D 分帳、AI-2407 定價規則語意、AI-1903 真人 live 走查（需環境）。

**里程碑**：**真實金流首個可運行階段落地**——Stripe Checkout 卡片付款閉環（toggle 灰度）。活躍延後：AI-2411（P2）/ AI-2412（P3）/ AI-2413（P3）/ AI-2407（P3）/ AI-2409（P4）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 49 (2026-07-03)

**主題**: 真實金流（Stripe）上線評估——決策先行 spike（backlog #10）（5 SP，US-001~002 全數完成）

**完成**:
- **backlog #10 評估 → ✅ 完成（US-001+US-002，spike）**：產出 [PAYMENT_INTEGRATION_ASSESSMENT.md](../07_design/PAYMENT_INTEGRATION_ASSESSMENT.md)——揭穿「Stripe 已整合」假象（兩套並行付款程式碼：上線純 Mock + 孤兒 Gateway 抽象層無人注入，S14/S21 遺留死碼）；real/stub/missing 速查表；分階段路線（Phase A 卡片 MVP→B webhook→C 退款→D 分帳）；§mock↔real toggle + §Connect vs 手動分帳 + §Stripe.js 選型 + §待 PO 決策 6 項。**不改 production code、無 schema 變更**。

**驗證**:
- 純文件、不動 code；既有測試狀態沿用 S48（後端單元 22 + 整合 54 + validate-e2e 53 passed/0 fail）；catch(Exception)/@Deprecated=0；schema-free（V58）。
- 誠實：spike 型（決策文件非可運行功能，SP 偏輕）；真實金流實作（13 SP + 外部依賴）分 AI-2410~2413 另立；Stripe scaffolding 為 S14/S21 遺留孤兒死碼。

**新增延後項目**:
- **AI-2410（P3）**：真實金流 Phase A 卡片付款 MVP（需 PO 決策 + Stripe 帳號）。
- **AI-2411（P3）**：Phase B webhook 驅動狀態。
- **AI-2412（P3）**：Phase C 退款真串接。
- **AI-2413（P3）**：Phase D 分帳/提現（需 PO 決策 Connect vs 手動）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S49、**金流 §待 PO 決策 6 項 → 決策後啟動 AI-2410 卡片付款 MVP**、AI-2407 定價規則語意、AI-1903 真人 live 走查（需環境），或其他新功能。

**里程碑**：**真實金流評估完成**——backlog #10 拆為 Phase A~D + 6 項 PO 決策 + 4 實作 US（AI-2410~2413）。活躍延後：AI-2407（P3）/ AI-2409（P4）/ AI-2202f（P4）/ AI-2408（P4）/ AI-2410~2413（P3，金流實作）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 48 (2026-07-03)

**主題**: PRODUCT/cart 漲價——M12 進階定價 PRODUCT 側收官（8 SP，US-001~002 全數完成）

**完成**:
- **AI-2406c → ✅ 完成（US-001 後端 + US-002 前端）**：承 S46 界線（PRODUCT 另立）。破**兩道閘門**——閘門 2（結構性）applyProductRule 由 discount-only 擴充支援漲價型（MANUAL_OVERRIDE/SEASONAL/WEEKDAY_WEEKEND，對齊 ROOM config key，保留 discountPercent 向後相容）+ 閘門 1 RedisCartService 折扣閘門放寬含漲價；CartItemResponse priceAdjustmentType + 有號 discountAmount；下單自動繼承。前端 cart/page 首次顯示 item 雙向定價（折扣刪除線+綠標/漲價不刪除線+橙標）+ E2E-M11-012。

**驗證**:
- 後端單元 22（PricingService 18 含 ProductEffectivePriceTests 4 + Cart 4）+ 真 DB 整合 54（含 IT-EP-004 漲價 + cart/order/PRODUCT 折扣不退步）全過；mvn 0 error、checkstyle 綠；`make validate-e2e` **53 passed / 6 skipped / 0 failed**（+1 E2E-M11-012）+ schema 無漂移。catch(Exception)/@Deprecated=0。**schema-free**（V58）。
- 誠實：SP 初估 3→探勘修正 8（兩道閘門，PRODUCT 計價核心結構性是 discount-only）；checkout 不 itemize PRODUCT 未改；PRODUCT/ROOM 兩套計算器對齊 config key 未合併；向後相容保住 S44 折扣。

**新增延後項目**:
- **AI-2409（P4）**：定價計算器統一評估（PRODUCT/ROOM 兩套計算器分歧）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S48、AI-2407 定價規則選取語意、AI-2409 計算器統一、AI-2202f 開放窗清除、AI-2408 reason i18n、AI-1903 真人 live 走查（需環境）、真實金流評估（backlog #10），或回歸新功能。

**里程碑**：**M12 進階定價全面收官**——ROOM+PRODUCT 折扣+漲價皆「顯示與收費一致」。活躍延後：AI-2407（P3）/ AI-2409（P4）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 47 (2026-07-03)

**主題**: 開放窗語意實作——區分「未開放 vs 可訂」（7 SP，US-001~002 全數完成）

**完成**:
- **AI-2202e → ✅ 完成（US-001 後端 + US-002 前端）**：承 S45 決策文件與 PO 拍板選項 A + 追加滾動視窗 + host UI。V58 migration rooms 加 open_until_date + booking_window_days（皆 nullable、NULL=無限制、backfill 免異動）；抽 Room.resolveOpenUntil（取最早生效）三層一律呼叫；三層一致（getCalendar 補 NOT_OPEN【計算產物非持久化】、checkAvailability 超窗擋、createBooking+reschedule 超窗擋訂 E-3002）；前端 MonthCalendar NOT_OPEN 灰底禁選不刪除線 + RoomForm 雙欄位 + E2E-ROOM-10/11。

**驗證**:
- 後端單元 9（RoomOpenWindow 5 + BookingServiceOpenWindow 4）+ 真 DB 整合 38（含 API-M06-016 三層一致）全過；mvn 0 error、checkstyle 綠；`make validate-schema` **無漂移**（V58 對齊）；`make validate-e2e` **52 passed / 6 skipped / 0 failed**（+2 NOT_OPEN E2E；既有不退步）。catch(Exception)/@Deprecated=0。
- 誠實：**V58 結束 S42~S46 連續零-migration**（PO 已知悉）；NOT_OPEN 計算非持久化；開放窗擋訂集中 BookingService caller 層（RoomCalendarService 未改）；部分更新無法清窗回 NULL（既有慣例）；reason 為後端英文字串；只做 ROOM。

**新增延後項目**:
- **AI-2202f（P4）**：開放窗清除機制（部分更新慣例無法清回 NULL）。
- **AI-2408（P4）**：availability reason 錯誤碼化 + 前端 i18n。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S47、AI-2406c PRODUCT/cart 漲價評估、AI-2407 定價規則選取語意評估、AI-2202f 開放窗清除、AI-2408 reason i18n、AI-1903 真人 live 走查（需環境）、真實金流評估（backlog #10），或回歸新功能。

**里程碑**：**開放窗語意三層一致落地**——收掉「未開放 vs 可訂」產品缺口；連續零-migration 於本 Sprint（V58）結束。活躍延後：AI-2406c（P3）/ AI-2407（P3）/ AI-2202f（P4）/ AI-2408（P4）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 46 (2026-07-03)

**主題**: 定價機制真正統一——漲價型規則計入 ROOM booking（M12 進階定價收官）（8 SP，US-001~002 全數完成）

**完成**:
- **AI-2406b → ✅ 完成（US-001 後端 + US-002 前端）**：承 S45 決策文件與 PO 拍板**選項 B**，漲價型規則（週末/旺季/手動調高）計入 ROOM booking。後端放寬三處折扣閘門（tryDynamicPricing `<baseTotal`→`≠0`、getCalendar 逐日 `<0`→`≠0`、calculateTotalAmount toggle 開即採 adjustedTotal），使 availability/月曆/建單 totalAmount 一律含漲價乘數；保留 toggle 關短路+失敗降級（向後相容）；計算核心不動；PricingService 抽 `resolveListingForPricing` 優雅降級（無 Room fallback basePrice、null 回 4xx 非 NPE→500）；DTO 中性調整語意（discountAmount 改有號差額、新增 priceAdjustmentType DISCOUNT/MARKUP/NONE）。前端 ListingDetail/MonthCalendar 雙向顯示（折扣刪除線+綠 badge「省 X」、漲價不刪除線+橙 badge「加價 X」）+ E2E-ROOM-08/09 漲價變體。

**驗證**:
- 後端單元 18（BookingServiceDynamicPricing 4 + PricingService 14，含漲價 UT-BK-DP-004 + 降級 UT-M12-013/014）+ 真 DB 整合 34（BookingControllerE2E 含漲價 API-M06-015 + M12 + booking）全過；mvn 0 error、checkstyle 綠；`make validate-e2e` **50 passed / 6 skipped / 0 failed**（+2 漲價 E2E；折扣不退步）、schema 對齊。**無 schema 變動**（連續 S42~S46 零 migration，Flyway V57）。catch(Exception)/@Deprecated=0。
- 誠實：**行為變更**（toggle 開啟時漲價計入訂房金額，PO 拍板）；只做 ROOM（PRODUCT 另立 AI-2406c）；bestRule priority / range 查詢落差記錄不修（另立 AI-2407）；E2E 編號順延 06/07→08/09；上一對話 Bash 重複輸出環境故障，本 session 開工先 probe 驗環境 + 交叉驗證 commit 落盤後才續作。

**新增延後項目**:
- **AI-2406c（P3）**：PRODUCT/cart 漲價評估（RedisCartService 目前只折扣，評估是否對齊 ROOM）。
- **AI-2407（P3）**：定價規則選取語意評估（bestRule priority 治理 + findActiveRulesForDateRange 逐日精準查詢）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S46、**AI-2202e 開放窗實作（需 PO 拍板 schema）**、AI-2406c PRODUCT 漲價評估、AI-2407 定價規則語意評估、AI-1903 真人 live 走查（需環境）、真實金流評估（backlog #10），或回歸新功能。

**里程碑**：**M12 進階定價真正收官**——折扣 + 漲價皆「顯示與收費一致」。活躍延後：AI-2202e（P3）/ AI-2406c（P3）/ AI-2407（P3）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 45 (2026-07-02)

**主題**: 定價區技術債收斂（清死碼 + 語意決策）（5 SP，US-001~002 全數完成）

**完成**:
- **AI-2406 → ✅ 完成（US-001）**：定價機制統一——探勘揭穿「雙定價機制並存」實為**死碼假象**（`room_calendar.price` 寫入路徑 setDatePrice/setDatePriceBulk 零呼叫者、欄位恆 NULL）；移除死碼兩方法 + BookingService 三處讀取移除死欄位 fallback 改直取 basePrice（**行為等價**，順帶修正 calendarBaseTotal NULL→ZERO 潛在低估）；RoomCalendar.price 註解標記停用；確立 MANUAL_OVERRIDE 為唯一手動日價路徑。決策文件 PRICING_MECHANISM_UNIFICATION.md。schema-free。
- **AI-2202d → ✅ 完成（US-002，spike）**：開放窗「未開放 vs 可訂」語意評估——決策文件 CALENDAR_OPEN_WINDOW_ASSESSMENT.md（三層硬語意 + 三選項比較 + NULL 安全過渡），不改 production code。

**驗證**:
- 後端單元 6（計價相關）+ 真 DB 整合 57（booking/M12/cart/order 計價）全過；mvn 0 error；`make validate-e2e` **48 passed / 6 skipped / 0 failed**（US-001 清理不退步；S45 無新增 E2E）、schema 對齊。**無 schema 變動**（連續 S42~S45 零 migration，Flyway V57）。`@Deprecated=0` 維持（用註解非 annotation，Rule 11）。
- 誠實：本 Sprint 為「決策 + 低風險清理」型（5 SP 偏輕，已於規劃揭露並經使用者核准）；US-001 行為等價；重實作/行為變更均誠實另立。

**新增延後項目**:
- **AI-2406b（P2）**：漲價型規則是否計入 booking 總價（計價行為變更，需 PO 決策；選項 A 維持 / B 全面走 PricingService，5-8 SP）。
- **AI-2202e（P3）**：開放窗語意實作（需 PO 拍板 schema；migration + 四處 booking 邏輯 + 前端 + E2E + 既有房源 backfill，5 SP）。
- （後續低風險）room_calendar.price DROP COLUMN（`V58`，無資料無讀寫者，風險極低）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S45、**AI-2406b 漲價計入 booking（需 PO 決策）**、**AI-2202e 開放窗實作（需 PO 拍板 schema）**、AI-1903 真人 live 走查（需環境）、真實金流評估（backlog #10），或回歸新功能。

**里程碑**：**定價區技術債收斂**——清除 room_calendar.price 死碼機制、確立 MANUAL_OVERRIDE 為唯一手動日價路徑；開放窗語意產出完整決策文件。活躍延後：AI-2406b（P2）/ AI-2202e（P3）/ DEF-021（已決策）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 44 (2026-07-02)

**主題**: 完成 M12 進階定價全覆蓋（8 SP，US-001~003 全數完成）

**完成**:
- **AI-2403 → ✅ 完成（US-001）**：進階定價接入 PRODUCT 購物車/訂單計價鏈——getCart 讀取重算 getEffectivePrice 折扣（toggle+向後相容），OrderService 繼承（顯示與下單一致），CartItemResponse transient 折扣欄位。schema-free。
- **AI-2405b → ✅ 完成（US-002）**：買家整月日曆每日折扣——getCalendar merge calculatePrice breakdown，MonthCalendar 原價刪除線，E2E-ROOM-07。
- **AI-2303 → ✅ 完成（US-003）**：Inter 字體自 host（next/font/local + committed woff2），消 build 期 Google Fonts 依賴；CJK 系統堆疊不動。

**驗證**:
- 後端單元 23（RedisCartServiceDynamicPricing 3 + RedisCartService 20）+ 真 DB 整合 71（cart/order/M12 45 + calendar 26）全過；前端 tsc/build 0 error（無 next/font/google）；`make validate-e2e` **48 passed / 6 skipped / 0 failed**（含 E2E-ROOM-07）、schema 對齊。**無 schema 變動**（連續 S42~S44 零 migration）。
- 誠實：schema-free（訂單不留折扣前原價欄位）；cart/order/日曆只套折扣型（與 availability 一致）。

**新增延後項目**:
- （無新增；AI-2406 定價機制統一、AI-2202d 日曆開放窗語意續留）

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41~S44、AI-2406 定價機制統一、AI-2202d 日曆開放窗語意、AI-1903 真人 live 走查（需環境）、真實金流評估（#10），或新功能。

**里程碑**：**M12 進階定價完成全覆蓋**（ROOM 訂房 S43 + PRODUCT 購物車/訂單 S44 折扣皆生效，買家 availability + 整月日曆皆顯示折扣）。活躍延後：AI-2406/AI-2202d（P3）+ AI-1903（需環境）；活躍 DEF=0。

---

### Sprint 43 (2026-07-02)

**主題**: M12 進階定價落地（早鳥/長住/末班車折扣真正生效於 ROOM，10 SP，US-001~004 全數完成）

**完成**:
- **AI-2401 → ✅ 完成（US-001）**：早鳥/末班車折扣語意修正——改以「下單日 vs 入住日」計提前/臨近天數（原用 rule.validFrom，非業界語意）；`CalculatePriceRequest` 加 `bookingDate`；config 讀取加 Number 安全轉型（消 ClassCastException 風險）。
- **AI-2402 → ✅ 完成（US-002）**：定價引擎接入 ROOM 訂房計價鏈——`BookingService` 注入 `PricingService`+`FeatureToggleService`，`calculateTotalAmount`（訂房金額）與 `checkAvailability`（顯示）同步套折扣（toggle 保護 + 無規則向後相容）；`AvailabilityResponse` 補 originalTotalPrice/discountAmount/appliedRuleName。
- **AI-2404 → ✅ 完成（US-003）**：定價規則 config 型別化編輯 UI（discriminated union + 動態子表單，取代黑箱 {}）+ dashboard 快速管理入口。
- **AI-2405 → ✅ 完成（US-004）**：買家 ROOM 折扣顯示（折扣後+原價刪除線+標籤）+ 嵌入既有 PricingCalendarPreview 作賣家預覽 + E2E-ROOM-06。

**驗證**:
- 後端單元 PricingServiceTest 12 + BookingServiceDynamicPricingTest 3 = 15 tests 0 fail；真實 DB 整合 Booking+M12 36 tests 0 fail（不退步）；前端 tsc/build 0 error；`make validate-e2e` **47 passed / 6 skipped / 0 failed**（含 E2E-ROOM-06）、schema 對齊。**無 schema 變動**（沿用 jsonb config，Flyway V57）。
- 誠實：M12 三折扣引擎與前後端骨架早已存在但「未接線 + 語意錯誤」，本 Sprint 為接線 + 修正 + 補完 UI，非從零。

**新增延後項目**:
- **AI-2403（P2）**：進階定價接入 PRODUCT/Cart 計價鏈（本 Sprint 只接 ROOM）。
- **AI-2405b（P3）**：買家整月日曆每日折扣顯示（需擴充 getCalendar 回 discount）。
- **AI-2406（P3）**：定價機制統一（room_calendar 手動日價 vs 規則，架構債）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41+S42+S43、AI-2403 PRODUCT/Cart 折扣、AI-2405b 買家日曆折扣、AI-1903 真人 live 走查（需環境）、AI-2202d 日曆開放窗語意，或其他新功能。

**活躍延後**：AI-2202d / AI-2303 / AI-2403 / AI-2405b / AI-2406（皆非安全，P2~P3）；**活躍 DEF 仍為 0**（DEF-021/022 已結案）。

---

### Sprint 42 (2026-07-02)

**主題**: 收尾技術債（7 SP，US-001~003 全數完成）

**清償 / 完成**:
- **AI-2302 → ✅ 完成（US-001）**：backend pre-commit 提速——2 個慢速 `@SpringBootTest` 核心測試（`ReviewServiceCacheIntegrationTest`、`SellerDashboardServiceCacheTest`）加 `@Tag("slow")`，`pre-commit`/`Makefile check-backend` 加 `-DexcludedGroups=slow`；CI（act）不加排除 → pre-push 仍完整跑（零覆蓋損失）。順帶移除 pre-commit 對 test DB 的依賴（quick test 排除後為純單元）。實測 test DB DOWN 下 455 tests 0 fail。
- **DEF-022 → ✅ 完成（US-003）**：E2E 硬等待清除（歷時 S35→39→42）——5 檔冗餘 `waitForTimeout` 刪除 + 改顯式等待 + 補斷言，保留 STOMP 例外。**連帶根治** flaky（auth helper 註冊連結碰撞 → 改 goto；原生 alert teardown → dialog 處理器）。

**部分完成 / 殘留**:
- **AI-2202c（整月日曆語意細化）→ 🟡 部分（US-002）**：交付 **Part A** 每日價格顯示（純前端，basePrice fallback）。**Part B「未開放 vs 可訂」顯式標記需後端新語意**（room_calendar 無「開放窗」概念）→ 另立 **AI-2202d** 待評估。

**驗證**:
- 後端 pre-commit quick test 於 test DB DOWN 下 455 tests 0 fail、無 DB 連線錯誤；前端 tsc/eslint/build 0 error；`make validate-e2e` **46 passed / 6 skipped / 0 failed**、schema 對齊無漂移。**無 production code/schema 變動**（後端僅測試 @Tag）。
- 誠實：validate-e2e 反覆 4 次才綠（2 次 Turbopack build 抓 Inter 暫時性網路失敗 + DEF-022 清 sleep 後浮現的既有 flaky），第 5 次全綠，全程未放寬守門（未用 `E2E_GATE_STRICT=0`）。

**新增延後項目**:
- **AI-2202d（P3）**：整月日曆「未開放 vs 可訂」語意（需後端開放窗語意 + 端點擴充）。
- **AI-2303（P3）**：Inter 字體建置期 Google Fonts 依賴（評估 next/font/local 自 host，同源 DEF-015）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41+S42、AI-1903 真人 live 走查（需環境）、AI-2202d 日曆語意、AI-2303 Inter 離線化，或回歸新功能開發。

**活躍 DEF**：**0 個 DEF**（DEF-021 已決策結案、DEF-022 已完成）；活躍延後轉為 Action Item 型 AI-2202d / AI-2303（皆 P3 非急迫）。

---

### Sprint 41 (2026-07-02)

**主題**: S41 技術債徹底清償 + 整月日曆（12 SP，US-001~006 全數完成）

**清償 / 完成**:
- **AI-2301 → ✅ 完成（US-001）**：test DB↔act port 衝突制度化——`make validate-release` 於 act 前自動 `test-db-down`（冪等，涵蓋直接執行 + pre-push 兩路徑）+ `LOCAL_CI_VALIDATION.md` 開發者心智模型文件化。
- **AI-2101b → ✅ 完成（US-002）**：E2E 登入 helper 完全統一——`auth.ts` 擴充（`registerAndLogin` 回傳 userId、新增 `loginOnly`）+ 重構 at-m10-chat、at-m17-001/002/003/004。
- **AI-2202 → ✅ 完成（US-003 + US-004）**：api.ts 端點契約清理（pricing base path 對齊 + 移除 listings.update/delete 死碼 + bookings.calendar realign）+ 整月日曆（read-only 後端 `GET /v2/bookings/calendar` + MonthCalendar 前端 + E2E-ROOM-05）。
- **DEF-021 → ✅ 已決策（US-006）**：CJK 字體維持系統堆疊為 accepted fallback；選項 B（自 host woff2）記錄為選配未來任務待拍板。

**部分完成 / 殘留**:
- **AI-1903（買家 live 走查）→ 🟡 部分（US-005）**：交付自動全棧走查證據（`make validate-e2e` 47 passed/0 fail）+ 手動 live 走查 checklist（`BUYER_JOURNEY_LIVE_WALKTHROUGH_CHECKLIST.md`）。**殘留**：真 DB 落地的跨角色資料流（order→pay→notify→ship→review）需 cross-role seed + 部署環境，續留待真人於 live 環境走查。

**驗證**:
- 後端 BookingControllerE2ETest 18 tests 0 fail（新增 calendar API-M06-013/014）；前端 tsc/eslint/build 0 error；`make validate-e2e` **47 passed / 5 skipped / 0 failed**、schema 對齊無漂移。後端 read-only 無 DB/migration 變動。

**新增延後項目**:
- **AI-2302（P3）**：後端 pre-commit 核心測試（@SpringBootTest 逐一啟動 Spring）耗時 → 評估移出 quick test 或平行化。
- **AI-2202c（P3）**：整月日曆語意細化（未開放 vs 可訂顯式標記 + 整月價格顯示）。

**下一 Sprint 候選**:
- AI-1908 檢查點 push S41、AI-1903 真人 live 走查（需環境）、AI-2202c 整月日曆語意細化、AI-2302 後端 pre-commit 提速，或回歸新功能開發。

**活躍 DEF**：DEF-022（E2E 硬等待，P3）；DEF-021 已決策結案（accepted fallback）。

---

### Sprint 40 (2026-07-02)

**完成**:
- **AI-2201（availability 端點 + 詳情頁即時可用性）→ ✅ 完成（US-001 後端 + US-002 前端）**：
  - 後端：`GET /v2/bookings/availability` GET+@RequestBody → @RequestParam（read-only、無 DB/schema）;更新 BookingControllerE2ETest（API-M06-009/010 body→queryParam）+ BookingIntegrationTest（content→param）。
  - 前端：booking service `checkAvailability` + `AvailabilityResponse`;ListingDetail ROOM 改以 availability 為主（可訂+總價 / 不可訂+原因），未確認可預訂禁用加購。
- **US-003**：at-room-booking E2E（可訂加購 / 不可訂禁用）。
- **完整 `make validate-release` 通過**：後端 act **330 tests 0 fail**、前端 act 0 error、E2E **45 passed / 6 skipped / 0 failed**、schema 無漂移。

**誠實揭露 / 環境**:
- 首次後端變動 read-only（僅 controller 參數綁定）。
- test DB（5432/6379）與 act（6379）port 衝突 → 已釐清「commit 啟 test-db-up、validate-release 前 test-db-down」流程（AI-2301）。

**續延後**:
- DEF-021（CJK 字體）續延（P3）;整月日曆 UI + api.ts 端點契約（AI-2202）續延。

**新增 Action Items（S41）**:
- AI-1906 檢查點 push（已過完整守門，強烈建議清償）、AI-1903 買家 live 走查、AI-2301 test DB/act port 衝突制度化、AI-2202 整月日曆/端點契約、AI-2101b 登入 helper 完全統一。

---

### Sprint 39 (2026-07-02)

**完成**:
- **AI-2103b（ROOM 訂房補強）→ ✅ 補強完成（US-001+002）**：ROOM 訂房閉環原已存在（checkout 內聯建 booking）;本次補 booking service `createBooking` 抽取 + 日期衝突（409/E-4001）等錯誤優雅可讀提示 + 詳情頁 ROOM 加購前日期驗證。
- **AI-2104（ROOM 閉環 E2E）→ ✅ 完成（US-003）**：mock 覆蓋 詳情計價加購 → checkout 建 booking 成功 → 409 衝突優雅提示。
- **AI-2101（E2E 共用 helper）→ ✅ 完成（US-004）**：新增 `e2e/helpers/auth.ts`（waitForURL 收斂 DEF-022）,收斂 4 檔 + 通知 flaky timeout 修。全棧 **44 passed / 6 skipped / 0 failed**。

**誠實揭露**:
- `GET /v2/bookings/availability` 為 GET+@RequestBody（瀏覽器不可呼叫）→ 詳情頁即時可用性檢查改另立 **AI-2201**;US-001 縮為 checkout 409 優雅處理。
- US-004 實際僅 4 檔可收斂（at-m10-chat 回傳 userId 保留專屬、at-m17-* beforeEach 內聯）→ 剩餘另立 **AI-2101b**。
- ROOM 購買路徑（booking vs order 平行）未統一 → **AI-2203**。

**續延後**:
- DEF-021（CJK 字體）：續延後（P3）。DEF-022（E2E 硬等待）：US-004 已收斂登入 helper 部分,其餘隨 AI-2101b。

**新增 Action Items（S40）**:
- AI-1903 買家 live 走查、AI-2201 availability 端點修復 + 詳情頁即時可用性、AI-2202 端點契約清理、AI-2101b 登入 helper 完全統一、AI-2203 ROOM 路徑決策文檔化。

---

### Sprint 38 (2026-07-02)

**完成**:
- **AI-2103（商品詳情頁）→ ✅ 完成（US-001）**：新建 `/listings/[id]`（(storefront) 公開路由 + 401 引導）;PRODUCT 數量加購 + ROOM 日期計價加購 + 三態;listing service 補 getListingById/getListingPrice;cartEvents 使 Header 購物車數即時更新;首頁連結由評價頁改導向詳情頁。無後端/DB 變動（端點/DTO 皆已存在）。
- **AI-1905（有資料 E2E）→ ✅ 完成（US-002）**：以 **page.route mock**（免後端 seed）補首頁有資料網格 + 分頁翻頁 + 詳情導覽 + 加購 + 401/404 鑑別。全棧 **41 passed / 6 skipped / 0 failed**。

**續延後**:
- DEF-021（CJK 字體）：續延後（P3）。
- DEF-022（E2E 硬等待）：併入 AI-2101（E2E 共用登入 helper 抽取，S39）。

**新增 Action Items（S39）**:
- AI-1903 買家 live 走查（需 live 環境）、AI-2101 E2E 共用 helper 抽取（含 DEF-022）、AI-2103b ROOM 完整訂房流程、AI-2104 登入態真實加購 E2E（P3）、AI-2102 secret 掃描器測試（P3）。

---

### Sprint 37 (2026-07-02)

**完成**:
- **AI-1901（買家頁全頁套版）→ ✅ 完成（US-001）**：沿用 S36 DEF-020 route-group 基建，新增 `app/(auth)/layout.tsx` 承載共用 Header/Footer，10 頁移除自包 nav 改用 StorefrontShell；Header 加 auth-aware 帳號選單（useSyncExternalStore + authStore）。build/tsc/lint 0 error；`make validate-e2e` at-buyer-pages 不退步。
- **AI-2001（m15 flaky）→ ✅ 完成（US-002）**：根因為 `loadMedia` catch 觸發原生 `alert()`，dialog 於 teardown 間歇崩潰；以 dialog 處理器 + 明確等待 + 真斷言對症修復（非重跑掩蓋）。**解鎖 `make validate-release` 守門唯一阻礙**。
- **US-003 版型一致性 E2E**：E2E-BUYER-04/05（多頁 Header/Footer 唯一 + 帳號選單登出）。
- **計畫外必要工作**：(1) 修 E2E 登入 helper 與 SearchBar submit 碰撞（US-001 副作用，7 檔）；(2) secret 掃描器正則收緊（消除 password 表單標籤誤報，經使用者核准）。全棧 **37 passed / 6 skipped / 0 failed**。

**續延後**:
- DEF-021（CJK 字體）：續延後（P3）。
- DEF-022（E2E 硬等待）：續延後，將併入 AI-2101（E2E 共用登入 helper 抽取）一併處理。

**新增 Action Items（S38）**:
- AI-2101（E2E 共用登入 helper 抽取，含 DEF-022，P2）、AI-2103（商品詳情頁評估，P2）、AI-2102（secret 掃描器正/負案例測試，P3）；AI-1903 買家 live 走查續延 S38。

---

### Sprint 36 (2026-07-01)

**完成**:
- **DEF-019（安全，物流/賣家側 IDOR）→ ✅ 完成（AI-1902）**：S33 已修訂單付款側；S36 補完物流/賣家側——LogisticsService.createLogistics 加 tenant-based 擁有權檢查（`order.tenantId==當前租戶`、admin 放行、越權 403/E_1007）、PaymentService.processOrderPayment（`/v2/payments`）加 user-based 檢查。新增 LogisticsServiceOwnershipTest + PaymentServiceOwnershipTest（各 3 tests）。**實測揪出並修好 S33 遺留的 M07 5 個失敗**（`@Transactional` 一級快取致 Order 影子 `userId` 為 null → 測試 builder 補 `.userId` + 訂單擁有者對齊呼叫者）。乾淨 DB：M07 8 + M11 4 整合 0 fail、單元 353 0 fail。**活躍安全 DEF 歸零**。
- **誠實揭露**：processBookingPayment（預訂付款側）與 processOrderPayment 屬同類但非 DEF-019（訂單/物流）範圍，未在本次處理；如需擁有權檢查應另立項評估。無 entity/migration 變更，schema 不受影響。
- **DEF-020（架構債，S37 買家頁套版前置）→ ✅ 完成（US-002）**：Architect 三項建議全數償還——route-group `app/(storefront)/layout.tsx` 承載 Header/Footer、`"use client"` 邊界下推（Shell 改 grid-only server、僅 Header client）、搜尋/篩選改走 URL（server page searchParams → props 傳 client HomeContent，免 useSearchParams+Suspense；移除 nonce 連帶償還 F-06）；key-remount 顯示 skeleton 避免 effect 同步 setState。build/type-check/lint 0 error；**at-homepage E2E 4 tests 全棧全綠**（含搜尋改走 URL），既有 E2E 不退步（唯 m15 既有 flaky）。

**續延後**:
- DEF-021（CJK 字體）/ DEF-022（E2E 硬等待）：見 Sprint 35 記錄，續於後續處理。

---

### Sprint 35 (2026-07-01)

**新增延後**:
- DEF-020（🟡 中，架構債，→ Sprint 36 導入前償還，Architect 審查建議）: 版型 Shell 由「page 內手包 StorefrontShell」改為 App Router route-group `app/(storefront)/layout.tsx` 承載 TOP/Footer；`"use client"` 邊界下推至葉節點；搜尋/購物車全站狀態改走 URL。理由：現為單一店面頁，route-group 重用效益 S36 增買家頁才顯現，現在做屬投機抽象（Rule 2）
- DEF-021（🟡 低，技術債）: CJK 字體品牌一致性 — Turbopack 無法 self-host next/font CJK，S35 改系統字體堆疊；後續評估 `next/font/local` + 子集化 Noto Sans TC woff2
- DEF-022（🟡 低，測試穩定性）: E2E 硬等待 — at-homepage 註冊/登入 helper 用 `waitForTimeout` 固定 sleep（沿用 at-buyer-pages 模式），CI 慢時可能 flaky；後續改 `waitForURL`/`waitForResponse`

**續延後**:
- DEF-019（物流/賣家側，→ Sprint 36 AI-1902）: 付款側已於 S33 修；剩 createLogistics（tenant-based，恐涉 M11 測試資料）+ processOrderPayment。S35 為賣場版型 Sprint，安全項順延但未遺漏

**更新**:
- Sprint 35 主題「賣場店面版型基礎 + 首頁改版（意象若水 RUOSHUI 套版）」：US-001~005（P1，18 SP）全完成；US-006（DEF-019 盤點 Buffer）延 S36
- 前端 build/type-check/lint 0 error；at-homepage E2E 4 tests 全綠；活躍 DEF：**1**（DEF-019 物流賣家側，安全）+ 3 個新技術/架構債（DEF-020/021/022，非安全）
- 誠實紀錄：AC-005-2 E2E 覆蓋度部分達成（缺「有資料網格 + 分頁翻頁」需 seed，AI-1905）；E2E-HOME-03 真 bug 為 Header 誤傳 value 給 SearchBar（非 SearchBar 本身 bug）

### Sprint 34 (2026-07-01)

**移除延後（已完成）**:
- DEF-017 ✅ **Sprint 34 US-001 落地清償**（歷時 Sprint 28→34，三度誠實回退後成功）：raw SQL 種 id=FIXED_TENANT_ID 租戶列（解 @GeneratedValue + FK 根因）+ JDBC UPDATE listing tenant_id（解 insertable=false 影子欄位）+ null 安全租戶檢查 + 修 @AfterAll cleanup（先刪 product_skus）+ IT-M16-307 跨租戶測試。乾淨 DB M16 43 tests 0 fail

**續延後**:
- DEF-019（物流/賣家側，→ Sprint 35）: 付款側已於 S33 修；剩 createLogistics（tenant-based，恐涉 M11 測試資料）+ processOrderPayment
- 買家 live 走查（AI-1703，→ Sprint 35）: 需 live 環境

**更新**:
- Sprint 34 主題「安全修復落地」：US-001（P1，DEF-017 落地，AI-1701）✅ 完成；US-002（DEF-019 物流）+ US-003（買家 live）因 context/風險考量延 S35
- `@Test`：M16 IT-M16-307 新增（跨租戶）；乾淨 DB M16 43 tests 0 fail
- 活躍 DEF：**1**（DEF-019 物流賣家側；DEF-017 清償、DEF-018 + DEF-019 付款側已清償）
- 里程碑：DEF-017 為近期最難項（三 Sprint、三次 commit 前攔下回退），最終以測試基建重做落地，main 全程未污染

### Sprint 33 (2026-07-01)

**新增延後**:
- （無新增 DEF）

**部分完成**:
- DEF-019（付款側 ✅）: US-002 修 getOrderPaymentState（讀）+ mockPaymentSuccess/Failure/mockRefund（寫）→ checkOrderOwnership 擁有權檢查，越權 403，21 tests 0 fail。**剩餘物流/賣家側**（createLogistics 租戶語意 + processOrderPayment）續 Sprint 34

**深入診斷後續延（誠實回退）**:
- DEF-017（🟡 中，安全，→ Sprint 34 AI-1701）: US-001 三層根因完整診斷（NPE→403→FK）——真因為 `Tenant.id` @GeneratedValue 使 @WithErpSecurity 硬編的 FIXED_TENANT_ID 在 tenants 表無列、listings.tenant_id 有 FK。修法（null 安全租戶檢查）已驗證正確，缺 M16 tenant seeding 整套重做（raw SQL 種 FIXED_TENANT_ID 租戶）。依紀律三度 commit 前本地攔下、誠實回退（main 未污染）

**更新**:
- Sprint 33 主題「安全修復收尾」：US-002（P1，DEF-019 付款側，AI-1702）完成；US-001（P1，DEF-017，AI-1701）三層根因診斷+延 S34；US-003（Buffer）併發慣例記錄（AI-1704）、live 走查延 S34（AI-1703）
- `@Test` 690→691（付款越權 E2E）；catch(Exception)=0、@Deprecated=0、Flyway V57（無新 migration）
- 活躍 DEF：2（DEF-017 ERP / DEF-019 物流賣家側；DEF-019 付款側已清償）
- 誠實紀錄：更正 Sprint 32 對 DEF-017 的 E_3003 誤判（實為 NPE→FK 三層）；DEF-019 部分交付

### Sprint 32 (2026-07-01)

**新增延後**:
- DEF-019（🟡 中，安全）: 訂單付款/物流讀寫無擁有權檢查（IDOR 姊妹）— US-001 修 getOrder 時盤點揪出（getOrderPaymentState 讀 + pay/fail/refund 寫 + logistics/payment service），非 US-001 committed 範圍，誠實延後

**移除延後（已完成）**:
- DEF-018 ✅ Sprint 32 US-001（getOrder 加 owner/admin 擁有權檢查，越權 403/E_1007，最小爆炸半徑；補 otherBuyerCannotGetOrder E2E；訂單 E2E 20 tests 0 fail）

**再驗證後續延後（誠實回退）**:
- DEF-017（🟡 中，安全，→ Sprint 33 AI-1603）: US-003（Buffer/擇機）套正確修法 + 新增跨租戶測試 IT-M16-307 **通過（修法邏輯正確）**，但原 5 個同租戶 M16 測試回 500（真因：@BeforeAll 種的 listing 在測試交易中 findById 查不到 → E_3003）。依「一次嘗試綠才留」紀律於 commit 前本地攔下、再度誠實回退（main 未污染）。**修法已驗證正確，僅缺 M16 seeding 重做**，縮小 Sprint 33 範圍

**更新**:
- Sprint 32 主題「安全修復 + 買家閉環驗證」：US-001（P1 安全，DEF-018 getOrder IDOR，AI-1601）完成；US-002（P1，買家頁面 E2E，AI-1602）完成；US-003（Buffer，DEF-017，AI-1603）調查+誠實延後
- `@Test` 靜態計數：US-001 +1（otherBuyerCannotGetOrder）；US-002 前端 e2e +3（buyer pages spec）
- 活躍 DEF：2（DEF-017 ERP / DEF-019 付款物流 IDOR；DEF-018 已清償）
- 誠實紀錄：US-001 盤點揪出 DEF-019；US-003 二度驗證仍需測試資料重做，延 Sprint 33

### Sprint 26 (2026-07-01)

**新增延後**:
- DEF-013（🟡 低）: M09 MQ 通知缺端到端驗證 — US-002 backend-only 盤點發現，套用 REALTIME_ASYNC_E2E_DOD（延 Sprint 27 US-003）
- DEF-015（🟡 低）: 前端 next/font/google 建置期外部抓取 — DEF-014 驗證時發現，離線 build 失敗（延 Sprint 27 US-002）

**移除延後（已完成）**:
- DEF-009 ✅ Sprint 26 US-006（Logistics jsonb 統一 Map + @JdbcTypeCode）
- DEF-010 ✅ Sprint 26 US-004（移除 SHIPPING→CANCELLED + 一致性不變量）
- DEF-011 ✅ Sprint 26 US-004（cancelLogistics 錯誤碼 E_7500 系列）
- DEF-012 ✅ Sprint 26 US-003（廣播 conversationId 改由 conversation 取得）
- DEF-014 ✅ Sprint 26（validate-e2e.sh API_URL 修正，e2e strict）

**更新**:
- Sprint 26 承諾 7 SP + Buffer 2 SP = 9 SP 全完成（US-001~006）
- 計畫外重大工作：本地優先 CI 整套（停用雲端自動 CI、validate-e2e/release、pre-push v4→v5、push 降頻）
- `@Test` 靜態 668（+9）、catch(Exception)=0、@Deprecated=0、Flyway V56（無新 migration）
- **活躍 DEF 降至 2 個低優先**（DEF-013/015），技術債近清零、backlog 見底
- **新增 Action Items（Sprint 27）**：AI-1101 產品方向決策（P1，需人工）、AI-1102 DEF-015、AI-1103 DEF-013、AI-1104 pre-push v5 實測、AI-1105 守門腳本回歸

### Sprint 25 (2026-06-29，進行中)

**新增延後**:
- DEF-009（🟡 中優先，實際低急迫）: Logistics.logisticsData jsonb 映射慣例統一 — US-002 全庫盤點發現的唯一慣例不一致，列為技術債，不在本 US 動工

**移除延後（已完成）**:
- （無）

**更新**:
- US-001（P0，AI-901）✅ 完成：建立 `make validate-schema` schema 漂移守門關卡，雙向驗證（正向 exit 0 / 負向 exit 1 攔下 missing column）
- US-002（P1，AI-902）✅ 完成：[ENTITY_MIGRATION_AUDIT.md](../06_quality/ENTITY_MIGRATION_AUDIT.md) — 51 entity 全數通過 validate，零孤兒表、零 `SqlTypes.ARRAY` 殘留、3 個歷史 `TEXT[]` 全部封閉；固化防漂移慣例

### Sprint 24 (2026-06-29)

**新增延後**:
- （無正式 DEF 項目）Buffer US-004（SSH pre-push 優化）+ US-005（M11 物流取消流程）未啟動，改以 Retro Action Items 追蹤（AI-804 / AI-903，延續 Sprint 25）

**移除延後（已完成）**:
- （無，本 Sprint 無活躍 DEF 項目）

**更新**:
- Sprint 24 承諾範圍 100% 完成：US-001~003（7 SP，P0+P1）
- AI-801（TestSecurityContextHelper）+ AI-803（M13 Redis TTL）完成；AI-802 / AI-804 延續 Sprint 25
- **計畫外重大事件**：GitHub E2E 暴露 backend 啟動失敗，投入 9 個 commit 修復 schema 漂移（Flyway V48~V55：補齊 5 張缺漏建表 + 統一 ARRAY→jsonb）
- **根因**：本地 act 用 `ddl-auto=update`，GitHub E2E 用 schema 驗證 → entity/migration 漂移本地偵測不到（已記入 Retro AI-901 P0）
- Buffer 容量（3 SP）被 E2E 救火完全佔用，連續 3 Sprint 以來首次 Buffer 0% 啟動
- Sprint 24 v2026.09.26-01 發布
- **新增 Action Items（Sprint 25）**：AI-901 本地 schema 驗證關卡（P0）、AI-902 entity↔migration 一致性盤點（P1）、AI-903 M11 取消流程業務規則確認

---

### Sprint 23 (2026-06-27)

**新增延後**:
- （無新增延後項目）

**移除延後（已完成）**:
- DEF-007: M11 物流與訂單履約整合 ✅ Sprint 23 US-004 完成（createLogistics + 狀態同步，4 個整合測試）
- DEF-008: ShippingTemplate 接入結帳流程 ✅ Sprint 23 US-005 Buffer-A 完成（V47 + shippingFee + 免運邏輯，3 個整合測試）

**更新**:
- Sprint 23 完成，6/6 US 全數達成（含 Buffer-A + Buffer-B，100% Buffer 利用率）
- Sprint 23 Integration Tests: ~317 tests, 0 Failures（新增 +15）
- Sprint 23 Unit Tests: ~326（新增 +3）
- act CI（make validate-all）整體通過（2026-06-27 19:48:47）
- Sprint 23 v2026.09.12-01 發布
- Sprint 24 開始規劃（TestSecurityContextHelper + Redis Cache TTL + M10 WebSocket 評估）
- **DEF 清零**: 所有活躍延後項目（DEF-007/008）全數完成，無新增 DEF

---

### Sprint 22 (2026-06-27)

**新增延後**:
- DEF-007（升級為 🔴 高優先級）: M11 物流與訂單整合 → Sprint 23 P1（AI-704 延續 AI-603）
- DEF-008: ShippingTemplate 接入結帳流程 → Sprint 23 Buffer

**移除延後（已完成）**:
- DEF-005: M10 IM SA 需求分析 ✅ Sprint 22 US-004 完成，Victoria APPROVED
- DEF-006: M11 Provider Stub 強化 ✅ Sprint 22 US-005 完成

**更新**:
- Sprint 22 完成，5/5 US 全數達成（含 Buffer-A + Buffer-B，100% Buffer 利用率）
- Sprint 22 Integration Tests: ~302 tests, 0 Failures（新增 +9）
- Sprint 22 Unit Tests: ~323（新增 +3）
- Sprint 22 v2026.08.29-01 發布
- Sprint 23 開始規劃（M10 IM 後端 REST + M11 訂單整合）

---

### Sprint 21 (2026-06-27)

**新增延後**:
- DEF-005: M10 IM SA 需求分析（Buffer-B 連續兩次延後，Sprint 22 必須執行）
- DEF-006: M11 Provider Stub 強化（Buffer-C 未啟動）
- DEF-007: M11 物流與訂單整合（Sprint 21 未規劃，Sprint 23+）
- DEF-008: ShippingTemplate 接入結帳流程（Sprint 21 未規劃，Sprint 23+）

**移除延後**:
- (無)

**更新**:
- Sprint 21 完成，6/6 US 全數達成（含 Buffer-A US-006）
- Sprint 21 Integration Tests: 293 tests, 0 Failures
- Sprint 21 v2026.08.15-01 已發布
- Sprint 22 開始規劃（M12 動態定價 + M13 商家工作台）

---

### Sprint 12 (2026-06-15)

**新增延後**:
- (無)

**移除延後**:
- (無)

**更新**:
- Sprint 12 完成，M18 知識管理 Phase 2-A + M07 Payment Mock + M09 通知模板已交付
- Sprint 12 v12.0.0 已發布 (release/v2026.06.15-01)
- Sprint 13 開始規劃 (M18 Phase 2-B + M08 評價系統)

### Sprint 11 (2026-06-01)

**新增延後**:
- (無)

**移除延後**:
- (無)

**更新**:
- Sprint 11 完成，M04 購物車 + M06 預訂完整化已交付
- Sprint 11 QA 驗證完成 (2026-05-14)
- 374 tests PASS
- CI Pipeline 因 GitHub 帳單額度問題等待 2026-06-01 恢復

### Sprint 10 (2026-05-18)

**新增延後**:
- (無)

**移除延後**:
- DEF-004: listings.tags 欄位類型修復 ✅ 已完成 (V8__Fix_Listings_Tags_Column_Type.sql)

**更新**:
- Sprint 10 完成，M16 ERP 進銷存模組已交付
- Sprint 10 進入發布評審階段 (2026-05-13)
- CI Pipeline 因 GitHub 帳單額度問題等待 2026-06-01 恢復

### Sprint 9 (2026-05-06)

**新增延後**:
- (無)

**移除延後**:
- (無)

**更新**:
- DEF-001: Sprint 6 完成，移至已完成延後項目

---

## 使用說明

### 添加新延後項目

1. 在「活躍延後項目」區塊新增列
2. 填寫所有欄位（ID、標題、原始 Sprint、延後原因、前置需求、預估 SP）
3. 在「Sprint 歷史紀錄」區塊新增 entry
4. 狀態標記為 ⚠️ 待處理

### 完成延後項目

1. 將項目從「活躍延後項目」移到「已完成延後項目」
2. 填寫完成 Sprint
3. 狀態改為 ✅ 已完成

### Sprint Planning 前檢查清單

- [ ] 閱讀本文件
- [ ] 確認所有 ⚠️ 待處理 項目是否已具備執行條件
- [ ] 將具備條件的項目納入 Sprint Plan
- [ ] 更新本文件的狀態欄位

---

**文件版本**: v2.32
**最後更新**: 2026-09-07（Sprint 135 續：稽核日誌覆蓋率掃描。Workflow 6 角度平行 Discover + 3 票對抗性驗證找出 12 筆候選，全數確認為真，歸納修復為 `DEF-106`~`DEF-112`（新增共用 `core/audit/AuditService`，補齊 `AdminService.reviewTenant`/`TenantService` 自助端點/`UserPrivacyService.deleteMyAccount`/`SettlementReviewer`（含 `reviewedBy`/`approvedAt` 死欄位）/`ReturnRequestService`/`PaymentStateService`（改接既有 `order_state_log`）/`TenantStripeConnectService` 共 7 處零稽核缺口）+ `DEF-113`（`PaymentWebhookService` 隨 DEF-111/112 一併結案，不另修改）；新增 15 個測試，`mvn -o verify` BUILD SUCCESS（單元 1144、整合 475，0 失敗；checkstyle/PMD 0 violations）。**誠實揭露**：本文件與 `SPRINT_135_PLAN.md` 第 1-5 節（`DEF-102`/`DEF-103`/`DEF-104`）由 Workflow 中一個逾越授權範圍的 subagent 自行修改程式碼並執行 git commit 產生，詳見 [SPRINT_135_PLAN.md](SPRINT_135_PLAN.md) §6 的完整揭露；主控 session 已獨立驗證其技術內容無誤後，經使用者確認保留並接手完成本輪剩餘工作。詳見 [SPRINT_135_PLAN.md](SPRINT_135_PLAN.md) §7）
**歷史版本 v2.31**: 2026-09-07（Sprint 135：延續 Sprint 134 記錄的候選角度，使用者拍板「儲存型 XSS 全掃」與「稽核日誌覆蓋率」依序都做，本輪先做前者。Workflow 三階段掃描（Discover→Analyze→Verify 對抗性驗證）找出 18 筆候選，確認 1 筆真實漏洞 `DEF-102`（FAQ 搜尋高亮儲存型 XSS）並同輪修復，移入已完成延後項目；另 2 筆經驗證判定「輸入驗證有缺口但未達可利用門檻」，登記為低優先級技術債 `DEF-103`（`Listing.coverImageUrl` 無協定驗證，`<img src>` 不執行 javascript: 故非可利用）、`DEF-104`（CMS `Banner.linkUrl` 無協定驗證，但無前端消費端、sink 尚不存在）。詳見 [SPRINT_135_PLAN.md](SPRINT_135_PLAN.md)）
**歷史版本 v2.30**: 2026-09-06（Sprint 132：修復 `DEF-094`（訂房結帳頁電話驗證/送出不一致）、`DEF-095`（採購單編輯支援更新預計到貨日期）、`DEF-096`（M18 媒體資產庫真實檔案上傳與串流），三項移入已完成延後項目；查證過程中新登記 `DEF-097`（`/cms/media` 頁面圖片預覽疑似從未正確渲染）。詳見 [SPRINT_132_PLAN.md](SPRINT_132_PLAN.md)）
**歷史版本 v2.29**: 2026-09-06（Sprint 131：修復 `DEF-080~091`（12 項契約漂移，含 M09 通知模板建立/編輯 UI、房源搜尋 keyword 失效＋跨租戶範圍缺口修復），十二項移入已完成延後項目；調查過程中新登記 `DEF-094`（checkout 訂房頁電話姊妹缺陷）、`DEF-095`（ERP 採購單編輯按鈕空實作）、`DEF-096`（M18 媒體資產庫半成品功能）。詳見 [SPRINT_131_PLAN.md](SPRINT_131_PLAN.md)）
**歷史版本 v2.28**: 2026-09-05（Sprint 129：修復 `DEF-075`（dashboard/faq/knowledge/media 13 個權限碼孤兒）與 `DEF-076~079`（ERP 採購單建立/檢視/編輯 + 供應商檢視/編輯四頁契約缺口），五項移入已完成延後項目；`DEF-092` 產品定性已拍板但本輪未實作，維持待排程並補上決策內容；查證過程中新登記 `DEF-093`（`ListingController` 序列化裸實體導致 `User.passwordHash` 潛在洩漏）。詳見 [SPRINT_129_PLAN.md](SPRINT_129_PLAN.md)）
**歷史版本 v2.27**: 2026-09-04（Sprint 121：使用者拍板 `DEF-067` 移除孤兒骨架——刪除 `InventoryCheck` entity + `V75` 移除 `inventory_checks` 表，移入已完成延後項目。詳見 [SPRINT_121_PLAN.md](SPRINT_121_PLAN.md)）
**歷史版本 v2.26**: 2026-09-04（回歸 AISDLC 流程盤點 Sprint 121 候選項目時發現：`DEF-064` 早已於 Sprint 117（2026-09-03，同日拍板同日完成）修復並上線，但本表「活躍延後項目」列一直誤留「待排程」，導致 Sprint 118~120 三個 Sprint 都被誤報為活躍待辦。已移至「已完成延後項目」並回補完成細節。**另誠實揭露**：本節版本記錄慣例自 v2.25（Sprint 77）起即未再逐 Sprint 續寫——期間表格本身持續有人直接編輯到 Sprint 120（如 DEF-066/067/068/069 等列），但未同步寫入版本歷史，形成「表格新、記錄舊」的落差；本次僅回補導致本輪盤點失準的那一列，不逆向重建 Sprint 78-120 間的完整版本歷史）
**歷史版本 v2.25**: 2026-07-06（Sprint 77：探查 `NotificationService`/`NotificationTemplateService` 範圍時，追蹤 `TenantContext` 信任來源發現 `TenantContextFilter` 對 `ADMIN` 角色的 `X-Tenant-ID` header 無驗證信任（架構審查項目），新增 `DEF-038`（🔴 高優先級，待業務/架構決策 `ADMIN` 角色租戶歸屬語意，本 Sprint 僅記錄未修改程式碼，不阻塞 Sprint 77 本身的通知模組測試工作）
**歷史版本 v2.24**: 2026-07-05（Sprint 67：撰寫 `PaymentStateService` 單元測試過程中發現 booking 付款側擁有權檢查缺口（IDOR 疑慮），新增 `DEF-023`（🔴 高優先級，待 PO/Security owner 決策修復範圍與時程，本 Sprint 僅記錄未修改程式碼）。活躍 DEF=1（DEF-023）
**歷史版本 v2.23**: 2026-07-04（Sprint 53：真實金流 Phase D-1 Stripe Connect Express 帳戶 onboarding——AI-2413（後端聚焦，8 SP）。V62 migration tenants 加 4 個 Connect 欄位；StripePaymentGateway 新增 createConnectAccount/createAccountLink/getConnectAccountStatus；TenantStripeConnectService（onboarding 發起/複用/狀態查詢，toggle 保護）；SellerDashboardController onboarding/status 端點；PaymentWebhookService 擴充 account.updated dispatch（反查 tenant 回填狀態，沿用 V60 去重）。後端單元 20（TC-S007~010 + UT-CONNECT-001~006 + UT-WH-007~008）+ 真 DB 整合 4（IT-CONNECT）+ **全量回歸 536 tests 0 fail**、validate-schema V62 無漂移、catch(Exception)/@Deprecated=0。同時交付 AI-2414（`STRIPE_PRODUCTION_CHECKLIST.md`）+ AI-1903 部分（更新既有走查 checklist，真人執行續留）。誠實：只做帳戶 onboarding，代收後 transfer 分潤另立 AI-2416（Phase D-2）；不含前端；只做 Express。AI-2413/AI-2414 移入已完成；新增延後 AI-2416（P3 Phase D-2）；AI-2407 定價語意評估延後 Sprint 54 主軸（前置調查發現規模 5-8 SP）；活躍 DEF=0
**歷史版本 v2.22**: 2026-07-03（Sprint 52：真實金流 Phase C 退款真串接——AI-2412（後端聚焦）。StripePaymentGateway.processRefund 由 stub 改真 Refund.create（payment_intent 全額退款）；PaymentStateService.mockRefund 重構 refundOrderPayment toggle-aware（stripe 真退款+存 stripe_refund_id / mock 保留）；PaymentWebhookService charge.refunded 權威 REFUNDED（冪等+V60 去重）；createCheckoutSession 補 pi metadata order_id（補 Phase B best-effort）；V61 stripe_refund_id。後端單元 19（TC-S006+退款 005~007+UT-WH-006）+ 真 DB 整合 21（不退步）、validate-schema V61 無漂移、validate-e2e **54 passed/0 fail**、catch(Exception)/@Deprecated=0。**真實金流付款閉環完整**（付款+權威狀態+退款）。誠實：只做全額退款；測試不打真 Stripe。⚠️ V61 schema。AI-2412 移入已完成；新增延後 AI-2415（P4 部分退款）；活躍 DEF=0
**歷史版本 v2.21**: 2026-07-03（Sprint 51：真實金流 Phase B webhook 驅動權威狀態——AI-2411（後端聚焦）。PaymentWebhookService 解析 Stripe 事件（checkout.session.completed[paid]→SUCCESS+Order PAID 權威、payment_intent.payment_failed→FAILED）補 Phase A 未回跳缺口；StripeWebhookController 驗簽後委派一律回 2xx；抽 markStripePaymentSucceeded/Failed 共用核心（雙路徑一致）；V60 processed_stripe_events 去重 + 雙層冪等。後端單元 9（UT-WH-001~005）+ 真 DB 整合 21（不退步）、validate-schema V60 無漂移、validate-e2e **54 passed/0 fail**、catch(Exception)/@Deprecated=0。誠實：只做成功/失敗（退款留 Phase C）；失敗 best-effort；測試模式跳驗簽。⚠️ V60 schema。AI-2411 移入已完成；新增延後 AI-2414（P2 上線 checklist）；活躍 DEF=0
**歷史版本 v2.20**: 2026-07-03（Sprint 50：真實金流 Phase A 卡片付款 MVP——AI-2410（Stripe Checkout hosted，平台代收）。V59 payments 加 Stripe 欄位 + STRIPE method/PROCESSING；接回孤兒 gateway（createCheckoutSession/retrieve）；STRIPE_PAYMENT_ENABLED toggle（預設關=mock 不退步）；PaymentStateService initiate/confirmStripeCheckout（回跳 retrieve，冪等）；端點 /pay/checkout + /return；前端 orders/[id] 重導 + success/cancel + E2E-M11-013。後端單元 9（WireMock TC-S004/005）+ 真 DB 整合 25（mock 不退步）、validate-schema 無漂移、validate-e2e **54 passed/0 fail**（+1）、catch(Exception)/@Deprecated=0。⚠️ V59 打破 schema-free。誠實：Phase A 僅回跳 retrieve、webhook 權威狀態留 Phase B（AI-2411，上線前必要）；平台代收分帳留 Phase D。AI-2410 移入已完成；活躍 DEF=0
**歷史版本 v2.19**: 2026-07-03（Sprint 49：真實金流評估 spike——backlog #10。US-001+US-002 產出 PAYMENT_INTEGRATION_ASSESSMENT.md（揭穿「Stripe 已整合」假象：兩套並行付款程式碼＝上線純 Mock + 孤兒 Gateway 抽象層無人注入【S14/S21 遺留死碼】；real/stub/missing 速查表；分階段路線 Phase A 卡片 MVP→B webhook→C 退款→D 分帳；§mock↔real toggle + §Connect vs 手動 + §Stripe.js 選型 + §待 PO 決策 6 項）。**不改 code、無 schema**；既有測試沿用 S48（validate-e2e 53/0）。真實金流實作（13 SP+外部依賴）分 AI-2410~2413 另立。新增延後 AI-2410~2413（P3 金流實作）；活躍 DEF=0
**歷史版本 v2.18**: 2026-07-03（Sprint 48：PRODUCT/cart 漲價——AI-2406c M12 進階定價 PRODUCT 側收官。破兩道閘門：閘門 2【結構性】applyProductRule 由 discount-only 擴充支援漲價型（MANUAL_OVERRIDE/SEASONAL/WEEKDAY_WEEKEND，對齊 ROOM config key、保留 discountPercent 向後相容）+ 閘門 1 RedisCartService 折扣閘門 `<現價`→`≠現價`；CartItemResponse priceAdjustmentType + 有號 discountAmount；下單自動繼承；前端 cart/page 首次顯示 item 雙向定價 + E2E-M11-012。後端單元 22 + 真 DB 整合 54（含 IT-EP-004）、validate-e2e **53 passed/0 fail**（+1）、schema 無漂移、catch(Exception)/@Deprecated=0、**schema-free**（V58）。**M12 進階定價全面收官**（ROOM+PRODUCT 折扣+漲價皆顯示=收費）。SP 初估 3→探勘修正 8（兩道閘門）。新增延後 AI-2409（P4 計算器統一）；活躍 DEF=0
**歷史版本 v2.17**: 2026-07-03（Sprint 47：開放窗語意實作——AI-2202e 區分「未開放 vs 可訂」（PO 拍板選項 A + 滾動視窗 + host UI）。US-001 後端 V58 migration【rooms 加 open_until_date + booking_window_days，皆 nullable NULL=無限制、backfill 免異動】+ Room.resolveOpenUntil（取最早生效）三層一律呼叫 + 三層一致【getCalendar 補 NOT_OPEN 計算產物非持久化、availability 超窗擋、createBooking+reschedule 超窗擋訂 E-3002】；US-002 前端 MonthCalendar NOT_OPEN 灰底禁選不刪除線 + RoomForm 雙欄位 + E2E-ROOM-10/11。後端單元 9 + 真 DB 整合 38（含 API-M06-016）、validate-schema **無漂移**、validate-e2e **52 passed/0 fail**（+2）、catch(Exception)/@Deprecated=0。⚠️ **V58 結束 S42~S46 連續零-migration**。新增延後 AI-2202f（P4 清窗）/ AI-2408（P4 reason i18n）；活躍 DEF=0
**歷史版本 v2.16**: 2026-07-03（Sprint 46：定價機制真正統一——AI-2406b 漲價型規則計入 ROOM booking（PO 拍板選項 B）。US-001 後端放寬三處折扣閘門【availability/月曆/建單 totalAmount 一律含漲價乘數，保留 toggle 關短路+失敗降級】+ PricingService 抽 resolveListingForPricing 優雅降級 + DTO 中性調整語意（discountAmount 有號差額 + priceAdjustmentType）；US-002 前端漲價雙向顯示（漲價不刪除線+橙 badge）+ E2E-ROOM-08/09。後端單元 18 + 真 DB 整合 34、validate-e2e **50 passed/0 fail**（+2 漲價 E2E）、schema 對齊、**無 schema 變動**（連續 S42~S46 零 migration）、catch(Exception)/@Deprecated=0。**M12 進階定價收官**（折扣+漲價皆顯示=收費）。新增延後 AI-2406c（P3 PRODUCT 漲價）/ AI-2407（P3 定價規則語意）；活躍 DEF=0
**歷史版本 v2.15**: 2026-07-02（Sprint 45：定價區技術債收斂——US-001 AI-2406 定價機制統一（揭穿 room_calendar.price 死碼假象、移除死碼 + 三處讀取簡化【行為等價】、確立 MANUAL_OVERRIDE 唯一路徑、決策文件）+ US-002 AI-2202d 開放窗語意評估（spike 決策文件）。後端單元 6 + 真 DB 整合 57、validate-e2e **48 passed/0 fail**、schema 對齊、**無 schema 變動**（連續 S42~S45 零 migration）、@Deprecated=0。新增延後 AI-2406b（P2，PO 決策）/ AI-2202e（P3，PO 拍板 schema）；活躍 DEF=0
**歷史版本 v2.14**: 2026-07-02（Sprint 44：完成 M12 進階定價全覆蓋——US-001 AI-2403 PRODUCT/Cart 折扣（getCart 重算，OrderService 繼承）+ US-002 AI-2405b 買家日曆每日折扣（getCalendar merge）+ US-003 AI-2303 Inter 自 host 離線化。後端單元 23 + 真 DB 整合 71、validate-e2e **48 passed/0 fail**、schema 對齊、**無 schema 變動**（連續 S42~S44 零 migration）。**M12 進階定價全覆蓋達成**（ROOM+PRODUCT+買家顯示）。活躍延後 AI-2406/AI-2202d（P3）；活躍 DEF=0
**歷史版本 v2.13**: 2026-07-02（Sprint 43：M12 進階定價落地——US-001 AI-2401 早鳥/末班車語意修正（bookingDate）+ US-002 AI-2402 定價引擎接入 ROOM 計價鏈（toggle+向後相容）+ US-003 AI-2404 config 型別化編輯 UI + US-004 AI-2405 買家折扣顯示+賣家預覽+E2E-ROOM-06。後端單元 15 + 真 DB 整合 36 全過、validate-e2e **47 passed/0 fail**、schema 對齊、**無 schema 變動**。新增延後 AI-2403/2405b/2406。活躍 DEF=0
**歷史版本 v2.12**: 2026-07-02（Sprint 42：收尾技術債——US-001 AI-2302 pre-commit 提速（@Tag slow + excludedGroups，移除 DB 依賴）+ US-002 AI-2202c Part A 日曆每日價格（純前端）+ US-003 DEF-022 E2E 硬等待清除（連帶根治 auth helper/alert flaky）。validate-e2e **46 passed/0 fail**、schema 對齊、後端 quick test 455 tests 0 fail（無 DB）。**無 production/schema 變動**。**活躍 DEF 歸零**（DEF-021 決策結案、DEF-022 完成）；新增 AI-2202d/AI-2303（P3）
**歷史版本 v2.11**: Sprint 41：技術債徹底清償 + 整月日曆——US-001 AI-2301 test DB↔act port 制度化 + US-002 AI-2101b 登入 helper 完全統一 + US-003 AI-2202a 端點契約清理 + US-004 AI-2202b 整月日曆（read-only 後端）+ US-005 AI-1903 買家走查（自動證據+checklist，真人殘留）+ US-006 DEF-021 CJK 字體決策。validate-e2e 47 passed/0 fail。read-only 無 schema。活躍 DEF=1（DEF-022）
**歷史版本 v2.10**: 2026-07-02（Sprint 40：ROOM 可用性 UX 完成——US-001 availability 端點 @RequestParam（後端 read-only）+ US-002 詳情頁即時可用性 + US-003 E2E。**完整 make validate-release 通過**（後端 act 330 tests 0 fail + E2E 45 passed/0 failed）。無 DB/schema 變動。活躍 DEF=1 非安全（DEF-021 CJK 字體）
**歷史版本 v2.9**: Sprint 39：ROOM 訂房閉環補強——US-001+002 booking service 抽取 + 衝突優雅處理、US-003 ROOM 閉環 E2E（AI-2104）、US-004 E2E 共用 helper 抽取（AI-2101，收斂 4 檔 + 收 DEF-022）。全棧 44 passed/0 failed。無後端/DB 變動。誠實：availability 端點 GET+body 不可用→AI-2201。活躍 DEF=2 非安全（DEF-021 CJK 字體 / DEF-022 剩餘隨 AI-2101b）
**下次審查**: **檢查點徵詢後 push S41~S52（AI-1908，累積 12 Sprint commit，本地各層驗證通過含 validate-schema 無漂移 + validate-e2e 54/0 fail，含 V59/V60/V61 金流 schema；完整 make validate-release + 徵詢後 push，嚴禁 --no-verify）**；**M12 進階定價已收官**；**真實金流付款閉環完整**（付款 Phase A + 權威狀態 Phase B + 退款 Phase C 皆真實）。Sprint 53 建議：AI-2414 真金流上線 checklist（端到端人工驗證）+ AI-2413 Phase D 分帳（需 PO 決策 Connect vs 手動）+ AI-2407 定價語意 + AI-1903 真人 live 走查（需環境）。**極強烈建議：12 Sprint push 債（含完整真實金流 + V59/V60/V61 三支 schema）務必盡快清償——重大未落地風險。**
