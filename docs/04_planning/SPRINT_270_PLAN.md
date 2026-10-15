# Sprint 270 Plan — 測試固件 Shadow 欄位 nullable 一致性殘留範圍（DEF-332）

**Sprint**: Sprint 270
**日期**: 2026-10-15

## 1. 範圍與起點

[SPRINT_269_PLAN.md](SPRINT_269_PLAN.md) 完成後，依使用者拍板順序進入第二項：DEF-332 殘留範圍。

DEF-332 原記錄：Sprint 237 發現測試庫 `listings.tenant_id`／`owner_id` 的 Hibernate 影子欄位（`insertable = false, updatable = false`）未標 `nullable = false`，會被 Hibernate 取用「預設可為 NULL」蓋掉 `@JoinColumn` 關聯本身的 `nullable = false`，導致只設影子欄位、未設關聯的測試固件默默把 NOT NULL 欄位存成 NULL。Sprint 237 當時只處理了 `Listing.java`（`tenantId`／`ownerId`），並記錄「同型的其他實體（`Order`、`Booking`、`ShippingTemplate`…的 `tenantId`／`userId` 影子欄位）測試庫同樣可能存成 NULL，是否比照處理待決定」。

## 2. 全庫掃描與逐欄位驗證

### 2.1 找出所有 shadow 欄位

`grep -rl "insertable = false" backend/src/main/java/.../domain/model/` 找到 **22 個實體檔案、共 34 個 shadow 欄位**（`@Column(insertable = false, updatable = false)` 搭配對應 `@JoinColumn` 關聯的唯讀鏡射欄位）。

### 2.2 逐欄位對照 Flyway migration 的真實 NOT NULL 狀態

**不可只比照欄位名稱是否為 `tenantId`／`userId` 就機械套用**——必須逐欄位讀對應 `CREATE TABLE`／`ALTER TABLE` migration 確認真實 nullable 狀態，因為部分欄位即使命名相似，語意上本該是選填：

- `Order.settledStatementId`／`Booking.settledStatementId`：`V83`／`V91` 新增時即為可為 NULL（訂單/訂房建立時尚未結算，結算後才回填）
- `MediaCategory.parentId`：可為 NULL（根分類無上層）
- `ArticleVersion.categoryId`／`createdById`：可為 NULL
- `MediaAsset.categoryId`：可為 NULL（分類為選填）
- `OrderItem.skuId`：可為 NULL（非規格化商品無 SKU）
- `ProductInventory.availableQty`：非 FK 影子欄位，是資料庫端計算欄位（`total_qty - reserved_qty`），與本缺口無關，排除

逐一 `grep` 對應 migration 確認後，**24 個欄位、分布於 19 個實體檔案**是真正的候選（真實 schema 為 `NOT NULL`，且對應 `@JoinColumn` 本身也已是 `nullable = false`，只差 shadow `@Column` 缺這個屬性）：

| 實體 | 欄位 |
|---|---|
| `TenantFeatureToggle` | `tenantId` |
| `OrderItem` | `orderId`、`listingId` |
| `Order` | `tenantId`、`userId` |
| `Booking` | `tenantId`、`userId`、`roomListingId` |
| `SettlementStatement` | `tenantId` |
| `PricingRule` | `tenantId` |
| `CreditNote` | `tenantId` |
| `Message` | `conversationId` |
| `FaqArticle` | `categoryId` |
| `ProductSku` | `productListingId` |
| `BookingReview` | `bookingId`、`userId` |
| `ArticleVersion` | `articleId`、`tenantId` |
| `KnowledgeArticle` | `tenantId`、`categoryId`、`authorId` |
| `KnowledgeArticleTag` | `articleId` |
| `ReviewReply` | `reviewId` |
| `MediaCategory` | `tenantId` |
| `Review` | `listingId`、`userId` |
| `ShippingTemplate` | `tenantId` |
| `PromoCode` | `tenantId` |

另外排查所有 migration 的 `DROP NOT NULL` 語句，確認上述 28 個欄位自建立以來從未被後續 migration 放寬為可為 NULL（`V76`／`V91` 的兩筆 `DROP NOT NULL` 分別作用於 `promo_code_usages.order_id`／`adjustment_statements.order_id`，與本次欄位無關）。

## 3. 做法

比照 Sprint 237 `Listing.java` 的既有修法：19 個檔案的 24 個目標 shadow `@Column` 補上 `nullable = false`（共 28 處 `@Column` 標註，因多個實體各有多個目標欄位），並在每處加上簡短註解交叉引用 Sprint 237 的說明（不逐處重複完整段落）。以 Python 腳本機械套用（22 個 entity 檔案逐一比對 `@Column(name = "X", insertable = false, updatable = false)` → 加 `, nullable = false)`），每個欄位替換前驗證恰好命中 1 次，避免誤觸同檔案內其他應排除的 shadow 欄位（如 `OrderItem.skuId`、`Order.settledStatementId`）。

## 4. 實作內容

| 檔案 | 變更欄位 |
|---|---|
| `tenant/TenantFeatureToggle.java` | `tenantId` |
| `order/OrderItem.java` | `orderId`、`listingId` |
| `order/Order.java` | `tenantId`、`userId` |
| `order/Booking.java` | `tenantId`、`userId`、`roomListingId` |
| `settlement/SettlementStatement.java` | `tenantId` |
| `room/PricingRule.java` | `tenantId` |
| `settlement/CreditNote.java` | `tenantId` |
| `chat/Message.java` | `conversationId` |
| `faq/FaqArticle.java` | `categoryId` |
| `product/ProductSku.java` | `productListingId` |
| `review/BookingReview.java` | `bookingId`、`userId` |
| `knowledge/ArticleVersion.java` | `articleId`、`tenantId` |
| `knowledge/KnowledgeArticle.java` | `tenantId`、`categoryId`、`authorId` |
| `knowledge/KnowledgeArticleTag.java` | `articleId` |
| `review/ReviewReply.java` | `reviewId` |
| `media/MediaCategory.java` | `tenantId` |
| `review/Review.java` | `listingId`、`userId` |
| `logistics/ShippingTemplate.java` | `tenantId` |
| `promo/PromoCode.java` | `tenantId` |

## 5. 測試

依 CLAUDE.md「開發-編譯-測試循環」規則：

1. `mvn -o clean test-compile`：`BUILD SUCCESS`。
2. `make validate-schema`（`ddl-auto=validate` + Flyway 對乾淨 PostgreSQL 啟動 backend，複製雲端 e2e 啟動條件，本地 act 抓不到的漏洞）：✅ **backend 啟動成功 → entity 與 Flyway schema 對齊，無漂移**。確認新增的 28 處 `nullable = false` 與真實資料庫完全一致，沒有任何一個誤判（否則 Hibernate 啟動時的 schema 驗證會直接失敗退出）。
3. 全量 `mvn -o clean verify`（本次變更屬生產程式碼——entity 映射——依 [[sprint-full-regression-policy]] 需跑全量，非純測試修補；與 Sprint 271 的通知管道變更一併驗證，詳見 [SPRINT_271_PLAN.md](SPRINT_271_PLAN.md) §5）：**單元測試 2237/0 失敗、整合測試 853/0 失敗、Checkstyle 0 違規、PMD 通過，`BUILD SUCCESS`**。第一次執行因與同時段 `git push`（Sprint 269）內建的 `validate-schema` 腳本共用 `backend/target/` 而出現檔案損毀式失敗（`OrderStateLog.class` 的 `ClassFormatError`，與本次變更無關），確認無其他 Maven 行程後重跑即恢復正常。

## 6. 範圍外與待使用者決定

- 本輪僅處理「真實 schema 為 NOT NULL 但 Hibernate shadow 欄位缺少對應標註」的欄位。`ProductInventory.availableQty` 等非 FK 性質的 `insertable=false` 欄位（資料庫計算欄位）與本缺口無關，不在範圍內。
- 下一步依使用者已拍板的順序，進入 DEF-317（通知管道誠實化）。

## 7. Velocity 紀錄

本輪為 1 項缺口修復的範圍完整化（19 個實體、28 處欄位標註），非新功能開發，不納入傳統 Story Point 計算。

## 8. 下一步／Action Items

1. 全量 `mvn -o clean verify` 確認無回歸。
2. commit＋push，等待雲端 CI 確認全綠。
3. 回填 push 狀態與雲端 CI 結果。
4. 開始 Sprint 271（DEF-317）。
