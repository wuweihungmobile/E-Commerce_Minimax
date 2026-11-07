import { AuthGuard } from "./AuthGuard"
import { StorefrontFooter } from "./StorefrontFooter"
import { SellerHeader } from "./SellerHeader"
import { SellerSidebar } from "./SellerSidebar"
import type { SellerArea } from "./sellerNav"

// 賣家外殼（賣家後台／平台管理台共用）：TOP（SellerHeader）＋ 側欄 ＋ Content（{children}）＋ Bottom（Footer）。
// 由 app/dashboard、app/admin 各自的 layout.tsx 使用；換頁時 Header／側欄／Footer 不重建（App Router 巢狀 layout）。
// （原本還有 app/cms 第三個區域，Sprint 332 的 DEF-466 併進 dashboard 後移除。）
//
// 內容欄用 <div> 而不是 <main>：各頁自己已有 <main>，再包一層 <main> 會變成巢狀 landmark。
//
// 登入守門（Sprint 331，ADR-002）：內容欄外面包 AuthGuard，與買家頁面的 (auth) layout 是同一個元件（DEF-413）。
// 沒登入就不渲染頁面內容、不發請求，整頁導去 /login?redirect=<原頁>。在這之前守門寫在 37 個 dashboard 頁面各自的
// useEffect 裡（Sprint 328 的註解：「這些頁面各自檢查登入」），/admin 與 /cms 的頁面則完全沒有——沒登入的人會先看到整頁、
// 先打出必然 401 的請求，才被 axios 攔截器導走。頁面不需要、也不應該再自己檢查登入。
// 守門只管「有沒有登入」；角色權限仍由後端回 403（各頁沒有前端角色守門的既有慣例不變）。
export function SellerShell({ area, children }: { area: SellerArea; children: React.ReactNode }) {
  return (
    <div className="min-h-screen flex flex-col bg-rs-bg-base">
      <SellerHeader area={area} />
      <div className="w-full max-w-[1440px] mx-auto px-6 py-8 pb-20 grid grid-cols-1 lg:grid-cols-[240px_1fr] gap-8 items-start flex-1">
        <SellerSidebar area={area} />
        <div className="min-w-0">
          <AuthGuard>{children}</AuthGuard>
        </div>
      </div>
      {/* 賣家端不放懸浮客服鈕：它會蓋在資料表右下角，而訊息中心已在側欄 */}
      <StorefrontFooter showChatFab={false} />
    </div>
  )
}
