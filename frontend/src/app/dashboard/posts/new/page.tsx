'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { createPost, getCategories } from '@/services/cms';
import { useAsyncData } from '@/hooks/useAsyncData'
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { NativeSelect } from '@/components/ui/native-select';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { PageTitle } from '@/components/ui/page-title';
import { Button } from '@/components/ui/button';
import { toast } from '@/components/ui/toast';

export default function NewPostPage() {
  const router = useRouter();
  const [title, setTitle] = useState('');
  const [content, setContent] = useState('');
  const [categoryId, setCategoryId] = useState('');
  const [tags, setTags] = useState('');
  const [featuredImageUrl, setFeaturedImageUrl] = useState('');
  const [autoPublish, setAutoPublish] = useState(false);
  const [saving, setSaving] = useState(false);

  const { data: categoriesData } = useAsyncData(
    () => getCategories(),
    [],
    {
      errorMessage: () => '載入分類失敗，請稍後再試',
      onError: (message, error) => {
        console.error('Failed to load categories:', error);
        toast.error(message);
      },
    }
  );
  const categories = categoriesData?.categories ?? [];

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
      toast.error('建立貼文失敗，請稍後再試');
      setSaving(false);
    }
  }

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <PageTitle className="mb-6">新建貼文</PageTitle>

      <form onSubmit={handleSubmit} className="space-y-6">
        <div>
          <Label className="block mb-1">標題 *</Label>
          <Input
            type="text"
            value={title}
            onChange={(e) => setTitle(e.target.value)}
            required
            placeholder="輸入貼文標題"
          />
        </div>

        <div>
          <Label className="block mb-1">內容 (Markdown)</Label>
          <Textarea
            value={content}
            onChange={(e) => setContent(e.target.value)}
            rows={15}
            className="font-mono"
            placeholder="使用 Markdown 格式撰寫內容

範例：
# 標題
這是**粗體**文字

嵌入商品卡片：
{{embed:listing:<listing_id>}}"
          />
          <p className="mt-1 text-sm text-rs-ink-muted">
            使用 &#123;&#123;embed:listing:&lt;listing_id&gt;&#125;&#125; 嵌入商品/房型卡片
          </p>
        </div>

        <div className="grid grid-cols-2 gap-4">
          <div>
            <Label className="block mb-1">分類</Label>
            <NativeSelect
              value={categoryId}
              onChange={(e) => setCategoryId(e.target.value)}
            >
              <option value="">選擇分類</option>
              {categories.map((cat) => (
                <option key={cat.id} value={cat.id}>{cat.name}</option>
              ))}
            </NativeSelect>
          </div>

          <div>
            <Label className="block mb-1">標籤</Label>
            <Input
              type="text"
              value={tags}
              onChange={(e) => setTags(e.target.value)}
              placeholder="標籤1, 標籤2, 標籤3"
            />
          </div>
        </div>

        <div>
          <Label className="block mb-1">封面圖片 URL</Label>
          <Input
            type="url"
            value={featuredImageUrl}
            onChange={(e) => setFeaturedImageUrl(e.target.value)}
            placeholder="https://example.com/image.jpg"
          />
        </div>

        <div className="flex items-center">
          <Checkbox
            id="autoPublish"
            checked={autoPublish}
            onChange={(e) => setAutoPublish(e.target.checked)}
          />
          <Label htmlFor="autoPublish" className="ml-2">
            建立後立即發布
          </Label>
        </div>

        <div className="flex gap-4">
          <Button
            variant="default"
            type="submit"
            disabled={saving}
          >
            {saving ? '儲存中...' : '儲存'}
          </Button>
          <Button
            variant="secondary"
            type="button"
            onClick={() => router.back()}
          >
            取消
          </Button>
        </div>
      </form>
    </div>
  );
}