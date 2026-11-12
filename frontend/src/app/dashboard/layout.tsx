import type { Metadata } from "next"
import { SellerShell } from "@/components/layout/SellerShell"
import { DASHBOARD_TITLE_TEMPLATE } from "@/lib/site"

// 頁面標題（Sprint 332，DEF-465）：底下各頁的標題是「頁名｜賣家後台｜意象若水 RUOSHUI」（template）。
// 同一層的 /dashboard 本身不適用 template（Next 的規則：layout 的 template 只管子層），所以它用 default；
// default 會經過根 layout 的 template 補上品牌，所以這裡寫到「賣家後台」為止。
export const metadata: Metadata = {
  title: { default: "營運總覽｜賣家後台", template: DASHBOARD_TITLE_TEMPLATE },
  // 搜尋引擎不收錄（DEF-500，Sprint 341）：賣家後台是登入後才能用的管理介面，沒有可收錄的公開內容
  robots: { index: false, follow: false },
}

// 賣家後台共用版型（Sprint 328，HOMEPAGE_TEMPLATE_INTEGRATION_FRD §7 階段三／行動項 AI-1904）：
// TOP（賣家變體 Header）＋ 側欄導覽 ＋ Content（{children}）＋ Bottom（Footer）。
// 各頁不再自己組頂欄（麵包屑＋email＋登出）；導覽設定集中在 components/layout/sellerNav.ts。
export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return <SellerShell area="dashboard">{children}</SellerShell>
}
