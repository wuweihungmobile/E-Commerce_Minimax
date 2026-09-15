# Sprint 165 Plan — 延伸 Sprint 164 §7 誠實揭露：分頁 page/size 邊界值導致 500

**Sprint**: Sprint 165
**日期**: 2026-09-15

---

## 1. 起點

[SPRINT_164_PLAN.md](SPRINT_164_PLAN.md) §7 誠實揭露：「未處理 `size <= 0`（含負數與零）時 `PageRequest.of` 建構子本身拋出未攔截 `IllegalArgumentException` 導致 500 的情況」。本輪（使用者要求「繼續完成任務」）驗證並修復此缺口。

**風險模型**：Spring Data 的 `PageRequest`/`AbstractPageRequest` 建構子本身驗證 `page >= 0`、`size >= 1`，不合法時直接拋出 `IllegalArgumentException`。Sprint 164 的 `Math.min(size, N)` 修法只補了上限，`Math.min(0, 100)` 仍是 0、`Math.min(-5, 100)` 仍是 -5，完全不擋下限；`page` 更是連上限修法都沒碰過。故全庫任何分頁端點只要帶 `size=0`、`size<0` 或 `page<0`，都會 500。

---

## 2. 修法範圍決策

此問題與 Sprint 162/163 已拍板的「全域例外處理器 → 400」模式看似同源，但性質不同：Sprint 161 §4 已明確判斷過，`IllegalArgumentException` 可能源自程式其他邏輯（非僅止於分頁參數解析），全域攔截有掩蓋真正程式錯誤的風險，故刻意不做。本輪不重啟這個已有結論的辯論，改為評估「如何在源頭修正分頁參數本身」的範圍與作法，透過 `AskUserQuestion` 徵詢使用者，提供三個選項：①新增共用工具方法統一處理邊界（推薦）②逐點內嵌 `Math.max`/`Math.min` 展開式③本輪不修。**使用者選擇①共用工具方法**。

---

## 3. 修復內容

### `DEF-216`：新增 `PageableUtils` 共用工具，全庫分頁參數統一正規化邊界

新增 [`PageableUtils.java`](../../backend/src/main/java/com/nextkey/ecommerce/shared/util/PageableUtils.java)：

```java
public static PageRequest of(int page, int size, int maxSize)        // 無排序
public static PageRequest of(int page, int size, int maxSize, Sort sort) // 含排序
```

內部邏輯：`page` 正規化為 `Math.max(0, page)`；`size` 正規化為 `Math.max(1, Math.min(size, maxSize))`。**設計取向是靜默正規化而非拒絕請求**——與 Sprint 162/163「攔截例外轉 400」不同：此處異常輸入不是「格式錯誤到無法處理」，而是可以合理地正規化成一個仍然有意義的請求（例如 `size=0` 正規化為 1，`page=-1` 正規化為 0），讓請求正常成功（200）而非用 400 回絕，語意上更貼近既有 `ReviewService.searchReviews` 早已採用的 `Math.max(0, page)`/`Math.max(1, Math.min(size, N))` 手寫慣例——本輪只是把這個既有慣例抽成共用工具、並補齊到全庫其餘所有遺漏此下限防護的呼叫點。

**全庫替換範圍**：跨 **26 個檔案**，涵蓋：
- Sprint 164 剛修復的 21 處（8 個既有測試檔案 + `SettlementGenerator`/`ErpController`×3/`TransferController`/`ReturnRequestController`×2 這 7 處先前無測試基礎設施者）
- 先前已有 `Math.min(size, N)` 上限防護、但同樣缺下限防護的 **13 個既有檔案**（`OrderService`/`RoomService`/`ChatService`/`BookingService`/`NotificationService`/`NotificationHistoryService`/`FaqService`/`ProductService`/`CmsService`/`KnowledgeBaseService`/`ReviewService`/`BookingReviewService`/`core/media/MediaService`）
- 查證過程中額外發現、先前完全未被任何 Sprint 掃描到的 **`ListingController.getListings`**（`GET /v2/listings` 公開商品/房源搜尋，已有上限但缺下限）

`ReviewService.searchReviews` 原本手寫的 `Math.max(0, page)`/`Math.min(Math.max(1, size), DEFAULT_PAGE_SIZE)` 一併改用共用工具，並簡化為直接讀 `pageRequest.getPageNumber()`/`getPageSize()` 建構回應，移除原本重複計算的局部變數。

`ReturnRequestController` 原本 `size > 0 ? Math.min(size, 100) : DEFAULT_PAGE_SIZE` 的下限回退值原為 `DEFAULT_PAGE_SIZE`（20），改用共用工具後 `size<=0` 的回退值統一變成 `1`（工具方法的固定下限），與全庫其餘呼叫點行為一致；移除該檔案因此變成無用的局部 `DEFAULT_PAGE_SIZE` 常數。

**刻意不動**：`NotificationService.java` 的 `PageRequest.of(0, Integer.MAX_VALUE)`（固定字面值，範圍限於呼叫者自己的未讀通知，非使用者可任意帶入的參數）；其餘全庫 `PageRequest.of(0, 1)`/`PageRequest.of(0, 100)`/`PageRequest.of(0, 200, ...)` 等純字面值呼叫（非使用者可控引數，無邊界風險）。

---

## 4. 驗證結果

- 紅燈先行：以 `GET /v2/posts?size=0` 與 `?page=-1`（公開端點，Sprint 164 已確認的最高風險端點）實際執行，修復前皆得到 500；改用 `PageableUtils` 後改為靜默正規化，回應 200。
- 新增 `PaginationBoundaryValidationTest`（2 案例，鎖定上述行為）與 `PageableUtilsTest`（6 案例，直接對工具方法本身做邊界值單元測試：`size<=0`、`page<0`、`size` 超上限、合法值原樣保留、含 `Sort` 多載、極端組合皆不拋例外）。
- 既有 8 個檔案（Sprint 164 新增的 `ArgumentCaptor<Pageable>` 測試）與 `ReviewServiceTest` 皆重新執行確認未受影響：**214 個相關測試全數通過**。
- `checkstyle:check`（main + test）：0 違規。
- `mvn -o verify` **1375 個單元測試（+8）+ 480 個整合測試（持平），0 failed**，PMD 無新增違規，`BUILD SUCCESS`（7m23s）。
- 本輪未執行 `make validate-e2e`（`size≤100` 且 `page≥0` 的合法請求行為完全不變，僅新增邊界值下的正規化行為，不影響任何 E2E 既定 happy path 斷言）。

---

## 5. 更新 `DEFERRED_ITEMS_TRACKER.md`

- 新增 `DEF-216`（已修復，見 §3）。

---

## 6. 誠實揭露總結

- `ReturnRequestController` 的下限回退值行為有微小變動（`size<=0` 時從原本的 20 變成 1），屬於刻意的一致性選擇（全庫統一使用共用工具的固定下限），已於 §3 記錄，未特別為此保留該檔案的舊有例外邏輯。
- `PostService.getPublishedPosts` 等回應 DTO 的 `page`/`size` 欄位回顯的是**原始請求值**而非正規化後的實際值（此為 Sprint 164 之前就存在的既有行為，本輪未一併修正，非本輪引入）——例如 `size=0` 的請求實際查了 1 筆，但回應 `size` 欄位仍顯示 `0`。這是欄位語意上的既有小落差，不影響資料正確性，記錄於此供未來評估是否一併修正。
- 已額外 `grep` 確認全庫沒有 `new PageRequest(...)`/`Pageable.ofSize(...)` 等其他建構 `Pageable` 的寫法（全庫僅使用 `PageRequest.of(...)` 這一種工廠方法），故本輪窮舉 `PageRequest.of(...)` 呼叫點已涵蓋全部分頁參數建構路徑。
