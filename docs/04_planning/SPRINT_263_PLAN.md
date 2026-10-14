# Sprint 263 Plan — M08 評價與社交互動文件補齊（DEF-365，使用者拍板優先處理）

**Sprint**: Sprint 263
**日期**: 2026-10-14

## 1. 範圍與起點

[SPRINT_261_PLAN.md](SPRINT_261_PLAN.md) 的文件一致性檢查發現 7 項文件缺口仍待排程（M07 已於 Sprint 262 處理）。以 `AskUserQuestion` 徵詢後，使用者選擇「M08 評價模組（推薦）」——理由是比照 M07 的做法，M08 同樣是「整個模組從需求到 API 規格全線沒有文件」的完整缺口，優先順序邏輯與上輪一致（先補完全沒有文件的整個模組，再處理局部缺口）。其餘 6 項（ReturnRequestController、SupportTicketController、M12 文件落差、M17 文件矛盾、FRD 缺 M17 成員管理與 M03 個人資料編輯 User Story）維持登記狀態，留待後續輪次。

## 2. 方法

逐行讀取 `ReviewController.java`、`BookingReviewController.java` 與其呼叫的 3 個 service（`ReviewService`、`ReviewReplyService`、`BookingReviewService`），核對每個端點的權限字串（交叉查 `Permission.java`／`RolePermissionMapping.java`／`isCurrentUserAdmin()` 定義，排除「@PreAuthorize 字串不在枚舉」的既有已知缺陷模式——本次核對結果：**不存在**此問題）、錯誤碼（交叉查 `ErrorCode.java`／`GlobalExceptionHandler.mapErrorCodeToStatus`）、回應 DTO 欄位。不寫探針、不跑真實服務，純讀碼撰寫規格（比照 Sprint 262 的方法）。

**與 Sprint 262 的差異**：M07 當時能沿用 `API_Index.md` 舊表格的 6 個端點 ID；M08 則發現 `API_Index.md` 舊表格與既有的 [TC_M08_Review.md](../03_testing/TC_M08_Review.md) 兩者的裸編號 `API-M08-NNN` **彼此衝突**（同一編號代表不同端點），因此改用全新、不衝突的 `API-M08-R-NNN`（ReviewController）／`API-M08-BR-NNN`（BookingReviewController）編號，不沿用任一方（詳見 API_M08_Review.md 文件頂部「編號說明」與新登記的 DEF-375）。

## 3. 實作內容（文件為主，含一處既有測試檔案的範圍擴充）

| 檔案 | 變更 |
|---|---|
| `docs/02_architecture/api/API_M08_Review.md` | **新建**（v1.0），18 個端點完整規格：§1 模組概述、§2 端點總覽、§3 資料模型、§4 端點規格（含權限、錯誤碼、業務規則）、§5 權限矩陣、§6 錯誤碼、§7 已知限制、§8 追蹤性 |
| `docs/02_architecture/SRD_Module_Technical_Design.md` | 新增 §7 M08 評價與社交互動模組（架構概覽、核心類設計、已知不對稱表）；原 §7／§8 依序改為 §8／§9；**同時修正 Sprint 262 遺留的章節編號漂移**（原 §7 異常處理架構的內部子標題仍寫 ### 6.1～6.3，未隨外層標題改號）；版本 v1.3→v1.4 |
| `docs/02_architecture/API_Index.md` | M08 連結改指向新文件；舊 Sprint 15/16 表格加註「不完整、與 TC_M08_Review.md 編號衝突、不再維護」；**同時補上 Sprint 262 遺漏的「已完整且有守門模組」摘要句更新**（原句未提及 M07，本次一併補 M07／M08）；版本 v1.4→v1.5 |
| `docs/01_requirements/E-Commerce_FRD_v1.0.md` | 新增 §6D M08 評價與社交互動專章（模組概述、功能總覽、主要用戶、資料模型、BR-M08-01～10、API 規格概要、與 PRD 的差異）；「情境使用指引」Phase 1/2 表同步更正（M08 由 Phase 2 改列 Phase 1）；版本 v1.9→v1.10 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | DEF-365 標記已完成；新登記 DEF-373／374／375；DEF-372 的解決進度註記更新為「M07／M08 已完成」 |
| `backend/src/test/java/com/nextkey/ecommerce/api/ApiRouteDocDriftTest.java` | **程式碼變更**：`GUARDED` 新增 `api/API_M08_Review.md` → `ReviewController`／`BookingReviewController`，自動防護從此納入 M08 |

## 4. 撰寫過程中新發現並登記的缺陷（非本輪修復範圍）

逐行讀碼撰寫規格時系統性比對了「誰能做什麼」與「程式碼實際檢查什麼」，發現三項過去未被任何文件揭露的落差：

- **DEF-373**：回覆評價的授權邏輯三處不一致——`ReviewReplyService.createReply` 要求「listing 的確切擁有者」；`ReviewService.checkReviewManagementAuthorization`（標記已/未處理）與 `BookingReviewService.replyToBookingReview`（回覆訂房評價）則都是「同租戶或管理員」。三者共用同一組 `@PreAuthorize` 權限字串，可能讓同租戶的共同擁有人/`STORE_STAFF` 通過權限檢查卻仍被回覆評價拒絕。
- **DEF-374**：`ReviewService.deleteReview` 的原始碼註解寫「檢查是否是本人或是管理員」，但實作沒有管理員例外分支——管理員無法經此端點刪除他人評價。
- **DEF-375**：`API_Index.md` 舊表格與 `TC_M08_Review.md` 的 `API-M08-NNN` 裸編號彼此衝突（同一編號在兩份文件代表不同端點）。

三項均屬「需要產品/權限模型決策，或需要確認意圖後才能修復」的問題，依既有慣例**登記、不擅自修復**，詳見 DEFERRED_ITEMS_TRACKER.md 各自的狀態欄。

全量驗證（見 §6）另意外重現第四項發現：

- **DEF-376**：`OrderCancellationCompensationIntegrationTest` 的兩個測試案例（`legacyDeductedOrderCancelled_reversesDeduction`、`legacyDeductedOrderShipped_doesNotDeductTwice`）在全量套件情境下失敗，斷言的移動記錄順序 `["OUTBOUND", "RESERVE", ...]` 與預期 `["RESERVE", "OUTBOUND", ...]` 顛倒。根因：測試輔助方法 `simulateLegacyDeductionAtPayment` 以原始 JDBC 用 SQL `NOW()` 手動寫入一筆 `stock_movements`；斷言查詢 `movementTypes()` 是 `ORDER BY created_at, movement_type`，當手動寫入與應用程式碼寫入的 `created_at` 打平時，字母排序的次要鍵讓 `"OUTBOUND"` 排到 `"RESERVE"` 之前——與觀察到的錯誤順序完全吻合。單獨重跑該測試類別 9/9 全過，確認只在全量套件（系統負載較高、時間戳更容易打平）情境下偶發重現，與本輪 M08 文件變更無關，屬既有測試固件脆弱性，已登記 DEF-376，不在本輪修復範圍。

## 5. 測試

依 CLAUDE.md 開發-編譯-測試循環，修改 `ApiRouteDocDriftTest.java` 後立即編譯並單獨執行：
- `mvn -o clean test -Dtest=ApiRouteDocDriftTest`：**2 個測試皆通過**（`documentedRoutesMatchControllers`／`guardCoversSomething`），證明 `API_M08_Review.md` 的 18 個端點宣告與 `ReviewController`／`BookingReviewController` 的實際路由完全一致（無遺漏、無幽靈端點）。使用 `clean test` 強制重建，避免 Sprint 262 曾遇過的 IDE 背景編譯器假性通過風險。
- 全量回歸：見 §6。

## 6. 驗證結果

**第一次 `mvn -o clean verify` 發現 2 個失敗（詳見 §4 DEF-376 排查）**：`OrderCancellationCompensationIntegrationTest` 的 `legacyDeductedOrderCancelled_reversesDeduction`／`legacyDeductedOrderShipped_doesNotDeductTwice` 斷言的移動記錄順序顛倒。單獨重跑該測試類別（`mvn test -Dtest=OrderCancellationCompensationIntegrationTest`）9/9 全過，確認非本輪變更造成的回歸，而是既有測試固件在全量套件負載下的時間戳打平排序脆弱性（根因見 DEF-376）。

**第二次 `mvn -o clean verify`（交叉確認，總耗時 19:26 分鐘）**：
- 單元測試：**2225 個，0 失敗，0 錯誤**（與第一次相同，確認無新增/遺漏）
- 整合測試：**848 個，0 失敗，0 錯誤**（含 `OrderCancellationCompensationIntegrationTest` 9/9 全過，證實 DEF-376 確屬間歇性）
- `ApiRouteDocDriftTest`（本輪修改）：2 個測試皆通過，確認 `API_M08_Review.md` 的 18 個端點宣告與 `ReviewController`／`BookingReviewController` 實際路由完全一致
- `BUILD SUCCESS`

## 7. 範圍外（延後）與待使用者決定

- ReturnRequestController、SupportTicketController、M12 文件落差（DEF-368）、M17 文件矛盾（DEF-369）、FRD 缺 M17 成員管理與 M03 個人資料編輯 User Story（DEF-370／371）：維持登記狀態，排程由使用者後續決定。
- DEF-373（回覆授權邏輯統一方向）、DEF-374（刪除是否該有管理員例外）、DEF-375（兩份舊文件編號衝突是否需互相校正）：本輪新發現，皆登記未修復，待使用者決定。
- 訂房評價（`BookingReview`）功能明顯落後商品/房源評價（無編輯/刪除/統計/搜尋端點）：已在 API_M08_Review.md／SRD §7.3 如實記錄為既有限制，是否補齊屬產品範圍決策，非本輪待修事項。
- PRD 的「評論權重演算法（過濾水軍）」與「檢舉機制」從未實作：已在 FRD §6D.7／API_M08_Review.md §7 揭露，非本輪範圍。

## 8. Velocity 紀錄

本輪性質為文件補齊＋一個既有測試檔案的範圍擴充，無新功能開發，不納入傳統 Story Point 計算。

## 9. 下一步／Action Items

1. 全量驗證通過後 commit＋push，等待雲端 CI 確認全綠。
2. 待使用者決定是否繼續下一輪處理剩餘 6 項文件缺口（ReturnRequest／SupportTicket／M12／M17／FRD 兩條 User Story）、本輪新發現的 3 項權限模型決策（DEF-373／374／375），或轉向其他 Sprint。
