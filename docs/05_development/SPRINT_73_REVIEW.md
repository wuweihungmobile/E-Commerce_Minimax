# Sprint 73 Review / Sprint 73 評審會議

> **Sprint 編號**: Sprint 73
> **期間**: 2026-07-06
> **評審日期**: 2026-07-06
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 目標達成度

> **主題**: `ReviewService` 7 個零覆蓋方法單元測試從零建立 + 3 項探查發現的擁有權/租戶問題修復（已確認）+ 1 項技術債記錄

| US | 標題 | SP | 狀態 |
|----|------|----|------|
| US-001 | `ReviewService` 7 個零覆蓋方法單元測試從零建立 | 8 | ✅ 完成 |
| US-002 | 修復 `markAsHandled`/`markAsUnhandled` 跨租戶寫入（`DEF-028`） | 2 | ✅ 完成 |
| US-003 | 修復 `getReviewsByHandlingStatus` 跨租戶讀取（`DEF-029`） | 2 | ✅ 完成 |
| US-004 | 修復 `getUserReviews` 匿名保護繞過（`DEF-030`） | 2 | ✅ 完成 |
| US-005 | 記錄 D 項技術債（`DEF-031`） | 0 | ✅ 完成 |

**14 SP 全數完成**。本 Sprint 開始前先完整探查 `ReviewService` 範圍（見 `SPRINT_73_PLAN.md`「前置條件確認」），確認與 Sprint 66/67/69/71 同量級，並在探查階段主動以「這個方法允許誰呼叫、有沒有檢查資源是否屬於呼叫者」的角度逐一審視 14 個方法，發現 3 項擁有權/租戶檢查缺口，經使用者確認後併入本 Sprint 一併處理。

---

## 2. 交付內容

### 新增測試（US-001~004 合併於單一檔案）

- **`ReviewServiceTest.java`**（新檔，**18 個測試**）：涵蓋先前完全零覆蓋的 7 個方法：
  - `updateReview`（3）：本人更新成功、評價不存在 `E_1087`、非本人 `E_1007`。
  - `deleteReview`（3）：本人軟刪除成功、評價不存在 `E_1087`、非本人 `E_1007`。
  - `markHelpful`（2）：首次投票 `helpfulCount=1`、評價不存在 `E_1087`。
  - `markAsHandled`/`markAsUnhandled`（6）：他租戶不得放行（`DEF-028` 紅燈驗證）、本租戶放行、admin 跨租戶放行，兩方法各 3 個。
  - `getReviewsByHandlingStatus`（2）：不得洩漏他租戶評價（`DEF-029` 紅燈驗證）、admin 可見所有租戶。
  - `getUserReviews`（3）：不得查詢他人評價列表（`DEF-030` 紅燈驗證）、可查詢自己、admin 可查詢任何人。
- 其餘 7 個方法（`createReview`/`getReviewsByListingId`/`getRatingStats`/`searchReviews`/`addImage`/`removeImage`/`reorderImages`）已由既有 `M08ReviewIntegrationTest`/`M08ReviewImageIntegrationTest`/`M08ReviewStatsIntegrationTest`/`ReviewServiceSearchTest`/`ReviewServiceCacheIntegrationTest` 涵蓋，依 Rule 3「精準改動」不重複造測試。

### 生產程式碼修復（US-002：`DEF-028`）

- **問題**：`markAsHandled`/`markAsUnhandled`（`core/review/ReviewService.java`）完全沒有擁有權/租戶檢查。Controller 端僅要求 `room:update`/`product:update` 權限，此權限分散於各租戶的 `SELLER`/`HOST`/`STORE_OWNER`/`ADMIN` 角色，任一租戶賣家可竄改其他租戶商品評價的處理狀態，屬跨租戶寫入 IDOR。
- **紅燈證明**：新增測試 `markAsHandled_crossTenantReview_mustBeRejected`/`markAsUnhandled_crossTenantReview_mustBeRejected`，斷言「他租戶評價不得放行」。**修復前執行確認失敗**（4 個 Failure，AssertionError：`Expecting code to raise a throwable`），實測證實漏洞存在。
- **修復**：新增 `checkReviewManagementAuthorization(Review)` + `isCurrentUserAdmin()` 兩個 helper（比照 `OrderService.checkOrderStatusUpdateAuthorization`/`BookingService.checkBookingOwnership` 既有前例），此操作無「本人」語意（操作者是管理商店的賣家，非評價作者），採「本租戶（`review.getListing().getTenantId()`）or admin（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN`）」放行，越權拋 `E_1007`。
- **轉綠**：修復後重跑，`ReviewServiceTest` 全數通過（含正向對照：本租戶賣家放行、admin 跨租戶放行，避免修復矯枉過正）。

### 生產程式碼修復（US-003：`DEF-029`）

- **問題**：`getReviewsByHandlingStatus`（`/v2/reviews/managed`）呼叫 `ReviewRepository.findByIsHandled(isHandled, pageable)` 完全無租戶過濾；`reviews` 資料表本身無 `tenant_id` 欄位，需經 `listing.tenant.id` 二層 join 取得。任一持有 `room:update`/`product:update` 權限的賣家（分散於各租戶）皆可取得系統中所有租戶的評價列表。
- **紅燈證明**：新增測試 `getReviewsByHandlingStatus_mustNotLeakOtherTenantReviews`，斷言結果不得包含他租戶評價。**修復前執行確認失敗**，實測證實漏洞存在。
- **修復**：新增 `ReviewRepository.findByIsHandledAndTenantId`（`@Query` JPQL `r.listing.tenant.id = :tenantId`，比照 `DEF-026` 保留舊方法 + 新增租戶過濾方法模式）；`getReviewsByHandlingStatus` 改為 admin（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN`）沿用舊查詢（跨租戶總覽），非 admin 一律改用租戶過濾查詢。
- **轉綠**：修復後重跑全數通過（含 admin 可見所有租戶的對照測試）。

### 生產程式碼修復（US-004：`DEF-030`）

- **問題**：`getUserReviews(userId, ...)` 未檢查 `userId` 是否等於 `TenantContext.getCurrentUser()`。`BUYER` 角色持有 `order:read` 權限，任意登入買家可在 `GET /v2/reviews/user/{userId}` 代入任意他人 `userId`，取得該使用者完整評價內容（`toReviewResponse` 僅依 `isAnonymous` 隱藏 `userId`/`userFullName`/`userAvatarUrl`，`content`/`rating`/`listingId` 一律回傳），等於繞過匿名評價的身分保護設計。
- **紅燈證明**：新增測試 `getUserReviews_otherUser_mustBeRejected`，斷言非本人查詢應拋例外。**修復前執行確認失敗**，實測證實漏洞存在。
- **修復**：比照 `DEF-018` 買家自助模式，`getUserReviews` 新增檢查：非 admin 且 `userId` 不等於當前使用者拋 `E_1007`。
- **轉綠**：修復後重跑全數通過（含查詢自己、admin 查詢他人的對照測試）。

### 技術債記錄（US-005：`DEF-031`）

- 記錄 2 項擱置的業務邏輯疑點於 `DEFERRED_ITEMS_TRACKER.md`：(1) `markHelpful` 同一使用者重複呼叫會無限累加投票數；(2) `createReview`/`BookingReviewService.createBookingReview` 未驗證 `orderId`/`bookingId` 歸屬。皆非跨租戶 IDOR，使用者已決策擱置，不併入本 Sprint。

### 文件

- **`SPRINT_73_PLAN.md`**（新檔）：本 Sprint 計劃，含前置範圍探查、既有測試覆蓋現況、US-001~005 完整 AC。
- **`DEFERRED_ITEMS_TRACKER.md`**：新增 `DEF-028`/`DEF-029`/`DEF-030`（皆已完成，直接記入「已完成延後項目」）、`DEF-031`（🟢 低優先級技術債）。
- **`RELEASE_TRACKER.md`**：新增 Sprint 73 列。
- **`RELEASE_NOTES_v2028.08.12-01.md`**（新檔）。

---

## 3. 驗證結果

| 項目 | 結果 |
|------|------|
| 後端編譯 | ✅ 0 error |
| 開發-編譯-測試循環 | ✅ 每完成一批測試立即編譯 + 執行驗證，US-002/003/004 額外執行「紅燈確認 → 修復 → 轉綠確認」雙重驗證節奏（含中途發現測試 fixture 本身缺陷並修正） |
| `ReviewServiceTest` 修復前（US-002/003/004 紅燈階段） | 🔴 18 tests，**4 Failures**（`markAsHandled_crossTenantReview_mustBeRejected`/`markAsUnhandled_crossTenantReview_mustBeRejected`/`getReviewsByHandlingStatus_mustNotLeakOtherTenantReviews`/`getUserReviews_otherUser_mustBeRejected`，皆為 AssertionError 而非 Error，正確反映「未拋出例外」的漏洞本質） |
| `ReviewServiceTest` 修復後 | ✅ 18 tests，0 fail |
| 後端單元回歸（`mvn test`） | ✅ **716 tests，0 fail** |
| 全量回歸（`mvn verify -Pintegration-test`） | ✅ **BUILD SUCCESS**：單元 716 + 整合（failsafe）342 = **1058 tests，0 fail** |
| `make validate-schema` | ✅ 無漂移（本 Sprint 僅新增 repository 查詢方法，無 entity/migration 變更），EXIT_CODE=0 |
| 驗證方式選擇 | 依全量回歸頻率政策：本 Sprint 修改生產程式碼（`ReviewService`/`ReviewRepository`），故執行全量 `mvn verify -Pintegration-test`，非僅 `mvn test` |

---

## 4. 誠實揭露（Rule 12）

1. **紅燈測試初版有 2 個 fixture 缺陷，經協作發現並修正**：
   - 第一版 `ReviewServiceTest.java` 的 `reviewOf()` helper 未設定 `reviewType` 欄位，導致 `toReviewResponse` 呼叫 `review.getReviewType().name()` 時撞 NPE，9 個測試在準備階段就崩潰（Error），而非跑到真正要驗證的擁有權檢查邏輯（Failure）。修正：補上 `.reviewType(Review.ReviewType.PRODUCT)`。
   - 修正後仍有 2 個跨租戶負向測試（`markAsHandled`/`markAsUnhandled`）因為當時「先讓 `save()` 正常回傳以便跑到後續程式碼」的 stub 設計，撞見巧合的 NullPointerException（而非明確的「未拋出例外」斷言失敗），修正後才呈現乾淨的紅燈失敗訊息。
   - 修復生效後，這些先前為了讓程式碼跑到 `save()`/`findBy...` 而加的 stub 反而變成「用不到的 stub」，Mockito 嚴格模式報 `UnnecessaryStubbingException`（4 個），移除後才是最終乾淨的 18 tests 0 fail。
   - 此紅綠燈過程展現了測試撰寫本身也需要交叉驗證：「紅燈」必須明確對應「缺口存在」的斷言失敗，而非任何形式的測試失敗都可視為有效證據。
2. **`isCurrentUserAdmin()` 判斷邏輯沿用既有前例的既定慣例（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 皆視為可跨租戶放行）**：`RolePermissionMapping.java` 註解原意「`ADMIN` 為租戶內管理」，但 `OrderService.checkOrderStatusUpdateAuthorization`/`BookingService.checkBookingOwnership` 等既有前例（`DEF-018/019/023/024`）皆將 `ROLE_ADMIN`/`ROLE_SUPER_ADMIN` 同等視為全域 admin 放行。依 Rule 11「配合程式碼庫的慣例，即使不同意」，本次三處新增檢查沿用相同慣例，未依文件註解重新定義 `ADMIN` 為租戶限定，此為既有慣例本身的潛在不一致，非本 Sprint 引入的新問題。
3. **D 項（`DEF-031`）僅記錄未驗證**：探查階段發現但依使用者決策直接擱置，未撰寫任何驗證測試，純粹是探查過程中的觀察記錄，與 US-002/003/004（先寫紅燈測試證實才修復）的處理方式不同。

---

## 5. Demo 重點

- **主動審視擁有權/租戶檢查，而非被動發現**：本 Sprint 依使用者明確要求，探查階段就以「這個方法允許誰呼叫、有沒有檢查資源是否屬於呼叫者」的角度逐一審視 14 個方法，一次揭露 3 項缺口（而非像先前 Sprint 68/70/72 那樣是撰寫測試過程中意外發現），驗證了系統性審視方法的有效性。
- **三種不同語意的擁有權檢查在同一 Service 內並存**：`markAsHandled`/`markAsUnhandled` 是「本租戶或 admin」（無本人語意，因操作者非資源建立者）；`getReviewsByHandlingStatus` 是「非 admin 才過濾」（分支邏輯而非拋例外）；`getUserReviews` 是「本人或 admin」（owner-or-admin，比照 `DEF-018`）。三種模式對應到既有程式碼庫中三個不同的既定前例，未套用單一制式模板。
- **測試品質的自我修正**：紅燈測試撰寫過程中發現並修正了 2 類 fixture 缺陷（`reviewType` 遺漏、stub 誤用導致巧合性 NPE），最終才確認 4 個真正代表漏洞的乾淨紅燈，展現「紅燈本身也需要驗證其正確性」的紀律。

---

**文件版本**: v1.0
**建立日期**: 2026-07-06
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
