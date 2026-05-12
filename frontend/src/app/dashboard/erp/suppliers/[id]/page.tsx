'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import { useParams } from 'next/navigation'
import SupplierForm from '@/components/erp/SupplierForm'
import AuthService from '@/services/auth'

export default function ViewSupplierPage() {
  const router = useRouter()
  const params = useParams()

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
  }, [router])

  const supplierId = params.id as string

  return (
    <div className="min-h-screen bg-gray-50">
      <nav className="bg-white shadow-sm">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
          <div className="flex justify-between h-16">
            <div className="flex items-center gap-4">
              <Link href="/dashboard" className="text-gray-600 hover:text-gray-900">
                Dashboard
              </Link>
              <span className="text-gray-400">/</span>
              <Link href="/dashboard/erp/suppliers" className="text-gray-600 hover:text-gray-900">
                供應商管理
              </Link>
              <span className="text-gray-400">/</span>
              <span className="text-gray-900 font-medium">詳情</span>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">
                {AuthService.getCurrentUser()?.email}
              </span>
              <button
                onClick={() => {
                  AuthService.clearAuthData()
                  router.push('/login')
                }}
                className="px-3 py-1.5 text-sm text-white bg-red-500 rounded-md hover:bg-red-600"
              >
                登出
              </button>
            </div>
          </div>
        </div>
      </nav>

      <main className="max-w-7xl mx-auto py-6 sm:px-6 lg:px-8">
        <div className="px-4 py-6 sm:px-0">
          <div className="mb-6">
            <h1 className="text-2xl font-bold text-gray-900">供應商詳情</h1>
          </div>
          <SupplierForm supplierId={supplierId} mode="view" />
        </div>
      </main>
    </div>
  )
}