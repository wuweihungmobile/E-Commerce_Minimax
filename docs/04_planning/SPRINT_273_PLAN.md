# Sprint 273 Plan — M01／M02 API 文件全面改寫（DEF-286／DEF-372 範圍）

## 1. 範圍與起點

Sprint 272（M09 訊息通知中心 API 文件補齊）收尾後，使用者貼回該輪總結並回覆「請繼續完成任務！」，**未對「等你決定」的 DEF-347（SMTP 側通道現況）、DEF-378（`E-6001` 誤用）兩項附加 `==>` 回覆**。依既有慣例（見專案記憶 `inline-decision-replies-mean-proceed` 的「無 `==>` 回覆」變體），此類回覆不視為對待決定項目的授權；應做的是總結自己列為「下一個可做的 AI 獨立項」且不需產品決策的事——Sprint 272 總結明確點出：「下一個可做的 AI 獨立項是比照同一慣例補齊 M01/M02 的已知具體缺口（DEF-286 範圍內）」。本輪即依此執行，DEF-347／DEF-378 維持原狀，不擅自決定。

`DEFERRED_ITEMS_TRACKER.md` 的 DEF-286／DEF-372 兩項已記載 M01/M02 的具體缺口（Sprint 261 唯讀 agent 逐條核對發現）：`category` 欄位必填/選填誤寫、文件宣稱存在但程式沒有的端點（`PUT .../publish`、`GET /v2/categories`、獨立 `/search`）、程式有但文件完全沒提的端點（`GET /v2/public/listings`、SKU CRUD、評分統計、動態定價兩端點、房源維護窗口、開放窗清除）。本輪延續 M05／M06／M07／M08／M09／M18 既有慣例，全面改寫 M01／M02 API 文件並接上 `ApiRouteDocDriftTest` 機械防漂移。

## 2. 讀碼調查

逐一讀取 6 個 Controller（`ListingController`／`PublicListingController`／`DashboardListingController`／`ProductController`／`RoomController`／`RoomCalendarController`）、3 個 Service（`ProductService`／`RoomService`／`RoomCalendarService`）、相關 DTO（`CreateListingRequest`／`ProductDto`／`RoomDto`／`SkuDto`／`ListingResponse`／`ReviewDto.RatingStats`／`PricingDto` 相關類別）、`Listing`／`Product`／`Room`／`RoomCalendar` 實體與 `Permission`／`RolePermissionMapping`，確認：

- 23 個端點（M01 14 個＋M02 8 個＋統一建立入口 1 個）的實際路徑、權限字串、請求/回應形狀、錯誤碼。
- `category`／`categoryId` 在兩種建立請求 DTO 的驗證差異（`ProductDto.CreateRequest` 必填 vs `CreateListingRequest` 選填），並確認全庫無 `Category` 實體或資料表。
- `blockDateRange`／`unblockDateRange` 全庫零生產呼叫點（`grep` 確認無任何 Controller 呼叫）。
- `ProductService.getProducts` 與 `RoomService.getRooms` 的租戶範圍邏輯逐分支比對，發現兩者不對稱（§3 第 1 點）。
- 建立商品/房源的實際行為：直接寫入 `status=ACTIVE`，無 `DRAFT` 階段，也不初始化任何 `room_calendar` 記錄。

## 3. 撰寫過程中發現的落差

1. **DEF-379（新登記，不修復）**：`ProductService.getProducts`（`GET /v2/products`）同一方法內部租戶範圍不一致——無篩選與 `keyword` 分支皆限呼叫者租戶 + `ACTIVE`，但 `category`／`brand`（無 `keyword`）三個分支改用 `ProductRepository.findByCategory`／`findByBrand`／`findByCategoryAndBrand`，查詢條件只有 `status=ACTIVE`，完全不比對 `tenantId`。對照 `RoomService.getRooms` 用 `Specification` 模式使所有篩選分支一致帶租戶條件，確認這是 `ProductController` 側獨有的落差，不是通用模式。純文件撰寫過程中的附帶發現，修復方向需先決定三個分支的設計意圖（收斂為租戶範圍 vs 本就該是全平台分類瀏覽），非單純技術修補，登記不修復。
2. **DEF-380（新登記，不修復）**：FRD §3／§4 描述的「建立 `DRAFT` 商品/房源 → 呼叫發布端點轉 `ACTIVE`」兩階段上架流程（AC-M01-002-1/2、AC-M02-003-1/2）從未實作——`ProductService`／`RoomService` 的兩條建立路徑（型別專屬、統一入口）皆寫死建立時 `status=ACTIVE`，後端也沒有任何 `PUT .../publish`／`PUT .../status` 端點。另兩處衍生落差：AC-M02-003-3／§4.2「技術實現概要」描述的「建立時自動初始化未來 90/365 天 room_calendar 記錄」不存在（`createRoom`／`createRoomFromDashboard` 完全不碰 `room_calendar` 表，該表採「缺列視為可訂」隱性語意）；AC-M02-004-2 描述的 `PUT /api/v2/dashboard/listings/:id/calendar`（批次依日期＋`reason` 封鎖）端點不存在，對應的 `blockDateRange`／`unblockDateRange` 雖存在但零生產呼叫點（Sprint 260，DEF-363 修復悲觀鎖時已發現，本輪只是再次確認）。已在 FRD §3／§4 對應驗收標準前加修訂註記揭露，**刻意未改寫 Gherkin 本文**——是否實作真正的草稿機制與日曆初始化、或把 FRD 改為反映「建立即上架」現況，屬產品決策，非單純文件工作範圍。
3. **FRD §3.7／§4.7 端點概要表與 API_Index.md 商品/房源中心表格已過時**：皆列有不存在的端點（`/search`、`/categories`、`PUT .../publish`、`PUT .../status`），已依新文件同步校正（非新缺陷，是本輪任務自然需要維護的文件，比照 Sprint 272 對 FRD §6C 的處理方式）。

## 4. 實作內容

| 檔案 | 說明 |
|------|------|
| `docs/02_architecture/api/API_M01_Product_Center.md`（全面改寫 v1.0→v2.0） | `ListingController`／`PublicListingController`／`ProductController` 共 14 個端點完整規格：模組概述、設計重點（8 點）、User Stories、端點總覽、4 個資料模型、8 個端點詳細規格、權限矩陣、錯誤碼表、已知限制（含 DEF-379／DEF-380）、追蹤性 |
| `docs/02_architecture/api/API_M02_Listing_Center.md`（全面改寫 v1.0→v2.0） | `RoomController`／`RoomCalendarController` 共 8 個端點完整規格，結構同上，含「房源三種不可訂機制比較表」與房源日曆機制交叉引用 M06／M12 |
| `docs/02_architecture/api/API_Listing_Unified_Create.md`（校正 v1.0→v2.0） | `POST /v2/dashboard/listings` 請求/回應/錯誤碼全部校正為實際行為（`category` 選填、成功狀態碼 200、回應為完整 `ProductDto.Response`／`RoomDto.Response`），改用 `- **端點**:` bullet 宣告格式以接上機械防漂移 |
| `backend/src/test/java/com/nextkey/ecommerce/api/ApiRouteDocDriftTest.java` | `GUARDED` 新增三項：`api/API_M01_Product_Center.md` → `ListingController`／`PublicListingController`／`ProductController`；`api/API_M02_Listing_Center.md` → `RoomController`／`RoomCalendarController`；`api/API_Listing_Unified_Create.md` → `DashboardListingController` |
| `docs/02_architecture/API_Index.md` | 標頭版本 v1.6→v1.7；「已完整且有守門的模組」清單加入 M01／M02；商品中心／房源中心兩個表格全面改寫（移除不存在端點，補上實際端點）；詳細規格連結區塊新增統一建立入口連結 |
| `docs/01_requirements/E-Commerce_FRD_v1.0.md` | 升版 v1.13→v1.14；頂部新增修訂註記；§3.7／§4.7 端點概要表全面改寫；§3／§4 三處新增修訂註記（AC-M01-002-1/2 前、AC-M02-003-1/2/3 前、AC-M02-004-2 前），**不改寫 Gherkin 本文**；版本歷史表新增 v1.14 列 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | DEF-286 進度追記（已完整且有守門模組累計 11 個）；DEF-372 狀態改為 ✅ 已完成；新登記 DEF-379／DEF-380；版本鏈 v3.56→v3.55 |
| `docs/04_planning/RELEASE_TRACKER.md` | 新增 Sprint 273 row（⏳ 待 push）；統計區同步；版本歷史表新增 v3.96 列 |

## 5. 測試

依 CLAUDE.md 開發-編譯-測試循環，修改 `ApiRouteDocDriftTest.java` 後立即編譯並單獨執行：

- `mvn -o clean test -Dtest=ApiRouteDocDriftTest`：**2 個測試皆通過**（`documentedRoutesMatchControllers`／`guardCoversSomething`），證明三份文件宣告的 23 個端點與 `ListingController`／`PublicListingController`／`ProductController`／`RoomController`／`RoomCalendarController`／`DashboardListingController` 的實際路由完全一致（無遺漏、無幽靈端點）。使用 `clean test` 強制重建，避免 IDE 背景編譯器假性通過風險。

本輪未修改任何 `src/main` 生產程式碼，純文件＋一個既有測試檔案的 `GUARDED` 清單擴充，不影響其他測試的編譯或行為，故未另外執行全量 `mvn verify`（比照 Sprint 272 的驗證範圍判斷）。

## 6. 範圍外與待使用者決定

- **DEF-347**（SMTP 側通道現況是否降級/結案）、**DEF-378**（`E-6001` 誤用是否新增專屬錯誤碼）：皆為 Sprint 272 已登記的待決定事項，本輪未處理。
- **DEF-379**（商品分類/品牌篩選租戶範圍）：修復方向需先決定三個分支的設計意圖，屬產品/安全範圍決策。
- **DEF-380**（`DRAFT`/發布兩階段上架與日曆初始化流程未實作）：是否實作真正的草稿機制、或把 FRD 改為反映現況，屬產品決策。
- M10～M16（含 `AdminController` 混雜端點）仍未納入 `ApiRouteDocDriftTest.GUARDED`，為 DEF-286 的下一個候選範圍，本輪未處理。

## 7. Velocity 紀錄

本輪性質為文件全面改寫＋一個既有測試檔案的 `GUARDED` 清單擴充，無新功能開發、無生產程式碼變更，不納入傳統 Story Point 計算。

## 8. 下一步／Action Items

- 待使用者指示：是否繼續依 DEF-286 慣例補齊下一個未納入模組（M10～M16，含 `AdminController` 混雜端點的處理方式）；或處理 DEF-347／DEF-379／DEF-380 其中之一的決策；或回到 AISDLC 流程重新盤點。
