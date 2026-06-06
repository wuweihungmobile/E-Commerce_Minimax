# Sprint 17 計劃 / Sprint 17 Plan

> **Sprint 編號**: Sprint 17
> **期間**: 2026-06-08 ~ 2026-06-19 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-06
> **基於**: [Sprint 16 Retrospective](../05_development/SPRINT_16_RETRO.md) + Sprint 16 完成狀態

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 16 開發完成 | ✅ M08 評價多圖 + M07 結算強化 + Pre-commit | Sprint 16 Plan + Tasks + Review + Retro 完整 |
| Sprint 16 Retrospective | ✅ [SPRINT_16_RETRO.md](../05_development/SPRINT_16_RETRO.md) | 9 個 Action Items 產出 |
| ✅ Sprint 16 Release 狀態 | ✅ **已合併** (PR #15 + tag v2026.06.06-01) | Sprint 16 程式碼進入 Production |
| ✅ Sprint 15 Release 狀態 | ✅ **已合併** (PR #13+#14 + tag v2026.06.04-01) | Sprint 15 程式碼進入 Production |
| Sprint 16 Action Items P0 | 4 個待處理 (AI-101/103/104/105) | 已納入 Sprint 17 規劃 |
| Sprint 17 開始日期 | ✅ **2026-06-08** (週一) | 為 Sprint 16 (06-04~06-06) 後 2 天 |

> ✅ **流程改善**: Sprint 16 結束時正確執行了完整 Release 流程 (PR #15 合併 + tag + GitHub Release),不再跳過 Release 流程。

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 17 |
| **開始日期** | 2026-06-08 (週一) |
| **結束日期** | 2026-06-19 (週五) |
| **Sprint 容量** | 20 SP |
| **規劃 SP** | 14 SP |
| **Buffer** | 6 SP (30%) |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 集中解決 Sprint 16 揭露的技術債 (83 個測試 bug + JPA 架構清理),同時建立「Final Approval 完整測試」流程改進,確保未來 Sprint 不再有測試覆蓋盲點。

### 具體目標

#### 🎯 P0 技術債清理 (從 Sprint 16 Retro)

| 功能 | 優先級 | 對應 Retro Action |
|------|--------|------------------|
| 修復 83 個既有測試 bug | P0 | AI-101 |
| Final Approval 流程改進 (強制跑完整 mvn test) | P0 | AI-104 |
| Sprint 17 Release 流程不可再次跳過 | P0 | (流程改進) |

#### 🏗️ 架構清理 (P1)

| 功能 | 優先級 | 對應 Retro Action |
|------|--------|------------------|
| cms.MediaService 拆分 (299 行 → 2 個子服務) | P1 | AI-105 |
| Flyway 正式啟用評估 + 同步 V13/V22 schema | P1 | TI-101 |
| 移除 /reply 舊路徑 (Sprint 15 @Deprecated 完整關閉) | P1 | TODO-4 |
| 移除 Review entity sellerReply 欄位 | P1 | TODO-3 |

#### 📋 流程改進 (P1)

| 功能 | 優先級 | 對應 Retro Action |
|------|--------|------------------|
| Pre-commit Hook 加上 mvn test 快速驗證 (smoke test) | P1 | PI-101 |
| 建立 Release 流程追蹤表 | P1 | PI-103 |

---

## 3. Sprint 17 User Stories

### US-001: 修復 83 個既有測試 bug (4 SP)

**描述**:
作為 Dev，我需要修復 Sprint 16 Final Approval 揭露的 83 個既有測試 bug (跟 Sprint 16 開發無關的長期技術債),確保 `mvn test` 完整跑出 100% 通過。

**驗收標準**:
- [ ] AC-001: M07PaymentMockIntegrationTest 8 個測試全部通過 (doNothing 對非 void 方法問題)
- [ ] AC-002: M18KnowledgePhase2IntegrationTest 9 個測試全部通過
- [ ] AC-003: M12PricingIntegrationTest 8 個測試全部通過
- [ ] AC-004: M02/M16/Booking/Auth/Order/Tenant/Cart/Post 既有測試 58 個全部通過
- [ ] AC-005: `mvn test` 完整跑 489 個測試 100% 通過 (0 Failures, 0 Errors)
- [ ] AC-006: 修復過程建立測試 bug 根因分類文件 (Sprint 17 經驗傳承)

**技術備註**:
- 常見 bug 模式:`doNothing()` 對非 void 方法、Mock 設定錯誤、Schema 變更後測試未更新
- 修復策略:逐一診斷 + 修復 + 確認 Sprint 16 既有測試不退步
- 預估每個測試類別 0.5-1 小時

**依賴**: 無

---

### US-002: Final Approval 流程改進 (2 SP)

**描述**:
作為 PM/PO，我需要建立「Final Approval 必須跑完整 mvn test」流程改進,避免再次發生 Sprint 16 揭露的「Final Approval 標 100% 通過但實際 83 個測試 bug」的情況。

**驗收標準**:
- [ ] AC-001: 更新 [EXECUTION_CHECKLIST.md](../../AISDLC_v0.01/EXECUTION_CHECKLIST.md) 或建立新文件 `SPRINT_FINAL_APPROVAL_PROCESS.md`
- [ ] AC-002: 流程要求:Final Approval 必須包含完整 mvn test 結果 (489 個測試,Failures + Errors 數量)
- [ ] AC-003: 流程要求:若完整測試有任何失敗,需在 Final Approval 中明確標示為「已知技術債 + Action Item」,不可隱藏
- [ ] AC-004: Sprint 18 開始執行新流程

**技術備註**:
- 文件位置:`docs/04_planning/SPRINT_FINAL_APPROVAL_PROCESS.md` (新建)
- 參考:SPRINT_16_FINAL_APPROVAL.md §1.3 補述

**依賴**: 無

---

### US-003: cms.MediaService 拆分 (2 SP)

**描述**:
作為 Dev，我需要將 Sprint 16 JPA 修復後仍偏大的 `cms.MediaService` (299 行) 拆分為 2 個專責子服務,提升可維護性。

**驗收標準**:
- [ ] AC-001: 新增 `MediaUploadService` 處理上傳邏輯 (MultipartFile → StorageService → MediaAsset)
- [ ] AC-002: 新增 `MediaValidationService` 處理檔案大小/MIME 類型/格式驗證
- [ ] AC-003: `cms.MediaService` 簡化為 Facade 模式,委派給 2 個子服務
- [ ] AC-004: 所有 cms.MediaService 既有方法保持可用 (向後相容)
- [ ] AC-005: 新增 `MediaUploadServiceTest` + `MediaValidationServiceTest` 各 5+ 個 UT

**技術備註**:
- 既有呼叫端:`PostController` (4 個 API),M15Dto,測試檔案
- 拆分不破壞既有 API

**依賴**: US-001 (測試基底先穩定)

---

### US-004: Flyway 正式啟用評估 + 同步 V13/V22 (2 SP)

**描述**:
作為 SD/SA，我需要評估正式啟用 Flyway 並同步 Sprint 8 引入的 V13 (cms.MediaAsset) 跟 Sprint 10 引入的 V22 (media.MediaAsset 欄位) schema,避免繼續依賴 Hibernate auto-update。

**驗收標準**:
- [ ] AC-001: 文件 `docs/04_planning/FLYWAY_EVALUATION.md` 建立完成
- [ ] AC-002: 評估項目包含:目前 Hibernate 自動管理的 schema 狀態、V13/V22 整合方案、Flyway 啟用步驟、風險評估
- [ ] AC-003: 建議方案 (A: 正式啟用 Flyway + 新增 V38 migration 同步 cms.MediaAsset 欄位; B: 維持 Hibernate auto-update)
- [ ] AC-004: 若選 A,新增 V38 migration 腳本並驗證

**技術備註**:
- 目前:`spring.flyway.enabled: false`
- 目前 schema 是 Hibernate 自動管理,V13/V22 從未執行
- Sprint 16 修復後,cms.MediaAsset 有額外 6 個欄位需要 schema 同步

**依賴**: 無

---

### US-005: 移除 /reply 舊路徑 + sellerReply 欄位清理 (1 SP)

**描述**:
作為 Dev，我需要完成 Sprint 15 標記為 @Deprecated 的 `/reply` 端點完整關閉,以及 Sprint 15 評價重構時殘留的 sellerReply 欄位清理,提升程式碼整潔度。

**驗收標準**:
- [ ] AC-001: 移除 `PostController` / `ReviewController` 中所有 `/reply` 相關端點
- [ ] AC-002: 移除 `Review` entity 的 `sellerReply` 欄位 + V38 migration
- [ ] AC-003: 移除相關 DTO 欄位、Service 方法
- [ ] AC-004: 確認所有呼叫端改用 `/replies` (Sprint 16 新 API) 或 `ReviewReplyService` (Sprint 16 新服務)
- [ ] AC-005: 既有測試更新 + mvn test 100% 通過

**技術備註**:
- 從 Sprint 15 開始 `/replies` 為主路徑,`/reply` 已 @Deprecated
- ReviewReply 1:1 獨立 Entity (Sprint 16 建立) 已取代 sellerReply 欄位

**依賴**: US-001

---

### US-006: Pre-commit Hook 加上 smoke test (1 SP)

**描述**:
作為 Dev，我需要在 Pre-commit Hook 加上「快速 smoke test」(只跑 Sprint 新增/修改測試),在 commit 前先驗證本次變更沒有破壞既有功能,而不需要跑完整 489 個測試。

**驗收標準**:
- [ ] AC-001: backend/hooks/pre-commit 加上 `mvn test -Dtest='*SprintCurrent*'` 或類似機制
- [ ] AC-002: frontend/.husky/pre-commit 加上 `npm run type-check`
- [ ] AC-003: smoke test 失敗時 commit 被阻擋
- [ ] AC-004: 跳過方式 (`--no-verify`) 有文件說明

**技術備註**:
- 既有:husky + lint-staged + ESLint + tsc
- 目標:加上「快速測試」檢查,但不取代完整 CI

**依賴**: US-001 (先穩定測試基底)

---

### US-007: 建立 Release 流程追蹤表 (0.5 SP)

**描述**:
作為 PM/PO，我需要建立 `docs/04_planning/RELEASE_TRACKER.md`,追蹤每個 Sprint 的 Release 狀態,避免連續 2+ 個 Sprint 跳過 Release。

**驗收標準**:
- [ ] AC-001: `RELEASE_TRACKER.md` 建立完成
- [ ] AC-002: 表格欄位:Sprint 編號、Release 分支、PR 號碼、Tag、合併日期、Release Notes
- [ ] AC-003: 補上 Sprint 10-16 歷史資料
- [ ] AC-004: 整合到 `SPRINT_PLANNING_TEMPLATE.md` 作為必要檢查項目

**技術備註**:
- 從 Sprint 12 開始有 Release (v2026.05.09, v2026.05.10, ... 一直到 v2026.06.06-01)
- 此表讓 PM/PO 在 Sprint 規劃時一眼看到 Release 狀態

**依賴**: 無

---

## 4. 技術可行性評估

### 架構影響分析

| US | 複雜度 | 架構影響 | 技術風險 | SD 建議 |
|----|--------|----------|----------|---------|
| US-001 | 中 | 無 | 中 | 逐一診斷,需要時間但不破壞架構 |
| US-002 | 低 | 無 | 低 | 純文件改進 |
| US-003 | 中 | 中 | 中 | Facade 模式,向後相容 |
| US-004 | 中 | 中 | 中 | 需謹慎評估 Flyway 啟用影響 |
| US-005 | 中 | 低 | 中 | 需 migration + 既有資料檢查 |
| US-006 | 低 | 無 | 低 | 既有 Pre-commit 擴展 |
| US-007 | 低 | 無 | 低 | 純文件 |

### 新技術依賴

| 技術 | 用途 | 風險 |
|------|------|------|
| Flyway 啟用 | US-004 | 中 - 需評估 Hibernate auto-update 切換風險 |
| Mockito 修正 | US-001 | 低 - 既有依賴 |

### SD 觀點

> **Marcus (SD) 回饋**：
> - US-001 是 Sprint 17 最重要的 US,沒有測試基底穩定,後續所有改動都有風險
> - US-002 流程改進是必要的「防止再次發生」措施
> - US-004 Flyway 評估建議保守:若風險過高可維持 Hibernate auto-update,但需記錄原因
> - US-005 sellerReply 移除需謹慎:確認 production 資料中 sellerReply 欄位已無資料

---

## 5. Story Points 估算

| US ID | 標題 | 複雜度 | 不確定性 | SP |
|-------|------|--------|----------|-----|
| US-001 | 修復 83 個既有測試 bug | 中 | 中 | 4 |
| US-002 | Final Approval 流程改進 | 低 | 低 | 2 |
| US-003 | cms.MediaService 拆分 | 中 | 中 | 2 |
| US-004 | Flyway 正式啟用評估 | 中 | 中 | 2 |
| US-005 | 移除 /reply + sellerReply 清理 | 中 | 低 | 1 |
| US-006 | Pre-commit Hook smoke test | 低 | 低 | 1 |
| US-007 | Release 流程追蹤表 | 低 | 低 | 0.5 |
| **合計** | | | | **12.5 SP** |

**註**: 12.5 SP 較 SPRINT_16_PLAN.md 規劃的 12 SP 略多,但因 US-001 是新發現的 4 SP 阻斷性工作,屬於必要。

---

## 6. Sprint 承諾

**Sprint 目標**:
> 集中解決 Sprint 16 揭露的技術債 (US-001 測試基底 + US-002 流程改進),並完成 5 個架構清理 (US-003~007),總計 12.5 SP,預留 7.5 SP buffer 處理臨時需求。

**承諾的 User Stories**:

| 優先級 | US ID | 標題 | SP | 負責人 |
|--------|-------|------|----|-------|
| P0 | US-001 | 修復 83 個既有測試 bug | 4 | Dev |
| P0 | US-002 | Final Approval 流程改進 | 2 | PM/PO + Dev |
| P1 | US-003 | cms.MediaService 拆分 | 2 | Dev |
| P1 | US-004 | Flyway 正式啟用評估 | 2 | SD + Dev |
| P1 | US-005 | 移除 /reply + sellerReply 清理 | 1 | Dev |
| P1 | US-006 | Pre-commit Hook smoke test | 1 | Dev |
| P1 | US-007 | Release 流程追蹤表 | 0.5 | PM/PO |

**總 SP**: 12.5 SP
**團隊容量**: 20 SP
**填充率**: 62.5%
**Buffer**: 7.5 SP (37.5%) - 預留給臨時需求 + Release 流程

**風險**:
1. **US-001 工作量可能低估** - 83 個測試 bug 數量龐大,部分可能涉及 schema 重設計。緩解措施:分批修復,先 Sprint 17 修 50 個,剩餘列入 Sprint 18
2. **Flyway 啟用風險** - 從 Hibernate auto-update 切換到 Flyway 可能破壞現有資料。緩解措施:US-004 為評估 + 試運行,不直接切換
3. **sellerReply 移除需 migration** - 既有資料若仍有 sellerReply 資料,移除欄位會破壞資料。緩解措施:先檢查 production 資料,若有空值才能移除

---

## 7. 測試規劃

### 測試範圍

| US ID | 測試重點 |
|-------|----------|
| US-001 | 489 個 mvn test 100% 通過 |
| US-002 | 無 (流程改進) |
| US-003 | MediaUploadServiceTest + MediaValidationServiceTest 各 5+ 個 UT |
| US-004 | Flyway 啟用後 mvn test + 手動驗證 schema |
| US-005 | 既有測試更新 + mvn test 100% 通過 |
| US-006 | 故意犯錯測試 (smoke test 應該失敗) |
| US-007 | 無 (文件) |

### 測試類型

- [x] 單元測試 (US-001, US-003)
- [x] 整合測試 (US-001 完整 mvn test)
- [x] E2E 測試 (US-001 涵蓋既有 14 個 Controller E2E)
- [x] 手動驗證 (US-004 Flyway 試運行, US-006 故意犯錯)

---

## 8. Sprint 17 Definition of Done

- [ ] 所有 7 個 User Stories 的驗收標準 (AC) 完成
- [ ] `mvn compile` 編譯通過
- [ ] `mvn test` 完整跑 489 個測試 100% 通過 (US-001 必須達成)
- [ ] 單元測試覆蓋率 >= 80% (新代碼)
- [ ] Frontend lint 通過 (0 errors)
- [ ] Frontend tsc 通過 (0 errors)
- [ ] 文件更新 (FLYWAY_EVALUATION.md, SPRINT_FINAL_APPROVAL_PROCESS.md, RELEASE_TRACKER.md)
- [ ] Sprint 17 Review 文件產生
- [ ] Sprint 17 Release 不能再次跳過

---

## 9. 開發順序建議

### 建議執行順序

1. **Day 1 上午** - Sprint 17 Plan Review + 同步 main → develop (確保 develop 包含 Sprint 16 Release)
2. **Day 1 下午 - Day 3** - **US-001 修復 83 個測試 bug** (4 SP, 最關鍵)
3. **Day 3 下午 - Day 4** - US-002 Final Approval 流程改進 (2 SP)
4. **Day 5** - US-007 Release 流程追蹤表 (0.5 SP)
5. **Day 6-7** - US-003 cms.MediaService 拆分 (2 SP)
6. **Day 8** - US-005 移除 /reply + sellerReply (1 SP)
7. **Day 9** - US-006 Pre-commit smoke test (1 SP)
8. **Day 10** - US-004 Flyway 評估 + 文件 (2 SP)
9. **Day 11-12** - Buffer + Sprint 17 Review + Release

### 每日進度追蹤

| 日期 | 目標完成 |
|------|----------|
| Day 1 (06-08) | Plan Review + 同步 main→develop + US-001 開始 (20%) |
| Day 2 (06-09) | US-001 (50%) |
| Day 3 (06-10) | US-001 (100%) + US-002 開始 |
| Day 4 (06-11) | US-002 (100%) + US-007 |
| Day 5 (06-12) | US-003 (50%) |
| Day 6 (06-15) | US-003 (100%) |
| Day 7 (06-16) | US-005 (100%) + US-006 (50%) |
| Day 8 (06-17) | US-006 (100%) + US-004 (50%) |
| Day 9 (06-18) | US-004 (100%) + 文件 |
| Day 10 (06-19) | Buffer + Sprint 17 Review + Release |

---

## 10. Sprint 17 與 AISDLC 流程整合

### 流程改善承諾

> 本 Sprint 嚴格遵守 Sprint 16 Retro 識別的流程改進:
> 1. **US-002 Final Approval 流程改進** - 確保 Sprint 17 結束時 Final Approval 反映完整 mvn test
> 2. **Sprint 17 Release 不可跳過** - Day 10 立即執行完整 Release 流程 (PR + tag + GitHub Release)
> 3. **US-006 Pre-commit smoke test** - 在 commit 前先驗證

### Sprint 17 結束後流程

1. ✅ Sprint 17 Review
2. ✅ Sprint 17 Retrospective
3. ⏳ **Release Sprint 17 (不可跳過)**
4. ⏳ Sprint 18 Planning

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-06
**基於**: Sprint 16 Retrospective Action Items + Sprint 16 完成狀態
**驗證人**: Claude Code (AI Assistant)
**Sprint 17 狀態**: 📋 **PLANNING COMPLETED** - 待團隊 Review 後開始執行
