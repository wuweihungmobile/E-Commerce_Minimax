import type { Metadata } from "next"
import { StoreBlogView } from "@/components/storefront/StoreBlogView"
import { loadStoreBlog, peekStoreProfile } from "@/lib/public-data"
import { storeMetadata } from "@/lib/seo"

// 標題用「店名｜部落格」（DEF-475，Sprint 335）；取不到店名就退回原本的頁型標題。
export async function generateMetadata({ params }: { params: Promise<{ slug: string }> }): Promise<Metadata> {
  const { slug } = await params
  return storeMetadata(slug, { section: '部落格', fallbackTitle: '店鋪部落格', path: '/blog' })
}

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
  // 分類篩選（Sprint 337，DEF-486）：?category={分類編號}；沒有或空字串＝全部。格式不在這裡檢查——
  // 列表元件會拿它對照公開的分類清單，對不上（網址亂填、分類已被停用）一律顯示「此分類不存在或已停用」，也不會送出貼文請求。
  const categoryRaw = typeof sp.category === "string" ? sp.category.trim() : ""
  const categoryId = categoryRaw === "" ? undefined : categoryRaw

  // SEO（DEF-500，Sprint 341）：店鋪資料、分類與這一頁的貼文在伺服器端取、隨第一份 HTML 送出；店鋪不存在由
  // stores/[slug]/layout.tsx 回真正的 404；後端暫時不可用就不帶資料，元件退回由瀏覽器自己載入。
  const profile = await peekStoreProfile(slug)
  const blog = profile ? await loadStoreBlog(profile.tenantId, page, categoryId) : {}

  return (
    <StoreBlogView
      slug={slug}
      page={page}
      categoryId={categoryId}
      initialProfile={profile}
      initialCategories={blog.categories}
      initialPosts={blog.posts}
    />
  )
}
