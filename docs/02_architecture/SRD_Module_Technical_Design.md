# E-Commerce 系統 — 模組技術設計 v1.0

> **文檔類型**: SRD - Module Technical Design
> **版本**: v1.0
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

#### TokenService.java
```java
@Service
public class TokenService {
    
    private final JwtTokenProvider jwtTokenProvider;
    
    // Token 有效期設定
    private static final long ACCESS_TOKEN_VALIDITY = 30 * 60; // 30 分鐘
    
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
            
            // 6. 狀態流轉：CREATED (= PAID for Phase 1)
            order = orderRepository.save(order);
            stateMachine.transition(order, OrderStatus.PAID, "system", OrderOperatorType.SYSTEM);
            
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
     │                    │ 狀態流轉 CREATED→PAID   │                      │
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

---

## 6. 異常處理架構 (Exception Handling)

### 6.1 異常層級結構

```
com.nextkey.ecommerce.shared.exception/
├── ECommerceException.java              # 基底異常
│   ├── validation/                      # 驗證異常
│   │   ├── ValidationException.java
│   │   └── FieldValidationException.java
│   ├── domain/                          # 領域異常
│   │   ├── EntityNotFoundException.java
│   │   ├── DuplicateEntityException.java
│   │   ├── InvalidStateTransitionException.java
│   │   └── BusinessRuleViolationException.java
│   ├── auth/                            # 認證異常
│   │   ├── InvalidCredentialsException.java
│   │   ├── AccountSuspendedException.java
│   │   └── TokenExpiredException.java
│   ├── inventory/                        # 庫存異常
│   │   └── InsufficientInventoryException.java
│   └── pricing/                          # 定價異常
│       └── FeatureNotEnabledException.java
```

### 6.2 統一異常處理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        return ResponseEntity
            .status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("E4041", ex.getMessage()));
    }
    
    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<ErrorResponse> handleDuplicate(DuplicateEntityException ex) {
        return ResponseEntity
            .status(HttpStatus.CONFLICT)
            .body(ErrorResponse.of("E4091", ex.getMessage()));
    }
    
    @ExceptionHandler(InsufficientInventoryException.class)
    public ResponseEntity<ErrorResponse> handleInsufficientInventory(InsufficientInventoryException ex) {
        return ResponseEntity
            .status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(ErrorResponse.of("E4021", ex.getMessage(), ex.getDetails()));
    }
}
```

### 6.3 錯誤碼對照表

| 錯誤碼 | HTTP 狀態 | 說明 | 對應模組 |
|--------|-----------|------|----------|
| E-1001 | 401 | JWT 無效 | M03 |
| E-1002 | 401 | JWT 過期 | M03 |
| E-2001 | 403 | 無權限 | 全域 |
| E-2003 | 403 | 租戶上下文不明 | 全域 |
| E-2020 | 403 | 功能未啟用 | M12/M17 |
| E-3001 | 409 | Email 已被註冊 | M03 |
| E-3002 | 401 | 登入認證失敗 | M03 |
| E-3003 | 403 | 帳號被停用 | M03 |
| E-4001 | 400 | 驗證失敗 | 全域 |
| E-4021 | 422 | 庫存不足 | M05 |
| E-4022 | 409 | 併發衝突 | M05 |
| E-4023 | 400 | 狀態轉換不允許 | M05 |
| E-4024 | 403 | 非訂單擁有者 | M05 |
| E-4025 | 404 | 訂單不存在 | M05 |
| E-4041 | 404 | 資源不存在 | 全域 |
| E-4091 | 409 | 資源名稱衝突 | 全域 |
| E-4092 | 409 | 申請已存在 | M17 |

---

## 7. 修訂歷史

| 版本 | 日期 | 作者 | 變更說明 |
|------|------|------|----------|
| v1.0 | 2026-04-09 | Marcus (SD-Architect) | 初始版本，包含 Phase 1 核心模組技術設計 |

---

**文檔版本**: AISDLC v0.09
**模板維護**: AISDLC Framework Team
**最後更新**: 2026-04-09
