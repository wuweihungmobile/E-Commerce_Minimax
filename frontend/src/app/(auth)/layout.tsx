import type { Metadata } from "next"
import { AuthGuard } from "@/components/layout/AuthGuard"
import { StorefrontHeader } from "@/components/layout/StorefrontHeader"
import { StorefrontFooter } from "@/components/layout/StorefrontFooter"

// 搜尋引擎不收錄（DEF-500，Sprint 341）：這個區域是登入、註冊、購物車、結帳、訂單、帳號等個人頁面，沒有可收錄的公開內容。
// robots 是 metadata 的巢狀欄位，底下各頁沒有自己宣告就沿用這一份。
export const metadata: Metadata = { robots: { index: false, follow: false } }

// 買家（已登入）頁共用版型（S37 AI-1901）：沿用 S36 DEF-020 route-group 範式，
// 承載共用 TOP（Header）+ Content（{children}）+ Bottom（Footer）。
// route-group `(auth)` 不影響 URL；各買家頁不再各自手包 nav/footer。
// Header 為 auth-aware client 葉節點（帳號/購物車/搜尋/主題），Footer 為 server component。
// DEF-413（Sprint 302）：Content 外面包 AuthGuard——除了登入、註冊等認證流程頁面，沒登入就導去 /login?redirect=<原頁>。
export default function AuthLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <div className="min-h-screen flex flex-col bg-rs-bg-base">
      <StorefrontHeader />
      <AuthGuard>{children}</AuthGuard>
      <StorefrontFooter />
    </div>
  )
}
