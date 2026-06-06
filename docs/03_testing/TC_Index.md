# 測試案例索引 / Test Case Index

> **文件類型**: 測試案例索引文件
> **版本**: v1.0
> **建立日期**: 2026-04-10
> **專案**: E-Commerce B2B2C 多租戶電子商務平台
> **依據**: Stage6_Test_Strategy.md, API 規格文件, SRD_Database_Schema.md

---

## 📋 文件元數據

| 項目 | 內容 |
|-----|------|
| **測試框架** | JUnit 5 + Mockito (UT), Spring Boot Test (IT), REST Assured (API), Playwright (E2E) |
| **測試覆蓋率目標** | Unit ≥ 80%, Integration ≥ 70%, API E2E 100% P0 |
| **測試環境** | Local (H2), Dev, Staging, Production |

---

## 📁 測試案例文件清單

| 檔案名 | 模組 | 測試案例數量 | 主要內容 |
|--------|------|-------------|---------|
| [TC_M03_Auth.md](./TC_M03_Auth.md) | M03 會員系統 | 25 | 會員註冊、登入、登出、JWT Token、RBAC 權限 |
| [TC_M17_Tenant.md](./TC_M17_Tenant.md) | M17 租戶管理 | 22 | 租戶申請、Admin 審核、Feature Toggle |
| [TC_M01_Product.md](./TC_M01_Product.md) | M01 商品管理 | 20 | 商品上架、編輯、下架、搜尋 |
| [TC_M05_Order.md](./TC_M05_Order.md) | M05 訂單管理 | 24 | 訂單建立、狀態機、庫存扣減、取消 |
| [TC_M02_Room.md](./TC_M02_Room.md) | M02 房源管理 | 18 | 房源上架、日曆查詢、預訂衝突 |
| [TC_M12_Pricing.md](./TC_M12_Pricing.md) | M12 動態定價 | 20 | 價格計算、規則優先級、手動覆蓋 |
| **🆕 [TC_M07_Settlement.md](./TC_M07_Settlement.md)** | M07 金流 - 結算 | 21 | 結算單生成、查詢、Admin 審核、排程 |
| **🆕 [TC_M08_Review.md](./TC_M08_Review.md)** | M08 評價系統 | 28 | 評價 CRUD、回覆、多圖、圖片管理 |
| [TC_E2E.md](./TC_E2E.md) | E2E 測試 | 12 | 核心業務流程端到端測試 |

**測試案例總數**: 190 (+49 🆕 Sprint 16 新增)

---

## 🎯 測試金字塔摘要

```
                    ┌─────────────┐
                    │    E2E      │  ← 12 案例 (Playwright)
                    │   Tests     │
                    └──────┬──────┘
                           │
              ┌────────────┴────────────┐
              │    Integration Tests     │  ← 50 案例 (Spring Boot Test)
              │     (API Layer)          │
              └────────────┬────────────┘
                           │
        ┌──────────────────┴──────────────────┐
        │         Unit Tests                  │  ← 79 案例 (JUnit 5 + Mockito)
        │    (Business Logic Layer)           │
        └─────────────────────────────────────┘
```

---

## 📊 優先級分布

| 優先級 | 數量 | 說明 |
|--------|------|------|
| **P0** | 45 | 核心功能，必需通過 |
| **P1** | 58 | 重要功能，應通過 |
| **P2** | 38 | 一般功能，儘量通過 |

---

## 🔑 測試類型說明

| 類型 | 縮寫 | 說明 | 工具 |
|------|------|------|------|
| 單元測試 | UT | 測試單一業務邏輯 | JUnit 5 + Mockito |
| 整合測試 | IT | 測試模組間互動 | Spring Boot Test + Testcontainers |
| API E2E | API | 測試 API 端點 | REST Assured / MockMvc |
| 端到端測試 | E2E | 測試完整使用者流程 | Playwright |

---

## 📌 測試案例 ID 命名規範

```
{類型}-{模組}-{編號}
  │      │       │
  │      │       └── 三位數編號 (001-999)
  │      │
  │      └── M03: 會員系統
  │          M17: 租戶管理
  │          M01: 商品管理
  │          M05: 訂單管理
  │          M02: 房源管理
  │          M12: 動態定價
  │          E2E: 端到端測試
  │
  └── UT: 單元測試
      IT: 整合測試
      API: API E2E 測試
      E2E: 端到端測試
```

**範例**:
- `UT-M03-001`: M03 會員系統，單元測試，第 1 案
- `IT-M05-010`: M05 訂單管理，整合測試，第 10 案
- `API-M01-005`: M01 商品管理，API E2E 測試，第 5 案
- `E2E-001`: 端到端測試，第 1 案

---

## 📝 各模組測試案例摘要

### M03 會員系統 (25 案例)

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 5 | 4 | 3 | 12 |
| IT | 2 | 2 | 1 | 5 |
| API | 3 | 3 | 2 | 8 |
| **合計** | 10 | 9 | 6 | **25** |

**關鍵驗證點**:
- 密碼 BCrypt 加密不可逆
- JWT Access Token 30 分鐘過期
- Refresh Token 30 天過期，可續命
- RBAC 角色繼承正確 (OWNER > STAFF > BUYER)

### M17 租戶管理 (22 案例)

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 4 | 3 | 2 | 9 |
| IT | 2 | 2 | 1 | 5 |
| API | 3 | 3 | 2 | 8 |
| **合計** | 9 | 8 | 5 | **22** |

**關鍵驗證點**:
- 多租戶資料隔離 (Tenant A 看不到 Tenant B)
- Feature Toggle 狀態正確反映
- 租戶狀態機: PENDING → APPROVED → ACTIVE

### M01 商品管理 (20 案例)

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 3 | 3 | 2 | 8 |
| IT | 2 | 2 | 1 | 5 |
| API | 3 | 3 | 1 | 7 |
| **合計** | 8 | 8 | 4 | **20** |

**關鍵驗證點**:
- Listing Type (PRODUCT/ROOM) 正確區分
- 商品狀態機: DRAFT → ACTIVE → INACTIVE → DELETED
- 商品搜尋與分類過濾

### M05 訂單管理 (24 案例)

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 4 | 3 | 2 | 9 |
| IT | 3 | 3 | 1 | 7 |
| API | 3 | 3 | 2 | 8 |
| **合計** | 10 | 9 | 5 | **24** |

**關鍵驗證點**:
- 訂單狀態機轉換正確
- 並發庫存扣減不超賣 (樂觀鎖)
- 取消訂單後庫存回滾

### M02 房源管理 (18 案例)

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 3 | 2 | 2 | 7 |
| IT | 2 | 2 | 1 | 5 |
| API | 2 | 3 | 1 | 6 |
| **合計** | 7 | 7 | 4 | **18** |

**關鍵驗證點**:
- Redis 分散式鎖防止雙重預訂
- 日曆可用日期計算正確
- 房源日曆狀態: AVAILABLE, BOOKED, BLOCKED, MAINTENANCE

### M12 動態定價 (20 案例)

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| UT | 4 | 3 | 2 | 9 |
| IT | 2 | 2 | 1 | 5 |
| API | 2 | 3 | 1 | 6 |
| **合計** | 8 | 8 | 4 | **20** |

**關鍵驗證點**:
- 價格計算流程: Override → Season → Weekend → Discount
- 早鳥/長住折扣正確計算
- 優先級高規則覆蓋低優先級規則

### E2E 測試 (12 案例)

| 測試類型 | P0 | P1 | P2 | 小計 |
|----------|----|----|----|------|
| E2E | 4 | 5 | 3 | 12 |

**關鍵驗證點**:
- 會員註冊 → 登入 → 登出
- 店鋪申請 → Admin 審核 → 開店
- 商品上架 → 搜尋 → 加入購物車 → 建立訂單
- 房源上架 → 日曆查詢 → 預訂

---

## 📁 相關文件

| 文件 | 路徑 |
|------|------|
| 測試策略 | `docs/04_planning/Stage6_Test_Strategy.md` |
| 系統架構 | `docs/02_architecture/SRD_System_Architecture.md` |
| 資料庫 Schema | `docs/02_architecture/SRD_Database_Schema.md` |
| API 規格 | `docs/02_architecture/api/` |
| M03 Auth API | `docs/02_architecture/api/API_M03_Auth.md` |
| M17 Tenant API | `docs/02_architecture/api/API_M17_Tenant.md` |
| M01 Product API | `docs/02_architecture/api/API_M01_Product_Center.md` |
| M05 Order API | `docs/02_architecture/api/API_M05_Order.md` |
| M02 Room API | `docs/02_architecture/api/API_M02_Listing_Center.md` |
| M12 Pricing API | `docs/02_architecture/api/API_M12_Dynamic_Pricing.md` |

---

**文件版本**: AISDLC v0.09
**模板維護**: QA Team
**最後更新**: 2026-04-10
