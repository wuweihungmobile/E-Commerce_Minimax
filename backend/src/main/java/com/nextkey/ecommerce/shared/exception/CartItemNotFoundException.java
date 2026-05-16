package com.nextkey.ecommerce.shared.exception;

public class CartItemNotFoundException extends BusinessException {

    private static final long serialVersionUID = 1L;

    public CartItemNotFoundException(final String message) {
        super(ErrorCode.E_5005, message);
    }
}