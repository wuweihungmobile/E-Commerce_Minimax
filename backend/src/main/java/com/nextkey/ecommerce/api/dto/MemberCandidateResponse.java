package com.nextkey.ecommerce.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * MemberCandidateResponse - 以 email 查詢可邀請使用者的回應 DTO（DEF-321 (a)，Sprint 248）
 * 僅用於邀請表單輸入 email 後，解析成 {@link AddMemberRequest#getUserId()} 所需的 userId。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberCandidateResponse {

    private String userId;
    private String displayName;
    private String email;
    private String avatarUrl;
}
