# Sprint 143 Plan — 治理雜項（功能開關 + 聊天 + 媒體分類）併發競態技術債查證與修復

**Sprint**: Sprint 143
**日期**: 2026-09-08

---

## 1. 本輪範圍與方法論

延續 Sprint 137~142 做法（不另開 Workflow，主控 session 直接逐筆重讀原始碼、獨立判斷、動手修復）。Sprint 142 完成房源日曆主題後，剩餘 14 筆技術債，領域分散（功能開關、聊天、媒體、訂單、付款、客服工單、租戶）。本輪選擇「治理雜項」5 筆——`AdminService`（`DEF-116`/`148`）+ `TenantService`（`DEF-143`）三者共用同一個 `TenantFeatureToggle` 實體，加上規模小、可獨立驗證的 `ChatService`（`DEF-118`）與 `MediaService`（`DEF-160`），湊成一輪合理大小的批次。

**查證結果**：逐一重讀原始碼、entity 定義、既有 DB 約束後，**5 筆全數確認為真**，其中 1 筆查證中發現一個**與併發無關、更嚴重的既有 bug**：

| ID | 方法 | 查證結論 |
|----|------|---------|
| DEF-116 | AdminService.updateTenantFeatureToggle | 真實：`TenantFeatureToggle` 無 `@DynamicUpdate`，與 DEF-143/148 共用同一實體的全欄位覆寫問題 |
| DEF-143 | TenantService.updateFeatureToggle | 真實，但🔴 **原始「lost update」判定只是次要問題**：建立新 toggle 時的 `.tenantId(tenantId)` 只設了 insertable=false 的唯讀影子欄位，未設 `.tenant(tenant)` 關聯物件，任何一次首次建立都會對 NOT NULL 的 `tenant_id` 插入 NULL 而 100% 失敗，是必現 bug、與併發完全無關 |
| DEF-148 | AdminService.setFeatureToggle | 真實：首次建立的 TOCTOU（已有 DB 唯一約束兜底但未攔截例外）+ 更新路徑的全欄位覆寫（`config` 欄位風險） |
| DEF-118 | ChatService.createConversation | 真實：`conversations` 表對 `(initiator_id, recipient_id)` 無唯一約束，同一對使用者可產生兩筆同時 active 的對話 |
| DEF-160 | MediaService.createCategory | 真實：`media_categories` 對 `(tenant_id, name, parent_id)` 無唯一約束，兩個併發請求可各自建立同名分類 |

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

比照 Sprint 136~142 慣例：每完成一個邏輯修復單元，立即 `mvn compile` + 執行相關測試類別，確認通過才進入下一項；全部完成後才跑 `mvn -o verify` 全量回歸 + `checkstyle:check@checkstyle-main`/`@checkstyle-test` + `make validate-schema`/`validate-schema-doc`；新增的兩個 migration（V81/V82）與 `TenantService.updateFeatureToggle` 的 bug 修復額外以真實 DB 整合測試驗證（`M10Chat*`、`M18MediaIntegrationTest`）。

---

## 3. 修復摘要：功能開關（`TenantFeatureToggle`，DEF-116/143/148）

**根因**：`TenantFeatureToggle` 無 `@Version` 也無 `@DynamicUpdate`；`AdminService.updateTenantFeatureToggle`、`AdminService.setFeatureToggle`、`TenantService.updateFeatureToggle` 三個方法共用同一張表，各自「讀→改欄位→`save()`」。`setFeatureToggle` 額外會寫 `config` 欄位，若與另兩個不碰 `config` 的方法併發交錯，`config` 可能被悄悄覆寫回舊值。`setFeatureToggle`/`updateFeatureToggle` 兩者都有「首次建立」分支（`orElse`/`toggle == null`），與 DB 既有的 `UNIQUE(tenant_id, feature_key)` 約束（`V1__Initial_Schema.sql`）之間是 TOCTOU；`updateTenantFeatureToggle` 用 `orElseThrow`，沒有建立路徑。

**修法**：
- `TenantFeatureToggle` 加 `@DynamicUpdate`（一次解決三個方法共用的全欄位覆寫問題）。
- `AdminService.setFeatureToggle`：`save()` 改 `saveAndFlush()` + 捕捉 `DataIntegrityViolationException` 轉譯為既有的 `E_9000`（僅在原本就是「首次建立」分支時才這樣轉譯，更新路徑撞到理論上不該發生的約束衝突則原樣拋出，不誤導）。
- `TenantService.updateFeatureToggle`：同樣的 `saveAndFlush` + 捕捉套用。

**額外發現（範圍外但直接相關）：`TenantService.updateFeatureToggle` 的 `.tenant(tenant)` 遺漏 bug**——建立新 `TenantFeatureToggle` 時原始碼寫的是 `.tenantId(tenantId)`，但 `tenantId` 是 `@Column(insertable = false, updatable = false)` 的唯讀影子欄位（即 `erp-tenant-test-seeding-gotcha` 既有教訓的同型陷阱），真正決定 INSERT 的 `tenant_id` 值的是 `.tenant(...)` 這個 `@ManyToOne` 關聯物件；未設定時 Hibernate 會插入 NULL，撞上 `tenant_feature_toggles.tenant_id NOT NULL` 約束，**100% 必現失敗，與併發無關**。這條「建立新 toggle」的分支目前之所以還沒在生產環境炸開，是因為 `TenantService.initializeFeatureToggles`（租戶核准時呼叫）已預先建立所有目前已知的 10 個 feature key，讓這個分支在現有資料下不可達——但只要未來 `FEATURE_DEFINITIONS` 新增一個鍵而忘記同步更新 `initializeFeatureToggles`，或有租戶繞過該初始化流程，這條路徑就會被觸發。已一併修復為 `.tenant(tenant)`（比照同檔案中原本就寫對的 `AdminService.setFeatureToggle`）。由於本輪要修的併發防護（`saveAndFlush`+捕捉）恰好會把這個 NOT NULL 違反也吞成 `DataIntegrityViolationException`，若不先修好這個 bug，併發防護的錯誤訊息會誤導成「請重試」，但重試永遠不會成功——兩者必須一起修，不能分開處理。

**因 NPath 複雜度超標的重構**：加入上述判斷後 `updateFeatureToggle` 的 checkstyle `NPathComplexity` 從可接受值暴增到 388（上限 200），抽出 `buildNewFeatureToggle`/`saveFeatureToggleOrTranslateConflict` 兩個私有方法降低複雜度，行為不變。

**誠實揭露：`TenantService.updateFeatureToggle` 的修復未新增真實 DB 整合測試**——這是本輪範圍內信心最低的一項修復：全代碼庫沒有任何既有的真實 DB（非 `@MockBean`）整合測試會呼叫 `TenantService.updateFeatureToggle`，建立一份全新的整合測試需要完整的 Tenant/User/TenantMember 資料 fixture，超出本輪合理投入；本輪僅以 Mockito 單元測試驗證程式邏輯，並以「與 `AdminService.setFeatureToggle` 已經正確運作的 `.tenant(tenant)` 寫法完全一致」的結構性比對建立信心。此為潛伏性/防禦性修復（目前資料狀態下不可達），非本輪立即高風險項目，但比 Mockito 單元測試更弱的驗證強度應誠實記錄。

---

## 4. 修復摘要：聊天發起（`ChatService.createConversation`，DEF-118）

**問題**：「檢查是否已有對話」與「建立新對話」之間沒有原子保護，`conversations` 表對 `(initiator_id, recipient_id)` 沒有任何唯一約束，兩個併發「發起聊天」請求都可能通過檢查各自建立一筆 active 對話。

**修法**：新增 `V81__Conversations_Active_Pair_Unique.sql`（`(initiator_id, recipient_id) WHERE is_active = true` 部分唯一索引——僅限制同時只能有一筆 active，允許歷史上多筆已結束的對話），`createConversation` 改用 `saveAndFlush` 捕捉違反約束；搶輸時比照上面「已有對話則直接回傳」的既有語意，重新查詢後回傳該筆既有對話（不送出本次的初始訊息，不拋錯），與非併發情境下「已有對話」分支的行為完全一致。

**驗證**：以既有的三個真實 DB 整合測試（`M10ChatIntegrationTest` 8 案例、`M10ChatUnreadCountConcurrencyIntegrationTest` 1 案例、`M10ChatTenantIsolationIntegrationTest` 3 案例，共 12 案例，皆使用真實 Postgres 而非 `@MockBean` repository）驗證 V81 migration 與程式碼變更未破壞既有行為。

---

## 5. 修復摘要：媒體分類（`MediaService.createCategory`，DEF-160）

**問題**：分類名稱唯一性檢查（`existsByTenantIdAndNameAndParentIsNull`/`existsByTenantIdAndNameAndParentId`）與 `save()` 之間是 TOCTOU，`media_categories` 對 `(tenant_id, name, parent_id)` 沒有唯一約束，兩個併發請求可各自建立一筆同名分類。

**修法**：新增 `V82__Media_Categories_Name_Unique_Per_Parent.sql`，依既有兩種檢查分支分別建立兩個部分唯一索引（根層級 `WHERE parent_id IS NULL`、子層級 `WHERE parent_id IS NOT NULL`——PostgreSQL 視多個 NULL 互不相等，單一 `UNIQUE(tenant_id, name, parent_id)` 約束無法涵蓋根層級同名的情況）。`createCategory` 改用 `saveAndFlush` 捕捉違反約束，依原始請求是否有 `parentId` 轉譯為對應的既有 `E_3001` 訊息。

**驗證**：以既有的 `M18MediaIntegrationTest`（15 案例，真實 `mediaCategoryRepository`/`tenantRepository`）確認 V82 migration 套用無誤（該測試的 `MediaService` 本身被 `IntegrationTestConfiguration` 的 `@Primary` mock bean 取代，故只驗證 migration 不影響既有流程，不直接驗證本輪程式碼邏輯，邏輯正確性由新增的 2 個 Mockito 單元測試涵蓋）。

---

## 6. 驗證

- **編譯**：每個修復單元改動後立即 `mvn compile` 確認真實編譯。
- **checkstyle**：`mvn checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test` 首次因 `TenantService.updateFeatureToggle` NPath 複雜度超標（388 > 200）失敗，抽出兩個私有方法後 **0 violations**。
- **單元測試**：新增 3 個測試（`ChatServiceTenantResolutionTest` +1：`createConversation_concurrentClaimLost_returnsRaceWinner`；`MediaServiceTest`（`core.media` 套件）+2：根/子層級併發撞約束各一）；`AdminServiceTest`/`TenantServiceTest` 既有測試的 mock 同步更新為 `saveAndFlush`/補上 `tenantRepository.findById` stub。
- **整合測試（真實 DB）**：`M10ChatIntegrationTest`（8）、`M10ChatUnreadCountConcurrencyIntegrationTest`（1）、`M10ChatTenantIsolationIntegrationTest`（3）、`M18MediaIntegrationTest`（15）全數通過，確認 V81/V82 兩個新 migration 套用無誤且不影響既有業務邏輯。
- **Schema 守門**：`make validate-schema`（entity↔migration）通過；`make validate-schema-doc`（migration↔文件）首次執行又遇到已知的 `postgres:18-alpine` pg_isready 啟動競態（連 V1 都失敗，[[validate-schema-doc-pg-isready-race]]），重跑一次通過——V81/V82 只新增索引未改欄位，`SRD_Database_Schema.md` 無需同步更新。
- **全量回歸**：`mvn -o verify`（含 failsafe 整合測試）**BUILD SUCCESS**：單元 **1186**（相對 Sprint 142 的 1183，+3）、整合 **477**（與 Sprint 142 持平，本輪未新增整合測試方法，僅驗證既有的），0 failures/errors；checkstyle（main+test）**0 violations**；總耗時 7:38 min。

---

## 7. 刻意不做的事（避免範圍蔓延）

- 不修復其餘 9 筆技術債（訂單/付款狀態記錄/客服工單等領域）——本輪聚焦治理雜項主題，其餘留待 Sprint 144（最後一批）。
- 不為 `TenantService.updateFeatureToggle` 的 `.tenant(tenant)` bug 修復新增專屬真實 DB 整合測試——見第 3 節誠實揭露，已記錄為信心較低的修復項目，供未來若相關程式碼再次變動時優先補測試。
- 不處理 `FEATURE_DEFINITIONS`/`initializeFeatureToggles` 兩份清單需手動保持同步的架構性風險——這是本輪發現的 bug 之所以能潛伏至今的根本原因，但重新設計為單一事實來源（例如從 `FEATURE_DEFINITIONS` 動態產生初始化清單）是較大範圍的重構，非本輪範圍，留待未來排程。
