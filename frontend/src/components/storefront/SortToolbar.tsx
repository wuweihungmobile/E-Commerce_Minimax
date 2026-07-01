"use client"

import { cn } from "@/lib/utils"

export interface SortTab {
  id: string
  label: string
}

export interface SortToolbarProps {
  tabs?: SortTab[]
  activeTab?: string
  onTabChange?: (id: string) => void
  /** 右側附加內容（例如結果數、分頁資訊） */
  right?: React.ReactNode
  className?: string
}

export const DEFAULT_SORT_TABS: SortTab[] = [
  { id: "recommend", label: "綜合排名" },
  { id: "newest", label: "最新" },
  { id: "hotsale", label: "月銷熱賣" },
]

// 賣場排序工具列：左側排序頁籤，右側附加資訊，對齊設計稿 rs-tb。
export function SortToolbar({
  tabs = DEFAULT_SORT_TABS,
  activeTab,
  onTabChange,
  right,
  className,
}: SortToolbarProps) {
  return (
    <div
      className={cn(
        "flex items-center justify-between gap-3 bg-white border border-rs-hairline rounded-lg py-3 pl-5 pr-3",
        className
      )}
    >
      <div className="flex items-center gap-1">
        {tabs.map((tab) => {
          const isActive = tab.id === activeTab
          return (
            <button
              key={tab.id}
              type="button"
              onClick={() => onTabChange?.(tab.id)}
              aria-pressed={isActive}
              className={cn(
                "border-none cursor-pointer text-sm text-rs-ink px-5 py-2 rounded-md transition-colors whitespace-nowrap hover:bg-rs-bg-base",
                isActive && "bg-rs-primary text-white hover:bg-rs-primary"
              )}
            >
              {tab.label}
            </button>
          )
        })}
      </div>
      {right && (
        <div className="flex items-center gap-2 text-sm text-rs-ink-muted">
          {right}
        </div>
      )}
    </div>
  )
}
