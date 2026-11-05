import { SellerShell } from "@/components/layout/SellerShell"

// 內容管理（CMS）共用版型（Sprint 328）：與賣家後台同一個外殼，導覽設定見 components/layout/sellerNav.ts。
export default function CmsLayout({ children }: { children: React.ReactNode }) {
  return <SellerShell area="cms">{children}</SellerShell>
}
