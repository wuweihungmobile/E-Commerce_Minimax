# M15 CMS Frontend 實作計劃

> **版本**: v1.0
> **日期**: 2026-05-06
> **依據**: API Contract v1.1 + Sprint 9 Tasks

---

## 1. 技術棧 Overview

| 項目 | 技術 |
|------|------|
| Framework | Next.js 16.2.2 (App Router) |
| UI Library | Radix UI + Tailwind CSS |
| HTTP Client | Axios (已設定攔截器) |
| 狀態管理 | React useState/useEffect |
| 圖標 | Lucide React |
| 測試 | Playwright |

### 現有結構

```
frontend/src/
├── app/
│   ├── dashboard/
│   │   └── posts/page.tsx    # 現有貼文列表頁 (需遷移到 /cms)
│   └── ...
├── components/
│   ├── ui/                    # 通用 UI 組件 (button, input, card 等)
│   └── ...                    # 業務組件
├── services/
│   └── cms.ts                 # CMS API 服務 (已實作)
└── lib/
    ├── axios.ts               # Axios 實例 (已設定 JWT 攔截器)
    └── api.ts                 # API 端點配置
```

---

## 2. 任務清單與實作順序

### Task-M15-102: 貼文列表頁 (`/cms`) ⭐ 先做

**目標**: 建立 CMS 儀表板頁面，展示貼文列表

**檔案位置**: `frontend/src/app/cms/page.tsx`

**實作細節**:

```typescript
// 需要的功能：
1. 頁面標題 + 新建按鈕
2. 狀態篩選器 (全部/草稿/已發布/已歸檔)
3. 貼文列表表格
   - 標題 (可點擊進入編輯)
   - 狀態 Badge
   - 分類
   - 瀏覽數
   - 發布時間
   - 操作 (發布/下架/編輯/刪除)
4. 分頁 (目前 API 有 totalCount, page, size)
```

**API 使用**:
- `GET /v2/dashboard/posts` - 取得貼文列表
- `POST /v2/dashboard/posts/{postId}/publish` - 發布
- `DELETE /v2/dashboard/posts/{postId}/publish` - 下架
- `DELETE /v2/dashboard/posts/{postId}` - 刪除

**現有檔案復用**:
- `frontend/src/app/dashboard/posts/page.tsx` → 遷移並改寫為 `/cms`

---

### Task-M15-103: 貼文編輯頁 (`/cms/posts/new`, `/cms/posts/:id/edit`)

**目標**: 建立新建/編輯貼文頁面

**檔案位置**:
- `frontend/src/app/cms/posts/new/page.tsx` - 新建
- `frontend/src/app/cms/posts/[id]/edit/page.tsx` - 編輯

**實作細節**:

```typescript
// 需要的功能：
1. 標題輸入 (必填)
2. 內容編輯器 (富文字或 Markdown)
3. 分類選擇 (下拉選單)
4. 標籤輸入 (多選)
5. 精選圖片選擇 (從媒體庫或上傳)
6. 嵌入商品卡解析
   - 語法: {{embed:listing:<listingId>}}
   - 解析並顯示預覽卡片
7. 儲存/發布/下架按鈕
```

**API 使用**:
- `POST /v2/dashboard/posts` - 建立
- `GET /v2/dashboard/posts/{postId}` - 取得詳情
- `PUT /v2/dashboard/posts/{postId}` - 更新
- `GET /v2/dashboard/post-categories` - 取得分類列表

**現有參考**:
- `frontend/src/app/dashboard/posts/page.tsx` 的 UI 風格

---

### Task-M15-104: 嵌入卡片預覽

**目標**: 在編輯器內即時預覽嵌入的商品/房型卡片

**實作方式**:

```typescript
// 兩種方案：

// 方案 A: 即時解析
// 在 content 變化時，正規表達式解析 {{embed:listing:<id>}}
// 呼叫 GET /v2/listings/{listingId}/card 取得卡片資訊
// 渲染為嵌入式卡片 UI

// 方案 B: 延遲解析 (Debounce)
// 使用 useEffect + debounce 避免頻繁 API 呼叫

// 預覽卡片 Component
interface EmbedCardProps {
  listingId: string;
  card: ListingCardResponse;
}
```

**API 使用**:
- `GET /v2/listings/{listingId}/card` - 取得商品卡資訊

**需要新增 Component**:
- `frontend/src/components/cms/EmbedCard.tsx`

---

### Task-M15-105: 媒體庫頁面 (`/cms/media`)

**目標**: 管理上傳的媒體檔案

**檔案位置**: `frontend/src/app/cms/media/page.tsx`

**實作細節**:

```typescript
// 需要的功能：
1. 頁面標題 + 上傳按鈕
2. 篩選器 (全部/圖片/影片/文件)
3. 媒體檔案網格/列表顯示
   - 縮圖/圖示
   - 檔案名稱
   - 檔案大小
   - 上傳時間
4. 上傳功能
   - MultipartFile 上傳
   - 拖放上傳支援
5. 刪除功能 (需確認未被使用)
6. 分頁
```

**API 使用**:
- `POST /v2/dashboard/media/upload-multipart` - 上傳 (Multipart)
- `GET /v2/dashboard/media` - 取得列表
- `DELETE /v2/dashboard/media/{mediaId}` - 刪除

**需要修改 cms.ts Service**:
```typescript
// 新增 Multipart 上傳方法
export async function uploadMediaMultipart(file: File): Promise<MediaResponse> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await apiClient.post('/v2/dashboard/media/upload-multipart', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data.data;
}
```

---

### Task-M15-106: 前台部落格 (`/blog`, `/blog/:slug`)

**目標**: 公開的部落格頁面

**檔案位置**:
- `frontend/src/app/blog/page.tsx` - 部落格列表
- `frontend/src/app/blog/[slug]/page.tsx` - 貼文詳情

**實作細節**:

```typescript
// /blog/page.tsx
// 需要的功能：
1. 店鋪選擇 (從 localStorage tenantId)
// 2. 部落格文章列表
// 3. 分頁

// /blog/[slug]/page.tsx
// 需要的功能：
1. 取得 URL slug 參數
2. 呼叫 GET /v2/posts/{slug}?tenantId={tenantId}
3. 解析內容中的嵌入卡片並渲染
4. 顯示作者、發布時間、瀏覽數
```

**API 使用**:
- `GET /v2/posts?tenantId={tenantId}` - 已發布列表
- `GET /v2/posts/{slug}?tenantId={tenantId}` - 依 slug 取得

---

## 3. 路由規劃

| 路由 | 檔案 | 任務 |
|------|------|------|
| `/cms` | `app/cms/page.tsx` | Task-M15-102 |
| `/cms/posts/new` | `app/cms/posts/new/page.tsx` | Task-M15-103 |
| `/cms/posts/:id/edit` | `app/cms/posts/[id]/edit/page.tsx` | Task-M15-103 |
| `/cms/media` | `app/cms/media/page.tsx` | Task-M15-105 |
| `/blog` | `app/blog/page.tsx` | Task-M15-106 |
| `/blog/:slug` | `app/blog/[slug]/page.tsx` | Task-M15-106 |

**注意**: 需建立 `/cms` 資料夾並遷移現有 `/dashboard/posts` 的功能

---

## 4. 需要新增的目錄結構

```
frontend/src/
├── app/
│   ├── cms/
│   │   ├── page.tsx                    # 列表頁 (Task-M15-102)
│   │   ├── posts/
│   │   │   ├── new/
│   │   │   │   └── page.tsx            # 新建頁 (Task-M15-103)
│   │   │   └── [id]/
│   │   │       └── edit/
│   │   │           └── page.tsx       # 編輯頁 (Task-M15-103)
│   │   └── media/
│   │       └── page.tsx               # 媒體庫 (Task-M15-105)
│   └── blog/
│       ├── page.tsx                   # 列表 (Task-M15-106)
│       └── [slug]/
│           └── page.tsx              # 詳情 (Task-M15-106)
├── components/
│   └── cms/
│       ├── EmbedCard.tsx              # 嵌入卡片 (Task-M15-104)
│       ├── PostEditor.tsx             # 編輯器
│       └── MediaUploader.tsx          # 上傳組件
└── services/
    └── cms.ts                         # 需更新
```

---

## 5. 共用 UI 組件 (Radix UI)

現有 `frontend/src/components/ui/` 已有：
- `button.tsx`
- `input.tsx`
- `card.tsx`
- `badge.tsx`
- `label.tsx`
- `select.tsx`
- `alert.tsx`
- `switch.tsx`
- `skeleton.tsx`

**建議新增用於 CMS**:
- `frontend/src/components/ui/dialog.tsx` (編輯視窗)
- `frontend/src/components/ui/dropdown-menu.tsx` (操作選單)

---

## 6. 實作優先順序建議

### Phase 1: 基礎建設 (Day 1-2)
1. 建立 `/cms/*` 路由結構
2. Task-M15-102: 遷移並實作貼文列表頁
3. Task-M15-105: 媒體庫頁面

### Phase 2: 核心功能 (Day 3-4)
4. Task-M15-103: 編輯/新建頁
5. Task-M15-104: 嵌入卡片預覽

### Phase 3: 前台整合 (Day 5)
6. Task-M15-106: 前台部落格

---

## 7. 測試覆蓋目標 (Task-M15-108)

**FE-BE 整合測試** (目標 17/17 通過):

| ID | 測試項目 | API Endpoint |
|----|---------|--------------|
| IT-M15-101 | 建立草稿貼文 | POST /v2/dashboard/posts |
| IT-M15-102 | 取得貼文列表 | GET /v2/dashboard/posts |
| IT-M15-103 | 取得貼文詳情 | GET /v2/dashboard/posts/{id} |
| IT-M15-104 | 更新貼文 | PUT /v2/dashboard/posts/{id} |
| IT-M15-105 | 刪除草稿貼文 | DELETE /v2/dashboard/posts/{id} |
| IT-M15-106 | 發布草稿 | POST /v2/dashboard/posts/{id}/publish |
| IT-M15-107 | 下架已發布 | DELETE /v2/dashboard/posts/{id}/publish |
| IT-M15-108 | 建立分類 | POST /v2/dashboard/post-categories |
| IT-M15-109 | 取得分類列表 | GET /v2/dashboard/post-categories |
| IT-M15-110 | 更新分類 | PUT /v2/dashboard/post-categories/{id} |
| IT-M15-111 | 刪除分類 | DELETE /v2/dashboard/post-categories/{id} |
| IT-M15-112 | 上傳媒體 (Multipart) | POST /v2/dashboard/media/upload-multipart |
| IT-M15-113 | 取得媒體列表 | GET /v2/dashboard/media |
| IT-M15-114 | 刪除未引用媒體 | DELETE /v2/dashboard/media/{id} |
| IT-M15-115 | 取得已發布列表 | GET /v2/posts |
| IT-M15-116 | 依 slug 取得已發布 | GET /v2/posts/{slug} |
| IT-M15-117 | 取得商品卡 | GET /v2/listings/{id}/card |

---

## 8. E2E 測試覆蓋目標 (Task-M15-109)

**Playwright E2E** (目標 5/5 通過):

| ID | 測試項目 |
|----|---------|
| E2E-M15-001 | 建立並發布貼文流程 |
| E2E-M15-002 | 編輯並更新貼文流程 |
| E2E-M15-003 | 媒體上傳流程 |
| E2E-M15-004 | 前台部落格瀏覽流程 |
| E2E-M15-005 | 嵌入商品卡解析流程 |

---

**文件狀態**: 📋 規劃完成
**下次更新**: FE Dev 實作時