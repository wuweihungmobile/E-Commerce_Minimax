# Release Notes - v2026.06.04-01

**發布日期**: 2026-06-04
**發布類型**: Minor (新增功能 + 結算系統)
**Sprint**: Sprint 15
**Git Tag**: `v2026.06.04-01`
**基於 Commit**: `2135ed3` (hotfix: Sprint 15 Release CI 修復)
**專案**: E-Commerce Platform (B2B2C Multi-tenant)

---

## 🎯 發布摘要

Sprint 15 完成 **M08 評價系統 Phase 2**（商家回覆 + 標記）與 **M07 金流 Phase 2-C**（結算系統基礎），並修復 Sprint 15 PR #13 的 CI 問題。

### 關鍵指標

| 項目 | 數值 |
|------|------|
| User Stories 完成 | 4 / 4 (100%) |
| Story Points | 18 / 18 SP (100%) |
| 程式碼變更 | 33 檔案，1691 行新增 |
| Database Migrations | 3 個 (V34, V35, V36) |
| CI 狀態 | ✅ 全部代碼檢查通過 |
| 發布 PR | #13 + #14 (hotfix) |

---

## 新功能 ✨

### M08 評價系統 Phase 2 - 商家回覆 + 標記

#### 商家回覆評價功能 (US-001)
- **新增 API**:
  - `POST /v2/reviews/{reviewId}/replies` - 商家提交回覆
  - `GET /v2/reviews/{reviewId}/replies` - 查詢回覆列表
- **核心邏輯**:
  - `ReviewReplyService.createReply()` - 含防止重複回覆驗證 (`existsByReviewId`)
  - `BookingReviewService` - 整合 Booking 評價的回覆邏輯
- **權限控制**: 僅 StoreOwner / StoreStaff 可回覆
- **M09 通知整合**: 商家回覆後自動通知買家

#### 評價標記功能 (US-004)
- **新增 API**:
  - `PUT /v2/reviews/{reviewId}/handle` - 標記為已處理
  - Query param `?handled=true/false` - 篩選已處理/未處理
- **資料庫變更 (V34 Migration)**:
  - `is_handled` BOOLEAN DEFAULT false
  - `handled_at` TIMESTAMP NULL
  - `handled_by` UUID FK → users.id NULL
- **業務價值**: 商家可管理客服工作流程

### M07 金流 Phase 2-C - 結算系統基礎

#### 結算單生成 (US-002)
- **新增 API**:
  - `GET /v2/settlements` - 商家查詢自己租戶的結算單
  - `GET /v2/settlements/{statementId}` - 查詢結算單詳情
- **資料庫變更 (V35 + V36 Migration)**:
  - `settlement_statements` - 結算單主表
    - 包含訂單數、GMV、退款、平台抽成、結算金額
    - 索引: `(tenant_id, period_start DESC)`
    - 唯一約束: `(tenant_id, statement_number)`
  - `credit_notes` - 貸項憑證表
- **Scheduled Job**:
  - `@Scheduled(cron = "0 0 0 ? * MON")` - 週一凌晨自動生成上週結算單
- **狀態機**: PENDING / PENDING_REVIEW / APPROVED / REJECTED / PAID / FAILED

#### 結算單審核流程 (US-003)
- **新增 API (Admin)**:
  - `PUT /v2/settlements/{statementId}/submit` - 提交審核
  - `GET /v2/admin/settlements/pending` - 待審核列表
  - `PUT /v2/admin/settlements/{statementId}/approve` - 批准
  - `PUT /v2/admin/settlements/{statementId}/reject` - 駁回 (需填原因)
- **權限控制**: 僅 Admin 角色可操作
- **狀態轉換**: PENDING → PENDING_REVIEW → APPROVED/REJECTED

---

## 修復項目 🐛

### CI 修復 (來自 PR #14 hotfix)
- **TypeScript TS2339 錯誤** (`c5f9822`):
  - 修復 checkout workflow 中 `success` 屬性不存在的錯誤
- **ESLint no-explicit-any 錯誤** (`782e654`):
  - `frontend/src/app/dashboard/knowledge/page.tsx` - 移除 any 型別
  - `frontend/src/app/dashboard/media/page.tsx` - 移除 any 型別
  - `frontend/src/app/dashboard/notifications/page.tsx` - 移除 any 型別
- **整合測試清理** (`906e557`):
  - 移除 `IntegrationTestConfiguration` 未使用變數

---

## 資料庫 Migration 📦

| 版本 | 名稱 | 影響表 | 說明 |
|------|------|--------|------|
| V34 | Add_Review_Handled_Fields | reviews | 新增 3 個欄位用於標記功能 |
| V35 | Create_Settlement_Statements_Table | settlement_statements | 新建結算單主表 |
| V36 | Create_Credit_Notes_Table | credit_notes | 新建貸項憑證表 |

> ⚠️ **重要**: Migration 將在應用程式啟動時自動執行，無需手動操作。

---

## API 端點變更 📡

### 新增端點 (9 個)

| HTTP | 端點 | 用途 | 權限 |
|------|------|------|------|
| POST | `/v2/reviews/{id}/replies` | 建立評價回覆 | StoreOwner/Staff |
| GET | `/v2/reviews/{id}/replies` | 查詢回覆列表 | 公開 |
| PUT | `/v2/reviews/{id}/handle` | 標記為已處理 | StoreOwner/Staff |
| GET | `/v2/settlements` | 查詢結算單列表 | StoreOwner/Staff |
| GET | `/v2/settlements/{id}` | 查詢結算單詳情 | StoreOwner/Staff |
| PUT | `/v2/settlements/{id}/submit` | 提交審核 | StoreOwner/Staff |
| GET | `/v2/admin/settlements/pending` | 待審核列表 | Admin |
| PUT | `/v2/admin/settlements/{id}/approve` | 批准結算單 | Admin |
| PUT | `/v2/admin/settlements/{id}/reject` | 駁回結算單 | Admin |

### 變更端點
- `GET /v2/reviews/{id}` - 在評價詳情中一併返回回覆列表

---

## 技術變更 🔧

### 新增 Java 類別
- `BookingReviewController` - 評價回覆 API 控制器
- `BookingReviewDto` - 評價回覆 DTO
- `SettlementController` - 結算單 API 控制器
- `SettlementService` - 結算業務邏輯 (331 行)
- `SettlementStatement` - 結算單 Entity
- `SettlementStatementRepository` - 結算單 Repository
- `CreditNote` - 貸項憑證 Entity
- `CreditNoteRepository` - 貸項憑證 Repository

### 修改類別
- `ReviewService` - 加入回覆與標記邏輯 (+67 行)
- `BookingReviewService` - 整合回覆邏輯 (+40 行)
- `ReviewController` - 加入 handle 與 replies 整合 (+31 行)
- `Review` Entity - 新增 3 個標記欄位
- `ReviewRepository` - 加入 `existsByReviewId` 方法

### 測試更新
- `M08ReviewIntegrationTest` - 擴展測試 (+39 行)
- 8 個 E2E 測試 - 加入新的 import 與 @MockBean

---

## 驗證結果 ✅

### CI Pipeline 驗證 (PR #14)

| Check | 狀態 | 耗時 |
|-------|------|------|
| Secret Detection (Gitleaks) | ⚠️ 403 | 9s (環境權限問題，非代碼) |
| Dependency Scan | ✅ PASS | 11s |
| License Compliance | ✅ PASS | 19s |
| Backend Build & Test | ✅ PASS | 2m22s |
| Frontend Build & Test | ✅ PASS | 1m17s |
| SAST (Static Analysis) | ✅ PASS | 29s |
| E2E Tests | ✅ PASS | 2m11s |

> 注: Gitleaks Secret Detection 失敗為 GitHub Actions fork PR 權限問題（HTTP 403），並非代碼缺陷。歷次發布 (PR #10, #11, #12) 皆有同樣情況，皆已合併。

### 程式碼統計

| 指標 | 數值 |
|------|------|
| 變更檔案總數 | 33 |
| 新增行數 | +1691 |
| 刪除行數 | -23 |
| 新增 Migration | 3 (V34/V35/V36) |
| 新增 Service | 1 (SettlementService) |
| 新增 Controller | 2 (BookingReviewController, SettlementController) |

---

## 升級注意事項 ⚠️

### 資料庫
- ✅ 3 個 Migration (V34/V35/V36) 將在 Spring Boot 啟動時自動執行
- ✅ 無需手動 SQL 腳本
- ⚠️ 建議在 Staging 環境先執行驗證

### 環境變數
- 無新增環境變數
- 既有的 Redis / PostgreSQL 連線設定維持不變

### 設定檔
- 無需修改 application.yml
- 新增的 Scheduled Job 使用預設 cron 表達式

### 相依性
- 無新增 Maven / npm 套件
- 所有依賴維持不變

---

## 部署步驟 🚀

### 1. 拉取最新代碼
```bash
git fetch origin
git checkout v2026.06.04-01
```

### 2. 啟動後端 (自動執行 Migration)
```bash
cd backend
./mvnw spring-boot:run
# 確認 V34/V35/V36 成功執行
```

### 3. 啟動前端
```bash
cd frontend
npm install
npm run dev
```

### 4. 健康檢查
```bash
curl http://localhost:8080/api/actuator/health
curl http://localhost:3000/health
```

### 5. 驗證新功能
- 商家回覆評價：登入 StoreOwner → 任一評價 → 提交回覆
- 評價標記：登入 StoreOwner → 評價列表 → 標記為已處理
- 結算單：手動觸發 Scheduled Job 或等待週一 00:00

---

## 回滾計畫 🔙

若發現重大問題，可執行以下回滾步驟：

1. **回滾代碼**:
   ```bash
   git checkout v2026.05.16-02  # Sprint 14 上一個穩定版本
   ```

2. **回滾 Migration** (謹慎使用):
   - V34: 移除 `is_handled`, `handled_at`, `handled_by` 欄位
   - V35: DROP TABLE `settlement_statements`
   - V36: DROP TABLE `credit_notes`

3. **重啟服務** 並驗證健康狀態

> 詳細回滾流程請參考 [ROLLBACK_PLAN_SPRINT10.md](ROLLBACK_PLAN_SPRINT10.md)

---

## 相關連結 🔗

- **Git Tag**: [v2026.06.04-01](https://github.com/wuweihungmobile/E-Commerce_Minimax/releases/tag/v2026.06.04-01)
- **PR #13**: [Release v2026.06.04-01 - Sprint 15](https://github.com/wuweihungmobile/E-Commerce_Minimax/pull/13)
- **PR #14**: [hotfix: Sprint 15 Release CI 修復](https://github.com/wuweihungmobile/E-Commerce_Minimax/pull/14)
- **Sprint 15 Plan**: [SPRINT_15_PLAN.md](../04_planning/SPRINT_15_PLAN.md)
- **Sprint 15 Review**: [SPRINT_15_REVIEW.md](../05_development/SPRINT_15_REVIEW.md)
- **Sprint 15 Retrospective**: [SPRINT_15_RETRO.md](../05_development/SPRINT_15_RETRO.md)

---

## 簽核 ✍️

| 角色 | 確認狀態 | 日期 | 備註 |
|------|----------|------|------|
| Tech Lead | ⏳ 待確認 | - | - |
| PM/PO | ⏳ 待確認 | - | - |
| DevOps | ⏳ 待確認 | - | - |

---

**文件版本**: v1.0
**建立日期**: 2026-06-04
**負責人**: Claude Code (AI Assistant)
**基於 AISDLC**: v0.09
