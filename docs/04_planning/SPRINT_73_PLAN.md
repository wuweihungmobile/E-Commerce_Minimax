# Sprint 73 計劃 / Sprint 73 Plan

> **Sprint 編號**: Sprint 73
> **期間**: 2026-07-06
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-07-06
> **基於**: `SPRINT_72_PLAN.md` §7「後續 Sprint 待處理清單」——`ReviewService`（14 方法，測試目錄完全不存在）；範圍與 3 項安全缺口修復決策已於本 Sprint 開始前經使用者確認（詳見「前置條件確認」）
> **規劃協作**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 72 收尾狀態 | ✅ 已收尾並 push | ERP 模組（Supplier/StockMovement/PurchaseOrder/Inventory）單元測試 + `DEF-026`/`DEF-027` 跨租戶修復完成 |
| `ReviewService` 範圍探查 | ✅ 完成，使用者已確認 | 1 個 Service（14 public 方法、647 行），與 Sprint 66/67/69/71（皆 8 SP 量級）相當，**單一 Sprint 涵蓋、不拆分** |
| 探查中發現的擁有權/租戶隔離問題 | ✅ 已標記並經使用者決策：**A/B/C 三項併入本 Sprint 修復**，D 項擱置記錄技術債 | 詳見 US-002（A，`DEF-028`）、US-003（B，`DEF-029`）、US-004（C，`DEF-030`）、US-005（D，`DEF-031` 技術債記錄） |
| 探查誠實揭露 | `ReviewService` 14 個方法中 7 個（`updateReview`/`deleteReview`/`getUserReviews`/`markHelpful`/`markAsHandled`/`markAsUnhandled`/`getReviewsByHandlingStatus`）**完全零測試覆蓋**（無單元測試、無整合/E2E 測試）；其餘 7 個（`createReview`/`getReviewsByListingId`/`getRatingStats`/`searchReviews`/`addImage`/`removeImage`/`reorderImages`）已由既有 `M08ReviewIntegrationTest`/`M08ReviewImageIntegrationTest`/`M08ReviewStatsIntegrationTest`/`ReviewServiceSearchTest`/`ReviewServiceCacheIntegrationTest` 涵蓋，本 Sprint 依 Rule 3「精準改動」不重複造測試 | 依 Rule 12「大聲失敗」誠實揭露範圍界線 |
| Push 狀態 | 依現行節奏，本 Sprint 收尾後立即 push | 不累積 |

---

## 1. 既有覆蓋現況（Dev David + QA Quincy 盤點）

| 方法 | 既有測試涵蓋 | 擁有權/租戶檢查現況 |
|---|---|---|
| `createReview` | `M08ReviewIntegrationTest`、`M08ReviewImageIntegrationTest`（間接） | 僅檢查 Listing 存在、重複評價；訂單/訂房歸屬未驗證（D 項，擱置） |
| `updateReview` | ❌ 無 | ✅ 已有本人檢查 |
| `deleteReview` | ❌ 無 | ✅ 已有本人檢查（軟刪除） |
| `getReviewsByListingId` | `M08ReviewIntegrationTest` | 公開瀏覽端點，無需擁有權檢查 |
| `getUserReviews` | ❌ 無 | ❌ **缺口（C 項）**：任意 `order:read` 使用者可代入他人 `userId`，繞過 `isAnonymous` 匿名保護 |
| `getRatingStats` | `ReviewServiceCacheIntegrationTest`、`M08ReviewStatsIntegrationTest`、`M08ReviewIntegrationTest` | 公開統計，無需擁有權檢查 |
| `markHelpful` | ❌ 無 | 無本人限制屬合理設計；重複投票行為疑點（D 項，擱置） |
| `markAsHandled` | ❌ 無 | ❌ **缺口（A 項）**：完全無租戶檢查，跨租戶寫入 IDOR |
| `markAsUnhandled` | ❌ 無 | ❌ **缺口（A 項）**：同上 |
| `getReviewsByHandlingStatus` | ❌ 無 | ❌ **缺口（B 項）**：Repository 查詢無 tenant 過濾，跨租戶讀取洩漏 |
| `searchReviews` | `ReviewServiceSearchTest`（474 行） | 公開搜尋，無需擁有權檢查 |
| `addImage` | `M08ReviewImageIntegrationTest`（含 403 負向測試） | ✅ 已有本人檢查 |
| `removeImage` | `M08ReviewImageIntegrationTest` | ✅ 已有本人檢查 |
| `reorderImages` | `M08ReviewImageIntegrationTest` | ✅ 已有本人檢查 |

REST 層由 `ReviewController.java`（`/v2/reviews/**`）暴露。

---

## 2. Sprint 73 目標

> **主題**: `ReviewService` 7 個零覆蓋方法單元測試從零建立 + 3 項探查發現的擁有權/租戶問題修復（已確認）+ 1 項技術債記錄

為 `ReviewService` 先前完全零覆蓋的 7 個方法建立單元測試，並修復探查階段已確認的 3 項擁有權/租戶檢查缺口（比照 `DEF-018/019/023/024/026/027` 既有前例），同時將 1 項擱置的業務邏輯疑點記錄為技術債。

---

## 3. User Story

### US-001：`ReviewService` 7 個零覆蓋方法單元測試從零建立

> **SP**: 8 | **優先級**: P1 | **狀態**: 待執行

**AC-001-1**：新增 `ReviewServiceTest.java`，涵蓋：
- `updateReview`：正常路徑（部分欄位更新）、評價不存在拋 `E_1087`、非本人拋 `E_1007`、圖片超限/無效沿用既有驗證。
- `deleteReview`：正常路徑（軟刪除，`isVisible=false`）、評價不存在拋 `E_1087`、非本人拋 `E_1007`。
- `markHelpful`：正常路徑（`helpfulVotes`/`helpfulCount` 正確累加）、評價不存在拋 `E_1087`。

**AC-001-2**：測試風格比照既有 `OrderServiceTest`/`BookingReviewServiceTest`：`@ExtendWith(MockitoExtension.class)` + `@Mock` repository + `@InjectMocks` service，`TenantContext` 於各測試內設定、`@AfterEach` 清理（含 `SecurityContextHolder.clearContext()`），AssertJ 風格斷言。

**AC-001-3**：過程中若發現生產程式碼問題則停止修改、記錄待決策，不自行假設修復（US-002/003/004 已知的 3 項、US-005 的技術債除外，因已於本 Sprint 前經使用者決策）。

---

### US-002：修復 `markAsHandled`/`markAsUnhandled` 跨租戶寫入（`DEF-028`，已確認）

> **SP**: 2 | **優先級**: P0（安全） | **狀態**: 待執行

**背景**：`markAsHandled`/`markAsUnhandled`（`core/review/ReviewService.java:311`/`331`）完全沒有擁有權/租戶檢查。Controller 端僅要求 `hasAuthority('room:update') or hasAuthority('product:update')`——`SELLER`/`HOST`/`STORE_OWNER`/`ADMIN` 角色皆持有，且分散於各租戶。任一租戶的賣家可呼叫 `PUT /v2/reviews/{reviewId}/handle` 修改**任何其他租戶**商品評價的處理狀態，屬跨租戶寫入 IDOR，與 `DEF-024`/`DEF-026`/`DEF-027` 同一模式。此操作無「本人」語意（操作者是管理商店的賣家，非評價作者），比照 `DEF-024` 三選一模式簡化為「本租戶（`review.getListing().getTenantId()`）or admin（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN`）」放行。

**AC-002-1**：先在 `ReviewServiceTest.java` 新增**修復前會失敗（紅燈）**測試：以租戶 A 的 `TenantContext` 呼叫 `markAsHandled`/`markAsUnhandled`（`review` 屬租戶 B 的 listing），驗證修復前會錯誤地成功寫入。

**AC-002-2**：新增 `checkReviewManagementAuthorization(Review review)` helper（比照 `OrderService.checkOrderStatusUpdateAuthorization`/`BookingService.checkBookingOwnership` 的 `isAdmin` 判斷寫法），`markAsHandled`/`markAsUnhandled` 呼叫前置檢查，越權拋 `E_1007`。

**AC-002-3**：AC-002-1 測試轉綠；新增本租戶放行、admin 跨租戶放行的對照測試，避免修復矯枉過正。

---

### US-003：修復 `getReviewsByHandlingStatus` 跨租戶讀取（`DEF-029`，已確認）

> **SP**: 2 | **優先級**: P0（安全） | **狀態**: 待執行

**背景**：`getReviewsByHandlingStatus`（`core/review/ReviewService.java:351`）呼叫 `ReviewRepository.findByIsHandled(isHandled, pageable)`，**完全無 listingId/tenant 過濾**。`reviews` 資料表本身無 `tenant_id` 欄位（需透過 `listing.tenant.id` 二層 join 取得），任一持有 `room:update`/`product:update` 的賣家呼叫 `/v2/reviews/managed` 會取得**系統中所有租戶**的評價列表。

**AC-003-1**：先在 `ReviewServiceTest.java` 新增**修復前會失敗（紅燈）**測試：以租戶 A 的 `TenantContext` 呼叫 `getReviewsByHandlingStatus`，租戶 B 的評價混入 mock 回傳資料，驗證修復前會錯誤地包含租戶 B 資料。

**AC-003-2**：於 `ReviewRepository` 新增 `findByIsHandledAndTenantId(Boolean isHandled, UUID tenantId, Pageable pageable)`（`@Query` JPQL：`r.listing.tenant.id = :tenantId`，比照 `DEF-026` 保留舊方法 + 新增租戶過濾方法的模式）；`getReviewsByHandlingStatus` 改為：admin（`ROLE_ADMIN`/`ROLE_SUPER_ADMIN`）沿用舊 `findByIsHandled`（跨租戶總覽），非 admin 一律改用新的租戶過濾方法。

**AC-003-3**：AC-003-1 測試轉綠；新增租戶 A 僅看到自己租戶評價、admin 看到全部的對照測試。

---

### US-004：修復 `getUserReviews` 匿名保護繞過（`DEF-030`，已確認，owner-or-admin）

> **SP**: 2 | **優先級**: P0（安全/隱私） | **狀態**: 待執行

**背景**：`getUserReviews(userId, ...)`（`core/review/ReviewService.java:227`）未檢查 `userId` 是否等於 `TenantContext.getCurrentUser()`。`BUYER` 角色持有 `order:read` 權限，任意登入買家可在 `GET /v2/reviews/user/{userId}` 代入任意他人 `userId`，取得該使用者完整評價內容（`toReviewResponse` 僅依 `isAnonymous` 隱藏 `userId`/`userFullName`/`userAvatarUrl`，`content`/`rating`/`listingId` 一律回傳），等於繞過匿名評價的身分保護設計。比照 `DEF-018` 買家自助模式，採 owner-or-admin：僅能查詢自己的評價列表，`ADMIN`/`SUPER_ADMIN` 可例外查詢任何人。

**AC-004-1**：先在 `ReviewServiceTest.java` 新增**修復前會失敗（紅燈）**測試：以使用者 A 的 `TenantContext` 呼叫 `getUserReviews(使用者B的userId, ...)`，驗證修復前會錯誤地成功回傳使用者 B 的評價資料。

**AC-004-2**：`getUserReviews` 新增檢查：非 admin 且 `userId` 不等於當前使用者，拋 `E_1007`。

**AC-004-3**：AC-004-1 測試轉綠；新增查詢自己評價列表放行、admin 查詢任何人放行的對照測試。

---

### US-005：記錄 D 項技術債（`DEF-031`，擱置不修）

> **SP**: 0（僅文件記錄） | **優先級**: 低 | **狀態**: 待執行

**背景**：探查階段發現但經使用者決策擱置的 2 項業務邏輯疑點，非本 Sprint 修復範圍：
1. `markHelpful`：同一使用者重複呼叫會無限累加 `helpfulVotes`/`helpfulCount`（`votes.getOrDefault(userIdStr, 0) + 1` 每次呼叫皆 +1，並非「已投過就擋」），是否為預期行為需業務規則確認。
2. `createReview`（本 Sprint）/`BookingReviewService.createBookingReview`（既有）：皆未驗證傳入的 `orderId`/`bookingId` 是否真的屬於呼叫者本人，理論上可用他人的 `orderId` 建立評價（僅受限於「同一 orderId+listingId 僅能評價一次」的唯一性約束，非歸屬驗證）。

**AC-005-1**：於 `DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-031`，記錄為 🟢 低優先級技術債，不排入排程，待業務規則確認後再評估。

---

## 4. Story Points 規劃

| US | 標題 | SP | 優先級 |
|----|------|----|----|
| US-001 | `ReviewService` 7 個零覆蓋方法單元測試從零建立 | 8 | P1 |
| US-002 | 修復 `markAsHandled`/`markAsUnhandled` 跨租戶寫入（`DEF-028`） | 2 | P0（安全） |
| US-003 | 修復 `getReviewsByHandlingStatus` 跨租戶讀取（`DEF-029`） | 2 | P0（安全） |
| US-004 | 修復 `getUserReviews` 匿名保護繞過（`DEF-030`） | 2 | P0（安全/隱私） |
| US-005 | 記錄 D 項技術債（`DEF-031`） | 0 | 低 |
| **合計** | | **14** | |

> **Velocity 參考**：較 Sprint 66/67/69/71（皆 8 SP）高，與 Sprint 72（13 SP，2 項安全修復）相當，因本 Sprint 包含 3 項安全問題的紅燈測試驗證與修復工作。

---

## 5. Definition of Done

- [x] US-001：新增 `ReviewServiceTest.java`（新檔，涵蓋 `updateReview`/`deleteReview`/`markHelpful`，另含 US-002~004 的紅燈驗證測試，共 18 個測試）
- [x] 開發-編譯-測試循環：每新增一個測試方法後立即編譯 + 執行驗證，未累積（含中途發現並修正 2 類 fixture 缺陷）
- [x] US-002：新增修復前紅燈測試（確認失敗）→ 新增 `checkReviewManagementAuthorization`/`isCurrentUserAdmin` → 修復 `markAsHandled`/`markAsUnhandled` → 測試轉綠
- [x] US-003：新增修復前紅燈測試（確認失敗）→ 新增 `ReviewRepository.findByIsHandledAndTenantId` → 修復 `getReviewsByHandlingStatus` → 測試轉綠
- [x] US-004：新增修復前紅燈測試（確認失敗）→ 修復 `getUserReviews` → 測試轉綠
- [x] 因本 Sprint 修改生產程式碼（US-002+003+004），完成後執行全量回歸 `mvn verify -Pintegration-test`，**1058 tests 0 fail**（單元 716 + 整合 342）
- [x] `make validate-schema` 無漂移，EXIT_CODE=0
- [x] US-005：`DEFERRED_ITEMS_TRACKER.md` 新增 `DEF-031`（低優先級技術債）
- [x] `DEFERRED_ITEMS_TRACKER.md` 更新（`DEF-028`/`DEF-029`/`DEF-030` 記入「已完成延後項目」）
- [x] Sprint 73 Review / Retro / Release Notes + trackers
- [x] 本 Sprint 收尾後立即 push

---

## 6. 產出物

| 產出物 | 路徑 |
|--------|------|
| 後端測試 | `ReviewServiceTest.java`（新檔） |
| 生產程式碼修復 | `ReviewService.java`（US-002/003/004） |
| 生產程式碼修復 | `ReviewRepository.java`（US-003，新增 `findByIsHandledAndTenantId`） |
| 追蹤文件 | `DEFERRED_ITEMS_TRACKER.md`（新增 `DEF-028`/`DEF-029`/`DEF-030`/`DEF-031`） |
| Sprint 收尾 | Review / Retro / Release Notes + trackers |

---

## 7. 後續 Sprint 待處理清單（多 Sprint 測試強化計劃）

依風險排序，供 Sprint 74+ 規劃參考：

1. 其餘：`CmsService`（11 方法）、`ChatService`、`LogisticsService`、`NotificationService`、`PromoService`、`OAuthService`、`IdempotencyService`、`FeatureToggleService`、`NotificationTemplateService`

---

**文件版本**: v1.0
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
**基於**: AISDLC v0.09 Sprint Planning Workflow
