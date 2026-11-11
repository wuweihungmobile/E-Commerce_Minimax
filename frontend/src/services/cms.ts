/**
 * M15 CMS API Service
 * 貼文、分類 API 服務（媒體庫見 services/media.ts）
 */

import apiClient, { type RequestOptions } from '@/lib/axios';

// Types
export interface PostEmbedResponse {
  id: string;
  listingId: string;
  listingType: string;
  embedOrder: number;
}

export interface PostResponse {
  id: string;
  tenantId: string;
  tenantName: string;
  // 店鋪的網址代稱（Sprint 336，DEF-483）：文章的公開網址是 /stores/{tenantSlug}/blog/{slug}，見 lib/article-path
  tenantSlug: string;
  authorId: string;
  authorName: string;
  title: string;
  slug: string;
  content: string;
  excerpt: string;
  featuredImageUrl: string;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  categoryId: string;
  categoryName: string;
  tags: string[];
  viewCount: number;
  publishedAt: string;
  createdAt: string;
  updatedAt: string;
  embeds: PostEmbedResponse[];
}

export interface CreatePostRequest {
  title: string;
  content: string;
  categoryId?: string;
  tags?: string[];
  featuredImageUrl?: string;
  autoPublish?: boolean;
}

export interface UpdatePostRequest {
  title?: string;
  content?: string;
  categoryId?: string;
  tags?: string[];
  featuredImageUrl?: string;
}

export interface PostListResponse {
  posts: PostResponse[];
  totalCount: number;
  page: number;
  size: number;
  totalPages: number;
}

/**
 * 跨店鋪公開貼文列表的一筆摘要（Sprint 339，DEF-495，GET /v2/public/posts）。
 * 刻意沒有內文、作者、瀏覽數：這是匿名可讀、橫跨所有店鋪的端點，後端只回卡片需要的欄位。
 * 文章的公開網址是 /stores/{tenantSlug}/blog/{slug}，見 lib/article-path。
 */
export interface PublicPostSummary {
  id: string;
  slug: string;
  title: string;
  /** 貼文自己的摘要；沒有就是內文開頭（後端已去掉商品嵌入語法）；都沒有是空字串 */
  excerpt: string;
  featuredImageUrl: string | null;
  publishedAt: string | null;
  tenantSlug: string;
  tenantName: string;
  categoryId: string | null;
  categoryName: string | null;
}

export interface PublicPostFeed {
  posts: PublicPostSummary[];
  totalCount: number;
  /** 實際使用的頁碼（0-based）與大小：後端會收斂超出範圍的請求值 */
  page: number;
  size: number;
  totalPages: number;
}

export interface CategoryResponse {
  id: string;
  tenantId: string;
  name: string;
  slug: string;
  description: string;
  sortOrder: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateCategoryRequest {
  name: string;
  description?: string;
  sortOrder?: number;
}

export interface UpdateCategoryRequest {
  name?: string;
  description?: string;
  sortOrder?: number;
}

export interface CategoryListResponse {
  categories: CategoryResponse[];
  totalCount: number;
}

export interface ListingCardResponse {
  listingId: string;
  listingType: string;
  title: string;
  coverImageUrl: string;
  basePrice: number;
  currentPrice: number | null; // DEF-321(d)：MAINTENANCE 狀態無定價
  currency: string;
  availability: {
    inStock: boolean;
    availableQty: number;
    available: boolean;
  };
  tenantName: string;
  ctaUrl: string;
  isActive: boolean;
  statusReason: string | null;
}

// Post APIs

export async function getPosts(params?: {
  page?: number;
  size?: number;
  status?: string;
}): Promise<PostListResponse> {
  const response = await apiClient.get('/v2/dashboard/posts', { params });
  return response.data.data;
}

export async function getPost(postId: string): Promise<PostResponse> {
  const response = await apiClient.get(`/v2/dashboard/posts/${postId}`);
  return response.data.data;
}

export async function createPost(data: CreatePostRequest): Promise<PostResponse> {
  const response = await apiClient.post('/v2/dashboard/posts', data);
  return response.data.data;
}

export async function updatePost(postId: string, data: UpdatePostRequest): Promise<PostResponse> {
  const response = await apiClient.put(`/v2/dashboard/posts/${postId}`, data);
  return response.data.data;
}

export async function deletePost(postId: string): Promise<void> {
  await apiClient.delete(`/v2/dashboard/posts/${postId}`);
}

export async function publishPost(postId: string): Promise<PostResponse> {
  const response = await apiClient.post(`/v2/dashboard/posts/${postId}/publish`);
  return response.data.data;
}

export async function unpublishPost(postId: string): Promise<PostResponse> {
  const response = await apiClient.delete(`/v2/dashboard/posts/${postId}/publish`);
  return response.data.data;
}

// Public Post APIs

export async function getPublishedPosts(tenantId: string, params?: {
  page?: number;
  size?: number;
  categoryId?: string;
}, options?: RequestOptions): Promise<PostListResponse> {
  const response = await apiClient.get('/v2/posts', { params: { tenantId, ...params }, ...options });
  return response.data.data;
}

/**
 * 前台：跨店鋪的公開貼文列表（Sprint 339，DEF-495）：所有店鋪（ACTIVE）已發布的貼文，新到舊，匿名可讀、沒有 tenantId。
 * 單店鋪的列表仍用 getPublishedPosts。
 */
export async function getPublicPostFeed(params?: {
  page?: number;
  size?: number;
}, options?: RequestOptions): Promise<PublicPostFeed> {
  const response = await apiClient.get('/v2/public/posts', { params: { ...params }, ...options });
  return response.data.data;
}

/**
 * 前台：取得分類列表（公開，僅回傳啟用中分類，DEF-358(c)）
 */
export async function getPublicCategories(tenantId: string, options?: RequestOptions): Promise<CategoryListResponse> {
  const response = await apiClient.get('/v2/posts/categories', { params: { tenantId }, ...options });
  return response.data.data;
}

/**
 * 依 slug 取得已發布的文章。預設每呼叫一次瀏覽數 +1（後端的語意）；`countView: false` 只讀取不計瀏覽
 * （Sprint 336，DEF-483：舊網址 /blog/{slug} 為了找出文章所屬店鋪而查詢、隨即導向新網址，新網址那一頁才是真正的瀏覽，不能算兩次）。
 */
export async function getPublishedPostBySlug(
  slug: string,
  tenantId: string,
  options?: RequestOptions & { countView?: boolean }
): Promise<PostResponse> {
  const { countView, ...requestOptions } = options ?? {};
  const response = await apiClient.get(`/v2/posts/${slug}`, {
    params: { tenantId, ...(countView === false ? { countView: false } : {}) },
    ...requestOptions,
  });
  return response.data.data;
}

// Listing Card API

export async function getListingCard(listingId: string, options?: RequestOptions): Promise<ListingCardResponse> {
  const response = await apiClient.get(`/v2/listings/${listingId}/card`, options);
  return response.data.data;
}

// Category APIs

export async function getCategories(): Promise<CategoryListResponse> {
  const response = await apiClient.get('/v2/dashboard/post-categories');
  return response.data.data;
}

export async function createCategory(data: CreateCategoryRequest): Promise<CategoryResponse> {
  const response = await apiClient.post('/v2/dashboard/post-categories', data);
  return response.data.data;
}

export async function updateCategory(categoryId: string, data: UpdateCategoryRequest): Promise<CategoryResponse> {
  const response = await apiClient.put(`/v2/dashboard/post-categories/${categoryId}`, data);
  return response.data.data;
}

export async function deleteCategory(categoryId: string): Promise<void> {
  await apiClient.delete(`/v2/dashboard/post-categories/${categoryId}`);
}

// 媒體 API（原本接 /v2/dashboard/media 的 M15 部落格媒體庫）：Sprint 332（DEF-466）隨 /cms/media 頁面一併移除——
// 媒體管理只剩 /dashboard/media 一個介面，走 services/media.ts（M18，/v2/media）。後端 PostController 的
// /v2/dashboard/media/* 五個端點仍在，但沒有前端呼叫者了（見追蹤表）。