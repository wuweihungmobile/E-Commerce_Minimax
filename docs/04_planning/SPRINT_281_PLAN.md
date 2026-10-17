# Sprint 281 Plan — DEF-331(d)(e)(f) 殘留子項處置

## 1. 範圍與起點

使用者貼回 Sprint 280 總結（含「下一步：DEF-336」提示）並指示「請繼續完成任務」，依最合理化方案決策、真需要人類輸入時才用互動選項詢問。

獨立盤點後發現：上一輪總結提到的下一候選 DEF-336 僅為 🟢 低優先級，而追蹤表裡 10 個 🟡 中優先級項目中有 9 個其實已結案（顏色標記是原始嚴重度，結案後未改色，非真的未處理）。真正還開放、且優先級高於 DEF-336 的是 **DEF-331 的 (d)(e)(f)** 三個殘留子項（(a)(b)(c) 已於 Sprint 251 修復）。

## 2. 讀碼調查

- **(d)**：確認 `DEF-329`（Sprint 235）修復記錄本身已明確記載「未處理：已簽發 access token 在 15 分鐘有效期內仍可使用——`JwtAuthenticationFilter` 不複驗成員狀態（`DEF-331` (d)）」；`DEF-326`（Sprint 240）修復記錄亦有同款「已知限制」註記。確認這是已被兩次接受的既有取捨，非新議題。
- **(e)**：原登記僅提及 `ReturnRequestService.isAdmin` 一處。全庫 grep `ROLE_SUPER_ADMIN` 搜尋同款「ADMIN 與 SUPER_ADMIN 一併放行跨租戶」模式，發現至少 7 個模組同款（`OrderService`／`BookingService`／`PaymentService`／`PaymentStateService`／`CmsService`／`BookingReviewService`／`ReviewReplyService`／`ReturnRequestService`）。讀 `RolePermissionMapping.java` ADMIN 權限區塊，發現多個權限旁已有 Sprint/DEF 編號的「平台端跨租戶」inline 註解（`RETURN_READ` 標 Sprint 118／DEF-044；`SUPPORT_TICKET_*` 標 Sprint 91；`NOTIFICATION_TEMPLATE_*` 標 Sprint 128／DEF-073；`DASHBOARD_READ`/`FAQ_*`/`KNOWLEDGE_*`/`MEDIA_*` 標 Sprint 129／DEF-075；`CMS_*` 標 Sprint 129-130／DEF-092），證實這是歷史上多個 Sprint 持續正式拍板的既有設計，只有最上面「租戶內管理」的標題註解沒跟上更新。讀 `TenantContextFilter.resolveEffectiveTenantId` 與 `DEF-038` 完整修復記錄，確認其限制的是「header 偽造切換 TenantContext」這個不同機制層級，與 Service 層 isAdmin 擁有權放行不衝突。
- **(f)**：讀 `TenantService.updateFeatureToggle`，確認第 504 行驗證邏輯是 `existsByTenantIdAndUserIdAndStatus`（僅驗證有效成員），與方法上方註解「Verify user is owner of this tenant」不符。讀同檔案既有 5 處店主驗證前例（319、751、809、954、1011 行），確認既有慣例是 `existsByTenantIdAndUserIdAndStoreRole(tenantId, userId, StoreRole.STORE_OWNER)`。

## 3. 使用者決策

以 `AskUserQuestion` 詢問 (e)：「ADMIN 角色該不該可以跨店鋪（租戶）處理退貨、訂單、付款、評價等客服事務？」選項：維持現狀（推薦）／收緊為租戶內／先給完整清單再決定。使用者選擇**維持現狀：ADMIN 本來就是平台客服**。

(d)(f) 不需使用者決策——(d) 有明確先例可循，(f) 是純技術修復（補上與方法註解一致的檢查，不改變任何業務語意）。

## 4. 實作內容

| 項目 | 檔案 | 說明 |
|------|------|------|
| (d) | `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | 維持現狀不處理，純文件記錄，無程式碼變更 |
| (e) | `backend/src/main/java/com/nextkey/ecommerce/domain/model/user/RolePermissionMapping.java` | 改寫 ADMIN 區塊標題註解，反映「一般業務租戶內、特定平台客服職能跨租戶」現況，不改動任何權限邏輯 |
| (e) | `backend/src/main/java/com/nextkey/ecommerce/api/filter/TenantContextFilter.java` | `resolveEffectiveTenantId` 註解補充說明與 Service 層 isAdmin 擁有權放行是不同機制層級，核心邏輯（DEF-038）不動 |
| (f) | `backend/src/main/java/com/nextkey/ecommerce/core/tenant/TenantService.java` | `updateFeatureToggle` 的店主檢查改用 `existsByTenantIdAndUserIdAndStoreRole(tenantId, userId, STORE_OWNER)`，比照同檔案既有 5 處前例 |
| (f) | `backend/src/test/java/com/nextkey/ecommerce/core/tenant/TenantServiceTest.java` | 5 個 `updateFeatureToggle` 相關測試同步改為 mock 新方法；「非店鋪成員」測試更名為「非店主」，DisplayName 明確記載同時覆蓋「完全非成員」與「有效成員但非店主」兩種情境 |
| 全項 | `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | DEF-331 條目更新，記錄 (d)(e)(f) 最終處置 |
| 全項 | `docs/04_planning/RELEASE_TRACKER.md` | 新增 Sprint 281 row |

## 5. 測試

依 CLAUDE.md 開發-編譯-測試循環，(f) 修復後立即 `mvn -o compile`／`test-compile`／單類別測試確認。

- (f)：`TenantServiceTest$UpdateFeatureToggleTests` 5/5 通過。**突變驗證**（`git stash` 還原修復前程式碼、保留新測試重跑）：5/5 全數轉紅（4 個因呼叫未 mock 的舊方法被誤判無權限而報 `BusinessException`，1 個因 stub 從未被觸發遭 Mockito 嚴格模式判定 `UnnecessaryStubbingException`），證實非巧合通過；`git stash pop` 恢復後重新確認 5/5 轉綠。過程中兩次遇到 VS Code JDT/ECJ 背景編譯寫壞 `target/classes` 的既知假性失敗（`Flexible Constructor Bodies` 編譯層級錯誤），`mvn clean` 後消失，與本輪程式碼變更無關。
- 全量 `mvn -o clean verify`：**單元 2273/2273、整合 857/857，0 失敗**（與 Sprint 280 數字相同——本輪未新增/刪除任何測試方法，僅修改既有 mock 呼叫與註解文字）；Checkstyle 0 違規；PMD 正常完成；exit code 0。以 `grep -l "FAILURE!"` 對全部 surefire／failsafe 報告交叉確認真正的測試失敗標記數為 0，不只信任 `-q` 靜默模式的文字輸出。

## 6. 範圍外

- **DEF-336**（清空購物車未清優惠券標記）：追蹤表低優先級區下一個候選，非本輪範圍。
- **DEF-348 後續**（移除 MinIO/S3 預設憑證值）：使用者已有方向決策（維持 yml 預設、上線檢查強制覆寫），移除預設值本身需同步整合測試設定，工作量較大，留待下一輪評估是否排入。

## 7. 下一步／Action Items

- 追蹤表低優先級區仍有 DEF-336 及約 30 餘項 🟢 低優先級技術債可作為下一輪候選。
- DEF-348 後續（移除硬編碼預設憑證）可視下一輪範圍決定是否排入。
