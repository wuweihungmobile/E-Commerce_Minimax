"use client"

import Link from "next/link"
import {
  ArrowDownToLine,
  Bell,
  ChevronDown,
  CircleHelp,
  Droplets,
  Globe,
  ShoppingCart,
  Store,
} from "lucide-react"
import { SearchBar } from "@/components/storefront/SearchBar"
import { Badge } from "@/components/ui/badge"
import { ThemeSwitcher } from "@/components/theme/ThemeSwitcher"

export interface StorefrontHeaderProps {
  cartCount?: number
  hotKeywords?: string[]
  onSearch?: (q: string) => void
  cartHref?: string
}

// TOP 區塊：頂欄（賣家中心/App/通知/幫助/語言/主題）+ 主頁首（logo + 搜尋 + 熱搜 + 購物車）。
export function StorefrontHeader({
  cartCount = 0,
  hotKeywords = [],
  onSearch,
  cartHref = "/cart",
}: StorefrontHeaderProps) {
  return (
    <header className="sticky top-0 z-[70]">
      {/* 頂欄 */}
      <div className="bg-rs-topbar text-rs-topbar-text text-xs">
        <div className="max-w-[1440px] mx-auto px-6 h-8 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Link href="/dashboard" className="inline-flex items-center gap-1 hover:opacity-80">
              <Store className="w-3.5 h-3.5" aria-hidden />
              賣家中心
            </Link>
            <span className="opacity-30">|</span>
            <span className="inline-flex items-center gap-1">
              <ArrowDownToLine className="w-3.5 h-3.5" aria-hidden />
              下載 App
            </span>
          </div>
          <div className="flex items-center gap-3">
            <Link href="/notifications" className="inline-flex items-center gap-1 hover:opacity-80">
              <Bell className="w-3.5 h-3.5" aria-hidden />
              通知
            </Link>
            <span className="inline-flex items-center gap-1">
              <CircleHelp className="w-3.5 h-3.5" aria-hidden />
              幫助中心
            </span>
            <span className="opacity-30">|</span>
            <span className="inline-flex items-center gap-1">
              <Globe className="w-3.5 h-3.5" aria-hidden />
              繁體中文
              <ChevronDown className="w-3.5 h-3.5" aria-hidden />
            </span>
            <span className="opacity-30">|</span>
            <ThemeSwitcher />
          </div>
        </div>
      </div>
      {/* 主頁首 */}
      <div className="bg-rs-header border-b border-rs-hairline">
        <div className="max-w-[1440px] mx-auto px-6 py-4 flex items-center gap-8">
          <Link href="/" className="inline-flex items-center gap-2 flex-none">
            <Droplets className="w-7 h-7 text-rs-primary" aria-hidden />
            <span className="flex flex-col leading-none">
              <span className="text-2xl font-semibold tracking-[2px] text-rs-primary">
                意象若水
              </span>
              <span className="text-[10px] tracking-[4px] mt-[3px] text-rs-primary opacity-70">
                RUOSHUI
              </span>
            </span>
          </Link>
          <div className="flex-1 flex flex-col items-center">
            <SearchBar onSubmit={onSearch} />
            {hotKeywords.length > 0 && (
              <div className="flex flex-wrap gap-x-4 h-5 overflow-hidden w-full max-w-[800px] mt-2 pl-0.5">
                <span className="text-xs font-medium text-rs-info flex-none">熱搜</span>
                {hotKeywords.map((kw) => (
                  <button
                    key={kw}
                    type="button"
                    onClick={() => onSearch?.(kw)}
                    className="text-xs text-rs-ink-muted hover:text-rs-primary"
                  >
                    {kw}
                  </button>
                ))}
              </div>
            )}
          </div>
          <Link
            href={cartHref}
            aria-label="購物車"
            className="relative inline-flex p-1 flex-none"
          >
            <ShoppingCart className="w-6 h-6 text-rs-ink" aria-hidden />
            {cartCount > 0 && (
              <Badge variant="count" className="absolute -top-0.5 -right-1.5">
                {cartCount}
              </Badge>
            )}
          </Link>
        </div>
      </div>
    </header>
  )
}
