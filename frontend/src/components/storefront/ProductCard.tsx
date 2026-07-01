import Link from "next/link"
import { Droplets, Star } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import { cn } from "@/lib/utils"

export interface ProductCardProps {
  image?: string
  title: string
  price: number
  was?: number
  currency?: string
  /** 促銷標籤文字（例如「-30%」「限時」） */
  promo?: string
  /** 排行序號（例如熱銷 No.1） */
  rank?: number
  /** 物流標籤（例如「免運」「快速到貨」） */
  logistics?: string[]
  /** 特色標籤 */
  features?: string[]
  rating?: number
  sold?: number
  href?: string
  className?: string
}

function formatPrice(currency: string, value: number): string {
  return `${currency}${value.toLocaleString("zh-TW")}`
}

// 賣場商品卡：方形圖 + 促銷/物流徽章 + 兩行標題 + 價格，對齊設計稿 rs-pc。
export function ProductCard({
  image,
  title,
  price,
  was,
  currency = "NT$",
  promo,
  rank,
  logistics = [],
  features = [],
  rating,
  sold,
  href = "#",
  className,
}: ProductCardProps) {
  return (
    <Link
      href={href}
      className={cn(
        "group flex flex-col bg-white border border-rs-hairline rounded-lg overflow-hidden transition-colors no-underline text-inherit hover:border-rs-primary",
        className
      )}
    >
      <div className="relative aspect-square overflow-hidden bg-rs-tertiary">
        {image ? (
          // eslint-disable-next-line @next/next/no-img-element -- 商品圖為後端動態 URL，避免 next/image remotePatterns 設定
          <img
            src={image}
            alt={title}
            className="w-full h-full object-cover transition-transform duration-300 ease-out group-hover:scale-105"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <Droplets className="w-12 h-12 text-rs-primary opacity-45" aria-hidden />
          </div>
        )}
        {promo && (
          <div className="absolute top-2 left-2">
            <Badge variant="promo">{promo}</Badge>
          </div>
        )}
        {rank !== undefined && !promo && (
          <div className="absolute top-2 left-2">
            <Badge variant="promo">No.{rank}</Badge>
          </div>
        )}
        {logistics.length > 0 && (
          <div className="absolute bottom-2 left-2 flex gap-1">
            {logistics.map((l, i) => (
              <Badge key={`${l}-${i}`} variant={i === 0 ? "logistics" : "logisticsAlt"}>
                {l}
              </Badge>
            ))}
          </div>
        )}
      </div>
      <div className="p-3 flex flex-col gap-2">
        {features.length > 0 && (
          <div className="flex gap-1 flex-wrap">
            {features.map((f) => (
              <Badge key={f} variant="feature">
                {f}
              </Badge>
            ))}
          </div>
        )}
        <h3 className="text-sm leading-normal text-rs-ink line-clamp-2 min-h-[2.4em]">
          {title}
        </h3>
        {(rating !== undefined || sold !== undefined) && (
          <div className="flex items-center gap-2 text-xs text-rs-ink-muted">
            {rating !== undefined && (
              <span className="inline-flex items-center gap-0.5">
                <Star className="w-3 h-3 fill-rs-warning text-rs-warning" aria-hidden />
                {rating.toFixed(1)}
              </span>
            )}
            {sold !== undefined && <span>已售 {sold}</span>}
          </div>
        )}
        <div className="flex items-baseline gap-2">
          <span className="text-xl font-bold text-rs-primary">
            {formatPrice(currency, price)}
          </span>
          {was !== undefined && was > price && (
            <span className="text-xs text-rs-ink-muted line-through">
              {formatPrice(currency, was)}
            </span>
          )}
        </div>
      </div>
    </Link>
  )
}
