import type { Metadata } from 'next'
import RoomForm from '@/components/room/RoomForm'

export const metadata: Metadata = {
  title: '新增房源',
}

export default function NewRoomPage() {
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