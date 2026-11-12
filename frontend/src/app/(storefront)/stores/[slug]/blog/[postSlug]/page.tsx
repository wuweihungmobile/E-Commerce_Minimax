import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { StoreArticleView } from "@/components/storefront/StoreArticleView"
import { postBySlugPath, UUID_PATTERN } from "@/lib/public-api"
import { loadEmbeddedCards, peekStoreProfile } from "@/lib/public-data"
import { JsonLd } from "@/components/seo/JsonLd"
import { articleJsonLd, postMetadata } from "@/lib/seo"
import { dataOrUndefined, fetchPublic } from "@/lib/server-api"
import type { PostResponse } from "@/services/cms"

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
//
// SEO（DEF-500，Sprint 341）：文章全文與內嵌的商品卡片在「伺服器端」取、隨第一份 HTML 送出（以前是「載入中...」，
// 搜尋引擎與不執行 JavaScript 的爬蟲看不到內文）。取文章帶 countView=false（爬蟲與連結預覽不是一次瀏覽），
// 瀏覽器載入頁面時才計一次（見 StoreArticle）。
//   - 店鋪不存在（含停權）：只由 stores/[slug]/layout.tsx 判定並回真正的 404（畫面是「找不到這間店鋪」）；
//     這一頁用 peekStoreProfile（不丟 notFound），否則會被離這一頁最近的 not-found 邊界接走，變成「找不到文章」。
//   - 文章不存在、草稿、已下架（後端 404）：這裡 notFound()＝真正的 404（[postSlug]/not-found.tsx 畫面與備援共用 ArticleNotFound）。
//   - 後端暫時不可用（逾時、429、5xx）：絕不回 404，不帶資料，由瀏覽器自己載入。
export default async function StoreArticlePage({
  params,
}: {
  params: Promise<{ slug: string; postSlug: string }>
}) {
  const { slug, postSlug } = await params
  const profile = await peekStoreProfile(slug)
  let post: PostResponse | undefined
  if (profile && UUID_PATTERN.test(profile.tenantId)) {
    const result = await fetchPublic<PostResponse>(postBySlugPath(postSlug, profile.tenantId))
    if (result.status === "notfound") notFound()
    post = dataOrUndefined(result)
  }
  const cards = post ? await loadEmbeddedCards(post.content) : undefined

  return (
    <>
      {/* 文章的結構化資料（DEF-500）：標題、說明、發布日期、作者與所屬的店鋪部落格 */}
      <JsonLd data={post && profile ? articleJsonLd(slug, postSlug, profile.name, post) : null} />
      <StoreArticleView
        slug={slug}
        postSlug={postSlug}
        initialProfile={profile}
        initialPost={post}
        initialCards={cards}
      />
    </>
  )
}
