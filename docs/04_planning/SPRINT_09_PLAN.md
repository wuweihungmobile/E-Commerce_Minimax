# Sprint 9 計劃 / Sprint 9 Plan

> **Sprint 編號**: Sprint 9
> **期間**: 2026-05-06 ~ 2026-05-17 (2 週)
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **建立日期**: 2026-05-05
> **更新日期**: 2026-05-05
> **基於**: Sprint 8 M15 CMS 完成 + PM/PO 確認

---

## 🔴 PM/PO 確認摘要

**Sprint 8 發布**: ✅ **APPROVED** - 2026-05-05

| 項目 | 確認結果 |
|------|----------|
| 發布決策 | ✅ APPROVED - 同意發布 Sprint 8 M15 CMS 功能 |
| Sprint 9 開始日期 | ✅ **2026-05-06** (明天) |
| FE 團隊 Briefing | ✅ 不需要 |

---

## 1. Sprint 資訊

| 欄位 | 內容 |
|------|------|
| **Sprint 編號** | Sprint 9 |
| **開始日期** | 2026-05-06 (週二) |
| **結束日期** | 2026-05-17 (週六) |
| **Sprint 容量** | 30 SP |
| **規劃 SP** | 待規劃 |
| **Buffer** | ~13% |
| **團隊** | 2 人 Dev Team |

---

## 2. Sprint 目標

> **目標**: 完成 M15 CMS 前端整合（FE-BE 整合測試），並為 M16 ERP 提供庫存與 CMS 內容整合基礎。

### 具體目標

#### M15 Frontend 實作（待 FE Dev）

| 功能 | 優先級 | 說明 |
|------|--------|------|
| 貼文列表頁 | P0 | `/cms` - Dashboard 顯示所有貼文 |
| 貼文編輯頁 | P0 | `/cms/posts/new`, `/cms/posts/:id/edit` |
| 嵌入卡片預覽 | P0 | 編輯器內即時預覽商品卡 |
| 媒體庫頁面 | P1 | `/cms/media` - 上傳/刪除 |
| 前台部落格 | P0 | `/blog`, `/blog/:slug` |
| 分類管理 | P2 | `/cms/categories` |

#### Dev Backend Tasks

| 功能 | 優先級 | 說明 |
|------|--------|------|
| MultipartFile Upload Endpoint | P1 | 新增真正的檔案上傳 API |
| MinIO 驗證 | P2 | 確認實際上傳到 MinIO |

---

## 3. M15 Frontend 元件清單

### 3.1 頁面路由

| 頁面 | Route | 優先級 |
|------|-------|--------|
| CMS 儀表板 | `/cms` | P0 |
| 新增貼文 | `/cms/posts/new` | P0 |
| 編輯貼文 | `/cms/posts/:id/edit` | P0 |
| 媒體庫 | `/cms/media` | P1 |
| 前台部落格首頁 | `/blog` | P0 |
| 貼文詳情 | `/blog/:slug` | P0 |
| 分類管理 | `/cms/categories` | P2 |

### 3.2 API 整合點

| 功能 | Method | Endpoint | 優先級 |
|------|--------|----------|--------|
| 建立貼文 | POST | `/v2/dashboard/posts` | P0 |
| 取得貼文列表 | GET | `/v2/dashboard/posts` | P0 |
| 更新貼文 | PUT | `/v2/dashboard/posts/{postId}` | P0 |
| 刪除貼文 | DELETE | `/v2/dashboard/posts/{postId}` | P0 |
| 發布/下架 | POST/DELETE | `/v2/dashboard/posts/{postId}/publish` | P0 |
| **上傳媒體 (Multipart)** | **POST** | **`/v2/dashboard/media/upload-multipart`** | **P1** |
| 取得媒體列表 | GET | `/v2/dashboard/media` | P1 |
| 刪除媒體 | DELETE | `/v2/dashboard/media/{mediaId}` | P1 |
| 前台貼文列表 | GET | `/v2/posts` | P0 |
| 前台貼文詳情 | GET | `/v2/posts/{slug}` | P0 |
| 嵌入商品卡 | GET | `/v2/listings/{listingId}/card` | P0 |

---

## 4. User Stories 摘要

| US ID | 標題 | SP | 優先級 |
|-------|------|-----|--------|
| US-M15-007 | FE: StoreOwner 檢視 CMS Dashboard | 2 | P0 |
| US-M15-008 | FE: StoreOwner 建立貼文 | 3 | P0 |
| US-M15-009 | FE: StoreOwner 上傳媒體（真正上傳到 MinIO） | 3 | P1 |
| US-M15-010 | FE: 前台用戶瀏覽 CMS 貼文 | 2 | P0 |
| US-M15-011 | FE: 前台用戶點擊嵌入商品卡 | 2 | P0 |
| **待補充** | 視 FE Dev 評估調整 | | |

---

## 5. 任務分解（初步）

| 任務 ID | 任務名稱 | SP | 負責人 | 優先級 |
|---------|----------|-----|--------|--------|
| Task-M15-101 | 新增 MultipartFile Upload Endpoint | 3 | Dev | P1 |
| Task-M15-102 | FE: 貼文列表頁 | 3 | FE Dev | P0 |
| Task-M15-103 | FE: 貼文編輯頁 | 5 | FE Dev | P0 |
| Task-M15-104 | FE: 嵌入卡片預覽 | 3 | FE Dev | P0 |
| Task-M15-105 | FE: 媒體庫頁面 | 3 | FE Dev | P1 |
| Task-M15-106 | FE: 前台部落格 | 3 | FE Dev | P0 |
| Task-M15-107 | MinIO 實際上傳驗證 | 2 | QA/Dev | P2 |
| Task-M15-108 | FE-BE 整合測試 | 3 | QA | P1 |
| **合計** | | **25 SP** | | |

---

## 6. Sprint 9 前置準備檢查清單

### 6.1 Backend ✅ 已完成

| 項目 | 狀態 | 備註 |
|------|------|------|
| M15 CMS Backend API | ✅ 完成 | 26/26 測試通過 |
| CORS 設定 | ✅ 完成 | 允許 localhost:3000 |
| JWT 認證 | ✅ 完成 | - |
| MinIO 服務 | ✅ 啟動中 | Port 9000/9001 |
| StorageService | ✅ 完成 | 待驗證 |

### 6.2 Frontend ⏳ 待開始

| 項目 | 狀態 | 備註 |
|------|------|------|
| React 專案 | ⏳ 待開始 | 使用 Next.js |
| API Client (Axios) | ⏳ 待開始 | 需處理 JWT |
| CMS 頁面路由 | ⏳ 待開始 | 見 3.1 |

### 6.3 測試環境 ✅ 已就緒

| 項目 | 狀態 | 備註 |
|------|------|------|
| Backend | ✅ 運行中 | http://localhost:8080 |
| Frontend | ✅ 運行中 | http://localhost:3000 |
| MinIO | ✅ 運行中 | http://localhost:9000 |
| PostgreSQL | ✅ 運行中 | Port 5432 |
| Redis | ✅ 運行中 | Port 6379 |

---

## 7. 測試規劃

### 7.1 測試類型

| 類型 | 數量 | 負責 | 執行時間 |
|------|------|------|----------|
| FE Integration Test | 17 | QA | Sprint 9 Day 3-5 |
| E2E Test | 5 | QA | Sprint 9 Day 4-5 |
| MinIO Upload Validation | 1 | QA/Dev | Sprint 9 Day 1 |

### 7.2 測試案例（詳見 TP_M15_Sprint9_FE_Detailed.md）

| 模組 | 案例數 | 執行順序 |
|------|--------|----------|
| FE-A: 登入與認證 | 2 | Day 3 |
| FE-B: 貼文管理 | 6 | Day 3-4 |
| FE-C: 媒體庫 | 2 | Day 4 |
| FE-D: 前台顯示 | 3 | Day 4 |
| FE-E: 錯誤處理 | 4 | Day 5 |

---

## 8. Phase 2-B 依賴關係

```
Sprint 8 M15 CMS Backend ✅ ─┐
                              ├─> M15 FE Integration (Sprint 9) ← 當前
                              │
                              └─> M16 ERP (Sprint 10)
```

---

## 9. 風險追蹤

| 風險 ID | 等級 | 說明 | 緩解措施 | 狀態 |
|---------|------|------|----------|------|
| R-001 | 中 | 測試隔離 | 使用乾淨的測試資料 | ⏳ Sprint 9 驗證 |
| R-002 | 中 | MinIO 實際上傳 | Sprint 9 Day 1 驗證 | ⏳ 待驗證 |
| R-004 | 中 | CORS 跨域 | 已設定 | ✅ 完成 |
| R-005 | 中 | JWT Token 過期 | FE 需實作 Refresh | ⏳ FE 實作 |
| R-006 | 低 | 嵌入商品卡 URL | 確認跳轉路徑 | ⏳ FE 實作 |

---

## 10. 成功標準

| 標準 | 目標 | 狀態 |
|------|------|------|
| FE-BE 整合測試通過 | 17/17 | ✅ 已達成 (21/21) |
| E2E 測試通過 | 5/5 | ✅ 已達成 (12/12) |
| MultipartFile 上傳工作 | 是 | ✅ 已驗證 |
| MinIO 實際寫入驗證 | 是 | ✅ 已驗證 |
| 無 High 缺陷 | High = 0 | ✅ 達成 |

---

## 11. 與前一 Sprint 的差異

| 項目 | Sprint 8 | Sprint 9 |
|------|---------|---------|
| **焦點** | Backend API 開發 | FE-BE 整合 |
| **測試** | Backend IT (26 通過) | FE Integration (17 待測) |
| **前端** | 僅Dashboard框架 | 完整CMS功能 |
| **媒體上傳** | 路徑版本 | MultipartFile 版本 |

---

**文件狀態**: ✅ Sprint 9 完成
**下一步**: Sprint 10 M16 ERP 規劃
**相關文件**:
- [SPRINT_08_RELEASE_CONFIRMATION.md](SPRINT_08_RELEASE_CONFIRMATION.md)
- [SPRINT_09_FE_INTEGRATION_API_CONTRACT.md](SPRINT_09_FE_INTEGRATION_API_CONTRACT.md)
- [TP_M15_Sprint9_FE_Detailed.md](../../03_testing/TP_M15_Sprint9_FE_Detailed.md)