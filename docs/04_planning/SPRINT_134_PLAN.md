# Sprint 134 Plan — 檔案上傳驗證安全掃描（DEF-099~101）

**Sprint**: Sprint 134
**日期**: 2026-09-07

---

## 1. 本輪範圍

回歸 AISDLC 流程盤點：`DEFERRED_ITEMS_TRACKER.md` 高優先級待辦已再次歸零（延續 Sprint 128~133 的「前後端 API 契約漂移全掃」收尾），文件未明寫下一步，屬於需要向使用者確認方向的節點。

透過 `AskUserQuestion` 讓使用者對兩個決策點拍板：

| # | 問題 | 拍板結果 |
|---|------|---------|
| 1 | 技術債已再次歸零，下一輪 AISDLC 流程要往哪個方向走？ | 開新一輪系統性掃描 |
| 2 | 這一輪系統性掃描要涵蓋哪些角度？（候選：檔案上傳 MIME 嗅探繞過／儲存型 XSS 全掃／稽核日誌覆蓋率） | 檔案上傳 MIME 嗅探繞過 |

執行方式：以 Workflow 進行三階段多 agent 掃描（Discover 找出所有上傳端點 → Analyze 逐端點深入分析驗證破口 → Verify 對抗性驗證排除假陽性，預設傾向懷疑）。掃描 5 個端點、原始發現 10 項，驗證後確認 **7 項為真**，去除重複描述同一根因者後，實質為 **3 個獨立缺陷**（DEF-099~101）。3 項排除的假陽性（Stored XSS via metadata-only 端點——與 DEF-101 性質重疊且已受 `nosniff` 緩解；儲存路徑檔名路徑穿越——依賴 MinIO 後端是否正規化，證據強度不足）不列入本輪修復範圍。

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

每完成一項，立即編譯（`mvn compile`/`checkstyle:check`）+ 執行相關測試，絕不累積開發。

---

## 3. 各項修復摘要

### §1 DEF-099：上傳驗證無 magic bytes 檢查

**問題**：`MediaValidationService.determineFileType()`/`validateFileSize()` 僅比對 `file.getContentType()`（multipart part 的 Content-Type header，完全由呼叫端自訂），全程未讀取檔案實際位元組驗證真實格式；同時 `getMaxSizeForType()` 也依賴同一可偽造欄位判定大小上限（謊報 `video/mp4` 可拿到 100MB 配額）。此驗證服務為 `core.media`/`core.cms.media` 共用單例，缺口同時影響 `/v2/media/upload-multipart` 與 `/v2/dashboard/media/upload-multipart` 兩端點。

**修法**：

1. `MediaValidationService` 新增 `validateActualContent(MultipartFile file, String mimeType)`：讀取檔案開頭 16 bytes，比對 8 種允許型別（JPEG/PNG/GIF/WEBP/PDF/MP4/QuickTime/AVI）各自的已知 magic number（以具名 `static final int[]` 常數宣告，避免 checkstyle `MagicNumberCheck` 誤判），不符即拋 `E_9000`。不引入第三方函式庫（`pom.xml` 確認無 Tika/commons-io 可用，純手寫位元組比對已足夠應付這 8 種封閉允許清單）。
2. `core.media.MediaService.uploadAssetMultipart()` 與 `core.cms.media.MediaUploadService.uploadMedia(MultipartFile)` 於既有 `determineFileType()` 後插入呼叫。

**已知限制（誠實揭露）**：MP4/QuickTime 的區分僅以 `ftyp` box 第二個 4 bytes（major brand，`qt  ` 判定為 QuickTime）為準，對於不含 `ftyp` box 的極舊式 QuickTime 檔案改用 `moov`/`mdat`/`free`/`wide` atom 前綴寬鬆比對；未逐一驗證所有 ISO base media file format brand 變體，屬於封閉允許清單下的務實折衷，非完整的媒體格式解析器。

### §2 DEF-100：下載端點 Content-Disposition 檔名注入

**問題**：`MediaController.getFile()`（`/v2/media/files/{assetId}`）與 `PostController.getMediaFile()`（`/v2/dashboard/media/{id}/file`）皆為 `"inline; filename=\"" + file.fileName() + "\""` 原始字串拼接，`fileName()` 來自使用者上傳時自訂、未經任何跳脫的原始檔名，理論上可注入額外的 `filename*=` 參數誤導瀏覽器對下載檔案類型的判斷。

**修法**：兩端點皆改用 Spring 內建的 `ContentDisposition.inline().filename(name, StandardCharsets.UTF_8).build()`，交由框架處理特殊字元跳脫與 RFC 6266 非 ASCII 檔名編碼，不再手動拼接字串。

### §3 DEF-101：Metadata-only 上傳端點跨租戶 IDOR

**問題**：`POST /v2/media/upload`（JSON）與 `POST /v2/dashboard/media/upload-by-path` 兩個「metadata-only」端點（不接收真實檔案位元組，只接收 client 自報的 `filePath` 等欄位）對 `filePath` 完全不做擁有權/存在性檢查即寫入 `MediaAsset.filePath`；下載端點（`downloadAsset`/`downloadMedia`）也只驗證「資料列的 tenantId」相符，從未驗證 `filePath` 字串本身是否真的落在該租戶的物件前綴下——「多租戶隔離」實際上只是 `StorageService.buildObjectName()` 寫入時的命名慣例，從未在讀取時被強制驗證的存取控制邊界。只要能得知（或洩漏管道取得）其他租戶在同一 MinIO bucket 下的真實物件路徑，即可在自己租戶下登記一筆指向該路徑的假 `MediaAsset`，再透過自己合法的下載端點把該物件內容讀出，形成跨租戶任意物件讀取。

**修法**：

1. `StorageService` 新增 `belongsToTenant(String objectName, UUID tenantId)`：檢查 `objectName` 是否以 `{tenantId}/` 開頭（比照 `buildObjectName()` 既有命名慣例）。
2. **寫入端把關**：`core.media.MediaService.uploadAsset()`（JSON 端點）與 `core.cms.media.MediaUploadService.uploadMedia(String 版)`（`upload-by-path` 端點）新增 `belongsToTenant` + `objectExists`（Sprint 132/133 已修好但全庫零呼叫端的既有方法）雙重檢查，任一失敗即拋 `E_9000`（「Invalid file path」，不透露是「不屬於本租戶」還是「物件不存在」，避免成為攻擊者探測其他租戶路徑是否存在的 oracle）。
3. **讀取端縱深防禦**：`core.media.MediaService.downloadAsset()` 與 `core.cms.media.MediaService.downloadMedia()` 在既有的資料列 tenantId 檢查之後，新增獨立的 `belongsToTenant` 檢查——即使未來出現其他寫入路徑繞過寫入端檢查，下載端仍會擋下（回傳與「資產不存在」相同的錯誤碼 `E_4000`/`E_4103`，同樣不額外洩漏內部判斷依據）。

---

## 4. 調查過程中發現、另開追蹤的項目

無新增。掃描過程中識別出另外 2 個候選角度（儲存型 XSS 全掃、稽核日誌覆蓋率）留待未來 Sprint 由使用者排程決定是否啟動，非本輪範圍。

---

## 5. 驗證

- 後端單元測試新增：
  - `MediaValidationServiceTest$ValidateActualContent`（+12：8 種允許型別各自的正確簽章通過 + 4 個偽造/空檔案拒絕案例）
  - `core.media.MediaServiceTest`（+4：`uploadAsset` 合法 filePath 成功 / 不屬本租戶拒絕 / 物件不存在拒絕 3 案例 + `downloadAsset` 跨租戶阻擋 1 案例）
  - `core.cms.media.MediaUploadServiceTest$UploadMediaPathString`（+1：`upload-by-path` 跨租戶 filePath 拒絕）
  - `core.cms.media.MediaServiceTest$DownloadMedia`（+1：`downloadMedia` 跨租戶阻擋）
- 涉及檔案的既有測試（`uploadAssetMultipart`/`uploadMedia(MultipartFile)`/`downloadAsset`/`downloadMedia` 既有案例）全數同步更新 mock stub（新增 `belongsToTenant`/`objectExists` 樁）後**全數維持通過，無回歸**
- Checkstyle：main（`MagicNumberCheck` 修正為具名常數）+ test（僅檢查 `UnusedImports`）皆 0 violations
- 針對性單元測試：`MediaValidationServiceTest` / `core.media.MediaServiceTest` / `core.cms.media.MediaUploadServiceTest` / `core.cms.media.MediaServiceTest` 四類**全數通過**（詳細數字見下方全量回歸）
- 整合測試：`M18MediaIntegrationTest`（15/15 通過）、`PostControllerE2ETest`（22/22 通過，含 `getMediaFile_shouldSucceed`）── **誠實揭露**：這兩個測試類別的 `MediaService` 是 `IntegrationTestConfiguration` 提供的 `@MockBean`，故只驗證 HTTP 層（路由/序列化）未被本輪改動破壞；`testGetFile`/`getMediaFile_shouldSucceed` 確實會執行到 Controller 內真實的 `ContentDisposition` 建構程式碼（DEF-100 修法本身在 Controller 層，未被 mock），但 DEF-099/101 的實際驗證邏輯位於被 mock 掉的 Service 層，此處不構成端對端證明——DEF-099/101 的真實覆蓋率來自上方使用真實 Service 實例（僅 repository/儲存層被 mock）的單元測試
- 後端全量回歸：`mvn -o verify` **BUILD SUCCESS**
  - 單元測試 **1128**（相對 Sprint 133 的 1110，+18，恰等於本輪四個測試檔新增數：`MediaValidationServiceTest$ValidateActualContent`+12、`core.media.MediaServiceTest`+4、`MediaUploadServiceTest$UploadMediaPathString`+1、`core.cms.media.MediaServiceTest$DownloadMedia`+1）
  - 整合測試 **475**（與 Sprint 133 相同，本輪未新增整合測試案例）
  - 0 Failures / 0 Errors / 0 Skipped
  - checkstyle（main + test）**0 violations**（`MagicNumberCheck` 以具名 `static final int[]` 常數修正後清除）
  - PMD **0 violations**
- 未新增/修改 Flyway migration（本輪未變更任何 `@Entity` 欄位型別/資料庫結構），`make validate-schema`/`make validate-schema-doc` 不適用
