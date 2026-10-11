import { StoreProductsView } from "@/components/storefront/StoreProductsView"

// 店鋪前台商品列表（PRD §10 /stores/[slug]/products；DEF-321(b)，Sprint 252）。
export default async function StoreProductsPage({
  params,
  searchParams,
}: {
  params: Promise<{ slug: string }>
  searchParams: Promise<{ [key: string]: string | string[] | undefined }>
}) {
  const { slug } = await params
  const sp = await searchParams
  const pageRaw = typeof sp.page === "string" ? parseInt(sp.page, 10) : 1
  const page = Number.isFinite(pageRaw) && pageRaw > 0 ? pageRaw - 1 : 0

  return <StoreProductsView slug={slug} page={page} />
}
