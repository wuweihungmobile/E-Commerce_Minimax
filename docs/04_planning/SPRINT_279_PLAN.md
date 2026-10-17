# Sprint 279 Plan — DEF-286 主線結案＋DEF-341／DEF-344 錯誤碼與輸入驗證修復

## 1. 範圍與起點

使用者貼回 Sprint 278 總結＋「請繼續完成任務！」，**沒有對總結列出的 6 項「等你決定」事項（DEF-347／379／380／384／386／387）附加 `==>` 回覆**。依既有慣例（專案記憶 `inline-decision-replies-mean-proceed` 的「無 `==>` 回覆」變體），此類回覆不是對待決定項目的授權，應做的是「AI 能獨立處理、追蹤表沒標『需使用者決定』」的事。

Sprint 278 的總結本身沒有列出具體的「下一個可做項目」（不像 Sprint 229 的前例），因此本輪依「`aisdlc-next-step-request-playbook`」的盤點步驟：先核對 `git status`／最近 commit，再逐列檢視 `DEFERRED_ITEMS_TRACKER.md` 活躍區。盤點時發現兩個重要落差：

1. **DEF-286（API 文件補齊主線，Sprint 203 起）實質已於 Sprint 277 完成**——`API_Index.md` 檔頭已自述「至此 PRD 列出的全部模組皆已有獨立 API 規格文件」，`ApiRouteDocDriftTest.GUARDED` 核對後確認 17 個模組規格皆已納入機械防漂移（M14 全部端點、M16／M17 各自一部分端點因實作於混雜多模組的 `AdminController`，為永久結構限制而非待辦）——但追蹤表 DEF-286 的狀態欄自 Sprint 273 後就沒再更新，從未正式標記 ✅ 完成，Sprint 274～277 的實際進度完全沒有回填（同樣是 `deferred-items-tracker-drift` 記憶描述的「最容易被跳過的一步」）。
2. **`API_Index.md` 自身的「✅ 已完整且有守門的模組」彙總句漏列 M12、M17**——兩者皆已於 Sprint 266 完成改寫並納入 `GUARDED`，但該彙總句從未提及，可能是該句在後續多輪被逐次追加編輯、而 Sprint 266 的項目不在當時的編輯範圍內所致。

同時盤點發現追蹤表上還有兩項明確、**不需產品決策**、延續 Sprint 278 同一主題（錯誤碼跨領域誤用、輸入驗證缺口）的既有登記項目：

- **DEF-341**：`BookingService.getCalendar` 的日曆參數驗證（`endDate` 早於 `startDate`、區間超過 92 天）與 `PaymentStateService` 的 Stripe 未啟用檢查，皆誤用語意不符的錯誤碼（分別是 `E-3001`「無效的刊登類型」與 `E-6002`「付款已取消」）。追蹤表本身已寫明修法方向（改用語意相符的碼），純屬機械性修正。
- **DEF-344**：`GET /v2/listings`／`/v2/products`／`/v2/rooms` 的 `sortBy`／`sortDir` 參數亂填會落入全域例外處理器 catch-all 變 `500`，而非回報 `400` 驗證錯誤。追蹤表已指出可重用 `PageableUtils.sortOf`（M05／M06 於 Sprint 243，DEF-339 已建立的相同模式）。

本輪即處理這 4 項（DEF-286 收尾、DEF-341、DEF-344），6 項待決定事項維持原狀，不擅自決定。

## 2. 讀碼調查

- **DEF-286**：讀 `ApiRouteDocDriftTest.java` 全部 `GUARDED.put(...)` 項目，核對 `API_Index.md` 的模組清單與各別 API 規格文件的 §7「已知限制」，確認 M12／M17 的守門範圍（M17 僅 `TenantController`／`StorefrontController`，`AdminController` 的 14 個端點不受保護）。
- **DEF-341**：`grep` 全庫 `E_4003`／`E_4004` 實際用法，確認 9 個既有呼叫點（`OrderService`×3、`BookingService`×2、`PricingService`×2、`DashboardPricingController`、`RoomCalendarController`）一致用 `E_4003` 表示「結束不晚於開始」類驗證，僅 `RedisCartService` 一處用 `E_4004`——依多數慣例選 `E_4003`。讀 `FeatureToggleService.checkFeatureEnabled` 確認它正是全庫其他 feature toggle 檢查（`RETAIL_ENABLED`／`BOOKING_ENABLED`）共用的既有方法，直接拋 `E_2004`（403），比手寫新碼更貼合現有慣例。
- **DEF-344**：讀 `PageableUtils.sortOf` 簽章與既有呼叫點（`OrderService`／`BookingService`），讀 `ListingController`／`ProductService`（含 `mapSortField` 欄位映射）／`RoomService` 三處目前的排序建構方式，確認 `ProductService` 需要「先驗證白名單再映射欄位路徑」兩步驟（不能直接套用 `sortOf` 回傳值），`RoomService` 的 `Room` 實體本身無 `basePrice` 欄位，白名單不應納入。

## 3. 實測發現的意外問題（非原計畫範圍，過程中排查並修復）

全量測試前先針對受影響測試類別跑一輪，發現 3 個既有測試失敗：

1. `BookingApiRealStackIntegrationTest.calendar`：斷言舊的 `422 E-3001`，因修復直接轉紅——補上既有測試遺漏。
2. `BookingPaymentIntegrationTest.stripeCheckout_guards`：`initiateStripeBookingCheckout` 改用 `checkFeatureEnabled` 後**沒有拋出例外**。根因：該測試的 `@BeforeEach` 對 `FeatureToggleService`（`@MockBean`）設了 `lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString())`——這是一條跨「全部 feature key」的常態放行 stub（原意是讓 `BOOKING_ENABLED` 等其他檢查不干擾測試），與同一個 `@BeforeEach` 另外針對 `isFeatureEnabled(STRIPE_PAYMENT_ENABLED)` 設的 `false` stub **互相矛盾**——`initiateStripeBookingCheckout` 改走 `checkFeatureEnabled` 後，吃到的是永遠不丟的那條。修法：改用 `doAnswer` 動態代理到 `isFeatureEnabled`，兩者永遠同步，不會再各自漂移。
3. 以此為線索，排查全庫另有 27 個整合測試檔案也有同款 `doNothing()` 常態放行 stub；逐一檢查後確認僅 5 個檔案實際測試 Stripe checkout 發起（`BookingPaymentApiIntegrationTest`／`BookingCancellationRefundIntegrationTest`／`OrderAutoRefundIntegrationTest`／`StoreSuspendedPaymentIntegrationTest`／`OrderStripeCheckoutRetryIntegrationTest`），實跑後確認**皆通過**（它們的「停用」情境測試並非透過本次改動的兩個方法觸發，或已在各自測試內覆寫為啟用狀態）。

## 4. 實作內容

| 檔案 | 說明 |
|------|------|
| `backend/src/main/java/com/nextkey/ecommerce/core/booking/BookingService.java` | `getCalendar` 的兩處驗證改用 `E_4003`（原 `E_3001`） |
| `backend/src/main/java/com/nextkey/ecommerce/core/payment/PaymentStateService.java` | `initiateStripeCheckout`／`initiateStripeBookingCheckout` 改用 `featureToggleService.checkFeatureEnabled(...)`（原手寫 `if(!isFeatureEnabled) throw E_6002`） |
| `backend/src/main/java/com/nextkey/ecommerce/api/controller/ListingController.java` | 新增 `SORTABLE_FIELDS` 白名單（`basePrice`／`createdAt`／`updatedAt`），改用 `PageableUtils.sortOf` |
| `backend/src/main/java/com/nextkey/ecommerce/core/product/ProductService.java` | 新增同一白名單，先驗證再經既有 `mapSortField` 映射到 `listing.*` |
| `backend/src/main/java/com/nextkey/ecommerce/core/room/RoomService.java` | 新增白名單（`createdAt`／`updatedAt`，不含 `basePrice`） |
| `backend/src/test/java/com/nextkey/ecommerce/core/booking/BookingServiceGetCalendarValidationTest.java`（新建） | 4 個測試：`endDate` 早於 `startDate`→`E_4003`、區間超過 92 天→`E_4003`、恰好 92 天不拋例外、不是 ROOM 仍是 `E_3001`（回歸確認） |
| `ProductServiceTest.java`／`RoomServiceTest.java` | 各新增 2 個測試（白名單外欄位、非法方向皆拋 `E_9000`） |
| `ListingControllerE2ETest.java` | 新增 2 個 MockMvc 測試（`sortBy`／`sortDir` 非法值回 `400 E-9000`，而非 `500`） |
| `PaymentStateServiceStripeTest.java`／`PaymentStateServiceBookingTest.java` | 既有「toggle 關閉」測試斷言 `E_6002`→`E_2004`；`BookingTest` 新增 `stripeDisabled()` 輔助方法（原測試靠「未 stub 的 boolean 預設 false」間接製造情境，改用 `checkFeatureEnabled` 後必須明確 stub） |
| `BookingPaymentIntegrationTest.java` | `@BeforeEach` 的 `checkFeatureEnabled` stub 改用 `doAnswer` 動態代理到 `isFeatureEnabled`（見第 3 節）；`stripeCheckout_guards` 斷言 `E_6002`→`E_2004` |
| `OrderApiRealStackIntegrationTest.java`／`BookingApiRealStackIntegrationTest.java` | MockMvc 層級斷言同步更新（`calendar` 測試 `422 E-3001`→`400 E-4003`；付款測試 `400 E-6002`→`403 E-2004`） |
| `docs/02_architecture/api/API_M01_Product_Center.md`／`API_M02_Listing_Center.md` | `sortBy` 白名單說明、錯誤碼表、已知限制列（移除已解決項）、追蹤項目同步 |
| `docs/02_architecture/api/API_M05_Order.md`／`docs/02_architecture/API_M06_Booking.md` | Stripe／日曆錯誤碼表、已知限制列（移除已解決項）同步 |
| `docs/02_architecture/API_Index.md` | 「✅ 已完整且有守門的模組」彙總句補上 M12／M17 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | DEF-286 標記 ✅ 已完成並回填 Sprint 274-277 進度；DEF-341／DEF-344 標記 ✅ 已修復 |
| `docs/04_planning/RELEASE_TRACKER.md` | 新增 Sprint 279 row（⏳ 待 push）；統計區同步 |

## 5. 測試

依 CLAUDE.md 開發-編譯-測試循環，每次程式碼修改後立即 `mvn -o clean test-compile` 確認編譯成功。

- 目標測試類別（`BookingServiceGetCalendarValidationTest`／`PaymentStateServiceStripeTest`／`PaymentStateServiceBookingTest`／`ProductServiceTest`／`RoomServiceTest`）：**112 個測試全過**。
- 受影響的真實全棧整合測試（`BookingPaymentIntegrationTest`／`OrderApiRealStackIntegrationTest`／`BookingApiRealStackIntegrationTest`／`ListingControllerE2ETest`）：第一輪 **2 個失敗**（見第 3 節），修復後第二輪 **52 個全過**。
- 排查用的 5 個額外整合測試檔案：**56 個全過**，確認未受影響。
- 全量 `mvn -o clean verify`：**單元 2273/2273、整合 856/856，0 失敗、0 錯誤**。第一輪背景執行在全部測試通過後被 checkstyle-test 攔下（`BookingPaymentIntegrationTest.java` 多了一個未使用的 `import static org.mockito.Mockito.doAnswer`——改用 `lenient().doAnswer(...)` 鏈式呼叫而非靜態呼叫，該 import 從未被直接引用）；移除該 import 後以 `mvn -o clean test-compile checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test` 確認「Compiling 386+300 source files」＋0 violations，未重跑全部 3129 個測試（1 行 import 刪除不影響任何測試行為，且已由第一輪驗證過全數通過）。
- `git commit`（`672ab160`）前 pre-commit 快速檢查通過；`git push`（`672ab160`）前 pre-push 輕量守門（act Backend Unit＋schema 漂移＋frontend）通過；雲端 CI run [39002809190](https://github.com/wuweihungmobile/E-Commerce_Minimax/actions/runs/39002809190) 三個 job 皆 success，以 `gh run view --json status,conclusion,jobs` 結構化查詢交叉確認（不只信任 `gh run watch` 文字輸出或背景任務通知的 exit code）。

## 6. 範圍外與待使用者決定

維持原狀，不擅自決定：

- **DEF-347**：忘記密碼側通道——已查明 `AccountSecurityService.sendPasswordResetMail` 自 Sprint 211 起已改非同步，追蹤表描述與現況有落差，是否降級/結案待使用者確認。
- **DEF-379**：`ProductService.getProducts` 分類/品牌篩選無租戶範圍——修復方向需先決定三個分支的設計意圖（收斂為租戶範圍 vs 全平台瀏覽），屬產品/安全決策。
- **DEF-380**：`DRAFT`/發布兩階段上架流程從未實作——是否實作真正草稿機制、或把 FRD 改為反映現況，屬產品決策。
- **DEF-384**：`AdminService.getPlatformStats` 的 `totalPlatformGMV` 為假資料——技術修復不難，但「過去顯示的 GMV 數字皆為假資料」是否需要揭露，屬溝通/產品決策。
- **DEF-386**：`PostController` 自製租戶解析＋硬編碼 fallback 租戶 UUID——修復方向明確，但涉及既有測試對 `UserDetails` 參數的依賴評估，優先序待使用者決定。
- **DEF-387**：M15 CMS 兩套獨立媒體庫實作——整合成一套還是維持兩套，屬產品決策。

## 7. Velocity 紀錄

本輪性質為 3 個已登記缺陷的機械性修復（錯誤碼替換、輸入驗證白名單）＋1 個文件追蹤狀態的回填結案，無新功能開發，不納入傳統 Story Point 計算。

## 8. 下一步／Action Items

- 待使用者對 6 項方向性問題（DEF-347／379／380／384／386／387）其中之一或多個拍板。
- 若使用者傾向繼續累積低風險技術債修復：`DEFERRED_ITEMS_TRACKER.md` 低優先級區仍有 DEF-336（清空購物車未清優惠券標記）等候選，可延續本輪同一模式。
