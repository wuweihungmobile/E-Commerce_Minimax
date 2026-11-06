'use client'

import * as React from 'react'
import * as AlertDialogPrimitive from '@radix-ui/react-alert-dialog'

import { Button } from './button'

// 確認框（取代原生 confirm()）：命令式呼叫，回傳 Promise<boolean>——
//   if (!(await confirmDialog('確定要刪除這篇貼文嗎？'))) return
//   if (!(await confirmDialog({ message: '…', confirmLabel: '刪除', destructive: true }))) return
// 呼叫端只需把處理函式改成 async；畫面由根 layout 掛的 <ConfirmDialogHost /> 負責（見 app/layout.tsx）。
//
// 為什麼不用原生 confirm()：破壞性操作（刪除、取消）的唯一安全網不該是不能主題化、不能測試的瀏覽器對話框——
// Playwright 預設自動關閉未處理的對話框（confirm 回 false），所以這 19 個刪除流程以前在 E2E 裡根本走不到「確定」。
// 取消（按取消鈕、ESC、點遮罩）一律回 false。

export interface ConfirmOptions {
  title?: string
  message: React.ReactNode
  confirmLabel?: string
  cancelLabel?: string
  /** 破壞性操作（刪除、取消訂單…）：確認鈕用 destructive 變體。 */
  destructive?: boolean
}

interface Pending extends ConfirmOptions {
  resolve: (ok: boolean) => void
}

let pending: Pending | null = null
const queue: Pending[] = []
const listeners = new Set<() => void>()

function emit() {
  listeners.forEach((listener) => listener())
}

function showNext() {
  pending = queue.shift() ?? null
  emit()
}

// 以請求物件本身比對：Radix 的 Action 按下後還會再呼叫 onOpenChange(false)，
// 若佇列裡還有下一個請求，不能讓這個「遲到的關閉」誤把下一個請求當成取消。
function settle(request: Pending, ok: boolean) {
  if (pending !== request) return
  pending = null
  request.resolve(ok)
  showNext()
}

export function confirmDialog(options: string | ConfirmOptions): Promise<boolean> {
  const opts: ConfirmOptions = typeof options === 'string' ? { message: options } : options
  return new Promise<boolean>((resolve) => {
    queue.push({ ...opts, resolve })
    if (!pending) showNext()
  })
}

function subscribe(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function ConfirmDialogHost() {
  const current = React.useSyncExternalStore(subscribe, () => pending, () => null)

  return (
    <AlertDialogPrimitive.Root
      open={current !== null}
      onOpenChange={(open) => {
        if (!open && current) settle(current, false)
      }}
    >
      <AlertDialogPrimitive.Portal>
        <AlertDialogPrimitive.Overlay className="fixed inset-0 z-[90] bg-black/50" />
        <AlertDialogPrimitive.Content
          data-testid="confirm-dialog"
          className="fixed left-1/2 top-1/2 z-[90] grid w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 gap-4 rounded-lg border border-rs-hairline bg-rs-surface p-6 text-rs-ink"
        >
          <AlertDialogPrimitive.Title className="text-lg font-semibold text-rs-ink">
            {current?.title ?? '請確認'}
          </AlertDialogPrimitive.Title>
          <AlertDialogPrimitive.Description className="text-sm text-rs-ink-muted">
            {current?.message}
          </AlertDialogPrimitive.Description>
          <div className="flex justify-end gap-2">
            <AlertDialogPrimitive.Cancel asChild>
              <Button variant="outline" data-testid="confirm-dialog-cancel">
                {current?.cancelLabel ?? '取消'}
              </Button>
            </AlertDialogPrimitive.Cancel>
            <AlertDialogPrimitive.Action asChild>
              <Button
                variant={current?.destructive ? 'destructive' : 'default'}
                data-testid="confirm-dialog-confirm"
                onClick={() => {
                  if (current) settle(current, true)
                }}
              >
                {current?.confirmLabel ?? '確定'}
              </Button>
            </AlertDialogPrimitive.Action>
          </div>
        </AlertDialogPrimitive.Content>
      </AlertDialogPrimitive.Portal>
    </AlertDialogPrimitive.Root>
  )
}
