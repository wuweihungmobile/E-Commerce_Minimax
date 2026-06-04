'use client';

import { useEffect, useState } from 'react';
import {
  getKnowledgeArticles,
  deleteKnowledgeArticle,
  getKnowledgeCategories,
  KnowledgeArticleDto,
  KnowledgeCategoryDto,
  PageResponse,
} from '@/services/knowledge';

export default function KnowledgePage() {
  const [articles, setArticles] = useState<KnowledgeArticleDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [categoryFilter, setCategoryFilter] = useState<string>('');
  const [keyword, setKeyword] = useState<string>('');
  const [categories, setCategories] = useState<KnowledgeCategoryDto[]>([]);
  const [deleting, setDeleting] = useState<string | null>(null);

  useEffect(() => {
    loadCategories();
  }, []);

  useEffect(() => {
    loadArticles();
  }, [page, categoryFilter, keyword]);

  async function loadCategories() {
    try {
      const data = await getKnowledgeCategories();
      setCategories(data);
    } catch (error) {
      console.error('Failed to load categories:', error);
    }
  }

  async function loadArticles() {
    setLoading(true);
    try {
      const params: {
        page: number;
        size: number;
        categoryId?: string;
        keyword?: string;
      } = {
        page,
        size: 20,
      };
      if (categoryFilter) params.categoryId = categoryFilter;
      if (keyword) params.keyword = keyword;

      const data: PageResponse<KnowledgeArticleDto> = await getKnowledgeArticles(params);
      setArticles(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (error) {
      console.error('Failed to load articles:', error);
      alert('載入知識庫文章失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(articleId: string) {
    if (!confirm('確定要刪除這篇文章嗎？')) return;
    setDeleting(articleId);
    try {
      await deleteKnowledgeArticle(articleId);
      loadArticles();
      alert('刪除成功');
    } catch (error) {
      console.error('Failed to delete article:', error);
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

  function getStatusBadge(status: string): { label: string; className: string } {
    switch (status) {
      case 'PUBLISHED':
        return { label: '已發布', className: 'bg-green-100 text-green-800' };
      case 'DRAFT':
        return { label: '草稿', className: 'bg-yellow-100 text-yellow-800' };
      case 'ARCHIVED':
        return { label: '已封存', className: 'bg-gray-100 text-gray-800' };
      default:
        return { label: status, className: 'bg-gray-100 text-gray-800' };
    }
  }

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">知識庫</h1>
        <div className="text-sm text-gray-500">
          總計 {totalElements} 篇文章
        </div>
      </div>

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
              <option key={cat.id} value={cat.id}>{cat.name}</option>
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
            placeholder="搜尋標題..."
            className="border rounded px-3 py-1.5 text-sm w-48"
          />
        </div>
        <div className="flex items-end">
          <button
            onClick={() => loadArticles()}
            className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
          >
            搜尋
          </button>
        </div>
      </div>

      {/* Articles Table */}
      {loading ? (
        <div className="text-center py-12 text-gray-500">載入中...</div>
      ) : articles.length === 0 ? (
        <div className="text-center py-12 text-gray-500">尚無文章</div>
      ) : (
        <>
          <div className="bg-white rounded-lg shadow overflow-hidden">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">標題</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">分類</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">狀態</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">瀏覽</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">發布日期</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">操作</th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {articles.map((article) => {
                  const statusBadge = getStatusBadge(article.status);
                  return (
                    <tr key={article.id} className="hover:bg-gray-50">
                      <td className="px-4 py-3">
                        <div className="text-sm font-medium text-gray-900">{article.title}</div>
                        {article.excerpt && (
                          <div className="text-xs text-gray-500 truncate max-w-xs">{article.excerpt}</div>
                        )}
                        {article.tags && article.tags.length > 0 && (
                          <div className="flex gap-1 mt-1">
                            {article.tags.slice(0, 3).map((tag, i) => (
                              <span key={i} className="text-xs bg-gray-100 px-1.5 py-0.5 rounded">{tag}</span>
                            ))}
                          </div>
                        )}
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">{article.categoryName || '-'}</td>
                      <td className="px-4 py-3">
                        <span className={`px-2 py-1 text-xs rounded-full ${statusBadge.className}`}>
                          {statusBadge.label}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-sm text-gray-600">{article.viewCount || 0}</td>
                      <td className="px-4 py-3 text-sm text-gray-600">{formatDate(article.publishedAt)}</td>
                      <td className="px-4 py-3 text-right text-sm">
                        <button
                          onClick={() => handleDelete(article.id)}
                          disabled={deleting === article.id}
                          className="text-red-600 hover:text-red-800 disabled:opacity-50"
                        >
                          {deleting === article.id ? '刪除中...' : '刪除'}
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
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