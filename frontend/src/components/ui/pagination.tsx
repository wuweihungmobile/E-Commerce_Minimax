"use client"

import Link from "next/link"
import { ChevronLeft, ChevronRight } from "lucide-react"
import { cn } from "@/lib/utils"

export interface PaginationProps {
  current?: number
  total?: number
  onChange?: (page: number) => void
  /**
   * 給了就用真正的連結（<a href>）渲染頁碼、上一頁、下一頁（DEF-500，Sprint 341）：搜尋引擎與不執行 JavaScript 的爬蟲
   * 只會沿著連結走，按鈕＋onChange 的分頁讓它們永遠到不了第 2 頁以後的內容。點擊仍是用戶端導覽（不整頁重載）。
   * 連結一律不預取（prefetch=false）：每個分頁連結預取一次就是伺服器多渲染一頁，等於多打一次後端（Sprint 340 的教訓）。
   * 沒給＝按鈕＋onChange（賣家後台等不需要被搜尋引擎爬的列表）。
   */
  hrefFor?: (page: number) => string
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
  hrefFor,
  className,
}: PaginationProps) {
  if (total <= 1) return null
  const pages = buildPages(current, total)

  const go = (page: number) => {
    if (page >= 1 && page <= total && page !== current) onChange?.(page)
  }

  const itemBase =
    "w-12 h-12 flex items-center justify-center rounded-md cursor-pointer transition-colors disabled:cursor-not-allowed disabled:opacity-40"
  const stepStyle = cn(itemBase, "text-rs-ink-muted hover:bg-rs-bg-base hover:text-rs-ink")
  const pageStyle = (active: boolean) =>
    cn(
      itemBase,
      "text-lg",
      active ? "bg-rs-primary text-white" : "text-rs-ink-muted hover:bg-rs-bg-base hover:text-rs-ink"
    )

  if (hrefFor) {
    // 連結模式：目前這一頁不是連結（連到自己沒有意義）；第一頁沒有「上一頁」、最後一頁沒有「下一頁」，畫成停用的連結外觀
    const disabledStep = (label: string, icon: React.ReactNode) => (
      <span role="link" aria-disabled="true" aria-label={label} className={cn(stepStyle, "cursor-not-allowed opacity-40")}>
        {icon}
      </span>
    )
    return (
      <nav className={cn("flex items-center justify-center gap-3", className)} aria-label="分頁">
        {current > 1 ? (
          <Link href={hrefFor(current - 1)} prefetch={false} rel="prev" aria-label="上一頁" className={stepStyle}>
            <ChevronLeft className="w-5 h-5" />
          </Link>
        ) : (
          disabledStep("上一頁", <ChevronLeft className="w-5 h-5" />)
        )}
        {pages.map((p, i) =>
          p === "…" ? (
            <span key={`ellipsis-${i}`} className="w-12 h-12 flex items-center justify-center text-rs-ink-muted" aria-hidden>
              …
            </span>
          ) : p === current ? (
            <span key={p} aria-current="page" className={pageStyle(true)}>
              {p}
            </span>
          ) : (
            <Link key={p} href={hrefFor(p)} prefetch={false} className={pageStyle(false)}>
              {p}
            </Link>
          )
        )}
        {current < total ? (
          <Link href={hrefFor(current + 1)} prefetch={false} rel="next" aria-label="下一頁" className={stepStyle}>
            <ChevronRight className="w-5 h-5" />
          </Link>
        ) : (
          disabledStep("下一頁", <ChevronRight className="w-5 h-5" />)
        )}
      </nav>
    )
  }

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
        className={stepStyle}
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
            className={pageStyle(p === current)}
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
        className={stepStyle}
      >
        <ChevronRight className="w-5 h-5" />
      </button>
    </nav>
  )
}
