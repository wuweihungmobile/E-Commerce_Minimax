package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.core.settlement.SettlementService.SettlementStatementResponse;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.BookingRepository;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.infrastructure.persistence.UniqueConflictRetry;
import com.nextkey.ecommerce.shared.exception.BusinessException;

/**
 * 結算單生成的交易邊界（DEF-450、DEF-451，Sprint 326）。
 *
 * <p>單元層沒有 Spring 代理、沒有真實交易，所以守兩件事：
 * <ul>
 *   <li><b>結構</b>：兩個入口本身不能是 {@code @Transactional}——只要它們開了外層交易，{@link UniqueConflictRetry} 偵測到
 *       「已在交易內」就只執行一次，每個租戶各自一個交易、撞號重做都會悄悄失效，而單元測試看不出來
 *       （真實棧才看得到，見 {@code SettlementGenerationRaceRealStackIntegrationTest}）；</li>
 *   <li><b>次數</b>：交易管理員是 mock，用它被呼叫的次數證明「每個租戶一個交易、失敗只回滾失敗的那個」與
 *       「撞號輸家的交易整個回滾後在新交易重做」。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SettlementGenerator: 交易邊界（DEF-450／DEF-451）")
class SettlementGenerationTransactionBoundaryTest {

    @Mock private SettlementStatementRepository settlementRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private BookingRepository bookingRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SettlementAdjustmentRepository adjustmentRepository;
    @Mock private SettlementMapper mapper;

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private SettlementGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new SettlementGenerator(settlementRepository, tenantRepository, orderRepository,
                bookingRepository, paymentRepository, adjustmentRepository, new SettlementCalculator(), mapper,
                new UniqueConflictRetry(transactionManager));
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(any(), any(), any())).thenReturn(List.of());
        when(orderRepository.findUnsettledByTenantIdAndStatusInAndCreatedAtBefore(any(), any(), any()))
                .thenReturn(List.of());
        when(adjustmentRepository.findByTenantIdAndStatus(any(), any())).thenReturn(List.of());
        when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(inv -> {
            SettlementStatement s = inv.getArgument(0);
            s.setId(UUID.randomUUID());
            return s;
        });
    }

    private Tenant tenant() {
        Tenant tenant = Tenant.builder().id(UUID.randomUUID()).status(Tenant.TenantStatus.ACTIVE).name("t")
                .commissionRate(0.05).build();
        when(tenantRepository.findById(tenant.getId())).thenReturn(Optional.of(tenant));
        return tenant;
    }

    @Test
    @DisplayName("結構：週結算與手動產生兩個入口本身都不是 @Transactional（交易由 UniqueConflictRetry 逐次開）")
    void entryPointsOpenNoTransactionThemselves() throws NoSuchMethodException {
        assertThat(SettlementGenerator.class.getAnnotation(Transactional.class)).as("類別層級").isNull();
        assertThat(SettlementGenerator.class.getMethod("generateWeeklyStatements").getAnnotation(Transactional.class))
                .as("generateWeeklyStatements").isNull();
        assertThat(SettlementGenerator.class
                .getMethod("generateStatementManually", UUID.class, LocalDate.class, LocalDate.class)
                .getAnnotation(Transactional.class)).as("generateStatementManually").isNull();
    }

    @Test
    @DisplayName("週結算：每個租戶各自一個交易；中間一個發生資料庫失敗只回滾它自己，前後的租戶照常提交（DEF-451）")
    void weeklyJob_runsEveryTenantInItsOwnTransaction_andAFailureRollsBackOnlyItself() {
        Tenant first = tenant();
        Tenant failing = tenant();
        Tenant last = tenant();
        when(tenantRepository.findByStatus(Tenant.TenantStatus.ACTIVE)).thenReturn(List.of(first, failing, last));
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(eq(failing.getId()), any(), any()))
                .thenThrow(new DeadlockLoserDataAccessException("deadlock detected", null));

        generator.generateWeeklyStatements();

        verify(transactionManager, times(3)).getTransaction(any());
        verify(transactionManager, times(2)).commit(any());
        verify(transactionManager, times(1)).rollback(any());
        ArgumentCaptor<SettlementStatement> saved = ArgumentCaptor.forClass(SettlementStatement.class);
        verify(settlementRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(s -> s.getTenant().getId())
                .as("失敗的租戶之前與之後的租戶都產生了結算單").containsExactly(first.getId(), last.getId());
    }

    @Test
    @DisplayName("手動產生：撞唯一約束輸了 → 該次交易整個回滾，在新交易重做，看到贏家的結算單並回傳（DEF-450）")
    void manual_losingTheUniqueRace_reRunsInAFreshTransactionAndReturnsTheWinner() {
        Tenant tenant = tenant();
        LocalDate start = LocalDate.of(2030, 1, 7);
        SettlementStatement winner = SettlementStatement.builder().id(UUID.randomUUID()).tenant(tenant)
                .statementNumber("STL-winner").periodStart(start).periodEnd(start.plusDays(6)).build();
        SettlementStatementResponse winnersResponse = SettlementStatementResponse.builder()
                .id(winner.getId()).statementNumber("STL-winner").build();
        // 第一次：冪等檢查沒看到任何結算單，INSERT 撞上對手剛提交的那張；第二次（新交易）：看得到贏家的
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(eq(tenant.getId()), any(), any()))
                .thenReturn(List.of()).thenReturn(List.of(winner));
        when(settlementRepository.save(any(SettlementStatement.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate key value violates unique constraint"));
        when(mapper.toStatementResponse(winner)).thenReturn(winnersResponse);

        SettlementStatementResponse response = generator.generateStatementManually(
                tenant.getId(), start, start.plusDays(6));

        assertThat(response).isSameAs(winnersResponse);
        verify(transactionManager, times(2)).getTransaction(any());
        verify(transactionManager, times(1)).rollback(any());
        verify(transactionManager, times(1)).commit(any());
        verify(settlementRepository, times(1)).save(any(SettlementStatement.class));
    }

    @Test
    @DisplayName("手動產生：結束日早於起始日 → 直接拒絕，不開任何交易")
    void manual_invalidPeriod_isRejectedBeforeAnyTransactionStarts() {
        LocalDate start = LocalDate.of(2030, 1, 7);

        assertThatThrownBy(() -> generator.generateStatementManually(UUID.randomUUID(), start, start.minusDays(1)))
                .isInstanceOf(BusinessException.class);

        verify(transactionManager, never()).getTransaction(any());
    }
}
