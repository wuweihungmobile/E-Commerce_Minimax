package com.nextkey.ecommerce.infrastructure.email;

/** 寄信失敗（Sprint 204）。獨立型別，讓呼叫端能只攔它，而不必吞掉所有 RuntimeException。 */
public class EmailDeliveryException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public EmailDeliveryException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
