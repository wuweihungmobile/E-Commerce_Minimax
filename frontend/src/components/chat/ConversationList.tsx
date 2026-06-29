'use client';

/**
 * 對話列表（Sprint 25 US-004）。顯示對方名稱、最後訊息預覽與未讀計數，
 * 未讀計數由父層以 STOMP 即時累計（後端列表未提供每對話未讀，見 services 註解）。
 */

import type { ConversationResponse } from '@/services/chat';

interface ConversationListProps {
  conversations: ConversationResponse[];
  selectedId: string | null;
  unreadMap: Record<string, number>;
  currentUserId: string | null;
  onSelect: (conversationId: string) => void;
}

function otherPartyName(
  conversation: ConversationResponse,
  currentUserId: string | null,
): string {
  if (currentUserId && conversation.initiatorId === currentUserId) {
    return conversation.recipientName ?? '對方';
  }
  return conversation.initiatorName ?? '對方';
}

export default function ConversationList({
  conversations,
  selectedId,
  unreadMap,
  currentUserId,
  onSelect,
}: ConversationListProps) {
  if (conversations.length === 0) {
    return (
      <div className="p-4 text-sm text-gray-500" data-testid="conversation-empty">
        尚無對話
      </div>
    );
  }

  return (
    <ul className="divide-y divide-gray-100" data-testid="conversation-list">
      {conversations.map((conversation) => {
        const unread = unreadMap[conversation.conversationId] ?? 0;
        const selected = conversation.conversationId === selectedId;
        return (
          <li key={conversation.conversationId}>
            <button
              type="button"
              data-testid={`conversation-item-${conversation.conversationId}`}
              onClick={() => onSelect(conversation.conversationId)}
              className={`w-full px-4 py-3 text-left hover:bg-gray-50 ${
                selected ? 'bg-blue-50' : ''
              }`}
            >
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-gray-900 truncate">
                  {otherPartyName(conversation, currentUserId)}
                </span>
                {unread > 0 && (
                  <span
                    data-testid={`conversation-unread-${conversation.conversationId}`}
                    className="ml-2 inline-flex items-center justify-center rounded-full bg-red-500 px-2 py-0.5 text-xs font-medium text-white"
                  >
                    {unread}
                  </span>
                )}
              </div>
              <div className="mt-1 text-xs text-gray-500 truncate">
                {conversation.lastMessage?.content ?? '（尚無訊息）'}
              </div>
            </button>
          </li>
        );
      })}
    </ul>
  );
}
