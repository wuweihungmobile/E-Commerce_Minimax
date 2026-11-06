import * as React from "react"

import { cn } from "@/lib/utils"
import { fieldStyles } from "./field-styles"

// 原生 <select>：欄位規格同 Input（field-styles.ts）。
// 與 Radix 版的 Select（select.tsx）並存：Radix 版是自訂下拉面板；這個保留瀏覽器原生下拉
// （密集的篩選列／表單、行動裝置，且 value／onChange 與原生 <select> 完全相容，Playwright 可用 selectOption）。
const NativeSelect = React.forwardRef<HTMLSelectElement, React.ComponentProps<"select">>(
  ({ className, children, ...props }, ref) => {
    return (
      <select className={cn("block", fieldStyles, className)} ref={ref} {...props}>
        {children}
      </select>
    )
  }
)
NativeSelect.displayName = "NativeSelect"

export { NativeSelect }
