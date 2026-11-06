'use client';

/**
 * 訊息輸入框（Sprint 25 US-004）。Enter 送出，Shift+Enter 換行。
 */

import { useState } from 'react';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';

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
      <Textarea
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
        className="flex-1 resize-none"
      />
      <Button
        variant="default"
        type="button"
        data-testid="send-button"
        onClick={submit}
        disabled={disabled || !text.trim()}
      >
        送出
      </Button>
    </div>
  );
}
