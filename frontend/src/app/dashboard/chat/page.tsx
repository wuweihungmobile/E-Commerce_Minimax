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
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { PageTitle } from '@/components/ui/page-title';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';

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
      toast.error('訊息傳送失敗，請稍後再試');
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
      toast.error('建立對話失敗，請確認收件者 ID 是否正確');
    }
  }

  async function handleDelete(conversationId: string) {
    if (!(await confirmDialog({ message: '確定要刪除這個對話嗎？', confirmLabel: '刪除', destructive: true }))) {
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
      toast.error('刪除對話失敗，請稍後再試');
    }
  }

  return (
    <div className="flex h-[calc(100vh-3rem)] flex-col p-6">
      <div className="mb-4 flex items-center justify-between">
        <PageTitle>訊息中心</PageTitle>
        <Button
          variant="default"
          size="sm"
          type="button"
          data-testid="new-conversation-button"
          onClick={() => setNewConvOpen((open) => !open)}
        >
          發起對話
        </Button>
      </div>

      {newConvOpen && (
        <Card className="mb-4 p-4" data-testid="new-conversation-form">
          <div className="flex flex-wrap items-end gap-3">
            <div className="flex-1 min-w-[16rem]">
              <Label className="mb-1 block">收件者使用者 ID</Label>
              <Input
                data-testid="new-recipient-input"
                value={newRecipientId}
                onChange={(e) => setNewRecipientId(e.target.value)}
                placeholder="recipient user UUID"
              />
            </div>
            <div className="flex-1 min-w-[16rem]">
              <Label className="mb-1 block">第一則訊息（選填）</Label>
              <Input
                data-testid="new-message-input"
                value={newInitialMessage}
                onChange={(e) => setNewInitialMessage(e.target.value)}
                placeholder="你好！"
              />
            </div>
            <Button
              variant="success"
              size="sm"
              type="button"
              data-testid="create-conversation-submit"
              onClick={handleCreateConversation}
              disabled={!newRecipientId.trim()}
            >
              建立
            </Button>
          </div>
        </Card>
      )}

      <Card className="flex flex-1 overflow-hidden">
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
                <Button
                  variant="linkDestructive"
                  size="inline"
                  type="button"
                  data-testid="delete-conversation-button"
                  onClick={() => handleDelete(selectedId)}
                >
                  刪除對話
                </Button>
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
            <div className="flex flex-1 items-center justify-center text-sm text-rs-ink-muted">
              選擇左側對話開始聊天
            </div>
          )}
        </section>
      </Card>
    </div>
  );
}
