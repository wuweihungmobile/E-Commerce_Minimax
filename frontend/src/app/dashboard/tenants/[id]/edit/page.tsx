'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import TenantEditForm from '@/components/tenant/TenantEditForm'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

interface TenantEditPageProps {
  params: Promise<{
    id: string
  }>
}

export default function TenantEditPage({ params }: TenantEditPageProps) {
  const router = useRouter()
  const [tenantId, setTenantId] = useState<string | null>(null)
  const [user, setUser] = useState<{ email: string; role: string; tenantId: string | null } | null>(null)

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }

    const currentUser = AuthService.getCurrentUser()
    // Use setTimeout to avoid synchronous setState in effect
    setTimeout(() => {
      setUser(currentUser)
    }, 0)

    // Unwrap params
    params.then((resolvedParams) => {
      setTenantId(resolvedParams.id)
    })
  }, [router, params])

  // Render loading state until we have the tenant ID
  if (!tenantId || !user) {
    return (
      <div>
        <main>
          <div>
            <div className="flex items-center justify-center h-64">
              <div className="text-rs-ink-muted">載入中...</div>
            </div>
          </div>
        </main>
      </div>
    )
  }

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <h1 className="text-2xl font-semibold text-rs-ink">編輯店鋪</h1>
            <p className="mt-1 text-sm text-rs-ink-muted">
              修改店鋪的基本資訊
            </p>
          </div>
          <TenantEditForm tenantId={tenantId} />
        </div>
      </main>
    </div>
  )
}
