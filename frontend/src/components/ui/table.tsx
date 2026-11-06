import * as React from "react"

import { cn } from "@/lib/utils"

// 資料表：外框、橫向捲動、表頭底色、列分隔、列 hover 都在這裡決定；頁面只負責欄位與內容。
// 規格沿用 Sprint 328 之前全站最常見的寫法（表頭 12px／500／次要色／uppercase、儲存格 padding 12×16、1px 分隔線），
// 去掉陰影（設計稿「嚴禁陰影，只用 1px 實線框與色差分層」）。
export interface TableProps extends React.HTMLAttributes<HTMLTableElement> {
  /**
   * default：列表（儲存格 padding 12×16）。
   * compact：資料錄入表（儲存格內含輸入框、欄位多，padding 8×12），例如採購單的訂貨品項。
   */
  density?: "default" | "compact"
}

const Table = React.forwardRef<HTMLTableElement, TableProps>(
  ({ className, density = "default", ...props }, ref) => (
    <div className="w-full overflow-x-auto rounded-lg border border-rs-hairline bg-rs-surface">
      <table
        ref={ref}
        className={cn(
          "min-w-full divide-y divide-rs-hairline text-left",
          // 後代選擇器（特異性 0,1,1）比儲存格自己的 px-4 py-3（0,1,0）優先
          density === "compact" && "[&_td]:px-3 [&_td]:py-2 [&_th]:px-3 [&_th]:py-2",
          className
        )}
        {...props}
      />
    </div>
  )
)
Table.displayName = "Table"

const TableHeader = React.forwardRef<
  HTMLTableSectionElement,
  React.HTMLAttributes<HTMLTableSectionElement>
>(({ className, ...props }, ref) => (
  <thead ref={ref} className={cn("bg-rs-bg-base", className)} {...props} />
))
TableHeader.displayName = "TableHeader"

const TableBody = React.forwardRef<
  HTMLTableSectionElement,
  React.HTMLAttributes<HTMLTableSectionElement>
>(({ className, ...props }, ref) => (
  <tbody
    ref={ref}
    className={cn("divide-y divide-rs-hairline [&>tr:hover]:bg-rs-bg-base", className)}
    {...props}
  />
))
TableBody.displayName = "TableBody"

const TableFooter = React.forwardRef<
  HTMLTableSectionElement,
  React.HTMLAttributes<HTMLTableSectionElement>
>(({ className, ...props }, ref) => (
  <tfoot ref={ref} className={cn("bg-rs-bg-base", className)} {...props} />
))
TableFooter.displayName = "TableFooter"

const TableRow = React.forwardRef<HTMLTableRowElement, React.HTMLAttributes<HTMLTableRowElement>>(
  ({ className, ...props }, ref) => (
    <tr ref={ref} className={cn("transition-colors", className)} {...props} />
  )
)
TableRow.displayName = "TableRow"

const TableHead = React.forwardRef<
  HTMLTableCellElement,
  React.ThHTMLAttributes<HTMLTableCellElement>
>(({ className, ...props }, ref) => (
  <th
    ref={ref}
    className={cn(
      "px-4 py-3 text-left text-xs font-medium uppercase text-rs-ink-muted",
      className
    )}
    {...props}
  />
))
TableHead.displayName = "TableHead"

const TableCell = React.forwardRef<
  HTMLTableCellElement,
  React.TdHTMLAttributes<HTMLTableCellElement>
>(({ className, ...props }, ref) => (
  <td ref={ref} className={cn("px-4 py-3", className)} {...props} />
))
TableCell.displayName = "TableCell"

export { Table, TableHeader, TableBody, TableFooter, TableRow, TableHead, TableCell }
