/**
 * M10 IM 聊天 API Service（Sprint 25 US-004）
 *
 * 對應後端 ChatController（/v2/chat），型別對齊 ChatDto。
 * STOMP 即時收訊另見 hooks/useChatSocket.ts。
 */

import apiClient from '@/lib/axios';

// ── Types（對齊後端 ChatDto）──────────────────────────────────

export type ConversationType =
  | 'DIRECT'
  | 'LISTING_INQUIRY'
  | 'ORDER_INQUIRY'
  | 'BOOKING_INQUIRY';

export type MessageType = 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM';

export interface MessageResponse {
  messageId: string;
  conversationId: string;
  senderId: string;
  senderName: string | null;
  messageType: MessageType;
  content: string;
  attachments: string[] | null;
  isRead: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface ConversationResponse {
  conversationId: string;
  listingId: string | null;
  orderId: string | null;
  conversationType: string;
  initiatorId: string;
  initiatorName: string | null;
  recipientId: string;
  recipientName: string | null;
  lastMessage: MessageResponse | null;
  unreadCount: number | null;
  isActive: boolean;
  lastMessageAt: string | null;
  createdAt: string;
}

export interface ConversationListResponse {
  conversations: ConversationResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  unreadCount: number;
}

export interface MessageListResponse {
  messages: MessageResponse[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface CreateConversationRequest {
  recipientId: string;
  listingId?: string;
  orderId?: string;
  conversationType?: ConversationType;
  initialMessage?: string;
}

export interface SendMessageRequest {
  conversationId: string;
  content: string;
  messageType?: MessageType;
  attachments?: string[];
}

// ── APIs ───────────────────────────────────────────────────

export async function createConversation(
  data: CreateConversationRequest,
): Promise<ConversationResponse> {
  const response = await apiClient.post('/v2/chat/conversations', data);
  return response.data.data;
}

export async function getConversations(
  page = 0,
  size = 20,
): Promise<ConversationListResponse> {
  const response = await apiClient.get('/v2/chat/conversations', {
    params: { page, size },
  });
  return response.data.data;
}

export async function getMessages(
  conversationId: string,
  page = 0,
  size = 50,
): Promise<MessageListResponse> {
  const response = await apiClient.get(
    `/v2/chat/conversations/${conversationId}/messages`,
    { params: { page, size } },
  );
  return response.data.data;
}

export async function sendMessage(
  data: SendMessageRequest,
): Promise<MessageResponse> {
  const response = await apiClient.post('/v2/chat/messages', data);
  return response.data.data;
}

export async function markConversationRead(conversationId: string): Promise<void> {
  await apiClient.put(`/v2/chat/conversations/${conversationId}/read`);
}

export async function deleteConversation(conversationId: string): Promise<void> {
  await apiClient.delete(`/v2/chat/conversations/${conversationId}`);
}
