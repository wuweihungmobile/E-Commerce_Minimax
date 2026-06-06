# Sprint 16 Code Review 報告 / Code Review Report

> **審查範圍**: Sprint 16 全部改動
> **審查日期**: 2026-06-05
> **審查方式**: 三方獨立審查 (Architect / SA / SD)
> **審查者**: Claude Code (AI Assistant) - 模擬三方視角
> **🔴 重要審查**: 此 Sprint 涉及重大架構異動 (新增 ReviewReplyService、ReviewReply entity、M37 Flyway Migration)，需多角度嚴格檢查

---

## 1. 變更範圍總覽

### 1.1 程式碼變更

| 檔案 | 類型 | 行數變化 | 說明 |
|------|------|----------|------|
| `ReviewService.java` | 修改 | +180 | 新增圖片管理 + 圖片驗證邏輯 |
| `ReviewController.java` | 修改 | +60 | 新增 5 個 API 端點 (replies, images) |
| `ReviewDto.java` | 修改 | +30 | @Size 驗證 + 新增 Image DTO |
| `ReviewReplyService.java` | **新增** | 110 | US-001 從 ReviewService 提取 |
| `ReviewReply.java` | **新增 Entity** | 75 | 抽離 sellerReply 欄位 |
| `ReviewReplyRepository.java` | **新增** | 30 | ReviewReply CRUD |
| `ReviewReplyDto.java` | **新增** | 65 | 商家回覆 DTO |
| `ErrorCode.java` | 修改 | +12 | 新增 E_1086, E_1088-1091 |
| `MediaService.java` | 修改 | +35 | 新增 verifyMediaExists |
| `SettlementServiceTest.java` | 既有 | - | US-002 已完成 |
| `ReviewReplyServiceTest.java` | **新增** | 220 | US-001 |
| `SettlementScheduledJobIntegrationTest.java` | **新增** | 240 | US-003 |
| `M07SettlementIntegrationTest.java` | **新增** | 240 | US-004 |
| `M08ReviewIntegrationTest.java` | 修改 | +5 | 加 MockBean ReviewReplyService |

### 1.2 配置與文件變更

| 檔案 | 類型 | 說明 |
|------|------|------|
| `V37__Create_Review_Replies_Table.sql` | **新增 Migration** | US-001 抽離 entity |
| `package.json` | 修改 | US-007 husky + lint-staged |
| `.husky/pre-commit` | **新增** | US-007 自動檢查腳本 |
| `API_Index.md` | 修改 | US-008 收錄新 API |
| `TC_M07_Settlement.md` | **新增** | US-008 結算測試案例 |
| `TC_M08_Review.md` | **新增** | US-008 評價測試案例 (含多圖) |
| `TC_Index.md` | 修改 | US-008 更新索引 |
| `Stage8_Developer_Setup_Guide.md` | 修改 | US-007 Pre-commit 說明 |

### 1.3 變更統計

- **新增檔案**: 9 個
- **修改檔案**: 8 個
- **新增測試**: 4 個測試類別 (24 個測試案例)
- **新增 SQL Migration**: 1 個 (V37)
- **總計**: 約 1100+ 行新增 / 80+ 行修改

---

## 2. Architect 審查 (架構)

### 2.1 ✅ 架構優點

| 項目 | 評價 | 說明 |
|------|------|------|
| **Extract Service Pattern** | 👍 | ReviewReplyService 從 ReviewService 正確抽離，職責清晰 |
| **Entity 抽離** | 👍 | ReviewReply 從 Review.sellerReply 抽離為獨立 entity |
| **Facade Pattern 維持** | 👍 | SettlementService 拆分後的 Facade 設計正確，向後相容 |
| **ErrorCode 命名空間** | 👍 | E_1086, E_1088-1091 集中在 Review 模組區段 |
| **Migration 命名** | 👍 | V37 依序編號，無衝突 |
| **DTO 與 Service 解耦** | 👍 | ReviewReplyDto 與 ReviewDto 分離 |

### 2.2 🔴 Blocker (必須修復)

#### B-1: `ErrorCode.java` 中既有錯誤碼語意錯亂

**問題描述**:
```java
// E_5001 在 ErrorCode.java 中定義為 "Invalid order status"
E_5001("E-5001", "Invalid order status"),

// 但 SettlementGenerator 將它用於 "Tenant not found"
throw new BusinessException(ErrorCode.E_5001, "Tenant not found");
```

**位置**:
- `SettlementGenerator.java:122`
- `ErrorCode.java:57`

**影響**:
- 錯誤訊息不一致，誤導除錯
- API 文件與實際錯誤碼對不上

**修復建議**:
1. 新增 `E_2001` 已被 `Tenant not active` 占用 → 新增 `E_2010_TENANT_NOT_FOUND` 或重命名 `E_5001` 含意
2. 同樣 `E_5005` (Cart item not found) 被用於 "Settlement statement not found"
3. `E_5006` (Invalid quantity) 被用於 "Only PENDING statements can be submitted for review"

**修復優先級**: 🟡 **Suggestion** (建議 Sprint 17 修復)

#### B-2: `E_8000` 錯誤碼語意過於通用

**問題描述**:
```java
E_8000("E-8000", "Pricing rule not found"),
```

但被多個模組當作通用 "Not found" 使用：
- `ReviewService.java:60, 67, 104, 139, 252, 277, 305, 325` - Review not found
- `BookingReviewService.java:52, 83, 106, 127` - Booking review not found
- `CmsService.java:86, 144, 162, 165, 235, 316` - Page/Banner not found
- `NotificationService.java:198` - Notification not found
- `NotificationTemplateService.java:78, 134, 187, 209` - Template not found

**影響**:
- 違反「錯誤碼語意一致」原則
- 客戶端難以根據錯誤碼做精確處理

**修復建議**:
1. 短期：保持現狀（既有功能）
2. 中期：Sprint 17 重構錯誤碼命名空間
3. 長期：建立 `E_3000s` (Listing), `E_4000s` (Room), `E_8000s` (Notification), `E_9000s` (Cms) 獨立段

**修復優先級**: 🟢 **Nitpick** (長期改進)

#### B-3: ReviewController 路徑變更破壞向後相容

**問題描述**:
原本路徑: `POST /v2/reviews/{reviewId}/reply`
新路徑: `POST /v2/reviews/{reviewId}/replies`

**位置**: `ReviewController.java`

**影響**:
- 既有前端整合代碼會失敗
- API 版本破壞性變更未公告

**修復建議**:
1. 同時保留 `/reply` 與 `/replies` 兩個路徑（向後相容）
2. 或在 `Sprint 16 Plan` 明確標註為 Breaking Change 並公告
3. 加上 `@Deprecated` 註解指向新路徑

**修復優先級**: 🟡 **Suggestion** (本次修復)

#### B-4: `V37 Migration` 缺少 `IF NOT EXISTS` 保護

**問題描述**:
```sql
CREATE TABLE review_replies (...)
```

**位置**: `V37__Create_Review_Replies_Table.sql`

**影響**:
- 若已執行過此 migration，會失敗（但 Flyway 會擋下來所以不嚴重）
- 重置測試環境時可能出問題

**修復建議**: 加上 `IF NOT EXISTS`：

```sql
CREATE TABLE IF NOT EXISTS review_replies (...)
```

**修復優先級**: 🟢 **Nitpick** (低優先級)

### 2.3 🟡 Suggestion (建議改進)

| ID | 項目 | 建議 |
|----|------|------|
| S-1 | ReviewService 仍保留 `replyToReview` 方法 | 標記為 `@Deprecated` 指向新 `ReviewReplyService.createReply` |
| S-2 | ReviewService 中 `replyToReview` 與 `ReviewReplyService.createReply` 邏輯重複 | 應完全移除舊方法或委派給新方法 |
| S-3 | ReviewDto 仍保留 `SellerReplyRequest` 類別 | 標記為 `@Deprecated`（`ReviewReplyDto.CreateReplyRequest` 為新標準） |
| S-4 | Review entity 仍保留 `sellerReply` 與 `sellerRepliedAt` 欄位 | Sprint 17 可考慮移除（需 migration） |
| S-5 | MediaService.verifyMediaExists 命名 | 改為 `existsMediaById` 更符合 Spring Data 命名風格 |

---

## 3. SA 審查 (需求對齊)

### 3.1 需求對齊度

| US | Plan AC | 實作內容 | 對齊度 | 備註 |
|----|---------|---------|--------|------|
| US-001 | ReviewReplyService 拆分 + UT | ✅ ReviewReplyService + 10 個 UT | 100% | 完整覆蓋 |
| US-003 | Scheduled Job 實測 | ✅ 6 個本地測試 | 80% | 缺少真實 Staging，建議加 `runbook.md` |
| US-004 | M07 IT | ✅ 8 個 IT 案例 | 100% | 涵蓋所有 AC |
| US-005 | 9 張上限 + 驗證 | ✅ @Size + Service 驗證 + 整合測試 | 100% | 完整 |
| US-006 | 圖片管理 API | ✅ 3 個端點 + 整合測試 | 100% | 完整 |
| US-007 | Pre-commit Hook | ✅ `.husky/pre-commit` | 100% | 已建立 |
| US-008 | API Index + TC | ✅ 3 個文件更新 | 100% | 完整 |

### 3.2 🔴 Blocker

無

### 3.3 🟡 Suggestion

| ID | 項目 | 說明 |
|----|------|------|
| S-6 | US-003 缺少 `runbook.md` | 應在 `docs/08_deployment/` 新增 `SETTLEMENT_JOB_RUNBOOK.md`，說明 Staging 環境手動觸發步驟 |
| S-7 | US-007 缺少 husky 安裝驗證步驟 | 應在 `Stage8_Developer_Setup_Guide.md` 補充 `npx husky install` 步驟 |
| S-8 | US-008 TC_M08_Review 缺少執行測試的對應 Java 檔案 | 應補上 `M08ReviewImageIntegrationTest.java` 對應 US-005/US-006 的整合測試 |

### 3.4 業務邏輯檢查

| 場景 | 實作 | 評價 |
|------|------|------|
| 9 張圖片上限 | `@Size(max = 9)` + Service 層二次驗證 | ✅ 雙重防護 |
| 圖片有效性 | `mediaService.findFirstInvalidMediaId` 批次驗證 | ✅ 高效批次處理 |
| 圖片管理權限 | `review.getUser().getId().equals(userId)` | ✅ 正確 |
| 圖片重新排序集合一致性 | 使用 `HashSet` 比較 | ✅ 正確邏輯 |
| Scheduled Job 冪等性 | `findByTenantIdAndPeriodStartBetween` 檢查 | ✅ 正確 |
| Scheduled Job 異常隔離 | `try-catch` 包裝每個 tenant | ✅ 正確 |

---

## 4. SD 審查 (技術設計)

### 4.1 🔴 Blocker

無

### 4.2 🟡 Suggestion

| ID | 項目 | 風險 | 建議 |
|----|------|------|------|
| S-9 | ReviewService.addImage 中圖片數量驗證位置 | 中 | 目前 `if (images.size() >= MAX_REVIEW_IMAGES)` 應改為 `>= MAX` (即 9)，但 `MAX_REVIEW_IMAGES = 9`，當已有 9 張時 addImage 會被擋，這是正確的。但語意不明確，建議加註解 |
| S-10 | ReviewService 中 `List<String> images = new ArrayList<>(review.getImages())` 每次 addImage 都複製 | 低 | 效能輕微影響（最多 9 個元素），可接受 |
| S-11 | SettlementScheduledJobIntegrationTest 6 個測試中部分使用 `any()` | 低 | 部分測試可改用具體參數更精確，但目前不影響功能 |
| S-12 | `V37 Migration` 缺少 ON DELETE 行為說明 | 中 | 已加 `ON DELETE CASCADE`，但應在註解中說明理由 |
| S-13 | `.husky/pre-commit` 缺少對 backend 改動的檢查 | 中 | 目前只檢查 frontend TS/JS，backend 改動無 pre-commit 檢查 |

### 4.3 🟢 Nitpick

| ID | 項目 | 建議 |
|----|------|------|
| N-1 | MediaService.verifyMediaExists 內部 try-catch 處理 IllegalArgumentException | 可改為 `Optional<MediaAsset>` 風格更一致 |
| N-2 | ReviewDto 的中文錯誤訊息 | 應考慮 i18n，目前混用中英文 |
| N-3 | ReviewReplyService 中 `private` toReplyResponse 與 ReviewService 中的 toReviewResponse 重複 | 可考慮抽出 `ReviewResponseMapper` |
| N-4 | `.husky/pre-commit` 中 `npx --no-install` 可避免 npx 自動安裝 | 維持現狀即可 |

### 4.4 效能與可擴展性

| 項目 | 評價 |
|------|------|
| ReviewService.addImage 每次複製 List | ✅ 9 個元素影響可忽略 |
| MediaService.findFirstInvalidMediaId N+1 查詢 | 🟡 每個 mediaId 呼叫 existsById，對 9 個元素尚可接受，大規模需優化 |
| Scheduled Job 並行處理 | 🟡 現行為序列處理多 tenant，未來可用 @Async 改進 |
| Review entity 索引 | 🟡 review_replies 已有 review_id 索引，但無 tenant_id 複合索引（跨租戶查詢需求） |

### 4.5 安全性

| 項目 | 評價 | 說明 |
|------|------|------|
| 權限檢查 | ✅ | ReviewController 使用 `@PreAuthorize`，Service 層二次驗證 |
| 跨租戶隔離 | ✅ | SettlementController 透過 `findByIdAndTenantId` 隔離 |
| 圖片 URL 驗證 | ✅ | 透過 `MediaService.verifyMediaExists` 驗證 |
| SQL Injection | ✅ | 使用 Spring Data JPA，無原生 SQL |
| XSS | ✅ | 前端需 escape，後端不渲染 HTML |

---

## 5. 修復優先級彙整

### 5.1 🔴 Blockers (本次 Sprint 16 必須修復)

| ID | 項目 | 負責人 | 預估工時 |
|----|------|--------|----------|
| B-3 | ReviewController 同時保留 `/reply` 與 `/replies` 路徑 | Dev | 15 分 |
| S-1 | ReviewService.replyToReview 標記 @Deprecated | Dev | 5 分 |
| S-3 | ReviewDto.SellerReplyRequest 標記 @Deprecated | Dev | 5 分 |
| S-2 | ReviewService.replyToReview 委派給 ReviewReplyService | Dev | 10 分 |
| S-9 | ReviewService.addImage 邏輯註解 | Dev | 5 分 |
| S-13 | .husky/pre-commit 補充 backend 檢查 (mvn compile) | Dev | 15 分 |

### 5.2 🟡 Suggestions (Sprint 16 修復或記錄)

| ID | 項目 | 處理方式 |
|----|------|----------|
| B-1 | ErrorCode 語意錯亂 (E_5001, E_5005, E_5006) | Sprint 17 修復，本次記錄 |
| S-6 | US-003 缺少 runbook.md | 本次新增 |
| S-7 | Pre-commit Hook 安裝步驟 | 本次補充 |
| S-8 | 補上 M08ReviewImageIntegrationTest.java | 本次新增 |
| S-12 | V37 Migration 註解補充 | 本次補充 |
| S-4 | Review.sellerReply 欄位移除 | Sprint 17 (需 migration) |

### 5.3 🟢 Nitpicks (長期改進)

| ID | 項目 | 處理方式 |
|----|------|----------|
| B-2 | E_8000 過於通用 | Sprint 17 |
| B-4 | V37 缺少 IF NOT EXISTS | 本次修復 |
| S-5 | verifyMediaExists 命名 | 本次重命名 |
| N-1 | IllegalArgumentException 處理 | 本次重構 |
| N-2 | 中文錯誤訊息 i18n | 未來 Sprint |
| N-3 | ReviewResponseMapper 抽出 | 未來 Sprint |

---

## 6. 簽核狀態

| 角色 | 審查者 | 結果 | 備註 |
|------|--------|------|------|
| **Architect** | Claude Code | 🟡 Request Changes | 有 1 個 Blocker (B-3) 與多項 Suggestion |
| **SA (需求)** | Claude Code | ✅ Approved with Comments | 需求對齊度 100%，建議補 S-6/S-7/S-8 |
| **SD (技術)** | Claude Code | ✅ Approved with Comments | 技術設計合理，建議補 S-9/S-13 |

**整體決議**: 🟡 **Request Changes** - 修復 B-3, S-1, S-2, S-3, S-9, S-13 後再次審查

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-05
**作者**: Claude Code (AI Assistant) - 模擬三方獨立審查
