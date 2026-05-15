/**
 * M18 知識庫 API Service
 */

import apiClient from '@/lib/axios';

// Types
export interface KnowledgeArticleDto {
  id: string;
  tenantId: string;
  categoryId: string;
  categoryName: string;
  authorId: string;
  authorName: string;
  title: string;
  slug: string;
  content: string;
  excerpt: string;
  coverImageUrl: string;
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
  viewCount: number;
  isPinned: boolean;
  tags: string[];
  publishedAt: string;
  createdAt: string;
  updatedAt: string;
}

export interface KnowledgeCategoryDto {
  id: string;
  tenantId: string;
  name: string;
  slug: string;
  description: string;
  icon: string;
  sortOrder: number;
  articleCount: number;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface CreateArticleRequest {
  categoryId: string;
  title: string;
  content: string;
  excerpt?: string;
  coverImageUrl?: string;
  tags?: string[];
  isPinned?: boolean;
  autoPublish?: boolean;
}

export interface UpdateArticleRequest {
  categoryId?: string;
  title?: string;
  content?: string;
  excerpt?: string;
  coverImageUrl?: string;
  tags?: string[];
  isPinned?: boolean;
  status?: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';
}

export interface CreateCategoryRequest {
  name: string;
  description?: string;
  icon?: string;
  sortOrder?: number;
}

export interface UpdateCategoryRequest {
  name?: string;
  description?: string;
  icon?: string;
  sortOrder?: number;
  isActive?: boolean;
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

// Knowledge Article APIs

export async function getKnowledgeArticles(params?: {
  page?: number;
  size?: number;
  categoryId?: string;
  keyword?: string;
}): Promise<PageResponse<KnowledgeArticleDto>> {
  const response = await apiClient.get('/v2/knowledge', { params });
  return response.data.data;
}

export async function getKnowledgeArticle(articleId: string): Promise<KnowledgeArticleDto> {
  const response = await apiClient.get(`/v2/knowledge/${articleId}`);
  return response.data.data;
}

export async function getKnowledgeArticleBySlug(slug: string): Promise<KnowledgeArticleDto> {
  const response = await apiClient.get(`/v2/knowledge/slug/${slug}`);
  return response.data.data;
}

export async function createKnowledgeArticle(data: CreateArticleRequest): Promise<KnowledgeArticleDto> {
  const response = await apiClient.post('/v2/knowledge', data);
  return response.data.data;
}

export async function updateKnowledgeArticle(articleId: string, data: UpdateArticleRequest): Promise<KnowledgeArticleDto> {
  const response = await apiClient.put(`/v2/knowledge/${articleId}`, data);
  return response.data.data;
}

export async function deleteKnowledgeArticle(articleId: string): Promise<void> {
  await apiClient.delete(`/v2/knowledge/${articleId}`);
}

export async function incrementKnowledgeArticleView(articleId: string): Promise<void> {
  await apiClient.post(`/v2/knowledge/${articleId}/view`);
}

// Knowledge Category APIs

export async function getKnowledgeCategories(): Promise<KnowledgeCategoryDto[]> {
  const response = await apiClient.get('/v2/knowledge/categories');
  return response.data.data;
}

export async function createKnowledgeCategory(data: CreateCategoryRequest): Promise<KnowledgeCategoryDto> {
  const response = await apiClient.post('/v2/knowledge/categories', data);
  return response.data.data;
}

export async function updateKnowledgeCategory(categoryId: string, data: UpdateCategoryRequest): Promise<KnowledgeCategoryDto> {
  const response = await apiClient.put(`/v2/knowledge/categories/${categoryId}`, data);
  return response.data.data;
}

export async function deleteKnowledgeCategory(categoryId: string): Promise<void> {
  await apiClient.delete(`/v2/knowledge/categories/${categoryId}`);
}