import * as React from "react"
import { cva, type VariantProps } from "class-variance-authority"

import { cn } from "@/lib/utils"

const badgeVariants = cva(
  "inline-flex items-center rounded-md border px-2.5 py-0.5 text-xs font-semibold transition-colors focus:outline-none focus:ring-2 focus:ring-ring focus:ring-offset-2",
  {
    variants: {
      variant: {
        default:
          "border-transparent bg-primary text-primary-foreground shadow hover:bg-primary/80",
        secondary:
          "border-transparent bg-secondary text-secondary-foreground hover:bg-secondary/80",
        destructive:
          "border-transparent bg-error text-error-foreground shadow hover:bg-error/80",
        success:
          "border-transparent bg-success text-success-foreground shadow hover:bg-success/80",
        outline: "text-foreground",
        warning:
          "border-transparent bg-amber-100 text-amber-800 hover:bg-amber-200",
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
