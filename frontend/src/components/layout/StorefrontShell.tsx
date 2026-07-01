import { StorefrontTools } from "./StorefrontTools"

export interface StorefrontShellProps {
  children: React.ReactNode
  /** Tools 側欄內容（通常為 SidebarNav）。提供時顯示雙欄版面（D2：非瀏覽頁不提供則隱藏）。 */
  sidebar?: React.ReactNode
}

// 內容區版型（Content 網格）：可選 Tools 側欄 + Content。
// DEF-020：TOP（Header）/ Bottom（Footer）已移至 route-group layout；本元件僅負責
// 「Tools + Content」網格，為 server component（由 client 頁面 import 時亦可用）。
export function StorefrontShell({ children, sidebar }: StorefrontShellProps) {
  if (sidebar) {
    return (
      <div className="w-full max-w-[1440px] mx-auto px-6 py-8 pb-20 grid grid-cols-1 lg:grid-cols-[240px_1fr] gap-8 items-start flex-1">
        <StorefrontTools className="hidden lg:block sticky top-28">
          {sidebar}
        </StorefrontTools>
        <main className="flex flex-col gap-6 min-w-0">{children}</main>
      </div>
    )
  }
  return (
    <main className="w-full max-w-[1440px] mx-auto px-6 py-8 pb-20 flex-1">
      {children}
    </main>
  )
}
