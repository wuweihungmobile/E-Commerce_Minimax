'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import Link from 'next/link'
import RoomList from '@/components/room/RoomList'
import AuthService from '@/services/auth'

export default function RoomsPage() {
  const router = useRouter()

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push('/login')
      return
    }
  }, [router])

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
              <span className="text-gray-900 font-medium">房源管理</span>
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
            <h1 className="text-2xl font-bold text-gray-900">房源管理</h1>
            <p className="mt-1 text-sm text-gray-600">
              管理您的房源資料，上下架房源
            </p>
          </div>
          <RoomList />
        </div>
      </main>
    </div>
  )
}