'use client';

/**
 * 訊息串（Sprint 25 US-004）。自己/對方訊息左右對齊，顯示時間與已讀回執（✓/✓✓）。
 * 已讀回執依載入時的 isRead 顯示；後端不廣播已讀事件，故不會即時翻轉（見 services 註解）。
 */

import { useEffect, useRef } from 'react';
import type { MessageResponse } from '@/services/chat';
import type { ChatSocketStatus } from '@/hooks/useChatSocket';

interface MessageThreadProps {
  messages: MessageResponse[]; // 由舊到新
  currentUserId: string | null;
  status: ChatSocketStatus;
  loading: boolean;
}

const STATUS_LABEL: Record<ChatSocketStatus, string> = {
  idle: '未連線',
  connecting: '連線中…',
  connected: '即時連線中',
  disconnected: '連線中斷，重連中…',
};

const STATUS_COLOR: Record<ChatSocketStatus, string> = {
  idle: 'bg-gray-400',
  connecting: 'bg-yellow-400',
  connected: 'bg-green-500',
  disconnected: 'bg-red-500',
};

function formatTime(iso: string): string {
  if (!iso) {
    return '';
  }
  return new Date(iso).toLocaleTimeString('zh-TW', {
    hour: '2-digit',
    minute: '2-digit',
  });
}

export default function MessageThread({
  messages,
  currentUserId,
  status,
  loading,
}: MessageThreadProps) {
  const bottomRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages.length]);

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center gap-2 border-b px-4 py-2 text-xs text-gray-500">
        <span
          className={`inline-block h-2 w-2 rounded-full ${STATUS_COLOR[status]}`}
          data-testid="socket-status-dot"
        />
        <span data-testid="socket-status">{STATUS_LABEL[status]}</span>
      </div>

      <div className="flex-1 space-y-3 overflow-y-auto p-4" data-testid="message-thread">
        {loading ? (
          <div className="py-8 text-center text-sm text-gray-500">載入中…</div>
        ) : messages.length === 0 ? (
          <div className="py-8 text-center text-sm text-gray-500">尚無訊息，開始聊天吧！</div>
        ) : (
          messages.map((message) => {
            const own = currentUserId != null && message.senderId === currentUserId;
            return (
              <div
                key={message.messageId}
                data-testid="message-item"
                className={`flex ${own ? 'justify-end' : 'justify-start'}`}
              >
                <div
                  className={`max-w-[70%] rounded-lg px-3 py-2 text-sm ${
                    own ? 'bg-blue-600 text-white' : 'bg-gray-100 text-gray-900'
                  }`}
                >
                  {!own && (
                    <div className="mb-0.5 text-xs font-medium opacity-70">
                      {message.senderName ?? '對方'}
                    </div>
                  )}
                  <div className="whitespace-pre-wrap break-words">{message.content}</div>
                  <div
                    className={`mt-1 flex items-center justify-end gap-1 text-[10px] ${
                      own ? 'text-blue-100' : 'text-gray-400'
                    }`}
                  >
                    <span>{formatTime(message.createdAt)}</span>
                    {own && (
                      <span data-testid="read-receipt" title={message.isRead ? '已讀' : '已送出'}>
                        {message.isRead ? '✓✓' : '✓'}
                      </span>
                    )}
                  </div>
                </div>
              </div>
            );
          })
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  );
}
