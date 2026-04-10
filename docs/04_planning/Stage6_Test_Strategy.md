# Stage 6 測試策略 / Test Strategy

> **日期**: 2026-04-09
> **Stage**: 6 - 測試策略規劃
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **狀態**: ✅ 人機協作確認點 7.1 已確認

---

## 1. 測試策略總覽

### 1.1 測試金字塔

```
                    ┌─────────────┐
                    │    E2E      │  ← Playwright (少量但關鍵)
                    │   Tests     │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │    Integration Tests     │  ← API Tests (Spring Boot)
              │     (API Layer)          │
              └────────────┬────────────┘
                           │
        ┌──────────────────┴──────────────────┐
        │         Unit Tests                  │  ← JUnit 5 + Mockito
        │    (Business Logic Layer)           │
        └─────────────────────────────────────┘
```

### 1.2 測試覆蓋率目標

| 層級 | 目標覆蓋率 | 工具 |
|------|------------|------|
| Unit Tests | ≥ 80% | JUnit 5 + Mockito |
| Integration Tests | ≥ 70% | Spring Boot Test + Testcontainers |
| API Tests (E2E) | 100% P0 APIs | REST Assured / MockMvc |
| E2E Tests | 核心場景 | Playwright |

---

## 2. 測試環境規劃

### 2.1 環境架構

| 環境 | 用途 | 資料庫 | 部署方式 |
|------|------|--------|----------|
| Local | 開發者本機測試 | H2 (in-memory) | Docker Compose |
| Dev | 開發整合測試 | PostgreSQL (Dev) | GitHub Actions |
| Staging | 上線前驗證 | PostgreSQL (Staging) | GitHub Actions |
| Production | 正式環境 | PostgreSQL (Prod) | GitHub Actions |

### 2.2 測試資料策略

- **測試資料庫**: 每個 Sprint 使用 Flyway Migration 自動建立乾淨 schema
- **測試資料**: 使用 `@BeforeEach` + Builder Pattern 建立測試資料
- **Fixtures**: 共享的測試資料放在 `test/fixtures/`

---

## 3. 模組化測試策略

### 3.1 M03 會員系統測試

| 測試類型 | 測試內容 | 測試工具 |
|----------|----------|----------|
| Unit | BCrypt 加密邏輯 | JUnit 5 |
| Unit | JWT Token 產生/驗證 | JUnit 5 + Mockito |
| Unit | RBAC 權限判斷 | JUnit 5 |
| Integration | 會員註冊流程 | Spring Boot Test |
| Integration | 登入/登出流程 | Spring Boot Test |
| Integration | JWT Refresh 流程 | Spring Boot Test |
| API E2E | POST /api/v2/auth/register | REST Assured |
| API E2E | POST /api/v2/auth/login | REST Assured |

**關鍵驗證點**:
- 密碼不可明文儲存
- JWT Token 過期後 Refresh Token 可續命
- 角色繼承正確 (OWNER > STAFF > BUYER)

### 3.2 M17 租戶管理測試

| 測試類型 | 測試內容 | 測試工具 |
|----------|----------|----------|
| Unit | TenantContext ThreadLocal 管理 | JUnit 5 |
| Integration | 租戶申請流程 | Spring Boot Test |
| Integration | Admin 審核通過/駁回 | Spring Boot Test |
| Integration | Feature Toggle 查詢/更新 | Spring Boot Test |
| API E2E | POST /api/v2/tenants/apply | REST Assured |
| API E2E | GET /api/v2/tenants | REST Assured |
| API E2E | POST /api/v2/admin/tenants/:id/approve | REST Assured |

**關鍵驗證點**:
- 多租戶資料隔離 (Tenant A 看不到 Tenant B 資料)
- Feature Toggle 狀態正確反映

### 3.3 M01 商品管理測試

| 測試類型 | 測試內容 | 測試工具 |
|----------|----------|----------|
| Unit | Listing 統一抽象邏輯 | JUnit 5 |
| Integration | 商品上架/編輯/下架 | Spring Boot Test |
| Integration | 商品搜尋與過濾 | Spring Boot Test |
| Integration | 商品分類查詢 | Spring Boot Test |
| API E2E | GET /api/v2/listings | REST Assured |
| API E2E | POST /api/v2/dashboard/listings | REST Assured |

**關鍵驗證點**:
- Listing Type (PRODUCT/ROOM) 正確區分
- 商品狀態機: DRAFT → ACTIVE → INACTIVE → DELETED

### 3.4 M05 訂單管理測試

| 測試類型 | 測試內容 | 測試工具 |
|----------|----------|----------|
| Unit | 訂單狀態機邏輯 | JUnit 5 + State Machine |
| Unit | 庫存扣減邏輯 (樂觀鎖) | JUnit 5 |
| Integration | 建立訂單流程 | Spring Boot Test |
| Integration | 取消訂單 + 回滾 | Spring Boot Test |
| Integration | 訂單狀態更新 | Spring Boot Test |
| API E2E | POST /api/v2/orders | REST Assured |
| API E2E | GET /api/v2/orders/:id | REST Assured |
| API E2E | PUT /api/v2/orders/:id/status | REST Assured |

**關鍵驗證點**:
- 訂單狀態機轉換正確
- 並發庫存扣減不超賣
- 取消訂單後庫存回滾

### 3.5 M02 房源管理測試

| 測試類型 | 測試內容 | 測試工具 |
|----------|----------|----------|
| Unit | Room Calendar 日期衝突判斷 | JUnit 5 |
| Integration | 房源上架/編輯/下架 | Spring Boot Test |
| Integration | 日曆查詢 (可用日期) | Spring Boot Test |
| Integration | 預訂衝突檢查 | Spring Boot Test + Redis |
| API E2E | GET /api/v2/listings/:id/calendar | REST Assured |

**關鍵驗證點**:
- Redis 分散式鎖防止雙重預訂
- 日曆可用日期計算正確

### 3.6 M12 動態定價測試

| 測試類型 | 測試內容 | 測試工具 |
|----------|----------|----------|
| Unit | 價格計算引擎邏輯 | JUnit 5 |
| Unit | 規則優先級排序 | JUnit 5 |
| Integration | 定價規則 CRUD | Spring Boot Test |
| Integration | 價格計算 (含折扣) | Spring Boot Test |
| Integration | 手動覆蓋優先於規則 | Spring Boot Test |
| API E2E | GET /api/v2/listings/:id/price | REST Assured |
| API E2E | POST /api/v2/dashboard/pricing/rules | REST Assured |

**關鍵驗證點**:
- 價格計算流程: Override → Season → Weekend → Discount
- 早鳥/長住折扣正確計算
- 優先級高規則覆蓋低優先級規則

---

## 4. 測試執行策略

### 4.1 CI/CD 自動化測試

```
Push/PR
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  Stage 1: Build & Unit Tests                        │
│  ├── mvn clean compile                             │
│  ├── mvn test (Unit Tests)                         │
│  └── SonarQube Code Quality Gate                    │
└─────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  Stage 2: Integration Tests                         │
│  ├── docker-compose up (PostgreSQL + Redis)         │
│  ├── mvn verify (Integration Tests)                 │
│  └── Testcontainers (if available)                  │
└─────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  Stage 3: API E2E Tests                             │
│  ├── Deploy to ephemeral environment                 │
│  ├── Run API E2E tests                              │
│  └── Generate API documentation                     │
└─────────────────────────────────────────────────────┘
    │
    ▼
┌─────────────────────────────────────────────────────┐
│  Stage 4: Build & Package                           │
│  ├── mvn package                                    │
│  ├── Build Docker Image                             │
│  └── Push to Container Registry                     │
└─────────────────────────────────────────────────────┘
```

### 4.2 測試執行時機

| 階段 | 何時執行 | 失敗策略 |
|------|----------|----------|
| Unit Tests | 每次 Push/PR | Block merge |
| Integration Tests | 每次 PR | Block merge |
| API E2E Tests | 每日 / Release 前 | Block release |
| E2E Tests (Playwright) | Sprint 結束前 | Must pass before demo |

---

## 5. 測試資料管理

### 5.1 測試資料 Fixture

```java
// test/fixtures/MemberFixture.java
public class MemberFixture {
    public static Member buyer() {
        return Member.builder()
            .email("buyer@test.com")
            .role(Role.BUYER)
            .build();
    }

    public static Member storeOwner(UUID tenantId) {
        return Member.builder()
            .email("owner@test.com")
            .role(Role.STORE_OWNER)
            .tenantId(tenantId)
            .build();
    }
}
```

### 5.2 Database Migration 測試策略

- 每個 Migration 必須有對應的 Rollback SQL
- Integration Tests 使用 `@FlywayTest` 自動執行 Migration
- 測試資料庫隔離: 每個測試類別使用 `@Transactional` + Rollback

---

## 6. 效能測試策略

### 6.1 效能測試場景

| 場景 | 目標指標 | 工具 |
|------|----------|------|
| 登入並取得 Token | < 100ms | JMeter |
| 商品搜尋 | < 200ms (P95) | JMeter |
| 房源日曆查詢 | < 300ms (P95) | JMeter |
| 建立訂單 | < 500ms (P95) | JMeter |
| 價格計算 | < 100ms | JMeter |

### 6.2 負載測試

- 目標: 100 concurrent users
- 預熱: 5 分鐘
- 持續: 15 分鐘
- 監控: APM (Application Performance Monitoring)

---

## 7. 安全測試策略

### 7.1 安全測試項目

| 測試項目 | 工具 | 執行頻率 |
|----------|------|----------|
| OWASP Top 10 掃描 | ZAP (Zed Attack Proxy) | 每月 |
| SQL Injection 測試 | 內建測試案例 | 每次 PR |
| XSS 測試 | 內建測試案例 | 每次 PR |
| JWT 有效性測試 | 內建測試案例 | 每次 PR |
| CORS 配置檢查 | 內建測試案例 | 每次 PR |

### 7.2 JWT 安全驗證

- [ ] Token 過期後不可使用
- [ ] Refresh Token 不可跨用戶使用
- [ ] 登出後 Token 應失效 (Blacklist 或縮短 TTL)

---

## 8. E2E 測試策略 (Playwright)

### 8.1 核心 E2E 場景

| 測試案例 | 描述 | 優先級 |
|----------|------|--------|
| TC-E2E-001 | 會員註冊 → 登入 → 登出 | P0 |
| TC-E2E-002 | 店鋪申請 → Admin 審核 → 開店 | P0 |
| TC-E2E-003 | 商品上架 → 搜尋 → 加入購物車 | P0 |
| TC-E2E-004 | 建立訂單 → 支付 → 查看訂單 | P0 |
| TC-E2E-005 | 房源上架 → 日曆查詢 → 預訂 | P1 |

### 8.2 Playwright 配置

```javascript
// playwright.config.js
module.exports = {
  testDir: './e2e',
  timeout: 30000,
  retries: 2,
  use: {
    baseURL: process.env.E2E_BASE_URL || 'http://localhost:3000',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  }
};
```

---

## 9. 測試交付物

| 交付物 | 位置 | 說明 |
|--------|------|------|
| 單元測試報告 | `backend/target/surefire-reports/` | JUnit XML |
| 整合測試報告 | `backend/target/failsafe-reports/` | JUnit XML |
| API E2E 報告 | `backend/target/api-e2e-reports/` | REST Assured |
| E2E 測試報告 | `frontend/playwright-report/` | Playwright HTML |
| 測試覆蓋率報告 | SonarQube Dashboard | Cobertura |
| 效能測試報告 | `docs/03_testing/performance/` | JMeter HTML |

---

## 10. QA 工作分配

假設 1 人 QA (Sprint 1-5) / 2 人 QA (Sprint 6+):

| Sprint | QA 工作重點 |
|--------|------------|
| Sprint 0 | 測試環境建置、測試策略制定 |
| Sprint 1 | M03 測試案例撰寫、執行 |
| Sprint 2 | M17 + M01 測試案例、執行 |
| Sprint 3 | M05 測試案例、重點: 訂單狀態機 |
| Sprint 4 | M02 測試案例、重點: Redis 鎖 |
| Sprint 5 | M12 測試案例、重點: 價格計算 |
| Sprint 6 | 整合測試、E2E 測試、效能測試 |

---

## 📁 相關文件

| 文件 | 路徑 |
|------|------|
| Stage 5 User Story 確認 | `docs/04_planning/Stage5_UserStory_Confirmation.md` |
| Stage 6 Sprint 規劃 | `docs/04_planning/Stage6_Sprint_Planning.md` |
| API 規格 | `docs/02_architecture/api/` |
| 資料庫 Schema | `docs/02_architecture/SRD_Database_Schema.md` |

---

**文件結束**
