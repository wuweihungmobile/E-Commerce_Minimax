import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

// 設計稿 rs-badge 規格：12px／字重 500／行高 1／padding 4×8／radius-sm（4px）；無陰影。
// 語意變體（default～warning）取設計稿狀態色；warning 文字用 ink（亮黃當文字不可讀）。
const badgeVariants = cva(
  "inline-flex items-center rounded-sm border px-2 py-1 text-xs font-medium leading-none whitespace-nowrap transition-colors",
  {
    variants: {
      variant: {
        default: "border-transparent bg-rs-primary text-white",
        secondary: "border-transparent bg-rs-tertiary text-rs-ink",
        destructive: "border-transparent bg-rs-error text-white",
        success: "border-transparent bg-rs-success text-white",
        outline: "border-rs-hairline text-rs-ink",
        warning: "border-transparent bg-rs-warning/20 text-rs-ink",
        // 意象若水 RUOSHUI 賣場徽章變體
        promo: "border-transparent rounded-sm bg-rs-accent text-white",
        logistics: "border-transparent rounded-sm bg-rs-tertiary text-rs-ink",
        logisticsAlt:
          "border-transparent rounded-sm bg-rs-secondary text-rs-ink",
        feature:
          "rounded-sm border-rs-primary text-rs-primary bg-transparent",
        rating:
          "border-transparent rounded-sm bg-rs-bg-base text-rs-ink-muted font-medium",
        count:
          "border-transparent rounded-full bg-rs-accent text-white justify-center min-w-[18px] h-[18px] px-1.5 text-[10px]",
      },
    },
    defaultVariants: {
      variant: "default",
    },
  }
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLDivElement>,
    VariantProps<typeof badgeVariants> {}

function Badge({ className, variant, ...props }: BadgeProps) {
  return (
    <div className={cn(badgeVariants({ variant }), className)} {...props} />
  )
}

export { Badge, badgeVariants }
