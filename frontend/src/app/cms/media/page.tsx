'use client';

import { useState, useEffect, useRef, useCallback } from 'react';
import Link from 'next/link';
import {
  getMediaList,
  uploadMediaMultipart,
  deleteMedia,
  getMediaFileBlob,
  MediaResponse,
} from '@/services/cms';
import { AuthenticatedImage } from '@/components/ui/authenticated-image';
import { Input } from '@/components/ui/input';
import { PageTitle } from '@/components/ui/page-title';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Chip } from '@/components/ui/chip';
import { toast } from '@/components/ui/toast';
import { confirmDialog } from '@/components/ui/confirm-dialog';

export default function MediaLibraryPage() {
  const [mediaList, setMediaList] = useState<MediaResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [filter, setFilter] = useState<string>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadMedia = useCallback(async () => {
    setLoading(true);
    try {
      const params: { page?: number; size?: number; fileType?: string } = {
        page,
        size: 24,
      };
      if (filter) params.fileType = filter;
      const data = await getMediaList(params);
      setMediaList(data.items);
      setTotalCount(data.totalCount);
      setTotalPages(data.totalPages);
    } catch (error) {
      console.error('Failed to load media:', error);
      toast.error('載入媒體庫失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }, [filter, page]);

  useEffect(() => {
    loadMedia();
  }, [loadMedia]);

  async function handleUpload(file: File) {
    // Validate file type
    const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'video/mp4', 'application/pdf'];
    if (!allowedTypes.includes(file.type)) {
      toast.error('不支援的檔案類型');
      return;
    }

    // Validate file size
    const maxSizes: Record<string, number> = {
      'image/jpeg': 10 * 1024 * 1024,
      'image/png': 10 * 1024 * 1024,
      'image/gif': 10 * 1024 * 1024,
      'image/webp': 10 * 1024 * 1024,
      'video/mp4': 100 * 1024 * 1024,
      'application/pdf': 5 * 1024 * 1024,
    };
    const maxSize = maxSizes[file.type] || 10 * 1024 * 1024;
    if (file.size > maxSize) {
      toast.error('檔案大小超過限制');
      return;
    }

    setUploading(true);
    try {
      await uploadMediaMultipart(file);
      toast.success('上傳成功');
      loadMedia();
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
    // Reset input
    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  }

  async function handleDelete(mediaId: string) {
    if (!(await confirmDialog({ message: '確定要刪除這個媒體檔案嗎？', confirmLabel: '刪除', destructive: true }))) return;
    try {
      await deleteMedia(mediaId);
      toast.success('刪除成功');
      loadMedia();
    } catch (error) {
      console.error('Failed to delete media:', error);
      const errObj = error as { response?: { data?: { message?: string } } };
      const message = errObj?.response?.data?.message || '刪除失敗，請稍後再試';
      toast.error(message);
    }
  }

  function formatFileSize(bytes: number): string {
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(1) + ' MB';
  }

  function getFileIcon(mimeType: string) {
    if (mimeType.startsWith('image/')) return '🖼️';
    if (mimeType.startsWith('video/')) return '🎬';
    if (mimeType === 'application/pdf') return '📄';
    return '📁';
  }

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <div className="flex items-center gap-4">
          <PageTitle>媒體庫</PageTitle>
          <Link href="/cms" className="text-rs-primary hover:text-rs-primary-hover text-sm">
            返回 CMS
          </Link>
        </div>
        <div>
          <Input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp,video/mp4,application/pdf"
            onChange={handleFileChange}
            className="hidden w-auto"
          />
          <Button
            variant="default"
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
          >
            {uploading ? '上傳中...' : '上傳檔案'}
          </Button>
        </div>
      </div>

      {/* Filter */}
      <div className="mb-4 flex gap-2">
        <Chip
          onClick={() => { setFilter(''); setPage(0); }} selected={!filter}
        >
          全部 ({totalCount})
        </Chip>
        <Chip
          onClick={() => { setFilter('IMAGE'); setPage(0); }} selected={filter === 'IMAGE'}
        >
          圖片
        </Chip>
        <Chip
          onClick={() => { setFilter('VIDEO'); setPage(0); }} selected={filter === 'VIDEO'}
        >
          影片
        </Chip>
        <Chip
          onClick={() => { setFilter('DOCUMENT'); setPage(0); }} selected={filter === 'DOCUMENT'}
        >
          文件
        </Chip>
      </div>

      {/* Media Grid */}
      {loading ? (
        <div className="text-center py-8">載入中...</div>
      ) : mediaList.length === 0 ? (
        <div className="text-center py-8 text-rs-ink-muted">尚無媒體檔案</div>
      ) : (
        <>
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
            {mediaList.map((media) => (
              <Card
                key={media.id}
                className="overflow-hidden hover:border-rs-primary transition-colors"
              >
                <div className="aspect-square flex items-center justify-center bg-rs-disabled-bg relative group">
                  {media.mimeType.startsWith('image/') ? (
                    <AuthenticatedImage
                      id={media.id}
                      fetchBlob={getMediaFileBlob}
                      alt={media.originalName}
                      className="object-cover w-full h-full"
                      fallback={<div className="text-4xl">{getFileIcon(media.mimeType)}</div>}
                    />
                  ) : (
                    <div className="text-4xl">{getFileIcon(media.mimeType)}</div>
                  )}
                  <Button
                    variant="destructive"
                    size="icon"
                    onClick={() => handleDelete(media.id)}
                    className="absolute top-2 right-2 w-6 h-6 opacity-0 group-hover:opacity-100"
                  >
                    ×
                  </Button>
                </div>
                <div className="p-2">
                  <div className="text-sm truncate" title={media.originalName}>
                    {media.originalName}
                  </div>
                  <div className="text-xs text-rs-ink-muted">
                    {formatFileSize(media.fileSize)}
                  </div>
                </div>
              </Card>
            ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="mt-6 flex justify-center gap-2">
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
              >
                上一頁
              </Button>
              <span className="px-3 py-1">
                第 {page + 1} 頁，共 {totalPages} 頁
              </span>
              <Button
                variant="secondary"
                size="sm"
                onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                disabled={page >= totalPages - 1}
              >
                下一頁
              </Button>
            </div>
          )}
        </>
      )}
    </div>
  );
}