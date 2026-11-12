"use client"

import { useState } from "react"
import Link from "next/link"
import { Droplets, Star } from "lucide-react"
import { Badge } from "@/components/ui/badge"
import listingService, { type Listing } from "@/services/listing"
import { useAsyncData } from "@/hooks/useAsyncData"
import { useIsClient } from "@/hooks/useIsClient"
import cartService from "@/services/cart"
import { notifyCartChanged } from "@/services/cartEvents"
import bookingService, { type AvailabilityResponse } from "@/services/booking"
import { MonthCalendar } from "@/components/storefront/MonthCalendar"
import { toLocalDateString } from "@/lib/date"
import { Label } from "@/components/ui/label"
import { Input } from "@/components/ui/input"
import { PageTitle } from "@/components/ui/page-title"
import { buttonVariants, Button } from "@/components/ui/button"
import { EmptyState } from "@/components/ui/states"
import { ListingNotFound } from "@/components/storefront/ListingNotFound"
import { getApiErrorInfo } from '@/lib/api-error'

type LoadState = "loading" | "ok" | "auth" | "notfound" | "error"

function statusOf(err: unknown): number | undefined {
  return getApiErrorInfo(err).status
}

function formatPrice(currency: string, value: number): string {
  const symbol = currency === "TWD" ? "NT$" : currency
  return `${symbol}${value.toLocaleString("zh-TW")}`
}

// availability 不可訂原因碼中文對照（Sprint 58 AI-2408）：後端回傳 code（見 BookingDto.AvailabilityReasonCode），
// 全站目前無 i18n 需求（純中文介面），故不導入多語系框架，僅用查表；查無對應 code 時原樣顯示（向後相容）。
const AVAILABILITY_REASON_MESSAGES: Record<string, string> = {
  INVALID_DATE_RANGE: "退房日期必須晚於入住日期",
  NOT_OPEN_FOR_BOOKING: "所選日期尚未開放預訂",
  BOOKED: "所選日期已被預訂",
  BLOCKED: "所選日期已被房源鎖定",
  MAINTENANCE: "所選日期進行維護中",
}

function unavailableReasonText(reason: string): string {
  return AVAILABILITY_REASON_MESSAGES[reason] ?? reason
}

// 買家商品詳情內容（client）：載入 GET /v2/listings/{id}，三態處理，
// PRODUCT → 數量 + 加入購物車；ROOM → 日期選擇 + 計價 + 加入購物車（帶日期）。
//
// SEO（DEF-500，Sprint 341）：server page 先取好商品以 initialListing 傳進來，第一份 HTML 就有商品名稱、價格、說明與圖片
// （以前是載入中骨架）。商品不存在由伺服器端直接回真正的 404（listings/[id]/not-found.tsx，與下面的備援畫面共用 ListingNotFound）；
// 沒取到（逾時、後端壞了、需要登入的舊行為）就不傳，由瀏覽器自己載入並照舊處理各種失敗。
export function ListingDetail({ id, initialListing }: { id: string; initialListing?: Listing }) {
  const { data: listing, error, errorInfo, loading, reload } = useAsyncData(
    () => listingService.getListingById(id),
    [id],
    { errorMessage: "載入商品失敗", initialData: initialListing }
  )
  // 整月日曆的第一個畫面依賴「現在的時間」（本月是幾月、今天是哪一天）：伺服器（容器通常是 UTC）與訪客瀏覽器（台灣 UTC+8）
  // 在月底／午夜前後算出不同的月份與日期，hydration 會因文字不符而丟掉伺服器的 HTML。日曆不是搜尋引擎要收錄的內容，
  // 所以只在瀏覽器渲染，伺服器端（與 hydration 期間）先放同樣大小的佔位（DEF-500，Sprint 341）。
  //
  // 同一個 isClient 也讓下單相關的互動控制項（日期、數量、查詢可用性、加入購物車）在 hydration 完成前保持停用：
  // 內容改由伺服器渲染之後，這些控制項在第一份 HTML 就出現了，但事件處理還沒接上——這時輸入的日期與數量會在 hydration 時被
  // 重設、點擊沒有任何反應（以前它們要等用戶端載入完才出現，天然避開了這個空窗）。停用讓使用者看得出「還沒準備好」，
  // 而且自動化測試的 fill／click 會等到它可用。
  const isClient = useIsClient()
  const status = errorInfo?.status
  const state: LoadState = loading
    ? "loading"
    : error === null
      ? "ok"
      : status === 401 || status === 403
        ? "auth"
        : status === 404
          ? "notfound"
          : "error"

  // PRODUCT
  const [quantity, setQuantity] = useState(1)
  // ROOM
  const [checkIn, setCheckIn] = useState("")
  const [checkOut, setCheckOut] = useState("")
  const [availability, setAvailability] = useState<AvailabilityResponse | null>(null)
  const [checking, setChecking] = useState(false)
  const [availError, setAvailError] = useState<string | null>(null)

  // 加購
  const [adding, setAdding] = useState(false)
  const [added, setAdded] = useState(false)
  const [addError, setAddError] = useState<string | null>(null)

  const handleRetry = () => {
    reload()
  }

  // ROOM 日期驗證：入住不早於今日、退房晚於入住
  const dateError = (): string | null => {
    const today = toLocalDateString(new Date())
    if (!checkIn || !checkOut) return "請選擇入住與退房日期"
    if (checkIn < today) return "入住日期不可早於今日"
    if (checkOut <= checkIn) return "退房日期需晚於入住日期"
    return null
  }

  const handleCheckAvailability = () => {
    const err = dateError()
    if (err) {
      setAvailError(err)
      setAvailability(null)
      return
    }
    setChecking(true)
    setAvailError(null)
    setAvailability(null)
    bookingService
      .checkAvailability(id, checkIn, checkOut)
      .then((a) => setAvailability(a))
      // 可訂性查詢需登入（booking:read，DEF-402）：訪客得到 401 是「要先登入」，不是「失敗」
      .catch((err) =>
        setAvailError(statusOf(err) === 401 ? "請先登入以查詢可用性" : "查詢可用性失敗，請稍後再試")
      )
      .finally(() => setChecking(false))
  }

  const handleAddToCart = () => {
    if (!listing) return
    const isRoom = listing.listingType === "ROOM"
    if (isRoom) {
      const err = dateError()
      if (err) {
        setAddError(err)
        return
      }
      // 需先查詢可用性且為可預訂；日期變動後需重查（避免加購已被訂走的日期）
      if (
        !availability ||
        !availability.available ||
        availability.checkInDate !== checkIn ||
        availability.checkOutDate !== checkOut
      ) {
        setAddError("請先查詢可用性，確認可預訂後再加入購物車")
        return
      }
    }
    setAdding(true)
    setAddError(null)
    setAdded(false)
    const payload = isRoom
      ? { listingId: id, quantity: 1, startDate: checkIn, endDate: checkOut }
      : { listingId: id, quantity }
    cartService
      .addItem(payload)
      .then(() => {
        setAdded(true)
        notifyCartChanged()
      })
      .catch((err: unknown) => {
        const s = statusOf(err)
        setAddError(s === 401 || s === 403 ? "請先登入再加入購物車" : "加入購物車失敗，請稍後再試")
      })
      .finally(() => setAdding(false))
  }

  if (state === "loading") {
    return (
      <div data-testid="listing-loading" className="grid gap-8 lg:grid-cols-2">
        <div className="aspect-square rounded-lg bg-rs-tertiary animate-pulse" />
        <div className="flex flex-col gap-4">
          <div className="h-8 w-2/3 rounded bg-rs-tertiary animate-pulse" />
          <div className="h-6 w-1/3 rounded bg-rs-tertiary animate-pulse" />
          <div className="h-24 w-full rounded bg-rs-tertiary animate-pulse" />
        </div>
      </div>
    )
  }

  if (state === "auth") {
    return (
      <EmptyState
        data-testid="listing-auth-empty"
        className="py-24"
        title="請先登入以查看商品詳情"
        action={
          <Link href="/login" className={buttonVariants({ variant: "default" })}>
            前往登入
          </Link>
        }
      />
    )
  }

  if (state === "notfound") {
    return <ListingNotFound />
  }

  if (state === "error" || !listing) {
    return (
      <EmptyState
        data-testid="listing-error"
        className="py-24"
        title="商品載入失敗"
        action={
          <Button variant="default" type="button" onClick={handleRetry}>
            重新載入
          </Button>
        }
      />
    )
  }

  const isRoom = listing.listingType === "ROOM"
  // ROOM 需先查詢可用性且為可預訂（且日期未變動）才可加購；PRODUCT 不受限
  const roomAddBlocked =
    isRoom &&
    !(
      availability?.available &&
      availability.checkInDate === checkIn &&
      availability.checkOutDate === checkOut
    )

  return (
    <div data-testid="listing-detail" className="grid gap-8 lg:grid-cols-2">
      {/* 商品圖 */}
      <div className="relative aspect-square overflow-hidden rounded-lg bg-rs-tertiary">
        {listing.coverImageUrl ? (
          // eslint-disable-next-line @next/next/no-img-element -- 商品圖為後端動態 URL，避免 next/image remotePatterns 設定
          <img
            src={listing.coverImageUrl}
            alt={listing.title}
            className="w-full h-full object-cover"
          />
        ) : (
          <div className="w-full h-full flex items-center justify-center">
            <Droplets className="w-16 h-16 text-rs-primary opacity-45" aria-hidden />
          </div>
        )}
      </div>

      {/* 資訊 + 動作 */}
      <div className="flex flex-col gap-5">
        <div className="flex items-center gap-2">
          <Badge variant={isRoom ? "logisticsAlt" : "logistics"}>
            {isRoom ? "旅宿房型" : "商品"}
          </Badge>
          {listing.tags?.slice(0, 3).map((t) => (
            <Badge key={t} variant="feature">
              {t}
            </Badge>
          ))}
        </div>

        <PageTitle className="leading-snug">{listing.title}</PageTitle>

        <div className="flex items-baseline gap-2">
          <span className="text-3xl font-bold text-rs-primary">
            {formatPrice(listing.currency, listing.basePrice)}
          </span>
          {isRoom && <span className="text-sm text-rs-ink-muted">/ 晚起</span>}
        </div>

        {listing.description && (
          <p className="text-sm leading-relaxed text-rs-ink-muted whitespace-pre-line">
            {listing.description}
          </p>
        )}

        {/* ROOM：日期選擇 + 計價 */}
        {isRoom && (
          <div className="flex flex-col gap-3 rounded-lg border border-rs-hairline p-4">
            {isClient ? (
              <MonthCalendar
                roomListingId={id}
                checkIn={checkIn}
                checkOut={checkOut}
                basePrice={listing.basePrice}
                currency={listing.currency}
                onSelectRange={(ci, co) => {
                  setCheckIn(ci)
                  setCheckOut(co)
                  setAvailability(null)
                  setAvailError(null)
                }}
              />
            ) : (
              <div data-testid="listing-calendar-placeholder" aria-hidden className="min-h-72 rounded bg-rs-tertiary animate-pulse" />
            )}
            <div className="flex flex-col gap-1 sm:flex-row sm:gap-4">
              <Label className="flex flex-col gap-1">
                入住日期
                <Input
                  type="date"
                  data-testid="listing-checkin"
                  value={checkIn}
                  onChange={(e) => setCheckIn(e.target.value)}
                  disabled={!isClient}
                  className="w-auto"
                />
              </Label>
              <Label className="flex flex-col gap-1">
                退房日期
                <Input
                  type="date"
                  data-testid="listing-checkout"
                  value={checkOut}
                  onChange={(e) => setCheckOut(e.target.value)}
                  disabled={!isClient}
                  className="w-auto"
                />
              </Label>
            </div>
            <Button
              variant="outline"
              type="button"
              onClick={handleCheckAvailability}
              disabled={checking || !isClient}
              className="self-start"
            >
              {checking ? "查詢中…" : "查詢可用性"}
            </Button>
            {availError && <p className="text-sm text-rs-error">{availError}</p>}
            {availability && availability.available && (
              <div data-testid="listing-availability" className="text-sm text-rs-ink">
                <span className="text-rs-success">可預訂</span>
                {availability.nightsCount != null && availability.totalPrice != null && (
                  <>
                    {" · "}
                    <span className="text-rs-ink-muted">{availability.nightsCount} 晚合計</span>{" "}
                    {/* 動態定價調整（AI-2406b）：折扣（>0）原價刪除線；加價（<0）原價不刪除線 */}
                    {availability.discountAmount != null &&
                      availability.discountAmount !== 0 &&
                      availability.originalTotalPrice != null && (
                        <span
                          data-testid="listing-original-price"
                          className={
                            availability.discountAmount > 0
                              ? "mr-1 text-rs-ink-muted line-through"
                              : "mr-1 text-rs-ink-muted"
                          }
                        >
                          {formatPrice(
                            availability.currency ?? listing.currency,
                            availability.originalTotalPrice
                          )}
                        </span>
                      )}
                    <span
                      data-testid="listing-total-price"
                      className="text-xl font-bold text-rs-primary"
                    >
                      {formatPrice(
                        availability.currency ?? listing.currency,
                        availability.totalPrice
                      )}
                    </span>
                    {availability.appliedRuleName &&
                      availability.discountAmount != null &&
                      availability.discountAmount !== 0 && (
                        <Badge
                          data-testid="listing-discount-badge"
                          variant={availability.discountAmount > 0 ? "success" : "warning"}
                          className="ml-2"
                        >
                          {availability.appliedRuleName}｜
                          {availability.discountAmount > 0 ? "省 " : "加價 "}
                          {formatPrice(
                            availability.currency ?? listing.currency,
                            Math.abs(availability.discountAmount)
                          )}
                        </Badge>
                      )}
                  </>
                )}
              </div>
            )}
            {availability && !availability.available && (
              <p data-testid="listing-unavailable" className="text-sm text-rs-error">
                此日期不可預訂
                {availability.unavailableReason
                  ? `（${unavailableReasonText(availability.unavailableReason)}）`
                  : ""}
              </p>
            )}
          </div>
        )}

        {/* PRODUCT：數量 */}
        {!isRoom && (
          <Label className="flex items-center gap-3">
            數量
            <Input
              type="number"
              min={1}
              max={999}
              data-testid="listing-quantity"
              value={quantity}
              onChange={(e) => {
                const v = parseInt(e.target.value, 10)
                if (!isNaN(v) && v >= 1 && v <= 999) setQuantity(v)
              }}
              disabled={!isClient}
              className="w-20 text-center"
            />
          </Label>
        )}

        {/* 加入購物車 */}
        <div className="flex flex-col gap-2">
          <Button
            variant="default"
            type="button"
            onClick={handleAddToCart}
            disabled={adding || roomAddBlocked || !isClient}
            data-testid="listing-add-cart"
            className="gap-2"
          >
            <Star className="w-4 h-4" aria-hidden />
            {adding ? "加入中…" : "加入購物車"}
          </Button>
          {added && (
            <p data-testid="listing-add-success" className="text-sm text-rs-success">
              已加入購物車！
              <Link href="/cart" className="ml-2 underline hover:text-rs-primary">
                前往購物車
              </Link>
            </p>
          )}
          {addError && <p className="text-sm text-rs-error">{addError}</p>}
        </div>

        <Link
          href={`/reviews/product/${id}`}
          className="text-sm text-rs-info underline underline-offset-2 hover:text-rs-primary"
        >
          查看商品評價
        </Link>
      </div>
    </div>
  )
}
