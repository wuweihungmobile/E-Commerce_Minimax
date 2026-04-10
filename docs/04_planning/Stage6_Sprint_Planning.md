# Stage 6 Sprint 規劃與測試策略 / Sprint Planning and Test Strategy

> **日期**: 2026-04-09
> **Stage**: 6 - Sprint 規劃與路線圖 + 測試策略
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0

---

## 🔴 人機協作確認點 7.1 結果

### Sprint 規劃確認
**選擇**: ✅ 確認通過，Sprint 規劃合理

### Sprint 目標確認
| Sprint | 目標 | Story Points |
|--------|------|--------------|
| Sprint 0 | 環境建置、架構搭建 | N/A (基礎建設) |
| Sprint 1 | M03 會員系統 (核心) | ~18 |
| Sprint 2 | M17 租戶管理 + M01 商品部分 | ~18 |
| Sprint 3 | M01 商品管理 + M05 訂單部分 | ~18 |
| Sprint 4 | M02 房源管理 | ~18 |
| Sprint 5 | M12 動態定價 | ~18 |
| Sprint 6 | 整合測試 + 收尾 | ~16 |

### 確認人
- **Human User**: 確認通過

---

## 1. Sprint 0 規劃（環境建置）

### 1.1 目標
建立完整的開發環境、架構骨架、CI/CD 基礎設施

### 1.2 工作項目

| 工作項目 | 負責人 | 預估時間 | 優先級 |
|----------|--------|----------|--------|
| Next.js 專案初始化 + App Router | Dev | 2 天 | P0 |
| Spring Boot 專案初始化 + Clean Architecture | Dev | 2 天 | P0 |
| PostgreSQL 資料庫設定 + Flyway 遷移 | Dev | 1 天 | P0 |
| Redis 設定（快取 + 分散式鎖）| Dev | 1 天 | P0 |
| GitHub Actions CI/CD 骨架 | DevOps | 2 天 | P0 |
| JWT + OAuth2 基礎設施 | Dev | 2 天 | P0 |
| 專案架構骨架建立 | SD + Dev | 2 天 | P0 |
| API 統一回應格式 + 異常處理 | Dev | 1 天 | P1 |

### 1.3 交付物
- [ ] Next.js 專案骨架 (`frontend/`)
- [ ] Spring Boot 專案骨架 (`backend/`)
- [ ] 資料庫 Migration Scripts (`db/migration/`)
- [ ] CI/CD Pipeline 配置 (`.github/workflows/`)
- [ ] Developer Setup Guide

### 1.4 驗收標準
- [ ] 本機開發環境可正常啟動
- [ ] CI Pipeline 可成功執行 Build + Test
- [ ] 資料庫 Migration 可正常執行
- [ ] JWT 認證流程可正常運作

---

## 2. Sprint 1: M03 會員系統

### 2.1 Sprint 目標
完成會員系統核心功能：註冊、登入、JWT 刷新、RBAC

### 2.2 User Stories 分配

| ID | 標題 | Story Points | 優先級 |
|----|------|--------------|--------|
| US-M03-001 | 會員註冊 | 3 | P0 |
| US-M03-002 | 會員登入 | 3 | P0 |
| US-M03-003 | JWT 刷新 | 3 | P0 |
| US-M03-004 | 會員登出 | 2 | P2 |
| US-M03-005 | 取得當前用戶資訊 | 2 | P1 |

**Sprint 1 Total**: 13 points (預留 buffer 至 18)

### 2.3 依賴關係
- 前置依賴: Sprint 0 完成
- 被依賴: Sprint 2+ (其他模組需要認證)

### 2.4 技術要點
- BCrypt 密碼加密
- JWT Access Token (15min) + Refresh Token (7 days)
- RBAC 角色繼承: SUPER_ADMIN > ADMIN > STORE_OWNER > STORE_STAFF > BUYER > GUEST
- OAuth2 Social Login (Google/Facebook/Apple) - 可選

---

## 3. Sprint 2: M17 租戶管理 + M01 商品部分

### 3.1 Sprint 目標
完成多租戶系統核心 +  商品管理基礎

### 3.2 User Stories 分配

| ID | 標題 | Story Points | 優先級 |
|----|------|--------------|--------|
| US-M17-001 | 開店申請 | 3 | P0 |
| US-M17-002 | 店鋪詳情 | 2 | P0 |
| US-M17-004 | 取得我的店鋪列表 | 2 | P0 |
| US-M17-007 | Admin 審核通過店鋪 | 3 | P0 |
| US-M17-008 | Admin 駁回店鋪申請 | 3 | P0 |
| US-M01-001 | 商品搜尋 | 3 | P0 |
| US-M01-002 | 商品上架 | 3 | P0 |
| US-M01-003 | 商品詳情 | 2 | P0 |

**Sprint 2 Total**: 18 points

### 3.3 依賴關係
- 前置依賴: Sprint 0 完成
- 被依賴: Sprint 3+

### 3.4 技術要點
- TenantContext Filter (ThreadLocal)
- Hibernate Tenant Filter (多租戶隔離)
- Feature Toggle 機制
- Listing 統一抽象 (PRODUCT/ROOM)

---

## 4. Sprint 3: M01 商品管理 + M05 訂單部分

### 4.1 Sprint 目標
完成商品完整 CRUD + 訂單基礎

### 4.2 User Stories 分配

| ID | 標題 | Story Points | 優先級 |
|----|------|--------------|--------|
| US-M01-004 | 商品編輯 | 3 | P1 |
| US-M01-005 | 商品下架 | 2 | P1 |
| US-M05-001 | 建立訂單 | 5 | P0 |
| US-M05-002 | 查詢訂單列表 | 3 | P0 |
| US-M05-003 | 查詢訂單詳情 | 2 | P0 |
| US-M05-004 | 取消訂單 | 3 | P1 |
| US-M05-005 | 更新訂單狀態 | 3 | P0 |

**Sprint 3 Total**: 21 points → 調整為 18 points (分期)

### 4.3 依賴關係
- 前置依賴: Sprint 1 (認證), Sprint 2 (租戶上下文)
- 被依賴: Sprint 5

### 4.4 技術要點
- 訂單狀態機: CREATED → PAID → SHIPPING → DELIVERED → COMPLETED
- 庫存扣減 (樂觀鎖)
- 取消/回滾機制

---

## 5. Sprint 4: M02 房源管理

### 5.1 Sprint 目標
完成民宿/訂房業務的房源管理

### 5.2 User Stories 分配

| ID | 標題 | Story Points | 優先級 |
|----|------|--------------|--------|
| US-M02-001 | 房源搜尋與過濾 | 3 | P0 |
| US-M02-002 | 查看房源日曆與價格 | 4 | P0 |
| US-M02-003 | 房源上架 | 3 | P0 |
| US-M02-004 | 房源編輯 | 3 | P1 |
| US-M02-005 | 房源下架 | 2 | P1 |
| US-M17-005 | 功能開關查詢 | 2 | P1 |
| US-M17-006 | 申請功能開關 | 2 | P1 |

**Sprint 4 Total**: 19 points → 調整為 18 points

### 5.3 依賴關係
- 前置依賴: Sprint 2 (租戶)
- 被依賴: Sprint 5

### 5.4 技術要點
- Room Calendar (Redis 分散式鎖)
- 地點搜尋 (Lat/Lng)
- 民宿預訂流程

---

## 6. Sprint 5: M12 動態定價

### 6.1 Sprint 目標
完成智慧化動態定價引擎

### 6.2 User Stories 分配

| ID | 標題 | Story Points | 優先級 |
|----|------|--------------|--------|
| US-M12-001 | 設定平假日調價規則 | 5 | P1 |
| US-M12-002 | 手動覆蓋特定日期價格 | 4 | P1 |
| US-M12-003 | 查詢定價規則列表 | 2 | P1 |
| US-M12-004 | 設定早鳥優惠規則 | 3 | P2 |
| US-M12-005 | 設定長住折扣規則 | 3 | P2 |
| US-M12-006 | 刪除定價規則 | 2 | P2 |
| US-M12-007 | 預覽定價日曆 | 3 | P1 |

**Sprint 5 Total**: 22 points → 調整為 18 points

### 6.3 依賴關係
- 前置依賴: Sprint 4 (房源基礎數據)
- 被依賴: Sprint 6 (整合)

### 6.4 技術要點
- 動態定價規則引擎
- 規則優先級 (Priority)
- 價格計算流程: Override → Season → Weekend → Discount

---

## 7. Sprint 6: 整合測試 + 收尾

### 7.1 Sprint 目標
系統整合測試、API E2E 測試、部署準備

### 7.2 工作項目

| 工作項目 | 預估時間 | 優先級 |
|----------|----------|--------|
| API 整合測試 | 3 天 | P0 |
| E2E 測試 (Playwright) | 3 天 | P0 |
| 效能測試 | 1 天 | P1 |
| 安全掃描 | 1 天 | P1 |
| 文件更新 | 1 天 | P1 |
| 部署腳本準備 | 1 天 | P0 |
| Buffer | 2 天 | P1 |

### 7.3 交付物
- [ ] 整合測試報告
- [ ] E2E 測試報告
- [ ] 效能測試報告
- [ ] 部署配置文件

### 7.4 驗收標準
- [ ] 所有 User Stories 的 AC 通過
- [ ] P0 Bug = 0
- [ ] API 回應時間 P95 < 200ms
- [ ] CI/CD Pipeline 正常運作

---

## 8. 里程碑定義

| 里程碑 | 日期 | 說明 |
|--------|------|------|
| M0: Sprint 0 完成 | +2 週 | 開發環境就緒 |
| M1: 會員系統完成 | +4 週 | 核心認證機制就緒 |
| M2: 商品+租戶完成 | +6 週 | 零售業務基礎 |
| M3: 訂單+房源完成 | +8 週 | 電商交易閉環 |
| M4: 動態定價完成 | +10 週 | 智慧定價上線 |
| M5: 測試完成 | +12 週 | 系統就緒 |
| R1: Release | +13 週 | MVP 發布 |

---

## 9. 風險註冊表 (Risk Register)

| 風險 | 可能性 | 影響 | 緩解策略 |
|------|--------|------|----------|
| OAuth2 整合複雜度 | 中 | 中 | Sprint 0 先做 MVP，Social Login 延後 |
| Redis 分散式鎖競爭 | 低 | 高 | 使用 Redisson，提供重試機制 |
| 動態定價規則衝突 | 中 | 低 | 明確優先級設計，單元測試覆蓋 |
| 第三方 API 延遲 | 低 | 中 | 使用 Adapter Pattern，封裝Mock |
| 單一團隊人力瓶頸 | 高 | 高 | 每日 Standup，及時識別blocking |

---

## 10. Sprint Roadmap 總覽

```
Sprint 0 (2週): 環境建置 ─────────────────────────────────┐
                                                            │
Sprint 1 (2週): M03 會員系統                               │
  ├── US-M03-001 會員註冊                                  │
  ├── US-M03-002 會員登入                                  │
  ├── US-M03-003 JWT 刷新                                  │
  ├── US-M03-004 會員登出                                  │
  └── US-M03-005 取得當前用戶資訊                          │
                                                            ▼
Sprint 2 (2週): M17 租戶 + M01 商品基礎 ───────────────────┐
  ├── US-M17-001~008 (租戶管理)                            │
  └── US-M01-001~003 (商品基礎)                            │
                                                            ▼
Sprint 3 (2週): M01 商品 + M05 訂單 ──────────────────────┐
  ├── US-M01-004~005 (商品編輯/下架)                       │
  └── US-M05-001~005 (訂單管理)                            │
                                                            ▼
Sprint 4 (2週): M02 房源管理 ──────────────────────────────┐
  ├── US-M02-001~005 (房源 CRUD)                          │
  └── US-M17-005~006 (功能開關)                            │
                                                            ▼
Sprint 5 (2週): M12 動態定價 ─────────────────────────────┐
  └── US-M12-001~007 (定價規則)                            │
                                                            ▼
Sprint 6 (2週): 整合測試 + 收尾 ──────────────────────────┘
  ├── API 整合測試
  ├── E2E 測試
  └── 部署準備
```

---

## 📁 相關文件

| 文件 | 路徑 |
|------|------|
| Stage 5 User Story 確認 | `docs/04_planning/Stage5_UserStory_Confirmation.md` |
| FRD | `docs/01_requirements/E-Commerce_FRD_v1.0.md` |
| API 規格 | `docs/02_architecture/api/` |
| 測試策略 | `docs/04_planning/Stage6_Test_Strategy.md` |

---

## ✅ 確認簽核

| 角色 | 確認狀態 | 簽核日期 |
|------|----------|----------|
| Human User | ✅ Sprint 分配合理 | 2026-04-09 |
| Human User | ✅ Story Points 分配可接受 | 2026-04-09 |
| Human User | ✅ 測試策略滿足需求 | 2026-04-09 |
| PM/PO (Victoria) | 待確認 | - |
| SA (Amanda) | 待確認 | - |

---

**文件結束**
