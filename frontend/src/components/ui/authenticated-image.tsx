'use client';

import { useEffect, useState } from 'react';

interface AuthenticatedImageProps {
  id: string;
  fetchBlob: (id: string) => Promise<Blob>;
  alt: string;
  className?: string;
  fallback?: React.ReactNode;
}

/**
 * 顯示需要 Authorization header 才能存取的圖片。
 * <img src> 無法附加自訂 header，故改用 fetchBlob 帶 token 抓取內容後建立 object URL。
 * fetchBlob 須為穩定的模組層級函式（例如直接傳入 service 匯出的函式），
 * 若每次 render 都傳入新的行內函式會導致無限重新抓取。
 */
export function AuthenticatedImage({ id, fetchBlob, alt, className, fallback }: AuthenticatedImageProps) {
  const [src, setSrc] = useState<string | null>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    let objectUrl: string | null = null;
    let cancelled = false;

    fetchBlob(id)
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setSrc(objectUrl);
      })
      .catch(() => {
        if (!cancelled) setFailed(true);
      });

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [id, fetchBlob]);

  if (failed) return <>{fallback ?? null}</>;
  if (!src) return null;
  return <img src={src} alt={alt} className={className} />;
}
