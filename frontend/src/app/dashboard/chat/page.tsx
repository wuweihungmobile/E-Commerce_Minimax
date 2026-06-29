'use client';

/**
 * M10 IM 聊天頁（Sprint 25 US-004）。
 *
 * 接上 Sprint 24 STOMP 後端：載入對話與訊息（REST），以 useChatSocket 即時收訊，
 * 自己/對方訊息去重（送出走 REST、後端亦廣播回來），非開啟對話即時累計未讀。
 */

import { useCallback, useEffect, useMemo, useState } from 'react';
import authService from '@/services/auth';
import {
  createConversation,
  deleteConversation,
  getConversations,
  getMessages,
  markConversationRead,
  sendMessage,
  type ConversationResponse,
  type MessageResponse,
} from '@/services/chat';
import { useChatSocket } from '@/hooks/useChatSocket';
import ConversationList from '@/components/chat/ConversationList';
import MessageThread from '@/components/chat/MessageThread';
import MessageComposer from '@/components/chat/MessageComposer';

export default function ChatPage() {
  const [currentUserId, setCurrentUserId] = useState<string | null>(null);
  const [conversations, setConversations] = useState<ConversationResponse[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [unreadMap, setUnreadMap] = useState<Record<string, number>>({});
  const [loadingMessages, setLoadingMessages] = useState(false);
  const [newConvOpen, setNewConvOpen] = useState(false);
  const [newRecipientId, setNewRecipientId] = useState('');
  const [newInitialMessage, setNewInitialMessage] = useState('');

  useEffect(() => {
    setCurrentUserId(authService.getCurrentUser()?.id ?? null);
  }, []);

  const loadConversations = useCallback(async () => {
    try {
      const data = await getConversations(0, 50);
      setConversations(data.conversations);
    } catch (error) {
      console.error('Failed to load conversations:', error);
    }
  }, []);

  useEffect(() => {
    loadConversations();
  }, [loadConversations]);

  const conversationIds = useMemo(
    () => conversations.map((c) => c.conversationId),
    [conversations],
  );

  /** 將訊息加入當前訊息串（依 messageId 去重，避免 REST 與 STOMP 重複）。 */
  const appendMessage = useCallback((message: MessageResponse) => {
    setMessages((prev) =>
      prev.some((m) => m.messageId === message.messageId) ? prev : [...prev, message],
    );
  }, []);

  /** 更新對話列表的最後訊息預覽並移到最前。 */
  const bumpConversation = useCallback((message: MessageResponse) => {
    setConversations((prev) => {
      const index = prev.findIndex((c) => c.conversationId === message.conversationId);
      if (index === -1) {
        return prev;
      }
      const updated: ConversationResponse = {
        ...prev[index],
        lastMessage: message,
        lastMessageAt: message.createdAt,
      };
      return [updated, ...prev.filter((_, i) => i !== index)];
    });
  }, []);

  /** STOMP 收到新訊息。 */
  const handleIncoming = useCallback(
    (message: MessageResponse) => {
      bumpConversation(message);
      if (message.conversationId === selectedId) {
        appendMessage(message);
        if (currentUserId && message.senderId !== currentUserId) {
          void markConversationRead(selectedId);
        }
      } else if (currentUserId && message.senderId !== currentUserId) {
        setUnreadMap((prev) => ({
          ...prev,
          [message.conversationId]: (prev[message.conversationId] ?? 0) + 1,
        }));
      }
    },
    [appendMessage, bumpConversation, currentUserId, selectedId],
  );

  const socketStatus = useChatSocket(conversationIds, handleIncoming);

  async function handleSelect(conversationId: string) {
    setSelectedId(conversationId);
    setUnreadMap((prev) => ({ ...prev, [conversationId]: 0 }));
    setLoadingMessages(true);
    try {
      const data = await getMessages(conversationId, 0, 50);
      // 後端回傳為 createdAt 降冪，反轉為由舊到新顯示
      setMessages([...data.messages].reverse());
      await markConversationRead(conversationId);
    } catch (error) {
      console.error('Failed to load messages:', error);
      setMessages([]);
    } finally {
      setLoadingMessages(false);
    }
  }

  async function handleSend(content: string) {
    if (!selectedId) {
      return;
    }
    try {
      const sent = await sendMessage({ conversationId: selectedId, content });
      appendMessage(sent);
      bumpConversation(sent);
    } catch (error) {
      console.error('Failed to send message:', error);
      alert('訊息傳送失敗，請稍後再試');
    }
  }

  async function handleCreateConversation() {
    const recipientId = newRecipientId.trim();
    if (!recipientId) {
      return;
    }
    try {
      const conversation = await createConversation({
        recipientId,
        conversationType: 'DIRECT',
        initialMessage: newInitialMessage.trim() || undefined,
      });
      setNewConvOpen(false);
      setNewRecipientId('');
      setNewInitialMessage('');
      await loadConversations();
      await handleSelect(conversation.conversationId);
    } catch (error) {
      console.error('Failed to create conversation:', error);
      alert('建立對話失敗，請確認收件者 ID 是否正確');
    }
  }

  async function handleDelete(conversationId: string) {
    if (!confirm('確定要刪除這個對話嗎？')) {
      return;
    }
    try {
      await deleteConversation(conversationId);
      if (selectedId === conversationId) {
        setSelectedId(null);
        setMessages([]);
      }
      await loadConversations();
    } catch (error) {
      console.error('Failed to delete conversation:', error);
      alert('刪除對話失敗，請稍後再試');
    }
  }

  return (
    <div className="flex h-[calc(100vh-3rem)] flex-col p-6">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-bold">訊息中心</h1>
        <button
          type="button"
          data-testid="new-conversation-button"
          onClick={() => setNewConvOpen((open) => !open)}
          className="rounded bg-blue-600 px-4 py-1.5 text-sm text-white hover:bg-blue-700"
        >
          發起對話
        </button>
      </div>

      {newConvOpen && (
        <div className="mb-4 rounded-lg border bg-white p-4" data-testid="new-conversation-form">
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex-1 min-w-[16rem]">
              <label className="mb-1 block text-sm font-medium text-gray-700">收件者使用者 ID</label>
              <input
                data-testid="new-recipient-input"
                value={newRecipientId}
                onChange={(e) => setNewRecipientId(e.target.value)}
                placeholder="recipient user UUID"
                className="w-full rounded border px-3 py-1.5 text-sm"
              />
            </div>
            <div className="flex-1 min-w-[16rem]">
              <label className="mb-1 block text-sm font-medium text-gray-700">第一則訊息（選填）</label>
              <input
                data-testid="new-message-input"
                value={newInitialMessage}
                onChange={(e) => setNewInitialMessage(e.target.value)}
                placeholder="你好！"
                className="w-full rounded border px-3 py-1.5 text-sm"
              />
            </div>
            <button
              type="button"
              data-testid="create-conversation-submit"
              onClick={handleCreateConversation}
              disabled={!newRecipientId.trim()}
              className="rounded bg-green-600 px-4 py-1.5 text-sm text-white hover:bg-green-700 disabled:opacity-50"
            >
              建立
            </button>
          </div>
        </div>
      )}

      <div className="flex flex-1 overflow-hidden rounded-lg border bg-white">
        {/* 左：對話列表 */}
        <aside className="w-72 shrink-0 overflow-y-auto border-r">
          <ConversationList
            conversations={conversations}
            selectedId={selectedId}
            unreadMap={unreadMap}
            currentUserId={currentUserId}
            onSelect={handleSelect}
          />
        </aside>

        {/* 右：訊息串 + 輸入框 */}
        <section className="flex flex-1 flex-col">
          {selectedId ? (
            <>
              <div className="flex items-center justify-end border-b px-4 py-1.5">
                <button
                  type="button"
                  data-testid="delete-conversation-button"
                  onClick={() => handleDelete(selectedId)}
                  className="text-xs text-red-600 hover:text-red-800"
                >
                  刪除對話
                </button>
              </div>
              <div className="flex-1 overflow-hidden">
                <MessageThread
                  messages={messages}
                  currentUserId={currentUserId}
                  status={socketStatus}
                  loading={loadingMessages}
                />
              </div>
              <MessageComposer disabled={false} onSend={handleSend} />
            </>
          ) : (
            <div className="flex flex-1 items-center justify-center text-sm text-gray-500">
              選擇左側對話開始聊天
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
