import Link from "next/link"
import { MessageCircleMore } from "lucide-react"
import { buttonVariants } from "@/components/ui/button"
import { cn } from "@/lib/utils"

// Bottom 區塊：頁尾（版權 + 連結）+ 懸浮客服鈕。
// showChatFab=false：賣家外殼不放懸浮鈕（會蓋住資料表右下角，且訊息中心已在側欄）。
export function StorefrontFooter({ showChatFab = true }: { showChatFab?: boolean }) {
  return (
    <>
      <footer className="bg-rs-surface border-t border-rs-hairline mt-auto" data-testid="storefront-footer">
        <div className="max-w-[1440px] mx-auto px-6 py-12 flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <span className="text-sm text-rs-ink-muted">
            © 2026 意象若水 RUOSHUI. 生活減法，無負擔的購物體驗。
          </span>
          <div className="flex gap-6 text-sm text-rs-ink-muted">
            <Link href="/privacy" className="hover:text-rs-primary">
              隱私權政策
            </Link>
            <Link href="/terms" className="hover:text-rs-primary">
              服務條款
            </Link>
            <Link href="/notifications" className="hover:text-rs-primary">
              聯絡客服
            </Link>
          </div>
        </div>
      </footer>
      {/* 懸浮客服（設計稿規格：radius-md、padding 10×20、18px／500；無陰影） */}
      {showChatFab && (
        <Link
          href="/dashboard/chat"
          aria-label="聊聊客服"
          className={cn(buttonVariants({ variant: "default" }), "fixed right-2 bottom-8 z-[60] gap-2")}
        >
          <MessageCircleMore className="w-6 h-6" aria-hidden />
          <span className="text-lg font-medium">聊聊</span>
          <span className="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-rs-accent border-2 border-white" />
        </Link>
      )}
    </>
  )
}
