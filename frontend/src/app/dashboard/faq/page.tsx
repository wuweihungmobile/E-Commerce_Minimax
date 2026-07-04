'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import {
  getFaqArticles,
  deleteFaqArticle,
  getFaqCategories,
  getPinnedFaqArticles,
  searchFaqArticlesWithHighlight,
  FaqArticleDto,
  FaqCategoryDto,
  PageResponse,
} from '@/services/faq';

export default function FaqPage() {
  const [articles, setArticles] = useState<FaqArticleDto[]>([]);
  const [pinnedArticles, setPinnedArticles] = useState<FaqArticleDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [categoryFilter, setCategoryFilter] = useState<string>('');
  const [keyword, setKeyword] = useState<string>('');
  const [categories, setCategories] = useState<FaqCategoryDto[]>([]);
  const [deleting, setDeleting] = useState<string | null>(null);

  useEffect(() => {
    loadCategories();
    loadPinnedArticles();
  }, []);

  useEffect(() => {
    loadArticles();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [page, categoryFilter, keyword]);

  async function loadCategories() {
    try {
      const data = await getFaqCategories();
      setCategories(data);
    } catch (error) {
      console.error('Failed to load FAQ categories:', error);
    }
  }

  async function loadPinnedArticles() {
    try {
      const data = await getPinnedFaqArticles();
      setPinnedArticles(data);
    } catch (error) {
      console.error('Failed to load pinned FAQ articles:', error);
    }
  }

  async function loadArticles() {
    setLoading(true);
    try {
      let data: PageResponse<FaqArticleDto>;
      if (keyword) {
        // 關鍵字搜尋改走高亮端點，呈現後端已支援的 <mark> 高亮結果
        data = await searchFaqArticlesWithHighlight({ page, size: 20, keyword });
      } else {
        data = await getFaqArticles({
          page,
          size: 20,
          categoryId: categoryFilter || undefined,
        });
      }
      setArticles(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (error) {
      console.error('Failed to load FAQ articles:', error);
      alert('載入 FAQ 文章失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(articleId: string) {
    if (!confirm('確定要刪除這則 FAQ 嗎？')) return;
    setDeleting(articleId);
    try {
      await deleteFaqArticle(articleId);
      loadArticles();
      loadPinnedArticles();
      alert('刪除成功');
    } catch (error) {
      console.error('Failed to delete FAQ article:', error);
      alert('刪除失敗，請稍後再試');
    } finally {
      setDeleting(null);
    }
  }

  function formatDate(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('zh-TW');
  }

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">FAQ 管理</h1>
        <div className="flex items-center gap-4">
          <Link
            href="/dashboard/faq/categories"
            className="px-4 py-1.5 border rounded text-sm hover:bg-gray-50"
          >
            分類管理
          </Link>
          <div className="text-sm text-gray-500">總計 {totalElements} 則 FAQ</div>
        </div>
      </div>

      {/* Pinned Articles */}
      {pinnedArticles.length > 0 && (
        <div className="mb-6">
          <h2 className="text-sm font-semibold text-gray-700 mb-2">置頂 FAQ</h2>
          <div className="flex flex-wrap gap-2">
            {pinnedArticles.map((article) => (
              <span
                key={article.id}
                className="text-xs bg-yellow-50 border border-yellow-200 text-yellow-800 px-2 py-1 rounded"
              >
                {article.question}
              </span>
            ))}
          </div>
        </div>
      )}

      {/* Filters */}
      <div className="mb-4 flex flex-wrap gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">分類</label>
          <select
            value={categoryFilter}
            onChange={(e) => {
              setCategoryFilter(e.target.value);
              setPage(0);
            }}
            className="border rounded px-3 py-1.5 text-sm"
          >
            <option value="">全部分類</option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id}>
                {cat.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">關鍵字搜尋</label>
          <input
            type="text"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value);
              setPage(0);
            }}
            placeholder="搜尋問題或答案..."
            className="border rounded px-3 py-1.5 text-sm w-48"
          />
        </div>
      </div>

      {/* Articles List */}
      {loading ? (
        <div className="text-center py-12 text-gray-500">載入中...</div>
      ) : articles.length === 0 ? (
        <div className="text-center py-12 text-gray-500">尚無 FAQ</div>
      ) : (
        <>
          <div className="bg-white rounded-lg shadow overflow-hidden divide-y divide-gray-200">
            {articles.map((article) => (
              <div key={article.id} className="px-4 py-4 hover:bg-gray-50">
                <div className="flex items-start justify-between">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      {article.isPinned && (
                        <span className="text-xs bg-yellow-100 text-yellow-800 px-1.5 py-0.5 rounded">
                          置頂
                        </span>
                      )}
                      <div
                        className="text-sm font-medium text-gray-900"
                        dangerouslySetInnerHTML={{
                          __html: article.highlightedQuestion || article.question,
                        }}
                      />
                    </div>
                    <div
                      className="text-xs text-gray-500 mt-1 line-clamp-2"
                      dangerouslySetInnerHTML={{
                        __html: article.highlightedAnswer || article.answer,
                      }}
                    />
                    <div className="text-xs text-gray-400 mt-1">
                      {article.categoryName || '未分類'} · 瀏覽 {article.viewCount || 0} ·{' '}
                      {formatDate(article.publishedAt)}
                    </div>
                  </div>
                  <button
                    onClick={() => handleDelete(article.id)}
                    disabled={deleting === article.id}
                    className="ml-4 text-red-600 hover:text-red-800 disabled:opacity-50 text-sm"
                  >
                    {deleting === article.id ? '刪除中...' : '刪除'}
                  </button>
                </div>
              </div>
            ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center items-center gap-2 mt-6">
              <button
                onClick={() => setPage(0)}
                disabled={page === 0}
                className="px-3 py-1 text-sm border rounded disabled:opacity-50"
              >
                首頁
              </button>
              <button
                onClick={() => setPage(page - 1)}
                disabled={page === 0}
                className="px-3 py-1 text-sm border rounded disabled:opacity-50"
              >
                上一頁
              </button>
              <span className="text-sm text-gray-600">
                第 {page + 1} / {totalPages} 頁
              </span>
              <button
                onClick={() => setPage(page + 1)}
                disabled={page >= totalPages - 1}
                className="px-3 py-1 text-sm border rounded disabled:opacity-50"
              >
                下一頁
              </button>
              <button
                onClick={() => setPage(totalPages - 1)}
                disabled={page >= totalPages - 1}
                className="px-3 py-1 text-sm border rounded disabled:opacity-50"
              >
                末頁
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
