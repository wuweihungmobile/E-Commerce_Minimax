# M15 CMS Backend UAT 詳細測試計劃 / M15 CMS Backend UAT Detailed Test Plan

## 文件資訊
- **TP-ID**: TP-M15-UAT
- **版本**: 1.0
- **日期**: 2026-05-05
- **狀態**: 🔴 待 QA Team 執行
- **測試類型**: 手動 UAT + API 驗證

---

## 1. 測試環境資訊

### 1.1 環境配置

| 環境 | URL | 用途 | 資料庫 |
|------|-----|------|--------|
| Backend API | http://localhost:8080 | API 測試 | nextkey_uat |
| MinIO Console | http://localhost:9001 | 媒體驗證 | - |
| Frontend (待 Sprint 9) | http://localhost:3000 | FE 整合測試 | nextkey_uat |
| PostgreSQL | localhost:5432 | 資料庫 | nextkey_uat |
| Redis | localhost:6379 | 快取 | - |

### 1.2 測試帳號

| 角色 | Email | Password | Tenant ID | 用途 |
|------|-------|----------|-----------|------|
| Admin | admin@nextkey.com | Admin123! | a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11 | 全功能測試 |
| Store Owner | owner@nextkey.com | Owner123! | a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11 | CMS 操作 |
| Store Staff | staff@nextkey.com | Staff123! | a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11 | CMS 唯讀 |
| 其他 Tenant | other@tenant.com | Other123! | b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12 | 跨 tenant 驗證 |

### 1.3 JWT Token 取得

```bash
# 取得 Admin JWT Token
curl -X POST http://localhost:8080/v2/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@nextkey.com","password":"Admin123!"}'
```

**預期 Response**:
```json
{
  "success": true,
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 86400
  }
}
```

---

## 2. 前端 URL 與操作流程

### 2.1 CMS 前端 URL（待 Sprint 9 FE 實作）

| 頁面 | Frontend URL | 測試功能 |
|------|-------------|----------|
| CMS Dashboard | http://localhost:3000/cms | 貼文列表 |
| 建立貼文 | http://localhost:3000/cms/posts/new | 新增貼文 |
| 編輯貼文 | http://localhost:3000/cms/posts/{postId}/edit | 編輯貼文 |
| 媒體庫 | http://localhost:3000/cms/media | 媒體管理 |
| 前台首頁 | http://localhost:3000/blog | 已發布貼文列表 |
| 貼文詳情 | http://localhost:3000/blog/{slug} | 貼文內容 |

### 2.2 Backend API URL（目前可直接測試）

| API | Method | URL | 說明 |
|-----|--------|-----|------|
| 登入 | POST | http://localhost:8080/v2/auth/login | 取得 JWT |
| 建立貼文 | POST | http://localhost:8080/v2/tenants/{tenantId}/posts | 新增草稿 |
| 取得貼文列表 | GET | http://localhost:8080/v2/tenants/{tenantId}/posts | Dashboard |
| 發布貼文 | PATCH | http://localhost:8080/v2/posts/{postId}/status | 發布/下架 |
| 刪除貼文 | DELETE | http://localhost:8080/v2/posts/{postId} | 刪除草稿 |
| 媒體上傳 | POST | http://localhost:8080/v2/media/upload | 上傳圖片 |
| 媒體列表 | GET | http://localhost:8080/v2/tenants/{tenantId}/media | 媒體庫 |
| 前台列表 | GET | http://localhost:8080/v2/posts | 已發布列表 |
| 前台詳情 | GET | http://localhost:8080/v2/posts/{slug} | 依 slug |
| 商品卡片 | GET | http://localhost:8080/v2/listings/{id}/card | 嵌入卡片 |

---

## 3. 詳細測試案例

### 模組 A：Post 管理功能（API 直接測試）

---

#### TC-M15-001: 建立草稿貼文

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-001 |
| **測試類型** | API Functional |
| **優先級** | P1 (Critical) |
| **測試 API** | POST /v2/tenants/{tenantId}/posts |

**前置條件**:
1. JWT Token 已取得（見 1.3）
2. Tenant ID: `a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11`

**測試步驟**:

```bash
# Step 1: 設定 JWT Token（Postman/Insomnia 或 curl）
TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Step 2: 執行建立貼文 API
curl -X POST http://localhost:8080/v2/tenants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/posts \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Test Post Title",
    "content": "This is test content with {{embed:listing:c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13}} embedded.",
    "autoPublish": false
  }'
```

**預期結果**:
- HTTP Status: `201 Created`
- Response Body:
```json
{
  "success": true,
  "data": {
    "id": "uuid-of-created-post",
    "title": "Test Post Title",
    "slug": "test-post-title",
    "status": "DRAFT",
    "authorId": "b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12",
    "createdAt": "2026-05-05T10:00:00Z"
  }
}
```

**驗證點**:
- [ ] Status code 為 201
- [ ] response.success 為 true
- [ ] postId 已返回
- [ ] status 為 DRAFT

**後置條件**: 記錄回傳的 postId，供後續測試使用

---

#### TC-M15-002: 建立貼文 - 標題驗證（空白標題）

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-002 |
| **測試類型** | API Validation |
| **優先級** | P1 (Critical) |
| **測試 API** | POST /v2/tenants/{tenantId}/posts |

**測試步驟**:

```bash
curl -X POST http://localhost:8080/v2/tenants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/posts \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "",
    "content": "Some content"
  }'
```

**預期結果**:
- HTTP Status: `400 Bad Request`
- Error Code: `E-9005`

---

#### TC-M15-003: 建立貼文 - 有效 Embed 語法

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-003 |
| **測試類型** | API Functional |
| **優先級** | P2 (High) |
| **測試 API** | POST /v2/tenants/{tenantId}/posts |

**前置條件**:
- Listing ID `c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13` 存在且狀態為 ACTIVE

**測試步驟**:

```bash
curl -X POST http://localhost:8080/v2/tenants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/posts \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Post with Embed",
    "content": "Check out this product: {{embed:listing:c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13}}"
  }'
```

**預期結果**:
- HTTP Status: `201 Created`
- 回傳的 post 包含 embeds 陣列，其中 listingId 為 `c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13`

---

#### TC-M15-004: 建立貼文 - 重複 Embed 驗證

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-004 |
| **測試類型** | API Validation |
| **優先級** | P2 (High) |
| **測試 API** | POST /v2/tenants/{tenantId}/posts |

**測試步驟**:

```bash
curl -X POST http://localhost:8080/v2/tenants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/posts \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Post with Duplicate Embed",
    "content": "Product 1: {{embed:listing:c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13}}\nProduct 2: {{embed:listing:c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13}}"
  }'
```

**預期結果**:
- HTTP Status: `400 Bad Request`
- Error Code: `E-4104`
- Error Message: 包含 "duplicate" 或 "already embedded"

---

#### TC-M15-005: 更新貼文

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-005 |
| **測試類型** | API Functional |
| **優先級** | P1 (Critical) |
| **測試 API** | PUT /v2/posts/{postId} |

**前置條件**: 已建立草稿貼文（使用 TC-M15-001 的 postId）

**測試步驟**:

```bash
POST_ID="uuid-from-tc-m15-001"

curl -X PUT http://localhost:8080/v2/posts/${POST_ID} \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{
    "title": "Updated Post Title",
    "content": "Updated content here"
  }'
```

**預期結果**:
- HTTP Status: `200 OK`
- slug 可能更新（如果標題改變）

---

#### TC-M15-006: 發布貼文

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-006 |
| **測試類型** | API Functional |
| **優先級** | P1 (Critical) |
| **測試 API** | PATCH /v2/posts/{postId}/status |

**前置條件**: 有草稿狀態的 postId

**測試步驟**:

```bash
POST_ID="uuid-from-tc-m15-001"

curl -X PATCH http://localhost:8080/v2/posts/${POST_ID}/status \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"status": "PUBLISHED"}'
```

**預期結果**:
- HTTP Status: `200 OK`
- response.data.status 為 `PUBLISHED`
- publishedAt 時間戳記已設定

---

#### TC-M15-007: 下架貼文

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-007 |
| **測試類型** | API Functional |
| **優先級** | P1 (Critical) |
| **測試 API** | PATCH /v2/posts/{postId}/status |

**前置條件**: 有已發布狀態的 postId（剛從 TC-M15-006 來的）

**測試步驟**:

```bash
POST_ID="uuid-from-tc-m15-006"

curl -X PATCH http://localhost:8080/v2/posts/${POST_ID}/status \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"status": "DRAFT"}'
```

**預期結果**:
- HTTP Status: `200 OK`
- response.data.status 為 `DRAFT`

---

#### TC-M15-008: 刪除草稿

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-008 |
| **測試類型** | API Functional |
| **優先級** | P1 (Critical) |
| **測試 API** | DELETE /v2/posts/{postId} |

**前置條件**: 有草稿狀態的 postId

**測試步驟**:

```bash
# 先建立一個草稿（用於刪除測試）
CREATE_RESULT=$(curl -s -X POST http://localhost:8080/v2/tenants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/posts \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"title": "Draft to Delete"}')
DRAFT_ID=$(echo $CREATE_RESULT | jq -r '.data.id')

# 刪除草稿
curl -X DELETE http://localhost:8080/v2/posts/${DRAFT_ID} \
  -H "Authorization: Bearer ${TOKEN}"
```

**預期結果**:
- HTTP Status: `204 No Content`
- 再次取得該 postId 會回傳 404

---

#### TC-M15-009: 刪除已發布（自動下架後刪除）

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-009 |
| **測試類型** | API Functional |
| **優先級** | P1 (Critical) |
| **測試 API** | DELETE /v2/posts/{postId} |

**測試步驟**:

```bash
# Step 1: 建立並發布一個貼文
CREATE_RESULT=$(curl -s -X POST http://localhost:8080/v2/tenants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/posts \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"title": "Published to Delete", "autoPublish": true}')
PUBLISHED_ID=$(echo $CREATE_RESULT | jq -r '.data.id')

# Step 2: 刪除已發布的貼文
curl -X DELETE http://localhost:8080/v2/posts/${PUBLISHED_ID} \
  -H "Authorization: Bearer ${TOKEN}"
```

**預期結果**:
- HTTP Status: `204 No Content`
- 系統自動先執行 unpublish，再執行 delete
- 前台 API 取得該 slug 回傳 404

---

#### TC-M15-010: 取得貼文詳情

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-010 |
| **測試類型** | API Functional |
| **優先級** | P1 (Critical) |
| **測試 API** | GET /v2/posts/{postId} |

**測試步驟**:

```bash
POST_ID="uuid-from-tc-m15-001"

curl -X GET http://localhost:8080/v2/posts/${POST_ID} \
  -H "Authorization: Bearer ${TOKEN}"
```

**預期結果**:
- HTTP Status: `200 OK`
- 回傳完整 post 資料（包含 id, title, slug, content, status, embeds, timestamps）

---

#### TC-M15-011: 取得無效貼文

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-011 |
| **測試類型** | API Validation |
| **優先級** | P2 (High) |
| **測試 API** | GET /v2/posts/{postId} |

**測試步驟**:

```bash
INVALID_ID="00000000-0000-0000-0000-000000000000"

curl -X GET http://localhost:8080/v2/posts/${INVALID_ID} \
  -H "Authorization: Bearer ${TOKEN}"
```

**預期結果**:
- HTTP Status: `404 Not Found`
- Error Code: `E-4100`

---

#### TC-M15-012: 跨 Tenant 權限驗證

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-012 |
| **測試類型** | API Security |
| **優先級** | P1 (Critical) |
| **測試 API** | GET /v2/posts/{postId} |

**測試步驟**:

```bash
# 使用 other tenant 的 token
OTHER_TOKEN="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."  # other@tenant.com 的 token
POST_ID="uuid-from-tc-m15-001"  # 屬於 a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11 的貼文

curl -X GET http://localhost:8080/v2/posts/${POST_ID} \
  -H "Authorization: Bearer ${OTHER_TOKEN}"
```

**預期結果**:
- HTTP Status: `403 Forbidden`
- Error Code: `E-4031`

---

### 模組 B：Public API 功能（無需認證）

---

#### TC-M15-013: 取得已發布貼文列表

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-013 |
| **測試類型** | Public API |
| **優先級** | P1 (Critical) |
| **測試 API** | GET /v2/posts |

**測試步驟**:

```bash
# 無需 JWT Token
curl -X GET "http://localhost:8080/v2/posts?tenantId=a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11&page=0&size=10"
```

**預期結果**:
- HTTP Status: `200 OK`
- 回傳分頁資料（posts 陣列, totalCount, page, size, totalPages）
- 只包含 status=PUBLISHED 的貼文

---

#### TC-M15-014: 依 Slug 取得已發布貼文

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-014 |
| **測試類型** | Public API |
| **優先級** | P1 (Critical) |
| **測試 API** | GET /v2/posts/{slug} |

**前置條件**: 有一個已發布的貼文（從 TC-M15-006 來的 slug）

**測試步驟**:

```bash
SLUG="test-post-title"  # 從 TC-M15-001 或 TC-M15-006

curl -X GET http://localhost:8080/v2/posts/${SLUG}
```

**預期結果**:
- HTTP Status: `200 OK`
- 回傳完整 post 內容（包含 embeds）
- viewCount 應該 +1

---

#### TC-M15-015: 依 Slug 取得 - 無效 slug

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-015 |
| **測試類型** | Public API Validation |
| **優先級** | P2 (High) |
| **測試 API** | GET /v2/posts/{slug} |

**測試步驟**:

```bash
curl -X GET http://localhost:8080/v2/posts/nonexistent-slug-12345
```

**預期結果**:
- HTTP Status: `404 Not Found`
- Error Code: `E-4100`

---

#### TC-M15-016: 依 Slug 取得 - 已下架貼文

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-016 |
| **測試類型** | Public API Validation |
| **優先級** | P2 (High) |
| **測試 API** | GET /v2/posts/{slug} |

**前置條件**: 有一個已下架的貼文（從 TC-M15-007 來的）

**測試步驟**:

```bash
SLUG="published-to-delete"  # 從 TC-M15-009 的貼文（已下架）

curl -X GET http://localhost:8080/v2/posts/${SLUG}
```

**預期結果**:
- HTTP Status: `404 Not Found`
- Error Code: `E-4101`

---

### 模組 C：Media 上傳功能（⭐ R-002 重點驗證）

---

#### TC-M15-017: 媒體上傳 - 實際 S3/MinIO 寫入 ⭐

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-017 |
| **測試類型** | API Functional + Storage |
| **優先級** | P1 (Critical) |
| **測試 API** | POST /v2/media/upload |
| **風險** | R-002 - 需驗證實際寫入 MinIO |

⚠️ **重要發現 🔴**: 根據程式碼分析，現有 `/v2/media/upload` endpoint 使用的是 `String filePath` 版本（路徑-based），**不會實際上傳到 S3/MinIO**。真正的 S3 上傳需要使用 `MultipartFile` 版本。

**現有問題**:
- Controller (PostController.java:337) 使用 `uploadMedia(UUID, UUID, String, String, Long, String, String)` - 路徑版本
- 這個方法只會寫入資料庫記錄，不會真的上傳到 S3/MinIO
- 實際 S3 上傳的方法是 `uploadMedia(UUID, UUID, MultipartFile)` - MediaService.java:59

**⚠️ 前置條件**: MinIO 服務必須已啟動

**測試步驟**:

```bash
# Step 1: 確認 MinIO 運行中
mc ls local/media

# Step 2: 建立測試圖片（如果沒有）
echo "test image content" > /tmp/test-image.jpg

# Step 3: 執行媒體上傳（路徑版本 - 不會真的上傳到 S3）
curl -X POST http://localhost:8080/v2/media/upload \
  -H "Authorization: Bearer ${TOKEN}" \
  -F "fileName=test-image.jpg" \
  -F "originalName=test-image.jpg" \
  -F "fileSize=102400" \
  -F "mimeType=image/jpeg" \
  -F "filePath=/tmp/test-image.jpg"

# Step 4: 檢查 Response
# 注意：此版本是路徑-based，不會真的寫入 S3
```

**預期結果（路徑版本）**:
- HTTP Status: `201 Created`
- 回傳 mediaId 和 URL
- **但檔案不會實際寫入 MinIO/S3**

**🔴 關鍵驗證點（R-002）**:

```bash
# 驗證是否真的寫入 MinIO
mc ls local/media/

# 如果看到檔案存在，表示真正的 S3 上傳有運作
# 如果沒有看到，表示目前的 endpoint 不會寫入 S3
```

**⚠️ 發現**: 目前 `/v2/media/upload` endpoint (PostController.java:337) 使用的是 `String filePath` 版本，不會實際上傳到 S3。

**如果要測試真正的 S3 上傳**，需要：
1. Dev 修改 Controller，新增 MultipartFile 版本的上傳 endpoint
2. 或使用 Spring 的 `@RequestParam("file") MultipartFile file` 方式

**MultipartFile 上傳測試（需 Dev 修改後）**:

```bash
# 準備一個真實圖片
dd if=/dev/urandom of=/tmp/real-test.jpg bs=1024 count=100

# 使用 MultipartFile 上傳
curl -X POST http://localhost:8080/v2/media/upload/multipart \
  -H "Authorization: Bearer ${TOKEN}" \
  -F "file=@/tmp/real-test.jpg"

# 驗證是否寫入 MinIO
mc ls local/media/
mc stat local/media/real-test.jpg
```

---

#### TC-M15-018: 媒體上傳 - 檔案類型驗證

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-018 |
| **測試類型** | API Validation |
| **優先級** | P2 (High) |
| **測試 API** | POST /v2/media/upload |

**測試步驟**:

```bash
# 建立一個非圖片檔案
echo "这不是图片" > /tmp/test.txt

curl -X POST http://localhost:8080/v2/media/upload \
  -H "Authorization: Bearer ${TOKEN}" \
  -F "fileName=test.txt" \
  -F "originalName=test.txt" \
  -F "fileSize=100" \
  -F "mimeType=text/plain" \
  -F "filePath=/tmp/test.txt"
```

**預期結果**:
- HTTP Status: `400 Bad Request`
- Error Code: `E-9001`

---

#### TC-M15-019: 列出媒體

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-019 |
| **測試類型** | API Functional |
| **優先級** | P2 (High) |
| **測試 API** | GET /v2/tenants/{tenantId}/media |

**測試步驟**:

```bash
curl -X GET http://localhost:8080/v2/tenants/a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11/media \
  -H "Authorization: Bearer ${TOKEN}"
```

**預期結果**:
- HTTP Status: `200 OK`
- 回傳 media list（可能是空陣列如果還沒上傳）

---

#### TC-M15-020: 刪除媒體

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-020 |
| **測試類型** | API Functional |
| **優先級** | P2 (High) |
| **測試 API** | DELETE /v2/media/{mediaId} |

**前置條件**: 有一個已上傳的 mediaId（從 TC-M15-017）

**測試步驟**:

```bash
MEDIA_ID="media-id-from-tc-m15-017"

curl -X DELETE http://localhost:8080/v2/media/${MEDIA_ID} \
  -H "Authorization: Bearer ${TOKEN}"
```

**預期結果**:
- HTTP Status: `204 No Content`

---

### 模組 D：Listing Card 功能

---

#### TC-M15-021: 取得商品卡片（PRODUCT）

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-021 |
| **測試類型** | Public API |
| **優先級** | P1 (Critical) |
| **測試 API** | GET /v2/listings/{listingId}/card |

**前置條件**: Listing ID `c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13` 存在且類型為 PRODUCT

**測試步驟**:

```bash
LISTING_ID="c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13"

curl -X GET http://localhost:8080/v2/listings/${LISTING_ID}/card
```

**預期結果**:
- HTTP Status: `200 OK`
- 回傳 listing card 包含：
  - id, title, description
  - price, currency
  - imageUrl
  - availability（庫存狀態）
  - type: "PRODUCT"

---

#### TC-M15-022: 取得房型卡片（ROOM）

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-022 |
| **測試類型** | Public API |
| **優先級** | P1 (Critical) |
| **測試 API** | GET /v2/listings/{listingId}/card |

**前置條件**: 有一個 ROOM 類型的 Listing

**測試步驟**:

```bash
# 需要先確認有一個 ROOM 類型的 Listing ID
ROOM_LISTING_ID="d0eebc99-9c0b-4ef8-bb6d-6bb9bd380a14"  # 如果存在的話

curl -X GET http://localhost:8080/v2/listings/${ROOM_LISTING_ID}/card
```

**預期結果**:
- HTTP Status: `200 OK`
- 回傳 room card 包含：
  - type: "ROOM"
  - 可能包含房型特定欄位（capacity, bedType 等）

---

#### TC-M15-023: 卡片可用性驗證

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-023 |
| **測試類型** | API Validation |
| **優先級** | P2 (High) |
| **測試 API** | GET /v2/listings/{listingId}/card |

**測試步驟**:

```bash
LISTING_ID="c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13"

curl -X GET http://localhost:8080/v2/listings/${LISTING_ID}/card
```

**預期結果**:
- availability 欄位應正確反映：
  - ACTIVE: available: true
  - INACTIVE/MAINTENANCE: available: false

---

#### TC-M15-024: 取得無效 Listing

| 項目 | 內容 |
|------|------|
| **測試 ID** | TC-M15-024 |
| **測試類型** | Public API Validation |
| **優先級** | P2 (High) |
| **測試 API** | GET /v2/listings/{listingId}/card |

**測試步驟**:

```bash
INVALID_ID="00000000-0000-0000-0000-000000000000"

curl -X GET http://localhost:8080/v2/listings/${INVALID_ID}/card
```

**預期結果**:
- HTTP Status: `404 Not Found`
- Error Code: `E-3000`

---

## 4. 前端操作流程測試（待 Sprint 9 FE 完成）

### 4.1 FE-M15-001: 建立完整貼文流程

| 步驟 | 操作 | Frontend URL | 預期結果 |
|------|------|-------------|----------|
| 1 | 登入 CMS | http://localhost:3000/login | 登入成功，導向 Dashboard |
| 2 | 點擊「新增貼文」 | http://localhost:3000/cms/posts/new | 開啟貼文編輯器 |
| 3 | 填寫標題 | - | 標題欄位可輸入 |
| 4 | 填寫內容（含 Embed） | - | 內容含 `{{embed:listing:...}}` |
| 5 | 點擊「發布」 | - | 貼文發布成功 |
| 6 | 前往前台 | http://localhost:3000/blog | 看到新發布的貼文 |

### 4.2 FE-M15-002: 編輯已發布貼文

| 步驟 | 操作 | Frontend URL | 預期結果 |
|------|------|-------------|----------|
| 1 | 進入 CMS Dashboard | http://localhost:3000/cms | 看到已發布貼文列表 |
| 2 | 選擇已發布貼文 | - | 開啟編輯頁面 |
| 3 | 修改標題/內容 | - | 保存成功 |
| 4 | 前往前台確認 | http://localhost:3000/blog/{slug} | 顯示更新內容 |

### 4.3 FE-M15-003: 刪除已發布貼文

| 步驟 | 操作 | Frontend URL | 預期結果 |
|------|------|-------------|----------|
| 1 | 進入 CMS Dashboard | http://localhost:3000/cms | 看到已發布貼文列表 |
| 2 | 選擇已發布貼文 | - | 開啟編輯頁面 |
| 3 | 點擊「刪除」 | - | 確認對話框 |
| 4 | 確認刪除 | - | 先自動下架再刪除 |
| 5 | 前往前台確認 | http://localhost:3000/blog/{slug} | 404 Not Found |

### 4.4 FE-M15-004: 媒體上傳流程

| 步驟 | 操作 | Frontend URL | 預期結果 |
|------|------|-------------|----------|
| 1 | 進入媒體庫 | http://localhost:3000/cms/media | 顯示已上傳媒體列表 |
| 2 | 點擊「上傳」 | - | 開啟檔案選擇器 |
| 3 | 選擇圖片檔案 | - | 顯示上傳進度 |
| 4 | 上傳完成 | - | 媒體出現在列表 |
| 5 | 選擇圖片作為封面 | - | 圖片設為 featuredImageUrl |

### 4.5 FE-M15-005: 前台瀏覽與嵌入點擊

| 步驟 | 操作 | Frontend URL | 預期結果 |
|------|------|-------------|----------|
| 1 | 前往前台首頁 | http://localhost:3000/blog | 看到已發布貼文列表 |
| 2 | 點擊貼文 | http://localhost:3000/blog/{slug} | 顯示貼文內容 |
| 3 | 看到嵌入商品卡 | - | 商品卡正確顯示 |
| 4 | 點擊商品卡 | - | 跳轉到商品詳情頁 |

---

## 5. R-002 風險驗證特別步驟

### 5.1 MinIO 服務驗證

```bash
# Step 1: 檢查 MinIO 服务狀態
mc ls local/media

# 如果 mc 命令不可用，嘗試：
curl -I http://localhost:9000/minio/health

# Step 2: 如果服務未運行，啟動 MinIO
# Docker 啟動方式：
docker run -d \
  --name minio \
  -p 9000:9000 \
  -p 9001:9001 \
  -e "MINIO_ROOT_USER=minioadmin" \
  -e "MINIO_ROOT_PASSWORD=minioadmin" \
  minio/minio server /data

# Step 3: 設定 alias（如果需要）
mc alias set local http://localhost:9000 minioadmin minioadmin

# Step 4: 創建 bucket
mc mb local/media

# Step 5: 設定 public policy（可選）
mc anonymous set download local/media
```

### 5.2 實際上傳測試（R-002 關鍵驗證）

**問題**: 根據程式碼分析，現有 E2E 測試使用的是路徑-based `uploadMedia` 方法，不會真的上傳到 S3。

**驗證方法**:

```bash
# 準備一個真實圖片
dd if=/dev/urandom of=/tmp/real-test.jpg bs=1024 count=100

# 使用 curl 上傳（需確認 API 接受 MultipartFile）
curl -X POST http://localhost:8080/v2/media/upload \
  -H "Authorization: Bearer ${TOKEN}" \
  -F "file=@/tmp/real-test.jpg" \
  -F "fileName=real-test.jpg" \
  -F "originalName=real-test.jpg" \
  -F "fileSize=102400" \
  -F "mimeType=image/jpeg"

# 驗證是否寫入 MinIO
mc ls local/media/
mc stat local/media/real-test.jpg
```

---

## 6. 測試資料準備 SQL

### 6.1 測試用 SQL 指令

```sql
-- 清理並創建測試資料庫
DROP DATABASE IF EXISTS nextkey_uat;
CREATE DATABASE nextkey_uat;
\c nextkey_uat;

-- 創建 Tenant
INSERT INTO tenants (id, name, slug, status, created_at, updated_at)
VALUES ('a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', 'Test Tenant', 'test-tenant', 'ACTIVE', NOW(), NOW());

-- 創建 User（密碼需 bcrypt hash）
INSERT INTO users (id, email, password_hash, full_name, role, tenant_id, status, created_at, updated_at)
VALUES (
  'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12',
  'admin@nextkey.com',
  '$2a$10$YourHashHere',  -- 替換為真實 hash
  'Admin User',
  'STORE_OWNER',
  'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
  'ACTIVE',
  NOW(),
  NOW()
);

-- 創建 Listing (PRODUCT)
INSERT INTO listings (id, tenant_id, title, listing_type, status, base_price, created_at, updated_at)
VALUES (
  'c0eebc99-9c0b-4ef8-bb6d-6bb9bd380a13',
  'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
  'Test Product',
  'PRODUCT',
  'ACTIVE',
  999.00,
  NOW(),
  NOW()
);

-- 創建 Listing (ROOM)
INSERT INTO listings (id, tenant_id, title, listing_type, status, base_price, created_at, updated_at)
VALUES (
  'd0eebc99-9c0b-4ef8-bb6d-6bb9bd380a14',
  'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
  'Test Room',
  'ROOM',
  'ACTIVE',
  2999.00,
  NOW(),
  NOW()
);

-- 創建 Category
INSERT INTO post_categories (id, tenant_id, name, slug, created_at, updated_at)
VALUES (
  'e0eebc99-9c0b-4ef8-bb6d-6bb9bd380a15',
  'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11',
  'General',
  'general',
  NOW(),
  NOW()
);

-- 其他 Tenant（用於跨 tenant 測試）
INSERT INTO tenants (id, name, slug, status, created_at, updated_at)
VALUES ('b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12', 'Other Tenant', 'other-tenant', 'ACTIVE', NOW(), NOW());

INSERT INTO users (id, email, password_hash, full_name, role, tenant_id, status, created_at, updated_at)
VALUES (
  'f0eebc99-9c0b-4ef8-bb6d-6bb9bd380a16',
  'other@tenant.com',
  '$2a$10$YourHashHere',
  'Other User',
  'STORE_OWNER',
  'b0eebc99-9c0b-4ef8-bb6d-6bb9bd380a12',
  'ACTIVE',
  NOW(),
  NOW()
);
```

---

## 7. 測試執行追蹤表

### 7.1 執行摘要

| 項目 | 數值 |
|------|------|
| 測試期間 | 2026-05-05 ~ (待填) |
| 測試案例總數 | 24 |
| 通過 | (待填) |
| 失敗 | (待填) |
| 阻塞 | (待填) |

### 7.2 詳細結果追蹤

| TC ID | 執行時間 | 執行者 | 結果 | 備註 |
|-------|----------|--------|------|------|
| TC-M15-001 | | | ⏳ | |
| TC-M15-002 | | | ⏳ | |
| TC-M15-003 | | | ⏳ | |
| TC-M15-004 | | | ⏳ | |
| TC-M15-005 | | | ⏳ | |
| TC-M15-006 | | | ⏳ | |
| TC-M15-007 | | | ⏳ | |
| TC-M15-008 | | | ⏳ | |
| TC-M15-009 | | | ⏳ | |
| TC-M15-010 | | | ⏳ | |
| TC-M15-011 | | | ⏳ | |
| TC-M15-012 | | | ⏳ | |
| TC-M15-013 | | | ⏳ | |
| TC-M15-014 | | | ⏳ | |
| TC-M15-015 | | | ⏳ | |
| TC-M15-016 | | | ⏳ | |
| TC-M15-017 | | | ⏳ | |
| TC-M15-018 | | | ⏳ | |
| TC-M15-019 | | | ⏳ | |
| TC-M15-020 | | | ⏳ | |
| TC-M15-021 | | | ⏳ | |
| TC-M15-022 | | | ⏳ | |
| TC-M15-023 | | | ⏳ | |
| TC-M15-024 | | | ⏳ | |

---

## 8. 缺陷記錄

| 缺陷 ID | 嚴重度 | TC ID | 描述 | 狀態 | 發現時間 |
|---------|--------|-------|------|------|----------|
| (待填) | | | | | |

---

## 9. 驗收標準達成狀態

| 標準 | 目標 | 實際 | 狀態 |
|------|------|------|------|
| 功能測試 | 24/24 通過 | X/24 | ⏳ |
| R-001 已緩解 | 是 | 待確認 | ⏳ |
| R-002 已緩解 | 是 | 待確認 | ⏳ |
| R-003 已緩解 | 是 | 待 Sprint 9 | ⏳ |
| 無 High 缺陷 | High = 0 | 待確認 | ⏳ |

---

## 10. 聯絡窗口

| 角色 | 負責人 | 聯絡方式 |
|------|--------|----------|
| Backend Dev | David | (待填) |
| QA Lead | Quincy | (待填) |
| FE Dev | (Sprint 9) | (待填) |

---

**文件狀態**: 🔴 **待 QA Team 執行**
**最後更新**: 2026-05-05