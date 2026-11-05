import { StorefrontFooter } from "./StorefrontFooter"
import { SellerHeader } from "./SellerHeader"
import { SellerSidebar } from "./SellerSidebar"
import type { SellerArea } from "./sellerNav"

// 賣家外殼（賣家後台／平台管理台／內容管理共用）：TOP（SellerHeader）＋ 側欄 ＋ Content（{children}）＋ Bottom（Footer）。
// 由 app/dashboard、app/admin、app/cms 各自的 layout.tsx 使用；換頁時 Header／側欄／Footer 不重建（App Router 巢狀 layout）。
//
// 內容欄用 <div> 而不是 <main>：各頁自己已有 <main>，再包一層 <main> 會變成巢狀 landmark。
// 登入守門不放在這裡：這些頁面各自檢查登入並導去 /login（與 Sprint 328 之前相同），外殼只負責版型。
export function SellerShell({ area, children }: { area: SellerArea; children: React.ReactNode }) {
  return (
    <div className="min-h-screen flex flex-col bg-rs-bg-base">
      <SellerHeader area={area} />
      <div className="w-full max-w-[1440px] mx-auto px-6 py-8 pb-20 grid grid-cols-1 lg:grid-cols-[240px_1fr] gap-8 items-start flex-1">
        <SellerSidebar area={area} />
        <div className="min-w-0">{children}</div>
      </div>
      {/* 賣家端不放懸浮客服鈕：它會蓋在資料表右下角，而訊息中心已在側欄 */}
      <StorefrontFooter showChatFab={false} />
    </div>
  )
}
