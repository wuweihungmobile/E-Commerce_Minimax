'use client';

import { useEffect, useState, useCallback } from 'react';
import Link from 'next/link';
import { getPosts, publishPost, unpublishPost, deletePost, PostResponse } from '@/services/cms';

export default function PostListPage() {
  const [posts, setPosts] = useState<PostResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<string>('');

  const loadPosts = useCallback(async () => {
    setLoading(true);
    try {
      const params = filter ? { status: filter } : {};
      const data = await getPosts(params);
      setPosts(data.posts);
    } catch (error) {
      console.error('Failed to load posts:', error);
      alert('載入貼文失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }, [filter]);

  useEffect(() => {
    loadPosts();
  }, [loadPosts]);

  async function handlePublish(postId: string) {
    try {
      await publishPost(postId);
      loadPosts();
    } catch (error) {
      console.error('Failed to publish post:', error);
      alert('發布貼文失敗，請稍後再試');
    }
  }

  async function handleUnpublish(postId: string) {
    try {
      await unpublishPost(postId);
      loadPosts();
    } catch (error) {
      console.error('Failed to unpublish post:', error);
      alert('下架貼文失敗，請稍後再試');
    }
  }

  async function handleDelete(postId: string) {
    if (!confirm('確定要刪除這篇貼文嗎？')) return;
    try {
      await deletePost(postId);
      loadPosts();
    } catch (error) {
      console.error('Failed to delete post:', error);
      alert('刪除貼文失敗，請稍後再試');
    }
  }

  const getStatusBadge = (status: string) => {
    const styles = {
      DRAFT: 'bg-rs-disabled-bg text-rs-ink',
      PUBLISHED: 'bg-rs-success/10 text-rs-success',
      ARCHIVED: 'bg-rs-warning/15 text-rs-ink',
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
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-semibold">貼文管理</h1>
        <Link
          href="/dashboard/posts/new"
          className="bg-rs-primary text-white px-4 py-2 rounded hover:bg-rs-primary-hover"
        >
          新建貼文
        </Link>
      </div>

      {/* Filter */}
      <div className="mb-4 flex gap-2">
        <button
          onClick={() => setFilter('')}
          className={`px-3 py-1 rounded ${!filter ? 'bg-rs-tertiary text-rs-primary' : 'bg-rs-disabled-bg'}`}
        >
          全部
        </button>
        <button
          onClick={() => setFilter('DRAFT')}
          className={`px-3 py-1 rounded ${filter === 'DRAFT' ? 'bg-rs-tertiary text-rs-primary' : 'bg-rs-disabled-bg'}`}
        >
          草稿
        </button>
        <button
          onClick={() => setFilter('PUBLISHED')}
          className={`px-3 py-1 rounded ${filter === 'PUBLISHED' ? 'bg-rs-tertiary text-rs-primary' : 'bg-rs-disabled-bg'}`}
        >
          已發布
        </button>
        <button
          onClick={() => setFilter('ARCHIVED')}
          className={`px-3 py-1 rounded ${filter === 'ARCHIVED' ? 'bg-rs-tertiary text-rs-primary' : 'bg-rs-disabled-bg'}`}
        >
          已歸檔
        </button>
      </div>

      {/* Post List */}
      {loading ? (
        <div className="text-center py-8">載入中...</div>
      ) : posts.length === 0 ? (
        <div className="text-center py-8 text-rs-ink-muted">尚無貼文</div>
      ) : (
        <div className="bg-white rounded-lg overflow-hidden border border-rs-hairline">
          <table className="min-w-full">
            <thead className="bg-rs-bg-base">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-rs-ink-muted uppercase">標題</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-rs-ink-muted uppercase">狀態</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-rs-ink-muted uppercase">分類</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-rs-ink-muted uppercase">瀏覽</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-rs-ink-muted uppercase">發布時間</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-rs-ink-muted uppercase">操作</th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {posts.map((post) => (
                <tr key={post.id} className="hover:bg-rs-bg-base">
                  <td className="px-4 py-3">
                    <Link href={`/dashboard/posts/${post.id}/edit`} className="text-rs-primary hover:underline">
                      {post.title}
                    </Link>
                  </td>
                  <td className="px-4 py-3">{getStatusBadge(post.status)}</td>
                  <td className="px-4 py-3 text-rs-ink-muted">{post.categoryName || '-'}</td>
                  <td className="px-4 py-3 text-rs-ink-muted">{post.viewCount}</td>
                  <td className="px-4 py-3 text-rs-ink-muted">
                    {post.publishedAt ? new Date(post.publishedAt).toLocaleDateString('zh-TW') : '-'}
                  </td>
                  <td className="px-4 py-3 text-right">
                    <div className="flex justify-end gap-2">
                      {post.status === 'DRAFT' && (
                        <button
                          onClick={() => handlePublish(post.id)}
                          className="text-rs-success hover:text-rs-success/80 text-sm"
                        >
                          發布
                        </button>
                      )}
                      {post.status === 'PUBLISHED' && (
                        <button
                          onClick={() => handleUnpublish(post.id)}
                          className="text-rs-ink hover:text-rs-ink text-sm"
                        >
                          下架
                        </button>
                      )}
                      <Link
                        href={`/dashboard/posts/${post.id}/edit`}
                        className="text-rs-primary hover:text-rs-primary-hover text-sm"
                      >
                        編輯
                      </Link>
                      <button
                        onClick={() => handleDelete(post.id)}
                        className="text-rs-error hover:text-rs-error/80 text-sm"
                        disabled={post.status !== 'DRAFT'}
                      >
                        刪除
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}