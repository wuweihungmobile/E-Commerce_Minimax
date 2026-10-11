"use client"

import { useEffect, useState } from "react"
import Link from "next/link"
import { StorefrontShell } from "@/components/layout/StorefrontShell"
import { Skeleton } from "@/components/ui/skeleton"
import storefrontService, { type StoreProfile } from "@/services/storefront"
import { StoreNav, type StoreNavTab } from "@/components/storefront/StoreNav"

type LoadState = "loading" | "ok" | "notfound" | "error"

function statusOf(err: unknown): number | undefined {
  if (err && typeof err === "object" && "response" in err) {
    return (err as { response?: { status?: number } }).response?.status
  }
  return undefined
}

export interface StoreLayoutProps {
  slug: string
  active: StoreNavTab
  children: (profile: StoreProfile) => React.ReactNode
}

// 店鋪前台共用外殼（PRD §11.3 <StoreLayout/>；DEF-321(b)，Sprint 252）：
// 載入店鋪公開資料（GET /v2/public/stores/{slug}，匿名可呼叫）一次，統一處理
// 「店鋪不存在／非 ACTIVE」（皆回 404，不區分，避免列舉店鋪狀態）與錯誤重試，
// 其餘 4 條路線（home/products/rooms/blog）共用同一份已解析的 StoreProfile。
export function StoreLayout({ slug, active, children }: StoreLayoutProps) {
  const [state, setState] = useState<LoadState>("loading")
  const [profile, setProfile] = useState<StoreProfile | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  useEffect(() => {
    let cancelled = false
    storefrontService
      .getStoreProfile(slug)
      .then((p) => {
        if (cancelled) return
        setProfile(p)
        setState("ok")
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setState(statusOf(err) === 404 ? "notfound" : "error")
      })
    return () => {
      cancelled = true
    }
  }, [slug, reloadKey])

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
    return (
      <StorefrontShell>
        <div
          data-testid="store-notfound"
          className="flex flex-col items-center justify-center py-24 text-center gap-3"
        >
          <p className="text-lg text-rs-ink">找不到這間店鋪</p>
          <p className="text-sm text-rs-ink-muted">店鋪可能已下架或不存在</p>
          <Link
            href="/"
            className="inline-flex items-center justify-center border border-rs-hairline rounded-md px-6 py-2.5 text-sm font-medium text-rs-ink transition-colors hover:border-rs-primary"
          >
            回首頁逛逛
          </Link>
        </div>
      </StorefrontShell>
    )
  }

  if (state === "error" || !profile) {
    return (
      <StorefrontShell>
        <div
          data-testid="store-error"
          className="flex flex-col items-center justify-center py-24 text-center gap-3"
        >
          <p className="text-lg text-rs-ink">載入失敗，請稍後再試</p>
          <button
            type="button"
            onClick={() => {
              setState("loading")
              setReloadKey((k) => k + 1)
            }}
            className="inline-flex items-center justify-center bg-rs-primary text-white rounded-md px-6 py-2.5 text-sm font-medium transition-colors hover:bg-rs-primary-hover"
          >
            重新載入
          </button>
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
