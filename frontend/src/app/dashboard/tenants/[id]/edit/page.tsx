'use client'

import { useEffect, useState } from 'react'
import { useRouter } from 'next/navigation'
import TenantEditForm from '@/components/tenant/TenantEditForm'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'
import { PageTitle, PageDescription } from '@/components/ui/page-title'
import { LoadingState } from '@/components/ui/states'

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
            <LoadingState />
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
            <PageTitle>編輯店鋪</PageTitle>
            <PageDescription>
              修改店鋪的基本資訊
            </PageDescription>
          </div>
          <TenantEditForm tenantId={tenantId} />
        </div>
      </main>
    </div>
  )
}
