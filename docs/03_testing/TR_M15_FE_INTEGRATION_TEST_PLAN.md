# M15 CMS FE-BE 整合測試計劃

> **版本**: v1.0
> **日期**: 2026-05-06
> **任務**: Task-M15-108
> **目標**: 17/17 測試通過

---

## 1. 測試環境需求

| 項目 | 值 |
|------|-----|
| Backend URL | `http://localhost:8080/api` |
| Frontend URL | `http://localhost:3000` |
| 測試資料 | 使用現有 E2E test data setup |
| 認證 | JWT Bearer Token |

---

## 2. 測試覆蓋矩陣

### 2.1 Post Management (貼文管理)

| ID | 測試項目 | API Endpoint | Method | 前置條件 | 預期結果 |
|----|---------|--------------|--------|----------|----------|
| IT-M15-101 | 建立草稿貼文 | `/v2/dashboard/posts` | POST | 已登入 | 201 Created, 回傳 PostResponse |
| IT-M15-102 | 取得貼文列表 | `/v2/dashboard/posts` | GET | 已登入 | 200 OK, 回傳分頁列表 |
| IT-M15-103 | 取得貼文詳情 | `/v2/dashboard/posts/{id}` | GET | 有草稿貼文 | 200 OK, 回傳 PostResponse |
| IT-M15-104 | 更新貼文 | `/v2/dashboard/posts/{id}` | PUT | 有草稿貼文 | 200 OK, 回傳更新後 PostResponse |
| IT-M15-105 | 刪除草稿貼文 | `/v2/dashboard/posts/{id}` | DELETE | 有草稿貼文 | 200 OK |
| IT-M15-106 | 發布草稿 | `/v2/dashboard/posts/{id}/publish` | POST | 有草稿貼文 | 200 OK, status: PUBLISHED |
| IT-M15-107 | 下架已發布 | `/v2/dashboard/posts/{id}/publish` | DELETE | 有已發布貼文 | 200 OK, status: DRAFT |

### 2.2 Category Management (分類管理)

| ID | 測試項目 | API Endpoint | Method | 前置條件 | 預期結果 |
|----|---------|--------------|--------|----------|----------|
| IT-M15-108 | 建立分類 | `/v2/dashboard/post-categories` | POST | 已登入 | 201 Created |
| IT-M15-109 | 取得分類列表 | `/v2/dashboard/post-categories` | GET | 有分類資料 | 200 OK, 回傳 categories array |
| IT-M15-110 | 更新分類 | `/v2/dashboard/post-categories/{id}` | PUT | 有分類資料 | 200 OK |
| IT-M15-111 | 刪除分類 | `/v2/dashboard/post-categories/{id}` | DELETE | 分類無關聯貼文 | 200 OK |

### 2.3 Media Library (媒體庫)

| ID | 測試項目 | API Endpoint | Method | 前置條件 | 預期結果 |
|----|---------|--------------|--------|----------|----------|
| IT-M15-112 | 上傳媒體 (Multipart) | `/v2/dashboard/media/upload-multipart` | POST | 已登入 | 200 OK, 回傳 MediaResponse |
| IT-M15-113 | 取得媒體列表 | `/v2/dashboard/media` | GET | 有上傳資料 | 200 OK, 回傳分頁 media array |
| IT-M15-114 | 刪除未引用媒體 | `/v2/dashboard/media/{id}` | DELETE | 有未引用媒體 | 200 OK |

### 2.4 Public Post (公開貼文)

| ID | 測試項目 | API Endpoint | Method | 前置條件 | 預期結果 |
|----|---------|--------------|--------|----------|----------|
| IT-M15-115 | 取得已發布列表 | `/v2/posts` | GET | 有已發布貼文 | 200 OK, 回傳 posts array |
| IT-M15-116 | 依 slug 取得已發布 | `/v2/posts/{slug}` | GET | 有已發布貼文 | 200 OK, 回傳 PostResponse |

### 2.5 Listing Card (嵌入卡片)

| ID | 測試項目 | API Endpoint | Method | 前置條件 | 預期結果 |
|----|---------|--------------|--------|----------|----------|
| IT-M15-117 | 取得商品卡 | `/v2/listings/{id}/card` | GET | 有商品/房型資料 | 200 OK, 回傳 ListingCardResponse |

---

## 3. 測試資料準備

### 3.1 必要測試資料

```typescript
// Test Data Setup
const testData = {
  storeOwner: {
    email: `cms_test_${Date.now()}@test.com`,
    password: 'Test123!',
    role: 'STORE_OWNER'
  },
  tenant: {
    name: 'CMS Test Tenant',
    status: 'ACTIVE'
  },
  post: {
    title: 'Test Post',
    content: 'Test content with {{embed:listing:<listingId>}}',
    status: 'DRAFT'
  },
  category: {
    name: 'Test Category',
    slug: 'test-category'
  }
};
```

### 3.2 隔離策略

- 每個測試使用 unique email (`${timestamp}@test.com`)
- 測試後清理建立的資料
- 使用 transaction rollback (如果支援)

---

## 4. 測試執行流程

### 4.1 前置條件
1. 啟動 Backend (`cd backend && mvn spring-boot:run`)
2. 啟動 MinIO 服務
3. 初始化測試資料 (flyway migrate)

### 4.2 執行順序

```
1. IT-M15-101: 建立草稿貼文 (取得 postId)
2. IT-M15-102: 取得貼文列表 (驗證 postId 存在)
3. IT-M15-103: 取得貼文詳情 (使用 postId)
4. IT-M15-104: 更新貼文 (使用 postId)
5. IT-M15-106: 發布草稿 (使用 postId)
6. IT-M15-107: 下架已發布 (使用 postId)
7. IT-M15-105: 刪除草稿 (使用 postId)

8. IT-M15-108: 建立分類
9. IT-M15-109: 取得分類列表
10. IT-M15-110: 更新分類
11. IT-M15-111: 刪除分類

12. IT-M15-112: 上傳媒體
13. IT-M15-113: 取得媒體列表
14. IT-M15-114: 刪除未引用媒體

15. IT-M15-115: 取得已發布列表
16. IT-M15-116: 依 slug 取得已發布

17. IT-M15-117: 取得商品卡
```

---

## 5. Playwright E2E 測試案例

### 5.1 E2E-M15-001: 建立並發布貼文流程

**Steps**:
1. 登入系統
2. 前往 `/cms`
3. 點擊「新建貼文」
4. 輸入標題、內容
5. 點擊「發布」
6. 驗證出現在列表且狀態為「已發布」

**Expected**:
- 成功發布，貼文出現在已發布列表

### 5.2 E2E-M15-002: 編輯並更新貼文流程

**Steps**:
1. 登入系統
2. 前往 `/cms`
3. 選擇一篇草稿貼文
4. 點擊「編輯」
5. 修改標題
6. 點擊「儲存變更」
7. 驗證更新後的內容

**Expected**:
- 更新成功，標題已變更

### 5.3 E2E-M15-003: 媒體上傳流程

**Steps**:
1. 登入系統
2. 前往 `/cms/media`
3. 點擊「上傳檔案」
4. 選擇圖片
5. 驗證圖片出現在列表

**Expected**:
- 上傳成功，縮圖正確顯示

### 5.4 E2E-M15-004: 前台部落格瀏覽流程

**Steps**:
1. 確保有已發布貼文
2. 前往 `/blog`
3. 點擊任一文章
4. 驗證文章內容正確顯示

**Expected**:
- 文章正確載入

### 5.5 E2E-M15-005: 嵌入商品卡解析流程

**Steps**:
1. 建立含嵌入語法的貼文: `{{embed:listing:<listingId>}}`
2. 發布該貼文
3. 前往前台部落格查看
4. 驗證嵌入卡片正確顯示

**Expected**:
- 商品卡正確解析並顯示

---

## 6. 成功標準

| 標準 | 目標 | 實際 | 狀態 |
|------|------|------|------|
| IT-M15-101 ~ 117 | 17/17 | - | ⏳ |
| E2E-M15-001 ~ 005 | 5/5 | - | ⏳ |
| High 缺陷數 | 0 | - | ⏳ |

---

## 7. 測試報告格式

```markdown
## Task-M15-108 測試報告

### 執行日期: 2026-05-XX
### 測試人員: QA

### 結果摘要
- IT 測試: X/17 通過
- E2E 測試: X/5 通過
- High 缺陷: X

### 失敗測試 (如有)
| ID | 失敗原因 | 嚴重性 | 修復負責人 |
|----|---------|--------|-----------|
```

---

**文件狀態**: 📋 待執行
**建立日期**: 2026-05-06