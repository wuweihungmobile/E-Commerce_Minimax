package com.nextkey.ecommerce.integration;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;

/**
 * 訂房付款端點（Sprint 221，DEF-303 (1)）：經過真實 JWT 與<b>生產</b>權限表的 HTTP 層。
 *
 * <p>DEF-298 的教訓：買家（BUYER）有 {@code booking:create}／{@code booking:cancel}，但<b>沒有</b>
 * {@code booking:update}。付款端點若只要求 {@code booking:update}，一般買家付款一律 403，而 service 層測試看不到這件事。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("integration-test")
@DisplayName("IT-BOOKING-PAY-API: 訂房付款端點（Sprint 221）")
class BookingPaymentApiIntegrationTest {

    private static final String PASSWORD = "SecurePass123!";
    private static final String STRIPE_TOGGLE = "STRIPE_PAYMENT_ENABLED";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;

    @MockBean private FeatureToggleService featureToggleService;
    @MockBean private PaymentGatewayFactory paymentGatewayFactory;

    private Tenant tenant;
    private UUID roomListingId;
    private String buyerToken;
    private String otherBuyerToken;
    private int nextNight;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(featureToggleService.isFeatureEnabled(anyString())).thenReturn(true);
        lenient().when(featureToggleService.isFeatureEnabled("DYNAMIC_PRICING_ENABLED")).thenReturn(false);
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_TOGGLE)).thenReturn(false);
        lenient().doNothing().when(featureToggleService).checkFeatureEnabled(anyString());
        long stamp = System.nanoTime();
        tenant = tenantRepository.save(Tenant.builder().name("Booking Pay API Tenant").slug("booking-pay-api-" + stamp)
                .contactEmail("booking-pay-api-" + stamp + "@tenant.com").contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE).build());
        User host = userRepository.save(User.builder().email("booking-pay-api-host-" + stamp + "@example.com")
                .passwordHash("dummy").fullName("Host").role(User.UserRole.STORE_OWNER).status("ACTIVE")
                .tenantId(tenant.getId()).build());
        roomListingId = listingRepository.save(Listing.builder().tenant(tenant).owner(host)
                .listingType(Listing.ListingType.ROOM).title("Booking Pay API Room")
                .basePrice(new BigDecimal("1500.00")).status(Listing.ListingStatus.ACTIVE).build()).getId();
        jdbcTemplate.update("INSERT INTO rooms (listing_id, max_guests, room_count, check_in_time, check_out_time, "
                + "created_at, updated_at) VALUES (?, 4, 1, '15:00'::time, '11:00'::time, NOW(), NOW())", roomListingId);
        buyerToken = registerBuyerAndLogin("booking-pay-api-buyer-" + stamp + "@example.com");
        otherBuyerToken = registerBuyerAndLogin("booking-pay-api-other-" + stamp + "@example.com");
        nextNight = 5;
    }

    @Test
    @DisplayName("一般買家（只有 booking:create、沒有 booking:update）付得了自己的訂房：200、訂房 PAID")
    void buyer_canPayOwnBooking() throws Exception {
        String bookingId = buyerCreatesBooking();

        mockMvc.perform(post("/v2/bookings/" + bookingId + "/pay").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("PAID")))
                .andExpect(jsonPath("$.data.paymentStatus", is("SUCCESS")))
                .andExpect(jsonPath("$.data.canPay", is(false)));

        mockMvc.perform(get("/v2/orders/bookings/" + bookingId + "/payment").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("PAID")))
                .andExpect(jsonPath("$.data.paymentProvider", is("mock")));
        mockMvc.perform(get("/v2/bookings/" + bookingId).header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("PAID")));
    }

    @Test
    @DisplayName("別的買家付不了這筆訂房：403（E-1007）；沒登入：401")
    void otherBuyerAndAnonymous_cannotPay() throws Exception {
        String bookingId = buyerCreatesBooking();

        mockMvc.perform(post("/v2/bookings/" + bookingId + "/pay").header("Authorization", "Bearer " + otherBuyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("E-1007")));
        mockMvc.perform(post("/v2/bookings/" + bookingId + "/pay"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/v2/bookings/" + bookingId).header("Authorization", "Bearer " + buyerToken))
                .andExpect(jsonPath("$.data.status", is("CREATED")));
    }

    @Test
    @DisplayName("啟用 Stripe 後 Mock 付款被拒（E-6004），訂房仍是待付款")
    void mockPay_whileStripeEnabled_isRejected() throws Exception {
        String bookingId = buyerCreatesBooking();
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_TOGGLE)).thenReturn(true);

        mockMvc.perform(post("/v2/bookings/" + bookingId + "/pay").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.code", is("E-6004")));

        mockMvc.perform(get("/v2/bookings/" + bookingId).header("Authorization", "Bearer " + buyerToken))
                .andExpect(jsonPath("$.data.status", is("CREATED")));
    }

    @Test
    @DisplayName("Stripe：買家發起結帳拿到重導網址；回跳確認後訂房 PAID")
    void stripe_buyerCheckoutThenReturnConfirm() throws Exception {
        String bookingId = buyerCreatesBooking();
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_TOGGLE)).thenReturn(true);
        String sessionId = "cs_test_" + UUID.randomUUID();
        lenient().when(paymentGatewayFactory.createCheckoutSession(eq("STRIPE"), any()))
                .thenReturn(PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                        .sessionId(sessionId).sessionUrl("https://checkout.stripe.com/c/pay/" + sessionId)
                        .status("open").paymentStatus("unpaid").build());
        lenient().when(paymentGatewayFactory.retrieveCheckoutSession("STRIPE", sessionId))
                .thenReturn(PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                        .sessionId(sessionId).paymentIntentId("pi_api").status("complete").paymentStatus("paid").build());

        mockMvc.perform(post("/v2/bookings/" + bookingId + "/pay/checkout").header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingId", is(bookingId)))
                .andExpect(jsonPath("$.data.sessionId", is(sessionId)))
                .andExpect(jsonPath("$.data.sessionUrl", is("https://checkout.stripe.com/c/pay/" + sessionId)));

        mockMvc.perform(get("/v2/bookings/" + bookingId + "/pay/checkout/return").param("sessionId", sessionId)
                        .header("Authorization", "Bearer " + buyerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderStatus", is("PAID")))
                .andExpect(jsonPath("$.data.paymentStatus", is("SUCCESS")));
    }

    @Test
    @DisplayName("Stripe：別的買家不能替這筆訂房發起結帳（403）")
    void stripe_otherBuyerCannotStartCheckout() throws Exception {
        String bookingId = buyerCreatesBooking();
        lenient().when(featureToggleService.isFeatureEnabled(STRIPE_TOGGLE)).thenReturn(true);

        mockMvc.perform(post("/v2/bookings/" + bookingId + "/pay/checkout").header("Authorization", "Bearer " + otherBuyerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code", is("E-1007")));
    }

    private String buyerCreatesBooking() throws Exception {
        LocalDate checkIn = LocalDate.now().plusDays(nextNight);
        nextNight += 3;
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "roomListingId", roomListingId.toString(),
                "checkInDate", checkIn.toString(),
                "checkOutDate", checkIn.plusDays(2).toString(),
                "guestCount", 2,
                "guestName", "Test Guest",
                "guestPhone", "0912345678",
                "guestEmail", "guest@example.com"));
        String response = mockMvc.perform(post("/v2/bookings").header("Authorization", "Bearer " + buyerToken)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asText();
    }

    /** 註冊買家（註冊時不會設定租戶，比照 BookingControllerE2ETest 補上）並登入取得 token。 */
    private String registerBuyerAndLogin(final String email) throws Exception {
        mockMvc.perform(post("/v2/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email, "password", PASSWORD, "userType", "BUYER"))))
                .andExpect(status().isCreated());
        userRepository.findByEmail(email).ifPresent(user -> {
            user.setTenantId(tenant.getId());
            userRepository.save(user);
        });
        String response = mockMvc.perform(post("/v2/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
