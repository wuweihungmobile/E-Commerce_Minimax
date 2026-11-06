'use client';

import { useState } from 'react';
import Link from 'next/link';
import {
  getFaqCategories,
  getFaqCategoryStats,
  createFaqCategory,
  updateFaqCategory,
  deleteFaqCategory,
  FaqCategoryDto,
  FaqCategoryStatsDto,
} from '@/services/faq';
import { Label } from '@/components/ui/label';
import { Input } from '@/components/ui/input';
import { Table, TableHeader, TableRow, TableHead, TableBody, TableCell } from '@/components/ui/table';
import { PageTitle } from '@/components/ui/page-title';
import { EmptyState, LoadingState } from '@/components/ui/states';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';
import { apiErrorMessage } from '@/lib/api-error';
import { useAsyncData } from '@/hooks/useAsyncData';

interface CategoryFormState {
  name: string;
  slug: string;
  description: string;
  icon: string;
  sortOrder: string;
}

const EMPTY_FORM: CategoryFormState = { name: '', slug: '', description: '', icon: '', sortOrder: '0' };

const NO_STATS: Record<string, FaqCategoryStatsDto> = {};

export default function FaqCategoriesPage() {
  const { data, loading, reload: loadCategories } = useAsyncData(
    async () => {
      const [categoryList, statsList] = await Promise.all([getFaqCategories(), getFaqCategoryStats()]);
      const statsMap: Record<string, FaqCategoryStatsDto> = {};
      statsList.forEach((s) => {
        statsMap[s.categoryId] = s;
      });
      return { categories: categoryList, stats: statsMap };
    },
    [],
    {
      errorMessage: () => '載入分類失敗，請稍後再試',
      onError: (message, error) => {
        console.error('Failed to load FAQ categories:', error);
        toast.error(message);
      },
    }
  );
  const categories = data?.categories ?? [];
  const stats = data?.stats ?? NO_STATS;
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<CategoryFormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);

  function startCreate() {
    setForm(EMPTY_FORM);
    setEditingId(null);
    setShowCreateForm(true);
  }

  function startEdit(category: FaqCategoryDto) {
    setForm({
      name: category.name,
      slug: category.slug,
      description: category.description || '',
      icon: category.icon || '',
      sortOrder: String(category.sortOrder ?? 0),
    });
    setEditingId(category.id);
    setShowCreateForm(true);
  }

  function cancelForm() {
    setShowCreateForm(false);
    setEditingId(null);
    setForm(EMPTY_FORM);
  }

  async function handleSubmit() {
    if (!form.name.trim() || !form.slug.trim()) {
      toast.error('名稱與 slug 為必填');
      return;
    }
    setSubmitting(true);
    try {
      const payload = {
        name: form.name.trim(),
        slug: form.slug.trim(),
        description: form.description.trim() || undefined,
        icon: form.icon.trim() || undefined,
        sortOrder: Number(form.sortOrder) || 0,
      };
      if (editingId) {
        await updateFaqCategory(editingId, payload);
      } else {
        await createFaqCategory(payload);
      }
      cancelForm();
      loadCategories();
    } catch (error) {
      toast.error(apiErrorMessage(error, editingId ? '更新分類失敗' : '新增分類失敗'));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(category: FaqCategoryDto) {
    if (!(await confirmDialog({ message: `確定要刪除分類「${category.name}」嗎？`, confirmLabel: '刪除', destructive: true }))) return;
    try {
      await deleteFaqCategory(category.id);
      loadCategories();
    } catch (error) {
      toast.error(apiErrorMessage(error, '刪除分類失敗（分類底下可能仍有 FAQ 文章）'));
    }
  }

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <div className="flex items-center gap-3">
          <Link href="/dashboard/faq" className="text-sm text-rs-primary hover:underline">
            ← 返回 FAQ 列表
          </Link>
          <PageTitle>FAQ 分類管理</PageTitle>
        </div>
        <Button
          variant="default"
          size="sm"
          onClick={startCreate}
        >
          新增分類
        </Button>
      </div>

      {showCreateForm && (
        <Card className="p-4 mb-6">
          <h2 className="text-sm font-semibold mb-3">{editingId ? '編輯分類' : '新增分類'}</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <Label className="block text-xs mb-1">名稱 *</Label>
              <Input
                type="text"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
              />
            </div>
            <div>
              <Label className="block text-xs mb-1">Slug *</Label>
              <Input
                type="text"
                value={form.slug}
                onChange={(e) => setForm({ ...form, slug: e.target.value })}
              />
            </div>
            <div>
              <Label className="block text-xs mb-1">描述</Label>
              <Input
                type="text"
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
              />
            </div>
            <div>
              <Label className="block text-xs mb-1">Icon</Label>
              <Input
                type="text"
                value={form.icon}
                onChange={(e) => setForm({ ...form, icon: e.target.value })}
              />
            </div>
            <div>
              <Label className="block text-xs mb-1">排序</Label>
              <Input
                type="number"
                value={form.sortOrder}
                onChange={(e) => setForm({ ...form, sortOrder: e.target.value })}
              />
            </div>
          </div>
          <div className="flex gap-2 mt-4">
            <Button
              variant="default"
              size="sm"
              onClick={handleSubmit}
              disabled={submitting}
            >
              {submitting ? '儲存中...' : '儲存'}
            </Button>
            <Button
              variant="outline"
              size="sm"
              onClick={cancelForm}
            >
              取消
            </Button>
          </div>
        </Card>
      )}

      {loading ? (
        <LoadingState className="h-auto py-12" />
      ) : categories.length === 0 ? (
        <EmptyState title="尚無分類" />
      ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>名稱</TableHead>
                <TableHead>Slug</TableHead>
                <TableHead>文章數</TableHead>
                <TableHead>已發布</TableHead>
                <TableHead className="text-right">操作</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {categories.map((category) => {
                const stat = stats[category.id];
                return (
                  <TableRow key={category.id}>
                    <TableCell>
                      <div className="text-sm font-medium text-rs-ink">{category.name}</div>
                      {category.description && (
                        <div className="text-xs text-rs-ink-muted">{category.description}</div>
                      )}
                    </TableCell>
                    <TableCell className="text-sm text-rs-ink-muted font-mono">{category.slug}</TableCell>
                    <TableCell className="text-sm text-rs-ink-muted">{stat?.totalArticles ?? 0}</TableCell>
                    <TableCell className="text-sm text-rs-ink-muted">{stat?.publishedArticles ?? 0}</TableCell>
                    <TableCell className="text-right text-sm">
                      <Button
                        variant="link"
                        size="inline"
                        onClick={() => startEdit(category)}
                        className="mr-3"
                      >
                        編輯
                      </Button>
                      <Button
                        variant="linkDestructive"
                        size="inline"
                        onClick={() => handleDelete(category)}
                      >
                        刪除
                      </Button>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
      )}
    </div>
  );
}
