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
 * 前台：取得分類列表（公開，僅回傳啟用中分類，DEF-358(c)）
 */
export async function getPublicCategories(tenantId: string, options?: RequestOptions): Promise<CategoryListResponse> {
  const response = await apiClient.get('/v2/posts/categories', { params: { tenantId }, ...options });
  return response.data.data;
}

export async function getPublishedPostBySlug(slug: string, tenantId: string, options?: RequestOptions): Promise<PostResponse> {
  const response = await apiClient.get(`/v2/posts/${slug}`, { params: { tenantId }, ...options });
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