import * as React from "react"

import { cn } from "@/lib/utils"

// 頁面標題（<h1>）與副標：字級、字重、顏色由這裡決定，頁面只放文字內容與版面 class（flex、mb-*…）。
// 設計稿字級 h1 24（Tailwind text-2xl）、字重 600；副標 14／次要色。
const PageTitle = React.forwardRef<HTMLHeadingElement, React.HTMLAttributes<HTMLHeadingElement>>(
  ({ className, ...props }, ref) => (
    <h1 ref={ref} className={cn("text-2xl font-semibold text-rs-ink", className)} {...props} />
  )
)
PageTitle.displayName = "PageTitle"

const PageDescription = React.forwardRef<
  HTMLParagraphElement,
  React.HTMLAttributes<HTMLParagraphElement>
>(({ className, ...props }, ref) => (
  <p ref={ref} className={cn("mt-1 text-sm text-rs-ink-muted", className)} {...props} />
))
PageDescription.displayName = "PageDescription"

export { PageTitle, PageDescription }
