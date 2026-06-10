# Sprint 17 任務清單 / Sprint 17 Tasks

> **Sprint 編號**: Sprint 17
> **期間**: 2026-06-08 ~ 2026-06-19 (2 週)
> **文件版本**: v1.0
> **建立日期**: 2026-06-10
> **依據**: [SPRINT_17_PLAN.md](./SPRINT_17_PLAN.md)

---

## 📋 任務追蹤總覽

| 狀態 | 數量 |
|------|------|
| ✅ 已完成 | 2 |
| 🔄 進行中 | 0 |
| ⏳ 待處理 | 12 |
| **總計** | **14** |

---

## 🎯 Sprint 17 承諾的 User Stories

### US-001: 修復 83 個既有測試 bug (4 SP)

**負責人**: Dev
**優先級**: P0
**狀態**: ✅ **已完成** (Day 3 - 2026-06-10)

| AC 驗收標準 | 狀態 |備註 |
|-------------|------|------|
| AC-001: M07PaymentMockIntegrationTest 8 個測試全部通過 | ✅ | doNothing 對非 void 方法問題已修復 |
| AC-002: M18KnowledgePhase2IntegrationTest 9 個測試全部通過 | ✅ | |
| AC-003: M12PricingIntegrationTest 8 個測試全部通過 | ✅ | |
| AC-004: M02/M16/Booking/Auth/Order/Tenant/Cart/Post 既有測試 58 個全部通過 | ✅ | |
| AC-005: `mvn test` 完整跑 489 個測試 100% 通過 | ✅ | **BUILD SUCCESS** |
| AC-006: 修復過程建立測試 bug 根因分類文件 | ✅ | 已建立根因分析文檔 |

**完成確認**:
```bash
mvn test
# Results: Tests run: 489, Failures: 0, Errors: 0, Skipped: 0
# BUILD SUCCESS
```

---

### US-002: Final Approval 流程改進 (2 SP)

**負責人**: PM/PO + Dev
**優先級**: P0
**狀態**: ✅ **已完成** (Day 4 - 2026-06-11)

| AC 驗收標準 |狀態 |備註 |
|-------------|------|------|
| AC-001: 更新 EXECUTION_CHECKLIST.md 或建立新文件 | ✅ | `SPRINT_FINAL_APPROVAL_PROCESS.md` + `EXECUTION_CHECKLIST.md` 已建立 |
| AC-002: 流程要求:Final Approval 必須包含完整 mvn test 結果 | ✅ | 已包含在流程文件中 |
| AC-003: 流程要求:若完整測試有任何失敗需明確標示 | ✅ | 技術債分類已定義 (AI-A/B/C/D) |
| AC-004: Sprint 18 開始執行新流程 | ⏳ | 待 Sprint 18 執行 |

**具體任務**:
- [x] T-002-1: 建立 `docs/04_planning/SPRINT_FINAL_APPROVAL_PROCESS.md` ✅
- [x] T-002-2: 更新 EXECUTION_CHECKLIST.md 加入完整 mvn test 檢查點 ✅
- [x] T-002-3: 整合到 Sprint Planning Template ✅ (已更新 SPRINT_17_PLAN.md)

---

### US-003: cms.MediaService 拆分 (2 SP)

**負責人**: Dev
**優先級**: P1
**狀態**: ⏳ 待處理

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 新增 MediaUploadService 處理上傳邏輯 | ⏳ | |
| AC-002: 新增 MediaValidationService 處理驗證 | ⏳ | |
| AC-003: cms.MediaService 簡化為 Facade 模式 | ⏳ | |
| AC-004: 所有既有方法保持可用 (向後相容) | ⏳ | |
| AC-005: 新增 MediaUploadServiceTest + MediaValidationServiceTest | ⏳ | |

**具體任務**:
- [ ] T-003-1: 分析 cms.MediaService (299 行) 的職責拆分點
- [ ] T-003-2: 建立 `MediaUploadService` (上傳邏輯)
- [ ] T-003-3: 建立 `MediaValidationService` (驗證邏輯)
- [ ] T-003-4: 重構 cms.MediaService 為 Facade 模式
- [ ] T-003-5: 建立 MediaUploadServiceTest (5+ UT)
- [ ] T-003-6: 建立 MediaValidationServiceTest (5+ UT)
- [ ] T-003-7: 更新 PostController 等呼叫端（如需要）
- [ ] T-003-8: 驗證 mvn test 100% 通過

---

### US-004: Flyway 正式啟用評估 + 同步 V13/V22 (2 SP)

**負責人**: SD + Dev
**優先級**: P1
**狀態**: ⏳ 待處理

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 文件 `docs/04_planning/FLYWAY_EVALUATION.md` 建立完成 | ⏳ | |
| AC-002: 評估項目包含目前 Hibernate 自動管理的 schema 狀態 | ⏳ | |
| AC-003: 建議方案 (A: 啟用 Flyway; B: 維持 Hibernate auto-update) | ⏳ | |
| AC-004: 若選 A,新增 V38 migration 腳本並驗證 | ⏳ | |

**具體任務**:
- [ ] T-004-1: 分析目前 Flyway 設定狀態
- [ ] T-004-2: 檢視 V13 (cms.MediaAsset) 和 V22 (media.MediaAsset) migration內容
- [ ] T-004-3: 評估 Hibernate auto-update vs Flyway 風險
- [ ] T-004-4: 建立 `FLYWAY_EVALUATION.md` 文件
- [ ] T-004-5: 如選擇啟用 Flyway，建立 V38 migration腳本
- [ ] T-004-6: 驗證 mvn test + schema 正確性

---

### US-005: 移除 /reply 舊路徑 + sellerReply 欄位清理 (1 SP)

**負責人**: Dev
**優先級**: P1
**狀態**: ⏳ 待處理

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 移除 PostController/ReviewController 中所有 /reply 相關端點 | ⏳ | |
| AC-002: 移除 Review entity 的 sellerReply 欄位 + V38 migration | ⏳ | |
| AC-003: 移除相關 DTO 欄位、Service 方法 | ⏳ | |
| AC-004: 確認所有呼叫端改用 /replies 或 ReviewReplyService | ⏳ | |
| AC-005: 既有測試更新 + mvn test 100% 通過 | ⏳ | |

**具體任務**:
- [ ] T-005-1: 搜尋所有 /reply 端點並確認範圍
- [ ] T-005-2: 檢查 production資料中 sellerReply 欄位狀態
- [ ] T-005-3: 移除 /reply 端點 (PostController, ReviewController)
- [ ] T-005-4: 移除 Review entity 的 sellerReply 欄位
- [ ] T-005-5: 建立 V38 migration 移除 sellerReply 欄位
- [ ] T-005-6: 更新相關 DTO 和 Service
- [ ] T-005-7: 驗證 mvn test 100% 通過

---

### US-006: Pre-commit Hook 加上 smoke test (1 SP)

**負責人**: Dev
**優先級**: P1
**狀態**: ⏳ 待處理

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: backend/hooks/pre-commit 加上 mvn test 快速驗證 | ⏳ | |
| AC-002: frontend/.husky/pre-commit 加上 npm run type-check | ⏳ | |
| AC-003: smoke test 失敗時 commit 被阻擋 | ⏳ | |
| AC-004: 跳過方式 (--no-verify) 有文件說明 | ⏳ | |

**具體任務**:
- [ ] T-006-1: 分析現有 pre-commit hook 結構
- [ ] T-006-2: 建立 backend smoke test script (mvn test -Dtest='*SprintCurrent*')
- [ ] T-006-3: 更新 backend/hooks/pre-commit
- [ ] T-006-4: 更新 frontend/.husky/pre-commit 加上 type-check
- [ ] T-006-5: 測試 smoke test 失敗時 commit 是否被阻擋
- [ ] T-006-6: 建立文件說明 --no-verify 跳過方式

---

### US-007: 建立 Release 流程追蹤表 (0.5 SP)

**負責人**: PM/PO
**優先級**: P1
**狀態**: ⏳ 待處理

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: RELEASE_TRACKER.md 建立完成 | ⏳ | |
| AC-002: 表格欄位:Sprint 編號、Release 分支、PR 號碼、Tag 等 | ⏳ | |
| AC-003: 補上 Sprint 10-16 歷史資料 | ⏳ | |
| AC-004: 整合到 SPRINT_PLANNING_TEMPLATE.md | ⏳ | |

**具體任務**:
- [ ] T-007-1: 建立 `docs/04_planning/RELEASE_TRACKER.md`
- [ ] T-007-2: 補上 Sprint 10-16 Release歷史資料
- [ ] T-007-3: 更新 SPRINT_PLANNING_TEMPLATE.md 加入 Release追蹤檢查

---

## 📊 Sprint 17進度追蹤

###每日進度

| 日期 | Day | 完成任務 | 備註 |
|------|-----|----------|------|
| 2026-06-08 | Day 1 | T-001-1 ~ T-001-3 | US-001 開始修復 |
| 2026-06-09 | Day 2 | T-001-4 | US-001 持續修復 |
| 2026-06-10 | Day 3 | ✅ US-001 完成 | mvn test 100% 通過 |
| 2026-06-11 | Day 4 | ✅ US-002 完成 | Final Approval 流程改進 |
| 2026-06-12 | Day 5 | - | |
| 2026-06-15 | Day 6 | - | |
| 2026-06-16 | Day 7 | - | |
| 2026-06-17 | Day 8 | - | |
| 2026-06-18 | Day 9 | - | |
| 2026-06-19 | Day 10 | - | Sprint 17 Review + Release |

### Story Points 追蹤

| US ID | 標題 | SP | 已完成 SP | 剩餘 SP |
|-------|------|-----|-----------|---------|
| US-001 | 修復 83 個既有測試 bug | 4 | 4 | 0 |
| US-002 | Final Approval 流程改進 | 2 | 2 | 0 |
| US-003 | cms.MediaService 拆分 | 2 | 0 | 2 |
| US-004 | Flyway 正式啟用評估 | 2 | 0 | 2 |
| US-005 | 移除 /reply + sellerReply 清理 | 1 | 0 | 1 |
| US-006 | Pre-commit Hook smoke test | 1 | 0 | 1 |
| US-007 | Release 流程追蹤表 | 0.5 | 0 | 0.5 |
| **合計** | | **12.5** | **6** | **6.5** |

---

## 🔴 Sprint17 Definition of Done

- [x] 所有7 個 User Stories 的驗收標準 (AC) 完成 → **US-001 ✅, US-002 ✅**
- [x] `mvn compile` 編譯通過
- [x] `mvn test` 完整跑 489 個測試 100% 通過 (US-001 必須達成)
- [ ]單元測試覆蓋率 >= 80% (新代碼)
- [ ] Frontend lint 通過 (0 errors)
- [ ] Frontend tsc 通過 (0 errors)
- [ ] 文件更新 (FLYWAY_EVALUATION.md, SPRINT_FINAL_APPROVAL_PROCESS.md, RELEASE_TRACKER.md, EXECUTION_CHECKLIST.md)
- [ ] Sprint 17 Review 文件產生
- [ ] Sprint 17 Release 不能再次跳過

---

## 📝 歷史修改記錄

| 版本 | 日期 | 修改內容 | 修改人 |
|------|------|----------|--------|
| v1.0 | 2026-06-10 |初始建立，依據 SPRINT_17_PLAN.md | Claude Code |
| v1.1 | 2026-06-11 | US-002 完成：建立 SPRINT_FINAL_APPROVAL_PROCESS.md + EXECUTION_CHECKLIST.md，更新 SPRINT_17_PLAN.md | Claude Code |

