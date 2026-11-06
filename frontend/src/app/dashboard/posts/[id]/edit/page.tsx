'use client';

import { useEffect, useState, use, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { getPost, updatePost, getCategories, deletePost, publishPost, unpublishPost, PostResponse, CategoryResponse } from '@/services/cms';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { NativeSelect } from '@/components/ui/native-select';
import { Input } from '@/components/ui/input';
import { PageTitle } from '@/components/ui/page-title';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';
import { StatusBadge } from '@/components/ui/status-badge';

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
      toast.error('載入貼文失敗，請稍後再試');
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
      toast.error('載入分類失敗，請稍後再試');
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
      toast.success('貼文已更新');
    } catch (error) {
      console.error('Failed to update post:', error);
      toast.error('更新貼文失敗，請稍後再試');
      setSaving(false);
    }
  }

  async function handlePublish() {
    try {
      await publishPost(id);
      loadPost();
    } catch (error) {
      console.error('Failed to publish post:', error);
      toast.error('發布貼文失敗，請稍後再試');
    }
  }

  async function handleUnpublish() {
    try {
      await unpublishPost(id);
      loadPost();
    } catch (error) {
      console.error('Failed to unpublish post:', error);
      toast.error('下架貼文失敗，請稍後再試');
    }
  }

  async function handleDelete() {
    if (!(await confirmDialog({ message: '確定要刪除這篇貼文嗎？', confirmLabel: '刪除', destructive: true }))) return;
    try {
      await deletePost(id);
      router.push('/dashboard/posts');
    } catch (error) {
      console.error('Failed to delete post:', error);
      toast.error('刪除貼文失敗，請稍後再試');
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
        <PageTitle>編輯貼文</PageTitle>
        <div className="flex gap-2">
          {post.status === 'DRAFT' && (
            <Button
              variant="success"
              onClick={handlePublish}
            >
              發布
            </Button>
          )}
          {post.status === 'PUBLISHED' && (
            <Button
              variant="warning"
              onClick={handleUnpublish}
            >
              下架
            </Button>
          )}
        </div>
      </div>

      <Card className="p-6 mb-6">
        <div className="flex items-center gap-2 mb-4">
          <StatusBadge domain="post" status={post.status} />
          <span className="text-sm text-rs-ink-muted">
            瀏覽次數: {post.viewCount}
          </span>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
          <div>
            <Label className="block mb-1">標題 *</Label>
            <Input
              type="text"
              value={title}
              onChange={(e) => setTitle(e.target.value)}
              required
            />
          </div>

          <div>
            <Label className="block mb-1">內容 (Markdown)</Label>
            <Textarea
              value={content}
              onChange={(e) => setContent(e.target.value)}
              rows={15}
              className="font-mono"
            />
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
            />
          </div>

          <div className="flex items-center gap-4">
            <Button
              variant="default"
              type="submit"
              disabled={saving}
            >
              {saving ? '儲存中...' : '儲存更新'}
            </Button>
            <Button
              variant="linkDestructive"
              size="inline"
              type="button"
              onClick={handleDelete}
            >
              刪除貼文
            </Button>
          </div>
        </form>
      </Card>

      {/* Embedded Listings */}
      {post.embeds && post.embeds.length > 0 && (
        <Card className="p-6">
          <h2 className="text-lg font-medium mb-4">嵌入的卡片</h2>
          <div className="grid grid-cols-2 gap-4">
            {post.embeds.map((embed) => (
              <div key={embed.id} className="border rounded-lg p-4">
                <p className="text-sm text-rs-ink-muted">Listing ID: {embed.listingId}</p>
                <p className="text-sm text-rs-ink-muted">Type: {embed.listingType}</p>
              </div>
            ))}
          </div>
        </Card>
      )}
    </div>
  );
}