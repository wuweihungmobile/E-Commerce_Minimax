# Sprint 265 Plan — 退貨申請與客服工單文件補齊（DEF-366／367，延續同型模組清查）

**Sprint**: Sprint 265
**日期**: 2026-10-14

## 1. 範圍與起點

[SPRINT_264_PLAN.md](SPRINT_264_PLAN.md) 完成 DEF-373／374 後，剩餘 8 項文件/測試穩定性缺口待排程。以 `AskUserQuestion` 徵詢後，使用者選擇「延續同型模組文件清查：DEF-366＋367」——`ReturnRequestController`（退貨申請）與 `SupportTicketController`（客服工單）兩個完整功能模組運作 100+ Sprint 至今從未有任何 API 規格、SRD 或 FRD 提及，與 M07/M08 當時的情況完全同型，且 Sprint 261 登記時已建議「下一輪若處理 DEF-366 建議一併核實 DEF-367」。其餘 6 項（M12 文件落差 DEF-368、M17 文件矛盾 DEF-369、FRD 缺兩條 User Story DEF-370／371、M08 編號衝突 DEF-375、測試排序脆弱性 DEF-376）維持登記狀態，留待後續輪次。

## 2. 方法

逐行讀取 `ReturnRequestController.java`、`SupportTicketController.java` 與其呼叫的 Service（`ReturnRequestService`、`SupportTicketService`、`SupportMessageService`），核對每個端點的權限字串、請求/回應 DTO、錯誤碼（交叉查 `ErrorCode.java`／`GlobalExceptionHandler`）、狀態機與多租戶隔離邏輯。先派一個唯讀 agent 收集基礎事實（模組歸屬、既有文件現況、Controller/Service/Entity/測試完整內容），再由主控對話親自讀 Controller 本體與 `ApiRouteDocDriftTest.java` 做關鍵核實（權限字串與路由這部分風險最高），寫完文件後用 `ApiRouteDocDriftTest` 機械驗證端點清單與 Controller 實際路由完全一致，才繼續下一步。

**模組歸屬判斷**：退貨申請在 PRD 資料表定義（§8.2.12/13）、FRD 功能總覽表、既有測試命名（`M05ReturnRequestIntegrationTest`）三處皆指向 M05，但既有 `API_M05_Order.md` 的「範圍外」卻誤寫「退貨（M11）」——M11 實際是物流追蹤模組，與退貨申請業務性質不符，判定為該行的既有誤記，歸入 M05 並更正該行。客服工單的模組歸屬無爭議，PRD §6.10 明確定為 M18（知識管理系統的客服子功能），且 `docs/01_requirements/M18_Knowledge_Management_SPEC.md` §5 已有草稿規格（放在需求文件而非架構文件，未納入機械防漂移，且漏列 2 條端點）。

## 3. 實作內容（文件為主，含一處既有測試檔案的範圍擴充）

| 檔案 | 變更 |
|---|---|
| `docs/02_architecture/api/API_M05_Return.md` | **新建**（v1.0），8 個端點完整規格：§1 模組概述、§2 端點總覽、§3 資料模型、§4 端點規格、§5 權限矩陣、§6 錯誤碼、§7 已知限制、§8 追蹤性 |
| `docs/02_architecture/api/API_M18_Support.md` | **新建**（v1.0），11 個端點完整規格，結構同上；明確標註僅涵蓋 M18 的客服工單子功能，不含知識庫等其他子功能 |
| `docs/02_architecture/api/API_M05_Order.md` | 更正「範圍外」欄兩處既有誤記：「退貨（M11）」→ 更正為指向新文件並說明歸屬判斷；「結算（M13）」→ 更正為 M07（settlement 實際模組編號，複核時發現的額外落差） |
| `docs/02_architecture/API_Index.md` | 新增 M05 退貨申請、M18 客服工單兩個模組的連結與摘要；版本 v1.5→v1.6 |
| `docs/02_architecture/SRD_Module_Technical_Design.md` | 新增 §8 M05 退貨申請模組、§9 M18 客服工單模組；原 §8／§9 依序改為 §10／§11；**同時修正 Sprint 264 遺留的文件缺口**：§7 M08 評價模組的回覆授權程式碼範例與不對稱表仍描述 DEF-373／374 修復前的舊邏輯，本次一併更正；版本 v1.4→v1.5 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | DEF-366／367 標記已完成；DEF-372 的解決進度註記更新；端點數量複核後更正（9→8、12→11） |
| `backend/src/test/java/com/nextkey/ecommerce/api/ApiRouteDocDriftTest.java` | **程式碼變更**：`GUARDED` 新增 `api/API_M05_Return.md` → `ReturnRequestController`、`api/API_M18_Support.md` → `SupportTicketController`，自動防護從此納入兩模組 |

## 4. 撰寫過程中新發現的落差（非本輪修復範圍）

- **端點數量複核**：Sprint 261 登記時估計 `ReturnRequestController` 有 9 個端點、`SupportTicketController` 有 12 個端點；逐行讀碼後確認實際分別是 8 個與 11 個（各少 1），已在 DEFERRED_ITEMS_TRACKER.md 更正，非新缺陷，純屬原始估計的計數誤差。
- **`ReturnDto.Response` 缺少 `reviewedBy`／`receivedBy`／`updatedAt` 三個欄位**：Entity 存在這三個欄位，但回應 DTO 從未映射，呼叫方無法得知審核/收貨的操作者與更新時間。已記錄於 API_M05_Return.md §7，未登記新 DEF（風險低，純文件層級揭露）。
- **`ReturnRequestItem.skuId` 的 `nullable=false` 與實際可能寫入 `null` 的潜在落差**：Service 建立退貨單時若原訂單品項的 SKU 已被刪除會代入 `null`，與 Entity 宣告矛盾，但無測試驗證此邊界情境的真實行為。已記錄於 API_M05_Return.md §7，建議後續確認。
- **`M18_Knowledge_Management_SPEC.md` §5.4 的既有 API 表漏列 2 條端點**（店家層與平台層的工單詳情查詢），新文件已補齊，純文件層級落差，未登記新 DEF。
- **兩個 Controller 的 `@PreAuthorize` 層完全無測試覆蓋**：既有測試（Service 單元測試＋ `M05ReturnRequestIntegrationTest`）皆直接呼叫 Service，不經過 Spring Security／HTTP 層。本文件列出的權限字串皆已核對 `RolePermissionMapping.java` 確認掛到了角色，但未被任何測試在 HTTP 層驗證過。已記錄於兩份新文件 §7，未新增測試（超出本輪「純文件補齊」範圍，比照 M07/M08 既有慣例）。
- **`E-8009`／`SenderType.SYSTEM` 是定義了但全庫無任何拋出/寫入點的死碼**（客服工單模組）：已誠實記錄於 API_M18_Support.md §7，非新發現的缺陷（是刻意的 IDOR 防護設計遺留的副產品）。

以上均屬文件層級的誠實揭露，不涉及生產程式碼變更，依既有慣例不逐一登記新 DEF（除非後續有人需要依賴這些細節做決策時再另案處理）。

## 5. 測試

依 CLAUDE.md 開發-編譯-測試循環，修改 `ApiRouteDocDriftTest.java` 後立即編譯並單獨執行：
- `mvn -o clean test -Dtest=ApiRouteDocDriftTest`：**2 個測試皆通過**（`documentedRoutesMatchControllers`／`guardCoversSomething`），證明 `API_M05_Return.md`（8 端點）與 `API_M18_Support.md`（11 端點）的宣告與 `ReturnRequestController`／`SupportTicketController` 的實際路由完全一致（無遺漏、無幽靈端點）。使用 `clean test` 強制重建，避免既有 IDE 背景編譯器假性通過風險。
- 全量回歸：見 §6。

## 6. 驗證結果

`mvn clean verify -o`（背景執行，總耗時 20:12 分鐘）：
- 單元測試：**2231 個，0 失敗，0 錯誤**（與 Sprint 264 數量相同——本輪純文件補齊＋`ApiRouteDocDriftTest.GUARDED` 資料擴充，未新增測試方法）
- 整合測試：**853 個，0 失敗，0 錯誤**（同上）
- `BUILD SUCCESS`
- 數量以 `target/surefire-reports/*.txt`／`target/failsafe-reports/*.txt` 加總核對，非僅讀終端輸出（本次已改將輸出導向獨立記錄檔，避免 Sprint 264 曾遇過的 `tail` 截斷問題）

## 7. 範圍外（延後）與待使用者決定

- M12 文件落差（DEF-368）、M17 文件矛盾（DEF-369）、FRD 缺兩條 User Story（DEF-370／371）、M08 編號衝突（DEF-375）、測試排序脆弱性（DEF-376）：維持登記狀態，排程由使用者後續決定。
- 兩個 Controller 的 `@PreAuthorize` 層缺乏測試覆蓋：已於 §4 揭露，是否值得補一輪 Controller/HTTP 層測試屬獨立範圍決策，非本輪待修事項。
- `ReturnDto.Response` 欄位缺口、`ReturnRequestItem.skuId` 的潜在空值矛盾：已於 §4 揭露，修復與否屬產品/工程決策，非本輪範圍。

## 8. Velocity 紀錄

本輪性質為文件補齊＋一個既有測試檔案的範圍擴充，無新功能開發，不納入傳統 Story Point 計算。

## 9. 下一步／Action Items

1. 全量驗證通過後 commit＋push，等待雲端 CI 確認全綠。
2. 待使用者決定是否繼續下一輪處理剩餘 6 項缺口（M12／M17 文件類 DEF-368／369、FRD 缺兩條 User Story DEF-370／371、M08 編號衝突 DEF-375、測試穩定性 DEF-376），或轉向其他 Sprint。
