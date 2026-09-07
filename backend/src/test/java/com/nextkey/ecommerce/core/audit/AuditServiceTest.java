package com.nextkey.ecommerce.core.audit;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nextkey.ecommerce.domain.model.audit.AuditLog;
import com.nextkey.ecommerce.domain.repository.audit.AuditLogRepository;
import com.nextkey.ecommerce.shared.tenant.TenantContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditService 單元測試")
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditService auditService;

    private static final UUID ENTITY_ID = UUID.randomUUID();
    private static final UUID TENANT_ID = UUID.randomUUID();
    private static final UUID CURRENT_USER_ID = UUID.randomUUID();
    private static final UUID EXPLICIT_ACTOR_ID = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("record()：無明確 actorUserId 時，落地為目前 TenantContext 的使用者")
    void record_withoutExplicitActor_usesTenantContextUser() {
        TenantContext.setCurrentUser(CURRENT_USER_ID);

        auditService.record("SETTLEMENT_APPROVED", "SETTLEMENT_STATEMENT", ENTITY_ID, TENANT_ID,
                "PENDING_REVIEW", "APPROVED", "reason");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        AuditLog saved = captor.getValue();
        assertThat(saved.getAction()).isEqualTo("SETTLEMENT_APPROVED");
        assertThat(saved.getEntityType()).isEqualTo("SETTLEMENT_STATEMENT");
        assertThat(saved.getEntityId()).isEqualTo(ENTITY_ID);
        assertThat(saved.getTenantId()).isEqualTo(TENANT_ID);
        assertThat(saved.getUserId()).isEqualTo(CURRENT_USER_ID);
        assertThat(saved.getOldValue()).isEqualTo("PENDING_REVIEW");
        assertThat(saved.getNewValue()).isEqualTo("APPROVED");
        assertThat(saved.getReason()).isEqualTo("reason");
    }

    @Test
    @DisplayName("record()：帶明確 actorUserId 時（如 webhook 場景），優先使用該值而非 TenantContext")
    void record_withExplicitActor_overridesTenantContextUser() {
        TenantContext.setCurrentUser(CURRENT_USER_ID);

        auditService.record("CONNECT_STATUS_SYNCED", "TENANT", ENTITY_ID, TENANT_ID,
                "pending", "enabled", null, EXPLICIT_ACTOR_ID);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(EXPLICIT_ACTOR_ID);
    }

    @Test
    @DisplayName("record()：無 TenantContext 使用者也無明確 actor 時（webhook 情境），userId 為 null 不拋例外")
    void record_noActorAndNoTenantContext_persistsNullUser() {
        auditService.record("CONNECT_STATUS_SYNCED", "TENANT", ENTITY_ID, TENANT_ID,
                "pending", "enabled", null);

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isNull();
    }

    @Test
    @DisplayName("record()：底層儲存拋出例外時吞下，不中斷呼叫端流程")
    void record_repositoryThrows_doesNotPropagate() {
        when(auditLogRepository.save(any(AuditLog.class))).thenThrow(new RuntimeException("db down"));

        assertThatCode(() -> auditService.record("X", "Y", ENTITY_ID, TENANT_ID, null, null, null))
                .doesNotThrowAnyException();
    }
}
