# Sprint 9 FE 整合測試詳細計劃 / Sprint 9 FE Integration Test Detailed Plan

## 文件資訊
- **TP-ID**: TP-M15-Sprint9-FE
- **版本**: 1.0
- **日期**: 2026-05-05
- **狀態**: ⏳ 待 Sprint 9 開始
- **測試類型**: FE-BE 整合測試

---

## 1. 前端實作範圍

### 1.1 CMS 功能前端（待 FE Dev 實作）

| 功能 | 優先級 | Frontend URL | 說明 |
|------|--------|-------------|------|
| 貼文列表頁 | P1 | http://localhost:3000/cms | Dashboard 顯示所有貼文 |
| 貼文編輯頁 | P1 | http://localhost:3000/cms/posts/new | 建立新貼文 |
| 貼文編輯頁 | P1 | http://localhost:3000/cms/posts/{postId}/edit | 編輯現有貼文 |
| 媒體庫管理 | P2 | http://localhost:3000/cms/media | 上傳、刪除媒體 |
| 前台顯示 | P1 | http://localhost:3000/blog | 顯示已發布貼文 |
| 貼文詳情 | P1 | http://localhost:3000/blog/{slug} | 顯示貼文內容與嵌入 |

### 1.2 FE-BE API 整合點

| 功能 | Method | Backend URL | 說明 |
|------|--------|------------|------|
| 建立貼文 | POST | /v2/tenants/{tenantId}/posts | 新增草稿 |
| 取得貼文列表 | GET | /v2/tenants/{tenantId}/posts | Dashboard 列表 |
| 更新貼文 | PUT | /v2/posts/{postId} | 編輯貼文 |
| 發布/下架 | PATCH | /v2/posts/{postId}/status | 狀態變更 |
| 刪除貼文 | DELETE | /v2/posts/{postId} | 刪除草稿或已發布 |
| 媒體上傳 | POST | /v2/media/upload | 上傳圖片 |
| 媒體列表 | GET | /v2/tenants/{tenantId}/media | 媒體庫 |
| 刪除媒體 | DELETE | /v2/media/{mediaId} | 刪除媒體 |
| 前台列表 | GET | /v2/posts | 已發布列表（Public） |
| 前台詳情 | GET | /v2/posts/{slug} | 依 slug 取得（Public） |
| 商品卡片 | GET | /v2/listings/{id}/card | 嵌入卡片（Public） |

---

## 2. 前端操作測試案例

### 模組 FE-A：登入與認證

---

#### FE-TC-001: CMS 管理員登入

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-001 |
| **測試類型** | E2E |
| **優先級** | P1 (Critical) |

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 開啟登入頁 | http://localhost:3000/login | 顯示登入表單 |
| 2 | 輸入 Email | - | admin@nextkey.com |
| 3 | 輸入 Password | - | Admin123! |
| 4 | 點擊「登入」 | - | 發送 POST /v2/auth/login |
| 5 | 等待回應 | - | 取得 JWT Token |
| 6 | 重新導向 | - | 前往 CMS Dashboard |

**預期結果**:
- 登入成功
- JWT Token 儲存於 localStorage/sessionStorage
- 自動導向 http://localhost:3000/cms
- 顯示「歡迎，Admin」之類的訊息

**驗證點**:
- [ ] 登入表單驗證（空白欄位提示）
- [ ] 錯誤密碼顯示錯誤訊息
- [ ] 成功後 Token 存在
- [ ] 失敗後不導向 CMS

---

#### FE-TC-002: 無效 Session 重新登入

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-002 |
| **測試類型** | E2E |
| **優先級** | P2 (High) |

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 清除 Token | - | 清除 localStorage |
| 2 | 嘗試訪問 CMS | http://localhost:3000/cms | 重新導向到登入頁 |
| 3 | 自動登入（如果記住我） | - | 或需重新輸入帳密 |

**預期結果**:
- 未登入用戶訪問 CMS 頁面會被導向登入頁
- 之後的 API 請求會收到 401

---

### 模組 FE-B：貼文管理

---

#### FE-TC-003: 建立新貼文（含嵌入解析）

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-003 |
| **測試類型** | E2E |
| **優先級** | P1 (Critical) |

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 登入 CMS | http://localhost:3000/login | 登入成功 |
| 2 | 點擊「新增貼文」 | http://localhost:3000/cms/posts/new | 開啟編輯器 |
| 3 | 輸入標題 | - | 「測試文章標題」 |
| 4 | 輸入內容 | - | 包含 `{{embed:listing:c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13}}` |
| 5 | 點擊「儲存草稿」 | - | POST /v2/tenants/{id}/posts |
| 6 | 等待回應 | - | 201 Created |
| 7 | 確認畫面 | - | 顯示「草稿已儲存」 |

**預期結果**:
- 貼文建立成功
- 系統解析 `{{embed:listing:...}}` 並顯示商品卡預覽
- 貼文狀態為 DRAFT

**驗證點**:
- [ ] 標題空白時顯示 E-9005 錯誤
- [ ] 有效嵌入顯示商品卡預覽
- [ ] 重複嵌入顯示 E-4104 錯誤

**API 呼叫**:
```bash
# POST /v2/tenants/{tenantId}/posts
{
  "title": "測試文章標題",
  "content": "這是一篇測試文章，包含嵌入：{{embed:listing:c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13}}",
  "autoPublish": false
}
```

---

#### FE-TC-004: 發布草稿貼文

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-004 |
| **測試類型** | E2E |
| **優先級** | P1 (Critical) |

**前置條件**: 有一個草稿狀態的貼文

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 進入 CMS Dashboard | http://localhost:3000/cms | 看到草稿列表 |
| 2 | 選擇草稿貼文 | - | 點擊「編輯」 |
| 3 | 點擊「發布」 | - | PATCH /v2/posts/{postId}/status |
| 4 | 確認發布成功 | - | 顯示「已發布」 |
| 5 | 前往前台 | http://localhost:3000/blog | 看到新發布的貼文 |

**預期結果**:
- HTTP Status: 200 OK
- post status 變為 PUBLISHED
- 前台可見該貼文

**API 呼叫**:
```bash
# PATCH /v2/posts/{postId}/status
{ "status": "PUBLISHED" }
```

---

#### FE-TC-005: 編輯已發布貼文

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-005 |
| **測試類型** | E2E |
| **優先級** | P1 (Critical) |

**前置條件**: 有一個已發布狀態的貼文

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 進入 CMS Dashboard | http://localhost:3000/cms | 看到已發布列表 |
| 2 | 選擇已發布貼文 | - | 點擊「編輯」 |
| 3 | 修改標題 | - | 「更新後的標題」 |
| 4 | 修改內容 | - | 新增或修改嵌入 |
| 5 | 點擊「儲存」 | - | PUT /v2/posts/{postId} |
| 6 | 確認更新成功 | - | 顯示「已更新」 |
| 7 | 前往前台 | http://localhost:3000/blog/{slug} | 看到更新後的內容 |

**預期結果**:
- 標題和內容更新成功
- Slug 可能改變（如果標題變更）
- 前台顯示新內容

---

#### FE-TC-006: 下架已發布貼文

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-006 |
| **測試類型** | E2E |
| **優先級** | P1 (Critical) |

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 進入 CMS Dashboard | http://localhost:3000/cms | 看到已發布列表 |
| 2 | 選擇已發布貼文 | - | 點擊「編輯」 |
| 3 | 點擊「下架」 | - | PATCH /v2/posts/{postId}/status |
| 4 | 確認下架成功 | - | 顯示「已下架」 |
| 5 | 前往前台 | http://localhost:3000/blog/{slug} | 404 Not Found |

**預期結果**:
- post status 變為 DRAFT
- 前台 API 回傳 404（E-4101）

---

#### FE-TC-007: 刪除草稿貼文

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-007 |
| **測試類型** | E2E |
| **優先級** | P1 (Critical) |

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 進入 CMS Dashboard | http://localhost:3000/cms | 看到草稿列表 |
| 2 | 選擇草稿 | - | 點擊「編輯」 |
| 3 | 點擊「刪除」 | - | 顯示確認對話框 |
| 4 | 確認刪除 | - | DELETE /v2/posts/{postId} |
| 5 | 確認成功 | - | 導向列表頁，貼文消失 |

**預期結果**:
- HTTP Status: 204 No Content
- 貼文從列表中消失
- 無需先下架

---

#### FE-TC-008: 刪除已發布貼文（自動下架）

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-008 |
| **測試類型** | E2E |
| **優先級** | P1 (Critical) |

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 進入 CMS Dashboard | http://localhost:3000/cms | 看到已發布列表 |
| 2 | 選擇已發布 | - | 點擊「編輯」 |
| 3 | 點擊「刪除」 | - | 顯示確認對話框 |
| 4 | 確認刪除 | - | 系統自動先 unpublish，再刪除 |
| 5 | 確認成功 | - | 導向列表頁，貼文消失 |
| 6 | 前往前台 | http://localhost:3000/blog/{slug} | 404 Not Found |

**預期結果**:
- 系統自動先呼叫 unpublish，再執行 delete
- HTTP Status: 204 No Content
- 前台不可見該貼文

---

### 模組 FE-C：媒體庫管理

---

#### FE-TC-009: 上傳圖片媒體

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-009 |
| **測試類型** | E2E |
| **優先級** | P2 (High) |

**⚠️ 注意**: 現有 `/v2/media/upload` 使用路徑-based 方法，不會真的上傳到 S3。需確認是否有 MultipartFile 版本。

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 進入媒體庫 | http://localhost:3000/cms/media | 顯示已上傳媒體列表 |
| 2 | 點擊「上傳」 | - | 開啟檔案選擇器 |
| 3 | 選擇圖片檔案 | - | 限制：jpg, png, gif, webp |
| 4 | 確認上傳 | - | POST /v2/media/upload |
| 5 | 顯示進度 | - | 上傳進度條 |
| 6 | 上傳完成 | - | 媒體出現在列表 |

**預期結果**:
- 圖片上傳成功
- 出現在媒體列表
- 可以選擇作為貼文封面

**驗證點**:
- [ ] 檔案類型錯誤顯示 E-9001
- [ ] 檔案大小超標顯示錯誤
- [ ] 上傳進度顯示

---

#### FE-TC-010: 刪除未使用媒體

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-010 |
| **測試類型** | E2E |
| **優先級** | P2 (High) |

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 進入媒體庫 | http://localhost:3000/cms/media | 看到媒體列表 |
| 2 | 選擇未使用媒體 | - | 點擊「刪除」 |
| 3 | 確認刪除 | - | DELETE /v2/media/{mediaId} |
| 4 | 確認成功 | - | 媒體從列表消失 |

**預期結果**:
- HTTP Status: 204 No Content
- 媒體從列表消失
- 如果被 Post 使用，顯示錯誤

---

### 模組 FE-D：前台顯示

---

#### FE-TC-011: 瀏覽已發布貼文列表

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-011 |
| **測試類型** | E2E |
| **優先級** | P1 (Critical) |

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 前往前台首頁 | http://localhost:3000/blog | 看到已發布貼文列表 |
| 2 | 滾動列表 | - | 分頁載入更多 |
| 3 | 點擊貼文 | - | 前往詳情頁 |

**預期結果**:
- 只顯示已發布的貼文（status=PUBLISHED）
- 分頁正常運作
- 顯示 excerpt 和 featured image

---

#### FE-TC-012: 瀏覽貼文詳情與嵌入商品卡

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-012 |
| **測試類型** | E2E |
| **優先級** | P1 (Critical) |

**前置條件**: 有一個包含嵌入的已發布貼文

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 前往前台 | http://localhost:3000/blog | 看到貼文列表 |
| 2 | 點擊貼文 | http://localhost:3000/blog/{slug} | 顯示貼文詳情 |
| 3 | 查看嵌入商品卡 | - | 顯示 `{{embed:listing:...}}` 解析後的商品卡 |
| 4 | 點擊商品卡 | - | 跳轉到商品詳情頁 |

**預期結果**:
- 嵌入語法被解析，顯示為商品卡
- 商品卡包含：圖片、標題、價格
- 點擊可跳轉到對應頁面

**嵌入解析格式**:
```
{{embed:listing:<listing_id>}} → 顯示為商品卡
{{embed:listing:c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13}} → GET /v2/listings/{id}/card
```

---

#### FE-TC-013: 瀏覽已下架貼文（應該 404）

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-013 |
| **測試類型** | E2E |
| **優先級** | P2 (High) |

**前置條件**: 有一個已下架的貼文

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 直接訪問已下架貼文 | http://localhost:3000/blog/{slug} | 顯示 404 頁面 |

**預期結果**:
- 顯示「找不到頁面」或類似錯誤
- 不顯示貼文內容

---

### 模組 FE-E：错误處理

---

#### FE-TC-014: 標題空白提交

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-014 |
| **測試類型** | Error Handling |
| **優先級** | P1 (Critical) |

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 進入新增貼文 | http://localhost:3000/cms/posts/new | 開啟編輯器 |
| 2 | 留空標題 | - | |
| 3 | 填寫內容 | - | |
| 4 | 點擊「發布」 | - | POST API |
| 5 | 查看錯誤 | - | 顯示 E-9005 錯誤 |

**預期結果**:
- 顯示錯誤訊息：「標題為必填」
- 不發布成功

---

#### FE-TC-015: 重複嵌入相同商品

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-015 |
| **測試類型** | Error Handling |
| **優先級** | P2 (High) |

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 進入新增貼文 | http://localhost:3000/cms/posts/new | 開啟編輯器 |
| 2 | 標題輸入「測試」 | - | |
| 3 | 內容輸入 | - | `{{embed:listing:c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13}}` 出現兩次 |
| 4 | 點擊「發布」 | - | POST API |
| 5 | 查看錯誤 | - | 顯示 E-4104 錯誤 |

**預期結果**:
- 顯示錯誤：「重複的嵌入商品」
- 不發布成功

---

#### FE-TC-016: 上傳非圖片檔案

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-016 |
| **測試類型** | Error Handling |
| **優先級** | P2 (High) |

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 進入媒體庫 | http://localhost:3000/cms/media | 開啟媒體庫 |
| 2 | 點擊「上傳」 | - | 開啟檔案選擇器 |
| 3 | 選擇 .txt 檔案 | - | |
| 4 | 確認上傳 | - | POST API |
| 5 | 查看錯誤 | - | 顯示 E-9001 錯誤 |

**預期結果**:
- 顯示錯誤：「不支援的檔案類型」
- 上傳失敗

---

#### FE-TC-017: 未登入訪問後台 API

| 項目 | 內容 |
|------|------|
| **測試 ID** | FE-TC-017 |
| **測試類型** | Security |
| **優先級** | P1 (Critical) |

**測試步驟**:

| 步驟 | 操作 | URL | 預期結果 |
|------|------|-----|----------|
| 1 | 清除登入狀態 | - | 清除 Token |
| 2 | 直接呼叫 CMS API | curl -X POST http://localhost:8080/v2/tenants/{id}/posts | 401 Unauthorized |

**預期結果**:
- HTTP Status: 401 Unauthorized
- Error Code: E-1000
- 不允許操作

---

## 3. CORS 與 API 整合驗證

### 3.1 CORS 設定檢查

**後端必須設定允許的前端 Origin**:

```yaml
# backend/src/main/resources/application.yml
cors:
  allowed-origins:
    - "http://localhost:3000"
    - "https://staging.example.com"
```

**驗證步驟**:

```bash
# 測試 CORS Preflight
curl -X OPTIONS http://localhost:8080/v2/tenants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/posts \
  -H "Origin: http://localhost:3000" \
  -H "Access-Control-Request-Method: POST" \
  -H "Access-Control-Request-Headers: Authorization,Content-Type"
```

**預期結果**:
- HTTP Status: 204 No Content
- Headers 包含:
  - `Access-Control-Allow-Origin: http://localhost:3000`
  - `Access-Control-Allow-Methods: GET, POST, PUT, DELETE, PATCH, OPTIONS`
  - `Access-Control-Allow-Headers: Authorization,Content-Type`

---

### 3.2 JWT Token 傳遞驗證

**前端必須在每個請求中攜帶 JWT**:

```javascript
// API Client 設定範例
const apiClient = axios.create({
  baseURL: 'http://localhost:8080',
  timeout: 30000,
});

// Request Interceptor - 自動附加 Token
apiClient.interceptors.request.use((config) => {
  const token = localStorage.getItem('token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response Interceptor - 處理 401
apiClient.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      // Token 過期，清除並重新登入
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

---

## 4. Sprint 9 前置準備檢查清單

### 4.1 Backend 準備（Dev Team）

| 項目 | 負責人 | 狀態 | 備註 |
|------|--------|------|------|
| M15 CMS API 完成 | David | ✅ Done | 所有 E2E 測試通過 |
| StorageService 實際上傳驗證 | David | ⏳ 待確認 | 需 MinIO 環境 |
| CORS 設定允許前端 origin | David | ⏳ 待確認 | 需檢查 application.yml |
| JWT 驗證逻辑正確 | David | ✅ Done | AuthController 已實作 |

### 4.2 Frontend 準備（FE Dev）

| 項目 | 負責人 | 狀態 | 備註 |
|------|--------|------|------|
| React 專案初始化 | FE Dev | ⏳ 待開始 | |
| API Client 設定（Axios） | FE Dev | ⏳ 待開始 | 需處理 JWT |
| JWT 登入流程 | FE Dev | ⏳ 待開始 | 登入 → Token 儲存 |
| CMS 頁面路由設定 | FE Dev | ⏳ 待開始 | /cms/* |
| 貼文列表頁實作 | FE Dev | ⏳ 待開始 | |
| 貼文編輯頁實作 | FE Dev | ⏳ 待開始 | 含嵌入解析 |
| 媒體庫頁面實作 | FE Dev | ⏳ 待開始 | 上傳/刪除 |
| 前台頁面實作 | FE Dev | ⏳ 待開始 | Blog 首頁/詳情 |

### 4.3 測試環境準備

| 項目 | 負責人 | 狀態 | 備註 |
|------|--------|------|------|
| MinIO/S3 服務啟動 | DevOps | ⏳ 待確認 | |
| 前端 Build 環境 | FE Dev | ⏳ 待開始 | |
| CORS 設定驗證 | QA | ⏳ 待確認 | |
| 測試資料準備 | QA | ⏳ 待開始 | |

---

## 5. 測試執行追蹤表

### 5.1 執行摘要

| 項目 | 數值 |
|------|------|
| 測試期間 | Sprint 9 Day 3 ~ Day 5 |
| 測試案例總數 | 17 |
| 通過 | (待填) |
| 失敗 | (待填) |
| 阻塞 | (待填) |

### 5.2 詳細結果追蹤

| FE-TC ID | 執行時間 | 執行者 | 結果 | 備註 |
|----------|----------|--------|------|------|
| FE-TC-001 | | | ⏳ | |
| FE-TC-002 | | | ⏳ | |
| FE-TC-003 | | | ⏳ | |
| FE-TC-004 | | | ⏳ | |
| FE-TC-005 | | | ⏳ | |
| FE-TC-006 | | | ⏳ | |
| FE-TC-007 | | | ⏳ | |
| FE-TC-008 | | | ⏳ | |
| FE-TC-009 | | | ⏳ | |
| FE-TC-010 | | | ⏳ | |
| FE-TC-011 | | | ⏳ | |
| FE-TC-012 | | | ⏳ | |
| FE-TC-013 | | | ⏳ | |
| FE-TC-014 | | | ⏳ | |
| FE-TC-015 | | | ⏳ | |
| FE-TC-016 | | | ⏳ | |
| FE-TC-017 | | | ⏳ | |

---

## 6. 驗收標準達成狀態

| 標準 | 目標 | 實際 | 狀態 |
|------|------|------|------|
| 整合測試 | 8/8 E2E 流程通過 | X/8 | ⏳ |
| 錯誤處理 | 4/4 錯誤場景正確處理 | X/4 | ⏳ |
| 安全測試 | 1/1 權限驗證正確 | X/1 | ⏳ |
| CORS 設定 | 已驗證 | 待確認 | ⏳ |
| 無 High 缺陷 | High = 0 | 待確認 | ⏳ |

---

## 7. 風險追蹤更新

| 風險 ID | 等級 | 說明 | 驗證方式 | 狀態 |
|---------|------|------|----------|------|
| R-001 | 中 | 測試隔離環境 | UAT 環境乾淨 | ⏳ UAT 待確認 |
| R-002 | 中 | StorageService Mock | MinIO 實際上傳 | ⏳ UAT 待確認 |
| R-003 | 低 | 前端整合 | Sprint 9 FE 整合 | ⏳ Sprint 9 待驗證 |
| R-004 | 中 | CORS 跨域問題 | 確認後端允許前端 origin | ⏳ Sprint 9 待確認 |
| R-005 | 中 | JWT Token 過期處理 | 前端需實作 Refresh | ⏳ Sprint 9 待確認 |
| R-006 | 低 | 嵌入商品卡 URL 前端路由 | 確認跳轉路徑 | ⏳ Sprint 9 待確認 |

---

**文件狀態**: ⏳ 待 Sprint 9 開始時更新為執行中
**負責 QA**: Quincy (待指派)
**最後更新**: 2026-05-05