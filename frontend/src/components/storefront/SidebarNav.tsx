"use client"

import { LayoutGrid } from "lucide-react"
import { cn } from "@/lib/utils"

export interface SidebarNavItem {
  id: string
  label: string
  count?: number
  icon?: React.ReactNode
}

export interface SidebarNavProps {
  title?: string
  items: SidebarNavItem[]
  active?: string
  onSelect?: (id: string) => void
  className?: string
}

// 賣場分類側欄：左側色條標示 active，hover 淺底，對齊設計稿 rs-side。
export function SidebarNav({
  title = "所有分類",
  items,
  active,
  onSelect,
  className,
}: SidebarNavProps) {
  return (
    <nav
      className={cn("w-full bg-transparent", className)}
      aria-label={title}
    >
      <div className="flex items-center gap-2 text-base font-semibold text-rs-ink px-3 py-2">
        <LayoutGrid className="w-4 h-4 text-rs-primary" aria-hidden />
        <span>{title}</span>
      </div>
      <div className="h-px bg-rs-hairline my-2" />
      <ul className="flex flex-col">
        {items.map((item) => {
          const isActive = item.id === active
          return (
            <li key={item.id}>
              <button
                type="button"
                onClick={() => onSelect?.(item.id)}
                aria-current={isActive ? "true" : undefined}
                className={cn(
                  "w-full flex items-center justify-between gap-2 px-3 py-2.5 text-sm text-rs-ink cursor-pointer transition-colors border-l-4 border-transparent hover:bg-rs-tertiary",
                  isActive &&
                    "bg-rs-tertiary border-l-rs-primary font-medium"
                )}
              >
                <span className="flex items-center gap-2 min-w-0">
                  {item.icon}
                  <span className="truncate">{item.label}</span>
                </span>
                {item.count !== undefined && (
                  <span className="text-rs-ink-muted text-xs flex-none">
                    {item.count}
                  </span>
                )}
              </button>
            </li>
          )
        })}
      </ul>
    </nav>
  )
}
