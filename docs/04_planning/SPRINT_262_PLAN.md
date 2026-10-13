# Sprint 262 Plan — M07 結算與撥款文件補齊（DEF-364，使用者拍板優先處理）

**Sprint**: Sprint 262
**日期**: 2026-10-13

## 1. 範圍與起點

[SPRINT_261_PLAN.md](SPRINT_261_PLAN.md) 的文件一致性檢查發現 6 項模組/功能完全沒有任何文件，以 `AskUserQuestion` 徵詢後，使用者選擇「先補 M07 結算（推薦）」——理由是 M07 涉及沖正、撥款重試、手動補產等高風險金流端點，且完全不在自動化測試防護範圍內，相對 M08 評價等其他缺口風險更高。本輪範圍限定在 M07，其餘 5 項（M08、ReturnRequestController、SupportTicketController、FRD 缺 M17 成員管理與 M03 個人資料編輯 User Story）維持登記狀態，留待後續輪次。

## 2. 方法

逐行讀取 `SettlementController.java`、`TransferController.java` 與其呼叫的 5 個 service（`SettlementGenerator`、`SettlementReviewer`、`SettlementReversalService`、`TransferService`、`SettlementCalculator`），核對每個端點的權限字串（交叉查 `Permission.java`／`RolePermissionMapping.java` 確認角色歸屬，排除「@PreAuthorize 字串不在枚舉」的既有已知缺陷模式）、錯誤碼（交叉查 `GlobalExceptionHandler.mapErrorCodeToStatus` 確認 HTTP 狀態碼）、回應 DTO 欄位（直接讀 `SettlementService`／`TransferService` 內的 DTO class）。不寫探針、不跑真實服務，純讀碼撰寫規格（比照 Sprint 244 的方法）。沿用既有 `API_Index.md` 內嵌舊表格的 6 個端點 ID（`API-M07-S-001～006`），因 [TC_M07_Settlement.md](../03_testing/TC_M07_Settlement.md) 已引用，新端點延續編號至 007～010，撥款另立 `API-M07-T-001～002`。

## 3. 實作內容（文件清單，無程式碼邏輯變更）

| 檔案 | 變更 |
|---|---|
| `docs/02_architecture/api/API_M07_Settlement.md` | **新建**（v1.0），12 個端點完整規格：§1 模組概述、§2 端點總覽、§3 資料模型、§4 端點規格（含權限、錯誤碼、業務規則）、§5 權限矩陣、§6 錯誤碼、§7 已知限制、§8 追蹤性 |
| `docs/02_architecture/SRD_Module_Technical_Design.md` | 新增 §6 M07 結算與撥款模組（架構概覽、核心類設計、狀態機與撥款流程圖）；原 §6／§7 依序改為 §7／§8；版本 v1.2→v1.3；檔頭版號同步校正（先前長期停滯於 v1.0，與下方修訂歷史不符） |
| `docs/02_architecture/API_Index.md` | M07 連結改指向新文件；舊 Sprint 15/16 表格加註「不完整、不再維護，以新文件為準」；版本 v1.3→v1.4 |
| `docs/01_requirements/E-Commerce_FRD_v1.0.md` | §6B.6／§6B.7 更新為「已建立」；版本 v1.8→v1.9，新增修訂註記與修訂紀錄列 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | DEF-364 標記已完成 |
| `backend/src/test/java/com/nextkey/ecommerce/api/ApiRouteDocDriftTest.java` | **程式碼變更**：`GUARDED` 新增 `api/API_M07_Settlement.md` → `SettlementController`／`TransferController`，自動防護從此納入 M07 |

## 4. 測試

依 CLAUDE.md 開發-編譯-測試循環，修改 `ApiRouteDocDriftTest.java` 後立即編譯並單獨執行：
- 第一次 `mvn -o test -Dtest=ApiRouteDocDriftTest` 顯示「Nothing to compile」但測試仍通過——**比照既有教訓**（IDE JDT 背景編譯器可能搶先編譯），不直接採信，改用 `mvn -o clean test` 強制重建。
- Clean 重建後，直接用 `grep -a` 在編譯出的 `.class` 檔二進位內容搜尋 `SettlementController`／`api/API_M07_Settlement.md` 字串常數，**確認編譯產物真的包含本輪變更**（`strings` 工具對 macOS 下的 `.class` 檔案誤判為 Mach-O fat binary 而失敗，改用 `grep -a` 繞過）：兩個字串皆命中，證實非假性通過。
- `ApiRouteDocDriftTest`：2 個測試（`documentedRoutesMatchControllers`／`guardCoversSomething`）皆通過，證明 `API_M07_Settlement.md` 的 12 個端點宣告與 `SettlementController`／`TransferController` 的實際路由完全一致（無遺漏、無幽靈端點）。
- 全量回歸：見 §5（Sprint Plan 撰寫時尚在背景執行，結果另行回填）。

## 5. 驗證結果

**第一次 `mvn -o clean verify` 誤判排查**：第一次執行出現 11 個 `ApplicationContext` 載入失敗（`java.net.ConnectException: Connection refused` 連線 `localhost:5432`）。排查確認根因是**忘記先執行 `make test-db-up`**，不是本輪變更造成的回歸——11 個失敗全部是需要真實 Postgres 的整合測試，第一個失敗後 Spring 測試情境快取的「失敗門檻」機制讓後續同類測試直接跳過嘗試。啟動測試資料庫（`nk-test-pg:5432`／`nk-test-redis:6379`）後重跑確認問題消失。

**正式驗證結果**（`make test-db-up` 後 `mvn -o verify`，總耗時 34:22 分鐘）：
- 單元測試：**2225 個，0 失敗，0 錯誤**
- 整合測試：**848 個，0 失敗，0 錯誤**
- `ApiRouteDocDriftTest`（本輪修改）：2 個測試皆通過，確認 `API_M07_Settlement.md` 的 12 個端點宣告與 `SettlementController`／`TransferController` 實際路由完全一致
- `BUILD SUCCESS`

## 6. 範圍外（延後）與待使用者決定

- M08 評價、ReturnRequestController、SupportTicketController、FRD 缺 M17 成員管理與 M03 個人資料編輯 User Story：維持 DEF-365／366／367／370／371 登記狀態，排程由使用者後續決定。
- `API_M07_Settlement.md` §7 已知限制列出的「`Transfer` 併發防護靠唯一索引而非樂觀鎖」「撥款失敗原因可能模糊」兩項，屬觀察記錄，非本輪待修事項。
- Stripe Connect 真實撥款從未對真實 Stripe 服務驗證（沿用既有已知限制，非本輪新增）。

## 7. Velocity 紀錄

本輪性質為文件補齊＋一個既有測試檔案的範圍擴充，無新功能開發，不納入傳統 Story Point 計算。

## 8. 下一步／Action Items

1. 全量驗證通過後 commit＋push，等待雲端 CI 確認全綠。
2. 待使用者決定是否繼續下一輪處理 M08／ReturnRequest／SupportTicket／FRD 兩條 User Story，或轉向其他 Sprint。
