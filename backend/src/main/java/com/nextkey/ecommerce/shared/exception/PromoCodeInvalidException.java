package com.nextkey.ecommerce.shared.exception;

public class PromoCodeInvalidException extends BusinessException {

    private static final long serialVersionUID = 1L;

    private final String promoCode;
    private final String reason;

    public PromoCodeInvalidException(final String promoCode, final String reason) {
        super(ErrorCode.E_5007, "Promo code " + promoCode + " is invalid: " + reason);
        this.promoCode = promoCode;
        this.reason = reason;
    }

    public String getPromoCode() {
        return promoCode;
    }

    public String getReason() {
        return reason;
    }
}