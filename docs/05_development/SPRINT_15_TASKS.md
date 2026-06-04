# Sprint 15 任務分解 / Sprint 15 Tasks

> **Sprint 編號**: Sprint 15
> **期間**: 2026-06-29 ~ 2026-07-10 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-05-17
> **基於**: SPRINT_15_PLAN.md

---

## 📋 Sprint 15 任務概覽

| US ID | 標題 | SP | 負責人 | 狀態 |
|-------|------|-----|--------|------|
| US-001 | 商家回覆評價功能 | 5 | Dev | ✅ COMPLETED |
| US-002 | 結算單生成基礎 | 8 | Dev | ✅ COMPLETED |
| US-003 | 結算單審核流程 | 3 | Dev | ✅ COMPLETED |
| US-004 | 評價標記功能 | 2 | Dev | ✅ COMPLETED |
| **合計** | | **18 SP** | | |

---

## 🔴 US-001: 商家回覆評價功能 (5 SP)

### 任務清單

#### 1.1 資料庫 Migration
- [ ] Task 1.1.1: 建立 `V{version}__Create_Review_Replies_Table.sql` (30 min)
  - `review_replies` 表結構
  - `reply_id`, `review_id`, `tenant_id`, `user_id`, `content`, `created_at`
  - `tenant_id` + `review_id` 建立唯一約束

#### 1.2 Domain Layer
- [ ] Task 1.2.1: 建立 `ReviewReply` Entity (1h)
  - `@Entity`, `@Table` 標注
  - 欄位映射
  - 建構子與 Getter/Setter
- [ ] Task 1.2.2: 建立 `ReviewReplyRepository` (30 min)
  - `findByReviewId()` 方法
  - `existsByReviewId()` 方法 (防止重複回覆)

#### 1.3 Service Layer
- [ ] Task 1.3.1: 建立 `ReviewReplyService` (2h)
  - `createReply(reviewId, userId, content)` 方法
  - 防止重複回覆驗證
  - 整合 M09 通知買家
  - `getRepliesByReviewId(reviewId)` 方法

#### 1.4 API Layer
- [ ] Task 1.4.1: 建立 `ReviewReplyController` (1h)
  - `POST /v2/reviews/{reviewId}/replies` - 提交回覆
  - `GET /v2/reviews/{reviewId}/replies` - 取得回覆列表
  - 權限驗證 (StoreOwner/StoreStaff)
- [ ] Task 1.4.2: 更新 `ReviewController` (30 min)
  - 在取得評價詳情時一併返回回覆

#### 1.5 測試
- [ ] Task 1.5.1: 單元測試 - ReviewReplyService (1h)
- [ ] Task 1.5.2: 整合測試 - ReviewReplyController (1h)

**預估工時**: 7.5 小時

---

## 🟡 US-002: 結算單生成基礎 (8 SP)

### 任務清單

#### 2.1 資料庫 Migration
- [ ] Task 2.1.1: 建立 `V{version}__Create_Settlement_Statements_Table.sql` (30 min)
  - `settlement_statements` 表結構 (§8.2.9)
  - 索引: `(tenant_id, period_start DESC)`
  - 唯一約束: `(tenant_id, statement_number)`
- [ ] Task 2.1.2: 建立 `V{version}__Create_Credit_Notes_Table.sql` (30 min)
  - `credit_notes` 表結構 (§8.2.10)

#### 2.2 Domain Layer
- [ ] Task 2.2.1: 建立 `SettlementStatement` Entity (1.5h)
  - `@Entity`, `@Table` 標注
  - 欄位映射
  - ENUM: `PENDING / PENDING_REVIEW / APPROVED / REJECTED / PAID / FAILED`
- [ ] Task 2.2.2: 建立 `SettlementStatementRepository` (30 min)
  - `findByTenantIdAndPeriodStartBetween()` 方法
  - `findByIdAndTenantId()` 方法
  - `findByStatus()` 方法
- [ ] Task 2.2.3: 建立 `CreditNote` Entity (30 min)
  - 結構如 §8.2.10

#### 2.3 Service Layer
- [ ] Task 2.3.1: 建立 `SettlementService` (3h)
  - `generateWeeklyStatements()` - 每週一生成上週結算單
  - `calculateSettlementAmount(tenantId, periodStart, periodEnd)` - 結算金額計算
  - `getStatementById(statementId)` - 取得結算單詳情
  - `getStatementsByTenant(tenantId)` - 取得商家所有結算單
- [ ] Task 2.3.2: 建立 Scheduled Job (1h)
  - `@Scheduled(cron = "0 0 0 ? * MON")` - 週一凌晨執行
  - 呼叫 `settlementService.generateWeeklyStatements()`

#### 2.4 API Layer
- [ ] Task 2.4.1: 建立 `SettlementController` (1.5h)
  - `GET /v2/settlements` - 取得結算單列表
  - `GET /v2/settlements/{statementId}` - 取得結算單詳情
  - 商家僅能看到自己的結算單

#### 2.5 測試
- [ ] Task 2.5.1: 單元測試 - SettlementService (2h)
- [ ] Task 2.5.2: 整合測試 - SettlementController (1h)

**預估工時**: 11.5 小時

---

## 🟡 US-003: 結算單審核流程 (3 SP)

### 任務清單

#### 3.1 Service Layer
- [ ] Task 3.1.1: 更新 `SettlementService` (1h)
  - `submitForReview(statementId)` - 提交審核
  - `approveStatement(statementId, adminId)` - 批准
  - `rejectStatement(statementId, adminId, reason)` - 駁回
  - 狀態機驗證 (§7.2.1 規格)

#### 3.2 API Layer
- [ ] Task 3.2.1: 更新 `SettlementController` (1h)
  - `PUT /v2/admin/settlements/{statementId}/submit` - 提交審核
  - `PUT /v2/admin/settlements/{statementId}/approve` - 批准
  - `PUT /v2/admin/settlements/{statementId}/reject` - 駁回
  - Admin 角色驗證

#### 3.3 測試
- [ ] Task 3.3.1: 單元測試 - 狀態機轉換 (1h)
- [ ] Task 3.3.2: 整合測試 - Admin API (1h)

**預估工時**: 4 小時

---

## 🟢 US-004: 評價標記功能 (2 SP)

### 任務清單

#### 4.1 資料庫 Migration
- [ ] Task 4.1.1: 更新 `reviews` 表 (15 min)
  - 新增 `is_handled` BOOLEAN DEFAULT false
  - 新增 `handled_at` TIMESTAMP NULL
  - 新增 `handled_by` UUID FK → users.id NULL

#### 4.2 Service Layer
- [ ] Task 4.2.1: 更新 `ReviewService` (30 min)
  - `markAsHandled(reviewId, userId)` 方法
  - `getReviewsByHandlingStatus(isHandled)` 方法

#### 4.3 API Layer
- [ ] Task 4.3.1: 更新 `ReviewController` (30 min)
  - `PUT /v2/reviews/{reviewId}/handle` - 標記為已處理
  - Query param: `?handled=true/false`

#### 4.4 測試
- [ ] Task 4.4.1: 單元測試 - markAsHandled (30 min)
- [ ] Task 4.4.2: 整合測試 - 篩選功能 (30 min)

**預估工時**: 2.5 小時

---

## 📊 Sprint 15 工作量統計

| US | 任務數 | 預估工時 | SP |
|----|--------|----------|-----|
| US-001 | 5 | 7.5h | 5 |
| US-002 | 5 | 11.5h | 8 |
| US-003 | 3 | 4h | 3 |
| US-004 | 4 | 2.5h | 2 |
| **合計** | **17** | **25.5h** | **18 SP** |

---

## 🔄 Sprint 15 工作流程

### 開發順序

1. **US-001** (商家回覆評價) - 資料庫 → Domain → Service → API → 測試
2. **US-004** (評價標記) - 可與 US-001 同時進行（小功能）
3. **US-002** (結算單生成) - 資料庫 → Domain → Service → API → 測試
4. **US-003** (結算單審核) - 在 US-002 完成後進行

### 每日進度追蹤

| 日期 | 目標完成 |
|------|----------|
| Day 1-2 | US-001 完成 |
| Day 3 | US-004 完成 |
| Day 4-6 | US-002 完成 |
| Day 7 | US-003 完成 |
| Day 8-10 | Buffer + 測試完善 |

---

## ✅ Sprint 15 Definition of Done

- [ ] 所有 4 個 User Stories 的驗收標準 (AC) 完成
- [ ] `mvn compile` 編譯通過
- [ ] 單元測試覆蓋率 >= 80%
- [ ] 整合測試通過
- [ ] Flyway Migration 可正常執行
- [ ] API 文件更新完成
- [ ] Sprint 15 Review 文件產生

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-05-17
**最後更新**: 2026-05-17
**Sprint 15 狀態**: ✅ **DEVELOPMENT COMPLETED**