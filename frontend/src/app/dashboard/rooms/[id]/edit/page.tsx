import type { Metadata } from 'next'
import RoomForm from '@/components/room/RoomForm'

export const metadata: Metadata = {
  title: '編輯房源',
}

export default async function EditRoomPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params

  return (
    <div>
      <main className="max-w-3xl">
        <div>
          <RoomForm roomId={id} />
        </div>
      </main>
    </div>
  )
}