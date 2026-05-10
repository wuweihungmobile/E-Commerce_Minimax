package com.nextkey.ecommerce.shared.exception;

public class CartItemNotFoundException extends BusinessException {

    public CartItemNotFoundException(final String message) {
        super(ErrorCode.E_5005, message);
    }
}