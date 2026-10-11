import Link from "next/link"
import { cn } from "@/lib/utils"

export type StoreNavTab = "home" | "products" | "rooms" | "blog"

const TABS: { id: StoreNavTab; label: string; hrefSuffix: string }[] = [
  { id: "home", label: "首頁", hrefSuffix: "" },
  { id: "products", label: "商品", hrefSuffix: "/products" },
  { id: "rooms", label: "房源", hrefSuffix: "/rooms" },
  { id: "blog", label: "貼文", hrefSuffix: "/blog" },
]

// 店鋪前台分頁導覽（PRD §11.3 <StoreLayout/> navItems；DEF-321(b)，Sprint 252）。
export function StoreNav({ slug, active }: { slug: string; active: StoreNavTab }) {
  return (
    <nav className="flex gap-1 border-b border-rs-hairline" data-testid="store-nav">
      {TABS.map((tab) => (
        <Link
          key={tab.id}
          href={`/stores/${slug}${tab.hrefSuffix}`}
          data-testid={`store-nav-${tab.id}`}
          className={cn(
            "px-4 py-3 text-sm font-medium border-b-2 -mb-px transition-colors",
            active === tab.id
              ? "border-rs-primary text-rs-primary"
              : "border-transparent text-rs-ink-muted hover:text-rs-ink"
          )}
        >
          {tab.label}
        </Link>
      ))}
    </nav>
  )
}
