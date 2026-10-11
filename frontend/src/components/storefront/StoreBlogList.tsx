"use client"

import { useEffect, useState } from "react"
import { useRouter } from "next/navigation"
import Link from "next/link"
import { Pagination } from "@/components/ui/pagination"
import { Skeleton } from "@/components/ui/skeleton"
import { getPublishedPosts, type PostResponse } from "@/services/cms"

const PAGE_SIZE = 12

export interface StoreBlogListProps {
  slug: string
  tenantId: string
  /** 0-based 頁碼，由 server page 依 URL `page` 解析（DEF-020 慣例） */
  page: number
}

// 店鋪前台「貼文」列表（PRD §10 /stores/[slug]/blog；DEF-321(b)，Sprint 252）。
// 直接重用既有公開端點 GET /v2/posts?tenantId=（PostController，已 permitAll，
// 本就支援任意 tenantId），不另建新後端端點。
export function StoreBlogList({ slug, tenantId, page }: StoreBlogListProps) {
  const router = useRouter()
  const [posts, setPosts] = useState<PostResponse[] | null>(null)
  const [totalPages, setTotalPages] = useState(0)
  const [error, setError] = useState(false)

  useEffect(() => {
    let cancelled = false
    getPublishedPosts(tenantId, { page, size: PAGE_SIZE })
      .then((data) => {
        if (cancelled) return
        setPosts(data.posts)
        setTotalPages(data.totalPages)
        setError(false)
      })
      .catch(() => {
        if (cancelled) return
        setPosts(null)
        setError(true)
      })
    return () => {
      cancelled = true
    }
  }, [tenantId, page])

  const handlePageChange = (oneBased: number) => {
    const params = new URLSearchParams()
    if (oneBased > 1) params.set("page", String(oneBased))
    const qs = params.toString()
    router.push(`/stores/${slug}/blog${qs ? `?${qs}` : ""}`)
  }

  if (error) {
    return (
      <div className="flex flex-col items-center justify-center py-24 text-center gap-3">
        <p className="text-lg text-rs-ink">載入失敗，請稍後再試</p>
      </div>
    )
  }

  if (posts === null) {
    return (
      <div className="grid md:grid-cols-2 gap-6">
        {Array.from({ length: 4 }).map((_, i) => (
          <Skeleton key={i} className="aspect-video w-full rounded-lg" />
        ))}
      </div>
    )
  }

  if (posts.length === 0) {
    return (
      <div
        data-testid="store-blog-empty"
        className="flex flex-col items-center justify-center py-24 text-center gap-2"
      >
        <p className="text-lg text-rs-ink">此店鋪目前沒有已發布的貼文</p>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-6">
      <div data-testid="store-blog-grid" className="grid md:grid-cols-2 gap-6">
        {posts.map((post) => (
          <Link
            key={post.id}
            href={`/blog/${post.slug}`}
            className="flex flex-col bg-white border border-rs-hairline rounded-lg overflow-hidden no-underline text-inherit transition-colors hover:border-rs-primary"
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
        ))}
      </div>
      {totalPages > 1 && (
        <Pagination current={page + 1} total={totalPages} onChange={handlePageChange} />
      )}
    </div>
  )
}
