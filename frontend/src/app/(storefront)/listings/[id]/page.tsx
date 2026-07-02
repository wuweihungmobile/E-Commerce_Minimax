import { StorefrontShell } from "@/components/layout/StorefrontShell"
import { ListingDetail } from "@/components/storefront/ListingDetail"

// 買家商品詳情頁（S38 AI-2103）：置於 (storefront) group，沿用共用 Header/Footer + StorefrontShell。
// 公開路由；未登入/401 由 ListingDetail 顯示登入引導。Next 16：params 為 Promise。
export default async function ListingDetailPage({
  params,
}: {
  params: Promise<{ id: string }>
}) {
  const { id } = await params
  return (
    <StorefrontShell>
      <ListingDetail id={id} />
    </StorefrontShell>
  )
}
