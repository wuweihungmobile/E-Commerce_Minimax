package com.nextkey.ecommerce.integration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.api.dto.CartDto;
import com.nextkey.ecommerce.integration.util.TestSecurityContextHelper;
import com.nextkey.ecommerce.core.cart.RedisCartService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.logistics.ShippingTemplate;
import com.nextkey.ecommerce.domain.model.order.Order;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.OrderStateLogRepository;
import com.nextkey.ecommerce.domain.repository.ProductSkuRepository;
import com.nextkey.ecommerce.domain.repository.ShippingTemplateRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * M11 ShippingTemplate 接入訂單結帳整合測試 (Sprint 23 US-005 / DEF-008)
 *
 * 測試範圍：
 * - IT-SFEE-001: 訂單小計 < 免運門檻 → totalAmount = 小計 + 運費
 * - IT-SFEE-002: 訂單小計 >= 免運門檻 → totalAmount = 小計（免運）
 * - IT-SFEE-003: 無運費模板 → totalAmount = 小計（免運）
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("IT-M11-SFEE: M11 ShippingTemplate 接入訂單結帳整合測試")
class M11ShippingFeeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderRepository orderRepository;

    @MockBean
    private OrderStateLogRepository orderStateLogRepository;

    @MockBean
    private TenantRepository tenantRepository;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private ListingRepository listingRepository;

    @MockBean
    private ProductSkuRepository productSkuRepository;

    @MockBean
    private RedisCartService redisCartService;

    @MockBean
    private ShippingTemplateRepository shippingTemplateRepository;

    @MockBean
    private com.nextkey.ecommerce.core.feature.FeatureToggleService featureToggleService;

    private static final String ORDERS_URL = "/v2/orders";
    private static final UUID TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID LISTING_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @AfterEach
    void clearSecurityContext() {
        TestSecurityContextHelper.clear();
    }

    private void setupBaseMocks(BigDecimal itemPrice) {
        Tenant tenant = Tenant.builder()
                .name("Test Tenant")
                .slug("test-tenant")
                .status(Tenant.TenantStatus.ACTIVE)
                .build();
        tenant.setId(TENANT_ID);

        User user = User.builder()
                .email("test@example.com")
                .fullName("Test User")
                .build();
        user.setId(USER_ID);

        Listing listing = Listing.builder()
                .tenantId(TENANT_ID)
                .listingType(Listing.ListingType.PRODUCT)
                .title("測試商品")
                .status(Listing.ListingStatus.ACTIVE)
                .basePrice(itemPrice)
                .currency("TWD")
                .build();
        listing.setId(LISTING_ID);

        CartDto.CartItemResponse cartItem = CartDto.CartItemResponse.builder()
                .listingId(LISTING_ID)
                .quantity(1)
                .unitPrice(itemPrice)
                .subtotal(itemPrice)
                .build();

        CartDto.CartResponse cart = CartDto.CartResponse.builder()
                .items(Collections.singletonList(cartItem))
                .build();

        TestSecurityContextHelper.setUserContext(USER_ID, "buyer@test.com", TENANT_ID, "BUYER", List.of("order:create"));

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant));
        when(redisCartService.getCart(USER_ID, TENANT_ID)).thenReturn(cart);
        when(listingRepository.findById(LISTING_ID)).thenReturn(Optional.of(listing));
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            o.setId(UUID.randomUUID());
            o.setCreatedAt(Instant.now());
            o.setUpdatedAt(Instant.now());
            return o;
        });
        when(orderRepository.findByUserIdOrderByCreatedAtDesc(eq(USER_ID), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));
    }

    private ShippingTemplate buildFreeThresholdTemplate(BigDecimal fixedFee, BigDecimal threshold) {
        ShippingTemplate template = ShippingTemplate.builder()
                .name("標準運費")
                .feeType(ShippingTemplate.FeeType.FREE_THRESHOLD)
                .fixedAmount(fixedFee)
                .freeThreshold(threshold)
                .build();
        template.setId(UUID.randomUUID());
        template.setTenantId(TENANT_ID);
        return template;
    }

    // ── IT-SFEE-001: 小計 < 免運門檻 → 加收運費 ─────────────────────

    @Test
    @DisplayName("IT-SFEE-001: 訂單小計未達免運門檻 → totalAmount = 小計 + 運費")
    void createOrder_belowFreeThreshold_includesShippingFee() throws Exception {
        BigDecimal itemPrice = BigDecimal.valueOf(200);
        BigDecimal shippingFee = BigDecimal.valueOf(60);
        BigDecimal freeThreshold = BigDecimal.valueOf(500);

        setupBaseMocks(itemPrice);
        when(shippingTemplateRepository.findByTenantId(TENANT_ID))
                .thenReturn(Collections.singletonList(
                        buildFreeThresholdTemplate(shippingFee, freeThreshold)));

        String body = """
            {"orderType": "PRODUCT", "shippingAddress": "台北市", "shippingRecipientName": "收件人", "shippingPhone": "0912345678"}
            """;

        mockMvc.perform(post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shippingFee").value(60))
                .andExpect(jsonPath("$.data.totalAmount").value(260));
    }

    // ── IT-SFEE-002: 小計 >= 免運門檻 → 免運 ──────────────────────

    @Test
    @DisplayName("IT-SFEE-002: 訂單小計達免運門檻 → shippingFee = 0，totalAmount = 小計")
    void createOrder_aboveFreeThreshold_noShippingFee() throws Exception {
        BigDecimal itemPrice = BigDecimal.valueOf(600);
        BigDecimal shippingFee = BigDecimal.valueOf(60);
        BigDecimal freeThreshold = BigDecimal.valueOf(500);

        setupBaseMocks(itemPrice);
        when(shippingTemplateRepository.findByTenantId(TENANT_ID))
                .thenReturn(Collections.singletonList(
                        buildFreeThresholdTemplate(shippingFee, freeThreshold)));

        String body = """
            {"orderType": "PRODUCT", "shippingAddress": "台北市", "shippingRecipientName": "收件人", "shippingPhone": "0912345678"}
            """;

        mockMvc.perform(post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shippingFee").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(600));
    }

    // ── IT-SFEE-003: 無運費模板 → 免運 ───────────────────────────

    @Test
    @DisplayName("IT-SFEE-003: 無運費模板 → shippingFee = 0，totalAmount = 小計")
    void createOrder_noShippingTemplate_noShippingFee() throws Exception {
        BigDecimal itemPrice = BigDecimal.valueOf(300);

        setupBaseMocks(itemPrice);
        when(shippingTemplateRepository.findByTenantId(TENANT_ID))
                .thenReturn(Collections.emptyList());

        String body = """
            {"orderType": "PRODUCT", "shippingAddress": "台北市", "shippingRecipientName": "收件人", "shippingPhone": "0912345678"}
            """;

        mockMvc.perform(post(ORDERS_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shippingFee").value(0))
                .andExpect(jsonPath("$.data.totalAmount").value(300));
    }
}
