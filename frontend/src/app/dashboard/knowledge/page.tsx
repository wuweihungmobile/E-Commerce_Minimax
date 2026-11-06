'use client';

import { useState } from 'react';
import {
  getKnowledgeArticles,
  deleteKnowledgeArticle,
  getKnowledgeCategories,
} from '@/services/knowledge';
import { useAsyncData } from '@/hooks/useAsyncData';
import { Label } from '@/components/ui/label';
import { NativeSelect } from '@/components/ui/native-select';
import { Input } from '@/components/ui/input';
import { Table, TableHeader, TableRow, TableHead, TableBody, TableCell } from '@/components/ui/table';
import { PageTitle } from '@/components/ui/page-title';
import { EmptyState, LoadingState } from '@/components/ui/states';
import { Button } from '@/components/ui/button';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';
import { StatusBadge } from '@/components/ui/status-badge';
import { Badge } from '@/components/ui/badge';

export default function KnowledgePage() {
  const [page, setPage] = useState(0);
  const [categoryFilter, setCategoryFilter] = useState<string>('');
  const [keyword, setKeyword] = useState<string>('');
  const [deleting, setDeleting] = useState<string | null>(null);

  // 分類只是篩選用的選項：載入失敗只記錄，篩選器就沒有選項
  const { data: categoriesData } = useAsyncData(
    () => getKnowledgeCategories(),
    [],
    {
      errorMessage: () => '載入分類失敗',
      onError: (_message, error) => console.error('Failed to load categories:', error),
    }
  );
  const categories = categoriesData ?? [];

  const { data, loading, reload } = useAsyncData(
    () => {
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
      return getKnowledgeArticles(params);
    },
    [page, categoryFilter, keyword],
    {
      errorMessage: () => '載入知識庫文章失敗，請稍後再試',
      onError: (message, error) => {
        console.error('Failed to load articles:', error);
        toast.error(message);
      },
    }
  );
  const articles = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  async function handleDelete(articleId: string) {
    if (!(await confirmDialog({ message: '確定要刪除這篇文章嗎？', confirmLabel: '刪除', destructive: true }))) return;
    setDeleting(articleId);
    try {
      await deleteKnowledgeArticle(articleId);
      reload();
      toast.success('刪除成功');
    } catch (error) {
      console.error('Failed to delete article:', error);
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
        <PageTitle>知識庫</PageTitle>
        <div className="text-sm text-rs-ink-muted">
          總計 {totalElements} 篇文章
        </div>
      </div>

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
              <option key={cat.id} value={cat.id}>{cat.name}</option>
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
            placeholder="搜尋標題..."
            className="w-48"
          />
        </div>
        <div className="flex items-end">
          <Button
            variant="default"
            size="sm"
            onClick={() => reload()}
          >
            搜尋
          </Button>
        </div>
      </div>

      {/* Articles Table */}
      {loading ? (
        <LoadingState className="h-auto py-12" />
      ) : articles.length === 0 ? (
        <EmptyState title="尚無文章" />
      ) : (
        <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>標題</TableHead>
                  <TableHead>分類</TableHead>
                  <TableHead>狀態</TableHead>
                  <TableHead>瀏覽</TableHead>
                  <TableHead>發布日期</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {articles.map((article) => {
                  return (
                    <TableRow key={article.id}>
                      <TableCell>
                        <div className="text-sm font-medium text-rs-ink">{article.title}</div>
                        {article.excerpt && (
                          <div className="text-xs text-rs-ink-muted truncate max-w-xs">{article.excerpt}</div>
                        )}
                        {article.tags && article.tags.length > 0 && (
                          <div className="flex gap-1 mt-1">
                            {article.tags.slice(0, 3).map((tag, i) => (
                              <Badge key={i} variant="secondary">{tag}</Badge>
                            ))}
                          </div>
                        )}
                      </TableCell>
                      <TableCell className="text-sm text-rs-ink-muted">{article.categoryName || '-'}</TableCell>
                      <TableCell>
                        <StatusBadge domain="knowledgeArticle" status={article.status} />
                      </TableCell>
                      <TableCell className="text-sm text-rs-ink-muted">{article.viewCount || 0}</TableCell>
                      <TableCell className="text-sm text-rs-ink-muted">{formatDate(article.publishedAt)}</TableCell>
                      <TableCell className="text-right text-sm">
                        <Button
                          variant="linkDestructive"
                          size="inline"
                          onClick={() => handleDelete(article.id)}
                          disabled={deleting === article.id}
                        >
                          {deleting === article.id ? '刪除中...' : '刪除'}
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>

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