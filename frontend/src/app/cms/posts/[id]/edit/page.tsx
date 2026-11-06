'use client';

import { useState } from 'react';
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
  UpdatePostRequest,
  getListingCard,
} from '@/services/cms';
import { useAsyncData } from '@/hooks/useAsyncData';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { NativeSelect } from '@/components/ui/native-select';
import { Input } from '@/components/ui/input';
import { PageTitle } from '@/components/ui/page-title';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';
import { StatusBadge } from '@/components/ui/status-badge';

// 內文裡的嵌入商品卡語法 {{embed:listing:<id>}}：取出不重複的 listingId（保持出現順序）
function embeddedListingIds(content: string): string[] {
  const pattern = /{{embed:listing:([a-f0-9-]+)}}/gi;
  return [...new Set([...content.matchAll(pattern)].map((m) => m[1]))];
}

// 嵌入商品卡預覽：跟著編輯中的內文，內文裡的 listingId 集合一變就重新載入；載入失敗的卡片只記錄、不顯示
function EmbeddedCardsPreview({ content }: { content: string }) {
  const idsKey = embeddedListingIds(content).join(',');
  const { data: cards } = useAsyncData(
    async (signal) => {
      const ids = idsKey ? idsKey.split(',') : [];
      const loaded = await Promise.all(
        ids.map((id) =>
          getListingCard(id, { signal }).then(
            (card) => [id, card] as const,
            (err: unknown) => {
              if (!signal.aborted) console.error('Failed to load listing card:', id, err);
              return null;
            }
          )
        )
      );
      return loaded.filter((entry) => entry !== null);
    },
    [idsKey],
    { enabled: idsKey !== '', errorMessage: '載入嵌入商品卡失敗' }
  );
  const shown = idsKey === '' ? [] : (cards ?? []);
  if (shown.length === 0) return null;

  return (
    <div className="border rounded-lg p-4 bg-rs-bg-base">
      <h3 className="text-sm font-medium text-rs-ink mb-3">嵌入商品卡預覽</h3>
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {shown.map(([listingId, card]) => (
          <Card key={listingId} className="p-3">
            <div className="flex gap-3">
              {card.coverImageUrl && (
                // eslint-disable-next-line @next/next/no-img-element -- 封面／精選圖是店家輸入的任意網址；next/image 需要 width／height，且網域須事先在 next.config 設定 remotePatterns
                <img
                  src={card.coverImageUrl}
                  alt={card.title}
                  className="w-16 h-16 object-cover rounded"
                />
              )}
              <div className="flex-1">
                <div className="font-medium">{card.title}</div>
                <div className="text-sm text-rs-ink-muted">{card.tenantName}</div>
                <div className="text-sm font-medium text-rs-primary">
                  {card.currentPrice !== null
                    ? `${card.currency} ${card.currentPrice.toLocaleString()}`
                    : '無定價'}
                </div>
              </div>
            </div>
          </Card>
        ))}
      </div>
    </div>
  );
}

export default function EditPostPage() {
  const router = useRouter();
  const params = useParams();
  const postId = params.id as string;

  const [loading, setLoading] = useState(false);
  const { data: post, loading: postLoading, reload: loadPost } = useAsyncData(
    () => getPost(postId),
    [postId],
    {
      errorMessage: () => '載入貼文失敗',
      // 載入失敗（含儲存後重新載入失敗）一律回後台首頁，與原本相同
      onError: (message, error) => {
        console.error('Failed to load post:', error);
        toast.error(message);
        router.push('/cms');
      },
    }
  );
  const { data: categoriesData } = useAsyncData(
    () => getCategories(),
    [],
    {
      errorMessage: () => '載入分類失敗',
      onError: (_message, error) => console.error('Failed to load categories:', error),
    }
  );
  const categories = categoriesData?.categories ?? [];

  // 表單內容：預設是伺服器上的貼文；使用者編輯後存成「針對哪一份貼文改的」草稿。
  // 儲存／發布／下架後重新載入，新的貼文一到，草稿就自動失效、表單改帶入新資料（重新載入失敗則保留使用者輸入的內容）。
  const [draft, setDraft] = useState<{ base: PostResponse | undefined; values: UpdatePostRequest } | null>(null);
  const seeded: UpdatePostRequest = post
    ? {
        title: post.title,
        content: post.content,
        categoryId: post.categoryId || undefined,
        tags: post.tags,
        featuredImageUrl: post.featuredImageUrl || undefined,
      }
    : {};
  const formData: UpdatePostRequest = draft !== null && draft.base === post ? draft.values : seeded;
  const setFormData = (values: UpdatePostRequest) => setDraft({ base: post, values });
  const [tagInput, setTagInput] = useState('');

  async function handleSubmit(e: React.FormEvent, autoPublish = false) {
    e.preventDefault();
    if (!formData.title?.trim()) {
      toast.error('請輸入標題');
      return;
    }
    if (!formData.content?.trim()) {
      toast.error('請輸入內容');
      return;
    }

    setLoading(true);
    try {
      await updatePost(postId, formData);

      if (autoPublish && post?.status === 'DRAFT') {
        await publishPost(postId);
        toast.success('貼文已更新並發布');
      } else {
        toast.success('貼文已儲存');
      }
      loadPost();
    } catch (error) {
      console.error('Failed to update post:', error);
      toast.error('更新貼文失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }

  async function handlePublish() {
    if (post?.status === 'DRAFT') {
      try {
        await publishPost(postId);
        toast.success('貼文已發布');
        loadPost();
      } catch (error) {
        console.error('Failed to publish post:', error);
        toast.error('發布失敗，請稍後再試');
      }
    }
  }

  async function handleUnpublish() {
    if (post?.status === 'PUBLISHED') {
      try {
        await unpublishPost(postId);
        toast.success('貼文已下架');
        loadPost();
      } catch (error) {
        console.error('Failed to unpublish post:', error);
        toast.error('下架失敗，請稍後再試');
      }
    }
  }

  async function handleDelete() {
    if (!(await confirmDialog({ message: '確定要刪除這篇貼文嗎？', confirmLabel: '刪除', destructive: true }))) return;
    try {
      await deletePost(postId);
      toast.success('貼文已刪除');
      router.push('/cms');
    } catch (error) {
      console.error('Failed to delete post:', error);
      toast.error('刪除失敗，請稍後再試');
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
    setFormData({ ...formData, content: e.target.value });
  }

  if (postLoading && post === undefined) {
    return <div className="p-6 text-center">載入中...</div>;
  }

  if (!post) {
    return <div className="p-6 text-center">找不到貼文</div>;
  }

  return (
    <div className="p-6 max-w-4xl mx-auto">
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-4">
          <Link href="/cms" className="text-rs-ink-muted hover:text-rs-ink">
            ← 返回
          </Link>
          <PageTitle>編輯貼文</PageTitle>
        </div>
        <div className="flex items-center gap-2">
          <StatusBadge domain="post" status={post.status} />
        </div>
      </div>

      <form onSubmit={(e) => handleSubmit(e, false)} className="space-y-6">
        {/* Title */}
        <div>
          <Label className="block mb-1">
            標題 <span className="text-rs-error">*</span>
          </Label>
          <Input
            type="text"
            value={formData.title || ''}
            onChange={(e) => setFormData({ ...formData, title: e.target.value })}
          />
        </div>

        {/* Content */}
        <div>
          <Label className="block mb-1">
            內容 <span className="text-rs-error">*</span>
          </Label>
          <Textarea
            value={formData.content || ''}
            onChange={handleContentChange}
            rows={12}
            className="font-mono"
          />
          <p className="mt-1 text-xs text-rs-ink-muted">
            嵌入商品卡語法: {'{{embed:listing:<listingId>}}'}
          </p>
        </div>

        {/* Embed Cards Preview */}
        <EmbeddedCardsPreview content={formData.content ?? ''} />

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

        {/* Post Info */}
        <div className="text-sm text-rs-ink-muted space-y-1 pt-4 border-t">
          <div>瀏覽次數: {post.viewCount}</div>
          <div>建立時間: {new Date(post.createdAt).toLocaleString('zh-TW')}</div>
          <div>更新時間: {new Date(post.updatedAt).toLocaleString('zh-TW')}</div>
          {post.publishedAt && (
            <div>發布時間: {new Date(post.publishedAt).toLocaleString('zh-TW')}</div>
          )}
        </div>

        {/* Actions */}
        <div className="flex gap-3 pt-4 border-t">
          <Button
            variant="default"
            type="submit"
            disabled={loading}
          >
            儲存變更
          </Button>

          {post.status === 'DRAFT' && (
            <Button
              variant="success"
              type="button"
              onClick={handlePublish}
            >
              發布
            </Button>
          )}

          {post.status === 'PUBLISHED' && (
            <Button
              variant="warning"
              type="button"
              onClick={handleUnpublish}
            >
              下架
            </Button>
          )}

          <Button type="button" variant="linkDestructive" onClick={handleDelete}>
            刪除
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