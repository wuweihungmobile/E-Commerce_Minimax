"use client"

import { useEffect, useState, useSyncExternalStore } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
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
import listingService from "@/services/listing"
import AuthService from "@/services/auth"
import {
  subscribeAuth,
  getAuthEmailSnapshot,
  getAuthServerSnapshot,
  notifyAuthChange,
} from "@/services/authStore"
import { subscribeCartChanged } from "@/services/cartEvents"

const DEFAULT_HOT_KEYWORDS = ["極簡生活", "質感家居", "旅宿房型", "香氛療癒", "收納"]

export interface StorefrontHeaderProps {
  hotKeywords?: string[]
  cartHref?: string
}

// TOP 區塊（全站共用，置於 route-group layout）：頂欄（賣家中心/App/通知/幫助/語言/主題）
// + 主頁首（logo + 搜尋 + 熱搜 + 購物車）。DEF-020：搜尋走 URL（router.push `/?keyword=`）、
// 購物車數量自行取得，Header 自足不再由頁面以 props 串接（避免 page-scoped callback）。
export function StorefrontHeader({
  hotKeywords = DEFAULT_HOT_KEYWORDS,
  cartHref = "/cart",
}: StorefrontHeaderProps) {
  const router = useRouter()
  const [cartCount, setCartCount] = useState(0)
  // 帳號區為 auth-aware：以 useSyncExternalStore 讀登入 email（SSR/hydration 回 null → 顯示訪客，
  // client 端切換為實際登入態）。避免 effect 內同步 setState（React 19 嚴格 hooks 禁令）。
  const email = useSyncExternalStore(subscribeAuth, getAuthEmailSnapshot, getAuthServerSnapshot)

  useEffect(() => {
    if (!email) return
    let cancelled = false
    const refresh = () => {
      listingService
        .getCartCount()
        .then((c) => {
          if (!cancelled) setCartCount(c)
        })
        .catch(() => {
          // 購物車數量取得失敗不影響頁首
        })
    }
    refresh()
    // 加購後（詳情頁/購物車）以事件通知即時更新購物車數（layout 不隨導覽 re-render）
    const unsubscribe = subscribeCartChanged(refresh)
    return () => {
      cancelled = true
      unsubscribe()
    }
  }, [email])

  const handleLogout = () => {
    AuthService.clearAuthData()
    notifyAuthChange()
    setCartCount(0)
    router.push("/login")
  }

  const handleSearch = (q: string) => {
    const query = q.trim()
    router.push(query ? `/?keyword=${encodeURIComponent(query)}` : "/")
  }

  return (
    <header className="sticky top-0 z-[70]" data-testid="storefront-header">
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
            {/* 帳號區（auth-aware）：已登入顯示 email/我的訂單/登出；未登入顯示 登入/註冊 */}
            {email ? (
              <span className="inline-flex items-center gap-3" data-testid="header-account">
                <span className="max-w-[140px] truncate opacity-90" title={email}>
                  {email}
                </span>
                <Link href="/orders" className="hover:opacity-80">
                  我的訂單
                </Link>
                <Link href="/addresses" className="hover:opacity-80">
                  地址簿
                </Link>
                <button
                  type="button"
                  onClick={handleLogout}
                  className="cursor-pointer hover:opacity-80"
                  data-testid="header-logout"
                >
                  登出
                </button>
                <span className="opacity-30">|</span>
              </span>
            ) : (
              <span className="inline-flex items-center gap-3" data-testid="header-guest">
                <Link href="/login" className="hover:opacity-80" data-testid="header-login">
                  登入
                </Link>
                <Link href="/register" className="hover:opacity-80">
                  註冊
                </Link>
                <span className="opacity-30">|</span>
              </span>
            )}
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
            <SearchBar onSubmit={handleSearch} />
            {hotKeywords.length > 0 && (
              <div className="flex flex-wrap gap-x-4 h-5 overflow-hidden w-full max-w-[800px] mt-2 pl-0.5">
                <span className="text-xs font-medium text-rs-info flex-none">熱搜</span>
                {hotKeywords.map((kw) => (
                  <button
                    key={kw}
                    type="button"
                    onClick={() => handleSearch(kw)}
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
