'use client';

import { useState, useEffect } from 'react';
import Link from 'next/link';
import { getPublishedPosts, PostResponse } from '@/services/cms';

export default function BlogPage() {
  const [posts, setPosts] = useState<PostResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [tenantId, setTenantId] = useState<string>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalCount, setTotalCount] = useState(0);

  useEffect(() => {
    // Get tenantId from localStorage
    const storedTenantId = localStorage.getItem('tenantId');
    if (storedTenantId) {
      setTenantId(storedTenantId);
    }
  }, []);

  useEffect(() => {
    if (tenantId) {
      loadPosts();
    }
  }, [tenantId, page]);

  async function loadPosts() {
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
  }

  const getStatusBadge = (status: string) => {
    const styles = {
      DRAFT: 'bg-gray-100 text-gray-800',
      PUBLISHED: 'bg-green-100 text-green-800',
      ARCHIVED: 'bg-yellow-100 text-yellow-800',
    };
    const labels = {
      DRAFT: '草稿',
      PUBLISHED: '已發布',
      ARCHIVED: '已歸檔',
    };
    return (
      <span className={`px-2 py-1 rounded-full text-xs ${styles[status as keyof typeof styles]}`}>
        {labels[status as keyof typeof labels] || status}
      </span>
    );
  };

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow-sm">
        <div className="max-w-4xl mx-auto px-4 py-6">
          <h1 className="text-3xl font-bold text-gray-900">部落格</h1>
          <p className="mt-2 text-gray-600">探索最新文章和資訊</p>
        </div>
      </header>

      {/* Content */}
      <main className="max-w-4xl mx-auto px-4 py-8">
        {!tenantId ? (
          <div className="text-center py-12 text-gray-500">
            <p>請先登入並設定店鋪以瀏覽部落格</p>
            <Link href="/login" className="text-blue-600 hover:underline mt-2 inline-block">
              前往登入
            </Link>
          </div>
        ) : loading ? (
          <div className="text-center py-12">載入中...</div>
        ) : posts.length === 0 ? (
          <div className="text-center py-12 text-gray-500">尚無已發布的文章</div>
        ) : (
          <>
            {/* Post Grid */}
            <div className="grid md:grid-cols-2 gap-6">
              {posts.map((post) => (
                <Link
                  key={post.id}
                  href={`/blog/${post.slug}`}
                  className="bg-white rounded-lg shadow overflow-hidden hover:shadow-md transition-shadow"
                >
                  {post.featuredImageUrl && (
                    <div className="aspect-video bg-gray-100">
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
                    <h2 className="text-xl font-semibold text-gray-900 mb-2">
                      {post.title}
                    </h2>
                    <p className="text-gray-600 text-sm mb-3 line-clamp-2">
                      {post.excerpt || post.content.substring(0, 100) + '...'}
                    </p>
                    <div className="flex items-center justify-between text-sm text-gray-500">
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
                  className="px-4 py-2 rounded bg-white shadow disabled:opacity-50 hover:bg-gray-50"
                >
                  上一頁
                </button>
                <span className="px-4 py-2">
                  第 {page + 1} 頁，共 {totalPages} 頁
                </span>
                <button
                  onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                  disabled={page >= totalPages - 1}
                  className="px-4 py-2 rounded bg-white shadow disabled:opacity-50 hover:bg-gray-50"
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