'use client';

import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { getPublishedPosts, getPublicCategories, PostResponse, CategoryResponse } from '@/services/cms';
import { Pagination } from '@/components/ui/pagination';

const PAGE_SIZE = 12;

export default function BlogCategoryPage() {
  const params = useParams();
  const categoryId = params.id as string;

  const [posts, setPosts] = useState<PostResponse[]>([]);
  const [category, setCategory] = useState<CategoryResponse | null>(null);
  const [categoryNotFound, setCategoryNotFound] = useState(false);
  const [loading, setLoading] = useState(true);
  const [tenantId, setTenantId] = useState<string>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    const storedTenantId = localStorage.getItem('tenantId');
    if (storedTenantId) {
      setTenantId(storedTenantId);
    }
  }, []);

  const loadCategory = useCallback(async () => {
    try {
      // 公開端點只回傳啟用中分類；找不到就視為「此分類不存在」（含店主已停用的情況）
      const data = await getPublicCategories(tenantId);
      const found = data.categories.find((c) => c.id === categoryId);
      if (found) {
        setCategory(found);
      } else {
        setCategoryNotFound(true);
      }
    } catch (error) {
      console.error('Failed to load category:', error);
      setCategoryNotFound(true);
    }
  }, [tenantId, categoryId]);

  useEffect(() => {
    if (tenantId && categoryId) {
      loadCategory();
    }
  }, [tenantId, categoryId, loadCategory]);

  const loadPosts = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getPublishedPosts(tenantId, { page, size: PAGE_SIZE, categoryId });
      setPosts(data.posts);
      setTotalPages(data.totalPages);
    } catch (error) {
      console.error('Failed to load posts:', error);
    } finally {
      setLoading(false);
    }
  }, [tenantId, categoryId, page]);

  useEffect(() => {
    if (tenantId && categoryId && !categoryNotFound) {
      loadPosts();
    }
  }, [tenantId, categoryId, categoryNotFound, loadPosts]);

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow-sm">
        <div className="max-w-4xl mx-auto px-4 py-6">
          <Link href="/blog" className="text-sm text-blue-600 hover:underline">
            ← 返回部落格
          </Link>
          {category && (
            <>
              <h1 className="mt-2 text-3xl font-bold text-gray-900" data-testid="blog-category-title">
                {category.name}
              </h1>
              {category.description && (
                <p className="mt-2 text-gray-600">{category.description}</p>
              )}
            </>
          )}
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
        ) : categoryNotFound ? (
          <div className="text-center py-12 text-gray-500" data-testid="blog-category-not-found">
            此分類不存在
          </div>
        ) : loading ? (
          <div className="text-center py-12">載入中...</div>
        ) : posts.length === 0 ? (
          <div className="text-center py-12 text-gray-500" data-testid="blog-category-empty">
            此分類尚無已發布的文章
          </div>
        ) : (
          <>
            {/* Post Grid */}
            <div className="grid md:grid-cols-2 gap-6" data-testid="blog-category-grid">
              {posts.map((post) => (
                <Link
                  key={post.id}
                  href={`/blog/${post.slug}`}
                  className="bg-white rounded-lg shadow overflow-hidden hover:shadow-md transition-shadow"
                >
                  {post.featuredImageUrl && (
                    <div className="aspect-video bg-gray-100">
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
            <div className="mt-8">
              <Pagination current={page + 1} total={totalPages} onChange={(p) => setPage(p - 1)} />
            </div>
          </>
        )}
      </main>
    </div>
  );
}
