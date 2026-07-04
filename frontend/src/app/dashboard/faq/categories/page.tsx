'use client';

import { useEffect, useState } from 'react';
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

interface CategoryFormState {
  name: string;
  slug: string;
  description: string;
  icon: string;
  sortOrder: string;
}

const EMPTY_FORM: CategoryFormState = { name: '', slug: '', description: '', icon: '', sortOrder: '0' };

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { message?: string } } };
    return axiosErr.response?.data?.message || fallback;
  }
  return fallback;
}

export default function FaqCategoriesPage() {
  const [categories, setCategories] = useState<FaqCategoryDto[]>([]);
  const [stats, setStats] = useState<Record<string, FaqCategoryStatsDto>>({});
  const [loading, setLoading] = useState(true);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<CategoryFormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    loadCategories();
  }, []);

  async function loadCategories() {
    setLoading(true);
    try {
      const [categoryList, statsList] = await Promise.all([getFaqCategories(), getFaqCategoryStats()]);
      setCategories(categoryList);
      const statsMap: Record<string, FaqCategoryStatsDto> = {};
      statsList.forEach((s) => {
        statsMap[s.categoryId] = s;
      });
      setStats(statsMap);
    } catch (error) {
      console.error('Failed to load FAQ categories:', error);
      alert('載入分類失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }

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
      alert('名稱與 slug 為必填');
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
      alert(extractErrorMessage(error, editingId ? '更新分類失敗' : '新增分類失敗'));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(category: FaqCategoryDto) {
    if (!confirm(`確定要刪除分類「${category.name}」嗎？`)) return;
    try {
      await deleteFaqCategory(category.id);
      loadCategories();
    } catch (error) {
      alert(extractErrorMessage(error, '刪除分類失敗（分類底下可能仍有 FAQ 文章）'));
    }
  }

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <div className="flex items-center gap-3">
          <Link href="/dashboard/faq" className="text-sm text-blue-600 hover:underline">
            ← 返回 FAQ 列表
          </Link>
          <h1 className="text-2xl font-bold">FAQ 分類管理</h1>
        </div>
        <button
          onClick={startCreate}
          className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
        >
          新增分類
        </button>
      </div>

      {showCreateForm && (
        <div className="bg-white border rounded-lg shadow p-4 mb-6">
          <h2 className="text-sm font-semibold mb-3">{editingId ? '編輯分類' : '新增分類'}</h2>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">名稱 *</label>
              <input
                type="text"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                className="border rounded px-3 py-1.5 text-sm w-full"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Slug *</label>
              <input
                type="text"
                value={form.slug}
                onChange={(e) => setForm({ ...form, slug: e.target.value })}
                className="border rounded px-3 py-1.5 text-sm w-full"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">描述</label>
              <input
                type="text"
                value={form.description}
                onChange={(e) => setForm({ ...form, description: e.target.value })}
                className="border rounded px-3 py-1.5 text-sm w-full"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">Icon</label>
              <input
                type="text"
                value={form.icon}
                onChange={(e) => setForm({ ...form, icon: e.target.value })}
                className="border rounded px-3 py-1.5 text-sm w-full"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-gray-700 mb-1">排序</label>
              <input
                type="number"
                value={form.sortOrder}
                onChange={(e) => setForm({ ...form, sortOrder: e.target.value })}
                className="border rounded px-3 py-1.5 text-sm w-full"
              />
            </div>
          </div>
          <div className="flex gap-2 mt-4">
            <button
              onClick={handleSubmit}
              disabled={submitting}
              className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm hover:bg-blue-700 disabled:opacity-50"
            >
              {submitting ? '儲存中...' : '儲存'}
            </button>
            <button
              onClick={cancelForm}
              className="px-4 py-1.5 border rounded text-sm hover:bg-gray-50"
            >
              取消
            </button>
          </div>
        </div>
      )}

      {loading ? (
        <div className="text-center py-12 text-gray-500">載入中...</div>
      ) : categories.length === 0 ? (
        <div className="text-center py-12 text-gray-500">尚無分類</div>
      ) : (
        <div className="bg-white rounded-lg shadow overflow-hidden">
          <table className="min-w-full divide-y divide-gray-200">
            <thead className="bg-gray-50">
              <tr>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">名稱</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">Slug</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">文章數</th>
                <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">已發布</th>
                <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">操作</th>
              </tr>
            </thead>
            <tbody className="bg-white divide-y divide-gray-200">
              {categories.map((category) => {
                const stat = stats[category.id];
                return (
                  <tr key={category.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <div className="text-sm font-medium text-gray-900">{category.name}</div>
                      {category.description && (
                        <div className="text-xs text-gray-500">{category.description}</div>
                      )}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600 font-mono">{category.slug}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{stat?.totalArticles ?? 0}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{stat?.publishedArticles ?? 0}</td>
                    <td className="px-4 py-3 text-right text-sm">
                      <button
                        onClick={() => startEdit(category)}
                        className="text-blue-600 hover:text-blue-800 mr-3"
                      >
                        編輯
                      </button>
                      <button
                        onClick={() => handleDelete(category)}
                        className="text-red-600 hover:text-red-800"
                      >
                        刪除
                      </button>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
