/**
 * M18 FAQ API Service（後台管理，權限 faq:read/create/update/delete，比照 knowledge.ts）
 */

import apiClient from '@/lib/axios';

// Types

export interface FaqArticleDto {
  id: string;
  categoryId: string;
  categoryName: string;
  question: string;
  answer: string;
  slug: string;
  sortOrder: number;
  viewCount: number;
  isPinned: boolean;
  isPublished: boolean;
  publishedAt: string;
  createdAt: string;
  updatedAt: string;
  highlightedQuestion?: string;
  highlightedAnswer?: string;
}

export interface FaqCategoryDto {
  id: string;
  name: string;
  slug: string;
  description: string;
  icon: string;
  sortOrder: number;
  createdAt: string;
  updatedAt: string;
}

export interface FaqCategoryStatsDto {
  categoryId: string;
  categoryName: string;
  categorySlug: string;
  totalArticles: number;
  publishedArticles: number;
}

export interface CreateFaqArticleRequest {
  categoryId: string;
  question: string;
  answer: string;
  slug: string;
  sortOrder?: number;
  isPinned?: boolean;
}

export interface UpdateFaqArticleRequest {
  categoryId?: string;
  question?: string;
  answer?: string;
  slug?: string;
  sortOrder?: number;
  isPinned?: boolean;
  isPublished?: boolean;
}

export interface CreateFaqCategoryRequest {
  name: string;
  slug: string;
  description?: string;
  icon?: string;
  sortOrder?: number;
}

export interface UpdateFaqCategoryRequest {
  name?: string;
  slug?: string;
  description?: string;
  icon?: string;
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

// FAQ Article APIs

export async function getFaqArticles(params?: {
  page?: number;
  size?: number;
  categoryId?: string;
  keyword?: string;
}): Promise<PageResponse<FaqArticleDto>> {
  const response = await apiClient.get('/v2/faqs', { params });
  return response.data.data;
}

export async function getFaqArticle(articleId: string): Promise<FaqArticleDto> {
  const response = await apiClient.get(`/v2/faqs/${articleId}`);
  return response.data.data;
}

export async function getFaqArticleBySlug(slug: string): Promise<FaqArticleDto> {
  const response = await apiClient.get(`/v2/faqs/slug/${slug}`);
  return response.data.data;
}

export async function createFaqArticle(data: CreateFaqArticleRequest): Promise<FaqArticleDto> {
  const response = await apiClient.post('/v2/faqs', data);
  return response.data.data;
}

export async function updateFaqArticle(articleId: string, data: UpdateFaqArticleRequest): Promise<FaqArticleDto> {
  const response = await apiClient.put(`/v2/faqs/${articleId}`, data);
  return response.data.data;
}

export async function deleteFaqArticle(articleId: string): Promise<void> {
  await apiClient.delete(`/v2/faqs/${articleId}`);
}

export async function incrementFaqArticleView(articleId: string): Promise<void> {
  await apiClient.post(`/v2/faqs/${articleId}/view`);
}

export async function getPinnedFaqArticles(): Promise<FaqArticleDto[]> {
  const response = await apiClient.get('/v2/faqs/pinned');
  return response.data.data;
}

export async function searchFaqArticlesWithHighlight(params: {
  page?: number;
  size?: number;
  keyword?: string;
}): Promise<PageResponse<FaqArticleDto>> {
  const response = await apiClient.get('/v2/faqs/search', { params });
  return response.data.data;
}

// FAQ Category APIs

export async function getFaqCategories(): Promise<FaqCategoryDto[]> {
  const response = await apiClient.get('/v2/faqs/categories');
  return response.data.data;
}

export async function getFaqCategory(categoryId: string): Promise<FaqCategoryDto> {
  const response = await apiClient.get(`/v2/faqs/categories/${categoryId}`);
  return response.data.data;
}

export async function createFaqCategory(data: CreateFaqCategoryRequest): Promise<FaqCategoryDto> {
  const response = await apiClient.post('/v2/faqs/categories', data);
  return response.data.data;
}

export async function updateFaqCategory(categoryId: string, data: UpdateFaqCategoryRequest): Promise<FaqCategoryDto> {
  const response = await apiClient.put(`/v2/faqs/categories/${categoryId}`, data);
  return response.data.data;
}

export async function deleteFaqCategory(categoryId: string): Promise<void> {
  await apiClient.delete(`/v2/faqs/categories/${categoryId}`);
}

export async function getFaqCategoryStats(): Promise<FaqCategoryStatsDto[]> {
  const response = await apiClient.get('/v2/faqs/categories/stats');
  return response.data.data;
}
