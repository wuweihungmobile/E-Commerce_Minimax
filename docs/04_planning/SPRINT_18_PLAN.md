# Sprint 18 計劃 / Sprint 18 Plan

> **Sprint 編號**: Sprint 18
> **期間**: 2026-06-22 ~ 2026-07-03 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-11
> **基於**: [Sprint 17 Retrospective](../05_development/SPRINT_17_RETRO.md) + Sprint 17 完成狀態

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 17 開發完成 | ✅ 7/7 US 完成 (100%) | US-001~007 全部 AC 達成 |
| Sprint 17 測試狀態 | ✅ 532 tests, 0 Failures, 0 Errors | BUILD SUCCESS |
| Sprint 17 Retrospective | ✅ [SPRINT_17_RETRO.md](../05_development/SPRINT_17_RETRO.md) | 4 個 Action Items 產出 |
| Sprint 17 Release 狀態 | ⏳ release/v2026.06.19-01 分支已建立 | PR 待合併至 main |
| Sprint 17 Final Approval | ✅ [SPRINT_17_FINAL_APPROVAL.md](../06_quality/SPRINT_17_FINAL_APPROVAL.md) | 四方審議全數 APPROVED |
| Sprint 18 開始日期 | ✅ **2026-06-22** (週一) | Sprint 17 (06-08~06-19) 後 3 天 |

> ✅ **技術基底穩定**: Sprint 17 完成 83 個測試 bug 修復，測試基底從 83 個失敗歸零至 532 tests 100% 通過。Sprint 18 可基於此穩定基底恢復新功能開發。

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 18 |
| **開始日期** | 2026-06-22 (週一) |
| **結束日期** | 2026-07-03 (週五) |
| **Sprint 容量** | 20 SP |
| **規劃 SP** | 12 SP |
| **Buffer** | 8 SP (40%) |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 基於 Sprint 17 建立之穩定測試基底 (532 tests 100% 通過)，正式啟用 Flyway 解決 Hibernate auto-update 風險，同時評估新功能開發需求。

### 具體目標

#### 🎯 P0 必須完成

| 功能 | 優先級 | 對應 Retro Action |
|------|--------|------------------|
| 正式啟用 Flyway (依據 FLYWAY_EVALUATION.md) | P0 | AI-203 |
| Sprint 17 Release 合併至 main | P0 | AI-201 |

#### 🏗️ P1 架構與技術債

| 功能 | 優先級 | 對應 Retro Action |
|------|--------|------------------|
| 新功能開發恢復 (M08/M07/M09 下一階段) | P1 | 業務需求 |
| 前端 Pre-commit type-check 實作 | P1 | TI-201 |
| ErrorCode 重構評估 (E_5001/E_5005/E_5006/E_8000) | P2 | AI-204 |

---

## 3. Sprint 18 User Stories

### US-001: 正式啟用 Flyway (5 SP)

**描述**:
作為 SD/Dev，我需要正式啟用 Flyway 並停用 Hibernate auto-update，依據 `FLYWAY_EVALUATION.md` 建議的「選項 A」，確保 Schema 版本控制與可回滾能力。

**驗收標準**:
- [ ] AC-001: 修改 `application.yml` 啟用 Flyway (`enabled: true`)
- [ ] AC-002: 停用 Hibernate ddl-auto (改為 `validate` 或 `none`)
- [ ] AC-003: 建立 V38__Consolidate_Media_Assets schema 統一 migration
- [ ] AC-004: 修復 V13 vs V22 `media_assets` 表命名衝突
- [ ] AC-005: `mvn test` 完整跑 532 個測試 100% 通過
- [ ] AC-006: 文件更新 (FLYWAY_EVALUATION.md 標註已啟用)

**技術備註**:
- V38 migration 需結合 V13 和 V22 的欄位
- 需確保既有資料不受影響
- 參考: [FLYWAY_EVALUATION.md](../04_planning/FLYWAY_EVALUATION.md)

**依賴**: 無

---

### US-002: 前端 Pre-commit type-check 實作 (2 SP)

**描述**:
作為 Dev，我需要在前端 `.husky/pre-commit` 加上 `npm run type-check`，補足 Sprint 17 US-006 延期的部分，確保 commit 前先驗證 TypeScript 型別正確。

**驗收標準**:
- [ ] AC-001: `frontend/.husky/pre-commit` 加上 `npm run type-check`
- [ ] AC-002: type-check 失敗時 commit 被阻擋
- [ ] AC-003: 跳過方式 (`--no-verify`) 有文件說明
- [ ] AC-004: 驗證 type-check 正確攔截錯誤

**技術備註**:
- Sprint 17 backend pre-commit 已更新 (mvn compile + core layer test)
- 前端需同步加入 type-check

**依賴**: 無

---

### US-003: 新功能開發 - M08 評價系統下一階段 (3 SP)

**描述**:
作為 PM/PO，我需要在技術債清理完成後，恢復 M08 評價系統的新功能開發。基於 Sprint 16-17 建立的穩定基底，繼續推進 M08 Phase 2-C。

**驗收標準**:
- [ ] AC-001: 根據 Sprint 17 Retro 建議，確認 M08 下一階段需求
- [ ] AC-002: 完成至少 1 個 M08 新功能 (範圍待 PM/PO 確認)
- [ ] AC-003: 所有既有測試 100% 通過

**技術備註**:
- 具體功能範圍待 Sprint 18 Planning 會議確認
- 可能包含:評價統計強化、評價標記進階、商家回覆通知等

**依賴**: US-001 (Flyway 啟用完成後再開始新功能)

---

### US-004: ErrorCode 重構評估 (1 SP)

**描述**:
作為 SD，我需要評估 ErrorCode 重構的可行性，根據 Sprint 16 Retro 識別的 `E_5001/E_5005/E_5006` 語意問題與 `E_8000` 過於通用的問題，產出重構評估報告。

**驗收標準**:
- [ ] AC-001: 分析 E_5001/E_5005/E_5006 當前使用情況
- [ ] AC-002: 分析 E_8000 當前使用情況 (過於通用)
- [ ] AC-003: 建立 ErrorCode 重構評估文件
- [ ] AC-004: 如時間允許，開始 E_8000 重構 (改為更具體的錯誤碼)

**技術備註**:
- Sprint 16 Final Approval 識別的技術債 (B-1, B-2)
- 重構可能影響既有錯誤處理，需謹慎評估

**依賴**: 無

---

### US-005: Sprint 18 日常開發支援 (1 SP)

**描述**:
作為 Dev，我需要處理日常開發中的緊急需求、Bug 修復、技術支援等工作，確保 Sprint 18 緩衝空間足夠。

**驗收標準**:
- [ ] AC-001: 緊急 Bug 修復 (如有)
- [ ] AC-002: PM/PO 臨時需求 (如有)
- [ ] AC-003: 團隊技術支援 (如有)

**技術備註**:
- 1 SP buffer 預留給臨時需求
- 如無臨時需求，可用於文件改進或技術研究

**依賴**: 無

---

## 4. 技術可行性評估

### 架構影響分析

| US | 複雜度 | 架構影響 | 技術風險 | SD 建議 |
|----|--------|----------|----------|---------|
| US-001 | 高 | 高 | 中 | Flyway 啟用是重大變更，需充分測試 |
| US-002 | 低 | 無 | 低 | 前端既有框架擴展 |
| US-003 | 中 | 中 | 低 | 基於穩定基底，風險可控 |
| US-004 | 中 | 中 | 中 | ErrorCode 重構可能影響既有錯誤處理 |
| US-005 | 低 | 無 | 低 | 緩衝性質 |

### 新技術依賴

| 技術 | 用途 | 風險 |
|------|------|------|
| Flyway | US-001 | 中 - 從 Hibernate auto-update 切換 |
| TypeScript | US-002 | 無 - 既有技術 |

### SD 觀點

> **Marcus (SD) 回饋**：
> - US-001 Flyway 啟用是 Sprint 18 最重要的任務，需預留足够測試時間
> - US-002 前端 type-check 是 Sprint 17 延期項目，應儘快完成
> - US-003 新功能開發應在 Flyway 啟用驗證完成後再開始
> - US-004 ErrorCode 重構應謹慎評估影響範圍

---

## 5. Story Points 估算

| US ID | 標題 | 複雜度 | 不確定性 | SP |
|-------|------|--------|----------|-----|
| US-001 | 正式啟用 Flyway | 高 | 中 | 5 |
| US-002 | 前端 Pre-commit type-check | 低 | 低 | 2 |
| US-003 | M08 新功能開發 | 中 | 中 | 3 |
| US-004 | ErrorCode 重構評估 | 中 | 中 | 1 |
| US-005 | 日常開發支援 | 低 | 低 | 1 |
| **合計** | | | | **12 SP** |

**註**: 12 SP 規劃，預留 8 SP buffer (40%) 給未知需求和緊急變更。

---

## 6. Sprint 承諾

**Sprint 目標**:
> 基於 Sprint 17 穩定的測試基底，正式啟用 Flyway 解決 Hibernate auto-update 風險，同時恢復新功能開發。總計 12 SP，預留 8 SP buffer。

**承諾的 User Stories**:

| 優先級 | US ID | 標題 | SP | 負責人 |
|--------|-------|------|----|-------|
| P0 | US-001 | 正式啟用 Flyway | 5 | SD + Dev |
| P1 | US-002 | 前端 Pre-commit type-check | 2 | Dev |
| P1 | US-003 | M08 新功能開發 | 3 | Dev |
| P2 | US-004 | ErrorCode 重構評估 | 1 | SD |
| P2 | US-005 | 日常開發支援 | 1 | Dev |

**總 SP**: 12 SP
**團隊容量**: 20 SP
**填充率**: 60%
**Buffer**: 8 SP (40%) - 預留給未知需求 + Flyway 啟用風險

**風險**:
1. **Flyway 啟用風險** - 從 Hibernate auto-update 切換到 Flyway 可能影響既有資料。緩解措施:充分測試 + V38 migration 等冪性設計
2. **新功能範圍不確定** - M08 下一階段具體需求待確認。緩解措施:US-003 預留 3 SP，先從小型功能開始
3. **ErrorCode 重構影響範圍** - E_8000 過於通用，重構可能影響大量錯誤處理。緩解措施:US-004 先做評估，實際重構推遲至 Sprint 19

---

## 7. 測試規劃

### 測試範圍

| US ID | 測試重點 |
|-------|----------|
| US-001 | 532 個 mvn test 100% 通過 + Flyway migration 驗證 |
| US-002 | npm run type-check 正確執行 |
| US-003 | M08 新功能 UT + IT |
| US-004 | 無 (評估文件) |
| US-005 | 如有 Bug 修復，相關測試 |

### 測試類型

- [ ] 單元測試 (US-001, US-003)
- [ ] 整合測試 (US-001 完整 mvn test)
- [ ] Migration 驗證 (US-001 Flyway)
- [ ] Type-check (US-002)

---

## 8. Sprint 18 Definition of Done

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

## 9. 開發順序建議

### 建議執行順序

1. **Day 1 上午** - Sprint 18 Plan Review + 同步 main → develop
2. **Day 1 下午 - Day 3** - **US-001 Flyway 啟用** (5 SP, 最關鍵)
3. **Day 4-5** - **US-003 M08 新功能開發** (3 SP)
4. **Day 6** - US-002 前端 type-check (2 SP)
5. **Day 7** - US-004 ErrorCode 重構評估 (1 SP)
6. **Day 8-9** - US-005 日常支援 + Buffer (1 SP)
7. **Day 10** - Sprint 18 Review + Release

### 每日進度追蹤

| 日期 | 目標完成 |
|------|----------|
| Day 1 (06-22) | Plan Review + US-001 開始 (20%) |
| Day 2 (06-23) | US-001 (50%) |
| Day 3 (06-24) | US-001 (100%) + US-003 開始 |
| Day 4 (06-25) | US-003 (50%) |
| Day 5 (06-26) | US-003 (100%) |
| Day 6 (06-29) | US-002 (100%) |
| Day 7 (06-30) | US-004 (100%) |
| Day 8 (07-01) | US-005 + Buffer |
| Day 9 (07-02) | Buffer + 文件 |
| Day 10 (07-03) | Sprint 18 Review + Release |

---

## 10. Sprint 18 與 AISDLC 流程整合

### 流程改善執行

> Sprint 18 將嚴格執行新的 Final Approval 流程:
> 1. **Final Approval 強制執行完整 mvn test** - 不再只看 Sprint 範圍內測試
> 2. **Sprint 18 Release 不可跳過** - Day 10 立即執行完整 Release 流程
> 3. **EXECUTION_CHECKLIST.md 應用** - Final Approval 前必須通過檢查清單

### Sprint 18 結束後流程

1. ⏳ Sprint 18 Review
2. ⏳ Sprint 18 Retrospective
3. ⏳ Release Sprint 18
4. ⏳ Sprint 19 Planning

---

## 11. 相關文件

| 文件 | 路徑 | 狀態 |
|------|------|------|
| Sprint 17 Review | `docs/05_development/SPRINT_17_REVIEW.md` | ✅ |
| Sprint 17 Retro | `docs/05_development/SPRINT_17_RETRO.md` | ✅ |
| Sprint 17 Final Approval | `docs/06_quality/SPRINT_17_FINAL_APPROVAL.md` | ✅ |
| Flyway Evaluation | `docs/04_planning/FLYWAY_EVALUATION.md` | ✅ |
| Release Tracker | `docs/04_planning/RELEASE_TRACKER.md` | ✅ |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-11
**基於**: Sprint 17 Retrospective Action Items + Sprint 17 完成狀態
**驗證人**: Claude Code (AI Assistant)
**Sprint 18 狀態**: 📋 **PLANNING COMPLETED** - 待團隊 Review 後開始執行
