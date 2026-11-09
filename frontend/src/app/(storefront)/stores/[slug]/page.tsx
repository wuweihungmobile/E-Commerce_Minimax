import type { Metadata } from "next"
import { StoreHomeView } from "@/components/storefront/StoreHomeView"
import { storeMetadata } from "@/lib/seo"

// 標題用店名（DEF-475，Sprint 335）；取不到（店鋪不存在、後端慢或壞）就退回原本的頁型標題。
export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params
  return storeMetadata(slug, { fallbackTitle: '店鋪首頁', path: '' })
}

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
