package com.nextkey.ecommerce.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * TenantMemberResponse - M17 成員管理回應 DTO
 * 用於成員列表、新增成員、更新角色等 API 回應
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantMemberResponse {

    private String userId;
    private String displayName;
    private String email;
    private String avatarUrl;
    private String role;         // STORE_OWNER, STORE_STAFF, STORE_MANAGER, etc.
    private Instant joinedAt;
}