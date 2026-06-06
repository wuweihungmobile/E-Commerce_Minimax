# M07 結算單測試案例 / Settlement Test Cases

> **模組**: M07 金流系統 - 結算子模組
> **版本**: v1.0
> **建立日期**: 2026-06-05
> **依據**: API_Index.md (Sprint 15-16 新增 API), SettlementService (拆分後)
> **測試框架**: JUnit 5 + Mockito (UT), Spring Boot Test (IT), REST Assured (API)
> **🔴 Sprint 16 US-008 / Retro DI-003**: 新建結算測試案例文件

---

## 📋 測試案例總覽

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 4 | 3 | 2 | 9 |
| IT | 3 | 2 | 1 | 6 |
| API | 3 | 2 | 1 | 6 |
| **合計** | 10 | 7 | 4 | **21** |

---

## 1. 單元測試 (Unit Tests)

### 1.1 SettlementCalculator 金額計算

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M07-S-001 | 計算 GMV - 無退款 | P0 | 5 筆 COMPLETED 訂單 | 1. 計算 totalGmv | GMV = 訂單金額總和 |
| UT-M07-S-002 | 計算 GMV - 0 筆訂單 | P0 | 空訂單列表 | 1. 計算 totalGmv | GMV = 0.00 |
| UT-M07-S-003 | 計算抽成 - 10% 比例 | P0 | GMV = 10000 | 1. 計算 commission | commission = 1000.00 |
| UT-M07-S-004 | 計算結算金額 - 無退款 | P0 | GMV=10000, commission=1000, refunds=0 | 1. 計算 netSettlement | net = 9000.00 |
| UT-M07-S-005 | 計算結算金額 - 部分退款 | P0 | GMV=10000, commission=1000, refunds=2000 | 1. 計算 netSettlement | net = 7000.00 |
| UT-M07-S-006 | 計算結算金額 - 全額退款 | P0 | GMV=10000, commission=1000, refunds=10000 | 1. 計算 netSettlement | net = 0.00（不為負） |
| UT-M07-S-007 | 過濾可結算訂單 | P1 | 混合 COMPLETED/REFUNDED/CANCELLED 訂單 | 1. filterSettleableOrders | 只回傳 COMPLETED + DELIVERED |
| UT-M07-S-008 | BigDecimal 精度 | P2 | 0.1 + 0.2 場景 | 1. 多筆小數運算 | 結果保留 2 位小數（HALF_UP） |
| UT-M07-S-009 | null 安全 | P2 | 訂單 totalAmount 為 null | 1. 計算 GMV | NPE 不發生，已過濾 null |

### 1.2 SettlementGenerator 冪等性

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M07-S-010 | 重複週期不重複建立 | P0 | 已存在 PENDING 結算單 | 1. 觸發 generateStatementForTenant | 回傳既有結算單，不建立新單 |
| UT-M07-S-011 | 多租戶批次生成 | P0 | 3 個 ACTIVE tenants | 1. 觸發 generateWeeklyStatements | 建立 3 個結算單 |
| UT-M07-S-012 | 結算單號格式 | P1 | tenantId = "12345678-aaaa-..." | 1. 生成結算單 | statementNumber = "STL-12345678-yyyyMMdd" |

### 1.3 SettlementReviewer 狀態機

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| UT-M07-S-013 | PENDING → PENDING_REVIEW | P0 | PENDING 狀態 | 1. submitForReview | 狀態變更成功 |
| UT-M07-S-014 | PENDING_REVIEW → APPROVED | P0 | PENDING_REVIEW 狀態 | 1. approveStatement | 狀態變更 + reviewedAt 設定 |
| UT-M07-S-015 | PENDING_REVIEW → REJECTED (with reason) | P0 | PENDING_REVIEW 狀態 | 1. rejectStatement with reason | 狀態變更 + rejectionReason 設定 |
| UT-M07-S-016 | 無效狀態轉換 - PENDING → APPROVED | P0 | PENDING 狀態 | 1. 直接 approveStatement | 拋出 BusinessException |

---

## 2. 整合測試 (Integration Tests)

### 2.1 商家結算單查詢

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M07-S-001 | StoreOwner 查詢自己租戶的結算單列表 | P0 | 3 筆結算單資料 | 1. GET /v2/settlements | 200, data.statements 有 3 筆 |
| IT-M07-S-002 | 跨租戶禁止查詢 | P0 | 屬於 Tenant B 的結算單 | 1. Tenant A 用戶 GET /v2/settlements/{tenantB-statementId} | 404, 訊息: "Settlement statement not found" |
| IT-M07-S-003 | 商家提交審核 | P0 | PENDING 結算單 | 1. PUT /v2/settlements/{id}/submit | 200, status = PENDING_REVIEW |
| IT-M07-S-004 | 分頁測試 | P1 | 25 筆結算單 | 1. GET /v2/settlements?page=0&size=10 | 200, totalPages = 3, size = 10 |

### 2.2 Admin 審核流程

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| IT-M07-S-005 | Admin 取得待審核列表 | P0 | 5 筆 PENDING_REVIEW 結算單 | 1. GET /v2/admin/settlements/pending | 200, 5 筆 PENDING_REVIEW |
| IT-M07-S-006 | Admin 批准結算單 | P0 | 1 筆 PENDING_REVIEW | 1. PUT /v2/admin/settlements/{id}/approve | 200, status = APPROVED |
| IT-M07-S-007 | Admin 駁回結算單（帶原因） | P0 | 1 筆 PENDING_REVIEW | 1. PUT /v2/admin/settlements/{id}/reject?reason=... | 200, status = REJECTED, rejectionReason 設定 |
| IT-M07-S-008 | 非 Admin 角色呼叫 Admin API | P0 | StoreOwner 用戶 | 1. PUT /v2/admin/settlements/{id}/approve | 403 Forbidden |

---

## 3. API E2E 測試 (API E2E Tests)

### 3.1 商家端 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M07-S-001 | GET /v2/settlements - 商家查詢 | P0 | StoreOwner 登入 | 1. GET /v2/settlements?page=0&size=20 | 200, ApiResponse.data.statements |
| API-M07-S-002 | GET /v2/settlements/{id} - 商家詳情 | P0 | 有結算單 | 1. GET /v2/settlements/{id} | 200, 完整結算單資料 |
| API-M07-S-003 | PUT /v2/settlements/{id}/submit | P0 | PENDING 結算單 | 1. PUT /v2/settlements/{id}/submit | 200, status = PENDING_REVIEW |

### 3.2 Admin 端 API

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| API-M07-S-004 | GET /v2/admin/settlements/pending | P0 | Admin 登入 | 1. GET /v2/admin/settlements/pending | 200, 待審核列表 |
| API-M07-S-005 | PUT /v2/admin/settlements/{id}/approve | P0 | Admin + PENDING_REVIEW | 1. PUT .../approve | 200, status = APPROVED |
| API-M07-S-006 | PUT /v2/admin/settlements/{id}/reject | P0 | Admin + PENDING_REVIEW | 1. PUT .../reject?reason=xxx | 200, status = REJECTED |

---

## 4. 結算單狀態流轉圖

```
       PENDING ──────► PENDING_REVIEW
                              │
                  ┌───────────┴───────────┐
                  ▼                       ▼
              APPROVED                REJECTED
                  │                       │
                  ▼                       ▼
                PAID                   (END)
                  │
                  ▼
                FAILED
                  │
                  └─► (END)
```

**合法狀態轉換**:
- `PENDING` → `PENDING_REVIEW`（商家提交）
- `PENDING_REVIEW` → `APPROVED`（Admin 批准）
- `PENDING_REVIEW` → `REJECTED`（Admin 駁回）
- `APPROVED` → `PAID`（撥款成功）
- `APPROVED` → `FAILED`（撥款失敗）

**非法轉換**:
- `PENDING` → `APPROVED`（跳過審核）→ 拋出 E_5006
- `REJECTED` → 任何狀態（終態）→ 拋出 E_5006
- `PENDING` → `PAID`（跳過審核）→ 拋出 E_5006

---

## 5. 多租戶隔離測試

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| SEC-M07-S-001 | Tenant A 看不到 Tenant B 的結算單 | P0 | 2 個 Tenants 各有結算單 | 1. Tenant A 用戶查詢所有結算單 | 只看到 Tenant A 的 |
| SEC-M07-S-002 | Tenant A 嘗試存取 Tenant B 的結算單詳情 | P0 | Tenant B 的結算單 | 1. GET /v2/settlements/{tenantB-id} | 404 Not Found |
| SEC-M07-S-003 | Tenant A 嘗試提交 Tenant B 的結算單 | P0 | Tenant B 的 PENDING 結算單 | 1. PUT .../submit (Tenant A 用戶) | 404 Not Found |

---

## 6. 排程任務測試（🆕 Sprint 16 US-003）

| TC ID | 測試案例名稱 | 優先級 | 前置條件 | 測試步驟 | 預期結果 |
|-------|-------------|--------|----------|----------|----------|
| SCH-M07-S-001 | 週一自動觸發生成結算單 | P0 | 多個 ACTIVE tenants 有訂單 | 1. 呼叫 generateWeeklyStatements() | 為每個 tenant 各建立 1 筆 |
| SCH-M07-S-002 | 冪等性：重複觸發不重複建立 | P0 | 已存在 PENDING 結算單 | 1. 重複呼叫 generateWeeklyStatements() | 不重複建立 |
| SCH-M07-S-003 | 異常 Tenant 不影響其他 Tenant | P0 | Tenant A 處理失敗 | 1. 呼叫 generateWeeklyStatements() | Tenant B 仍正常處理 |
| SCH-M07-S-004 | 結算單號格式正確 | P1 | tenantId UUID | 1. 生成結算單 | 格式: STL-{前8碼}-{yyyyMMdd} |

---

## 📝 關鍵驗證點總結

| 驗證項目 | 測試案例 | 優先級 |
|---------|---------|--------|
| 金額計算邊界（0、負、超大） | UT-M07-S-001 ~ 006 | P0 |
| 結算單冪等性 | UT-M07-S-010 | P0 |
| 狀態機合法性 | UT-M07-S-013 ~ 016 | P0 |
| 多租戶隔離 | SEC-M07-S-001 ~ 003 | P0 |
| 權限控管 | IT-M07-S-008 | P0 |
| Scheduled Job 觸發 | SCH-M07-S-001 ~ 004 | P0 |

---

**文件版本**: AISDLC v0.09
**測試框架**: JUnit 5 + Mockito, Spring Boot Test, REST Assured
**最後更新**: 2026-06-05
**作者**: Claude Code (AI Assistant)
