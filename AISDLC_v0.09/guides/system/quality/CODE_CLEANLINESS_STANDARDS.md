# 程式碼清潔度標準
# Code Cleanliness Standards

> **🔴 強制標準**
>
> 本文件定義 AISDLC 開發流程中**程式碼清潔度的強制執行規則**。
>
> - **適用範圍**: 所有使用 AISDLC 框架的開發情境
> - **執行時機**: 每次 commit 前、Code Review 時
> - **強制程度**: 強制（非建議）— 違規時不可 commit 或合併

---

**版本**: v1.0
**建立日期**: 2026-05-11
**文檔類型**: 品質標準 | 強制規範
**相關文檔**:
- [Development_Build_Test_Cycle.md](../../user/process/Development_Build_Test_Cycle.md) - 開發-編譯-測試循環（含清潔度檢查步驟）
- [Code_Review_Guidelines.md](../../user/process/Code_Review_Guidelines.md) - Code Review 指南（含 Import & Declaration Review）
- [Document_Quality_Checklist.md](Document_Quality_Checklist.md) - 文檔品質檢查清單

---

## 一、Import 清潔度標準（強制）

### 1.1 核心規則

| 規則 | 說明 | 違規嚴重性 |
|------|------|-----------|
| **無未使用的 import** | 每個 import 必須在程式碼中實際被使用，禁止「預留」或「備用」的 import | 🔴 高 |
| **正確的 import 順序** | 必須符合 checkstyle ImportOrder 規則（見 1.2 節） | 🟡 中 |
| **無遺漏的必要 import** | 尤其是移動/複製程式碼後，必須確認所有依賴的類別都已 import | 🔴 高 |
| **無重複的 import** | 同一個類別不可出現兩次 import | 🟡 中 |

### 1.2 Import 順序規則（Java/Spring 專案）

```
群組 1: java.*        ← 標準 Java 庫
群組 2: jakarta.*     ← Jakarta EE（舊版 javax）
群組 3: javax.*       ← Java 擴展庫
群組 4: org.*         ← Apache、Spring 等組織庫
群組 5: com.*         ← 商業庫與自定義套件（包含自身套件）

每個群組之間空一行（Checkstyle 要求）
每個群組內部按字母順序排列
```

**正確範例**：
```java
import java.util.List;
import java.util.Optional;

import jakarta.persistence.EntityManager;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.ecommerce.domain.model.listing.Listing;
```

### 1.3 移動/複製程式碼後的檢查流程

```
移動/複製程式碼
    ↓
立即執行 mvn checkstyle:check（或 IDE 的 unused import 檢查）
    ↓
確認所有引用的類別都已 import
    ↓
確認 import 順序符合規則
    ↓
確認無多餘的 import（原始碼中不再使用的）
```

---

## 二、欄位/變數清潔度標準（強制）

### 2.1 核心規則

| 規則 | 說明 | 違規嚴重性 |
|------|------|-----------|
| **無未使用的欄位** | 所有宣告的類別欄位（Fields）必須在方法中實際被呼叫 | 🔴 高 |
| **無未使用的局部變數** | 方法內的局部變數宣告後必須使用 | 🟡 中 |
| **無未使用的靜態常量** | `static final` 常量同樣需要實際使用 | 🟡 中 |
| **保留欄位需要標記** | 若有特殊原因需要保留未使用欄位，必須添加 `@SuppressWarnings("unused")` 並附說明 | 📝 規範 |

### 2.2 處理「需要保留未使用欄位」的情境

**允許保留的情況**（必須同時滿足以下條件）：
1. 有明確的業務原因（如：預計下個 Sprint 實作）
2. 已添加 `@SuppressWarnings("unused")`
3. 有 comment 說明保留原因

```java
// ✅ 正確：保留未使用欄位並標記說明
@SuppressWarnings("unused")  // Sprint 2 實作多租戶功能時使用
@Autowired
private TenantRepository tenantRepository;
```

**不允許的做法**：
```java
// ❌ 錯誤：無標記的未使用欄位
@Autowired
private TenantRepository tenantRepository; // 宣告後從未使用
```

### 2.3 清理步驟

1. 用 IDE 的「Find Usages」功能確認欄位使用情況
2. 確定未使用後，移除欄位宣告和對應的 `@Autowired` / `@Inject` 標記
3. 移除後立即執行編譯，確認沒有其他地方依賴此欄位

---

## 三、IDE-Maven 配置同步規則（強制）

### 3.1 問題說明

IDE（如 VS Code + Java Extension）與 Maven 的編譯器設置不一致時，會出現以下現象：
- IDE 顯示「1102 warning: At least one of the problems in category 'unused' is not analysed due to a compiler option being ignored」
- Maven 編譯正常通過，但 IDE 的 unused 警告可能被忽略或不準確
- 這會導致團隊成員誤判：IDE 無警告 ≠ checkstyle 通過

### 3.2 配置同步規則

**規則 1**: VS Code 必須設置自動同步建置配置

```json
// .vscode/settings.json
{
  "java.configuration.updateBuildConfiguration": "automatic"
}
```

**操作步驟**:
1. `Ctrl+Shift+P` → 搜尋「Open User Settings (JSON)」
2. 確認或新增此鍵值
3. 重新啟動 VS Code Java Language Server（`Ctrl+Shift+P` → 「Java: Clean Java Language Server Workspace」）

**規則 2**: IDE 編譯器版本必須與 `pom.xml` 一致

```xml
<!-- pom.xml 示例 -->
<properties>
    <java.version>17</java.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
</properties>
```

確認 IDE 的 Java 版本設置（底部狀態欄或 `Ctrl+Shift+P` → 「Java: Configure Java Runtime」）與上述版本一致。

### 3.3 驗證方法

```bash
# 驗證 IDE 與 Maven 一致：兩者都應通過
mvn compile        # Maven 編譯
mvn checkstyle:check  # Checkstyle 風格檢查

# 若 Maven 通過但 IDE 仍顯示警告 → 排查配置不一致
# 若 checkstyle 失敗 → 修復程式碼後重試
```

---

## 四、提交前強制檢查清單（每次 commit 前必做）

> 🛑 **強制執行**: 以下所有項目必須通過，才能執行 `git commit`。

```
【提交前強制自查清單】

Import 清潔度
□ mvn checkstyle:check 通過（ImportOrder 及其他 style 規則無違規）
□ 無未使用的 import（IDE unused import 警告為 0）
□ 所有 import 順序正確（java → jakarta → javax → org → com）
□ 無漏掉的必要 import（所有引用的類別都已正確 import）
□ 無重複的 import

欄位/變數清潔度
□ 無未使用的欄位/變數（或已標記 @SuppressWarnings("unused") 並附說明）
□ 未使用的 @Autowired 欄位已移除
□ 未使用的 static final 常量已移除

IDE-Maven 配置
□ IDE 與 Maven 配置一致（無 1102 警告）
□ mvn compile 無錯誤

移動/複製程式碼後（若本次有移動/複製）
□ 確認所有依賴的類別都已 import
□ 確認原本的 import 中沒有因移動而多餘的項目
```

---

## 五、違規範例與修正對照

### P1：未使用的 import 語句

**問題情境**：重構後，原本使用的類別被移除，但 import 留著。

```java
// ❌ 違規（AdminControllerE2ETest.java）
import com.fasterxml.jackson.databind.ObjectMapper; // 重構後從未使用
import jakarta.persistence.EntityManager;           // 預留但實際未用

@SpringBootTest
public class AdminControllerE2ETest {
    // ObjectMapper 和 EntityManager 的引用被移除了，但 import 還在
}
```

```java
// ✅ 修正：直接刪除未使用的 import
@SpringBootTest
public class AdminControllerE2ETest {
    // 只保留實際使用的 import
}
```

**如何發現**：
- IDE 會以灰色或警告標記未使用的 import
- `mvn checkstyle:check` 會報告 ImportOrder 和 UnusedImports 違規

---

### P2：未使用的欄位宣告

**問題情境**：預留欄位或重構後遺留的 @Autowired 欄位。

```java
// ❌ 違規（M16ErpIntegrationTest.java）
@SpringBootTest
public class M16ErpIntegrationTest {
    @Autowired
    private UserRepository userRepository; // 從未在任何測試方法中使用

    @Autowired
    private TenantRepository tenantRepository; // 從未在任何測試方法中使用

    @Autowired
    private ListingRepository listingRepository; // 從未在任何測試方法中使用
}
```

```java
// ✅ 修正選項 1：移除不需要的欄位（推薦）
@SpringBootTest
public class M16ErpIntegrationTest {
    // 只保留實際使用的 @Autowired 欄位
}

// ✅ 修正選項 2：若確實需要保留（必須附說明）
@SpringBootTest
public class M16ErpIntegrationTest {
    @SuppressWarnings("unused")  // Sprint 3 實作多租戶測試時使用
    @Autowired
    private TenantRepository tenantRepository;
}
```

---

### P3：未使用的局部變數

**問題情境**：`BookingController.java` 中宣告了 `listingImportList` 局部變數，但從未實際使用。

```java
// ❌ 違規（BookingController.java）
public ResponseEntity<String> importListings(@RequestBody List<ImportRequest> requests) {
    List<Listing> listingImportList = new ArrayList<>();  // 宣告但從未使用！

    for (ImportRequest request : requests) {
        listingRepository.save(new Listing(request));  // 直接存檔，未使用 listingImportList
    }
    return ResponseEntity.ok("匯入成功");
}
```

```java
// ✅ 修正選項 1：移除未使用的局部變數（推薦）
public ResponseEntity<String> importListings(@RequestBody List<ImportRequest> requests) {
    for (ImportRequest request : requests) {
        listingRepository.save(new Listing(request));
    }
    return ResponseEntity.ok("匯入成功");
}

// ✅ 修正選項 2：若確實需要此變數，則實際使用它
public ResponseEntity<String> importListings(@RequestBody List<ImportRequest> requests) {
    List<Listing> listingImportList = new ArrayList<>();
    for (ImportRequest request : requests) {
        listingImportList.add(new Listing(request));  // 實際使用變數
    }
    listingRepository.saveAll(listingImportList);
    return ResponseEntity.ok("匯入成功");
}
```

**如何發現**：IDE 編譯器會對未使用的局部變數發出警告；Checkstyle 的 `UnusedLocalVariable` 規則也會捕捉此問題。

---

### P4：Import 順序錯誤

**問題情境**：Spring 框架 import 和自定義套件 import 順序顛倒。

```java
// ❌ 違規（OrderService.java）
import com.example.ecommerce.domain.repository.OrderRepository; // com.* 在前
import org.springframework.data.domain.Page;                   // org.* 在後（違規）
import org.springframework.stereotype.Service;                  // org.* 在後（違規）
```

```java
// ✅ 修正：依群組排列
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
                                                    // 群組間空行
import com.example.ecommerce.domain.repository.OrderRepository;
```

**工具輔助**：在 IDE 中使用「Organize Imports」（VS Code: `Shift+Alt+O`）可自動排序。

---

### P5：IDE/Maven 配置不一致

**問題情境**：IDE 顯示「1102 warning」，Maven 正常通過。

```
// 症狀
IDE 顯示：1102 warning: At least one of the problems in category 'unused'
          is not analysed due to a compiler option being ignored

Maven 執行：BUILD SUCCESS（無警告）
```

```json
// ✅ 修正：在 .vscode/settings.json 添加
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.compile.nullAnalysis.mode": "automatic"
}
```

**驗證**：重啟 VS Code 後，1102 警告消失，IDE 的 unused 警告現在與 Maven checkstyle 結果一致。

---

## 變更歷史

| 版本 | 日期 | 說明 | 作者 |
|------|------|------|------|
| v1.0 | 2026-05-11 | 初版建立：5大章節，涵蓋 Import/欄位/IDE-Maven/提交前清單/違規範例 | AISDLC Team |

---

**授權與使用**: 本文件為 **AISDLC Framework v0.09** 的一部分，遵循專案整體授權條款。
