'use client';

import { useEffect, useRef, useState } from 'react';
import {
  getMediaAssets,
  deleteMediaAsset,
  getMediaCategories,
  createMediaCategory,
  uploadMediaAssetMultipart,
  getMediaAssetFileBlob,
  MediaCategoryDto,
} from '@/services/media';
import { useAsyncData } from '@/hooks/useAsyncData';
import { AuthenticatedImage } from '@/components/ui/authenticated-image';
import { Label } from '@/components/ui/label';
import { NativeSelect } from '@/components/ui/native-select';
import { Input } from '@/components/ui/input';
import { PageTitle } from '@/components/ui/page-title';
import { EmptyState, LoadingState } from '@/components/ui/states';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';

export default function MediaPage() {
  const [page, setPage] = useState(0);
  const [categoryFilter, setCategoryFilter] = useState<string>('');
  const [mimeTypeFilter, setMimeTypeFilter] = useState<string>('');
  const [keyword, setKeyword] = useState<string>('');
  const [categories, setCategories] = useState<MediaCategoryDto[]>([]);
  const [deleting, setDeleting] = useState<string | null>(null);

  // Sprint 132（DEF-096）：真實檔案上傳 + 快速新增分類
  const [uploading, setUploading] = useState(false);
  const [uploadCategoryId, setUploadCategoryId] = useState<string>('');
  const [uploadTags, setUploadTags] = useState<string>('');
  const [newCategoryName, setNewCategoryName] = useState<string>('');
  const [creatingCategory, setCreatingCategory] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    loadCategories();
  }, []);

  const { data, loading, reload } = useAsyncData(
    () => {
      const params: {
        page: number;
        size: number;
        categoryId?: string;
        mimeType?: string;
        keyword?: string;
      } = {
        page,
        size: 20,
      };
      if (categoryFilter) params.categoryId = categoryFilter;
      if (mimeTypeFilter) params.mimeType = mimeTypeFilter;
      if (keyword) params.keyword = keyword;
      return getMediaAssets(params);
    },
    [page, categoryFilter, mimeTypeFilter, keyword],
    {
      errorMessage: () => '載入媒體失敗，請稍後再試',
      onError: (message, error) => {
        console.error('Failed to load media:', error);
        toast.error(message);
      },
    }
  );
  const media = data?.content ?? [];
  const totalPages = data?.totalPages ?? 0;
  const totalElements = data?.totalElements ?? 0;

  async function loadCategories() {
    try {
      const data = await getMediaCategories();
      setCategories(data);
    } catch (error) {
      console.error('Failed to load categories:', error);
    }
  }

  async function handleUpload(file: File) {
    setUploading(true);
    try {
      const tags = uploadTags
        .split(',')
        .map((t) => t.trim())
        .filter(Boolean);
      await uploadMediaAssetMultipart(file, {
        categoryId: uploadCategoryId || undefined,
        tags: tags.length > 0 ? tags : undefined,
      });
      toast.success('上傳成功');
      setPage(0);
      reload();
    } catch (error) {
      console.error('Failed to upload media:', error);
      toast.error('上傳失敗，請稍後再試');
    } finally {
      setUploading(false);
    }
  }

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (file) {
      handleUpload(file);
    }
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }

  async function handleCreateCategory() {
    if (!newCategoryName.trim()) return;
    setCreatingCategory(true);
    try {
      await createMediaCategory({ name: newCategoryName.trim() });
      setNewCategoryName('');
      loadCategories();
    } catch (error) {
      console.error('Failed to create category:', error);
      toast.error('新增分類失敗，請稍後再試');
    } finally {
      setCreatingCategory(false);
    }
  }

  async function handleDelete(mediaId: string) {
    if (!(await confirmDialog({ message: '確定要刪除這個媒體嗎？', confirmLabel: '刪除', destructive: true }))) return;
    setDeleting(mediaId);
    try {
      await deleteMediaAsset(mediaId);
      reload();
      toast.success('刪除成功');
    } catch (error) {
      console.error('Failed to delete media:', error);
      toast.error('刪除失敗，請稍後再試');
    } finally {
      setDeleting(null);
    }
  }

  function formatFileSize(bytes: number): string {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  function getFileTypeIcon(mimeType: string): string {
    if (mimeType.startsWith('image/')) return '🖼️';
    if (mimeType.startsWith('video/')) return '🎬';
    if (mimeType.startsWith('audio/')) return '🎵';
    if (mimeType.includes('pdf')) return '📄';
    if (mimeType.includes('word') || mimeType.includes('document')) return '📝';
    if (mimeType.includes('sheet') || mimeType.includes('excel')) return '📊';
    return '📁';
  }

  function isImage(mimeType: string): boolean {
    return mimeType.startsWith('image/');
  }

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <PageTitle>媒體中心</PageTitle>
        <div className="text-sm text-rs-ink-muted">
          總計 {totalElements} 個媒體
        </div>
      </div>

      {/* Upload */}
      <Card className="mb-4 p-4 flex flex-wrap items-end gap-4">
        <div>
          <Label className="block mb-1">上傳到分類（選填）</Label>
          <NativeSelect
            value={uploadCategoryId}
            onChange={(e) => setUploadCategoryId(e.target.value)}
            className="w-auto"
          >
            <option value="">未分類</option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id}>{cat.name}</option>
            ))}
          </NativeSelect>
        </div>
        <div>
          <Label className="block mb-1">標籤（選填，以逗號分隔）</Label>
          <Input
            type="text"
            value={uploadTags}
            onChange={(e) => setUploadTags(e.target.value)}
            placeholder="banner, 首頁"
            className="w-48"
          />
        </div>
        <div>
          <Input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp,video/mp4,video/quicktime,video/x-msvideo,application/pdf"
            onChange={handleFileChange}
            className="hidden w-auto"
          />
          <Button
            variant="default"
            size="sm"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
          >
            {uploading ? '上傳中...' : '上傳媒體'}
          </Button>
        </div>
        <div className="flex items-end gap-2 ml-auto">
          <div>
            <Label className="block mb-1">新增分類</Label>
            <Input
              type="text"
              value={newCategoryName}
              onChange={(e) => setNewCategoryName(e.target.value)}
              placeholder="分類名稱"
              className="w-32"
            />
          </div>
          <Button
            variant="outline"
            size="sm"
            onClick={handleCreateCategory}
            disabled={creatingCategory || !newCategoryName.trim()}
          >
            {creatingCategory ? '新增中...' : '新增'}
          </Button>
        </div>
      </Card>

      {/* Filters */}
      <div className="mb-4 flex flex-wrap gap-4">
        <div>
          <Label className="block mb-1">分類</Label>
          <NativeSelect
            value={categoryFilter}
            onChange={(e) => {
              setCategoryFilter(e.target.value);
              setPage(0);
            }}
            className="w-auto"
          >
            <option value="">全部分類</option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id}>{cat.name}</option>
            ))}
          </NativeSelect>
        </div>
        <div>
          <Label className="block mb-1">檔案類型</Label>
          <NativeSelect
            value={mimeTypeFilter}
            onChange={(e) => {
              setMimeTypeFilter(e.target.value);
              setPage(0);
            }}
            className="w-auto"
          >
            <option value="">全部類型</option>
            <option value="image/">圖片</option>
            <option value="video/">影片</option>
            <option value="application/pdf">PDF</option>
          </NativeSelect>
        </div>
        <div>
          <Label className="block mb-1">關鍵字搜尋</Label>
          <Input
            type="text"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value);
              setPage(0);
            }}
            placeholder="搜尋檔案名稱..."
            className="w-48"
          />
        </div>
        <div className="flex items-end">
          <Button
            variant="default"
            size="sm"
            onClick={() => reload()}
          >
            搜尋
          </Button>
        </div>
      </div>

      {/* Media Grid */}
      {loading ? (
        <LoadingState className="h-auto py-12" />
      ) : media.length === 0 ? (
        <EmptyState title="尚無媒體" />
      ) : (
        <>
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
            {media.map((item) => (
              <Card key={item.id} className="overflow-hidden">
                <div className="aspect-square bg-rs-disabled-bg flex items-center justify-center relative">
                  {isImage(item.mimeType) ? (
                    <AuthenticatedImage
                      id={item.id}
                      fetchBlob={getMediaAssetFileBlob}
                      alt={item.fileName}
                      className="object-cover w-full h-full"
                      fallback={<span className="text-4xl">{getFileTypeIcon(item.mimeType)}</span>}
                    />
                  ) : (
                    <span className="text-4xl">{getFileTypeIcon(item.mimeType)}</span>
                  )}
                </div>
                <div className="p-3">
                  <p className="text-sm font-medium truncate" title={item.fileName}>
                    {item.fileName}
                  </p>
                  <p className="text-xs text-rs-ink-muted">{formatFileSize(item.fileSize)}</p>
                  {item.categoryName && (
                    <p className="text-xs text-rs-primary">{item.categoryName}</p>
                  )}
                  {item.tags && item.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-1">
                      {item.tags.slice(0, 2).map((tag, i) => (
                        <span key={i} className="text-xs bg-rs-disabled-bg px-1 rounded">{tag}</span>
                      ))}
                    </div>
                  )}
                  <Button
                    variant="linkDestructive"
                    size="inline"
                    onClick={() => handleDelete(item.id)}
                    disabled={deleting === item.id}
                    className="mt-2"
                  >
                    {deleting === item.id ? '刪除中...' : '刪除'}
                  </Button>
                </div>
              </Card>
            ))}
          </div>

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
    </div>
  );
}