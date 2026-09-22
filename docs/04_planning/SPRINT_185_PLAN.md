# Sprint 185 Plan — 定價規則查詢跨租戶洩漏 + 房源維護狀態跨租戶竄改（DEF-255~256）

**Sprint**: Sprint 185
**日期**: 2026-09-23

## 1. 起點

Sprint 184 修復 `DEF-254` 後，`DEFERRED_ITEMS_TRACKER.md` 僅剩 `DEF-235`/`DEF-236`/`DEF-241`/`DEF-248`/`DEF-252`/`DEF-253` 六項低優先級「不排入排程」項目。本輪延續 Sprint 184 結尾提出的兩個方向：

1. Mass assignment 模式廣義延伸——系統性掃描全庫所有 Create/Update DTO，檢查是否還有其他「呼叫端能設定不該由其設定的欄位」的漏洞（`DEF-244` 的廣義延伸）。
2. 稽核日誌完整性/可竄改性，以及匯出/報表/批次查詢類端點的資料範圍控制——這兩塊先前從未被系統性審查過。

## 2. 掃描方法與結果

派出 2 個背景唯讀調查 agent：

- **Agent 1（DTO mass assignment 全庫掃描）**：以 `grep -rln "class.*Request"` 列出全部候選，共 61 個 DTO 原始檔、90+ 個 Request 類別，逐一讀取並追蹤至對應 Controller/Service。核心欄位層級掃描（權限/角色/租戶歸屬、時間戳記/審計、狀態/審核結果、跨使用者操作）**未發現新的高風險欄位層級漏洞**，僅找到 3 個低風險的死欄位/孤兒 DTO（見 §4 誠實揭露）。但**在任務範圍之外意外發現一個真實可利用的路徑變數 IDOR**：`RoomCalendarController.markMaintenance`/`unmarkMaintenance` 完全沒有租戶擁有權檢查，已收錄為本輪修復項目 `DEF-256`。
- **Agent 2（稽核日誌完整性 + 匯出/報表資料範圍審查）**：確認 `AuditLog` 本身是真正的 append-only（全庫沒有任何更新/刪除呼叫點），查詢端點正確限定 `SUPER_ADMIN`，內容未夾帶敏感欄位——**稽核日誌本身完整性無缺陷**。匯出/報表類端點逐一審查（`AnalyticsController`/`AdminController`/ERP/結算/物流等）確認皆正確以 `TenantContext` 範圍化，僅發現一項真實漏洞：`PricingService.getRules()` 帶入 `roomListingId`/`listingId` 查詢參數時完全未驗證租戶歸屬，已收錄為 `DEF-255`。另發現稽核覆蓋率缺口（`PricingService`/`OrderService`/`BookingService`/`PaymentService` 的狀態轉換皆未寫入稽核紀錄），因涉及大範圍多服務改動，登記為不排入本輪排程的項目。

兩個 agent 各自在指定任務範圍之外都意外發現了一個真實、獨立的跨租戶 IDOR，皆已驗證並修復。

## 3. 修復內容

### 3.1 DEF-255（🟠）：`PricingService.getRules()` 跨租戶定價規則洩漏

`getRules(roomListingId, listingId, activeOnly)` 帶入 `roomListingId`/`listingId` 參數時，查詢完全不檢查該 listing/room 是否屬於呼叫者租戶：`roomListingId` 分支在非 `activeOnly` 情境下甚至是 `pricingRuleRepository.findAll()` 全表掃描後在記憶體中過濾；對應的 repository 方法（`findByRoomListingIdAndIsActiveTrue`/`findByListingIdAndIsActiveTrue`/`findByListingId`）本身也完全沒有 `tenantId` 參數。本檔其餘 5 個寫入方法（`createRule`/`updateRule`/`deleteRule`/`setCalendarPrice`/`overridePrice`）皆已在 `DEF-242`（Sprint 181）補上 `checkListingTenantOwnership`/`checkRuleTenantOwnership`，唯獨這個讀取/列表方法當時未被同一輪修復觸及——是「修一半」的典型案例。

**攻擊情境**：任一持有 `room:read`/`product:read` 權限的已登入使用者（含 GUEST 角色）呼叫 `GET /v2/dashboard/pricing/rules?roomListingId=<他租戶的 roomListingId>`，即可取得該租戶完整的定價規則清單（規則名稱、類型、優先序、折扣金額/百分比等 `config` JSON 設定、有效期間）。由於 `roomListingId`/`listingId` 可經公開瀏覽端點取得，非需暴力猜測的機密值，攻擊門檻低，洩漏競爭對手的定價策略等商業敏感資訊。

**修法**：`getRules` 新增 `isSuperAdmin` 參數，在 `roomListingId`/`listingId` 非 null 的兩個分支開頭呼叫既有的 `checkListingTenantOwnership`（`DEF-242` 已建立的私有方法，直接重用）；`PricingController.getRules` 依既有慣例新增 `@AuthenticationPrincipal UserPrincipal principal` 解析 `isSuperAdmin` 並傳入。

**測試**：此方法先前完全零測試覆蓋（單元/整合皆無斷言其租戶範圍行為）。新增 `PricingServiceTest$RuleTenantOwnershipTests` 4 案例（他租戶 roomListingId/listingId 皆拒絕、本租戶放行、SUPER_ADMIN 旁路）。**連帶修復既有整合測試的測試基礎設施缺口**（比照 `DEF-242` 當時發現的同型問題）：`M12PricingProductIntegrationTest.getRules_withListingId_returnsRuleList` 原本用 `@WithMockUser`（非本專案 `UserPrincipal` 型別），新增的 `@AuthenticationPrincipal` 會解析為 null 而 NPE，改用既有的 `@WithErpSecurity` 並補上 `listingRepository` stub；`M12PricingIntegrationTest.getRules_filterByRoomListingId_returnsRules` 本已用 `@WithErpSecurity`，僅需補上 `listingRepository` stub。

**紅燈驗證**：`getRules` 新增了 `isSuperAdmin` 參數（簽章變更），比照 `DEF-242` 手法，暫時把兩處新增的 `checkListingTenantOwnership` 呼叫短路為 `if (false) { ... }`（保留簽章不動），確認新增的 2 個跨租戶拒絕案例皆轉紅；還原後轉綠。

### 3.2 DEF-256（🟠）：房源維護狀態端點跨租戶竄改（Agent 1 附帶發現）

`RoomCalendarController.markMaintenance`/`unmarkMaintenance`（`POST`/`DELETE /v2/dashboard/rooms/{roomListingId}/maintenance`，僅要求 `room:update` 權限）與對應的 `RoomCalendarService` 方法，先前完全沒有任何租戶擁有權檢查。任一持有 `room:update` 權限者（任意租戶的 SELLER/HOST/STORE_OWNER/STORE_STAFF）皆可對**他租戶**的 `roomListingId` 標記維護狀態——這會：(1) 破壞該房源的可訂性（AVAILABLE/BLOCKED/BOOKED 全部被改為 MAINTENANCE）；(2) 若該日期已被預訂，連帶把對應 `Booking.statusFlags` 標記 `under_maintenance=true`，出現在受害租戶的 Admin Dashboard MaintenanceWarnings 列表，造成營運混亂與錯誤警示。此為真實可達、具破壞性的跨租戶 IDOR，且路徑變數 `roomListingId` 同樣可經公開瀏覽端點取得。

**修法**：比照本專案既有的 `checkListingTenantOwnership` 慣例（`PricingService`/`BookingService` 已有各自的私有實作），在 `RoomCalendarService` 新增同名私有方法（`ListingRepository` 已是既有注入的依賴，無需新增）；`markMaintenance`/`unmarkMaintenance` 新增 `isSuperAdmin` 參數並在方法開頭呼叫此檢查；`RoomCalendarController` 依既有慣例新增 `@AuthenticationPrincipal UserPrincipal principal` 解析並傳入。

**測試**：新增 `RoomCalendarServiceTest` 3 案例（`markMaintenance`/`unmarkMaintenance` 對他租戶皆拒絕、本租戶放行）。既有 7 個 `markMaintenance`/`unmarkMaintenance` 測試（聚焦於維護狀態轉換邏輯本身，非授權）改為以 `isSuperAdmin=true` 呼叫，略過新增的擁有權檢查，維持原本的測試意圖不變。

**紅燈驗證**：同樣新增了 `isSuperAdmin` 參數，採用短路技巧驗證：暫時把兩處新增的 `checkListingTenantOwnership` 呼叫短路為 `if (false) { ... }`，確認新增的 2 個跨租戶拒絕案例皆轉紅；還原後轉綠。

### 3.3 附帶清理：刪除孤兒 Payment DTO（無風險程式碼衛生）

Agent 1 掃描發現 `dto/payment/CreatePaymentRequest.java`/`MockPaymentRequest.java`/`RefundPaymentRequest.java` 三個類別在全庫（含測試）零呼叫點——真正上線的付款端點使用 `PaymentDto.PaymentRequest`/`RefundRequest`（金額由伺服器端計算，非客戶端傳入，已於 `DEF-019`/`DEF-023`/`DEF-243` 修復擁有權與金額上限檢查）。`CreatePaymentRequest.amount` 正是教科書等級的「客戶端指定付款金額」欄位，若未來重構時不慎把這組死 DTO 接到真實端點會立即重現金流竄改漏洞。三檔皆確認零引用後直接刪除，非本輪安全修復項目，純屬降低未來風險的程式碼衛生清理。

## 4. 測試總覽

- **紅燈先行**：`DEF-255`/`DEF-256` 皆因新增 `isSuperAdmin` 參數（簽章變更），採用短路新增檢查邏輯的技巧驗證，理由見 §3.1/§3.2。
- **新增測試**：`PricingServiceTest` +4、`RoomCalendarServiceTest` +3。單元測試合計 +7。
- **既有測試修正（因簽章變更需更新呼叫點，非行為缺陷）**：`RoomCalendarServiceTest` 既有 7 個測試補上 `isSuperAdmin=true`。
- **既有整合測試修正（測試基礎設施缺口，同型於 DEF-242 當時發現的問題）**：`M12PricingProductIntegrationTest.getRules_withListingId_returnsRuleList`（`@WithMockUser`→`@WithErpSecurity`）、`M12PricingIntegrationTest.getRules_filterByRoomListingId_returnsRules`（補 `listingRepository` stub）、`M17MaintenanceWorkflowIntegrationTest` 3 案例（`@WithMockUser`→`@WithErpSecurity`，新增 `@MockBean ListingRepository` 並補 stub；此檔在初次規劃修復範圍時被遺漏，靠完整 `mvn verify` 才發現，詳見 §5 過程插曲）。

## 5. 驗證結果

`mvn -o clean verify`（`make test-db-up` 真實 postgres/redis）：**BUILD SUCCESS**，**1629 個單元測試（+7）+ 486 個整合測試（持平），0 failed**；checkstyle（main+test）**0 違規**；PMD 無新增問題。

**過程插曲**：第一次跑完整 `mvn verify` 時，`M17MaintenanceWorkflowIntegrationTest` 3 個案例回歸（500，`@WithMockUser` 的 principal 非本專案 `UserPrincipal` 型別，新增的 `@AuthenticationPrincipal` 解析為 null 而 NPE）——與 `DEF-242`（Sprint 181）當時發現的測試基礎設施缺口完全同型，且**這是本次 `git grep` 排查時完全沒注意到的第三個受影響測試檔**（原本只找到並修復了 `M12PricingIntegrationTest`/`M12PricingProductIntegrationTest` 兩個，遺漏了 `M17MaintenanceWorkflowIntegrationTest`，證明「新增 `@AuthenticationPrincipal` 前用 grep 找出所有呼叫該 Controller 方法的既有測試」這件事，光憑記憶或局部搜尋範圍不夠全面，必須靠完整 `mvn verify` 才能可靠抓到全部影響面）。修法比照既有慣例：改用 `@WithErpSecurity` 並補上 `listingRepository` stub。修復後重跑確認 486/486 全綠。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-255`/`DEF-256`：狀態記為「✅ 已修復（Sprint 185）」，發現與修復同輪完成。
- 新增 `DEF-257`（🟡，不排入排程）：`PricingService`/`OrderService`/`BookingService`/`PaymentService`/`PaymentStateService` 的核心狀態轉換（下單、付款、退款、取消、定價規則異動）皆未寫入 `AuditService` 稽核紀錄，僅依賴各自的 `log.info`。目前有稽核覆蓋的操作類型明顯偏向「Admin 審核類」（租戶審核、功能開關、結算審核、退貨審核等），金流/訂單生命週期本身幾乎沒有稽核軌跡。未直接修復理由：涉及至少 5 個 Service 的多處改動，屬於「補齊稽核覆蓋率」的獨立專案性質工作而非單點缺陷修復，且 `AuditService` 本身的完整性（append-only、查詢權限）已確認無缺陷——這是覆蓋率缺口而非安全漏洞本身。建議另闢專門 Sprint 處理。
- 新增 `DEF-258`（🟢，不排入排程）：`TenantUpdateRequest.status` 是死欄位（`TenantService.applyTenantUpdates` 完全不讀取），但命名容易誤導——與 `AdminDto.TenantStatusUpdateRequest`（SUPER_ADMIN 專屬、值域鎖定的合法管理欄位）同名。若未來比照同檔案 `businessType` 欄位先例（先前也是死欄位、後來被接上寫入邏輯）直接接上此欄位而未加驗證，`STORE_OWNER` 就能繞過審核自我核准/終止租戶狀態。目前前端零呼叫點，無可利用路徑，僅記錄提醒未來維護者。
- 新增 `DEF-259`（🟢，不排入排程）：`UpdateKnowledgeArticleRequest.scheduledPublishAt` 為死欄位（`KnowledgeBaseService.updateArticle` 不讀取，真正排程發布走獨立端點），且整個 `updateKnowledgeArticle` 更新路徑前端零呼叫點。無可利用路徑，僅記錄。

## 7. 誠實揭露總結

- 本輪兩個 agent 分別針對「DTO 欄位」與「稽核/匯出範圍」兩個不同角度調查，卻**各自獨立在任務範圍之外發現了一個真實的跨租戶 IDOR**（`DEF-255`/`DEF-256`）——這提示：即使掃描角度本身沒有直接命中，讓 agent 帶著明確的「這是什麼樣的缺陷」背景知識去審查程式碼，仍然有很高機率意外發現同一系統性模式（本輪兩者皆是「先前的租戶擁有權修復只覆蓋了部分方法，漏了另一個方法」）在全庫的其他角落重演。這也再次印證了 Sprint 181/182 已多次得出的結論：任何一輪「補上租戶擁有權檢查」的修復，都應該同時檢查同一個 Service/Controller 底下是否還有其他兄弟方法遺漏了同一道檢查，而非修完立刻視為該類問題已解決。
- `DEF-255` 與 `DEF-256` 是同一種「寫入路徑已修、讀取/查詢路徑遺漏」與「寫入路徑已修、另一個平行的寫入端點遺漏」的變形，建議未來新掃描角度可以更明確地聚焦：「已經在某個 Service 修過 DEF-XXX 租戶擁有權檢查的，其餘所有 public 方法是否都補齊了」，而非只看新的 Service。
- `DEFERRED_ITEMS_TRACKER.md` 目前累積 `DEF-235`/`DEF-236`/`DEF-241`/`DEF-248`/`DEF-252`/`DEF-253`/`DEF-257`/`DEF-258`/`DEF-259` 九項低優先級「不排入排程」項目，其中 `DEF-257`（稽核覆蓋率）規模較大，建議未來若排入排程應獨立成一個 Sprint 而非隨手夾帶。Sprint 186 開工時仍需自選新掃描角度。
