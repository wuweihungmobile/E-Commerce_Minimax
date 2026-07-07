package com.nextkey.ecommerce.core.settlement;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nextkey.ecommerce.core.feature.FeatureToggleService;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement;
import com.nextkey.ecommerce.domain.model.settlement.SettlementStatement.SettlementStatus;
import com.nextkey.ecommerce.domain.model.settlement.Transfer;
import com.nextkey.ecommerce.domain.model.settlement.Transfer.TransferStatus;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.TenantRepository;
import com.nextkey.ecommerce.domain.repository.settlement.SettlementStatementRepository;
import com.nextkey.ecommerce.domain.repository.settlement.TransferRepository;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayFactory;
import com.nextkey.ecommerce.infrastructure.payment.PaymentGatewayRequestResponse;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 結算單審核通過（{@code APPROVED}）後，實際將淨額 transfer 給賣家 Connect 帳戶
 * （Sprint 80，AI-2416 Phase D-2）。
 *
 * <p>由 {@link SettlementReviewer#approveStatement} 於狀態轉換完成後呼叫，實作該類別 Javadoc
 * 早已記載但從未實作的 {@code APPROVED → PAID} 轉換。呼叫端必須以 try-catch 隔離本服務的例外，
 * 任何 transfer 失敗絕不可回滾「審核通過」這個既定的 Admin 決策。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private static final String GATEWAY_STRIPE = "STRIPE";
    private static final String STRIPE_TRANSFER_ENABLED = "STRIPE_TRANSFER_ENABLED";
    private static final int CENTS_PER_UNIT = 100;

    private final SettlementStatementRepository settlementRepository;
    private final TransferRepository transferRepository;
    private final TenantRepository tenantRepository;
    private final PaymentGatewayFactory paymentGatewayFactory;
    private final FeatureToggleService featureToggleService;

    /**
     * 依結算單建立/推進 transfer（冪等：已有記錄直接回傳，不重複轉帳）。
     * 僅 {@code APPROVED} 狀態的結算單可觸發；非此狀態直接拒絕，避免誤觸發。
     */
    @Transactional
    public Transfer createTransferForStatement(final UUID statementId) {
        SettlementStatement statement = settlementRepository.findById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5013, "Settlement statement not found"));

        if (statement.getStatus() != SettlementStatus.APPROVED) {
            throw new BusinessException(ErrorCode.E_5014, "Only APPROVED statements can be transferred");
        }

        Transfer existing = transferRepository.findBySettlementStatementId(statementId).orElse(null);
        if (existing != null) {
            log.info("Transfer already exists for statement, skipping duplicate: statementId={}", statementId);
            return existing;
        }

        Tenant tenant = tenantRepository.findById(statement.getTenantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.E_2000, "Tenant not found"));

        Transfer transfer = Transfer.builder()
                .settlementStatementId(statementId)
                .tenantId(statement.getTenantId())
                .transferAmount(statement.getNetSettlementAmount())
                .currency(statement.getCurrency())
                .status(TransferStatus.PENDING)
                .build();

        if (!featureToggleService.isFeatureEnabledForTenant(tenant.getId(), STRIPE_TRANSFER_ENABLED)) {
            log.info("STRIPE_TRANSFER_ENABLED disabled for tenant, skipping transfer: tenantId={}, statementId={}",
                    tenant.getId(), statementId);
            transfer.setStatus(TransferStatus.SKIPPED_ONBOARDING_INCOMPLETE);
            transfer.setFailureReason("STRIPE_TRANSFER_ENABLED feature toggle disabled for tenant");
            return transferRepository.save(transfer);
        }

        if (!isConnectAccountReady(tenant)) {
            log.warn("Tenant Connect account not ready, skipping transfer: tenantId={}, statementId={}",
                    tenant.getId(), statementId);
            transfer.setStatus(TransferStatus.SKIPPED_ONBOARDING_INCOMPLETE);
            transfer.setFailureReason("Tenant Stripe Connect onboarding incomplete");
            return transferRepository.save(transfer);
        }

        try {
            long amountInCents = statement.getNetSettlementAmount()
                    .multiply(BigDecimal.valueOf(CENTS_PER_UNIT)).longValue();
            PaymentGatewayRequestResponse.TransferResult result = paymentGatewayFactory.createTransfer(
                    GATEWAY_STRIPE, tenant.getStripeConnectAccountId(), amountInCents,
                    statement.getCurrency(), statementId.toString());

            transfer.setStatus(TransferStatus.COMPLETED);
            transfer.setStripeTransferId(result.getTransferId());
            transfer = transferRepository.save(transfer);

            statement.setStatus(SettlementStatus.PAID);
            statement.setPaidAt(Instant.now());
            settlementRepository.save(statement);

            log.info("Transfer completed: statementId={}, tenantId={}, stripeTransferId={}",
                    statementId, tenant.getId(), result.getTransferId());
            return transfer;
        } catch (BusinessException e) {
            log.error("Transfer failed: statementId={}, tenantId={}, reason={}",
                    statementId, tenant.getId(), e.getMessage());
            transfer.setStatus(TransferStatus.FAILED);
            transfer.setFailureReason(e.getMessage());
            transfer = transferRepository.save(transfer);

            statement.setStatus(SettlementStatus.FAILED);
            settlementRepository.save(statement);

            return transfer;
        }
    }

    /**
     * 管理端重試失敗/前置條件未滿足的 transfer（限自己租戶的 ADMIN，或 SUPER_ADMIN 跨租戶）。
     */
    @Transactional
    public Transfer retryFailedTransfer(final UUID statementId, final boolean isSuperAdmin) {
        SettlementStatement statement = settlementRepository.findById(statementId)
                .orElseThrow(() -> new BusinessException(ErrorCode.E_5013, "Settlement statement not found"));

        checkTenantAccess(statement.getTenantId(), isSuperAdmin);

        Transfer existing = transferRepository.findBySettlementStatementId(statementId).orElse(null);
        if (existing == null || (existing.getStatus() != TransferStatus.FAILED
                && existing.getStatus() != TransferStatus.SKIPPED_ONBOARDING_INCOMPLETE)) {
            throw new BusinessException(ErrorCode.E_5014, "Only FAILED or SKIPPED transfers can be retried");
        }

        // 重試等同重新走一次結算單觸發流程：先清掉舊記錄，讓 createTransferForStatement 重新判斷前置條件
        transferRepository.delete(existing);
        statement.setStatus(SettlementStatus.APPROVED);
        settlementRepository.save(statement);

        return createTransferForStatement(statementId);
    }

    /**
     * 查詢呼叫者所在租戶的 transfer 記錄（對帳用）。SUPER_ADMIN 可另呼叫 {@link #getTransfersForTenant}。
     */
    @Transactional(readOnly = true)
    public Page<Transfer> getTransfersForCurrentTenant(final Pageable pageable) {
        UUID tenantId = TenantContext.getCurrentTenant();
        return transferRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    /**
     * SUPER_ADMIN 查詢指定租戶的 transfer 記錄。
     */
    @Transactional(readOnly = true)
    public Page<Transfer> getTransfersForTenant(final UUID tenantId, final Pageable pageable) {
        return transferRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    private void checkTenantAccess(final UUID resourceTenantId, final boolean isSuperAdmin) {
        if (isSuperAdmin) {
            return;
        }
        UUID callerTenantId = TenantContext.getCurrentTenant();
        if (!resourceTenantId.equals(callerTenantId)) {
            log.warn("Cross-tenant transfer access denied: callerTenant={}, resourceTenant={}",
                    callerTenantId, resourceTenantId);
            throw new BusinessException(ErrorCode.E_1007, "No permission to access this tenant's transfer records");
        }
    }

    private boolean isConnectAccountReady(final Tenant tenant) {
        return tenant.getConnectOnboardingStatus() == Tenant.ConnectOnboardingStatus.COMPLETE
                && Boolean.TRUE.equals(tenant.getConnectChargesEnabled())
                && Boolean.TRUE.equals(tenant.getConnectPayoutsEnabled());
    }

    /** 將 {@link Transfer} entity 轉為 API 回應 DTO。 */
    public static TransferResponse toResponse(final Transfer transfer) {
        return TransferResponse.builder()
                .id(transfer.getId())
                .settlementStatementId(transfer.getSettlementStatementId())
                .tenantId(transfer.getTenantId())
                .stripeTransferId(transfer.getStripeTransferId())
                .transferAmount(transfer.getTransferAmount())
                .currency(transfer.getCurrency())
                .status(transfer.getStatus().name())
                .failureReason(transfer.getFailureReason())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .build();
    }

    public static TransferListResponse toListResponse(final Page<Transfer> page) {
        List<TransferResponse> transfers = page.getContent().stream()
                .map(TransferService::toResponse)
                .collect(Collectors.toList());
        return TransferListResponse.builder()
                .transfers(transfers)
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .build();
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransferResponse {
        private UUID id;
        private UUID settlementStatementId;
        private UUID tenantId;
        private String stripeTransferId;
        private BigDecimal transferAmount;
        private String currency;
        private String status;
        private String failureReason;
        private Instant createdAt;
        private Instant updatedAt;
    }

    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class TransferListResponse {
        private List<TransferResponse> transfers;
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
    }
}
