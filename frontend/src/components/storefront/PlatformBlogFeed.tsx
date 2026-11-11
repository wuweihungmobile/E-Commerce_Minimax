"use client"

import Link from "next/link"
import { useRouter } from "next/navigation"
import { Badge } from "@/components/ui/badge"
import { buttonVariants } from "@/components/ui/button"
import { Card } from "@/components/ui/card"
import { Pagination } from "@/components/ui/pagination"
import { EmptyState, ErrorState, LoadingState } from "@/components/ui/states"
import { getPublicPostFeed } from "@/services/cms"
import { useAsyncData } from "@/hooks/useAsyncData"
import { articlePath, platformBlogPath } from "@/lib/article-path"

const PAGE_SIZE = 12

// 平台層部落格 /blog（Sprint 339，DEF-495，使用者選了『做成跨店鋪部落格』）：所有營業中店鋪（ACTIVE）的已發布貼文，
// 新到舊，匿名可讀。資料來自 GET /v2/public/posts（只有卡片需要的摘要欄位）。
//
// 之前這一頁用瀏覽器 localStorage 的店鋪（只有登入才有）取「那一間店鋪」的貼文，匿名訪客只看到登入提示。
// 現在不依賴登入狀態、也不送 tenantId；每張卡片帶店鋪名稱，連到「那間店鋪」的文章頁 /stores/{店鋪}/blog/{文章}。
// 分類是店鋪內的概念，這裡沒有分類篩選（要篩選請進該店鋪的貼文列表 /stores/{店鋪}/blog?category=）。
//
// 頁碼由網址 ?page= 決定（server page 解析成 0-based 交給這裡），翻頁用 router.push：可分享、上一頁會回來。
export function PlatformBlogFeed({ page }: { page: number }) {
  const router = useRouter()
  // 載入失敗顯示錯誤與重新載入（DEF-468 的原則：使用者分不出「沒有文章」與「出錯」）
  const { data, error, loading, reload } = useAsyncData(
    (signal) => getPublicPostFeed({ page, size: PAGE_SIZE }, { signal }),
    [page],
    {
      errorMessage: "載入文章失敗",
      onError: (_message, err) => console.error("Failed to load the platform blog feed:", err),
    }
  )

  let content: React.ReactNode
  if (error !== null) {
    content = <ErrorState data-testid="blog-error" title="載入文章失敗，請稍後再試" onRetry={reload} />
  } else if (loading || data === undefined) {
    content = <LoadingState className="h-auto py-12" />
  } else if (data.posts.length === 0 && data.totalCount > 0) {
    // 網址的頁碼超出範圍（?page=999）：有文章，只是這一頁沒有；畫成「還沒有文章」會誤導，也沒有出路
    content = (
      <EmptyState
        data-testid="blog-page-out-of-range"
        title="這一頁沒有文章"
        action={
          <Link href={platformBlogPath()} className={buttonVariants({ variant: "outline" })}>
            回到第一頁
          </Link>
        }
      />
    )
  } else if (data.posts.length === 0) {
    content = <EmptyState data-testid="blog-empty" title="目前還沒有已發布的文章" />
  } else {
    content = (
      <>
        <div data-testid="blog-grid" className="grid md:grid-cols-2 gap-6">
          {data.posts.map((post) => (
            <Card asChild key={post.id} className="overflow-hidden hover:border-rs-primary transition-colors">
              <Link href={articlePath(post.tenantSlug, post.slug)}>
                {post.featuredImageUrl && (
                  <div className="aspect-video bg-rs-disabled-bg">
                    {/* eslint-disable-next-line @next/next/no-img-element -- 封面圖是店家輸入的任意網址；next/image 需要 width／height，且網域須事先在 next.config 設定 remotePatterns */}
                    <img
                      src={post.featuredImageUrl}
                      alt={post.title}
                      className="w-full h-full object-cover"
                      onError={(e) => {
                        e.currentTarget.style.display = "none"
                      }}
                    />
                  </div>
                )}
                <div className="p-4">
                  <h2 className="text-xl font-semibold text-rs-ink mb-2">{post.title}</h2>
                  {post.excerpt && <p className="text-rs-ink-muted text-sm mb-3 line-clamp-2">{post.excerpt}</p>}
                  <div className="flex items-center justify-between gap-3 text-sm text-rs-ink-muted">
                    <div className="flex min-w-0 items-center gap-2">
                      <span className="truncate">{post.tenantName}</span>
                      {post.categoryName && (
                        <Badge variant="secondary" className="flex-none">
                          {post.categoryName}
                        </Badge>
                      )}
                    </div>
                    {post.publishedAt && (
                      <span className="flex-none">{new Date(post.publishedAt).toLocaleDateString("zh-TW")}</span>
                    )}
                  </div>
                </div>
              </Link>
            </Card>
          ))}
        </div>
        <div className="mt-8">
          <Pagination current={page + 1} total={data.totalPages} onChange={(oneBased) => router.push(platformBlogPath(oneBased))} />
        </div>
      </>
    )
  }

  return (
    <div className="flex-1">
      <header className="bg-white border border-rs-hairline">
        <div className="max-w-4xl mx-auto px-4 py-6">
          <h1 className="text-3xl font-semibold text-rs-ink">部落格</h1>
          <p className="mt-2 text-rs-ink-muted">探索各家店鋪的最新文章和資訊</p>
        </div>
      </header>
      <main className="max-w-4xl mx-auto px-4 py-8">{content}</main>
    </div>
  )
}
