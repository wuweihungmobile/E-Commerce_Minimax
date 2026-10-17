# Sprint 280 Plan — 六項待決定缺陷拍板處理（DEF-347／379／380／384／386／387）

## 1. 範圍與起點

使用者貼回 Sprint 279 總結（含「6 項待你決策」清單），並明確指示：「以上請依照最合理化，最佳化，最理想，最適當的解決方案幫我決策，若真需要我決定，請以最簡單的解釋，讓我互動選項決策」。

這是比既有「無 `==>` 回覆＝只做 AI 能獨立處理者」更明確的授權——使用者主動要求 AI 直接依最佳判斷決策，只在真正需要人類輸入時才用互動選項詢問。逐項檢視 Sprint 279 總結列出的 6 項，確認每項都是技術/工程判斷（有明確、可驗證的理由支持單一結論），而非需要使用者個人偏好或商業策略輸入的決策：

| 項目 | 判斷依據 |
|------|------|
| DEF-347 | 安全分析已完成兩輪（Sprint 211、271），結論一致：剩餘同步路徑需先登入，不構成匿名列舉風險——純粹是「這個結論是否正確」的技術問題，已可驗證 |
| DEF-379 | 讀碼確認 `/v2/products` 本身是需要 `product:read` 的已登入端點，全平台瀏覽已有獨立公開端點 `/v2/public/listings`，同方法內 4 個分支中已有 2 個是租戶範圍——收斂一致不是「選一個新方向」，是「修正內部不一致」 |
| DEF-380 | 系統「建立即上架」已穩定運行超過 280 個 Sprint，從未有人需要兩階段草稿機制——改文件反映現況是 YAGNI 原則的直接應用 |
| DEF-384 | 假資料 GMV 無論如何都不該留著；是否需要「揭露」只取決於是否曾作為對外財務報表（內部管理後台，答案明確是否） |
| DEF-386 | 同模組其餘三個 Controller 都已證明標準機制 `TenantContext` 可用且正確；`PostController` 的例外沒有架構上的理由 |
| DEF-387 | 使用者自己給的建議已經是「維持現狀」這個最低風險選項，不需要再問 |

六項皆依建議執行，未使用 `AskUserQuestion` 詢問任何一項（確實不需要）。

## 2. 讀碼調查

- **DEF-347**：確認 `AccountSecurityService.sendPasswordResetMail` 仍為非同步（`passwordResetMailExecutor`）；`dispatchEmailVerification` 的唯一對外入口 `AuthController.resendEmailVerification`（`POST /v2/auth/email/verify/send`）要求 `@AuthenticationPrincipal UserPrincipal principal` 非空，確認需登入。
- **DEF-379**：讀 `ProductService.getProducts`／`ProductRepository`／`ProductController`（確認 `@PreAuthorize("hasAuthority('product:read')")`，非公開端點）；讀 `PublicListingController`（確認是獨立、刻意不影響 `/v2/listings` 授權範圍的公開端點）；讀 `RoomService.getRooms` 的 `Specification` 模式作為修復範本。
- **DEF-380**：讀 FRD §3／§4 既有的「修訂註記」與 AC 原文（AC-M01-002-1/2、AC-M02-003-1/2/3、AC-M02-004-2）；讀 `RoomCalendarController` 的 `maintenance` 端點（`POST/DELETE /v2/dashboard/rooms/{roomListingId}/maintenance`）確認改寫 AC-M02-004-2 要對應的真實機制。
- **DEF-384**：讀 `AdminService.getPlatformStats`／`AnalyticsService.getRevenueStats`（確認 GMV 計入狀態集合既有慣例：`PAID`／`CONFIRMED`／`SHIPPING`／`DELIVERED`／`COMPLETED`）；讀 `OrderRepository` 既有 `sumTotalAmountByTenantIdAndStatusAndCreatedAtAfter` 的 JPQL 風格作為新查詢範本。
- **DEF-386**：讀 `PostController` 全文，發現原登記文字只提到 `getTenantIdFromUser`，**但 `getUserIdFromUser` 有完全同款的反射＋硬編碼 fallback（假 UUID `...002`），原登記未提及**；讀 `TenantContext.java` 確認 `getCurrentTenant()`/`getCurrentUser()` 由 `JwtAuthenticationFilter` 於請求進入前設置、對 store-less 使用者回系統租戶哨兵值而非 `null`；讀 `CmsController` 確認同模組其餘 Controller 完全不在 Controller 層解析租戶，Service 內部直接呼叫 `TenantContext`；讀 `PostControllerE2ETest`/`PostControllerFilterValidationTest` 確認皆為 MockMvc／真實 JWT 流程，不直接呼叫 Controller 方法。
- **DEF-387**：讀 API_M15_CMS.md §1.2 既有的兩套媒體庫說明，確認兩者實際服務的業務情境（貼文內嵌媒體 vs 通用素材庫）。

## 3. 實作內容

| 項目 | 檔案 | 說明 |
|------|------|------|
| DEF-347 | `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | 結案，純文件，無程式碼變更 |
| DEF-379 | `backend/src/main/java/com/nextkey/ecommerce/domain/repository/ProductRepository.java` | 新增 `findByTenantIdAndListingStatusAndCategory`／`...AndBrand`／`...AndCategoryAndBrand` 三個租戶範圍查詢，移除無租戶條件的舊方法 |
| DEF-379 | `backend/src/main/java/com/nextkey/ecommerce/core/product/ProductService.java` | `getProducts` 三個分支改呼叫新方法 |
| DEF-379 | `backend/src/test/java/com/nextkey/ecommerce/api/controller/ProductControllerE2ETest.java`（新增測試） | `getProducts_filterByCategoryOrBrand_excludesOtherTenantProducts`：兩個真實租戶、同 category/brand，驗證三種篩選皆只回自己租戶商品 |
| DEF-380 | `docs/01_requirements/E-Commerce_FRD_v1.0.md`（v1.14→v1.15） | 改寫 3 處 Gherkin AC（AC-M01-002-1/2、AC-M02-003-1/2/3、AC-M02-004-2）反映「建立即上架、缺列視為可訂、維護窗口取代批次封鎖」現況；同步修正 2 處「技術實現概要」殘留的 `DRAFT` 敘述 |
| DEF-380 | `docs/02_architecture/api/API_M01_Product_Center.md`／`API_M02_Listing_Center.md` | §7／§8 交叉引用同步更新為已結案狀態 |
| DEF-384 | `backend/src/main/java/com/nextkey/ecommerce/domain/repository/OrderRepository.java` | 新增 `sumTotalAmountByStatusIn`（跨租戶真實加總，比照 `AnalyticsService` 狀態集合） |
| DEF-384 | `backend/src/main/java/com/nextkey/ecommerce/core/admin/AdminService.java` | `getPlatformStats` 改用真實查詢，移除 `MOCK_AVERAGE_ORDER_VALUE` |
| DEF-384 | `backend/src/test/java/com/nextkey/ecommerce/core/admin/AdminServiceTest.java` | `getPlatformStats_returnsStats` 斷言改為驗證真實加總值（而非 `orders×1500`） |
| DEF-384 | `docs/02_architecture/api/API_M14_Admin_Console.md` | 5 處「假 GMV」敘述更新為已修復 |
| DEF-386 | `backend/src/main/java/com/nextkey/ecommerce/api/controller/PostController.java` | 移除 6 個反射/fallback 輔助方法與 `TenantIdInfo` record；12 個端點方法改直接呼叫 `TenantContext.getCurrentTenant()`／`getCurrentUser()`；移除 `@AuthenticationPrincipal UserDetails` 參數、`tenantMemberRepository`／`userRepository` 欄位與對應 import |
| DEF-386 | `docs/02_architecture/api/API_M15_CMS.md` | §1.2／§7 等 6 處敘述更新為已修復 |
| DEF-387 | `docs/02_architecture/api/API_M15_CMS.md` | §1.2／§7 補充兩套媒體庫定位差異的說明文字（維持現狀，無程式碼變更） |
| 全項 | `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | 6 個條目狀態更新（4 項 ✅ 已修復/結案、1 項 ✅ 已決策維持現狀、1 項 ✅ 已結案） |
| 全項 | `docs/04_planning/RELEASE_TRACKER.md` | 新增 Sprint 280 row |

## 4. 測試

依 CLAUDE.md 開發-編譯-測試循環，每項修復完成後立即 `mvn -o compile`／`test-compile` 確認編譯成功（過程中兩次遇到 VS Code JDT/ECJ 背景編譯寫壞 `target/classes` 的既知假性失敗——`ClassFormatError: Invalid method Code length 0`／`Flexible Constructor Bodies` 編譯層級錯誤，皆與本輪程式碼變更無關，`mvn clean` 後消失）。

- DEF-379：`ProductControllerE2ETest` 11/11 通過（含新增的租戶隔離迴歸測試）。
- DEF-384：`AdminServiceTest` 52/52 通過；`AdminControllerE2ETest`（`getPlatformStats_asSuperAdmin_shouldSucceed`／`getPlatformStats_asBuyer_shouldReturn403`）2/2 通過，對真實 Postgres 確認 `COALESCE(SUM(...))` SQL 正確執行。
- DEF-386：`PostControllerE2ETest` 24/24、`PostControllerFilterValidationTest` 2/2 皆通過。
- 全量 `mvn -o clean verify`：**單元 2273/2273、整合 857/857，0 失敗、0 錯誤**（整合測試數較 Sprint 279 的 856 增加 1，對應本輪新增的 `ProductControllerE2ETest` 迴歸測試）。Checkstyle／PMD 皆 0 違規。Total time 18:05 min。

## 5. 範圍外

- **DEF-336**（清空購物車未清優惠券標記）：Sprint 279 總結提及可作為下一個低風險候選，但本輪使用者的指示明確針對「以上」6 項，未要求擴大範圍，故不納入本輪。

## 6. Velocity 紀錄

本輪性質為 6 個已登記缺陷的拍板處理，其中 3 項為生產程式碼修復（DEF-379／384／386，皆含新增/更新測試）、1 項為需求文件改寫（DEF-380）、2 項為純文件結案（DEF-347／387），無新功能開發，不納入傳統 Story Point 計算。

## 7. 下一步／Action Items

- 追蹤表低優先級區仍有 DEF-336 可作為下一輪候選。
- 待使用者審閱 FRD v1.15 的 Gherkin 改寫是否符合預期。
