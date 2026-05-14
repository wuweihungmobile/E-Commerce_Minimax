package com.nextkey.ecommerce.shared.exception;

public class CartEmptyException extends BusinessException {

    public CartEmptyException(final String message) {
        super(ErrorCode.E_5004, message);
    }
}