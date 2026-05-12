# AISDLC Enhancement Prompt for Claude Code

## 任務：精進 AISDLC 開發品質標準

### 背景說明

在 E-Commerce_Minimax 專案開發中發現以下技術債問題：

1. **未使用的 import**：`ObjectMapper`, `EntityManager` 等 import 宣告後從未使用
2. **未使用的欄位**：`userRepository`, `tenantRepository` 等欄位僅宣告但從未呼叫
3. **漏掉的 import**：`Listing` 類別移動後缺少 import
4. **Import 順序錯誤**：Checkstyle ImportOrder 規則未遵守
5. **IDE/Maven 配置不一致**：1102 警告造成開發困擾

這些問題不是框架設計缺陷，而是**執行紀律**和**品質檢查點**可以更嚴謹。

---

### 需要執行的 Enhancement

#### 1. 新建檔案：`AISDLC_v0.09/guides/system/quality/CODE_CLEANLINESS_STANDARDS.md`

內容必須包含：

**Import 清潔度標準**
- 每個 import 都必須實際被使用，絕不允許「預留但未使用」的 import
- Import 順序必須符合 checkstyle 規則：`groups="java,jakarta,javax,org,com"`
- 移動/複製程式碼後必須檢查 import 是否完整

**欄位/變數清潔度標準**
- 所有宣告的欄位/變數必須實際被使用
- 若因特殊原因需要保留未使用的欄位，必須添加 `@SuppressWarnings("unused")`
- 確定不再需要的欄位/變數必須移除，不要留空宣告浪費資源

**IDE-Maven 配置同步**
- `.vscode/settings.json` 的 `java.configuration.updateBuildConfiguration` 必須設為 `"automatic"`
- 確保 IDE 編譯器設置與 `pom.xml` 一致，避免 1102 警告混淆

**提交前強制檢查清單（每次 commit 前必須執行）**
```
□ mvn checkstyle:check 通過
□ 無未使用的 import
□ 無未使用的欄位（除非有 @SuppressWarnings("unused")）
□ 所有 import 都正確且完整
□ IDE 與 Maven 配置一致
```

#### 2. 更新：`AISDLC_v0.09/guides/user/process/Development_Build_Test_Cycle.md`

在「步驟：編譯成功 → 執行單元測試」之間新增：

```
步驟：Code Cleanliness Check
  a. 執行 mvn checkstyle:check
  b. 檢查所有 import 是否被使用（IDE unused import 警告為參考）
  c. 檢查所有宣告的變數/欄位是否有實際使用
  d. 若有問題立即修復
  e. 確認通過後才能執行單元測試
```

#### 3. 更新：`AISDLC_v0.09/agent/core/06.dev-developer-zh.yaml`

在 `quality_standards` 或 `collaboration_rules` 區塊中新增：

```yaml
【品質紀律 - 強制執行】
1. 每次 commit 前執行 mvn checkstyle:check，違規時不可 commit
2. 不允許未使用的 import 存在（會被 checkstyle 拦截）
3. 不允許未使用的欄位（除非添加 @SuppressWarnings("unused")）
4. 移動/複製程式碼後檢查 import 完整性
5. 保持 IDE 與 Maven 配置一致（java.configuration.updateBuildConfiguration: automatic）
6. 提交前確認所有程式碼已通过編譯和 checkstyle
```

#### 4. 更新：`AISDLC_v0.09/guides/user/process/Code_Review_Guidelines.md`

新增「Import & Declaration Review」檢查點：

```
【Import & Declaration Review】(每個 Code Review 都必須檢查)
□ 檢查是否有未使用的 import
□ 檢查是否有未使用的欄位/變數（沒有 @SuppressWarnings 的那種）
□ 檢查 import 順序是否正確（java → jakarta → javax → org → com）
□ 檢查是否有漏掉的必要 import
□ 確認沒有 1102 警告（IDE/Maven 配置不一致問題）
```

---

### 執行約束

1. **只更新/新增檔案，不要刪除任何現有檔案**
2. **使用中文（繁體）撰寫所有內容**
3. **內容必須具體、可操作，避免空泛的原則性描述**
4. **不要修改核心 workflow 流程，只強化品質標準**
5. **新增的檢查點必須明確標示為「強制」而非「建議」**

---

### 預期產出

執行完成後，AISDLC 將具備：
1. 完整的 CODE_CLEANLINESS_STANDARDS.md 品質標準文件
2. 更新後的 Development_Build_Test_Cycle.md（含清潔度檢查步驟）
3. 更新後的 dev-developer-zh.yaml（含品質紀律規則）
4. 更新後的 Code_Review_Guidelines.md（含 Import Review 檢查點）