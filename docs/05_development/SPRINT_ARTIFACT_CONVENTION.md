# Sprint 產出文件慣例 / Sprint Artifact Convention

> **文件類型**: 流程標準 (Process Standard)
> **版本**: v1.0
> **建立日期**: 2026-09-01
> **決策者**: 使用者裁定（2026-09-01）
> **目的**: 正式承認 Sprint 93 起「Retro 產出併入 Sprint Plan」的實際慣例，終結文件形式與實務脫節

---

## 1. 背景：為什麼要寫這份文件

`docs/05_development/` 的 `SPRINT_XX_RETRO.md` 停在 **Sprint 92**。

Sprint 93～100 這八個 Sprint **並非沒有做回顧**，而是回顧內容改寫進了：

- `docs/04_planning/SPRINT_XX_PLAN.md` —— 缺口盤點方法、使用者決策、實作理由、測試設計、驗證結果、範圍外項目
- `docs/04_planning/RELEASE_TRACKER.md` —— 每個 Sprint 的交付摘要與 push 狀態
- `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` —— 延後項目與其判斷理由

也就是說：**資訊沒有遺失，但 AISDLC 定義的 Retro 產出形式已實質變更八個 Sprint 而未被承認**。這份文件把慣例寫下來，讓文件形式與實務一致。

---

## 2. 為什麼慣例會自然漂移

`SPRINT_92_RETRO.md` 以前的格式是「回顧會議紀錄」——做得好的／待改善的／改善行動／Action Items 追蹤／Velocity／下一步，預設有多位角色參與討論。

Sprint 93 起的實際工作模式是**單一 AI 執行者依 PRD 掃描缺口 → 使用者在分歧點拍板 → 實作 → 驗證**。在這個模式下：

- 「做得好的／待改善的」與 PLAN 裡的「掃描方法」「為什麼存活 N 個 Sprint 沒被發現」高度重複
- 「改善行動」實際上就是 PLAN 的「範圍外（延後）」＋ `DEFERRED_ITEMS_TRACKER`
- 硬要另立一份 RETRO，等於把同一批內容抄第二遍

**慣例漂移是合理的，問題只在於沒有被記錄下來。**

---

## 3. 新慣例（Sprint 93 起適用，Sprint 101 起強制完整）

### 3.1 每個 Sprint 的必要產出

| 產出 | 位置 | 說明 |
|------|------|------|
| Sprint Plan | `docs/04_planning/SPRINT_XX_PLAN.md` | **唯一的 Sprint 主文件**，同時承擔原 Plan 與原 Retro 的職責 |
| Release 記錄 | `docs/04_planning/RELEASE_TRACKER.md` | 新增一列；狀態欄依該文件的「狀態欄維護規則」於**下一個 Sprint 開工時回填** |
| 延後項目 | `docs/04_planning/DEFERRED_ITEMS_TRACKER.md` | 新發現的延後項目；已完成者移入「已完成延後項目」 |

**不再另建** `docs/05_development/SPRINT_XX_RETRO.md`。

### 3.2 Sprint Plan 的必要章節

| # | 章節 | 來源 |
|---|------|------|
| 1 | 缺口盤點結果（含掃描方法、偽陽性排除） | 原 Plan + 原 Retro「做得好的／待改善的」 |
| 2 | 使用者決策（AskUserQuestion 拍板紀錄） | 原 Plan |
| 3 | 實作內容 | 原 Plan |
| 4 | 測試（含紅燈驗證是否實際執行） | 原 Plan |
| 5 | 驗證結果 | 原 Plan |
| 6 | 範圍外（延後） | 原 Retro「改善行動」 |
| **7** | **Velocity 紀錄** | **原 Retro §6，保留** |
| **8** | **下一步 / Action Items** | **原 Retro §5＋§7，保留** |

### 3.3 為什麼保留 Velocity 與 Action Items

這兩節是原 RETRO 中**唯一沒有被 PLAN 覆蓋**的內容：

- **Velocity 紀錄**：跨 Sprint 的趨勢資料，只存在於 RETRO，一旦停寫就會斷鏈。
- **Action Items 追蹤**：「上個 Sprint 說要做的，這個 Sprint 做到了沒」是防止候選項目連續多輪被列出卻永遠不排入的唯一機制（Sprint 91 的 M18 客服工單曾連續 8 個 Sprint 被列為候選才被真正排入，正是靠這張表才看得出來）。

因此新慣例**要求 Plan 必須含這兩節**，而不是整批捨棄。

---

## 4. 誠實揭露：Sprint 93～100 的已知缺漏

| 項目 | 狀態 |
|------|------|
| Sprint 93～100 的 PLAN | ✅ 皆存在，§1-6 章節完整 |
| Sprint 93～100 的 Velocity 紀錄 | ❌ **缺**（PLAN 未含此節） |
| Sprint 93～100 的 Action Items 追蹤表 | ❌ **缺**（PLAN 未含此節；候選項目散見於各 PLAN 的「範圍外」與 `DEFERRED_ITEMS_TRACKER`） |

**使用者裁定不回填**：回填需重建八個 Sprint 的 SP 估算數字，屬事後補文件，價值低於誠實記錄缺漏。Velocity 序列自 **Sprint 101 重新起算**（S98～S100 的數字由該三個 Sprint 的實際交付量回推，僅供趨勢參考）。

---

## 5. 與既有文件的關係

- **不影響** `docs/05_development/` 既有的 `SPRINT_01~92_RETRO.md`——歷史文件保留原樣，不追溯改寫。
- **不影響** `Sprint_Review_Guide.md`（Sprint Review 的 Demo 操作指南，性質不同）。
- 品質門檻仍依 [`docs/04_planning/EXECUTION_CHECKLIST.md`](../04_planning/EXECUTION_CHECKLIST.md)。

---

**文件版本**: v1.0
**建立日期**: 2026-09-01
**建立者**: Claude Code（依使用者 2026-09-01 裁定）
**維護責任**: 慣例再次變更時必須同步更新本文件，不得再度沉默漂移
