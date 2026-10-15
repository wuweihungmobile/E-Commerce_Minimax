# Sprint 268 Plan — M08 編號衝突校正與測試排序脆弱性修復（DEF-375／376）

**Sprint**: Sprint 268
**日期**: 2026-10-15

## 1. 範圍與起點

[SPRINT_267_PLAN.md](SPRINT_267_PLAN.md) 完成後，Sprint 261 文件一致性稽核當時登記的 9 項缺口僅剩 2 項未處理：DEF-375（M08 編號衝突）、DEF-376（測試排序脆弱性）。兩項皆在追蹤表中明寫「待使用者決定」，非既定做法，故以 `AskUserQuestion` 徵詢方向，使用者對兩項皆選擇「建議」選項（校正編號／用更正後的做法修復）。

## 2. DEF-375：M08 編號衝突校正

### 2.1 問題

`API_Index.md` 舊表格（Sprint 15/16 時期）與 [TC_M08_Review.md](../03_testing/TC_M08_Review.md)（2026-04 建立）都使用裸編號 `API-M08-NNN`，但彼此代表完全不同的端點（例如 `API-M08-001`：前者是「商家回覆評價」、後者是「建立評價」）。[API_M08_Review.md](../02_architecture/api/API_M08_Review.md)（Sprint 263 新建）已改用不衝突的 `API-M08-R-NNN`／`API-M08-BR-NNN` 規避此問題，但兩份舊文件彼此的衝突本身從未被更正。

### 2.2 既有先例調查

複核 `TC_M07_Settlement.md` 發現其 API E2E 測試案例編號早就是 `API-M07-S-NNN`／`API-M07-T-NNN`（非裸編號），`API_M07_Settlement.md` 的「取代關係」註記說明這是刻意保留既有編號相容；M07 從未發生 M08 這種衝突，因此沒有可以直接套用的「新舊文件編號對齊」先例可循，需另訂方案。

### 2.3 做法

依「來源文件」分別加上中綴，三套編號自此互不重疊：
- `API_Index.md` 舊表格：`API-M08-NNN` → `API-M08-IDX-NNN`（IDX = 本文件 API_Index.md）
- `TC_M08_Review.md`：`API-M08-NNN` → `API-M08-TC-NNN`（TC = 本測試文件）
- `API_M08_Review.md`：維持既有 `API-M08-R-NNN`／`API-M08-BR-NNN` 不變

`TC_M08_Review.md` 內部兩處交叉引用（§「關鍵驗證點總結」的 `API-M08-104`／`API-M08-103`）同步更新為新編號，避免改了表格本體卻漏改內部引用。

## 3. DEF-376：測試排序脆弱性修復

### 3.1 問題與根因複核

`OrderCancellationCompensationIntegrationTest` 的 `simulateLegacyDeductionAtPayment`（測試固件，模擬改版前付款即扣帳的訂單）以原始 JDBC 寫入 `stock_movements`，`created_at` 用 SQL `NOW()`；而正常的 RESERVE 紀錄由生產程式碼經 JPA `@PrePersist`（`StockMovement.java`）寫入，用的是 Java `Instant.now()`——**兩筆紀錄用的是不同時鐘來源**（DB 事務時鐘 vs. JVM 時鐘）。斷言用的 `movementTypes()` 查詢依 `ORDER BY created_at, movement_type` 排序，全量套件負載較高時兩筆紀錄偶爾打平，次要鍵 `movement_type` 按字母序把 `"OUTBOUND"` 排到 `"RESERVE"` 之前，導致偶發斷言失敗。

### 3.2 否決原登記建議的修法

原登記建議「讓 `movementTypes()` 查詢加入穩定排序鍵（如 `id`）」。複核 `V1__Initial_Schema.sql` 確認 `stock_movements.id` 是 `UUID DEFAULT gen_random_uuid()`——**隨機值，不具時間序**。拿隨機 UUID 當平手排序鍵無法重現「RESERVE 先於 OUTBOUND」的語意，只是把「偶發打平失敗」換成「每次打平時各 50% 機率失敗」，不是真正的修復。

### 3.3 實際做法

修改 `simulateLegacyDeductionAtPayment` 的 INSERT 語句，`created_at` 欄位改用子查詢 `(SELECT MAX(created_at) + INTERVAL '1 millisecond' FROM stock_movements WHERE reference_id = ?)`，以該筆訂單既有紀錄（即 RESERVE）實際儲存的時間戳為基準往後位移，確保嚴格晚於、不依賴任何時鐘精度或巧合。1 毫秒的位移量足以消除平手，也遠小於後續 `cancelOrder()`／出貨等步驟之間的真實執行間隔，不會反過來搶先晚於它們的真實時間戳。

## 4. 實作內容

| 檔案 | 變更 |
|---|---|
| `backend/src/test/java/com/nextkey/ecommerce/integration/OrderCancellationCompensationIntegrationTest.java` | `simulateLegacyDeductionAtPayment` 的 `created_at` 改用子查詢明確位移，不再依賴 `NOW()`；新增 Javadoc 說明根因與修法 |
| `docs/02_architecture/API_Index.md` | M08 舊表格 7 個端點編號加上 `-IDX-` 中綴；警示文字新增 DEF-375 已解決說明 |
| `docs/03_testing/TC_M08_Review.md` | §3 API E2E 測試 12 個案例編號加上 `-TC-` 中綴；內部交叉引用同步更新；新增 DEF-375 已解決說明（不違反 Sprint 244 現況聲明——僅編號格式變更，內容本身未重寫） |
| `docs/02_architecture/api/API_M08_Review.md` | 頂部「編號說明」、§7 已知限制 DEF-375 列更新為已解決；版本 v1.1→v1.2 |
| `docs/01_requirements/E-Commerce_FRD_v1.0.md` | §6D.6 DEF-375 敘述更新為已解決；**順手修正** BR-M08-10 遺留的 DEF-374 陳舊敘述（見下方第 5 節）；版本 v1.11→v1.12，新增修訂歷史列 |
| `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | DEF-375／376 狀態欄標記已完成；版本歷史降級 v3.50 為歷史版本，新增 v3.51 |
| `docs/04_planning/RELEASE_TRACKER.md` | 新增 Sprint 268 列 |

## 5. 撰寫過程中新發現的落差（已於本輪一併修正，非新登記缺陷）

複核 FRD §6D.6 附近段落時發現 BR-M08-10 仍寫著「管理員無法經此端點刪除他人評價（**DEF-374**，未修復）」——但 DEF-374 其實已於 **Sprint 264** 修復（`ReviewService.deleteReview`／`BookingReviewService.deleteBookingReview` 皆補上管理員例外）。交叉核對 `SRD_Module_Technical_Design.md` 發現 SRD 當時已正確同步為「✅ 管理員例外（Sprint 264，DEF-374）」，**只有 FRD 這一處漏改**。已一併更正為已修復敘述，避免讀者誤信已解決的問題仍然存在。

## 6. 測試

依 CLAUDE.md「開發-編譯-測試循環」規則，完成測試檔案修改後立即執行：

1. `cd backend && mvn clean test-compile`：編譯成功，無錯誤。
2. `make test-db-up` 啟動真實 Postgres/Redis 測試資料庫。
3. `mvn failsafe:integration-test -Dit.test=OrderCancellationCompensationIntegrationTest`：**9/9 全部通過**（`target/failsafe-reports/...txt` 確認），與修復前單獨執行的基準一致。

**驗證的侷限（誠實揭露）**：本缺陷的性質是「全量套件負載較高時才偶發重現」，單獨執行此測試類別本來就一直是 9/9 通過（修復前後皆然），因此這次單獨執行**無法直接證明稀有競態已被消除**。但修法本身是結構性保證（新時間戳由既有紀錄的實際值位移計算，與任何時鐘精度或系統負載無關），而非依賴機率降低重現率，邏輯上足以排除根因。未額外執行全量 `mvn verify` 嘗試重現原始打平情境（成本高、且原本就是偶發，不保證能在合理時間內重現或不重現，不是有效的驗證方式）。

本輪只修改 1 支測試檔案（無生產程式碼變更），依 [[sprint-full-regression-policy]] 的既有判準（純補/修測試用快速驗證即可，改生產邏輯才需全量回歸）不需另外執行全量 `mvn clean verify`。

## 7. 範圍外與待使用者決定

- 本輪完成後，Sprint 261 文件稽核當時登記的 9 項缺口全數處理完畢；追蹤表中其餘項目皆為更早、使用者已個別決策的低優先級技術債或待確認事項（如 DEF-346／347／348／349／355／356 等基礎設施／上線前檢查項目），不在本輪範圍內，維持原狀。

## 8. Velocity 紀錄

本輪為 2 項缺口修復（1 項純文件編號校正＋ 1 項測試固件修復），非新功能開發，不納入傳統 Story Point 計算。

## 9. 下一步／Action Items

1. commit＋push，等待雲端 CI 確認全綠。
2. 回填 push 狀態與雲端 CI 結果。
