import type { Metadata } from "next"
import { StoreNotFound } from "@/components/storefront/StoreNotFound"

// 店鋪不存在（stores/[slug]/layout.tsx 拋出 notFound()）：真正的 HTTP 404＋noindex，畫面與用戶端備援（StoreLayout）共用 StoreNotFound。
export const metadata: Metadata = { title: "找不到店鋪" }

export default function StoresNotFound() {
  return <StoreNotFound />
}
