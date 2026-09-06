/**
 * M18 媒體中心 API Service
 */

import apiClient from '@/lib/axios';

// Types
export interface MediaAssetDto {
  id: string;
  tenantId: string;
  categoryId: string | null;
  categoryName: string | null;
  fileName: string;
  filePath: string;
  fileSize: number;
  mimeType: string;
  tags: string[];
  usageCount: number;
  altText?: string | null;
  title?: string | null;
  createdAt: string;
  updatedAt: string;
  // Sprint 132（DEF-096）：後端組出的檔案串流端點路徑，供 <img>/下載使用
  url: string | null;
}

export interface MediaCategoryDto {
  id: string;
  tenantId: string;
  name: string;
  description: string | null;
  parentId: string | null;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface UploadMediaRequest {
  fileName: string;
  filePath: string;
  fileSize: number;
  mimeType: string;
  categoryId?: string;
  tags?: string[];
}

export interface UpdateMediaRequest {
  categoryId?: string;
  tags?: string[];
}

export interface CreateCategoryRequest {
  name: string;
  description?: string;
  parentId?: string;
  sortOrder?: number;
}

export interface UpdateCategoryRequest {
  name?: string;
  description?: string;
  parentId?: string;
  sortOrder?: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// Media Asset APIs

export async function getMediaAssets(params?: {
  page?: number;
  size?: number;
  categoryId?: string;
  mimeType?: string;
  keyword?: string;
}): Promise<PageResponse<MediaAssetDto>> {
  const response = await apiClient.get('/v2/media', { params });
  return response.data.data;
}

export async function getMediaAsset(assetId: string): Promise<MediaAssetDto> {
  const response = await apiClient.get(`/v2/media/${assetId}`);
  return response.data.data;
}

export async function uploadMediaAsset(data: UploadMediaRequest): Promise<MediaAssetDto> {
  const response = await apiClient.post('/v2/media/upload', data);
  return response.data.data;
}

/**
 * 上傳媒體（實際二進位檔案上傳，Sprint 132，DEF-096）
 * 分類/標籤/替代文字/標題皆為選填
 */
export async function uploadMediaAssetMultipart(
  file: File,
  options?: { categoryId?: string; tags?: string[]; altText?: string; title?: string }
): Promise<MediaAssetDto> {
  const formData = new FormData();
  formData.append('file', file);
  if (options?.categoryId) formData.append('categoryId', options.categoryId);
  options?.tags?.forEach((tag) => formData.append('tags', tag));
  if (options?.altText) formData.append('altText', options.altText);
  if (options?.title) formData.append('title', options.title);

  const response = await apiClient.post('/v2/media/upload-multipart', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
  return response.data.data;
}

export async function updateMediaAsset(assetId: string, data: UpdateMediaRequest): Promise<MediaAssetDto> {
  const response = await apiClient.put(`/v2/media/${assetId}`, data);
  return response.data.data;
}

export async function deleteMediaAsset(assetId: string): Promise<void> {
  await apiClient.delete(`/v2/media/${assetId}`);
}

export async function getMediaAssetCount(): Promise<number> {
  const response = await apiClient.get('/v2/media/count');
  return response.data.data;
}

/**
 * 取得媒體檔案內容（Sprint 133，DEF-096 回歸修復）
 * <img src={item.url}> 直接指向需要 Bearer token 的授權端點，<img> 標籤無法附加
 * Authorization header，故改由 apiClient 帶 token 抓取 blob 後建立 object URL
 */
export async function getMediaAssetFileBlob(assetId: string): Promise<Blob> {
  const response = await apiClient.get(`/v2/media/files/${assetId}`, {
    responseType: 'blob',
  });
  return response.data;
}

// Media Category APIs

export async function getMediaCategories(): Promise<MediaCategoryDto[]> {
  const response = await apiClient.get('/v2/media/categories');
  return response.data.data;
}

export async function createMediaCategory(data: CreateCategoryRequest): Promise<MediaCategoryDto> {
  const response = await apiClient.post('/v2/media/categories', data);
  return response.data.data;
}

export async function updateMediaCategory(categoryId: string, data: UpdateCategoryRequest): Promise<MediaCategoryDto> {
  const response = await apiClient.put(`/v2/media/categories/${categoryId}`, data);
  return response.data.data;
}

export async function deleteMediaCategory(categoryId: string): Promise<void> {
  await apiClient.delete(`/v2/media/categories/${categoryId}`);
}