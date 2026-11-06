import type { Metadata } from "next"
import { PageTitle } from "@/components/ui/page-title"

// 隱私權政策頁（DEF-403 佔位）：頁尾連結原本指向不存在的 /privacy（404）。
// 正式內容屬法務／業務決定、尚未提供；此頁只說明「內容即將公告」，不含任何法律文字。
export const metadata: Metadata = {
  title: "隱私權政策｜意象若水 RUOSHUI",
}

export default function PrivacyPage() {
  return (
    <main className="flex-1 w-full max-w-3xl mx-auto px-6 py-16">
      <PageTitle>隱私權政策</PageTitle>
      <p className="mt-4 text-rs-ink-muted">內容即將公告。</p>
    </main>
  )
}
