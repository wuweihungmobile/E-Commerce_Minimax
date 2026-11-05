'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import SupplierForm from '@/components/erp/SupplierForm'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

export default function NewSupplierPage() {
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
            <h1 className="text-2xl font-semibold text-rs-ink">新增供應商</h1>
          </div>
          <SupplierForm mode="create" />
        </div>
      </main>
    </div>
  )
}