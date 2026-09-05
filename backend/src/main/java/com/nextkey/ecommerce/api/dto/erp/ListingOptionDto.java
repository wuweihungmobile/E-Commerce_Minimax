package com.nextkey.ecommerce.api.dto.erp;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.*;

/**
 * 採購單品項的 listing 選擇器選項（Sprint 129，DEF-076）。
 *
 * <p>刻意不直接回傳 {@code Listing} 實體——其 {@code owner}/{@code tenant} 為 LAZY 關聯，專案
 * 未設定 Jackson Hibernate 模組亦未關閉 open-in-view，序列化時會觸發懶載入並把整個 {@code User}
 * （含 {@code passwordHash}，無 {@code @JsonIgnore}）序列化進回應。此輕量 DTO 只挑選選擇器需要的欄位。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ListingOptionDto {

    private UUID id;

    private String title;

    private BigDecimal basePrice;

    private String currency;
}
