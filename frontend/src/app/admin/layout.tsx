import { SellerShell } from "@/components/layout/SellerShell"

// 平台管理台共用版型（Sprint 328）：與賣家後台同一個外殼，導覽設定見 components/layout/sellerNav.ts。
// 各功能頁沒有前端角色守門、純依賴後端 403（見 app/admin/page.tsx 檔頭註解），外殼沿用同一慣例，不新增角色導向。
export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return <SellerShell area="admin">{children}</SellerShell>
}
