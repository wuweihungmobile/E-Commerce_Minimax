# SSH pre-push Keepalive 優化 / SSH pre-push Keepalive

> **對應**: Sprint 25 US-005（Buffer-A）/ AI-804
> **建立日期**: 2026-06-30
> **狀態**: ✅ 已實作（repo-local `core.sshCommand`）
> **目錄歸屬**: docs/08_deployment（CI/CD / DevOps）

---

## 1. 問題（exit 141 / SIGPIPE）

push 時 pre-push hook 會在推送前批次執行完整 `act` CI（10–20 分鐘）。過程中曾發生 `git push` 以 **exit 141（SIGPIPE）** 失敗。

### 根因分析

`git push` 的執行順序：

```
git push
  → 連線 remote（SSH）取得 remote refs（ls-remote）   ← SSH 連線於此開啟
  → 執行 pre-push hook（act CI，約 10–20 分鐘）          ← SSH 連線閒置
  → hook 通過後，git 協商並傳送 pack data               ← 連線已被伺服器關閉 → 寫入已關閉的 pipe → SIGPIPE(141)
```

關鍵：SSH 連線在 hook 執行**前**就已建立，hook 跑 act 的長時間內該連線**閒置**，GitHub 端會關閉閒置連線；hook 結束後 git 要傳資料時連線已死，導致 **SIGPIPE（exit 141）**。

### 現況確認（AC-005-1）

| 項目 | 結果 |
|------|------|
| remote 協定 | SSH（`git@github.com:wuweihungmobile/E-Commerce_Minimax.git`） |
| `~/.ssh/config` | **不存在**（無 ServerAliveInterval / ControlMaster 設定） |
| `core.sshCommand` | 原本未設定 |
| pre-push hook | 推送前批次跑完整 act（`act -W .github/workflows/act-compat.yml`） |

---

## 2. 方案評估

### 方案 A：SSH Keepalive（ServerAliveInterval）— ✅ 採用

於 SSH 連線閒置時，client 每隔 N 秒送 keepalive 封包，使連線在 act 長時間執行期間維持活著，不被伺服器關閉。

- **優點**：最小侵入、可逆、不改 remote 協定、不需 PAT；直接消除 SIGPIPE 根因
- **實作**：repo-local `git config core.sshCommand`（不動使用者全域 `~/.ssh/config`）

### 方案 B：HTTPS push（AC-005-2 評估）— 不採用

將 remote 改為 `https://github.com/...` + credential helper / PAT。

- **缺點**：需建立並保管 Personal Access Token、改 remote 協定（影響既有 SSH 金鑰流程）；HTTPS 長操作同樣可能有閒置中斷問題，未根治
- **結論**：相較方案 A 侵入性高、效益相當，**不採用**；保留為備案

### 方案 C：ControlMaster 連線複用 — 不需要

ControlMaster/ControlPersist 適合「短時間內多次連線複用」，本情境是「單一連線長時間閒置」，keepalive 已足夠；不額外引入。

---

## 3. 實作（AC-005-3）

採 **repo-local** 設定（寫入 `.git/config`，不影響使用者其他專案）：

```bash
git config --local core.sshCommand "ssh -o ServerAliveInterval=60 -o ServerAliveCountMax=30"
```

- `ServerAliveInterval=60`：閒置時每 60 秒送一次 keepalive
- `ServerAliveCountMax=30`：最多容忍 30 次無回應 → 約 **30 分鐘**容忍度，足以覆蓋 act CI 執行時間

### 驗證

```
$ git config --local core.sshCommand
ssh -o ServerAliveInterval=60 -o ServerAliveCountMax=30

$ GIT_SSH_COMMAND="ssh -o ServerAliveInterval=60 -o ServerAliveCountMax=30" ssh -T git@github.com
Hi wuweihungmobile! You've successfully authenticated, ...   # ✅ keepalive 參數下 SSH 正常
```

---

## 4. 可重現性 / 全域替代設定

`.git/config` 為本機設定、不隨版控散布。若要對所有專案生效，可改於使用者全域 `~/.ssh/config` 加入：

```sshconfig
Host github.com
    HostName github.com
    User git
    ServerAliveInterval 60
    ServerAliveCountMax 30
```

> 本 Sprint 採 repo-local 方案（範圍最小、可逆）。如需納入 `make setup` 自動化，列為後續 DevOps 改善。

---

## 5. 注意事項

- 本優化**不改變** pre-push 仍會跑完整 act CI 的守門機制（CLAUDE.md 規範不變）。
- 🔴 **嚴禁** `git push --no-verify` 繞過 pre-push。
- 若仍偶發中斷，可調高 `ServerAliveCountMax` 或檢視 act 執行時間是否異常拉長。
