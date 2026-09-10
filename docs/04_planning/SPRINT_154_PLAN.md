# Sprint 154 Plan — DEF-103/104/105（S135 儲存型 XSS 全掃，防禦性強化批次）

**Sprint**: Sprint 154
**日期**: 2026-09-11

---

## 1. 起點

Sprint 153 完成後，使用者要求開新 Sprint 處理 [DEFERRED_ITEMS_TRACKER.md](DEFERRED_ITEMS_TRACKER.md)「🟢 低優先級」中的技術債。查證該表「活躍延後項目」章節（第 9-53 行）後發現：🔴 高優先級已全數結案、🟡 中優先級唯一項目（DEF-021）已由使用者拍板維持現狀，低優先級 17 項狀態欄全部是「⚠️ 已記錄，**不排入排程**」——也就是說原始「待排程」佇列實際上是空的，本輪等於是主控 session 依使用者選擇**重新拍板**其中一批，而非單純接續既有排程。

使用者從三個候選批次中選擇 **DEF-103/104/105**（Sprint 135「儲存型 XSS 全掃」發現的一組協定白名單驗證缺口，三位獨立懷疑者查證後一致判定「非可利用漏洞，僅為 defense-in-depth 建議」）：

| ID | 標題 | 判定 |
|----|------|------|
| DEF-103 | `CreateListingRequest.coverImageUrl` 無協定白名單，消費端為 `<img src>` | 非可利用（`<img src>` 不執行 `javascript:`） |
| DEF-104 | `CmsDto` Banner `linkUrl` 無協定白名單，`GET /cms/banners/active` 為公開端點 | 非可利用（零前端消費端，sink 不存在） |
| DEF-105 | `PostService.createPost`/`updatePost` 對 `title`/`content` 零 HTML 消毒 | 非可利用（前台純文字顯示，非 HTML/dangerouslySetInnerHTML） |

---

## 2. 使用者決策

實作 DEF-105 前，深入查證發現原始記錄的建議（「HTML allowlist 消毒函式庫寫入前處理」）有一個未被檢視的副作用：`PostService` 的 `title`/`content` 是純文字/Markdown 來源，前台 `blog/[slug]/page.tsx` 的 `renderContentWithEmbeds` 把它當純字串直接插入 React（非 `dangerouslySetInnerHTML`）。任何以 HTML 解析器為基礎的消毒函式庫（如 Jsoup）在序列化輸出時，會把純文字中常見的 `&`/`<`/`>` 字元強制轉成 `&amp;`/`&lt;`/`&gt;` 實體編碼——這是「輸出可安全插入 HTML」的必要特性，但在現行「當純文字顯示」的渲染方式下，會讓任何含這些常見字元的合法內容（如標題「Q&A」、內文「溫度 < 100 度」）永久顯示成亂碼給使用者看，而且幾乎是必然發生而非罕見 edge case。

將此權衡提交 `AskUserQuestion` 徵詢，選項包括「照原建議做（接受亂碼風險）」「改用偵測即拒絕」「改成未來守門測試」「暫緩」。使用者選擇**「改用偵測即拒絕」**：title/content 若含 `<script>`/`<iframe>`/`<object>`/`<embed>`/`<style>`/事件屬性（`on\w+=`）/`javascript:`/`vbscript:`/`data:text/html` 等明確危險樣式即回 400 拒絕存檔，完全不改寫任何合法字元。

---

## 3. 實作內容

### 3.1 DEF-103：`CreateListingRequest.coverImageUrl` 協定白名單

[`CreateListingRequest.java:54-56`](../../backend/src/main/java/com/nextkey/ecommerce/api/dto/CreateListingRequest.java#L54-L56) 新增：

```java
@Pattern(regexp = "^(?!\\s*(?i:javascript|data|vbscript|file):).*$",
        message = "Cover image URL must not use javascript/data/vbscript/file protocol")
private String coverImageUrl;
```

採負向前瞻（negative lookahead）而非正向要求「必須是 http(s)://」，理由：欄位本身選填，且要保留未來若改用相對路徑（自架 MinIO 物件路徑）的彈性；只要不是明確的危險 scheme 即放行。`(?i:...)` 以群組內嵌旗標達成大小寫不敏感比對，`\s*` 額外擋前導空白繞過（如 `" javascript:..."`）。

### 3.2 DEF-104：`CmsDto` Banner `linkUrl` 協定白名單

[`CmsDto.java`](../../backend/src/main/java/com/nextkey/ecommerce/api/dto/CmsDto.java) 的 `CreateBannerRequest`/`UpdateBannerRequest.linkUrl` 套用與 3.1 完全相同的 `@Pattern`。查證 `CmsService` 對 `linkUrl` 未依 `linkType`（LISTING/PAGE/CATEGORY/URL）做任何格式區分，直接原樣存取，且全代碼庫目前零消費端讀取此欄位（DEF-104 原記錄已載明），故沿用同一條負向前瞻規則，刻意不強制要求完整網址格式——避免未來 `linkType=LISTING` 情境下存放純 ID/相對路徑時被誤擋。

### 3.3 DEF-105：`PostService` title/content 危險標記偵測

[`PostService.java`](../../backend/src/main/java/com/nextkey/ecommerce/core/cms/post/PostService.java) 新增 `DANGEROUS_MARKUP_PATTERN`：

```java
private static final Pattern DANGEROUS_MARKUP_PATTERN = Pattern.compile(
        "<\\s*(script|iframe|object|embed|style)\\b|on\\w+\\s*=|javascript:|vbscript:|data:text/html",
        Pattern.CASE_INSENSITIVE);
```

與 3.1/3.2 不同，此處是「內容中任意位置搜尋」（`Matcher.find()`）而非「整體必須符合格式」（`Matcher.matches()`），因為 title/content 是自由文字而非 URL 欄位——例如 Markdown 連結語法 `[text](javascript:...)` 不含 HTML 角括號標籤，但若未來加上 markdown-to-HTML 渲染會變成可執行的 `<a href="javascript:...">`，故偵測樣式同時涵蓋危險 URI scheme（不侷限於 HTML 標籤）。

新增 `validateNoUnsafeMarkup(title, content)` 私有方法，在 `createPost`（title 必填檢查後）與 `updatePost`（租戶擁有權驗證後、任何欄位變更前）呼叫，偵測到即拋 `BusinessException(ErrorCode.E_9009)`，`updatePost` 情境下確保拒絕發生在改任何欄位之前，不會部分覆寫既有內容。新增 `ErrorCode.E_9009`（"內容包含不允許的標記"），沿用既有 `E-9000s` 驗證錯誤碼區段編號慣例。

---

## 4. 範圍外（刻意不做，如實揭露）

- **不消毒改寫，只偵測拒絕**：§2 已說明理由。若使用者日後認為「明確拒絕」造成的使用者體驗（合法內容誤觸發、需要修改後重新送出）比「靜默改寫」更差，需重新評估，但那將是需求層級的取捨而非本輪技術缺陷。
- **不擴大到 `TenantUpdateRequest.coverImageUrl`（店鋪封面圖）**：查證發現此欄位與 DEF-103 描述的 `Listing.coverImageUrl` 是不同 DTO/不同網域物件（Tenant 而非 Listing），DEF-103 原記錄的標題與消費端證據皆只涵蓋 `CreateListingRequest`。`TenantUpdateRequest.coverImageUrl` 理論上可能有類似的協定驗證缺口，但那是一個**新發現**，不在本輪 DEF-103 的既定範圍內，如實記錄於此、不擅自擴大修復範圍，留待未來查證後另案登記。
- **`DANGEROUS_MARKUP_PATTERN` 是封閉式樣式清單，非通用 HTML/JS 語意分析**：只涵蓋當前已知的常見危險建構（危險標籤、事件屬性、危險 URI scheme），無法保證涵蓋所有理論上可能的混淆/編碼繞過手法（如 HTML 實體編碼過的 `<script>`、Unicode 正規化繞過等）。這在「defense-in-depth、現行渲染端本身不執行 HTML」的前提下是可接受的權衡，但**若未來真的新增 Markdown-to-HTML 或 `dangerouslySetInnerHTML` 渲染路徑，該處必須針對其實際輸出情境（HTML sink）另行消毒，不可假設這裡的檢查已經足夠**——已在程式碼註解中明確記載此警語。
- **`featuredImageUrl`（Post 精選圖）未套用協定驗證**：DEF-105 原記錄範圍僅限 title/content 的 HTML 消毒，未涵蓋此欄位；`featuredImageUrl` 性質上與 DEF-103 更接近（圖片 URL），但不在本輪三個 DEF 編號的既定範圍內，不擅自擴大。

---

## 5. 驗證結果

### 5.1 個別修復的紅燈先行驗證

三項修復都採「先確認測試綠燈 → 暫時還原/移除修復 → 確認測試真的轉紅燈 → 還原修復 → 確認回到綠燈」的流程，避免虛設斷言：

- DEF-103/104：`CreateListingRequestValidationTest`（11 案例）+ `CmsDtoValidationTest`（10 案例），共 21 案例。暫時移除兩個 `@Pattern` 後重跑：12 個危險協定案例全數轉紅（`Tests run: 21, Failures: 12`），還原後全數轉綠。
- DEF-105：`PostServiceTest` 新增 5 案例（4 個危險樣式 + 1 個反例守衛，確保含 `&`/`<`/`>` 的正常內容不被誤傷）。暫時移除 `validateNoUnsafeMarkup` 呼叫後重跑：4 個危險樣式案例轉紅（`Tests run: 32, Failures: 4`），反例守衛案例維持綠燈（證實其獨立於本次修復也成立，是正確的負向對照組）。還原後 32 案例全數轉綠。

### 5.2 `mvn -o checkstyle:check@checkstyle-main checkstyle:check@checkstyle-test`

0 違規。

### 5.3 `mvn -o verify`（完整單元＋整合回歸）

第一輪因主控 session 忘記先啟動本地測試用 Postgres（`make test-db-up`）而失敗：`SellerDashboardServiceCacheTest` 3 個既有案例因 `Connection to localhost:5432 refused` 拋 `ApplicationContext` 載入失敗——查證 `target/surefire-reports` 完整 stack trace 確認根因純屬環境未就緒（[[backend-integration-test-profile-needs-real-db]]），與本輪程式碼變更無關。啟動 `make test-db-up` 並等待 5 秒通過 postgres:18-alpine 內部重啟窗口後重跑：

**BUILD SUCCESS**：單元測試 **1282 個**（以本次執行時間窗過濾 `target/surefire-reports`，排除殘留舊報告污染加總，相對 Sprint 153 結束時基準 +26，即本輪新增的 11+10+5 個案例），**0 failures / 0 errors**；整合測試 **478 個**（與 Sprint 153 基準持平，本輪未新增整合測試），**0 failures / 0 errors**；checkstyle-main/checkstyle-test 皆 **0 violations**；PMD 通過。

### 5.4 `make validate-e2e`（乾淨 DB + host 全棧 + Playwright）

**62 passed / 4 skipped / 0 failed**，與 Sprint 153 既有基準完全一致（本輪無新增/刪除 E2E 案例，純背景回歸確認），schema 對齊無漂移。

---

## 6. 下一步 / Action Items

| # | 項目 | 來源 | 狀態 | 去向 |
|---|------|------|------|------|
| 1 | DEF-103：`CreateListingRequest.coverImageUrl` 協定白名單 | S135，本輪拍板重新排程 | ✅ 完成 | 詳見 §3.1 |
| 2 | DEF-104：`CmsDto` Banner `linkUrl` 協定白名單 | S135，本輪拍板重新排程 | ✅ 完成 | 詳見 §3.2 |
| 3 | DEF-105：`PostService` title/content 危險標記偵測 | S135，本輪拍板重新排程 | ✅ 完成 | 詳見 §3.3 |
| 4 | `mvn -o verify` 完整回歸 | 本輪交付前 | ✅ 完成 | 見 §5.3 |
| 5 | `make validate-e2e` 回歸確認無 schema/前端漂移 | 本輪交付前 | ✅ 完成 | 見 §5.4 |
| 6 | `TenantUpdateRequest.coverImageUrl` 是否有同型協定驗證缺口 | 本輪 §4 新發現 | ⬜ 待查證登記 | 需先查證是否有可達的消費端、是否構成新的 DEF 編號 |

---

## 7. 誠實揭露總結

- 本輪的「待排程佇列」實際上是重新拍板既有「不排入排程」的決策，而非承接原有排程——已在 §1 明確說明，避免造成「這些項目原本就排定要修」的錯誤印象。
- DEF-105 的實作方式（偵測即拒絕）**偏離原始 DEFERRED_ITEMS_TRACKER 記錄的建議**（HTML allowlist 消毒改寫），理由與取捨已在 §2 完整說明，非遺漏或疏忽。
- 查證 DEF-103 過程中在 `TenantUpdateRequest.coverImageUrl` 發現一個可能同型的協定驗證缺口，但**刻意不在本輪修復**（不在 DEF-103 既定範圍內），如實記錄於 §4/§6，避免範圍蔓延。
- `DANGEROUS_MARKUP_PATTERN` 是封閉式樣式清單而非通用消毒，其侷限性與「未來若渲染端改變必須重新檢視」的警語已寫入程式碼註解與 §4，不宣稱這是萬用防護。
