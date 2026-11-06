"use client"

import { useState } from "react"
import { Search } from "lucide-react"
import { cn } from "@/lib/utils"
import { Input } from "@/components/ui/input"
import { Button } from "@/components/ui/button"

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
      <Input
        type="search"
        value={current}
        onChange={(e) => handleChange(e.target.value)}
        placeholder={placeholder}
        aria-label={placeholder}
        className="flex-1 min-w-0"
        suppressHydrationWarning
      />
      <Button
        variant="default"
        type="submit"
        className="flex-none gap-2"
      >
        <Search className="w-4 h-4" aria-hidden />
        <span>{buttonLabel}</span>
      </Button>
    </form>
  )
}
