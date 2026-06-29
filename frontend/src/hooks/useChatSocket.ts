'use client';

/**
 * M10 IM STOMP 即時收訊 hook（Sprint 25 US-004 / AC-004-2、AC-004-3）。
 *
 * <p>連線後端 STOMP（SockJS endpoint `${API_BASE}/ws`），CONNECT 時以 STOMP frame
 * native header 帶 `Authorization: Bearer {token}`（對應後端 StompAuthChannelInterceptor），
 * 訂閱每個對話的 `/queue/conversations/{id}/messages` 即時收訊。
 *
 * <p>支援多對話訂閱：可同時監聽目前開啟的對話與其他對話，使非開啟的對話也能即時累計未讀。
 * 斷線由 @stomp/stompjs 內建 `reconnectDelay` 自動重連；訂閱動作放在 onConnect 內，
 * 確保重連後自動重新訂閱全部對話。對話清單變更時切換訂閱。
 */

import { useEffect, useRef, useState } from 'react';
import { Client, type IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { API_CONFIG } from '@/lib/api';
import type { MessageResponse } from '@/services/chat';

export type ChatSocketStatus = 'idle' | 'connecting' | 'connected' | 'disconnected';

const RECONNECT_DELAY_MS = 5000;

function buildSockJsUrl(): string {
  // 後端 SockJS endpoint 在 servlet context-path（/api）下的 /ws → ${baseUrl}/ws
  return `${API_CONFIG.baseUrl}/ws`;
}

function readToken(): string | null {
  if (typeof window === 'undefined') {
    return null;
  }
  return localStorage.getItem('accessToken');
}

/**
 * 訂閱多個對話的即時訊息。
 *
 * @param conversationIds 要訂閱的對話 ID 清單；為空時不連線
 * @param onMessage       收到新訊息的回呼（以 ref 保存，變更不會觸發重連）
 * @returns 連線狀態
 */
export function useChatSocket(
  conversationIds: string[],
  onMessage: (message: MessageResponse) => void,
): ChatSocketStatus {
  // liveStatus 僅由 STOMP client 的 callback 驅動（避免在 effect body 同步 setState）
  const [liveStatus, setLiveStatus] = useState<ChatSocketStatus>('idle');
  const onMessageRef = useRef(onMessage);
  const idsRef = useRef(conversationIds);

  // React 19：ref 僅可於 effect 內更新（不可於 render 期間），每次 render 後同步最新值
  useEffect(() => {
    onMessageRef.current = onMessage;
    idsRef.current = conversationIds;
  });

  // 以排序後的 join 字串為依賴鍵：訂閱集合不變時不重連（避免每次 render 重建連線）
  const subscriptionKey = [...conversationIds].sort().join(',');

  useEffect(() => {
    if (idsRef.current.length === 0) {
      return;
    }

    const token = readToken();
    if (!token) {
      return;
    }

    const client = new Client({
      webSocketFactory: () => new SockJS(buildSockJsUrl()),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: RECONNECT_DELAY_MS,
      beforeConnect: () => setLiveStatus('connecting'),
      onConnect: () => {
        setLiveStatus('connected');
        for (const id of idsRef.current) {
          client.subscribe(`/queue/conversations/${id}/messages`, (frame: IMessage) => {
            try {
              const message = JSON.parse(frame.body) as MessageResponse;
              // 後端廣播 payload 的 conversationId 可能為 null；訂閱為 per-conversation，
              // 以訂閱的 id 為權威來源補上，確保 client 端能正確路由到對應對話
              if (!message.conversationId) {
                message.conversationId = id;
              }
              onMessageRef.current(message);
            } catch {
              // 忽略無法解析的訊息，避免一筆壞訊息中斷訂閱
            }
          });
        }
      },
      onWebSocketClose: () => setLiveStatus('disconnected'),
      onStompError: () => setLiveStatus('disconnected'),
    });

    client.activate();

    return () => {
      void client.deactivate();
    };
    // 僅在訂閱集合（subscriptionKey）變更時重建連線；ids 與 callback 以 ref 取得最新值
  }, [subscriptionKey]);

  // 無對話時對外回報 idle；否則回報實際連線狀態
  return conversationIds.length === 0 ? 'idle' : liveStatus;
}
