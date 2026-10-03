package com.nextkey.ecommerce.api.dto.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderPaymentStateDto {

    private UUID orderId;
    private String orderStatus;
    private UUID paymentId;
    private String paymentStatus;
    private String transactionId;
    private String nextValidStates;
    private Boolean canPay;
    private Boolean canCancel;
    private Boolean canRefund;
    private Instant paidAt;
    private Instant updatedAt;
    // 真實金流（Sprint 50 AI-2410）：付款提供者（mock / stripe），前端據此決定付款 UI（重導 or mock 按鈕）
    private String paymentProvider;
    // Sprint 242：這筆訂單／訂房所屬的店鋪目前是否營業中（只有 ACTIVE 算營業）。停權／終止的店鋪不能再付款（E-2010），
    // 前端據此顯示「暫停營業、可取消訂單」並收起付款按鈕；舊版沒有此欄位的回應視為營業中
    private Boolean storeOpen;
    // 部分退款（Sprint 56 AI-2415）：累計已退款金額，供呼叫端判斷剩餘可退額度
    private BigDecimal refundedAmount;
}