import * as React from "react"

import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

// 載入中／空狀態／載入失敗：頁面載入資料時的三種畫面，長相都在這個檔案。要改它們的版面、顏色、字級，改這裡。
// ErrorState 是 Sprint 332（DEF-468）加的：在那之前只有前兩個，各頁自己內嵌了錯誤畫面（StoreListingGrid、HomeContent…），
// 沒有內嵌錯誤畫面的頁面就把失敗畫成「沒有資料」。較早那批內嵌的錯誤畫面還沒有收斂進來（它們有各自的 data-testid，E2E 依賴）。

// 都轉傳其餘的 div 屬性（`data-testid`、`id`…），E2E 靠 testid 定位這些區塊。
export interface LoadingStateProps extends Omit<React.HTMLAttributes<HTMLDivElement>, "children"> {
  /** 預設「載入中...」（沿用全站既有文字，E2E 與使用者看到的字不變）。 */
  label?: React.ReactNode
}

function LoadingState({ label = "載入中...", className, ...props }: LoadingStateProps) {
  return (
    <div
      role="status"
      aria-live="polite"
      className={cn("flex h-64 items-center justify-center text-rs-ink-muted", className)}
      {...props}
    >
      {label}
    </div>
  )
}

export interface EmptyStateProps extends Omit<React.HTMLAttributes<HTMLDivElement>, "title"> {
  /** 大圖示（emoji 或 lucide 圖示）；只是裝飾，輔助科技會略過。 */
  icon?: React.ReactNode
  title: React.ReactNode
  description?: React.ReactNode
  /** 通常是一個 <Button>／連結（例如「建立第一張採購訂單」）。 */
  action?: React.ReactNode
}

function EmptyState({ icon, title, description, action, className, ...props }: EmptyStateProps) {
  return (
    <div className={cn("flex flex-col items-center justify-center gap-2 py-12 text-center", className)} {...props}>
      {icon ? (
        <div aria-hidden="true" className="mb-2 text-5xl">
          {icon}
        </div>
      ) : null}
      <p className="font-medium text-rs-ink">{title}</p>
      {description ? <p className="text-sm text-rs-ink-muted">{description}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  )
}

export interface ErrorStateProps extends Omit<React.HTMLAttributes<HTMLDivElement>, "title"> {
  /** 預設「載入失敗，請稍後再試」；有具體對象時寫明（例如「載入文章失敗，請稍後再試」）。 */
  title?: React.ReactNode
  description?: React.ReactNode
  /** 有給才顯示重試按鈕。載入失敗的畫面幾乎都該給：使用者唯一能做的事就是再試一次。 */
  onRetry?: () => void
  /** 預設「重新載入」（沿用全站既有文字，E2E 與使用者看到的字不變）。 */
  retryLabel?: React.ReactNode
}

/**
 * 載入失敗：與 EmptyState 刻意長得不一樣的語意——「成功但沒有資料」不需要使用者做什麼，「失敗」要讓他知道可以重試。
 * 兩者不可以互相冒充：把失敗畫成 EmptyState，使用者會以為真的沒有資料（DEF-468）。
 * role=alert：失敗是使用者沒有預期、需要立即知道的事，螢幕閱讀器會主動念出。
 */
function ErrorState({
  title = "載入失敗，請稍後再試",
  description,
  onRetry,
  retryLabel = "重新載入",
  className,
  ...props
}: ErrorStateProps) {
  return (
    <div
      role="alert"
      className={cn("flex flex-col items-center justify-center gap-3 py-12 text-center", className)}
      {...props}
    >
      <p className="font-medium text-rs-ink">{title}</p>
      {description ? <p className="text-sm text-rs-ink-muted">{description}</p> : null}
      {onRetry ? (
        <Button type="button" onClick={onRetry}>
          {retryLabel}
        </Button>
      ) : null}
    </div>
  )
}

export { LoadingState, EmptyState, ErrorState }
