package com.nextkey.ecommerce.shared.exception;

public class CartEmptyException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public CartEmptyException(final String message) {
        super(ErrorCode.E_5004, message);
    }
}