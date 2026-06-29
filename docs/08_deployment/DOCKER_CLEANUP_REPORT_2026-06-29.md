# Docker 部署/下載最佳化清理報告

**日期**：2026-06-29
**主題**：Docker「該部署的才部署、該下載的才下載」
**執行者**：Claude Code（AISDLC v0.09）+ devops-docker skill
**依據政策**：[DOCKER_POLICY.md](DOCKER_POLICY.md)

---

## 1. 問題定義

> 「Docker 該部署的才部署，該下載的才下載。」

調查後鎖定兩類問題：

| 類別 | 問題 | 影響 |
|------|------|------|
| **不該下載卻仍能觸發下載** | Local LLM 服務已於 2026-06-24 從 compose 移除，但下載/部署工具鏈（Makefile target、下載腳本、profile 指令、文件）全部殘留 | 使用者執行 `make download-llm-model` / `make up-mock-with-llm` 仍會嘗試下載 1~4.5GB 模型或啟動不存在的服務 |
| **不該部署卻每次自動部署** | MinIO 物件儲存在 `docker-compose.override.yml`，每次 `docker compose up` 都自動部署+下載 | 不需要媒體功能的開發者仍被迫下載/啟動 MinIO |
| **規則自相矛盾** | CLAUDE.md Docker 規則仍禁止「移除 local-llm profile」「修改 llama.cpp tag」，與 DOCKER_POLICY.md「已移除」相牴觸 | 誤導未來的 AI 與開發者 |

---

## 2. 調查結論（哪些該保留）

| 服務 | 判定 | 證據 |
|------|------|------|
| postgres / redis | ✅ 核心，保留並釘定版本 | prod/CI/dev 全用 |
| **MinIO** | ✅ **確實使用**，改為按需啟動 | `io.minio` 依賴、`StorageService.java`、application.yml `bucket=media` |
| Mock Server (Mockoon) | ✅ 已是按需（僅 `-f docker-compose.mock.yml` 顯式啟動） | 無需變更 |
| **Local LLM** | 🔴 已下線，工具鏈全面清理 | 代碼庫無任何 LLM 呼叫 |

---

## 3. 執行項目（逐項確認）

### 階段一：清理 Local LLM 殘留工具鏈
- [x] `Makefile`：移除 `up-mock-with-llm`、`download-llm-model`、`download-llm-7b` 三個失效 target，修正 `up-mock` 註解
- [x] 刪除孤兒下載腳本 `scripts/download-llm-model.sh`
- [x] 刪除過時文件 `docs/08_deployment/LOCAL_LLM_SETUP.md`
- [x] `mocks/README.md`：移除 LLM 啟動說明與 `LOCAL_LLM_SETUP.md` 連結
- [x] `LOCAL_CI_VALIDATION.md`：移除 4.2 Local LLM 章節與 `--profile with-llm`、llama.cpp 引用
- [x] `LOCAL_CI_VALIDATION_CHECKLIST.md`：移除 0.6 / 4.6 / 4.7 三個 LLM 檢查項

### 階段二：MinIO 改為按需啟動（profile）
- [x] 調用 **devops-docker skill** 指導 docker-compose profiles 配置
- [x] `docker-compose.override.yml`：MinIO 加 `profiles: ["storage"]`
- [x] `Makefile`：新增 `up-storage` target（`docker compose --profile storage up -d`），並於 `up` 提示需 MinIO 時改用

### 階段三：修正矛盾規則與文件對齊
- [x] `CLAUDE.md`：修正兩條過時 LLM Docker 規則，並將「profile 保護資源」精神轉移至 MinIO
- [x] `DOCKER_POLICY.md`：新增「按需啟動原則」、更新 Volume 表與服務架構圖

### 階段四：驗證
- [x] `docker compose config --services`（預設）→ `backend frontend postgres redis`（**不含 minio** ✅）
- [x] `docker compose --profile storage config --services` → 額外含 **minio** ✅
- [x] 四個 compose 組合（prod / dev / CI / mock）`config --quiet` 全部通過
- [x] `make help` 正常顯示 `up-storage`，Makefile 無任何 LLM 引用
- [x] 全庫掃描：無有效 LLM 工具鏈殘留（僅剩歷史記錄與廢止說明）

---

## 4. 變更後的部署/下載行為

| 指令 | 啟動的服務 | 下載的 image |
|------|-----------|-------------|
| `make up` / `docker compose up` | frontend, backend, postgres, redis | 不含 MinIO |
| `make up-storage` | 上述 + **minio** | 需要時才下載 MinIO |
| `make up-mock` | 主服務 + mock-server | mockoon/cli |
| `make up-ci` | 主服務（CI profile） | postgres, redis |
| ~~`make download-llm-model`~~ | 已移除 | 不再下載任何 GGUF 模型 |

---

## 5. 刻意保留項目（非殘留）

- 各文件中以 `~~刪除線~~` / `ℹ️ 已移除` / `已廢止` 標記的 LLM 說明 → 保留作為決策歷史
- `RELEASE_NOTES_v2026.07.03-01.md` 中的 `profiles: ["with-llm"]` → 歷史發布記錄，不竄改

---

## 6. 後續提醒

- 本次變更涉及 Docker 設定，依 CLAUDE.md CI 規則，commit/push 時 pre-push hook 會執行完整本機 act CI 驗證。
- 嚴禁使用 `--no-verify`。
