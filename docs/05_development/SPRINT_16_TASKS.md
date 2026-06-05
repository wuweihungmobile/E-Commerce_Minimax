# Sprint 16 任務分解 / Sprint 16 Tasks

> **Sprint 編號**: Sprint 16
> **期間**: 2026-07-13 ~ 2026-07-24 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-04
> **基於**: [SPRINT_16_PLAN.md](../04_planning/SPRINT_16_PLAN.md)

---

## 📋 Sprint 16 任務概覽

| US ID | 標題 | SP | 負責人 | 狀態 |
|-------|------|-----|--------|------|
| US-001 | ReviewReplyService 單元測試 | 1 | Dev | ⏳ PLANNED |
| US-002 | SettlementService 單元測試 | 2 | Dev | ⏳ PLANNED |
| US-003 | Scheduled Job 實測驗證 | 0.5 | Dev | ⏳ PLANNED |
| US-004 | M07SettlementIntegrationTest | 1 | Dev/QA | ⏳ PLANNED |
| US-005 | 多圖評價 9 張上限 + 驗證 | 3 | Dev | ⏳ PLANNED |
| US-006 | 多圖評價 圖片管理 API | 3 | Dev | ⏳ PLANNED |
| US-007 | Pre-commit Hook ESLint+tsc | 0.5 | Dev | ⏳ PLANNED |
| US-008 | API Index + TC 文件更新 | 1 | Dev/QA | ⏳ PLANNED |
| **合計** | | **12 SP** | | |

---

## 🔴 Day 1 必修任務 - Release 補做

> 🔴 **警告**：跳過 Sprint 15 Release 違反 AISDLC 流程，Day 1 上午必須補上！

#### Task R-1: 確認 release/v2026.06.04-01 分支狀態 (15 min)
- 檢查 CI 最後狀態
- 確認無未解決的 conflict

#### Task R-2: 合併 release/v2026.06.04-01 到 main (15 min)
```bash
git checkout main
git merge --no-ff release/v2026.06.04-01
git push origin main
```

#### Task R-3: 建立 Git tag v2026.06.04-01 (5 min)
```bash
git tag -a v2026.06.04-01 -m "Release v2026.06.04-01 - Sprint 15 (M08 評價 Phase 2 + M07 結算)"
git push origin v2026.06.04-01
```

#### Task R-4: 更新部署文件 (15 min)
- 更新 `docs/08_deployment/RELEASE_NOTES.md`
- 更新 `docs/08_deployment/CHANGELOG.md`

---

## 🔴 US-001: ReviewReplyService 單元測試 (1 SP)

### 任務清單

#### 1.1 建立測試類別
- [ ] Task 1.1.1: 建立 `ReviewReplyServiceTest.java` (15 min)
  - 位於 `backend/src/test/java/com/nextkey/ecommerce/core/review/`
  - 使用 Mockito + JUnit 5
  - `@ExtendWith(MockitoExtension.class)`

#### 1.2 createReply 測試
- [ ] Task 1.2.1: 測試成功建立回覆 (20 min)
  - 模擬：ReviewRepository 找到 review
  - 模擬：existsByReviewId 返回 false
  - 預期：回傳 ReviewReply，呼叫 save
- [ ] Task 1.2.2: 測試重複回覆拋出例外 (15 min)
  - 模擬：existsByReviewId 返回 true
  - 預期：拋出 BusinessException E1086
- [ ] Task 1.2.3: 測試 review 不存在拋出例外 (15 min)
  - 模擬：ReviewRepository.findById 返回 Optional.empty()
  - 預期：拋出 NotFoundException

#### 1.3 getRepliesByReviewId 測試
- [ ] Task 1.3.1: 測試空列表 (10 min)
  - 模擬：findByReviewId 返回空 List
  - 預期：回傳空 List
- [ ] Task 1.3.2: 測試單筆回覆 (10 min)
  - 模擬：回傳 1 筆
  - 預期：回傳 1 筆
- [ ] Task 1.3.3: 測試多筆回覆按時間排序 (10 min)

#### 1.4 邊界測試
- [ ] Task 1.4.1: 測試空字串 content (10 min)
- [ ] Task 1.4.2: 測試超長 content (10 min)
- [ ] Task 1.4.3: 測試 null content (10 min)

**預估工時**: 2 小時
**覆蓋率目標**: >= 80%

---

## 🟡 US-002: SettlementService 單元測試 (2 SP)

### 任務清單

#### 2.1 環境準備
- [ ] Task 2.1.1: 建立 `SettlementServiceTest.java` (20 min)
  - 位於 `backend/src/test/java/com/nextkey/ecommerce/core/settlement/`
  - Mock 依賴：SettlementStatementRepository, CreditNoteRepository, OrderRepository, RefundRepository

#### 2.2 calculateSettlementAmount 測試
- [ ] Task 2.2.1: 測試無退款場景 (20 min)
  - GMV = 10000, Refund = 0
  - 平台抽成 = 10% = 1000
  - 結算金額 = 9000
- [ ] Task 2.2.2: 測試部分退款場景 (20 min)
  - GMV = 10000, Refund = 2000
  - 結算金額 = 10000 - 2000 - 800 = 7200
- [ ] Task 2.2.3: 測試全額退款場景 (15 min)
  - GMV = 10000, Refund = 10000
  - 結算金額 = 0
- [ ] Task 2.2.4: 測試 0 元訂單 (15 min)
  - GMV = 0, Refund = 0
  - 結算金額 = 0
- [ ] Task 2.2.5: 測試超大金額邊界 (15 min)
  - GMV = Long.MAX_VALUE

#### 2.3 generateWeeklyStatements 測試
- [ ] Task 2.3.1: 測試單租戶 (20 min)
  - 模擬：1 個 tenant, 5 筆訂單
  - 預期：1 個 SettlementStatement
- [ ] Task 2.3.2: 測試多租戶 (20 min)
  - 模擬：3 個 tenants
  - 預期：3 個 SettlementStatement
- [ ] Task 2.3.3: 測試冪等性（重複執行不重複建立）(20 min)
  - 模擬：已有 PENDING 結算單
  - 預期：跳過建立
- [ ] Task 2.3.4: 測試無訂單租戶跳過 (15 min)
  - 模擬：tenant 沒有訂單
  - 預期：不建立結算單

#### 2.4 狀態機測試
- [ ] Task 2.4.1: 測試 PENDING → PENDING_REVIEW (15 min)
- [ ] Task 2.4.2: 測試 PENDING_REVIEW → APPROVED (15 min)
- [ ] Task 2.4.3: 測試 PENDING_REVIEW → REJECTED (15 min)
- [ ] Task 2.4.4: 測試無效狀態轉換拋出例外 (15 min)
  - PENDING → APPROVED (跳過審核)
  - REJECTED → APPROVED (不可逆)

#### 2.5 整合驗證
- [ ] Task 2.5.1: 執行 `mvn test -Dtest=SettlementServiceTest` 確認綠燈 (10 min)
- [ ] Task 2.5.2: 確認覆蓋率 >= 80% (5 min)

**預估工時**: 5 小時
**覆蓋率目標**: >= 80%

---

## 🟡 US-003: Scheduled Job 實測驗證 (0.5 SP)

### 任務清單

#### 3.1 Staging 環境準備
- [ ] Task 3.1.1: 確認 Staging 環境可訪問 (10 min)
- [ ] Task 3.1.2: 確認應用程式有測試觸發機制 (10 min)
  - 環境變數 `SETTLEMENT_TRIGGER_MODE=MANUAL` 開啟
  - 或呼叫內部測試 endpoint

#### 3.2 手動觸發測試
- [ ] Task 3.2.1: 準備測試資料 (15 min)
  - 建立 2 個 tenant, 每個 tenant 3 筆訂單
  - 1 筆部分退款
- [ ] Task 3.2.2: 觸發 generateWeeklyStatements (10 min)
- [ ] Task 3.2.3: 驗證 settlement_statements 表 (15 min)
  - 2 個 tenant 各 1 筆結算單
  - 金額計算正確
- [ ] Task 3.2.4: 驗證多租戶隔離 (10 min)
  - tenant A 的結算單不包含 tenant B 的訂單

#### 3.3 異常場景
- [ ] Task 3.3.1: 測試無訂單租戶 (10 min)
- [ ] Task 3.3.2: 測試重複觸發冪等性 (10 min)

**預估工時**: 1.5 小時

---

## 🟡 US-004: M07SettlementIntegrationTest (1 SP)

### 任務清單

#### 4.1 建立測試類別
- [ ] Task 4.1.1: 建立 `M07SettlementIntegrationTest.java` (20 min)
  - 位於 `backend/src/test/java/com/nextkey/ecommerce/integration/`
  - 使用 `@WebMvcTest(SettlementController.class)`
  - `@Import(IntegrationTestConfiguration.class)`

#### 4.2 GET /v2/settlements 測試
- [ ] Task 4.2.1: StoreOwner 查詢自己租戶的結算單 (20 min)
- [ ] Task 4.2.2: 跨租戶禁止測試 (15 min)
- [ ] Task 4.2.3: 分頁測試 (10 min)

#### 4.3 GET /v2/settlements/{id} 測試
- [ ] Task 4.3.1: 查詢存在的結算單 (10 min)
- [ ] Task 4.3.2: 查詢不存在的結算單 404 (10 min)
- [ ] Task 4.3.3: 跨租戶查詢禁止 403 (10 min)

#### 4.4 Admin 審核 API 測試
- [ ] Task 4.4.1: PUT /admin/settlements/{id}/submit (15 min)
- [ ] Task 4.4.2: PUT /admin/settlements/{id}/approve (15 min)
- [ ] Task 4.4.3: PUT /admin/settlements/{id}/reject 帶 reason (15 min)
- [ ] Task 4.4.4: 非 Admin 角色呼叫 403 (10 min)
- [ ] Task 4.4.5: GET /admin/settlements/pending 列表 (10 min)

**預估工時**: 2.5 小時

---

## 🟢 US-005: 多圖評價 9 張上限 + 驗證 (3 SP)

### 任務清單

#### 5.1 Bean Validation
- [ ] Task 5.1.1: 更新 `ReviewDto.java` 加入 `@Size` 驗證 (20 min)
  - `CreateReviewRequest.images` 加 `@Size(max = 9, message = "最多 9 張圖片")`
  - `UpdateReviewRequest.images` 加 `@Size(max = 9)`

#### 5.2 Service 層驗證
- [ ] Task 5.2.1: 更新 `ReviewService.createReview()` 加入驗證 (30 min)
  - 呼叫 `MediaService.verifyMediaExists()` 驗證每個 URL
  - 拋出 `BusinessException E1088` (圖片無效)
- [ ] Task 5.2.2: 新增常數 `MAX_REVIEW_IMAGES = 9` (5 min)
- [ ] Task 5.2.3: 整合 MediaService 注入 (15 min)
  - 在 `ReviewService` constructor 加入 `MediaService`

#### 5.3 錯誤碼定義
- [ ] Task 5.3.1: 確認 ErrorCode E1088 已存在或新增 (10 min)
  - 訊息：`E1088 評價圖片最多 9 張，目前 {actual} 張`
  - 訊息：`E1089 評價圖片無效: {imageId}`

#### 5.4 測試
- [ ] Task 5.4.1: 整合測試 - 上傳 0 張圖片成功 (15 min)
- [ ] Task 5.4.2: 整合測試 - 上傳 9 張圖片成功 (15 min)
- [ ] Task 5.4.3: 整合測試 - 上傳 10 張圖片 400 錯誤 (15 min)
- [ ] Task 5.4.4: 整合測試 - 無效 mediaId 400 錯誤 (15 min)
- [ ] Task 5.4.5: 單元測試 - ReviewService 圖片驗證邏輯 (30 min)

**預估工時**: 3 小時

---

## 🟢 US-006: 多圖評價 圖片管理 API (3 SP)

### 任務清單

#### 6.1 Service 層
- [ ] Task 6.1.1: `ReviewService.addImage(reviewId, imageUrl, userId)` (30 min)
  - 權限驗證：Review.userId == userId
  - 數量驗證：當前 + 1 <= 9
  - 媒體驗證：MediaService.verifyMediaExists()
- [ ] Task 6.1.2: `ReviewService.removeImage(reviewId, imageIndex, userId)` (30 min)
  - 權限驗證
  - 從 list 中移除指定 index
- [ ] Task 6.1.3: `ReviewService.reorderImages(reviewId, newOrder, userId)` (30 min)
  - 權限驗證
  - 重新排序 list

#### 6.2 Controller 層
- [ ] Task 6.2.1: `POST /v2/reviews/{id}/images` (30 min)
  - 請求：AddImageRequest { imageUrl }
  - 權限：@PreAuthorize 評價本人
- [ ] Task 6.2.2: `DELETE /v2/reviews/{id}/images/{imageIndex}` (30 min)
  - 權限：@PreAuthorize 評價本人
- [ ] Task 6.2.3: `PUT /v2/reviews/{id}/images/order` (30 min)
  - 請求：ReorderImagesRequest { imageUrls: [...] }
  - 權限：@PreAuthorize 評價本人

#### 6.3 測試
- [ ] Task 6.3.1: 整合測試 - 新增圖片成功 (20 min)
- [ ] Task 6.3.2: 整合測試 - 新增至 10 張 400 (15 min)
- [ ] Task 6.3.3: 整合測試 - 刪除圖片成功 (15 min)
- [ ] Task 6.3.4: 整合測試 - 重新排序成功 (15 min)
- [ ] Task 6.3.5: 整合測試 - 他人操作 403 (15 min)
- [ ] Task 6.3.6: 單元測試 - ReviewService 圖片管理 (30 min)

**預估工時**: 5 小時

---

## 🟢 US-007: Pre-commit Hook ESLint + tsc (0.5 SP)

### 任務清單

#### 7.1 套件安裝
- [ ] Task 7.1.1: 安裝 husky 與 lint-staged (10 min)
  ```bash
  cd frontend
  npm install --save-dev husky lint-staged
  ```
- [ ] Task 7.1.2: 初始化 husky (5 min)
  ```bash
  npx husky init
  ```

#### 7.2 配置
- [ ] Task 7.2.1: 在 package.json 加入 lint-staged 配置 (10 min)
  ```json
  "lint-staged": {
    "*.{ts,tsx}": ["eslint --fix", "tsc --noEmit"]
  }
  ```
- [ ] Task 7.2.2: 建立 `.husky/pre-commit` (5 min)
  ```bash
  npx lint-staged
  ```

#### 7.3 驗證
- [ ] Task 7.3.1: 故意製造 ESLint 錯誤測試 (10 min)
  - 加入 `var x = 1;` 然後 commit
  - 預期：commit 被阻擋
- [ ] Task 7.3.2: 故意製造 TS 錯誤測試 (10 min)
  - 加入型別錯誤然後 commit
  - 預期：commit 被阻擋

#### 7.4 文件
- [ ] Task 7.4.1: 更新 `Stage8_Developer_Setup_Guide.md` (10 min)

**預估工時**: 1 小時

---

## 🟢 US-008: API Index + TC 文件更新 (1 SP)

### 任務清單

#### 8.1 API_Index.md 更新
- [ ] Task 8.1.1: 收錄 Sprint 15 新增 API (20 min)
  - POST /v2/reviews/{id}/replies
  - GET /v2/reviews/{id}/replies
  - PUT /v2/reviews/{id}/handle
  - GET /v2/settlements
  - GET /v2/settlements/{id}
  - PUT /v2/settlements/{id}/submit
  - PUT /v2/admin/settlements/{id}/approve
  - PUT /v2/admin/settlements/{id}/reject
  - GET /v2/admin/settlements/pending
- [ ] Task 8.1.2: 收錄 Sprint 16 新增 API (15 min)
  - POST /v2/reviews/{id}/images
  - DELETE /v2/reviews/{id}/images/{imageIndex}
  - PUT /v2/reviews/{id}/images/order

#### 8.2 TC_M07_Settlement.md 新建
- [ ] Task 8.2.1: 建立測試案例文件 (30 min)
  - 結算單列表查詢
  - 結算單詳情查詢
  - 結算單提交審核
  - 結算單批准/駁回
  - 權限測試

#### 8.3 TC_M08_Review.md 擴展
- [ ] Task 8.3.1: 加入多圖評價測試案例 (20 min)
  - 9 張上限驗證
  - 圖片 CRUD
  - 圖片排序
  - 權限測試

**預估工時**: 2 小時

---

## 📊 Sprint 16 工作量統計

| US | 任務數 | 預估工時 | SP |
|----|--------|----------|-----|
| US-001 | 8 | 2h | 1 |
| US-002 | 14 | 5h | 2 |
| US-003 | 7 | 1.5h | 0.5 |
| US-004 | 11 | 2.5h | 1 |
| US-005 | 10 | 3h | 3 |
| US-006 | 12 | 5h | 3 |
| US-007 | 8 | 1h | 0.5 |
| US-008 | 6 | 2h | 1 |
| Release 補做 | 4 | 1h | 0 |
| **合計** | **80** | **23h** | **12 SP** |

---

## 🔄 Sprint 16 工作流程

### 開發順序

1. **Day 1 上午** - 🔴 Release 補做（R-1 ~ R-4）
2. **Day 1 下午** - US-001 (ReviewReplyService UT)
3. **Day 2** - US-002 (SettlementService UT) - 大部分時間
4. **Day 3 上午** - US-002 收尾
5. **Day 3 下午** - US-003 (Scheduled Job 實測)
6. **Day 4** - US-004 (M07 IT) + US-007 (Pre-commit Hook)
7. **Day 5-6** - US-005 (多圖 9 張上限)
8. **Day 7-8** - US-006 (圖片管理 API)
9. **Day 9** - US-008 (文件更新)
10. **Day 10** - Buffer + Sprint 16 Review

### 每日進度追蹤

| 日期 | 目標完成 |
|------|----------|
| Day 1 | Release 補做 + US-001 (50%) |
| Day 2 | US-001 (50%) + US-002 (50%) |
| Day 3 | US-002 (50%) + US-003 |
| Day 4 | US-004 + US-007 |
| Day 5 | US-005 (50%) |
| Day 6 | US-005 (50%) + 整合測試 |
| Day 7 | US-006 (50%) |
| Day 8 | US-006 (50%) + 整合測試 |
| Day 9 | US-008 文件 |
| Day 10 | Buffer + Review |

---

## ✅ Sprint 16 Definition of Done

- [ ] 所有 8 個 User Stories 的驗收標準 (AC) 完成
- [ ] `mvn compile` 編譯通過
- [ ] `mvn test` 單元測試與整合測試全部通過
- [ ] 單元測試覆蓋率（新代碼）>= 80%
- [ ] API_Index.md 與 TC 文件更新完成
- [ ] Pre-commit hook 正常運作
- [ ] Scheduled Job Staging 實測通過
- [ ] Sprint 16 Review 文件產生

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-04
**最後更新**: 2026-06-04
**Sprint 16 狀態**: 📋 **PLANNING COMPLETED** - 待團隊 Review 後開始執行
