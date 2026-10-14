# E-Commerce 系統 — 模組技術設計 v1.5

> **文檔類型**: SRD - Module Technical Design
> **版本**: v1.5（v1.0 → v1.1：Sprint 203 文件一致性檢查；v1.1 → v1.2：Sprint 244 文件對齊；v1.2 → v1.3：Sprint 262 新增 §6 M07 結算與撥款模組；v1.3 → v1.4：Sprint 263 新增 §7 M08 評價與社交互動模組，並修正 §8（原 §7）異常處理架構內部子標題未隨改號的遺留漂移；v1.4 → v1.5：Sprint 265 新增 §8 M05 退貨申請、§9 M18 客服工單模組，並補上 Sprint 264 遺留的 §7 M08 內容未同步問題，見 §11 修訂歷史）
> **依據**: E-Commerce_SRD_System_Architecture.md, E-Commerce_FRD_v1.0.md
> **建立日期**: 2026-04-09
> **作者**: Marcus (SD-Architect)
> **Phase**: Phase 1 (Must Have)

---

## 📋 文檔元數據

| 項目 | 內容 |
|-----|------|
| **專案名稱** | E-Commerce B2B2C 多租戶電子商務平台 |
| **技術棧** | Spring Boot 3.2.x + Java 21 |
| **架構模式** | Clean Architecture + DDD |
| **多租戶** | Shared Schema + Hibernate Filter |

---

## 📌 文檔追蹤

### 上游文檔
- **系統架構**: [SRD_System_Architecture.md](./SRD_System_Architecture.md)
- **資料庫 Schema**: [SRD_Database_Schema.md](./SRD_Database_Schema.md)
- **FRD**: [E-Commerce_FRD_v1.0.md](../01_requirements/E-Commerce_FRD_v1.0.md)

### 下游文檔
- **API 規格**: [API_Index.md](./API_Index.md)
- **測試計劃**: [docs/03_testing/](../03_testing/)

---

## 1. M03 認證模組 (Authentication Module)

### 1.1 架構概覽

```
com.nextkey.ecommerce/
├── api/
│   └── controller/
│       └── AuthController.java           # 認證 API 端點
├── core/
│   └── auth/
│       ├── AuthService.java              # 認證核心服務
│       ├── TokenService.java             # JWT Token 服務
│       ├── RefreshTokenService.java      # Refresh Token 服務
│       └── dto/
├── domain/
│   ├── entity/
│   │   ├── User.java
│   │   └── RefreshToken.java
│   ├── repository/
│   │   ├── UserRepository.java
│   │   └── RefreshTokenRepository.java
│   └── service/
│       └── DomainUserService.java        # 領域服務
└── infrastructure/
    ├── security/
    │   ├── JwtAuthFilter.java           # JWT 認證過濾器
    │   ├── JwtTokenProvider.java        # Token 解析
    │   └── SecurityConfig.java          # Spring Security 配置
    └── persistence/
        └── adapter/
            ├── UserRepositoryAdapter.java
            └── RefreshTokenRepositoryAdapter.java
```

### 1.2 核心類設計

#### AuthService.java
```java
@Service
@Transactional
public class AuthService {
    
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final RefreshTokenService refreshTokenService;
    
    /**
     * 會員註冊
     * API: POST /api/v2/auth/register
     */
    public User register(RegisterRequest request) {
        // 1. 驗證 Email 唯一性
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new EmailAlreadyExistsException(request.getEmail());
        }
        
        // 2. 密碼強度驗證
        validatePasswordStrength(request.getPassword());
        
        // 3. 建立使用者
        User user = User.create(
            request.getEmail(),
            passwordEncoder.encode(request.getPassword()),
            request.getUserType()
        );
        
        return userRepository.save(user);
    }
    
    /**
     * 會員登入
     * API: POST /api/v2/auth/login
     */
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(request.getEmail())
            .orElseThrow(() -> new InvalidCredentialsException());
        
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        
        if (user.isSuspended()) {
            throw new AccountSuspendedException();
        }
        
        // 更新最後登入時間
        user.recordLogin();
        
        // 產生 Token
        String accessToken = tokenService.generateAccessToken(user);
        String refreshToken = refreshTokenService.createRefreshToken(user, httpRequest);
        
        return new AuthResponse(accessToken, refreshToken, 1800, "Bearer");
    }
    
    /**
     * 刷新 Access Token
     * API: POST /api/v2/auth/refresh
     */
    public AuthResponse refresh(String refreshToken) {
        RefreshToken storedToken = refreshTokenService.validateRefreshToken(refreshToken);
        User user = storedToken.getUser();
        
        // 產生新 Token（Rotation）
        String newAccessToken = tokenService.generateAccessToken(user);
        String newRefreshToken = refreshTokenService.rotateRefreshToken(storedToken);
        
        return new AuthResponse(newAccessToken, newRefreshToken, 1800, "Bearer");
    }
}
```

> **⚠️ 修訂註記（Sprint 244，2026-10-03）**：以下為設計草稿。實際類別是 `infrastructure/security/JwtTokenService`（簽發與驗證都在那裡），效期由設定檔注入，不是寫死的常數；Refresh Token 的輪替與重放偵測見 [SRD_System_Architecture.md](./SRD_System_Architecture.md) §5.1。

#### TokenService.java
```java
@Service
public class TokenService {
    
    private final JwtTokenProvider jwtTokenProvider;
    
    // Token 有效期設定
    private static final long ACCESS_TOKEN_VALIDITY = 15 * 60; // 15 分鐘（Sprint 244 依實作改寫；實際值由 jwt.access-token-expiration 設定，預設 900000 ms）
    
    /**
     * 產生 Access Token
     */
    public String generateAccessToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("email", user.getEmail());
        claims.put("userType", user.getUserType());
        claims.put("roles", user.getRoles());
        
        // 多租戶：若用戶只有一個 tenant，直接寫入 token
        if (user.getSingleTenantId() != null) {
            claims.put("tenantId", user.getSingleTenantId());
        }
        
        return jwtTokenProvider.generateToken(
            user.getId().toString(),
            claims,
            ACCESS_TOKEN_VALIDITY
        );
    }
    
    /**
     * 解析 Token 取得用戶 ID
     */
    public UUID parseUserId(String token) {
        return UUID.fromString(jwtTokenProvider.getSubject(token));
    }
}
```

### 1.3 JWT 流程序列圖

```
┌─────────┐     ┌──────────────┐     ┌──────────────┐     ┌────────────────┐
│  Client │     │ AuthController│     │  AuthService │     │  TokenService  │
└────┬─────┘     └──────┬───────┘     └──────┬───────┘     └───────┬────────┘
     │                  │                    │                     │
     │ POST /login     │                    │                     │
     │────────────────>│                    │                     │
     │                  │                    │                     │
     │                  │ validateCredentials│                     │
     │                  │──────────────────>│                     │
     │                  │                    │                     │
     │                  │                    │ generateAccessToken │
     │                  │                    │───────────────────>│
     │                  │                    │                    │
     │                  │                    │<───────────────────│
     │                  │                    │  accessToken       │
     │                  │                    │                     │
     │                  │                    │ createRefreshToken │
     │                  │                    │───────────────────>│
     │                  │                    │                    │
     │                  │                    │<───────────────────│
     │                  │                    │  refreshToken      │
     │                  │                    │                     │
     │ 200 OK           │                    │                     │
     │ accessToken      │                    │                     │
     │ refreshToken     │<───────────────────│                     │
     │<─────────────────│                    │                     │
     │                  │                    │                     │
```

---

## 2. M17 租戶管理模組 (Tenant Management Module)

> **🔴 實作現況與本節設計稿的差異（2026-09-02，Sprint 110 補註）**
>
> 本節 §2.2／§2.3 的類別草稿與序列圖為設計期產物，描述的是「申請時就建立 `Tenant`（`PENDING`）、
> 審核時再改為 `ACTIVE`」的單實體模型。**實作採兩實體模型**（PRD §4.3、FRD BR-M17-001）：
>
> | 本節設計稿 | 實作現況 |
> |------------|----------|
> | `TenantApplicationService.apply()` | `TenantService.createApplication()`（`core/tenant/`） |
> | 申請時 `tenantRepository.save(Tenant(PENDING))` | 申請時只寫 `tenant_applications`（`TenantApplication`，`status = PENDING`），**不建立 `Tenant`** |
> | 申請時即建立 Owner Member、初始化 Feature Toggle | 兩者都延後到核准時，由 `AdminService.approveTenantApplication()` 在同一交易內完成 |
> | 審核＝更新既有 `Tenant` 狀態 | 審核＝更新 `TenantApplication`；`Tenant` 於核准當下才建立，且建立即 `ACTIVE` |
> | `Tenant.createApplication()`／`findByOwnerId()`／`existsByStoreName()` | 均不存在於實作 |
>
> 因此 `Tenant.PENDING_REVIEW` 在生產環境不可達。**以本節程式碼草稿為準會實作出錯誤的流程**，
> 請以 PRD §4.3／§7.4.1、FRD BR-M17-001 與 `AdminService` 實際程式碼為準。
> 本節保留原文作為設計決策的歷史紀錄，未改寫。

### 2.1 架構概覽

```
com.nextkey.ecommerce/
├── api/
│   ├── controller/
│   │   ├── TenantController.java         # 租戶 API
│   │   └── AdminTenantController.java    # 管理員 API
│   └── dto/
├── core/
│   └── tenant/
│       ├── TenantService.java            # 租戶核心服務
│       ├── TenantApplicationService.java  # 申請流程服務
│       ├── FeatureToggleService.java      # 功能開關服務
│       └── dto/
├── domain/
│   ├── entity/
│   │   ├── Tenant.java
│   │   ├── TenantMember.java
│   │   └── TenantFeatureToggle.java
│   ├── repository/
│   │   ├── TenantRepository.java
│   │   ├── TenantMemberRepository.java
│   │   └── FeatureToggleRepository.java
│   ├── service/
│   │   └── TenantDomainService.java       # 領域服務
│   └── event/
│       ├── TenantApplicationSubmitted.java
│       └── FeatureEnabledEvent.java
└── infrastructure/
    └── persistence/
        └── adapter/
```

### 2.2 核心類設計

#### TenantApplicationService.java
```java
@Service
@Transactional
public class TenantApplicationService {
    
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * 申請開店
     * API: POST /api/v2/tenants/apply
     */
    public TenantApplicationResult apply(UUID userId, TenantApplicationRequest request) {
        // 1. 檢查用戶是否已有待審核或已通過的店鋪
        List<Tenant> existingTenants = tenantRepository.findByOwnerId(userId);
        if (existingTenants.stream().anyMatch(Tenant::isPendingOrActive)) {
            throw new TenantApplicationAlreadyExistsException();
        }
        
        // 2. 檢查店鋪名稱唯一性
        if (tenantRepository.existsByStoreName(request.getStoreName())) {
            throw new StoreNameAlreadyExistsException(request.getStoreName());
        }
        
        // 3. 建立租戶（預設狀態 PENDING）
        Tenant tenant = Tenant.createApplication(
            request.getStoreName(),
            request.getStoreDescription(),
            request.getBusinessType(),
            request.getContactEmail(),
            request.getContactPhone(),
            request.getBusinessLicenseUrl(),
            userId
        );
        
        tenant = tenantRepository.save(tenant);
        
        // 4. 建立擁有者成員關聯
        TenantMember owner = TenantMember.createOwner(tenant.getId(), userId);
        tenantMemberRepository.save(owner);
        
        // 5. 初始化預設功能開關
        initializeDefaultFeatureToggles(tenant.getId(), request.getBusinessType());
        
        // 6. 發布領域事件
        eventPublisher.publishEvent(
            new TenantApplicationSubmitted(tenant, userId)
        );
        
        return new TenantApplicationResult(
            tenant.getId(),
            tenant.getStoreName(),
            tenant.getBusinessType(),
            tenant.getStatus(),
            tenant.getCreatedAt()
        );
    }
    
    /**
     * 初始化預設功能開關
     */
    private void initializeDefaultFeatureToggles(UUID tenantId, BusinessType businessType) {
        // 預設開啟的功能
        createToggle(tenantId, "RETAIL_ENABLED", businessType.supportsRetail());
        createToggle(tenantId, "BOOKING_ENABLED", businessType.supportsBooking());
        createToggle(tenantId, "CMS_ENABLED", true);
        createToggle(tenantId, "ERP_ENABLED", true);
        createToggle(tenantId, "DYNAMIC_PRICING_ENABLED", false);
        createToggle(tenantId, "PROMO_ENABLED", false);
    }
}
```

#### FeatureToggleService.java
```java
@Service
@Transactional
public class FeatureToggleService {
    
    /**
     * 取得租戶功能開關狀態
     * API: GET /api/v2/dashboard/tenants/features
     */
    public List<FeatureToggleResponse> getFeatureToggles(UUID tenantId) {
        List<TenantFeatureToggle> toggles = featureToggleRepository.findByTenantId(tenantId);
        return toggles.stream()
            .map(this::toResponse)
            .collect(Collectors.toList());
    }
    
    /**
     * 申請啟用功能
     * API: PUT /api/v2/dashboard/tenants/features/:feature
     */
    public FeatureToggleResult requestFeature(UUID tenantId, String featureKey, boolean enabled) {
        TenantFeatureToggle toggle = featureToggleRepository
            .findByTenantIdAndFeatureKey(tenantId, featureKey)
            .orElseThrow(() -> new FeatureNotFoundException(featureKey));
        
        if (enabled && !toggle.canBeEnabledByTenant()) {
            throw new FeatureRequiresAdminApprovalException(featureKey);
        }
        
        // 更新狀態
        if (enabled) {
            toggle.requestEnable();
        } else {
            toggle.disable();
        }
        
        toggle = featureToggleRepository.save(toggle);
        return toResult(toggle);
    }
    
    /**
     * 管理員審核功能開關
     * API: POST /api/v2/admin/tenants/:id/approve
     */
    @Transactional
    public void approveFeature(UUID tenantId, String featureKey, UUID adminId) {
        TenantFeatureToggle toggle = featureToggleRepository
            .findByTenantIdAndFeatureKeyForUpdate(tenantId, featureKey)
            .orElseThrow(() -> new FeatureNotFoundException(featureKey));
        
        toggle.approve(adminId);
        featureToggleRepository.save(toggle);
        
        // 發布事件
        eventPublisher.publishEvent(new FeatureEnabledEvent(tenantId, featureKey));
    }
}
```

### 2.3 租戶申請流程序列圖

```
┌─────────┐     ┌──────────────────────┐     ┌───────────────────┐
│  Store  │     │ TenantApplicationService│     │ FeatureToggleSvc  │
│  Owner  │     └──────────┬───────────┘     └─────────┬─────────┘
└────┬─────┘                │                          │
     │ POST /tenants/apply  │                          │
     │─────────────────────>│                          │
     │                      │                          │
     │                      │ 檢查現有申請              │
     │                      │─────────────────────────>│
     │                      │                          │
     │                      │<─────────────────────────│
     │                      │                          │
     │                      │ 建立 Tenant (PENDING)     │
     │                      │──────────────────────────│
     │                      │                          │
     │                      │<──────────────────────────│
     │                      │                          │
     │                      │ 建立 Owner Member         │
     │                      │──────────────────────────│
     │                      │                          │
     │                      │<──────────────────────────│
     │                      │                          │
     │                      │ 初始化預設功能開關        │
     │                      │─────────────────────────>│
     │                      │                          │
     │                      │<─────────────────────────│
     │                      │                          │
     │ 201 Created          │                          │
     │ applicationId        │                          │
     │<─────────────────────│                          │
     │                      │                          │
```

---

## 3. M01/M02 商品房源模組 (Listing Module)

### 3.1 架構概覽

```
com.nextkey.ecommerce/
├── api/
│   ├── controller/
│   │   ├── ListingController.java        # C 端房源 API
│   │   └── DashboardListingController.java # B 端店鋪 API
│   └── dto/
├── core/
│   └── listing/
│       ├── ListingService.java           # 統一 Listing 服務
│       ├── ListingQueryService.java      # 查詢服務
│       ├── ProductService.java           # 商品特有邏輯
│       ├── RoomService.java              # 房源特有邏輯
│       └── dto/
├── domain/
│   ├── entity/
│   │   ├── Listing.java                  # 統一抽象
│   │   ├── Product.java                  # 商品特化
│   │   ├── Room.java                    # 房源特化
│   │   ├── ProductInventory.java         # 庫存
│   │   └── RoomCalendar.java            # 日曆
│   ├── repository/
│   │   ├── ListingRepository.java
│   │   ├── ProductRepository.java
│   │   ├── RoomRepository.java
│   │   ├── InventoryRepository.java
│   │   └── RoomCalendarRepository.java
│   ├── service/
│   │   └── ListingDomainService.java
│   └── vo/
│       ├── ListingType.java
│       └── ListingStatus.java
└── infrastructure/
    └── persistence/
        └── adapter/
```

### 3.2 統一 Listing 處理

#### ListingService.java
```java
@Service
@Transactional
public class ListingService {
    
    private final ListingRepository listingRepository;
    private final ProductRepository productRepository;
    private final RoomRepository roomRepository;
    private final InventoryRepository inventoryRepository;
    private final RoomCalendarRepository calendarRepository;
    
    /**
     * 建立房源/商品
     * API: POST /api/v2/dashboard/listings
     */
    public Listing create(UUID tenantId, UUID userId, CreateListingRequest request) {
        // 1. 驗證權限
        validateCreatePermission(tenantId, userId);
        
        // 2. 驗證功能開關
        validateFeatureEnabled(tenantId, request.getListingType());
        
        // 3. 建立 Listing
        Listing listing = Listing.create(
            tenantId,
            request.getListingType(),
            request.getTitle(),
            request.getDescription(),
            request.getCoverImageUrl(),
            request.getBasePrice(),
            request.getCurrency(),
            request.getLocation(),
            request.getMaxGuests(),
            userId
        );
        
        listing = listingRepository.save(listing);
        
        // 4. 建立特化資料
        if (request.isProduct()) {
            Product product = Product.create(listing.getId(), request);
            productRepository.save(product);
            
            // 初始化庫存
            ProductInventory inventory = ProductInventory.create(listing.getId(), request.getInitialStock());
            inventoryRepository.save(inventory);
        } else if (request.isRoom()) {
            Room room = Room.create(listing.getId(), request);
            roomRepository.save(room);
            
            // 初始化日曆（未來 365 天）
            roomCalendarService.initializeCalendar(listing.getId(), request);
        }
        
        return listing;
    }
    
    /**
     * 更新房源/商品
     * API: PUT /api/v2/dashboard/listings/:id
     */
    @Transactional
    public Listing update(UUID listingId, UUID tenantId, UUID userId, UpdateListingRequest request) {
        Listing listing = listingRepository.findByIdForUpdate(listingId)
            .orElseThrow(() -> new ListingNotFoundException(listingId));
        
        // 租戶隔離驗證
        listing.validateTenant(tenantId);
        
        // 更新通用欄位
        listing.updateBasicInfo(
            request.getTitle(),
            request.getDescription(),
            request.getCoverImageUrl(),
            request.getBasePrice()
        );
        
        // 更新特化資料
        if (listing.isProduct()) {
            updateProduct(listingId, request);
        } else if (listing.isRoom()) {
            updateRoom(listingId, request);
        }
        
        return listingRepository.save(listing);
    }
    
    /**
     * 查詢房源列表（帶過濾）
     * API: GET /api/v2/listings?type=ROOM
     */
    public ListingSearchResult search(ListingSearchCriteria criteria) {
        // 建構查詢
        Specification<Listing> spec = ListingSpecifications
            .withType(criteria.getListingType())
            .withLocation(criteria.getLocation())
            .withStatus(ListingStatus.ACTIVE)
            .withDateRange(criteria.getCheckInDate(), criteria.getCheckOutDate())
            .withGuests(criteria.getGuests())
            .withPriceRange(criteria.getMinPrice(), criteria.getMaxPrice())
            .build();
        
        // 分頁查詢
        Pageable pageable = PageRequest.of(
            criteria.getPage() - 1,  // 1-based to 0-based
            criteria.getLimit(),
            Sort.by(criteria.getSortField(), criteria.getSortDirection())
        );
        
        Page<Listing> page = listingRepository.findAll(spec, pageable);
        
        // 轉換為 DTO（只暴露必要欄位）
        return toSearchResult(page);
    }
}
```

#### RoomCalendarService.java
```java
@Service
@Transactional
public class RoomCalendarService {
    
    /**
     * 查詢日曆與動態價格
     * API: GET /api/v2/listings/:id/calendar
     */
    public CalendarResult getCalendar(UUID listingId, LocalDate start, LocalDate end, int guests) {
        Listing listing = listingRepository.findById(listingId)
            .orElseThrow(() -> new ListingNotFoundException(listingId));
        
        // 查詢日曆資料
        List<RoomCalendar> calendarDays = calendarRepository
            .findByListingIdAndDateRange(listingId, start, end);
        
        // 計算動態價格
        List<CalendarDayResult> results = calendarDays.stream()
            .map(day -> calculateDayPrice(listing, day, guests))
            .collect(Collectors.toList());
        
        BigDecimal totalPrice = results.stream()
            .map(CalendarDayResult::getPrice)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        return new CalendarResult(listingId, start, end, guests, results, totalPrice);
    }
    
    /**
     * 計算單日價格（套用動態定價規則）
     */
    private CalendarDayResult calculateDayPrice(Listing listing, RoomCalendar day, int guests) {
        // 優先使用手動覆蓋價格
        if (day.hasPriceOverride()) {
            return CalendarDayResult.withOverride(day, day.getPriceOverride());
        }
        
        // 取得動態價格
        DynamicPrice price = dynamicPricingService.calculatePrice(
            listing.getId(),
            day.getCalendarDate(),
            day.getBasePriceAtDate(),
            guests
        );
        
        return CalendarDayResult.withDynamicPrice(day, price);
    }
}
```

---

## 4. M05 訂單履約模組 (Order Fulfillment Module)

### 4.1 架構概覽

```
com.nextkey.ecommerce/
├── api/
│   ├── controller/
│   │   ├── OrderController.java          # 買家 API
│   │   └── DashboardOrderController.java # 賣家 API
│   └── dto/
├── core/
│   └── order/
│       ├── OrderService.java             # 訂單核心服務
│       ├── OrderCreationService.java     # 創建訂單流程
│       ├── OrderStateMachine.java        # 狀態機
│       ├── InventoryService.java         # 庫存服務
│       └── dto/
├── domain/
│   ├── entity/
│   │   ├── Order.java
│   │   ├── OrderItem.java
│   │   └── OrderStateLog.java
│   ├── repository/
│   │   ├── OrderRepository.java
│   │   ├── OrderItemRepository.java
│   │   └── OrderStateLogRepository.java
│   ├── service/
│   │   └── OrderDomainService.java      # 領域服務（狀態驗證）
│   ├── event/
│   │   ├── OrderCreatedEvent.java
│   │   └── OrderStatusChangedEvent.java
│   └── vo/
│       └── OrderStatus.java
└── infrastructure/
    └── persistence/
        └── adapter/
```

### 4.2 核心類設計

#### OrderCreationService.java
```java
@Service
@Transactional
public class OrderCreationService {
    
    private final OrderRepository orderRepository;
    private final InventoryService inventoryService;
    private final DynamicPricingService pricingService;
    private final OrderStateMachine stateMachine;
    
    /**
     * 建立訂單
     * API: POST /api/v2/orders
     */
    public Order create(UUID userId, CreateOrderRequest request, UUID idempotencyKey) {
        // 1. 防重複檢查（Idempotency）
        if (idempotencyKey != null) {
            Optional<Order> existing = orderRepository.findByIdempotencyKey(idempotencyKey);
            if (existing.isPresent()) {
                return existing.get(); // 返回已存在的訂單
            }
        }
        
        // 2. 驗證並計算價格
        List<OrderItemData> itemDataList = validateAndCalculate(request.getItems());
        
        // 3. 扣減庫存（樂觀鎖）
        for (OrderItemData itemData : itemDataList) {
            inventoryService.reserve(itemData.getListingId(), itemData.getQuantity());
        }
        
        try {
            // 4. 建立訂單
            Order order = Order.create(
                userId,
                request.getTenantId(),
                itemDataList,
                request.getPaymentMethod(),
                request.getNotes()
            );
            
            // 5. 設定 Idempotency Key
            if (idempotencyKey != null) {
                order.assignIdempotencyKey(idempotencyKey);
            }
            
            // 6. 建立時為 CREATED；付款成功（Mock 或 Stripe 入帳）後才由付款服務轉為 PAID（Sprint 244 修訂：舊寫法的 Phase 1 建單即付款已不存在）
            order = orderRepository.save(order);
            
            // 7. 建立訂單明細
            createOrderItems(order, itemDataList);
            
            // 8. 發布領域事件
            eventPublisher.publishEvent(new OrderCreatedEvent(order));
            
            return order;
            
        } catch (Exception e) {
            // 失敗時釋放庫存
            for (OrderItemData itemData : itemDataList) {
                inventoryService.release(itemData.getListingId(), itemData.getQuantity());
            }
            throw e;
        }
    }
    
    /**
     * 驗證並計算價格
     */
    private List<OrderItemData> validateAndCalculate(List<OrderItemRequest> items) {
        return items.stream().map(item -> {
            Listing listing = listingRepository.findById(item.getListingId())
                .orElseThrow(() -> new ListingNotFoundException(item.getListingId()));
            
            // 計算動態價格
            BigDecimal unitPrice;
            if (listing.isProduct()) {
                unitPrice = listing.getBasePrice(); // 零售直接用原價
            } else {
                // 民宿：計算入住晚數
                int nights = calculateNights(item.getCheckInDate(), item.getCheckOutDate());
                DynamicPrice price = pricingService.calculateForBooking(
                    listing.getId(),
                    item.getCheckInDate(),
                    item.getCheckOutDate(),
                    item.getGuests()
                );
                unitPrice = price.getTotalPrice().divide(BigDecimal.valueOf(nights));
            }
            
            return new OrderItemData(
                listing,
                item.getQuantity(),
                unitPrice,
                item.getCheckInDate(),
                item.getCheckOutDate(),
                item.getGuests()
            );
        }).collect(Collectors.toList());
    }
}
```

> **⚠️ 修訂註記（Sprint 244）**：以下 `ALLOWED_TRANSITIONS` 為設計草稿，與實際規則不同（實際為 `PAID → CONFIRMED`、`CONFIRMED → SHIPPING`，`SHIPPING` 之後不可取消，`CANCELLED → REFUNDING → REFUNDED`）。實際規則是 `core/order/OrderStateMachine.java` 的靜態 `canTransition`，權威對照表見 [SRD_System_Architecture.md](./SRD_System_Architecture.md) §6.3.1。

#### OrderStateMachine.java
```java
@Service
public class OrderStateMachine {
    
    /**
     * 定義狀態流轉規則
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS;
    
    static {
        ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);
        ALLOWED_TRANSITIONS.put(OrderStatus.CREATED, 
            Set.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAID, 
            Set.of(OrderStatus.SHIPPING, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPING, 
            Set.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, 
            Set.of(OrderStatus.COMPLETED, OrderStatus.REFUNDING));
        ALLOWED_TRANSITIONS.put(OrderStatus.REFUNDING, 
            Set.of(OrderStatus.REFUNDED));
        // COMPLETED, CANCELLED, REFUNDED 為終態
    }
    
    /**
     * 執行狀態轉換
     */
    public Order transition(Order order, OrderStatus newStatus, String reason, OrderOperatorType operatorType) {
        // 1. 驗證轉換是否允許
        validateTransition(order.getStatus(), newStatus);
        
        // 2. 執行轉換前鉤子
        executePreTransitionHook(order, newStatus);
        
        // 3. 更新狀態
        OrderStatus fromStatus = order.getStatus();
        order.transitionTo(newStatus);
        
        // 4. 記錄狀態日誌
        orderStateLogRepository.save(OrderStateLog.create(
            order.getId(),
            order.getNextSequence(),
            fromStatus,
            newStatus,
            reason,
            operatorType
        ));
        
        // 5. 執行轉換後鉤子
        executePostTransitionHook(order, fromStatus, newStatus);
        
        return order;
    }
    
    /**
     * 前置鉤子：執行業務邏輯
     */
    private void executePreTransitionHook(Order order, OrderStatus newStatus) {
        switch (newStatus) {
            case CANCELLED:
                // 取消時釋放庫存
                inventoryService.releaseForOrder(order.getId());
                break;
            case REFUNDING:
                // 退款申請通知
                notificationService.notifyRefundRequest(order);
                break;
        }
    }
    
    /**
     * 後置鉤子：發送事件/通知
     */
    private void executePostTransitionHook(Order order, OrderStatus from, OrderStatus to) {
        eventPublisher.publishEvent(new OrderStatusChangedEvent(order, from, to));
        
        switch (to) {
            case SHIPPING:
                notificationService.notifyShipping(order);
                break;
            case COMPLETED:
                notificationService.notifyCompleted(order);
                break;
        }
    }
}
```

### 4.3 訂單建立流程序列圖

```
┌─────────┐     ┌────────────────────┐     ┌────────────────┐     ┌─────────────┐
│  Buyer  │     │ OrderCreationService│     │ InventorySvc   │     │  OrderRepo  │
└────┬────┘     └─────────┬──────────┘     └───────┬────────┘     └──────┬──────┘
     │                    │                        │                      │
     │ POST /orders       │                        │                      │
     │ X-Idempotency-Key  │                        │                      │
     │───────────────────>│                        │                      │
     │                    │                        │                      │
     │                    │ 檢查 Idempotency       │                      │
     │                    │───────────────────────────────────────────────>│
     │                    │                                              │
     │                    │<───────────────────────────────────────────────│
     │                    │                        │                      │
     │                    │ 驗證並計算價格           │                      │
     │                    │────────────────────────>│                      │
     │                    │<────────────────────────│                      │
     │                    │                        │                      │
     │                    │ 扣減庫存                │                      │
     │                    │────────────────────────>│                      │
     │                    │<────────────────────────│ SUCCESS              │
     │                    │                        │                      │
     │                    │ 建立訂單                │                      │
     │                    │───────────────────────────────────────────────>│
     │                    │                                              │
     │                    │ 付款成功 CREATED→PAID   │                      │
     │                    │───────────────────────────────────────────────>│
     │                    │                                              │
     │                    │ 建立訂單明細           │                      │
     │                    │───────────────────────────────────────────────>│
     │                    │                                              │
     │ 201 Created        │                        │                      │
     │ orderId            │                        │                      │
     │<───────────────────│                        │                      │
     │                    │                        │                      │
```

---

## 5. M12 動態定價模組 (Dynamic Pricing Module)

### 5.1 架構概覽

```
com.nextkey.ecommerce/
├── api/
│   ├── controller/
│   │   ├── PricingController.java       # 動態定價 API
│   │   └── DashboardPricingController.java
│   └── dto/
├── core/
│   └── pricing/
│       ├── DynamicPricingService.java    # 動態定價核心服務
│       ├── PricingRuleService.java       # 規則管理服務
│       ├── PriceCalculator.java          # 價格計算引擎
│       └── dto/
├── domain/
│   ├── entity/
│   │   ├── PricingRule.java
│   │   └── PricingOverride.java
│   ├── repository/
│   │   ├── PricingRuleRepository.java
│   │   └── PricingOverrideRepository.java
│   ├── service/
│   │   └── PricingDomainService.java
│   └── vo/
│       ├── RuleType.java
│       ├── AdjustmentType.java
│       └── PriceType.java
└── infrastructure/
    └── pricing/
        ├── ruleengine/
        │   ├── RuleEngine.java           # 規則引擎
        │   ├── RuleExecutor.java         # 規則執行器
        │   └── evaluators/
        │       ├── WeekdayRuleEvaluator.java
        │       ├── WeekendRuleEvaluator.java
        │       └── ...
```

### 5.2 核心類設計

#### DynamicPricingService.java
```java
@Service
@Transactional(readOnly = true)
public class DynamicPricingService {
    
    private final PricingRuleRepository ruleRepository;
    private final PricingOverrideRepository overrideRepository;
    private final RuleEngine ruleEngine;
    private final PriceCalculator calculator;
    
    /**
     * 計算動態價格（民宿/房源）
     * API: GET /api/v2/listings/:id/price
     */
    public DynamicPriceResult calculatePrice(
            UUID listingId,
            LocalDate checkInDate,
            LocalDate checkOutDate,
            int guests) {
        
        Listing listing = listingRepository.findById(listingId)
            .orElseThrow(() -> new ListingNotFoundException(listingId));
        
        // 1. 取得房源日曆
        List<RoomCalendar> calendars = calendarRepository
            .findByListingIdAndDateRange(listingId, checkInDate, checkOutDate.minusDays(1));
        
        // 2. 取得適用的定價規則
        List<PricingRule> rules = ruleRepository
            .findApplicableRules(listing.getTenantId(), listingId, checkInDate);
        
        // 3. 逐日計算價格
        List<DailyPriceBreakdown> dailyPrices = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        
        LocalDate current = checkInDate;
        int nightIndex = 0;
        
        while (current.isBefore(checkOutDate)) {
            RoomCalendar day = calendars.stream()
                .filter(c -> c.getCalendarDate().equals(current))
                .findFirst()
                .orElseThrow(() -> new CalendarNotFoundException(listingId, current));
            
            DailyPriceBreakdown dayPrice = calculateDayPrice(
                listing, day, rules, guests, nightIndex
            );
            
            dailyPrices.add(dayPrice);
            subtotal = subtotal.add(dayPrice.getAdjustedPrice());
            current = current.plusDays(1);
            nightIndex++;
        }
        
        // 4. 計算折扣
        List<DiscountBreakdown> discounts = calculateDiscounts(
            listingId, subtotal, checkInDate, checkOutDate, guests, rules
        );
        
        BigDecimal totalDiscount = discounts.stream()
            .map(DiscountBreakdown::getAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        BigDecimal totalPrice = subtotal.add(totalDiscount);
        BigDecimal averagePrice = totalPrice.divide(
            BigDecimal.valueOf(nightIndex), 2, RoundingMode.HALF_UP
        );
        
        return new DynamicPriceResult(
            listingId,
            listing.getBasePrice(),
            listing.getCurrency(),
            checkInDate,
            checkOutDate,
            guests,
            dailyPrices,
            subtotal,
            discounts,
            totalPrice,
            averagePrice
        );
    }
    
    /**
     * 計算單日價格
     */
    private DailyPriceBreakdown calculateDayPrice(
            Listing listing,
            RoomCalendar day,
            List<PricingRule> rules,
            int guests,
            int nightIndex) {
        
        // 優先檢查手動覆蓋
        if (day.hasPriceOverride()) {
            return DailyPriceBreakdown.withOverride(
                day,
                day.getPriceOverride(),
                day.getPriceType(),
                Collections.emptyList()
            );
        }
        
        // 取得基礎價格
        BigDecimal basePrice = day.getBasePriceAtDate() != null 
            ? day.getBasePriceAtDate() 
            : listing.getBasePrice();
        
        // 取得適用的規則（按 priority 排序）
        List<PricingRule> applicableRules = rules.stream()
            .filter(r -> r.isApplicable(day.getCalendarDate(), guests))
            .sorted(Comparator.comparingInt(PricingRule::getPriority).reversed())
            .collect(Collectors.toList());
        
        // 計算調整後價格
        PriceCalculationContext ctx = PriceCalculationContext.builder()
            .basePrice(basePrice)
            .listing(listing)
            .date(day.getCalendarDate())
            .guests(guests)
            .nightIndex(nightIndex)
            .build();
        
        BigDecimal adjustedPrice = ruleEngine.execute(ctx, applicableRules);
        
        // 判斷價格類型
        PriceType priceType = determinePriceType(day, applicableRules);
        
        return DailyPriceBreakdown.withRules(
            day,
            basePrice,
            adjustedPrice,
            priceType,
            applicableRules
        );
    }
}
```

#### RuleEngine.java
```java
@Component
public class RuleEngine {
    
    private final Map<RuleType, RuleEvaluator> evaluators;
    
    /**
     * 執行規則鏈
     * 規則按 priority 從高到低執行
     */
    public BigDecimal execute(PriceCalculationContext ctx, List<PricingRule> rules) {
        BigDecimal currentPrice = ctx.getBasePrice();
        
        for (PricingRule rule : rules) {
            RuleEvaluator evaluator = evaluators.get(rule.getRuleType());
            if (evaluator == null) {
                continue; // 未知規則類型，跳過
            }
            
            currentPrice = evaluator.evaluate(currentPrice, rule, ctx);
        }
        
        return currentPrice;
    }
}
```

### 5.3 動態定價計算流程

```
┌─────────────────────────────────────────────────────────────────────────┐
│                    動態定價計算流程                                      │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│  1. 取得 basePrice（listings.base_price）                              │
│     │                                                                   │
│     ▼                                                                   │
│  2. 檢查是否有手動覆蓋（Override）                                     │
│     │ 有 → 使用覆蓋價格，直接輸出                                       │
│     │ 無 → 繼續                                                         │
│     │                                                                   │
│     ▼                                                                   │
│  3. 套用規則（按 priority 從高到低）                                     │
│     │                                                                   │
│     ├── WEEKEND → basePrice × 1.3 (週五、週六)                        │
│     ├── HOLIDAY → basePrice × 1.5 (國定假日)                          │
│     ├── PEAK_SEASON → basePrice × 2.0 (連續假期、旺季)                │
│     │                                                                   │
│     ▼                                                                   │
│  4. 套用長住折扣（如果符合條件）                                        │
│     │                                                                   │
│     ├── 7天 → -10%                                                     │
│     └── 30天 → -20%                                                     │
│     │                                                                   │
│     ▼                                                                   │
│  5. 套用早鳥/晚鳥折扣                                                   │
│     │                                                                   │
│     ├── 早鳥 7 天前 → -10%                                              │
│     └── 晚鳥 3 天內 → -15%                                              │
│     │                                                                   │
│     ▼                                                                   │
│  6. 輸出最終價格                                                       │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 5.4 價格精度與規則設定驗證（Sprint 192／193 補充）

以下兩項是實作後補記的規格，先前只存在於 Sprint 計畫與程式碼。

#### 5.4.1 金額精度與捨入（DEF-266、DEF-267）

| 項目 | 規則 |
|------|------|
| 動態定價運算 | 全程 `BigDecimal`，不混入 `double`（`1 - 7.0 / 100 = 0.9299999999999999` 之類的雜訊會讓 1234.50 × 7% 折扣落庫為 1148.08 而非 1148.09，少收一分） |
| 捨入時點 | **在源頭逐晚捨入**至 2 位小數、`RoundingMode.HALF_UP`（`PricingService.PRICE_SCALE`／`PRICE_ROUNDING`），逐晚加總恆等於總額；不交給資料庫 `NUMERIC(_,2)` 隱性四捨五入。與 `PromoService`、`SettlementCalculator` 同一慣例 |
| 退款金額位數 | `PaymentStateService.refundOrderPayment` 拒絕小數位數超過 2 位的退款金額（`stripTrailingZeros().scale() > 2`，回 `E-6009`）。`500.005` 在 Stripe（換算為分後截斷）與資料庫（四捨五入）會得到不同結果；`500.500` 以數值判斷，不誤擋 |

#### 5.4.2 定價規則 `config` 的數值範圍（DEF-268，使用者於 Sprint 193 拍板）

`config` 是自由格式 `Map<String, Object>`，DB 也沒有金額 `CHECK`，所以由 `PricingService.findConfigViolation` 檢查（值為 `null` 視為未設定；存在但不是有限數字，含 `NaN`、`Infinity`、`"10%"`，一律違規）：

| `config` 鍵 | 合法範圍 |
|------------|---------|
| `discountPercent` | 大於 0 且**小於 100**（不允許免費） |
| `multiplier`、`weekendMultiplier` | 大於 0 且**不超過 10**（加價倍率上限） |
| `price`（覆蓋價） | 大於 0 |
| `minNights`、`minDaysAhead` | 至少 1 |

- **建立／更新規則時**：違規拒絕，回 HTTP 422、`E-8001`（FRD §定價規則驗證表寫的是 `E-4001 VALIDATION_ERROR`，已被 [API_Error_Codes.md](./API_Error_Codes.md) 取代）。
- **計算價格時**：略過既存的違規規則並記一筆 WARN（不靜默），避免壞規則讓訂房出現負價或 500。
- FRD 只明訂下限（`≤ 0` 驗證失敗）；上限（`discountPercent` 小於 100、倍率不超過 10）是 Sprint 193 補的規格。`discountPercent > 50` 仍是 FRD 所述「警告但允許」。

---

## 6. M07 結算與撥款模組 (Settlement & Transfer Module)

> **Sprint 262 新增**：本模組先前完全沒有技術設計章節（與 §4 M05、§5 M12 等不同，不是「過期未同步」而是「從未存在」，見 [DEF-364](../04_planning/DEFERRED_ITEMS_TRACKER.md)）。以下內容依實際程式碼撰寫，端點細節見 [API_M07_Settlement.md](api/API_M07_Settlement.md)。

### 6.1 架構概覽

```
com.nextkey.ecommerce/
├── api/
│   └── controller/
│       └── settlement/
│           ├── SettlementController.java     # 結算單列表/詳情/提交/審核/手動補產/沖正 API
│           └── TransferController.java       # 撥款對帳查詢/重試 API
├── core/
│   └── settlement/
│       ├── SettlementService.java            # 持有三個子服務引用＋共用回應 DTO（無獨立業務邏輯）
│       ├── SettlementGenerator.java          # 結算單生成（週排程＋手動補產）、商家查詢
│       ├── SettlementCalculator.java         # GMV／抽成純函數計算（無副作用）
│       ├── SettlementReviewer.java           # 審核狀態機（提交／核准／駁回），核准後觸發 TransferService
│       ├── SettlementReversalService.java    # 雙重授權逆轉（PAID→REVERSAL_PENDING→REVERSED）
│       ├── SettlementAdjustmentService.java  # 跨結算週期退款調整（訂單與訂房退款共用）
│       ├── SettlementMapper.java             # entity ↔ DTO 轉換
│       └── TransferService.java              # Stripe Connect 撥款、失敗重試、webhook 處理
└── domain/
    ├── model/settlement/
    │   ├── SettlementStatement.java          # 結算單主檔，狀態機見 §6.3
    │   ├── Transfer.java                     # 撥款明細（settlementStatementId 唯一索引，一單一筆）
    │   └── CreditNote.java                   # 逆轉產生的會計沖銷記錄
    └── repository/settlement/
        ├── SettlementStatementRepository.java
        ├── TransferRepository.java
        ├── CreditNoteRepository.java
        └── SettlementAdjustmentRepository.java
```

### 6.2 核心類設計

#### SettlementGenerator.java（結算單生成）

```java
@Component
@RequiredArgsConstructor
public class SettlementGenerator {

    // 每週一 00:00（營運時區 Asia/Taipei）觸發；zone 參數避免 UTC 容器把時間切偏
    @Scheduled(cron = "0 0 0 ? * MON", zone = BusinessTime.ZONE_ID)
    public void generateWeeklyStatements() {
        // 逐租戶呼叫 generateStatementForTenant，單一租戶失敗僅記錄，不中斷其他租戶
    }

    /**
     * 手動補產（DEF-287，Sprint 208）：僅 settlement:generate（SUPER_ADMIN）可用。
     * 與排程共用同一套冪等（同租戶+期間已有結算單直接回傳既有的）與
     * 原子認領（UPDATE ... WHERE settled_statement_id IS NULL）防護。
     */
    public SettlementStatementResponse generateStatementManually(
            UUID tenantId, LocalDate periodStart, LocalDate periodEnd) {
        if (periodEnd.isBefore(periodStart)) {
            throw new BusinessException(ErrorCode.E_9008, "periodEnd must not be before periodStart");
        }
        return mapper.toStatementResponse(generateStatementForTenant(tenantId, periodStart, periodEnd));
    }
}
```

#### TransferService.java（撥款，核准後自動觸發）

```java
@Service
@RequiredArgsConstructor
public class TransferService {

    @Transactional
    public Transfer createTransferForStatement(UUID statementId) {
        // 1. 結算單必須是 APPROVED，否則 E_5014
        // 2. 已有 transfer 記錄直接回傳（冪等，不重複撥款）
        // 3. 以 idx_transfers_settlement_statement_id 唯一索引原子佔位（saveAndFlush），
        //    搶輸的併發請求改回傳既有記錄，不會各自真的呼叫 Stripe 一次
        // 4. STRIPE_TRANSFER_ENABLED 關閉，或賣家 Connect 帳戶未就緒
        //    → SKIPPED_ONBOARDING_INCOMPLETE（非失敗，可之後補建）
        // 5. 呼叫 Stripe Transfer.create 成功 → COMPLETED，結算單同步轉 PAID
        // 6. 失敗（BusinessException）→ FAILED，結算單同步轉 FAILED（可由 §4.12 重試）
    }
}
```

### 6.3 結算單狀態機與撥款流程

```
   PENDING ──(商家提交)──► PENDING_REVIEW ──(Admin 核准)──► APPROVED ──(撥款成功)──► PAID
                                 │                              │                     │
                                 └─(Admin 駁回)─► REJECTED       └─(撥款失敗)─► FAILED  │
                                   （釋放已認領的                  │（可重試回 APPROVED） │
                                    訂單/訂房/調整單）                                   │
                                                                          (SUPER_ADMIN/CFO 發起)
                                                                                         ▼
                                                                                REVERSAL_PENDING
                                                                                         │
                                                                     (另一角色確認，產生 CreditNote)
                                                                                         ▼
                                                                                    REVERSED
```

**核准→撥款的同步觸發與失敗隔離**：`SettlementReviewer.approveStatement` 完成狀態轉換與稽核紀錄寫入後，在同一次呼叫內以 `try { transferService.createTransferForStatement(...) } catch (RuntimeException e) { log.error(...) }` 呼叫撥款——**任何撥款例外都只記錄，不會回滾或影響已持久化的「核准」決策**，也不會讓 `PUT .../approve` 的 HTTP 回應失敗；撥款的實際結果需另外呼叫 `GET /v2/transfers` 或結算單詳情查詢。

**逆轉與 Stripe webhook 的交錯**：`TransferService.handleTransferReversedWebhook` 處理 Stripe 主動送達的 `transfer.reversed` 事件，只有結算單仍是 `PAID`（webhook 延遲送達前的預期狀態）才會被改回 `FAILED`；若雙重授權逆轉流程已將其推進到 `REVERSAL_PENDING`／`REVERSED`，則不覆寫，只記錄 info log——避免 webhook 無條件覆寫掉已簽核或已完成的人工決策。

---

## 7. M08 評價與社交互動模組 (Review & Social Interaction Module)

> **Sprint 263 新增**：本模組先前完全沒有技術設計章節（與 §4 M05、§5 M12、§6 M07 等不同，不是「過期未同步」而是「從未存在」，見 [DEF-365](../04_planning/DEFERRED_ITEMS_TRACKER.md)）。以下內容依實際程式碼撰寫，端點細節見 [API_M08_Review.md](api/API_M08_Review.md)。

### 7.1 架構概覽

```
com.nextkey.ecommerce/
├── api/
│   └── controller/
│       ├── ReviewController.java              # 評價 CRUD、搜尋、回覆、投票、圖片管理、評價管理 API
│       └── BookingReviewController.java       # 訂房評價建立、查詢、房東回覆 API
├── core/
│   └── review/
│       ├── ReviewService.java                 # 評價 CRUD、搜尋、投票、圖片管理、評價管理（無獨立拆分子服務）
│       ├── ReviewReplyService.java             # 商家回覆（Sprint 16 從 ReviewService 拆分）
│       └── BookingReviewService.java           # 訂房評價 CRUD（含兩個零呼叫點的方法，見 §7.3）
└── domain/
    ├── model/review/
    │   ├── Review.java                         # 商品/房源評價主檔，reviewType 區分 PRODUCT／ROOM
    │   ├── ReviewReply.java                     # 商家回覆，DB 唯一索引防重複
    │   └── BookingReview.java                   # 訂房評價（含 hostReply 單一欄位，非獨立回覆表）
    └── repository/
        ├── ReviewRepository.java                # 含 registerHelpfulVote 原子投票、searchReviews 多維度查詢
        ├── ReviewReplyRepository.java
        └── review/BookingReviewRepository.java
```

### 7.2 核心類設計

#### ReviewService.java（建立評價的購買證明檢查與原子投票）

```java
@Service
@RequiredArgsConstructor
public class ReviewService {

    /**
     * 建立評價前必須驗證「購買證明」：呼叫者提供的 orderId 或 bookingId
     * 必須屬於自己，且該訂單/訂房引用的 listingId 與評價的 listingId 相同。
     * DEF-331(b) 修復——先前任何已登入使用者可評價任何商品，無購買驗證。
     */
    private void verifyPurchase(UUID listingId, UUID orderId, UUID bookingId, UUID userId) {
        // orderId 與 bookingId 至少要有一個；兩者皆由各自的 Repository 查詢，
        // 確認 order.buyerId == userId（或 booking.userId == userId）
        // 且 order.listingId（或 booking.listingId）等於傳入的 listingId，
        // 任一條件不符皆丟 BusinessException(E_1007)。
    }

    /** 原子化去重計票，取代先前讀-改-寫導致高併發下票數流失的實作。 */
    public ReviewDto.ReviewResponse markHelpful(UUID reviewId) {
        // ReviewRepository.registerHelpfulVote 是單一 native UPDATE：
        //   helpful_votes = helpful_votes || jsonb_build_object(:userId, 1)
        //   helpful_count = 依去重後的 key 數量重新計算
        // 同一使用者重複呼叫不會讓 helpfulCount 持續累加。
    }
}
```

#### 回覆授權邏輯（Sprint 264 已統一，DEF-373）

```java
// ReviewReplyService.createReply／ReviewService.checkReviewManagementAuthorization（標記已/未處理）
// ／BookingReviewService.replyToBookingReview —— 三者現統一要求「同租戶或管理員」
boolean isSameTenant = listing.getTenantId().equals(currentTenantId);
if (!isCurrentUserAdmin() && !isSameTenant) {
    throw new BusinessException(ErrorCode.E_1007);
}
```

> Sprint 264 前，`createReply` 原僅認定確切 `listing.ownerId`，導致同租戶的其他共同擁有人被鎖死；已統一為上述邏輯（見 [SPRINT_264_PLAN.md](../04_planning/SPRINT_264_PLAN.md)）。

### 7.3 已知不對稱與限制（生命週期單純，無須繪製狀態圖）

評價的生命週期比 §6 結算單簡單，只有「建立 → （可任意次編輯）→ （可被作者軟刪除）」，沒有審核或多階段流程，因此本節以表格呈現已知的不對稱，取代狀態圖：

| 項目 | 商品/房源評價（`Review`） | 訂房評價（`BookingReview`） |
|------|:----:|:----:|
| 編輯 | ✅ 無時間窗 | ❌ 無端點 |
| 刪除 | ✅ 軟刪除，✅ 管理員例外（Sprint 264，DEF-374） | ⚠️ `deleteBookingReview` 服務方法存在，**零 Controller 呼叫點**；✅ 管理員例外（Sprint 264，DEF-374） |
| 列表查詢 | ✅ | ⚠️ `getBookingReviews` 服務方法存在，**零 Controller 呼叫點** |
| 統計 | ✅（`@Cacheable`） | ⚠️ `getAverageRatingByBookingId`／`countByBookingId` 存在，**零呼叫點** |
| 搜尋 | ✅（Sprint 18 多維度） | ❌ 無 |
| 圖片 | ✅ 上限 9 張＋媒體驗證 | ⚠️ 無上限、無驗證 |
| 回覆授權 | 同租戶或管理員（Sprint 264 起與右欄一致，DEF-373） | 同租戶或管理員 |
| 回覆重複防護 | ✅ DB 唯一索引（`E-1086`） | ❌ 單一欄位覆寫，無防護 |
| 軟刪除過濾 | ✅ 查詢一律過濾 `isVisible=true` | ❌ `getBookingReviewById` 未過濾 |

**為什麼表中仍有未修復的不對稱**：這些落差是 Sprint 263 撰寫文件時逐行讀碼才系統性發現的。其中 DEF-373（回覆授權不一致）、DEF-374（刪除無管理員例外）兩項使用者判斷為直接碰觸已上線功能的授權邏輯、風險較高，已於 Sprint 264 修復（見 [SPRINT_264_PLAN.md](../04_planning/SPRINT_264_PLAN.md)）。其餘列（訂房評價缺編輯/統計/搜尋端點、圖片無上限、回覆重複防護、軟刪除過濾）屬「訂房評價的功能範圍是否該與商品/房源評價對齊」的產品決策，非文件撰寫本身的範圍，仍登記未修復（見 [DEFERRED_ITEMS_TRACKER.md](../04_planning/DEFERRED_ITEMS_TRACKER.md)），留待後續排程。

---

## 8. M05 退貨申請模組 (Return Request Module)

> **Sprint 265 新增**：本模組先前完全沒有技術設計章節（從未存在，見 [DEF-366](../04_planning/DEFERRED_ITEMS_TRACKER.md)）。以下內容依實際程式碼撰寫，端點細節見 [API_M05_Return.md](api/API_M05_Return.md)。

### 8.1 架構概覽

```
com.nextkey.ecommerce/
├── api/
│   └── controller/returns/
│       └── ReturnRequestController.java    # 買家層 /v2/returns、店家層 /v2/dashboard/returns
├── core/
│   └── returns/
│       └── ReturnRequestService.java       # 建立/撤回/核准/拒絕/收貨，所有狀態轉換皆用條件式 UPDATE（CAS）
└── domain/
    └── model/returns/
        ├── ReturnRequest.java               # 退貨單主檔，狀態機見 §8.2
        └── ReturnRequestItem.java           # 退貨品項，sellableQty/unsellableQty 收貨時才填
```

### 8.2 狀態機

```
REQUESTED ──approve──→ APPROVED ──receive──→ RECEIVED（唯一會動庫存的一步）
    │                      │
    ├──reject──→ REJECTED  │
    └──cancel──→ CANCELLED ←──cancel──┘
```

`REJECTED`／`RECEIVED`／`CANCELLED` 為終態；`REJECTED`／`CANCELLED` 不佔用可退額度，買家可對同一訂單品項重新申請。

### 8.3 多租戶隔離與併發防護

```java
// ReturnRequestService.loadForTenant —— 先查擁有權、才查狀態（承 DEF-036 教訓）
// 排除「系統租戶佔位值」避免把沒有店鋪的消費者誤判為同租戶（Sprint 234）
if (!isAdmin() && (!TenantContext.isStoreTenant(tenantId) || !tenantId.equals(returnRequest.getTenantId()))) {
    throw new BusinessException(ErrorCode.E_1007);
}
```

核准／拒絕／收貨皆用條件式 UPDATE（`reviewIfStatus`／`updateStatusIfCurrent`）取代樂觀鎖版本號，受影響列數為 0 即判定併發搶輸（`E-5017`）；建立退貨單用訂單悲觀鎖序列化可退額度檢查（DEF-132）。詳見 [API_M05_Return.md](api/API_M05_Return.md) §1.2。

---

## 9. M18 客服工單模組 (Support Ticket Module)

> **Sprint 265 新增**：本模組先前完全沒有技術設計章節（從未存在，見 [DEF-367](../04_planning/DEFERRED_ITEMS_TRACKER.md)）。以下內容依實際程式碼撰寫，端點細節見 [API_M18_Support.md](api/API_M18_Support.md)。

### 9.1 架構概覽

```
com.nextkey.ecommerce/
├── api/
│   └── controller/support/
│       └── SupportTicketController.java    # 買家/店家/平台三層路由
├── core/
│   └── support/
│       ├── SupportTicketService.java        # 工單 CRUD、狀態轉換、指派
│       └── SupportMessageService.java       # 留言串（買家/店家各自的 post 方法）
└── domain/
    └── model/support/
        ├── SupportTicket.java               # @DynamicUpdate，避免併發更新互相覆寫（DEF-163）
        └── SupportMessage.java
```

### 9.2 狀態機

```
OPEN ──→ IN_PROGRESS ──→ RESOLVED ──→ CLOSED（終態）
 │                           │
 └──────────→ CLOSED ←───────┘
```

### 9.3 刻意用 404 隱藏工單存在性

```java
// findForStaff / requireStoreTenantUnlessSuperAdmin —— 非本人/非同租戶一律回 E-8008（404）
// 而非 E-8009（403），避免洩漏「工單是否存在」。E-8009 已定義但全庫無任何拋出點（死碼）。
if (!isSuperAdmin && (!TenantContext.isStoreTenant(callerTenantId) || !callerTenantId.equals(ticket.getTenantId()))) {
    throw new BusinessException(ErrorCode.E_8008);
}
```

詳見 [API_M18_Support.md](api/API_M18_Support.md) §1.2。

---

## 10. 異常處理架構 (Exception Handling)

> **Sprint 203 改寫**：v1.0 此節描述的 `ECommerceException` 例外階層、`ErrorResponse.of("E4041", …)` 與一份自成一格的錯誤碼表，**從未存在於程式碼**（全庫只有下列一個基底例外與一個處理器）。已改寫為實際架構；錯誤碼與 HTTP 狀態碼以 [API_Error_Codes.md](./API_Error_Codes.md) 為準。

### 10.1 例外結構

```
com.nextkey.ecommerce.shared.exception/
├── ErrorCode.java                 # enum：錯誤碼字串（"E-XXXX"）＋預設中文訊息
├── BusinessException.java         # 唯一的業務例外基底，持有 ErrorCode
│   ├── CartEmptyException.java
│   ├── CartItemNotFoundException.java
│   └── PromoCodeInvalidException.java
com.nextkey.ecommerce.api.dto/
├── ApiResponse.java               # 統一回應封包（成功與失敗同形，見 SRD_System_Architecture §4）
└── GlobalExceptionHandler.java    # @RestControllerAdvice，唯一的例外→回應對應處
com.nextkey.ecommerce.api.controller/
└── ApiErrorController.java        # 容器 ERROR 分派（sendError 後對 /error 的內部轉送）
```

- 業務規則違反一律丟 `BusinessException(ErrorCode, …)`；**不新增**例外子類別來表達不同錯誤，差異由 `ErrorCode` 表達。
- 給使用者看的訊息預設是 `ErrorCode` 的中文訊息，**不回顯**例外內的動態英文細節（AI-2418）；只有經 `BusinessException.withFormattedMessage` 建構、且訊息模板本就設計了佔位符（張數、索引）的例外，才把代入後的文字回給使用者。

### 10.2 統一異常處理

`GlobalExceptionHandler` 對應規則（完整表格見 [API_Error_Codes.md §2](./API_Error_Codes.md)）：

| 例外 | HTTP | `code` |
|------|:----:|--------|
| `BusinessException` | 依 `ErrorCode`（`mapErrorCodeToStatus`） | 該例外的 `ErrorCode` |
| `MethodArgumentNotValidException`、`BindException`、`HttpMessageNotReadableException`、`MethodArgumentTypeMismatchException` | 400 | `E-9000`（前兩者帶 `errors`） |
| `MissingServletRequestParameterException` | 400 | `E-9005` |
| `AuthenticationException`／`BadCredentialsException` | 401 | `E-1000`／`E-1001` |
| `AccessDeniedException` | 403 | `E-1007` |
| `NoResourceFoundException` | 404 | `E-4041` |
| `HttpRequestMethodNotSupportedException`、`HttpMediaTypeNotSupportedException` | 405／415 | `E-9000` |
| 其他 `Exception` | 500 | `E-9900` |

`mapErrorCodeToStatus` 是**窮舉的 `switch`、沒有 `default`**：新增 `ErrorCode` 卻沒決定 HTTP 狀態碼會直接編譯失敗，而不是靜默落入 500（Sprint 171，DEF-223）。

### 10.3 錯誤碼對照表

已移至 [API_Error_Codes.md §4](./API_Error_Codes.md)（138 個錯誤碼，由 `ErrorCodeDocDriftTest` 與程式碼逐列比對）。v1.0 在此列出的 17 個錯誤碼中，**12 個與實作不符**（`E-1001`、`E-2001`、`E-3001`～`E-3003`、`E-4001` 在實作中意義不同；`E-2020`、`E-4021`～`E-4025` 在實作中不存在），只有 `E-1002`、`E-2003`、`E-4041`、`E-4091`、`E-4092` 大致相符；不可再引用該表。

---

## 11. 修訂歷史

| 版本 | 日期 | 作者 | 變更說明 |
|------|------|------|----------|
| v1.0 | 2026-04-09 | Marcus (SD-Architect) | 初始版本，包含 Phase 1 核心模組技術設計 |
| v1.1 | 2026-09-26 | Claude Code（Sprint 203） | 文件一致性檢查（DEF-280）：§6 異常處理架構改寫為實際架構（原描述的例外階層與錯誤碼表從未存在於程式碼），錯誤碼移至 [API_Error_Codes.md](./API_Error_Codes.md)；新增 §5.4 價格精度與規則設定驗證（Sprint 192／193）。**未逐段核對**其餘章節（§1～§5.3）與程式碼的一致性，例如 §4.3 的 `X-Idempotency-Key` 標頭與實作的 `Idempotency-Key` 不同（見 DEF-285） |
| v1.2 | 2026-10-03 | Claude Code（Sprint 244） | 文件對齊（3/3）：§1.2 Token 效期改為 15 分鐘（實際類別 `JwtTokenService`，效期由設定注入，見註記）；§4 M05 建單草稿移除「建單即 PAID」，`ALLOWED_TRANSITIONS` 草稿加註記（實際規則見 `OrderStateMachine`）；序列圖的付款標示改為「付款成功 CREATED→PAID」 |
| v1.3 | 2026-10-13 | Claude Code（Sprint 262） | 新增 §6 M07 結算與撥款模組（架構概覽、核心類設計、狀態機與撥款流程），此模組先前完全沒有技術設計章節（DEF-364）；原 §6／§7 依序改為 §7／§8；檔頭版本號同步校正（先前長期停滯於 v1.0） |
| v1.4 | 2026-10-14 | Claude Code（Sprint 263） | 新增 §7 M08 評價與社交互動模組（架構概覽、核心類設計、已知不對稱表），此模組先前完全沒有技術設計章節（DEF-365）；原 §7／§8 依序改為 §8／§9；**同時修正 Sprint 262 遺留的章節編號漂移**（原 §7 異常處理架構的內部子標題仍寫 ### 6.1～6.3，未隨外層標題改號，本次一併更正為 ### 8.1～8.3） |
| v1.5 | 2026-10-14 | Claude Code（Sprint 265） | 新增 §8 M05 退貨申請模組、§9 M18 客服工單模組（皆先前完全沒有技術設計章節，DEF-366／367）；原 §8／§9 依序改為 §10／§11，§8 異常處理架構的內部子標題同步改為 ### 10.1～10.3。**同時修正 Sprint 264 遺留的文件缺口**：§7 M08 評價模組的回覆授權程式碼範例與不對稱表仍描述 DEF-373／374 修復前的舊邏輯，Sprint 264 修復時忘了同步這份文件，本次一併更正為已修復狀態 |

---

**文檔版本**: AISDLC v0.09
**模板維護**: AISDLC Framework Team
**最後更新**: 2026-10-14
