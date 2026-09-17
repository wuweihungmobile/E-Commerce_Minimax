# Sprint 172 Plan — NotificationTemplateService 誤用 E_8001（定價規則錯誤碼）於範本停用情境（DEF-224）

**Sprint**: Sprint 172
**日期**: 2026-09-18

## 1. 起點

`DEFERRED_ITEMS_TRACKER.md` 活躍延後項目中，`DEF-224` 是唯一待排程項目（Sprint 171 修復 `DEF-223` 時意外發現，登記待排程）：`NotificationTemplateService.renderTemplate` 對「模板未啟用」情境誤用語意不符的 `ErrorCode.E_8001`（「無效的定價規則設定」，定價規則模組專用碼）。前置需求為「無」（只需新增一個 `ErrorCode` 常數 + 改一處 throw 語句），本輪直接排入處理。

## 2. 缺口說明

`NotificationTemplateService.renderTemplate`（`NotificationTemplateService.java:212`）對停用中的通知範本（`isActive == false`）拋出 `new BusinessException(ErrorCode.E_8001, "Template is not active")`。`ErrorCode.E_8001` 的固定使用者訊息是「無效的定價規則設定」，與通知範本完全無關——使用者呼叫 `POST /notifications/templates/render` 渲染一個已停用的範本時，會看到文不對題的錯誤訊息「無效的定價規則設定」，而非「此範本已停用」之類的正確說明。

全庫 `grep ErrorCode.E_8001` 確認：`E_8001` 在 `src/main` 中唯一的拋出點就是這一處——其「本業」（定價規則驗證錯誤）目前完全沒有任何呼叫點使用它，此為額外觀察，非本輪缺口範圍，不予處理（不擴大範圍，比照 Rule 3 精準改動）。

HTTP 狀態碼本身因 `DEF-223`（Sprint 171）已修復為語意正確的 422（`E_8001` 屬「無效的 X 設定」類 `UNPROCESSABLE_ENTITY`），故此項僅止於**錯誤碼選錯、訊息文字錯誤**，非狀態碼映射問題，與 `DEF-223` 根因不同。

## 3. 修法決策

比照 `DEFERRED_ITEMS_TRACKER.md` 建議方向：新增專屬 `ErrorCode`（`E_8011`「此範本已停用」），比照同型的 `E_7008`「供應商已停用」（`PurchaseOrderService.java:344`，同樣是「資源存在但目前狀態不可操作」語意）歸類為 **403 FORBIDDEN**，而非沿用 `E_8001` 原本的 422。

新碼編號採用 `E_8011`——`E-8000` 區塊（Pricing/Notification/CMS/Address/Ticket 共用此千位區塊為既有慣例）目前最大值為 `E_8010`（工單狀態轉換），`E_8011` 為下一個可用值。

## 4. 修復內容

- `ErrorCode.java`：`E_8010` 之後新增 `E_8011("E-8011", "此範本已停用")`。
- `NotificationTemplateService.java:212`：`ErrorCode.E_8001` 改為 `ErrorCode.E_8011`。
- `GlobalExceptionHandler.java`：`mapErrorCodeToStatus` 的窮舉 `switch`（Sprint 171 已移除 `default`）新增 `E_8011` 到既有 `FORBIDDEN` case（與 `E_7008`/`E_8007`/`E_8009` 同組）。因 switch 對 enum 已是完全窮舉，新增 `ErrorCode.E_8011` 若未同步在此決定狀態碼會直接編譯失敗——`DEF-223` 建立的編譯期防護機制在本輪首次真正發揮作用。

## 5. 測試

- **紅燈先行**：先同步更新三處既有測試斷言到修復後的預期行為（`GlobalExceptionHandlerTest` 的 `expectedStatusByCode()` 403 分組新增 `E_8011`；`NotificationTemplateServiceTest#renderTemplate_inactive_throwsBusinessException` 改為明確斷言 `exception.getErrorCode() == ErrorCode.E_8011`；`M09NotificationTemplateIntegrationTest#testRenderInactiveTemplate` 改為斷言 `status().isForbidden()` + `jsonPath("$.code").value("E-8011")`），再 `git stash` 只暫存 `NotificationTemplateService.java` 的修復（保留 `ErrorCode.java`/`GlobalExceptionHandler.java`/測試斷言不動），重跑 `NotificationTemplateServiceTest#renderTemplate_inactive_throwsBusinessException`：確認先失敗——`expected: <E_8011> but was: <E_8001>`，證實修復前後行為差異為真。
- `git stash pop` 還原修復後重跑同一測試 + `GlobalExceptionHandlerTest`：全數轉綠。
- `mvn -o compile`：每次修改後立即編譯，通過（確認移除 `default` 的窮舉 switch 因新增 `E_8011` case 而編譯成功，而非意外遺漏）。
- `checkstyle`（main+test）：0 違規。

## 6. 驗證結果

- 紅燈階段：見 §5，`NotificationTemplateServiceTest` 確認先失敗（`expected: <E_8011> but was: <E_8001>`）再轉綠。
- `mvn -o verify`（真實 postgres/redis，`make test-db-up`）：**1550 個單元測試（+1，`GlobalExceptionHandlerTest` 的 `@ParameterizedTest` 隨 `ErrorCode.values()` 增為 137 個列舉值同步多跑 1 案例）+ 482 個整合測試（持平，僅既有 `testRenderInactiveTemplate` 案例斷言內容變更，案例數不變），0 failed**，`BUILD SUCCESS`，checkstyle（main+test）0 違規，PMD 無新增問題。
- **前端呼叫點查證**：`grep` 確認 `frontend/src/app/dashboard/notifications/page.tsx`（範本預覽功能，呼叫 `renderTemplate` → `POST /v2/notification-templates/render`）是此端點唯一的生產前端消費端。查看其 `catch` 區塊（`page.tsx:193-198`）：對任何錯誤皆一律顯示固定文字「無法渲染模板，請檢查變數設定」，未讀取回應的 HTTP 狀態碼或 `$.code` 做任何分支判斷，故本輪把狀態碼由 422 改為 403、錯誤碼由 `E-8001` 改為 `E-8011`，對此前端頁面的實際顯示行為無影響（使用者原本就看不到「無效的定價規則設定」這段文不對題的訊息，因為前端從未透傳它）。
- 未執行 `make validate-e2e`：本輪修改僅涉及後端例外碼選用與對應測試斷言，經上述查證確認不影響既有前端流程；`make validate-release` 執行前會涵蓋完整 E2E 驗證。

## 7. 更新 `DEFERRED_ITEMS_TRACKER.md`

- `DEF-224` 狀態由「⚠️ 已記錄，待排程」改為「✅ 已修復（Sprint 172）」：新增專屬 `ErrorCode.E_8011`「此範本已停用」取代誤用的 `E_8001`，比照同型 `E_7008`「供應商已停用」歸類為 403 FORBIDDEN。詳見本文件。

---

## 8. 誠實揭露總結

- **`E_8001`（「無效的定價規則設定」）本業目前在 `src/main` 中無任何呼叫點使用**：這是本輪查證過程中的額外觀察，可能代表定價規則模組的驗證邏輯尚未實作對應的錯誤路徑，或該驗證發生在別處（如 DTO `@Valid` 層級，不經過 `BusinessException`）。未深入追查根因，不在本輪 `DEF-224`（選錯錯誤碼）範圍內，若未來需要可另開查證。
- **403 FORBIDDEN 的狀態碼選擇涉及一定程度的語意類比判斷**（比照 `E_7008` 而非另立更貼近「資源狀態不可用」語意的其他分組，例如 409 CONFLICT 或 422 UNPROCESSABLE_ENTITY 也可能說得通）：`DEFERRED_ITEMS_TRACKER.md` 原始記錄已建議此類比方向，本輪採用之，非規格文件明文規定，未來若有更明確的業務決策應以該決策為準。
- **草稿階段一度誤判「無生產前端消費端」，經覆核後更正**：初稿曾寫「前端目前無任何頁面呼叫此端點」，實際 `grep` 後發現 `dashboard/notifications/page.tsx` 確有呼叫；已在 §6 更正並確認其 `catch` 為完全泛用（不讀狀態碼/錯誤碼），故結論（此變更不影響前端行為）仍然成立，但過程中的查證疏漏已誠實記錄於此，提醒未來覆核不可只憑印象斷言「無呼叫點」。
