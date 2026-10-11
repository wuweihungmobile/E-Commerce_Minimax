import { StoreRoomsView } from "@/components/storefront/StoreRoomsView"

// 店鋪前台房源列表（PRD §10 /stores/[slug]/rooms；DEF-321(b)，Sprint 252）。
export default async function StoreRoomsPage({
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

  return <StoreRoomsView slug={slug} page={page} />
}
