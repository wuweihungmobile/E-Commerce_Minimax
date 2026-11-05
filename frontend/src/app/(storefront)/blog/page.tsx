'use client';

import { useState, useEffect, useCallback } from 'react';
import Link from 'next/link';
import { getPublishedPosts, PostResponse } from '@/services/cms';

export default function BlogPage() {
  const [posts, setPosts] = useState<PostResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [tenantId, setTenantId] = useState<string>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [, setTotalCount] = useState(0);

  useEffect(() => {
    // Get tenantId from localStorage
    const storedTenantId = localStorage.getItem('tenantId');
    if (storedTenantId) {
      setTenantId(storedTenantId);
    }
  }, []);

  const loadPosts = useCallback(async () => {
    if (!tenantId) return;

    setLoading(true);
    try {
      const data = await getPublishedPosts(tenantId, { page, size: 12 });
      setPosts(data.posts);
      setTotalCount(data.totalCount);
      setTotalPages(data.totalPages);
    } catch (error) {
      console.error('Failed to load posts:', error);
    } finally {
      setLoading(false);
    }
  }, [tenantId, page]);

  useEffect(() => {
    if (tenantId) {
      loadPosts();
    }
  }, [tenantId, loadPosts]);

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
          <div className="text-center py-12 text-rs-ink-muted">
            <p>請先登入並設定店鋪以瀏覽部落格</p>
            <Link href="/login" className="text-rs-primary hover:underline mt-2 inline-block">
              前往登入
            </Link>
          </div>
        ) : loading ? (
          <div className="text-center py-12">載入中...</div>
        ) : posts.length === 0 ? (
          <div className="text-center py-12 text-rs-ink-muted">尚無已發布的文章</div>
        ) : (
          <>
            {/* Post Grid */}
            <div className="grid md:grid-cols-2 gap-6">
              {posts.map((post) => (
                <Link
                  key={post.id}
                  href={`/blog/${post.slug}`}
                  className="bg-white rounded-lg overflow-hidden hover:border-rs-primary transition-colors border border-rs-hairline"
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
              ))}
            </div>

            {/* Pagination */}
            {totalPages > 1 && (
              <div className="mt-8 flex justify-center gap-2">
                <button
                  onClick={() => setPage(Math.max(0, page - 1))}
                  disabled={page === 0}
                  className="px-4 py-2 rounded bg-white disabled:opacity-50 hover:bg-rs-bg-base border border-rs-hairline"
                >
                  上一頁
                </button>
                <span className="px-4 py-2">
                  第 {page + 1} 頁，共 {totalPages} 頁
                </span>
                <button
                  onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-4 py-2 rounded bg-white disabled:opacity-50 hover:bg-rs-bg-base border border-rs-hairline"
                >
                  下一頁
                </button>
              </div>
            )}
          </>
        )}
      </main>
    </div>
  );
}