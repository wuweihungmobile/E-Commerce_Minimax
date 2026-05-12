'use client';

import { useEffect, useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { createPost, getCategories, CategoryResponse } from '@/services/cms';

export default function NewPostPage() {
  const router = useRouter();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [tags, setTags] = useState('');
  const [featuredImageUrl, setFeaturedImageUrl] = useState('');
  const [autoPublish, setAutoPublish] = useState(false);
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [saving, setSaving] = useState(false);

  const loadCategories = useCallback(async () => {
    try {
      const data = await getCategories();
      setCategories(data.categories);
    } catch (error) {
      console.error('Failed to load categories:', error);
      alert('載入分類失敗，請稍後再試');
    }
  }, []);

  useEffect(() => {
    // Use setTimeout to defer the async call to avoid setState-in-effect
    const timer = setTimeout(() => {
      loadCategories();
    }, 0);
    return () => clearTimeout(timer);
  }, [loadCategories]);

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      const tagsArray = tags.split(',').map(t => t.trim()).filter(t => t);
      await createPost({
        title,
        content,
        categoryId: categoryId || undefined,
        tags: tagsArray,
        featuredImageUrl: featuredImageUrl || undefined,
        autoPublish,
      });
      router.push('/dashboard/posts');
    } catch (error) {
      console.error('Failed to create post:', error);
      alert('建立貼文失敗，請稍後再試');
      setSaving(false);
    }
  }

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <h1 className="text-2xl font-bold mb-6">新建貼文</h1>

      <form onSubmit={handleSubmit} className="space-y-6">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">標題 *</label>
          <input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="輸入貼文標題"
          />
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">內容 (Markdown)</label>
          <textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={15}
            className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500 font-mono"
            placeholder="使用 Markdown 格式撰寫內容

範例：
# 標題
這是**粗體**文字

嵌入商品卡片：
{{embed:listing:<listing_id>}}"
          />
          <p className="mt-1 text-sm text-gray-500">
            使用 &#123;&#123;embed:listing:&lt;listing_id&gt;&#125;&#125; 嵌入商品/房型卡片
          </p>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">分類</label>
            <select
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            >
              <option value="">選擇分類</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>{cat.name}</option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-sm font-medium text-gray-700 mb-1">標籤</label>
            <input
              type="text"
              value={tags}
              onChange={(e) => setTags(e.target.value)}
              className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              placeholder="標籤1, 標籤2, 標籤3"
            />
          </div>
        </div>

        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">封面圖片 URL</label>
          <input
            type="url"
            value={featuredImageUrl}
            onChange={(e) => setFeaturedImageUrl(e.target.value)}
            className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
            placeholder="https://example.com/image.jpg"
          />
        </div>

        <div className="flex items-center">
          <input
            type="checkbox"
            id="autoPublish"
            checked={autoPublish}
            onChange={(e) => setAutoPublish(e.target.checked)}
            className="h-4 w-4 text-blue-600 focus:ring-blue-500 border-gray-300 rounded"
          />
          <label htmlFor="autoPublish" className="ml-2 text-sm text-gray-700">
            建立後立即發布
          </label>
        </div>

        <div className="flex gap-4">
          <button
            type="submit"
            disabled={saving}
            className="bg-blue-600 text-white px-6 py-2 rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {saving ? '儲存中...' : '儲存'}
          </button>
          <button
            type="button"
            onClick={() => router.back()}
            className="bg-gray-200 text-gray-700 px-6 py-2 rounded-lg hover:bg-gray-300"
          >
            取消
          </button>
        </div>
      </form>
    </div>
  );
}