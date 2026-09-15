# Sprint 164 Plan — 全庫分頁 `size` 參數無上限防禦性掃描（資源耗盡風險）

**Sprint**: Sprint 164
**日期**: 2026-09-15

---

## 1. 起點

`DEFERRED_ITEMS_TRACKER.md` 已無待排程項目，Sprint 161~163 三輪聚焦「非法輸入值格式（enum/型別轉換）→ 500」家族已收斂。本輪改換角度，查證另一類長期未被系統性檢視的防禦性課題：**分頁查詢的 `size` 參數是否有上限**。起因是查證 Sprint 161/162/163 過程中注意到 `OrderService.getUserOrders`/`getTenantOrders` 已有 `Math.min(size, 100)` 的既有防護（`getTenantOrders_capsPageSizeAt100` 測試），但這是否為全庫慣例、還是僅少數 Service 才有，先前從未有 Sprint 系統性驗證過。

**風險模型**：`size` 若無上限，client 可送出極大值（如 `size=999999999`）直接傳入 `PageRequest.of(page, size)`，Spring Data 的 `PageRequest` 建構子本身只驗證 `size >= 1`，**不驗證上限**，該值會直接轉為 SQL 查詢的 `LIMIT`，可能造成單次查詢回傳整張表、記憶體壓力或回應時間暴增——尤其若命中的是**公開、無需登入**的端點，任何人都能觸發，屬於未經身份門檻的資源耗盡（DoS）風險。

---

## 2. 查證方法與範圍

全庫 `grep PageRequest.of(` 逐一追蹤每個呼叫點的 `size` 引數來源，排除純字面值（如 `PageRequest.of(0, 1)`、`PageRequest.of(0, 100)`，非使用者可控）；分三輪擴大掃描範圍（`core/` Service 層 → 全 `src/main/java` 含 Controller 層 → 含三元運算子偽裝的下限防護但無上限的變形寫法），共找到 **21 處**跨 **12 個檔案**的真缺口：

| 檔案 | 方法 / 行數 | 暴露範圍 |
|------|------|------|
| `SettlementReviewer.java:146` | `getPendingReviewStatements` | Admin |
| `SettlementGenerator.java:201` | `getStatementsByTenant` | 店家 |
| `SettlementReversalService.java:147` | `getReversalCandidateStatements` | SUPER_ADMIN/CFO |
| `AdminService.java:128` | `getTenants`（含篩選） | Admin |
| `AdminService.java:262` | `getPendingApprovalPurchaseOrders` | SUPER_ADMIN |
| `AdminService.java:561` | `getUsers`（含篩選） | Admin |
| `AdminService.java:1068` | `getAuditLogs` | Admin |
| `PostService.java:256` | `getPosts`（dashboard） | 店家 |
| `PostService.java:288` | **`getPublishedPosts`（前台，公開）** | 🔴 **任何人，無需登入** |
| `cms/media/MediaService.java:87` | `getMediaList` | 店家 |
| `SupportTicketService.java:103/123/140` | `listMyTickets`/`listTenantTickets`/`listAllTickets` | 買家／店家／Admin |
| `NotificationTemplateService.java:44` | `getTemplates` | 店家 |
| `AnalyticsService.java:247` | `getRecentActivity` | 店家 |
| `ErpController.java:143/229/284` | 採購單/庫存台帳/庫存異動列表 | 店家（`STORE_OWNER`/`SELLER`） |
| `TransferController.java:51` | `getTransfers` | 一般角色/Admin |
| `ReturnRequestController.java:62/89` | `getMyReturns`/`getTenantReturns`（已有下限防護但無上限） | 買家／店家 |

其中 `PostService.getPublishedPosts`（`GET /posts`）是**唯一無 `@PreAuthorize`、明確標註「公開」的端點**，風險層級高於其餘全數需要登入的端點。

**確認 CLEAN 的既有慣例**（比對用，非本輪缺陷）：`OrderService`/`RoomService`/`ChatService`/`BookingService`/`NotificationService`/`NotificationHistoryService`/`FaqService`/`ProductService`/`CmsService`/`KnowledgeBaseService`/`ReviewService`/`BookingReviewService`/`core/media/MediaService`（注意與 `cms/media/MediaService` 是兩個不同類別）共 13 個檔案皆已有 `Math.min(size, 100)` 或等價的 `DEFAULT_PAGE_SIZE` 上限防護，證實這是既有的、已確立的專案慣例，本輪 21 處純屬遺漏補齊，非新創規範。

**刻意排除**：`NotificationService.java:196`（`PageRequest.of(0, Integer.MAX_VALUE)`）——固定字面值非使用者可控引數，且查詢範圍限於呼叫者自己的未讀通知（非跨用戶/跨租戶批量讀取），語意上是「一次取得使用者自己全部未讀」的刻意設計（供標記全已讀等批次操作使用），與其餘 21 處的「使用者可任意指定巨大 size」性質不同，不在本輪修復範圍。

---

## 3. 修復內容

### `DEF-215`：全庫 21 處分頁 `size` 參數補上上限防護

統一比照既有 `OrderService` 慣例，改為 `Math.min(size, 100)`（`ReturnRequestController` 兩處因已有 `size > 0 ? size : DEFAULT_PAGE_SIZE` 下限防護，改為 `size > 0 ? Math.min(size, 100) : DEFAULT_PAGE_SIZE`，僅補上上限、不動既有下限邏輯）。全數為單行引數包裝，不改變回傳型別、不影響既有呼叫端行為（`size <= 100` 時行為完全不變）。

---

## 4. 驗證結果

- 新增 14 個測試案例（`PostServiceTest` ×2、`AdminServiceTest` ×4、`SupportTicketServiceTest` ×3、`AnalyticsServiceTest` ×1、`NotificationTemplateServiceTest` ×1、`SettlementReviewerTest` ×1、`SettlementReversalServiceTest` ×1、`cms/media/MediaServiceTest` ×1），皆採 Mockito `ArgumentCaptor<Pageable>` 模式（比照既有 `OrderServiceTest#getTenantOrders_capsPageSizeAt100` 先例）：呼叫時傳入 `size=999999999`，斷言實際傳給 repository 的 `Pageable.getPageSize()` 等於 100。**目標測試類別（8 個檔案）獨立執行 185 passed / 0 failed**。
- `checkstyle:check`（main + test）：0 違規。
- `mvn -o verify` **1367 個單元測試（+14）+ 480 個整合測試（持平），0 failed**，PMD 無新增違規，`BUILD SUCCESS`（8m10s）。（過程中第一次執行因忘記 `make test-db-up` 而 9 個既有 `@SpringBootTest` 測試類別 ApplicationContext 載入失敗，與本輪修復內容無關，重啟測試 DB 後重跑全數通過。）
- 本輪未執行 `make validate-e2e`（純後端分頁上限防禦性修正，`size` 在既有合理範圍（≤100）內行為完全不變，不影響任何 E2E 既定 happy path 斷言）。

---

## 5. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-215`（已修復，見 §3）。

---

## 6. 誠實揭露總結

- **21 處修復中，僅 14 處（8 個檔案）有專屬的鎖定測試**；其餘 7 處（`SettlementGenerator`、`ErpController` 3 處、`TransferController`、`ReturnRequestController` 2 處）因先前完全沒有對應的單元測試基礎設施（無 `*Test.java` 檔案可擴充），從零建立測試類別的成本與本輪修復本身（單行 `Math.min` 包裝）不成比例，故僅以編譯通過 + 完整 `mvn verify` 回歸（含既有整合/E2E 測試若有涵蓋這些端點）作為驗證，未逐一寫紅燈先行的專屬測試。此為本輪主動的取捨判斷，記錄於此供使用者覆核；若使用者認為這 7 處仍應補測試，可在下一輪處理。
- 本輪判斷「延伸 Sprint 161 已確立的『發現同型缺陷可直接修復不需每次詢問』授權範圍」適用於此類有清楚既有前例（`OrderService` 的 `Math.min(size, 100)`）可循的防禦性修復，未透過 `AskUserQuestion` 徵詢，是本輪主動判斷。
- 未評估這 21 處修復前，該系統在生產環境是否已實際發生過因超大 `size` 導致的效能事故或資源耗盡——本輪僅從程式碼靜態分析角度確認風險存在，未查閱任何生產監控/日誌佐證是否已被利用。
- 上限值統一選擇 `100`（沿用 `OrderService` 既有慣例），未針對個別端點的資料量特性做差異化調整（例如 `AuditLog`/`StockMovement` 等單筆資料量可能遠大於 `Tenant`/`User`，理論上或許該有更嚴格的上限，但為維持與既有慣例一致、且避免對外部呼叫端造成不可預期的行為差異，本輪統一採用同一數值）。
- 未處理 `size <= 0`（含負數與零）時 `PageRequest.of` 建構子本身拋出未攔截 `IllegalArgumentException` 導致 500 的情況——此為與本輪不同性質的問題（輸入驗證缺口，而非資源上限缺口），且與 Sprint 161 §4 刻意不做全域 `IllegalArgumentException` handler 的既有決策範圍重疊，留待未來視需要另外評估。
