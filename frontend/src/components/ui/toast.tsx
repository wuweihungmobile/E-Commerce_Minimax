'use client'

import * as React from 'react'
import { X } from 'lucide-react'

import { cn } from '@/lib/utils'

// 通知（取代原生 alert()）：命令式呼叫 toast.success('已儲存')／toast.error('刪除失敗')／toast.info(...)，
// 與原本 alert(...) 的呼叫習慣一致——事件處理器、catch 區塊都能直接呼叫，不需要 hook 或 Context。
// 畫面由根 layout 掛一個 <Toaster />（見 app/layout.tsx）。
//
// 為什麼不用原生 alert()：①不能主題化；②阻塞 JS 執行緒；③Playwright 會自動關閉未處理的對話框，
// 而且原生對話框曾在 teardown 造成 session 崩潰（S37 AI-2001）；④E2E 只能 waitForEvent('dialog')，無法用一般的可見性斷言。
// Toast 是普通 DOM：E2E 用 getByTestId('toast') 斷言即可。

type ToastKind = 'success' | 'error' | 'info'

interface ToastItem {
  id: number
  kind: ToastKind
  message: string
}

const DURATION_MS: Record<ToastKind, number> = { success: 4000, info: 4000, error: 6000 }

let items: ToastItem[] = []
let nextId = 1
const listeners = new Set<() => void>()
const timers = new Map<number, ReturnType<typeof setTimeout>>()
const EMPTY: ToastItem[] = []

function emit() {
  listeners.forEach((listener) => listener())
}

function dismiss(id: number) {
  const timer = timers.get(id)
  if (timer) clearTimeout(timer)
  timers.delete(id)
  items = items.filter((t) => t.id !== id)
  emit()
}

function push(kind: ToastKind, message: string) {
  const id = nextId++
  items = [...items, { id, kind, message }]
  timers.set(id, setTimeout(() => dismiss(id), DURATION_MS[kind]))
  emit()
}

export const toast = {
  success: (message: string) => push('success', message),
  error: (message: string) => push('error', message),
  info: (message: string) => push('info', message),
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

// 左側 4px 色條表達類型；表面一律實色（浮在內容上，不能透底）；無陰影（設計稿規定）。
const KIND_STYLES: Record<ToastKind, string> = {
  success: 'border-l-4 border-l-rs-success',
  error: 'border-l-4 border-l-rs-error',
  info: 'border-l-4 border-l-rs-primary',
}

export function Toaster() {
  const list = React.useSyncExternalStore(subscribe, () => items, () => EMPTY)
  return (
    <div
      data-testid="toast-region"
      className="pointer-events-none fixed bottom-6 left-1/2 z-[100] flex w-[min(28rem,calc(100vw-2rem))] -translate-x-1/2 flex-col gap-2"
    >
      {list.map((t) => (
        <div
          key={t.id}
          data-testid="toast"
          data-kind={t.kind}
          role={t.kind === 'error' ? 'alert' : 'status'}
          className={cn(
            'pointer-events-auto flex items-start gap-3 rounded-lg border border-rs-hairline bg-rs-surface px-4 py-3 text-sm text-rs-ink',
            KIND_STYLES[t.kind]
          )}
        >
          <span className="min-w-0 flex-1 whitespace-pre-wrap break-words">{t.message}</span>
          <button
            type="button"
            aria-label="關閉通知"
            onClick={() => dismiss(t.id)}
            className="shrink-0 rounded-sm text-rs-ink-muted transition-colors hover:text-rs-ink"
          >
            <X className="size-4" />
          </button>
        </div>
      ))}
    </div>
  )
}
