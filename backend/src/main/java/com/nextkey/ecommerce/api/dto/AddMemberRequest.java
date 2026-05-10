package com.nextkey.ecommerce.api.dto;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AddMemberRequest - M17 新增成員請求 DTO
 * Phase 1: 直接新增成員（無 email 邀請機制）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddMemberRequest {

    private UUID userId;        // 要新增的用戶 ID (Phase 1: 直接輸入 userId)
    private String role;        // STORE_STAFF, STORE_MANAGER, SELLER, HOST
}