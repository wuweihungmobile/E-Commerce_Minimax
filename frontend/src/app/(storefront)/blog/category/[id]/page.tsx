'use client';

import { useState, useSyncExternalStore } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { getPublishedPosts, getPublicCategories } from '@/services/cms';
import { useAsyncData } from '@/hooks/useAsyncData';
import { getAuthServerSnapshot, getSelectedTenantIdSnapshot, subscribeAuth } from '@/services/authStore';
import { Pagination } from '@/components/ui/pagination';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/states';
import { Card } from '@/components/ui/card';
import { articlePath } from '@/lib/article-path';

const PAGE_SIZE = 12;

export default function BlogCategoryPage() {
  const params = useParams();
  const categoryId = params.id as string;

  // 目前選用的店鋪（localStorage）；SSR／hydration 前是 null，與原本「mount 之後才讀到」相同
  const tenantId = useSyncExternalStore(subscribeAuth, getSelectedTenantIdSnapshot, getAuthServerSnapshot);
  const [page, setPage] = useState(0);

  // 公開端點只回傳啟用中分類；成功的回應裡找不到就是「此分類不存在」（含店主已停用的情況）。
  // 載入失敗是另一回事（DEF-468：原本也當成不存在，使用者以為分類被刪了）：顯示錯誤與重新載入。
  // category 是 null＝載入完成但沒有這個分類；undefined＝還沒載入、失敗、或沒有發請求
  const { data: category, error: categoryError, reload: reloadCategory } = useAsyncData(
    async (signal) => {
      const data = await getPublicCategories(tenantId ?? '', { signal });
      return data.categories.find((c) => c.id === categoryId) ?? null;
    },
    [tenantId, categoryId],
    {
      enabled: Boolean(tenantId && categoryId),
      errorMessage: '載入分類失敗',
      onError: (_message, error) => console.error('Failed to load category:', error),
    }
  );
  const categoryNotFound = category === null;

  // 文章與分類同時載入（分類確定不存在、或查分類失敗就不需要文章；重新載入分類成功後文章才會跟著載入）。
  // 文章載入失敗顯示錯誤與重新載入（DEF-468：原本畫面顯示「此分類尚無已發布的文章」）
  const { data: postsData, error: postsError, loading, reload: reloadPosts } = useAsyncData(
    (signal) => getPublishedPosts(tenantId ?? '', { page, size: PAGE_SIZE, categoryId }, { signal }),
    [tenantId, categoryId, page],
    {
      enabled: Boolean(tenantId && categoryId) && !categoryNotFound && categoryError === null,
      errorMessage: '載入文章失敗',
      onError: (_message, error) => console.error('Failed to load posts:', error),
    }
  );
  const posts = postsData?.posts ?? [];
  const totalPages = postsData?.totalPages ?? 0;

  return (
    <div className="flex-1">
      {/* Header */}
      <header className="bg-white border border-rs-hairline">
        <div className="max-w-4xl mx-auto px-4 py-6">
          <Link href="/blog" className="text-sm text-rs-primary hover:underline">
            ← 返回部落格
          </Link>
          {category ? (
            <>
              <h1 className="mt-2 text-3xl font-semibold text-rs-ink" data-testid="blog-category-title">
                {category.name}
              </h1>
              {category.description && (
                <p className="mt-2 text-rs-ink-muted">{category.description}</p>
              )}
            </>
          ) : (
            // 分類還沒載入、不存在、或沒有店鋪時也要有頁面主標題（DEF-459）
            <h1 className="mt-2 text-3xl font-semibold text-rs-ink">文章分類</h1>
          )}
        </div>
      </header>

      {/* Content */}
      <main className="max-w-4xl mx-auto px-4 py-8">
        {!tenantId ? (
          <EmptyState
            title="請先登入並設定店鋪以瀏覽部落格"
            action={<Link href="/login" className="text-rs-primary hover:underline">前往登入</Link>}
          />
        ) : categoryError !== null ? (
          <ErrorState data-testid="blog-category-error" title="載入分類失敗，請稍後再試" onRetry={reloadCategory} />
        ) : categoryNotFound ? (
          <EmptyState data-testid="blog-category-not-found" title="此分類不存在" />
        ) : loading ? (
          <LoadingState className="h-auto py-12" />
        ) : postsError !== null ? (
          <ErrorState data-testid="blog-category-error" title="載入文章失敗，請稍後再試" onRetry={reloadPosts} />
        ) : posts.length === 0 ? (
          <EmptyState data-testid="blog-category-empty" title="此分類尚無已發布的文章" />
        ) : (
          <>
            {/* Post Grid */}
            <div className="grid md:grid-cols-2 gap-6" data-testid="blog-category-grid">
              {posts.map((post) => (
                <Card asChild key={post.id} className="overflow-hidden hover:border-rs-primary transition-colors">
<Link
                  href={articlePath(post.tenantSlug, post.slug)}
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
            <div className="mt-8">
              <Pagination current={page + 1} total={totalPages} onChange={(p) => setPage(p - 1)} />
            </div>
          </>
        )}
      </main>
    </div>
  );
}
