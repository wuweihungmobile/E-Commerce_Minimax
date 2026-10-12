package com.nextkey.ecommerce.api.dto;

import jakarta.validation.constraints.Size;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 會員自助更新個人資料（PRD §10.1.1 `/auth/profile`，DEF-358(b)，Sprint 257）。
 *
 * <p>兩欄位皆選填、{@code null} 代表不變更該欄位（PATCH 語意，比照既有
 * {@code AddressDto.UpdateRequest} 慣例）。範圍僅限姓名/電話——email 變更牽涉重新驗證
 * 流程、角色/租戶/狀態等欄位屬系統管理，皆不在此表單範圍內。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateProfileRequest {

    private static final int FULL_NAME_MAX_LENGTH = 255;
    private static final int PHONE_MAX_LENGTH = 255;

    @Size(min = 1, max = FULL_NAME_MAX_LENGTH, message = "Full name must be between 1 and 255 characters")
    private String fullName;

    @Size(max = PHONE_MAX_LENGTH, message = "Phone must not exceed 255 characters")
    private String phone;
}
