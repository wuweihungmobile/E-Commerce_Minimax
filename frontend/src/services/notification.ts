/**
 * M09 通知模板 API Service
 */

import apiClient from '@/lib/axios';

// Types
export interface NotificationTemplateDto {
  id: string;
  tenantId: string;
  templateCode: string;
  notificationType: string;
  channel: 'IN_APP' | 'EMAIL' | 'SMS' | 'PUSH';
  name: string;
  subject: string;
  contentTemplate: string;
  variables: string[];
  isActive: boolean;
  priority: number;
  createdBy: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
}

export interface CreateTemplateRequest {
  templateCode: string;
  notificationType: string;
  channel: string;
  name: string;
  subject?: string;
  contentTemplate: string;
  variables?: string[];
  isActive?: boolean;
  priority?: number;
}

export interface UpdateTemplateRequest {
  templateCode?: string;
  notificationType?: string;
  channel?: string;
  name?: string;
  subject?: string;
  contentTemplate?: string;
  variables?: string[];
  isActive?: boolean;
  priority?: number;
}

export interface SearchTemplateRequest {
  notificationType?: string;
  channel?: string;
  isActive?: boolean;
  page?: number;
  size?: number;
}

export interface RenderTemplateRequest {
  templateCode: string;
  variables: Record<string, string>;
}

export interface RenderTemplateResponse {
  subject: string;
  content: string;
  variables: Record<string, string>;
}

export interface PageResponse<T> {
  templates: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// Notification Template APIs

export async function getTemplates(params?: SearchTemplateRequest): Promise<PageResponse<NotificationTemplateDto>> {
  const response = await apiClient.get('/v2/notification-templates', { params });
  return response.data.data;
}

export async function getTemplate(templateId: string): Promise<NotificationTemplateDto> {
  const response = await apiClient.get(`/v2/notification-templates/${templateId}`);
  return response.data.data;
}

export async function createTemplate(data: CreateTemplateRequest): Promise<NotificationTemplateDto> {
  const response = await apiClient.post('/v2/dashboard/notification-templates', data);
  return response.data.data;
}

export async function updateTemplate(templateId: string, data: UpdateTemplateRequest): Promise<NotificationTemplateDto> {
  const response = await apiClient.put(`/v2/dashboard/notification-templates/${templateId}`, data);
  return response.data.data;
}

export async function deleteTemplate(templateId: string): Promise<void> {
  await apiClient.delete(`/v2/dashboard/notification-templates/${templateId}`);
}

export async function renderTemplate(data: RenderTemplateRequest): Promise<RenderTemplateResponse> {
  const response = await apiClient.post('/v2/notification-templates/render', data);
  return response.data.data;
}

// Helper functions for dropdown options

export const NOTIFICATION_TYPES = [
  { value: 'ORDER_CONFIRMED', label: '訂單確認' },
  { value: 'ORDER_PAID', label: '訂單已付款' },
  { value: 'ORDER_SHIPPED', label: '訂單已發貨' },
  { value: 'ORDER_DELIVERED', label: '訂單已送達' },
  { value: 'ORDER_COMPLETED', label: '訂單已完成' },
  { value: 'ORDER_CANCELLED', label: '訂單已取消' },
  { value: 'BOOKING_CONFIRMED', label: '預訂確認' },
  { value: 'BOOKING_REMINDER', label: '預訂提醒' },
  { value: 'PAYMENT_SUCCESS', label: '支付成功' },
  { value: 'PAYMENT_FAILED', label: '支付失敗' },
  { value: 'REFUND_COMPLETED', label: '退款完成' },
  { value: 'REVIEW_REQUEST', label: '請求評價' },
  { value: 'NEW_MESSAGE', label: '新消息' },
  { value: 'SYSTEM_ANNOUNCEMENT', label: '系統公告' },
];

export const CHANNELS = [
  { value: 'IN_APP', label: '站內通知' },
  { value: 'EMAIL', label: '電子郵件' },
  { value: 'SMS', label: '簡訊' },
  { value: 'PUSH', label: '推播通知' },
];