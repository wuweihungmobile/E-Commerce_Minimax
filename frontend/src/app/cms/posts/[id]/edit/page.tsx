'use client';

import { useState, useEffect } from 'react';
import { useRouter, useParams } from 'next/navigation';
import Link from 'next/link';
import {
  getPost,
  updatePost,
  publishPost,
  unpublishPost,
  getCategories,
  deletePost,
  PostResponse,
  CategoryResponse,
  UpdatePostRequest,
  getListingCard,
  ListingCardResponse,
} from '@/services/cms';

export default function EditPostPage() {
  const router = useRouter();
  const params = useParams();
  const postId = params.id as string;

  const [loading, setLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(true);
  const [post, setPost] = useState<PostResponse | null>(null);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [embedCards, setEmbedCards] = useState<Map<string, ListingCardResponse>>(new Map());

  const [formData, setFormData] = useState<UpdatePostRequest>({});
  const [tagInput, setTagInput] = useState('');

  useEffect(() => {
    loadPost();
    loadCategories();
  }, [postId]);

  async function loadPost() {
    try {
      const data = await getPost(postId);
      setPost(data);
      setFormData({
        title: data.title,
        content: data.content,
        categoryId: data.categoryId || undefined,
        tags: data.tags,
        featuredImageUrl: data.featuredImageUrl || undefined,
      });
      // Parse embed cards from content
      parseEmbedCards(data.content);
    } catch (error) {
      console.error('Failed to load post:', error);
      alert('載入貼文失敗');
      router.push('/cms');
    } finally {
      setInitialLoading(false);
    }
  }

  async function loadCategories() {
    try {
      const data = await getCategories();
      setCategories(data.categories);
    } catch (error) {
      console.error('Failed to load categories:', error);
    }
  }

  function parseEmbedCards(content: string) {
    const embedPattern = /{{embed:listing:([a-f0-9-]+)}}/gi;
    const matches = content.matchAll(embedPattern);
    const listingIds = [...matches].map(m => m[1]);

    listingIds.forEach(id => {
      if (!embedCards.has(id)) {
        getListingCard(id)
          .then(card => {
            setEmbedCards(prev => new Map(prev).set(id, card));
          })
          .catch(err => console.error('Failed to load listing card:', id, err));
      }
    });
  }

  async function handleSubmit(e: React.FormEvent, autoPublish = false) {
    e.preventDefault();
    if (!formData.title?.trim()) {
      alert('請輸入標題');
      return;
    }
    if (!formData.content?.trim()) {
      alert('請輸入內容');
      return;
    }

    setLoading(true);
    try {
      const updatedPost = await updatePost(postId, formData);

      if (autoPublish && post?.status === 'DRAFT') {
        await publishPost(postId);
        alert('貼文已更新並發布');
      } else {
        alert('貼文已儲存');
      }
      loadPost();
    } catch (error) {
      console.error('Failed to update post:', error);
      alert('更新貼文失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }

  async function handlePublish() {
    if (post?.status === 'DRAFT') {
      try {
        await publishPost(postId);
        alert('貼文已發布');
        loadPost();
      } catch (error) {
        console.error('Failed to publish post:', error);
        alert('發布失敗，請稍後再試');
      }
    }
  }

  async function handleUnpublish() {
    if (post?.status === 'PUBLISHED') {
      try {
        await unpublishPost(postId);
        alert('貼文已下架');
        loadPost();
      } catch (error) {
        console.error('Failed to unpublish post:', error);
        alert('下架失敗，請稍後再試');
      }
    }
  }

  async function handleDelete() {
    if (!confirm('確定要刪除這篇貼文嗎？')) return;
    try {
      await deletePost(postId);
      alert('貼文已刪除');
      router.push('/cms');
    } catch (error) {
      console.error('Failed to delete post:', error);
      alert('刪除失敗，請稍後再試');
    }
  }

  function handleAddTag() {
    const tag = tagInput.trim();
    if (tag && !formData.tags?.includes(tag)) {
      setFormData({ ...formData, tags: [...(formData.tags || []), tag] });
      setTagInput('');
    }
  }

  function handleRemoveTag(tag: string) {
    setFormData({ ...formData, tags: (formData.tags || []).filter(t => t !== tag) });
  }

  function handleContentChange(e: React.ChangeEvent<HTMLTextAreaElement>) {
    const newContent = e.target.value;
    setFormData({ ...formData, content: newContent });

    // Debounced embed card parsing
    setTimeout(() => {
      parseEmbedCards(newContent);
    }, 500);
  }

  const getStatusBadge = (status: string) => {
    const styles: Record<string, string> = {
      DRAFT: 'bg-gray-100 text-gray-800',
      PUBLISHED: 'bg-green-100 text-green-800',
      ARCHIVED: 'bg-yellow-100 text-yellow-800',
    };
    const labels: Record<string, string> = {
      DRAFT: '草稿',
      PUBLISHED: '已發布',
      ARCHIVED: '已歸檔',
    };
    return (
      <span className={`px-2 py-1 rounded-full text-xs ${styles[status] || ''}`}>
        {labels[status] || status}
      </span>
    );
  };

  if (initialLoading) {
    return <div className="p-6 text-center">載入中...</div>;
  }

  if (!post) {
    return <div className="p-6 text-center">找不到貼文</div>;
  }

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-4">
          <Link href="/cms" className="text-gray-500 hover:text-gray-700">
            ← 返回
          </Link>
          <h1 className="text-2xl font-bold">編輯貼文</h1>
        </div>
        <div className="flex items-center gap-2">
          {getStatusBadge(post.status)}
        </div>
      </div>

      <form onSubmit={(e) => handleSubmit(e, false)} className="space-y-6">
        {/* Title */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            標題 <span className="text-red-500">*</span>
          </label>
          <input
            type="text"
            value={formData.title || ''}
            onChange={(e) => setFormData({ ...formData, title: e.target.value })}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
        </div>

        {/* Content */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">
            內容 <span className="text-red-500">*</span>
          </label>
          <textarea
            value={formData.content || ''}
            onChange={handleContentChange}
            rows={12}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500 font-mono text-sm"
          />
          <p className="mt-1 text-xs text-gray-500">
            嵌入商品卡語法: {'{{embed:listing:<listingId>}}'}
          </p>
        </div>

        {/* Embed Cards Preview */}
        {embedCards.size > 0 && (
          <div className="border rounded-lg p-4 bg-gray-50">
            <h3 className="text-sm font-medium text-gray-700 mb-3">嵌入商品卡預覽</h3>
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              {Array.from(embedCards.entries()).map(([listingId, card]) => (
                <div key={listingId} className="border rounded p-3 bg-white">
                  <div className="flex gap-3">
                    {card.coverImageUrl && (
                      <img
                        src={card.coverImageUrl}
                        alt={card.title}
                        className="w-16 h-16 object-cover rounded"
                      />
                    )}
                    <div className="flex-1">
                      <div className="font-medium">{card.title}</div>
                      <div className="text-sm text-gray-500">{card.tenantName}</div>
                      <div className="text-sm font-medium text-blue-600">
                        {card.currency} {card.currentPrice.toLocaleString()}
                      </div>
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>
        )}

        {/* Category */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">分類</label>
          <select
            value={formData.categoryId || ''}
            onChange={(e) => setFormData({ ...formData, categoryId: e.target.value || undefined })}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          >
            <option value="">選擇分類</option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id}>{cat.name}</option>
            ))}
          </select>
        </div>

        {/* Tags */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">標籤</label>
          <div className="flex gap-2 mb-2">
            <input
              type="text"
              value={tagInput}
              onChange={(e) => setTagInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  handleAddTag();
                }
              }}
              className="flex-1 px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
            />
            <button
              type="button"
              onClick={handleAddTag}
              className="px-4 py-2 bg-gray-100 text-gray-700 rounded-md hover:bg-gray-200"
            >
              新增
            </button>
          </div>
          <div className="flex flex-wrap gap-2">
            {(formData.tags || []).map((tag) => (
              <span
                key={tag}
                className="inline-flex items-center gap-1 px-2 py-1 bg-blue-100 text-blue-800 rounded-full text-sm"
              >
                {tag}
                <button
                  type="button"
                  onClick={() => handleRemoveTag(tag)}
                  className="hover:text-blue-900"
                >
                  ×
                </button>
              </span>
            ))}
          </div>
        </div>

        {/* Featured Image */}
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">精選圖片 URL</label>
          <input
            type="text"
            value={formData.featuredImageUrl || ''}
            onChange={(e) => setFormData({ ...formData, featuredImageUrl: e.target.value || undefined })}
            className="w-full px-3 py-2 border border-gray-300 rounded-md focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          {formData.featuredImageUrl && (
            <img
              src={formData.featuredImageUrl}
              alt="預覽"
              className="mt-2 max-h-40 rounded"
              onError={(e) => (e.currentTarget.style.display = 'none')}
            />
          )}
        </div>

        {/* Post Info */}
        <div className="text-sm text-gray-500 space-y-1 pt-4 border-t">
          <div>瀏覽次數: {post.viewCount}</div>
          <div>建立時間: {new Date(post.createdAt).toLocaleString('zh-TW')}</div>
          <div>更新時間: {new Date(post.updatedAt).toLocaleString('zh-TW')}</div>
          {post.publishedAt && (
            <div>發布時間: {new Date(post.publishedAt).toLocaleString('zh-TW')}</div>
          )}
        </div>

        {/* Actions */}
        <div className="flex gap-3 pt-4 border-t">
          <button
            type="submit"
            disabled={loading}
            className="px-4 py-2 bg-blue-600 text-white rounded-md hover:bg-blue-700 disabled:opacity-50"
          >
            儲存變更
          </button>

          {post.status === 'DRAFT' && (
            <button
              type="button"
              onClick={handlePublish}
              className="px-4 py-2 bg-green-600 text-white rounded-md hover:bg-green-700"
            >
              發布
            </button>
          )}

          {post.status === 'PUBLISHED' && (
            <button
              type="button"
              onClick={handleUnpublish}
              className="px-4 py-2 bg-yellow-600 text-white rounded-md hover:bg-yellow-700"
            >
              下架
            </button>
          )}

          <button
            type="button"
            onClick={handleDelete}
            className="px-4 py-2 text-red-600 hover:text-red-800"
          >
            刪除
          </button>

          <Link
            href="/cms"
            className="px-4 py-2 text-gray-500 hover:text-gray-700"
          >
            取消
          </Link>
        </div>
      </form>
    </div>
  );
}