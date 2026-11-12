import type { Metadata } from "next"
import { StorefrontShell } from "@/components/layout/StorefrontShell"
import { ListingNotFound } from "@/components/storefront/ListingNotFound"

// 商品／房源不存在（已下架、網址亂填）：真正的 HTTP 404＋noindex，畫面與用戶端備援（ListingDetail）共用 ListingNotFound。
export const metadata: Metadata = { title: "找不到商品" }

export default function ListingNotFoundRoute() {
  return (
    <StorefrontShell>
      <ListingNotFound />
    </StorefrontShell>
  )
}
