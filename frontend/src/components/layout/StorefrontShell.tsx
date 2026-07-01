"use client"

import { StorefrontHeader } from "./StorefrontHeader"
import { StorefrontFooter } from "./StorefrontFooter"
import { StorefrontTools } from "./StorefrontTools"

export interface StorefrontShellProps {
  children: React.ReactNode
  /** Tools 側欄內容（通常為 SidebarNav）。提供時顯示雙欄版面（D2：非瀏覽頁不提供則隱藏）。 */
  sidebar?: React.ReactNode
  cartCount?: number
  hotKeywords?: string[]
  onSearch?: (q: string) => void
}

// 全站共用店面版型：TOP（Header）+ 可選 Tools（側欄）+ Content（children）+ Bottom（Footer）。
export function StorefrontShell({
  children,
  sidebar,
  cartCount,
  hotKeywords,
  onSearch,
}: StorefrontShellProps) {
  return (
    <div className="min-h-screen flex flex-col bg-rs-bg-base">
      <StorefrontHeader
        cartCount={cartCount}
        hotKeywords={hotKeywords}
        onSearch={onSearch}
      />
      {sidebar ? (
        <div className="w-full max-w-[1440px] mx-auto px-6 py-8 pb-20 grid grid-cols-1 lg:grid-cols-[240px_1fr] gap-8 items-start flex-1">
          <StorefrontTools className="hidden lg:block sticky top-28">
            {sidebar}
          </StorefrontTools>
          <main className="flex flex-col gap-6 min-w-0">{children}</main>
        </div>
      ) : (
        <main className="w-full max-w-[1440px] mx-auto px-6 py-8 pb-20 flex-1">
          {children}
        </main>
      )}
      <StorefrontFooter />
    </div>
  )
}
