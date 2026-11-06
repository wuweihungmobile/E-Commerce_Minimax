'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import TenantList from '@/components/tenant/TenantList'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

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
            <PageTitle>店鋪管理</PageTitle>
            <PageDescription>
              管理您的店鋪資訊，切換不同店鋪
            </PageDescription>
          </div>
          <TenantList />
        </div>
      </main>
    </div>
  )
}
