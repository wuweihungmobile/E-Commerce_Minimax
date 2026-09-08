# Sprint 139 Plan — CMS（CmsService）併發競態技術債查證與修復

**Sprint**: Sprint 139
**日期**: 2026-09-08

---

## 1. 本輪範圍與方法論

延續 Sprint 137/138 做法（不另開 Workflow，主控 session 直接逐筆重讀原始碼、獨立判斷、動手修復）。Sprint 138 完成知識庫主題後，剩餘 34 筆技術債。本輪聚焦「CMS」主題——`CmsService` 中與 `ContentPage`/`Banner` 生命週期相關的 5 筆候選（`DEF-119`/`149`/`150`/`151`/`152`），理由：全部集中在同一個 service、只有兩個實體，根因高度重疊（3 筆是 `@DynamicUpdate` 全欄位覆寫、2 筆是已有 DB 唯一約束兜底但未攔截例外），與 Sprint 138 knowledge-base 群組同型態，可延續同一套已驗證有效的修法快速處理。

**查證結果**：逐一重讀原始碼、entity 定義、Flyway migration 後，**5 筆全數確認為真**：

| ID | 方法 | 根因 | 查證結論 |
|----|------|------|---------|
| DEF-119 | publishPage | `ContentPage` 無 `@DynamicUpdate` | 真實：`publishPage` 只碰 `status`/`publishedAt`，但全欄位 UPDATE 會覆寫併發的 `updatePage` 已提交的其他欄位 |
| DEF-152 | updatePage | `ContentPage` 無 `@DynamicUpdate` | 真實：與 DEF-119 同根因 |
| DEF-149 | createPage | TOCTOU + DB 已有 `cms_pages_slug_key`（V49，全域 `UNIQUE(slug)`） | 真實但資料完整性有 DB 兜底：唯一約束擋下重複 slug，但敗方請求收到未攔截的 `DataIntegrityViolationException`（原始 500） |
| DEF-150 | publishBanner | `Banner` 無 `@DynamicUpdate` | 真實：`publishBanner` 只碰 `status`，全欄位 UPDATE 會覆寫併發的 `updateBanner` 已提交的其他欄位 |
| DEF-151 | updateBanner | `Banner` 無 `@DynamicUpdate` | 真實：與 DEF-150 同根因 |

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

比照 Sprint 136~138 慣例：每完成一個邏輯修復單元，立即 `mvn compile` + 執行 `CmsServiceTest`，確認通過才進入下一項；全部完成後才跑 `mvn -o verify` 全量回歸 + `checkstyle:check` + `make validate-schema`/`validate-schema-doc`。

---

## 3. 修復摘要：`@DynamicUpdate` 全欄位覆寫類（DEF-119/150/151/152）

**根因**：`ContentPage`/`Banner` 皆無 `@Version` 也無 `@DynamicUpdate`；`updatePage`/`publishPage`/`updateBanner`/`publishBanner` 都是「讀取整包實體 → 只碰部分欄位 → 單一 `save()`」的模式，但 Hibernate 對沒有 `@DynamicUpdate` 的 managed entity 預設用「整列所有欄位」組 UPDATE SQL（值取自該交易載入時的完整快照）。`publishPage`/`publishBanner` 只顯式碰 1-2 個欄位（`status`/`publishedAt`），與 `updatePage`/`updateBanner`（碰其餘欄位）併發時，後 commit 者會用自己交易一開始讀到的舊快照，把先寫入者已提交的欄位悄悄覆蓋回去——這正是既有的
`checkCmsTenantOwnership` 註解（Sprint 74 DEF-032）已指出的同一批方法，當時只補了租戶擁有權檢查，未處理併發覆寫。

**修法**：`ContentPage`、`Banner` 兩個實體加上 `@DynamicUpdate`（比照 Sprint 136 §6 / Sprint 138 §4 的既有修法），一次修復同時解決 4 筆候選（`publishPage`+`updatePage` 共用 `ContentPage`；`publishBanner`+`updateBanner` 共用 `Banner`）。

`make validate-schema` 確認兩個實體加註解後 entity↔migration 仍完全對齊。

---

## 4. 修復摘要：TOCTOU 撞 DB 約束但未攔截例外（DEF-149）

**根因**：`createPage` 是「`findBySlug` 檢查 → 建立」的 check-then-act，DB 端已有全域唯一約束 `cms_pages_slug_key`（`V49__...sql`）兜底，資料本身不會出現兩筆同 slug 的頁面，但敗方請求會收到未被 `GlobalExceptionHandler` 特別處理的原始 `DataIntegrityViolationException`（500），而非乾淨的既有業務錯誤 `E_9005`「Slug already exists」。

**修法**：比照 Sprint 138 §5 的既有慣例——`contentPageRepository.save` 改為 `saveAndFlush`，`catch (DataIntegrityViolationException)` 轉譯為既有的 `E_9005`。未新增資料庫遷移——既有約束已足夠保護資料完整性。

**測試**：新增 `CmsServiceTest.createPage_concurrentDuplicateSlug_translatesToE9005`，以 mock 拋出 `DataIntegrityViolationException` 模擬併發撞約束的情境，斷言轉譯為 `E_9005` 而非讓例外原樣拋出。

---

## 5. 驗證

- **編譯**：每個修復單元改動後立即 `mvn compile` 確認真實編譯。
- **checkstyle**：`mvn checkstyle:check` **0 violations**。
- **單元測試**：`CmsServiceTest` 由 32 增至 **33**（+1：`createPage_concurrentDuplicateSlug_translatesToE9005`），全數通過；既有 `createPage_success` 測試同步更新 mock（`save`→`saveAndFlush`）。
- **Schema 守門**：`make validate-schema`（entity↔migration）通過；`make validate-schema-doc`（migration↔文件）**首次執行遇到已知的 `postgres:18-alpine` 啟動競態**（見 [[validate-schema-doc-pg-isready-race]]：`pg_isready` 在 initdb 內部重啟窗口誤判就緒，導致連 `V1__Initial_Schema.sql` 都套用失敗，重跑一次仍同樣失敗）——本輪額外手動驗證排除環境因素：另起一個 debug 容器、`pg_isready` 通過後多等 10 秒，80 個遷移全數套用成功，再以 `SCHEMA_DOC_PG_CONTAINER=<debug容器> python3 scripts/lib/check_schema_doc.py` 直接執行文件比對邏輯，結果 **一致，無漂移**，確認是純環境時序問題、非本輪變更造成的迴歸（本輪未新增任何 Flyway 遷移，`@DynamicUpdate` 註解不影響 schema）。
- **全量回歸**：`mvn -o verify`（含 failsafe 整合測試）**BUILD SUCCESS**：單元 **1172**（相對 Sprint 138 的 1171，+1，即本輪新增的守衛測試）、整合 **477**（與 Sprint 138 持平），0 failures/errors；checkstyle（main+test）**0 violations**。

---

## 6. 刻意不做的事（避免範圍蔓延）

- 不修復其餘 29 筆技術債（ERP/客服工單/房源日曆/租戶功能開關/聊天室/媒體等領域）——本輪聚焦 CMS 主題，其餘留待後續 Sprint 依主題分批查證（Sprint 140 起）。
- 不修復 `scripts/validate-schema-doc.sh` 的 `pg_isready` 競態——與本輪程式碼變更無關的既有環境缺陷，已有既存教訓記錄（[[validate-schema-doc-pg-isready-race]]），非本輪範圍。
- 不處理 `updatePage`/`updateBanner` 既有的 slug/內容欄位 TOCTOU 以外的其他潛在問題——本輪 5 筆候選皆已對應處理，未發現額外候選。
