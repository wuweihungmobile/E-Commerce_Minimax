# AISDLC Guides 導航
# AISDLC Guides Navigation

> **📚 AISDLC Framework 完整指南目錄**
>
> 本目錄包含 AISDLC Framework 的所有參考文件，分為：
> - 🤖 **system/** - 給 AI Agent 使用的技術規範
> - 👥 **user/** - 給人類使用者閱讀的指南
> - 📦 **archive/** - 歷史文件存檔

---

**版本**: v0.09
**最後更新**: 2025-01-15
**維護者**: AISDLC Framework Team

---

## 🎯 快速導航 - 我應該看哪個文件？

### 🆕 我是新手，從這裡開始

| 目標 | 推薦文件 | 預計時間 |
|------|---------|---------|
| **5 分鐘了解 AISDLC** | [user/onboarding/QUICK_START_GUIDE.md](user/onboarding/QUICK_START_GUIDE.md) | 5 分鐘 |
| **找到適合的開發情境** | [user/onboarding/SCENARIO_SELECTOR.md](user/onboarding/SCENARIO_SELECTOR.md) | 5 分鐘 |
| **視覺化情境決策** | [user/onboarding/SCENARIO_DECISION_TREE.md](user/onboarding/SCENARIO_DECISION_TREE.md) | 3 分鐘 |
| **互動式教學** | [user/onboarding/TUTORIAL_MODE.md](user/onboarding/TUTORIAL_MODE.md) | 30-120 分鐘 |
| **常用範本庫** | [user/onboarding/QUICK_START_TEMPLATES.md](user/onboarding/QUICK_START_TEMPLATES.md) | 10 分鐘 |
| **快速優化技巧** | [user/onboarding/QUICK_WINS_GUIDE.md](user/onboarding/QUICK_WINS_GUIDE.md) | 15 分鐘 |

**推薦路徑**: QUICK_START_GUIDE → SCENARIO_SELECTOR → 選擇對應情境的 SOP

---

### 🎯 我要執行專案，需要參考

| 階段 | 推薦文件 | 使用時機 |
|------|---------|---------|
| **專案啟動** | [user/onboarding/SCENARIO_SELECTOR.md](user/onboarding/SCENARIO_SELECTOR.md) | 決定使用哪個情境 SOP |
| **技術選型** | [user/technical/SMART_DEFAULTS.md](user/technical/SMART_DEFAULTS.md) | Greenfield Stage 3 |
| **架構設計** | [system/architecture/C4_Model_Guidelines.md](system/architecture/C4_Model_Guidelines.md) | Greenfield Stage 5 (SRD 撰寫) |
| **估算規劃** | [system/planning/Estimation_Standards.md](system/planning/Estimation_Standards.md) | Sprint Planning |
| **MVP 範圍界定** | [system/planning/Kano_Model_Guide.md](system/planning/Kano_Model_Guide.md) | Greenfield Stage 4 步驟 4.2 |
| **ID 命名** | [system/naming/AISDLC_ID_Naming_Convention.md](system/naming/AISDLC_ID_Naming_Convention.md) | User Story 撰寫時 |

---

### 🔧 我要設計架構，需要參考

| 領域 | 推薦文件 | 適用情境 |
|------|---------|---------|
| **C4 架構設計** | [system/architecture/C4_Model_Guidelines.md](system/architecture/C4_Model_Guidelines.md) | 所有架構設計場景 |
| **Web 架構選擇** | [system/architecture/Web_Architecture_Decision_Tree.md](system/architecture/Web_Architecture_Decision_Tree.md) | Web App 技術選型 |
| **架構圖維護** | [system/architecture/Architecture_Diagram_Maintenance.md](system/architecture/Architecture_Diagram_Maintenance.md) | 架構圖版本控制 |
| **可觀測性設計** | [system/architecture/Observability_Design_Guide.md](system/architecture/Observability_Design_Guide.md) | Logging/Metrics/Tracing 設計 |
| **API 版本管理** | [system/api/API_Versioning_Guide.md](system/api/API_Versioning_Guide.md) | API 設計與升級 |
| **API 強制檢查** | [system/api/API_Mandatory_Checklist.md](system/api/API_Mandatory_Checklist.md) | API 文檔交付前 |

---

### ✅ 我要確保品質，需要參考

| 目標 | 推薦文件 | 使用時機 |
|------|---------|---------|
| **文件品質檢查** | [system/quality/Document_Quality_Checklist.md](system/quality/Document_Quality_Checklist.md) | Greenfield Stage 9 |
| **文檔可讀性** | [user/standards/DOCUMENTATION_READABILITY_GUIDE.md](user/standards/DOCUMENTATION_READABILITY_GUIDE.md) | 撰寫文檔時 |
| **安全設計檢查** | [system/quality/Security_Design_Checklist.md](system/quality/Security_Design_Checklist.md) | SRD 撰寫時 |
| **Code Review** | [user/process/Code_Review_Guidelines.md](user/process/Code_Review_Guidelines.md) | 每個 PR 送出後 |
| **AT vs TC 區分** | [system/testing/AT_vs_TC_Guide.md](system/testing/AT_vs_TC_Guide.md) | 測試階段 |

---

### 🤖 我要選擇 Agent，需要參考

| 目標 | 推薦文件 | 使用時機 |
|------|---------|---------|
| **跨平台 Agent 選擇** | [system/agent/Platform_Agent_Selection_Guide.md](system/agent/Platform_Agent_Selection_Guide.md) | 技術選型階段 |
| **Specialized Agent 選擇** | [system/agent/Specialized_Agent_Selection_Guide.md](system/agent/Specialized_Agent_Selection_Guide.md) | Agent 載入時 |

---

## 📁 目錄結構總覽

```
guides/
│
├── system/                          # 🤖 系統參考文件（AI Agent 使用）
│   ├── README.md                   # system/ 目錄說明
│   ├── naming/                     # 命名與識別規範
│   │   └── AISDLC_ID_Naming_Convention.md
│   ├── architecture/               # 架構設計規範
│   │   ├── C4_Model_Guidelines.md
│   │   ├── Architecture_Diagram_Maintenance.md
│   │   ├── Web_Architecture_Decision_Tree.md
│   │   └── Observability_Design_Guide.md
│   ├── api/                        # API 設計規範
│   │   ├── API_Versioning_Guide.md
│   │   └── API_Mandatory_Checklist.md
│   ├── testing/                    # 測試規範
│   │   └── AT_vs_TC_Guide.md
│   ├── quality/                    # 品質管理規範
│   │   ├── Document_Quality_Checklist.md
│   │   └── Security_Design_Checklist.md
│   ├── planning/                   # 規劃與估算規範
│   │   ├── Estimation_Standards.md
│   │   └── Kano_Model_Guide.md
│   └── agent/                      # Agent 管理規範
│       ├── Platform_Agent_Selection_Guide.md
│       └── Specialized_Agent_Selection_Guide.md
│
├── user/                           # 👥 使用者參考文件（人類使用）
│   ├── README.md                   # user/ 目錄說明
│   ├── onboarding/                 # 新手入門
│   │   ├── QUICK_START_GUIDE.md
│   │   ├── QUICK_START_TEMPLATES.md
│   │   ├── QUICK_WINS_GUIDE.md
│   │   ├── SCENARIO_SELECTOR.md
│   │   ├── SCENARIO_DECISION_TREE.md
│   │   └── TUTORIAL_MODE.md
│   ├── standards/                  # 標準與規範
│   │   └── DOCUMENTATION_READABILITY_GUIDE.md
│   ├── technical/                  # 技術指引
│   │   └── SMART_DEFAULTS.md
│   └── process/                    # 流程與管理
│       └── Code_Review_Guidelines.md
│
├── archive/                        # 📦 歷史文件存檔
│   ├── README.md
│   ├── HOW_TO_RESUME.md           # v0.02 中斷恢復指南
│   ├── INDEX.md                   # v0.02 完整索引
│   ├── REVIEW_INDEX.md            # v0.02 審查文檔索引
│   └── REVIEW_QUICK_REFERENCE.md  # v0.02 審查快速參考
│
└── README.md                       # 本檔案 - Guides 總覽與導航
```

---

## 🔍 檔案索引（按字母排序）

### A
- [API_Mandatory_Checklist.md](system/api/API_Mandatory_Checklist.md) - API 規格強制檢查清單
- [API_Versioning_Guide.md](system/api/API_Versioning_Guide.md) - API 版本升級與管理指引
- [Architecture_Diagram_Maintenance.md](system/architecture/Architecture_Diagram_Maintenance.md) - 架構圖版本控制指引
- [AT_vs_TC_Guide.md](system/testing/AT_vs_TC_Guide.md) - Acceptance Test vs Test Case 區分指引
- [AISDLC_ID_Naming_Convention.md](system/naming/AISDLC_ID_Naming_Convention.md) - 統一 ID 命名規範

### C
- [C4_Model_Guidelines.md](system/architecture/C4_Model_Guidelines.md) - C4 架構設計指南
- [Code_Review_Guidelines.md](user/process/Code_Review_Guidelines.md) - Code Review 指南

### D
- [Document_Quality_Checklist.md](system/quality/Document_Quality_Checklist.md) - 文件品質檢查清單
- [DOCUMENTATION_READABILITY_GUIDE.md](user/standards/DOCUMENTATION_READABILITY_GUIDE.md) - 文檔可讀性指南

### E
- [Estimation_Standards.md](system/planning/Estimation_Standards.md) - 估算標準化指南

### K
- [Kano_Model_Guide.md](system/planning/Kano_Model_Guide.md) - Kano 模型功能分類指引

### O
- [Observability_Design_Guide.md](system/architecture/Observability_Design_Guide.md) - 可觀測性設計指南

### P
- [Platform_Agent_Selection_Guide.md](system/agent/Platform_Agent_Selection_Guide.md) - 跨平台 Agent 選擇指南

### Q
- [QUICK_START_GUIDE.md](user/onboarding/QUICK_START_GUIDE.md) - 快速上手指南
- [QUICK_START_TEMPLATES.md](user/onboarding/QUICK_START_TEMPLATES.md) - 快速啟動範本庫
- [QUICK_WINS_GUIDE.md](user/onboarding/QUICK_WINS_GUIDE.md) - 快速優化指南

### S
- [SCENARIO_DECISION_TREE.md](user/onboarding/SCENARIO_DECISION_TREE.md) - 情境選擇決策樹（視覺化）
- [SCENARIO_SELECTOR.md](user/onboarding/SCENARIO_SELECTOR.md) - 情境選擇助手
- [Security_Design_Checklist.md](system/quality/Security_Design_Checklist.md) - 安全設計檢查清單
- [SMART_DEFAULTS.md](user/technical/SMART_DEFAULTS.md) - 智能預設值配置
- [Specialized_Agent_Selection_Guide.md](system/agent/Specialized_Agent_Selection_Guide.md) - Specialized Agent 選擇指南

### T
- [TUTORIAL_MODE.md](user/onboarding/TUTORIAL_MODE.md) - 教學模式

### W
- [Web_Architecture_Decision_Tree.md](system/architecture/Web_Architecture_Decision_Tree.md) - Web 架構決策樹

---

## 📊 統計資訊

- **system/ 檔案數**: 15 個（給 AI Agent 使用）
- **user/ 檔案數**: 8 個（給人類使用）
- **archive/ 檔案數**: 5 個（歷史文件）
- **總計**: 28 個 Markdown 檔案

---

## 🔄 維護指引

### 新增檔案決策流程

```
問題：這個檔案是給誰用的？
│
├─ AI Agent 使用 → 放入 guides/system/
│   └─ 問題：屬於哪個分類？
│       ├─ 命名規範 → system/naming/
│       ├─ 架構設計 → system/architecture/
│       ├─ API 設計 → system/api/
│       ├─ 測試 → system/testing/
│       ├─ 品質管理 → system/quality/
│       ├─ 規劃估算 → system/planning/
│       └─ Agent 管理 → system/agent/
│
└─ 人類使用 → 放入 guides/user/
    └─ 問題：屬於哪個分類？
        ├─ 新手入門 → user/onboarding/
        ├─ 標準規範 → user/standards/
        ├─ 技術指引 → user/technical/
        └─ 流程管理 → user/process/
```

### 相關文檔

- [system/README.md](system/README.md) - 系統參考文件目錄詳細說明
- [user/README.md](user/README.md) - 使用者參考文件目錄詳細說明
- [FILE_DIRECTORY_RULES.md](../FILE_DIRECTORY_RULES.md) - 檔案目錄維護規則（權威定義）

---

**最後更新**: 2025-01-15

**變更記錄**:
- 2025-01-15: 新增 API_Mandatory_Checklist.md、SCENARIO_DECISION_TREE.md（v0.09 開發專注版）
- 2025-12-01: guides/ 目錄重組（v0.09），建立 system/ 和 user/ 子目錄結構
