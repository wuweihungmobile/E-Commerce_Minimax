'use client';

import { useState } from 'react';
import Link from 'next/link';
import { getPosts, publishPost, unpublishPost, deletePost } from '@/services/cms';
import { apiErrorMessage } from '@/lib/api-error';
import { useAsyncData } from '@/hooks/useAsyncData';
import { Table, TableHeader, TableRow, TableHead, TableBody, TableCell } from '@/components/ui/table';
import { PageTitle } from '@/components/ui/page-title';
import { buttonVariants, Button } from '@/components/ui/button';
import { Chip } from '@/components/ui/chip';
import { Pagination } from '@/components/ui/pagination';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';
import { StatusBadge } from '@/components/ui/status-badge';

const PAGE_SIZE = 20;

// 貼文管理（賣家後台）。Sprint 332（DEF-466）：與原本並行的 /cms 貼文介面合併成這一套——
// 從 /cms 帶過來的：分頁（原本這頁只看得到第一頁 20 筆）、列表顯示 slug、總數。
// 刪除規則（Sprint 334，DEF-476，使用者決定）：已發布的貼文要先下架才能刪（SPRINT_08 AC-007）——列表、編輯頁、後端
// （409 E-4107）三處一致；原本只有這裡的按鈕在執行，編輯頁與後端都放行。
export default function PostListPage() {
  const [filter, setFilter] = useState<string>('');
  const [page, setPage] = useState(0);

  const { data, loading, reload } = useAsyncData(
    () => getPosts({ page, size: PAGE_SIZE, ...(filter ? { status: filter } : {}) }),
    [filter, page],
    {
      errorMessage: () => '載入貼文失敗，請稍後再試',
      onError: (message, error) => {
        console.error('Failed to load posts:', error);
        toast.error(message);
      },
    }
  );
  const posts = data?.posts ?? [];
  const totalCount = data?.totalCount ?? 0;
  const totalPages = data?.totalPages ?? 0;

  function selectFilter(status: string) {
    setFilter(status);
    setPage(0);
  }

  // 動作之後這一頁可能變空：刪掉最後一頁唯一的一筆，或在篩選條件下把唯一的一筆發布／下架而移出篩選結果。
  // 總頁數會因此變成 1，分頁元件會把自己藏起來——使用者就停在一個沒有分頁控制的空頁。所以變空就退回前一頁（換頁會自動重新載入）。
  function reloadAfterChange(rowLeavesView: boolean) {
    if (rowLeavesView && page > 0 && posts.length === 1) setPage(page - 1);
    else reload();
  }

  async function handlePublish(postId: string) {
    try {
      await publishPost(postId);
      reloadAfterChange(filter !== '');
    } catch (error) {
      console.error('Failed to publish post:', error);
      toast.error('發布貼文失敗，請稍後再試');
    }
  }

  async function handleUnpublish(postId: string) {
    try {
      await unpublishPost(postId);
      reloadAfterChange(filter !== '');
    } catch (error) {
      console.error('Failed to unpublish post:', error);
      toast.error('下架貼文失敗，請稍後再試');
    }
  }

  async function handleDelete(postId: string) {
    if (!(await confirmDialog({ message: '確定要刪除這篇貼文嗎？', confirmLabel: '刪除', destructive: true }))) return;
    try {
      await deletePost(postId);
      reloadAfterChange(true);
    } catch (error) {
      console.error('Failed to delete post:', error);
      toast.error(apiErrorMessage(error, '刪除貼文失敗，請稍後再試'));
    }
  }

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <PageTitle>貼文管理</PageTitle>
        <Link
          href="/dashboard/posts/new"
          className={buttonVariants({ variant: "default" })}
        >
          新建貼文
        </Link>
      </div>

      {/* Filter */}
      <div className="mb-4 flex items-center gap-2">
        <Chip
          onClick={() => selectFilter('')} selected={!filter}
        >
          全部
        </Chip>
        <Chip
          onClick={() => selectFilter('DRAFT')} selected={filter === 'DRAFT'}
        >
          草稿
        </Chip>
        <Chip
          onClick={() => selectFilter('PUBLISHED')} selected={filter === 'PUBLISHED'}
        >
          已發布
        </Chip>
        <Chip
          onClick={() => selectFilter('ARCHIVED')} selected={filter === 'ARCHIVED'}
        >
          已歸檔
        </Chip>
        {/* 目前篩選條件下的總數（不放進「全部」鈕的標籤：選了「草稿」之後它會變成草稿數，鈕名卻還是「全部」） */}
        {!loading && <span className="ml-auto text-sm text-rs-ink-muted">共 {totalCount} 篇</span>}
      </div>

      {/* Post List */}
      {loading ? (
        <div className="text-center py-8">載入中...</div>
      ) : posts.length === 0 ? (
        <div className="text-center py-8 text-rs-ink-muted">尚無貼文</div>
      ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>標題</TableHead>
                <TableHead>狀態</TableHead>
                <TableHead>分類</TableHead>
                <TableHead>瀏覽</TableHead>
                <TableHead>發布時間</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {posts.map((post) => (
                <TableRow key={post.id}>
                  <TableCell>
                    <Link href={`/dashboard/posts/${post.id}/edit`} className="text-rs-primary hover:underline">
                      {post.title}
                    </Link>
                    <div className="text-xs text-rs-disabled-text">{post.slug}</div>
                  </TableCell>
                  <TableCell><StatusBadge domain="post" status={post.status} /></TableCell>
                  <TableCell className="text-rs-ink-muted">{post.categoryName || '-'}</TableCell>
                  <TableCell className="text-rs-ink-muted">{post.viewCount}</TableCell>
                  <TableCell className="text-rs-ink-muted">
                    {post.publishedAt ? new Date(post.publishedAt).toLocaleDateString('zh-TW') : '-'}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-2">
                      {post.status === 'DRAFT' && (
                        <Button
                          variant="linkSuccess"
                          size="inline"
                          onClick={() => handlePublish(post.id)}
                        >
                          發布
                        </Button>
                      )}
                      {post.status === 'PUBLISHED' && (
                        <Button
                          variant="linkNeutral"
                          size="inline"
                          onClick={() => handleUnpublish(post.id)}
                        >
                          下架
                        </Button>
                      )}
                      <Link
                        href={`/dashboard/posts/${post.id}/edit`}
                        className="text-rs-primary hover:text-rs-primary-hover text-sm"
                      >
                        編輯
                      </Link>
                      <Button
                        variant="linkDestructive"
                        size="inline"
                        onClick={() => handleDelete(post.id)}
                        disabled={post.status === 'PUBLISHED'}
                        title={post.status === 'PUBLISHED' ? '已發布的貼文請先下架才能刪除' : undefined}
                      >
                        刪除
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
      )}

      {!loading && <Pagination current={page + 1} total={totalPages} onChange={(p) => setPage(p - 1)} className="mt-4" />}
    </div>
  );
}
