'use client';

import { useEffect, useState, useCallback } from 'react';
import {
  getTemplates,
  createTemplate,
  updateTemplate,
  deleteTemplate,
  renderTemplate,
  NotificationTemplateDto,
  PageResponse,
  SearchTemplateRequest,
  NOTIFICATION_TYPES,
  CHANNELS,
} from '@/services/notification';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { NativeSelect } from '@/components/ui/native-select';
import { Input } from '@/components/ui/input';
import { Checkbox } from '@/components/ui/checkbox';
import { Table, TableHeader, TableRow, TableHead, TableBody, TableCell } from '@/components/ui/table';
import { PageTitle } from '@/components/ui/page-title';
import { EmptyState, LoadingState } from '@/components/ui/states';
import { Dialog, DialogContent, DialogDescription, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';
import { StatusBadge } from '@/components/ui/status-badge';

interface TemplateFormState {
  templateCode: string;
  notificationType: string;
  channel: string;
  name: string;
  subject: string;
  contentTemplate: string;
  isActive: boolean;
  priority: string;
}

const EMPTY_FORM: TemplateFormState = {
  templateCode: '',
  notificationType: NOTIFICATION_TYPES[0].value,
  channel: CHANNELS[0].value,
  name: '',
  subject: '',
  contentTemplate: '',
  isActive: true,
  priority: '0',
};

function extractErrorMessage(error: unknown, fallback: string): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const axiosErr = error as { response?: { data?: { message?: string } } };
    return axiosErr.response?.data?.message || fallback;
  }
  return fallback;
}

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
  const [showForm, setShowForm] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setForm] = useState<TemplateFormState>(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);

  const loadTemplates = useCallback(async () => {
    setLoading(true);
    try {
      const params: SearchTemplateRequest = {
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
      toast.error('載入通知模板失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }, [page, typeFilter, channelFilter, activeFilter]);

  useEffect(() => {
    loadTemplates();
  }, [loadTemplates]);

  function startCreate() {
    setForm(EMPTY_FORM);
    setEditingId(null);
    setShowForm(true);
  }

  function startEdit(template: NotificationTemplateDto) {
    setForm({
      templateCode: template.templateCode,
      notificationType: template.notificationType,
      channel: template.channel,
      name: template.name,
      subject: template.subject || '',
      contentTemplate: template.contentTemplate,
      isActive: template.isActive,
      priority: String(template.priority ?? 0),
    });
    setEditingId(template.id);
    setShowForm(true);
  }

  function cancelForm() {
    setShowForm(false);
    setEditingId(null);
    setForm(EMPTY_FORM);
  }

  async function handleSubmit() {
    if (!form.templateCode.trim() || !form.name.trim() || !form.contentTemplate.trim()) {
      toast.error('模板代碼、名稱與內容為必填');
      return;
    }
    setSubmitting(true);
    try {
      const payload = {
        templateCode: form.templateCode.trim(),
        notificationType: form.notificationType,
        channel: form.channel,
        name: form.name.trim(),
        subject: form.subject.trim() || undefined,
        contentTemplate: form.contentTemplate,
        isActive: form.isActive,
        priority: Number(form.priority) || 0,
      };
      if (editingId) {
        await updateTemplate(editingId, payload);
      } else {
        await createTemplate(payload);
      }
      cancelForm();
      loadTemplates();
    } catch (error) {
      toast.error(extractErrorMessage(error, editingId ? '更新模板失敗' : '新增模板失敗'));
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(templateId: string) {
    if (!(await confirmDialog({ message: '確定要刪除這個模板嗎？', confirmLabel: '刪除', destructive: true }))) return;
    setDeleting(templateId);
    try {
      await deleteTemplate(templateId);
      loadTemplates();
      toast.success('刪除成功');
    } catch (error) {
      console.error('Failed to delete template:', error);
      toast.error('刪除失敗，請稍後再試');
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
        <PageTitle>通知模板</PageTitle>
        <div className="flex items-center gap-4">
          <div className="text-sm text-rs-ink-muted">
            總計 {totalElements} 個模板
          </div>
          <Button
            variant="default"
            size="sm"
            onClick={startCreate}
          >
            新增模板
          </Button>
        </div>
      </div>

      {/* Filters */}
      <div className="mb-4 flex flex-wrap gap-4">
        <div>
          <Label className="block mb-1">通知類型</Label>
          <NativeSelect
            value={typeFilter}
            onChange={(e) => {
              setTypeFilter(e.target.value);
              setPage(0);
            }}
            className="w-auto"
          >
            <option value="">全部類型</option>
            {NOTIFICATION_TYPES.map((type) => (
              <option key={type.value} value={type.value}>{type.label}</option>
            ))}
          </NativeSelect>
        </div>
        <div>
          <Label className="block mb-1">頻道</Label>
          <NativeSelect
            value={channelFilter}
            onChange={(e) => {
              setChannelFilter(e.target.value);
              setPage(0);
            }}
            className="w-auto"
          >
            <option value="">全部頻道</option>
            {CHANNELS.map((channel) => (
              <option key={channel.value} value={channel.value}>{channel.label}</option>
            ))}
          </NativeSelect>
        </div>
        <div>
          <Label className="block mb-1">狀態</Label>
          <NativeSelect
            value={activeFilter}
            onChange={(e) => {
              setActiveFilter(e.target.value);
              setPage(0);
            }}
            className="w-auto"
          >
            <option value="">全部</option>
            <option value="true">啟用</option>
            <option value="false">停用</option>
          </NativeSelect>
        </div>
        <div className="flex items-end">
          <Button
            variant="default"
            size="sm"
            onClick={() => loadTemplates()}
          >
            篩選
          </Button>
        </div>
      </div>

      {/* Templates Table */}
      {loading ? (
        <LoadingState className="h-auto py-12" />
      ) : templates.length === 0 ? (
        <EmptyState title="尚無模板" />
      ) : (
        <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>模板名稱</TableHead>
                  <TableHead>類型</TableHead>
                  <TableHead>頻道</TableHead>
                  <TableHead>狀態</TableHead>
                  <TableHead>優先級</TableHead>
                  <TableHead>更新日期</TableHead>
                  <TableHead className="text-right">操作</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {templates.map((template) => (
                  <TableRow key={template.id}>
                    <TableCell>
                      <div className="text-sm font-medium text-rs-ink">{template.name}</div>
                      <div className="text-xs text-rs-ink-muted">{template.templateCode}</div>
                    </TableCell>
                    <TableCell className="text-sm text-rs-ink-muted">{getTypeLabel(template.notificationType)}</TableCell>
                    <TableCell className="text-sm text-rs-ink-muted">{getChannelLabel(template.channel)}</TableCell>
                    <TableCell>
                      <StatusBadge domain="activation" status={template.isActive ? 'ACTIVE' : 'INACTIVE'} />
                    </TableCell>
                    <TableCell className="text-sm text-rs-ink-muted">{template.priority}</TableCell>
                    <TableCell className="text-sm text-rs-ink-muted">{formatDate(template.updatedAt)}</TableCell>
                    <TableCell className="text-right text-sm space-x-2">
                      <Button
                        variant="link"
                        size="inline"
                        onClick={() => handlePreview(template)}
                      >
                        預覽
                      </Button>
                      <Button
                        variant="link"
                        size="inline"
                        onClick={() => startEdit(template)}
                      >
                        編輯
                      </Button>
                      <Button
                        variant="linkDestructive"
                        size="inline"
                        onClick={() => handleDelete(template.id)}
                        disabled={deleting === template.id}
                      >
                        {deleting === template.id ? '刪除中...' : '刪除'}
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex justify-center items-center gap-2 mt-6">
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(0)}
                disabled={page === 0}
              >
                首頁
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(page - 1)}
                disabled={page === 0}
              >
                上一頁
              </Button>
              <span className="text-sm text-rs-ink-muted">
                第 {page + 1} / {totalPages} 頁
              </span>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(page + 1)}
                disabled={page >= totalPages - 1}
              >
                下一頁
              </Button>
              <Button
                variant="outline"
                size="sm"
                onClick={() => setPage(totalPages - 1)}
                disabled={page >= totalPages - 1}
              >
                末頁
              </Button>
            </div>
          )}
        </>
      )}

      {/* Create/Edit Modal */}
      {showForm && (
        <Dialog open onOpenChange={(open) => { if (!open) cancelForm(); }}>
          <DialogContent className="max-w-2xl gap-0 p-0" aria-describedby={undefined}>
            <div className="p-6 border-b">
              <DialogTitle>{editingId ? '編輯模板' : '新增模板'}</DialogTitle>
            </div>
            <div className="p-6 grid grid-cols-1 md:grid-cols-2 gap-3">
              <div>
                <Label className="block text-xs mb-1">模板代碼 *</Label>
                <Input
                  type="text"
                  value={form.templateCode}
                  onChange={(e) => setForm({ ...form, templateCode: e.target.value })}
                />
              </div>
              <div>
                <Label className="block text-xs mb-1">名稱 *</Label>
                <Input
                  type="text"
                  value={form.name}
                  onChange={(e) => setForm({ ...form, name: e.target.value })}
                />
              </div>
              <div>
                <Label className="block text-xs mb-1">通知類型</Label>
                <NativeSelect
                  value={form.notificationType}
                  onChange={(e) => setForm({ ...form, notificationType: e.target.value })}
                >
                  {NOTIFICATION_TYPES.map((type) => (
                    <option key={type.value} value={type.value}>{type.label}</option>
                  ))}
                </NativeSelect>
              </div>
              <div>
                <Label className="block text-xs mb-1">頻道</Label>
                <NativeSelect
                  value={form.channel}
                  onChange={(e) => setForm({ ...form, channel: e.target.value })}
                >
                  {CHANNELS.map((channel) => (
                    <option key={channel.value} value={channel.value}>{channel.label}</option>
                  ))}
                </NativeSelect>
              </div>
              <div>
                <Label className="block text-xs mb-1">主旨</Label>
                <Input
                  type="text"
                  value={form.subject}
                  onChange={(e) => setForm({ ...form, subject: e.target.value })}
                />
              </div>
              <div>
                <Label className="block text-xs mb-1">優先級</Label>
                <Input
                  type="number"
                  value={form.priority}
                  onChange={(e) => setForm({ ...form, priority: e.target.value })}
                />
              </div>
              <div className="md:col-span-2">
                <Label className="block text-xs mb-1">
                  內容 * (以 {'{{變數名稱}}'} 標示變數，儲存時系統會自動偵測)
                </Label>
                <Textarea
                  value={form.contentTemplate}
                  onChange={(e) => setForm({ ...form, contentTemplate: e.target.value })}
                  rows={6}
                  className="font-mono"
                />
              </div>
              <div className="md:col-span-2 flex items-center gap-2">
                <Checkbox
                  id="isActive"
                  checked={form.isActive}
                  onChange={(e) => setForm({ ...form, isActive: e.target.checked })}
                />
                <Label htmlFor="isActive">啟用</Label>
              </div>
            </div>
            <div className="flex gap-2 p-6 border-t">
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
          </DialogContent>
        </Dialog>
      )}

      {/* Preview Modal */}
      {previewTemplate && (
        <Dialog
          open
          onOpenChange={(open) => {
            if (!open) {
              setPreviewTemplate(null);
              setPreviewContent(null);
            }
          }}
        >
          <DialogContent className="max-w-2xl gap-0 p-0">
            <div className="p-6 border-b">
              <DialogTitle>模板預覽: {previewTemplate.name}</DialogTitle>
              <DialogDescription className="mt-1">
                類型: {getTypeLabel(previewTemplate.notificationType)} | 頻道: {getChannelLabel(previewTemplate.channel)}
              </DialogDescription>
            </div>
            <div className="p-6">
              {previewLoading ? (
                <div className="text-center py-8 text-rs-ink-muted">渲染中...</div>
              ) : previewContent ? (
                <div>
                  <div className="mb-4">
                    <Label className="block mb-1">主旨</Label>
                    <div className="p-3 bg-rs-bg-base rounded text-sm">{previewContent.subject}</div>
                  </div>
                  <div>
                    <Label className="block mb-1">內容</Label>
                    <div className="p-3 bg-rs-bg-base rounded text-sm whitespace-pre-wrap">{previewContent.content}</div>
                  </div>
                </div>
              ) : null}
            </div>
          </DialogContent>
        </Dialog>
      )}
    </div>
  );
}