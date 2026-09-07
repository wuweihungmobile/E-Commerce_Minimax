package com.nextkey.ecommerce.infrastructure.payment;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import com.nextkey.ecommerce.domain.model.payment.Payment;
import com.nextkey.ecommerce.domain.repository.PaymentRepository;
import com.nextkey.ecommerce.shared.exception.BusinessException;
import com.nextkey.ecommerce.shared.exception.ErrorCode;
import com.stripe.exception.CardException;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.model.Transfer;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.TransferCreateParams;
import com.stripe.param.checkout.SessionCreateParams;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Stripe 支付網關實現（Phase 3 — 真實 Stripe Java SDK）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripePaymentGateway implements PaymentGateway {

    private final PaymentRepository paymentRepository;

    @Value("${stripe.secret.key:sk_test_placeholder}")
    private String stripeApiKey;

    private static final String GATEWAY_TYPE = "STRIPE";

    @Override
    public PaymentGatewayRequestResponse.PaymentIntentResult createPaymentIntent(
            PaymentGatewayRequestResponse.PaymentIntentRequest request) {
        log.info("Creating Stripe PaymentIntent: orderId={}, amount={}, currency={}",
                request.getOrderId(), request.getAmount(), request.getCurrency());

        try {
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(request.getAmount().multiply(BigDecimal.valueOf(100)).longValue())
                    .setCurrency(request.getCurrency().toLowerCase())
                    .putMetadata("order_id", request.getOrderId().toString())
                    .build();

            String idempotencyKey = request.getIdempotencyKey() != null
                    ? request.getIdempotencyKey()
                    : request.getOrderId().toString();

            RequestOptions options = RequestOptions.builder()
                    .setApiKey(stripeApiKey)
                    .setIdempotencyKey(idempotencyKey)
                    .build();

            PaymentIntent intent = PaymentIntent.create(params, options);

            Map<String, Object> metadata = new HashMap<>(intent.getMetadata());
            if (request.getBookingId() != null) {
                metadata.put("booking_id", request.getBookingId().toString());
            }

            return PaymentGatewayRequestResponse.PaymentIntentResult.builder()
                    .transactionId(intent.getId())
                    .clientSecret(intent.getClientSecret())
                    .status(intent.getStatus())
                    .paymentIntentId(intent.getId())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .metadata(metadata)
                    .build();

        } catch (CardException e) {
            log.error("[E-6006] Stripe card declined: orderId={}, code={}, message={}",
                    request.getOrderId(), e.getCode(), e.getMessage());
            throw new BusinessException(ErrorCode.E_6006, e.getMessage());
        } catch (StripeException e) {
            log.error("[E-6007] Stripe provider error: orderId={}, code={}, message={}",
                    request.getOrderId(), e.getCode(), e.getMessage());
            throw new BusinessException(ErrorCode.E_6007, e.getMessage());
        }
    }

    @Override
    public PaymentGatewayRequestResponse.PaymentConfirmResult confirmPayment(
            PaymentGatewayRequestResponse.PaymentConfirmRequest request) {
        log.info("Confirming Stripe payment: transactionId={}, paymentIntentId={}",
                request.getTransactionId(), request.getPaymentIntentId());

        return PaymentGatewayRequestResponse.PaymentConfirmResult.builder()
                .success(true)
                .transactionId(request.getTransactionId())
                .status("succeeded")
                .build();
    }

    @Override
    public PaymentGatewayRequestResponse.RefundResult processRefund(
            PaymentGatewayRequestResponse.RefundRequest request) {
        // Sprint 52 Phase C（AI-2412）：真 Stripe Refund.create（request.transactionId = payment_intent id）。
        // amount 為 null → 全額退款（Stripe 預設）；本 Sprint 只做全額退款。
        log.info("Processing Stripe refund: paymentIntent={}, amount={}, reason={}",
                request.getTransactionId(), request.getAmount(), request.getReason());
        try {
            RefundCreateParams.Builder paramsBuilder = RefundCreateParams.builder()
                    .setPaymentIntent(request.getTransactionId());
            if (request.getAmount() != null) {
                paramsBuilder.setAmount(request.getAmount().multiply(BigDecimal.valueOf(100)).longValue());
            }
            RequestOptions options = RequestOptions.builder()
                    .setApiKey(stripeApiKey)
                    .setIdempotencyKey(request.getIdempotencyKey() != null
                            ? request.getIdempotencyKey() : "refund-" + request.getTransactionId())
                    .build();

            Refund refund = Refund.create(paramsBuilder.build(), options);

            return PaymentGatewayRequestResponse.RefundResult.builder()
                    .success(true)
                    .refundId(refund.getId())
                    .transactionId(request.getTransactionId())
                    .refundAmount(request.getAmount())
                    .status(refund.getStatus())
                    .build();
        } catch (CardException e) {
            log.error("[E-6006] Stripe refund card error: paymentIntent={}, message={}",
                    request.getTransactionId(), e.getMessage());
            throw new BusinessException(ErrorCode.E_6006, e.getMessage());
        } catch (StripeException e) {
            log.error("[E-6007] Stripe refund provider error: paymentIntent={}, message={}",
                    request.getTransactionId(), e.getMessage());
            throw new BusinessException(ErrorCode.E_6007, e.getMessage());
        }
    }

    @Override
    public PaymentGatewayRequestResponse.PaymentStatusResult getPaymentStatus(String transactionId) {
        log.info("Getting Stripe payment status: transactionId={}", transactionId);

        try {
            Payment payment = paymentRepository.findByTransactionId(transactionId)
                    .orElse(null);

            if (payment == null) {
                return PaymentGatewayRequestResponse.PaymentStatusResult.builder()
                        .transactionId(transactionId)
                        .status("not_found")
                        .errorMessage("Payment not found")
                        .build();
            }

            return PaymentGatewayRequestResponse.PaymentStatusResult.builder()
                    .transactionId(transactionId)
                    .status(payment.getStatus().name().toLowerCase())
                    .amount(payment.getAmount())
                    .currency(payment.getCurrency())
                    .build();

        } catch (DataAccessException e) {
            log.error("[E_6001] Failed to get Stripe payment status (DataAccessException): {}", e.getMessage(), e);
            return PaymentGatewayRequestResponse.PaymentStatusResult.builder()
                    .transactionId(transactionId)
                    .status("error")
                    .errorMessage("Failed to get payment status: " + e.getMessage())
                    .build();
        }
    }

    @Override
    public PaymentGatewayRequestResponse.CheckoutSessionResult createCheckoutSession(
            PaymentGatewayRequestResponse.CheckoutSessionRequest request) {
        log.info("Creating Stripe Checkout Session: orderId={}, amount={}, currency={}",
                request.getOrderId(), request.getAmount(), request.getCurrency());
        try {
            String productName = request.getProductName() != null
                    ? request.getProductName() : "Order " + request.getOrderId();
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.PAYMENT)
                    .setSuccessUrl(request.getSuccessUrl())
                    .setCancelUrl(request.getCancelUrl())
                    .putMetadata("order_id", request.getOrderId().toString())
                    // Phase C（AI-2412）：pi 也帶 order_id metadata，使 payment_intent/charge 相關
                    // 事件（payment_failed / charge.refunded）可由 order_id 可靠對應（補 Phase B best-effort 缺口）
                    .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                            .putMetadata("order_id", request.getOrderId().toString())
                            .build())
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setQuantity(1L)
                            .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                    .setCurrency(request.getCurrency().toLowerCase())
                                    .setUnitAmount(request.getAmount()
                                            .multiply(BigDecimal.valueOf(100)).longValue())
                                    .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                            .setName(productName)
                                            .build())
                                    .build())
                            .build())
                    .build();

            RequestOptions options = RequestOptions.builder()
                    .setApiKey(stripeApiKey)
                    .setIdempotencyKey(request.getIdempotencyKey() != null
                            ? request.getIdempotencyKey() : request.getOrderId().toString())
                    .build();

            Session session = Session.create(params, options);

            return PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                    .sessionId(session.getId())
                    .sessionUrl(session.getUrl())
                    .paymentIntentId(session.getPaymentIntent())
                    .status(session.getStatus())
                    .paymentStatus(session.getPaymentStatus())
                    .build();
        } catch (CardException e) {
            log.error("[E-6006] Stripe checkout card declined: orderId={}, message={}",
                    request.getOrderId(), e.getMessage());
            throw new BusinessException(ErrorCode.E_6006, e.getMessage());
        } catch (StripeException e) {
            log.error("[E-6007] Stripe checkout provider error: orderId={}, message={}",
                    request.getOrderId(), e.getMessage());
            throw new BusinessException(ErrorCode.E_6007, e.getMessage());
        }
    }

    @Override
    public PaymentGatewayRequestResponse.CheckoutSessionResult retrieveCheckoutSession(String sessionId) {
        log.info("Retrieving Stripe Checkout Session: sessionId={}", sessionId);
        try {
            RequestOptions options = RequestOptions.builder().setApiKey(stripeApiKey).build();
            Session session = Session.retrieve(sessionId, options);
            return PaymentGatewayRequestResponse.CheckoutSessionResult.builder()
                    .sessionId(session.getId())
                    .sessionUrl(session.getUrl())
                    .paymentIntentId(session.getPaymentIntent())
                    .status(session.getStatus())
                    .paymentStatus(session.getPaymentStatus())
                    .build();
        } catch (StripeException e) {
            log.error("[E-6007] Stripe retrieve session error: sessionId={}, message={}",
                    sessionId, e.getMessage());
            throw new BusinessException(ErrorCode.E_6007, e.getMessage());
        }
    }

    @Override
    public String getGatewayType() {
        return GATEWAY_TYPE;
    }

    @Override
    public PaymentGatewayRequestResponse.ConnectAccountResult createConnectAccount(String email) {
        // Sprint 53（AI-2413 Phase D-1）：建立 Stripe Connect Express 帳戶（賣家 onboarding 第一步）。
        log.info("Creating Stripe Connect Express account: email={}", email);
        try {
            AccountCreateParams.Builder paramsBuilder = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS);
            if (email != null) {
                paramsBuilder.setEmail(email);
            }
            RequestOptions options = RequestOptions.builder().setApiKey(stripeApiKey).build();
            Account account = Account.create(paramsBuilder.build(), options);

            return PaymentGatewayRequestResponse.ConnectAccountResult.builder()
                    .accountId(account.getId())
                    .chargesEnabled(account.getChargesEnabled())
                    .payoutsEnabled(account.getPayoutsEnabled())
                    .detailsSubmitted(account.getDetailsSubmitted())
                    .build();
        } catch (StripeException e) {
            log.error("[E-6008] Stripe Connect account creation error: email={}, message={}",
                    email, e.getMessage());
            throw new BusinessException(ErrorCode.E_6008, e.getMessage());
        }
    }

    @Override
    public PaymentGatewayRequestResponse.AccountLinkResult createAccountLink(
            String accountId, String refreshUrl, String returnUrl) {
        // Sprint 53（AI-2413 Phase D-1）：account link 為一次性導轉 URL，導向 Stripe 代管 KYC 表單。
        log.info("Creating Stripe Connect account link: accountId={}", accountId);
        try {
            AccountLinkCreateParams params = AccountLinkCreateParams.builder()
                    .setAccount(accountId)
                    .setRefreshUrl(refreshUrl)
                    .setReturnUrl(returnUrl)
                    .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                    .build();
            RequestOptions options = RequestOptions.builder().setApiKey(stripeApiKey).build();
            AccountLink link = AccountLink.create(params, options);

            return PaymentGatewayRequestResponse.AccountLinkResult.builder()
                    .url(link.getUrl())
                    .build();
        } catch (StripeException e) {
            log.error("[E-6008] Stripe Connect account link error: accountId={}, message={}",
                    accountId, e.getMessage());
            throw new BusinessException(ErrorCode.E_6008, e.getMessage());
        }
    }

    @Override
    public PaymentGatewayRequestResponse.ConnectAccountResult getConnectAccountStatus(String accountId) {
        log.info("Retrieving Stripe Connect account status: accountId={}", accountId);
        try {
            RequestOptions options = RequestOptions.builder().setApiKey(stripeApiKey).build();
            Account account = Account.retrieve(accountId, options);

            return PaymentGatewayRequestResponse.ConnectAccountResult.builder()
                    .accountId(account.getId())
                    .chargesEnabled(account.getChargesEnabled())
                    .payoutsEnabled(account.getPayoutsEnabled())
                    .detailsSubmitted(account.getDetailsSubmitted())
                    .build();
        } catch (StripeException e) {
            log.error("[E-6008] Stripe Connect account retrieve error: accountId={}, message={}",
                    accountId, e.getMessage());
            throw new BusinessException(ErrorCode.E_6008, e.getMessage());
        }
    }

    @Override
    public PaymentGatewayRequestResponse.TransferResult createTransfer(
            String destinationAccountId, long amountInCents, String currency, String sourceReferenceId) {
        // Sprint 80（AI-2416 Phase D-2）：Separate charges and transfers——結算單審核通過後，
        // 將平台已代收的貨款事後分步轉給賣家 Connect 帳戶，不影響既有 createPaymentIntent/createCheckoutSession。
        log.info("Creating Stripe Transfer: destination={}, amount={}, currency={}, sourceReferenceId={}",
                destinationAccountId, amountInCents, currency, sourceReferenceId);
        try {
            TransferCreateParams params = TransferCreateParams.builder()
                    .setAmount(amountInCents)
                    .setCurrency(currency)
                    .setDestination(destinationAccountId)
                    .putMetadata("settlementStatementId", sourceReferenceId)
                    .build();
            RequestOptions options = RequestOptions.builder()
                    .setApiKey(stripeApiKey)
                    .setIdempotencyKey(sourceReferenceId)
                    .build();
            Transfer transfer = Transfer.create(params, options);

            return PaymentGatewayRequestResponse.TransferResult.builder()
                    .transferId(transfer.getId())
                    .build();
        } catch (StripeException e) {
            log.error("[E-6010] Stripe Transfer error: destination={}, sourceReferenceId={}, message={}",
                    destinationAccountId, sourceReferenceId, e.getMessage());
            throw new BusinessException(ErrorCode.E_6010, e.getMessage());
        }
    }
}
