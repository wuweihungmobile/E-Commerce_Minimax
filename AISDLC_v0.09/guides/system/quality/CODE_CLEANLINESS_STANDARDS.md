# Code Cleanliness Standards
# 程式碼清潔度標準

> **版本**: v1.0
> **適用範圍**: 所有 AISDLC 開發情境
> **最後更新**: 2026-05-10
> **角色**: 所有開發者（Dev）、程式碼審查者（Reviewer）

---

## 🔴 強制執行聲明

**本文件中所有規則皆為強制執行，違反者視為不合格的程式碼。**

本標準是基於實際專案開發中發現的技術債問題（E-Commerce_Minimax 案例）制定，旨在：
1. 杜絕未使用 import/欄位殘留
2. 確保 Import 組織符合 Checkstyle 規範
3. 保持 IDE 與 Maven 配置一致
4. 建立可執行的提交前檢查流程

---

## 1. Import 清潔度標準

### 1.1 每個 Import 都必須實際被使用

**🛑 嚴格禁止**：
- 宣告但從未使用的 import
- 「預留」但實際未使用的 import
- 複製貼上時遺漏的 import

**範例**：

```java
// ❌ 錯誤：import 後從未使用
import com.fasterxml.jackson.databind.ObjectMapper;  // 從未在程式碼中出現

public class AdminControllerE2ETest {
    @Autowired
    private JwtTokenService jwtTokenService;  // objectMapper 完全沒用到
}
```

```java
// ✅ 正確：所有 import 都有實際使用
public class SomeController {
    @Autowired
    private ObjectMapper objectMapper;  // 有使用 objectMapper.writeValueAsString()

    public void doSomething() {
        String json = objectMapper.writeValueAsString(data);
    }
}
```

**例外情況**：
- 若因特殊原因需要保留「預留」的 import，必須添加 `@SuppressWarnings("unused")` 註解
- 這個例外不適用於從未使用的 import（那是人為疏忽，不是預留）

### 1.2 Import 順序必須符合 Checkstyle 規則

**標準順序**（groups 定義）：
```
java → jakarta → javax → org → com → 其餘
```

**詳細規則**：
1. 每個 group 之間用空行分隔
2. 同一 group 內的 import 依字母順序排列
3. 不允許使用 `import *`（wildcard import）
4. 靜態 import 放在一般 import 之後

**正確範例**：
```java
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.OrderDto;
import com.nextkey.ecommerce.domain.model.order.Order;
```

**錯誤範例**：
```java
// ❌ 錯誤：順序不符合規範
import org.springframework.stereotype.Service;  // 應該在 jakarta之後
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
```

### 1.3 移動/複製程式碼後必須檢查 Import 完整性

**情境**：當移動或複製一段程式碼到新檔案時，必須：
1. 確認所有必要的 import 都已存在
2. 確認沒有遺漏的 import（尤其是跨 package 的引用）
3. 確認沒有重複的 import

**操作流程**：
```
移動/複製程式碼
    ↓
檢查新檔案是否有漏掉的 import（IDE 會顯示紅色錯誤）
    ↓
若有漏掉，補上必要的 import
    ↓
檢查是否有重複的 import
    ↓
執行 mvn checkstyle:check 確認通過
```

---

## 2. 欄位/變數清潔度標準

### 2.1 所有宣告的欄位/變數都必須實際被使用

**🛑 嚴格禁止**：
- 宣告後從未使用的欄位（除非有正當理由）
- 預留但實際未使用的 repository 欄位
- 從未呼叫的 service 欄位

**範例**：

```java
// ❌ 錯誤：欄位僅宣告但從未使用
public class M16ErpIntegrationTest {
    @Autowired
    private UserRepository userRepository;  // 從未使用 userRepository.findById() 等方法

    @Autowired
    private TenantRepository tenantRepository;  // 從未使用

    @Autowired
    private EntityManager entityManager;  // 從未使用
}
```

```java
// ✅ 正確：所有欄位都有實際使用
public class SomeService {
    @Autowired
    private UserRepository userRepository;  // 有使用 userRepository.findById()

    @Autowired
    private TenantRepository tenantRepository;  // 有使用 tenantRepository.findBySlug()
}
```

### 2.2 正當理由保留未使用欄位的規則

若因特殊原因需要保留未使用的欄位（例如：預留未來擴展、測試環境模擬），必須：

1. **添加 `@SuppressWarnings("unused")`** 註解
2. **在註解中說明理由**

**正確範例**：
```java
public class TestClass {
    // 預留用於未來測試擴展
    @SuppressWarnings("unused")
    private String storeOwnerToken;

    // 預留用於多角色測試場景
    @SuppressWarnings("unused")
    private static final String TEST_PASSWORD = "SecurePass123!";
}
```

### 2.3 確定不需要的欄位必須移除

**不要**：
- 留空宣告「以防萬一」
- 註解掉欄位但不移除
- 假裝欄位還會被使用

**正確做法**：
```
發現未使用的欄位
    ↓
評估：是否真的需要保留？（預留擴展？）
    ↓
是 → 添加 @SuppressWarnings("unused") + 說明理由
    ↓
否 → 直接移除（Delete）
        ↓
執行編譯確認不影響功能
```

---

## 3. IDE 與 Maven 配置同步標準

### 3.1 VS Code settings.json 必要配置

**必須包含**：
```json
{
    "java.configuration.updateBuildConfiguration": "automatic"
}
```

**原因**：
- 確保 IDE 編譯器設置與 Maven 一致
- 避免出現 `1102 warning: At least one of the problems in category 'unused' is not analysed due to a compiler option being ignored`

### 3.2 避免 1102 警告混淆

**問題說明**：
- 1102 警告表示「IDE 的編譯器設置與 Maven 不同，導致某些檢查被忽略」
- 這會造成：IDE 顯示沒問題，但實際上有未使用的 import/欄位

**解決方案**：
1. 確保 `.vscode/settings.json` 中 `updateBuildConfiguration` 為 `"automatic"`
2. 提交前執行 `mvn checkstyle:check` 驗證
3. 若 IDE 仍顯示 1102，重啟 VSCode 或執行「Reload Window」

---

## 4. 提交前強制檢查清單

**🛑 每次 commit 前都必須執行以下檢查，違規者不可 commit！**

### 4.1 必須檢查的項目

```
□ mvn checkstyle:check 通過（0 violations）
□ 無未使用的 import（所有 import 都實際被使用）
□ 無未使用的欄位（除了有 @SuppressWarnings("unused") 的）
□ 所有 import 都正確且完整（無漏掉、無重複）
□ Import 順序正確（java → jakarta → javax → org → com）
□ IDE 設置與 Maven 配置一致（無 1102 警告）
□ mvn compile 通過
□ mvn test 通過（若修改涉及測試）
```

### 4.2 執行順序

```
完成程式碼撰寫
    ↓
執行 mvn checkstyle:check
    ↓
檢查是否有 unused import 警告
    ↓
修復所有問題（移除不需要的 import）
    ↓
再次執行 mvn checkstyle:check 確認通過
    ↓
執行 mvn compile 確認編譯成功
    ↓
執行 mvn test 確認測試通過
    ↓
git add + git commit
```

### 4.3 快捷指令（適用於 Java/Maven 專案）

```bash
# 快速檢查（推薦寫成腳本）
./mvnw checkstyle:check && mvn compile

# 完整檢查
./mvnw checkstyle:check && mvn compile && mvn test

# 若只想檢查 import 順序問題
./mvnw checkstyle:check -Dcheckstyle.consoleOutput=true | grep -i "import"
```

---

## 5. 問題案例與修正方法

### 案例 1：未使用的 ObjectMapper import

**檔案**：AdminControllerE2ETest.java

**問題**：
```java
import com.fasterxml.jackson.databind.ObjectMapper;  // 從未使用

public class AdminControllerE2ETest {
    // objectMapper 欄位從未使用
    private ObjectMapper objectMapper;
}
```

**修正**：
```java
// 移除 ObjectMapper import
// 移除 objectMapper 欄位（或添加 @SuppressWarnings 若需要保留）

public class AdminControllerE2ETest {
    // 已移除未使用的 objectMapper
}
```

### 案例 2：未使用的 EntityManager import

**檔案**：M16ErpIntegrationTest.java

**問題**：
```java
import jakarta.persistence.EntityManager;  // 從未使用

public class M16ErpIntegrationTest {
    @Autowired
    private EntityManager entityManager;  // 從未使用
}
```

**修正**：
```java
// 移除 EntityManager import
// 移除 entityManager 欄位

public class M16ErpIntegrationTest {
    // 已移除未使用的 entityManager
}
```

### 案例 3：漏掉的 Listing import

**檔案**：OrderService.java

**問題**：
```
Listing cannot be resolved to a type
```

**原因**：程式碼中使用了 `Listing` 類別，但缺少 import

**修正**：
```java
// 添加必要的 import
import com.nextkey.ecommerce.domain.model.listing.Listing;
```

### 案例 4：Import 順序錯誤

**檔案**：OrderService.java

**問題**：Checkstyle 報告 `org.springframework.data.domain.Page` 的位置不正確

**原因**：Page 在 `com.nextkey.*` import 之後，但應該在之前

**修正**：
```java
import org.springframework.data.domain.Page;        // 移到前面
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import com.nextkey.ecommerce.api.dto.OrderDto;      // 在這之後
import com.nextkey.ecommerce.domain.model.listing.Listing;
```

---

## 6. 違反本標準的後果

| 違規類型 | 嚴重程度 | 後果 |
|---------|---------|------|
| 未使用的 import 未移除 | 🔴 高 | Checkstyle 失敗，CI/CD 阻擋 |
| 未使用的欄位未處理 | 🔴 高 | 技術債累積，影響程式碼可讀性 |
| Import 順序錯誤 | 🔴 高 | Checkstyle 失敗，CI/CD 阻擋 |
| 漏掉必要 import | 🔴 高 | 編譯失敗 |
| 持續違反（同一檔案多次） | 🚨 極高 | 需回顧開發流程並改善 |

---

## 7. 相關文檔

| 文檔 | 位置 | 說明 |
|------|------|------|
| **Development_Build_Test_Cycle.md** | `guides/user/process/` | 開發-編譯-測試循環機制 |
| **Code_Review_Guidelines.md** | `guides/user/process/` | Code Review 完整指南 |
| **dev-developer-zh.yaml** | `agent/core/` | 開發者 Agent 配置（含品質紀律） |
| **Document_Quality_Checklist.md** | `guides/system/quality/` | 文檔品質檢查清單 |

---

## 8. 變更歷史

| 版本 | 日期 | 變更內容 | 作者 |
|------|------|---------|------|
| v1.0 | 2026-05-10 | 初版建立：基於 E-Commerce_Minimax 實際案例制定 | AISDLC Enhancement |

---

**本標準為 AISDLC Framework v0.09 的一部分，強制執行於所有使用 AISDLC 的開發專案。**