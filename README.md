# NextKey E-Commerce Platform

> B2B2C 多租戶電子商務平台 - Phase 1

## 📋 專案概述

本專案為一個專業的 B2B2C 多租戶電子商務平台，涵蓋五大專業領域：

- **電子商務**：購物車、結帳、金流、會員系統
- **民宿管理**：預訂系統、行事曆、動態定價
- **內容發布**：CMS 貼文、商品/房型嵌入卡片
- **知識管理**：文件管理、分類標籤、搜尋
- **進銷存管理**：採購、庫存、條碼掃描、物流追蹤

### Phase 1 技術架構

| 層級 | 技術 | 備註 |
|------|------|------|
| 前端 | Next.js 15 (App Router) + TypeScript + Tailwind CSS | SSR + SSG |
| 後端 | Spring Boot 3.2 (Java 21) + Clean Architecture + DDD | 多租戶感知 |
| 資料庫 | PostgreSQL 18 | Shared Schema + Tenant ID 隔離 |
| 快取 | Redis 7 | 購物車 / 分佈式鎖 / 限流 / 定價快取 |
| DB 遷移 | Flyway | 所有 Schema 變更走 Migration |
| E2E 測試 | Playwright | 端到端自動化測試 |
| CI/CD | GitHub Actions | 自動化構建與測試 |

## 🚀 快速開始

### 前置需求

- Node.js 20+
- Java 21+
- Maven 3.9+ 或使用 wrapper
- PostgreSQL 18+
- Redis 7+

### 安裝步驟

1. **Clone 專案**
```bash
git clone <repository-url>
cd E-Commerce
```

2. **設定資料庫**

確認 PostgreSQL 和 Redis 已啟動，並建立資料庫：

```sql
CREATE DATABASE nextkeytest;
```

3. **設定環境變數**

複製環境變數範例檔案並修改：

```bash
cp backend/src/main/resources/application.yml.example backend/src/main/resources/application.yml
```

編輯 `application.yml` 填入你的資料庫連線資訊：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://192.168.1.133:5432/nextkeytest
    username: koala
    password: koala5
```

4. **啟動後端**

```bash
cd backend

# 使用 Maven Wrapper (推薦)
./mvnw spring-boot:run

# 或使用 Maven
mvn spring-boot:run
```

5. **啟動前端**

```bash
cd frontend
npm install
npm run dev
```

6. **開啟瀏覽器**

- 前端：http://localhost:3000
- 後端 API：http://localhost:8080/api
- API 文件（待實作）：http://localhost:8080/api/swagger-ui.html

## 📁 專案結構

```
E-Commerce/
├── .github/                   # CI/CD (GitHub Actions)
│   └── workflows/
│       └── ci.yml            # 主要 CI/CD Pipeline
├── docs/                      # 專案文件
│   ├── 01_requirements/      # 需求文檔 (PRD, FRD)
│   ├── 02_architecture/      # 架構設計 (SRD, API Spec)
│   ├── 03_testing/           # 測試文檔
│   ├── 04_planning/          # 開發規劃
│   ├── 05_development/       # 開發文檔
│   ├── 06_quality/           # 品質文檔
│   ├── 07_design/            # 設計文檔
│   └── 08_deployment/        # 部署文檔
├── backend/                   # Spring Boot 後端
│   └── src/main/
│       ├── java/com/nextkey/ecommerce/
│       │   ├── api/          # Controllers, DTOs, Filters
│       │   ├── core/          # Business Logic (Services)
│       │   ├── domain/        # Entities, Repositories
│       │   ├── infrastructure/ # JPA, Redis, Security
│       │   └── shared/        # Utils, Constants, Exceptions
│       └── resources/
│           ├── application.yml
│           └── db/migration/  # Flyway Migrations
└── frontend/                  # Next.js 前端
    └── src/
        ├── app/               # Pages (App Router)
        ├── components/        # UI Components
        ├── features/          # Business Components
        ├── lib/               # Utilities
        ├── services/          # API Calls
        └── types/             # TypeScript Interfaces
```

## 🔑 預設帳號

Phase 1 使用 Payment Mock，無需真實金流：

- **Admin**: admin@nextkey.local / admin123 (更換於生產環境！)
- **測試用戶**: user@test.com / test123

## 📦 Phase 1 開發模組

### P0 - 核心模組

| ID | 模組 | 功能 |
|----|------|------|
| M03 | 會員系統 | 會員註冊/登入、RBAC、JWT |
| M17 | 多租戶系統 | 租戶隔離、Feature Toggle |
| M01 | 商品中心 | Product CRUD、分類、搜尋 |
| M02 | 房源中心 | Room CRUD、民宿管理 |
| M04 | 購物車 | Redis 購物車、優惠券 |
| M05 | 訂單履約 | 狀態機、取消/回滾 |
| M06 | 預訂日曆 | Redis 分散式鎖 |
| M07 | 支付系統 | Payment Mock |
| M12 | 動態定價 | 平假日/旺季/早鳥/長住 |

### P1 - 重要模組

| ID | 模組 | 功能 |
|----|------|------|
| M11 | 物流追蹤 | 黑貓、新竹物流 |
| M13 | 商家工作台 | 儀表板、訂單管理 |
| M14 | 平台管理 | Admin 功能 |

### Phase 2 延後

| ID | 模組 |
|----|------|
| M08 | 評價系統 |
| M09 | 通知系統 |
| M10 | IM 通訊 |
| M15 | CMS |
| M16 | 進銷存 |
| - | 行動端 App |

## 🧪 測試

### 執行測試

```bash
# 後端測試
cd backend
./mvnw test

# 前端測試
cd frontend
npm test

# E2E 測試
cd frontend
npx playwright test
```

## 📚 文件

- [PRD 規格文件](docs/01_requirements/E-Commerce_PRD_v0.9_R02_Final.md)
- [Phase 1 執行計劃](docs/04_planning/PHASE1_EXECUTION_PLAN_v1.0.md)

## 🤝 貢獻指南

1. 建立 Feature Branch：`git checkout -b feature/your-feature-name`
2. 提交變更：`git commit -m 'Add some feature'`
3. 推送 Branch：`git push origin feature/your-feature-name`
4. 建立 Pull Request

## 📄 授權

All rights reserved.
