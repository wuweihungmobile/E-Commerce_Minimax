"use client"

import { StorefrontShell } from "@/components/layout/StorefrontShell"
import { Skeleton } from "@/components/ui/skeleton"
import storefrontService, { type StoreProfile } from "@/services/storefront"
import { useAsyncData } from "@/hooks/useAsyncData"
import { StoreNav, type StoreNavTab } from "@/components/storefront/StoreNav"
import { StoreNotFound } from "@/components/storefront/StoreNotFound"
import { Button } from "@/components/ui/button"

type LoadState = "loading" | "ok" | "notfound" | "error"

export interface StoreLayoutProps {
  slug: string
  active: StoreNavTab
  /** 伺服器端已取好的店鋪公開資料（SEO 的伺服器端渲染，DEF-500）：有就直接用、不再請求；沒有（沒取到）就由瀏覽器自己載入 */
  initialProfile?: StoreProfile
  children: (profile: StoreProfile) => React.ReactNode
}

// 店鋪前台共用外殼（PRD §11.3 <StoreLayout/>；DEF-321(b)，Sprint 252）：
// 載入店鋪公開資料（GET /v2/public/stores/{slug}，匿名可呼叫）一次，統一處理
// 「店鋪不存在／非 ACTIVE」（皆回 404，不區分，避免列舉店鋪狀態）與錯誤重試，
// 其餘 4 條路線（home/products/rooms/blog）共用同一份已解析的 StoreProfile。
//
// SEO（DEF-500，Sprint 341）：店鋪頁的 server page 先取好店鋪資料以 initialProfile 傳進來，第一份 HTML 就有店名與導覽
// （以前是載入中骨架）。店鋪不存在由伺服器端直接回真正的 404（stores/not-found.tsx，與下面的備援畫面共用 StoreNotFound）；
// 這裡的 notfound 分支只在「伺服器端沒取到、由瀏覽器自己載入才收到 404」時才會用到。
export function StoreLayout({ slug, active, initialProfile, children }: StoreLayoutProps) {
  const { data: profile, error, errorInfo, loading, reload } = useAsyncData(
    () => storefrontService.getStoreProfile(slug),
    [slug],
    { errorMessage: "載入店鋪失敗", initialData: initialProfile }
  )
  const state: LoadState = loading ? "loading" : error === null ? "ok" : errorInfo?.status === 404 ? "notfound" : "error"

  if (state === "loading") {
    return (
      <StorefrontShell>
        <div data-testid="store-loading" className="flex flex-col gap-4">
          <Skeleton className="h-10 w-1/3" />
          <Skeleton className="h-6 w-1/2" />
        </div>
      </StorefrontShell>
    )
  }

  if (state === "notfound") {
    return <StoreNotFound />
  }

  if (state === "error" || !profile) {
    return (
      <StorefrontShell>
        <div
          data-testid="store-error"
          className="flex flex-col items-center justify-center py-24 text-center gap-3"
        >
          <p className="text-lg text-rs-ink">載入失敗，請稍後再試</p>
          <Button
            variant="default"
            type="button"
            onClick={() => reload()}
          >
            重新載入
          </Button>
        </div>
      </StorefrontShell>
    )
  }

  return (
    <StorefrontShell>
      <div data-testid="store-header" className="flex items-center gap-3 pb-4">
        {profile.logoUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- 店鋪 Logo 為後端動態 URL
          <img
            src={profile.logoUrl}
            alt={profile.name}
            className="w-10 h-10 rounded-full object-cover border border-rs-hairline"
          />
        ) : null}
        <h1 className="text-xl font-semibold text-rs-ink">{profile.name}</h1>
      </div>
      <StoreNav slug={slug} active={active} />
      <div className="pt-6">{children(profile)}</div>
    </StorefrontShell>
  )
}
