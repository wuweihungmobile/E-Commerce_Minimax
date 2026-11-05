'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import PurchaseOrderForm from '@/components/erp/PurchaseOrderForm'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

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
            <h1 className="text-2xl font-semibold text-rs-ink">新增採購訂單</h1>
          </div>
          <PurchaseOrderForm mode="create" />
        </div>
      </main>
    </div>
  )
}