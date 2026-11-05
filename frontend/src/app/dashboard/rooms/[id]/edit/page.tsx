'use client'

import { useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { useParams } from 'next/navigation'
import RoomForm from '@/components/room/RoomForm'
import AuthService from '@/services/auth'
import { loginHref } from '@/lib/auth-redirect'

export default function EditRoomPage() {
  const router = useRouter()
  const params = useParams()

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
          <RoomForm roomId={params.id as string} />
        </div>
      </main>
    </div>
  )
}