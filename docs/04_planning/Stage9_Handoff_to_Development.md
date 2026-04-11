# Stage 9 移交開發團隊 / Handoff to Development Team

> **日期**: 2026-04-10
> **Stage**: 9 - 移交開發團隊
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **狀態**: Ready for Development Kickoff

---

## 1. 文檔完整性檢查

### 1.1 必備文檔狀態

| 目錄 | 文檔 | 狀態 | 路徑 |
|------|------|------|------|
| **01_requirements** | PRD (v1.0) | ✅ | `docs/01_requirements/E-Commerce_PRD_v1.0_Final.md` |
| | FRD (v1.0) | ✅ | `docs/01_requirements/E-Commerce_FRD_v1.0.md` |
| **02_architecture** | SRD (v1.0) | ✅ | `docs/02_architecture/SRD_System_Architecture.md` |
| | Database Schema | ✅ | `docs/02_architecture/SRD_Database_Schema.md` |
| | API Specs (7 modules) | ✅ | `docs/02_architecture/api/` |
| **04_planning** | Sprint Planning | ✅ | `docs/04_planning/Stage6_Sprint_Planning.md` |
| | Test Strategy | ✅ | `docs/04_planning/Stage6_Test_Strategy.md` |
| | Coding Standards | ✅ | `docs/04_planning/Stage8_Coding_Standards.md` |
| | Developer Setup Guide | ✅ | `docs/04_planning/Stage8_Developer_Setup_Guide.md` |
| | Handoff to Dev Team | ✅ | `docs/04_planning/Stage9_Handoff_to_Development.md` |
| **03_testing** | Test Case Index | ✅ | `docs/03_testing/TC_Index.md` |
| | M03 Auth Tests (25 cases) | ✅ | `docs/03_testing/TC_M03_Auth.md` |
| | M17 Tenant Tests (22 cases) | ✅ | `docs/03_testing/TC_M17_Tenant.md` |
| | M01 Product Tests (20 cases) | ✅ | `docs/03_testing/TC_M01_Product.md` |
| | M05 Order Tests (24 cases) | ✅ | `docs/03_testing/TC_M05_Order.md` |
| | M02 Room Tests (18 cases) | ✅ | `docs/03_testing/TC_M02_Room.md` |
| | M12 Pricing Tests (20 cases) | ✅ | `docs/03_testing/TC_M12_Pricing.md` |
| | E2E Tests (12 cases) | ✅ | `docs/03_testing/TC_E2E.md` |
| **06_quality** | Review Reports | ✅ | `docs/06_quality/` |
| **Infrastructure** | CI/CD Pipeline | ✅ | `.github/workflows/ci.yml` |
| | Docker Configuration | ✅ | `docker-compose.yml`, `Dockerfile` |
| | Secret Detection | ✅ | `.gitleaks.toml` |

### 1.2 待完成項目 (非必要)

| 項目 | 說明 | 處理方式 |
|------|------|----------|
| 05_development/* | 開發迭代文檔 | Sprint 1 開始後自動產生 |
| 07_design/* | UI/UX 設計稿 | 預計 Sprint 2 完成 |
| 08_deployment/* | 部署腳本 | 生產部署前建立 |

---

## 2. 技術架構 Walkthrough

### 2.1 系統架構總覽

```
┌─────────────────────────────────────────────────────────────────────┐
│                         B2B2C Platform                              │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    Frontend (Next.js 15)                    │   │
│  │           App Router + TypeScript + Tailwind + Zustand      │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                   │                                  │
│                                   ▼                                  │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    API Gateway (Spring Boot)                │   │
│  │                    /api/v2/* → Multi-Tenant Aware            │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                   │                                  │
│         ┌─────────────────────────┼─────────────────────────┐       │
│         ▼                         ▼                         ▼       │
│  ┌─────────────┐           ┌─────────────┐           ┌─────────┐  │
│  │  Retail     │           │  Booking    │           │ Platform│  │
│  │  Engine     │           │  Engine     │           │ Infra   │  │
│  │  (M01,M05) │           │  (M02,M06) │           │(M03,M17)│  │
│  └─────────────┘           └─────────────┘           └─────────┘  │
│         │                         │                         │       │
│         └─────────────────────────┴─────────────────────────┘       │
│                                ▼                                    │
│  ┌────────────────────┐          ┌────────────────────────────┐   │
│  │  PostgreSQL 18     │          │       Redis 7             │   │
│  │  (Shared Schema)    │          │  (Cache/Lock/Rate Limit)  │   │
│  └────────────────────┘          └────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 技術棧摘要

| 層級 | 技術 | 版本 |
|------|------|------|
| **Frontend** | Next.js | 15.x |
| | TypeScript | 5.x |
| | Tailwind CSS | 4.x |
| | Zustand (State) | 4.x |
| **Backend** | Spring Boot | 3.2.x |
| | Java (JDK) | 21 |
| | Spring Data JPA | 3.2.x |
| | Hibernate | 6.x |
| | Flyway | 9.x |
| **Database** | PostgreSQL | 16.x |
| **Cache** | Redis | 7.x |
| **CI/CD** | GitHub Actions | - |
| **Container** | Docker | 24.x |

### 2.3 多租戶架構

**策略**: Shared Schema + Tenant ID + Hibernate Filter

```
請求流程:
API Request → JwtAuthFilter → TenantContextFilter → Controller → Hibernate Filter
                                                   ↓
                                      自動注入 WHERE tenant_id = :tid
```

**關鍵點**:
- 所有 Tenant-Aware 實體都帶有 `tenant_id` 欄位
- Hibernate Filter 在 Repository 層自動套用
- TenantContext 由 Filter 注入 ThreadLocal

### 2.4 統一商品模型 (Unified Listing)

```
                    listings (統一抽象)
                          │
          ┌───────────────┴───────────────┐
          ▼                               ▼
      products                        rooms
   (PRODUCT 特有)                   (ROOM 特有)
```

### 2.5 部署架構

| 環境 | 用途 | 部署方式 |
|------|------|----------|
| Local | 開發者本機 | Docker Compose |
| Dev | 開發整合 | GitHub Actions |
| Staging | 上線前驗證 | GitHub Actions (手動) |
| Production | 正式環境 | GitHub Actions (手動) |

---

## 3. 測試計畫說明

### 3.1 測試金字塔

```
                    ┌─────────────┐
                    │    E2E      │  ← Playwright
                    │   Tests     │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │    Integration Tests     │  ← Spring Boot Test
              └────────────┬────────────┘
                           │
        ┌──────────────────┴──────────────────┐
        │         Unit Tests                  │  ← JUnit 5 + Mockito
        └─────────────────────────────────────┘
```

### 3.2 覆蓋率目標

| 層級 | 目標 | 工具 |
|------|------|------|
| Unit Tests | ≥ 80% | JUnit 5 + Mockito |
| Integration Tests | ≥ 70% | Spring Boot Test |
| API E2E | 100% P0 APIs | REST Assured |
| E2E Tests | 核心場景 | Playwright |

### 3.3 測試環境

| 環境 | 用途 | 資料庫 |
|------|------|--------|
| Local | 開發者本機 | H2 (in-memory) |
| Dev | CI/CD | PostgreSQL (Dev) |
| Staging | 上線前驗證 | PostgreSQL (Staging) |

### 3.4 模組測試重點

| 模組 | 測試重點 |
|------|----------|
| **M03 Auth** | BCrypt 加密、JWT 產生/驗證、RBAC 權限判斷 |
| **M17 Tenant** | 多租戶資料隔離、Feature Toggle |
| **M01 Product** | Listing 統一抽象、商品狀態機 |
| **M05 Order** | 訂單狀態機、並發庫存扣減、取消回滾 |
| **M02 Room** | 日曆衝突、Redis 分散式鎖 |
| **M12 Pricing** | 價格計算引擎、優先級規則 |

### 3.5 CI/CD 自動化測試

```
Push/PR → Layer 0 (Security) → Layer 1 (Build & Test) → E2E → Deploy
              │                       │
              ├── Gitleaks            ├── Lint + Format
              ├── OWASP Dep Check     ├── Unit Test + Coverage ≥80%
              ├── License Check       ├── Integration Test
              └── SAST (SpotBugs)     └── Build
```

---

## 4. 開發注意事項

### 4.1 Git Workflow

**分支命名**:
```
main                  # 生產環境 (protected)
develop               # 開發整合 (protected)
feature/US-M03-001   # 功能分支
bugfix/BUG-001       # Bug 修復分支
hotfix/BUG-002       # 緊急修復分支
```

**Commit 格式**:
```
{type}({scope}): {subject}

範例: feat(M03): Add member registration endpoint
```

### 4.2 程式碼規範

**前端 (TypeScript)**:
- Components: PascalCase (e.g., `MemberRegistration.tsx`)
- Hooks: camelCase + `use` prefix (e.g., `useAuth.ts`)
- 禁止使用 `any` 型別
- ESLint + Prettier 強制格式化

**後端 (Java)**:
- Classes: PascalCase (e.g., `MemberController`)
- Methods: camelCase (e.g., `findByEmail()`)
- 使用 Lombok 減少 boilerplate
- Entity 需配置 Hibernate Filter

### 4.3 多租戶開發須知

**⚠️ 重要**: 所有涉及資料庫操作的程式碼必須：
1. 在 Entity 上配置 `@Filter` 註解
2. Repository 方法自動繼承 tenant 過濾
3. 新增資料時自動注入 `tenant_id`
4. 避免跨租戶查詢

### 4.4 JWT 開發須知

- Access Token: 15 分鐘有效
- Refresh Token: 30 天有效，HttpOnly Cookie
- Token 過期後自動使用 Refresh Token 續命
- 登出時 Refresh Token 列入黑名單

### 4.5 常見問題處理

| 問題 | 解決方式 |
|------|----------|
| Docker container 啟動失敗 | `docker compose logs -f <service>` |
| 資料庫連接失敗 | 檢查 PostgreSQL 是否運行 `docker compose ps postgres` |
| Gradle build 失敗 | `./gradlew clean --refresh-dependencies` |
| 前端模組安裝失敗 | 刪除 `node_modules` 後重新 `npm ci` |

---

## 5. 專案 Kickoff Meeting 議程

### 5.1 會議資訊

| 項目 | 內容 |
|------|------|
| **會議名稱** | E-Commerce Platform Sprint 1 Kickoff |
| **日期/時間** | 2026-04-XX (待定) |
| **地點** | 視訊/線下 (待定) |
| **參與者** | 開發團隊、PM、SA |
| **會議時長** | 90 分鐘 |

### 5.2 議程

| 時間 | 主題 | 負責人 | 說明 |
|------|------|--------|------|
| 0-5 min | 開場 | PM | 會議目標確認 |
| 5-20 min | 專案背景介紹 | PM/SA | 業務目標、產品願景 |
| 20-40 min | 技術架構 Walkthrough | SD | 系統架構、多租戶設計 |
| 40-55 min | Sprint 1 目標與範圍 | PM | US-M03-001 ~ US-M03-003 |
| 55-70 min | 開發環境設定 | Dev | Docker、本機開發設定 |
| 70-85 min | CI/CD 流程說明 | Dev | Git Workflow、測試覆蓋率 |
| 85-90 min | Q&A 與下一步 | ALL | 問題討論、後續行動 |

### 5.3 Sprint 1 目標

**主要交付物**:
1. ✅ M03 會員系統功能完成
   - US-M03-001: 會員註冊
   - US-M03-002: 會員登入
   - US-M03-003: JWT Refresh Token

2. ✅ M17 租戶管理功能完成
   - US-M17-001: 租戶申請
   - US-M17-002: Admin 審核
   - US-M17-003: Feature Toggle

**技術目標**:
- CI/CD Pipeline 正常運行
- 單元測試覆蓋率 ≥ 80%
- 開發環境可正常啟動

### 5.4 後續行動項目

| 行動項目 | 負責人 | 截止日期 |
|----------|--------|----------|
| 建立 GitHub Repository | Dev | Sprint 1 Day 1 |
| 設定 GitHub Secrets | Dev | Sprint 1 Day 1 |
| 確認所有開發者環境 | Dev | Sprint 1 Day 2 |
| 建立第一個 Feature Branch | Dev | Sprint 1 Day 2 |

---

## 6. 快速參考

### 6.1 關鍵文件連結

| 文件 | 路徑 |
|------|------|
| **開發者設定指南** | `docs/04_planning/Stage8_Developer_Setup_Guide.md` |
| **程式碼規範** | `docs/04_planning/Stage8_Coding_Standards.md` |
| **測試策略** | `docs/04_planning/Stage6_Test_Strategy.md` |
| **系統架構** | `docs/02_architecture/SRD_System_Architecture.md` |
| **API 規格** | `docs/02_architecture/API_Index.md` |
| **CI/CD Pipeline** | `.github/workflows/ci.yml` |

### 6.2 環境存取資訊

| 環境 | URL | 說明 |
|------|-----|------|
| **Local Frontend** | http://localhost:3000 | 開發環境 |
| **Local Backend** | http://localhost:8080 | 開發環境 |
| **Health Check** | http://localhost:8080/actuator/health | 後端健康檢查 |

### 6.3 緊急聯絡

| 角色 | 負責範圍 |
|------|----------|
| SA (System Analyst) | 需求規格、技術疑問 |
| SD (System Designer) | 架構設計、技術決策 |
| PM (Project Manager) | 進度管理、優先級調整 |

---

## 📁 相關文件

| 文件 | 路徑 |
|------|------|
| 開發規範 | `docs/04_planning/Stage8_Coding_Standards.md` |
| 開發者設定指南 | `docs/04_planning/Stage8_Developer_Setup_Guide.md` |
| Sprint 規劃 | `docs/04_planning/Stage6_Sprint_Planning.md` |
| 測試策略 | `docs/04_planning/Stage6_Test_Strategy.md` |
| 系統架構 | `docs/02_architecture/SRD_System_Architecture.md` |
| API 規格 | `docs/02_architecture/API_Index.md` |

---

**文件結束**

---

## ✅ Stage 9 移交確認

| 項目 | 狀態 | 日期 |
|------|------|------|
| 文檔完整性檢查 | ✅ | 2026-04-10 |
| 技術架構 Walkthrough | ✅ | 2026-04-10 |
| 測試計畫說明 | ✅ | 2026-04-10 |
| 開發注意事項說明 | ✅ | 2026-04-10 |
| Kickoff Meeting 準備 | ✅ | 2026-04-10 |
| **測試案例建立** | ✅ | 2026-04-10 (QA Agent) |

### 測試案例摘要

| 模組 | 案例數 | 測試類型 |
|------|--------|----------|
| M03 Auth | 25 | UT + IT + API |
| M17 Tenant | 22 | UT + IT + API |
| M01 Product | 20 | UT + IT + API |
| M05 Order | 24 | UT + IT + API |
| M02 Room | 18 | UT + IT + API |
| M12 Pricing | 20 | UT + IT + API |
| E2E | 12 | Playwright |
| **總計** | **141** | |

**移交日期**: 2026-04-10
**下一階段**: Sprint 1 開發執行
