'use client'

import * as React from 'react'
import { cn } from '@/lib/utils'

export interface SwitchProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  checked?: boolean
  onCheckedChange?: (checked: boolean) => void
}

// 開＝設計稿主色；關＝disabled 文字色（設計稿沒有 Switch，取其中性灰階中對白色滑塊對比足夠的一個）。
// 無陰影；聚焦外框由 globals.css 的 :focus-visible 提供。
const Switch = React.forwardRef<HTMLButtonElement, SwitchProps>(
  ({ className, checked, onCheckedChange, disabled, onClick, ...props }, ref) => {
    return (
      <button
        role="switch"
        aria-checked={checked}
        disabled={disabled}
        onClick={(e) => {
          if (!disabled && onCheckedChange) {
            onCheckedChange(!checked)
          }
          onClick?.(e)
        }}
        className={cn(
          'peer inline-flex h-6 w-11 shrink-0 cursor-pointer items-center rounded-full border-2 border-transparent transition-colors disabled:cursor-not-allowed disabled:opacity-50',
          checked ? 'bg-rs-primary' : 'bg-rs-disabled-text',
          className
        )}
        ref={ref}
        {...props}
      >
        <span
          className={cn(
            'pointer-events-none inline-block h-5 w-5 rounded-full bg-white ring-0 transition-transform',
            checked ? 'translate-x-5' : 'translate-x-0'
          )}
        />
      </button>
    )
  }
)
Switch.displayName = 'Switch'

export { Switch }
