'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import AuthService from '@/services/auth'

export default function ErpDashboardPage() {
  const router = useRouter()

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
  }, [router])

  const erpModules = [
    {
      title: '供應商管理',
      description: '管理供應商資料、建立、編輯',
      href: '/dashboard/erp/suppliers',
      icon: '🏢',
      color: 'bg-blue-500',
    },
    {
      title: '採購訂單',
      description: '建立、管理採購訂單，追蹤交貨狀態',
      href: '/dashboard/erp/purchase-orders',
      icon: '📦',
      color: 'bg-green-500',
    },
    {
      title: '庫存帳查',
      description: '查看庫存水位、低庫存警告',
      href: '/dashboard/erp/inventory',
      icon: '📊',
      color: 'bg-yellow-500',
    },
    {
      title: '庫存異動',
      description: '記錄入庫、出庫、調整等異動',
      href: '/dashboard/erp/stock-movements',
      icon: '🔄',
      color: 'bg-purple-500',
    },
  ]

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
              <span className="text-gray-900 font-medium">ERP 進銷存</span>
            </div>
            <div className="flex items-center gap-4">
              <span className="text-sm text-gray-600">
                {AuthService.getCurrentUser()?.email}
              </span>
              <button
                onClick={async () => {
                  await AuthService.logout()
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
          <div className="mb-8">
            <h1 className="text-3xl font-bold text-gray-900">ERP 進銷存系統</h1>
            <p className="mt-2 text-gray-600">
              管理供應商、採購訂單、庫存和異動記錄
            </p>
          </div>

          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            {erpModules.map((module) => (
              <Link
                key={module.href}
                href={module.href}
                className="block group"
              >
                <div className="bg-white rounded-lg shadow-sm hover:shadow-md transition-shadow p-6 border border-gray-200">
                  <div className="flex items-start gap-4">
                    <div className={`${module.color} rounded-lg p-3 text-white text-2xl`}>
                      {module.icon}
                    </div>
                    <div className="flex-1">
                      <h3 className="text-lg font-semibold text-gray-900 group-hover:text-blue-600">
                        {module.title}
                      </h3>
                      <p className="mt-1 text-sm text-gray-500">
                        {module.description}
                      </p>
                    </div>
                    <div className="text-gray-400 group-hover:text-blue-600">
                      →
                    </div>
                  </div>
                </div>
              </Link>
            ))}
          </div>
        </div>
      </main>
    </div>
  )
}