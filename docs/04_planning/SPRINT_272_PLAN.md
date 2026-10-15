# Sprint 272 Plan — M09 訊息通知中心 API 文件補齊（DEF-286 範圍）

## 1. 範圍與起點

Sprint 271（DEF-317，通知管道誠實化）收尾時，使用者對「回歸 AISDLC 流程，下一步該進行什麼？」的提問回覆「請繼續完成任務」。盤點確認：DEF-361／332／317 三項候選缺口已全部結案；追蹤表其餘未結案項目皆為先前已定位的低優先級、刻意不排程項目或等待使用者決定的項目（DEF-347）。依既有慣例（待辦池僅剩低優先級與待決項時，下一步是文件與實作一致性），以 `AskUserQuestion` 列出 4 個候選，使用者選擇「補 `API_M09_Notification.md`」——延續 M05／M06／M07／M08／M18 既有做法：哪個 Sprint 動到哪個模組，就補齊該模組的 API 規格文件。Sprint 271 剛修改 `NotificationConsumerService`，但 M09 通知模組自 Sprint 20 功能上線至今，`docs/02_architecture/` 下從未有任何獨立規格文件（`API_Index.md` 本身已於 Sprint 203 揭露完整性未保證，DEF-286）。

## 2. 讀碼調查

逐一讀取 3 個 Controller（`NotificationController`／`NotificationPreferenceController`／`NotificationTemplateController`）、4 個 Service（`NotificationService`／`NotificationHistoryService`／`NotificationPreferenceService`／`NotificationTemplateService`）、MQ 管線（`NotificationProducerService`／`NotificationConsumerService`）、4 個 Entity 及其對應 DTO，確認：

- 17 個端點的實際路徑、權限字串、請求/回應形狀、錯誤碼。
- Sprint 271（DEF-317）新增的 `dispatch()` 邏輯：IN_APP 必定視為送達、EMAIL 經 `canDeliver()` 真實送出、SMS/PUSH 明確回報未送達。
- `BuyerNotificationService`（18 個業務事件通知方法，無對外端點）與本文件涵蓋的管理員 API 的邊界。
- 權限矩陣：`NOTIFICATION_TEMPLATE_READ/CREATE/UPDATE/DELETE` 在 `RolePermissionMapping` 的角色分配（STORE_OWNER／STORE_STAFF（僅 READ）／ADMIN／SUPER_ADMIN）。
- 既有測試覆蓋範圍：僅 `/v2/notifications/history/**` 與範本管理端點有 HTTP 層（MockMvc + 真實 JWT）整合測試，核心 `/v2/notifications`（send/broadcast/list/read/delete/unread-count）與偏好設定完全沒有 HTTP 層測試。

## 3. 撰寫過程中發現的落差

1. **FRD §6C 已過時**：Sprint 244 寫入的 §6C.2／6C.5（BR-M09-05）／6C.6 仍描述「EMAIL/SMS/PUSH 一律標為已送出」「通知 API 規格尚未建立」，與 Sprint 271 之後的實際行為不符。已同步更新（非新缺陷，是本輪任務自然需要維護的文件）。
2. **DEF-378（新登記，不修復）**：`NotificationTemplateService.createTemplate`／`updateTemplate` 對「範本代碼重複」拋 `ErrorCode.E_6001`，但該碼官方定義是「付款失敗」（付款模組專用），語意與情境完全無關。HTTP 狀態碼（400）本身合理，純粹是借用了錯誤的碼/訊息文字，不影響功能正確性。屬純文件撰寫過程中的附帶發現，修復需決定新碼或改用既有「衝突」類碼，非本輪文件工作範圍，登記不修復。
3. **範本讀取端點的權限不一致**：`GET /v2/notification-templates`、`GET /v2/notification-templates/{id}` 僅要求 `isAuthenticated()`，不檢查 `notification_template:read`；只有渲染預覽端點（`POST .../render`）才真正檢查該權限字串。風險與既有 DEF-330(a) 同類（讀取範圍過寬但尚無已知可利用路徑），記錄於文件 §7，不視為新缺陷登記（已有同類先例覆蓋判準）。

## 4. 實作內容

| 檔案 | 說明 |
|------|------|
| `docs/02_architecture/api/API_M09_Notification.md`（新建） | v1.0，17 個端點完整規格：模組概述、設計重點（8 點）、User Stories、端點總覽、4 個資料模型＋2 組列舉、17 個端點詳細規格、權限矩陣、錯誤碼表、已知限制（8 項）、追蹤性 |
| `docs/02_architecture/API_Index.md` | 標頭「已完整且有守門的模組」清單加入 M09；「詳細 API 規格連結」新增 M09 區塊；更新「最後更新」 |
| `docs/01_requirements/E-Commerce_FRD_v1.0.md` | §6C 橫幅、§6C.2（EMAIL/SMS/PUSH 現況、API 規格現況兩列）、§6C.5（BR-M09-05）、§6C.6 同步更新為 Sprint 271 之後現況，並連結新文件 |
| `backend/src/test/java/com/nextkey/ecommerce/api/ApiRouteDocDriftTest.java` | `GUARDED` 新增 `api/API_M09_Notification.md` → `NotificationController`／`NotificationPreferenceController`／`NotificationTemplateController` |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | DEF-286 進度追記（已完整且有守門模組累計 9 個）；新登記 DEF-378；版本鏈 v3.54→v3.55 |

## 5. 測試

依 CLAUDE.md 開發-編譯-測試循環，修改 `ApiRouteDocDriftTest.java` 後立即編譯並單獨執行：

- `mvn -o clean test -Dtest=ApiRouteDocDriftTest`：**2 個測試皆通過**（`documentedRoutesMatchControllers`／`guardCoversSomething`），證明 `API_M09_Notification.md` 的 17 個端點宣告與 `NotificationController`／`NotificationPreferenceController`／`NotificationTemplateController` 的實際路由完全一致（無遺漏、無幽靈端點）。使用 `clean test` 強制重建，避免 IDE 背景編譯器假性通過風險。

本輪未修改任何 `src/main` 生產程式碼，純文件＋一個既有測試檔案的 `GUARDED` 清單擴充，不影響其他測試的編譯或行為，故未另外執行全量 `mvn verify`（比照 Sprint 262「結算文件」當輪的驗證範圍判斷；若後續 Sprint 對本模組的程式碼有變更，仍會經由常態全量驗證流程覆蓋本次新增的 `ApiRouteDocDriftTest` 斷言）。

## 6. 範圍外與待使用者決定

- **DEF-378**（`E_6001` 誤用）：是否新增專屬錯誤碼或改用既有碼，留待後續評估，非本輪範圍。
- **DEF-347**（SMTP 時序側通道現況與追蹤表描述落差）：Sprint 271 已記錄，仍待使用者決定是否降級/結案，本輪未處理。
- **範本讀取端點權限收斂**：是否要讓 `GET /v2/notification-templates`／`{id}` 也檢查 `notification_template:read`，屬產品/安全決策，本輪僅記錄現況。
- **`BuyerNotificationService` 是否額外送 EMAIL**：Sprint 271 已登記為範圍外產品決策，本輪未變動。

## 7. Velocity 紀錄

本輪性質為文件補齊＋一個既有測試檔案的 `GUARDED` 清單擴充，無新功能開發、無生產程式碼變更，不納入傳統 Story Point 計算。

## 8. 下一步／Action Items

- 待使用者指示：是否繼續依 DEF-286 慣例補齊下一個未納入模組（M01／M02，已知有具體缺口待修，見 DEF-286 原欄位）；或處理 DEF-347 的降級/結案決定；或回到 AISDLC 流程重新盤點。
