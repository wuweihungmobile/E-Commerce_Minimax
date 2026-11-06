'use client';

import { useState } from 'react';
import { useAsyncData } from '@/hooks/useAsyncData';
import {
  getCategories,
  createCategory,
  updateCategory,
  deleteCategory,
  CategoryResponse,
  CreateCategoryRequest,
  UpdateCategoryRequest
} from '@/services/cms';
import { Label } from '@/components/ui/label';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Textarea } from '@/components/ui/textarea';
import { Input } from '@/components/ui/input';
import { Table, TableHeader, TableRow, TableHead, TableBody, TableCell } from '@/components/ui/table';
import { PageTitle } from '@/components/ui/page-title';
import { Button } from '@/components/ui/button';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';
import { StatusBadge } from '@/components/ui/status-badge';

export default function CategoriesPage() {
  const { data: categoriesData, loading, reload: loadCategories } = useAsyncData(
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
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);

  function openCreateForm() {
    setEditingId(null);
    setName('');
    setDescription('');
    setShowForm(true);
  }

  function openEditForm(category: CategoryResponse) {
    setEditingId(category.id);
    setName(category.name);
    setDescription(category.description || '');
    setShowForm(true);
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    setSaving(true);
    try {
      if (editingId) {
        const data: UpdateCategoryRequest = {
          name,
          description: description || undefined,
        };
        await updateCategory(editingId, data);
        toast.success('分類已更新');
      } else {
        const data: CreateCategoryRequest = {
          name,
          description: description || undefined,
        };
        await createCategory(data);
        toast.success('分類已建立');
      }
      setShowForm(false);
      loadCategories();
    } catch (error) {
      console.error('Failed to save category:', error);
      toast.error('儲存分類失敗，請稍後再試');
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(categoryId: string) {
    if (!(await confirmDialog({ message: '確定要刪除這個分類嗎？', confirmLabel: '刪除', destructive: true }))) return;
    try {
      await deleteCategory(categoryId);
      loadCategories();
    } catch (error) {
      console.error('Failed to delete category:', error);
      toast.error('刪除分類失敗，請稍後再試');
    }
  }

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <PageTitle>貼文分類管理</PageTitle>
        <Button
          variant="default"
          onClick={openCreateForm}
        >
          新增分類
        </Button>
      </div>

      {/* Category Form Modal */}
      {showForm && (
        <Dialog open onOpenChange={(open) => { if (!open) setShowForm(false); }}>
          <DialogContent className="max-w-md" aria-describedby={undefined}>
            <DialogHeader>
              <DialogTitle>{editingId ? '編輯分類' : '新增分類'}</DialogTitle>
            </DialogHeader>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <Label className="block mb-1">
                  名稱 *
                </Label>
                <Input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                  placeholder="分類名稱"
                />
              </div>
              <div>
                <Label className="block mb-1">
                  描述
                </Label>
                <Textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={3}
                  placeholder="分類描述（選填）"
                />
              </div>
              <div className="flex justify-end gap-2">
                <Button
                  variant="secondary"
                  type="button"
                  onClick={() => setShowForm(false)}
                >
                  取消
                </Button>
                <Button
                  variant="default"
                  type="submit"
                  disabled={saving}
                >
                  {saving ? '儲存中...' : '儲存'}
                </Button>
              </div>
            </form>
          </DialogContent>
        </Dialog>
      )}

      {/* Categories List */}
      {loading ? (
        <div className="text-center py-8">載入中...</div>
      ) : categories.length === 0 ? (
        <div className="text-center py-8 text-rs-ink-muted">尚無分類</div>
      ) : (
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>
                  名稱
                </TableHead>
                <TableHead>
                  描述
                </TableHead>
                <TableHead>
                  排序
                </TableHead>
                <TableHead>
                  狀態
                </TableHead>
                <TableHead className="text-right">
                  操作
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {categories.map((category) => (
                <TableRow key={category.id}>
                  <TableCell className="font-medium">{category.name}</TableCell>
                  <TableCell className="text-rs-ink-muted">
                    {category.description || '-'}
                  </TableCell>
                  <TableCell className="text-rs-ink-muted">
                    {category.sortOrder}
                  </TableCell>
                  <TableCell>
                    <StatusBadge domain="activation" status={category.isActive ? 'ACTIVE' : 'INACTIVE'} />
                  </TableCell>
                  <TableCell className="text-right">
                    <Button
                      variant="link"
                      size="inline"
                      onClick={() => openEditForm(category)}
                      className="mr-2"
                    >
                      編輯
                    </Button>
                    <Button
                      variant="linkDestructive"
                      size="inline"
                      onClick={() => handleDelete(category.id)}
                    >
                      刪除
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
      )}
    </div>
  );
}
