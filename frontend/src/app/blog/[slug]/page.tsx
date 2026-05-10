'use client';

import { useState, useEffect } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { getPublishedPostBySlug, PostResponse, getListingCard, ListingCardResponse } from '@/services/cms';

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

  useEffect(() => {
    if (slug && tenantId) {
      loadPost();
    }
  }, [slug, tenantId]);

  async function loadPost() {
    if (!tenantId) return;

    setLoading(true);
    setError(null);
    try {
      const data = await getPublishedPostBySlug(slug, tenantId);
      setPost(data);
      parseEmbeds(data.content);
    } catch (err) {
      console.error('Failed to load post:', err);
      setError(err instanceof Error ? err.message : (err as { response?: { data?: { message?: string } } })?.response?.data?.message || '載入失敗');
    } finally {
      setLoading(false);
    }
  }

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
        parts.push(
          <div
            key={`embed-${listingId}`}
            className="my-4 p-4 border rounded-lg bg-white shadow-sm"
          >
            <div className="flex gap-4">
              {card.coverImageUrl && (
                <img
                  src={card.coverImageUrl}
                  alt={card.title}
                  className="w-24 h-24 object-cover rounded"
                />
              )}
              <div className="flex-1">
                <div className="text-xs text-gray-500 mb-1">{card.listingType}</div>
                <h3 className="font-semibold text-gray-900">{card.title}</h3>
                <p className="text-sm text-gray-600">{card.tenantName}</p>
                <div className="mt-2 flex items-center justify-between">
                  <span className="text-lg font-bold text-blue-600">
                    {card.currency} {card.currentPrice.toLocaleString()}
                  </span>
                  {card.availability.available ? (
                    <span className="text-xs text-green-600">可預訂</span>
                  ) : (
                    <span className="text-xs text-red-600">已額滿</span>
                  )}
                </div>
                <a
                  href={card.ctaUrl}
                  className="mt-2 inline-block px-3 py-1 bg-blue-600 text-white text-sm rounded hover:bg-blue-700"
                >
                  查看詳情
                </a>
              </div>
            </div>
          </div>
        );
      } else if (cardData?.loading) {
        parts.push(
          <div
            key={`embed-${match[1]}`}
            className="my-4 p-4 border rounded-lg bg-gray-50 animate-pulse"
          >
            <div className="flex gap-4">
              <div className="w-24 h-24 bg-gray-200 rounded" />
              <div className="flex-1">
                <div className="h-4 bg-gray-200 rounded w-3/4 mb-2" />
                <div className="h-3 bg-gray-200 rounded w-1/2 mb-2" />
                <div className="h-5 bg-gray-200 rounded w-1/4" />
              </div>
            </div>
          </div>
        );
      } else {
        parts.push(
          <div
            key={`embed-${match[1]}`}
            className="my-4 p-4 border border-red-200 rounded-lg bg-red-50 text-red-600 text-sm"
          >
            無法載入商品卡: {match[1]}
          </div>
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
      <div className="min-h-screen bg-gray-50">
        <div className="max-w-3xl mx-auto px-4 py-12 text-center">
          載入中...
        </div>
      </div>
    );
  }

  if (error || !post) {
    return (
      <div className="min-h-screen bg-gray-50">
        <div className="max-w-3xl mx-auto px-4 py-12 text-center">
          <h1 className="text-2xl font-bold text-gray-900 mb-4">找不到文章</h1>
          <p className="text-gray-600 mb-6">{error || '此文章不存在或已下架'}</p>
          <Link href="/blog" className="text-blue-600 hover:underline">
            返回部落格
          </Link>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-gray-50">
      {/* Header */}
      <header className="bg-white shadow-sm">
        <div className="max-w-3xl mx-auto px-4 py-6">
          <Link href="/blog" className="text-blue-600 hover:underline text-sm">
            ← 返回部落格
          </Link>
        </div>
      </header>

      {/* Article */}
      <main className="max-w-3xl mx-auto px-4 py-8">
        <article className="bg-white rounded-lg shadow-sm">
          {/* Featured Image */}
          {post.featuredImageUrl && (
            <div className="aspect-video bg-gray-100 rounded-t-lg overflow-hidden">
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
            <div className="mb-6 text-sm text-gray-500">
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
            <h1 className="text-3xl font-bold text-gray-900 mb-6">
              {post.title}
            </h1>

            {/* Category & Tags */}
            {post.categoryName && (
              <div className="mb-4">
                <span className="inline-block px-2 py-1 bg-blue-100 text-blue-800 text-sm rounded">
                  {post.categoryName}
                </span>
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
                      className="px-2 py-1 bg-gray-100 text-gray-600 text-sm rounded"
                    >
                      #{tag}
                    </span>
                  ))}
                </div>
              </div>
            )}
          </div>
        </article>
      </main>
    </div>
  );
}