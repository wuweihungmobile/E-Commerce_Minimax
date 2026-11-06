import * as React from "react"

import { cn } from "@/lib/utils"
import { fieldStyles } from "./field-styles"

// 多行輸入：欄位規格同 Input（field-styles.ts），但取消單行的固定高（h-auto），最小高 80、垂直 padding 10。
const Textarea = React.forwardRef<HTMLTextAreaElement, React.ComponentProps<"textarea">>(
  ({ className, ...props }, ref) => {
    return <textarea className={cn("flex", fieldStyles, "h-auto min-h-20 py-2.5", className)} ref={ref} {...props} />
  }
)
Textarea.displayName = "Textarea"

export { Textarea }
