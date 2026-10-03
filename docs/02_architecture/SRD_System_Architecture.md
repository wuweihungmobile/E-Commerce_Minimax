# E-Commerce System — 系統需求文檔 (SRD) v1.3

> **文檔類型**: SRD (System Requirements Document)
> **版本**: v1.3（v1.0 → v1.1：Sprint 203 文件一致性檢查；v1.1 → v1.2：Sprint 204 新增 §5.5；v1.2 → v1.3：Sprint 206 §5.4.1 連接器層錯誤，見 §9 修訂歷史）
> **依據**: E-Commerce_PRD_v1.0_Final.md, E-Commerce_FRD_v1.0.md
> **建立日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)
> **AISDLC 版本**: v0.09

---

## 📋 文檔元數據

| 項目 | 內容 |
|-----|------|
| **專案名稱** | E-Commerce B2B2C 多租戶電子商務平台 |
| **系統類型** | B2B2C 多租戶電子商務 + 民宿預訂系統 |
| **文檔狀態** | Draft |
| **System Architect** | Marcus (SD-Architect) |
| **最後更新** | 2026-10-03 |

---

## 📌 文檔追蹤

### 上游文檔
- **PRD 連結**: [E-Commerce_PRD_v1.0_Final.md](../01_requirements/E-Commerce_PRD_v1.0_Final.md)
- **FRD 連結**: [E-Commerce_FRD_v1.0.md](../01_requirements/E-Commerce_FRD_v1.0.md)

### 下游文檔
- **API 規格**: [docs/02_architecture/API_Index.md](./API_Index.md)
- **API 錯誤契約與錯誤碼**: [API_Error_Codes.md](./API_Error_Codes.md)
- **AT 連結**: [docs/03_testing/](../03_testing/) (驗收測試)

---

## 1. 技術架構總覽 (Technical Overview)

### 1.1 系統架構圖

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         B2B2C Platform                              │
│                                                                      │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    Frontend (Next.js 15)                    │   │
│  │   React 18 + TypeScript + Tailwind CSS + Zustand            │   │
│  │   Shadcn UI + Radix UI ( Accessible UI Components )           │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                   │                                  │
│                                   ▼                                  │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      API Gateway (Spring Boot)                  │   │
│  │                    /api/v2/* → Multi-Tenant Aware            │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                   │                                  │
│         ┌─────────────────────────┼─────────────────────────┐       │
│         ▼                         ▼                         ▼       │
│  ┌─────────────┐           ┌─────────────┐           ┌─────────┐  │
│  │  Retail     │           │  Booking    │           │  Platform│  │
│  │  Engine     │           │  Engine     │           │  Infra  │  │
│  │  (M01,M05)  │           │  (M02,M06)  │           │ (M03,M17)│  │
│  └─────────────┘           └─────────────┘           └─────────┘  │
│         │                         │                         │       │
│         │         ┌──────────────┘                         │       │
│         │         ▼                                        │       │
│         │  ┌─────────────────┐                             │       │
│         │  │  Dynamic        │                             │       │
│         │  │  Pricing (M12)  │                             │       │
│         │  └─────────────────┘                             │       │
│         │                                                    │       │
│         └──────────────────────┬───────────────────────────┘       │
│                                ▼                                    │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                    PostgreSQL 18 (Shared Schema)             │   │
│  │              Tenant ID Isolation + Hibernate Filter          │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                │                                    │
│                                ▼                                    │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │                      Redis 7 (Cache Layer)                   │   │
│  │         Cart / Distributed Lock / Rate Limit / Pricing        │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.2 後端分層架構（Clean Architecture + DDD + Multi-Tenancy）

```
com.nextkey.ecommerce/
├── api/                                    # Presentation Layer
│   ├── controller/
│   │   ├── storefront/                    # C 端前台 API (/api/v2/listings)
│   │   ├── dashboard/                     # B 端店鋪 API (/api/v2/dashboard/*)
│   │   └── admin/                         # 平台管理 API (/api/v2/admin/*)
│   ├── filter/
│   │   ├── JwtAuthFilter.java             # JWT 認證過濾器
│   │   └── TenantContextFilter.java        # ★ 租戶上下文注入
│   ├── interceptor/                        # 攔截器
│   └── dto/
│       ├── request/                       # 請求 DTO
│       └── response/                      # 回應 DTO
│
├── core/                                   # Application Layer (Use Cases)
│   ├── auth/                              # M03 認證服務
│   ├── listing/                           # ★ 統一商品/房源 Use Cases
│   ├── retail/                           # M01 零售特化邏輯
│   ├── booking/                           # M02/M06 預訂特化邏輯
│   ├── order/                             # M05 訂單履約
│   ├── pricing/                           # ★ M12 動態定價引擎
│   └── tenant/                            # M17 租戶管理
│
├── domain/                                 # Domain Layer
│   ├── entity/                            # 領域實體
│   ├── vo/                                # Value Objects
│   ├── repository/                        # Repository 介面
│   ├── service/                           # Domain Services
│   └── event/                             # Domain Events
│
├── infrastructure/                         # Infrastructure Layer
│   ├── persistence/                       # JPA Repository 實作
│   │   ├── adapter/                       # Repository 介面卡
│   │   └── repository/                   # JPA Repository
│   ├── redis/                             # Redis 客戶端
│   ├── security/                          # Spring Security 配置
│   └── config/                           # 各類設定
│
└── shared/                                 # Shared Utilities
    ├── exception/                         # 統一異常處理
    ├── constant/                          # 常數定義
    ├── util/                              # 工具類
    └── dto/                               # 共用 DTO
```

### 1.3 技術選型

| 層級 | 技術 | 版本 | 備註 |
|------|------|------|------|
| **前端框架** | Next.js 15 | 15.x | App Router + SSR + SSG |
| **前端 UI 框架** | React | 18.x | 元件化開發 |
| **前端 UI 組件庫** | Shadcn UI + Radix UI | 最新 | Accessible + 無頭組件 |
| **前端語言** | TypeScript | 5.x | 強型別 |
| **前端樣式** | Tailwind CSS | 3.x | Utility-First |
| **前端狀態** | Zustand | 4.x | 輕量狀態管理 |
| **後端框架** | Spring Boot | 3.2.x | Java 21 |
| **後端 ORM** | Spring Data JPA | 3.2.x | Hibernate 6.x |
| **資料庫遷移** | Flyway | 9.x | Versioned Migration |
| **資料庫** | PostgreSQL | 16.x | Shared Schema |
| **快取** | Redis | 7.x | 分散式鎖/限流/定價快取 |
| **API 文件** | SpringDoc OpenAPI | 2.x | Swagger UI |
| **建置工具** | Gradle (Kotlin DSL) | 8.x | 快速建置 |
| **測試框架** | JUnit 5 + Mockito | 5.x | 單元測試 |
| **容器化** | Docker + Docker Compose | 24.x | 開發環境 |
| **CI/CD** | GitHub Actions | — | 自動化建置測試 |

---

## 2. 多租戶架構 (Multi-Tenancy)

### 2.1 租戶隔離策略

**採用策略**: Shared Schema + Tenant ID + Hibernate Filter

| 策略 | 優點 | 缺點 | 適用場景 |
|------|------|------|----------|
| **Shared Schema + Tenant ID** | 成本低、易維護 | 需注意索引設計 | Phase 1中小規模 |
| Separate Database | 隔離性最好 | 成本高、管理複雜 | 大型客戶 |
| Separate Schema | 平衡方案 | 中等成本 | 中大型多租戶 |

### 2.2 TenantContext 流程

```
┌──────────┐     ┌─────────────────┐     ┌──────────────────┐     ┌───────────┐
│ API      │ ──▶ │ JwtAuthFilter   │ ──▶ │TenantContextFilter│ ──▶ │ Controller│
│ Request  │     │ (驗證 JWT)      │     │ (注入 TenantCtx)  │     │ (業務)   │
└──────────┘     └─────────────────┘     └──────────────────┘     └───────────┘
                                                                    │
    ┌───────────────────────────────────────────────────────────────┘
    ▼
┌──────────────────────────────────────────────────────────────┐
│                  Hibernate Filter                            │
│  @FilterDef(name="tenantFilter", ...)                       │
│  @Filter(name="tenantFilter", condition="tenant_id = :tid")  │
└──────────────────────────────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────────────────────────────┐
│              自動注入 WHERE tenant_id = :tid                 │
└──────────────────────────────────────────────────────────────┘
```

### 2.3 資料隔離實現

**Hibernate Filter 配置**:
```java
@Entity
@Table(name = "listings")
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = String.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class Listing {
    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;
    // ...
}
```

**Repository 層**:
```java
@Repository
public interface ListingRepository extends JpaRepository<Listing, UUID> {
    
    @Query("SELECT l FROM Listing l WHERE l.status = 'ACTIVE'")
    List<Listing> findActiveListings(); // Hibernate 自動注入 TenantContext
}
```

---

## 3. 統一商品/服務模型 (Unified Listing Model)

### 3.1 統一 Listing 抽象

```
┌─────────────────────────────────────────────┐
│           listings (統一抽象表)             │
├─────────────────────────────────────────────┤
│ id: UUID (PK)                               │
│ tenant_id: UUID (FK)                        │
│ listing_type: ENUM('PRODUCT', 'ROOM')       │
│ title: VARCHAR(200)                          │
│ description: TEXT                           │
│ cover_image_url: VARCHAR(500)                │
│ status: ENUM('DRAFT','ACTIVE','INACTIVE')   │
│ owner_id: UUID (FK → users.id)              │
│ base_price: DECIMAL(12,2)                   │
│ currency: VARCHAR(3)                        │
│ tags: TEXT[]                                │
│ created_at: TIMESTAMP                       │
│ updated_at: TIMESTAMP                       │
└─────────────────────────────────────────────┘
                      │
         ┌────────────┴────────────┐
         ▼                         ▼
┌─────────────────────┐   ┌─────────────────────┐
│    products         │   │       rooms         │
│  (PRODUCT 特有)     │   │   (ROOM 特有)       │
├─────────────────────┤   ├─────────────────────┤
│ listing_id: FK      │   │ listing_id: FK      │
│ category            │   │ location            │
│ brand               │   │ latitude/longitude │
│ weight/dimensions   │   │ max_guests          │
└─────────────────────┘   │ amenities          │
                          │ check_in/out_time  │
                          └─────────────────────┘
```

### 3.2 設計理由

| 設計決策 | 理由 |
|---------|------|
| **單一 listings 表** | 減少冗餘，查詢效率高 |
| **listing_type 區分** | 支援零售(PRODUCT)和民宿(ROOM) |
| **統一 base_price** | 動態定價可複用相同邏輯 |
| **Product/Room 特化表** | 僅儲存各自特有的欄位 |

---

## 4. API 設計

### 4.1 API 版本策略

| 策略 | 說明 |
|------|------|
| **路徑版本** | `/api/v2/{resource}` |
| **API 版本升級時機** | Breaking Changes 發生時 |
| **向下相容** | 舊版本至少維護 6 個月 |

### 4.2 API 統一回應格式

所有回應使用**扁平封包** `ApiResponse`（成功與失敗同形，沒有巢狀的 `error` 包裹）。欄位定義見 [API_Error_Codes.md §1](./API_Error_Codes.md)。

```json
{
  "success": true,
  "data": { ... },
  "timestamp": "2026-09-26T10:30:00Z"
}
```

成功回應**不帶** `requestId`（成功 payload 是前端已依賴的契約，不因追蹤功能多出欄位）；值為 `null` 的欄位不出現在 JSON。

### 4.3 錯誤回應格式

```json
{
  "success": false,
  "code": "E-5000",
  "message": "找不到訂單",
  "timestamp": "2026-09-26T10:30:00Z",
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

欄位級驗證錯誤（HTTP 400、`code` 為 `E-9000`）另帶 `errors`，每筆 `{ "field", "message", "rejectedValue" }`。`requestId` 與回應標頭 `X-Request-ID` 相同。

**錯誤碼、HTTP 狀態碼對照、各類例外的對應方式**，以 [API_Error_Codes.md](./API_Error_Codes.md) 為準；該文件**取代** v1.0 在此處的舊格式（`code` 為數字、`errors[].code`），以及 PRD §9.17／§16／§17.2 的巢狀格式與錯誤碼表（Sprint 203，DEF-280）。

### 4.4 時間與時區慣例

| 項目 | 規則 |
|------|------|
| API 時間戳（`timestamp` 等 `Instant` 欄位） | ISO 8601、UTC（結尾 `Z`） |
| 營運時區 | **Asia/Taipei（UTC+8）**，PRD／FRD 明訂。由 `shared/time/BusinessTime` 提供「營運時區的今天／現在」與「營運日 00:00 對應的絕對時刻」 |
| 適用邏輯 | 判斷**資格、有效期、價格適用日、統計與結算區間**的邏輯，一律使用營運時區，不取 JVM 預設時區。CI 與正式容器（`eclipse-temurin:21-jre-alpine`，未設 `TZ`）是 UTC、開發機是 Asia/Taipei，取預設時區會使結果隨部署環境不同（DEF-269：台灣時間每天 00:00～08:00 「今天」變成前一天）。目前使用 `BusinessTime` 的類別：`PricingService`、`PromoCode`、`BookingService`、`RedisCartService`、`AnalyticsService`、`SettlementGenerator` |
| 期間表示 | 以絕對時刻的**半開區間** `[start, end)`：`BusinessTime.startOfDay(d)` 與 `startOfDay(d.plusDays(1))` 組成一日，相鄰兩日／兩週共用同一個邊界時刻，不重疊也不留縫 |
| 排程 | 週結算為 `@Scheduled(cron = "0 0 0 ? * MON", zone = "Asia/Taipei")`；未指定 `zone` 時 Spring 以 JVM 時區解讀 cron |
| 不在此列 | 僅用於稽核戳記、單號日期字串等不影響金額的 `LocalDate.now()`／`LocalDateTime.now()` |

---

## 5. 安全架構 (Security Architecture)

### 5.1 認證機制

> **⚠️ 修訂註記（Sprint 244，2026-10-03）**：本節原寫「Access 30 分鐘、Refresh 30 天、HttpOnly Cookie、Payload 含 `roles` 陣列」，與實作不符，已依程式碼改寫（DEF-323 (b)）。

**JWT 雙令牌機制**（`infrastructure/security/JwtTokenService`、`core/auth/AuthService`）:
- Access Token: **15 分鐘**（設定 `jwt.access-token-expiration`，預設 `900000` ms）
- Refresh Token: **預設 7 天**（設定 `jwt.refresh-token-expiration`，預設 `604800000` ms；`docker-compose.yml` 設為 30 天）。每次換發都會輪替；舊的 Refresh Token 再次使用會觸發重放偵測（Sprint 213／230）。登出會讓該 Token 失效；重設密碼會讓該會員的所有 Refresh Token 失效（§5.5）。
- **沒有 Cookie**：後端不設定也不讀取任何 Cookie。登入與換發回應以 JSON 回傳 Token，`expiresIn` 單位是**毫秒**。前端存於 `localStorage`（鍵名 `accessToken`、`refreshToken`，見 `frontend/src/lib/axios.ts`）。
- 因為 Token 存在 `localStorage`，XSS 是最主要的威脅，所以 §5.4 採用嚴格 nonce CSP。

**Token Payload**（實際 claims；`sub` 為使用者 UUID，`role` 是單一字串，為簽發當時的**有效角色**，見 `AuthService.effectiveRole`、DEF-326）:
```json
{
  "sub": "user-uuid",
  "email": "user@example.com",
  "role": "BUYER",
  "tenantId": "tenant-uuid",
  "iat": 1712640000,
  "exp": 1712640900
}
```

### 5.2 授權控制

**RBAC 角色層級**:
```
SuperAdmin
    └── Admin
          └── StoreOwner
                └── StoreStaff
                      └── Seller / Host
                            └── Buyer
                                  └── Guest
```

### 5.3 安全防護

| 防護機制 | 實作 |
|---------|------|
| **SQL Injection** | JPA PreparedStatement |
| **XSS** | 輸出編碼 + CSP Header（後端與前端各一份政策，見 §5.4） |
| **CSRF** | SameSite Cookie + CSRF Token |
| **Rate Limiting** | Redis + 令牌桶演算法 |
| **密碼儲存** | bcrypt (cost factor = 12) |
| **回應安全標頭、請求追蹤、CORS** | 見 §5.4 |

### 5.4 回應安全標頭、請求追蹤與 CORS（Sprint 197～202、191）

#### 5.4.1 後端回應（`SecurityHeaderPolicy`，唯一定義處）

後端只回 JSON（無 HTML 頁），政策為：

| 標頭 | 值 | 備註 |
|------|----|------|
| `X-Content-Type-Options` | `nosniff` | |
| `X-Frame-Options` | `DENY` | |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'` | 純 JSON 回應，不需載入任何資源 |
| `Referrer-Policy` | `no-referrer` | |
| `Strict-Transport-Security` | `max-age=31536000` | **只在 HTTPS 請求送出**：`request.isSecure()`，或 `X-Forwarded-Proto` 第一段（多層代理 `https,http`）為 `https`（不分大小寫）。**不含 `includeSubDomains`、不含 `preload`**：子網域是否都能走 https 無從驗證，而該指示送出後一年內無法收回 |
| `Cache-Control`／`Pragma`／`Expires`、`X-XSS-Protection: 0` | Spring Security 預設值 | |

**單一來源**：政策只定義在 `SecurityHeaderPolicy`，兩處套用同一份——`SecurityConfig`（Spring Security 的 `HeaderWriterFilter`，涵蓋一般請求的回應）與 `ApiErrorController`（Servlet 容器的 ERROR 分派；`HeaderWriterFilter` 沿用 `OncePerRequestFilter` 預設而不處理該分派，防火牆拒絕的請求原本因此完全沒有安全標頭，DEF-282）。日後改任何標頭只改 `SecurityHeaderPolicy`。

**Tomcat 連接器層的拒絕**（Sprint 206，DEF-283）：路徑含 `%2f`、`%5C`、無效百分比編碼或超長的請求，由 Tomcat 在進入 Servlet 前拒絕，原本回 HTML 錯誤頁、無任何標頭。`ApiErrorReportValve` 只換掉 Host 上寫錯誤頁的 valve，改回 JSON 封包、`X-Request-ID` 與同一份 `SecurityHeaderPolicy`，**不放寬任何 Tomcat 的拒絕規則**；封包對應只有一份（`ContainerErrorResponse`，與 `ApiErrorController` 共用）。細節見 [API_Error_Codes.md §2.2](./API_Error_Codes.md)。

#### 5.4.2 前端頁面（Next.js）

| 來源 | 標頭 | 備註 |
|------|------|------|
| `next.config.ts` | `X-Frame-Options: DENY`、`X-Content-Type-Options: nosniff`、`Referrer-Policy: strict-origin-when-cross-origin`；`poweredByHeader: false` | |
| `next.config.ts` | `Strict-Transport-Security: max-age=31536000` | 只在請求帶 `X-Forwarded-Proto: https`（第一段、不分大小寫）時送出；判斷與值與後端一致，同樣不含 `includeSubDomains`／`preload` |
| `src/proxy.ts` | `Content-Security-Policy`（**每次請求一個 nonce**） | `script-src 'self' 'nonce-…' 'strict-dynamic'`（開發模式另加 `'unsafe-eval'`）；`style-src 'self' 'unsafe-inline'`；`img-src 'self' data: blob: https:`（後端為明文 http 時另加 `http:`）；`font-src 'self' data:`；`connect-src 'self'` 加後端 HTTP／WS 來源；`object-src 'none'`；`base-uri 'self'`；`form-action 'self'`；`frame-ancestors 'none'`；刻意不加 `upgrade-insecure-requests`（http 部署會把打向後端的請求也升級而全部失敗） |

- **為何用嚴格 nonce CSP**：token 存在 `localStorage`，XSS 一旦成功即可竊取 token；nonce ＋ `strict-dynamic` 使被注入的 inline script、inline 事件處理器與外部 script 都無法執行。
- **代價**：所有頁面必須動態渲染（每次請求一個新 nonce），無法靜態預先產生。
- **新增 inline script 或第三方 script 前**必須先確認 CSP 是否放行，否則會被瀏覽器擋下。
- **緊急開關**：執行環境設 `CSP_REPORT_ONLY=1`，改送 `Content-Security-Policy-Report-Only`（只回報不攔截；瀏覽器主控台仍列出違規），用於部署後發現 CSP 擋到未預期資源時先恢復功能，不必改程式。

#### 5.4.3 請求追蹤（`X-Request-ID`）

`RequestIdFilter` 排在過濾鏈最前端（早於 Spring Security），每個回應都帶 `X-Request-ID` 並寫入日誌 MDC，讓使用者回報的 ID 直接對到後端日誌。沿用呼叫端帶入的值，但只接受 `[A-Za-z0-9._-]{1,64}`，否則重新產生 UUID——該值會被印進日誌，不驗證的話呼叫端可帶入換行字元偽造日誌行。詳見 [API_Error_Codes.md §3](./API_Error_Codes.md)。

#### 5.4.4 CORS

前端來源由 `app.cors.allowed-origins` 設定，執行環境以 **`APP_CORS_ALLOWED_ORIGINS`**（逗號分隔）覆寫，預設 `http://localhost:3000,http://localhost:8080`。因 `allowCredentials=true`，**不可使用 `*`**（Spring 會拒絕這個組合，由 `SecurityConfigCorsOriginsTest` 釘住）。非 localhost 部署（前端以 `NEXT_PUBLIC_API_URL` 指向正式後端網域並由瀏覽器跨源直連）必須設定此變數，否則所有 API 呼叫被 CORS 封鎖（DEF-265）。允許方法：`GET／POST／PUT／PATCH／DELETE／OPTIONS`；允許請求標頭：`Authorization`、`Content-Type`、`X-Tenant-ID`、`Idempotency-Key`；暴露回應標頭：`Authorization`、`X-RateLimit-*`、`Retry-After`、`X-Request-ID`。

### 5.5 一次性連結：忘記密碼與 Email 驗證（Sprint 204，DEF-252／253）

需求見 PRD §7.4.2、FRD US-M03-006／007／BR-M03-003；端點見 [API_M03_Auth.md](./api/API_M03_Auth.md) §6～§9。

| 元件 | 位置 | 職責 |
|------|------|------|
| `AccountTokenService` | `infrastructure/security` | 簽發與消耗一次性 token，存於 **Redis**（無新資料表、無 Flyway 遷移） |
| `AccountSecurityService` | `core/auth` | 忘記密碼／重設密碼／Email 驗證／開店申請前置條件的流程 |
| `EmailSender` | `infrastructure/email` | 寄信介面；`canDeliver()` 表示是否真能寄出信 |
| `LoggingEmailSender` | `infrastructure/email` | 日誌型；`SMTP_USERNAME` 未設定時使用（非 prod 記全文、prod 不記內容） |
| `SmtpEmailSender` | `infrastructure/email` | Google Workspace SMTP（Sprint 209）；`SMTP_USERNAME` 有值時 `@Primary` 取代日誌型；尚未對真實伺服器實測 |

**Token 設計（為什麼放 Redis）**：token 天生短命（重設 30 分鐘、驗證 24 小時）、要能原子地「取出並作廢」、過期就該消失——正是 TTL＋`GETDEL` 擅長的。Redis 被清空的代價只是使用者重新申請一次連結。

| 性質 | 做法 |
|------|------|
| 不可猜測 | 32 位元組 `SecureRandom`，Base64URL |
| 不存原文 | Redis 只存 SHA-256；key 為 `account_token:{用途}:{雜湊}` → 會員 id |
| 一次性、原子 | 消耗用 `GETDEL`：兩個併發請求只有一個成功（16 執行緒實測恰好 1 個） |
| 每（用途, 會員）一個有效連結 | `account_token_latest:{用途}:{會員 id}` 指向目前的雜湊；簽發新的會作廢舊的 |
| 寄信冷卻 | `account_token_cooldown:{用途}:{會員 id}`，`SET NX EX 60` |
| 過期 | 每個 key 皆有 TTL，不超過該用途的有效期 |

**不揭露帳號是否存在**：`POST /password/forgot` 的回應與 Email 是否存在、是否冷卻中、寄信是否成功完全無關；Controller 也不把 Email 寫進日誌。**已知限制**：存在的帳號多做 Redis 與寄信，回應時間有差異；Mock 是即時的所以可忽略，**接上會阻塞的真實寄信服務時須改為非同步寄送**，否則成為時序側通道。

**連結網址**：`{app.frontend-base-url}/reset-password?token=…`、`/verify-email?token=…`。`app.frontend-base-url` 預設 `http://localhost:3000`，非 localhost 部署必須以環境變數 `APP_FRONTEND_BASE_URL` 設定（Stripe 導回網址也用同一個設定）。

**寄信通道與 `canDeliver()`**：
- 非 `prod` profile：`LoggingEmailSender` 把信件全文（含連結）寫進日誌，`canDeliver() = true`。開發者與 E2E 從日誌取連結（E2E 見 `frontend/e2e/helpers/mailbox.ts`，日誌路徑由環境變數 `E2E_BACKEND_LOG` 提供）。
- `prod` profile：**不記錄內容**（連結等同密碼重設憑證，任何能讀日誌的人都能接管帳號），`canDeliver() = false`，啟動時記 WARN。
- **開店申請的 Email 驗證前置條件只在 `canDeliver() = true` 時生效**：沒有信可寄就無從驗證，強制檢查只會鎖死所有申請者。接上真實寄信服務（新增 `EmailSender` 實作並標 `@Primary`，`canDeliver()` 回 `true`）後自動生效。
- **真實寄信（`SmtpEmailSender`，Sprint 209）已接線**：`@Primary`，只在 `spring.mail.username`（環境變數 `SMTP_USERNAME`）有值時建立（`@ConditionalOnExpression`，不用 `@ConditionalOnProperty`，見該類別註解）。寄件位址預設沿用 `SMTP_USERNAME`，可由 `SMTP_FROM_ADDRESS` 覆寫。啟用時 `canDeliver()` 為 `true`。
- **目前的部署狀態**：`docker-compose.yml` 傳遞 `SMTP_*`（Sprint 244 收尾追加，DEF-322），但 `SMTP_USERNAME` 預設為空，所以預設的 `prod` 仍是 `canDeliver() = false`。**`SmtpEmailSender` 尚未對真實 Google Workspace SMTP 伺服器實測過**（Sprint 209 紀錄）。
- **在完成實測並把 `SMTP_*` 設到正式環境之前，這兩項功能不可宣告可在正式環境上線。**
- **啟用前必修**：`SmtpEmailSender` 目前是同步寄送（忘記密碼端點在請求內呼叫 `send`），啟用後回應時間會洩漏帳號是否存在（DEF-347）。S204 登記的「改為非同步寄送」條件已成立。

**與既有機制的關係**：重設密碼成功會呼叫 `RefreshTokenService.blacklistAllRefreshTokens` 並解除 `LoginAttemptService` 的登入鎖定；四個端點都納入 `LoginRateLimitFilter`（每來源 IP、每路徑 30 次／分鐘）；三個公開端點在 `SecurityConfig` 為 `permitAll`。

---

## 6. 資料模型 (Data Model)

### 6.1 核心資料表

| 表格名 | 說明 | Phase |
|--------|------|-------|
| `tenants` | 租戶/店鋪主檔 | Phase 1 |
| `tenant_members` | 租戶成員關聯 | Phase 1 |
| `tenant_feature_toggles` | 功能開關 | Phase 1 |
| `users` | 使用者主檔 | Phase 1 |
| `user_profiles` | 使用者拡張檔 | Phase 1 |
| `listings` | 統一商品/房源 | Phase 1 |
| `products` | 商品特化資料 | Phase 1 |
| `product_skus` | SKU 規格 | Phase 2 |
| `product_inventory` | 庫存管理 | Phase 1 |
| `rooms` | 房源特化資料 | Phase 1 |
| `room_calendar` | 日曆可用性 | Phase 1 |
| `orders` | 訂單主檔 | Phase 1 |
| `order_items` | 訂單明細 | Phase 1 |
| `order_state_log` | 狀態異動日誌 | Phase 1 |
| `pricing_rules` | 動態定價規則 | Phase 1 |

### 6.2 詳細 ERD（實體關係圖）

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              E-Commerce 系統 ERD                              │
├─────────────────────────────────────────────────────────────────────────────────┤
│                                                                                 │
│  ┌──────────────┐       ┌──────────────────┐       ┌──────────────┐           │
│  │   tenants    │       │ tenant_members   │       │    users    │           │
│  ├──────────────┤       ├──────────────────┤       ├──────────────┤           │
│  │ id (PK)     │◄──────│ tenant_id (FK)  │       │ id (PK)     │           │
│  │ store_name  │       │ user_id (FK)     │◄──────│ email       │           │
│  │ status      │       │ role             │       │ status      │           │
│  │ business_   │       └──────────────────┘       └──────┬───────┘           │
│  │ type        │                                        │                    │
│  └──────┬───────┘                                        │ 1:N                │
│         │                                                 ▼                    │
│         │ 1:N                  ┌──────────────────┐       ┌──────────────┐     │
│         ▼                      │ tenant_feature_  │       │ user_       │     │
│  ┌──────────────┐              │ toggles         │       │ profiles    │     │
│  │   listings   │              ├──────────────────┤       ├──────────────┤     │
│  ├──────────────┤              │ tenant_id (FK)  │       │ user_id (PK)│     │
│  │ id (PK)     │              │ feature_key     │       │ display_    │     │
│  │ tenant_id   │◄─────────────│ is_enabled      │       │ name        │     │
│  │ listing_type│              └──────────────────┘       └──────────────┘     │
│  │ title       │                                                               │
│  │ base_price  │              ┌──────────────────┐                             │
│  └──────┬───────┘              │  refresh_tokens │                             │
│         │                      ├──────────────────┤                             │
│         │ 1:1                  │ user_id (FK)    │                             │
│    ┌────┴────┐                │ token_hash      │                             │
│    ▼         ▼                └──────────────────┘                             │
│ ┌──────┐ ┌────────┐                                                          │
│ │products│ │ rooms  │                                                          │
│ ├───────┤ ├────────┤                                                          │
│ │listing │ │listing │                                                          │
│ │_id(FK)│ │_id(FK) │                                                          │
│ └───────┘ └───┬────┘                                                          │
│               │ 1:N                                                           │
│               ▼                                                                │
│      ┌──────────────────┐                                                     │
│      │  room_calendar   │                                                     │
│      ├──────────────────┤                                                     │
│      │ listing_id (FK)  │                                                     │
│      │ calendar_date    │                                                     │
│      │ status           │                                                     │
│      └──────────────────┘                                                     │
│                                                                                 │
│  ┌──────────────┐       ┌──────────────────┐       ┌──────────────┐          │
│  │   orders     │       │   order_items   │       │ pricing_     │          │
│  ├──────────────┤       ├──────────────────┤       │ rules        │          │
│  │ id (PK)     │◄──────│ order_id (FK)   │       ├──────────────┤          │
│  │ tenant_id   │       │ listing_id (FK)  │       │ tenant_id   │          │
│  │ user_id     │       └──────────────────┘       │ listing_id  │          │
│  │ status      │                                 └──────┬───────┘          │
│  │ total_amount│                                         │                   │
│  └──────┬───────┘                                         │                   │
│         │                                                 ▼                   │
│         │ 1:N              ┌──────────────────┐     ┌──────────────┐         │
│         └─────────────────>│ order_state_log │     │pricing_over  │         │
│                            ├──────────────────┤     │ rides        │         │
│                            │ order_id (FK)   │     ├──────────────┤         │
│                            │ from_status     │     │ listing_id   │         │
│                            │ to_status       │     │ override_date│         │
│                            └──────────────────┘     └──────────────┘         │
│                                                                                 │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### 6.3 狀態機定義

#### 6.3.1 訂單狀態流轉

> **⚠️ 修訂註記（Sprint 244，2026-10-03）**：舊圖的 `CREATED(=PAID)`（Phase 1 Payment Mock 建單即付款）與 `DELIVERED → REFUNDING`（買家申請退款）與實際不符，已依 `core/order/OrderStateMachine.java` 的 `canTransition` 重繪。實作中建單後停在 `CREATED`，付款成功才轉為 `PAID`。

```
CREATED ──付款成功──▶ PAID ──店家確認──▶ CONFIRMED ──出貨──▶ SHIPPING ──送達──▶ DELIVERED ──完成──▶ COMPLETED（終態）
   │                   │                    │
   └──────────── 取消（SHIPPING 之後不可取消）────────────┘
                       ▼
                   CANCELLED ──（已付款者）──▶ REFUNDING ──退款完成──▶ REFUNDED（終態）
```

| 從 | 到 | 觸發 | 實作位置 |
|----|----|------|----------|
| `CREATED` | `PAID` | 付款成功：Mock `POST /v2/orders/{orderId}/pay`，或 Stripe 入帳 | `PaymentStateService` |
| `CREATED`／`PAID`／`CONFIRMED` | `CANCELLED` | 買家或店家（含管理員）取消；未付款超過 24 小時自動取消 | `OrderService`、`OrderTimeoutService` |
| `PAID` | `CONFIRMED` | 店家確認（`PATCH /v2/orders/{orderId}/status`） | `OrderStateMachine` |
| `CONFIRMED` | `SHIPPING` | 店家出貨（Sprint 218 起出貨時才扣庫存） | `OrderStateMachine` |
| `SHIPPING` | `DELIVERED` | 送達；出貨後不可取消 | `OrderStateMachine` |
| `DELIVERED` | `COMPLETED` | 完成（終態） | `OrderStateMachine` |
| `CANCELLED` | `REFUNDING` | 已付款的取消單進入退款（取消補償） | `OrderService` |
| `REFUNDING` | `REFUNDED` | 退款完成（管理員退款或自動退款排程） | `RefundProcessingService`、`PaymentStateService` |

`canPay` 只在 `CREATED` 成立；`canCancel` 為 `CREATED`／`PAID`／`CONFIRMED`。`COMPLETED` 與 `REFUNDED` 為終態。API 與權限見 [API_M05_Order.md](./api/API_M05_Order.md) §5。

#### 6.3.2 Listing 狀態流轉

```
       DRAFT ──────► ACTIVE ──────► INACTIVE
         │             │               │
         ▼             ▼               ▼
      DELETED       DELETED         DELETED
```

#### 6.3.3 租戶/店鋪狀態流轉

```
       PENDING ──────► APPROVED ──────► ACTIVE
           │              │               │
           ▼              ▼               ▼
       REJECTED      SUSPENDED        SUSPENDED
```

#### 6.3.4 訂房狀態流轉（Sprint 244 補充）

> **⚠️ 注意**：`CONFIRMED`、`CHECKED_IN`、`CHECKED_OUT`、`COMPLETED` 在列舉中有定義，但**目前沒有任何程式路徑寫入**（DEF-345）。實際可達的狀態只有 `CREATED`、`PAID`、`CANCELLED`。

| 從 | 到 | 觸發 | 狀態 |
|----|----|------|------|
| `CREATED` | `PAID` | 付款成功：Mock `POST /v2/bookings/{bookingId}/pay`，或 Stripe 入帳 | 已實作 |
| `CREATED`／`PAID`／`CONFIRMED` | `CANCELLED` | 買家或店家取消；未付款超過 `payment_due_at` 自動取消 | 已實作 |
| `PAID` → `CONFIRMED` → `CHECKED_IN` → `CHECKED_OUT` → `COMPLETED` | — | 店家確認、入住、退房、完成 | **未實作**（DEF-345；PRD Phase 1 寫明 CONFIRMED 等效於 PAID） |

取消與退款規則（PRD Q14：入住前 24 小時以上全額、不足 24 小時不退；商家、管理員與系統取消全額）見 [API_M06_Booking.md](./API_M06_Booking.md)。

### 6.4 索引設計

**複合索引**:
- `listings`: `(tenant_id, listing_type, status)`
- `orders`: `(tenant_id, user_id, status)`
- `room_calendar`: `(listing_id, calendar_date)` — UNIQUE
- `pricing_rules`: `(tenant_id, listing_id)` WHERE listing_id IS NOT NULL

**租戶隔離索引（用於 Hibernate Filter）**:
- `listings`: `(tenant_id, status)`
- `orders`: `(tenant_id, status)`

### 6.5 詳細資料表清單

| 表格名 | 說明 | 詳細定義 |
|--------|------|----------|
| `tenants` | 租戶/店鋪主檔 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#21-tenants---租戶店鋪主檔) |
| `tenant_members` | 租戶成員關聯 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#22-tenant_members---租戶成員關聯) |
| `tenant_feature_toggles` | 功能開關 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#23-tenant_feature_toggles---功能開關) |
| `users` | 使用者主檔 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#24-users---使用者主檔) |
| `user_profiles` | 使用者擴展檔 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#25-user_profiles---使用者擴展檔) |
| `refresh_tokens` | Refresh Token 儲存 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#26-refresh_tokens---refresh-token-儲存) |
| `listings` | 統一商品/房源 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#27-listings---統一商品房源主檔) |
| `products` | 商品特化資料 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#28-products---商品特化資料) |
| `product_inventory` | 庫存管理 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#29-product_inventory---庫存管理) |
| `rooms` | 房源特化資料 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#210-rooms---房源特化資料) |
| `room_calendar` | 日曆可用性 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#211-room_calendar---日曆可用性) |
| `orders` | 訂單主檔 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#212-orders---訂單主檔) |
| `order_items` | 訂單明細 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#213-order_items---訂單明細) |
| `order_state_log` | 狀態異動日誌 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#214-order_state_log---狀態異動日誌) |
| `pricing_rules` | 動態定價規則 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#215-pricing_rules---動態定價規則) |
| `pricing_overrides` | 手動價格覆蓋 | [SRD_Database_Schema.md](./SRD_Database_Schema.md#216-pricing_overrides---手動價格覆蓋) |

---

## 7. 部署架構 (Deployment Architecture)

### 7.1 容器架構

```
┌──────────────────────────────────────────────────────────────┐
│                    Docker Compose (Development)               │
├──────────────────────────────────────────────────────────────┤
│  ┌────────────┐  ┌────────────┐  ┌────────────────────────┐   │
│  │  frontend  │  │   backend  │  │     postgres        │   │
│  │  (Next.js)  │  │  (Spring)  │  │     (Database)      │   │
│  │   Port:3000 │  │   Port:8080│  │     Port:5432       │   │
│  └────────────┘  └────────────┘  └────────────────────────┘   │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                    Redis                              │   │
│  │                    Port:6379                         │   │
│  └──────────────────────────────────────────────────────┘   │
└──────────────────────────────────────────────────────────────┘
```

### 7.2 生產環境架構（未來規劃）

| 元件 | 規格 | 高可用 |
|------|------|--------|
| **ALB** | AWS Application LB | Multi-AZ |
| **Backend** | 4x t3.medium | Auto Scaling |
| **PostgreSQL** | RDS Multi-AZ | 自動容錯移轉 |
| **Redis** | ElastiCache Cluster | Multi-AZ |

---

## 8. 追溯性 (Traceability)

### 8.1 需求追蹤矩陣

| PRD Feature | FRD Business Rule | User Story | API |
|------------|-------------------|------------|-----|
| F-M01-001 | BR-M01-001 | US-M01-001 | API-M01-001 |
| F-M01-002 | BR-M01-002 | US-M01-002 | API-M01-002 |
| F-M12-001 | BR-M12-001 | US-M12-001 | API-M12-001 |
| F-M17-001 | BR-GEN-001, BR-GEN-002 | US-M17-001 | API-M17-001 |

---

## 9. 修訂歷史

| 版本 | 日期 | 作者 | 變更說明 |
|------|------|------|----------|
| v1.0 | 2026-04-09 | Marcus (SD-Architect) | 初始版本 |
| v1.1 | 2026-09-26 | Claude Code（Sprint 203） | 文件一致性檢查（DEF-280）：§4.2／§4.3 改為實際的扁平回應封包並指向新增的 [API_Error_Codes.md](./API_Error_Codes.md)；新增 §4.4 時間與時區慣例（DEF-269／271）；新增 §5.4 回應安全標頭、請求追蹤與 CORS（Sprint 191、197～202，DEF-265／278／279／280／281／282）。此前這些行為只存在於 Sprint 計畫與程式碼 |
| v1.2 | 2026-09-26 | Claude Code（Sprint 204） | 新增 §5.5 一次性連結：忘記密碼與 Email 驗證（Redis token、`EmailSender`／`canDeliver()`、與開店申請前置條件的關係） |
| v1.3 | 2026-09-27 | Claude Code（Sprint 206） | §5.4.1：Tomcat 連接器層的拒絕改回 JSON 封包＋`X-Request-ID`＋安全標頭（DEF-283） |
| v1.4 | 2026-10-03 | Claude Code（Sprint 244） | 文件對齊（3/3）：§5.1 Token 效期（Access 15 分鐘、Refresh 預設 7 天）改為實作現況，並註明無 Cookie、前端存 `localStorage`、Payload 為實際 claims；§5.5 寄信通道補 `SmtpEmailSender`（Sprint 209）與其未實測、同步寄送的限制（DEF-347）；§6.3.1 訂單狀態圖依 `OrderStateMachine` 重繪（移除 `CREATED(=PAID)` 與 `DELIVERED→REFUNDING`）；新增 §6.3.4 訂房狀態（`CONFIRMED` 以後未實作，DEF-345） |

---

**文檔版本**: AISDLC v0.09
**模板維護**: AISDLC Framework Team
**最後更新**: 2026-09-26
