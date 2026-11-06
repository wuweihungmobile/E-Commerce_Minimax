import * as React from "react"

import { cn } from "@/lib/utils"

// 載入中／空狀態：全站只有這兩個長相。要改它們的版面、顏色、字級，改這個檔案。

// 兩者都轉傳其餘的 div 屬性（`data-testid`、`id`…），E2E 靠 testid 定位這些區塊。
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

export { LoadingState, EmptyState }
