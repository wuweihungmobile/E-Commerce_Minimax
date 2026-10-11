# Sprint 251 Plan — 評價授權缺口修復與部落格卡片死連結（DEF-331a/b/c、DEF-321c）

**Sprint**: Sprint 251
**日期**: 2026-10-11

## 1. 起點與範圍

### 1.1 起點

使用者貼回 Sprint 250 總結（DEF-318 結案）並說「請繼續完成任務」，總結本身點名兩項「還沒做」：`BOOKING_REMINDER`（PRD US-011 明文 Phase 2，非缺口）與 DEF-317（通知 Email/Push 真實送達）。依既有慣例（[[inline-decision-replies-mean-proceed]]：無 `==>` 回覆時只做「總結列為還沒做、且 AI 能獨立處理」的事），核對後兩項皆非可直接動手之處：`BOOKING_REMINDER` 排除在範圍外；DEF-317 的 tracker 原文自述「需要決定寄送政策、使用者偏好、退訂方式」，且啟用真實 SMTP 前還有 DEF-347（時序側通道）擋著，屬需要產品決策的範圍。

因此回頭盤點整個 `DEFERRED_ITEMS_TRACKER.md` 待辦池，讀碼**確認**（非僅憑文件自述）三個 tracker 本身已標註「不需產品決策、可獨立修」的既有缺陷，以 `AskUserQuestion` 呈現「修復已驗證缺陷／處理 DEF-317／文件一致性稽核」三個方向，使用者選擇**「修復已驗證缺陷」**。

### 1.2 讀碼確認的三個缺陷

1. **DEF-321(c)**：`ListingCardService.buildProductCard`/`buildRoomCard`（:95、:144）的 `ctaUrl` 指向 `/products/{id}`／`/rooms/{id}`，前端僅有 `/listings/[id]`（`frontend/src/app/(storefront)/listings/[id]/page.tsx`），無對應的 `/products/[id]`／`/rooms/[id]` 公開頁面（僅有 `/dashboard/products/[id]`／`/dashboard/rooms/[id]` 賣家編輯頁，用途不同）。部落格嵌入卡片（`blog/[slug]/page.tsx:130` 直接 `<a href={card.ctaUrl}>`）因此點擊後 404。

2. **DEF-331(a)**：`BookingReviewService.createBookingReview`（:44-57）完全沒有檢查 `bookingId` 是否歸屬呼叫者本人——任何登入使用者皆可代入他人的 `bookingId` 建立評價。

3. **DEF-331(b)**：`ReviewService.createReview`（:67-123）的 `orderId`/`bookingId` 皆為選填，且提供時也不驗證歸屬本人與對應此次評價的 `listingId`——等同任何人都能對任何商品/房源留評價，不需任何購買證明。

4. **DEF-331(c)**：`BookingReviewService.replyToBookingReview`（:80-90）的「是否為房東」比對寫反——比對的是 `booking.getUserId()`（訂房的客人），而非房源所屬租戶。修復前，真正的房東永遠無法回覆評價（403），而訂房客人本人若呼叫同一端點反而會被誤判通過。

## 2. 修復內容

| 缺陷 | 檔案 | 修法 |
|------|------|------|
| DEF-321c | `ListingCardService.java` | `ctaUrl` 統一改為 `/listings/{id}`（PRODUCT／ROOM 共用同一個前端詳情頁） |
| DEF-331a | `BookingReviewService.createBookingReview` | 新增 `booking.getUserId().equals(userId)` 檢查，不符拋 `E_1007` |
| DEF-331b | `ReviewService.createReview` | 新增 `verifyPurchase` 私有方法：要求至少提供 `orderId`/`bookingId` 其中之一；提供時驗證該筆訂單/訂房歸屬呼叫者本人、且確實關聯此次評價的 `listingId`（訂單側另檢查 `OrderItem.listingId` 是否命中）；新增 `OrderRepository`／`BookingRepository` 依賴 |
| DEF-331c | `BookingReviewService.replyToBookingReview` | 改比對 `TenantContext.getCurrentTenant()` 與 `booking.getRoomListing().getTenantId()`（same-tenant or admin，比照 `ReviewService.checkReviewManagementAuthorization` 既有前例），新增 `isCurrentUserAdmin()` 私有方法 |

刻意不做的範圍（維持 Rule 2「簡潔優先」）：未新增「訂單/訂房狀態須為已完成才能評價」的業務規則——PRD M08（評價與社交互動）僅列「圖文評論、星級評分、商家回覆」，沒有明文要求評價時機，追加這條規則屬超出本次缺陷描述（「沒驗證購買」≠「沒驗證已完成」）的新功能，留待產品決策。

## 3. 實作順序（逐一編譯＋測試，不累積開發）

1. `ListingCardService.ctaUrl` 修正 → 新增 `ListingCardServiceTest`（2 案例） → 編譯＋測試
2. `BookingReviewService.createBookingReview`／`replyToBookingReview` 修正 → 重寫 `BookingReviewServiceTest`（15 案例，含原先將訂房客人誤當房東的 fixture 全面改為租戶比對） → 編譯＋測試
3. `ReviewService.createReview` 新增 `verifyPurchase` → `ReviewServiceTest`／`ReviewServiceSearchTest` 補 `@Mock OrderRepository`/`BookingRepository`（防 `@InjectMocks` 靜默吃 null，比照 Sprint 250 教訓） → 編譯＋測試
4. 新增 `ReviewAuthorizationRealDbIntegrationTest`（真實 PostgreSQL，12 案例，涵蓋三個缺陷的正負面情境＋管理員跨租戶回覆） → 編譯＋測試
5. 全量 `mvn clean verify`

## 4. 守門與測試

| 層 | 內容 | 結果 |
|----|------|------|
| 單元 | `ListingCardServiceTest`（新增 2）、`BookingReviewServiceTest`（重寫，15）、`ReviewServiceTest`／`ReviewServiceSearchTest`（補 Mock，21＋14） | ✅ 52 個測試 0 失敗 |
| 整合（真實 PostgreSQL） | `ReviewAuthorizationRealDbIntegrationTest`（新增，12 案例） | ✅ 0 失敗 |
| 突變驗證 | `git stash` 還原 `BookingReviewService.java`／`ReviewService.java` 為修復前版本，重跑 `ReviewAuthorizationRealDbIntegrationTest` | ✅ 精準轉紅 8/12（`createReview` 的 4 個越權/無購買證明案例、`createBookingReview` 1 個冒用案例、`replyToBookingReview` 3 個——其中「真房東被拒」「訂房客人被誤判為房東」「管理員無法跨租戶」三個 ERROR 訊息皆為原始缺陷的 `Only the host can reply`，直接證明修復前的真實行為），其餘 4 個不受影響案例維持綠燈，`git stash pop` 還原後重跑恢復 12/12 綠燈 |
| 編譯 | 每個步驟後 `mvn -o clean test-compile`（不加 `-q`），確認 `Compiling N source files` | ✅ 全程確認為真實編譯 |
| 全量回歸 | `mvn -o clean verify` | ⏳ 背景執行中，詳見 §5 |

## 5. 全量回歸結果

`mvn -o clean verify`：單元測試 2197 個（0 失敗，較 Sprint 250 的 2190 增加 7 個，對應新增的 `ListingCardServiceTest`＋2、`BookingReviewServiceTest` 重寫後淨增＋5）；整合測試 825 個（0 失敗，較 813 增加 12 個，即新增的 `ReviewAuthorizationRealDbIntegrationTest`）；checkstyle（main＋test）0 違規；PMD 無違規；`BUILD SUCCESS`。

## 6. 待辦（非本輪範圍，登記於 `DEFERRED_ITEMS_TRACKER.md`）

- DEF-317（通知 EMAIL／SMS／PUSH 真實送達）：需先決定寄送政策／使用者偏好／退訂方式，且啟用真實 SMTP 前須先修 DEF-347。
- DEF-321(b)(d)(e)：店鋪前台 `/stores/[slug]` 路由未實作、嵌入卡片 MAINTENANCE/INACTIVE 規格未照做、PRD 路由表與實際頁面對照表缺失。`/privacy`／`/terms` 需使用者提供法務文字，AI 不能代寫。
- DEF-331(d)(e)：`JwtAuthenticationFilter` 不每請求複驗成員/帳號狀態、`ReturnRequestService.isAdmin` 跨租戶放行與 `RolePermissionMapping` 定義不一致——皆需產品決策。
- DEF-346／347／348／355／356：Stripe 開關預設關閉風險、SMTP 時序側通道、MinIO 預設憑證、前端 Docker URL 路徑重複、`make up` migration 執行順序——皆已登記待使用者確認，非本輪處理。
