'use client';

import { useState } from 'react';
import Link from 'next/link';
import {
  getFaqArticles,
  deleteFaqArticle,
  getFaqCategories,
  getPinnedFaqArticles,
  searchFaqArticlesWithHighlight,
} from '@/services/faq';
import { useAsyncData } from '@/hooks/useAsyncData';
import { Label } from '@/components/ui/label';
import { NativeSelect } from '@/components/ui/native-select';
import { Input } from '@/components/ui/input';
import { PageTitle } from '@/components/ui/page-title';
import { EmptyState, LoadingState } from '@/components/ui/states';
import { Card } from '@/components/ui/card';
import { buttonVariants, Button } from '@/components/ui/button';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';
import { Badge } from '@/components/ui/badge';

export default function FaqPage() {
  const [page, setPage] = useState(0);
  const [categoryFilter, setCategoryFilter] = useState<string>('');
  const [keyword, setKeyword] = useState<string>('');
  const [deleting, setDeleting] = useState<string | null>(null);

  // 分類與置頂文章只是輔助資訊：載入失敗只記錄，畫面照常（與原本相同）
  const { data: categoriesData } = useAsyncData(
    () => getFaqCategories(),
    [],
    {
      errorMessage: () => '載入 FAQ 分類失敗',
      onError: (_message, error) => console.error('Failed to load FAQ categories:', error),
    }
  );
  const categories = categoriesData ?? [];
  const { data: pinnedData, reload: reloadPinned } = useAsyncData(
    () => getPinnedFaqArticles(),
    [],
    {
      errorMessage: () => '載入置頂 FAQ 失敗',
      onError: (_message, error) => console.error('Failed to load pinned FAQ articles:', error),
    }
  );
  const pinnedArticles = pinnedData ?? [];

  const { data, loading, reload: reloadArticles } = useAsyncData(
    () => {
      if (keyword) {
        // 關鍵字搜尋改走高亮端點，呈現後端已支援的 <mark> 高亮結果
        return searchFaqArticlesWithHighlight({ page, size: 20, keyword });
      }
      return getFaqArticles({
        page,
        size: 20,
        categoryId: categoryFilter || undefined,
      });
    },
    [page, categoryFilter, keyword],
    {
      errorMessage: () => '載入 FAQ 文章失敗，請稍後再試',
      onError: (message, error) => {
        console.error('Failed to load FAQ articles:', error);
        toast.error(message);
      },
    }
  );
  const articles = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  async function handleDelete(articleId: string) {
    if (!(await confirmDialog({ message: '確定要刪除這則 FAQ 嗎？', confirmLabel: '刪除', destructive: true }))) return;
    setDeleting(articleId);
    try {
      await deleteFaqArticle(articleId);
      reloadArticles();
      reloadPinned();
      toast.success('刪除成功');
    } catch (error) {
      console.error('Failed to delete FAQ article:', error);
      toast.error('刪除失敗，請稍後再試');
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
        <PageTitle>FAQ 管理</PageTitle>
        <div className="flex items-center gap-4">
          <Link
            href="/dashboard/faq/categories"
            className={buttonVariants({ variant: "outline", size: "sm" })}
          >
            分類管理
          </Link>
          <div className="text-sm text-rs-ink-muted">總計 {totalElements} 則 FAQ</div>
        </div>
      </div>

      {/* Pinned Articles */}
      {pinnedArticles.length > 0 && (
        <div className="mb-6">
          <h2 className="text-sm font-semibold text-rs-ink mb-2">置頂 FAQ</h2>
          <div className="flex flex-wrap gap-2">
            {pinnedArticles.map((article) => (
              <Badge key={article.id} variant="warning">
                {article.question}
              </Badge>
            ))}
          </div>
        </div>
      )}

      {/* Filters */}
      <div className="mb-4 flex flex-wrap gap-4">
        <div>
          <Label className="block mb-1">分類</Label>
          <NativeSelect
            value={categoryFilter}
            onChange={(e) => {
              setCategoryFilter(e.target.value);
              setPage(0);
            }}
            className="w-auto"
          >
            <option value="">全部分類</option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id}>
                {cat.name}
              </option>
            ))}
          </NativeSelect>
        </div>
        <div>
          <Label className="block mb-1">關鍵字搜尋</Label>
          <Input
            type="text"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value);
              setPage(0);
            }}
            placeholder="搜尋問題或答案..."
            className="w-48"
          />
        </div>
      </div>

      {/* Articles List */}
      {loading ? (
        <LoadingState className="h-auto py-12" />
      ) : articles.length === 0 ? (
        <EmptyState title="尚無 FAQ" />
      ) : (
        <>
          <Card className="overflow-hidden divide-y divide-rs-hairline">
            {articles.map((article) => (
              <div key={article.id} className="px-4 py-4 hover:bg-rs-bg-base">
                <div className="flex items-start justify-between">
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center gap-2">
                      {article.isPinned && (
                        <Badge variant="warning">置頂</Badge>
                      )}
                      {article.highlightedQuestion ? (
                        <div
                          className="text-sm font-medium text-rs-ink"
                          dangerouslySetInnerHTML={{ __html: article.highlightedQuestion }}
                        />
                      ) : (
                        <div className="text-sm font-medium text-rs-ink">{article.question}</div>
                      )}
                    </div>
                    {article.highlightedAnswer ? (
                      <div
                        className="text-xs text-rs-ink-muted mt-1 line-clamp-2"
                        dangerouslySetInnerHTML={{ __html: article.highlightedAnswer }}
                      />
                    ) : (
                      <div className="text-xs text-rs-ink-muted mt-1 line-clamp-2">{article.answer}</div>
                    )}
                    <div className="text-xs text-rs-disabled-text mt-1">
                      {article.categoryName || '未分類'} · 瀏覽 {article.viewCount || 0} ·{' '}
                      {formatDate(article.publishedAt)}
                    </div>
                  </div>
                  <Button
                    variant="linkDestructive"
                    size="inline"
                    onClick={() => handleDelete(article.id)}
                    disabled={deleting === article.id}
                    className="ml-4"
                  >
                    {deleting === article.id ? '刪除中...' : '刪除'}
                  </Button>
                </div>
              </div>
            ))}
          </Card>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center items-center gap-2 mt-6">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(0)}
                disabled={page === 0}
              >
                首頁
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(page - 1)}
                disabled={page === 0}
              >
                上一頁
              </Button>
              <span className="text-sm text-rs-ink-muted">
                第 {page + 1} / {totalPages} 頁
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(page + 1)}
                disabled={page >= totalPages - 1}
              >
                下一頁
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(totalPages - 1)}
                disabled={page >= totalPages - 1}
              >
                末頁
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
