package com.nextkey.ecommerce.api.dto.erp;

import lombok.*;

/**
 * 更新採購單請求（僅 DRAFT 狀態可更新）
 * PRD §9.15
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderUpdateRequest {

    private String notes;

    // 注意：items 不允許在 update 時修改（若需修改，應取消後重新建立）
}