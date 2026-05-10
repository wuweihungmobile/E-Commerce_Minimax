# Code Review 檢查清單 / Code Review Checklist

**版本**: v1.0
**更新日期**: 2026-05-09
**適用範圍**: 所有 Pull Request

---

## 🔴 程式碼品質門禁 / Quality Gate (PR 必須通過)

### 1. Checkstyle 檢查（強制）

- [ ] **無新增 Checkstyle 錯誤**
  - 執行：`cd backend && mvn checkstyle:check`
  - 結果：必須返回 `BUILD SUCCESS`
  - 若有錯誤，請在 PR 描述中說明原因（需 PM 批准）

- [ ] **FinalParameters 檢查**
  - 所有方法參數必須使用 `final` 關鍵字
  - 範例：`public void process(final UUID id, final String name)`

- [ ] **MagicNumber 檢查**
  - 嚴禁硬編碼數值（除 -1, 0, 1-10, 100, 1000）
  - 應使用命名常數：`MAX_RETRY_COUNT`, `DEFAULT_TIMEOUT` 等

### 2. 編譯檢查

- [ ] **本地編譯通過**
  ```bash
  cd backend && mvn clean compile
  ```

- [ ] **無新增警告**
  ```bash
  cd backend && mvn clean compile -Dmaven.compiler.showWarnings=true
  ```

### 3. 測試覆蓋率

- [ ] **單元測試通過**
  ```bash
  cd backend && mvn test
  ```

- [ ] **覆蓋率門禁（80%）**
  ```bash
  cd backend && mvn jacoco:check
  ```

---

## 🟡 程式碼品質檢查 / Code Quality Check

### 4. 命名規範

- [ ] **類別命名**：PascalCase（如 `UserService`）
- [ ] **方法命名**：camelCase（如 `getUserById`）
- [ ] **常數命名**：UPPER_SNAKE_CASE（如 `MAX_RETRY_COUNT`）
- [ ] **變數命名**：camelCase（如 `userId`, `orderList`）

### 5. Javadoc 註解

- [ ] **所有 public/protected 方法有 Javadoc**
- [ ] **Javadoc 包含 `@param`, `@return`, `@throws`**
- [ ] **類別頂部有類別說明**

### 6. 錯誤處理

- [ ] **無裸露的 `try-catch`（需有適當處理）**
- [ ] **異常需記錄 log**
- [ ] **使用自訂 BusinessException 而非直接 throw Exception**

### 7. 安全檢查

- [ ] **無硬編碼敏感資訊**（密碼、API Key、Token）
- [ ] **使用 environment variables 或 secrets manager**
- [ ] **輸入驗證存在且有效**

---

## 🟢 最佳實踐 / Best Practices

### 8. 程式碼結構

- [ ] **單一職責原則**：每個方法只做一件事
- [ ] **避免過度巢狀**：巢狀深度不超過 5 層
- [ ] **方法長度控制**：不超過 150 行

### 9. 異常處理

- [ ] **不使用 `catch(Exception e)` 或 `catch(Throwable t)`**
- [ ] **特定異常需被捕獲並適當處理**

### 10. 事務管理

- [ ] **讀取方法標記 `@Transactional(readOnly = true)`**
- [ ] **寫入方法包含適當事務邊界**

---

## 📋 Code Review 流程

### 提交前（Contributor）

1. [ ] 本地執行 `mvn checkstyle:check` 確認通過
2. [ ] 本地執行 `mvn test` 確認測試通過
3. [ ] 確認無硬編碼敏感資訊
4. [ ] 更新相關技術文檔（如有需要）

### 審核中（Reviewer）

1. [ ] 檢視程式碼邏輯正確性
2. [ ] 確認 business requirements 被滿足
3. [ ] 檢查安全性問題
4. [ ] 驗證測試覆蓋率足夠
5. [ ] 確認無新增 checkstyle 錯誤

### 合併條件

- [ ] 所有 🔴 強制項目已滿足
- [ ] 至少 1 位 reviewer 批准
- [ ] CI Pipeline 全部通過
- [ ] 無待解決的 conversation 或 comment

---

## 🔧 快速檢查命令

```bash
# 快速檢查（推薦）
cd backend && mvn checkstyle:check && mvn test

# 完整檢查
cd backend && mvn clean compile test checkstyle:check jacoco:check
```

---

## 📞 尋求協助

- **Checkstyle 規則疑問**：聯繫 SA (System Analyst)
- **Business Logic 疑問**：聯繫 PM/PO
- **測試相關疑問**：聯繫 QA

---

**最後更新者**: Claude Code Agent
**下次 review**: 每 sprint 結束時檢視有效性
