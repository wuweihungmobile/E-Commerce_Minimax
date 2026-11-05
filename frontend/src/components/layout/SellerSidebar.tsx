"use client"

import { useEffect, useRef, useState } from "react"
import Link from "next/link"
import { usePathname } from "next/navigation"
import { ChevronDown, LayoutGrid } from "lucide-react"
import { cn } from "@/lib/utils"
import { SELLER_AREAS, findActiveHref, type SellerArea } from "./sellerNav"

// 賣家外殼的側欄：沿用設計稿 SidebarNav 的語言（標題＋細線、左側 4px 色條標示目前頁、hover 淺底），
// 項目是連結（路由）而不是設計稿原本的「選分類」按鈕，所以另立元件而非重用 storefront/SidebarNav。
//
// 只渲染「一份」導覽（不是桌機一份、手機一份靠 CSS 切換）：兩份 DOM 會讓同一個 data-testid／連結名稱出現兩次，
// Playwright strict mode 會直接失敗。手機寬度改成可收合：預設收起，點標題列展開。
export function SellerSidebar({ area }: { area: SellerArea }) {
  const pathname = usePathname() ?? ""
  const [open, setOpen] = useState(false)
  const config = SELLER_AREAS[area]
  const activeHref = findActiveHref(pathname, config)
  const asideRef = useRef<HTMLElement | null>(null)
  const activeRef = useRef<HTMLAnchorElement | null>(null)

  // 項目多時側欄自己會捲動（lg 以上有 max-h）：目前頁若在可視範圍之外，把它捲到側欄中間，使用者一進頁就看得到「我在哪」。
  // 只動側欄自己的 scrollTop，不用 scrollIntoView（它會連整個頁面一起捲）。手機寬度側欄不是捲動容器，賦值無效、無副作用。
  useEffect(() => {
    const aside = asideRef.current
    const el = activeRef.current
    if (!aside || !el) return
    aside.scrollTop = Math.max(0, el.offsetTop - aside.clientHeight / 2 + el.offsetHeight / 2)
  }, [activeHref])

  return (
    <aside
      ref={asideRef}
      className="bg-rs-surface border border-rs-hairline rounded-xl py-2 lg:sticky lg:top-24 lg:max-h-[calc(100vh-7rem)] lg:overflow-y-auto"
      data-testid="seller-sidebar"
    >
      <button
        type="button"
        aria-expanded={open}
        aria-controls="seller-nav-list"
        onClick={() => setOpen((v) => !v)}
        className="lg:hidden flex w-full items-center justify-between px-3 py-2 text-base font-semibold text-rs-ink"
      >
        <span className="flex items-center gap-2">
          <LayoutGrid className="w-4 h-4 text-rs-primary" aria-hidden />
          {config.navTitle}
        </span>
        <ChevronDown className={cn("w-4 h-4 transition-transform", open && "rotate-180")} aria-hidden />
      </button>
      <nav
        id="seller-nav-list"
        aria-label={config.navTitle}
        className={cn(open ? "block" : "hidden", "lg:block")}
      >
        <div className="hidden lg:flex items-center gap-2 text-base font-semibold text-rs-ink px-3 py-2">
          <LayoutGrid className="w-4 h-4 text-rs-primary" aria-hidden />
          <span>{config.navTitle}</span>
        </div>
        {config.sections.map((section, i) => (
          <div key={section.title ?? `section-${i}`}>
            <div className="h-px bg-rs-hairline my-2" />
            {section.title && (
              <p className="px-3 pt-1 pb-1 text-xs font-medium text-rs-ink-muted">{section.title}</p>
            )}
            <ul className="flex flex-col">
              {section.items.map((item) => {
                const isActive = item.href === activeHref
                return (
                  <li key={item.href}>
                    <Link
                      ref={isActive ? activeRef : undefined}
                      href={item.href}
                      data-testid={item.testId}
                      aria-current={isActive ? "page" : undefined}
                      onClick={() => setOpen(false)}
                      className={cn(
                        "flex items-center px-3 py-2.5 text-sm text-rs-ink border-l-4 border-transparent transition-colors hover:bg-rs-tertiary",
                        isActive && "bg-rs-tertiary border-l-rs-primary font-medium"
                      )}
                    >
                      {item.label}
                    </Link>
                  </li>
                )
              })}
            </ul>
          </div>
        ))}
      </nav>
    </aside>
  )
}
