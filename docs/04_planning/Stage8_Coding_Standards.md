# Stage 8 開發規範 / Development Standards

> **日期**: 2026-04-10
> **Stage**: 8 - 開發準備與規範制定
> **專案**: E-Commerce Platform (B2B2C Multi-tenant)
> **版本**: v1.0
> **狀態**: Draft

---

## 1. Git Workflow 規範

### 1.1 分支策略

**分支命名慣例**:
```
main                  # 生產環境分支 (protected)
develop               # 開發整合分支 (protected)
feature/{ticket-id}   # 功能分支 (e.g., feature/US-M03-001)
bugfix/{ticket-id}    # Bug 修復分支 (e.g., bugfix/BUG-001)
hotfix/{ticket-id}    # 緊急修復分支 (e.g., hotfix/BUG-002)
release/v{sprint}      # 發布分支 (e.g., release/v1.0)
```

**Ticket ID 格式** (AISDLC v0.09):
| 類型 | 格式 | 範例 |
|------|------|------|
| User Story | US-{Module}-{Sequence} | US-M03-001 |
| Acceptance Criteria | AC-{US}-{Number} | AC-M03-001-1 |
| Bug | BUG-{Sequence} | BUG-001 |
| Technical | TECH-{Sequence} | TECH-001 |

### 1.2 Commit 訊息規範

**格式**:
```
{type}({scope}): {subject}

{body}

{footer}
```

**Type 類型**:
| Type | 說明 |
|------|------|
| feat | 新功能 |
| fix | Bug 修復 |
| docs | 文件更新 |
| style | 程式碼格式 (不影响功能) |
| refactor | 重構 |
| test | 測試相關 |
| chore | 建置/工具變更 |
| perf | 效能優化 |
| ci | CI/CD 變更 |

**範例**:
```
feat(M03): Add member registration endpoint

- Implement BCrypt password hashing
- Add email validation
- Create JWT token on successful registration

Closes: US-M03-001
```

### 1.3 Pull Request 規範

**PR 標題格式**:
```
[{type}] {subject} ({ticket-id})

範例:
[feat] Add member registration (US-M03-001)
[fix] Fix JWT refresh token expiration (BUG-001)
```

**PR 描述模板**:
```markdown
## Summary
<!-- 簡要描述變更 -->

## Changes
- Change 1
- Change 2

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests passed
- [ ] Manual testing completed

## Screenshots (if applicable)
<!-- UI 變更的截圖 -->

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Documentation updated (if needed)
- [ ] No sensitive data committed
```

---

## 2. 前端開發規範 (Frontend)

### 2.1 技術棧

| 技術 | 版本 | 用途 |
|------|------|------|
| Next.js | 15.x | React Framework |
| TypeScript | 5.x | 強型別 |
| Tailwind CSS | 4.x | 樣式框架 |
| Zustand | 4.x | 狀態管理 |
| ESLint | 9.x | 程式碼檢查 |
| Prettier | 3.x | 程式碼格式化 |

### 2.2 目錄結構

```
frontend/
├── src/
│   ├── app/                    # Next.js App Router
│   │   ├── page.tsx           # Page components
│   │   ├── layout.tsx         # Root layout
│   │   └── [route]/           # Dynamic routes
│   ├── components/
│   │   ├── ui/                # Reusable UI components
│   │   ├── features/          # Feature-specific components
│   │   └── layouts/           # Layout components
│   ├── hooks/                 # Custom React hooks
│   ├── lib/                   # Utilities and helpers
│   ├── stores/                # Zustand stores
│   ├── types/                 # TypeScript types
│   └── api/                   # API client functions
├── public/                    # Static assets
├── tests/
│   ├── unit/                 # Unit tests
│   ├── integration/           # Integration tests
│   └── e2e/                  # E2E tests (Playwright)
└── package.json
```

### 2.3 命名規範

| 類型 | 規範 | 範例 |
|------|------|------|
| Components | PascalCase | `MemberRegistration.tsx` |
| Hooks | camelCase + use prefix | `useAuth.ts` |
| Utilities | camelCase | `formatCurrency.ts` |
| Types/Interfaces | PascalCase | `UserProfile.ts` |
| Constants | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| CSS Classes | kebab-case | `text-center` |

### 2.4 程式碼規範

**TypeScript**:
```typescript
// ✅ Good: Explicit types for function parameters
function createUser(name: string, email: string): User {
  return { id: uuid(), name, email };
}

// ❌ Bad: Using 'any' type
function createUser(name: any, email: any): any {
  return { id: uuid(), name, email };
}

// ✅ Good: Interface for object shapes
interface UserProps {
  id: string;
  name: string;
  email: string;
  role: UserRole;
}

// ✅ Good: Union types for states
type OrderStatus = 'CREATED' | 'PAID' | 'SHIPPING' | 'DELIVERED';
```

**React Components**:
```typescript
// ✅ Good: Functional component with explicit props
interface ButtonProps {
  variant: 'primary' | 'secondary';
  children: React.ReactNode;
  onClick?: () => void;
  disabled?: boolean;
}

export function Button({ variant, children, onClick, disabled }: ButtonProps) {
  return (
    <button
      className={cn('btn', `btn-${variant}`)}
      onClick={onClick}
      disabled={disabled}
    >
      {children}
    </button>
  );
}

// ❌ Bad: Prop drilling, any types
function Parent() {
  const data: any = useData();
  return <Child data={data} />;
}
```

### 2.5 Lint 規則

```json
// .eslintrc.json (核心規則)
{
  "extends": ["next/core-web-vitals", "next/typescript"],
  "rules": {
    "no-console": ["warn", { "allow": ["warn", "error"] }],
    "prefer-const": "error",
    "no-var": "error",
    "eqeqeq": "error",
    "@typescript-eslint/no-unused-vars": ["error", { "argsIgnorePattern": "^_" }]
  }
}
```

---

## 3. 後端開發規範 (Backend)

### 3.1 技術棧

| 技術 | 版本 | 用途 |
|------|------|------|
| Spring Boot | 3.2.x | Application Framework |
| Java | 21 | Language |
| Spring Data JPA | 3.2.x | ORM |
| Hibernate | 6.x | ORM Implementation |
| Flyway | 9.x | Database Migration |
| Gradle | 8.x | Build Tool |
| JUnit 5 | 5.x | Testing |

### 3.2 目錄結構 (Clean Architecture)

```
backend/
├── src/main/java/com/nextkey/ecommerce/
│   ├── api/                    # Presentation Layer
│   │   ├── controller/        # REST Controllers
│   │   ├── filter/            # Filters (JWT, Tenant)
│   │   ├── interceptor/       # Interceptors
│   │   └── dto/
│   │       ├── request/       # Request DTOs
│   │       └── response/      # Response DTOs
│   ├── core/                   # Application Layer
│   │   ├── auth/              # M03 Authentication
│   │   ├── listing/          # M01/M02 Unified Listing
│   │   ├── order/            # M05 Order
│   │   ├── pricing/          # M12 Dynamic Pricing
│   │   └── tenant/           # M17 Tenant
│   ├── domain/                 # Domain Layer
│   │   ├── entity/           # JPA Entities
│   │   ├── vo/               # Value Objects
│   │   ├── repository/       # Repository Interfaces
│   │   ├── service/          # Domain Services
│   │   └── event/            # Domain Events
│   ├── infrastructure/        # Infrastructure Layer
│   │   ├── persistence/      # JPA Repositories
│   │   ├── redis/           # Redis Operations
│   │   ├── security/        # Spring Security
│   │   └── config/          # Configurations
│   └── shared/                # Shared Utilities
│       ├── exception/       # Exception Handling
│       ├── constant/         # Constants
│       └── util/             # Utilities
├── src/main/resources/
│   ├── application.yml       # Main config
│   ├── application-dev.yml   # Dev profile
│   ├── application-prod.yml  # Prod profile
│   └── db/migration/        # Flyway migrations
└── src/test/
    ├── unit/                # Unit tests
    └── integration/         # Integration tests
```

### 3.3 命名規範

| 類型 | 規範 | 範例 |
|------|------|------|
| Classes | PascalCase | `MemberController` |
| Methods | camelCase | `findByEmail()` |
| Variables | camelCase | `accessToken` |
| Constants | UPPER_SNAKE_CASE | `MAX_LOGIN_ATTEMPTS` |
| Packages | lowercase | `com.nextkey.ecommerce` |
| Tables | snake_case | `user_profiles` |
| Columns | snake_case | `created_at` |

### 3.4 程式碼規範

**Java 類**:
```java
// ✅ Good: Clear class documentation
/**
 * Member management controller.
 * Handles registration, login, and profile operations.
 *
 * @author Dev Team
 * @since 2026-04-10
 */
@RestController
@RequestMapping("/api/v2/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * Register a new member.
     *
     * @param request Registration request containing email and password
     * @return AuthResponse with access and refresh tokens
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }
}

// ❌ Bad: No documentation, magic values
@RestController
public class AuthController {
    @PostMapping("/reg")
    public ResponseEntity reg(Map<String, Object> req) {
        // ...
    }
}
```

**Entity 規範**:
```java
// ✅ Good: Proper JPA annotations and tenant isolation
@Entity
@Table(name = "listings")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Listing extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false)
    private ListingType listingType;

    @Column(nullable = false, length = 200)
    private String title;

    // Use Lombok for boilerplate
    @Getter
    @Setter
    @Builder
    // ...
}
```

**DTO 規範**:
```java
// ✅ Good: Immutable DTO with validation
public record RegisterRequest(
    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    String email,

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
    String password
) {}

// ✅ Good: Response DTO
public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn
) {}
```

### 3.5 API 規範

**統一回應格式**:
```java
// ✅ Good: Standardized API response
public record ApiResponse<T>(
    int code,
    String message,
    T data,
    String timestamp,
    String requestId
) {
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(
            200,
            "Success",
            data,
            Instant.now().toString(),
            RequestContext.getRequestId()
        );
    }
}
```

**錯誤處理**:
```java
// ✅ Good: Global exception handling
@RestControllerAdvice
@RequiredArgsConstructor
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<ErrorDetail>> handleValidation(
            ValidationException ex) {
        return ResponseEntity
            .badRequest()
            .body(ApiResponse.error("Validation failed", ex.getErrors()));
    }

    @ExceptionHandler(TenantAccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleTenantAccess(
            TenantAccessDeniedException ex) {
        return ResponseEntity
            .status(HttpStatus.FORBIDDEN)
            .body(ApiResponse.error("Access denied"));
    }
}
```

---

## 4. Code Review 規範

### 4.1 Review 檢查清單

**程式碼品質**:
- [ ] 程式碼可讀性良好
- [ ] 命名有意義且一致
- [ ] 沒有重複程式碼 (DRY)
- [ ] 適當的註解（非自我解釋程式碼）
- [ ] 錯誤處理完善
- [ ] 沒有安全漏洞 (OWASP Top 10)

**功能正確性**:
- [ ] 符合需求規格
- [ ] 邊界條件處理
- [ ] 空值檢查
- [ ] 異常情況處理
- [ ] 業務邏輯正確

**技術標準**:
- [ ] 遵循 Coding Standards
- [ ] 單元測試覆蓋 ≥ 80%
- [ ] 效能考量 (N+1 查詢等)
- [ ] 多租戶隔離正確
- [ ] 無硬編碼敏感資訊

### 4.2 Review 流程

```
PR 創建
    │
    ▼
自動檢查 (CI)
├── Lint ✅
├── Unit Tests ✅
├── Coverage ≥ 80% ✅
└── Build Success ✅
    │
    ▼
Code Review
├── 至少 1 人 approval
├── 無 blocking comments
└── 所有 conversation resolved
    │
    ▼
合併 (Merge)
```

### 4.3 Review 註解慣例

```markdown
<!-- Blocking - Must fix before merge -->
[blocker] Critical: SQL injection vulnerability in raw query

<!-- Non-blocking - Suggestion -->
[nit] Consider using Optional instead of null for better readability

<!-- Question -->
[question] Why do we need this additional validation here?

<!-- Praise - Optional but encouraged -->
[praise] Great error handling approach!
```

---

## 5. Testing 標準

### 5.1 測試覆蓋率目標

| 層級 | 目標 | 工具 |
|------|------|------|
| Unit Tests | ≥ 80% | JUnit 5 + Mockito |
| Integration Tests | ≥ 70% | Spring Boot Test |
| API E2E | 100% P0 APIs | REST Assured |
| E2E Tests | 核心場景 | Playwright |

### 5.2 測試命名慣例

```java
// Backend: JUnit 5
class MemberServiceTest {

    @Nested
    @DisplayName("register()")
    class RegisterTests {
        @Test
        @DisplayName("should return tokens when registration succeeds")
        void shouldReturnTokensOnSuccess() {
            // test implementation
        }

        @Test
        @DisplayName("should throw exception when email already exists")
        void shouldThrowExceptionWhenEmailExists() {
            // test implementation
        }
    }
}
```

```typescript
// Frontend: Jest
describe('useAuth', () => {
  describe('register', () => {
    it('should return tokens when registration succeeds', async () => {
      // test implementation
    });

    it('should throw error when email already exists', async () => {
      // test implementation
    });
  });
});
```

### 5.3 測試資料管理

```java
// Backend: Test fixtures
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MemberRepositoryTest {

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private Flyway flyway;

    @BeforeAll
    void init() {
        flyway.migrate();
    }

    private Member createTestMember(String email) {
        return Member.builder()
            .email(email)
            .passwordHash(bcrypt.encode("password"))
            .role(Role.BUYER)
            .status(MemberStatus.ACTIVE)
            .build();
    }
}
```

---

## 6. 環境變數管理

### 6.1 環境變數命名

**後端 (Spring Boot)**:
```bash
# 格式: UPPER_SNAKE_CASE with prefix
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/ecommerce
SPRING_DATASOURCE_USERNAME=koala
SPRING_DATASOURCE_PASSWORD=koala5
JWT_SECRET=your-256-bit-secret
REDIS_HOST=localhost
REDIS_PORT=6379
```

**前端 (Next.js)**:
```bash
# 格式: NEXT_PUBLIC_{name} for client-exposed
NEXT_PUBLIC_API_URL=http://localhost:8080/api/v2
NEXT_PUBLIC_APP_ENV=development
```

### 6.2 Secrets 管理

| 環境 | 儲存方式 |
|------|----------|
| Local Dev | .env.local (不提交) |
| CI/CD | GitHub Secrets |
| Staging | AWS Secrets Manager |
| Production | AWS Secrets Manager |

---

## 7. 驗收標準

| 項目 | 驗收標準 |
|------|----------|
| Git Workflow | 分支命名和 commit 格式符合規範 |
| 前端規範 | ESLint + Prettier 配置正確 |
| 後端規範 | Checkstyle 配置正確 |
| Code Review | 所有 PR 經過 review 並獲得 approval |
| 測試覆蓋 | 單元測試覆蓋率 ≥ 80% |

---

## 📁 相關文件

| 文件 | 路徑 |
|------|------|
| Stage 6 Sprint 規劃 | `docs/04_planning/Stage6_Sprint_Planning.md` |
| SRD 系統架構 | `docs/02_architecture/SRD_System_Architecture.md` |
| API 規格 | `docs/02_architecture/api/` |
| 測試策略 | `docs/04_planning/Stage6_Test_Strategy.md` |

---

**文件結束**
