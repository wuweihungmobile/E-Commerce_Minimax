# Sprint 184 Plan — 狀態偽造模式全庫複查 + 媒體上傳驗證一致性（DEF-254）

**Sprint**: Sprint 184
**日期**: 2026-09-23

## 1. 起點

Sprint 183 修復 `DEF-249~251` 後，`DEFERRED_ITEMS_TRACKER.md` 僅剩 `DEF-235`/`DEF-236`/`DEF-241`/`DEF-248`/`DEF-252`/`DEF-253` 六項低優先級「不排入排程」項目。本輪選定兩個角度：

1. `DEF-245`（`OrderService` 訂單狀態偽造）與 `DEF-246`（`CmsService` 發布權限繞過）是同一種模式（「通用更新端點的權限要求，比某個特定狀態值理應要求的權限來得低」），但這個模式尚未在全庫其他帶狀態欄位的實體上做過系統性複查——這是本輪第一個角度。
2. Sprint 183 審查完認證子系統後，檔案/媒體上傳是這個代碼庫另一個先前從未被系統性審查過的功能面——這是本輪第二個角度。

## 2. 掃描方法與結果

派出 2 個背景唯讀調查 agent：

- **Agent 1（狀態偽造模式全庫複查）**：系統性列出全庫 22 個帶狀態欄位的實體模型，逐一追查其 Service 層更新路徑與 Controller 權限設定，比對「通用更新端點是否允許直接指定應由特定業務事件才能觸發的狀態值」。**結論：未發現新的可利用缺陷**——`SettlementStatement`/`TenantApplication`/`ReturnRequest`/`PurchaseOrder` 等高風險候選皆已透過專屬動作方法（而非通用 patch-any-field 端點）把關，`Tenant.status` 的通用更新端點完全不讀取 `status` 欄位，`KnowledgeArticle` 雖表面符合模式但持有 `knowledge:update` 的角色（`STORE_OWNER`/`ADMIN`）本就是應該能發布的角色，無低信任角色可利用。此為有價值的負向結果，確認 `DEF-245`/`DEF-246` 這一類模式已在全庫範圍內掃除。
- **Agent 2（檔案/媒體上傳審查）**：系統性審查全部 4 個上傳相關端點（`MediaController`/`PostController` 的 multipart 與 metadata-only 兩種變體）。確認核心攻擊面（檔案類型偽造、路徑穿越、跨租戶存取）在先前 Sprint（`DEF-096`/`097`/`099`/`101`/`221`）已系統性加固並有程式碼註解可查證。找到 🟡 一致性缺口 `DEF-254`：`/v2/media/upload`（JSON metadata-only 變體）未做任何 MIME 類型/檔案大小驗證，與同代碼庫的 `uploadAssetMultipart` 既有慣例不一致。

## 3. 修復內容

### 3.1 DEF-254（🟡）：媒體 metadata-only 上傳端點缺少驗證，與既有慣例不一致

`MediaService.uploadAsset`（`POST /v2/media/upload`，JSON body，不含實際檔案內容，僅對「已存在」的 `filePath` 重新登記一筆 `MediaAsset` metadata）先前用一個不拋錯的私有方法 `inferFileType` 推斷 `fileType`：任意字串（含 `"text/html"`）只要不是 `image/`/`video/` 開頭，一律靜默落到 `DOCUMENT`，完全沒有白名單驗證，也未呼叫既有的 `validateFileSize`。這與同一代碼庫另一條真實二進位上傳路徑 `uploadAssetMultipart`（呼叫 `mediaValidationService.validateMimeType()`/`determineFileType()`/`validateActualContent()` 三重驗證）形成不一致的雙重標準。

**攻擊情境**：具備 `media:create` 權限的租戶使用者，先透過合法的 `uploadAssetMultipart` 上傳一個通過 magic-bytes 檢查的真實檔案（取得其 `filePath`），接著呼叫 `/v2/media/upload` 對**同一個 filePath** 重新登記一筆 `MediaAsset`，但這次 `mimeType` 完全由呼叫端自訂字串。之後透過 `GET /v2/media/files/{assetId}` 讀取該資產時，`MediaController.getFile` 會原樣照抄這個攻擊者自訂的字串作為回應 `Content-Type` header（搭配 `ContentDisposition.inline()`）。若攻擊者上傳的是一個通過 magic-bytes 檢查但同時嵌有 HTML/JS 的多型（polyglot）檔案，重新登記為 `text/html` 後，若有使用者直接開啟該檔案 URL（而非透過前端既有的 blob-fetch 方式載入），瀏覽器會以 HTML 解析並可能執行內嵌腳本。範圍侷限同租戶（`belongsToTenant`/`objectExists` 仍有效檢查，非跨租戶 IDOR），且目前前端 UI 未見任何呼叫此 metadata-only 端點的畫面（純後端可達、前端未接線）。

**修法**：`uploadAsset` 改用與 `uploadAssetMultipart` 相同的 `mediaValidationService.validateFileSize()`/`determineFileType()`（皆會在不合法時拋出 `BusinessException(E_9000)`），取代不拋錯的私有 `inferFileType`；後者已無其他呼叫點，一併刪除避免留下死碼。

**紅燈驗證**：`git stash` 還原 `MediaService.java`（純方法體內部邏輯調整，簽章未變）。新增的 2 個測試（`uploadAsset_disallowedMimeType_throwsE9000`/`uploadAsset_fileSizeExceedsLimit_throwsE9000`）在舊程式碼下觸發 Mockito 的 `UnnecessaryStubbingException`——證實舊版 `uploadAsset` 完全不會呼叫 `mediaValidationService` 的任何驗證方法，等同直接證明驗證缺口存在（未拋出預期例外，而是連 mock 都沒被呼叫到）；還原修復後兩案例皆轉綠。既有 3 個測試同步補上 `mediaValidationService.determineFileType`/`validateFileSize` 的明確 stub（先前依賴 Mockito 對未 stub 方法回傳 `null`/不做任何事的預設行為悄悄通過，語意不明確）。

## 4. 測試總覽

- **紅燈先行**：`DEF-254` 用 `git stash`（方法簽章未變）驗證，見 §3.1。
- **新增測試**：`MediaServiceTest` +2（`uploadAsset_disallowedMimeType_throwsE9000`/`uploadAsset_fileSizeExceedsLimit_throwsE9000`）。
- **既有測試補強（非行為缺陷，測試意圖明確化）**：`MediaServiceTest.uploadAsset_validFilePath_createsAsset` 補上 `mediaValidationService` 相關的明確 stub 與呼叫驗證斷言。
- **無新增缺陷需修復的完整負向掃描**：Agent 1 對全庫 22 個狀態欄位實體的複查記錄於 §2，確認 `DEF-245`/`DEF-246` 模式已在全庫範圍內掃除，本輪無對應修復項目。

## 5. 驗證結果

`mvn -o clean verify`（`make test-db-up` 真實 postgres/redis）：**BUILD SUCCESS**，**1622 個單元測試（+2）+ 486 個整合測試（持平），0 failed**；checkstyle（main+test）**0 違規**；PMD 無新增問題。

## 6. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-254`：狀態記為「✅ 已修復（Sprint 184）」，發現與修復同輪完成。
- 本輪 Agent 1 的狀態偽造模式全庫複查未發現新缺陷，不新增 DEF 項目，僅記錄於 Sprint Plan 供未來參考（避免未來 Sprint 重複同一角度的掃描）。
- Agent 2 額外發現一個非安全性的功能性 bug（供未來修復 session 參考，非本輪範圍）：`application.yml` 未覆寫 Spring Boot 的 `spring.servlet.multipart.max-file-size`/`max-request-size` 預設值（1MB/10MB），導致 `MediaValidationService` 宣稱的 100MB 影片上限實際上會先被 Spring 用更嚴格的 1MB 擋下，影片上傳功能可能實際上傳不了。

## 7. 誠實揭露總結

- 本輪 Agent 1 的全庫複查是一次徹底的**負向結果**（未發現新缺陷）——這本身是有價值的產出，證明 `DEF-245`/`DEF-246` 這個模式已系統性掃除，而非「本輪沒找到東西所以隨便補一個」。本人抽查驗證了兩項最關鍵的排除判斷（`TenantService.applyTenantUpdates` 確實不讀取 `status` 欄位、`RolePermissionMapping` 確認 `KNOWLEDGE_UPDATE` 確實只授予 `STORE_OWNER`/`ADMIN`），確認 agent 的排除理由屬實。
- `DEF-254` 本身是本輪唯一的修復項目，嚴重程度為 🟡（同租戶內、前端未接線、需要多型檔案技巧才能構成實際可執行的 XSS），修復理由主要是消弭代碼庫內部的驗證標準不一致（CLAUDE.md Rule 7「公開衝突，不要平均它們」——兩條上傳路徑各自代表不同的驗證嚴謹度，選擇較嚴謹的一方統一）。
- 本輪產出的修復項目數量（1 項）明顯少於先前幾輪 Sprint（3~4 項），這是掃描角度本身趨於窮盡後的自然結果，而非調查不夠仔細——過程中兩個 agent 皆花費完整調查週期（`tool_uses` 均逾 30 次）逐一排除候選項目並附上具體排除依據，非草率結案。
- `DEFERRED_ITEMS_TRACKER.md` 目前僅剩 `DEF-235`/`DEF-236`/`DEF-241`/`DEF-248`/`DEF-252`/`DEF-253` 六項低優先級「不排入排程」項目，Sprint 185 開工時仍需自選新掃描角度——建議下一輪可考慮的方向：匯出/報表類端點的資料範圍是否有洩漏、稽核日誌本身是否可被竄改/刪除、或系統性掃描 Create/Update DTO 是否還有其他「呼叫端能設定不該由其設定的欄位」的 mass assignment 模式（`DEF-244` 的廣義延伸）。
