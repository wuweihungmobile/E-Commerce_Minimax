import * as React from "react"
import { Slot } from "@radix-ui/react-slot"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

// 設計稿 rs-btn 規格（Homepage (standalone) 設計稿 DesignSystem Button）：
// radius-md、字重 500、行高 1；solid／outline／ghost／accent；尺寸 sm 32／md 40／lg 48；
// 停用＝disabled 底與字（不是 opacity）；無陰影；聚焦外框由 globals.css 的 :focus-visible 提供。
const buttonVariants = cva(
  "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md border border-transparent text-sm font-medium leading-none transition-colors disabled:pointer-events-none disabled:border-transparent disabled:bg-rs-disabled-bg disabled:text-rs-disabled-text [&_svg]:pointer-events-none [&_svg]:size-4 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        default: "bg-rs-primary text-white hover:bg-rs-primary-hover",
        accent: "bg-rs-accent text-white hover:bg-rs-accent-hover",
        destructive: "bg-rs-error text-white hover:bg-rs-error/90",
        success: "bg-rs-success text-white hover:bg-rs-success/90",
        // 警告色是亮黃，白字不可讀：文字用 ink（設計稿規定警告色不當文字色，白字也不行）
        warning: "bg-rs-warning text-rs-ink hover:bg-rs-warning/90",
        outline:
          "border-rs-hairline bg-rs-surface text-rs-ink hover:border-rs-primary hover:text-rs-primary",
        secondary: "bg-rs-tertiary text-rs-ink hover:bg-rs-secondary",
        ghost: "text-rs-ink hover:bg-rs-bg-base hover:text-rs-primary",
        // 文字連結樣式的按鈕（表格列內的「編輯」「刪除」「下架」）：搭配 size="inline"
        link: "text-rs-primary underline-offset-4 hover:underline",
        linkDestructive: "text-rs-error underline-offset-4 hover:underline",
        linkSuccess: "text-rs-success underline-offset-4 hover:underline",
        linkNeutral: "text-rs-ink underline-offset-4 hover:underline",
      },
      size: {
        default: "h-10 px-5",
        sm: "h-8 px-4",
        lg: "h-12 px-7 text-base",
        icon: "size-10",
        // 行內：沒有最小高與內距，高度跟著文字（表格列內的文字連結按鈕）
        inline: "h-auto p-0",
      },
    },
    defaultVariants: {
      variant: "default",
      size: "default",
    },
  }
)

export interface ButtonProps
  extends React.ButtonHTMLAttributes<HTMLButtonElement>,
    VariantProps<typeof buttonVariants> {
  asChild?: boolean
}

const Button = React.forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant, size, asChild = false, ...props }, ref) => {
    const Comp = asChild ? Slot : "button"
    return (
      <Comp
        className={cn(buttonVariants({ variant, size, className }))}
        ref={ref}
        {...props}
      />
    )
  }
)
Button.displayName = "Button"

export { Button, buttonVariants }
