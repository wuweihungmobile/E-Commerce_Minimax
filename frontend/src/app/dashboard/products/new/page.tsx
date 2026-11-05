'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import ProductForm from '@/components/product/ProductForm'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

export default function NewProductPage() {
  const router = useRouter()

  useEffect(() => {
    if (!AuthService.isAuthenticated()) {
      router.push(loginHref())
      return
    }
  }, [router])

  return (
    <div>
      <main className="max-w-3xl">
        <div>
          <ProductForm />
        </div>
      </main>
    </div>
  )
}