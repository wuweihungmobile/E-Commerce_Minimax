package com.nextkey.ecommerce.api.dto;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * TenantInviteResponse - 待確認的店鋪成員邀請（PRD §8.2.3/§9.11，Sprint 98）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantInviteResponse {

    private String memberId;
    private String tenantId;
    private String tenantName;
    private String role;
    private Instant invitedAt;
}
