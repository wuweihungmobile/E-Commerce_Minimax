# Sprint 169 Plan — 媒體上傳檔名未過濾路徑穿越序列，可致未攔截例外或物件鍵遭注入路徑片段（DEF-221）

**Sprint**: Sprint 169
**日期**: 2026-09-17

## 1. 起點

`DEFERRED_ITEMS_TRACKER.md` 活躍延後項目已無待排程項目（Sprint 168 的 `DEF-220` 已修復）。本輪自選新角度：Sprint 162~168 聚焦「未攔截例外 → 500」與「認證流程節流/鎖定」兩個家族後，改為查證過去從未被系統性檢查過的媒體上傳（`StorageService`/S3-MinIO 儲存層）——`git log`/`find` 確認全庫（`src/test`）**從未存在過 `StorageServiceTest`**，是本專案第一次替這支類別補測試。

## 2. 缺口說明

`StorageService.buildObjectName(tenantId, fileName)`（`backend/.../infrastructure/storage/StorageService.java`）組出的物件鍵格式為 `{tenantId}/{uuid}-{fileName}`，其中 `fileName` 直接來自 `MultipartFile#getOriginalFilename()`——這是 multipart 請求裡呼叫端自行宣告的字串欄位，與本機檔案系統無關，任何 HTTP client 都可任意指定（不限瀏覽器行為），先前完全沒有過濾就拼進物件鍵。

以 Mockito 取代內部 `MinioClient` 直接驗證後，證實兩條互相獨立的缺口：

- **正斜線穿越序列（`../../other-tenant/evil.jpg`）**：MinIO Java SDK 自己的 `ObjectArgs.Builder.validateObjectName` 會擋下含 `.`/`..` path segment 的物件名稱，但拋出的是**未被 `StorageService.uploadFile` 任何 catch 分支接住的 `IllegalArgumentException`**（該方法只捕捉 Minio 的受檢例外與 `IOException`）。此例外會一路往上穿過 `MediaUploadService`/`MediaService`，落入全域例外處理器的 catch-all 變成 500——與 Sprint 161~166 已修復的「未攔截例外 → 500」屬同一缺陷家族，只是這次的觸發點是 SDK 自身的輸入驗證，不是本專案程式碼直接拋出。
- **反斜線穿越序列（`..\..\other-tenant\evil.jpg`）**：MinIO SDK 的 `validateObjectName` 只檢查以 `/` 分隔的 path segment，完全不處理 `\`；本專案在此之前也未做任何過濾，因此這種寫法會**原封不動**通過 SDK 檢查，讓攻擊者完全自訂的路徑分隔字元進到實際送往儲存層的物件鍵，SDK 與本專案兩層都沒有防護。

兩者共同指出：物件鍵的安全性先前完全仰賴 MinIO SDK 內建的部分檢查（只防正斜線 `..`），既不完整（反斜線繞過）也不是本專案主動控制的防線（例外沒接住，繞過會變 500 而非乾淨拒絕）。

## 3. 修法決策

不新增錯誤碼或改變既有回應行為，選擇在 `StorageService.buildObjectName` 這個所有上傳呼叫（`core.media.MediaService.uploadAssetMultipart`、`core.cms.media.MediaUploadService.uploadMedia`）的共同必經之處做**靜默淨化**：只取檔名裡最後一個 `/` 或 `\` 之後的片段（不論哪種分隔符），拿掉目錄穿越的可能性，其餘字元（含中文檔名）原樣保留——不採用允許清單正規表示式（如 `[A-Za-z0-9._-]`），因為本專案使用者以繁體中文為主，過度限制字元集會誤傷所有中文檔名這個最常見的合法情境。

## 4. 修復內容

`StorageService.java`：`buildObjectName` 呼叫新增的 `private static sanitizeFileName(String)`，取最後一個路徑分隔符（`/` 或 `\`，取較後者，不依賴平台相關的 `File.separator`）之後的子字串；若結果為空白、`.` 或 `..`，回退為固定字串 `"file"`。

## 5. 測試

- **紅燈先行**：先寫 `StorageServiceTest.java`（本專案第一支，以 `ReflectionTestUtils` 把建構子內建立的真實 `MinioClient` 換成 Mockito mock，`ArgumentCaptor<PutObjectArgs>` 擷取實際送出的物件鍵）針對修復前程式碼執行：
  - 正斜線案例：拋出未預期的 `IllegalArgumentException`（`object name with '.' or '..' path segment is not supported`），證實這是未攔截例外，不是乾淨的業務錯誤。
  - 反斜線案例：物件鍵斷言 `doesNotContain("\\")` 失敗，實際值為 `{uuid}-..\..\other-tenant\evil.jpg`，證實完全未過濾。
- 套用 §4 修復後重跑，3 個案例（正斜線、反斜線、中文檔名不受影響）全數轉綠。

## 6. 驗證結果

- 紅燈階段：見 §5，2 個案例確認先失敗（1 個未預期例外 + 1 個斷言失敗）再轉綠。
- `mvn -o compile`：每次修改後立即編譯，通過。
- `checkstyle`（main+test）：0 違規。
- `mvn -o verify`（真實 postgres/redis，`make test-db-up`）：**1410 個單元測試（+3）+ 482 個整合測試（持平），0 failed**，`BUILD SUCCESS`。**首次執行時 4 個既有整合測試類別（`PaginationBoundaryValidationTest`/`PostControllerFilterValidationTest`/`RequestParamTypeMismatchValidationTest`/`StripeWebhookReachabilityTest`）因 `Connection to localhost:5432 refused` 出現非本輪造成的環境性失敗**——根因是 `make test-db-up` 與背景執行的 `mvn verify` 幾乎同時啟動，撞上 postgres:18-alpine initdb 後內部重啟窗口（與既有 [[validate-schema-doc-pg-isready-race]] 記錄的判別法一致：這 4 個失敗類別與本輪修改的 `StorageService`/媒體上傳完全無關，`Caused by` 明確是 JDBC 連線被拒，非程式邏輯問題）；等待 postgres 穩定後重跑，全數轉綠，確證為環境時序問題非回歸。
- 未執行 `make validate-e2e`：本輪修復範圍完全侷限於後端 `StorageService` 內部物件鍵組裝邏輯，未變更任何 API 契約、DTO 欄位或前端呼叫的回應格式，媒體上傳功能本身在本機環境也因 §8 所述 MinIO image 拉取失敗而無法啟動測試，`make validate-release` 執行前會涵蓋完整 E2E 驗證。

## 7. 更新 `DEFERRED_ITEMS_TRACKER.md`

新增 `DEF-221`（已修復）：媒體上傳 `StorageService.buildObjectName` 未過濾 `MultipartFile#getOriginalFilename()`，正斜線路徑穿越序列會讓 MinIO SDK 拋出未攔截的 `IllegalArgumentException`（→ 500），反斜線路徑穿越序列則完全繞過 SDK 內建檢查；新增 `sanitizeFileName` 只取路徑最後片段，中文檔名不受影響。

---

## 8. 誠實揭露總結

- **未對真實 MinIO 後端做端對端驗證**：本輪原計畫用 `docker compose --profile storage up -d minio` 起一個真實 MinIO 容器做端對端確認，但 `minio/minio:RELEASE.2025-09-07T16-13-09Z` 這個已釘定版本在 Docker Hub 拉取失敗（`pull access denied for minio/minio, repository does not exist or may require 'docker login'`）——初步判斷是 MinIO 官方把映像檔從 Docker Hub 下架/搬遷（例如轉往 `quay.io`），不是網路或認證問題，但未進一步查證確切原因。依 CLAUDE.md 的 Docker 管理限制規則（禁止未經人工確認變更已釘定 image 版本/來源），未擅自修改 `docker-compose.override.yml` 的 image tag，本輪修復與測試完全建立在「Mockito 取代 `MinioClient`」的單元測試層級，驗證的是**本專案程式碼實際送給 SDK 的物件鍵字串**與 SDK 客戶端驗證邏輯的行為（已用 `javap` 反組譯 SDK class 確認 `validateObjectName` 的實際檢查邏輯與拋出例外類型），但未驗證這把物件鍵送到真實 MinIO/S3 伺服器後，伺服器端是否有額外的正規化或差異行為。**媒體上傳功能本身目前在此環境無法透過 `make up-storage` 部署測試**，這是一個獨立於本次修復的環境問題，需要使用者確認是否要更新 `DOCKER_POLICY.md` 核准清單改用其他來源（如 `quay.io/minio/minio`）。
- **`sanitizeFileName` 只處理路徑分隔符，未處理其他可能有害的字元**：例如檔名中的 NUL 字元、控制字元、或極端長度，這些先前也未被過濾，本輪範圍刻意只鎖定「路徑穿越」這個已用紅燈測試證實的具體缺口，未做超出範圍的推測性防護（Rule 2）。
- **回應下載時的 `Content-Disposition` 標頭已確認安全，未列入本輪修復範圍**：`MediaController`/`PostController` 皆透過 Spring `ContentDisposition.inline().filename(...)` 建構器組出標頭（內部會做 RFC 5987/6266 編碼），檔名中的特殊字元不會造成標頭注入，故排除在本輪修復範圍外，僅在調查過程中順帶確認。
