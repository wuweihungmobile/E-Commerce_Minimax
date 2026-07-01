import Link from "next/link"
import { MessageCircleMore } from "lucide-react"

// Bottom 區塊：頁尾（版權 + 連結）+ 懸浮客服鈕。
export function StorefrontFooter() {
  return (
    <>
      <footer className="bg-rs-surface border-t border-rs-hairline mt-auto">
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
      {/* 懸浮客服 */}
      <Link
        href="/dashboard/chat"
        aria-label="聊聊客服"
        className="fixed right-2 bottom-8 z-[60] inline-flex items-center justify-center gap-2 bg-rs-primary text-white rounded-full px-4 py-3 shadow-lg transition-colors hover:bg-rs-primary-hover"
      >
        <MessageCircleMore className="w-6 h-6" aria-hidden />
        <span className="text-sm font-medium">聊聊</span>
        <span className="absolute -top-1 -right-1 w-3 h-3 rounded-full bg-rs-accent border-2 border-white" />
      </Link>
    </>
  )
}
