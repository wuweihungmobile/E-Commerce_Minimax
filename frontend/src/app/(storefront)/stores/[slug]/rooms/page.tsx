import type { Metadata } from "next"
import { StoreRoomsView } from "@/components/storefront/StoreRoomsView"
import { storeMetadata } from "@/lib/seo"

// 標題用「店名｜房源」（DEF-475，Sprint 335）；取不到店名就退回原本的頁型標題。
export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params
  return storeMetadata(slug, { section: '房源', fallbackTitle: '店鋪房源', path: '/rooms' })
}

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
