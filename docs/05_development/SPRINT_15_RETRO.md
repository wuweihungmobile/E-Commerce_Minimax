# Sprint 15 Retrospective 報告 / Sprint 15 Retrospective Report

> **Sprint 編號**: Sprint 15
> **期間**: 2026-05-17 ~ 2026-06-04 (實際執行)
> **原訂期間**: 2026-06-29 ~ 2026-07-10 (計劃期間)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **報告日期**: 2026-06-04
> **基於**: [SPRINT_15_REVIEW.md](SPRINT_15_REVIEW.md), [SPRINT_15_PLAN.md](../04_planning/SPRINT_15_PLAN.md), [SPRINT_15_TASKS.md](SPRINT_15_TASKS.md)

---

## 1. Sprint 15 回顧概覽

| 項目 | 內容 |
|------|------|
| **Sprint 目標** | 完成 M08 評價系統 Phase 2（商家回覆 + 標記）+ M07 金流 Phase 2-C（結算系統基礎） |
| **完成 SP** | 18 SP (100%) |
| **完成 US** | 4 US (100%) |
| **代碼變更** | 33 檔案，1691 行新增，23 行刪除 |
| **Migration 變更** | V34 (評價標記), V35 (結算單), V36 (貸項憑證) |
| **CI 修復 Commits** | 2 個 (TS2339, no-explicit-any) |
| **團隊** | 2 人 Dev Team |

---

## 2. 做得好的地方 (What Went Well)

### 2.1 目標達成

| 項目 | 說明 | 證據 |
|------|------|------|
| **100% US 完成** | 4 個 User Story 全部達成 AC | US-001/002/003/004 全部 ✅ |
| **100% SP 達成** | 18 SP 全部完成（規劃容量 30 SP，填充率 60%） | Sprint 容量管理得當 |
| **DoD 全部滿足** | 7 項 DoD 標準全部通過 | 編譯、Migration、API、測試皆達標 |
| **模組進展** | M08 評價系統達到 100%、M07 金流 Phase 2-C 完成 | 模組完成度報告 |

### 2.2 技術實現

| 項目 | 說明 | 證據 |
|------|------|------|
| **M09 通知整合** | 商家回覆時自動通知買家 | NotificationService 觸發邏輯已實作 |
| **狀態機設計** | 結算單審核狀態機嚴謹 | PENDING → PENDING_REVIEW → APPROVED/REJECTED |
| **重複回覆防護** | `existsByReviewId` 防止重複 | ReviewRepository 已新增方法 |
| **Migration 版本控制** | V34/V35/V36 依序正確 | 連續三個 Migration 無衝突 |
| **Scheduled Job 設計** | 週一 00:00 自動生成結算單 | `@Scheduled(cron = "0 0 0 ? * MON")` |
| **多層 Controller 設計** | Settlement 模組獨立 sub-package | `/api/controller/settlement/` |

### 2.3 流程管理

| 項目 | 說明 | 證據 |
|------|------|------|
| **Commit 訊息規範** | 採用 Conventional Commits | `feat(M08):`, `fix(ci):`, `chore(test):` |
| **任務分解細緻** | 每個 US 都有明確的 Migration/Domain/Service/API 階段 | SPRINT_15_TASKS.md |
| **文件驅動開發** | Plan + Tasks + Review + Retrospective 完整產出 | docs/ 結構完整 |

---

## 3. 需改進的地方 (What Could Be Improved)

### 3.1 技術面

| 問題 | 影響 | 建議 | 優先級 |
|------|------|------|--------|
| **缺乏 ReviewReplyService 單元測試** | 商家回覆核心邏輯無 UT 保護 | 補上 `ReviewReplyServiceTest.java` | P0 |
| **缺乏 SettlementService 單元測試** | 結算金額計算邏輯複雜卻無 UT | 補上 `SettlementServiceTest.java` (calculateSettlementAmount 為主) | P0 |
| **缺乏結算單整合測試** | M07 結算功能無專屬 IT | 新增 `M07SettlementIntegrationTest.java` | P1 |
| **Scheduled Job 未實測** | 週一 00:00 自動生成尚未在 Staging 驗證 | Sprint 16 第一天做一次手動觸發測試 | P0 |
| **2 個 CI 修復 Commit** | TypeScript TS2339 + ESLint no-explicit-any | 應在 Pre-commit hook 加上 ESLint + tsc 檢查 | P1 |
| **M09 通知 E2E 測試** | 通知觸發邏輯無 E2E 驗證 | 新增 Notification E2E 測試 | P2 |
| **Controller package 不一致** | BookingReviewController 在 `/api/controller/`，SettlementController 在 `/api/controller/settlement/` | 制定 Controller 組織規範，後續統一 | P2 |

### 3.2 流程面

| 問題 | 影響 | 建議 | 優先級 |
|------|------|------|--------|
| **計劃 vs 實際時間錯位** | 計劃期間 2026-06-29~07-10，實際完成 2026-06-04 | 提前 25 天完成，建議在 Plan 中標註「預計提前完成」 | P2 |
| **Sprint 15 文件未即時更新** | Review/Retro 在最後才產出 | 建議 Sprint 中期先寫初版 Review 草稿 | P2 |
| **缺少 Retrospective 議程文件** | 沒有像 Sprint 12 的 REVIEW_MEETING_AGENDA | 參考 SPRINT_12_REVIEW_MEETING_AGENDA.md 補上 | P3 |

### 3.3 文件面

| 問題 | 影響 | 建議 | 優先級 |
|------|------|------|--------|
| **API Spec 缺更新** | Sprint 15 新增 5+ 個 API 端點未寫進 `API_Index.md` | Sprint 16 補上 Settlement + Review Reply API 文件 | P1 |
| **E2E 測試案例缺更新** | 新增的回覆/標記/結算 API 沒有對應 TC | 在 `docs/03_testing/TC_M08_*.md` 和 `TC_M07_*.md` 補上 | P1 |

---

## 4. 行動項目 (Action Items)

### 4.1 Sprint 16 必須實現

| ID | 行動項目 | 負責人 | 優先級 | 預估 SP | 狀態 |
|----|----------|--------|--------|---------|------|
| AI-001 | ReviewReplyService 單元測試 | Dev | P0 | 1 | 待實現 |
| AI-002 | SettlementService 單元測試 (金額計算為主) | Dev | P0 | 2 | 待實現 |
| AI-003 | Scheduled Job 手動觸發測試 (Staging) | Dev | P0 | 0.5 | 待驗證 |
| AI-004 | M07SettlementIntegrationTest | Dev/QA | P1 | 1 | 待實現 |

### 4.2 技術改進

| ID | 行動項目 | 負責人 | 優先級 | 預估 SP | 狀態 |
|----|----------|--------|--------|---------|------|
| TI-001 | Pre-commit hook 加上 ESLint + tsc 檢查 | Dev | P1 | 0.5 | 待實現 |
| TI-002 | Controller package 組織規範文件化 | SD/SA | P2 | 0.5 | 評估中 |
| TI-003 | Notification E2E 測試 (M09 整合驗證) | QA | P2 | 1 | 評估中 |

### 4.3 文件改進

| ID | 行動項目 | 負責人 | 優先級 | 狀態 |
|----|----------|--------|--------|------|
| DI-001 | 更新 `API_Index.md` 收錄 Sprint 15 新增 API 端點 | Dev | P1 | 待更新 |
| DI-002 | 更新 `TC_M08_*.md` 新增回覆/標記測試案例 | QA | P1 | 待更新 |
| DI-003 | 新增 `TC_M07_Settlement.md` 結算測試案例 | QA | P1 | 待建立 |

### 4.4 流程改進

| ID | 行動項目 | 負責人 | 優先級 | 狀態 |
|----|----------|--------|--------|------|
| PI-001 | Sprint 16 規劃時參考本 Retro Action Items | PM/PO | P0 | 待執行 |
| PI-002 | 將「CI 修復循環次數」列入 Sprint 健康度指標 | PM/PO | P2 | 評估中 |

---

## 5. Sprint 15 數據分析

### 5.1 Velocity 分析

| Sprint | 規劃 SP | 完成 SP | Velocity | 主要內容 |
|--------|---------|---------|----------|----------|
| Sprint 12 | 30 SP | 30 SP | 1.0 | M18 知識管理 Phase 2-A + M07 Payment Mock + M09 通知模板 |
| Sprint 13 | 25 SP | 25 SP | 1.0 | M18 Phase 2-B + M08 評價系統 Phase 1 |
| Sprint 14 | 21 SP | 21 SP | 1.0 | M09 MQ + M07 Stripe + M18 FAQ + Refund |
| **Sprint 15** | **18 SP** | **18 SP** | **1.0** | **M08 Phase 2 + M07 結算 + 標記** |
| **平均** | **94 SP** | **94 SP** | **1.0** | **穩定的高產出** |

### 5.2 Sprint 15 Commit 統計

| 類型 | 數量 | Commit Hash | 說明 |
|------|------|-------------|------|
| Feature | 1 | `70d9310` | M08 + M07 主要功能（1691 行新增） |
| CI Fix | 2 | `c5f9822`, `782e654` | TypeScript TS2339 + ESLint no-explicit-any |
| Chore | 1 | `906e557` | IntegrationTestConfiguration 清理 |
| **合計** | **4** | | **總計 33 檔案變更** |

### 5.3 程式碼品質指標

| 指標 | 數值 | 評估 |
|------|------|------|
| 平均檔案大小 | ~50 行/檔案 | ✅ 良好 |
| Service 層最大檔案 | SettlementService 331 行 | ⚠️ 偏大，建議拆分 |
| Controller 層最大檔案 | SettlementController 107 行 | ✅ 合理 |
| Migration 連續性 | V34→V35→V36 無跳號 | ✅ 良好 |
| Test 更新範圍 | 8 個 E2E + 1 個 IT | ⚠️ 缺乏新功能專屬 UT |

### 5.4 模組完成度更新

| 模組 | Phase 1 | Phase 2-A | Phase 2-B | Phase 2-C | 總體 |
|------|---------|-----------|-----------|-----------|------|
| M08 評價系統 | ✅ Sprint 13 | - | ✅ Sprint 15 | - | 100% |
| M07 金流 | ✅ Sprint 12 | ✅ Sprint 13 | ✅ Sprint 14 | ✅ Sprint 15 | Phase 2-C ✅ |
| M09 通知系統 | - | ✅ Sprint 12 | ✅ Sprint 14 | - | 100% |
| M18 知識管理 | ✅ Sprint 12 | ✅ Sprint 13 | ✅ Sprint 14 | - | 100% |

---

## 6. 團隊回饋

### 6.1 Dev (David) 回饋

> "Sprint 15 同時處理 M08 評價回覆 + M07 結算系統，程式碼量較大（1691 行），但功能模組化設計讓分工明確。SettlementService 邏輯集中在單一檔案（331 行）後續可考慮拆分為 `SettlementCalculator`、`SettlementGenerator`、`SettlementReviewer` 三個子服務。Pre-commit hook 加上 ESLint 應該能避免這次 2 個 CI 修復 commit。"

### 6.2 QA (Quincy) 回饋

> "M08ReviewIntegrationTest 已擴展（+39 行），但缺少對新功能的專屬單元測試。建議 Sprint 16 補上 ReviewReplyService 與 SettlementService 的單元測試，並建立 TC_M07_Settlement.md 測試案例文件。E2E 測試中 8 個 Controller 都有更新但只增加 2 行，建議確認是否完整覆蓋新的回覆/標記/結算場景。"

### 6.3 PM/PO (Victoria) 回饋

> "Sprint 15 提前 25 天完成（2026-06-04 vs 2026-06-29 計劃），表示團隊產能強勁。M08 評價回覆是商家期待已久的功能，M07 結算系統為 Phase 2-C 鋪墊良好基礎。建議 Sprint 16 將 M08 多圖評價（P2）和 M10 IM 通訊準備納入評估。"

### 6.4 SD (Marcus) 回饋

> "Settlement 模組的 Controller 採用 sub-package 設計（`/api/controller/settlement/`）是好的實踐，但 BookingReviewController 仍放在 `/api/controller/` 根目錄，建議制定 Controller 組織規範統一標準。State machine 在 SettlementService 內實作良好，後續可考慮抽出 `SettlementStateMachine` 工具類供其他模組參考。"

---

## 7. 經驗教訓 (Lessons Learned)

### 7.1 流程面

1. **提前完成的影響**：本次 Sprint 提前 25 天完成，建議下次在 Plan 中標註「預計提前 X 天完成」並提前通知利害關係人。
2. **CI 修復的時間成本**：2 個 CI 修復 commit 雖然規模小，但會打斷開發節奏。Pre-commit hook 是必要的投資。
3. **文件驅動的價值**：本 Sprint 嚴格遵循 Plan → Tasks → Code → Review → Retro 的順序，文件齊全度極高。

### 7.2 技術面

1. **狀態機的內聚性**：SettlementService 包含 331 行（含狀態機邏輯），後續可考慮拆分。
2. **測試覆蓋率不平衡**：Integration 測試完整，但新功能的 Unit 測試缺乏。建議下個 Sprint 補上。
3. **跨模組整合**：M09 通知整合在 Review 階段才被提及為「未完整 E2E 測試」，建議在 Plan 時即列出整合測試項目。

### 7.3 團隊面

1. **2 人團隊的極限**：本次 18 SP 對 2 人團隊已是滿載，建議 Sprint 16 維持 18 SP 或更低。
2. **Buffer 使用**：規劃 30 SP 容量，實際完成 18 SP（60% 填充率），下次可考慮提升到 70-80%。

---

## 8. 下一步建議

### 8.1 立即行動（合併 Release 之前）

1. **🔴 建立 Release Tag 候選**：確認 `release/v2026.06.04-01` 分支 CI 全綠
2. **🔴 合併 Release 分支**：將 `release/v2026.06.04-01` 合併至 `main`
3. **🔴 建立 Git Tag**：`v2026.06.04-01`

### 8.2 Sprint 16 準備

1. **優先實現**：M08 多圖評價（M08 擴展）+ M10 IM 前期準備
2. **測試完善**：補上 ReviewReplyService + SettlementService 單元測試（4 SP）
3. **文件補完**：更新 API_Index.md、新增 TC_M07_Settlement.md（2 SP）
4. **流程改進**：建立 Pre-commit hook 規範（1 SP）

### 8.3 長期改進

1. **拆分 SettlementService**：331 行偏大，建議拆分為 3 個子服務
2. **Controller 組織規範**：制定 `/api/controller/{module}/` 統一規範
3. **自動化文件**：Swagger/OpenAPI 自動生成 API Spec

---

## 9. 確認簽核

| 角色 | 確認狀態 | 簽核日期 | 備註 |
|------|----------|----------|------|
| Human User | ⏳ 待確認 | - | - |
| PM/PO (Victoria) | ⏳ 待確認 | - | - |
| SA (Amanda) | ⏳ 待確認 | - | - |
| SD (Marcus) | ⏳ 待確認 | - | - |
| Dev (David) | ⏳ 待確認 | - | - |
| QA (Quincy) | ⏳ 待確認 | - | - |

---

**文件版本**: v1.0
**最後更新**: 2026-06-04
**基於 AISDLC**: v0.09

## 📝 文件修訂紀錄

| 版本 | 日期 | 修改內容 | 確認人 |
|------|------|----------|--------|
| v1.0 | 2026-06-04 | 初始版本（Sprint 15 Retrospective） | Claude Code |
