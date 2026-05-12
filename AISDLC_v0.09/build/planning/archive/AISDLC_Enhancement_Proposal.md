# AISDLC Enhancement Proposal: Code Quality Enforcement

## 一、發現的問題（Problems Encountered）

在 E-Commerce_Minimax 專案開發過程中，發現以下技術債問題：

### 1.1 未使用的 Import 語句
- `AdminControllerE2ETest.java`: `import com.fasterxml.jackson.databind.ObjectMapper;` (從未使用)
- `M16ErpIntegrationTest.java`: `import jakarta.persistence.EntityManager;` (從未使用)

### 1.2 未使用的欄位宣告
- `AdminControllerE2ETest.java`: `private ObjectMapper objectMapper;` (僅宣告，從未呼叫)
- `M16ErpIntegrationTest.java`: `private UserRepository userRepository;` (僅宣告，從未使用)
- `M16ErpIntegrationTest.java`: `private TenantRepository tenantRepository;` (僅宣告，從未使用)
- `M16ErpIntegrationTest.java`: `private ListingRepository listingRepository;` (僅宣告，從未使用)
- `M16ErpIntegrationTest.java`: `private ProductInventoryRepository productInventoryRepository;` (僅宣告，從未使用)
- `M16ErpIntegrationTest.java`: `private EntityManager entityManager;` (僅宣告，從未使用)

### 1.3 缺少必要的 Import
- `OrderService.java`: 缺少 `import com.nextkey.ecommerce.domain.model.listing.Listing;`

### 1.4 Import 順序錯誤（Checkstyle 違規）
- `BookingController.java`: `PutMapping` 應在 `PostMapping` 之前（依字母順序）
- `OrderService.java`: `org.springframework.data.domain.Page` 應在 `com.nextkey.*` 之前

### 1.5 IDE 與 Maven 編譯器設置不一致
- IDE 顯示 `1102 warning: At least one of the problems in category 'unused' is not analysed due to a compiler option being ignored`
- Maven 編譯正常通過，但 IDE 顯示警告，造成困擾

---

## 二、根本原因分析（Root Cause Analysis）

這些問題的發生是因為**執行層面的紀律缺失**，而非 AISDLC 框架本身的設計缺陷：

| 原因 | 說明 |
|------|------|
| 缺乏「提交前檢查」流程 | 開發者在 commit 前未完整檢查程式碼清潔度 |
| 未使用 import 未及时清理 | 預留程式碼或重構後遺留未使用的 import/fields |
| 移動程式碼時漏掉 import | 複製貼上程式碼時遺漏必要的 import |
| Import 組織規則不明确 | 團隊成員不清楚 checkstyle 的 import 順序要求 |
| IDE 與 Maven 配置不同步 | `.vscode/settings.json` 與 `pom.xml` 的編譯設置不一致 |

---

## 三、建議的 Enhancement（Proposed Enhancements）

### 3.1 在 Quality 文件中新增「提交前必須檢查清單」

在 `AISDLC_v0.09/guides/system/quality/Document_Quality_Checklist.md` 或新建 `CODE_CLEANLINESS_STANDARDS.md`，新增以下檢查點：

#### 3.1.1 Import 清潔度檢查
```
□ 確認所有 import 都實際被使用（無未使用的 import）
□ 確認 import 順序符合 checkstyle 規則：
  - java.* → jakarta.* → javax.* → org.* → com.*
□ 確認沒有漏掉的 import（尤其是在移動/複製程式碼後）
□ 確認沒有重複的 import
```

#### 3.1.2 欄位/變數清潔度檢查
```
□ 確認所有宣告的欄位/變數都有實際使用
□ 若有「預留但未使用」的欄位，必須添加 @SuppressWarnings("unused")
□ 若確定欄位不再需要，必須移除（不要留空宣告）
□ 確認靜態常量（static final）都有實際使用
```

#### 3.1.3 IDE 設置一致性檢查
```
□ 確認 .vscode/settings.json 的 java.configuration.updateBuildConfiguration 為 "automatic"
□ 確認 IDE 編譯器設置與 pom.xml 一致（尤其是 source/target version）
□ 在 commit 前執行 mvn compile 確認無編譯錯誤
□ 在 commit 前執行 mvn checkstyle:check 確認無 style 違規
```

### 3.2 在 Development Build Test Cycle 中新增清潔度驗證步驟

在 `AISDLC_v0.09/guides/user/process/Development_Build_Test_Cycle.md` 的「編譯成功後」階段，新增：

```
步驟 X: 執行 Code Cleanliness Check
  a. 執行 mvn checkstyle:check（確認 ImportOrder 和其他 style 規則）
  b. 檢查所有 import 是否實際被使用（IDE 的 unused import 警告為參考）
  c. 檢查所有宣告的變數/欄位是否有實際使用
  d. 若有未使用的 import 或變數，立即清理後再繼續
  e. 確認 IDE 與 Maven 配置一致（避免 1102 警告混淆）
```

### 3.3 新增「Code Review 檢查清單」

在 `AISDLC_v0.09/guides/user/process/Code_Review_Guidelines.md` 或新建 `CODE_REVIEW_CHECKLIST.md`，新增：

```
【Import & Declaration Review】
□ 檢查是否有未使用的 import
□ 檢查是否有未使用的欄位/變數
□ 檢查 import 順序是否正確
□ 檢查是否有漏掉的必要 import

【Compiler Settings Review】
□ 檢查 IDE 設置與 Maven 配置是否一致
□ 確認沒有因 compiler option 被忽略而漏檢的警告
□ 若有 1102 警告，確認是 IDE/Maven 配置問題而非程式碼問題
```

### 3.4 在 Agent 配置中新增紀律提醒

在 `AISDLC_v0.09/agent/core/` 的 developer agent (`06.dev-developer-zh.yaml`) 中，新增紀律提醒：

```
【品質紀律 - 必須遵守】
1. 每次 commit 前必須執行 mvn checkstyle:check
2. 所有 import 必須實際被使用，不允許「預留」未使用的 import
3. 所有宣告的欄位/變數必須實際被使用，或添加 @SuppressWarnings("unused"
4. 移動/複製程式碼後必須檢查 import 是否完整
5. IDE 與 Maven 配置必須保持一致（使用 "automatic" 設定）
```

---

## 四、具體的 Enhancement Prompt

以下是給 Claude Code 用於更新 AISDLC 的具體 Prompt：

---

### Enhancement Prompt for AISDLC Quality Standards

```
## 任務：精進 AISDLC 開發品質標準

### 背景
在實際專案開發（E-Commerce_Minimax）中發現以下問題：
1. 未使用的 import（ObjectMapper, EntityManager）未及時清理
2. 未使用的欄位（userRepository, tenantRepository 等）長期遺留
3. 移動程式碼時漏掉必要的 import（Listing）
4. Import 順序錯誤導致 checkstyle 違規
5. IDE 與 Maven 編譯器設置不一致導致 1102 警告

### 需求
請根據以下問題，在 AISDLC 中新增/強化品質標準文件：

#### 1. 新建檔案：`AISDLC_v0.09/guides/system/quality/CODE_CLEANLINESS_STANDARDS.md`

內容应包含：
- Import 清潔度標準（每個 import 都必須實際被使用）
- 欄位/變數清潔度標準（宣告的變數都必須使用，或添加 @SuppressWarnings）
- Import 組織規則（groups="java,jakarta,javax,org,com" 的順序）
- IDE-Maven 配置同步要求
- 提交前的強制檢查清單

#### 2. 更新：`AISDLC_v0.09/guides/user/process/Development_Build_Test_Cycle.md`

在「編譯成功後，執行單元測試前」新增步驟：
```
步驟 X: 執行 Code Cleanliness Check
  - mvn checkstyle:check
  - 檢查所有 import 是否被使用
  - 檢查所有變數/欄位是否被使用
  - 若有問題立即修復後再繼續
```

#### 3. 更新：`AISDLC_v0.09/agent/core/06.dev-developer-zh.yaml`

在 quality_standards 或 collaboration_rules 中新增：
```
【品質紀律 - 強制執行】
1. 每次 commit 前執行 mvn checkstyle:check
2. 不允許未使用的 import 存在
3. 不允許未使用的欄位（除非添加 @SuppressWarnings("unused")）
4. 移動/複製程式碼後檢查 import 完整性
5. 保持 IDE 與 Maven 配置一致
```

#### 4. 考慮更新：`AISDLC_v0.09/guides/user/process/Code_Review_Guidelines.md`

新增 Import & Declaration Review 檢查點

### 約束
- 所有修改必須符合現有 AISDLC 的檔案目錄結構
- 使用中文（繁體）撰寫
- 確保內容具體、可操作
- 不要修改核心 workflow 流程，只強化品質標準
```

---

## 五、預期效益

執行此 Enhancement 後：
1. **減少技術債累積** - 提交前檢查杜絕未使用 import/fields
2. **提升程式碼品質** - import 組織明確，減少風格不一致
3. **加速 Code Review** - 檢查清單明確，reviewer 有據可循
4. **降低溝通成本** - 標準化規則減少團隊成員的理解歧異

---

## 六、附加建議（可選）

若資源允許，可以考慮：
1. 在 CI/CD pipeline 中新增 `mvn checkstyle:check` 強制檢查
2. 在 pre-commit hook 中增加 import 清潔度快速檢查（可用 IDE 工具）
3. 提供 IDE 設置範本（`.vscode/settings.json.example`）供團隊成員參考

---

以上是完整的 Enhancement Proposal，請根據實際需要調整執行範圍。