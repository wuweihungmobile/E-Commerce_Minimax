# Stage 5 User Story 確認記錄
# User Story Confirmation Record

> **日期**: 2026-04-09
> **Stage**: 5 - User Story Splitting
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0

---

## 🔴 人機協作確認點 6.1 結果

### 確認選擇
**選擇 1**: ✅ Epic 分解合理，故事定義準確，粒度適當

### 工作量估算確認
| 項目 | 確認結果 |
|------|----------|
| 總 Story Points (106) | [1] 合理 |
| 預估 Sprint 數 (6 sprints) | [1] 可接受 |

### 確認人
- **Human User**: 確認通過

---

## 📋 User Story 總覽

### Epic 分解結構

| Epic ID | 模組名稱 | 業務價值 | 故事數量 | Story Points |
|---------|----------|----------|----------|--------------|
| EP-M01 | 商品管理模組 | 提供完整的商品管理功能，支援零售業務 | 5 | 待估算 |
| EP-M02 | 房源管理模組 | 支援民宿/訂房業務的房源管理 | 5 | 待估算 |
| EP-M03 | 會員管理模組 | 安全的會員認證與授權機制 | 5 | 待估算 |
| EP-M05 | 訂單管理模組 | 完整的訂單生命週期管理 | 5 | 待估算 |
| EP-M12 | 動態定價模組 | 智慧化動態定價，提升營收 | 7 | 待估算 |
| EP-M17 | 租戶管理模組 | 多租戶店鋪管理與功能開關控制 | 8 | 待估算 |

---

## 📊 User Story 清單

### M01 商品管理模組

| ID | 標題 | 角色 | 優先級 |
|----|------|------|--------|
| US-M01-001 | 商品搜尋 | Guest+ | P0 |
| US-M01-002 | 商品上架 | StoreOwner | P0 |
| US-M01-003 | 商品詳情 | Guest+ | P0 |
| US-M01-004 | 商品編輯 | StoreOwner | P1 |
| US-M01-005 | 商品下架 | StoreOwner | P1 |

### M02 房源管理模組

| ID | 標題 | 角色 | 優先級 |
|----|------|------|--------|
| US-M02-001 | 房源搜尋與過濾 | Guest+ | P0 |
| US-M02-002 | 查看房源日曆與價格 | Guest+ | P0 |
| US-M02-003 | 房源上架 | StoreOwner | P0 |
| US-M02-004 | 房源編輯 | StoreOwner | P1 |
| US-M02-005 | 房源下架 | StoreOwner | P1 |

### M03 會員管理模組

| ID | 標題 | 角色 | 優先級 |
|----|------|------|--------|
| US-M03-001 | 會員註冊 | Guest | P0 |
| US-M03-002 | 會員登入 | Guest | P0 |
| US-M03-003 | JWT 刷新 | Member | P0 |
| US-M03-004 | 會員登出 | Member | P2 |
| US-M03-005 | 取得當前用戶資訊 | Member | P1 |

### M05 訂單管理模組

| ID | 標題 | 角色 | 優先級 |
|----|------|------|--------|
| US-M05-001 | 建立訂單 | Buyer | P0 |
| US-M05-002 | 查詢訂單列表 | Buyer, StoreOwner | P0 |
| US-M05-003 | 查詢訂單詳情 | Buyer, StoreOwner | P0 |
| US-M05-004 | 取消訂單 | Buyer | P1 |
| US-M05-005 | 更新訂單狀態 | StoreOwner | P0 |

### M12 動態定價模組

| ID | 標題 | 角色 | 優先級 |
|----|------|------|--------|
| US-M12-001 | 設定平假日調價規則 | StoreOwner+ | P1 |
| US-M12-002 | 手動覆蓋特定日期價格 | StoreOwner+ | P1 |
| US-M12-003 | 查詢定價規則列表 | StoreOwner+ | P1 |
| US-M12-004 | 設定早鳥優惠規則 | StoreOwner+ | P2 |
| US-M12-005 | 設定長住折扣規則 | StoreOwner+ | P2 |
| US-M12-006 | 刪除定價規則 | StoreOwner+ | P2 |
| US-M12-007 | 預覽定價日曆 | StoreOwner+ | P1 |

### M17 租戶管理模組

| ID | 標題 | 角色 | 優先級 |
|----|------|------|--------|
| US-M17-001 | 開店申請 | Guest | P0 |
| US-M17-002 | 店鋪詳情 | Guest+ | P0 |
| US-M17-003 | 更新店鋪資訊 | StoreOwner | P1 |
| US-M17-004 | 取得我的店鋪列表 | StoreOwner | P0 |
| US-M17-005 | 功能開關查詢 | StoreOwner+ | P1 |
| US-M17-006 | 申請功能開關 | StoreOwner | P1 |
| US-M17-007 | Admin 審核通過店鋪 | Admin | P0 |
| US-M17-008 | Admin 駁回店鋪申請 | Admin | P0 |

---

## 📈 工作量估算摘要

| 項目 | 數值 |
|------|------|
| **總 User Stories** | 35 個 |
| **總 Story Points** | 106 points |
| **預估 Sprint 數** | 6 sprints (假設 Sprint velocity = 18 points) |
| **P0 (Must Have)** | 71 points |
| **P1 (Should Have)** | 28 points |
| **P2 (Could Have)** | 7 points |

### Sprint 分配建議

| Sprint | 內容 | 預估 Points |
|--------|------|-------------|
| Sprint 1 | M03 會員系統 (核心) | ~18 |
| Sprint 2 | M17 租戶管理 (核心) + M01 商品部分 | ~18 |
| Sprint 3 | M01 商品管理 + M05 訂單部分 | ~18 |
| Sprint 4 | M02 房源管理 | ~18 |
| Sprint 5 | M12 動態定價 | ~18 |
| Sprint 6 | 整合測試 + 收尾 | ~16 |

---

## 🔗 與 API 規格的對應關係

| 模組 | API 端點數 | 對應 User Stories |
|------|-----------|-------------------|
| M01 | 7 APIs | US-M01-001 ~ US-M01-005 |
| M02 | 6 APIs | US-M02-001 ~ US-M02-005 |
| M03 | 5 APIs | US-M03-001 ~ US-M03-005 |
| M05 | 9 APIs | US-M05-001 ~ US-M05-005 |
| M12 | 6 APIs | US-M12-001 ~ US-M12-007 |
| M17 | 9 APIs | US-M17-001 ~ US-M17-008 |

---

## 📁 相關文件

| 文件 | 路徑 | 說明 |
|------|------|------|
| FRD | `docs/01_requirements/E-Commerce_FRD_v1.0.md` | 完整功能需求文檔 |
| API 規格 | `docs/02_architecture/api/` | 各模組 API 規格 |
| SRD | `docs/02_architecture/SRD/` | 系統架構設計 |

---

## ✅ 確認簽核

| 角色 | 確認狀態 | 簽核日期 |
|------|----------|----------|
| Human User | ✅ 確認通過 | 2026-04-09 |
| SA (Amanda) | 待確認 | - |
| SD (Marcus) | 待確認 | - |

---

**文件結束**
