'use client';

import { useEffect, useSyncExternalStore } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { getPublishedPostBySlug } from '@/services/cms';
import { useAsyncData } from '@/hooks/useAsyncData';
import { getAuthServerSnapshot, getSelectedTenantIdSnapshot, subscribeAuth } from '@/services/authStore';
import { PageTitle } from '@/components/ui/page-title';
import { EmptyState } from '@/components/ui/states';
import { articlePath } from '@/lib/article-path';

// 舊的文章網址 /blog/{slug}（Sprint 336，DEF-483）：文章的公開網址改成 /stores/{店鋪}/blog/{slug}（見 lib/article-path）。
// 這一頁只負責把舊網址導向新網址，讓已經存下來的書籤與連結不會變成死連結。
//
// 舊網址沒有店鋪，要找出文章屬於哪一間店，靠的是瀏覽器 localStorage 的 tenantId（登入時寫入）——和改版前取文章的方式相同，
// 所以改版前打得開這個網址的人（登入且選定店鋪的人），現在一樣會被導到文章；改版前就打不開的人（匿名訪客）維持原本的登入提示。
// 查詢帶 countView:false：這裡只是為了找店鋪，真正的瀏覽是導向後的新網址那一頁，不能算兩次。
export default function LegacyBlogPostRedirectPage() {
  const params = useParams();
  const slug = params.slug as string;
  const router = useRouter();

  // 目前選用的店鋪（localStorage）；SSR／hydration 前是 null
  const tenantId = useSyncExternalStore(subscribeAuth, getSelectedTenantIdSnapshot, getAuthServerSnapshot);
  const { data: post, error } = useAsyncData(
    (signal) => getPublishedPostBySlug(slug, tenantId ?? '', { signal, countView: false }),
    [slug, tenantId],
    {
      enabled: Boolean(slug && tenantId),
      errorMessage: (info, err) =>
        info.status === 404 ? '此文章不存在或已下架' : err instanceof Error ? err.message : info.message || '載入失敗',
      onError: (_message, err) => console.error('Failed to load post:', err),
    }
  );

  useEffect(() => {
    // 文章回應沒有店鋪代稱（後端比前端舊）就無從導向：articlePath 會退回 /blog/{slug}＝導向自己
    if (post?.tenantSlug) router.replace(articlePath(post.tenantSlug, post.slug));
  }, [post, router]);

  // 沒有選用店鋪的訪客：與部落格清單頁一致，提示登入
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

  // 找到文章卻沒有店鋪代稱：不能卡在「載入中」，明講
  const failure = error ?? (post && !post.tenantSlug ? '無法開啟這篇文章（缺少店鋪資訊）' : null);
  if (failure) {
    return (
      <div className="flex-1">
        <div className="max-w-3xl mx-auto px-4 py-12 text-center">
          <PageTitle className="mb-4">找不到文章</PageTitle>
          <p className="text-rs-ink-muted mb-6">{failure}</p>
          <Link href="/blog" className="text-rs-primary hover:underline">
            返回部落格
          </Link>
        </div>
      </div>
    );
  }

  // 載入中，或已找到文章、正在導向新網址
  return (
    <div className="flex-1">
      <div className="max-w-3xl mx-auto px-4 py-12 text-center">載入中...</div>
    </div>
  );
}
