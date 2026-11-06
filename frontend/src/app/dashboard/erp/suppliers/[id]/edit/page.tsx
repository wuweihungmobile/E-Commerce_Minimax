'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useParams } from 'next/navigation'
import SupplierForm from '@/components/erp/SupplierForm'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'
import { PageTitle } from '@/components/ui/page-title'

export default function EditSupplierPage() {
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
            <PageTitle>編輯供應商</PageTitle>
          </div>
          <SupplierForm supplierId={supplierId} mode="edit" />
        </div>
      </main>
    </div>
  )
}