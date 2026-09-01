package com.nextkey.ecommerce.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.nextkey.ecommerce.core.review.ReviewService;
import com.nextkey.ecommerce.domain.model.listing.Listing;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.model.user.User;
import com.nextkey.ecommerce.domain.repository.ListingRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.UserRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

/**
 * 評價「有幫助」投票的去重與併發正確性整合測試（Sprint 105，DEF-054；真實 PostgreSQL）。
 *
 * <p>修復前 {@code ReviewService.markHelpful} 有兩個各自獨立的缺陷：
 *
 * <ol>
 *   <li><b>無去重</b>：{@code votes.put(userId, currentVotes + 1)} 讓**同一個人**可以無限次
 *       遞增。前端 {@code ReviewList.tsx} 顯示的是「N <b>人</b>覺得有幫助」（人數語意），
 *       且 {@code helpfulCount} 是 {@code ReviewSearchCriteria} 的排序欄位，
 *       任何登入者都能直接打 API 灌高任意評價的排名。</li>
 *   <li><b>讀後寫競態</b>：整個 JSON map 被讀出、在記憶體改完再整份寫回，
 *       {@code Review} 沒有 {@code @Version}，因此**不同使用者**的併發投票會互相覆蓋而靜默漏計。</li>
 * </ol>
 *
 * <p>規格 {@code IT-M08-203} 只定義了「POST 一次 → helpfulCount+1」，未涵蓋重複投票；
 * 每人一票的語意由使用者於 Sprint 105 拍板（見 SPRINT_105_PLAN §2）。
 *
 * <p>既有的 {@code ReviewServiceTest.markHelpful_firstVote_incrementsCount} 只測首次投票，
 * 且 mock 掉 repository——兩個缺陷都在它的視野之外。
 */
@SpringBootTest
@ActiveProfiles("integration-test")
@DisplayName("IT-M08-VOTE: 評價有幫助投票去重與併發（Sprint 105 / DEF-054）")
class M08ReviewHelpfulVotingIntegrationTest {

    @Autowired private ReviewService reviewService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private TenantRepository tenantRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ListingRepository listingRepository;

    /** 併發投票的相異使用者數。 */
    private static final int VOTERS = 10;

    /** 同一人重複投票的次數。 */
    private static final int REPEAT_VOTES = 5;

    private UUID listingId;
    private UUID reviewerId;
    private UUID tenantId;

    @BeforeEach
    void setUp() {
        long stamp = System.nanoTime();
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("Helpful Vote Tenant")
                .slug("vote-" + stamp)
                .contactEmail("vote-" + stamp + "@tenant.com")
                .contactPhone("+886-123456789")
                .status(Tenant.TenantStatus.ACTIVE)
                .build());

        tenantId = tenant.getId();
        User owner = seedUser(tenantId, "owner", User.UserRole.STORE_OWNER);
        reviewerId = seedUser(tenantId, "reviewer", User.UserRole.BUYER).getId();

        listingId = listingRepository.save(Listing.builder()
                .tenant(tenant)
                .owner(owner)
                .listingType(Listing.ListingType.PRODUCT)
                .title("Helpful Vote Product")
                .basePrice(new BigDecimal("100.00"))
                .status(Listing.ListingStatus.ACTIVE)
                .build()).getId();
    }

    private User seedUser(final UUID ownerTenantId, final String tag, final User.UserRole role) {
        long stamp = System.nanoTime();
        return userRepository.save(User.builder()
                .email("vote-" + tag + "-" + stamp + "@example.com")
                .passwordHash("dummy")
                .fullName("Vote " + tag)
                .role(role)
                .status("ACTIVE")
                .tenantId(ownerTenantId)
                .build());
    }

    /**
     * 種一則評價；helpful_votes 起始為空 map、helpful_count 為 0。
     *
     * <p>{@code is_anonymous} / {@code is_handled} 必須顯式給值：{@code toReviewResponse}
     * 會把兩者拆箱成 {@code boolean}，null 會直接 NPE。本機測試 DB 由 {@code ddl-auto=update}
     * 建表，**沒有** Flyway migration 上的 {@code DEFAULT false}，省略欄位就會留 null。
     */
    private UUID seedReview() {
        UUID reviewId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO reviews
                    (id, listing_id, user_id, review_type, rating, title, content,
                     helpful_votes, helpful_count, is_visible, is_anonymous, is_handled,
                     created_at, updated_at)
                VALUES (?, ?, ?, 'PRODUCT', 5, 'Great', 'Nice product',
                        '{}'::jsonb, 0, true, false, false,
                        NOW(), NOW())
                """, reviewId, listingId, reviewerId);
        return reviewId;
    }

    private int helpfulCountOf(final UUID reviewId) {
        return jdbcTemplate.queryForObject(
                "SELECT helpful_count FROM reviews WHERE id = ?", Integer.class, reviewId);
    }

    private int distinctVotersOf(final UUID reviewId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jsonb_object_keys((SELECT helpful_votes FROM reviews WHERE id = ?))",
                Integer.class, reviewId);
    }

    @Test
    @DisplayName("同一使用者連投 5 次 → helpfulCount 維持 1（每人一票，冪等）")
    void repeatedVotesFromSameUserCountOnce() {
        UUID reviewId = seedReview();
        UUID voterId = seedUser(tenantId, "single", User.UserRole.BUYER).getId();

        try {
            TenantContext.setCurrentUser(voterId);
            for (int i = 0; i < REPEAT_VOTES; i++) {
                reviewService.markHelpful(reviewId);
            }
        } finally {
            TenantContext.clear();
        }

        assertThat(helpfulCountOf(reviewId))
                .as("前端顯示的是「N 人覺得有幫助」，helpfulCount 又是搜尋排序欄位。"
                        + "若同一人能把它推到 %s，任何登入者都能直接灌高任意評價的排名", REPEAT_VOTES)
                .isEqualTo(1);

        assertThat(distinctVotersOf(reviewId))
                .as("helpful_votes 應只留下一個 key（該投票者），而非累計次數")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("10 位不同使用者同時投票 → helpfulCount 為 10，不因競態互相覆蓋")
    void concurrentVotesFromDistinctUsersAreAllCounted() throws Exception {
        UUID reviewId = seedReview();
        List<UUID> voters = new ArrayList<>(VOTERS);
        for (int i = 0; i < VOTERS; i++) {
            voters.add(seedUser(tenantId, "concurrent-" + i, User.UserRole.BUYER).getId());
        }

        Map<String, Integer> unexpected = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(VOTERS);
        CountDownLatch startGun = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>(VOTERS);
        try {
            for (UUID voterId : voters) {
                Callable<Void> attempt = () -> {
                    startGun.await();
                    try {
                        TenantContext.setCurrentUser(voterId);
                        reviewService.markHelpful(reviewId);
                    } catch (RuntimeException e) {
                        unexpected.merge(e.getClass().getSimpleName(), 1, Integer::sum);
                    } finally {
                        TenantContext.clear();
                    }
                    return null;
                };
                results.add(pool.submit(attempt));
            }
            startGun.countDown();
            for (Future<?> f : results) {
                f.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);
        }

        assertThat(unexpected).as("投票不應拋出技術性例外。本次結果：%s", unexpected).isEmpty();

        assertThat(distinctVotersOf(reviewId))
                .as("%s 位相異使用者各投一票，helpful_votes 必須留下 %s 個 key。"
                        + "少於此數即為整份 JSON map 讀後寫互相覆蓋所致的靜默漏計", VOTERS, VOTERS)
                .isEqualTo(VOTERS);

        assertThat(helpfulCountOf(reviewId))
                .as("helpfulCount 必須與相異投票人數一致")
                .isEqualTo(VOTERS);
    }
}
