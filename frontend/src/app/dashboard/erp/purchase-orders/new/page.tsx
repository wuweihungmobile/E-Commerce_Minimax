'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import PurchaseOrderForm from '@/components/erp/PurchaseOrderForm'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'
import { PageTitle } from '@/components/ui/page-title'

export default function NewPurchaseOrderPage() {
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
            <PageTitle>新增採購訂單</PageTitle>
          </div>
          <PurchaseOrderForm mode="create" />
        </div>
      </main>
    </div>
  )
}