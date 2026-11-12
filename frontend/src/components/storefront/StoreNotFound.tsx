import Link from "next/link"
import { StorefrontShell } from "@/components/layout/StorefrontShell"
import { buttonVariants } from "@/components/ui/button"

// 「找不到這間店鋪」畫面（店鋪不存在或非 ACTIVE；後端皆回 404、不區分，避免列舉店鋪狀態）。
// 兩個地方用同一份：① 伺服器端——app/(storefront)/stores/not-found.tsx，店鋪不存在時回真正的 HTTP 404（DEF-500，Sprint 341，
// 否則搜尋引擎把這個畫面當成一般頁面收錄，即 soft 404）；② 瀏覽器端——StoreLayout 在伺服器端沒取到資料、改由瀏覽器載入時
// 收到 404 的備援。兩邊文字與 data-testid 必須一致（E2E 依賴）。
export function StoreNotFound() {
  return (
    <StorefrontShell>
      <div data-testid="store-notfound" className="flex flex-col items-center justify-center py-24 text-center gap-3">
        <p className="text-lg text-rs-ink">找不到這間店鋪</p>
        <p className="text-sm text-rs-ink-muted">店鋪可能已下架或不存在</p>
        <Link href="/" className={buttonVariants({ variant: "outline" })}>
          回首頁逛逛
        </Link>
      </div>
    </StorefrontShell>
  )
}
