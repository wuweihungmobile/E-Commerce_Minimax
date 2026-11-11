// @vitest-environment node
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { describe, expect, it } from 'vitest'
import nextConfig from '../../next.config'

// 正式前端映像守門（Sprint 338，DEF-493）：frontend/Dockerfile 的 runner 階段用 COPY 取走 builder 階段的 .next/standalone，
// 這個目錄只有 next.config.ts 設了 output: 'standalone' 才會被 next build 產生；沒設的話映像在那一行 COPY 就建不出來。
// 這兩個檔案曾不一致約半年沒人發現（Dockerfile 最後修改 2026-05-10、Sprint 337 才實測出來），因為本地 E2E 守門與雲端 CI
// 都是 npm run build + npm start、不經 Dockerfile，沒有任何自動化流程會建那個映像——所以把「兩邊要一致」寫成測試。

const DOCKERFILE = readFileSync(fileURLToPath(new URL('../../Dockerfile', import.meta.url)), 'utf-8')

/** 去掉 Dockerfile 的註解行（# 開頭），避免註解裡提到 .next/standalone 就被當成真的 COPY。 */
const withoutComments = (dockerfile: string) =>
  dockerfile
    .split('\n')
    .filter((line) => !line.trim().startsWith('#'))
    .join('\n')

/** 有沒有一行 COPY 從別的階段（--from=…）取走 .next/standalone。 */
const copiesStandaloneOutput = (dockerfile: string) =>
  /^\s*COPY\s+[^\n]*--from=\S+[^\n]*\/\.next\/standalone(\s|$)/m.test(withoutComments(dockerfile))

describe('正式前端映像：Dockerfile 與 next.config.ts 要一致（DEF-493）', () => {
  it('Dockerfile 的 runner 階段複製 .next/standalone（本守門的前提；改成別種部署方式時要連同這個測試一起改）', () => {
    expect(copiesStandaloneOutput(DOCKERFILE)).toBe(true)
  })

  it("next.config.ts 設了 output: 'standalone'——沒有它 next build 不產生 .next/standalone，Dockerfile 的 COPY 會失敗", () => {
    expect(
      nextConfig.output,
      "Dockerfile 要複製 .next/standalone，但 next.config.ts 沒有 output: 'standalone'：正式與 CI 模擬的 compose（target: runner）前端映像會建不出來"
    ).toBe('standalone')
  })

  // DEF-498（Sprint 339，使用者同意）：docker-compose.yml 的 frontend 以 ./frontend 當建置上下文，專案根目錄的 .dockerignore 對它無效。
  // 沒有 frontend/.dockerignore 時，node_modules（約 600 MB、主機平台的原生套件）與 .next（約 440 MB）整包送進建置：實測上下文 1.07 GB、
  // 傳輸 328 秒，最終映像還混入 @img/sharp-darwin-x64 等 macOS 套件。Dockerfile 的 deps 階段自己 npm ci、builder 階段自己 next build。
  it('frontend/.dockerignore 排除 node_modules、.next 與 .env*：主機的依賴、建置產物與環境檔不能被送進建置上下文（DEF-498）', () => {
    const path = fileURLToPath(new URL('../../.dockerignore', import.meta.url))
    const entries = (existsSync(path) ? readFileSync(path, 'utf-8') : '')
      .split('\n')
      .map((line) => line.trim())
      .filter((line) => line !== '' && !line.startsWith('#'))
    for (const required of ['node_modules', '.next', '.env*']) {
      expect(entries, `frontend/.dockerignore 少了 ${required}：${required} 會被整包送進 docker 建置上下文`).toContain(required)
    }
  })

  it('偵測器只認真的 COPY 指令：註解掉的、複製別的目錄的、沒有 --from 的都不算', () => {
    const copy = 'COPY --from=builder --chown=nextjs:nodejs /app/.next/standalone ./'
    expect(copiesStandaloneOutput(copy)).toBe(true)
    expect(copiesStandaloneOutput(`# ${copy}`)).toBe(false)
    expect(copiesStandaloneOutput('COPY --from=builder /app/.next/static ./.next/static')).toBe(false)
    expect(copiesStandaloneOutput('COPY .next/standalone ./')).toBe(false)
  })
})
