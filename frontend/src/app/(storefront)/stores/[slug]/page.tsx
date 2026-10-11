import { StoreHomeView } from "@/components/storefront/StoreHomeView"

// 店鋪前台首頁（PRD §10 /stores/[slug]；§12.7 Guest/Buyer 瀏覽店鋪前台流程；
// DEF-321(b)，Sprint 252）。公開路由，無需登入。
export default async function StorePage({
  params,
}: {
  params: Promise<{ slug: string }>
}) {
  const { slug } = await params
  return <StoreHomeView slug={slug} />
}
