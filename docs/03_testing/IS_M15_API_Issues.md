# M15 CMS Backend API 問題分析報告 / M15 CMS Backend API Issue Analysis Report

## 文件資訊
- **AT-ID**: AT-M15-API
- **版本**: 1.1
- **日期**: 2026-05-05 16:00 UTC+0800
- **狀態**: 🔴 **需要 Dev 修復**

---

## 1. 問題總結

| 問題類別 | 測試案例 | 根本原因 | 負責 |
|----------|----------|----------|------|
| TC-M15-001~004, 011, 019 | 403 Forbidden | 測試用戶角色不符（需 SELLER/HOST） | Dev |
| TC-M15-005~010 | Skip (依賴前項) | 前項失敗導致 | - |
| TC-M15-015 | 500 錯誤 | Public API slug 查詢異常 | Dev |
| TC-M15-017, 018 | 500 錯誤 | Media 上傳 StorageService 異常 | Dev |
| TC-M15-021~023 | 404 錯誤 | Listing Card 查詢（需確認資料庫） | Dev/QA |

---

## 2. 詳細問題分析

### 2.1 TC-M15-001~004, 011, 019 - 403 Forbidden

**現象**：
- 新註冊的測試用戶調用 `/api/v2/dashboard/posts` 得到 HTTP 403
- PostController 的 `@PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")` 拒絕訪問

**根本原因**：

| 原因 | 說明 |
|------|------|
| **角色不符** | 測試腳本註冊的用戶預設角色為 `BUYER`（AuthService.resolveUserRole） |
| **無法註冊 CMS 角色** | `AuthService` 的 `resolveUserRole()` 只允許 `BUYER`, `SELLER`, `HOST` |
| **SELLER/HOST 無 CMS 權限** | `RolePermissionMapping` 定義的 SELLER 和 HOST 權限不包含 CMS 權限 |
| **需要 STORE_OWNER** | 只有 `STORE_OWNER` 和 `STORE_STAFF` 角色才能使用 CMS |

**程式碼分析**：

```java
// AuthService.java - 角色解析
private User.UserRole resolveUserRole(String userType) {
    if (userType == null) return User.UserRole.BUYER;
    return switch (userType) {
        case "SELLER" -> User.UserRole.SELLER;
        case "HOST" -> User.UserRole.HOST;
        default -> User.UserRole.BUYER;
    };
}

// PostController.java - 需要這些角色
@PreAuthorize("hasAnyRole('STORE_OWNER', 'STORE_STAFF', 'SELLER', 'HOST')")
```

**SecurityConfig.java 中的路徑設定**：
```java
.requestMatchers("/v2/posts").permitAll()        // 公開
.requestMatchers("/v2/posts/**").permitAll()   // 公開
.requestMatchers("/v2/listings/*/card").permitAll()  // 公開
.anyRequest().authenticated()  // /dashboard/** 需要認證
```

**修復方向（Dev）**：
1. **選項 A**：在測試資料庫中預先建立帶有 `STORE_OWNER` 角色的測試用戶，測試腳本使用這個預設帳號
2. **選項 B**：修改 `AuthService.resolveUserRole()` 或建立 admin API 來賦予 `STORE_OWNER` 角色給測試用戶
3. **選項 C**：修改 `RolePermissionMapping` 讓 `SELLER` 或 `HOST` 角色擁有 CMS 權限

---

### 2.2 TC-M15-015 - Public API slug 查詢返回 500

**現象**：
- `GET /api/v2/posts/{slug}?tenantId={TENANT_ID}` 返回 HTTP 500（預期 404）

**根本原因**：

| 可能原因 | 說明 |
|---------|------|
| **tenantId 參數格式** | Controller 中 `@RequestParam UUID tenantId`，如果為 null 或格式錯誤 |
| **Repository 查詢異常** | `postRepository.findByTenantIdAndSlug()` 可能因參數問題拋出底層異常 |
| **未捕獲的 Exception** | 任何 RuntimeException 未被捕獲會變成 500 |

**程式碼分析**：
```java
// PostController.java
@GetMapping("/posts/{slug}")
public ResponseEntity<ApiResponse<M15Dto.PostResponse>> getPublishedPostBySlug(
        @PathVariable String slug,
        @RequestParam UUID tenantId) {  // 可能為 null

// PostService.java
public M15Dto.PostResponse getPublishedPostBySlug(String slug, UUID tenantId) {
    Post post = postRepository.findByTenantIdAndSlug(tenantId, slug)
            .orElseThrow(() -> new BusinessException(ErrorCode.E_4100));
    // 如果 tenantId 為 null，這裡可能拋出底層異常
}
```

**修復方向（Dev）**：
1. 增加 null check 或 validate tenantId
2. 確保 repository 查詢時正确處理 null parameter
3. 確認 404 是預期行為（当 slug 不存在时）

---

### 2.3 TC-M15-017, 018 - Media 上傳返回 500

**現象**：
- `POST /api/v2/media/upload` 返回 HTTP 500

**根本原因**：

| 可能原因 | 說明 |
|---------|------|
| **StorageService 異常** | `StorageService.uploadFile()` 連接 MinIO 失敗時拋出未捕獲的 RuntimeException |
| **MultipartFile 處理** | 現有 endpoint 使用 `String filePath`（路徑版本），但錯誤可能來自其他部分 |

**程式碼分析**：
```java
// MediaService.java
try (InputStream inputStream = file.getInputStream()) {
    storedPath = storageService.uploadFile(
            tenantId, fileName, inputStream, fileSize, mimeType);
} catch (IOException e) {
    log.error("Failed to read file input stream: {}", fileName, e);
    throw new BusinessException(ErrorCode.E_9000, "Failed to upload file: " + fileName);
}

// StorageService.java - 可能拋出 RuntimeException
public String uploadFile(...) {
    // 如果 MinIO 連線失敗，可能拋出未捕獲的例外
    minioClient.putObject(PutObjectArgs.builder()...build());
}
```

**修復方向（Dev）**：
1. 在 `MediaService.uploadMedia()` 中捕獲 StorageService 異常並轉換為 BusinessException
2. 檢查 `application.yml` 中的 MinIO/S3 配置是否正確
3. 確認 StorageService 上傳失敗時不會拋出未捕獲的 RuntimeException

---

### 2.4 TC-M15-021~023 - Listing Card 返回 404

**現象**：
- `GET /api/v2/listings/{LISTING_ID}/card` 返回 HTTP 404

**根本原因**：

| 可能原因 | 說明 |
|---------|------|
| **測試資料不存在** | 已確認使用正確的測試資料 UUID（dddddddd...） |
| **Lazy Loading 問題** | Hibernate session 在 transaction 外關閉，訪問 lazy loaded collection |
| **Listing 已刪除** | 測試資料的 Listing 可能被標記為 DELETED |

**程式碼分析**：
```java
// ListingCardService.java
@Transactional(readOnly = true)
public M15Dto.ListingCardResponse getListingCard(UUID listingId) {
    Listing listing = listingRepository.findById(listingId)
            .orElseThrow(() -> new BusinessException(ErrorCode.E_3000));
    // 如果 listing 存在但 tenant 為 lazy-loaded
    // 可能在 buildProductCard/buildRoomCard 中發生 LazyInitializationException
}

// buildProductCard - 可能問題
.tenantName(listing.getTenant().getName())  // 如果 tenant 是 lazy-loaded
```

**修復方向（Dev）**：
1. 檢查 `Listing.tenant` 關聯是否為 EAGER 或在 query 中使用 JOIN FETCH
2. 確認資料庫中 `dddddddd-dddd-dddd-dddd-dddddddddddd` 存在且狀態為 ACTIVE
3. 將 `LazyInitializationException` 轉換為適當的業務異常

---

## 3. 修復優先級

| 優先級 | 問題 | 影響測試案例 | 修復工作量 |
|--------|------|-------------|-----------|
| **P0** | 測試用戶角色問題 | TC-M15-001~004, 011, 019 (6 cases) | 中 |
| **P1** | Media 上傳 500 | TC-M15-017, 018 (2 cases) | 低 |
| **P1** | Slug 查詢 500 | TC-M15-015 (1 case) | 低 |
| **P2** | Listing Card 404 | TC-M15-021~023 (3 cases) | 中 |

---

## 4. 建議修復方案

### 4.1 測試用戶角色問題（最重要）

**建議**：建立一個專門的測試用戶端點或腳本，預先在資料庫中建立帶有 `STORE_OWNER` 角色的測試用戶。

```sql
-- 預先建立的 CMS 測試用戶（需 DevOps 執行）
INSERT INTO users (id, email, full_name, password_hash, role, tenant_id, ...)
VALUES ('test-cms-owner@example.com', 'CMS Test User', 'STORE_OWNER', ...);
```

或修改測試腳本使用現有的 CMS 測試用戶。

### 4.2 Media 上傳錯誤處理

```java
// MediaService.java
try {
    storedPath = storageService.uploadFile(...);
} catch (Exception e) {
    log.error("Failed to upload to storage: {}", fileName, e);
    throw new BusinessException(ErrorCode.E_9000, "Failed to upload file: " + fileName);
}
```

### 4.3 Slug 查詢參數驗證

```java
// PostService.java
public M15Dto.PostResponse getPublishedPostBySlug(String slug, UUID tenantId) {
    if (tenantId == null) {
        throw new BusinessException(ErrorCode.E_1002, "tenantId is required");
    }
    // ... 繼續查詢
}
```

### 4.4 Listing Card Lazy Loading

```java
// ListingCardService.java
@Transactional(readOnly = true)
public M15Dto.ListingCardResponse getListingCard(UUID listingId) {
    Listing listing = listingRepository.findByIdWithTenant(listingId)  // 自定義 query 加上 join fetch
            .orElseThrow(() -> new BusinessException(ErrorCode.E_3000));
    // ...
}
```

---

## 5. 下一步行動

| 負責 | 行動 | 期限 |
|------|------|------|
| **Dev** | 確認 TC-M15-001~004, 011, 019 的修復方案（提供 CMS 測試用戶或調整角色） | Sprint 9 |
| **Dev** | 修復 Media 上傳的錯誤處理 | Sprint 9 |
| **Dev** | 修復 Slug 查詢的參數驗證 | Sprint 9 |
| **Dev** | 檢查 Listing Card API 的 Lazy Loading 問題 | Sprint 9 |
| **QA** | 驗證 MinIO 手動上傳 | 待定 |
| **QA** | 修復後重新執行測試 | Sprint 9 後 |

---

## 6. 附錄：程式碼位置

| 檔案 | 路徑 |
|------|------|
| AuthController | `backend/src/main/java/com/nextkey/ecommerce/api/controller/AuthController.java` |
| AuthService | `backend/src/main/java/com/nextkey/ecommerce/core/auth/AuthService.java` |
| SecurityConfig | `backend/src/main/java/com/nextkey/ecommerce/api/config/SecurityConfig.java` |
| PostController | `backend/src/main/java/com/nextkey/ecommerce/api/controller/PostController.java` |
| MediaService | `backend/src/main/java/com/nextkey/ecommerce/core/cms/media/MediaService.java` |
| StorageService | `backend/src/main/java/com/nextkey/ecommerce/infrastructure/storage/StorageService.java` |
| ListingCardService | `backend/src/main/java/com/nextkey/ecommerce/core/cms/listing/ListingCardService.java` |

---

**報告產生時間**: 2026-05-05 16:00 UTC+0800
**分析工具**: Claude Code (Automated Code Analysis)
