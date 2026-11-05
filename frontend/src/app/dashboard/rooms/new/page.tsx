'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import RoomForm from '@/components/room/RoomForm'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

export default function NewRoomPage() {
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
          <RoomForm />
        </div>
      </main>
    </div>
  )
}