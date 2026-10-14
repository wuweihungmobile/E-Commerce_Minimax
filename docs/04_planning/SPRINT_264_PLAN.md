# Sprint 264 Plan — M08 評價權限疑慮修復（DEF-373／374，使用者拍板優先處理）

**Sprint**: Sprint 264
**日期**: 2026-10-14

## 1. 範圍與起點

[SPRINT_263_PLAN.md](SPRINT_263_PLAN.md) 撰寫 API_M08_Review.md 時逐行讀碼核對授權邏輯，新登記 DEF-373（回覆評價授權邏輯三處不一致）、DEF-374（刪除評價缺少管理員例外）、DEF-375（M08 編號衝突）、DEF-376（測試排序脆弱性），四項皆登記未修復，連同 Sprint 261 留下的 6 項文件缺口，共 10 項缺口待使用者決定排程。

以 `AskUserQuestion` 徵詢優先序後，使用者選擇「先解決 DEF-373／374 評價權限疑慮（推薦）」——理由是這兩項直接碰觸已上線功能的實際授權判斷，風險性質上高於純文件缺口（DEF-366～371）。其餘 8 項（ReturnRequestController、SupportTicketController、M12 文件落差、M17 文件矛盾、FRD 缺兩條 User Story、DEF-375 編號衝突、DEF-376 測試穩定性）維持登記狀態，留待後續輪次。

選定後，先派一個唯讀 agent 逐行讀碼複核 DEF-373／374 的實際程式碼現狀（不只信任追蹤表敘述），發現原登記文字有兩處不精確：
1. DEF-373 稱「三者共用同一組 `@PreAuthorize` 權限字串」——實際上 `replyToBookingReview` 用的是 `room:update or booking:update`，與另外兩者的 `room:update or product:update` 不完全相同。
2. DEF-373 稱可能是 `STORE_STAFF` 通過權限檢查卻被 service 層擋下——查 `RolePermissionMapping.java` 後確認 STORE_STAFF 無任何 `*:update` 權限，在 `@PreAuthorize` 層即被擋；真正會發生「權限層放行、service 層卡住」的是同租戶的另一位 STORE_OWNER/HOST（共同擁有人）。

複核同時確認 `BookingReviewService.deleteBookingReview` 有與 DEF-374 完全相同的「註解寫本人或管理員、實作無管理員分支」落差，原登記範圍未涵蓋，本輪一併納入修復。

兩項的實際修法方向（放寬或收緊、是否補程式碼）仍需使用者拍板，以 `AskUserQuestion` 徵詢：

- **DEF-373**：使用者選擇「放寬 `createReply`，統一為同租戶或管理員」——理由是 Sprint 251（DEF-331(c)）已有「比對過嚴導致真正房東被鎖死」的同型前例，此次選擇與既有前例一致的方向。
- **DEF-374**：使用者選擇「補程式碼，新增管理員例外」——理由是已確認全庫無任何其他路徑可供管理員下架評價，若平台需要內容管理能力，這是唯一缺口。

## 2. 方法

依 CLAUDE.md 開發-編譯-測試循環，每改一個檔案立即編譯；每個修復單獨完成單元測試＋真實 DB 整合測試＋突變驗證後才進行下一個。

**為何需要真實 DB 整合測試**：`Listing.tenantId`（`insertable=false`）與 `BookingReview.userId`（`insertable=false`）皆是唯讀影子欄位，只有從資料庫載入的實體才會正確回填（既有 DEF-065／DEF-332 教訓），Mockito 手動建構的物件無法如實重現此風險。沿用 `ReviewAuthorizationRealDbIntegrationTest`（Sprint 251 建立）既有的 seed helper 擴充，不新建檔案。

**突變驗證**：生產程式碼修復完成後，用 `git stash` 暫時還原至修復前版本，重跑新增測試確認轉紅（且只有針對該修復的測試轉紅，其餘測試維持原因不變地通過），再 `git stash pop` 還原修復，證明新測試確實綁定該修復、不是假綠燈。

## 3. 實作內容

| 檔案 | 變更 |
|---|---|
| `backend/src/main/java/.../core/review/ReviewReplyService.java` | **DEF-373**：`createReply` 權限檢查由「確切 `listing.ownerId`」改為「同租戶或管理員」（比照既有 `checkReviewManagementAuthorization`／`replyToBookingReview`）；新增 `isCurrentUserAdmin()` 私有方法（比照另兩個 service 既有前例） |
| `backend/src/main/java/.../core/review/ReviewService.java` | **DEF-374**：`deleteReview` 補上 `!isCurrentUserAdmin() &&` 條件，重用既有 `isCurrentUserAdmin()` |
| `backend/src/main/java/.../core/review/BookingReviewService.java` | **DEF-374**（複核時發現的同型落差）：`deleteBookingReview` 補上 `!isCurrentUserAdmin() &&` 條件，重用既有 `isCurrentUserAdmin()` |
| `backend/src/test/java/.../core/review/ReviewReplyServiceTest.java` | 新增 3 個測試（同租戶非 owner 成功／不同租戶拒絕／管理員成功）；修正既有 3 個測試未設定 `TenantContext.setCurrentTenant` 的缺漏（新邏輯需要租戶context才能通過，否則會誤判為跨租戶）；`@AfterEach` 加入 `SecurityContextHolder.clearContext()` |
| `backend/src/test/java/.../core/review/ReviewServiceTest.java` | 新增 `deleteReview_admin_succeeds` |
| `backend/src/test/java/.../core/review/BookingReviewServiceTest.java` | 新增 3 個測試（`deleteBookingReview` 本人成功／非本人非管理員拒絕／管理員成功，原檔案完全沒有 `deleteBookingReview` 測試） |
| `backend/src/test/java/.../core/review/ReviewAuthorizationRealDbIntegrationTest.java` | 新增 5 個真實 DB 整合測試（DEF-373 的同租戶/跨租戶/管理員三案例、DEF-374 的 `deleteReview`／`deleteBookingReview` 管理員案例）；`@Autowired` 新增 `ReviewReplyService`；class-level Javadoc 更新範圍說明 |
| `docs/02_architecture/api/API_M08_Review.md` | §1.2.5／§1.3／§4.3／§4.8／§4.11／§4.17／§5／§7 同步更新為已修復狀態；v1.0 → v1.1 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | DEF-373／374 標記已完成；版本歷史新增 v3.47 |

## 4. 測試

依 CLAUDE.md 開發-編譯-測試循環，逐步驟編譯＋測試（細節見下方突變驗證結果），不累積開發：

- `mvn clean test -Dtest=ReviewReplyServiceTest`：12 個測試全過（9 原有 + 3 新增，含修正 3 個漏設 TenantContext 的既有測試）
- `mvn clean test -Dtest=ReviewServiceTest,BookingReviewServiceTest`：40 個測試全過（22 + 18，各含 1／3 個新增）
- `mvn failsafe:integration-test -Dit.test=ReviewAuthorizationRealDbIntegrationTest`：17 個測試全過（12 原有 + 5 新增）

**突變驗證（還原生產程式碼邏輯，確認新測試轉紅）**：

| 還原範圍 | 預期轉紅的測試 | 實際結果 |
|---|---|---|
| `ReviewReplyService.java`（DEF-373） | `createReply_SameTenantNonOwner_Succeeds`／`createReply_Admin_Succeeds`（單元）；`createReply_sameTenantNonOwner_succeeds`／`createReply_admin_succeeds`（整合） | ✅ 恰好這 4 個轉紅，其餘 10＋13 個測試維持通過（含 `createReply_DifferentTenantNonOwner_ThrowsE1007` 等，以舊邏輯的不同理由仍然通過） |
| `ReviewService.java`＋`BookingReviewService.java`（DEF-374） | `deleteReview_admin_succeeds`（單元）；`deleteBookingReview_admin_succeeds`（單元）；`deleteReview_admin_succeeds`／`deleteBookingReview_admin_succeeds`（整合） | ✅ 恰好這 4 個轉紅，其餘測試維持通過 |

兩輪皆以 `git stash push --keep-index -- <檔案>` 暫時還原、測試、再 `git stash pop` 復原，驗證期間未影響已暫存（staged）的測試檔案變更。

## 5. 驗證結果

`mvn clean verify -o`（背景執行，總耗時 21:24 分鐘）：
- 單元測試：**2231 個，0 失敗，0 錯誤**（2225 + 本輪新增 6：`ReviewReplyServiceTest` +2、`ReviewServiceTest` +1、`BookingReviewServiceTest` +3）
- 整合測試：**853 個，0 失敗，0 錯誤**（848 + 本輪新增 5：`ReviewAuthorizationRealDbIntegrationTest`）
- `BUILD SUCCESS`
- 數量以 `target/surefire-reports/*.txt`／`target/failsafe-reports/*.txt` 加總核對（`mvn clean verify` 已清空 `target/`，無舊報告殘留風險，見 [[test-suite-fork-reuse-and-count-baseline]]），而非只讀終端輸出尾端（背景指令輸出經 `tail -150` 截斷，只保留了整合測試階段的摘要，單元測試階段摘要已被截掉，改用報告檔案加總還原）

## 6. 範圍外（延後）與待使用者決定

- ReturnRequestController（DEF-366）、SupportTicketController（DEF-367）、M12 文件落差（DEF-368）、M17 文件矛盾（DEF-369）、FRD 缺兩條 User Story（DEF-370／371）：維持登記狀態，排程由使用者後續決定。
- DEF-375（M08 新舊文件編號衝突）、DEF-376（`OrderCancellationCompensationIntegrationTest` 排序脆弱性）：Sprint 263 新發現，本輪未處理，維持登記狀態。
- `API_Index.md` 舊表格、`TC_M08_Review.md` 皆未隨本輪修復更新（兩者本就是已知過期/衝突的歷史文件，見 DEF-375，非本輪範圍）。

## 7. Velocity 紀錄

本輪為 2 個權限邏輯缺陷修復＋對應測試（含真實 DB 整合測試與突變驗證），無新增使用者可見功能，不納入傳統 Story Point 計算。

## 8. 下一步／Action Items

1. 全量驗證通過後 commit＋push，等待雲端 CI 確認全綠，回填 RELEASE_TRACKER.md。
2. 待使用者決定是否繼續下一輪處理剩餘 8 項缺口（文件補齊類 DEF-366／367／370／371、文件自身問題 DEF-368／369／375、測試穩定性 DEF-376），或轉向其他 Sprint。
