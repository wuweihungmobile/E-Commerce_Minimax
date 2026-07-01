"use client"

import { useState } from "react"
import { Search } from "lucide-react"
import { cn } from "@/lib/utils"

export interface SearchBarProps {
  placeholder?: string
  buttonLabel?: string
  /** 受控值；提供時搭配 onChange */
  value?: string
  /** 非受控預設值 */
  defaultValue?: string
  onChange?: (value: string) => void
  onSubmit?: (value: string) => void
  className?: string
}

// 賣場搜尋列：input + 主色送出鈕，對齊設計稿 rs-search。
export function SearchBar({
  placeholder = "搜尋商品、品牌與分類…",
  buttonLabel = "搜尋",
  value,
  defaultValue = "",
  onChange,
  onSubmit,
  className,
}: SearchBarProps) {
  const isControlled = value !== undefined
  const [inner, setInner] = useState(defaultValue)
  const current = isControlled ? value : inner

  const handleChange = (next: string) => {
    if (!isControlled) setInner(next)
    onChange?.(next)
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onSubmit?.(current ?? "")
  }

  return (
    <form
      onSubmit={handleSubmit}
      className={cn(
        "flex items-stretch w-full max-w-[800px] bg-white rounded-md overflow-hidden border border-rs-hairline transition-colors focus-within:border-rs-primary",
        className
      )}
      role="search"
    >
      <input
        type="search"
        value={current}
        onChange={(e) => handleChange(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        className="flex-1 min-w-0 border-none outline-none bg-transparent text-base text-rs-ink px-4 min-h-[48px] placeholder:text-rs-ink-muted"
        suppressHydrationWarning
      />
      <button
        type="submit"
        className="flex-none inline-flex items-center justify-center gap-2 border-none cursor-pointer bg-rs-primary text-white px-7 text-base font-medium transition-colors hover:bg-rs-primary-hover"
      >
        <Search className="w-4 h-4" aria-hidden />
        <span>{buttonLabel}</span>
      </button>
    </form>
  )
}
