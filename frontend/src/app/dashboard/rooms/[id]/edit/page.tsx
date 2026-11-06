'use client'

import { useParams } from 'next/navigation'
import RoomForm from '@/components/room/RoomForm'

export default function EditRoomPage() {
  const params = useParams()

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