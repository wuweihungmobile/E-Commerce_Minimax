# AISDLC Enhancement 執行計畫
# AISDLC Code Quality Enhancement Plan

**建立日期**: 2026-05-11
**計畫版本**: v1.1（QA 審查後修正）
**計畫狀態**: ✅ 已完成實作（2026-05-11）
**相關文件**:
- [AISDLC_Enhancement_Prompt.md](AISDLC_Enhancement_Prompt.md)
- [AISDLC_Enhancement_Proposal.md](AISDLC_Enhancement_Proposal.md)

---

## 一、問題摘要

綜合分析兩份文件，發現 E-Commerce_Minimax 專案開發中暴露了 AISDLC 框架在**程式碼清潔度執行紀律**上的系統性漏洞：

### 1.1 五類具體問題

| # | 問題類型 | 具體案例 | 嚴重性 |
|---|---------|---------|-------|
| P1 | 未使用的 import 語句 | `ObjectMapper`, `EntityManager` 宣告後從未使用 | 🔴 高 |
| P2 | 未使用的欄位宣告 | `userRepository`, `tenantRepository` 等 5 個欄位僅宣告未使用 | 🔴 高 |
| P3 | 缺少必要的 import | `Listing` 類別移動後缺少 import，導致編譯錯誤 | 🔴 高 |
| P4 | Import 順序錯誤 | `PutMapping` 應在 `PostMapping` 之前；`org.*` 應在 `com.*` 之前 | 🟡 中 |
| P5 | IDE/Maven 配置不一致 | 1102 警告造成開發困擾（IDE 顯示警告，Maven 正常通過） | 🟡 中 |

### 1.2 根本原因分析

| 原因 | 說明 | 對應解決方向 |
|------|------|-------------|
| 開發-編譯-測試循環缺少清潔度驗證步驟 | 現有循環只檢查編譯與測試通過，未檢查程式碼清潔度 | 插入 Code Cleanliness Check 步驟 |
| 無明文的程式碼清潔度標準 | 團隊成員不清楚 import 順序規則等要求 | 建立 CODE_CLEANLINESS_STANDARDS.md |
| Agent 品質規範缺乏清潔度項目 | dev-developer Agent 的 quality_standards 未涵蓋此類問題 | 更新 Agent 配置 |
| Code Review 缺乏 Import 檢查項目 | 現有 Code Review 清單（8大類）無 import/宣告清潔度類別 | 新增第9大類檢查項目 |

---

## 二、解決策略

### 核心原則（依照 Enhancement Prompt 約束）

1. **只新增/更新，不刪除**現有檔案
2. **不修改核心 workflow 流程**，只強化品質標準
3. 所有新增檢查點**標示為「強制」**而非「建議」
4. 內容使用**繁體中文**，具體可操作

### 解決策略矩陣

| 問題 | 解決方案 | 執行方式 |
|------|---------|---------|
| 無清潔度標準文件 | 新建 CODE_CLEANLINESS_STANDARDS.md | 任務 T1 |
| 循環缺少清潔度驗證 | 更新 Development_Build_Test_Cycle.md | 任務 T2 |
| Agent 未強制執行清潔度 | 更新 06.dev-developer-zh.yaml | 任務 T3 |
| Code Review 無此類檢查 | 更新 Code_Review_Guidelines.md | 任務 T4 |
| 新檔案未登記至目錄規則 | 更新 FILE_DIRECTORY_RULES.md | 任務 T5 |
| 新檔案未出現在導航目錄 | 更新 guides/README.md | 任務 T6 |

---

## 三、執行任務清單

### ✅ 完整執行清單

- [x] **T1** 新建 `CODE_CLEANLINESS_STANDARDS.md`（品質標準文件）
- [x] **T2** 更新 `Development_Build_Test_Cycle.md`（插入清潔度檢查步驟）
- [x] **T3** 更新 `06.dev-developer-zh.yaml`（新增品質紀律規則）
- [x] **T4** 更新 `Code_Review_Guidelines.md`（新增 Import & Declaration Review）
- [x] **T5** 更新 `FILE_DIRECTORY_RULES.md`（登記新增檔案）
- [x] **T6** 更新 `guides/README.md`（新增品質文件導航）

---

## 四、各任務詳細規劃

### T1：新建 CODE_CLEANLINESS_STANDARDS.md

**路徑**: `AISDLC_v0.09/guides/system/quality/CODE_CLEANLINESS_STANDARDS.md`

**目錄**: 已存在（驗證通過），與 `Document_Quality_Checklist.md` 並列

**文件架構（5 大章節，須全部實作）**:

```
## 一、Import 清潔度標準（強制）
  - 規則：每個 import 必須實際被使用（禁止「預留」未使用 import）
  - 規則：Import 順序必須符合 checkstyle：java.* → jakarta.* → javax.* → org.* → com.*
  - 規則：移動/複製程式碼後必須重新確認 import 完整性
  - 規則：禁止重複 import

## 二、欄位/變數清潔度標準（強制）
  - 規則：所有宣告的欄位/變數必須實際被使用
  - 規則：若需保留未使用欄位，必須添加 @SuppressWarnings("unused") 並附說明
  - 規則：確定不需要的欄位/變數必須立即移除
  - 規則：static final 常量同樣適用

## 三、IDE-Maven 配置同步規則（強制）
  - 規則：.vscode/settings.json 的 java.configuration.updateBuildConfiguration 必須設為 "automatic"
  - 操作步驟：Ctrl+Shift+P → Open User Settings (JSON) → 確認/設定此鍵值
  - 規則：IDE 編譯器 source/target version 必須與 pom.xml 一致
  - 驗證：出現 1102 警告時必須排查並修正配置

## 四、提交前強制檢查清單（每次 commit 前必做）
  □ mvn checkstyle:check 通過（ImportOrder 等 style 規則）
  □ 無未使用的 import（IDE 警告為參考，mvn checkstyle 為權威）
  □ 無未使用的欄位/變數（或已標記 @SuppressWarnings("unused")）
  □ 所有 import 正確且完整（無漏掉的必要 import）
  □ IDE 與 Maven 配置一致（無 1102 警告）

## 五、違規範例與修正對照（P1~P5 每類至少 1 個）

### P1 - 未使用的 import
❌ 錯誤：
  import com.fasterxml.jackson.databind.ObjectMapper; // 從未使用
  import jakarta.persistence.EntityManager;           // 從未使用

✅ 修正：直接刪除未使用的 import 語句

### P2 - 未使用的欄位宣告
❌ 錯誤：
  @Autowired
  private UserRepository userRepository; // 從未呼叫

✅ 修正（二選一）：
  選項1：完全移除此欄位（推薦）
  選項2：保留時加標記：
    @SuppressWarnings("unused")
    @Autowired
    private UserRepository userRepository; // 保留原因：預計 Sprint 2 實作時使用

### P3 - 缺少必要的 import
❌ 錯誤（移動 Listing 類別後）：
  // 忘記加入 import
  List<Listing> listings = listingRepository.findAll();
  // 編譯錯誤：cannot find symbol class Listing

✅ 修正：
  import com.nextkey.ecommerce.domain.model.listing.Listing;

### P4 - Import 順序錯誤
❌ 錯誤：
  import com.nextkey.ecommerce.service.OrderService;   // com.* 在前
  import org.springframework.data.domain.Page;         // org.* 在後（違規）

✅ 修正：
  import org.springframework.data.domain.Page;         // org.* 在前
  import com.nextkey.ecommerce.service.OrderService;   // com.* 在後

### P5 - IDE/Maven 配置不一致
❌ 現象：IDE 顯示 "1102 warning: At least one of the problems..."
✅ 修正：在 .vscode/settings.json 確認設定：
  { "java.configuration.updateBuildConfiguration": "automatic" }
```

---

### T2：更新 Development_Build_Test_Cycle.md

**路徑**: `AISDLC_v0.09/guides/user/process/Development_Build_Test_Cycle.md`

#### 修改 1：核心流程圖（在第 16-31 行「核心流程」區塊）

**搜尋定位**：找到以下片段（目前在「🔴 核心原則」區塊的程式碼框內）：
```
編譯成功 ✅
    ↓
執行單元測試 (Unit Test)
```

**替換為**：
```
編譯成功 ✅
    ↓
執行 Code Cleanliness Check（程式碼清潔度檢查）
    ↓
清潔度不合格？ → 🔴 立即停止 → 清理後重新執行編譯和檢查
    ↓
清潔度通過 ✅
    ↓
執行單元測試 (Unit Test)
```

#### 修改 2：新增「階段 1.5」（插入於現有「階段 1」與「階段 2」之間）

**搜尋定位**：找到「### 階段 2: 單元測試檢查」，在其**之前**插入：

```markdown
### 階段 1.5: Code Cleanliness Check（程式碼清潔度檢查）【強制】

> ⚠️ 此階段為**強制執行**，不可跳過。清潔度不合格時，必須修復後重新從階段 1 開始。

```
□ 執行 checkstyle 檢查（依專案類型）:
  - Java: `mvn checkstyle:check`（確認 ImportOrder 等 style 規則）
  - Python: `flake8` 或 `pylint`（未使用 import 和變數）
  - TypeScript: `eslint --max-warnings 0`（no-unused-vars, no-unused-imports 規則）
□ 確認無未使用的 import（IDE unused import 警告為參考，checkstyle 為權威）
□ 確認無未使用的欄位/變數（除非已標記 @SuppressWarnings("unused") 或同等標記）
□ 確認 import 順序正確（Java: java → jakarta → javax → org → com）
□ 確認沒有漏掉的必要 import（尤其是移動/複製程式碼後）
□ 確認 IDE 與 Maven/建置工具配置一致（Java: 無 1102 警告）
□ 如有問題：立即修復後，重新執行階段 1（編譯）再到此階段
□ 全部通過：才能進入階段 2（單元測試）
```
```

#### 修改 3：「禁止行為」新增第 5 條

**搜尋定位**：找到「4. **❌ 禁止測試失敗後「先跳過」**」這一段，在其**之後**新增：

```markdown
5. **❌ 禁止跳過 Code Cleanliness Check**
   - 錯誤範例：編譯成功 → 直接執行單元測試（未執行清潔度檢查）
   - 正確做法：編譯成功 → Code Cleanliness Check 通過 → 才執行單元測試
```

#### 修改 4：更新「相關文檔」區塊

**搜尋定位**：找到「🔗 相關文檔」區塊，在「流程指南」中新增：

```markdown
- [CODE_CLEANLINESS_STANDARDS.md](../../guides/system/quality/CODE_CLEANLINESS_STANDARDS.md) - 程式碼清潔度標準（強制）
```

---

### T3：更新 06.dev-developer-zh.yaml

**路徑**: `AISDLC_v0.09/agent/core/06.dev-developer-zh.yaml`

**搜尋定位**：找到 `quality_standards:` 區塊中的第三條（「🔴 測試失敗零容忍」），在其**之後**插入 5 條新規則：

**現有第三條**（定位用）：
```yaml
    - "🔴 測試失敗零容忍 - 單元測試失敗必須立即依規格文檔修復，絕不跳過或註解測試"
```

**在上面這行之後，插入以下 5 條**：
```yaml
    - "🔴 程式碼清潔度強制 - 每次 commit 前必須執行 mvn checkstyle:check，違規時禁止 commit（遵循 guides/system/quality/CODE_CLEANLINESS_STANDARDS.md）"
    - "🔴 Import 清潔度零容忍 - 不允許未使用的 import 存在；移動/複製程式碼後必須立即驗證 import 完整性"
    - "🔴 欄位宣告清潔度 - 所有宣告的欄位/變數必須實際被使用，若有例外必須添加 @SuppressWarnings('unused') 並說明原因"
    - "🔴 Import 順序強制 - 必須遵守 checkstyle ImportOrder 規則：java.* → jakarta.* → javax.* → org.* → com.*"
    - "🔴 IDE-Maven 配置一致性 - IDE 與 Maven 編譯器設置必須保持一致，java.configuration.updateBuildConfiguration 設為 'automatic'，消除 1102 警告"
```

> ⚠️ **YAML 注意事項**：
> - 縮進必須使用空格（不可用 Tab），與現有條目對齊（4 個空格 + 「- 」）
> - 插入後現有的第 4-9 條順移為第 9-14 條
> - 插入後請確認 YAML 格式有效

---

### T4：更新 Code_Review_Guidelines.md

**路徑**: `AISDLC_v0.09/guides/user/process/Code_Review_Guidelines.md`

#### 修改 1：新增第 9 大類檢查項目

**搜尋定位**：找到以下文字（第 8 大類結尾）：
```
---

## 不同類型 PR 的審查重點
```

**在「## 不同類型 PR 的審查重點」之前插入**：

```markdown
### 9. Import 與宣告清潔度（Import & Declaration Cleanliness）【強制】

> ⚠️ 此類別為**強制**檢查項目，發現違規時必須標記「🚨 Must Fix」，不得合併。

| # | 檢查項目 | ✅/❌ | 備註 |
|---|---------|-------|------|
| 9.1 | 確認所有 import 都實際被使用（無未使用的 import） | [ ] | |
| 9.2 | 確認 import 順序符合 checkstyle 規則（java → jakarta → javax → org → com） | [ ] | |
| 9.3 | 確認沒有漏掉的必要 import（尤其是移動/複製程式碼後） | [ ] | |
| 9.4 | 確認沒有重複的 import | [ ] | |
| 9.5 | 確認所有宣告的欄位/變數都有實際使用 | [ ] | |
| 9.6 | 若有未使用欄位，確認已標記 @SuppressWarnings("unused") 並附說明 | [ ] | |
| 9.7 | 確認 IDE 與 Maven 編譯器設置一致（無 1102 警告） | [ ] | |
| 9.8 | 確認 mvn checkstyle:check 通過（無 ImportOrder 等 style 違規） | [ ] | |

**範例問題**:
- ❌ `import com.fasterxml.jackson.databind.ObjectMapper;` 宣告但從未使用
  - ✅ 直接刪除此 import
- ❌ `private UserRepository userRepository;` 宣告但從未呼叫
  - ✅ 移除此欄位，或添加 `@SuppressWarnings("unused")` 並說明保留原因
- ❌ `org.springframework.*` import 排在 `com.company.*` 之後（違反 ImportOrder）
  - ✅ 依規則調整：org.* 應在 com.* 之前

**參考文件**: [CODE_CLEANLINESS_STANDARDS.md](../../guides/system/quality/CODE_CLEANLINESS_STANDARDS.md)

---
```

#### 修改 2：更新「自我審查確認清單」

**精確定位**：找到「For Author（開發者）」區塊 → 「2. 自我審查 🔍」小節 → 「## 自我審查確認清單」→「### 程式碼品質」項目下。

**搜尋關鍵片段**（定位用）：
```
### 程式碼品質
- [ ] 符合 Coding Style（執行 Linter/Formatter）
- [ ] 函式職責單一（≤ 50 行）
- [ ] 命名清晰（變數、函式、類別）
- [ ] 無註解掉的程式碼
- [ ] 無 console.log, debugger, TODO
- [ ] 複雜邏輯有註解說明
```

**在「複雜邏輯有註解說明」之後新增 4 行**：
```
- [ ] 無未使用的 import（已執行 mvn checkstyle:check 確認）
- [ ] 無未使用的欄位/變數（或已標記 @SuppressWarnings("unused")）
- [ ] import 順序正確（java → jakarta → javax → org → com）
- [ ] 無漏掉的必要 import（尤其是移動/複製程式碼後）
```

---

### T5：更新 FILE_DIRECTORY_RULES.md

**路徑**: `AISDLC_v0.09/FILE_DIRECTORY_RULES.md`

**精確定位**：找到 `guides/system/quality/` 相關區塊，在現有檔案清單（`Document_Quality_Checklist.md`、`Security_Design_Checklist.md`）之後新增：

```
- `CODE_CLEANLINESS_STANDARDS.md` - 程式碼清潔度標準（Import 清潔度、欄位/變數清潔度、IDE-Maven 配置同步、提交前強制檢查清單）
```

> **備註**: 具體插入行號需查閱 FILE_DIRECTORY_RULES.md 中 `guides/system/quality/` 的現有內容位置。

---

### T6：更新 guides/README.md

**路徑**: `AISDLC_v0.09/guides/README.md`

**精確定位**：找到「我要確保品質」或「品質管理」相關的導航表格（位於文件中的品質檢查區塊），新增一行：

```markdown
| **程式碼清潔度標準** | [system/quality/CODE_CLEANLINESS_STANDARDS.md](system/quality/CODE_CLEANLINESS_STANDARDS.md) | 每次 commit 前 / Code Review 時 |
```

若無品質專屬表格，可在「我要執行專案，需要參考」表格中，在「開發循環流程」一行之後新增：

```markdown
| **程式碼清潔度標準** | [system/quality/CODE_CLEANLINESS_STANDARDS.md](system/quality/CODE_CLEANLINESS_STANDARDS.md) | 每次 commit 前 |
```

---

## 五、未來增強建議（可選，本次不實作）

| # | 建議 | 說明 | 優先度 |
|---|------|------|-------|
| F1 | CI/CD 整合 checkstyle | 在 GitHub Actions 新增 `mvn checkstyle:check` 強制檢查 | 中 |
| F2 | Pre-commit hook | 在 git pre-commit hook 中加入快速 import 清潔度檢查 | 中 |
| F3 | IDE 設置範本 | 提供 `.vscode/settings.json.example` 供團隊參考 | 低 |

---

## 六、驗收標準

執行所有任務後，驗證以下指標：

1. ✅ `CODE_CLEANLINESS_STANDARDS.md` 存在於 `guides/system/quality/` 且包含 5 個章節（Import/欄位/IDE-Maven/提交前清單/違規範例）
2. ✅ `Development_Build_Test_Cycle.md` 核心流程圖中包含「Code Cleanliness Check」步驟（在編譯成功與單元測試之間）
3. ✅ `Development_Build_Test_Cycle.md` 包含「階段 1.5」的完整清單
4. ✅ `Development_Build_Test_Cycle.md` 的「禁止行為」區塊包含第 5 條
5. ✅ `06.dev-developer-zh.yaml` 的 `quality_standards` 在第三條（測試失敗零容忍）之後包含 5 條新清潔度規則
6. ✅ `Code_Review_Guidelines.md` 包含第 9 大類「Import 與宣告清潔度」（8個檢查項 9.1-9.8）
7. ✅ `Code_Review_Guidelines.md` 的自我審查「程式碼品質」清單包含 4 個 import 相關項目
8. ✅ `FILE_DIRECTORY_RULES.md` 已在 `guides/system/quality/` 區塊登記 `CODE_CLEANLINESS_STANDARDS.md`
9. ✅ `guides/README.md` 已新增 `CODE_CLEANLINESS_STANDARDS.md` 的導航連結
10. ✅ 所有新增/修改內容使用繁體中文，無簡體字或英文主體內容

---

## 七、影響評估

### 正面效益
- 提交前清潔度強制檢查，杜絕技術債累積
- import 規則明確化，減少 checkstyle 違規
- Code Review 有據可循，提升審查效率
- IDE/Maven 配置問題有標準解法，降低溝通成本

### 風險評估
- **低風險**: 所有修改均為新增內容，不修改或刪除現有邏輯
- **相容性**: 修改不影響現有 workflow 流程
- **YAML 風險**: T3 修改時需注意縮進，避免格式錯誤

---

**計畫版本**: v1.1（已通過 QA 審查，可執行）
