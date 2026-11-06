'use client';

import { useState } from 'react';
import { useAsyncData } from '@/hooks/useAsyncData';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { createPost, getCategories, CreatePostRequest } from '@/services/cms';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { NativeSelect } from '@/components/ui/native-select';
import { Input } from '@/components/ui/input';
import { PageTitle } from '@/components/ui/page-title';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { toast } from '@/components/ui/toast';

export default function NewPostPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [formData, setFormData] = useState<CreatePostRequest>({
    title: '',
    content: '',
    categoryId: '',
    tags: [],
    featuredImageUrl: '',
    autoPublish: false,
  });
  const [tagInput, setTagInput] = useState('');

  // 分類只是下拉選項：載入失敗只記錄
  const { data: categoriesData } = useAsyncData(
    () => getCategories(),
    [],
    {
      errorMessage: () => '載入分類失敗',
      onError: (_message, error) => console.error('Failed to load categories:', error),
    }
  );
  const categories = categoriesData?.categories ?? [];

  async function handleSubmit(e: React.FormEvent, autoPublish = false) {
    e.preventDefault();
    if (!formData.title.trim()) {
      toast.error('請輸入標題');
      return;
    }
    if (!formData.content.trim()) {
      toast.error('請輸入內容');
      return;
    }

    setLoading(true);
    try {
      const postData = { ...formData, autoPublish };
      const post = await createPost(postData);
      toast.success(autoPublish ? '貼文已發布' : '貼文已儲存為草稿');
      router.push(`/cms/posts/${post.id}/edit`);
    } catch (error) {
      console.error('Failed to create post:', error);
      toast.error('建立貼文失敗，請稍後再試');
    } finally {
      setLoading(false);
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

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="flex items-center gap-4 mb-6">
        <Link href="/cms" className="text-rs-ink-muted hover:text-rs-ink">
          ← 返回
        </Link>
        <PageTitle>新建貼文</PageTitle>
      </div>

      <form onSubmit={(e) => handleSubmit(e, false)} className="space-y-6">
        {/* Title */}
        <div>
          <Label className="block mb-1">
            標題 <span className="text-rs-error">*</span>
          </Label>
          <Input
            type="text"
            value={formData.title}
            onChange={(e) => setFormData({ ...formData, title: e.target.value })}
            placeholder="輸入標題"
          />
        </div>

        {/* Content */}
        <div>
          <Label className="block mb-1">
            內容 <span className="text-rs-error">*</span>
          </Label>
          <Textarea
            value={formData.content}
            onChange={(e) => setFormData({ ...formData, content: e.target.value })}
            rows={12}
            className="font-mono"
            placeholder="輸入內容... (可使用 {{embed:listing:<id>}} 嵌入商品卡)"
          />
          <p className="mt-1 text-xs text-rs-ink-muted">
            嵌入商品卡語法: {'{{embed:listing:<listingId>}}'}
          </p>
        </div>

        {/* Category */}
        <div>
          <Label className="block mb-1">分類</Label>
          <NativeSelect
            value={formData.categoryId || ''}
            onChange={(e) => setFormData({ ...formData, categoryId: e.target.value || undefined })}
          >
            <option value="">選擇分類</option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id}>{cat.name}</option>
            ))}
          </NativeSelect>
        </div>

        {/* Tags */}
        <div>
          <Label className="block mb-1">標籤</Label>
          <div className="flex gap-2 mb-2">
            <Input
              type="text"
              value={tagInput}
              onChange={(e) => setTagInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault();
                  handleAddTag();
                }
              }}
              className="flex-1"
              placeholder="輸入標籤後按 Enter"
            />
            <Button
              variant="secondary"
              type="button"
              onClick={handleAddTag}
            >
              新增
            </Button>
          </div>
          <div className="flex flex-wrap gap-2">
            {(formData.tags || []).map((tag) => (
              <Badge key={tag} variant="secondary" className="gap-1">
                {tag}
                <Button
                  type="button"
                  variant="link"
                  size="inline"
                  aria-label={`移除標籤 ${tag}`}
                  onClick={() => handleRemoveTag(tag)}
                >
                  ×
                </Button>
              </Badge>
            ))}
          </div>
        </div>

        {/* Featured Image */}
        <div>
          <Label className="block mb-1">精選圖片 URL</Label>
          <Input
            type="text"
            value={formData.featuredImageUrl || ''}
            onChange={(e) => setFormData({ ...formData, featuredImageUrl: e.target.value || undefined })}
            placeholder="https://..."
          />
          {formData.featuredImageUrl && (
            // eslint-disable-next-line @next/next/no-img-element -- 封面／精選圖是店家輸入的任意網址；next/image 需要 width／height，且網域須事先在 next.config 設定 remotePatterns
            <img
              src={formData.featuredImageUrl}
              alt="預覽"
              className="mt-2 max-h-40 rounded"
              onError={(e) => (e.currentTarget.style.display = 'none')}
            />
          )}
        </div>

        {/* Actions */}
        <div className="flex gap-3 pt-4 border-t">
          <Button
            variant="secondary"
            type="button"
            onClick={(e) => handleSubmit(e as unknown as React.FormEvent, false)}
            disabled={loading}
          >
            儲存草稿
          </Button>
          <Button
            variant="default"
            type="button"
            onClick={(e) => handleSubmit(e as unknown as React.FormEvent, true)}
            disabled={loading}
          >
            發布
          </Button>
          <Link
            href="/cms"
            className="px-4 py-2 text-rs-ink-muted hover:text-rs-ink"
          >
            取消
          </Link>
        </div>
      </form>
    </div>
  );
}