# Sprint 161 Plan — 延伸 Sprint 160 §7 誠實揭露：全庫「裝飾性 `@Valid`／未攔截 enum `valueOf()`」防護缺口全掃

**Sprint**: Sprint 161
**日期**: 2026-09-15

---

## 1. 起點

[SPRINT_160_PLAN.md](SPRINT_160_PLAN.md) §7 誠實揭露列出「未逐一複查 `TenantController.updateMemberRole` 以外，全庫是否還有其他『驗證標註存在但對該型別實際無效』的裝飾性 `@Valid` 案例」。本輪（使用者要求「繼續完成任務」）接續此角度查證，過程中發現範圍比原設想更廣：不只是裝飾性 `@Valid`，而是一整個「Request DTO 的列舉型欄位是原生 `String`，Service／Controller 直接呼叫 `Enum.valueOf()` 卻未包 `try/catch`」的防護缺口家族——命中時會被 `GlobalExceptionHandler` 的 catch-all 轉成 500，而非正確的 400/422。

---

## 2. 查證方法與範圍

### 2.1 裝飾性 `@Valid`（零約束 DTO）掃描

全庫列舉 90 處 `@Valid @RequestBody` 標註，逐一檢查對應 DTO 類別（含巢狀類別）是否含任何 Bean Validation 約束標註（`@NotNull`/`@NotBlank`/`@Size`/`@Pattern`/…）。零約束的候選：

| DTO | 欄位 | 結論 |
|-----|------|------|
| `AddMemberRequest` | `role` | ✅ CLEAN——`TenantService.parseInviteRole` 已用 `try/catch` 做列舉白名單防護 |
| `PurchaseOrderUpdateRequest` | `notes`/`expectedDeliveryDate` | ✅ CLEAN——皆非列舉欄位，無解析風險 |
| `SupplierUpdateRequest` | `status` | 🔴 **發現真缺陷，見 §3.1** |
| `UpdateMediaRequest` | `categoryId`/`tags`/`altText`/`title` | ✅ CLEAN——皆非列舉欄位 |
| `BookingDto.UpdateRequest` | 多為日期/字串欄位 | ✅ CLEAN——無列舉欄位 |
| `CmsDto.UpdatePageRequest` | `featuredImageUrl` 無協定驗證 | ⚠️ 已是既有 `DEF-199`（零前端呼叫點死路徑，Sprint 156 發現、Sprint 158 已重新查證維持成立），非新發現 |
| `M15Dto.CreateCategoryRequest`/`UpdateCategoryRequest` | `name`/`description`/`sortOrder` | ✅ CLEAN——無列舉欄位 |
| `PricingDto.UpdateRuleRequest` | 多為日期/`Map`/字串欄位 | ✅ CLEAN——無列舉欄位 |

### 2.2 延伸掃描：全庫未攔截的 `Enum.valueOf()` 呼叫點

`SupplierUpdateRequest.status` 缺陷的根因（Service 直接對 Request 傳入的原生 `String` 呼叫 `.valueOf()`、未 `try/catch`）promptly 是否為孤例？全庫 `grep ".valueOf("` 排除 `Long`/`Integer`/`Boolean`/`Double`/`BigDecimal` 等基本型別，逐一追蹤每個呼叫點的輸入來源與是否有防護：

| 呼叫點 | 輸入來源 | 結論 |
|--------|----------|------|
| `OrderService.createOrderFromCart`（`orderType`） | Request body（`OrderDto.CreateRequest.orderType`，僅 `@NotNull`，無白名單） | 🔴 缺陷，見 §3.2 |
| `OrderService.getTenantOrders`（`status` 篩選） | Query param | 🔴 缺陷，見 §3.3 |
| `OrderService.updateOrderStatus`（`targetStatus`） | Request body | ✅ CLEAN——呼叫 `valueOf` 前已先過 `OrderStateMachine.canTransition`，其內部用 `switch` 對已知合法字面值做白名單比對，非法字串必落入 `default → denied`，早於 `valueOf` 前即以 `E_5001` 拒絕 |
| `RoomService` 更新方法（`status`） | Request body（`RoomDto.UpdateRequest.status`，原生 `String`） | 🔴 缺陷，見 §3.4 |
| `ProductService` 更新方法（`status`） | Request body（`ProductDto.UpdateRequest.status`，原生 `String`） | 🔴 缺陷，見 §3.4 |
| `AdminService.getUsers`（`role` 篩選） | Query param | 🔴 缺陷，見 §3.5 |
| `AdminService.updateTenantStatus` | Request body | ✅ CLEAN——已有 `try/catch → E_9000` |
| `NotificationTemplateService`（`notificationType`/`channel`，`getTemplates`/`createTemplate`/`updateTemplate` 共 6 處） | Request body/查詢條件 | 🔴 缺陷，見 §3.6 |
| `NotificationPreferenceService`（`parseNotificationType`/`parseChannel`） | 內部呼叫 | ✅ CLEAN——既有 `try/catch → E_9000`（本輪修復 `NotificationTemplateService` 時的直接參考先例） |
| `KnowledgeBaseService.updateKnowledgeArticle`（`status`） | Request body（`UpdateKnowledgeArticleRequest`） | ⚠️ 死路徑——`grep`/`find` 確認前端 `updateKnowledgeArticle()` service 函式已定義但全庫零呼叫點，與既有 `DEF-200`（`createKnowledgeArticle` 零呼叫點）同一功能未完工判準，不修復、只記錄 |
| `StockMovementService`（`movementType`） | Request body | ✅ CLEAN——已有 `try/catch → E_7005` |
| `PostController.getPosts`（`status` 篩選） | Query param | 🔴 缺陷，見 §3.7 |
| `PostController.getMediaList`（`fileType` 篩選） | Query param | 🔴 缺陷，見 §3.7 |
| `LogisticsController.updateLogisticsStatus` | Query param | ⚠️ 死路徑——方法註解本身明載「Mock - for testing」，`grep` 全庫確認零前端呼叫點，不修復、只記錄 |
| `ListingController.getListings`（`type`，2 處） | Query param（公開商品/房源搜尋，`@PreAuthorize("hasAuthority('product:read') or hasAuthority('room:read')")`，一般買賣家帳號皆可觸及） | 🔴 缺陷，見 §3.8 |
| `ChatService`/`PaymentService`/`CmsService`（Banner/Page）/`LogisticsService.createLogistics`/`PricingService` 等 `.valueOf(request.getX().name())` 呼叫 | Request body，但 DTO 欄位本身**已是強型別 enum**（非原生 `String`） | ✅ 不屬本次缺陷家族——Jackson 反序列化時遇到非法列舉值會直接拋 `HttpMessageNotReadableException`，同樣落入 catch-all 變 500，但這是**全庫共通的 JSON 反序列化錯誤處理**課題（任何 enum 型別欄位皆然），與本輪鎖定的「原生 `String` 欄位＋手動 `valueOf()`」缺陷成因不同，範圍外處理（見 §6） |
| `JwtAuthenticationFilter`/`UserRoleConverter`/`NotificationConsumerService`/`RateLimitFilter` | JWT claims／DB 既有值／MQ 訊息（伺服器內部信任邊界，非直接使用者輸入） | ✅ 不在本次「使用者可直接觸發」風險範圍內，不修復 |

---

## 3. 修復內容

所有修復均採同一模式：`try { Enum.valueOf(rawString); } catch (IllegalArgumentException e) { throw new BusinessException(ErrorCode, "Invalid X: " + rawString); }`，`ErrorCode` 優先重用既有語意相符的錯誤碼（`E_3001`「無效的刊登類型」已有 `DashboardListingController` 先例、`E_5001`「無效的訂單狀態」為 `OrderService` 既有錯誤碼、`E_9000`「驗證錯誤」為 `NotificationPreferenceService`/`AdminService.updateTenantStatus` 既有的「無效列舉字串」通用防護慣例），僅在 ERP 模組因既有慣例是「每個實體各自的無效狀態碼」（`E_7002` 無效採購單狀態、`E_7005` 無效庫存調整）而新增 `E_7010`。

### 3.1 `DEF-204`：`SupplierService.updateSupplier` 非法 `status` → 500

`Supplier.SupplierStatus.valueOf(request.getStatus().toUpperCase())` 未包 `try/catch`。新增 `ErrorCode.E_7010`（無效的供應商狀態，422 Unprocessable Entity，比照同模組 `E_7002`）。紅燈先行：`SupplierServiceTest` 新增案例修復前實際拋出 `IllegalArgumentException`（未被攔截），修復後正確拋出 `BusinessException(E_7010)`。

### 3.2 `DEF-205`：`OrderService.createOrderFromCart` 非法 `orderType` → 500

`Listing.ListingType.valueOf(request.getOrderType())` 未包 `try/catch`。重用既有 `ErrorCode.E_3001`（無效的刊登類型，422，與 `DashboardListingController.createListing` 既有防護完全同義）。`OrderServiceTest` 新增案例。

### 3.3 `DEF-206`：`OrderService.getTenantOrders` 非法 `status` 篩選 → 500

`Order.OrderStatus.valueOf(status)` 未包 `try/catch`。重用既有 `ErrorCode.E_5001`（無效的訂單狀態，422，同檔案內 `updateOrderStatus` 已用此碼）。`OrderServiceTest` 新增案例。

### 3.4 `DEF-207`：`RoomService`/`ProductService` 更新 `Listing.status` 非法值 → 500

兩個 Service 對同一個 `Listing.ListingStatus` 欄位有完全相同的未攔截呼叫（`RoomService` 更新房源、`ProductService` 更新商品，皆透過 `Listing` 共用實體）。新增 `try/catch → BusinessException(E_9000)`（比照 `NotificationPreferenceService` 既有的通用「無效列舉字串」慣例，因無更貼切的既有 `Listing` 狀態專屬碼）。`RoomServiceTest`/`ProductServiceTest` 各新增案例。

### 3.5 `DEF-208`：`AdminService.getUsers` 非法 `role` 篩選 → 500

`User.UserRole.valueOf(role)` 原本寫在 Specification lambda 內部（每次查詢執行才觸發，且未包 `try/catch`），改為在建構 lambda 前先行解析並 `try/catch → BusinessException(E_9000)`（比照同檔案 `updateTenantStatus` 既有防護模式）。`AdminServiceTest` 新增案例。

### 3.6 `DEF-209`：`NotificationTemplateService` 6 處未攔截的 `notificationType`/`channel` 解析 → 500

`getTemplates`（查詢篩選）、`createTemplate`、`updateTemplate` 共 6 個呼叫點皆直接呼叫 `NotificationType.valueOf(...)`/`NotificationChannel.valueOf(...)`，無任何防護。新增私有 `parseNotificationType`/`parseChannel` 輔助方法（直接比照同為 notification 領域、已有此防護的 `NotificationPreferenceService` 既有寫法與 `E_9000` 慣例），6 個呼叫點統一改用輔助方法，避免重複 `try/catch` 樣板碼。`NotificationTemplateServiceTest` 新增案例驗證 `getTemplates` 的兩個欄位。

### 3.7 `DEF-210`：`PostController` `status`/`fileType` 查詢篩選非法值 → 500

`getPosts`（CMS 貼文列表）、`getMediaList`（媒體庫列表）皆為前端實際在用的活功能（`frontend/src/app/cms/media/page.tsx`、部落格貼文管理頁），查詢篩選的 `status`/`fileType` 皆為原生 `String` 且直接 `.valueOf()`，未包 `try/catch`。新增 `try/catch → BusinessException(E_9000)`。新增 `PostControllerFilterValidationTest`（獨立、輕量的 `@SpringBootTest`＋`MockMvc` 測試類別，比照 Sprint 160 `StripeWebhookReachabilityTest` 的模式，不涉入既有 `PostControllerE2ETest` 的大型有序測試套件以避免耦合共享狀態）。

### 3.8 `DEF-211`：`ListingController.getListings` 非法 `type` 查詢參數 → 500

公開商品/房源搜尋端點（`GET /v2/listings`，一般買賣家帳號皆可觸及）的 `type` 查詢參數兩處呼叫皆未包 `try/catch`。抽出 `parseListingType` 私有方法，重用 `ErrorCode.E_3001`（與 `DashboardListingController` 既有防護完全同義）。`ListingControllerE2ETest` 新增案例（沿用既有檔案內 `@MockBean ListingRepository` + `@WithMockUser` 模式）。

---

## 4. 範圍外（刻意不做，如實揭露）

- **`KnowledgeBaseService.updateKnowledgeArticle` 的 `status` 解析缺陷**：與既有 `DEF-200`（`createKnowledgeArticle` 零前端呼叫點）同一個「知識庫文章管理 UI 尚未真正開發」死路徑，`updateKnowledgeArticle()` 前端 service 函式已定義但全庫零呼叫點，不修復、只記錄（見 §6 `DEF-212`）。
- **`LogisticsController.updateLogisticsStatus`**：方法註解本身明載「Mock - for testing」，全庫零前端呼叫點，不修復。
- **`.valueOf(request.getX().name())` 家族**（`ChatService`/`PaymentService`/`CmsService` Banner・Page/`LogisticsService.createLogistics`/`PricingService`）：這些 DTO 欄位本身已是強型別 enum，Jackson 反序列化階段遇到非法列舉字面值即拋 `HttpMessageNotReadableException`，同樣落入 500——但這是**全庫共通、與 enum 型別請求欄位本身相關**的 JSON 反序列化錯誤處理課題，範圍遠大於本輪鎖定的「原生 `String` 欄位＋手動 `valueOf()`」家族，且是否要為此新增全域 `@ExceptionHandler(HttpMessageNotReadableException.class)` 屬於架構層級決策（是否要讓所有反序列化錯誤統一變成 400，可能連帶影響其他非預期情境的錯誤分類），超出本輪範圍，留待未來評估。
- **`JwtAuthenticationFilter`/`UserRoleConverter`/`NotificationConsumerService`/`RateLimitFilter` 的 `.valueOf()` 呼叫**：輸入來源為 JWT claims／資料庫既有值／MQ 訊息，屬伺服器內部信任邊界內的資料，非使用者可直接任意帶入的欄位，不在本輪「使用者可直接觸發 500」的風險範圍內。
- **是否要為 `GlobalExceptionHandler` 新增全域 `IllegalArgumentException` 兜底 handler**（一次性解決本輪與未來所有同型呼叫點）：評估後**刻意不採用**——這會偏離本專案既有慣例（每個呼叫點各自 `try/catch` 對應到語意精確的 `ErrorCode`，見 `TenantService.updateMemberRole`/`parseInviteRole`、`NotificationPreferenceService`、`StockMovementService`、`AdminService.updateTenantStatus` 等既有前例），且 `IllegalArgumentException` 在其他脈絡（非列舉解析）也可能被拋出，全域兜底可能誤將非預期的程式錯誤靜默降級為 400，掩蓋真正的伺服器端錯誤。

---

## 5. 驗證結果

- `SupplierServiceTest`/`OrderServiceTest`/`RoomServiceTest`/`ProductServiceTest`/`AdminServiceTest`/`NotificationTemplateServiceTest`/`PostControllerFilterValidationTest`/`ListingControllerE2ETest` 新增共 10 個測試案例，紅燈先行逐一證實修復前為未攔截的 `IllegalArgumentException`（或 500 回應），修復後正確拋出對應 `BusinessException`/HTTP 狀態碼。
- `checkstyle:check`（main + test）：0 違規。
- `mvn -o verify` **1351 個單元測試（+9）+ 479 個整合測試（+1），0 failed**，PMD 無新增違規，`BUILD SUCCESS`（8m33s）。（`PostControllerFilterValidationTest` 命名未匹配 `*IntegrationTest`/`*E2ETest` 排除樣式，實際跑在 surefire 單元測試階段而非 failsafe，故計入單元測試 +9 而非整合測試。）
- 本輪未執行 `make validate-e2e`（純後端錯誤處理路徑修正，未變更任何前端可觀察行為或既有 API 回應結構，僅將部分端點的錯誤情境從 500 改為正確的 4xx，不影響任何 E2E 既定 happy path 斷言）。

---

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-204`～`DEF-211`（已修復，見 §3 對應小節）。
- 新增 `DEF-212`（不排入排程，純記錄）：`KnowledgeBaseService.updateKnowledgeArticle` 的 `status` 解析缺陷與 `LogisticsController.updateLogisticsStatus`，皆為零前端呼叫點死路徑，同 `DEF-200`/既有 Mock 樣式判準。

---

## 7. 誠實揭露總結

- 本輪起點是 Sprint 160 §7 的「未逐一複查其他裝飾性 `@Valid` 案例」，範圍在查證過程中自然擴大為「全庫未攔截 enum `valueOf()`」家族——比原設想的「裝飾性 `@Valid`」窄範圍更廣，兩者根因不同（前者是驗證標註本身無效，後者是完全沒有任何驗證標註，但共同外顯症狀都是「非法輸入未被正確攔截」）。
- 刻意排除了「DTO 欄位本身已是強型別 enum、僅 Jackson 反序列化層級會 500」的更大家族（§4），因為修復方式（全域 `HttpMessageNotReadableException` handler）是架構層級決策而非本輪的逐點防禦性修復可比擬，留待未來單獨評估是否要做。
- 未驗證正式生產環境是否已因這些缺口實際發生過使用者回報的 500 錯誤（本輪僅能確認程式碼層級的缺陷與本地測試結果）。
- 未對 `AdminController`/`TenantController` 以外的其餘 Controller 做全面「query param 型別轉換錯誤」的系統性掃描（例如 `@RequestParam UUID`/`@RequestParam LocalDate` 等 Spring 內建型別轉換失敗的情境），本輪僅鎖定「enum 字串」這一種型別。
