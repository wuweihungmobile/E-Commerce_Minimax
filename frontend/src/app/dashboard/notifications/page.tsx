'use client';

import { useEffect, useState } from 'react';
import {
  getTemplates,
  deleteTemplate,
  renderTemplate,
  NotificationTemplateDto,
  PageResponse,
  NOTIFICATION_TYPES,
  CHANNELS,
} from '@/services/notification';

export default function NotificationsPage() {
  const [templates, setTemplates] = useState<NotificationTemplateDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [typeFilter, setTypeFilter] = useState<string>('');
  const [channelFilter, setChannelFilter] = useState<string>('');
  const [activeFilter, setActiveFilter] = useState<string>('');
  const [deleting, setDeleting] = useState<string | null>(null);
  const [previewTemplate, setPreviewTemplate] = useState<NotificationTemplateDto | null>(null);
  const [previewContent, setPreviewContent] = useState<{ subject: string; content: string } | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);

  useEffect(() => {
    loadTemplates();
  }, [page, typeFilter, channelFilter, activeFilter]);

  async function loadTemplates() {
    setLoading(true);
    try {
      const params: any = {
        page,
        size: 20,
      };
      if (typeFilter) params.notificationType = typeFilter;
      if (channelFilter) params.channel = channelFilter;
      if (activeFilter) params.isActive = activeFilter === 'true';

      const data: PageResponse<NotificationTemplateDto> = await getTemplates(params);
      setTemplates(data.templates);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (error) {
      console.error('Failed to load templates:', error);
      alert('載入通知模板失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(templateId: string) {
    if (!confirm('確定要刪除這個模板嗎？')) return;
    setDeleting(templateId);
    try {
      await deleteTemplate(templateId);
      loadTemplates();
      alert('刪除成功');
    } catch (error) {
      console.error('Failed to delete template:', error);
      alert('刪除失敗，請稍後再試');
    } finally {
      setDeleting(null);
    }
  }

  async function handlePreview(template: NotificationTemplateDto) {
    setPreviewTemplate(template);
    setPreviewLoading(true);
    setPreviewContent(null);

    try {
      // Create sample variables from template variables
      const sampleVariables: Record<string, string> = {};
      if (template.variables && template.variables.length > 0) {
        template.variables.forEach((v) => {
          sampleVariables[v] = `{{${v}}}`;
        });
      } else {
        // Default sample variables
        sampleVariables['user_name'] = '張小明';
        sampleVariables['order_id'] = 'ORD-2024-001';
        sampleVariables['total_amount'] = '1,000';
        sampleVariables['currency'] = 'TWD';
      }

      const result = await renderTemplate({
        templateCode: template.templateCode,
        variables: sampleVariables,
      });

      setPreviewContent({
        subject: result.subject || '(無主旨)',
        content: result.content,
      });
    } catch (error) {
      console.error('Failed to render template:', error);
      setPreviewContent({
        subject: '渲染失敗',
        content: '無法渲染模板，請檢查變數設定',
      });
    } finally {
      setPreviewLoading(false);
    }
  }

  function formatDate(dateStr: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return date.toLocaleDateString('zh-TW');
  }

  function getTypeLabel(type: string): string {
    const found = NOTIFICATION_TYPES.find((t) => t.value === type);
    return found ? found.label : type;
  }

  function getChannelLabel(channel: string): string {
    const found = CHANNELS.find((c) => c.value === channel);
    return found ? found.label : channel;
  }

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">通知模板</h1>
        <div className="text-sm text-gray-500">
          總計 {totalElements} 個模板
        </div>
      </div>

      {/* Filters */}
      <div className="mb-4 flex flex-wrap gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">通知類型</label>
          <select
            value={typeFilter}
            onChange={(e) => {
              setTypeFilter(e.target.value);
              setPage(0);
            }}
            className="border rounded px-3 py-1.5 text-sm"
          >
            <option value="">全部類型</option>
            {NOTIFICATION_TYPES.map((type) => (
              <option key={type.value} value={type.value}>{type.label}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">頻道</label>
          <select
            value={channelFilter}
            onChange={(e) => {
              setChannelFilter(e.target.value);
              setPage(0);
            }}
            className="border rounded px-3 py-1.5 text-sm"
          >
            <option value="">全部頻道</option>
            {CHANNELS.map((channel) => (
              <option key={channel.value} value={channel.value}>{channel.label}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">狀態</label>
          <select
            value={activeFilter}
            onChange={(e) => {
              setActiveFilter(e.target.value);
              setPage(0);
            }}
            className="border rounded px-3 py-1.5 text-sm"
          >
            <option value="">全部</option>
            <option value="true">啟用</option>
            <option value="false">停用</option>
          </select>
        </div>
        <div className="flex items-end">
          <button
            onClick={() => loadTemplates()}
            className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
          >
            篩選
          </button>
        </div>
      </div>

      {/* Templates Table */}
      {loading ? (
        <div className="text-center py-12 text-gray-500">載入中...</div>
      ) : templates.length === 0 ? (
        <div className="text-center py-12 text-gray-500">尚無模板</div>
      ) : (
        <>
          <div className="bg-white rounded-lg shadow overflow-hidden">
            <table className="min-w-full divide-y divide-gray-200">
              <thead className="bg-gray-50">
                <tr>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">模板名稱</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">類型</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">頻道</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">狀態</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">優先級</th>
                  <th className="px-4 py-3 text-left text-xs font-medium text-gray-500 uppercase">更新日期</th>
                  <th className="px-4 py-3 text-right text-xs font-medium text-gray-500 uppercase">操作</th>
                </tr>
              </thead>
              <tbody className="bg-white divide-y divide-gray-200">
                {templates.map((template) => (
                  <tr key={template.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <div className="text-sm font-medium text-gray-900">{template.name}</div>
                      <div className="text-xs text-gray-500">{template.templateCode}</div>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">{getTypeLabel(template.notificationType)}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{getChannelLabel(template.channel)}</td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-1 text-xs rounded-full ${template.isActive ? 'bg-green-100 text-green-800' : 'bg-gray-100 text-gray-800'}`}>
                        {template.isActive ? '啟用' : '停用'}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">{template.priority}</td>
                    <td className="px-4 py-3 text-sm text-gray-600">{formatDate(template.updatedAt)}</td>
                    <td className="px-4 py-3 text-right text-sm space-x-2">
                      <button
                        onClick={() => handlePreview(template)}
                        className="text-blue-600 hover:text-blue-800"
                      >
                        預覽
                      </button>
                      <button
                        onClick={() => handleDelete(template.id)}
                        disabled={deleting === template.id}
                        className="text-red-600 hover:text-red-800 disabled:opacity-50"
                      >
                        {deleting === template.id ? '刪除中...' : '刪除'}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center items-center gap-2 mt-6">
              <button
                onClick={() => setPage(0)}
                disabled={page === 0}
                className="px-3 py-1 text-sm border rounded disabled:opacity-50"
              >
                首頁
              </button>
              <button
                onClick={() => setPage(page - 1)}
                disabled={page === 0}
                className="px-3 py-1 text-sm border rounded disabled:opacity-50"
              >
                上一頁
              </button>
              <span className="text-sm text-gray-600">
                第 {page + 1} / {totalPages} 頁
              </span>
              <button
                onClick={() => setPage(page + 1)}
                disabled={page >= totalPages - 1}
                className="px-3 py-1 text-sm border rounded disabled:opacity-50"
              >
                下一頁
              </button>
              <button
                onClick={() => setPage(totalPages - 1)}
                disabled={page >= totalPages - 1}
                className="px-3 py-1 text-sm border rounded disabled:opacity-50"
              >
                末頁
              </button>
            </div>
          )}
        </>
      )}

      {/* Preview Modal */}
      {previewTemplate && (
        <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
          <div className="bg-white rounded-lg shadow-xl max-w-2xl w-full mx-4 max-h-[80vh] overflow-y-auto">
            <div className="p-6 border-b">
              <div className="flex justify-between items-center">
                <h2 className="text-lg font-bold">模板預覽: {previewTemplate.name}</h2>
                <button
                  onClick={() => {
                    setPreviewTemplate(null);
                    setPreviewContent(null);
                  }}
                  className="text-gray-500 hover:text-gray-700"
                >
                  ✕
                </button>
              </div>
              <p className="text-sm text-gray-500 mt-1">
                類型: {getTypeLabel(previewTemplate.notificationType)} | 頻道: {getChannelLabel(previewTemplate.channel)}
              </p>
            </div>
            <div className="p-6">
              {previewLoading ? (
                <div className="text-center py-8 text-gray-500">渲染中...</div>
              ) : previewContent ? (
                <div>
                  <div className="mb-4">
                    <label className="block text-sm font-medium text-gray-700 mb-1">主旨</label>
                    <div className="p-3 bg-gray-50 rounded text-sm">{previewContent.subject}</div>
                  </div>
                  <div>
                    <label className="block text-sm font-medium text-gray-700 mb-1">內容</label>
                    <div className="p-3 bg-gray-50 rounded text-sm whitespace-pre-wrap">{previewContent.content}</div>
                  </div>
                </div>
              ) : null}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}