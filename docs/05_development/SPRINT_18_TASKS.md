# Sprint 18 任務清單 / Sprint 18 Tasks

> **Sprint 編號**: Sprint 18
> **期間**: 2026-06-22 ~ 2026-07-03 (2 週)
> **文件版本**: v1.8
> **建立日期**: 2026-06-11
> **更新日期**: 2026-06-24 (Day 3 ErrorCode 重構 Phase 1 完成)
> **依據**: [SPRINT_18_PLAN.md](./SPRINT_18_PLAN.md) + [SPRINT_18_DETAILED_EXECUTION_PLAN.md](../04_planning/SPRINT_18_DETAILED_EXECUTION_PLAN.md)

---

## 📋 任務追蹤總覽

| 狀態 | 數量 |
|------|------|
| ✅ 已完成 | 11 |
| 🔄 進行中 | 0 |
| ⏳ 待處理 | 0 |
| **總計** | **11** |

---

## 🎯 Sprint 18 承諾的 User Stories

### US-001: 正式啟用 Flyway (5 SP)

**負責人**: SD + Dev
**優先級**: P0
**狀態**: ✅ **已完成** (Sprint 17 Release v2026.06.19-01)

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 修改 `application.yml` 啟用 Flyway | ✅ | `enabled: true` |
| AC-002: 停用 Hibernate ddl-auto (改為 validate) | ✅ | `ddl-auto: validate` |
| AC-003: 建立 V38__Consolidate_Media_Assets schema 統一 migration | ✅ | `V38__Consolidate_Media_Assets_Schema.sql` |
| AC-004: 修復 V13 vs V22 media_assets 表命名衝突 | ✅ | V22 已標記廢棄 |
| AC-005: mvn test 完整跑 532 個測試 100% 通過 | ✅ | **541 tests 100% 通過** (272 Unit + 269 Integration) |
| AC-006: 文件更新 (FLYWAY_EVALUATION.md 標註已啟用) | ✅ | v1.1 已更新 |

**具體任務**:
- [x] T-001-1: 修改 application.yml 啟用 Flyway
- [x] T-001-2: 停用 Hibernate ddl-auto (改為 validate)
- [x] T-001-3: 建立 V38 migration 統一 media_assets schema
- [x] T-001-4: 驗證 V38 migration 等冪性
- [x] T-001-5: 執行 mvn test 驗證 532 tests 100% 通過 (**541 tests**)
- [x] T-001-6: 更新 FLYWAY_EVALUATION.md 標註已啟用

---

### US-002: 前端 Pre-commit type-check 實作 (2 SP)

**負責人**: Dev
**優先級**: P1
**狀態**: ✅ **已完成** (Sprint 18 Day 1)

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: frontend/.husky/pre-commit 加上 npm run type-check | ✅ | 既有 hook 已包含 type-check (line 53-65) |
| AC-002: type-check 失敗時 commit 被阻擋 | ✅ | 驗證：故意錯誤 → TS2322/TS2304 攔截 |
| AC-003: 跳過方式 (--no-verify) 有文件說明 | ✅ | [FRONTEND_PRECOMMIT_GUIDE.md](../06_quality/FRONTEND_PRECOMMIT_GUIDE.md) |
| AC-004: 驗證 type-check 正確攔截錯誤 | ✅ | 已驗證（src/__test__/ 暫存錯誤檔案） |

**具體任務**:
- [x] T-002-1: 更新 frontend/.husky/pre-commit 加上 type-check (既有已包含，無需修改)
- [x] T-002-2: 驗證 type-check 失敗時 commit 被阻擋 (已驗證 TS2322/TS2304 攔截)
- [x] T-002-3: 建立文件說明 --no-verify 跳過方式 ([FRONTEND_PRECOMMIT_GUIDE.md](../06_quality/FRONTEND_PRECOMMIT_GUIDE.md))

---

### US-003: M08 新功能開發 (3 SP)

**負責人**: Dev
**優先級**: P1
**狀態**: ✅ **已完成** (Sprint 18 Day 1 下午)

**新增功能**: 評價多維度搜尋與篩選

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 確認 M08 下一階段需求 | ✅ | 評價多維度搜尋（PM/PO 確認範圍） |
| AC-002: 完成至少 1 個 M08 新功能 | ✅ | searchReviews API + Service + Repository |
| AC-003: 所有既有測試 100% 通過 | ✅ | 286 Unit + 整合測試（待背景任務完成驗證） |

**新增檔案**:
- `ReviewSearchCriteria.java` (DTO with SortBy/SortDir enums)
- `ReviewServiceSearchTest.java` (14 個單元測試)

**修改檔案**:
- `ReviewRepository.java` (+ searchReviews JPQL query)
- `ReviewService.java` (+ searchReviews method, DEFAULT_PAGE_SIZE 改為 public)
- `ReviewController.java` (+ GET /v2/reviews/listing/{listingId}/search)

**具體任務**:
- [x] T-003-1: PM/PO 確認 M08 下一階段範圍 (評價搜尋)
- [x] T-003-2: 實作 M08 新功能 (searchReviews)
- [x] T-003-3: 建立相關測試 (14 個單元測試)
- [x] T-003-4: 驗證 mvn test 100% 通過 (286 Unit tests)

**功能規格**:
- 關鍵字搜尋 (標題/內容, 不區分大小寫)
- 評分範圍 (minRating/maxRating, 1-5)
- 日期範圍 (startDate/endDate, ISO-8601)
- 是否有圖片 (hasImages)
- 是否有商家回覆 (hasReply)
- 排序選項 (createdAt/rating/helpfulCount, ASC/DESC)
- 分頁支援 (size 上限 50)

---

### US-004: ErrorCode 重構評估 (1 SP)

**負責人**: SD
**優先級**: P2
**狀態**: ✅ **已完成** (Sprint 18 Day 1 晚上)

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 分析 E_5001/E_5005/E_5006 當前使用情況 | ✅ | 10/5/3 處，濫用率 50%/80%/100% |
| AC-002: 分析 E_8000 當前使用情況 | ✅ | **32 處，濫用率 94%** |
| AC-003: 建立 ErrorCode 重構評估文件 | ✅ | [ErrorCode_Refactor_Evaluation.md](../06_quality/ErrorCode_Refactor_Evaluation.md) |
| AC-004: 開始 E_8000 重構 (如時間允許) | ✅ **Phase 1 完成** | 新增 11 個錯誤碼，修正 11 處誤用，286 tests 通過 |

**具體任務**:
- [x] T-004-1: 搜尋 E_5001/E_5005/E_5006 使用情況 (共 18 處)
- [x] T-004-2: 搜尋 E_8000 使用情況 (32 處，跨 7 個模組)
- [x] T-004-3: 建立 ErrorCode 重構評估文件 (方案 A/B/C，推薦方案 B)
- [x] T-004-4: ErrorCode 重構 Phase 1 ✅（新增 11 個專用碼，修正 11 處誤用，286 tests 通過，2026-06-24）

**評估結論**:
- **推薦方案 B**: 漸進遷移 - 新增專用錯誤碼，保留舊碼向後相容
- **Phase 1 完成 (2026-06-24)**: 新增 11 個專用錯誤碼（E_5010-E_5014, E_8002-E_8005, E_1087, E_1092）
- **Phase 1 修正**: 11 處誤用修正（OrderService 2處, SettlementGenerator 2處, SettlementReviewer 6處, NotificationService 1處）
- **Phase 2-3**: Sprint 19-20 繼續遷移 ReviewService(12處), CmsService(5處), PaymentService(6處) 等

---

### US-005: Sprint 18 日常開發支援 (1 SP)

**負責人**: Dev
**優先級**: P2
**狀態**: ✅ **已完成** (Sprint 18 Day 2)

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 緊急 Bug 修復 (如有) | ✅ | 本 Sprint 無緊急 Bug |
| AC-002: PM/PO 臨時需求 (如有) | ✅ | 無臨時需求 |
| AC-003: 團隊技術支援 (如有) | ✅ | **技術債掃描報告** |

**具體任務**:
- [x] T-005-1: 處理緊急 Bug (本 Sprint 無)
- [x] T-005-2: 處理臨時需求 (本 Sprint 無)
- [x] T-005-3: **技術債清理 TODO 掃描** (主動出擊)

**US-005 產出**:
- [TECHNICAL_DEBT_TODO_SCAN.md](../06_quality/TECHNICAL_DEBT_TODO_SCAN.md) (新建)
  - 掃描 122 處技術債，分類為 6 大類
  - 0 處 TODO/FIXME/XXX/HACK（程式碼品質良好）
  - 11 處 @Deprecated + 68 處 Mock + 22 處 catch(Exception) + 4 處 RuntimeException + 5 處 Phase + 12 處註解
  - 建立 Sprint 19-21 清理計劃 (37.5-50.5 SP)

---

---

### US-006: ErrorCode 重構 Phase 2A — Review 模組遷移 (3 SP) [Buffer]

**負責人**: Dev
**優先級**: P2 (Sprint Buffer)
**狀態**: ✅ **已完成** (Sprint 18 Day 3 晚間，commit 8e9205e)
**依據**: [ErrorCode_Refactor_Evaluation.md](../06_quality/ErrorCode_Refactor_Evaluation.md) Phase 2

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 新增 E_1093/E_1094/E_1095 三個 Review 專用錯誤碼 | ✅ | E_1093 item reviewed / E_1094 booking reviewed / E_1095 rating range |
| AC-002: ReviewService 14 處 E_8000 遷移至 E_1087/E_1093/E_1094/E_1095 | ✅ | 9+1+1+3 處全部完成 |
| AC-003: ReviewReplyService 1 處 E_8000 → E_1087 | ✅ | |
| AC-004: BookingReviewService 4 處 E_8000 → E_1092/E_1094 | ✅ | 3+1 處 |
| AC-005: mvn test 286 Unit Tests 100% 通過 | ✅ | 0 Failures |

**具體任務**:
- [x] T-006-1: ErrorCode.java 新增 E_1093 / E_1094 / E_1095
- [x] T-006-2: ReviewService.java 遷移（14 處）
- [x] T-006-3: ReviewReplyService.java 遷移（1 處）
- [x] T-006-4: BookingReviewService.java 遷移（4 處）
- [x] T-006-5: mvn test 驗證 286 Unit Tests 通過

---

### US-007: ErrorCode 重構 Phase 2B — Notification + CMS 模組遷移 (2 SP) [Buffer]

**負責人**: Dev
**優先級**: P2 (Sprint Buffer)
**狀態**: ✅ **已完成** (Sprint 18 Day 3 晚間，commit 8e9205e)
**依據**: [ErrorCode_Refactor_Evaluation.md](../06_quality/ErrorCode_Refactor_Evaluation.md) Phase 2

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: NotificationTemplateService 4 處 E_8000 → E_8003 | ✅ | |
| AC-002: CmsService 6 處 E_8000 → E_8004/E_8005 | ✅ | 4 處 page + 2 處 banner |
| AC-003: mvn test 286 Unit Tests 100% 通過 | ✅ | 0 Failures |

**具體任務**:
- [x] T-007-1: NotificationTemplateService.java 遷移（4 處 → E_8003）
- [x] T-007-2: CmsService.java 遷移（4 處 → E_8004，2 處 → E_8005）
- [x] T-007-3: mvn test 驗證 286 Unit Tests 通過

---

### US-008: ErrorCode 重構 Phase 2C — Payment + BookingService 遷移 (2 SP) [Buffer]

**負責人**: Dev
**優先級**: P2 (Sprint Buffer)
**狀態**: ✅ **已完成** (Sprint 18 Day 3 晚間，commit 8e9205e)
**依據**: [ErrorCode_Refactor_Evaluation.md](../06_quality/ErrorCode_Refactor_Evaluation.md) Phase 2

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: PaymentService 2 處 E_5001 → E_5011 | ✅ | |
| AC-002: PaymentStateService 3 處 E_5001 → E_5011/E_5012 | ✅ | 2 處 payment + 1 處 refund |
| AC-003: BookingService 1 處 E_5001 → E_5010 | ✅ | |
| AC-004: mvn test 286 Unit Tests 100% 通過 | ✅ | 0 Failures |

**具體任務**:
- [x] T-008-1: PaymentService.java 遷移（2 處 → E_5011）
- [x] T-008-2: PaymentStateService.java 遷移（2 處 → E_5011，1 處 → E_5012）
- [x] T-008-3: BookingService.java 遷移（1 處 E_5001 → E_5010）
- [x] T-008-4: mvn test 驗證 286 Unit Tests 通過

---

## 📊 Sprint 18 進度追蹤

### 每日進度

| 日期 | Day | 完成任務 | 備註 |
|------|-----|----------|------|
| 2026-06-22 | Day 1 | US-001/002/003/004 (9/9 AC) + 環境驗證 555 tests | Sprint 18 Day 1 完成全部規劃 US |
| 2026-06-23 | Day 2 | ✅ Commit 02a4fa0 推送至 origin/main (10 檔案, 2036 行) + 完整 CI 驗證 (act) | 單一分支策略：直接 commit + push main |
| 2026-06-24 | Day 3 | ✅ ErrorCode Phase 1（11 碼 + 11 處修正）+ Docker 政策 + PMD/Checkstyle 強化 + CI 清理 | f9789d6，Sprint Buffer 啟動 |
| 2026-06-24 | Day 3（後） | ✅ US-006/007/008 全部完成（35 處遷移 + 3 新碼 + 286 tests 通過，commit 8e9205e） | Sprint Buffer 提前完成 |
| 2026-06-25 | Day 4 | - | |
| 2026-06-26 | Day 5 | - | |
| 2026-06-29 | Day 6 | - | |
| 2026-06-30 | Day 7 | - | |
| 2026-07-01 | Day 8 | - | |
| 2026-07-02 | Day 9 | - | |
| 2026-07-03 | Day 10 | - | Sprint 18 Review + Release |

### Story Points 追蹤

| US ID | 標題 | SP | 已完成 SP | 剩餘 SP |
|-------|------|-----|-----------|---------|
| US-001 | 正式啟用 Flyway | 5 | **5** ✅ | 0 |
| US-002 | 前端 Pre-commit type-check | 2 | **2** ✅ | 0 |
| US-003 | M08 新功能開發 | 3 | **3** ✅ | 0 |
| US-004 | ErrorCode 重構評估 | 1 | **1** ✅ | 0 |
| US-005 | 日常開發支援 | 1 | **1** ✅ | 0 |
| **規劃小計** | | **12** | **12** | **0** |
| US-006 | [Buffer] ErrorCode Phase 2A — Review 遷移 | 3 | **3** ✅ | 0 |
| US-007 | [Buffer] ErrorCode Phase 2B — Notification + CMS 遷移 | 2 | **2** ✅ | 0 |
| US-008 | [Buffer] ErrorCode Phase 2C — Payment + BookingService 遷移 | 2 | **2** ✅ | 0 |
| **含 Buffer 合計** | | **19/20** | **19** | **0** |

---

## 🔴 Sprint 18 Definition of Done

- [x] Flyway 正式啟用，Hibernate ddl-auto 改為 validate (Sprint 17 Release v2026.06.19-01)
- [x] `mvn verify -Pintegration-test` 跑 **555 tests 100% 通過** (286 Unit + 269 Integration)
- [x] Frontend type-check 攔截驗證 (Day 1 驗證 TS2322/TS2304)
- [x] Frontend pre-commit 文件建立 ([FRONTEND_PRECOMMIT_GUIDE.md](../06_quality/FRONTEND_PRECOMMIT_GUIDE.md))
- [x] M08 評價搜尋 API 完成 (GET /v2/reviews/listing/{listingId}/search + 14 個新測試)
- [x] ErrorCode 重構評估文件建立 ([ErrorCode_Refactor_Evaluation.md](../06_quality/ErrorCode_Refactor_Evaluation.md))
- [x] Sprint 18 Review 文件產生（[SPRINT_18_REVIEW.md](./SPRINT_18_REVIEW.md)，2026-06-24）
- [x] Sprint 18 Retrospective 文件產生（[SPRINT_18_RETRO.md](./SPRINT_18_RETRO.md)，2026-06-24）
- [ ] Sprint 18 Release 執行（v2026.07.03-01，Sprint 結束日 2026-07-03 前）

---

## 📝 歷史修改記錄

| 版本 | 日期 | 修改內容 | 修改人 |
|------|------|----------|--------|
| v2.0 | 2026-06-24 | Day 3 後：US-006/007/008 全部完成（35 處遷移 + 3 新碼，286 tests 通過，commit 8e9205e） | Claude Code |
| v1.9 | 2026-06-24 | Day 3 後：新增 Sprint Buffer US-006/007/008（ErrorCode Phase 2A/B/C），7 SP Buffer 規劃 | Claude Code |
| v1.8 | 2026-06-24 | Day 3：T-004-4 完成 — ErrorCode 重構 Phase 1（新增 11 個專用碼，修正 11 處誤用，286 tests 通過） | Claude Code |
| v1.7 | 2026-06-23 | Day 2：US-005 完成（技術債掃描，122 處分類，Sprint 19-21 計劃） | Claude Code |
| v1.6 | 2026-06-23 | Day 2：Commit 02a4fa0 推送至 origin/main（10 檔案, 2036 行, 完整 CI 驗證通過） | Claude Code |
| v1.5 | 2026-06-22 | Day 1 深夜：US-004 完成（ErrorCode 評估文件，方案 B 推薦） | Claude Code |
| v1.4 | 2026-06-22 | Day 1 晚上：整合測試通過，555 tests 100% 通過（+14 新測試） | Claude Code |
| v1.3 | 2026-06-22 | Day 1 晚上：US-003 完成（M08 評價搜尋 API + 14 個測試 + 文件更新） | Claude Code |
| v1.2 | 2026-06-22 | Day 1 下午：US-002 完成（既有 hook 已含 type-check + 驗證攔截 + 文件建立） | Claude Code |
| v1.1 | 2026-06-22 | Day 1 上午：US-001 全部 AC 已 ✅，541 tests 100% 通過，FLYWAY_EVALUATION.md 更新 | Claude Code |
| v1.0 | 2026-06-11 | 初始建立，依據 SPRINT_18_PLAN.md | Claude Code |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-11
**作者**: Claude Code (AI Assistant)
