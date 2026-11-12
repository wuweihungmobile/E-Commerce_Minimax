'use client';

import Link from 'next/link';
import { useParams } from 'next/navigation';
import { StorefrontShell } from '@/components/layout/StorefrontShell';

// 「找不到文章」畫面（文章不存在、未發布或已下架；店鋪是好的）。兩個地方用同一份：
//   ① 伺服器端——stores/[slug]/blog/[postSlug]/not-found.tsx，文章不存在時回真正的 HTTP 404（DEF-500，Sprint 341，
//      否則搜尋引擎把這個畫面當成一般頁面收錄，即 soft 404）；
//   ② 瀏覽器端——StoreArticle 在伺服器端沒取到文章、改由瀏覽器載入才收到 404 的備援。
// 兩邊文字與 data-testid 必須一致（E2E 依賴）。

export interface ArticleNotFoundProps {
  /** 這間店鋪貼文列表的網址（返回連結） */
  blogHref: string;
  /** 要說明的文字；沒有就用白話的「此文章不存在或已下架」 */
  message?: string | null;
}

export function ArticleNotFound({ blogHref, message }: ArticleNotFoundProps) {
  return (
    <div className="py-12 text-center" data-testid="store-article-notfound">
      {/* 店鋪外殼已經有一個 h1（店名），文章區的標題用 h2 */}
      <h2 className="text-xl font-semibold text-rs-ink mb-4">找不到文章</h2>
      <p className="text-rs-ink-muted mb-6">{message || '此文章不存在或已下架'}</p>
      <Link href={blogHref} prefetch={false} className="text-rs-primary hover:underline">
        返回部落格
      </Link>
    </div>
  );
}

/** not-found.tsx 用：從目前的路由取店鋪代稱組出返回連結（not-found 元件拿不到 params，用 useParams）。 */
export function ArticleNotFoundPage() {
  const params = useParams<{ slug?: string }>();
  const slug = typeof params.slug === 'string' ? params.slug : '';
  return (
    <StorefrontShell>
      <ArticleNotFound blogHref={slug ? `/stores/${encodeURIComponent(slug)}/blog` : '/blog'} />
    </StorefrontShell>
  );
}
