# Rollback Plan - Sprint 10 M16 ERP

**發布版本**: v1.0-Sprint10 (M16 ERP)
**發布日期**: 2026-05-13（暫定）
**基於分支**: develop → main

---

## 🔴 Rollback 觸發條件

### 自動回滾條件

| 條件 | 閾值 | 說明 |
|------|------|------|
| 錯誤率 | > 5% | 生產環境 5 分鐘內錯誤率超過 5% |
| P99 延遲 | > 5s | API 回應時間 P99 超過 5 秒 |
| 核心功能失敗 | - | 供應商/採購單/庫存 API 完全無法使用 |
| 資料庫連線失敗 | - | 無法連接到 PostgreSQL 或 Redis |

### 手動回滾條件

- 業務決策需要
- 重大 Bug 發現（非 Critical 但影響業務流程）
- Security Issue 發現

---

## 📋 Rollback 步驟

### Step 1: 確認回滾決策

```bash
# 檢查當前服務狀態
curl -s http://localhost:8080/api/health
curl -s http://localhost:3000

# 檢查錯誤日誌
tail -100 /var/log/ecommerce/error.log
```

### Step 2: 執行回滾

#### 方式 A: Git Tag 回滾（推薦）

```bash
# 1. 確認當前版本
git log --oneline -5

# 2. 回到上一個稳定版本
git checkout v1.0-Sprint9  # 或上一個 stable tag

# 3. 停止服務
cd /opt/ecommerce
./stop.sh

# 4. 重新部署
git pull origin v1.0-Sprint9
./start.sh

# 5. 驗證
curl -s http://localhost:8080/api/health
```

#### 方式 B: Git Branch 回滾

```bash
# 1. 回到 develop 分支最後 stable 版本
git checkout develop
git reset --hard <last-stable-commit-hash>

# 2. 重啟服務
./stop.sh && ./start.sh
```

### Step 3: 驗證回滾

```bash
# 服務健康檢查
curl -s http://localhost:8080/api/health

# 核心功能驗證
curl -s http://localhost:8080/api/v2/dashboard/suppliers
curl -s http://localhost:8080/api/v2/dashboard/purchase-orders
curl -s http://localhost:8080/api/v2/dashboard/inventory

# Frontend 驗證
curl -s http://localhost:3000
```

### Step 4: 通知團隊

```bash
# 通知相關人員
- PM/PO: 回滾原因和影響範圍
- QA: 需要重新驗證的功能
- Dev: 回滾後的補救措施
```

### Step 5: 記錄回滾原因

```markdown
## 回滾記錄

**日期**: [YYYY-MM-DD]
**版本**: v1.0-Sprint10
**回滾原因**:
- [具體原因]

**影響範圍**:
- [影響的功能]

**補救措施**:
- [需要做的修復]
```

---

## 🔧 資料庫 Rollback

### Flyway Migration Rollback

```bash
# 檢查 Migration 狀態
cd backend
mvn flyway:info -Dspring.profiles.active=production

# 回滾到指定版本
mvn flyway:rollback -Dspring.profiles.active=production -Dflyway.target=V16

# 驗證
mvn flyway:info -Dspring.profiles.active=production
```

### 手動 SQL Rollback（如需要）

```sql
-- V19 回滾（如果需要）
DROP TRIGGER IF EXISTS update_stock_on_movement;
DROP TABLE IF EXISTS stock_movements;

-- V18 回滾
DROP TABLE IF EXISTS purchase_order_items;
DROP TABLE IF EXISTS purchase_orders;
DROP TABLE IF EXISTS suppliers;

-- V17 回滾（通常不需要）
-- V17 是新增表格，不建議手動刪除
```

---

## ⚠️ 緊急聯絡資訊

| 角色 | 聯絡方式 | 職責 |
|------|----------|------|
| Dev Lead | @wuweihungmobile | 技術決策和執行 |
| PM/PO | - | 業務決策 |
| DBA | - | 資料庫緊急狀況 |

---

## 📊 回滾後監控

### 監控項目（回滾後 24 小時）

| 項目 | 頻率 | 閾值 |
|------|------|------|
| 錯誤率 | 每 15 分鐘 | > 1% |
| API 延遲 | 每 15 分鐘 | P99 > 2s |
| 服務可用性 | 每 5 分鐘 | < 99.5% |
| 資料庫效能 | 每 30 分鐘 | QPS < 正常值 50% |

### 監控命令

```bash
# 查看錯誤日誌
tail -500 /var/log/ecommerce/error.log | grep ERROR

# 查看 API 延遲
curl -s http://localhost:8080/api/metrics | grep latency

# 查看資料庫連線
psql -c "SELECT count(*) FROM pg_stat_activity WHERE datname='nextkeydb'"
```

---

## ✅ Rollback 完成檢查清單

- [ ] 確認服務已回滾到上一個穩定版本
- [ ] 確認資料庫 Migration 已回滾（如需要）
- [ ] 確認服務健康檢查通過
- [ ] 確認核心功能可正常運作
- [ ] 確認無資料損失
- [ ] 團隊通知已完成
- [ ] 回滾原因已記錄

---

**文件狀態**：📝 草稿
**最後更新**：2026-05-13