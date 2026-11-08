'use client';

import { useSyncExternalStore } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { getPublishedPostBySlug, getListingCard } from '@/services/cms';
import { useAsyncData } from '@/hooks/useAsyncData';
import { getAuthServerSnapshot, getSelectedTenantIdSnapshot, subscribeAuth } from '@/services/authStore';
import { PageTitle } from '@/components/ui/page-title';
import { EmptyState, ErrorState } from '@/components/ui/states';
import { Alert } from '@/components/ui/alert';
import { Card } from '@/components/ui/card';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';

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

export default function BlogPostPage() {
  const params = useParams();
  const slug = params.slug as string;

  // 目前選用的店鋪（localStorage）；SSR／hydration 前是 null
  const tenantId = useSyncExternalStore(subscribeAuth, getSelectedTenantIdSnapshot, getAuthServerSnapshot);
  const { data: post, error, loading } = useAsyncData(
    (signal) => getPublishedPostBySlug(slug, tenantId ?? '', { signal }),
    [slug, tenantId],
    {
      enabled: Boolean(slug && tenantId),
      // 與原本相同：Error 實例（含 axios 錯誤）優先用它的 message，其餘才用伺服器訊息或後備文案
      errorMessage: (info, err) => (err instanceof Error ? err.message : info.message || '載入失敗'),
      onError: (_message, err) => console.error('Failed to load post:', err),
    }
  );

  // 沒有選用店鋪的訪客：與部落格清單頁一致，提示登入（原本永遠停在「載入中...」）
  if (!tenantId) {
    return (
      <div className="flex-1">
        <div className="max-w-3xl mx-auto px-4 py-12 text-center">
          <PageTitle className="mb-4">部落格文章</PageTitle>
          <EmptyState
            title="請先登入並設定店鋪以瀏覽部落格"
            action={<Link href="/login" className="text-rs-primary hover:underline">前往登入</Link>}
          />
        </div>
      </div>
    );
  }

  if (loading) {
    return (
      <div className="flex-1">
        <div className="max-w-3xl mx-auto px-4 py-12 text-center">
          載入中...
        </div>
      </div>
    );
  }

  if (error || !post) {
    return (
      <div className="flex-1">
        <div className="max-w-3xl mx-auto px-4 py-12 text-center">
          <PageTitle className="mb-4">找不到文章</PageTitle>
          <p className="text-rs-ink-muted mb-6">{error || '此文章不存在或已下架'}</p>
          <Link href="/blog" className="text-rs-primary hover:underline">
            返回部落格
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="flex-1">
      {/* Header */}
      <header className="bg-white border border-rs-hairline">
        <div className="max-w-3xl mx-auto px-4 py-6">
          <Link href="/blog" className="text-rs-primary hover:underline text-sm">
            ← 返回部落格
          </Link>
        </div>
      </header>

      {/* Article */}
      <main className="max-w-3xl mx-auto px-4 py-8">
        <Card asChild>
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

            {/* Title */}
            <h1 className="text-3xl font-semibold text-rs-ink mb-6">
              {post.title}
            </h1>

            {/* Category & Tags */}
            {post.categoryName && post.categoryId && (
              <div className="mb-4">
                <Link
                  href={`/blog/category/${post.categoryId}`}
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
      </main>
    </div>
  );
}