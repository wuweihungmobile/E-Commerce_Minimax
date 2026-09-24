package com.nextkey.ecommerce.core.settlement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.scheduling.annotation.Scheduled;

import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.OrderRepository;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementAdjustmentRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.shared.time.BusinessTime;

/**
 * DEF-270／DEF-271：週結算的「一週」以營運時區（UTC+8）切分，且期間查詢用絕對時刻半開區間。
 *
 * <p>過去 cron 與期間都取 JVM 預設時區：正式容器（UTC）的一週是「台灣週一 08:00 ~ 下週一 07:59」，
 * 開發機（台灣）則是「台灣週一 00:00 ~ 週日 23:59」——同一段程式在兩處切出不同的結算週期；
 * 期間結束時間還用 {@code atTime(23, 59, 59)}，漏掉最後一秒。使用者於 2026-09-24 拍板改為台灣時間。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("SettlementGenerator: 結算週以營運時區（UTC+8）切分（DEF-270/271）")
class SettlementGeneratorBusinessWeekTest {

    @Mock private SettlementStatementRepository settlementRepository;
    @Mock private TenantRepository tenantRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SettlementAdjustmentRepository adjustmentRepository;
    @Mock private SettlementMapper mapper;

    private static final UUID TENANT_ID = UUID.randomUUID();

    private SettlementGenerator newGenerator() {
        return new SettlementGenerator(settlementRepository, tenantRepository, orderRepository, paymentRepository,
                adjustmentRepository, new SettlementCalculator(), mapper);
    }

    private Tenant tenant() {
        return Tenant.builder().id(TENANT_ID).status(Tenant.TenantStatus.ACTIVE).name("t")
                .commissionRate(0.05).build();
    }

    @Test
    @DisplayName("排程 cron 以營運時區觸發：@Scheduled(zone = Asia/Taipei)，仍是每週一 00:00")
    void scheduledJob_firesOnBusinessZoneMondayMidnight() throws NoSuchMethodException {
        Scheduled scheduled = SettlementGenerator.class.getMethod("generateWeeklyStatements")
                .getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("0 0 0 ? * MON");
        assertThat(scheduled.zone())
                .as("未指定 zone 時 Spring 以 JVM 預設時區解讀 cron：正式容器（UTC）會變成台灣週一 08:00 才觸發")
                .isEqualTo("Asia/Taipei");
    }

    @Test
    @DisplayName("台灣週一 00:00 觸發時，結算的是剛結束的那一週（12/28~1/3），不是再前一週")
    void weeklyJob_atTaipeiMondayMidnight_settlesTheWeekJustEnded() {
        // UTC 2027-01-03 16:00 = 台灣 2027-01-04（週一）00:00；此刻 UTC 日期仍是週日 1/3
        BusinessTime.useClockForTesting(Clock.fixed(Instant.parse("2027-01-03T16:00:00Z"), ZoneOffset.UTC));
        when(tenantRepository.findByStatus(Tenant.TenantStatus.ACTIVE)).thenReturn(List.of(tenant()));
        when(settlementRepository.findByTenantIdAndPeriodStartBetween(any(), any(), any()))
                .thenReturn(List.of(SettlementStatement.builder().build())); // 已存在 → 冪等直接回傳

        newGenerator().generateWeeklyStatements();

        ArgumentCaptor<LocalDate> start = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<LocalDate> end = ArgumentCaptor.forClass(LocalDate.class);
        verify(settlementRepository).findByTenantIdAndPeriodStartBetween(eq(TENANT_ID), start.capture(), end.capture());
        assertThat(start.getValue()).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(end.getValue()).isEqualTo(LocalDate.of(2027, 1, 3));
    }

    @Test
    @DisplayName("結算查詢的「建立時間上界」是台灣時區的次週一 00:00（不含），與 JVM 預設時區無關")
    void settlementQuery_upperBoundIsNextBusinessMonday_independentOfJvmZone() {
        TimeZone original = TimeZone.getDefault();
        try {
            for (String jvmZone : new String[] {"UTC", "Pacific/Honolulu", "Asia/Taipei"}) {
                TimeZone.setDefault(TimeZone.getTimeZone(jvmZone));
                org.mockito.Mockito.clearInvocations(orderRepository);
                when(settlementRepository.findByTenantIdAndPeriodStartBetween(any(), any(), any()))
                        .thenReturn(List.of());
                when(tenantRepository.findById(TENANT_ID)).thenReturn(Optional.of(tenant()));
                when(orderRepository.findUnsettledByTenantIdAndStatusInAndCreatedAtBefore(any(), any(), any()))
                        .thenReturn(List.of());
                when(adjustmentRepository.findByTenantIdAndStatus(any(), any())).thenReturn(List.of());
                when(settlementRepository.save(any(SettlementStatement.class))).thenAnswer(i -> i.getArgument(0));

                newGenerator().generateStatementForTenant(TENANT_ID, LocalDate.of(2027, 1, 4), LocalDate.of(2027, 1, 10));

                ArgumentCaptor<Instant> createdBefore = ArgumentCaptor.forClass(Instant.class);
                verify(orderRepository).findUnsettledByTenantIdAndStatusInAndCreatedAtBefore(
                        eq(TENANT_ID), eq(SettlementCalculator.SETTLEABLE_STATUSES), createdBefore.capture());
                // 期間 1/4~1/10（台灣）→ 上界是「次一週一 2027-01-11 00:00 台灣」= UTC 2027-01-10 16:00（不含）
                assertThat(createdBefore.getValue()).as("JVM 時區 %s", jvmZone)
                        .isEqualTo(Instant.parse("2027-01-10T16:00:00Z"));
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }
}
