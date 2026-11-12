"use client"

import { useMemo } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { Pagination } from "@/components/ui/pagination"
import { Skeleton } from "@/components/ui/skeleton"
import { Chip } from "@/components/ui/chip"
import { ErrorState } from "@/components/ui/states"
import { buttonVariants } from "@/components/ui/button"
import {
  getPublishedPosts,
  getPublicCategories,
  type CategoryListResponse,
  type CategoryResponse,
  type PostListResponse,
} from "@/services/cms"
import { useAsyncData } from "@/hooks/useAsyncData"
import { Card } from "@/components/ui/card"
import { articlePath, storeBlogPath } from "@/lib/article-path"
import { LIST_PAGE_SIZE as PAGE_SIZE } from "@/lib/public-api"

export interface StoreBlogListProps {
  slug: string
  tenantId: string
  /** 0-based 頁碼，由 server page 依 URL `page` 解析（DEF-020 慣例） */
  page: number
  /** 篩選的分類編號，由 server page 依 URL `category` 解析；沒有＝全部（DEF-486，Sprint 337） */
  categoryId?: string
  /** 伺服器端已取好的分類清單與這一頁的貼文（SEO 的伺服器端渲染，DEF-500）：有就直接用、不再請求；沒有（沒取到）就由瀏覽器自己載入 */
  initialCategories?: CategoryListResponse
  initialPosts?: PostListResponse
}

// 公開端點不指定分類的順序：依店主設定的 sortOrder，相同再依名稱，篩選列的順序才穩定
function sortCategories(categories: CategoryResponse[]): CategoryResponse[] {
  return [...categories].sort(
    (a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0) || a.name.localeCompare(b.name, "zh-Hant")
  )
}

function PostsSkeleton() {
  return (
    <div className="grid md:grid-cols-2 gap-6">
      {Array.from({ length: 4 }).map((_, i) => (
        <Skeleton key={i} className="aspect-video w-full rounded-lg" />
      ))}
    </div>
  )
}

// 店鋪前台「貼文」列表（PRD §10 /stores/[slug]/blog；DEF-321(b)，Sprint 252）。
// 直接重用既有公開端點 GET /v2/posts?tenantId=（PostController，已 permitAll，
// 本就支援任意 tenantId），不另建新後端端點。
//
// 分類篩選（Sprint 337，DEF-486）：網址 ?category={分類編號}。原本文章頁的分類徽章連到 /blog/category/{id}，那一頁的 tenantId
// 取自 localStorage（只有登入才有），匿名訪客點了只看到「請先登入並設定店鋪以瀏覽部落格」；這裡的店鋪由網址決定、
// tenantId 由公開的店鋪資料提供，匿名訪客也能用。分類來自 GET /v2/posts/categories（只回啟用中的分類）。
//
// SEO（DEF-500，Sprint 341）：server page 先取好分類與這一頁的貼文以 initialCategories／initialPosts 傳進來，第一份 HTML 就有
// 貼文卡片；分頁是真正的連結（搜尋引擎沿著連結走到第 2 頁以後）。篩選列（分類）仍是按鈕：分類頁是同一批貼文的子集合，
// 完整列表（含分頁）已經涵蓋全部貼文，不需要讓爬蟲另外走一遍分類。
export function StoreBlogList({ slug, tenantId, page, categoryId, initialCategories, initialPosts }: StoreBlogListProps) {
  const router = useRouter()

  // 分類載入失敗時：沒指定分類 → 篩選列不顯示、貼文照常（篩選是附加功能）；
  // 指定了分類 → 無法確認它存不存在，顯示錯誤與重新載入，不把失敗畫成「此分類不存在」（DEF-468 同一原則）。
  const {
    data: categoryData,
    error: categoriesError,
    reload: reloadCategories,
  } = useAsyncData((signal) => getPublicCategories(tenantId, { signal }), [tenantId], {
    errorMessage: "載入分類失敗",
    onError: (_message, err) => console.error("Failed to load categories:", err),
    initialData: initialCategories,
  })
  const categories = useMemo(() => sortCategories(categoryData?.categories ?? []), [categoryData])
  const selectedCategory = categoryId ? categories.find((c) => c.id === categoryId) : undefined

  // 指定了分類時，要等分類載入完、確認它存在，才載入貼文：不存在（含已停用、網址亂填）的分類編號不會送出貼文請求。
  // 失敗（error）與載入中（loading）分開處理：失敗顯示「載入失敗」，載入中顯示骨架
  const { data, error, loading } = useAsyncData(
    (signal) =>
      getPublishedPosts(tenantId, { page, size: PAGE_SIZE, ...(categoryId ? { categoryId } : {}) }, { signal }),
    [tenantId, page, categoryId],
    { enabled: !categoryId || selectedCategory !== undefined, errorMessage: "載入貼文失敗", initialData: initialPosts }
  )

  // 換分類回到第一頁（不帶 page）；翻頁保留目前的分類
  const go = (target: { categoryId?: string; page?: number }) => router.push(storeBlogPath(slug, target))

  const filterBar =
    categories.length > 0 ? (
      <div
        data-testid="store-blog-categories"
        role="group"
        aria-label="文章分類"
        className="flex flex-wrap items-center gap-2"
      >
        <Chip selected={!categoryId} onClick={() => go({})}>
          全部
        </Chip>
        {categories.map((category) => (
          <Chip
            key={category.id}
            selected={category.id === categoryId}
            onClick={() => go({ categoryId: category.id })}
          >
            {category.name}
          </Chip>
        ))}
      </div>
    ) : null

  let content: React.ReactNode
  if (categoryId && categoriesError !== null) {
    content = (
      <ErrorState
        data-testid="store-blog-category-error"
        title="載入分類失敗，請稍後再試"
        onRetry={reloadCategories}
      />
    )
  } else if (categoryId && categoryData === undefined) {
    content = <PostsSkeleton />
  } else if (categoryId && selectedCategory === undefined) {
    content = (
      <div
        data-testid="store-blog-category-not-found"
        className="flex flex-col items-center justify-center py-24 text-center gap-3"
      >
        <p className="text-lg text-rs-ink">此分類不存在或已停用</p>
        <Link href={storeBlogPath(slug)} prefetch={false} className={buttonVariants({ variant: "outline" })}>
          查看全部貼文
        </Link>
      </div>
    )
  } else if (error !== null) {
    content = (
      <div className="flex flex-col items-center justify-center py-24 text-center gap-3">
        <p className="text-lg text-rs-ink">載入失敗，請稍後再試</p>
      </div>
    )
  } else if (loading || data === undefined) {
    content = <PostsSkeleton />
  } else if (data.posts.length === 0) {
    content = (
      <div
        data-testid={categoryId ? "store-blog-category-empty" : "store-blog-empty"}
        className="flex flex-col items-center justify-center py-24 text-center gap-2"
      >
        <p className="text-lg text-rs-ink">
          {categoryId ? "此分類尚無已發布的貼文" : "此店鋪目前沒有已發布的貼文"}
        </p>
      </div>
    )
  } else {
    content = (
      <>
        <div data-testid="store-blog-grid" className="grid md:grid-cols-2 gap-6">
          {data.posts.map((post) => (
            <Card asChild key={post.id} className="flex flex-col overflow-hidden no-underline text-inherit transition-colors hover:border-rs-primary">
<Link
              href={articlePath(slug, post.slug)}
              prefetch={false}
            >
              {post.featuredImageUrl && (
                // eslint-disable-next-line @next/next/no-img-element -- 貼文封面圖為後端動態 URL
                <img
                  src={post.featuredImageUrl}
                  alt={post.title}
                  className="aspect-video w-full object-cover"
                />
              )}
              <div className="p-4 flex flex-col gap-2">
                <h3 className="text-base font-semibold text-rs-ink line-clamp-2">{post.title}</h3>
                <p className="text-sm text-rs-ink-muted line-clamp-2">
                  {post.excerpt || post.content.slice(0, 100)}
                </p>
              </div>
            </Link>
</Card>
          ))}
        </div>
        {data.totalPages > 1 && (
          <Pagination
            current={page + 1}
            total={data.totalPages}
            hrefFor={(oneBased) => storeBlogPath(slug, { categoryId, page: oneBased })}
          />
        )}
      </>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      {filterBar}
      {content}
    </div>
  )
}
