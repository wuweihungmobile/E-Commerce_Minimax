'use client';

import { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import {
  getMediaList,
  uploadMediaMultipart,
  deleteMedia,
  getMediaFileBlob,
  MediaResponse,
} from '@/services/cms';
import { AuthenticatedImage } from '@/components/ui/authenticated-image';

export default function MediaLibraryPage() {
  const [mediaList, setMediaList] = useState<MediaResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const [filter, setFilter] = useState<string>('');
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalCount, setTotalCount] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const loadMedia = async () => {
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
      alert('載入媒體庫失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadMedia();
  }, [filter, page]);

  async function handleUpload(file: File) {
    // Validate file type
    const allowedTypes = ['image/jpeg', 'image/png', 'image/gif', 'image/webp', 'video/mp4', 'application/pdf'];
    if (!allowedTypes.includes(file.type)) {
      alert('不支援的檔案類型');
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
      alert('檔案大小超過限制');
      return;
    }

    setUploading(true);
    try {
      await uploadMediaMultipart(file);
      alert('上傳成功');
      loadMedia();
    } catch (error) {
      console.error('Failed to upload media:', error);
      alert('上傳失敗，請稍後再試');
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
    if (!confirm('確定要刪除這個媒體檔案嗎？')) return;
    try {
      await deleteMedia(mediaId);
      alert('刪除成功');
      loadMedia();
    } catch (error) {
      console.error('Failed to delete media:', error);
      const errObj = error as { response?: { data?: { message?: string } } };
      const message = errObj?.response?.data?.message || '刪除失敗，請稍後再試';
      alert(message);
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
          <h1 className="text-2xl font-bold">媒體庫</h1>
          <Link href="/cms" className="text-blue-600 hover:text-blue-800 text-sm">
            返回 CMS
          </Link>
        </div>
        <div>
          <input
            ref={fileInputRef}
            type="file"
            accept="image/jpeg,image/png,image/gif,image/webp,video/mp4,application/pdf"
            onChange={handleFileChange}
            className="hidden"
          />
          <button
            onClick={() => fileInputRef.current?.click()}
            disabled={uploading}
            className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {uploading ? '上傳中...' : '上傳檔案'}
          </button>
        </div>
      </div>

      {/* Filter */}
      <div className="mb-4 flex gap-2">
        <button
          onClick={() => { setFilter(''); setPage(0); }}
          className={`px-3 py-1 rounded ${!filter ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}
        >
          全部 ({totalCount})
        </button>
        <button
          onClick={() => { setFilter('IMAGE'); setPage(0); }}
          className={`px-3 py-1 rounded ${filter === 'IMAGE' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}
        >
          圖片
        </button>
        <button
          onClick={() => { setFilter('VIDEO'); setPage(0); }}
          className={`px-3 py-1 rounded ${filter === 'VIDEO' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}
        >
          影片
        </button>
        <button
          onClick={() => { setFilter('DOCUMENT'); setPage(0); }}
          className={`px-3 py-1 rounded ${filter === 'DOCUMENT' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}
        >
          文件
        </button>
      </div>

      {/* Media Grid */}
      {loading ? (
        <div className="text-center py-8">載入中...</div>
      ) : mediaList.length === 0 ? (
        <div className="text-center py-8 text-gray-500">尚無媒體檔案</div>
      ) : (
        <>
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
            {mediaList.map((media) => (
              <div
                key={media.id}
                className="border rounded-lg overflow-hidden bg-white hover:shadow-md transition-shadow"
              >
                <div className="aspect-square flex items-center justify-center bg-gray-100 relative group">
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
                  <button
                    onClick={() => handleDelete(media.id)}
                    className="absolute top-2 right-2 w-6 h-6 bg-red-500 text-white rounded-full opacity-0 group-hover:opacity-100 transition-opacity text-sm"
                  >
                    ×
                  </button>
                </div>
                <div className="p-2">
                  <div className="text-sm truncate" title={media.originalName}>
                    {media.originalName}
                  </div>
                  <div className="text-xs text-gray-500">
                    {formatFileSize(media.fileSize)}
                  </div>
                </div>
              </div>
            ))}
          </div>

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="mt-6 flex justify-center gap-2">
              <button
                onClick={() => setPage(Math.max(0, page - 1))}
                disabled={page === 0}
                className="px-3 py-1 rounded bg-gray-100 disabled:opacity-50"
              >
                上一頁
              </button>
              <span className="px-3 py-1">
                第 {page + 1} 頁，共 {totalPages} 頁
              </span>
              <button
                onClick={() => setPage(Math.min(totalPages - 1, page + 1))}
                disabled={page >= totalPages - 1}
                className="px-3 py-1 rounded bg-gray-100 disabled:opacity-50"
              >
                下一頁
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}