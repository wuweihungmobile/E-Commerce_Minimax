'use client';

import { useState, useSyncExternalStore } from 'react';
import Link from 'next/link';
import { getPublishedPosts } from '@/services/cms';
import { useAsyncData } from '@/hooks/useAsyncData';
import { getAuthServerSnapshot, getSelectedTenantIdSnapshot, subscribeAuth } from '@/services/authStore';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/states';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';

export default function BlogPage() {
  // 目前選用的店鋪（localStorage）；SSR／hydration 前是 null，與原本「mount 之後才讀到」相同
  const tenantId = useSyncExternalStore(subscribeAuth, getSelectedTenantIdSnapshot, getAuthServerSnapshot);
  const [page, setPage] = useState(0);
  // 載入失敗顯示錯誤與重新載入（DEF-468：原本只記錄、畫面顯示「尚無已發布的文章」，使用者分不出沒有與出錯）；沒有店鋪時不發請求（enabled）
  const { data, error, loading, reload } = useAsyncData(
    (signal) => getPublishedPosts(tenantId ?? '', { page, size: 12 }, { signal }),
    [tenantId, page],
    {
      enabled: Boolean(tenantId),
      errorMessage: '載入文章失敗',
      onError: (_message, error) => console.error('Failed to load posts:', error),
    }
  );
  const posts = data?.posts ?? [];
  const totalPages = data?.totalPages ?? 0;

  return (
    <div className="flex-1">
      {/* Header */}
      <header className="bg-white border border-rs-hairline">
        <div className="max-w-4xl mx-auto px-4 py-6">
          <h1 className="text-3xl font-semibold text-rs-ink">部落格</h1>
          <p className="mt-2 text-rs-ink-muted">探索最新文章和資訊</p>
        </div>
      </header>

      {/* Content */}
      <main className="max-w-4xl mx-auto px-4 py-8">
        {!tenantId ? (
          <EmptyState
            title="請先登入並設定店鋪以瀏覽部落格"
            action={<Link href="/login" className="text-rs-primary hover:underline">前往登入</Link>}
          />
        ) : loading ? (
          <LoadingState className="h-auto py-12" />
        ) : error !== null ? (
          <ErrorState data-testid="blog-error" title="載入文章失敗，請稍後再試" onRetry={reload} />
        ) : posts.length === 0 ? (
          <EmptyState title="尚無已發布的文章" />
        ) : (
          <>
            {/* Post Grid */}
            <div className="grid md:grid-cols-2 gap-6">
              {posts.map((post) => (
                <Card asChild key={post.id} className="overflow-hidden hover:border-rs-primary transition-colors">
<Link
                  href={`/blog/${post.slug}`}
                >
                  {post.featuredImageUrl && (
                    <div className="aspect-video bg-rs-disabled-bg">
                      {/* eslint-disable-next-line @next/next/no-img-element -- 封面／精選圖是店家輸入的任意網址；next/image 需要 width／height，且網域須事先在 next.config 設定 remotePatterns */}
                      <img
                        src={post.featuredImageUrl}
                        alt={post.title}
                        className="w-full h-full object-cover"
                        onError={(e) => {
                          e.currentTarget.style.display = 'none';
                        }}
                      />
                    </div>
                  )}
                  <div className="p-4">
                    <h2 className="text-xl font-semibold text-rs-ink mb-2">
                      {post.title}
                    </h2>
                    <p className="text-rs-ink-muted text-sm mb-3 line-clamp-2">
                      {post.excerpt || post.content.substring(0, 100) + '...'}
                    </p>
                    <div className="flex items-center justify-between text-sm text-rs-ink-muted">
                      <span>{post.authorName}</span>
                      <span>
                        {post.publishedAt
                          ? new Date(post.publishedAt).toLocaleDateString('zh-TW')
                          : new Date(post.createdAt).toLocaleDateString('zh-TW')}
                      </span>
                    </div>
                  </div>
                </Link>
</Card>
              ))}
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="mt-8 flex justify-center gap-2">
                <Button
                  variant="outline"
                  onClick={() => setPage(Math.max(0, page - 1))}
                  disabled={page === 0}
                >
                  上一頁
                </Button>
                <span className="px-4 py-2">
                  第 {page + 1} 頁，共 {totalPages} 頁
                </span>
                <Button
                  variant="outline"
                  onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                  disabled={page >= totalPages - 1}
                >
                  下一頁
                </Button>
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}