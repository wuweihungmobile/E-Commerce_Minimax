'use client';

import { useEffect, useState } from 'react';
import { getMediaList, deleteMedia, uploadMedia, MediaResponse } from '@/services/cms';

export default function MediaPage() {
  const [media, setMedia] = useState<MediaResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [filter, setFilter] = useState<string>('');
  const [uploading, setUploading] = useState(false);

  useEffect(() => {
    loadMedia();
  }, [filter]);

  async function loadMedia() {
    setLoading(true);
    try {
      const params = filter ? { fileType: filter } : {};
      const data = await getMediaList(params);
      setMedia(data.media);
    } catch (error) {
      console.error('Failed to load media:', error);
      alert('載入媒體失敗，請稍後再試');
    } finally {
      setLoading(false);
    }
  }

  async function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    if (!file) return;

    setUploading(true);
    try {
      const formData = new FormData();
      formData.append('file', file);
      formData.append('fileName', file.name);
      formData.append('originalName', file.name);
      formData.append('fileSize', file.size.toString());
      formData.append('mimeType', file.type);
      formData.append('filePath', `/media/${file.name}`);
      await uploadMedia(formData);
      loadMedia();
      alert('上傳成功');
    } catch (error) {
      console.error('Failed to upload media:', error);
      alert('上傳失敗，請稍後再試');
    } finally {
      setUploading(false);
      e.target.value = '';
    }
  }

  async function handleDelete(mediaId: string) {
    if (!confirm('確定要刪除這個媒體嗎？')) return;
    try {
      await deleteMedia(mediaId);
      loadMedia();
    } catch (error) {
      console.error('Failed to delete media:', error);
      alert('刪除失敗，請稍後再試');
    }
  }

  const getFileTypeIcon = (fileType: string) => {
    switch (fileType) {
      case 'IMAGE':
        return '🖼️';
      case 'VIDEO':
        return '🎬';
      case 'DOCUMENT':
        return '📄';
      default:
        return '📁';
    }
  };

  return (
    <div className="p-6">
      <div className="flex justify-between items-center mb-6">
        <h1 className="text-2xl font-bold">媒體庫</h1>
        <label className={`bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700 cursor-pointer ${uploading ? 'opacity-50' : ''}`}>
          {uploading ? '上傳中...' : '上傳媒體'}
          <input
            type="file"
            className="hidden"
            accept="image/*,video/*,.pdf"
            onChange={handleFileChange}
            disabled={uploading}
          />
        </label>
      </div>

      {/* Filter */}
      <div className="mb-4 flex gap-2">
        <button
          onClick={() => setFilter('')}
          className={`px-3 py-1 rounded ${!filter ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}
        >
          全部
        </button>
        <button
          onClick={() => setFilter('IMAGE')}
          className={`px-3 py-1 rounded ${filter === 'IMAGE' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}
        >
          🖼️ 圖片
        </button>
        <button
          onClick={() => setFilter('VIDEO')}
          className={`px-3 py-1 rounded ${filter === 'VIDEO' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}
        >
          🎬 影片
        </button>
        <button
          onClick={() => setFilter('DOCUMENT')}
          className={`px-3 py-1 rounded ${filter === 'DOCUMENT' ? 'bg-blue-100 text-blue-800' : 'bg-gray-100'}`}
        >
          📄 文檔
        </button>
      </div>

      {/* Media Grid */}
      {loading ? (
        <div className="text-center py-8">載入中...</div>
      ) : media.length === 0 ? (
        <div className="text-center py-8 text-gray-500">尚無媒體</div>
      ) : (
        <div className="grid grid-cols-4 gap-4">
          {media.map((item) => (
            <div key={item.id} className="bg-white rounded-lg shadow overflow-hidden">
              <div className="aspect-square bg-gray-100 flex items-center justify-center">
                {item.fileType === 'IMAGE' ? (
                  <img
                    src={item.filePath}
                    alt={item.originalName}
                    className="object-cover w-full h-full"
                    onError={(e) => {
                      (e.target as HTMLImageElement).style.display = 'none';
                    }}
                  />
                ) : (
                  <span className="text-4xl">{getFileTypeIcon(item.fileType)}</span>
                )}
              </div>
              <div className="p-3">
                <p className="text-sm font-medium truncate" title={item.originalName}>
                  {item.originalName}
                </p>
                <p className="text-xs text-gray-500">{item.formattedFileSize}</p>
                <button
                  onClick={() => handleDelete(item.id)}
                  className="mt-2 text-xs text-red-600 hover:text-red-800"
                >
                  刪除
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}