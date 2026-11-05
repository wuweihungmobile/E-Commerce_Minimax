'use client';

import { useEffect, useState } from 'react';
import {
  getCategories,
  createCategory,
  updateCategory,
  deleteCategory,
  CategoryResponse,
  CreateCategoryRequest,
  UpdateCategoryRequest
} from '@/services/cms';

export default function CategoriesPage() {
  const [categories, setCategories] = useState<CategoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    loadCategories();
  }, []);

  async function loadCategories() {
    setLoading(true);
    try {
      const data = await getCategories();
      setCategories(data.categories);
    } catch (error) {
      console.error('Failed to load categories:', error);
      alert('載入分類失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }

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
        alert('分類已更新');
      } else {
        const data: CreateCategoryRequest = {
          name,
          description: description || undefined,
        };
        await createCategory(data);
        alert('分類已建立');
      }
      setShowForm(false);
      loadCategories();
    } catch (error) {
      console.error('Failed to save category:', error);
      alert('儲存分類失敗，請稍後再試');
    } finally {
      setSaving(false);
    }
  }

  async function handleDelete(categoryId: string) {
    if (!confirm('確定要刪除這個分類嗎？')) return;
    try {
      await deleteCategory(categoryId);
      loadCategories();
    } catch (error) {
      console.error('Failed to delete category:', error);
      alert('刪除分類失敗，請稍後再試');
    }
  }

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-semibold">貼文分類管理</h1>
        <button
          onClick={openCreateForm}
          className="bg-rs-primary text-white px-4 py-2 rounded hover:bg-rs-primary-hover"
        >
          新增分類
        </button>
      </div>

      {/* Category Form Modal */}
      {showForm && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg p-6 w-full max-w-md">
            <h2 className="text-xl font-semibold mb-4">
              {editingId ? '編輯分類' : '新增分類'}
            </h2>
            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-sm font-medium text-rs-ink mb-1">
                  名稱 *
                </label>
                <input
                  type="text"
                  value={name}
                  onChange={(e) => setName(e.target.value)}
                  required
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-rs-primary focus:border-rs-primary"
                  placeholder="分類名稱"
                />
              </div>
              <div>
                <label className="block text-sm font-medium text-rs-ink mb-1">
                  描述
                </label>
                <textarea
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  rows={3}
                  className="w-full px-3 py-2 border rounded-lg focus:ring-2 focus:ring-rs-primary focus:border-rs-primary"
                  placeholder="分類描述（選填）"
                />
              </div>
              <div className="flex justify-end gap-2">
                <button
                  type="button"
                  onClick={() => setShowForm(false)}
                  className="bg-rs-hairline text-rs-ink px-4 py-2 rounded hover:bg-rs-hairline"
                >
                  取消
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="bg-rs-primary text-white px-4 py-2 rounded hover:bg-rs-primary-hover disabled:opacity-50"
                >
                  {saving ? '儲存中...' : '儲存'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Categories List */}
      {loading ? (
        <div className="text-center py-8">載入中...</div>
      ) : categories.length === 0 ? (
        <div className="text-center py-8 text-rs-ink-muted">尚無分類</div>
      ) : (
        <div className="bg-white rounded-lg overflow-hidden border border-rs-hairline">
          <table className="min-w-full">
            <thead className="bg-rs-bg-base">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-rs-ink-muted uppercase">
                  名稱
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-rs-ink-muted uppercase">
                  描述
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-rs-ink-muted uppercase">
                  排序
                </th>
                <th className="px-4 py-3 text-left text-xs font-medium text-rs-ink-muted uppercase">
                  狀態
                </th>
                <th className="px-4 py-3 text-right text-xs font-medium text-rs-ink-muted uppercase">
                  操作
                </th>
              </tr>
            </thead>
            <tbody className="divide-y">
              {categories.map((category) => (
                <tr key={category.id} className="hover:bg-rs-bg-base">
                  <td className="px-4 py-3 font-medium">{category.name}</td>
                  <td className="px-4 py-3 text-rs-ink-muted">
                    {category.description || '-'}
                  </td>
                  <td className="px-4 py-3 text-rs-ink-muted">
                    {category.sortOrder}
                  </td>
                  <td className="px-4 py-3">
                    <span
                      className={`px-2 py-1 rounded-full text-xs ${
                        category.isActive
                          ? 'bg-rs-success/10 text-rs-success'
                          : 'bg-rs-disabled-bg text-rs-ink'
                      }`}
                    >
                      {category.isActive ? '啟用' : '停用'}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-right">
                    <button
                      onClick={() => openEditForm(category)}
                      className="text-rs-primary hover:text-rs-primary-hover text-sm mr-2"
                    >
                      編輯
                    </button>
                    <button
                      onClick={() => handleDelete(category.id)}
                      className="text-rs-error hover:text-rs-error/80 text-sm"
                    >
                      刪除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
