import { StorefrontHeader } from "@/components/layout/StorefrontHeader"
import { StorefrontFooter } from "@/components/layout/StorefrontFooter"

// 賣場店面共用版型（DEF-020）：TOP（Header）+ Content（{children}）+ Bottom（Footer）。
// route-group `(storefront)` 不影響 URL；換頁時 Header/Footer 不重建（App Router nested layout）。
// Header 為 client 葉節點（搜尋/主題/購物車）、Footer 為 server component。
export default function StorefrontLayout({
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
