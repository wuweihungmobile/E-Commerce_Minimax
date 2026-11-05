'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import TenantList from '@/components/tenant/TenantList'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

export default function TenantsPage() {
  const router = useRouter()

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
  }, [router])

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <h1 className="text-2xl font-semibold text-rs-ink">店鋪管理</h1>
            <p className="mt-1 text-sm text-rs-ink-muted">
              管理您的店鋪資訊，切換不同店鋪
            </p>
          </div>
          <TenantList />
        </div>
      </main>
    </div>
  )
}
