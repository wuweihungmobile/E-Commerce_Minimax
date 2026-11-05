import TenantApplyForm from '@/components/tenant/TenantApplyForm'
import { StorefrontShell } from '@/components/layout/StorefrontShell'

// 開店申請（Sprint 328 起放在 `(auth)` 路由群組，網址仍是 /tenant/apply）：
// 沿用買家頁共用版型（Header／Footer），登入守門由 (auth)/layout.tsx 的 AuthGuard 提供——
// 沒登入會被導去 /login?redirect=/tenant/apply，與原本頁面自己的檢查行為相同。
export default function TenantApplyPage() {
  return (
    <StorefrontShell>
      <div className="max-w-3xl">
        <div className="mb-8">
          <h1 className="text-2xl font-semibold text-rs-ink">申請開店</h1>
          <p className="mt-1 text-sm text-rs-ink-muted">
            填寫店鋪資訊以開始使用平台服務
          </p>
        </div>
        <TenantApplyForm />
      </div>
    </StorefrontShell>
  )
}
