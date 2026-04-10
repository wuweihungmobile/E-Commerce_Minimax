# Security & Compliance 情境 Prompts
# 安全評估與合規檢查快速啟動指南

**情境代碼**: `security` | `security-compliance`
**版本**: v0.03
**最後更新**: 2026-02-23

---

## 🚀 一鍵啟動指令

### 基本啟動（適合初次使用）

```
請載入 AISDLC_INIT.md (v0.09)

我需要進行安全評估和合規檢查：
- 專案類型: [Web App / Mobile App / API / 混合]
- 資料類型: [個人資料 / 金融資料 / 健康資料 / 其他]
- 合規要求: [GDPR / HIPAA / PCI-DSS / ISO 27001 / SOC 2 / 其他]
- 評估範圍: [完整系統 / 特定模組 / 新增功能]

請使用 Security & Compliance 情境幫我進行安全評估。
```

### 快速啟動（已熟悉流程）

```
AISDLC v0.09 security

我需要對[專案名稱]進行安全評估和[GDPR/HIPAA/PCI-DSS]合規檢查。
專案是[Web/Mobile/API]應用，處理[個資/金融/健康]資料。
```

### 專家模式（最精簡）

```
AISDLC security-compliance
專案: [專案名稱]
合規: GDPR + PCI-DSS
範圍: 完整系統評估
```

---

## 📋 階段推進指令

### Phase 1: 安全評估與合規分析

#### 步驟 1.1: 情境確認與範圍界定
```
請確認以下安全評估範圍：

1. 系統類型: [選擇]
   - [ ] Web 應用
   - [ ] Mobile App (iOS/Android)
   - [ ] API 服務
   - [ ] 混合系統

2. 資料敏感度: [選擇]
   - [ ] 個人身份資訊 (PII)
   - [ ] 金融交易資料
   - [ ] 健康醫療資料 (PHI/ePHI)
   - [ ] 商業機密

3. 適用合規標準: [選擇]
   - [ ] GDPR (歐盟資料保護)
   - [ ] HIPAA (美國醫療)
   - [ ] PCI-DSS (信用卡交易)
   - [ ] ISO 27001 (資訊安全管理)
   - [ ] SOC 2 (服務組織控制)
   - [ ] 其他: ___________

4. 評估範圍:
   - [ ] 完整系統 (所有模組)
   - [ ] 特定模組: [列出模組名稱]
   - [ ] 新增功能: [功能描述]

5. 第三方整合: [列出所有外部服務]
   - 支付服務: ___________
   - 雲端儲存: ___________
   - 身份驗證: ___________
   - 其他: ___________

請開始安全評估。
```

#### 步驟 1.2: 威脅建模
```
請進行 STRIDE 威脅建模分析：

系統概要:
- 用戶類型: [管理員/一般用戶/訪客]
- 資料流向: [描述主要資料流]
- 外部整合: [第三方服務]
- 信任邊界: [描述系統邊界]

請識別潛在威脅並評估風險等級。
```

#### 步驟 1.3: 漏洞掃描（既有系統）
```
請進行安全漏洞掃描：

系統資訊:
- 程式語言: [Node.js / Python / Java / .NET / 其他]
- 框架: [Express / Django / Spring / 其他]
- 依賴套件數量: [約略數量]
- 代碼庫位置: [GitHub / GitLab / 本地]

掃描範圍:
- [ ] 靜態代碼分析 (SAST)
- [ ] 依賴套件漏洞掃描
- [ ] 配置安全檢查
- [ ] 敏感資料掃描 (硬編碼密碼、API Key)

請執行掃描並報告發現。
```

#### 步驟 1.4: 合規差距分析
```
請進行 [GDPR/HIPAA/PCI-DSS] 合規差距分析：

業務背景:
- 行業: [金融/醫療/電商/SaaS/其他]
- 用戶地理位置: [歐盟/美國/全球]
- 資料處理量: [每日處理多少用戶資料]
- 現有合規措施: [已實施的控制措施]

請檢查合規狀態並識別差距。
```

---

### Phase 2: 安全需求與架構設計

#### 步驟 2.1: 制定安全需求
```
基於威脅模型和合規要求，請制定安全需求文件：

重點關注:
- [ ] 身份驗證與授權 (Authentication & Authorization)
- [ ] 資料加密 (靜態 + 傳輸)
- [ ] 輸入驗證與輸出編碼
- [ ] 會話管理
- [ ] 稽核日誌
- [ ] 錯誤處理

請產出 Security Requirements Document (SRD-Security)。
```

#### 步驟 2.2: 設計安全架構
```
請設計 Defense in Depth 安全架構：

現有架構: [簡述現有架構或上傳架構圖]

需要設計:
- [ ] 網路層安全 (Firewall, WAF, DDoS Protection)
- [ ] 應用層安全 (Input Validation, CSRF, XSS Prevention)
- [ ] 資料層安全 (Encryption at Rest, Key Management)
- [ ] 身份層安全 (SSO, MFA, RBAC)

請產出 Security Architecture Diagram。
```

#### 步驟 2.3: 選擇安全工具
```
請推薦適合的安全工具：

預算: [有限 / 中等 / 充足]
團隊技能: [初階 / 中階 / 高階]
雲端平台: [AWS / Azure / GCP / 混合 / 本地]

需要的工具類型:
- [ ] SAST/DAST 工具
- [ ] Secrets Management (Vault / AWS Secrets Manager)
- [ ] WAF 服務
- [ ] SIEM/日誌分析

請提供工具推薦和比較。
```

---

### Phase 3: 安全實施與修復

#### 步驟 3.1: 安全加固實施
```
請協助實施安全加固措施：

優先級 P0 (Critical) 項目:
1. [列出 P0 安全需求]
2. [列出 P0 安全需求]

請提供實施指南和代碼範例。
```

#### 步驟 3.2: 合規性實施（GDPR）
```
請協助實施 GDPR 合規措施：

需要實施:
- [ ] 隱私政策頁面
- [ ] Cookie 同意機制
- [ ] 資料主體存取請求 (DSAR) API
- [ ] 帳號刪除功能
- [ ] 個資下載功能 (JSON/CSV)
- [ ] 資料處理記錄 (ROPA)

請提供實施方案和範例代碼。
```

#### 步驟 3.2b: 合規性實施（HIPAA）
```
請協助實施 HIPAA 合規措施：

需要實施:
- [ ] ePHI 加密儲存 (AES-256)
- [ ] 傳輸加密 (TLS 1.3)
- [ ] 存取控制 (Role-Based)
- [ ] 稽核日誌 (所有 ePHI 存取)
- [ ] 資料備份與災難復原
- [ ] Business Associate Agreements (BAA)

請提供實施方案。
```

#### 步驟 3.2c: 合規性實施（PCI-DSS）
```
請協助實施 PCI-DSS 合規措施：

需要實施:
- [ ] 不儲存完整卡號 (Tokenization)
- [ ] CVV 不可儲存
- [ ] 強加密傳輸 (TLS 1.2+)
- [ ] 強密碼政策 + MFA
- [ ] 網路分段 (DMZ)
- [ ] 季度漏洞掃描
- [ ] 年度滲透測試

請提供實施方案。
```

---

### Phase 4: 安全測試與驗證

#### 步驟 4.1: OWASP Top 10 測試
```
請進行 OWASP Top 10 (2021) 安全測試：

測試範圍: [列出要測試的端點或功能]

重點測試項目:
- [ ] A01: Broken Access Control
- [ ] A02: Cryptographic Failures
- [ ] A03: Injection (SQL, XSS, etc.)
- [ ] A04: Insecure Design
- [ ] A05: Security Misconfiguration
- [ ] A06: Vulnerable and Outdated Components
- [ ] A07: Authentication Failures
- [ ] A08: Software and Data Integrity Failures
- [ ] A09: Logging and Monitoring Failures
- [ ] A10: Server-Side Request Forgery (SSRF)

請執行測試並報告發現。
```

#### 步驟 4.2: 漏洞修復與復測
```
針對發現的漏洞進行修復：

高危漏洞清單:
1. [漏洞描述] - [風險等級]
2. [漏洞描述] - [風險等級]

請提供修復方案並執行復測驗證。
```

---

### Phase 5: 文檔與交付

#### 步驟 5.1: 產出安全文檔
```
請產出完整的安全與合規文檔包：

需要的文檔:
- [ ] Security Assessment Report（安全評估報告）
- [ ] Threat Model（威脅模型）
- [ ] Security Requirements Document（安全需求文件）
- [ ] Security Test Report（安全測試報告）
- [ ] Vulnerability Scan Report（漏洞掃描報告）
- [ ] Remediation Plan（修復計畫）
- [ ] Compliance Report（合規報告）
- [ ] Privacy Policy（隱私政策）
- [ ] Incident Response Plan（事件應變計畫）

請產出這些文檔。
```

#### 步驟 5.2: 安全知識轉移
```
請產出安全培訓材料：

目標受眾: [開發團隊 / 運維團隊 / 全體員工]

培訓主題:
- [ ] Secure Coding Practices（安全編碼實踐）
- [ ] OWASP Top 10 Awareness（OWASP 風險意識）
- [ ] Data Privacy Best Practices（資料隱私最佳實踐）
- [ ] Incident Response Procedures（事件應變程序）
- [ ] Compliance Requirements（合規要求）

請產出培訓材料。
```

---

## 🎯 常見變體

### 變體 1: 新專案安全架構設計
```
AISDLC security

我正在開發新的[專案類型]，需要從頭設計安全架構：
- 專案: [專案名稱和簡介]
- 敏感度: [處理的資料類型]
- 合規要求: [適用的法規]
- 技術棧: [前端/後端技術]

請幫我設計 Security by Design 架構。
```

### 變體 2: 既有系統安全評估
```
AISDLC security

我們有一個既有的[系統類型]，需要進行安全評估：
- 系統: [系統簡介]
- 代碼庫: [GitHub URL 或代碼位置]
- 已知問題: [如有已知安全問題請列出]
- 評估目的: [客戶稽核 / 內部審查 / 認證準備]

請進行全面安全評估。
```

### 變體 3: 滲透測試準備
```
AISDLC security

我們即將進行滲透測試，需要事前準備：
- 測試範圍: [列出要測試的系統]
- 測試時間: [預計測試時間]
- 測試者: [內部 / 外部 / Bug Bounty]

請幫我進行預評估並修復明顯漏洞。
```

### 變體 4: 安全漏洞緊急修復
```
AISDLC security

我們發現了安全漏洞需要緊急修復：
- 漏洞類型: [SQL Injection / XSS / 其他]
- 影響範圍: [描述影響]
- 嚴重程度: [Critical / High / Medium]
- 發現來源: [安全掃描 / 滲透測試 / Bug Report]

請提供修復方案並協助實施。
```

### 變體 5: GDPR 合規準備
```
AISDLC security-gdpr

我們的 SaaS 服務需要符合 GDPR 合規：
- 服務: [服務簡介]
- 用戶: [歐盟用戶占比]
- 資料: [處理的個人資料類型]
- 法律基礎: [同意 / 合約履行 / 正當利益]

請進行 GDPR 合規差距分析並提供實施計畫。
```

### 變體 6: HIPAA 合規準備
```
AISDLC security-hipaa

我們的醫療應用需要符合 HIPAA 合規：
- 應用: [應用簡介]
- 角色: [Covered Entity / Business Associate]
- ePHI: [處理的健康資料類型]
- 雲端: [AWS / Azure / GCP]

請進行 HIPAA 合規評估並實施必要控制。
```

### 變體 7: PCI-DSS 認證準備
```
AISDLC security-pci

我們的電商平台需要 PCI-DSS 認證：
- 平台: [平台簡介]
- 交易量: [年交易量級別]
- 支付方式: [直接處理 / 透過 Gateway]
- Merchant Level: [Level 1 / 2 / 3 / 4]

請協助準備 PCI-DSS 認證。
```

### 變體 8: SOC 2 Type II 認證
```
AISDLC security-soc2

我們的 SaaS 服務需要 SOC 2 Type II 認證：
- 服務: [服務簡介]
- TSC: [Security + Availability + Confidentiality]
- 時程: [預計認證時間]
- 現有控制: [已實施的控制措施]

請協助準備 SOC 2 審計。
```

---

## 🔧 進階技巧

### 技巧 1: 並行處理多個合規標準
```
AISDLC security

我們需要同時符合多個合規標準：
- GDPR (歐盟用戶)
- HIPAA (美國醫療資料)
- ISO 27001 (資訊安全認證)

請找出共同控制需求並優化實施順序。
```

### 技巧 2: 漸進式安全改善
```
AISDLC security

我們資源有限，無法一次性完成所有安全改善：
- 預算: [有限預算]
- 人力: [1-2 人]
- 時程: [3-6 個月]

請制定漸進式安全改善路線圖，優先處理最高風險項目。
```

### 技巧 3: 自動化安全測試
```
AISDLC security

請協助建立自動化安全測試 Pipeline：
- CI/CD: [Jenkins / GitHub Actions / GitLab CI]
- 代碼庫: [GitHub / GitLab]
- 測試工具: [推薦或現有工具]

請設計並實施自動化安全掃描流程。
```

### 技巧 4: 安全監控與告警
```
AISDLC security

請協助建立安全監控系統：
- 監控目標: [列出要監控的安全事件]
- SIEM: [Splunk / ELK / 其他]
- 告警渠道: [Email / Slack / PagerDuty]

請設計監控策略和告警規則。
```

---

## 🚨 疑難排解

### 問題 1: 不確定適用哪些合規標準
```
我不確定我的專案需要符合哪些法規：
- 行業: [您的行業]
- 資料: [處理的資料類型]
- 用戶: [用戶地理分布]
- 客戶: [B2B / B2C / B2G]

請幫我識別適用的合規標準。
```

### 問題 2: 安全需求與業務需求衝突
```
安全需求與業務需求有衝突：
- 業務需求: [描述業務需求]
- 安全考量: [描述安全疑慮]
- 風險: [描述潛在風險]

請提供平衡安全與可用性的方案。
```

### 問題 3: 漏洞修復成本過高
```
發現的漏洞修復成本太高：
- 漏洞: [漏洞描述]
- 原因: [為何成本高]
- 風險: [實際風險評估]

請提供替代方案或補償控制。
```

### 問題 4: 合規檢查項目太多無法完成
```
合規檢查項目太多，無法在期限內完成：
- 標準: [GDPR / HIPAA / 其他]
- 期限: [剩餘時間]
- 資源: [可用資源]

請協助優先級排序並識別 MVP 合規範圍。
```

---

## 💡 最佳實踐建議

### 建議 1: 安全左移（Shift Left）
從開發早期就整合安全考量，而非最後才做安全測試。

```
在需求階段就載入 AISDLC Security：

AISDLC security

專案: [專案簡介]
階段: 需求分析
目標: 在設計階段就識別安全需求

請進行威脅建模並制定安全需求。
```

### 建議 2: 持續安全監控
不要只做一次性評估，建立持續監控機制。

```
AISDLC security

請協助建立持續安全監控機制：
- 自動化漏洞掃描 (每日)
- 依賴套件更新監控
- 安全日誌分析
- 異常行為偵測

請設計並實施持續監控策略。
```

### 建議 3: 安全意識培訓
定期對團隊進行安全培訓，提升整體安全水平。

```
AISDLC security

請設計季度安全培訓計畫：
- 受眾: 開發團隊 (10-20 人)
- 時長: 2-4 小時/季
- 形式: 線上 + 實作演練
- 主題: OWASP Top 10, Secure Coding, Privacy

請產出培訓材料。
```

---

## 📚 相關資源

### 內部資源
- **SOP**: [scenarios/security/SOP.md](../../scenarios/security/SOP.md)
- **Agents**:
  - [security-engineer-zh.yaml](../../agent/specialized/security-engineer-zh.yaml)
  - [compliance-officer-zh.yaml](../../agent/specialized/compliance-officer-zh.yaml)
- **Templates**: [Security Requirements Checklist](../../docs_template/support/Security_Requirements_Checklist.md)、[STRIDE 威脅分析模板](../../docs_template/support/STRIDE_Threat_Analysis_Template.md)

### 外部資源
- [OWASP Top 10](https://owasp.org/www-project-top-ten/)
- [OWASP ASVS](https://owasp.org/www-project-application-security-verification-standard/)
- [NIST Cybersecurity Framework](https://www.nist.gov/cyberframework)
- [CIS Benchmarks](https://www.cisecurity.org/cis-benchmarks/)
- [GDPR Official Text](https://gdpr-info.eu/)
- [HIPAA Security Rule](https://www.hhs.gov/hipaa/for-professionals/security/index.html)
- [PCI Security Standards](https://www.pcisecuritystandards.org/)

---

## 🎓 學習路徑

### 初學者
1. 從簡單的安全評估開始
2. 學習 OWASP Top 10
3. 了解基本加密概念
4. 認識主要合規標準

### 中階
1. 進行威脅建模
2. 實施安全控制
3. 進行合規差距分析
4. 準備安全認證

### 進階
1. 設計 Zero Trust 架構
2. 建立 Security Operations Center (SOC)
3. 進行滲透測試
4. 指導團隊安全實踐

---

**版本歷史**:
- v0.03 (2026-02-23): 修正 Agent 引用（加入 -zh 後綴）、修正模板連結、更新版本號
- v0.02 (2025-10-22): 初版發布，Security & Compliance 情境完整 Prompts
