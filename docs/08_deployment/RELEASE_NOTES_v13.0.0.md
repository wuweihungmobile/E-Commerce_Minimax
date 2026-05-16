# Release Notes - v13.0.0

**發布日期**: 2026-06-16
**發布類型**: Minor (新增功能)
**Sprint**: Sprint 13
**專案**: E-Commerce Platform (B2B2C Multi-tenant)

---

## 新功能 ✨

### M18 知識管理系統 (Phase 2-B)

#### 文章版本控制
- **Task-M18-201**: 文章版本控制 API
  - `ArticleVersionController` - 版本歷史 REST API
  - `getArticleVersions()` - 取得文章版本列表
  - `getArticleVersion()` - 取得特定版本詳細內容
  - `createVersionSnapshot()` - 手動建立版本快照
  - `restoreVersion()` - 恢復到之前的版本

#### 排程發布功能
- **Task-M18-202**: 排程發布 API
  - `schedulePublish()` - 設定文章未來發布時間
  - `scheduledPublishAt` 欄位追蹤排程狀態

#### 知識庫搜尋增強
- **Task-M18-203**: 全文搜尋功能
  - 關鍵字搜尋文章標題和內容
  - 支援 `keyword` 參數過濾

### M08 評價系統 (Phase 1)

#### 評價資料模型
- **Task-M08-101**: 資料庫遷移
  - `V30__Create_Reviews_Table.sql` - 建立商品評價表
  - `V31__Create_Booking_Reviews_Table.sql` - 建立預訂評價表
  - 支援評價星級 1-5 分
  - 支援圖片多媒體欄位 (JSONB)
  - 軟刪除機制 (is_visible)

#### 評價 CRUD API
- **Task-M08-102**: 評價 API
  - `createReview()` - 買家對訂單提交評價
  - `createBookingReview()` - 買家對預訂提交評價
  - `replyToReview()` - 賣家/房東回覆評價
  - `getReviews()` - 查詢評價列表
  - `getRatingStats()` - 取得評價統計

#### 預訂評價服務
- **Task-M08-103**: 預訂評價 Backend
  - `BookingReviewService` - 預訂評價業務邏輯
  - `BookingReviewRepository` - 預訂評價資料存取
  - 房東回覆功能 (host_reply, host_replied_at)
  - 防止重複評價約束

---

## 改進 🚀

### 文章版本管理
- 版本號遞增機制 (version_number)
- 已發布版本標記 (is_published)
- 版本排序和置頂支援

### 搜尋功能優化
- 支援關鍵字全文搜尋
- 整合現有分類和標籤過濾

### 評價系統優化
- 評價後不可修改 (Immutable)
- 每個訂單/預訂只能評價一次
- 評價統計計算 (平均分、分布)

---

## 技術改進 🔧

### 資料庫遷移
- V30: 建立 `reviews` 表（商品評價）
- V31: 建立 `booking_reviews` 表（預訂評價）

### 測試覆蓋
- **M18KnowledgePhase2IntegrationTest**: 8 個測試案例
  - 版本歷史查詢
  - 排程發布
  - 版本恢復
  - 全文搜尋
- **M08ReviewIntegrationTest**: 8 個測試案例
  - 評價建立、查詢、回覆
  - 重複評價防止
  - 平均評分計算
- 總計: 16 個整合測試

---

## Bug 修復 🐛

| Issue | 描述 | PR |
|-------|------|-----|
| 無 | - | - |

---

## 重大變更 ⚠️

### 新 API 端點

| 模組 | 端點 | 方法 | 說明 |
|------|------|------|------|
| M18 | `/v2/knowledge/articles/{articleId}/versions` | GET | 取得版本歷史 |
| M18 | `/v2/knowledge/articles/{articleId}/versions/{versionNumber}` | GET | 取得特定版本 |
| M18 | `/v2/knowledge/articles/{articleId}/versions/snapshot` | POST | 建立版本快照 |
| M18 | `/v2/knowledge/articles/{articleId}/versions/{versionNumber}/restore` | POST | 恢復版本 |
| M18 | `/v2/knowledge/articles/{articleId}/schedule` | PUT | 排程發布 |
| M08 | `/v2/reviews` | POST | 建立商品評價 |
| M08 | `/v2/booking-reviews` | POST | 建立預訂評價 |
| M08 | `/v2/reviews/{reviewId}/reply` | POST | 回覆評價 |
| M08 | `/v2/reviews/listing/{listingId}` | GET | 查詢商品評價 |

### 資料表變更

| 表名 | 變更類型 | 說明 |
|------|----------|------|
| `reviews` | 新建 | 商品評價表 |
| `booking_reviews` | 新建 | 預訂評價表 |

---

## 已知問題
- 無

---

## 升級指南

### 前置條件
- Java 17+
- Node.js 18+
- PostgreSQL 14+
- Redis 6+

### 部署步驟
1. 執行資料庫遷移: `mvn flyway:migrate`
2. 重啟 Backend 服務
3. 驗證 API: `GET /v2/knowledge/articles/{id}/versions`
4. 驗證 API: `GET /v2/reviews/listing/{listingId}`

### 版本相容性
- 此版本為 Minor 版本，向下相容
- 現有 API 不受影響

---

## 相關連結

- [Sprint 13 Plan](docs/04_planning/SPRINT_13_PLAN.md)
- [Sprint 13 Tasks](docs/05_development/SPRINT_13_TASKS.md)
- [Sprint 13 Review](docs/05_development/SPRINT_13_REVIEW.md)

---

## 貢獻者

- Dev Team (Backend Development)
- QA Team (Testing & Validation)

---

**文件版本**: AISDLC v0.09
**驗證狀態**: ✅ 四方專家審議通過
**Sprint 13 狀態**: ✅ Ready for Release