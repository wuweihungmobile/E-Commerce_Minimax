# Java 程式碼模板 / Java Code Templates

**版本**: v1.0
**更新日期**: 2026-05-09
**適用範圍**: 所有新增 Java 檔案

---

## 📜 模板檔案列表

| 模板檔案 | 用途 |
|----------|------|
| `Service_Template.md` | Service 層範本 |
| `Controller_Template.md` | Controller 層範本 |
| `Repository_Template.md` | Repository 層範本 |
| `Dto_Template.md` | DTO 範本 |
| `Entity_Template.md` | Entity/Model 範本 |

---

## 🎯 使用方法

1. 複製下方 Markdown 內容
2. 替換所有 `{PLACEHOLDER}` 為實際內容
3. 貼上並調整為實際程式碼

---

## 📝 Service 層範本

```java
package com.nextkey.ecommerce.core.{module};

import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * {ModuleName} Service
 * {Description}
 *
 * @author {Author}
 * @since {YYYY-MM-DD}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class {ModuleName}Service {

    // ========== Constants ==========
    // 示例：private static final int MAX_RETRY_COUNT = 3;
    // 示例：private static final String DEFAULT_STATUS = "ACTIVE";

    // ========== Dependencies ==========
    // 示例：private final {Entity}Repository {entity}Repository;

    /**
     * Get {entity} by ID.
     *
     * @param id the {entity} UUID
     * @return the {entity} details
     * @throws BusinessException if {entity} not found
     */
    @Transactional(readOnly = true)
    public {ReturnType} get{ModuleName}(final UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();

        // TODO: Implement logic

        log.info("Get {moduleName}: id={}, tenantId={}", id, tenantId);
        return null;
    }

    /**
     * List all {entities} for current tenant.
     *
     * @return list of {entities}
     */
    @Transactional(readOnly = true)
    public List<{ReturnType}> list{ModuleNames}() {
        UUID tenantId = TenantContext.getCurrentTenant();

        // TODO: Implement logic

        log.info("List {moduleNames}: tenantId={}", tenantId);
        return null;
    }

    /**
     * Create new {entity}.
     *
     * @param request the creation request
     * @return created {entity}
     */
    @Transactional
    public {ReturnType} create{ModuleName}(final {RequestType} request) {
        UUID tenantId = TenantContext.getCurrentTenant();

        // TODO: Implement logic

        log.info("Create {moduleName}: tenantId={}", tenantId);
        return null;
    }

    /**
     * Update existing {entity}.
     *
     * @param id      the {entity} UUID
     * @param request the update request
     * @return updated {entity}
     * @throws BusinessException if {entity} not found
     */
    @Transactional
    public {ReturnType} update{ModuleName}(final UUID id, final {RequestType} request) {
        UUID tenantId = TenantContext.getCurrentTenant();

        // TODO: Implement logic

        log.info("Update {moduleName}: id={}, tenantId={}", id, tenantId);
        return null;
    }

    /**
     * Delete {entity} (soft delete).
     *
     * @param id the {entity} UUID
     * @throws BusinessException if {entity} not found
     */
    @Transactional
    public void delete{ModuleName}(final UUID id) {
        UUID tenantId = TenantContext.getCurrentTenant();

        // TODO: Implement logic

        log.info("Delete {moduleName}: id={}, tenantId={}", id, tenantId);
    }

    // ========== Helper Methods ==========

    /**
     * Find {entity} by ID and tenant ID.
     *
     * @param id       the {entity} UUID
     * @param tenantId the tenant UUID
     * @return the {entity} entity
     * @throws BusinessException if not found
     */
    private {EntityType} findByIdAndTenantId(final UUID id, final UUID tenantId) {
        // TODO: Implement logic
        throw new BusinessException(ErrorCode.E_0000, "Not implemented");
    }
}
```

---

## 🎯 Controller 層範本

```java
package com.nextkey.ecommerce.api.controller;

import com.nextkey.ecommerce.api.dto.{ModuleName}Dto;
import com.nextkey.ecommerce.core.{module}.{ModuleName}Service;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * {ModuleName} REST Controller
 * {Description}
 *
 * @author {Author}
 * @since {YYYY-MM-DD}
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/{moduleNames}")
public class {ModuleName}Controller {

    private final {ModuleName}Service {moduleName}Service;

    /**
     * Get {entity} by ID.
     *
     * @param id the {entity} UUID
     * @return {entity} details
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('USER')")
    public ResponseEntity<{ModuleName}Dto> get{ModuleName}(@PathVariable final UUID id) {
        log.info("REST request to get {moduleName}: {}", id);
        return ResponseEntity.ok({moduleName}Service.get{ModuleName}(id));
    }

    /**
     * List all {entities}.
     *
     * @return list of {entities}
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<{ModuleName}Dto>> list{ModuleNames}() {
        log.info("REST request to list {moduleNames}");
        return ResponseEntity.ok({moduleName}Service.list{ModuleNames}());
    }

    /**
     * Create new {entity}.
     *
     * @param request the creation request
     * @return created {entity}
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<{ModuleName}Dto> create{ModuleName}(
            @Valid @RequestBody final {ModuleName}Dto request) {
        log.info("REST request to create {moduleName}: {}", request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body({moduleName}Service.create{ModuleName}(request));
    }

    /**
     * Update existing {entity}.
     *
     * @param id      the {entity} UUID
     * @param request the update request
     * @return updated {entity}
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<{ModuleName}Dto> update{ModuleName}(
            @PathVariable final UUID id,
            @Valid @RequestBody final {ModuleName}Dto request) {
        log.info("REST request to update {moduleName}: {} - {}", id, request);
        return ResponseEntity.ok({moduleName}Service.update{ModuleName}(id, request));
    }

    /**
     * Delete {entity}.
     *
     * @param id the {entity} UUID
     * @return no content
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete{ModuleName}(@PathVariable final UUID id) {
        log.info("REST request to delete {moduleName}: {}", id);
        {moduleName}Service.delete{ModuleName}(id);
        return ResponseEntity.noContent().build();
    }
}
```

---

## 📝 DTO 層範本

```java
package com.nextkey.ecommerce.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * {ModuleName} DTO
 * {Description}
 *
 * @author {Author}
 * @since {YYYY-MM-DD}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {ModuleName}Dto {

    // ========== Fields ==========
    // 示例：private static final int MAX_NAME_LENGTH = 100;

    private UUID id;

    @NotBlank(message = "Name is required")
    private String name;

    @NotNull(message = "Price is required")
    private BigDecimal price;

    private String description;

    private {ModuleName}Status status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ========== Nested Classes ==========

    /**
     * {ModuleName} status enum.
     */
    public enum {ModuleName}Status {
        ACTIVE,
        INACTIVE,
        DELETED
    }

    /**
     * Create request DTO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateRequest {
        @NotBlank(message = "Name is required")
        private String name;

        @NotNull(message = "Price is required")
        private BigDecimal price;

        private String description;
    }

    /**
     * Update request DTO.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRequest {
        private String name;

        private BigDecimal price;

        private String description;

        private {ModuleName}Status status;
    }
}
```

---

## 📝 Entity/Model 範本

```java
package com.nextkey.ecommerce.domain.model.{module};

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {ModuleName} Entity
 * {Description}
 *
 * @author {Author}
 * @since {YYYY-MM-DD}
 */
@Entity
@Table(name = "{table_name}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class {ModuleName} {

    // ========== Constants ==========
    // 示例：private static final int MAX_NAME_LENGTH = 100;

    // ========== ID & Version ==========
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Version
    private Long version;

    // ========== Base Fields ==========
    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    // ========== Tenant & Status ==========
    @Column(nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private {ModuleName}Status status;

    // ========== Timestamps ==========
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ========== Relationships ==========
    // 示例：@OneToMany(mappedBy = "{moduleName}", cascade = CascadeType.ALL)
    // 示例：@Builder.Default
    // 示例：private List<SubEntity> subEntities = new ArrayList<>();

    // ========== Status Enum ==========

    /**
     * {ModuleName} status.
     */
    public enum {ModuleName}Status {
        ACTIVE,
        INACTIVE,
        DELETED
    }

    // ========== Business Methods ==========

    /**
     * Soft delete this {moduleName}.
     */
    public void softDelete() {
        this.status = {ModuleName}Status.DELETED;
    }

    /**
     * Activate this {moduleName}.
     */
    public void activate() {
        this.status = {ModuleName}Status.ACTIVE;
    }

    /**
     * Deactivate this {moduleName}.
     */
    public void deactivate() {
        this.status = {ModuleName}Status.INACTIVE;
    }
}
```

---

## 🎯 關鍵規則摘要

### ✅ Checkstyle 必須遵守

| 規則 | 範例 |
|------|------|
| **FinalParameters** | `public void method(final UUID id)` |
| **MagicNumber** | 使用 `private static final int MAX = 100;` |
| **Javadoc** | 所有 public/protected 方法需要 Javadoc |
| **AvoidStarImport** | `import java.util.List;` 而非 `import java.util.*;` |
| **LineLength** | 每行最多 120 字元 |

### ❌ 禁止使用

| 項目 | 原因 |
|------|------|
| 硬編碼數字 | 難以維護，應使用常數 |
| `catch(Exception e)` | 應捕获特定異常 |
| `try-catch` 無 log | 異常應被記錄 |
| `public` 欄位 | 應使用 getter/setter |

---

**最後更新者**: Claude Code Agent
