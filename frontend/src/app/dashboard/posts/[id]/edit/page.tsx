'use client';

import { useEffect, useState, use, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { getPost, updatePost, getCategories, deletePost, publishPost, unpublishPost, PostResponse, CategoryResponse } from '@/services/cms';

export default function EditPostPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const router = useRouter();
  const [post, setPost] = useState<PostResponse | null>(null);
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [tags, setTags] = useState('');
  const [featuredImageUrl, setFeaturedImageUrl] = useState('');
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  const loadPost = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getPost(id);
      setPost(data);
      setTitle(data.title);
      setContent(data.content);
      setCategoryId(data.categoryId || '');
      setTags(data.tags?.join(', ') || '');
      setFeaturedImageUrl(data.featuredImageUrl || '');
    } catch (error) {
      console.error('Failed to load post:', error);
      alert('載入貼文失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadPost();
    loadCategories();
  }, [loadPost]);

  async function loadCategories() {
    try {
      const data = await getCategories();
      setCategories(data.categories);
    } catch (error) {
      console.error('Failed to load categories:', error);
      alert('載入分類失敗，請稍後再試');
    }
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      const tagsArray = tags.split(',').map(t => t.trim()).filter(t => t);
      await updatePost(id, {
        title,
        content,
        categoryId: categoryId || undefined,
        tags: tagsArray,
        featuredImageUrl: featuredImageUrl || undefined,
      });
      loadPost();
      alert('貼文已更新');
    } catch (error) {
      console.error('Failed to update post:', error);
      alert('更新貼文失敗，請稍後再試');
      setSaving(false);
    }
  }

  async function handlePublish() {
    try {
      await publishPost(id);
      loadPost();
    } catch (error) {
      console.error('Failed to publish post:', error);
      alert('發布貼文失敗，請稍後再試');
    }
  }

  async function handleUnpublish() {
    try {
      await unpublishPost(id);
      loadPost();
    } catch (error) {
      console.error('Failed to unpublish post:', error);
      alert('下架貼文失敗，請稍後再試');
    }
  }

  async function handleDelete() {
    if (!confirm('確定要刪除這篇貼文嗎？')) return;
    try {
      await deletePost(id);
      router.push('/dashboard/posts');
    } catch (error) {
      console.error('Failed to delete post:', error);
      alert('刪除貼文失敗，請稍後再試');
    }
  }

  if (loading) {
    return <div className="p-6">載入中...</div>;
  }

  if (!post) {
    return <div className="p-6">找不到貼文</div>;
  }

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-semibold">編輯貼文</h1>
        <div className="flex gap-2">
          {post.status === 'DRAFT' && (
            <button
              onClick={handlePublish}
              className="bg-rs-success text-white px-4 py-2 rounded hover:bg-rs-success/90"
            >
              發布
            </button>
          )}
          {post.status === 'PUBLISHED' && (
            <button
              onClick={handleUnpublish}
              className="bg-rs-warning text-white px-4 py-2 rounded hover:bg-rs-warning/90"
            >
              下架
            </button>
          )}
        </div>
      </div>

      <div className="bg-white rounded-lg p-6 mb-6 border border-rs-hairline">
        <div className="flex items-center gap-2 mb-4">
          <span className={`px-2 py-1 rounded-full text-xs ${
            post.status === 'PUBLISHED' ? 'bg-rs-success/10 text-rs-success' :
            post.status === 'DRAFT' ? 'bg-rs-disabled-bg text-rs-ink' :
            'bg-rs-warning/15 text-rs-ink'
          }`}>
            {post.status === 'PUBLISHED' ? '已發布' : post.status === 'DRAFT' ? '草稿' : '已歸檔'}
          </span>
          <span className="text-sm text-rs-ink-muted">
            瀏覽次數: {post.viewCount}
          </span>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <label className="block text-sm font-medium text-rs-ink mb-1">標題 *</label>
            <input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-rs-primary focus:border-rs-primary"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-rs-ink mb-1">內容 (Markdown)</label>
            <textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows={15}
              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-rs-primary focus:border-rs-primary font-mono"
            />
          </div>

          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-sm font-medium text-rs-ink mb-1">分類</label>
              <select
                value={categoryId}
                onChange={(e) => setCategoryId(e.target.value)}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-rs-primary focus:border-rs-primary"
              >
                <option value="">選擇分類</option>
                {categories.map((cat) => (
                  <option key={cat.id} value={cat.id}>{cat.name}</option>
                ))}
              </select>
            </div>

            <div>
              <label className="block text-sm font-medium text-rs-ink mb-1">標籤</label>
              <input
                type="text"
                value={tags}
                onChange={(e) => setTags(e.target.value)}
                className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-rs-primary focus:border-rs-primary"
                placeholder="標籤1, 標籤2, 標籤3"
              />
            </div>
          </div>

          <div>
            <label className="block text-sm font-medium text-rs-ink mb-1">封面圖片 URL</label>
            <input
              type="url"
              value={featuredImageUrl}
              onChange={(e) => setFeaturedImageUrl(e.target.value)}
              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-rs-primary focus:border-rs-primary"
            />
          </div>

          <div className="flex items-center gap-4">
            <button
              type="submit"
              disabled={saving}
              className="bg-rs-primary text-white px-6 py-2 rounded-lg hover:bg-rs-primary-hover disabled:opacity-50"
            >
              {saving ? '儲存中...' : '儲存更新'}
            </button>
            <button
              type="button"
              onClick={handleDelete}
              className="text-rs-error hover:text-rs-error/80"
            >
              刪除貼文
            </button>
          </div>
        </form>
      </div>

      {/* Embedded Listings */}
      {post.embeds && post.embeds.length > 0 && (
        <div className="bg-white rounded-lg p-6 border border-rs-hairline">
          <h2 className="text-lg font-medium mb-4">嵌入的卡片</h2>
          <div className="grid grid-cols-2 gap-4">
            {post.embeds.map((embed) => (
              <div key={embed.id} className="border rounded-lg p-4">
                <p className="text-sm text-rs-ink-muted">Listing ID: {embed.listingId}</p>
                <p className="text-sm text-rs-ink-muted">Type: {embed.listingType}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}