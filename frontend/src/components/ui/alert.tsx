'use client'

import * as React from 'react'
import { cn } from '@/lib/utils'

export interface AlertProps extends React.HTMLAttributes<HTMLDivElement> {
  variant?: 'default' | 'destructive' | 'success' | 'warning'
}

// 狀態色一律取設計稿語意 token（error／success／warning），底色用 10～15% 透明度、邊框 30～40%。
// warning 的文字用 ink：設計稿的 warning 是亮黃（#fbbf24 等），當文字在白底上不可讀，顏色交給底與框。
// （Sprint 328 前 destructive 寫的是 bg-destructive／text-destructive，但專案從沒定義過 --color-destructive，
//  所以該變體從來沒有上色；這次一併修正。）
const Alert = React.forwardRef<HTMLDivElement, AlertProps>(
  ({ className, variant = 'default', ...props }, ref) => {
    return (
      <div
        ref={ref}
        role="alert"
        className={cn(
          'relative w-full rounded-lg border p-4',
          {
            'border-rs-hairline bg-rs-surface text-rs-ink': variant === 'default',
            'border-rs-error/30 bg-rs-error/10 text-rs-error': variant === 'destructive',
            'border-rs-success/30 bg-rs-success/10 text-rs-success': variant === 'success',
            'border-rs-warning/40 bg-rs-warning/15 text-rs-ink': variant === 'warning',
          },
          className
        )}
        {...props}
      />
    )
  }
)
Alert.displayName = 'Alert'

const AlertDescription = React.forwardRef<
  HTMLParagraphElement,
  React.HTMLAttributes<HTMLParagraphElement>
>(({ className, ...props }, ref) => {
  return (
    <p
      ref={ref}
      className={cn('text-sm [&_p]:leading-relaxed', className)}
      {...props}
    />
  )
})
AlertDescription.displayName = 'AlertDescription'

export { Alert, AlertDescription }
