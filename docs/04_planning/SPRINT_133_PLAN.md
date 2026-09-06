# Sprint 133 Plan — DEF-097 修復

**Sprint**: Sprint 133
**日期**: 2026-09-07

---

## 1. 本輪範圍

DEFERRED_ITEMS_TRACKER.md 上僅存的候選項目 DEF-097：`/cms/media` 頁面圖片預覽疑似從未正確渲染（`filePath` 為 MinIO 內部物件路徑，瀏覽器無法直接存取）。

DEF-097 原始記錄標記「需決定：是否也比照 DEF-096 補檔案串流端點，或改用 presigned URL」，查證程式碼現況後，以 `AskUserQuestion` 讓使用者對兩個決策點拍板：

| # | 問題 | 拍板結果 |
|---|------|---------|
| 1 | DEF-097 圖片顯示要採用哪種技術方案？ | 後端授權串流端點 + 前端 blob fetch（而非 presigned URL） |
| 2 | 查證過程中發現 DEF-096（Sprint 132 已修）的 `dashboard/media/page.tsx` 很可能有相同的根本問題（`<img>` 標籤無法附加 Authorization header），如何處理？ | 本輪一併修復 |

**技術判斷（Presigned URL 為何不可行）**：本機 `docker-compose.override.yml` 的 `STORAGE_ENDPOINT=http://minio:9000` 為 docker 內部 hostname，僅後端容器可解析；瀏覽器無法連線此位址，故 MinIO SDK 產生的 presigned URL 在本機環境會失效。改用「後端授權端點串流 + 前端 apiClient 帶 token 抓 blob」不受此網路拓樸限制。

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

每完成一項，立即編譯（`mvn compile`/`npx tsc --noEmit`）+ 執行相關測試，絕不累積開發。

---

## 3. 各項修復摘要

### §1 DEF-097：`/cms/media` 圖片預覽

**後端**：

1. `StorageService.objectExists(UUID, String)` → `objectExists(String objectName)`，比照 DEF-096 對 `getObject` 的修法，直接以完整物件路徑檢查是否存在，修正原路徑重新推導缺陷（`buildObjectName()` 會產生新的亂數 UUID，與 `uploadFile()` 實際回傳路徑不符）。全庫零真實呼叫端，僅測試 mock 需同步調整。
2. `core.cms.media.MediaService` 新增 `downloadMedia(UUID mediaId, UUID tenantId)`：比照既有 `getMedia`/`deleteMedia` 的 tenant 擁有權檢查模式（`getMediaOrThrow` + tenant 比對），以 `storageService.getObject(media.getFilePath())` 取檔，回傳新增的 `MediaFile(InputStream, mimeType, fileName)` record。
3. `PostController` 新增 `GET /v2/dashboard/media/{mediaId}/file`（沿用既有媒體端點的角色限制 `STORE_OWNER`/`STORE_STAFF`/`SELLER`/`HOST` + `CMS_ENABLED` feature toggle 檢查），串流回應（`InputStreamResource` + `Content-Disposition: inline`）。

**前端**：

1. 新增共用元件 `components/ui/authenticated-image.tsx`（`AuthenticatedImage`）：以傳入的 `fetchBlob(id)` 函式（須為模組層級穩定函式，非行內箭頭函式）抓取 blob，建立 `URL.createObjectURL()` 物件網址，卸載/id 變更時 `URL.revokeObjectURL()` 釋放；失敗時渲染呼叫端提供的 `fallback`。
2. `services/cms.ts` 新增 `getMediaFileBlob(mediaId)`（`apiClient.get(..., {responseType: 'blob'})`，自動帶 Authorization header）。
3. `cms/media/page.tsx` 的 `<img src={media.filePath}>`（含手動 `innerHTML` fallback 的 DOM 操作）改為 `<AuthenticatedImage id={media.id} fetchBlob={getMediaFileBlob} .../>`，同時簡化掉原本不安全的 `innerHTML` 替換寫法。

### §2 DEF-098：`dashboard/media/page.tsx`（DEF-096 回歸）

查證 DEF-097 時發現：DEF-096（Sprint 132）新增的 `GET /v2/media/files/{assetId}` 端點要求 `@PreAuthorize("hasAuthority('media:read')")`（需 JWT），但前端直接用 `<img src={item.url}>` 存取——瀏覽器 `<img>` 標籤無法附加自訂 `Authorization` header，故實際瀏覽器渲染時該請求會以匿名身分送出，遭 401/403 拒絕，圖片顯示會 fallback 到圖示（DEF-096 的自動化測試因 MockMvc 直接帶 header 送出請求而測不出此問題）。

比照 DEF-097 相同手法修復：

1. `services/media.ts` 新增 `getMediaAssetFileBlob(assetId)`（帶 token 抓 blob）。
2. `dashboard/media/page.tsx` 改用 `AuthenticatedImage`，取代原本的裸 `<img src={item.url}>` + 手動 DOM class 切換 fallback 邏輯。

`MediaAssetDto.url` 欄位維持不變（後端仍會回傳，僅前端不再以此欄位直接當 `<img src>`），非本輪處理範圍。

---

## 4. 調查過程中發現、另開追蹤的項目

無（DEF-098 已於本輪同步修復並結案，詳見 §2）。

---

## 5. 驗證

- 後端單元測試：`core.cms.media.MediaServiceTest$DownloadMedia`（新增 3 案例：成功／404／跨租戶 403）
- 後端整合測試：`PostControllerE2ETest.getMediaFile_shouldSucceed`（新增 1 案例，IT-M15-011b）
- 後端全量回歸：`mvn -o verify` **BUILD SUCCESS**
  - 單元測試 **1110**（相對 Sprint 132 的 1107，+3，恰等於 `MediaServiceTest$DownloadMedia`+3）
  - 整合測試 **475**（相對 474，+1，恰等於 `PostControllerE2ETest`+1）
  - 0 Failures / 0 Errors；checkstyle（main+test）**0 violations**
- 前端：`npx tsc --noEmit` 通過、`npx eslint`（逐檔案）修正 1 個新增錯誤（`authenticated-image.tsx` 效果內同步呼叫 `setState` 觸發 `react-hooks/set-state-in-effect`，移除多餘的同步重置邏輯後清除；僅既有 pre-existing 警告如 `no-img-element`/`exhaustive-deps` 不受影響）、`npm run build` 全站生產建置通過
- 未新增/修改 Flyway migration（本輪未變更任何 `@Entity` 欄位型別/資料庫結構），`make validate-schema`/`make validate-schema-doc` 不適用
