# 情境選擇決策樹
# Scenario Decision Tree

> **🎯 1 分鐘快速選擇最適合的開發情境**
>
> 根據以下決策樹，快速找到適合您專案的 AISDLC 情境。

---

**版本**: v0.09
**創建日期**: 2025-01-15
**閱讀時間**: 1 分鐘
**關聯文檔**: [SCENARIO_SELECTOR.md](./SCENARIO_SELECTOR.md)（詳細版）

---

## 🔀 主決策樹

```
┌─────────────────────────────────────────────────────────────────┐
│                    您的專案是什麼狀態？                          │
└─────────────────────────────────────────────────────────────────┘
                              │
           ┌──────────────────┼──────────────────┐
           │                  │                  │
           ▼                  ▼                  ▼
    ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
    │   🆕 新專案   │   │  📦 既有專案  │   │  🔧 特定任務  │
    │  (無代碼庫)   │   │   (有代碼庫)  │   │   (跨專案)   │
    └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
           │                  │                  │
           ▼                  ▼                  ▼
    ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
    │  GREENFIELD  │   │  見「既有專案  │   │  見「特定任務  │
    │    情境      │   │   決策樹」    │   │   決策樹」    │
    └──────────────┘   └──────────────┘   └──────────────┘
```

---

## 📦 既有專案決策樹

```
┌─────────────────────────────────────────────────────────────────┐
│                    您的主要目標是什麼？                          │
└─────────────────────────────────────────────────────────────────┘
                              │
     ┌────────────┬───────────┼───────────┬────────────┐
     │            │           │           │            │
     ▼            ▼           ▼           ▼            ▼
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
│ 新增功能 │ │ 改善品質 │ │ 提升效能 │ │ 整合系統 │ │ 其他目標 │
│ 修改功能 │ │ 重構代碼 │ │ 優化速度 │ │ 第三方API│ │   ↓     │
│ 修復 Bug│ │ 減技術債 │ │ 降資源耗 │ │ 資料對接 │ │         │
└────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘
     │           │           │           │           │
     ▼           ▼           ▼           ▼           ▼
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐     見下方
│ 🏚️      │ │ ♻️       │ │ ⚡       │ │ 🔌       │
│BROWNFIELD│ │REFACTOR │ │PERFORMANCE│ │INTEGRATION│
└─────────┘ └─────────┘ └─────────┘ └─────────┘
```

### 其他既有專案目標

```
┌─────────────────────────────────────────────────────────────────┐
│                    其他目標分支                                  │
└─────────────────────────────────────────────────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          │                   │                   │
          ▼                   ▼                   ▼
   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
   │  建立自動化   │    │  補充測試    │    │  整理文檔    │
   │  CI/CD 部署  │    │  建立測試策略 │    │  技術文檔    │
   └──────┬───────┘    └──────┬───────┘    └──────┬───────┘
          │                   │                   │
          ▼                   ▼                   ▼
   ┌──────────────┐    ┌──────────────┐    ┌──────────────┐
   │   🚀 DEVOPS  │    │  ✅ TESTING  │    │ 📚 DOCUMENT │
   └──────────────┘    └──────────────┘    └──────────────┘
```

---

## 🔧 特定任務決策樹

```
┌─────────────────────────────────────────────────────────────────┐
│                 您需要處理什麼特定任務？                         │
└─────────────────────────────────────────────────────────────────┘
                              │
               ┌──────────────┴──────────────┐
               │                             │
               ▼                             ▼
        ┌──────────────┐              ┌──────────────┐
        │  安全評估    │              │  合規檢查    │
        │  滲透測試    │              │  安全加固    │
        │  漏洞修復    │              │  ISO/GDPR    │
        └──────┬───────┘              └──────┬───────┘
               │                             │
               └──────────────┬──────────────┘
                              │
                              ▼
                       ┌──────────────┐
                       │ 🔒 SECURITY  │
                       └──────────────┘
```

---

## 📊 情境快速對照表

| 情境 | 適用時機 | 預計時間 | 主導 Agent |
|------|----------|----------|------------|
| 🌱 **Greenfield** | 新專案從零開始 | 3-5 天 | PM/PO → SA → SD |
| 🏚️ **Brownfield** | 既有專案新增/修改功能 | 1-3 天 | SA + Code-Analyzer |
| ♻️ **Refactoring** | 代碼重構/技術債清理 | 2-4 天 | SD + Code-Analyzer |
| ⚡ **Performance** | 效能優化/速度提升 | 1-2 天 | Performance-Engineer |
| 🔌 **Integration** | 第三方 API 整合 | 0.5-2 天 | Integration Specialist |
| 🚀 **DevOps** | CI/CD Pipeline 建置 | 1-3 天 | DevOps-Engineer |
| ✅ **Testing** | 測試策略/自動化測試 | 1-2 天 | QA-Lead |
| 📚 **Documentation** | 技術文檔撰寫 | 0.5-1 天 | Technical-Writer |
| 🔒 **Security** | 安全評估/合規檢查 | 1-3 天 | Security-Engineer |

---

## 🎯 常見組合情境

有些專案可能需要組合多個情境，以下是常見組合：

### 組合 1: 新專案完整開發
```
Greenfield → DevOps → Testing
    │           │         │
    ▼           ▼         ▼
  需求分析    建置CI/CD   測試策略
  架構設計    自動部署    自動化測試
```

### 組合 2: 既有專案大改造
```
Brownfield → Refactoring → Performance → Testing
    │           │              │           │
    ▼           ▼              ▼           ▼
  影響分析    代碼重構      效能優化     回歸測試
```

### 組合 3: 安全加固專案
```
Security → Brownfield → Testing
    │           │          │
    ▼           ▼          ▼
  漏洞掃描    漏洞修復    安全測試
```

---

## 🔗 詳細文檔連結

| 情境 | SOP | 快速參考 | 深度指南 |
|------|-----|----------|----------|
| Greenfield | [SOP](../../../scenarios/greenfield/SOP.md) | [QuickRef](../../../scenarios/greenfield/SOP_QuickRef.md) | [DeepDive](../../../scenarios/greenfield/SOP_DeepDive.md) |
| Brownfield | [SOP](../../../scenarios/brownfield/SOP.md) | [QuickRef](../../../scenarios/brownfield/SOP_QuickRef.md) | [DeepDive](../../../scenarios/brownfield/SOP_DeepDive.md) |
| Refactoring | [SOP](../../../scenarios/refactoring/SOP.md) | [QuickRef](../../../scenarios/refactoring/SOP_QuickRef.md) | [DeepDive](../../../scenarios/refactoring/SOP_DeepDive.md) |
| Performance | [SOP](../../../scenarios/performance/SOP.md) | [QuickRef](../../../scenarios/performance/SOP_QuickRef.md) | [DeepDive](../../../scenarios/performance/SOP_DeepDive.md) |
| Integration | [SOP](../../../scenarios/integration/SOP.md) | [QuickRef](../../../scenarios/integration/SOP_QuickRef.md) | [DeepDive](../../../scenarios/integration/SOP_DeepDive.md) |
| DevOps | [SOP](../../../scenarios/devops/SOP.md) | [QuickRef](../../../scenarios/devops/SOP_QuickRef.md) | [DeepDive](../../../scenarios/devops/SOP_DeepDive.md) |
| Testing | [SOP](../../../scenarios/testing/SOP.md) | [QuickRef](../../../scenarios/testing/SOP_QuickRef.md) | [DeepDive](../../../scenarios/testing/SOP_DeepDive.md) |
| Documentation | [SOP](../../../scenarios/documentation/SOP.md) | [QuickRef](../../../scenarios/documentation/SOP_QuickRef.md) | [DeepDive](../../../scenarios/documentation/SOP_DeepDive.md) |
| Security | [SOP](../../../scenarios/security/SOP.md) | [QuickRef](../../../scenarios/security/SOP_QuickRef.md) | [DeepDive](../../../scenarios/security/SOP_DeepDive.md) |

---

## 💡 不確定時怎麼辦？

如果您不確定該選擇哪個情境：

1. **問自己**: 「這個專案的核心目標是什麼？」
2. **選擇主要情境**: 先選擇最符合核心目標的情境
3. **組合使用**: 如需要，後續再加入其他情境

**還是不確定？** 請參考 [SCENARIO_SELECTOR.md](./SCENARIO_SELECTOR.md) 的詳細問答指南。

---

**文檔版本**: v0.09
**最後更新**: 2025-01-15
**維護者**: AISDLC Framework Team
