import * as React from "react"

import { cn } from "@/lib/utils"
import { fieldStyles } from "./field-styles"

// 設計稿 rs-input 規格見 field-styles.ts（與 Textarea／NativeSelect／SelectTrigger 共用）。
const Input = React.forwardRef<HTMLInputElement, React.ComponentProps<"input">>(
  ({ className, type, ...props }, ref) => {
    return (
      <input
        type={type}
        className={cn(
          "flex",
          fieldStyles,
          "file:border-0 file:bg-transparent file:text-sm file:font-medium file:text-rs-ink",
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
