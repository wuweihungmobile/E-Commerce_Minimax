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
  createdAt: string;
  updatedAt: string;
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