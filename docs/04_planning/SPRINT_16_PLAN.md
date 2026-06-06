# Sprint 16 計劃 / Sprint 16 Plan

> **Sprint 編號**: Sprint 16
> **期間**: 2026-07-13 ~ 2026-07-24 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-06-04
> **基於**: [Sprint 15 Retrospective](../05_development/SPRINT_15_RETRO.md) + Sprint 15 完成狀態

---

## 🔴 前置條件確認

| 項目 | 確認結果 | 備註 |
|------|----------|------|
| Sprint 15 開發完成 | ✅ M08 評價 Phase 2 + M07 結算系統 | Sprint 15 Plan + Tasks + Review + Retro 完整 |
| Sprint 15 Retrospective | ✅ [SPRINT_15_RETRO.md](../05_development/SPRINT_15_RETRO.md) | 9 個 Action Items 產出 |
| ⚠️ Release 狀態 | ⏳ **未合併** | 跳過 Release 直接進入 Sprint 16（**建議 Sprint 16 Day 1 補做**） |
| Sprint 15 Action Items P0 | 4 個待補 UT | 已納入 Sprint 16 規劃 |
| Sprint 16 開始日期 | ✅ **2026-07-13** | 為 Sprint 15 (06-29~07-10) 後一週 |

> 🔴 **流程警告**：跳過 Release 違反 AISDLC 標準流程。Sprint 16 第一天強烈建議：
> 1. 確認 `release/v2026.06.04-01` 分支 CI 全綠
> 2. 合併至 `main` 並建立 tag `v2026.06.04-01`
> 3. 補完 Sprint 15 發布流程

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 16 |
| **開始日期** | 2026-07-13 (週一) |
| **結束日期** | 2026-07-24 (週五) |
| **Sprint 容量** | 18 SP |
| **規劃 SP** | 12 SP |
| **Buffer** | 6 SP (33%) |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 補足 Sprint 15 Retrospective 識別的 P0 測試缺口，並擴展 M08 評價系統支援多圖上傳（最多 9 張）+ Pre-commit 流程改進，提升整體程式碼品質與平台完整度。

### 具體目標

#### 🎯 品質強化 (P0 - 從 Sprint 15 Retro)

| 功能 | 優先級 | 對應 Retro Action |
|------|--------|------------------|
| ReviewReplyService 單元測試 | P0 | AI-001 |
| SettlementService 單元測試 | P0 | AI-002 |
| Scheduled Job 實測驗證 | P0 | AI-003 |
| M07SettlementIntegrationTest | P1 | AI-004 |

#### 🖼️ M08 多圖評價擴展 (P1)

| 功能 | 優先級 | 說明 |
|------|--------|------|
| 9 張上限 + 驗證 | P1 | 評價最多 9 張圖片 + 整合 M15 Media |
| 圖片管理 API | P1 | 新增/刪除/排序/替換 |

#### 🔧 流程改進 (P1)

| 功能 | 優先級 | 對應 Retro Action |
|------|--------|------------------|
| Pre-commit Hook (ESLint+tsc) | P1 | TI-001 |
| API_Index.md + TC_M07 更新 | P1 | DI-001, DI-003 |

---

## 3. Sprint 16 User Stories

### US-001: ReviewReplyService 單元測試 (1 SP)

**描述**:
作為 Dev，我需要為 Sprint 15 新增的商家回覆功能補上單元測試，以確保回覆建立、查詢、防重複等核心邏輯受到 UT 保護。

**驗收標準**:
- [ ] AC-001: `ReviewReplyService.createReply()` UT 通過（成功、防止重複、無效 reviewId）
- [ ] AC-002: `ReviewReplyService.getRepliesByReviewId()` UT 通過（空、單筆、多筆）
- [ ] AC-003: 覆蓋率 >= 80%

**技術備註**:
- 使用 Mockito + JUnit 5
- 不需要 Spring Context（純 Service 層）
- 涵蓋邊界：空字串、超長內容、null content

**依賴**: 無

---

### US-002: SettlementService 單元測試 (2 SP)

**描述**:
作為 Dev，我需要為 Sprint 15 的結算單核心邏輯補上單元測試，特別是金額計算（GMV、退款、平台抽成、結算金額），避免生產環境計算錯誤。

**驗收標準**:
- [ ] AC-001: `calculateSettlementAmount()` UT 通過（多場景：無退款/部分退款/全額退款）
- [ ] AC-002: `generateWeeklyStatements()` UT 通過（單租戶/多租戶/重複執行冪等性）
- [ ] AC-003: 狀態機 UT 通過（PENDING → PENDING_REVIEW → APPROVED/REJECTED）
- [ ] AC-004: 覆蓋率 >= 80%

**技術備註**:
- 重點測試金額計算的邊界（0 元、負數、超大金額）
- 驗證 Scheduled Job 邏輯（但實測在 US-003）

**依賴**: 無

---

### US-003: 結算單 Scheduled Job 實測驗證 (0.5 SP)

**描述**:
作為 Dev，我需要在 Staging 環境驗證週一 00:00 自動生成結算單的邏輯，確保生產環境不會出錯。

**驗收標準**:
- [ ] AC-001: Staging 環境手動觸發 Scheduled Job 成功
- [ ] AC-002: 結算單資料正確（訂單數、GMV、退款、平台抽成）
- [ ] AC-003: 多租戶場景驗證（每個 tenant 都有對應結算單）
- [ ] AC-004: 異常場景驗證（無訂單的租戶跳過生成）

**技術備註**:
- 使用應用程式內部的測試 endpoint 或環境變數觸發
- 不需修改 Production 代碼

**依賴**: US-002 (確保邏輯正確後再實測)

---

### US-004: M07 Settlement Integration Test (1 SP)

**描述**:
作為 Dev/QA，我需要為結算單 API 建立完整的整合測試，覆蓋商家查詢、Admin 審核流程。

**驗收標準**:
- [ ] AC-001: `M07SettlementIntegrationTest.java` 建立完成
- [ ] AC-002: `GET /v2/settlements` 測試通過（商家權限、租戶隔離）
- [ ] AC-003: `GET /v2/settlements/{id}` 測試通過（404、跨租戶禁止）
- [ ] AC-004: Admin 審核 API 測試通過（submit/approve/reject 狀態機）

**技術備註**:
- 使用 `@WebMvcTest` 配合 `@MockBean`
- 整合 Multi-tenancy 測試
- 涵蓋權限：BUYER 不可見、StoreOwner 僅見自己、Admin 見全部

**依賴**: US-002

---

### US-005: M08 多圖評價 9 張上限 + 驗證 (3 SP)

**描述**:
作為買家，我希望能上傳最多 9 張評價圖片，以便更完整地分享商品體驗。

**驗收標準**:
- [ ] AC-001: 評價上傳時圖片數量限制為 1-9 張
- [ ] AC-002: 超過 9 張回傳 400 錯誤並給予明確錯誤訊息
- [ ] AC-003: 整合 M15 Media 服務驗證每個圖片 URL 有效性
- [ ] AC-004: 不存在的 mediaId 回傳 400 錯誤

**技術備註**:
- 既有 `reviews.images` jsonb 欄位已存在（V30 Migration），無需新 Migration
- 既有 `Review.images` 與 `ReviewDto.images` 欄位已存在
- 主要工作：
  - ReviewDto 加上 `@Size(min = 0, max = 9)` Bean Validation
  - ReviewService.createReview() 加入圖片數量驗證
  - 整合 MediaService.verifyMediaExists() 驗證 URL
  - Custom exception: `E1088` (圖片數量超限)、`E1089` (圖片無效)

**依賴**:
- M15 Media Service 已完成（Sprint 9）
- M08 評價 Phase 1 已完成（Sprint 13）

---

### US-006: M08 多圖評價 圖片管理 API (3 SP)

**描述**:
作為買家，我希望能在評價建立後，新增、刪除、調整評價圖片，以修正上傳錯誤。

**驗收標準**:
- [ ] AC-001: `POST /v2/reviews/{id}/images` - 新增圖片（總數不超過 9 張）
- [ ] AC-002: `DELETE /v2/reviews/{id}/images/{imageId}` - 刪除單張圖片
- [ ] AC-003: `PUT /v2/reviews/{id}/images/order` - 調整圖片順序
- [ ] AC-004: 僅評價本人可操作（其他用戶 403）

**技術備註**:
- 圖片識別：使用 index 或 mediaId
- 權限驗證：Review.userId == currentUser.userId
- 順序儲存：在 jsonb 中 array index 即順序

**依賴**: US-005

---

### US-007: Pre-commit Hook ESLint + tsc (0.5 SP)

**描述**:
作為 Dev，我需要在 commit 前自動執行 ESLint 和 TypeScript 編譯檢查，避免 Sprint 15 的 2 個 CI 修復 commit 重演。

**驗收標準**:
- [ ] AC-001: husky + lint-staged 在 frontend/ 安裝完成
- [ ] AC-002: commit 前自動執行 `eslint --fix` 與 `tsc --noEmit`
- [ ] AC-003: 檢查失敗時 commit 被阻擋
- [ ] AC-004: 跳過方式（--no-verify）有文件說明

**技術備註**:
- 既有 `.github/workflows/ci.yml` 已有 ESLint 與 tsc 步驟
- 將相同指令搬到本地執行
- package.json 需新增 husky 相關 scripts

**依賴**: 無

---

### US-008: API Index + TC 文件更新 (1 SP)

**描述**:
作為文件維護者，我需要更新 API_Index.md 與測試案例文件，確保 Sprint 15 與 16 新增的功能有完整文件。

**驗收標準**:
- [ ] AC-001: `API_Index.md` 收錄 Sprint 15 新增 5 個端點 + Sprint 16 新增 4 個端點
- [ ] AC-002: `TC_M07_Settlement.md` 新建完成（涵蓋結算單/審核流程）
- [ ] AC-003: `TC_M08_Review.md` 擴展多圖評價測試案例

**技術備註**:
- 既有 `docs/02_architecture/API_Index.md` 為索引頁
- TC 文件位於 `docs/03_testing/`

**依賴**: US-005, US-006 (確保 API 完整)

---

## 4. 技術可行性評估

### 架構影響分析

| US | 複雜度 | 架構影響 | 技術風險 | SD 建議 |
|----|--------|----------|----------|---------|
| US-001 | 低 | 無 | 低 | 標準 Mockito 測試 |
| US-002 | 中 | 無 | 中 | 重點：金額計算邊界 |
| US-003 | 低 | 無 | 低 | Staging 環境準備 |
| US-004 | 中 | 無 | 低 | 整合既有 M08 IT 模式 |
| US-005 | 中 | 低 | 低 | 既有欄位擴展，無 Migration |
| US-006 | 中 | 中 | 中 | 新增 3 個 API 端點，權限設計需注意 |
| US-007 | 低 | 無 | 低 | 標準工具設置 |
| US-008 | 低 | 無 | 低 | 文件維護 |

### 新技術依賴

| 技術 | 用途 | 風險 |
|------|------|------|
| Mockito | US-001, US-002 UT | 低 - 既有依賴 |
| husky + lint-staged | US-007 Pre-commit | 低 - 業界標準 |
| MediaService 整合 | US-005, US-006 | 低 - 既有服務 |

### SD 觀點

> **Marcus (SD) 回饋**：
> - M08 多圖評價無需新 Migration，開發時間可節省
> - 圖片管理 API 的權限設計需明確（建議 US-006 開工前先做輕量級 Design Review）
> - 結算單 Scheduled Job 實測是關鍵，建議排在 Sprint 16 第二天

---

## 5. Story Points 估算

| US ID | 標題 | 複雜度 | 不確定性 | SP |
|-------|------|--------|----------|-----|
| US-001 | ReviewReplyService 單元測試 | 低 | 低 | 1 |
| US-002 | SettlementService 單元測試 | 中 | 中 | 2 |
| US-003 | Scheduled Job 實測驗證 | 低 | 低 | 0.5 |
| US-004 | M07SettlementIntegrationTest | 中 | 低 | 1 |
| US-005 | 多圖評價 9 張上限 + 驗證 | 中 | 中 | 3 |
| US-006 | 多圖評價 圖片管理 API | 中 | 中 | 3 |
| US-007 | Pre-commit Hook ESLint+tsc | 低 | 低 | 0.5 |
| US-008 | API Index + TC 文件更新 | 低 | 低 | 1 |
| **合計** | | | | **12 SP** |

**Story Points 估算參考**:
- 1: 半天內完成
- 2: 一天內完成
- 3: 2-3 天
- 5: 一週
- 8: 需拆分
- 13: 必須拆分

---

## 6. Sprint 承諾

**Sprint 目標**:
> 補足 Sprint 15 P0 測試缺口（4.5 SP）+ 擴展 M08 多圖評價功能（6 SP）+ 流程改進（1.5 SP），總計 12 SP，預留 6 SP buffer 處理 Release 補做與其他臨時需求。

**承諾的 User Stories**:

| 優先級 | US ID | 標題 | SP | 負責人 |
|--------|-------|------|----|-------|
| P0 | US-001 | ReviewReplyService 單元測試 | 1 | Dev |
| P0 | US-002 | SettlementService 單元測試 | 2 | Dev |
| P0 | US-003 | Scheduled Job 實測驗證 | 0.5 | Dev |
| P1 | US-004 | M07SettlementIntegrationTest | 1 | Dev/QA |
| P1 | US-005 | 多圖評價 9 張上限 + 驗證 | 3 | Dev |
| P1 | US-006 | 多圖評價 圖片管理 API | 3 | Dev |
| P1 | US-007 | Pre-commit Hook ESLint+tsc | 0.5 | Dev |
| P1 | US-008 | API Index + TC 文件更新 | 1 | Dev/QA |

**總 SP**: 12 SP
**團隊容量**: 18 SP
**填充率**: 67%
**Buffer**: 6 SP（33%）- 預留給 Release 補做 + 臨時需求

**風險**:
1. **SettlementService 331 行偏大** - 緩解措施：US-002 可順便拆分 `SettlementCalculator` 與 `SettlementStateMachine`
2. **多圖評價權限設計** - 緩解措施：US-006 開工前與 SD 對齊
3. **Release 補做時間** - 緩解措施：Sprint 16 Day 1 上午立即執行

---

## 7. 測試規劃

### 測試範圍

| US ID | 測試重點 |
|-------|----------|
| US-001 | ReviewReplyService 各種場景 |
| US-002 | SettlementService 金額計算邊界 + 狀態機 |
| US-003 | Staging 環境手動驗證 |
| US-004 | 結算單 API E2E + 權限測試 |
| US-005 | 多圖數量驗證 + Media 整合 |
| US-006 | 圖片 CRUD + 權限 |
| US-007 | Pre-commit hook 實測（故意犯錯） |
| US-008 | 文件完整性檢查 |

### 測試類型

- [x] 單元測試 (US-001, US-002, US-005, US-006)
- [x] 整合測試 (US-004)
- [x] E2E 測試 (US-006 圖片管理流程)
- [x] 手動驗證 (US-003 Staging, US-007 故意犯錯測試)

---

## 8. Sprint 16 Definition of Done

- [ ] 所有 8 個 User Stories 的驗收標準 (AC) 完成
- [ ] `mvn compile` 編譯通過
- [ ] `mvn test` 單元測試與整合測試全部通過
- [ ] 單元測試覆蓋率 >= 80%（新代碼）
- [ ] Flyway Migration 成功執行（US-005 無新 Migration）
- [ ] API 文件更新（API_Index.md）
- [ ] Sprint 16 Review 文件產生

---

## 9. 開發順序建議

### 建議執行順序

1. **Day 1 上午** - Release 補做（合併 release/v2026.06.04-01 + tag）
2. **Day 1 下午 - Day 2** - US-001 ReviewReplyService UT
3. **Day 2-3** - US-002 SettlementService UT（含拆分）
4. **Day 3 下午** - US-003 Scheduled Job 實測
5. **Day 4** - US-004 M07 IT + US-007 Pre-commit Hook
6. **Day 5-6** - US-005 多圖 9 張上限
7. **Day 7-8** - US-006 圖片管理 API
8. **Day 9** - US-008 文件更新
9. **Day 10** - Buffer + Sprint 16 Review

### 每日進度追蹤

| 日期 | 目標完成 |
|------|----------|
| Day 1 | Release 補做 + US-001 (50%) |
| Day 2 | US-001 + US-002 (50%) |
| Day 3 | US-002 + US-003 |
| Day 4 | US-004 + US-007 |
| Day 5 | US-005 (50%) |
| Day 6 | US-005 (50%) + 整合測試 |
| Day 7 | US-006 (50%) |
| Day 8 | US-006 (50%) + 整合測試 |
| Day 9 | US-008 文件 |
| Day 10 | Buffer + Review |

---

## 10. Sprint 16 與 AISDLC 流程整合

### 🔴 流程警告

> 本 Sprint 跳過了 Sprint 15 的 Release 階段。Sprint 16 第一天必須補上：
> 1. 合併 `release/v2026.06.04-01` 到 `main`
> 2. 建立 Git tag `v2026.06.04-01`
> 3. 更新 DEPLOYMENT 文件
> 否則 Sprint 15 的程式碼永遠不會進入 Production。

### Sprint 16 結束後流程

Sprint 16 結束後應依序完成：
1. ✅ Sprint 16 Review
2. ✅ Sprint 16 Retrospective
3. ⏳ Release Sprint 16（**不要再次跳過！**）
4. ⏳ Sprint 17 Planning

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-04
**基於**: Sprint 15 Retrospective + Retro Action Items
**驗證人**: Claude Code (AI Assistant)
**Sprint 16 狀態**: 📋 **PLANNING COMPLETED** - 待團隊 Review 後開始執行
