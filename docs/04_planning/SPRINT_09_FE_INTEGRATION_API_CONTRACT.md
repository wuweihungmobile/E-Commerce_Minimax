# M15 CMS Frontend Integration API Contract
# M15 CMS 前端整合 API 契約文件

## 文件資訊
- **版本**: 1.1
- **日期**: 2026-05-05
- **狀態**: ✅ 已驗證（26/26 測試通過）+ 新增 MultipartFile endpoint
- **Backend API Status**: Production Ready
- **更新內容**: 新增 `/v2/dashboard/media/upload-multipart` endpoint

---

## 1. API Base Information

| 項目 | 值 |
|------|-----|
| **Base URL** | `http://localhost:8080/api` |
| **Frontend URL** | `http://localhost:3000` |
| **認證方式** | JWT Bearer Token |
| **格式** | JSON (POST/PUT), FormData (上傳) |
| **CORS** | 已設定允許 `http://localhost:3000` |

---

## 2. API Endpoints Summary

### 2.1 Post Management (貼文管理)

| 功能 | Method | Endpoint | 認證 | 說明 |
|------|--------|----------|------|------|
| 建立貼文 | POST | `/v2/dashboard/posts` | Required | 建立草稿/發布 |
| 取得貼文列表 | GET | `/v2/dashboard/posts` | Required | Dashboard 列表 |
| 取得貼文詳情 | GET | `/v2/dashboard/posts/{postId}` | Required | 編輯頁載入 |
| 更新貼文 | PUT | `/v2/dashboard/posts/{postId}` | Required | 儲存變更 |
| 刪除貼文 | DELETE | `/v2/dashboard/posts/{postId}` | Required | 刪除草稿/已發布 |
| 發布貼文 | POST | `/v2/dashboard/posts/{postId}/publish` | Required | 發布草稿 |
| 下架貼文 | DELETE | `/v2/dashboard/posts/{postId}/publish` | Required | 下架已發布 |

### 2.2 Public Post (公開貼文 - 前台)

| 功能 | Method | Endpoint | 認證 | 說明 |
|------|--------|----------|------|------|
| 取得已發布列表 | GET | `/v2/posts` | Public | `?tenantId=xxx` |
| 依 Slug 取得 | GET | `/v2/posts/{slug}` | Public | 前台詳情頁 |

### 2.3 Listing Card (嵌入卡片)

| 功能 | Method | Endpoint | 認證 | 說明 |
|------|--------|----------|------|------|
| 取得商品/房型卡片 | GET | `/v2/listings/{listingId}/card` | Public | 嵌入用 |

### 2.4 Media Library (媒體庫)

| 功能 | Method | Endpoint | 認證 | 說明 |
|------|--------|----------|------|------|
| 上傳媒體 (Multipart) | POST | `/v2/dashboard/media/upload-multipart` | Required | ✅ **建議使用** - 實際上傳到 MinIO |
| 上傳媒體 (路徑) | POST | `/v2/media/upload` | Required | ⚠️ Mock 版本 |
| 取得媒體列表 | GET | `/v2/dashboard/media` | Required | 分頁 |
| 刪除媒體 | DELETE | `/v2/dashboard/media/{mediaId}` | Required | - |

### 2.5 Post Category (分類管理)

| 功能 | Method | Endpoint | 認證 | 說明 |
|------|--------|----------|------|------|
| 取得分類列表 | GET | `/v2/dashboard/post-categories` | Required | - |
| 建立分類 | POST | `/v2/dashboard/post-categories` | Required | - |
| 更新分類 | PUT | `/v2/dashboard/post-categories/{categoryId}` | Required | - |
| 刪除分類 | DELETE | `/v2/dashboard/post-categories/{categoryId}` | Required | - |

---

## 3. Detailed API Specifications

### 3.1 建立貼文

**Endpoint**: `POST /v2/dashboard/posts`
**認證**: Required (STORE_OWNER, STORE_STAFF, SELLER, HOST)

**Request Headers**:
```
Authorization: Bearer {JWT_TOKEN}
Content-Type: application/json
```

**Request Body**:
```json
{
  "title": "string (必填)",
  "content": "string (必填)",
  "categoryId": "UUID (選填)",
  "tags": ["string"] (選填),
  "featuredImageUrl": "string (選填)",
  "autoPublish": false (選填, 預設 false)
}
```

**Response** (HTTP 200):
```json
{
  "success": true,
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "tenantId": "...",
    "tenantName": "...",
    "authorId": "...",
    "authorName": "...",
    "title": "string",
    "slug": "auto-generated-slug",
    "content": "string",
    "excerpt": "auto-generated-excerpt",
    "featuredImageUrl": "string",
    "status": "DRAFT",
    "categoryId": "...",
    "categoryName": "...",
    "tags": [],
    "viewCount": 0,
    "publishedAt": null,
    "createdAt": "2026-05-05T12:00:00Z",
    "updatedAt": "2026-05-05T12:00:00Z",
    "embeds": []
  },
  "message": "Post created successfully"
}
```

**Error Responses**:
- `400 Bad Request` - E-9005 (標題為必填)
- `400 Bad Request` - E-4104 (重複嵌入)
- `401 Unauthorized` - E-1000 (未登入)

---

### 3.2 發布貼文

**Endpoint**: `POST /v2/dashboard/posts/{postId}/publish`
**認證**: Required

**Response** (HTTP 200):
```json
{
  "success": true,
  "data": { /* PostResponse with status: PUBLISHED */ },
  "message": "Post published successfully"
}
```

---

### 3.3 下架貼文

**Endpoint**: `DELETE /v2/dashboard/posts/{postId}/publish`
**認證**: Required

**Response** (HTTP 200):
```json
{
  "success": true,
  "data": { /* PostResponse with status: DRAFT */ },
  "message": "Post unpublished successfully"
}
```

---

### 3.4 刪除貼文

**Endpoint**: `DELETE /v2/dashboard/posts/{postId}`
**認證**: Required

**Response** (HTTP 200):
```json
{
  "success": true,
  "data": null,
  "message": "Post deleted successfully"
}
```

**注意**: 已發布的貼文會自動先下架再刪除

---

### 3.5 取得已發布列表（前台）

**Endpoint**: `GET /v2/posts`
**認證**: Public

**Query Parameters**:
| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| tenantId | UUID | Yes | 店鋪 ID |
| page | int | No | 預設 0 |
| size | int | No | 預設 20 |

**Response**:
```json
{
  "success": true,
  "data": {
    "posts": [ /* PostResponse array */ ],
    "totalCount": 10,
    "page": 0,
    "size": 20,
    "totalPages": 1
  }
}
```

---

### 3.6 依 Slug 取得（前台）

**Endpoint**: `GET /v2/posts/{slug}`
**認證**: Public

**Query Parameters**:
| 參數 | 類型 | 必填 | 說明 |
|------|------|------|------|
| tenantId | UUID | Yes | 店鋪 ID |

**Response**:
```json
{
  "success": true,
  "data": { /* PostResponse */ }
}
```

**Error**: `404 Not Found` - E-4101 (貼文不存在或已下架)

---

### 3.7 嵌入商品卡

**Endpoint**: `GET /v2/listings/{listingId}/card`
**認證**: Public

**Response**:
```json
{
  "success": true,
  "data": {
    "listingId": "...",
    "listingType": "PRODUCT",
    "title": "iPhone 15 Pro",
    "coverImageUrl": "https://...",
    "basePrice": 99900,
    "currentPrice": 89900,
    "currency": "TWD",
    "availability": {
      "inStock": true,
      "availableQty": 50,
      "available": true
    },
    "tenantName": "Apple Store",
    "ctaUrl": "/products/123",
    "isActive": true,
    "statusReason": null
  }
}
```

**ROOM Type Response**:
```json
{
  "listingId": "...",
  "listingType": "ROOM",
  "title": "豪華套房",
  "coverImageUrl": "https://...",
  "basePrice": 5000,
  "currentPrice": 4500,
  "currency": "TWD",
  "availability": {
    "inStock": true,
    "availableQty": 3,
    "available": true
  },
  "tenantName": "高雄民宿",
  "ctaUrl": "/rooms/123",
  "isActive": true,
  "statusReason": null
}
```

**Error**: `404 Not Found` - E-4101 (Listing 不存在)

---

### 3.8 媒體上傳

**Endpoint**: `POST /v2/media/upload`
**認證**: Required

**Request Headers**:
```
Authorization: Bearer {JWT_TOKEN}
Content-Type: multipart/form-data
```

**Form Data**:
| 欄位 | 類型 | 必填 | 說明 |
|------|------|------|------|
| fileName | String | Yes | 檔案名稱 |
| originalName | String | Yes | 原始檔案名稱 |
| fileSize | Long | Yes | 檔案大小 (bytes) |
| mimeType | String | Yes | MIME 類型 (image/jpeg, image/png, etc.) |
| filePath | String | Yes | 儲存路徑 |

**Response**:
```json
{
  "success": true,
  "data": {
    "id": "...",
    "fileName": "uploaded_image.jpg",
    "filePath": "media/tenant123/uploaded_image.jpg",
    "fileSize": 1024000,
    "mimeType": "image/jpeg",
    "fileType": "IMAGE",
    "uploadedAt": "2026-05-05T12:00:00Z"
  },
  "message": "Media uploaded successfully"
}
```

**Error**: `400 Bad Request` - E-9001 (不支援的檔案類型)

---

### 3.9 媒體上傳 (MultipartFile - 實際上傳到 MinIO) ⭐ **建議使用**

**Endpoint**: `POST /v2/dashboard/media/upload-multipart`
**認證**: Required
**功能**: 將檔案實際上傳到 MinIO S3 儲存

**Request Headers**:
```
Authorization: Bearer {JWT_TOKEN}
Content-Type: multipart/form-data
```

**Form Data**:
| 欄位 | 類型 | 必填 | 說明 |
|------|------|------|------|
| file | File | Yes | 要上傳的檔案 (image/jpeg, image/png, image/gif, image/webp) |

**Request Example**:
```bash
curl -X POST http://localhost:8080/api/v2/dashboard/media/upload-multipart \
  -H "Authorization: Bearer {JWT_TOKEN}" \
  -F "file=@/path/to/image.jpg"
```

**Response**:
```json
{
  "success": true,
  "data": {
    "id": "...",
    "fileName": "550e8400-e29b-41d4-a716-446655440000-image.jpg",
    "filePath": "tenantId/uuid-image.jpg",
    "fileSize": 1024000,
    "mimeType": "image/jpeg",
    "fileType": "IMAGE",
    "uploadedAt": "2026-05-05T12:00:00Z"
  },
  "message": "Media uploaded successfully"
}
```

**檔案大小限制**:
- 圖片 (image/*): 最大 10MB
- 影片 (video/*): 最大 100MB
- 文件 (application/pdf): 最大 5MB

**Error Responses**:
- `400 Bad Request` - E-9000 (檔案類型不支援)
- `400 Bad Request` - E-9000 (檔案大小超過限制)
- `401 Unauthorized` - E-1000 (未登入)

**注意**: 此 endpoint 會將檔案實際上傳到 MinIO，儲存路徑格式為 `{tenantId}/{uuid}-{originalFileName}`

---

### 3.10 錯誤碼對照表

| Error Code | HTTP Status | 說明 | 處理方式 |
|------------|-------------|------|----------|
| E-1000 | 401 | 未登入或 Token 過期 | 重新登入 |
| E-4101 | 404 | 資源不存在 | 顯示「找不到」 |
| E-4104 | 400 | 重複的嵌入商品 | 提示用戶 |
| E-9000 | 400 | 檔案類型不支援或大小超標 | 提示用戶 |
| E-9001 | 400 | 不支援的檔案類型 | 提示用戶 |
| E-9005 | 400 | 必填欄位空白 | 顯示欄位錯誤 |

---

## 4. Embed Syntax (嵌入語法)

### 4.1 語法格式
```
{{embed:listing:<listingId>}}
```

### 4.2 使用範例
```markdown
這篇文章介紹最新iPhone：

{{embed:listing:550e8400-e29b-41d4-a716-446655440000}}

還有熱門房型：

{{embed:listing:660e8400-e29b-41d4-a716-446655440001}}
```

### 4.3 前端處理流程
1. 用戶在編輯器輸入內容
2. 解析 `{{embed:listing:...}}` 語法
3. 呼叫 `GET /v2/listings/{listingId}/card` 取得卡片資料
4. 替換為商品卡 UI 組件

---

## 5. JWT Token Handling

### 5.1 Token 格式
```javascript
// Request Header
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 5.2 Token 過期處理
當收到 `401 Unauthorized` 時：
1. 清除 localStorage 中的 token
2. 導向登入頁 `/login`
3. 重新登入後繼續操作

### 5.3 建議的 Axios 設定
```javascript
import axios from 'axios';

const apiClient = axios.create({
  baseURL: 'http://localhost:8080/api',
  timeout: 30000,
});

// Request Interceptor - 附加 Token
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
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);
```

---

## 6. Frontend Routes

| 頁面 | Route | 對應 API |
|------|-------|----------|
| CMS 儀表板 | `/cms` | GET /v2/dashboard/posts |
| 新增貼文 | `/cms/posts/new` | POST /v2/dashboard/posts |
| 編輯貼文 | `/cms/posts/{postId}/edit` | GET/PUT /v2/dashboard/posts/{postId} |
| 媒體庫 | `/cms/media` | GET/POST/DELETE /v2/dashboard/media/* |
| 前台部落格 | `/blog` | GET /v2/posts |
| 貼文詳情 | `/blog/{slug}` | GET /v2/posts/{slug} |

---

## 7. Test Results Summary

| 測試類型 | 數量 | 通過 | 狀態 |
|---------|------|------|------|
| Backend IT | 26 | 26 | ✅ 100% |
| API 覆蓋率 | - | 92% | ✅ |
| 模組覆蓋率 | - | 100% | ✅ |

**完整測試報告**: [TR_M15_API_Results.md](../../03_testing/TR_M15_API_Results.md)

---

**文件狀態**: ✅ 已驗證 + 新增 MultipartFile endpoint，可供 FE 團隊使用
**最後更新**: 2026-05-05 22:30 UTC+0800
**Sprint 9 開始**: 2026-05-06