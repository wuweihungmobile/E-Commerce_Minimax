package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.order.Booking;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;

/**
 * V88 遷移的資料邏輯（Sprint 236，DEF-319 訂房側）：訂房的租戶與其房源所屬租戶不一致者，改成房源的租戶；
 * 本來就一致的訂房、以及訂房以外的欄位一律不動。
 *
 * <p>「不一致」在修復前的真實樣貌是：沒有店鋪的消費者訂房，被蓋成系統租戶佔位值；或 A 店成員去訂 B 店的房，被蓋成 A 店。
 * 兩者在資料上都是「訂房的 tenant_id ≠ 房源的 tenant_id」，本測試以一般租戶代表買家的租戶（測試庫不一定有系統租戶那一列）。
 *
 * <p>整合測試的資料庫是 Hibernate 建的，不會執行 Flyway；這裡直接讀 {@code db/migration/V88__*.sql} 的實際內容，
 * 對種好的資料執行。整個測試在交易內、結束後回滾，不會動到其他測試共用資料庫裡的任何列。
 * （Flyway 本身能不能跑、語法對不對，由 {@code make validate-e2e}／{@code make validate-schema} 對乾淨資料庫執行遷移驗證。）
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("integration-test")
@Transactional
@DisplayName("Sprint 236: V88 遷移——訂房的租戶回填成房源所屬的店鋪")
class BackfillBookingTenantMigrationIntegrationTest {

    private static final String MIGRATION = "db/migration/V88__Backfill_Booking_Tenant_To_Listing_Store.sql";

    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private ListingRepository listingRepository;
    @Autowired private BookingRepository bookingRepository;

    private Tenant newTenant(final String label) {
        long stamp = System.nanoTime();
        return tenantRepository.saveAndFlush(Tenant.builder()
                .name("V88 " + label + " " + stamp).slug("v88-" + label + "-" + stamp)
                .contactEmail("v88-" + label + "-" + stamp + "@example.com").status(Tenant.TenantStatus.ACTIVE).build());
    }

    private User newUser(final User.UserRole role) {
        return userRepository.saveAndFlush(User.builder()
                .email("v88-" + System.nanoTime() + "@example.com").passwordHash("dummy").role(role).status("ACTIVE").build());
    }

    /** 房源要同時設關聯（寫入資料庫的是 tenant／owner 關聯，tenantId／ownerId 只是唯讀影子欄位）。 */
    private Listing newRoom(final Tenant tenant, final User owner) {
        return listingRepository.saveAndFlush(Listing.builder()
                .tenant(tenant).owner(owner)
                .listingType(Listing.ListingType.ROOM).title("V88 房源 " + System.nanoTime())
                .basePrice(new BigDecimal("1500.00")).status(Listing.ListingStatus.ACTIVE).build());
    }

    private Booking newBooking(final Tenant bookingTenant, final User buyer, final Listing room,
                               final Booking.BookingStatus status, final String guestName) {
        return bookingRepository.saveAndFlush(Booking.builder()
                .tenant(bookingTenant).user(buyer).roomListing(room)
                .checkInDate(LocalDate.now().plusDays(30)).checkOutDate(LocalDate.now().plusDays(32))
                .guestCount(2).status(status).totalAmount(new BigDecimal("3000.00")).guestName(guestName).build());
    }

    private UUID tenantOf(final Booking booking) {
        return jdbcTemplate.queryForObject("SELECT tenant_id FROM bookings WHERE id = ?", UUID.class, booking.getId());
    }

    private int runMigration() throws Exception {
        String sql = StreamUtils.copyToString(new ClassPathResource(MIGRATION).getInputStream(), StandardCharsets.UTF_8);
        return jdbcTemplate.update(sql);
    }

    @Test
    @DisplayName("買家租戶 ≠ 房源租戶的訂房改成房源的租戶（含 A 店成員訂 B 店的房）；本來就一致的訂房、其他欄位一律不動")
    void movesBookingsToTheStoreOfTheirRoom() throws Exception {
        Tenant store = newTenant("store");
        Tenant otherStore = newTenant("other");
        Tenant buyerTenant = newTenant("buyer");
        User host = newUser(User.UserRole.STORE_OWNER);
        User otherHost = newUser(User.UserRole.STORE_OWNER);
        User consumer = newUser(User.UserRole.BUYER);
        Listing room = newRoom(store, host);
        Listing otherRoom = newRoom(otherStore, otherHost);

        Booking consumerBooking = newBooking(buyerTenant, consumer, room, Booking.BookingStatus.PAID, "消費者訂房");
        Booking crossStoreBooking = newBooking(otherStore, consumer, room, Booking.BookingStatus.CREATED, "A 店成員訂 B 店");
        Booking alreadyCorrect = newBooking(store, host, room, Booking.BookingStatus.CONFIRMED, "本來就對");
        Booking otherStoreBooking = newBooking(otherStore, consumer, otherRoom, Booking.BookingStatus.CREATED, "別家店自己的房");

        runMigration();

        assertThat(tenantOf(consumerBooking)).as("消費者的訂房（租戶≠房源租戶）").isEqualTo(store.getId());
        assertThat(tenantOf(crossStoreBooking)).as("A 店成員訂 B 店的房").isEqualTo(store.getId());
        assertThat(tenantOf(alreadyCorrect)).as("本來就一致的訂房不動").isEqualTo(store.getId());
        assertThat(tenantOf(otherStoreBooking)).as("別家店房源的訂房留在別家店，不可被誤搬到 store").isEqualTo(otherStore.getId());

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM bookings WHERE id = ?", String.class, consumerBooking.getId()))
                .as("只改租戶，不動狀態").isEqualTo("PAID");
        assertThat(jdbcTemplate.queryForObject("SELECT total_amount FROM bookings WHERE id = ?", BigDecimal.class,
                consumerBooking.getId())).as("只改租戶，不動金額").isEqualByComparingTo("3000.00");
        assertThat(jdbcTemplate.queryForObject("SELECT user_id FROM bookings WHERE id = ?", UUID.class, consumerBooking.getId()))
                .as("只改租戶，訂房人不變（消費者仍看得到自己的訂房）").isEqualTo(consumer.getId());
    }

    @Test
    @DisplayName("可重複執行：第二次不會再更新任何列")
    void isIdempotent() throws Exception {
        Tenant store = newTenant("store");
        Tenant buyerTenant = newTenant("buyer");
        User host = newUser(User.UserRole.STORE_OWNER);
        User consumer = newUser(User.UserRole.BUYER);
        Booking booking = newBooking(buyerTenant, consumer, newRoom(store, host), Booking.BookingStatus.CREATED, "冪等");

        runMigration();
        int secondRun = runMigration();

        assertThat(tenantOf(booking)).isEqualTo(store.getId());
        assertThat(secondRun).as("第一次已把所有租戶不一致的訂房修齊，第二次沒有任何列可更新").isZero();
    }
}
