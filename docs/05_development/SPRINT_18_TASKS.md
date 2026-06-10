# Sprint 18 任務清單 / Sprint 18 Tasks

> **Sprint 編號**: Sprint 18
> **期間**: 2026-06-22 ~ 2026-07-03 (2 週)
> **文件版本**: v1.0
> **建立日期**: 2026-06-11
> **依據**: [SPRINT_18_PLAN.md](./SPRINT_18_PLAN.md)

---

## 📋 任務追蹤總覽

| 狀態 | 數量 |
|------|------|
| ✅ 已完成 | 0 |
| 🔄 進行中 | 0 |
| ⏳ 待處理 | 8 |
| **總計** | **8** |

---

## 🎯 Sprint 18 承諾的 User Stories

### US-001: 正式啟用 Flyway (5 SP)

**負責人**: SD + Dev
**優先級**: P0
**狀態**: ⏳ 待處理

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 修改 `application.yml` 啟用 Flyway | ⏳ | |
| AC-002: 停用 Hibernate ddl-auto (改為 validate) | ⏳ | |
| AC-003: 建立 V38__Consolidate_Media_Assets schema 統一 migration | ⏳ | |
| AC-004: 修復 V13 vs V22 media_assets 表命名衝突 | ⏳ | |
| AC-005: mvn test 完整跑 532 個測試 100% 通過 | ⏳ | |
| AC-006: 文件更新 (FLYWAY_EVALUATION.md 標註已啟用) | ⏳ | |

**具體任務**:
- [ ] T-001-1: 修改 application.yml 啟用 Flyway
- [ ] T-001-2: 停用 Hibernate ddl-auto (改為 validate)
- [ ] T-001-3: 建立 V38 migration 統一 media_assets schema
- [ ] T-001-4: 驗證 V38 migration 等冪性
- [ ] T-001-5: 執行 mvn test 驗證 532 tests 100% 通過
- [ ] T-001-6: 更新 FLYWAY_EVALUATION.md 標註已啟用

---

### US-002: 前端 Pre-commit type-check 實作 (2 SP)

**負責人**: Dev
**優先級**: P1
**狀態**: ⏳ 待處理

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: frontend/.husky/pre-commit 加上 npm run type-check | ⏳ | |
| AC-002: type-check 失敗時 commit 被阻擋 | ⏳ | |
| AC-003: 跳過方式 (--no-verify) 有文件說明 | ⏳ | |
| AC-004: 驗證 type-check 正確攔截錯誤 | ⏳ | |

**具體任務**:
- [ ] T-002-1: 更新 frontend/.husky/pre-commit 加上 type-check
- [ ] T-002-2: 驗證 type-check 失敗時 commit 被阻擋
- [ ] T-002-3: 建立文件說明 --no-verify 跳過方式

---

### US-003: M08 新功能開發 (3 SP)

**負責人**: Dev
**優先級**: P1
**狀態**: ⏳ 待處理

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 確認 M08 下一階段需求 | ⏳ | |
| AC-002: 完成至少 1 個 M08 新功能 | ⏳ | |
| AC-003: 所有既有測試 100% 通過 | ⏳ | |

**具體任務**:
- [ ] T-003-1: PM/PO 確認 M08 下一階段範圍
- [ ] T-003-2: 實作 M08 新功能
- [ ] T-003-3: 建立相關測試 (UT + IT)
- [ ] T-003-4: 驗證 mvn test 100% 通過

---

### US-004: ErrorCode 重構評估 (1 SP)

**負責人**: SD
**優先級**: P2
**狀態**: ⏳ 待處理

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 分析 E_5001/E_5005/E_5006 當前使用情況 | ⏳ | |
| AC-002: 分析 E_8000 當前使用情況 | ⏳ | |
| AC-003: 建立 ErrorCode 重構評估文件 | ⏳ | |
| AC-004: 開始 E_8000 重構 (如時間允許) | ⏳ | |

**具體任務**:
- [ ] T-004-1: 搜尋 E_5001/E_5005/E_5006 使用情況
- [ ] T-004-2: 搜尋 E_8000 使用情況
- [ ] T-004-3: 建立 ErrorCode 重構評估文件
- [ ] T-004-4: 開始 E_8000 重構 (如時間允許)

---

### US-005: Sprint 18 日常開發支援 (1 SP)

**負責人**: Dev
**優先級**: P2
**狀態**: ⏳ 待處理

| AC 驗收標準 | 狀態 | 備註 |
|-------------|------|------|
| AC-001: 緊急 Bug 修復 (如有) | ⏳ | |
| AC-002: PM/PO 臨時需求 (如有) | ⏳ | |
| AC-003: 團隊技術支援 (如有) | ⏳ | |

**具體任務**:
- [ ] T-005-1: 處理緊急 Bug (如有)
- [ ] T-005-2: 處理臨時需求 (如有)
- [ ] T-005-3: 團隊技術支援 (如有)

---

## 📊 Sprint 18進度追蹤

###每日進度

| 日期 | Day | 完成任務 | 備註 |
|------|-----|----------|------|
| 2026-06-22 | Day 1 | - | Sprint 18 開始 |
| 2026-06-23 | Day 2 | - | |
| 2026-06-24 | Day 3 | - | |
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
| US-001 | 正式啟用 Flyway | 5 | 0 | 5 |
| US-002 | 前端 Pre-commit type-check | 2 | 0 | 2 |
| US-003 | M08 新功能開發 | 3 | 0 | 3 |
| US-004 | ErrorCode 重構評估 | 1 | 0 | 1 |
| US-005 | 日常開發支援 | 1 | 0 | 1 |
| **合計** | | **12** | **0** | **12** |

---

## 🔴 Sprint 18 Definition of Done

- [ ] 所有 5 個 User Stories 的驗收標準 (AC) 完成
- [ ] `mvn compile` 編譯通過
- [ ] `mvn test` 完整跑 532 個測試 100% 通過 (US-001 必須達成)
- [ ] Frontend lint 通過 (0 errors)
- [ ] Frontend tsc 通過 (0 errors)
- [ ] Flyway 正式啟用，Hibernate ddl-auto 改為 validate
- [ ] ErrorCode 重構評估文件建立
- [ ] Sprint 18 Review 文件產生
- [ ] Sprint 18 Retrospective 文件產生
- [ ] Sprint 18 Release 不能再次跳過

---

## 📝 歷史修改記錄

| 版本 | 日期 | 修改內容 | 修改人 |
|------|------|----------|--------|
| v1.0 | 2026-06-11 | 初始建立，依據 SPRINT_18_PLAN.md | Claude Code |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-11
**作者**: Claude Code (AI Assistant)
