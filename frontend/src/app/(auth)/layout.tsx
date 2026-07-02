import { StorefrontHeader } from "@/components/layout/StorefrontHeader"
import { StorefrontFooter } from "@/components/layout/StorefrontFooter"

// 買家（已登入）頁共用版型（S37 AI-1901）：沿用 S36 DEF-020 route-group 範式，
// 承載共用 TOP（Header）+ Content（{children}）+ Bottom（Footer）。
// route-group `(auth)` 不影響 URL；各買家頁不再各自手包 nav/footer。
// Header 為 auth-aware client 葉節點（帳號/購物車/搜尋/主題），Footer 為 server component。
export default function AuthLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <div className="min-h-screen flex flex-col bg-rs-bg-base">
      <StorefrontHeader />
      {children}
      <StorefrontFooter />
    </div>
  )
}
