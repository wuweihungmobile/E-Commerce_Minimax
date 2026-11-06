import { afterEach } from 'vitest'
import { cleanup, configure } from '@testing-library/react'

// findBy*／waitFor 的預設逾時 1 秒；機器忙的時候 Radix 的 Portal 掛載會超過（見 vitest.config.mts 的 testTimeout 說明）
configure({ asyncUtilTimeout: 5000 })

// 不開 vitest 的 globals（避免在 tsconfig 加全域型別），所以 Testing Library 的自動清理不會生效，這裡手動掛。
afterEach(() => {
  cleanup()
})
