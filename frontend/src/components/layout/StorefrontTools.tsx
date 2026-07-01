import { cn } from "@/lib/utils"

// Tools 區塊（側欄面板）：包裹分類導覽的卡片容器，對齊設計稿 aside 樣式。
// 註：Tools 區塊另含內容頂部的 SortToolbar（於各頁 Content 內使用）。
export function StorefrontTools({
  children,
  className,
}: {
  children: React.ReactNode
  className?: string
}) {
  return (
    <aside
      className={cn(
        "bg-rs-surface border border-rs-hairline rounded-xl py-2",
        className
      )}
    >
      {children}
    </aside>
  )
}
