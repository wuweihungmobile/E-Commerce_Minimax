# DevOps CI/CD 快速啟動指令集
# DevOps CI/CD Quick Start Prompts

**版本**: v0.09
**適用情境**: DevOps - CI/CD 與自動化部署
**最後更新**: 2026-02-17

---

## 🚀 一鍵啟動指令

### 標準啟動

```
我需要建立 CI/CD Pipeline 和自動化部署。

專案資訊：
- 專案類型：[Web/Mobile/Backend]
- 技術棧：[語言/框架]
- 版本控制：[Git/GitHub/GitLab]
- 目標平台：[AWS/GCP/Azure/On-Premise]

請載入 AISDLC_INIT.md，啟動 devops-setup-flow，執行 DevOps SOP。
```

### 快速 CI Pipeline 設定

```
我只需要快速設定 CI Pipeline（自動化測試）。

專案資訊：
- CI 工具：[GitHub Actions/GitLab CI/Jenkins]
- 測試類型：[Unit Test/Integration Test/E2E Test]
- 程式語言：[語言]

請 DevOps-Engineer 協助設定基礎 CI Pipeline。
```

---

## 📊 階段推進指令

### 階段 1：環境規劃與需求分析

```
請執行 DevOps SOP 階段 1：環境規劃與需求分析。

需求分析：
- 環境數量：[Dev/Staging/Production]
- 部署頻率：[每日/每週/On-demand]
- 團隊規模：[X 人]
- 預算：[有限/充裕]
- 合規要求：[SOC2/ISO27001/等]

產出：DevOps_Requirements_Analysis
```

### 階段 2：CI Pipeline 設計

```
環境規劃已完成，請進入階段 2：CI Pipeline 設計。

請 DevOps-Engineer 設計：
- Build Stage（編譯/打包）
- Test Stage（單元測試/整合測試）
- Code Quality Stage（Linting/SonarQube）
- Security Scan Stage（依賴漏洞掃描）
- Artifact Stage（產出物存儲）

CI 工具：[GitHub Actions/GitLab CI/Jenkins/CircleCI]
產出：CI_CD_Pipeline_Design
```

### 階段 3：CD Pipeline 設計

```
CI Pipeline 已設計，請進入階段 3：CD Pipeline 設計。

請 DevOps-Engineer 設計：
- Deployment Strategy（藍綠/金絲雀/滾動更新）
- Environment Promotion（Dev → Staging → Prod）
- Approval Gates（人工審批點）
- Rollback Mechanism（自動/手動回滾）

產出：Deployment_Strategy
```

### 階段 4：容器化與編排

```
CD Pipeline 已設計，請進入階段 4：容器化。

請準備：
- Dockerfile 編寫（最佳實踐/多階段構建）
- Docker Compose（本地開發環境）
- Kubernetes Manifests（若使用 K8s）
  * Deployment
  * Service
  * Ingress
  * ConfigMap/Secret

產出：Container_Configuration
```

### 階段 5：基礎設施即代碼（IaC）

```
容器化已完成，請進入階段 5：基礎設施即代碼。

請使用 IaC 工具：
- Terraform（AWS/GCP/Azure 資源）
- CloudFormation（AWS）
- Ansible（配置管理）

定義：
- 網路架構（VPC/Subnet/Security Group）
- 計算資源（EC2/ECS/EKS/GKE）
- 資料庫（RDS/Cloud SQL）
- 儲存（S3/GCS）

產出：IaC_Templates
```

### 階段 6：監控與告警設定

```
基礎設施已準備，請進入階段 6：監控與告警設定。

請 DevOps-Engineer 設定：
- Application Monitoring（APM：New Relic/Datadog）
- Infrastructure Monitoring（CPU/Memory/Disk）
- Log Aggregation（ELK/Splunk/CloudWatch Logs）
- Alerting（PagerDuty/Slack/Email）
- Dashboard（Grafana/Kibana）

產出：Monitoring_Setup
```

### 階段 7：災難恢復與備份

```
監控已設定，請進入階段 7：災難恢復規劃。

請規劃：
- Backup Strategy（資料庫/檔案備份）
- Recovery Point Objective (RPO)：[X 小時]
- Recovery Time Objective (RTO)：[Y 分鐘]
- Disaster Recovery Plan（區域性故障/完全災難）
- 定期演練計畫

產出：Disaster_Recovery_Plan
```

---

## 🔄 常見變體指令

### 變體 1：從零開始（Greenfield DevOps）

```
全新專案，完全沒有 DevOps 基礎設施。

請從頭建立：
- Git Repository 設定
- CI/CD Pipeline（GitHub Actions）
- Docker 容器化
- 部署到 Cloud（AWS/GCP/Azure）
- 監控與告警

專案：[專案名稱]
```

### 變體 2：改善既有 CI/CD（Brownfield DevOps）

```
既有專案有 CI/CD，但需要改善。

目前狀況：
- CI 工具：[工具名稱]
- 問題：[慢/不穩定/缺少測試]
- 改善目標：[加速/提升可靠性]

請診斷並改善既有 Pipeline。
```

### 變體 3：多環境管理

```
我需要管理多個環境（Dev/Staging/Prod）。

需求：
- 環境隔離（獨立資源/不同配置）
- 環境一致性（Infrastructure as Code）
- 環境升級流程（Dev → Staging → Prod）

請使用 IaC 和 Environment Variables 管理。
```

### 變體 4：Kubernetes 部署

```
我要部署到 Kubernetes。

需求：
- K8s 平台：[EKS/GKE/AKS/自建]
- 應用類型：[Stateless/Stateful]
- 流量管理：[Ingress/Service Mesh]

請設計：
- K8s Manifests（Deployment/Service/Ingress）
- Helm Charts（可重用）
- CI/CD 整合（kubectl/helm deploy）
```

### 變體 5：Serverless 部署

```
我要使用 Serverless 架構。

平台：
- AWS Lambda / Google Cloud Functions / Azure Functions
- API Gateway
- Managed Services（DynamoDB/S3）

請設計：
- Serverless Framework / SAM Template
- CI/CD for Serverless
- Cold Start 優化
```

---

## 🆘 疑難排解指令

### 問題 1：CI Pipeline 太慢

```
CI Pipeline 執行時間太長（X 分鐘）。

瓶頸：
- Build：[Y 分鐘]
- Test：[Z 分鐘]

請優化：
- 並行化（Parallel Jobs）
- 快取（Dependencies/Build Artifacts）
- 分層構建（Docker Multi-stage Build）
- 選擇性測試（只跑變更相關的測試）
```

### 問題 2：部署失敗頻繁

```
部署經常失敗，影響開發效率。

常見失敗原因：
- 環境問題（環境變數/依賴缺失）
- 測試不穩定（Flaky Tests）
- 資源不足（Memory/CPU）

請診斷並改善：
- 失敗原因分析
- 增加檢查點（Pre-deploy Checks）
- 改善錯誤訊息
- 自動 Rollback
```

### 問題 3：環境不一致

```
不同環境出現不一致問題（Dev 正常，Prod 失敗）。

請解決：
- 使用 Docker（確保環境一致）
- Infrastructure as Code（Terraform）
- Configuration Management（環境變數/ConfigMap）
- Smoke Test（部署後驗證）
```

### 問題 4：Secrets 管理

```
如何安全管理 Secrets（API Key/Database Password）？

請使用：
- CI/CD Secrets（GitHub Secrets/GitLab Variables）
- Secret Management（AWS Secrets Manager/HashiCorp Vault）
- 避免硬編碼（.env 不進版控）
- 最小權限原則
```

---

## 🎓 進階使用技巧

### 技巧 1：GitOps 模式

```
我想使用 GitOps 管理部署。

請設定：
- Git as Single Source of Truth
- ArgoCD / Flux（自動同步）
- 宣告式配置（Kubernetes Manifests）
- Pull-based Deployment
```

### 技巧 2：藍綠部署（Blue-Green Deployment）

```
我需要零停機時間部署。

請實現藍綠部署：
- 兩套環境（Blue/Green）
- Load Balancer 切換
- 快速回滾（切回 Blue）
- Health Check（確保 Green 健康）
```

### 技巧 3：金絲雀發布（Canary Release）

```
我想逐步放量新版本，降低風險。

請實現金絲雀發布：
- 流量分配（10% → 50% → 100%）
- 監控關鍵指標（錯誤率/延遲）
- 自動回滾（指標異常時）
- 使用工具（Istio/Flagger/AWS App Mesh）
```

### 技巧 4：成本優化

```
Cloud 成本太高，需要優化。

請優化：
- Right-sizing（調整 Instance 大小）
- Auto-scaling（自動擴縮容）
- Spot Instances / Preemptible VMs（節省成本）
- 資源清理（刪除未使用資源）
- Cost Monitoring（設定預算告警）
```

---

## 📚 參考資源

### 相關文檔
- [DevOps SOP](../../scenarios/devops/SOP.md)
- [AISDLC_INIT.md](../../AISDLC_INIT.md)

### 相關 Workflows
- [devops-setup-flow.md](../../workflow/scenario-specific/devops-setup-flow.md)

### 相關 Agents
- [devops-engineer-zh.yaml](../../agent/specialized/devops-engineer-zh.yaml) - DevOps Engineer
- [sd-architect-zh.yaml](../../agent/core/05.sd-architect-zh.yaml) - Marcus

### 文檔模板
- [CICD_Pipeline_Template.md](../../docs_template/scenario_specific/devops/CICD_Pipeline_Template.md)
- [DevOps 文檔模板目錄](../../docs_template/scenario_specific/devops/) 🚧 (更多模板 v0.09+ 預留)

---

**版本**: v0.09
**維護者**: AISDLC Framework Team
**最後更新**: 2026-02-17
