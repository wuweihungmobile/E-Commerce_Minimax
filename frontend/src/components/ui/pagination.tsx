"use client"

import { ChevronLeft, ChevronRight } from "lucide-react"
import { cn } from "@/lib/utils"

export interface PaginationProps {
  current?: number
  total?: number
  onChange?: (page: number) => void
  className?: string
}

// 產生分頁項目：首、尾、當前±1，其餘以省略號表示。
function buildPages(current: number, total: number): (number | "…")[] {
  if (total <= 7) {
    return Array.from({ length: total }, (_, i) => i + 1)
  }
  const pages: (number | "…")[] = [1]
  const start = Math.max(2, current - 1)
  const end = Math.min(total - 1, current + 1)
  if (start > 2) pages.push("…")
  for (let i = start; i <= end; i++) pages.push(i)
  if (end < total - 1) pages.push("…")
  pages.push(total)
  return pages
}

export function Pagination({
  current = 1,
  total = 1,
  onChange,
  className,
}: PaginationProps) {
  if (total <= 1) return null
  const pages = buildPages(current, total)

  const go = (page: number) => {
    if (page >= 1 && page <= total && page !== current) onChange?.(page)
  }

  const itemBase =
    "w-12 h-12 flex items-center justify-center rounded-md cursor-pointer transition-colors disabled:cursor-not-allowed disabled:opacity-40"

  return (
    <nav
      className={cn("flex items-center justify-center gap-3", className)}
      aria-label="分頁"
    >
      <button
        type="button"
        onClick={() => go(current - 1)}
        disabled={current <= 1}
        aria-label="上一頁"
        className={cn(itemBase, "text-rs-ink-muted hover:bg-rs-bg-base hover:text-rs-ink")}
      >
        <ChevronLeft className="w-5 h-5" />
      </button>
      {pages.map((p, i) =>
        p === "…" ? (
          <span
            key={`ellipsis-${i}`}
            className="w-12 h-12 flex items-center justify-center text-rs-ink-muted"
            aria-hidden
          >
            …
          </span>
        ) : (
          <button
            key={p}
            type="button"
            onClick={() => go(p)}
            aria-current={p === current ? "page" : undefined}
            className={cn(
              itemBase,
              "text-lg",
              p === current
                ? "bg-rs-primary text-white"
                : "text-rs-ink-muted hover:bg-rs-bg-base hover:text-rs-ink"
            )}
          >
            {p}
          </button>
        )
      )}
      <button
        type="button"
        onClick={() => go(current + 1)}
        disabled={current >= total}
        aria-label="下一頁"
        className={cn(itemBase, "text-rs-ink-muted hover:bg-rs-bg-base hover:text-rs-ink")}
      >
        <ChevronRight className="w-5 h-5" />
      </button>
    </nav>
  )
}
