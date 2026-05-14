# Deployment Checklist - Sprint 10 M16 ERP

**發布版本**: v1.0-Sprint10 (M16 ERP)
**發布日期**: 2026-05-13（暫定）
**發布類型**: Minor
**基於分支**: develop → main

---

## 🔴 Pre-Deployment Checklist

### 代碼準備

| 檢查項目 | 狀態 | 備註 |
|----------|------|------|
| Release branch 已建立 | ⏳ | 等待 CI 驗證後建立 |
| 版本號已更新 | ✅ | v1.0-Sprint10 |
| CHANGELOG 已更新 | ✅ | Release Notes 已建立 |
| Git tag 已建立 | ⏳ | 等待 CI 驗證後建立 |
| 所有功能已完成 | ✅ | M16 ERP 進銷存核心功能完成 |
| 所有 Bug 已修復或延後 | ✅ | 無待處理 High Bug |

### 環境準備

| 檢查項目 | 狀態 | 備註 |
|----------|------|------|
| 環境變數配置確認 | ✅ | 見 GITHUB_ENVIRONMENTS_SETUP.md |
| 資料庫遷移腳本準備 | ✅ | V17~V19 migrations 已就緒 |
| 回滾腳本準備 | ⏳ | 待建立 |
| 監控告警配置 | ⏳ | 待確認 |

### 通知準備

| 檢查項目 | 狀態 | 備註 |
|----------|------|------|
| 團隊通知 | ⏳ | 等待發布確認 |
| 客戶通知（如需要） | N/A | B2B2C 內部測試版本 |
| 維護公告（如需要） | N/A | 非必須 |

---

## 🚀 Deployment Steps

### Step 1: 準備 Release

```bash
# 1. 確認 CI 已通過
gh run list --limit 5

# 2. 建立 Release Tag
git tag -a v1.0-Sprint10 -m "Release Sprint 10 M16 ERP"
git push origin v1.0-Sprint10

# 3. 合併到 main
git checkout main
git merge develop
git push origin main
```

### Step 2: 部署到 Staging

```bash
# 1. SSH 到 Staging Server
ssh user@staging-server

# 2. 停止現有服務
cd /opt/ecommerce
./stop.sh

# 3. 更新代碼
git pull origin main

# 4. 執行資料庫 Migration
cd backend
mvn flyway:migrate -Dspring.profiles.active=staging

# 5. 重啟服務
./start.sh

# 6. 驗證健康檢查
curl -s http://localhost:8080/api/health
```

### Step 3: Staging 驗證

| 驗證項目 | 命令 | 預期結果 |
|----------|------|----------|
| 服務啟動 | `curl -s http://staging:8080/api/health` | `{"status":"UP"}` |
| 供應商 API | `curl -s http://staging:8080/api/v2/dashboard/suppliers` | 200 OK |
| 採購單 API | `curl -s http://staging:8080/api/v2/dashboard/purchase-orders` | 200 OK |
| 庫存 API | `curl -s http://staging:8080/api/v2/dashboard/inventory` | 200 OK |
| Frontend | `curl -s http://staging:3000` | 200 OK |

### Step 4: 部署到 Production

```bash
# 1. SSH 到 Production Server
ssh user@production-server

# 2. 執行與 Staging 相同步驟
```

---

## ✅ Post-Deployment Verification

### 服務健康檢查

| 檢查項目 | 狀態 | 備註 |
|----------|------|------|
| Backend API 健康 | ⏳ | |
| Frontend 可訪問 | ⏳ | |
| 資料庫連線正常 | ⏳ | |
| Redis 連線正常 | ⏳ | |

### 功能驗證

| 功能 | 測試場景 | 狀態 |
|------|----------|------|
| 供應商 CRUD | 新增/查詢/更新/刪除 | ⏳ |
| 採購單狀態機 | DRAFT→SUBMITTED→RECEIVED | ⏳ |
| 庫存異動 | INBOUND/OUTBOUND/ADJUST | ⏳ |
| 低庫存預警 | 查詢 low stock alerts | ⏳ |

### 煙霧測試

```bash
# 1. 供應商建立
curl -X POST http://localhost:8080/api/v2/dashboard/suppliers \
  -H "Content-Type: application/json" \
  -d '{"name":"Test Supplier","contactEmail":"test@supplier.com"}'

# 2. 採購單建立
curl -X POST http://localhost:8080/api/v2/dashboard/purchase-orders \
  -H "Content-Type: application/json" \
  -d '{"supplierId":1,"items":[{"skuId":"SKU001","quantity":100}]}'

# 3. 庫存查詢
curl -s http://localhost:8080/api/v2/dashboard/inventory
```

---

## 📋 Staging 驗證清單

### Backend API Tests

- [ ] GET /api/v2/dashboard/suppliers - 供應商列表
- [ ] POST /api/v2/dashboard/suppliers - 新增供應商
- [ ] GET /api/v2/dashboard/purchase-orders - 採購單列表
- [ ] POST /api/v2/dashboard/purchase-orders - 建立採購單 (DRAFT)
- [ ] PUT /api/v2/dashboard/purchase-orders/:id/submit - 提交採購單
- [ ] PUT /api/v2/dashboard/purchase-orders/:id/receive - 確認收貨
- [ ] GET /api/v2/dashboard/inventory - 庫存台帳
- [ ] GET /api/v2/dashboard/inventory/alerts - 低庫存預警

### Frontend UI Tests

- [ ] /dashboard/erp/suppliers - 供應商管理頁面
- [ ] /dashboard/erp/purchase-orders - 採購單管理頁面
- [ ] /dashboard/erp/inventory - 庫存台帳頁面

### Multi-tenancy Tests

- [ ] Tenant A 無法存取 Tenant B 的供應商
- [ ] Tenant A 無法存取 Tenant B 的採購單
- [ ] Tenant A 無法存取 Tenant B 的庫存

---

## 📅 預定時程

| 階段 | 日期 | 狀態 |
|------|------|------|
| CI 驗證 | 2026-06-01 | ⏳ 等待額度恢復 |
| Release Tag | 2026-06-01 | ⏳ |
| Staging 部署 | 2026-06-01 | ⏳ |
| Staging 驗證 | 2026-06-02 | ⏳ |
| Production 部署 | 2026-06-02 | ⏳ |

---

**文件狀態**：📝 草稿
**最後更新**：2026-05-13