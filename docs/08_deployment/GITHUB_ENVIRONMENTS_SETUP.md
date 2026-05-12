# GitHub Environments 設定指南 / GitHub Environments Setup Guide

**文檔類型**: 部署設定指南
**適用版本**: v0.09+
**更新日期**: 2026-05-12

---

## 概述

GitHub Environments 提供部署目標的環境保護規則和控制機制。本專案使用 Environments 來管理 Staging 和 Production 部署。

## 環境架構

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│   develop   │────▶│    main     │────▶│  staging   │
└─────────────┘     └─────────────┘     └─────────────┘
                           │                   │
                           │                   ▼
                    ┌─────────────┐     ┌─────────────┐
                    │  (自動)     │     │ production │
                    └─────────────┘     └─────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │  (手動)    │
                    └─────────────┘
```

## GitHub Environments 設定步驟

### 階段 1: 存取 Environments 設定

1. 前往 Repository 頁面
2. 點擊 **Settings** 分頁
3. 在左側選單中找到 **Environments**
4. 點擊 **New environment** 建立新環境

### 階段 2: 建立 Staging 環境

1. 點擊 **New environment**
2. 輸入環境名稱: `staging`
3. 設定環境保護規則（可選）:
   - **Required reviewers**: 設定需要審核的成員
   - **Wait timer**: 部署前等待時間
   - **Deployment branch rule**: 限制可部署的分支

### 階段 3: 建立 Production 環境

1. 點擊 **New environment**
2. 輸入環境名稱: `production`
3. 建議啟用保護規則:
   - **Required reviewers**: 至少 1 人審核
   - **Wait timer**: 建議 30 分鐘

### 階段 4: 設定 Secrets（可選）

若需要 SSH 部署，可在每個環境下設定專屬 secrets：

1. 進入環境設定頁面
2. 滾動到 **Environment secrets** 區塊
3. 點擊 **Add secret**
4. 輸入:
   - Name: `SSH_KEY`
   - Value: 你的 SSH private key 內容

### 階段 5: 更新 Workflow 使用 Environment Secrets

修改後的 workflow 會根據環境自動讀取對應的 secrets：

```yaml
deploy-staging:
  environment:
    name: staging
    url: https://staging.example.com
  # SSH_KEY 會自動從 staging 環境的 secrets 取得
```

## 保護規則建議

### Staging 環境
| 規則 | 建議設定 | 原因 |
|------|---------|------|
| Required reviewers | 0-1 人 | 快速迭代開發 |
| Wait timer | 0 分鐘 | 加速開發流程 |
| Branch rule | main | 自動部署 |

### Production 環境
| 規則 | 建議設定 | 原因 |
|------|---------|------|
| Required reviewers | 1-2 人 | 生產環境需要審核 |
| Wait timer | 30 分鐘 | 提供回滾窗口 |
| Branch rule | tags | 僅從標籤部署 |

## 故障排除

### 問題: "Environment not found"

**原因**: Repository 未設定該環境名稱

**解決方案**:
1. 前往 Settings → Environments
2. 建立對應名稱的環境

### 問題: "Secrets not available"

**原因**: Secrets 設定在錯誤的位置

**解決方案**:
- Repository secrets: 適用於所有 workflow
- Environment secrets: 僅適用於該環境的 workflow

確認 secrets 位置與 workflow 的 `environment.name` 匹配。

### 問題: Required reviewers 阻擋部署

**原因**: 需要指定審核者才能部署

**解決方案**:
1. 聯繫設定的審核者
2. 或修改環境保護規則暫停審核要求

## 相關檔案

| 檔案 | 說明 |
|------|------|
| `.github/workflows/ci.yml` | CI/CD Pipeline，包含 environment 設定 |
| `docs/08_deployment/RELEASE_NOTES_v*.md` | 發布記錄 |

## 延伸閱讀

- [GitHub Environments 文件](https://docs.github.com/en/actions/deployment/targeting-different-environments/using-environments-for-deployment)
- [環境保護規則](https://docs.github.com/en/actions/deployment/targeting-different-environments/environment-protection-rules)
