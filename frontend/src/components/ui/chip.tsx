import * as React from "react"

import { cn } from "@/lib/utils"

export interface ChipProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  /** 是否為選取態。以 aria-pressed 表達（輔助科技與 E2E 都讀這個，不讀 class 名）。 */
  selected?: boolean
}

// 可切換的小按鈕：篩選列、分頁籤、設施標籤。選取＝主色淺底＋主色字；未選＝停用底。
// 選取態的視覺由這裡決定；呼叫端只傳 selected，不再用條件式 class 自己拼。
const Chip = React.forwardRef<HTMLButtonElement, ChipProps>(
  ({ className, selected = false, type = "button", ...props }, ref) => (
    <button
      ref={ref}
      type={type}
      aria-pressed={selected}
      className={cn(
        "inline-flex items-center justify-center rounded-md px-3 py-1 text-sm font-medium transition-colors disabled:pointer-events-none disabled:bg-rs-disabled-bg disabled:text-rs-disabled-text",
        selected
          ? "bg-rs-tertiary text-rs-primary"
          : "bg-rs-disabled-bg text-rs-ink hover:bg-rs-tertiary",
        className
      )}
      {...props}
    />
  )
)
Chip.displayName = "Chip"

export { Chip }
