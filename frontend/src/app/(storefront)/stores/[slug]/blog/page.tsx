import { StoreBlogView } from "@/components/storefront/StoreBlogView"

// 店鋪前台貼文列表（PRD §10 /stores/[slug]/blog；DEF-321(b)，Sprint 252）。
export default async function StoreBlogPage({
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

  return <StoreBlogView slug={slug} page={page} />
}
