import * as React from "react"

import { cn } from "@/lib/utils"

// 設計稿 rs-input 規格：最小高 44、padding 10×16、1px 邊框、radius-md、內文字級（16px）；
// 聚焦＝主色邊框＋2px 外框（外框由 globals.css 的 :focus-visible 提供）；
// aria-invalid＝error 色邊框與外框；停用＝disabled 底與字；無陰影。
const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  ({ className, type, ...props }, ref) => {
    return (
      <input
        type={type}
        className={cn(
          "flex min-h-11 w-full rounded-md border border-rs-hairline bg-rs-surface px-4 py-2.5 text-base text-rs-ink transition-colors file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-rs-ink placeholder:text-rs-ink-muted focus-visible:border-rs-primary disabled:cursor-not-allowed disabled:bg-rs-disabled-bg disabled:text-rs-disabled-text aria-invalid:border-rs-error aria-invalid:focus-visible:outline-rs-error",
          className
        )}
        ref={ref}
        {...props}
      />
    )
  }
)
Input.displayName = "Input"

export { Input }
