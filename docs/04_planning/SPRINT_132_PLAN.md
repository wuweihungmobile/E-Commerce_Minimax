# Sprint 132 Plan — DEF-094~096 修復

**Sprint**: Sprint 132
**日期**: 2026-09-06

---

## 1. 本輪範圍

Sprint 131 調查過程中新登記的三個低優先級延後項目：DEF-094（訂房結帳頁電話姊妹缺陷）、DEF-095（ERP 採購單編輯頁「更新訂單」空實作）、DEF-096（M18 媒體資產庫半成品功能）。

DEF-095/DEF-096 皆標記「待拍板」，本輪先查證程式碼現況，再以 `AskUserQuestion` 讓使用者對兩個決策點拍板：

| # | 問題 | 拍板結果 |
|---|------|---------|
| 1 | DEF-095：編輯採購單「預計到貨日期」欄位，後端原僅支援更新 notes，此欄位如何處理？ | 擴充後端支援編輯（而非改為唯讀） |
| 2 | DEF-096：M18 媒體資產庫（`/v2/media` 系列）完整但從未接線，與已在生產使用的 `/cms/media` 功能重疊，如何處理？ | 繼續開發串接（而非移除半成品） |

兩項拍板皆選擇比原估規模更大的選項，實際工作量遠超原始 1+2+3=6 SP 估算。

---

## 2. 執行原則（依 CLAUDE.md 強制規則）

每完成一項，立即編譯（`mvn compile`/`npx tsc --noEmit`）+ 執行相關測試，絕不累積開發。

---

## 3. 各項修復摘要

### §1 DEF-094：訂房結帳頁電話驗證/送出不一致

`checkout/page.tsx`（純訂房結帳頁）送出前補上 `.replace(/\s/g, '')`，與驗證（:83）及 DEF-084 已修復的 `checkout/mixed/page.tsx` 一致。

### §2 DEF-095：採購單編輯支援更新預計到貨日期

**後端**：`PurchaseOrderUpdateRequest` 新增 `expectedDeliveryDate: LocalDate` 欄位；`PurchaseOrderService.updatePurchaseOrder()` 比照既有 notes 的 null-guard 慣例新增賦值邏輯（僅 DRAFT 狀態可更新，繼承既有 E_7002 保護）。

**前端**：`PurchaseOrderUpdateRequest` 型別補上欄位；`PurchaseOrderForm.tsx` 補上 `handleUpdate()`，接上原本空實作的「更新訂單」按鈕。

**已知限制**（沿用既有 notes 欄位的相同設計慣例，未額外處理）：`expectedDeliveryDate` 與 `notes` 皆採「非 null 才更新」語意，若採購單已設定到貨日期，目前無法透過編輯表單清空為未設定——與既有 notes 欄位的限制一致，非本輪引入的新問題。

### §3 DEF-096：M18 媒體資產庫真實檔案上傳與串流

查證後發現實際缺口比「補一個上傳按鈕」更深：

- `POST /v2/media/upload`（既有 JSON 端點）要求呼叫端已持有 `filePath`（已存在的儲存路徑），**完全沒有真正的二進位檔案上傳能力**。
- `StorageService.getObject(UUID, String)` 透過 `buildObjectName()` 重新產生亂數 UUID 組字串，與 `uploadFile()` 實際回傳的儲存路徑不符，**此方法在修正前全庫零呼叫端、從未被正確使用過**（`objectExists(UUID, String)` 有相同設計缺陷，但因不在本輪範圍不修改，僅記錄於 DEF-097）。
- `MediaAssetDto.url` 欄位組出的 `/api/v2/media/files/{id}` 路徑，對應的 Controller 端點原本並不存在。

**後端修復**：

1. `StorageService.getObject(UUID, String)` → `getObject(String objectName)`，直接以完整物件路徑取檔（比照既有 `deleteObject(String)` 的正確模式），修正原設計缺陷。
2. `core.media.MediaService` 新增依賴注入 `StorageService`、`MediaValidationService`（沿用 `core.cms.media` 套件既有驗證邏輯，不重複實作）、`UserRepository`。
3. 新增 `MediaService.uploadAssetMultipart(MultipartFile, categoryId, tags, altText, title)`：分類/標籤/替代文字/標題皆選填，驗證檔案大小與 MIME 類型後寫入 `StorageService`，建立 `MediaAsset` 記錄（含 uploader）。
4. 新增 `MediaService.downloadAsset(UUID assetId)` + `MediaService.AssetFile` record：以資產儲存的完整 `filePath` 向 `StorageService` 取檔（租戶範圍檢查沿用既有 `findActiveByIdAndTenantId`）。
5. `MediaController` 新增 `POST /v2/media/upload-multipart`（`media:create`）與 `GET /v2/media/files/{assetId}`（`media:read`，串流回應）。

**前端修復**：

1. `services/media.ts` 新增 `uploadMediaAssetMultipart()`（FormData 多部分上傳）；`MediaAssetDto` 補上 `url`/`altText`/`title` 欄位。
2. `dashboard/media/page.tsx` 新增「上傳媒體」區塊（分類選填下拉、標籤逗號分隔輸入、檔案選擇按鈕）與「新增分類」快速輸入（呼叫既有但先前未串接的 `createMediaCategory`）；圖片預覽由 `item.filePath`（MinIO 內部物件路徑，非可存取 URL）改為 `item.url`（新增的檔案串流端點）。

**範圍外、未處理事項**：

- 分類的編輯/刪除、媒體資產的標籤/替代文字/標題編輯 UI 未補齊（`updateMediaAsset`/`updateMediaCategory`/`deleteMediaCategory` 服務函式仍未串接）——本輪聚焦「讓真實檔案上傳與顯示能動起來」，UI 完整度留待下一輪視需求評估。
- `/cms/media` 頁面的 `<img src={media.filePath}>` 同樣指向 MinIO 內部物件路徑而非可存取 URL，圖片預覽推測從未正確渲染過——與 DEF-096 是同一儲存層缺口，但屬不同頁面/端點，依 Rule 3 精準改動原則不在本輪修復，另登記 DEF-097。

---

## 4. 調查過程中發現、另開追蹤的項目

- **DEF-097**：`/cms/media` 頁面（`services/cms.ts` 的 `getMediaList`/`uploadMediaMultipart`）圖片預覽疑似從未正確渲染——`filePath` 為 MinIO 內部物件路徑（如 `{tenantId}/{uuid}-{filename}`），瀏覽器無法直接存取；`StorageService.objectExists(UUID, String)` 與本輪修復前的 `getObject` 有相同的路徑重新推導設計缺陷（全庫零呼叫端，本輪未修改）。

---

## 5. 驗證

- 後端單元測試：`PurchaseOrderServiceTest`（+1，updatePurchaseOrder 更新到貨日期）、新建 `core.media.MediaServiceTest`（+6，此 Service 先前完全零測試覆蓋，僅涵蓋本輪新增的 `uploadAssetMultipart`/`downloadAsset`）
- 後端整合測試：`M18MediaIntegrationTest`（+2，IT-M18-014 實際二進位上傳／IT-M18-015 檔案串流回應）
- 後端全量回歸：`mvn -o verify` **BUILD SUCCESS**
  - 單元測試 **1107**（相對 Sprint 131 的 1100，+7，恰等於 `PurchaseOrderServiceTest`+1、`MediaServiceTest`+6）
  - 整合測試 **474**（相對 472，+2，恰等於 `M18MediaIntegrationTest`+2）
  - 0 Failures / 0 Errors；checkstyle（main+test）**0 violations**
- 前端：`npx tsc --noEmit` 通過、`npx eslint`（逐檔案）無新增錯誤（僅既有警告）、`npm run build` 全站生產建置通過
- 除錯過程記錄：`M18MediaIntegrationTest` 的 `mediaService` mock 為 class 層級單例（`@Primary Mockito.mock(...)` bean，跨所有 `@Test` 共用），每個 `@BeforeEach` 重新 `when(...)` 同一方法簽章時，Mockito 會先以既有 stub 執行一次「探測呼叫」（此時 `any()` 對應的實際引數皆為 `null`）——這正是既有 `createCategory`/`uploadAsset` 等 stub 皆對引數做 null-guard 的原因，本輪新增的 `uploadAssetMultipart` stub 一開始漏了這個防禦，補上後全數通過。
- 未新增/修改 Flyway migration（本輪未變更任何 `@Entity` 欄位型別/資料庫結構），`make validate-schema`/`make validate-schema-doc` 不適用
