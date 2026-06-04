'use client';

import { useEffect, useState } from 'react';
import {
  getMediaAssets,
  deleteMediaAsset,
  getMediaCategories,
  MediaAssetDto,
  MediaCategoryDto,
  PageResponse,
} from '@/services/media';

export default function MediaPage() {
  const [media, setMedia] = useState<MediaAssetDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [categoryFilter, setCategoryFilter] = useState<string>('');
  const [mimeTypeFilter, setMimeTypeFilter] = useState<string>('');
  const [keyword, setKeyword] = useState<string>('');
  const [categories, setCategories] = useState<MediaCategoryDto[]>([]);
  const [deleting, setDeleting] = useState<string | null>(null);

  useEffect(() => {
    loadCategories();
  }, []);

  useEffect(() => {
    loadMedia();
  }, [page, categoryFilter, mimeTypeFilter, keyword]);

  async function loadCategories() {
    try {
      const data = await getMediaCategories();
      setCategories(data);
    } catch (error) {
      console.error('Failed to load categories:', error);
    }
  }

  async function loadMedia() {
    setLoading(true);
    try {
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

      const data: PageResponse<MediaAssetDto> = await getMediaAssets(params);
      setMedia(data.content);
      setTotalPages(data.totalPages);
      setTotalElements(data.totalElements);
    } catch (error) {
      console.error('Failed to load media:', error);
      alert('載入媒體失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }

  async function handleDelete(mediaId: string) {
    if (!confirm('確定要刪除這個媒體嗎？')) return;
    setDeleting(mediaId);
    try {
      await deleteMediaAsset(mediaId);
      loadMedia();
      alert('刪除成功');
    } catch (error) {
      console.error('Failed to delete media:', error);
      alert('刪除失敗，請稍後再試');
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
        <h1 className="text-2xl font-bold">媒體中心</h1>
        <div className="text-sm text-gray-500">
          總計 {totalElements} 個媒體
        </div>
      </div>

      {/* Filters */}
      <div className="mb-4 flex flex-wrap gap-4">
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">分類</label>
          <select
            value={categoryFilter}
            onChange={(e) => {
              setCategoryFilter(e.target.value);
              setPage(0);
            }}
            className="border rounded px-3 py-1.5 text-sm"
          >
            <option value="">全部分類</option>
            {categories.map((cat) => (
              <option key={cat.id} value={cat.id}>{cat.name}</option>
            ))}
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">檔案類型</label>
          <select
            value={mimeTypeFilter}
            onChange={(e) => {
              setMimeTypeFilter(e.target.value);
              setPage(0);
            }}
            className="border rounded px-3 py-1.5 text-sm"
          >
            <option value="">全部類型</option>
            <option value="image/">圖片</option>
            <option value="video/">影片</option>
            <option value="application/pdf">PDF</option>
          </select>
        </div>
        <div>
          <label className="block text-sm font-medium text-gray-700 mb-1">關鍵字搜尋</label>
          <input
            type="text"
            value={keyword}
            onChange={(e) => {
              setKeyword(e.target.value);
              setPage(0);
            }}
            placeholder="搜尋檔案名稱..."
            className="border rounded px-3 py-1.5 text-sm w-48"
          />
        </div>
        <div className="flex items-end">
          <button
            onClick={() => loadMedia()}
            className="px-4 py-1.5 bg-blue-600 text-white rounded text-sm hover:bg-blue-700"
          >
            搜尋
          </button>
        </div>
      </div>

      {/* Media Grid */}
      {loading ? (
        <div className="text-center py-12 text-gray-500">載入中...</div>
      ) : media.length === 0 ? (
        <div className="text-center py-12 text-gray-500">尚無媒體</div>
      ) : (
        <>
          <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
            {media.map((item) => (
              <div key={item.id} className="bg-white rounded-lg shadow overflow-hidden border">
                <div className="aspect-square bg-gray-100 flex items-center justify-center relative">
                  {isImage(item.mimeType) ? (
                    <img
                      src={item.filePath}
                      alt={item.fileName}
                      className="object-cover w-full h-full"
                      onError={(e) => {
                        (e.target as HTMLImageElement).style.display = 'none';
                        (e.target as HTMLImageElement).nextElementSibling?.classList.remove('hidden');
                      }}
                    />
                  ) : null}
                  <span className={`text-4xl ${isImage(item.mimeType) ? 'hidden' : ''}`}>
                    {getFileTypeIcon(item.mimeType)}
                  </span>
                </div>
                <div className="p-3">
                  <p className="text-sm font-medium truncate" title={item.fileName}>
                    {item.fileName}
                  </p>
                  <p className="text-xs text-gray-500">{formatFileSize(item.fileSize)}</p>
                  {item.categoryName && (
                    <p className="text-xs text-blue-600">{item.categoryName}</p>
                  )}
                  {item.tags && item.tags.length > 0 && (
                    <div className="flex flex-wrap gap-1 mt-1">
                      {item.tags.slice(0, 2).map((tag, i) => (
                        <span key={i} className="text-xs bg-gray-100 px-1 rounded">{tag}</span>
                      ))}
                    </div>
                  )}
                  <button
                    onClick={() => handleDelete(item.id)}
                    disabled={deleting === item.id}
                    className="mt-2 text-xs text-red-600 hover:text-red-800 disabled:opacity-50"
                  >
                    {deleting === item.id ? '刪除中...' : '刪除'}
                  </button>
                </div>
              </div>
            ))}
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
    </div>
  );
}