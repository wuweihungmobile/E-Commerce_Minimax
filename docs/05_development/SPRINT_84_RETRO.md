# Sprint 84 Retrospective / Sprint 84 回顧會議

> **Sprint 編號**: Sprint 84
> **期間**: 2026-07-08
> **回顧日期**: 2026-07-08
> **參與者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code

---

## 1. Sprint 概覽

| 項目 | 數值 |
|------|------|
| 承諾 SP | 3 SP（DEF-041 原估） |
| 完成 SP | 5 SP（含中途發現並一併修復的根因問題，使用者已同意擴大範圍） |
| 主軸 | 修復 Sprint 83 發現的 DEF-041（`RoomService`/`ProductService` 寫入層租戶擁有權缺口）|

---

## 2. 做得好的（What went well）

- **依既有慣例做技術選型，而非各自發明一套**：專案內同時存在「模式 A」（Sprint 80/83：`checkListingTenantOwnership`，僅 SUPER_ADMIN 跨租戶）與「模式 B」（Sprint 74 `CmsService`：`ROLE_ADMIN` 也可跨租戶）兩種租戶擁有權檢查慣例。依 CLAUDE.md Rule 7（衝突時選較新、測試較多者，公開衝突而非混用）明確選擇模式 A，並在規劃文件中記錄理由，避免同一系統內堆疊第三種不一致的規則。
- **實作驗證階段意外挖出比原任務更嚴重的根因問題，主動停下請示而非默默擴大範圍或忽略**：撰寫整合測試驗證 DEF-041 修復時，`ProductControllerE2ETest` 出現非預期的 500 錯誤。沒有直接猜測修復，而是實際查詢測試資料庫確認：`RoomService`/`ProductService` 的 `createRoom`/`createProduct` 等四個建立方法，長期以來只設定 `Listing` 的唯讀影子欄位 `.tenantId(...)`，從未設定真正被 JPA 用來寫入的 `.tenant(...)`/`.owner(...)` 關聯物件，導致房源/商品的 `tenant_id`/`owner_id` 從未真正落地資料庫。這代表 DEF-041 的租戶擁有權檢查若不修此根因，等同建立在一個永遠是空值的欄位上，形同虛設；且連帶使 `getRooms`/`getProducts` 預設分支的 `tenantId` 過濾查詢理論上永遠查不到已建立的房源/商品（既有測試僅斷言「回傳不是 null」，未斷言「非空」，才沒被抓到）。明確停下向使用者說明範圍/風險後，由使用者決策「一併修復」才繼續動手，不自行決定擴大範圍。
- **修復根因後，主動追殺因此浮現的既有測試衛生缺口，而非只求自己新增的測試通過**：根因修復後，`ProductControllerE2ETest`/`CartControllerE2ETest`/`M01ProductIntegrationTest`/`M02RoomIntegrationTest` 四個既有測試檔案陸續因「以前 FK 從未真正生效」而露出教學：`tearDown` 刪除使用者前未清理其擁有的 listings/products（違反外鍵約束）、`@WithMockUser` 的預設 principal 型別不符新端點所需的 `@AuthenticationPrincipal UserPrincipal`（回傳 null 導致 NPE）。每次都完整追查根本原因後修復，並比照既有 `M07SettlementIntegrationTest.authAs` 模式解決，而非用 try-catch 掩蓋或跳過測試。
- **一次只做一個檔案的編譯-測試循環，及早抓到 stale build 問題**：多次遇到 `mvn compile` 回報「Nothing to compile - all classes are up to date」的假訊號（明明檔案已修改），每次都用 `mvn clean compile` 交叉驗證確保是真的編譯結果，而非信任可能過期的增量編譯快取。

---

## 3. 待改善的（What to improve）

- **`mvn verify -Pintegration-test` 全量回歸單次耗時偏長（曾觀察到 1 小時 25 分鐘）**：這次 Sprint 過程中全量回歸命令本身的執行時間明顯比先前 Sprint 記錄的數字（10-20 分鐘量級）長很多，原因未深入排查（可能與本機當下資源狀態、Docker/act 背景負載有關，需留意是否為單次偶發或已成為新常態）。建議下次全量回歸耗時異常時，記錄環境狀態（Docker Desktop 資源、是否有其他背景任務）供後續比對。
- **`Bash` 工具前景執行长時間指令曾一次因預設 2 分鐘逾時被誤判為中斷**：Session 前段執行 `git push`（實際需 8-12 分鐘的 pre-push 輕量守門）時，未使用 `run_in_background: true` 而在前景執行，被工具預設逾時強制中斷，一度讓使用者誤以為任務又卡住。已在同一 Session 中修正並記錄為記憶（`nohup echo EXIT_CODE` 手動組裝完成信號的模式本身有結構性缺陷，應直接對目標指令使用 `run_in_background: true`，不要自製完成信號）。

---

## 4. 改善行動（Action Items）

| ID | Action Item | 內容 | 負責人 | 優先級 | Sprint |
|----|------|------|--------|--------|--------|
| （技術債，延續自 Sprint 71-84）| `RELEASE_TRACKER.md` 補齊 Sprint 74+ 列 | 已連續多個 Sprint 記錄但未排入排程 | PM Victoria | 🟢 低 | 待排入（文件維護） |
| （待決策，延續自 Sprint 76-84）| `DEF-037`：`ShippingTemplateService.calculateFee` 跨租戶查詢 | 使用者已決策擱置 | PO Victoria（已決策） | 🟢 低 | 已結案（擱置） |

---

## 5. Sprint 83 → Sprint 84 Action Items 追蹤結果

| Action Item | 內容 | Sprint 84 達成狀態 |
|------------|------|---------------------|
| `DEF-041`（`RoomService`/`ProductService` 跨租戶寫入漏洞）| Sprint 83 新發現，建議優先排入 | ✅ **已完成**：修復擁有權檢查 + 意外發現並一併修復更根本的 tenant_id/owner_id 未落地問題 |

---

## 6. Velocity 紀錄

| Sprint | SP |
|--------|-----|
| S80 | 13 |
| S81 | 6 |
| S82 | 3 |
| S83 | 3 |
| **S84** | **5**（DEF-041 + 根因修復，原估 3 SP，因中途發現根因問題經使用者同意擴大範圍）|

---

## 7. 下一步

> **檢查點**：Sprint 84 已完成，DEF-041 及其根因問題皆已解決，目前無高優先級（🔴）延後項目。**下一輪規劃建議（依優先級）**：
> 1. PRD P0：M16 ERP 採購審批金額上限機制。
> 2. PRD 規格要求：M07 結算系統跨週期退款調整單（`adjustment_statement`）。
> 3. PRD Phase 2-B 項目：收貨地址簿、M18 客服工單子系統，視容量排入。
> 4. 技術債：`RELEASE_TRACKER.md` 補齊 Sprint 74+ 列。

---

**文件版本**: v1.0
**建立日期**: 2026-07-08
**建立者**: PM Victoria + SA Amanda + SD Marcus + Dev David + QA Quincy + Claude Code
