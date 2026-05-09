# Sprint 9 前端整合測試計劃 / Sprint 9 Frontend Integration Test Plan

## 文件資訊
- **TP-ID**: TP-M15-Sprint9
- **版本**: 1.0
- **日期**: 2026-05-05
- **狀態**: ⏳ 待執行

---

## 1. 前端實作範圍（待定）

### 1.1 CMS 功能前端

| 功能 | 優先級 | 說明 |
|------|--------|------|
| 貼文列表頁 | P1 | Dashboard 顯示所有貼文 |
| 貼文編輯頁 | P1 | 建立/編輯貼文，含嵌入解析 |
| 媒體庫管理 | P2 | 上傳、刪除媒體 |
| 前台顯示 | P1 | 顯示已發布貼文 |

### 1.2 FE-BE API 整合點

| API | Method | Endpoint |
|-----|--------|----------|
| 建立貼文 | POST | /v2/tenants/{tenantId}/posts |
| 取得貼文列表 | GET | /v2/tenants/{tenantId}/posts |
| 發布/下架 | PATCH | /v2/posts/{postId}/status |
| 刪除 | DELETE | /v2/posts/{postId} |
| 媒體上傳 | POST | /v2/media/upload |
| 媒體列表 | GET | /v2/tenants/{tenantId}/media |
| 前台貼文 | GET | /v2/posts |
| 前台依 slug | GET | /v2/posts/{slug} |
| 商品卡片 | GET | /v2/listings/{id}/card |

---

## 2. 整合測試矩陣

### 2.1 使用者流程測試 (E2E Scenarios)

| 測試 ID | 場景 | 測試步驟 | 預期結果 |
|---------|------|----------|----------|
| FE-M15-001 | 建立完整貼文 | 登入 → 新增 → 填寫標題內容 → 加入嵌入 → 發布 | 發布成功且前台可見 |
| FE-M15-002 | 編輯已發布貼文 | 登入 → 選擇已發布 → 編輯 → 更新 → 保存 | 更新成功且前台顯示新內容 |
| FE-M15-003 | 刪除已發布貼文 | 登入 → 選擇已發布 → 刪除 | 先自動下架再刪除，前台不可見 |
| FE-M15-004 | 媒體上傳流程 | 登入 → 上傳圖片 → 選擇圖片作為封面 | 上傳成功且可用於貼文 |
| FE-M15-005 | 前台瀏覽 | 未登入 → 瀏覽已發布貼文 → 點擊嵌入商品卡 | 正確顯示且可點擊跳轉 |

### 2.2 錯誤處理測試

| 測試 ID | 場景 | 預期結果 |
|---------|------|----------|
| FE-M15-006 | 標題空白提交 | 顯示 E-9005 驗證錯誤 |
| FE-M15-007 | 重複嵌入相同商品 | 顯示 E-4104 錯誤 |
| FE-M15-008 | 上傳非圖片檔案 | 顯示 E-9001 錯誤 |
| FE-M15-009 | 未登入訪問後台 API | 401 Unauthorized |

---

## 3. Sprint 9 前置條件

### 3.1 Backend 準備

- [x] M15 CMS API 完成（已通過 IT-M15-001~020）
- [x] 所有風險已識別（見 UAT_M15_CMS.md）
- [x] StorageService 已就緒（需 MinIO 環境）

### 3.2 Frontend 準備

- [ ] React 元件庫設定
- [ ] API Client 設定（Axios/Fetch）
- [ ] JWT 認證流程
- [ ] CMS 頁面路由設定

### 3.3 測試環境準備

- [ ] MinIO/S3 服務啟動
- [ ] 前端 Build 環境
- [ ] 跨域（CORS）設定驗證

---

## 4. 測試執行時程

| 階段 | 開始 | 結束 | 負責人 |
|------|------|------|--------|
| FE 實作 | Sprint 9 Day 1 | Sprint 9 Day 5 | FE Dev |
| BE API 驗證 | Sprint 9 Day 1 | Sprint 9 Day 2 | QA |
| 整合測試 | Sprint 9 Day 3 | Sprint 9 Day 4 | QA + FE |
| 缺陷修復 | Sprint 9 Day 4 | Sprint 9 Day 5 | FE Dev |
| 最終驗證 | Sprint 9 Day 5 | Sprint 9 Day 5 | QA |

---

## 5. 風險更新

| 風險 ID | 原等級 | 驗證狀態 | 備註 |
|---------|--------|----------|------|
| R-001 (測試隔離) | 中 | ⏳ UAT 待確認 | 需 QA Team 執行 |
| R-002 (Storage Mock) | 中 | ⏳ UAT 待確認 | 需 MinIO 環境 |
| R-003 (FE 整合) | 低 | ⏳ Sprint 9 待驗證 | 新增關注：跨域設定 |

---

## 6. 新增風險識別

| 風險 ID | 等級 | 說明 | 緩解措施 |
|---------|------|------|----------|
| R-004 | 中 | CORS 跨域問題 | 確認 backend CORS 設定允許前端 origin |
| R-005 | 中 | JWT Token 過期處理 | 前端需實作 Refresh Token 流程 |
| R-006 | 低 | 嵌入商品卡 URL 前端路由 | 確認跳轉路徑與前端路由一致 |

---

## 7. 驗收標準

| 標準 | 要求 |
|------|------|
| 整合測試 | 5/5 E2E 流程通過 |
| 錯誤處理 | 4/4 錯誤場景正確處理 |
| R-004, R-005 風險 | 已緩解或已驗證 |
| 無 High 缺陷 | High = 0 |

---

**文件狀態**: ⏳ 待 Sprint 9 開始時更新為執行中