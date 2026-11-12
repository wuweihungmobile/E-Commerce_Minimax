import Link from "next/link"
import { buttonVariants } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/states"

// 「找不到這個商品」畫面（商品／房源不存在、已下架，或網址不是合法的編號）。兩個地方用同一份：
//   ① 伺服器端——app/(storefront)/listings/[id]/not-found.tsx，商品不存在時回真正的 HTTP 404（DEF-500，Sprint 341，
//      否則搜尋引擎把這個畫面當成一般頁面收錄，即 soft 404）；
//   ② 瀏覽器端——ListingDetail 在伺服器端沒取到資料、改由瀏覽器載入才收到 404 的備援。
// 兩邊文字與 data-testid 必須一致（E2E 依賴）。
export function ListingNotFound() {
  return (
    <EmptyState
      data-testid="listing-notfound"
      className="py-24"
      title="找不到這個商品"
      description="商品可能已下架或不存在"
      action={
        <Link href="/" className={buttonVariants({ variant: "outline" })}>
          回首頁逛逛
        </Link>
      }
    />
  )
}
