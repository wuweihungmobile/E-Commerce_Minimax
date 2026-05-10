'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { getPosts, publishPost, unpublishPost, deletePost, PostResponse } from '@/services/cms';

export default function CmsPostListPage() {
  const [posts, setPosts] = useState<PostResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<string>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalCount, setTotalCount] = useState(0);

  const loadPosts = async () => {
    setLoading(true);
    try {
      const params: { page?: number; size?: number; status?: string } = {
        page,
        size: 20,
      };
      if (filter) params.status = filter;
      const data = await getPosts(params);
      setPosts(data.posts);
      setTotalCount(data.totalCount);
      setTotalPages(data.totalPages);
    } catch (error) {
      console.error('Failed to load posts:', error);
      alert('載入貼文失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadPosts();
  }, [filter, page]);

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
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">CMS 貼文管理</h1>
        <Link
          href="/cms/posts/new"
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
        >
          新建貼文
        </Link>
      </div>

      {/* Filter */}
      <div className="mb-4 flex gap-2">
        <button
          onClick={() => { setFilter(''); setPage(0); }}
          className={`px-3 py-1 rounded ${!filter ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}
        >
          全部 ({totalCount})
        </button>
        <button
          onClick={() => { setFilter('DRAFT'); setPage(0); }}
          className={`px-3 py-1 rounded ${filter === 'DRAFT' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}
        >
          草稿
        </button>
        <button
          onClick={() => { setFilter('PUBLISHED'); setPage(0); }}
          className={`px-3 py-1 rounded ${filter === 'PUBLISHED' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}
        >
          已發布
        </button>
        <button
          onClick={() => { setFilter('ARCHIVED'); setPage(0); }}
          className={`px-3 py-1 rounded ${filter === 'ARCHIVED' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}
        >
          已歸檔
        </button>
      </div>

      {/* Media Library Link */}
      <div className="mb-4">
        <Link
          href="/cms/media"
          className="text-blue-600 hover:text-blue-800 text-sm"
        >
          前往媒體庫
        </Link>
      </div>

      {/* Post List */}
      {loading ? (
        <div className="text-center py-8">載入中...</div>
      ) : posts.length === 0 ? (
        <div className="text-center py-8 text-gray-500">尚無貼文</div>
      ) : (
        <>
          <div className="bg-white rounded-lg shadow overflow-hidden">
            <table className="min-w-full">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">標題</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">狀態</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">分類</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">瀏覽</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">發布時間</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">操作</th>
                </tr>
              </thead>
              <tbody className="divide-y">
                {posts.map((post) => (
                  <tr key={post.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <Link href={`/cms/posts/${post.id}/edit`} className="text-blue-600 hover:underline">
                        {post.title}
                      </Link>
                      <div className="text-xs text-gray-400">{post.slug}</div>
                    </td>
                    <td className="px-4 py-3">{getStatusBadge(post.status)}</td>
                    <td className="px-4 py-3 text-gray-500">{post.categoryName || '-'}</td>
                    <td className="px-4 py-3 text-gray-500">{post.viewCount}</td>
                    <td className="px-4 py-3 text-gray-500">
                      {post.publishedAt ? new Date(post.publishedAt).toLocaleDateString('zh-TW') : '-'}
                    </td>
                    <td className="px-4 py-3 text-right">
                      <div className="flex justify-end gap-2">
                        {post.status === 'DRAFT' && (
                          <button
                            onClick={() => handlePublish(post.id)}
                            className="text-green-600 hover:text-green-800 text-sm"
                          >
                            發布
                          </button>
                        )}
                        {post.status === 'PUBLISHED' && (
                          <button
                            onClick={() => handleUnpublish(post.id)}
                            className="text-yellow-600 hover:text-yellow-800 text-sm"
                          >
                            下架
                          </button>
                        )}
                        <Link
                          href={`/cms/posts/${post.id}/edit`}
                          className="text-blue-600 hover:text-blue-800 text-sm"
                        >
                          編輯
                        </Link>
                        <button
                          onClick={() => handleDelete(post.id)}
                          className="text-red-600 hover:text-red-800 text-sm"
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

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="mt-4 flex justify-center gap-2">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
                className="px-3 py-1 rounded bg-gray-100 disabled:opacity-50"
              >
                上一頁
              </button>
              <span className="px-3 py-1">
                第 {page + 1} 頁，共 {totalPages} 頁
              </span>
              <button
                onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                disabled={page >= totalPages - 1}
                className="px-3 py-1 rounded bg-gray-100 disabled:opacity-50"
              >
                下一頁
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}