'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useParams } from 'next/navigation'
import SupplierForm from '@/components/erp/SupplierForm'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

export default function ViewSupplierPage() {
  const router = useRouter()
  const params = useParams()

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
  }, [router])

  const supplierId = params.id as string

  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <h1 className="text-2xl font-semibold text-rs-ink">供應商詳情</h1>
          </div>
          <SupplierForm supplierId={supplierId} mode="view" />
        </div>
      </main>
    </div>
  )
}