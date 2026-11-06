'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import SupplierForm from '@/components/erp/SupplierForm'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'
import { PageTitle } from '@/components/ui/page-title'

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
            <PageTitle>新增供應商</PageTitle>
          </div>
          <SupplierForm mode="create" />
        </div>
      </main>
    </div>
  )
}