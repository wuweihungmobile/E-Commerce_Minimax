# Release Notes - v2026.07.03-01

**發布日期**: 2026-07-03
**發布類型**: Minor（品質強化 + 新功能 + 技術債清理）
**Sprint**: Sprint 18
**Git Tag**: `v2026.07.03-01`
**基於 Commit**: `7411513` (docs(sprint18): SPRINT_18_TASKS v2.0)
**專案**: E-Commerce Platform (B2B2C Multi-tenant)

---

## 🎯 發布摘要

Sprint 18 完成 **Flyway 正式啟用**（告別 Hibernate auto-update 風險）、**M08 評價多維度搜尋** 新功能、以及史無前例的 **ErrorCode 全面遷移**（46 處誤用修正，14 個新錯誤碼）。同時建立 Docker 管理政策與 PMD/Checkstyle 強化，程式碼品質全面提升。

### 關鍵指標

| 項目 | 數值 |
|------|------|
| User Stories 完成 | 8 / 8（100%，含 3 Buffer US）|
| Story Points | 19 SP（規劃 12 + Buffer 7）|
| 達成率 | 158% |
| 測試狀態 | 555 tests, 0 Failures, 0 Errors |
| ErrorCode 修正 | 46 處誤用修正，14 個新錯誤碼 |
| 新增 Migration | V38__Consolidate_Media_Assets_Schema.sql |
| CI 狀態 | ✅ 全部代碼檢查通過 |

---

## 新功能 ✨

### M08 評價系統 — 多維度搜尋（US-003）

#### 評價多維度搜尋 API

- **新增 API**: `GET /v2/reviews/listing/{listingId}/search`
- **搜尋維度**:
  - 關鍵字（標題/內容，不區分大小寫）
  - 評分範圍（`minRating` / `maxRating`，1-5）
  - 日期範圍（`startDate` / `endDate`，ISO-8601）
  - 是否有圖片（`hasImages`）
  - 是否有商家回覆（`hasReply`）
  - 排序選項（`createdAt` / `rating` / `helpfulCount`，`ASC` / `DESC`）
  - 分頁支援（`page` / `size`，上限 50）
- **新增 DTO**: `ReviewSearchCriteria.java`（含 `SortBy` / `SortDir` enum）
- **新增測試**: `ReviewServiceSearchTest` — 14 個單元測試

---

## 技術改進 🔧

### Flyway 正式啟用（US-001）

- **啟用 Flyway**: `application.yml` 設定 `spring.flyway.enabled: true`
- **停用 Hibernate auto-update**: `ddl-auto: validate`（不再自動修改 Schema）
- **新增 Migration**: `V38__Consolidate_Media_Assets_Schema.sql`（統一 media_assets 表結構）
- **修復衝突**: V13 vs V22 media_assets 表命名衝突已解決
- **效益**: Schema 版本控制建立，每次部署可追蹤、可回滾

### ErrorCode 全面遷移（US-004 + US-006/007/008）

#### Phase 1 — 新增基礎錯誤碼

- **新增 11 個專用錯誤碼**:
  - `E_5010`: 訂單業務邏輯錯誤（BookingService）
  - `E_5011`: 付款處理失敗（PaymentService）
  - `E_5012`: 退款處理失敗（PaymentStateService）
  - `E_5013`: 結算業務邏輯錯誤（SettlementGenerator）
  - `E_5014`: 結算審核失敗（SettlementReviewer）
  - `E_8002`: 推播通知發送失敗
  - `E_8003`: 通知模板不存在
  - `E_8004`: CMS 頁面不存在
  - `E_8005`: CMS Banner 不存在
  - `E_1087`: 評價不存在
  - `E_1092`: 預訂評價不存在
- **修正處數**: 11 處誤用修正（OrderService/SettlementGenerator/SettlementReviewer/NotificationService）

#### Phase 2A — Review 模組遷移

- **新增 3 個錯誤碼**:
  - `E_1093`: 商品已評價
  - `E_1094`: 預訂已評價
  - `E_1095`: 評分範圍無效
- **遷移**: ReviewService（14 處）、ReviewReplyService（1 處）、BookingReviewService（4 處）

#### Phase 2B — Notification + CMS 模組遷移

- **遷移**: NotificationTemplateService（4 處 → E_8003）、CmsService（6 處 → E_8004/E_8005）

#### Phase 2C — Payment + BookingService 遷移

- **遷移**: PaymentService（2 處 → E_5011）、PaymentStateService（3 處 → E_5011/E_5012）、BookingService（1 處 → E_5010）

> **累計**: 46 處 `E_8000`/`E_5001` 濫用修正，14 個新專用錯誤碼

### 前端 Pre-commit Type-check（US-002）

- **驗證**: `frontend/.husky/pre-commit` 既有 hook 已包含 `npm run type-check`（Line 53-65）
- **攔截驗證**: 故意引入 TS2322/TS2304 錯誤，確認 commit 被阻擋
- **文件**: [FRONTEND_PRECOMMIT_GUIDE.md](../06_quality/FRONTEND_PRECOMMIT_GUIDE.md)

---

## 品質強化 🛡️

### Docker 管理政策（US-005 延伸）

- **新建**: [DOCKER_POLICY.md](DOCKER_POLICY.md)
- **禁止行為**: 未授權 image 新增、將釘定版本改為 `latest`、移除 healthcheck / `profiles: ["with-llm"]` 等
- **允許行為**: 修復明確語法錯誤、在明確指示下調整環境變數值

### PMD/Checkstyle 強化

- **新增 PMD 規則**:
  - `AvoidThrowingRawExceptionTypes`（禁止拋出 RuntimeException）
  - `ExceptionAsFlowControl`（禁止用例外控制流程）
  - `AvoidCatchingGenericException`（禁止過寬 catch(Exception)）
- **新增 Checkstyle 規則**:
  - `ThrowsCount`（限制 throws 宣告數量）
  - `IllegalThrows`（禁止宣告 RuntimeException/Error）

### 技術債掃描報告（US-005）

- **新建**: [TECHNICAL_DEBT_TODO_SCAN.md](../06_quality/TECHNICAL_DEBT_TODO_SCAN.md)
- **掃描結果**: 122 處技術債（0 TODO/FIXME + 11 @Deprecated + 68 Mock + 22 catch(Exception) + 4 RuntimeException + 12 待實作）
- **清理計劃**: Sprint 19-21 分批執行（37.5-50.5 SP）

### CI/CD Hook v3 架構

- **pre-commit**: 快速檢查（lint + compile + 核心測試，約 1-2 分鐘）
- **commit-msg**: 基本訊息格式檢查
- **pre-push**: 唯一完整 CI 守門員（act 完整驗證 + 10 分鐘快取）
- **效益**: commit 速度提升，push 前仍有完整 CI 保護

---

## 資料庫異動 🗄️

| Migration | 說明 |
|-----------|------|
| V38__Consolidate_Media_Assets_Schema.sql | 統一 media_assets 表結構，解決 V13/V22 命名衝突 |

---

## Breaking Changes ⚠️

**無** — 本次所有變更均向後相容：
- Flyway 啟用不影響現有 API
- ErrorCode 遷移保留舊碼（方案 B 漸進遷移）
- 新增 API 不影響現有端點

---

## 已知問題與下一步 📋

| 項目 | 狀態 | Sprint 19 計劃 |
|------|------|----------------|
| ErrorCode Phase 3（CheckoutService/UserService/StoreService 等） | ⏳ 待執行 | US 規劃中 |
| Payment `catch(Exception)` 細分（8 處） | ⏳ 待執行 | P0 |
| @Deprecated 清理（11 處） | ⏳ 待確認 | Sprint 19-20 |
| Stripe Webhook signature 驗證（Phase 3） | ⏳ 待執行 | P0 |
| OAuth `throw RuntimeException` → E_2003 | ⏳ 待執行 | P0 |

---

## 回滾方式 🔄

```bash
# 回滾至 Sprint 17 Release
git checkout v2026.06.19-01

# 注意：Flyway migration V38 已執行
# 如需回滾 V38，需手動執行 DROP 指令（見 V38 檔案中的回滾腳本）
```

---

## 相關文件 📄

- **Sprint 計劃**: [SPRINT_18_PLAN.md](../04_planning/SPRINT_18_PLAN.md)
- **Sprint 任務**: [SPRINT_18_TASKS.md](../05_development/SPRINT_18_TASKS.md)
- **Sprint Review**: [SPRINT_18_REVIEW.md](../05_development/SPRINT_18_REVIEW.md)
- **Sprint Retro**: [SPRINT_18_RETRO.md](../05_development/SPRINT_18_RETRO.md)
- **Flyway 評估**: [FLYWAY_EVALUATION.md](../06_quality/FLYWAY_EVALUATION.md)
- **ErrorCode 重構評估**: [ErrorCode_Refactor_Evaluation.md](../06_quality/ErrorCode_Refactor_Evaluation.md)
- **技術債掃描**: [TECHNICAL_DEBT_TODO_SCAN.md](../06_quality/TECHNICAL_DEBT_TODO_SCAN.md)
- **Docker 政策**: [DOCKER_POLICY.md](DOCKER_POLICY.md)
- **前端 Pre-commit 指南**: [FRONTEND_PRECOMMIT_GUIDE.md](../06_quality/FRONTEND_PRECOMMIT_GUIDE.md)

---

## 如何執行 Release

```bash
# 1. 確認測試通過
mvn verify -Pintegration-test

# 2. 建立 git tag
git tag -a v2026.07.03-01 -m "Release Sprint 18: Flyway啟用 + ErrorCode全面遷移 + M08搜尋"

# 3. push tag
git push origin v2026.07.03-01

# 4. 建立 GitHub Release（可選）
gh release create v2026.07.03-01 --title "v2026.07.03-01 - Sprint 18" --notes-file docs/08_deployment/RELEASE_NOTES_v2026.07.03-01.md
```

> ⚠️ **注意**: 實際 Release 應在 Sprint 18 結束日（2026-07-03）執行，或所有 DoD 項目確認完成後執行。

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-24
**建立者**: Dev David + PM/PO Victoria
