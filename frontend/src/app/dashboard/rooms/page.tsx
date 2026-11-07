import type { Metadata } from 'next'
import RoomList from '@/components/room/RoomList'
import { PageTitle, PageDescription } from '@/components/ui/page-title'

export const metadata: Metadata = {
  title: '房源管理',
}

export default function RoomsPage() {
  return (
    <div>
      <main>
        <div>
          <div className="mb-6">
            <PageTitle>房源管理</PageTitle>
            <PageDescription>
              管理您的房源資料，上下架房源
            </PageDescription>
          </div>
          <RoomList />
        </div>
      </main>
    </div>
  )
}