'use client';

import { useState, useEffect, useCallback } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { getPublishedPostBySlug, PostResponse, getListingCard, ListingCardResponse } from '@/services/cms';
import { PageTitle } from '@/components/ui/page-title';
import { Alert } from '@/components/ui/alert';
import { Card } from '@/components/ui/card';
import { buttonVariants } from '@/components/ui/button';
import { cn } from '@/lib/utils';
import { apiErrorMessage } from '@/lib/api-error';

interface EmbedCardData {
  listingId: string;
  card?: ListingCardResponse;
  loading: boolean;
}

export default function BlogPostPage() {
  const params = useParams();
  const slug = params.slug as string;

  const [post, setPost] = useState<PostResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [embedCards, setEmbedCards] = useState<EmbedCardData[]>([]);
  const [tenantId, setTenantId] = useState<string>('');

  useEffect(() => {
    const storedTenantId = localStorage.getItem('tenantId');
    if (storedTenantId) {
      setTenantId(storedTenantId);
    }
  }, []);

  const loadPost = useCallback(async () => {
    if (!tenantId) return;

    setLoading(true);
    setError(null);
    try {
      const data = await getPublishedPostBySlug(slug, tenantId);
      setPost(data);
      parseEmbeds(data.content);
    } catch (err) {
      console.error('Failed to load post:', err);
      setError(err instanceof Error ? err.message : apiErrorMessage(err, '載入失敗'));
    } finally {
      setLoading(false);
    }
  }, [slug, tenantId]);

  useEffect(() => {
    if (slug && tenantId) {
      loadPost();
    }
  }, [slug, tenantId, loadPost]);

  async function parseEmbeds(content: string) {
    const embedPattern = /{{embed:listing:([a-f0-9-]+)}}/gi;
    const matches = [...content.matchAll(embedPattern)];
    const uniqueIds = [...new Set(matches.map(m => m[1]))];

    const cards: EmbedCardData[] = uniqueIds.map(id => ({
      listingId: id,
      loading: true,
    }));
    setEmbedCards(cards);

    for (const cardData of cards) {
      try {
        const card = await getListingCard(cardData.listingId);
        setEmbedCards(prev =>
          prev.map(c =>
            c.listingId === cardData.listingId ? { ...c, card, loading: false } : c
          )
        );
      } catch (err) {
        console.error('Failed to load listing card:', cardData.listingId, err);
        setEmbedCards(prev =>
          prev.map(c =>
            c.listingId === cardData.listingId ? { ...c, loading: false } : c
          )
        );
      }
    }
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

      // Find the card data
      const listingId = match[1];
      const cardData = embedCards.find(c => c.listingId === listingId);

      if (cardData?.card) {
        const card = cardData.card;
        // DEF-321(d)：依 statusReason 分流文案與 CTA（PRD §6.6.3）
        const isUnavailable = card.statusReason === 'listing_unavailable';
        const isUnderMaintenance = card.statusReason === 'under_maintenance';
        const statusLabel = isUnavailable
          ? '此商品已下架'
          : isUnderMaintenance
            ? '房源維護中'
            : null;
        const ctaLabel = isUnavailable ? '前往店鋪' : isUnderMaintenance ? '聯絡客服' : '查看詳情';

        parts.push(
          <Card
            key={`embed-${listingId}`}
            className="my-4 p-4"
          >
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
      } else if (cardData?.loading) {
        parts.push(
          <div
            key={`embed-${match[1]}`}
            className="my-4 p-4 border rounded-lg bg-rs-bg-base animate-pulse"
          >
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
      } else {
        // DEF-321(d)：PRD §6.6.3——listing_id 不存在（已刪除或格式有誤）時顯示「此商品已下架」，
        // 用語與「下架但列尚存在」（statusReason=listing_unavailable）一致
        parts.push(
          <Alert
            variant="destructive"
            key={`embed-${match[1]}`}
            className="my-4"
          >
            此商品已下架
          </Alert>
        );
      }

      lastIndex = match.index + match[0].length;
    }

    // Add remaining text
    if (lastIndex < content.length) {
      parts.push(content.slice(lastIndex));
    }

    return parts;
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