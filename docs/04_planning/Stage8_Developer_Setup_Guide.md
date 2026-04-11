# Stage 8 開發環境設定指南 / Developer Setup Guide

> **日期**: 2026-04-10
> **Stage**: 8 - 開發準備與規範制定
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **狀態**: Draft

---

## 1. 環境需求

### 1.1 必要工具

| 工具 | 版本 | 用途 |
|------|------|------|
| Docker | 24.x+ | 容器化環境 |
| Docker Compose | 2.x+ | 多容器編排 |
| Java (JDK) | 21 | 後端運行/開發 |
| Node.js | 20.x | 前端開發 |
| Git | 2.x | 版本控制 |
| IDE | - | 開發編輯器 |

### 1.2 推薦 IDE 配置

**VS Code Extensions**:
```json
// .vscode/extensions.json
{
  "recommendations": [
    // Java
    "vscjava.vscode-java-pack",
    "redhat.java",
    "vscjava.vscode-gradle",
    "vscjava.vscode-spring-boot-dashboard",

    // Frontend
    "dbaeumer.vscode-eslint",
    "esbenp.prettier-vscode",
    "bradlc.vscode-tailwindcss",
    "ms-vscode.vscode-typescript-next",

    // Other
    "eamodio.gitlens",
    "oderwat.indent-rainbow"
  ]
}
```

**VS Code Settings**:
```json
// .vscode/settings.json
{
  // Java
  "java.configuration.runtimes": [
    { "name": "JavaSE-21", "path": "/path/to/jdk-21" }
  ],
  "java.compile.nullAnalysis.mode": "automatic",
  "java.codeGeneration.useBlocks": true,

  // ESLint
  "eslint.validate": ["javascript", "typescript"],
  "eslint.format.enable": true,

  // Prettier
  "editor.defaultFormatter": "esbenp.prettier-vscode",
  "editor.formatOnSave": true,
  "editor.codeActionsOnSave": {
    "source.fixAll.eslint": "explicit"
  },

  // TypeScript
  "typescript.tsdk": "frontend/node_modules/typescript/lib",

  // Tailwind
  "tailwindCSS.includeLanguages": {
    "typescriptreact": "html"
  }
}
```

---

## 2. 快速開始 (使用 Docker)

### 2.1 初次設定

```bash
# 1. Clone 專案
git clone https://github.com/your-org/ecommerce.git
cd ecommerce

# 2. 複製環境變數範本
cp backend/src/main/resources/application.yml.example backend/src/main/resources/application.yml
cp frontend/.env.example frontend/.env.local

# 3. 啟動開發環境 (使用 override)
docker compose up -d

# 4. 等待服務啟動
docker compose ps

# 5. 驗證服務
curl http://localhost:3000          # Frontend
curl http://localhost:8080/actuator/health  # Backend
```

### 2.2 啟動/停止服務

```bash
# 啟動所有服務
docker compose up -d

# 啟動特定服務
docker compose up -d backend

# 停止所有服務
docker compose down

# 停止並刪除 volume (乾淨重置)
docker compose down -v

# 查看日誌
docker compose logs -f backend
docker compose logs -f frontend
```

---

## 3. 本機開發設定 (不使用 Docker)

### 3.1 後端設定 (Spring Boot)

**前置條件**:
- JDK 21 installed
- PostgreSQL 18 running
- Redis 7 running

```bash
# 1. 進入 backend 目錄
cd backend

# 2. 使用 Gradle wrapper
chmod +x gradlew

# 3. 編譯專案
./gradlew clean compile

# 4. 執行測試
./gradlew test

# 5. 啟動開發伺服器 (with hot reload)
./gradlew bootRun

# 6. 或打包後執行
./gradlew bootJar
java -jar build/libs/*.jar
```

**手動啟動 PostgreSQL 和 Redis**:
```bash
# PostgreSQL
docker run -d \
  --name ecommerce-postgres \
  -e POSTGRES_USER=koala \
  -e POSTGRES_PASSWORD=koala5 \
  -e POSTGRES_DB=ecommerce \
  -p 5432:5432 \
  postgres:18-alpine

# Redis
docker run -d \
  --name ecommerce-redis \
  -e REDIS_PASSWORD=redis-dev-password \
  -p 6379:6379 \
  redis:7-alpine
```

### 3.2 前端設定 (Next.js)

**前置條件**:
- Node.js 20.x installed
- npm or pnpm

```bash
# 1. 進入 frontend 目錄
cd frontend

# 2. 安裝依賴
npm ci

# 3. 複製環境變數
cp .env.example .env.local

# 4. 啟動開發伺服器 (with hot reload)
npm run dev

# 5. 建置生產版本
npm run build

# 6. 執行測試
npm test

# 7. ESLint 檢查
npm run lint
```

---

## 4. 資料庫設定

### 4.1 Flyway Migration

```bash
# 自動執行 (應用啟動時)
# 或手動執行
./gradlew flywayMigrate

# 清理資料庫 (危險!)
./gradlew flywayClean

# 驗證 Migration 狀態
./gradlew flywayInfo
```

### 4.2 創建 Migration 檔案

```bash
# 命名慣例: V{version}__{description}.sql
# 例如: V1.0.0__create_members_table.sql

# 位置: backend/src/main/resources/db/migration/
```

**Migration 範例**:
```sql
-- V1.0.0__create_members_table.sql
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'BUYER',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);
```

---

## 5. 環境變數配置

### 5.1 後端環境變數

```bash
# backend/src/main/resources/application.yml
# 或使用系統環境變數

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ecommerce
    username: koala
    password: koala5
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration

jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-for-development-only}
  access-token-expiration: 900000    # 15 minutes
  refresh-token-expiration: 2592000000  # 30 days

redis:
  host: localhost
  port: 6379
  password: ${REDIS_PASSWORD:redis-dev-password}
```

### 5.2 前端環境變數

```bash
# frontend/.env.local
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v2
NEXT_PUBLIC_APP_ENV=development
```

---

## 6. 常見問題處理

### 6.1 Docker 相關

**Q: Docker container 啟動失敗**
```bash
# 檢查容器日誌
docker compose logs -f <service-name>

# 檢查端口衝突
lsof -i :3000
lsof -i :8080

# 清理並重啟
docker compose down -v
docker compose up -d --force-recreate
```

**Q: 資料庫連接失敗**
```bash
# 檢查 PostgreSQL 是否運行
docker compose ps postgres

# 測試連接
docker compose exec postgres psql -U koala -d ecommerce

# 重啟資料庫
docker compose restart postgres
```

### 6.2 後端相關

**Q: Gradle build 失敗**
```bash
# 清理並重新下載依賴
./gradlew clean --refresh-dependencies

# 檢查 Java 版本
java -version  # 必須是 21
```

**Q: 測試失敗**
```bash
# 只執行特定測試
./gradlew test --tests "*MemberServiceTest*"

# 查看詳細錯誤
./gradlew test --info
```

### 6.3 前端相關

**Q: npm install 失敗**
```bash
# 刪除 node_modules 並重新安裝
rm -rf frontend/node_modules
cd frontend
npm ci

# 清除 npm cache
npm cache clean --force
```

**Q: Next.js build 失敗**
```bash
# 清除 .next 目錄
rm -rf frontend/.next
npm run build
```

---

## 7. 開發工作流程

### 7.1 每日開發流程

```bash
# 1. 開始新工作前，先 pull 最新代码
git checkout develop
git pull origin develop

# 2. 從 develop 创建功能分支
git checkout -b feature/US-M03-001

# 3. 開發功能
# ... write code ...

# 4. 提交代碼 (遵循 commit 規範)
git add .
git commit -m "feat(M03): Add member registration (US-M03-001)"

# 5. 推送分支
git push -u origin feature/US-M03-001

# 6. 創建 Pull Request
# 在 GitHub 上创建 PR 并请求 code review
```

### 7.2 測試流程

```bash
# 後端測試
cd backend
./gradlew test                    # 單元測試
./gradlew integrationTest         # 整合測試
./gradlew test jacocoTestReport   # 產生覆蓋率報告

# 前端測試
cd frontend
npm test                          # 單元測試
npm run test:e2e                  # E2E 測試

# 完整 CI 檢查 (push 前)
npm run lint
npm run type-check
npm test
npm run build
```

---

## 8. 驗證清單

### 8.1 環境設定完成檢查

- [ ] Docker 已安裝並運行
- [ ] PostgreSQL 服務正常
- [ ] Redis 服務正常
- [ ] JDK 21 已設定
- [ ] Node.js 20.x 已設定
- [ ] 環境變數已配置
- [ ] 依賴已成功安裝

### 8.2 服務運行檢查

- [ ] Frontend 可訪問 (http://localhost:3000)
- [ ] Backend 健康檢查通過 (http://localhost:8080/actuator/health)
- [ ] 資料庫 Migration 已執行
- [ ] API 可正常呼叫

### 8.3 開發工具檢查

- [ ] IDE Extensions 已安裝
- [ ] ESLint 可正常運行
- [ ] Git hooks 已設定
- [ ] Code formatting 可正常運行

---

## 📁 相關文件

| 文件 | 路徑 |
|------|------|
| 開發規範 | `docs/04_planning/Stage8_Coding_Standards.md` |
| CI/CD Pipeline | `.github/workflows/ci.yml` |
| Docker Compose | `docker-compose.yml` |
| Stage 6 Sprint 規劃 | `docs/04_planning/Stage6_Sprint_Planning.md` |

---

**文件結束**
