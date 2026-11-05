'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useParams } from 'next/navigation'
import PurchaseOrderForm from '@/components/erp/PurchaseOrderForm'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

export default function EditPurchaseOrderPage() {
  const router = useRouter()
  const params = useParams()

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
  }, [router])

  const orderId = params.id as string

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <h1 className="text-2xl font-semibold text-rs-ink">編輯採購訂單</h1>
          </div>
          <PurchaseOrderForm orderId={orderId} mode="edit" />
        </div>
      </main>
    </div>
  )
}