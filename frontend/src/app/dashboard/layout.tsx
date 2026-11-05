import { SellerShell } from "@/components/layout/SellerShell"

// 賣家後台共用版型（Sprint 328，HOMEPAGE_TEMPLATE_INTEGRATION_FRD §7 階段三／行動項 AI-1904）：
// TOP（賣家變體 Header）＋ 側欄導覽 ＋ Content（{children}）＋ Bottom（Footer）。
// 各頁不再自己組頂欄（麵包屑＋email＋登出）；導覽設定集中在 components/layout/sellerNav.ts。
export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  return <SellerShell area="dashboard">{children}</SellerShell>
}
