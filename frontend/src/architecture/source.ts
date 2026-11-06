import { existsSync, readdirSync, readFileSync } from 'node:fs'
import { join, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'

// 架構守門測試（src/architecture/*.test.ts）共用的「讀原始碼」小工具。
// 這些測試掃的是 src/ 底下的原始碼文字，不是執行結果——規則寫成測試，違反時 npm test（pre-commit、CI）就會紅燈。

/** frontend/src 的絕對路徑 */
export const SRC_DIR = fileURLToPath(new URL('../', import.meta.url))

/** 列出 src 底下 dir 內符合條件的檔案，回傳「相對 src 的 POSIX 路徑」（排序後，結果穩定） */
export function listSourceFiles(dir: string, accept: (path: string) => boolean): string[] {
  const found: string[] = []
  const walk = (absolute: string) => {
    for (const entry of readdirSync(absolute, { withFileTypes: true })) {
      const full = join(absolute, entry.name)
      if (entry.isDirectory()) {
        walk(full)
        continue
      }
      const path = relative(SRC_DIR, full).split(sep).join('/')
      if (accept(path)) found.push(path)
    }
  }
  walk(join(SRC_DIR, dir))
  return found.sort()
}

export function readSource(path: string): string {
  return readFileSync(join(SRC_DIR, path), 'utf-8')
}

export function sourceExists(path: string): boolean {
  return existsSync(join(SRC_DIR, path))
}
