'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

export default function ErpDashboardPage() {
  const router = useRouter()

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
  }, [router])

  const erpModules = [
    {
      title: '供應商管理',
      description: '管理供應商資料、建立、編輯',
      href: '/dashboard/erp/suppliers',
      icon: '🏢',
      color: 'bg-rs-primary',
    },
    {
      title: '採購訂單',
      description: '建立、管理採購訂單，追蹤交貨狀態',
      href: '/dashboard/erp/purchase-orders',
      icon: '📦',
      color: 'bg-rs-success',
    },
    {
      title: '庫存帳查',
      description: '查看庫存水位、低庫存警告',
      href: '/dashboard/erp/inventory',
      icon: '📊',
      color: 'bg-rs-warning',
    },
    {
      title: '庫存異動',
      description: '記錄入庫、出庫、調整等異動',
      href: '/dashboard/erp/stock-movements',
      icon: '🔄',
      color: 'bg-rs-info',
    },
  ]

  return (
    <div>
      <main>
        <div>
          <div className="mb-8">
            <h1 className="text-3xl font-semibold text-rs-ink">ERP 進銷存系統</h1>
            <p className="mt-2 text-rs-ink-muted">
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
                <div className="bg-white rounded-lg hover:border-rs-primary transition-colors p-6 border border-rs-hairline">
                  <div className="flex items-start gap-4">
                    <div className={`${module.color} rounded-lg p-3 text-white text-2xl`}>
                      {module.icon}
                    </div>
                    <div className="flex-1">
                      <h3 className="text-lg font-semibold text-rs-ink group-hover:text-rs-primary">
                        {module.title}
                      </h3>
                      <p className="mt-1 text-sm text-rs-ink-muted">
                        {module.description}
                      </p>
                    </div>
                    <div className="text-rs-disabled-text group-hover:text-rs-primary">
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