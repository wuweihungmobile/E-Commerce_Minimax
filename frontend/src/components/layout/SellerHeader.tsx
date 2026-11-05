"use client"

import { useSyncExternalStore } from "react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { Bell, Droplets, Store } from "lucide-react"
import { ThemeSwitcher } from "@/components/theme/ThemeSwitcher"
import AuthService from "@/services/auth"
import {
  getAuthEmailSnapshot,
  getAuthRoleSnapshot,
  getAuthServerSnapshot,
  notifyAuthChange,
  subscribeAuth,
} from "@/services/authStore"
import { SELLER_AREAS, type SellerArea } from "./sellerNav"

// 賣家外殼的 TOP：StorefrontHeader 的「賣家變體」（HOMEPAGE_TEMPLATE_INTEGRATION_FRD §7 階段三）。
// 同一套設計稿頂欄（深色 topbar＋淺色 header、logo、主題切換）；去掉搜尋與購物車，
// 改放「返回賣場」「目前登入者 email (角色)」「登出」。
//
// 為什麼不含搜尋：搜尋鈕是 button[type=submit]，會被後台表單頁 E2E 的泛用選擇器搶走
// （見記憶 e2e-login-page-shared-header-collision）；賣家中心本來就沒有商品搜尋。
export function SellerHeader({ area }: { area: SellerArea }) {
  const router = useRouter()
  const config = SELLER_AREAS[area]
  // 帳號區為 auth-aware：SSR／hydration 一律回 null（不顯示），client 端再切換為實際登入態。
  const email = useSyncExternalStore(subscribeAuth, getAuthEmailSnapshot, getAuthServerSnapshot)
  const role = useSyncExternalStore(subscribeAuth, getAuthRoleSnapshot, getAuthServerSnapshot)

  const handleLogout = async () => {
    await AuthService.logout()
    notifyAuthChange()
    router.push("/login")
  }

  return (
    <header className="sticky top-0 z-[70]" data-testid="seller-header">
      {/* 頂欄 */}
      <div className="bg-rs-topbar text-rs-topbar-text text-xs">
        <div className="max-w-[1440px] mx-auto px-6 h-8 flex items-center justify-between gap-4">
          <div className="flex items-center gap-2">
            <Link href="/" className="inline-flex items-center gap-1 hover:opacity-80 flex-none whitespace-nowrap">
              <Store className="w-3.5 h-3.5" aria-hidden />
              返回賣場
            </Link>
          </div>
          <div className="flex items-center gap-3 min-w-0">
            {email ? (
              <span
                className="max-w-[360px] truncate opacity-90"
                title={email}
                data-testid="seller-header-account"
              >
                {email} {role ? `(${role})` : null}
              </span>
            ) : null}
            <Link href="/notifications" className="inline-flex items-center gap-1 hover:opacity-80 flex-none">
              <Bell className="w-3.5 h-3.5" aria-hidden />
              通知
            </Link>
            {email ? (
              <button
                type="button"
                onClick={handleLogout}
                className="cursor-pointer hover:opacity-80 flex-none"
                data-testid="seller-header-logout"
              >
                登出
              </button>
            ) : null}
            <span className="opacity-30">|</span>
            <ThemeSwitcher />
          </div>
        </div>
      </div>
      {/* 主頁首 */}
      <div className="bg-rs-header border-b border-rs-hairline">
        <div className="max-w-[1440px] mx-auto px-6 py-3 flex items-center gap-4">
          <Link href="/" className="inline-flex items-center gap-2 flex-none">
            <Droplets className="w-7 h-7 text-rs-primary" aria-hidden />
            <span className="flex flex-col leading-none">
              <span className="text-2xl font-semibold tracking-[2px] text-rs-primary">意象若水</span>
              <span className="text-[10px] tracking-[4px] mt-[3px] text-rs-primary opacity-70">RUOSHUI</span>
            </span>
          </Link>
          <span className="h-7 w-px bg-rs-hairline flex-none" aria-hidden />
          <span className="text-lg font-semibold text-rs-ink">{config.title}</span>
        </div>
      </div>
    </header>
  )
}
