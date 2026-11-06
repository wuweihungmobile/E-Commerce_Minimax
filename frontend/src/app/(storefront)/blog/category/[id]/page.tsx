'use client';

import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { getPublishedPosts, getPublicCategories, PostResponse, CategoryResponse } from '@/services/cms';
import { Pagination } from '@/components/ui/pagination';
import { EmptyState, LoadingState } from '@/components/ui/states';
import { Card } from '@/components/ui/card';

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
    <div className="flex-1">
      {/* Header */}
      <header className="bg-white border border-rs-hairline">
        <div className="max-w-4xl mx-auto px-4 py-6">
          <Link href="/blog" className="text-sm text-rs-primary hover:underline">
            ← 返回部落格
          </Link>
          {category && (
            <>
              <h1 className="mt-2 text-3xl font-semibold text-rs-ink" data-testid="blog-category-title">
                {category.name}
              </h1>
              {category.description && (
                <p className="mt-2 text-rs-ink-muted">{category.description}</p>
              )}
            </>
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
        ) : categoryNotFound ? (
          <EmptyState data-testid="blog-category-not-found" title="此分類不存在" />
        ) : loading ? (
          <LoadingState className="h-auto py-12" />
        ) : posts.length === 0 ? (
          <EmptyState data-testid="blog-category-empty" title="此分類尚無已發布的文章" />
        ) : (
          <>
            {/* Post Grid */}
            <div className="grid md:grid-cols-2 gap-6" data-testid="blog-category-grid">
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
            <div className="mt-8">
              <Pagination current={page + 1} total={totalPages} onChange={(p) => setPage(p - 1)} />
            </div>
          </>
        )}
      </main>
    </div>
  );
}
