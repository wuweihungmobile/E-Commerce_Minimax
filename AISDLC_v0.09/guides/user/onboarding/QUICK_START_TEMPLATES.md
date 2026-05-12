# AISDLC v0.09 快速啟動範本庫
# Quick Start Templates Library

**版本**: v0.09
**建立日期**: 2026-05-07 (v0.09 全面更新：新增 6 個情境範本，覆蓋 10 大情境)
**用途**: 提供預配置的專案範本，實現 < 3 分鐘快速啟動

---

## 📚 範本總覽

| 範本代碼 | 範本名稱 | 情境 | 技術棧/重點 | 預估週期 | 適用規模 |
|---------|---------|------|-----------|---------|---------|
| `ecommerce-web` | 電商網站 | Greenfield Web | React + Node.js + PostgreSQL | 2-3 月 | 中大型 |
| `mobile-app` | 移動應用 | Greenfield Mobile | React Native + Firebase | 1.5-2.5 月 | 中型 |
| `api-service` | API 服務 | Greenfield Backend | Node.js/Python + PostgreSQL | 1-2 月 | 中型 |
| `legacy-upgrade` | 舊系統升級 | Brownfield | 依原系統 | 2-4 月 | 大型 |
| `api-integration` | API 整合 | Integration | 依主系統 | 2-4 週 | 小中型 |
| `performance-tuning` | 效能優化 | Performance | 依系統 | 1-3 週 | 小中型 |
| `code-refactoring` | 程式碼重構 | Refactoring | 依原代碼庫 | 2-6 週 | 中大型 |
| `tech-migration` | 技術棧遷移 | Migration | 依遷移目標 | 1-4 月 | 中大型 |
| `devops-pipeline` | CI/CD 建置 | DevOps | GitHub Actions/GitLab CI | 1-3 週 | 全規模 |
| `test-strategy` | 測試策略建置 | Testing | 依測試框架 | 1-4 週 | 全規模 |
| `tech-documentation` | 技術文件整理 | Documentation | Markdown/Confluence | 1-3 週 | 全規模 |
| `security-review` | 安全審查強化 | Security | 依系統架構 | 2-4 週 | 中大型 |

---

## 1. ecommerce-web - 電商網站標準範本

### 基本資訊

```yaml
template_id: ecommerce-web
name: 電商網站標準流程
scenario: Greenfield Web
category: Full-stack Web Application
estimated_duration: 2-3 個月
team_size: 4-8 人
```

### 預設技術棧

```yaml
frontend:
  framework: React 18.x
  language: TypeScript 5.x
  state_management: Redux Toolkit / Zustand
  ui_library: Material-UI / Ant Design
  styling: Tailwind CSS / CSS Modules

backend:
  runtime: Node.js 20.x
  framework: Express.js / Nest.js
  language: TypeScript 5.x
  authentication: JWT + Refresh Token
  api_style: RESTful API

database:
  primary: PostgreSQL 15.x
  cache: Redis 7.x
  search: Elasticsearch (optional)

infrastructure:
  hosting: AWS / GCP / Azure
  cdn: CloudFront / CloudFlare
  ci_cd: GitHub Actions / GitLab CI
  monitoring: Datadog / New Relic
```

### 核心功能模組

```yaml
modules:
  1. 使用者系統:
    - 註冊登入 (Email + OAuth)
    - 個人資料管理
    - 密碼重設
    - 權限管理 (顧客/管理員)

  2. 產品目錄:
    - 產品列表與搜尋
    - 產品分類導航
    - 產品詳細資訊
    - 產品評論與評分

  3. 購物車:
    - 加入/移除商品
    - 數量調整
    - 購物車持久化
    - 折扣碼應用

  4. 結帳流程:
    - 訂單資訊確認
    - 配送地址管理
    - 付款方式選擇
    - 訂單確認與追蹤

  5. 後台管理:
    - 產品管理 (CRUD)
    - 訂單管理
    - 使用者管理
    - 報表分析
```

### 載入的 Agents

```yaml
primary_agents:
  - pm-po-agent (Victoria): 產品規劃與商業邏輯
  - sa-analyst (Amanda): 需求分析與功能設計

supporting_agents:
  - ba-business-analyst (Beatrice): 業務驗證與利害關係人溝通
  - sd-architect (Marcus): 技術架構設計
  - dev-developer: 開發實施評估
  - qa-tester (Quincy): 測試策略與驗收標準

optional_agents:
  - security-engineer: 當涉及支付/個資等敏感資料時
  - compliance-officer: 當有 PCI-DSS / GDPR 等合規要求時
```

### 推薦文檔

```yaml
documents:
  - PRD_Universal_Template.md (Greenfield)
  - FRD_Universal_Template.md (Standard)
  - SRD_Module_Template.md (每個模組一份)
  - API_Specification_Template.md (每個 API 端點一份)
  - AT_Module_Template.md (按模組測試)
```

### 快速參考

```yaml
quick_reference:
  - scenarios/greenfield/SOP_QuickRef.md (5 分鐘快速掌握)
  - scenarios/greenfield/SOP.md (完整流程)
  - scenarios/greenfield/SOP_DeepDive.md (進階技術細節)
```

---

## 2. mobile-app - 移動應用標準範本

### 基本資訊

```yaml
template_id: mobile-app
name: 移動 App 標準流程
scenario: Greenfield Mobile
category: Mobile Application
estimated_duration: 1.5-2.5 個月
team_size: 3-6 人
```

### 預設技術棧

```yaml
cross_platform:
  framework: React Native 0.72+
  language: TypeScript 5.x
  navigation: React Navigation
  state_management: Redux Toolkit / MobX
  ui_library: React Native Paper / NativeBase

ios_native:
  language: Swift 5.x
  ui_framework: SwiftUI
  architecture: MVVM / Clean Architecture
  dependency_manager: CocoaPods / SPM

android_native:
  language: Kotlin 1.9+
  ui_framework: Jetpack Compose
  architecture: MVVM / Clean Architecture
  dependency_manager: Gradle

backend:
  api_type: RESTful / GraphQL
  authentication: JWT + Biometric
  push_notification: Firebase Cloud Messaging

services:
  analytics: Firebase Analytics / Mixpanel
  crash_reporting: Crashlytics / Sentry
  ab_testing: Firebase Remote Config
```

### 核心功能模組

```yaml
modules:
  1. 使用者認證:
    - Email/手機號註冊登入
    - 社交登入 (Google/Apple/Facebook)
    - 生物辨識 (Face ID / Fingerprint)
    - 推播通知權限

  2. 主要功能:
    - 首頁 Dashboard
    - 核心業務流程 (依專案而定)
    - 搜尋與過濾
    - 個人化推薦

  3. 使用者中心:
    - 個人資料編輯
    - 設定與偏好
    - 通知中心
    - 幫助與回饋

  4. 整合服務:
    - API 整合
    - 推播通知
    - 深度連結 (Deep Link)
    - 應用內更新
```

### 載入的 Agents

```yaml
primary_agents:
  - pm-po-agent (Victoria): 產品規劃
  - sa-analyst (Amanda): 功能分析

supporting_agents:
  - sd-architect (Marcus): 架構設計
  - dev-developer: 開發實施
  - qa-tester (Quincy): 測試策略
  - qa-automation: 自動化測試 (E2E)

optional_agents:
  - sd-mobile-architect: 當涉及 iOS/Android 原生架構決策時
  - qa-mobile-tester: 當需要行動端專業化測試時
```

### 快速參考

```yaml
quick_reference:
  - scenarios/greenfield/SOP_QuickRef.md (5 分鐘快速掌握)
  - scenarios/greenfield/SOP.md (完整流程)
  - scenarios/greenfield/SOP_DeepDive.md (進階技術細節)
```

---

## 3. api-service - 後端 API 服務範本

### 基本資訊

```yaml
template_id: api-service
name: 後端 API 服務標準流程
scenario: Greenfield Backend
category: Backend API Service
estimated_duration: 1-2 個月
team_size: 2-5 人
```

### 預設技術棧

```yaml
option_1_nodejs:
  runtime: Node.js 20.x
  framework: Express.js / Nest.js / Fastify
  language: TypeScript 5.x
  orm: Prisma / TypeORM

option_2_python:
  runtime: Python 3.11+
  framework: FastAPI / Django REST Framework
  async: asyncio / aiohttp
  orm: SQLAlchemy / Django ORM

option_3_java:
  runtime: Java 17+ / Kotlin 1.9+
  framework: Spring Boot 3.x
  build_tool: Gradle / Maven
  orm: Hibernate / JPA

common_components:
  database: PostgreSQL / MySQL / MongoDB
  cache: Redis
  message_queue: RabbitMQ / Kafka (optional)
  authentication: JWT / OAuth 2.0
  api_documentation: OpenAPI 3.0 / Swagger
  logging: Winston / Logback / structlog
  monitoring: Prometheus + Grafana
```

### 核心功能模組

```yaml
modules:
  1. 認證授權:
    - JWT Token 認證
    - Refresh Token 機制
    - 角色權限管理 (RBAC)
    - API Key 管理

  2. 核心業務 API:
    - RESTful CRUD 操作
    - 複雜業務邏輯處理
    - 資料驗證與轉換
    - 錯誤處理機制

  3. 資料存取層:
    - Database ORM
    - Transaction 管理
    - Query 優化
    - Connection Pooling

  4. 整合服務:
    - 第三方 API 整合
    - Webhook 處理
    - 背景任務 (Job Queue)
    - 檔案上傳/下載

  5. 運維支援:
    - Health Check API
    - Metrics 輸出
    - 結構化日誌
    - API 文檔自動生成
```

### 載入的 Agents

```yaml
primary_agents:
  - sd-architect (Marcus): 技術架構與 API 設計

supporting_agents:
  - sa-analyst (Amanda): 需求分析
  - dev-developer: 實施指導
  - qa-automation: API 自動化測試
  - devops-engineer: 部署與監控
```

### 快速參考

```yaml
quick_reference:
  - scenarios/greenfield/SOP_QuickRef.md (5 分鐘快速掌握)
  - scenarios/greenfield/SOP.md (完整流程)
  - scenarios/greenfield/SOP_DeepDive.md (進階技術細節)
```

---

## 4. legacy-upgrade - 舊系統升級範本

### 基本資訊

```yaml
template_id: legacy-upgrade
name: 舊系統升級標準流程
scenario: Brownfield
category: Legacy System Modernization
estimated_duration: 2-4 個月
team_size: 4-10 人
```

### 評估階段配置

```yaml
assessment_phase:
  code_analysis:
    - 技術債統計
    - 依賴關係分析
    - 安全漏洞掃描
    - 效能瓶頸識別

  business_analysis:
    - 現有功能盤點
    - 使用者流程分析
    - 痛點識別
    - 優先級排序

  risk_analysis:
    - 技術風險評估
    - 業務影響分析
    - 回退策略規劃
    - 資源需求評估
```

### 升級策略選擇

```yaml
strategy_options:
  1. Strangler Fig Pattern (絞殺者模式):
    - 漸進式替換舊系統
    - 新舊系統並存
    - 風險: 🟢 低
    - 週期: 3-6 個月
    - 適用: 大型系統

  2. Big Bang Replacement (大爆炸替換):
    - 一次性完全替換
    - 切換時間短
    - 風險: 🔴 高
    - 週期: 1-2 個月
    - 適用: 小型系統

  3. Parallel Run (並行運行):
    - 新舊系統同時運行
    - 對比驗證結果
    - 風險: 🟡 中
    - 週期: 2-4 個月
    - 適用: 關鍵業務系統

  4. Incremental Modernization (增量現代化):
    - 分模組逐步升級
    - 保持系統可用
    - 風險: 🟢 低
    - 週期: 4-8 個月
    - 適用: 複雜業務系統
```

### 載入的 Agents

```yaml
primary_agents:
  - sa-analyst (Amanda): 現況分析與需求整理
  - dev-senior: 資深開發者，架構評估

supporting_agents:
  - code-analyzer: 代碼分析與技術債評估
  - sd-architect (Marcus): 新架構設計
  - qa-tester (Quincy): 測試策略與回歸測試
  - devops-engineer: 部署策略與回退計畫

optional_agents:
  - security-engineer: 當變更涉及認證授權或安全漏洞修復時
  - compliance-officer: 當變更由法規合規驅動時
  - sd-mobile-architect: 當涉及行動平台擴展時
```

### 快速參考

```yaml
quick_reference:
  - scenarios/brownfield/SOP_QuickRef.md (5 分鐘快速掌握)
  - scenarios/brownfield/SOP.md (完整流程)
  - scenarios/brownfield/SOP_DeepDive.md (進階技術細節)
```

---

## 5. api-integration - API 整合專案範本

### 基本資訊

```yaml
template_id: api-integration
name: 第三方 API 整合標準流程
scenario: Integration
category: Third-party Integration
estimated_duration: 2-4 週
team_size: 2-4 人
```

### 常見整合類型

```yaml
integration_types:
  payment_gateway:
    - Stripe
    - PayPal
    - 綠界 ECPay
    - 藍新 NewebPay
    complexity: 🟡 中等
    duration: 2-3 週

  social_login:
    - Google OAuth 2.0
    - Facebook Login
    - Apple Sign In
    - LINE Login
    complexity: 🟢 簡單
    duration: 1-2 週

  crm_system:
    - Salesforce
    - HubSpot
    - Zoho CRM
    complexity: 🔴 複雜
    duration: 3-4 週

  messaging:
    - SendGrid (Email)
    - Twilio (SMS)
    - LINE Messaging API
    complexity: 🟢 簡單
    duration: 1 週

  cloud_storage:
    - AWS S3
    - Google Cloud Storage
    - Azure Blob Storage
    complexity: 🟢 簡單
    duration: 1 週

  map_service:
    - Google Maps API
    - Mapbox
    complexity: 🟢 簡單
    duration: 1-2 週
```

### 核心實施步驟

```yaml
implementation_phases:
  1. API 研究 (30 分鐘):
    - 閱讀官方文檔
    - 識別認證方式
    - 確認 rate limit
    - 確認定價方案

  2. 認證設計 (20 分鐘):
    - OAuth 2.0 流程設計
    - API Key 管理
    - Token 更新機制
    - 安全性檢查

  3. 資料轉換 (30 分鐘):
    - Request mapping
    - Response parsing
    - 錯誤碼轉換
    - 資料驗證

  4. 錯誤處理 (20 分鐘):
    - Retry 策略
    - Fallback 機制
    - 錯誤通知
    - 日誌記錄

  5. 測試計畫 (30 分鐘):
    - Sandbox 測試
    - Edge case 測試
    - 效能測試
    - 上線檢查清單
```

### 載入的 Agents

```yaml
primary_agents:
  - integration-specialist: 整合專家

supporting_agents:
  - sd-architect (Marcus): 架構設計
  - qa-tester (Quincy): 測試策略
  - dev-developer: 實作評估
  - security-engineer: 安全審查 (如涉及敏感資料)
```

### 快速參考

```yaml
quick_reference:
  - scenarios/integration/SOP_QuickRef.md (5 分鐘快速掌握)
  - scenarios/integration/SOP.md (完整流程)
  - scenarios/integration/SOP_DeepDive.md (進階技術細節)
```

---

## 6. performance-tuning - 效能優化專案範本

### 基本資訊

```yaml
template_id: performance-tuning
name: 效能優化標準流程
scenario: Performance
category: Performance Optimization
estimated_duration: 1-3 週
team_size: 2-5 人
```

### 效能問題分類

```yaml
performance_categories:
  frontend_performance:
    issues:
      - 首次載入時間過長 (TTFB/FCP/LCP)
      - JavaScript bundle 過大
      - 圖片未優化
      - 過多 API 請求
    tools:
      - Lighthouse
      - Chrome DevTools
      - WebPageTest
      - Bundle Analyzer
    typical_duration: 1-2 週

  backend_performance:
    issues:
      - API 回應時間慢 (> 200ms)
      - 資料庫查詢慢 (N+1 query)
      - 記憶體洩漏
      - CPU 使用率高
    tools:
      - Application APM (Datadog/New Relic)
      - Database Query Analyzer
      - Profiling Tools (Node.js Profiler/py-spy)
    typical_duration: 1-2 週

  database_performance:
    issues:
      - 查詢過慢 (> 100ms)
      - 缺少索引
      - 資料表設計不佳
      - Connection pool 不足
    tools:
      - EXPLAIN ANALYZE
      - Slow Query Log
      - Database Profiler
    typical_duration: 1 週

  infrastructure_performance:
    issues:
      - 網路延遲高
      - CDN 未配置
      - 伺服器資源不足
      - 負載均衡問題
    tools:
      - Network Monitoring
      - Server Metrics (CPU/RAM/Disk)
      - Load Testing (k6/JMeter)
    typical_duration: 1-2 週
```

### 優化流程

```yaml
optimization_workflow:
  phase_1_baseline:
    - 建立效能基準
    - 設定優化目標
    - 識別瓶頸
    duration: 1-2 天

  phase_2_analysis:
    - Profiling 分析
    - 根因分析
    - 優化策略制定
    duration: 2-3 天

  phase_3_implementation:
    - 實施優化
    - A/B 測試
    - 效能驗證
    duration: 3-5 天

  phase_4_validation:
    - 上線前測試
    - 效能回歸測試
    - 監控設定
    duration: 1-2 天
```

### 載入的 Agents

```yaml
primary_agents:
  - performance-engineer: 效能工程師

supporting_agents:
  - sd-architect (Marcus): 架構優化建議
  - dev-senior: 代碼優化實施
  - qa-automation: 效能測試自動化

optional_agents:
  - devops-engineer: 當涉及基礎設施調整時
  - code-analyzer: 當需要深入代碼級效能分析時
  - security-engineer: 當優化涉及加密/安全標頭等安全相關效能時
```

### 快速參考

```yaml
quick_reference:
  - scenarios/performance/SOP_QuickRef.md (5 分鐘快速掌握)
  - scenarios/performance/SOP.md (完整流程)
  - scenarios/performance/SOP_DeepDive.md (進階技術細節)
```

---

## 7. code-refactoring - 程式碼重構範本

### 基本資訊

```yaml
template_id: code-refactoring
name: 程式碼重構標準流程
scenario: Refactoring
category: Code Quality Improvement
estimated_duration: 2-6 週
team_size: 2-5 人
```

### 重構類型選擇

```yaml
refactoring_types:
  code_quality:
    scope: 函數/類別層級
    goal: 降低複雜度、提升可讀性
    risk: 🟢 低
    duration: 1-2 週
    typical_actions:
      - 消除重複代碼 (DRY)
      - 拆分過長函數
      - 改善命名
      - 簡化條件邏輯

  architecture_refactoring:
    scope: 模組/服務層級
    goal: 改善系統架構、降低耦合
    risk: 🟡 中
    duration: 3-6 週
    typical_actions:
      - 分離關注點 (SoC)
      - 引入設計模式
      - 服務解耦
      - 依賴注入重構

  test_coverage:
    scope: 測試套件
    goal: 提升測試覆蓋率至 80%+
    risk: 🟢 低
    duration: 1-3 週
    typical_actions:
      - 補充單元測試
      - 引入測試框架
      - Mock/Stub 策略
      - 整合測試建立
```

### 重構執行流程

```yaml
refactoring_workflow:
  phase_1_analysis:
    - code-analyzer 掃描代碼品質指標
    - 識別技術債熱點
    - 評估重構優先級
    duration: 1-3 天

  phase_2_planning:
    - sd-architect 制定重構策略
    - 評估影響範圍
    - 制定測試保護計畫
    duration: 1-2 天

  phase_3_execution:
    - 建立測試保護網 (先補測試)
    - 小步驟重構
    - 持續驗證功能正確性
    duration: 1-4 週

  phase_4_validation:
    - 回歸測試
    - 效能比對
    - 代碼審查
    duration: 1-3 天
```

### 載入的 Agents

```yaml
primary_agents:
  - sd-architect (Marcus): 主導重構策略設計

supporting_agents:
  - code-analyzer: 識別重構範圍與優先級
  - dev-senior: 重構技術決策
  - qa-tester (Quincy): 回歸測試計畫
```

### 快速參考

```yaml
quick_reference:
  - scenarios/refactoring/SOP_QuickRef.md (5 分鐘快速掌握)
  - scenarios/refactoring/SOP.md (完整流程)
  - scenarios/refactoring/SOP_DeepDive.md (進階技術細節)
```

---

## 8. tech-migration - 技術棧遷移範本

### 基本資訊

```yaml
template_id: tech-migration
name: 技術棧遷移標準流程
scenario: Migration
category: Technology Stack Migration
estimated_duration: 1-4 個月
team_size: 3-8 人
```

### 遷移類型

```yaml
migration_types:
  framework_migration:
    examples:
      - Vue 2 → Vue 3
      - React Class → Hooks
      - AngularJS → Angular
      - Express → Nest.js
    risk: 🟡 中
    duration: 2-6 週

  language_migration:
    examples:
      - JavaScript → TypeScript
      - Python 2 → Python 3
      - PHP → Node.js
    risk: 🟡 中
    duration: 1-3 月

  platform_migration:
    examples:
      - 單體 → 微服務
      - 自建 → 雲原生
      - 傳統部署 → 容器化
    risk: 🔴 高
    duration: 2-4 月

  database_migration:
    examples:
      - MySQL → PostgreSQL
      - MongoDB → PostgreSQL
      - 關聯式 → 文件型
    risk: 🔴 高
    duration: 3-8 週
```

### 遷移策略

```yaml
migration_strategies:
  lift_and_shift:
    description: 原樣搬遷，最小化改動
    risk: 🟢 低
    適用: 雲端遷移、基礎設施遷移

  replatform:
    description: 保留核心邏輯，更換技術棧
    risk: 🟡 中
    適用: 框架升級、語言遷移

  rearchitect:
    description: 重新設計架構，大幅改造
    risk: 🔴 高
    適用: 單體→微服務、傳統→雲原生

  parallel_run:
    description: 新舊系統並行，逐步切換
    risk: 🟡 中 (成本較高)
    適用: 關鍵業務系統、零停機需求
```

### 載入的 Agents

```yaml
primary_agents:
  - sd-architect (Marcus): 遷移架構設計、技術棧映射
  - sa-analyst (Amanda): 需求重新分析、業務邏輯提取

supporting_agents:
  - code-analyzer: 舊系統代碼品質分析
  - dev-senior: 遷移技術決策
  - qa-tester (Quincy): 遷移驗證測試
  - devops-engineer: 遷移期 CI/CD 與並行部署
  - integration-specialist: 新舊系統整合、API Gateway

optional_agents:
  - pm-po-agent (Stage 1): 遷移範圍確認
  - performance-engineer (Stage 7): 遷移後效能基準測試
  - security-engineer (Stage 7): 新系統安全審查
```

### 快速參考

```yaml
quick_reference:
  - scenarios/migration/SOP_QuickRef.md (5 分鐘快速掌握)
  - scenarios/migration/SOP.md (完整流程)
  - scenarios/migration/SOP_DeepDive.md (進階技術細節)
```

---

## 9. devops-pipeline - CI/CD 建置範本

### 基本資訊

```yaml
template_id: devops-pipeline
name: CI/CD 自動化部署標準流程
scenario: DevOps
category: DevOps & Automation
estimated_duration: 1-3 週
team_size: 1-4 人
```

### 預設 CI/CD 工具棧

```yaml
ci_cd_options:
  github_actions:
    適用: GitHub 托管專案
    特點: 原生整合、免費額度充足
    範例:
      - 自動測試 (PR trigger)
      - Docker Build & Push
      - Kubernetes Deploy

  gitlab_ci:
    適用: GitLab 托管專案
    特點: 內建 Container Registry、完整 DevSecOps
    範例:
      - Pipeline as Code
      - 環境管理
      - Auto DevOps

  jenkins:
    適用: 企業自建環境
    特點: 高度客製化、豐富插件生態
    範例:
      - 複雜 Pipeline
      - 多語言支援
      - LDAP 整合

container_orchestration:
  kubernetes:
    工具: Helm / Kustomize / ArgoCD
    適用: 中大型系統、微服務架構

  docker_compose:
    適用: 小型專案、本地開發環境

monitoring_stack:
  metrics: Prometheus + Grafana
  logging: ELK Stack / Loki
  alerting: PagerDuty / OpsGenie
  tracing: Jaeger / Zipkin
```

### 核心配置項目

```yaml
pipeline_components:
  1. CI Pipeline (持續整合):
    - 代碼品質檢查 (ESLint/SonarQube)
    - 單元測試執行
    - 安全掃描 (SAST/Dependency Check)
    - Docker Image Build

  2. CD Pipeline (持續部署):
    - 環境管理 (Dev/Staging/Prod)
    - 自動化部署腳本
    - Smoke Test 驗證
    - 回滾機制

  3. 監控與告警:
    - 應用效能監控 (APM)
    - 基礎設施監控
    - 日誌集中管理
    - 告警策略設定
```

### 載入的 Agents

```yaml
primary_agents:
  - devops-engineer: 主導 DevOps 流程設計

supporting_agents:
  - sd-architect (Marcus): 基礎設施架構設計
  - qa-automation: 自動化測試整合 CI/CD
```

### 快速參考

```yaml
quick_reference:
  - scenarios/devops/SOP_QuickRef.md (5 分鐘快速掌握)
  - scenarios/devops/SOP.md (完整流程)
  - scenarios/devops/SOP_DeepDive.md (進階技術細節)
```

---

## 10. test-strategy - 測試策略建置範本

### 基本資訊

```yaml
template_id: test-strategy
name: 測試策略建置標準流程
scenario: Testing
category: Quality Assurance
estimated_duration: 1-4 週
team_size: 2-6 人
```

### 測試層級配置

```yaml
test_pyramid:
  unit_tests:
    覆蓋目標: 80%+ 代碼覆蓋率
    工具:
      - JavaScript: Jest / Vitest
      - Python: pytest
      - Java: JUnit 5
    執行時機: 每次 commit

  integration_tests:
    覆蓋目標: 關鍵業務流程 100%
    工具:
      - API: Supertest / REST Assured
      - Database: Test Containers
    執行時機: 每次 PR

  e2e_tests:
    覆蓋目標: 核心使用者旅程
    工具:
      - Web: Playwright / Cypress
      - Mobile: Detox / Appium
    執行時機: 每次 Release

  performance_tests:
    覆蓋目標: 關鍵 API 效能基準
    工具: k6 / JMeter / Locust
    執行時機: 每次 Release / 重大變更

  security_tests:
    覆蓋目標: OWASP Top 10
    工具: OWASP ZAP / Burp Suite
    執行時機: 每個 Sprint 末
```

### 測試策略重點

```yaml
strategy_focus:
  新專案測試建置:
    - 選擇測試框架
    - 建立 CI 整合
    - 制定測試規範
    - 建立 Mock 策略

  既有專案測試補強:
    - 分析測試覆蓋缺口
    - 優先補充高風險模組
    - 引入測試工具
    - 逐步提升覆蓋率

  自動化測試:
    - 識別可自動化場景
    - 建立自動化框架
    - CI/CD 整合
    - 維護策略
```

### 載入的 Agents

```yaml
primary_agents:
  - qa-lead: 主導測試策略

supporting_agents:
  - qa-automation: 自動化測試開發
  - qa-tester (Quincy): 測試案例設計和執行
  - dev-developer: 可測試性支援
```

### 快速參考

```yaml
quick_reference:
  - scenarios/testing/SOP_QuickRef.md (5 分鐘快速掌握)
  - scenarios/testing/SOP.md (完整流程)
  - scenarios/testing/SOP_DeepDive.md (進階技術細節)
```

---

## 11. tech-documentation - 技術文件整理範本

### 基本資訊

```yaml
template_id: tech-documentation
name: 技術文件整理標準流程
scenario: Documentation
category: Technical Documentation
estimated_duration: 1-3 週
team_size: 1-4 人
```

### 文件類型與工具

```yaml
documentation_types:
  api_documentation:
    目標: API 完整規格文件
    工具: OpenAPI 3.0 / Swagger / Postman
    產出: API Reference、使用範例

  architecture_documentation:
    目標: 系統架構設計文件
    工具: C4 Model / Mermaid / PlantUML
    產出: 架構圖、決策記錄 (ADR)

  user_guide:
    目標: 使用者操作手冊
    工具: GitBook / Docusaurus / Confluence
    產出: 操作指南、FAQ

  developer_guide:
    目標: 開發者上手文件
    工具: README.md / Wiki
    產出: 環境設定、開發流程、貢獻指南

  runbook:
    目標: 運維操作手冊
    工具: Confluence / Notion
    產出: 部署流程、故障處理、監控說明
```

### 文件整理流程

```yaml
documentation_workflow:
  phase_1_audit:
    - 現有文件盤點
    - 識別文件缺口
    - 確認目標受眾
    duration: 1-2 天

  phase_2_planning:
    - 制定文件結構
    - 選擇工具平台
    - 分配撰寫責任
    duration: 1 天

  phase_3_writing:
    - 技術內容撰寫
    - 圖表製作
    - 代碼範例整理
    duration: 1-2 週

  phase_4_review:
    - 技術準確性審查
    - 可讀性測試
    - 發布與維護計畫
    duration: 2-3 天
```

### 載入的 Agents

```yaml
primary_agents:
  - technical-writer: 主導技術文件撰寫

supporting_agents:
  - sa-analyst (Amanda): 功能文件審查
  - sd-architect (Marcus): 技術架構文件審查
  - dev-senior: 複雜技術文件深度審查

optional_agents:
  - security-engineer: 當文件涉及安全架構、威脅模型時
  - compliance-officer: 當文件涉及合規要求時
```

### 快速參考

```yaml
quick_reference:
  - scenarios/documentation/SOP_QuickRef.md (5 分鐘快速掌握)
  - scenarios/documentation/SOP.md (完整流程)
  - scenarios/documentation/SOP_DeepDive.md (進階技術細節)
```

---

## 12. security-review - 安全審查強化範本

### 基本資訊

```yaml
template_id: security-review
name: 安全審查強化標準流程
scenario: Security
category: Security Design & Review
estimated_duration: 2-4 週
team_size: 2-5 人
```

### 安全審查類型

```yaml
security_review_types:
  threat_modeling:
    目標: 識別和評估系統安全威脅
    方法: STRIDE / PASTA / DREAD
    產出: 威脅模型、風險優先級
    duration: 3-5 天

  security_audit:
    目標: 全面安全性評估
    工具: OWASP Top 10、安全掃描工具
    產出: 安全審計報告、修復清單
    duration: 1-2 週

  penetration_testing:
    目標: 模擬攻擊驗證防禦能力
    工具: Burp Suite / OWASP ZAP
    產出: 滲透測試報告、漏洞詳情
    duration: 1-2 週

  compliance_review:
    目標: 確認符合法規要求
    標準: GDPR / PCI-DSS / ISO 27001
    產出: 合規對照表、差距分析
    duration: 1-2 週
```

### 安全審查重點領域

```yaml
security_domains:
  authentication_authorization:
    - 認證機制安全性
    - 授權邏輯正確性
    - Session 管理
    - Token 安全

  data_protection:
    - 敏感資料加密
    - 傳輸加密 (TLS)
    - 資料最小化原則
    - 個資保護

  input_validation:
    - SQL Injection 防護
    - XSS 防護
    - CSRF 防護
    - 輸入過濾與驗證

  infrastructure_security:
    - 網路安全配置
    - 容器安全
    - 密鑰管理
    - 存取控制
```

### 載入的 Agents

```yaml
primary_agents:
  - security-engineer: 主導安全設計和審查

supporting_agents:
  - compliance-officer: 合規審查
  - qa-lead: 安全測試策略
  - sd-architect (Marcus): 架構安全審查
```

### 快速參考

```yaml
quick_reference:
  - scenarios/security/SOP_QuickRef.md (5 分鐘快速掌握)
  - scenarios/security/SOP.md (完整流程)
  - scenarios/security/SOP_DeepDive.md (進階技術細節)
```

---

## 🚀 範本使用指南

### 步驟 1: 選擇範本

根據專案情況選擇最合適的範本：

```
新專案（全新開發）:
  Web 應用   → ecommerce-web
  Mobile App  → mobile-app
  純後端 API  → api-service

既有系統（維護改造）:
  功能新增/改善 → legacy-upgrade
  程式品質提升  → code-refactoring
  技術棧升級    → tech-migration

特定任務:
  第三方 API 整合 → api-integration
  效能瓶頸解決    → performance-tuning
  CI/CD 建置     → devops-pipeline
  測試覆蓋提升   → test-strategy
  文件系統建立   → tech-documentation
  安全性審查     → security-review
```

### 步驟 2: 使用範本啟動

```
「AISDLC 使用範本 [template-name]」

範例:
- 「AISDLC 使用範本 ecommerce-web」
- 「AISDLC 使用範本 code-refactoring」
- 「AISDLC 使用範本 security-review」
```

### 步驟 3: 客製化配置（可選）

AI 會提示您填寫專案特定資訊，或您可以：

```
「使用預設配置」→ 使用範本預設值快速開始
「客製化配置」→ 修改技術棧/模組/時程等
```

### 步驟 4: 開始執行

AI 會自動：
1. ✅ 載入對應 Agents
2. ✅ 應用 Smart Defaults
3. ✅ 推薦文檔模板
4. ✅ 提供 SOP QuickRef
5. ✅ 進入執行階段

---

## 🔧 範本客製化

### 修改技術棧

```
「我想改用 Vue.js 而不是 React」
「後端改用 Python FastAPI」
「資料庫改用 MongoDB」
```

AI 會：
- ✅ 更新技術棧配置
- ✅ 調整文檔模板建議
- ✅ 保留其他範本設定

### 調整模組範圍

```
「暫時不需要後台管理模組」
「增加會員等級與積分系統」
「需要支援多租戶架構」
```

AI 會：
- ✅ 調整模組清單
- ✅ 更新預估週期
- ✅ 調整 Agent 配置

### 改變執行模式

```
「使用 Quick Mode（減少確認點）」
「使用 Detailed Mode（完整確認點）」
「啟用 Tutorial Mode（學習模式）」
```

---

## 📊 範本效益

### 啟動時間對比

```yaml
without_template:
  情境選擇: 5 分鐘
  技術棧討論: 15 分鐘
  架構設計: 20 分鐘
  Agent 載入: 3 分鐘
  文檔設定: 7 分鐘
  total: 50 分鐘

with_template:
  範本選擇: 30 秒
  客製化調整: 1-2 分鐘 (可選)
  自動載入: 30 秒
  total: < 3 分鐘

time_saved: 94% (50 分鐘 → 3 分鐘)
```

### 決策減負

```yaml
without_template:
  需要決策的項目: 25-30 個
  - 情境類型
  - 每個技術棧選擇 (Frontend/Backend/Database/...)
  - Agent 選擇
  - 文檔模板選擇
  - 確認點模式

with_template:
  需要決策的項目: 3-5 個
  - 範本選擇
  - 專案名稱
  - 特殊需求 (可選)

decision_reduction: 85%
```

---

## 🎯 最佳實踐

### 何時使用範本

**✅ 建議使用**:
- 標準類型專案（電商、API 服務、CI/CD 建置等）
- 團隊經驗豐富，熟悉範本技術棧
- 時程緊迫，需要快速啟動
- 新手使用者，需要引導

**🟡 考慮客製化**:
- 特殊業務需求
- 技術棧與範本不同
- 企業既有架構限制

**❌ 不建議使用**:
- 高度創新的專案（範本無法涵蓋）
- 完全非標準化的技術棧
- 需要深度定製的流程

### 常見情境組合

某些任務可能需要多個範本組合使用：

```yaml
scenario_combinations:
  新專案完整交付:
    1. ecommerce-web (主體開發)
    2. devops-pipeline (CI/CD 建置)
    3. security-review (上線前安全審查)
    順序: 並行或依序

  既有系統現代化:
    1. legacy-upgrade (功能評估與改善)
    2. code-refactoring (代碼品質提升)
    3. tech-migration (技術棧升級)
    順序: 建議依序

  安全合規強化:
    1. security-review (安全審查)
    2. test-strategy (安全測試建置)
    順序: 安全審查先行
```

### 範本使用後續

範本只是快速啟動工具，實際執行時：
- ✅ 仍需遵循 AISDLC 流程和確認點
- ✅ 文檔產出標準不變
- ✅ Agent 協作規則適用
- ✅ 可隨時調整配置

---

## 📚 相關文檔

- [AISDLC_INIT.md](AISDLC_INIT.md) - 框架初始化
- [SMART_DEFAULTS.md](SMART_DEFAULTS.md) - 智能預設值配置
- [scenarios/SCENARIO_AGENT_MAPPING.md](scenarios/SCENARIO_AGENT_MAPPING.md) - Agent 分配權威來源
- [scenarios/*/SOP_QuickRef.md](scenarios/) - 各情境快速參考
- [QUICK_START_GUIDE.md](QUICK_START_GUIDE.md) - 快速啟動指南（含情境決策樹）

---

**文檔版本**: v0.09
**最後更新**: 2026-05-07
**更新內容**: 新增 6 個情境範本 (Refactoring/Migration/DevOps/Testing/Documentation/Security)，覆蓋 AISDLC 全部 10 大情境；更新 Agent 配置對齊 SCENARIO_AGENT_MAPPING.md v0.09；補充所有範本的 SOP 引用
**維護者**: AISDLC Framework Team
