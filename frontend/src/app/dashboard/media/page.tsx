'use client';

import { useRef, useState } from 'react';
import {
  getMediaAssets,
  deleteMediaAsset,
  getMediaCategories,
  createMediaCategory,
  uploadMediaAssetMultipart,
  getMediaAssetFileBlob,
  updateMediaAsset,
  type MediaAssetDto,
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
import { Switch } from '@/components/ui/switch';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';
import { absolutePublicMediaUrl, MEDIA_ACCEPT, validateMediaFile } from '@/lib/media';

export default function MediaPage() {
  const [page, setPage] = useState(0);
  const [categoryFilter, setCategoryFilter] = useState<string>('');
  const [mimeTypeFilter, setMimeTypeFilter] = useState<string>('');
  const [keyword, setKeyword] = useState<string>('');
  const [deleting, setDeleting] = useState<string | null>(null);
  const [toggling, setToggling] = useState<string | null>(null);

  // Sprint 132（DEF-096）：真實檔案上傳 + 快速新增分類
  const [uploading, setUploading] = useState(false);
  const [uploadCategoryId, setUploadCategoryId] = useState<string>('');
  const [uploadTags, setUploadTags] = useState<string>('');
  const [newCategoryName, setNewCategoryName] = useState<string>('');
  const [creatingCategory, setCreatingCategory] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  // 分類只是篩選／上傳用的選項：載入失敗只記錄，下拉就沒有選項
  const { data: categoriesData, reload: loadCategories } = useAsyncData(
    () => getMediaCategories(),
    [],
    {
      errorMessage: () => '載入分類失敗',
      onError: (_message, error) => console.error('Failed to load categories:', error),
    }
  );
  const categories = categoriesData ?? [];

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

  async function handleUpload(file: File) {
    // 選檔當下就預檢類型與大小（DEF-466：自已移除的 /cms/media 移植；後端仍會再驗）
    const invalid = validateMediaFile(file);
    if (invalid) {
      toast.error(invalid);
      return;
    }
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

  // 公開開關（Sprint 336，DEF-482）：預設私有。公開＝任何拿到網址的人都能看，不需要登入；兩個方向都先確認——
  // 公開是不可逆的揭露（網址外流就收不回），取消公開則會讓已經用到網址的頁面顯示不出圖。
  // 「最多 5 分鐘」對應後端 PublicMediaController.CACHE_SECONDS（公開圖片的 Cache-Control max-age）。
  async function handleTogglePublic(item: MediaAssetDto, next: boolean) {
    const confirmed = await confirmDialog(
      next
        ? {
            title: '公開這個檔案？',
            message: '公開後，任何人只要拿到網址都能看到這個檔案，不需要登入。之後可以隨時取消公開。',
            confirmLabel: '公開',
          }
        : {
            title: '取消公開？',
            message:
              '取消公開後，已經使用這個網址的頁面（例如貼文的精選圖片）會顯示不出圖片；已被瀏覽器快取的副本最多還會有 5 分鐘看得到。',
            confirmLabel: '取消公開',
          }
    );
    if (!confirmed) return;
    setToggling(item.id);
    try {
      await updateMediaAsset(item.id, { isPublic: next });
      toast.success(next ? '已公開' : '已取消公開');
      reload();
    } catch (error) {
      console.error('Failed to update media visibility:', error);
      toast.error('更新公開設定失敗，請稍後再試');
    } finally {
      setToggling(null);
    }
  }

  async function handleCopyPublicUrl(item: MediaAssetDto) {
    if (!item.publicUrl) return;
    const url = absolutePublicMediaUrl(item.publicUrl);
    try {
      await navigator.clipboard.writeText(url);
      toast.success('已複製公開網址');
    } catch (error) {
      console.error('Failed to copy public url:', error);
      // 剪貼簿不可用（非安全環境、權限被擋）：把網址放進訊息，至少能手動選取複製
      toast.error(`無法自動複製，請手動複製：${url}`);
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
            accept={MEDIA_ACCEPT}
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
                  <div className="mt-2 flex items-center gap-2">
                    <Switch
                      checked={Boolean(item.isPublic)}
                      onCheckedChange={(next) => handleTogglePublic(item, next)}
                      disabled={toggling === item.id}
                      aria-label={`公開 ${item.fileName}`}
                      data-testid={`media-public-switch-${item.id}`}
                    />
                    <span className="text-xs text-rs-ink-muted" data-testid={`media-visibility-${item.id}`}>
                      {item.isPublic ? '公開' : '私有'}
                    </span>
                  </div>
                  {item.isPublic && item.publicUrl && (
                    <Button
                      variant="link"
                      size="inline"
                      onClick={() => handleCopyPublicUrl(item)}
                      className="mt-1"
                      data-testid={`media-copy-url-${item.id}`}
                    >
                      複製公開網址
                    </Button>
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