# Release Notes - v12.0.0

**發布日期**: 2026-06-15
**發布類型**: Minor (新增功能)
**Sprint**: Sprint 12
**專案**: E-Commerce Platform (B2B2C Multi-tenant)

---

## 新功能 ✨

### M18 知識管理系統 (Phase 2-A)

#### 媒體中心 Backend
- **Task-M18-101**: 媒體中心 Service + REST API
  - `MediaService` - 媒體上傳、下載、管理業務邏輯
  - `MediaController` - 媒體資產 CRUD API (`/api/v2/media`)
  - `MediaCategoryController` - 媒體分類管理 API (`/api/v2/media/categories`)
  - 支援分類階層 (parent_id)
  - 支援多標籤 (tags)
  - 軟刪除機制

#### 知識庫 Backend
- **Task-M18-102**: 知識庫文章 Service + API
  - `KnowledgeBaseService` - 知識庫文章 CRUD 業務邏輯
  - `KnowledgeArticleController` - 文章 REST API
  - `KnowledgeCategoryController` - 分類 REST API
  - 多租戶隔離 (tenant_id)
  - 支援文章置頂、發布狀態

#### FAQ 管理 Backend
- **Task-M18-103**: FAQ 管理 Service + API
  - `FaqService` - FAQ 分類、解答管理業務邏輯
  - `FaqCategoryController` - FAQ 分類 API
  - `FaqArticleController` - FAQ 文章 API
  - 多租戶隔離
  - 支援置頂、發布、關鍵字搜尋

### M07 金流準備

#### Payment Mock
- **Task-M07-101**: Payment Mock 服務
  - `PaymentService` - 支付模擬服務
  - `PaymentController` - 支付 REST API
  - 模擬支付成功/失敗場景
  - 為 Phase 2-B Stripe/LinePay 整合預留介面

#### 訂單支付狀態機
- **Task-M07-102**: 訂單支付狀態服務
  - `PaymentStateService` - 支付狀態機
  - `OrderPaymentController` - 訂單支付 REST API
  - 狀態機: `PENDING_PAYMENT → PAID → SHIPPED → COMPLETED`
  - 支援取消和退款流程

### M09 通知系統

#### 通知模板系統
- **Task-M09-101**: 通知模板 Backend
  - `NotificationTemplateService` - 通知模板業務邏輯
  - `NotificationTemplateController` - 通知模板 REST API
  - 支援變量語法 `{{variable_name}}`
  - 支援模板渲染預覽

### Frontend 頁面

#### 媒體中心頁面
- **Task-M18-104**: `/dashboard/media`
  - 媒體上傳介面
  - 分類管理
  - 媒體庫列表

#### 知識庫頁面
- **Task-M18-105**: `/dashboard/knowledge`
  - 知識庫文章列表
  - 分類導航
  - 搜尋功能

#### 通知管理頁面
- **Task-M09-102**: `/dashboard/notifications`
  - 通知模板列表
  - 模板編輯器
  - 變量預覽

---

## 改進 🚀

### 多租戶安全加固
- 修復 KnowledgeBaseService 多租戶隔離漏洞
- 修復 FaqService 多租戶隔離漏洞
- 所有 Repository 查詢已加入 tenant_id 過濾
- 新增 V29 資料庫遷移腳本

### 程式碼品質
- 清理未使用的 import
- 修復 MediaService 未使用變數警告
- 統一程式碼風格

### API 擴展
- 媒體分類 API: GET/POST/PUT/DELETE 完整 CRUD
- 知識庫 API: 文章 CRUD + 分類管理
- FAQ API: 分類 + 解答 + 搜尋
- 支付 API: 模擬支付 + 狀態查詢 + 退款

---

## 技術改進 🔧

### 資料庫遷移
- V21: 建立 media_categories 表
- V22: 建立 media_assets 表
- V23: 建立 knowledge_categories 表
- V24: 建立 knowledge_articles 表
- V25: 建立 knowledge_article_tags 表
- V26: 建立 faq_categories 表
- V27: 建立 faq_articles 表
- V28: 建立 notification_templates 表
- V29: 為現有表新增 tenant_id 欄位

### 測試覆蓋
- **M18MediaIntegrationTest**: 12 個測試案例
- **M07PaymentMockIntegrationTest**: 8 個測試案例
- **M09NotificationTemplateIntegrationTest**: 12 個測試案例
- 總計: 32 個整合測試

---

## Bug 修復 🐛

| Issue | 描述 | PR |
|-------|------|-----|
| Multi-tenant-001 | KnowledgeBaseService 缺少 tenant_id 過濾 | #179262d |
| Multi-tenant-002 | FaqService 缺少 tenant_id 過濾 | #179262d |

---

## 重大變更 ⚠️

### 多租戶隔離增強
為符合安全標準，以下資料表已新增 `tenant_id` 欄位：
- `knowledge_categories`
- `faq_categories`
- `faq_articles`

**遷移時間**: V29 遷移腳本自動執行

### API 版本
所有新功能使用 `/api/v2/` 路徑，與現有 `/api/v1/` API 分開。

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
3. 驗證 API: `GET /api/v2/media/categories`
4. 驗證 Frontend: 訪問 `/dashboard/media`

### 相關連結
- [Sprint 12 Plan](docs/04_planning/SPRINT_12_PLAN.md)
- [Sprint 12 Review](docs/05_development/SPRINT_12_REVIEW.md)
- [Sprint 12 Tasks](docs/05_development/SPRINT_12_TASKS.md)

---

## 貢獻者

- Dev Team (Backend Development)
- FE Dev Team (Frontend Development)
- QA Team (Testing & Validation)

---

**文件版本**: AISDLC v0.09
**驗證狀態**: ✅ 四方專家審議通過
**Sprint 12 狀態**: ✅ Ready for Release