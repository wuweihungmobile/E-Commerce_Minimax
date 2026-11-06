import * as React from "react"

import { cn } from "@/lib/utils"

// 原生 checkbox／radio 以 accent-color 取設計稿主色（不重畫控制項，保留原生鍵盤與無障礙行為）。
const toggleInputStyles =
  "size-4 shrink-0 cursor-pointer accent-rs-primary disabled:cursor-not-allowed"

const Checkbox = React.forwardRef<HTMLInputElement, Omit<React.ComponentProps<"input">, "type">>(
  ({ className, ...props }, ref) => {
    return <input type="checkbox" className={cn(toggleInputStyles, className)} ref={ref} {...props} />
  }
)
Checkbox.displayName = "Checkbox"

const Radio = React.forwardRef<HTMLInputElement, Omit<React.ComponentProps<"input">, "type">>(
  ({ className, ...props }, ref) => {
    return <input type="radio" className={cn(toggleInputStyles, className)} ref={ref} {...props} />
  }
)
Radio.displayName = "Radio"

export { Checkbox, Radio }
