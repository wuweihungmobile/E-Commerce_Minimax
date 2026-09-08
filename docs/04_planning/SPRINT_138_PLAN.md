# Sprint 138 Plan — 知識庫（KnowledgeBaseService）併發競態技術債查證與修復

**Sprint**: Sprint 138
**日期**: 2026-09-08

---

## 1. 本輪範圍與方法論

Sprint 136 併發競態全掃找出 71 筆真實競態，僅修復 19 筆，其餘 52 筆（`DEF-114`~`DEF-165`）登記為技術債。Sprint 137 聚焦「租戶治理」主題查證並修復 10 筆（另查證推翻 3 筆死流程競態），其餘 42 筆（CMS/ERP/客服工單/房源日曆/知識庫等領域）仍維持登記狀態。本輪使用者拍板「繼續完成其餘 42 筆」，延續 Sprint 137 的做法：**不另開 Workflow**，由主控 session 直接逐筆重讀原始碼、獨立判斷、動手修復。

**範圍選定**：42 筆技術債涵蓋多個不相關領域。本輪聚焦「知識庫」主題——`KnowledgeBaseService` 中與 `KnowledgeArticle`/`KnowledgeCategory`/`ArticleVersion` 生命週期相關的 8 筆候選（`DEF-120`/`121`/`122`/`153`/`154`/`155`/`156`/`157`），理由：(a) 全部集中在同一個 service/同一組實體，根因高度重疊（3 筆是 `@DynamicUpdate` 全欄位覆寫、3 筆是已有 DB 約束兜底但未攔截例外、2 筆需要新的併發防護機制），可用同一輪查證批次處理；(b) 是 42 筆中最大的單一 service 集中群，優先處理可最大化「每輪查證成本」的攤提效益。

**查證結果**：逐一重讀原始碼、entity 定義、Flyway migration 後，**8 筆全數確認為真**（無 Sprint 137 §1 那種「死流程」推翻案例）：

| ID | 方法 | 根因 | 查證結論 |
|----|------|------|---------|
| DEF-120 | createVersionSnapshot | `article_versions` 無 `(article_id, version_number)` 唯一約束 | 真實：兩個併發請求可各自算出相同版本號並成功 INSERT，之後 `findByArticleIdAndVersionNumber` 查到重複列會拋 `IncorrectResultSizeDataAccessException`（持續性 500，非機率性偶發，會一直存在到手動清資料） |
| DEF-121 | restoreVersion | 內部呼叫 createVersionSnapshot + `KnowledgeArticle` 無 `@DynamicUpdate` | 真實：與 DEF-120/122 同根因 |
| DEF-122 | updateArticle | `KnowledgeArticle` 無 `@DynamicUpdate` | 真實：PATCH 語意的部分更新會被 Hibernate 全欄位 UPDATE 悄悄覆寫併發的另一筆部分更新 |
| DEF-153 | createArticle | TOCTOU + DB 已有 `uk_knowledge_articles_slug (tenant_id, slug)` | 真實但資料完整性有 DB 兜底：唯一約束擋下重複資料，但敗方請求收到未攔截的 `DataIntegrityViolationException`（原始 500） |
| DEF-154 | createCategory | TOCTOU + DB 已有 `uk_knowledge_categories_tenant_slug` | 同 DEF-153 |
| DEF-155 | deleteCategory | TOCTOU + FK RESTRICT (`fk_knowledge_articles_category`) | 真實但資料完整性有 DB 兜底：FK 擋下孤兒文章，但敗方請求收到未攔截的原始 500 |
| DEF-156 | schedulePublish | `KnowledgeArticle` 無 `@DynamicUpdate` | 真實：與 DEF-122 同根因 |
| DEF-157 | updateCategory | `KnowledgeCategory` 無 `@DynamicUpdate` | 真實：與 DEF-122 同型態，換一個實體 |

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

比照 Sprint 136/137 慣例：每完成一個邏輯修復單元，立即 `mvn compile` + 執行 `KnowledgeBaseServiceTest`，確認通過才進入下一項；全部完成後才跑 `mvn -o verify` 全量回歸 + `checkstyle:check` + `make validate-schema`/`validate-schema-doc`。

---

## 3. 修復摘要：版本控制（`createVersionSnapshot`/`restoreVersion`，DEF-120/121）

**問題**：`createVersionSnapshot` 是「讀目前最大版本號 → +1 → INSERT」的複合操作，`article_versions` 表沒有 `(article_id, version_number)` 唯一約束兜底，兩個併發呼叫（含 `restoreVersion` 內部呼叫的隱性併發）可能算出相同的 `newVersion` 並各自成功 INSERT，造成同一篇文章底下出現重複版本號；`getArticleVersion`/`restoreVersion` 依版本號查詢單一版本時會因結果集不唯一拋出未攔截的 `IncorrectResultSizeDataAccessException`，是持續性功能性 500（非機率性偶發，直到有人手動清理重複列）。

**修法**：新增 `KnowledgeArticleRepository.findByIdAndTenantIdForUpdate`（`SELECT ... FOR UPDATE` 悲觀鎖，比照既有 `UserRepository.findByIdForUpdate` 模式），`createVersionSnapshot` 改用此方法載入文章，將整個「讀最大版本號→+1→INSERT」序列化在同一把列鎖之下。`restoreVersion` 因內部呼叫 `createVersionSnapshot`（同一 persistence context，JPA 身分映射確保是同一個 Java 物件），一併受惠：從 `createVersionSnapshot` 呼叫點起，該文章列被鎖定直到交易 commit，避免還原流程中途被其他併發還原/快照操作打斷。

未新增資料庫遷移——悲觀鎖足以完全消除此競態，不需要額外的唯一約束（且加了唯一約束後仍需搭配重試邏輯處理衝突，複雜度更高，Rule 2 簡潔優先下選擇悲觀鎖）。

**測試**：新增 `KnowledgeBaseServiceTest.createVersionSnapshot_usesLockedLookup` 守衛測試——斷言 `findByIdAndTenantIdForUpdate` 被呼叫、`findByIdAndTenantId` 不再被呼叫，一旦有人改回不上鎖的查詢會立刻失敗。既有 `createVersionSnapshot_firstVersion_isOne`/`createVersionSnapshot_incrementsFromExisting`/`restoreVersion_restoresContentAndSnapshotsCurrent` 三個測試的 mock 同步改為 stub 新方法。

---

## 4. 修復摘要：`@DynamicUpdate` 全欄位覆寫類（DEF-121/122/156/157）

**根因**：`KnowledgeArticle`/`KnowledgeCategory` 皆無 `@Version` 也無 `@DynamicUpdate`；`updateArticle`/`schedulePublish`/`updateCategory` 都是「DTO 部分欄位選填 → null 檢查後逐一 setter → 單一 `save()`」的 PATCH 語意，但 Hibernate 對沒有 `@DynamicUpdate` 的 managed entity 預設用「整列所有欄位」組 UPDATE SQL（值取自該交易載入時的完整快照）。兩個併發的部分更新各自只碰不同欄位時，後 commit 者會用自己交易一開始讀到的舊快照，把先寫入者已提交的欄位悄悄覆蓋回去。

**修法**：`KnowledgeArticle`、`KnowledgeCategory` 兩個實體加上 `@DynamicUpdate`（比照 Sprint 136 §6 對 `Listing`/`Room`/`Product`/`Booking`/`TenantMember` 的既有修法），讓 UPDATE 只包含本次交易內實際被 setter 改動過的欄位。一次修復同時解決 `updateArticle`（DEF-122）、`schedulePublish`（DEF-156）、`updateCategory`（DEF-157），並讓 `restoreVersion`（DEF-121）不再有「還原時整包覆寫其他併發已提交欄位」的風險。

`make validate-schema` 確認兩個實體加註解後 entity↔migration 仍完全對齊（`@DynamicUpdate` 純屬 Hibernate SQL 產生策略，不影響 schema）。

---

## 5. 修復摘要：TOCTOU 撞 DB 約束但未攔截例外（DEF-153/154/155）

**根因**：三個方法都是「檢查（`exists`/查詢）→ 建立/刪除」的 check-then-act，DB 端已有唯一約束（`uk_knowledge_articles_slug`/`uk_knowledge_categories_tenant_slug`）或外鍵約束（`fk_knowledge_articles_category ... ON DELETE RESTRICT`）兜底，資料本身不會損毀或產生孤兒列，但敗方請求會收到未被 `GlobalExceptionHandler` 特別處理的原始 `DataIntegrityViolationException`（500），而非乾淨的業務錯誤。

**修法**：比照 Sprint 136 §3（V79）/Sprint 137 §7（V80）的既有慣例——

- `createArticle`（DEF-153）：`articleRepository.save` 改為 `saveAndFlush`，`catch (DataIntegrityViolationException)` 轉譯為既有的 `E_3001`「Article slug already exists for this tenant」。
- `createCategory`（DEF-154）：`categoryRepository.save` 改為 `saveAndFlush`，`catch` 轉譯為既有的 `E_3001`「Category slug already exists for this tenant」。
- `deleteCategory`（DEF-155）：`categoryRepository.delete` 後補 `flush()`，`catch` 轉譯為既有的 `E_3001`「Cannot delete category with articles」（與「刪除前已檢查有文章」的既有錯誤訊息一致）。

三者皆未新增資料庫遷移——既有約束已足夠保護資料完整性，本輪只補上「例外轉譯」這一層。

**測試**：新增 `createArticle_concurrentDuplicateSlug_translatesToE3001`、`createCategory_concurrentDuplicateSlug_translatesToE3001`、`deleteCategory_concurrentArticleCreated_translatesToE3001` 三個測試，以 mock 拋出 `DataIntegrityViolationException` 模擬併發撞約束的情境，斷言轉譯為 `E_3001` 而非讓例外原樣拋出。

---

## 6. 驗證

- **編譯**：每個修復單元改動後立即 `mvn compile` 確認真實編譯。
- **checkstyle**：`mvn checkstyle:check` **0 violations**。
- **單元測試**：`KnowledgeBaseServiceTest` 由 27 增至 **31**（+4：`createVersionSnapshot_usesLockedLookup`、`createArticle_concurrentDuplicateSlug_translatesToE3001`、`createCategory_concurrentDuplicateSlug_translatesToE3001`、`deleteCategory_concurrentArticleCreated_translatesToE3001`），全數通過；既有 4 個測試（`createVersionSnapshot_firstVersion_isOne`/`_incrementsFromExisting`、`restoreVersion_restoresContentAndSnapshotsCurrent`、`createArticle_success`/`_savesAndReturnsTags`、`createCategory_success`）同步更新 mock 以符合新的方法簽章（`findByIdAndTenantIdForUpdate`/`saveAndFlush`）。
- **Schema 守門**：`make validate-schema`（entity↔migration）、`make validate-schema-doc`（migration↔文件）皆通過，本輪未新增任何 Flyway 遷移，兩個實體的 `@DynamicUpdate` 註解未造成漂移。
- **全量回歸**：`make test-db-up` 後 `mvn -o verify`（含 failsafe 整合測試）**BUILD SUCCESS**：單元 **1171**（相對 Sprint 137 的 1167，+4，即本輪新增的守衛測試）、整合 **477**（與 Sprint 137 持平，本輪未新增整合測試類別），0 failures/errors；checkstyle（main+test）**0 violations**；總耗時 6:54 min。

---

## 7. 刻意不做的事（避免範圍蔓延）

- 不修復其餘 34 筆技術債（CMS/ERP/客服工單/房源日曆/租戶功能開關/聊天室/媒體等領域）——本輪聚焦知識庫主題，其餘留待後續 Sprint 依主題分批查證（Sprint 139 起）。
- 不為 `article_versions` 額外新增 `(article_id, version_number)` 唯一約束——悲觀鎖已完全消除此競態，額外約束需搭配重試邏輯，複雜度增加但無額外保護效益（Rule 2 簡潔優先）。
- 不處理 `updateArticle`/`updateCategory` 既有的 slug TOCTOU（併發把 slug 改成別人正在用的值）——這不是本輪 8 筆候選之一，且該分支的 `save()` 呼叫同時承載其他欄位更新，若貿然加 `saveAndFlush`/`catch` 會改變所有更新路徑的例外語意，非本輪範圍。
