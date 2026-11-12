import type { Metadata } from "next"
import { SellerShell } from "@/components/layout/SellerShell"
import { ADMIN_TITLE_TEMPLATE } from "@/lib/site"

// 頁面標題（Sprint 332，DEF-465）：底下各頁的標題是「頁名｜平台管理｜意象若水 RUOSHUI」（template）。
// 同一層的 /admin 本身不適用 template（Next 的規則：layout 的 template 只管子層），所以它用 default；
// default 會經過根 layout 的 template 補上品牌，所以這裡寫到「平台管理」為止。
export const metadata: Metadata = {
  title: { default: "平台總覽｜平台管理", template: ADMIN_TITLE_TEMPLATE },
  // 搜尋引擎不收錄（DEF-500，Sprint 341）：平台管理台是登入後才能用的管理介面，沒有可收錄的公開內容
  robots: { index: false, follow: false },
}

// 平台管理台共用版型（Sprint 328）：與賣家後台同一個外殼，導覽設定見 components/layout/sellerNav.ts。
// 各功能頁沒有前端角色守門、純依賴後端 403（見 app/admin/page.tsx 檔頭註解），外殼沿用同一慣例，不新增角色導向。
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <SellerShell area="admin">{children}</SellerShell>
}
