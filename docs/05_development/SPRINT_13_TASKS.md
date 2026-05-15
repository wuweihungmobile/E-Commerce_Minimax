# Sprint 13 任務 / Sprint 13 Tasks

> **Sprint 編號**: Sprint 13
> **期間**: 2026-06-15 ~ 2026-06-26
> **總 SP**: 21 SP
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **基於**: SPRINT_13_PLAN.md

---

## 📋 任務總覽

| 類別 | 任務數 | SP |
|------|--------|-----|
| Backend Tasks | 6 | 16 SP |
| Frontend Tasks | 2 | 3 SP |
| QA/IT Tasks | 2 | 2 SP |
| **合計** | **10** | **21 SP** |

---

## 🔴 Backend Tasks (6 Tasks / 16 SP)

### US-001: 知識庫文章進階管理 (5 SP)

#### Task-001: 知識庫文章版本控制 Backend

| 欄位 | 內容 |
|------|------|
| **Task ID** | Task-M18-201 |
| **US ID** | US-001 |
| **標題** | Backend: 知識庫文章版本控制 |
| **預估工時** | 8h |
| **SP** | 3 |
| **負責人** | Dev |
| **優先級** | P0 |

**任務描述**:
- 建立文章版本相關資料表（可選：獨立版本表或直接在文章表新增版本欄位）
- 實作版本創建邏輯（每次文章更新時自動建立版本記錄）
- 實作版本查詢 API
- 實作版本恢復 API

**技術備註**:
- 版本恢復為 soft update，不刪除新版本
- 需要關聯 article_id, version, content, created_at

**產出檔案**:
- `backend/src/main/java/com/nextkey/ecommerce/core/knowledge/ArticleVersion.java`
- `backend/src/main/java/com/nextkey/ecommerce/domain/repository/knowledge/ArticleVersionRepository.java`
- `backend/src/main/java/com/nextkey/ecommerce/core/knowledge/ArticleVersionService.java`
- `backend/src/main/java/com/nextkey/ecommerce/api/controller/knowledge/ArticleVersionController.java`

**API Endpoints**:
- `GET /api/v2/knowledge/articles/{articleId}/versions` - 取得版本歷史
- `POST /api/v2/knowledge/articles/{articleId}/versions/{versionId}/restore` - 恢復版本
- `GET /api/v2/knowledge/articles/{articleId}/versions/{versionId}` - 取得特定版本

---

#### Task-002: 文章發布排程功能 Backend

| 欄位 | 內容 |
|------|------|
| **Task ID** | Task-M18-202 |
| **US ID** | US-001 |
| **標題** | Backend: 文章發布排程功能 |
| **預估工時** | 4h |
| **SP** | 2 |
| **負責人** | Dev |
| **優先級** | P0 |

**任務描述**:
- 在 Article 表新增 `scheduled_publish_at` 和 `published_at` 欄位
- 實作 Scheduler 或 Cron Job 定期檢查待發布文章
- 實作排程查詢 API

**技術備註**:
- Phase 1 可使用 @Scheduled 註解
- 未來可遷移到 MQ 或外部 Scheduler

**產出檔案**:
- 修改 `backend/src/main/java/com/nextkey/ecommerce/domain/model/knowledge/KnowledgeArticle.java`
- 修改 `backend/src/main/java/com/nextkey/ecommerce/core/knowledge/KnowledgeBaseService.java`
- 新增 `backend/src/main/java/com/nextkey/ecommerce/core/knowledge/ArticleScheduler.java`

**API Endpoints**:
- `PUT /api/v2/knowledge/articles/{articleId}/schedule` - 設定發布排程
- `GET /api/v2/knowledge/articles?status=SCHEDULED` - 查詢待發布文章

---

### US-002: 知識庫搜尋增強 (3 SP)

#### Task-003: 知識庫全文搜尋 Backend

| 欄位 | 內容 |
|------|------|
| **Task ID** | Task-M18-203 |
| **US ID** | US-002 |
| **標題** | Backend: 知識庫全文搜尋 |
| **預估工時** | 4h |
| **SP** | 2 |
| **負責人** | Dev |
| **優先級** | P1 |

**任務描述**:
- 實作文章全文搜尋（使用資料庫 LIKE 或 ILIKE）
- 支援標籤過濾
- 實作搜尋結果高亮標記

**技術備註**:
- 搜尋效能優化：注意建立適當索引
- 可考慮未來遷移到 ElasticSearch

**產出檔案**:
- 修改 `backend/src/main/java/com/nextkey/ecommerce/core/knowledge/KnowledgeBaseService.java`
- 新增搜尋相關 Repository 方法

**API Endpoints**:
- `GET /api/v2/knowledge/articles/search?q={keyword}&tags={tag1,tag2}` - 搜尋文章
- 回傳結果包含 `highlight` 欄位標記關鍵字

---

### US-003: 評價系統資料模型建立 (8 SP)

#### Task-004: 評價系統資料庫遷移

| 欄位 | 內容 |
|------|------|
| **Task ID** | Task-M08-101 |
| **US ID** | US-003 |
| **標題** | DB: 建立評價系統資料表 |
| **預估工時** | 4h |
| **SP** | 3 |
| **負責人** | Dev |
| **優先級** | P0 |

**任務描述**:
- 建立 V30: reviews 表（商品評價）
- 建立 V31: booking_reviews 表（民宿預訂評價）
- 建立正確的索引和約束

**資料庫遷移**:
```sql
-- V30: reviews 表
CREATE TABLE reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES users(id),
    listing_id UUID NOT NULL REFERENCES listings(id),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(200),
    content TEXT,
    images TEXT[], -- Array of image URLs
    is_public BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, user_id, listing_id) -- 防止重複評價
);
CREATE INDEX idx_reviews_listing_id ON reviews(listing_id);
CREATE INDEX idx_reviews_tenant_id ON reviews(tenant_id);

-- V31: booking_reviews 表
CREATE TABLE booking_reviews (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES tenants(id),
    user_id UUID NOT NULL REFERENCES users(id),
    booking_id UUID NOT NULL REFERENCES bookings(id),
    rating INTEGER NOT NULL CHECK (rating >= 1 AND rating <= 5),
    title VARCHAR(200),
    content TEXT,
    images TEXT[],
    host_reply TEXT,
    host_reply_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(tenant_id, user_id, booking_id) -- 防止重複評價
);
CREATE INDEX idx_booking_reviews_booking_id ON booking_reviews(booking_id);
CREATE INDEX idx_booking_reviews_tenant_id ON booking_reviews(tenant_id);
```

---

#### Task-005: 評價 Model + Repository

| 欄位 | 內容 |
|------|------|
| **Task ID** | Task-M08-102 |
| **US ID** | US-003 |
| **標題** | Backend: 評價 Model + Repository |
| **預估工時** | 4h |
| **SP** | 3 |
| **負責人** | Dev |
| **優先級** | P0 |

**任務描述**:
- 建立 Review 和 BookingReview Model 類別
- 建立 ReviewRepository 和 BookingReviewRepository
- 實作基本 CRUD 方法

**產出檔案**:
- `backend/src/main/java/com/nextkey/ecommerce/domain/model/review/Review.java`
- `backend/src/main/java/com/nextkey/ecommerce/domain/model/review/BookingReview.java`
- `backend/src/main/java/com/nextkey/ecommerce/domain/repository/review/ReviewRepository.java`
- `backend/src/main/java/com/nextkey/ecommerce/domain/repository/review/BookingReviewRepository.java`

---

#### Task-006: 評價 Service 基礎

| 欄位 | 內容 |
|------|------|
| **Task ID** | Task-M08-103 |
| **US ID** | US-003 |
| **標題** | Backend: 評價 Service 基礎 |
| **預估工時** | 4h |
| **SP** | 2 |
| **負責人** | Dev |
| **優先級** | P0 |

**任務描述**:
- 建立 ReviewService 和 BookingReviewService
- 實作防止重複評價邏輯
- 實作平均評分計算

**技術備註**:
- 評價提交後不可修改（Immutable）
- 計算 Average Rating 時需考慮 deleted 記錄

**產出檔案**:
- `backend/src/main/java/com/nextkey/ecommerce/core/review/ReviewService.java`
- `backend/src/main/java/com/nextkey/ecommerce/core/review/BookingReviewService.java`

---

### US-004: 評價 CRUD API (5 SP)

#### Task-007: 評價 API Controller

| 欄位 | 內容 |
|------|------|
| **Task ID** | Task-M08-104 |
| **US ID** | US-004 |
| **標題** | Backend: 評價 API Controller |
| **預估工時** | 6h |
| **SP** | 3 |
| **負責人** | Dev |
| **優先級** | P1 |

**任務描述**:
- 建立 ReviewController 和 BookingReviewController
- 實作評價 CRUD API
- 實作店家回覆 API

**產出檔案**:
- `backend/src/main/java/com/nextkey/ecommerce/api/controller/review/ReviewController.java`
- `backend/src/main/java/com/nextkey/ecommerce/api/controller/review/BookingReviewController.java`
- `backend/src/main/java/com/nextkey/ecommerce/api/dto/review/`

**API Endpoints**:
- `POST /api/v2/reviews` - 提交商品評價
- `GET /api/v2/reviews?listing_id={id}` - 取得商品評價列表
- `POST /api/v2/booking-reviews` - 提交預訂評價
- `GET /api/v2/booking-reviews?booking_id={id}` - 取得預訂評價
- `PUT /api/v2/reviews/{id}/reply` - 店家回覆評價
- `PUT /api/v2/booking-reviews/{id}/reply` - 房東回覆預訂評價

---

### Task-008: 資料庫遷移 Script

| 欄位 | 內容 |
|------|------|
| **Task ID** | Task-M18-204 |
| **標題** | DB: 知識庫文章版本控制遷移 |
| **預估工時** | 2h |
| **SP** | 1 |
| **負責人** | Dev |
| **優先級** | P0 |

**任務描述**:
- 建立 V32: article_versions 表（文章版本歷史）
- 修改 V33: knowledge_articles 表新增 scheduled_publish_at, published_at

**資料庫遷移**:
```sql
-- V32: article_versions 表
CREATE TABLE article_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id UUID NOT NULL REFERENCES knowledge_articles(id) ON DELETE CASCADE,
    version_number INTEGER NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by UUID REFERENCES users(id)
);
CREATE INDEX idx_article_versions_article_id ON article_versions(article_id);

-- V33: 修改 knowledge_articles 表
ALTER TABLE knowledge_articles ADD COLUMN scheduled_publish_at TIMESTAMP;
ALTER TABLE knowledge_articles ADD COLUMN published_at TIMESTAMP;
```

---

## 🔵 Frontend Tasks (2 Tasks / 3 SP)

### Task-FE-001: 知識庫搜尋增強 Frontend

| 欄位 | 內容 |
|------|------|
| **Task ID** | Task-M18-FE-201 |
| **標題** | FE: 知識庫搜尋增強 |
| **預估工時** | 4h |
| **SP** | 2 |
| **負責人** | FE Dev |
| **優先級** | P1 |

**任務描述**:
- 實作搜尋輸入框（帶防抖動）
- 實作標籤過濾 UI
- 實作搜尋結果高亮顯示

**產出檔案**:
- `frontend/src/app/dashboard/knowledge/page.tsx` (更新)
- `frontend/src/lib/services/knowledge.ts` (更新)

---

### Task-FE-002: 評價列表元件

| 欄位 | 內容 |
|------|------|
| **Task ID** | Task-M08-FE-101 |
| **標題** | FE: 評價列表元件 |
| **預估工時** | 4h |
| **SP** | 1 |
| **負責人** | FE Dev |
| **優先級** | P2 |

**任務描述**:
- 建立評價列表元件
- 實作評價星級顯示
- 實作店家回覆顯示

**產出檔案**:
- `frontend/src/components/reviews/ReviewList.tsx`
- `frontend/src/components/reviews/ReviewCard.tsx`

---

## 🟡 QA/IT Tasks (2 Tasks / 2 SP)

### Task-QA-001: M18 知識管理整合測試

| 欄位 | 內容 |
|------|------|
| **Task ID** | Task-M18-IT-201 |
| **標題** | IT: M18 知識管理 Phase 2-B 整合測試 |
| **預估工時** | 4h |
| **SP** | 1 |
| **負責人** | QA |
| **優先級** | P1 |

**任務描述**:
- 文章版本控制整合測試
- 排程發布整合測試
- 搜尋增強整合測試

**產出檔案**:
- `backend/src/test/java/com/nextkey/ecommerce/integration/M18KnowledgePhase2IntegrationTest.java`

---

### Task-QA-002: M08 評價系統整合測試

| 欄位 | 內容 |
|------|------|
| **Task ID** | Task-M08-IT-101 |
| **標題** | IT: M08 評價系統整合測試 |
| **預估工時** | 4h |
| **SP** | 1 |
| **負責人** | QA |
| **優先級** | P1 |

**任務描述**:
- 評價 CRUD 整合測試
- 重複評價防止測試
- 平均評分計算測試

**產出檔案**:
- `backend/src/test/java/com/nextkey/ecommerce/integration/M08ReviewIntegrationTest.java`

---

## ✅ Definition of Done

每個 Task 完成的標準：

| 檢查項目 | 說明 |
|---------|------|
| 代碼完成 | 功能實作完成且通過 Code Review |
| 編譯通過 | `mvn compile` 無錯誤 |
| 單元測試 | 新功能單元測試覆蓋率 >= 80% |
| IT 測試 | 整合測試檔案存在且可執行 |
| 文件更新 | API 文件或註解已更新 |

---

## 📊 任務狀態追蹤

### Backend Tasks

| Task ID | 任務名稱 | SP | 狀態 |
|---------|----------|-----|------|
| Task-M18-201 | 知識庫文章版本控制 | 3 | ⏳ Pending |
| Task-M18-202 | 文章發布排程功能 | 2 | ⏳ Pending |
| Task-M18-203 | 知識庫全文搜尋 | 2 | ⏳ Pending |
| Task-M18-204 | 資料庫遷移 Script | 1 | ⏳ Pending |
| Task-M08-101 | 評價系統資料表 | 3 | ⏳ Pending |
| Task-M08-102 | 評價 Model + Repository | 3 | ⏳ Pending |
| Task-M08-103 | 評價 Service 基礎 | 2 | ⏳ Pending |
| Task-M08-104 | 評價 API Controller | 3 | ⏳ Pending |

### Frontend Tasks

| Task ID | 任務名稱 | SP | 狀態 |
|---------|----------|-----|------|
| Task-M18-FE-201 | 知識庫搜尋增強 | 2 | ⏳ Pending |
| Task-M08-FE-101 | 評價列表元件 | 1 | ⏳ Pending |

### QA/IT Tasks

| Task ID | 任務名稱 | SP | 狀態 |
|---------|----------|-----|------|
| Task-M18-IT-201 | M18 知識管理整合測試 | 1 | ⏳ Pending |
| Task-M08-IT-101 | M08 評價系統整合測試 | 1 | ⏳ Pending |

---

**文件版本**: AISDLC v0.09
**建立日期**: 2026-06-15
**驗證人**: Claude Code (AI Assistant)
**Sprint 13 狀態**: 🔴 **規劃中** - 待團隊確認