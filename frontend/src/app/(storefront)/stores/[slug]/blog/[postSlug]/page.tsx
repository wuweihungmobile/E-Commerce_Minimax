import type { Metadata } from "next"
import { StoreArticleView } from "@/components/storefront/StoreArticleView"
import { postMetadata } from "@/lib/seo"

// 標題用「文章標題｜店名」，另帶 description 與 Open Graph（DEF-483，Sprint 336）；取不到（店鋪或文章不存在、後端慢或壞了）
// 就退回靜態標題「部落格文章」。
export async function generateMetadata({
  params,
}: {
  params: Promise<{ slug: string; postSlug: string }>
}): Promise<Metadata> {
  const { slug, postSlug } = await params
  return postMetadata(slug, postSlug, '部落格文章')
}

// 店鋪前台的單篇文章 /stores/{店鋪}/blog/{文章}（DEF-483，Sprint 336）。原本的 /blog/{文章} 見 app/(storefront)/blog/[slug]。
export default async function StoreArticlePage({
  params,
}: {
  params: Promise<{ slug: string; postSlug: string }>
}) {
  const { slug, postSlug } = await params
  return <StoreArticleView slug={slug} postSlug={postSlug} />
}
