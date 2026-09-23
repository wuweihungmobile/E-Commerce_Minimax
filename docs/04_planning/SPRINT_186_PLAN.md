# Sprint 186 Plan — 已修復 Service 全方法覆蓋複查（付款狀態查詢 + 知識庫 slug 查詢跨租戶洩漏）（DEF-260）

**Sprint**: Sprint 186
**日期**: 2026-09-23

## 1. 起點

Sprint 185 修復 `DEF-255`/`DEF-256` 後，`DEFERRED_ITEMS_TRACKER.md` 累積 9 項低優先級「不排入排程」項目。Sprint 185 結尾的誠實揭露明確指出：本輪連續兩次發現的缺陷，都是「同一 Service/Controller 底下，某個方法已修過租戶擁有權檢查，但兄弟方法遺漏了同一道檢查」的變形（`PricingService.getRules()` 遺漏 `DEF-242` 的檢查、`RoomCalendarController.markMaintenance` 遺漏既有慣例）。本輪選定的角度直接針對這個發現：系統性複查全庫「曾經因租戶擁有權缺陷被回報並修復過」的 Service，逐一核對其**所有** public 方法（而非只看曾被回報的那幾個）是否都補齊了一致的檢查。

## 2. 掃描方法與結果

派出 2 個背景唯讀調查 agent，分別複查不同的 Service 群組：

- **Agent 1（訂單/金流/物流/預訂/結算）**：複查 `OrderService`（10 個 public 方法）、`LogisticsService`（7 個）、`PaymentService`（3 個）、`PaymentStateService`（10 個）、`BookingService`（11 個）、`SettlementReviewer`/`SettlementReversalService`/`TransferService`/`SettlementGenerator`/`SettlementAdjustmentService`（共 18 個）。找到 🟠 `PaymentService.getPaymentStatus(paymentId)` 完全沒有擁有權檢查——與同檔案內已修復的 `processOrderPayment`（`DEF-019`）、`processBookingPayment`（`DEF-023`）、`processRefund`（`DEF-243`）形成明顯不對稱。另發現 `TransferService` 兩個方法屬於「目前無可利用路徑，但自身零防護、全靠呼叫端」的防禦縱深觀察，評估後不列入本輪修復。其餘 5 個 Service 共 46 個 public 方法逐一複查後確認皆有一致的擁有權檢查。
- **Agent 2（知識庫/CMS 貼文/ERP/退貨）**：複查 `KnowledgeBaseService`（18 個）、`PostService`/`PostCategoryService`（15 個）、`CmsService`（11 個）、`PurchaseOrderService`/`SupplierService`/`InventoryService`/`StockMovementService`/`ProductSkuService`（22 個）、`ReturnRequestService`（8 個）。找到 🟠 `KnowledgeBaseService.getArticleBySlug(slug)` 完全沒有租戶過濾——本類別其餘所有依 ID/slug 查詢的方法（含姊妹方法 `getCategoryBySlug`）皆一致以 `getCurrentTenant()` 過濾，唯獨這支被遺漏。其餘 73 個 public 方法逐一複查後確認皆有一致的擁有權檢查。

兩個 agent 合計複查 105 個 public 方法，找到 2 個真實缺陷，皆為「同一種模式（兄弟方法遺漏檢查）在不同 Service 的再現」。

## 3. 修復內容

### 3.1 DEF-260a：`PaymentService.getPaymentStatus` 跨租戶/跨使用者付款狀態洩漏

`GET /v2/payments/{paymentId}` 僅要求 `@PreAuthorize("hasAuthority('order:read') or hasAuthority('booking:read')")`——查 `RolePermissionMapping.java`，這兩個權限碼幾乎所有已登入角色（BUYER、SELLER、HOST、STORE_OWNER、STORE_STAFF、ADMIN、SUPER_ADMIN）皆持有，等同任何登入帳號。`PaymentService.getPaymentStatus(paymentId)` 內部直接 `paymentRepository.findById(paymentId)` 後回傳狀態，完全沒有呼叫同檔案內既有的 `checkOrderPaymentOwnership`/`checkBookingPaymentOwnership`（供 `processOrderPayment`/`processBookingPayment`/`processRefund` 使用）。任何使用者只要取得任一 `paymentId`（UUID），即可查得該筆付款的 `status`/`transactionId`/`updatedAt`，不論屬於哪個租戶或哪個買家。

**修法**：`processRefund` 既有的 `resolveAndAuthorizeRefundTarget` 私有方法（解析 order/booking 並套用擁有權檢查）正是 `getPaymentStatus` 所需的邏輯，直接重用。因為此方法現在同時供退款與狀態查詢兩種不同語意使用，改名為 `resolveAndAuthorizePaymentTarget`（`RefundTarget` 容器類別同步改名為 `PaymentTarget`），避免「用於狀態查詢的方法卻叫 Refund」的命名誤導。`getPaymentStatus` 在查得 `Payment` 後呼叫此方法即完成擁有權檢查（買家限本人訂單/預訂、ROLE_ADMIN/ROLE_SUPER_ADMIN 放行，與 `processOrderPayment` 等既有方法語意完全一致）。

**測試**：此方法先前完全零測試覆蓋。在既有的 `PaymentServiceOwnershipTest`（`DEF-019`/`DEF-023` 既有測試檔）新增 3 案例（他人查詢拒絕、本人查詢放行、admin 查詢放行），比照該檔案既有的測試手法與命名慣例。

**紅燈驗證**：方法簽章未變（`getPaymentStatus(UUID)`），`git stash` 整檔還原 `PaymentService.java` 驗證，修復前 `getPaymentStatus_otherUser_throwsE1007` 確實轉紅（未拋出任何例外，直接回傳他人付款狀態）。

### 3.2 DEF-260b：`KnowledgeBaseService.getArticleBySlug` 跨租戶知識庫文章洩漏

`GET /v2/knowledge/slug/{slug}`（`@PreAuthorize("hasAuthority('knowledge:read')")`）先前呼叫 `articleRepository.findPublishedBySlug(slug)`，該查詢只用 `slug` + `status='PUBLISHED'` 過濾，完全沒有 tenant 條件。查 `RolePermissionMapping.java`，knowledge 模組被明確定性為「租戶範圍的店家後台內容管理模組」，`knowledge:read` 由 `STORE_OWNER`/`STORE_STAFF`/`ADMIN` 持有——包含最低信任等級的店鋪員工。任一租戶的 `STORE_STAFF` 只要取得他租戶已發布文章的 slug，即可讀到該租戶知識庫文章的完整內容（可能含內部政策、業務流程等文件），繞過知識庫模組的租戶隔離設計，且本類別姊妹方法 `getCategoryBySlug` 早已正確使用 `getCurrentTenant()` + `findByTenantIdAndSlug` 過濾，形成同一類別內不一致的慣例。

**修法**：比照 `getCategoryBySlug` 既有慣例，改用 `getCurrentTenant()` + 既有的 `articleRepository.findByTenantIdAndSlug(tenantId, slug)`（此方法本已存在，供其他方法使用），再以 `.filter(status == PUBLISHED)` 保留原本「僅回傳已發布文章」的語意（不新增查詢方法，不改變既有行為，僅補上租戶範圍）。原本專用的 `findPublishedBySlug` 查詢方法因此無其他呼叫點，一併移除避免留下死碼。

**測試**：新增 2 案例（他租戶 slug 查無記錄拒絕、本租戶但未發布 DRAFT 狀態拒絕），既有的 `getArticleBySlug_found_returnsDto` 案例同步改用新的 repository 方法 stub。

**紅燈驗證**：方法簽章未變（`getArticleBySlug(String)`），`git stash` 同時還原 `KnowledgeBaseService.java` 與 `KnowledgeArticleRepository.java`（兩檔需一併還原，否則單獨還原前者會因後者已移除 `findPublishedBySlug` 而編譯失敗）驗證，修復前 2 個新案例皆轉紅（`UnnecessaryStubbingException`——證實舊程式碼完全不會呼叫 `findByTenantIdAndSlug`，走的是不分租戶的 `findPublishedBySlug`）。

## 4. 測試總覽

- **紅燈先行**：兩者皆因方法簽章未變，直接用 `git stash` 做行為紅燈驗證。
- **新增測試**：`PaymentServiceOwnershipTest` +3、`KnowledgeBaseServiceTest` +2。單元測試合計 +5。
- **既有測試修正（因 repository 方法變更需更新 stub，非行為缺陷）**：`KnowledgeBaseServiceTest.getArticleBySlug_found_returnsDto`。
- **完整負向複查記錄**：`OrderService`/`LogisticsService`/`PaymentStateService`/`BookingService`/Settlement 套件（46 個方法）與 `PostService`/`PostCategoryService`/`CmsService`/ERP 套件/`ReturnRequestService`（73 個方法），共 119 個 public 方法逐一複查後確認擁有權檢查一致，本輪無對應修復項目。

## 5. 驗證結果

`mvn -o clean verify`（`make test-db-up` 真實 postgres/redis）：**BUILD SUCCESS**，**1634 個單元測試（+5）+ 486 個整合測試（持平），0 failed**；checkstyle（main+test）**0 違規**；PMD 無新增問題。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-260`：狀態記為「✅ 已修復（Sprint 186）」，涵蓋 `PaymentService.getPaymentStatus`（3.1）與 `KnowledgeBaseService.getArticleBySlug`（3.2）兩個同一模式的獨立缺陷，發現與修復同輪完成。
- `TransferService.createTransferForStatement`/`getTransfersForTenant` 的防禦縱深觀察（目前無可利用路徑）不新增 DEF 編號，僅記錄於本文件供未來評估。

## 7. 誠實揭露總結

- 本輪驗證了 Sprint 185 結尾提出的假說：「已修過租戶擁有權檢查的 Service，其餘方法是否都補齊了」這個角度確實持續有效——連續第三輪（Sprint 185 的 `DEF-255`/`DEF-256`、本輪的 `DEF-260a`/`DEF-260b`）都在這個角度下找到真實缺陷，且兩次都精準命中「讀取方法被寫入方法的修復遺漏」這個具體子模式。這強烈建議：任何未來修復租戶擁有權缺陷時，除了修復當下被回報的方法，應同時對同一 Service/Controller 的**全部** public 方法做一次快速核對，而非視為單點修復結案。
- 本輪同時也是一次重要的**正向驗證**：119 個複查過的方法中，115 個確認已有一致的擁有權檢查——證明過去多輪 Sprint（`DEF-019`/`023`/`024`/`027`/`031`/`036`/`040`/`041`/`188`/`231`/`239`/`243` 等）的修復品質整體是扎實的，並非「每個 Service 都有系統性遺漏」，而是少數幾個具體方法的個案遺漏。這有助於判斷未來是否還需要持續投入這個角度——目前證據顯示遺漏率正在下降（Sprint 185 兩個 Service 各遺漏 1 個方法／Sprint 186 兩個 Service 各遺漏 1 個方法，皆為個位數比例），可考慮下一輪換一個全新角度，而非無限期延續本角度的複查。
- `DEFERRED_ITEMS_TRACKER.md` 目前累積 `DEF-235`/`DEF-236`/`DEF-241`/`DEF-248`/`DEF-252`/`DEF-253`/`DEF-257`/`DEF-258`/`DEF-259` 九項低優先級「不排入排程」項目。Sprint 187 開工時需自選全新掃描角度——建議候選方向：Sprint 184/185 皆已提及但尚未執行的「稽核覆蓋率補齊」（`DEF-257`，但需獨立成 Sprint 而非隨手夾帶）、或轉向前後端契約漂移／併發競態等本 session 較早期曾系統性掃描過、但近期未再複查的角度是否有新回歸。
