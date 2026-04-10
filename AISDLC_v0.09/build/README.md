# AISDLC v0.09 Build 目錄

**版本**: v0.09
**建立日期**: 2025-01-11
**用途**: 版本建置過程的臨時文檔

## 目錄結構

```
build/
├── logs/              # 版本專屬日誌
│   └── CHANGELOG_v0.09.md
├── planning/          # 計劃文檔
│   ├── active/       # 執行中的計劃
│   └── archive/      # 已完成的計劃
├── reports/          # 建置報告
│   ├── phase/       # 階段報告
│   ├── kpi/         # KPI 報告
│   ├── verification/ # 驗證報告
│   └── analysis/    # 分析報告
└── systems/          # 系統機制文檔
```

## 子目錄說明

### logs/ - 版本專屬日誌

記錄 v0.09 版本專屬的變更日誌和重組記錄：

- **CHANGELOG_v0.09.md**: 記錄 v0.09 的所有變更（開發專注版）
- **FILE_REORGANIZATION_LOG_v0.09.md**: 記錄檔案重組過程（如有）
- 其他版本專屬的日誌文檔

### planning/ - 計劃文檔

用於記錄升版過程中的計劃和決策：

- **active/**: 當前正在執行的計劃
- **archive/**: 已完成或歷史計劃

### reports/ - 建置報告

記錄各種建置過程的報告：

- **phase/**: 階段性報告（如 Phase 1, Phase 2 報告）
- **kpi/**: KPI 相關報告
- **verification/**: 驗證測試報告
- **analysis/**: 分析報告

### systems/ - 系統機制文檔

記錄系統機制相關的文檔。

## 注意事項

- ❌ **build/ 不隨版本升級** - 每個版本有自己的 build/
- 📦 **版本發布後歸檔** - 移至 `AISDLC_ALL/build_archives/v0.09/`
- ⏳ **臨時性文檔** - 僅用於記錄建置過程
- 🆕 **build/logs/** - 記錄版本專屬的變更日誌

## v0.09 特殊說明

v0.09 是「開發專注版」，本次建置包含以下主要變更：

- 📁 專案目錄結構調整（移除會議目錄、新增品質目錄）
- 🔄 開發-編譯-測試循環強制規則
- 📋 API 強制檢查清單
- 🔀 場景選擇決策樹

詳細變更請參考 [build/logs/CHANGELOG_v0.09.md](./logs/CHANGELOG_v0.09.md)

## 相關文檔

- [FILE_DIRECTORY_RULES.md](../FILE_DIRECTORY_RULES.md) - 文件分類規則
- [AISDLC_v0.10_UPGRADE_SOP.md](../AISDLC_v0.10_UPGRADE_SOP.md) - 下次升版 SOP
