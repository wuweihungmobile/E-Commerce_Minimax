'use client';

import Link from 'next/link';
import { getPublishedPostBySlug, getListingCard } from '@/services/cms';
import { useAsyncData } from '@/hooks/useAsyncData';
import { ErrorState } from '@/components/ui/states';
import { Alert } from '@/components/ui/alert';
import { Card } from '@/components/ui/card';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { storeBlogPath } from '@/lib/article-path';

// PRD §6.6.3：只有「listing_id 不存在（已刪除或格式有誤）」才是「此商品已下架」。後端對這兩種情形分別回
// 404（E-3000 找不到刊登項目）與 400（E-9000 路徑參數格式錯誤）；其餘失敗（網路中斷、5xx、429…）商品可能好好的，
// 說成「已下架」會讓使用者以為店家把商品拿掉了（DEF-473）。
function isListingMissing(status: number | undefined): boolean {
  return status === 404 || status === 400;
}

// 文章內嵌的商品／房源卡片：每張卡自己載入自己的資料（DEF-321(d)，PRD §6.6.3）
function EmbeddedListingCard({ listingId }: { listingId: string }) {
  const { data: card, error, errorInfo, loading, reload } = useAsyncData(
    (signal) => getListingCard(listingId, { signal }),
    [listingId],
    {
      errorMessage: '載入商品卡片失敗',
      onError: (_message, err) => console.error('Failed to load listing card:', listingId, err),
    }
  );

  if (loading) {
    return (
      <div className="my-4 p-4 border rounded-lg bg-rs-bg-base animate-pulse">
        <div className="flex gap-4">
          <div className="w-24 h-24 bg-rs-hairline rounded" />
          <div className="flex-1">
            <div className="h-4 bg-rs-hairline rounded w-3/4 mb-2" />
            <div className="h-3 bg-rs-hairline rounded w-1/2 mb-2" />
            <div className="h-5 bg-rs-hairline rounded w-1/4" />
          </div>
        </div>
      </div>
    );
  }

  if (error !== null || !card) {
    // DEF-321(d)：PRD §6.6.3——listing_id 不存在（已刪除或格式有誤）時顯示「此商品已下架」，
    // 用語與「下架但列尚存在」（statusReason=listing_unavailable）一致
    if (isListingMissing(errorInfo?.status)) {
      return (
        <Alert variant="destructive" className="my-4" data-testid="embedded-card-gone">
          此商品已下架
        </Alert>
      );
    }
    // 其餘失敗（含「沒有失敗卻沒有資料」這種不該發生的情形）一律是載入失敗：可以只重試這一張
    return (
      <ErrorState
        data-testid="embedded-card-error"
        className="my-4 rounded-lg border bg-rs-bg-base py-4"
        title="載入商品卡片失敗，請稍後再試"
        onRetry={reload}
      />
    );
  }

  // DEF-321(d)：依 statusReason 分流文案與 CTA（PRD §6.6.3）
  const isUnavailable = card.statusReason === 'listing_unavailable';
  const isUnderMaintenance = card.statusReason === 'under_maintenance';
  const statusLabel = isUnavailable
    ? '此商品已下架'
    : isUnderMaintenance
      ? '房源維護中'
      : null;
  const ctaLabel = isUnavailable ? '前往店鋪' : isUnderMaintenance ? '聯絡客服' : '查看詳情';

  return (
    <Card className="my-4 p-4">
      <div className="flex gap-4">
        {card.coverImageUrl && (
          // eslint-disable-next-line @next/next/no-img-element -- 封面／精選圖是店家輸入的任意網址；next/image 需要 width／height，且網域須事先在 next.config 設定 remotePatterns
          <img
            src={card.coverImageUrl}
            alt={card.title}
            className="w-24 h-24 object-cover rounded"
          />
        )}
        <div className="flex-1">
          <div className="text-xs text-rs-ink-muted mb-1">{card.listingType}</div>
          <h3 className="font-semibold text-rs-ink">{card.title}</h3>
          <p className="text-sm text-rs-ink-muted">{card.tenantName}</p>
          <div className="mt-2 flex items-center justify-between">
            {card.currentPrice !== null ? (
              <span className="text-lg font-bold text-rs-primary">
                {card.currency} {card.currentPrice.toLocaleString()}
              </span>
            ) : (
              <span className="text-sm text-rs-disabled-text">無定價</span>
            )}
            {statusLabel ? (
              <span className="text-xs text-rs-error">{statusLabel}</span>
            ) : card.availability.available ? (
              <span className="text-xs text-rs-success">可預訂</span>
            ) : (
              <span className="text-xs text-rs-error">已額滿</span>
            )}
          </div>
          <a
            href={card.ctaUrl}
            className={cn(buttonVariants({ variant: "default", size: "sm" }), "mt-2 inline-block")}
          >
            {ctaLabel}
          </a>
        </div>
      </div>
    </Card>
  );
}

function renderContentWithEmbeds(content: string): React.ReactNode[] {
  const embedPattern = /{{embed:listing:([a-f0-9-]+)}}/gi;
  const parts: React.ReactNode[] = [];
  let lastIndex = 0;
  let match: RegExpExecArray | null;

  while ((match = embedPattern.exec(content)) !== null) {
    // Add text before the embed
    if (match.index > lastIndex) {
      parts.push(content.slice(lastIndex, match.index));
    }

    parts.push(<EmbeddedListingCard key={`embed-${match[1]}`} listingId={match[1]} />);

    lastIndex = match.index + match[0].length;
  }

  // Add remaining text
  if (lastIndex < content.length) {
    parts.push(content.slice(lastIndex));
  }

  return parts;
}

export interface StoreArticleProps {
  /** 店鋪的網址代稱（網址 /stores/{storeSlug}/blog/{postSlug} 的第一段） */
  storeSlug: string;
  /** 店鋪的 tenantId：由 StoreLayout 載入的公開店鋪資料提供（不再讀瀏覽器 localStorage，匿名訪客也能讀） */
  tenantId: string;
  postSlug: string;
}

// 店鋪前台的單篇文章（Sprint 336，DEF-483）：從原本的 /blog/[slug] 搬來。
// 原本的頁面靠 localStorage 的 tenantId 取文章，而它只有登入才會寫入，所以匿名訪客（包含從公開的店鋪貼文列表點進來的人、
// 搜尋引擎與連結預覽）只看得到「請先登入並設定店鋪以瀏覽部落格」。現在店鋪由網址決定、tenantId 由公開的店鋪資料提供。
export function StoreArticle({ storeSlug, tenantId, postSlug }: StoreArticleProps) {
  const blogHref = `/stores/${encodeURIComponent(storeSlug)}/blog`;
  const { data: post, error, loading } = useAsyncData(
    (signal) => getPublishedPostBySlug(postSlug, tenantId, { signal }),
    [postSlug, tenantId],
    {
      // 文章不存在或未發布（後端 404）對訪客是正常情形，用白話說明；其餘維持原本的優先順序：
      // Error 實例（含 axios 錯誤）優先用它的 message，其餘才用伺服器訊息或後備文案
      errorMessage: (info, err) =>
        info.status === 404 ? '此文章不存在或已下架' : err instanceof Error ? err.message : info.message || '載入失敗',
      onError: (_message, err) => console.error('Failed to load post:', err),
    }
  );

  if (loading) {
    return (
      <div className="py-12 text-center" data-testid="store-article-loading">
        載入中...
      </div>
    );
  }

  if (error || !post) {
    return (
      <div className="py-12 text-center" data-testid="store-article-notfound">
        {/* 店鋪外殼已經有一個 h1（店名），文章區的標題用 h2 */}
        <h2 className="text-xl font-semibold text-rs-ink mb-4">找不到文章</h2>
        <p className="text-rs-ink-muted mb-6">{error || '此文章不存在或已下架'}</p>
        <Link href={blogHref} prefetch={false} className="text-rs-primary hover:underline">
          返回部落格
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-3xl mx-auto">
      <Link href={blogHref} prefetch={false} className="text-rs-primary hover:underline text-sm" data-testid="store-article-back">
        ← 返回部落格
      </Link>

      <Card asChild className="mt-4">
        <article>
          {/* Featured Image */}
          {post.featuredImageUrl && (
            <div className="aspect-video bg-rs-disabled-bg rounded-t-lg overflow-hidden">
              {/* eslint-disable-next-line @next/next/no-img-element -- 封面／精選圖是店家輸入的任意網址；next/image 需要 width／height，且網域須事先在 next.config 設定 remotePatterns */}
              <img
                src={post.featuredImageUrl}
                alt={post.title}
                className="w-full h-full object-cover"
                onError={(e) => {
                  e.currentTarget.style.display = 'none';
                }}
              />
            </div>
          )}

          <div className="p-8">
            {/* Meta */}
            <div className="mb-6 text-sm text-rs-ink-muted">
              <span>{post.authorName}</span>
              <span className="mx-2">•</span>
              <span>
                {post.publishedAt
                  ? new Date(post.publishedAt).toLocaleDateString('zh-TW')
                  : new Date(post.createdAt).toLocaleDateString('zh-TW')}
              </span>
              <span className="mx-2">•</span>
              <span>{post.viewCount} 次瀏覽</span>
            </div>

            {/* Title（店鋪外殼已經有一個 h1，文章標題用 h2） */}
            <h2 className="text-3xl font-semibold text-rs-ink mb-6" data-testid="store-article-title">
              {post.title}
            </h2>

            {/* Category & Tags：徽章連到這間店鋪貼文列表的分類篩選（Sprint 337，DEF-486）——原本連到 /blog/category/{id}，匿名訪客點了只看到登入提示 */}
            {post.categoryName && post.categoryId && (
              <div className="mb-4">
                <Link
                  href={storeBlogPath(storeSlug, { categoryId: post.categoryId })}
                  prefetch={false}
                  className={buttonVariants({ variant: "secondary", size: "sm" })}
                  data-testid="blog-post-category-link"
                >
                  {post.categoryName}
                </Link>
              </div>
            )}

            {/* Content with Embeds */}
            <div className="prose max-w-none">
              {renderContentWithEmbeds(post.content)}
            </div>

            {/* Tags */}
            {post.tags && post.tags.length > 0 && (
              <div className="mt-8 pt-6 border-t">
                <div className="flex flex-wrap gap-2">
                  {post.tags.map((tag) => (
                    <span
                      key={tag}
                      className="px-2 py-1 bg-rs-disabled-bg text-rs-ink-muted text-sm rounded"
                    >
                      #{tag}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        </article>
      </Card>
    </div>
  );
}
