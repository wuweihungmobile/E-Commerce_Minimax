'use client';

/**
 * 訊息輸入框（Sprint 25 US-004）。Enter 送出，Shift+Enter 換行。
 */

import { useState } from 'react';

interface MessageComposerProps {
  disabled: boolean;
  onSend: (content: string) => void;
}

export default function MessageComposer({ disabled, onSend }: MessageComposerProps) {
  const [text, setText] = useState('');

  function submit() {
    const trimmed = text.trim();
    if (!trimmed || disabled) {
      return;
    }
    onSend(trimmed);
    setText('');
  }

  return (
    <div className="flex items-end gap-2 border-t p-3">
      <textarea
        data-testid="message-input"
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={(e) => {
          if (e.key === 'Enter' && !e.shiftKey) {
            e.preventDefault();
            submit();
          }
        }}
        placeholder={disabled ? '請先選擇對話' : '輸入訊息…'}
        disabled={disabled}
        rows={1}
        className="flex-1 resize-none rounded border px-3 py-2 text-sm focus:outline-none focus:ring-1 focus:ring-blue-500 disabled:bg-gray-100"
      />
      <button
        type="button"
        data-testid="send-button"
        onClick={submit}
        disabled={disabled || !text.trim()}
        className="rounded bg-blue-600 px-4 py-2 text-sm text-white hover:bg-blue-700 disabled:opacity-50"
      >
        送出
      </button>
    </div>
  );
}
