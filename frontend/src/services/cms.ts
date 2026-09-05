/**
 * M15 CMS API Service
 * 貼文、分類、媒體庫 API 服務
 */

import apiClient from '@/lib/axios';

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
  currentPrice: number;
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

export interface MediaResponse {
  id: string;
  tenantId: string;
  uploaderId: string;
  uploaderName: string;
  fileName: string;
  originalName: string;
  filePath: string;
  fileSize: number;
  formattedFileSize: string;
  mimeType: string;
  fileType: 'IMAGE' | 'VIDEO' | 'DOCUMENT';
  width?: number;
  height?: number;
  durationSeconds?: number;
  createdAt: string;
}

export interface MediaListResponse {
  // 後端 M15Dto.MediaListResponse 的欄位名是 items（非 media）。原本宣告為 media 導致
  // 讀到 undefined，媒體庫從未列出任何檔案。Sprint 128，DEF-074。
  items: MediaResponse[];
  totalCount: number;
  page: number;
  size: number;
  totalPages: number;
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
}): Promise<PostListResponse> {
  const response = await apiClient.get('/v2/posts', { params: { tenantId, ...params } });
  return response.data.data;
}

export async function getPublishedPostBySlug(slug: string, tenantId: string): Promise<PostResponse> {
  const response = await apiClient.get(`/v2/posts/${slug}`, { params: { tenantId } });
  return response.data.data;
}

// Listing Card API

export async function getListingCard(listingId: string): Promise<ListingCardResponse> {
  const response = await apiClient.get(`/v2/listings/${listingId}/card`);
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

// Media APIs

export async function getMediaList(params?: {
  page?: number;
  size?: number;
  fileType?: string;
}): Promise<MediaListResponse> {
  const response = await apiClient.get('/v2/dashboard/media', { params });
  return response.data.data;
}

export async function uploadMedia(data: FormData): Promise<{ mediaId: string }> {
  const response = await apiClient.post('/v2/media/upload', data);
  return response.data.data;
}

export async function uploadMediaMultipart(file: File): Promise<MediaResponse> {
  const formData = new FormData();
  formData.append('file', file);
  const response = await apiClient.post('/v2/dashboard/media/upload-multipart', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data.data;
}

export async function deleteMedia(mediaId: string): Promise<void> {
  await apiClient.delete(`/v2/dashboard/media/${mediaId}`);
}