# Settlement Scheduled Job Runbook / 結算單排程任務手冊

> **用途**: Staging 環境手動觸發結算單生成任務的操作手冊
> **建立日期**: 2026-06-05
> **🔴 Sprint 16 US-003 / Retro AI-003**: 補上 Staging 環境實測 Runbook
> **基於**: [SPRINT_16_PLAN.md](../04_planning/SPRINT_16_PLAN.md) US-003, [SPRINT_15_RETRO.md](../05_development/SPRINT_15_RETRO.md) AI-003

> **⚠️ Sprint 205 更正（2026-09-27）：本 Runbook 建立於 Sprint 16，之後結算邏輯已大幅改動，下列敘述與程式不符，請勿照做。**
>
> | 本文件的說法 | 實際（依程式碼查證） |
> |-------------|-------------------|
> | §2 觸發時間為 **UTC** 週一 00:00 | Sprint 194（DEF-271）起是 `@Scheduled(cron = "0 0 0 ? * MON", zone = "Asia/Taipei")`，即**台灣時間**週一 00:00 |
> | §3.2 環境變數 `SETTLEMENT_TRIGGER_MODE=MANUAL` | 程式碼**沒有任何地方讀取**這個變數，設了無效 |
> | §3.3 以 `scheduling.settlement.cron` 覆蓋 cron | cron 是**寫死在 `@Scheduled` 註解**，不可由設定覆蓋；「替代方案」不存在 |
> | §3.3／§4.1 手動觸發或「透過 admin 介面」補建結算單 | **沒有任何手動觸發或補產入口**（`generateWeeklyStatements` 只被排程呼叫），見 DEF-287 |
> | §2 結算範圍「上週一 ~ 上週日」 | 結算單期間仍是上週，但 Sprint 195（PRD §6.2.1）起**納入所有「已完成且尚未結算」的訂單，不限下單週**（`orders.settled_statement_id`）；首張結算單會包含全部歷史已完成訂單 |
>
> 其餘章節（§3.4 驗證 SQL、§4 異常處理等）**未重新核對**，引用前請先與程式碼比對。撥款（Transfer）的上線項目見 [STRIPE_PRODUCTION_CHECKLIST.md](STRIPE_PRODUCTION_CHECKLIST.md)。

> **🔴 Sprint 219 更正（2026-09-30，DEF-305）：這個排程在此之前從未自動執行過。** 全專案自第一個 commit 起就沒有 `@EnableScheduling`，`@Scheduled` 不會被 Spring 處理；§4.4 列的「應用是否啟用 `@EnableScheduling`」檢查項從來沒有人真的檢查過。Sprint 219 起由 `SchedulingConfig` 啟用（專屬執行緒池 `app-scheduler-*`）：
>
> - **啟用後第一個台灣時間週一 00:00 會自動產生結算單**，且依 Sprint 195 的語意納入**所有「已完成且尚未結算」的訂單、不限下單週**——若正式環境已有歷史已完成訂單，第一張結算單會很大。結算單產生後是待審核狀態，撥款要經過審核／核准，不會因產生而動錢。
> - 想在正式環境先人工確認再讓它自動跑：部署時設 `APP_SCHEDULING_ENABLED=false`（同時關閉通知佇列消費與未付款訂單取消），用管理員手動觸發端點 `POST /v2/admin/settlements/generate`（SUPER_ADMIN，Sprint 208／DEF-287）檢視結果，之後再開。
> - 相關設定：`APP_SCHEDULING_ENABLED`（預設 true）、`APP_SCHEDULING_POOL_SIZE`（預設 4）、`ORDER_UNPAID_TIMEOUT_HOURS`（預設 24）、`ORDER_TIMEOUT_CHECK_INTERVAL_MS`（預設 300000）、`ORDER_TIMEOUT_INITIAL_DELAY_MS`（預設 60000）、`ORDER_TIMEOUT_BATCH_SIZE`（預設 100）。
> - 多個後端實例同時執行是安全的：結算單有「同期間已存在」的冪等檢查與訂單原子認領（Sprint 212 已驗證），佇列消費用 Redis 原子 pop，未付款訂單取消是 CAS。

---

## 1. 為什麼需要這個 Runbook？

Sprint 15 結算單 Scheduled Job 設計為 `@Scheduled(cron = "0 0 0 ? * MON")`，每週一 00:00 自動觸發。但 Sprint 15 結束時**沒有 Staging 環境實測驗證**，是 Sprint 15 Retrospective 識別的 P0 風險（AI-003）。

本 Runbook 規範 Staging 環境的：
- 手動觸發步驟
- 異常情境處理
- 監控指標
- 緊急回滾

---

## 2. 自動觸發條件

| 項目 | 值 |
|------|-----|
| Cron 表達式 | `0 0 0 ? * MON` |
| 觸發時間 | 每週一 00:00 (UTC) |
| 結算範圍 | 上週一 ~ 上週日 |
| 影響對象 | 所有 `status = ACTIVE` 的 Tenants |

**注意**: 觸發時間為 UTC。若 Staging 環境時區為 UTC+8，實際本地時間為**每週一 08:00**。

---

## 3. Staging 環境手動觸發步驟

### 3.1 前置檢查

```bash
# 1. 確認 Staging 環境可訪問
curl -sf https://staging-api.example.com/actuator/health

# 2. 確認 Redis 連線正常
redis-cli -h staging-redis ping

# 3. 確認資料庫連線正常
psql -h staging-db -U app -d ecommerce -c "SELECT COUNT(*) FROM tenants WHERE status = 'ACTIVE';"
```

### 3.2 環境變數設定

```bash
# 設定環境變數
export SETTLEMENT_TRIGGER_MODE=MANUAL
export SPRING_PROFILES_ACTIVE=staging

# 重新啟動應用（若需要）
kubectl rollout restart deployment/backend -n staging
```

### 3.3 透過 Actuator 手動觸發

> 🔴 **待辦**: 目前應用尚未提供 Actuator 觸發端點。後續可考慮新增：
> `POST /actuator/scheduled/settlement/weekly`

**目前替代方案**：在 Staging 環境部署時**調整 cron 觸發時間**到當下：

```yaml
# application-staging.yml
scheduling:
  settlement:
    # Staging: 部署後 5 分鐘觸發一次
    cron: "0 */5 * * * *"
```

### 3.4 觸發後驗證

```sql
-- 1. 檢查上週 (週一 ~ 週日) 結算單數量
SELECT
    t.name AS tenant_name,
    s.statement_number,
    s.period_start,
    s.period_end,
    s.total_orders,
    s.total_gmv,
    s.total_refunds,
    s.commission_amount,
    s.net_settlement_amount,
    s.status,
    s.generated_at
FROM settlement_statements s
JOIN tenants t ON t.id = s.tenant_id
WHERE s.period_start = CURRENT_DATE - INTERVAL '7 days' - (EXTRACT(DOW FROM CURRENT_DATE - INTERVAL '7 days') - 1) * INTERVAL '1 day'
ORDER BY t.name;
```

**預期結果**:
- 至少有 1 個 ACTIVE tenant 對應 1 個 PENDING 結算單
- 結算單號格式: `STL-{tenantId前8碼}-{yyyyMMdd}`
- 金額計算正確（GMV - 抽成 - 退款 = 結算金額）

---

## 4. 異常情境處理

### 4.1 情境 A: 某個 Tenant 處理失敗

**現象**: 應用日誌出現 `Failed to generate settlement statement for tenant: {tenantId}`

**影響**: 該 Tenant 當週無結算單，**但其他 Tenant 仍正常處理**（異常隔離設計）

**處理**:
```bash
# 1. 查看失敗原因
kubectl logs -n staging deployment/backend | grep "Failed to generate settlement" | tail -20

# 2. 若為 DB 暫時性錯誤，重試
psql -h staging-db -U app -d ecommerce -c "SELECT * FROM settlement_statements WHERE tenant_id = '{tenantId}';"

# 3. 若仍無結算單，手動補建（透過 admin 介面或 SQL）
```

### 4.2 情境 B: 重複觸發建立多個結算單

**現象**: 同一期間有多個結算單

**預期行為**: 不會發生。`generateStatementForTenant` 內有冪等性檢查：
```java
List<SettlementStatement> existingStatements = settlementRepository.findByTenantIdAndPeriodStartBetween(
        tenantId, periodStart, periodEnd);
if (!existingStatements.isEmpty()) {
    return existingStatements.get(0);
}
```

**若仍發生**: 檢查 cron 表達式是否正確，避免重複觸發。

### 4.3 情境 C: 無 ACTIVE Tenants

**現象**: 沒有任何結算單被建立

**處理**: 這是預期行為（沒有 ACTIVE Tenants 就無需結算）。檢查 SQL：
```sql
SELECT id, name, status FROM tenants WHERE status = 'ACTIVE';
```

### 4.4 情境 D: Scheduled Job 完全沒觸發

**檢查**:
1. 應用是否啟用 `@EnableScheduling`
2. cron 表達式是否正確
3. 應用時區是否正確
4. 應用日誌是否有 `Starting weekly settlement statement generation`

```bash
# 查看應用日誌
kubectl logs -n staging deployment/backend | grep "settlement" | tail -50
```

---

## 5. 監控指標

### 5.1 關鍵指標 (KPI)

| 指標 | 預期值 | 告警條件 |
|------|--------|----------|
| 結算單生成成功率 | 100% | < 100% |
| 結算單生成耗時 | < 30 秒/100 個 tenant | > 60 秒 |
| 結算單冪等性 | 0 重複 | > 0 |
| 金額計算錯誤 | 0 | > 0 |

### 5.2 Grafana Dashboard（建議）

```
# 結算單生成成功率
rate(settlement_generation_success_total[5m])
/ rate(settlement_generation_total[5m])

# 結算單生成耗時 P99
histogram_quantile(0.99, settlement_generation_duration_seconds)
```

### 5.3 告警規則（建議）

```yaml
- alert: SettlementGenerationFailed
  expr: rate(settlement_generation_failures[5m]) > 0
  for: 1m
  annotations:
    summary: 結算單生成失敗
    description: 至少 1 個 tenant 結算單生成失敗，請查看日誌
```

---

## 6. 緊急回滾

若生產環境結算單生成出現嚴重問題：

```bash
# 1. 停用 Scheduled Job
kubectl set env deployment/backend -n production ENABLE_SCHEDULED_JOBS=false

# 2. 確認已停用
kubectl logs -n production deployment/backend | grep "Scheduling disabled"

# 3. 聯繫 DBA 處理資料
# - 刪除錯誤的結算單
# - 或重跑結算（修改 periodStart 強制觸發）
```

---

## 7. 本地測試替代方案

由於本地開發環境通常無 Staging，本地測試可使用：
- `SettlementScheduledJobIntegrationTest` (Sprint 16 US-003 新增)
- 6 個 Mockito 測試案例覆蓋多租戶、冪等性、異常隔離、結算單號格式

```bash
# 執行本地測試
cd backend
mvn test -Dtest=SettlementScheduledJobIntegrationTest
```

---

## 8. 相關文件

| 文件 | 路徑 |
|------|------|
| Sprint 16 Plan | `docs/04_planning/SPRINT_16_PLAN.md` |
| Sprint 15 Retro (AI-003) | `docs/05_development/SPRINT_15_RETRO.md` |
| SettlementService | `backend/src/main/java/com/nextkey/ecommerce/core/settlement/SettlementService.java` |
| SettlementGenerator | `backend/src/main/java/com/nextkey/ecommerce/core/settlement/SettlementGenerator.java` |
| 本地測試 | `backend/src/test/java/com/nextkey/ecommerce/core/settlement/SettlementScheduledJobIntegrationTest.java` |

---

**文件版本**: AISDLC v0.09
**作者**: Claude Code (AI Assistant)
**建立日期**: 2026-06-05
