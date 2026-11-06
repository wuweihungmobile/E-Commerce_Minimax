'use client';

import { useState } from 'react';
import Link from 'next/link';
import { getPosts, publishPost, unpublishPost, deletePost } from '@/services/cms';
import { useAsyncData } from '@/hooks/useAsyncData';
import { Table, TableHeader, TableRow, TableHead, TableBody, TableCell } from '@/components/ui/table';
import { PageTitle } from '@/components/ui/page-title';
import { buttonVariants, Button } from '@/components/ui/button';
import { Chip } from '@/components/ui/chip';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';
import { StatusBadge } from '@/components/ui/status-badge';

export default function PostListPage() {
  const [filter, setFilter] = useState<string>('');

  const { data, loading, reload } = useAsyncData(
    () => getPosts(filter ? { status: filter } : {}),
    [filter],
    {
      errorMessage: () => '載入貼文失敗，請稍後再試',
      onError: (message, error) => {
        console.error('Failed to load posts:', error);
        toast.error(message);
      },
    }
  );
  const posts = data?.posts ?? [];

  async function handlePublish(postId: string) {
    try {
      await publishPost(postId);
      reload();
    } catch (error) {
      console.error('Failed to publish post:', error);
      toast.error('發布貼文失敗，請稍後再試');
    }
  }

  async function handleUnpublish(postId: string) {
    try {
      await unpublishPost(postId);
      reload();
    } catch (error) {
      console.error('Failed to unpublish post:', error);
      toast.error('下架貼文失敗，請稍後再試');
    }
  }

  async function handleDelete(postId: string) {
    if (!(await confirmDialog({ message: '確定要刪除這篇貼文嗎？', confirmLabel: '刪除', destructive: true }))) return;
    try {
      await deletePost(postId);
      reload();
    } catch (error) {
      console.error('Failed to delete post:', error);
      toast.error('刪除貼文失敗，請稍後再試');
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
      <div className="mb-4 flex gap-2">
        <Chip
          onClick={() => setFilter('')} selected={!filter}
        >
          全部
        </Chip>
        <Chip
          onClick={() => setFilter('DRAFT')} selected={filter === 'DRAFT'}
        >
          草稿
        </Chip>
        <Chip
          onClick={() => setFilter('PUBLISHED')} selected={filter === 'PUBLISHED'}
        >
          已發布
        </Chip>
        <Chip
          onClick={() => setFilter('ARCHIVED')} selected={filter === 'ARCHIVED'}
        >
          已歸檔
        </Chip>
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
                        disabled={post.status !== 'DRAFT'}
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
    </div>
  );
}