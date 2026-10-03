package com.nextkey.ecommerce.core.payment;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.nextkey.ecommerce.core.tenant.StoreCheckoutGuard;
import com.nextkey.ecommerce.domain.model.tenant.Tenant;
import com.nextkey.ecommerce.domain.repository.TenantRepository;

import lombok.RequiredArgsConstructor;

/**
 * 「發起付款」時的店鋪營業狀態守門（Sprint 242，使用者拍板：停權前已成立、尚未付款的訂單／訂房，停權後也不能付款）。
 *
 * <p>Sprint 239 的 {@link StoreCheckoutGuard} 只擋「建立」訂單與訂房；店鋪停權前就已成立、尚未付款的單，消費者仍可把款項付給
 * 停權的店鋪（在 24 小時未付款逾時清掉之前）。本類別把同一個判斷（只有 ACTIVE 的店鋪算營業中）接到所有「會收錢」的付款入口：
 * Mock 付款、Stripe Checkout 發起、舊版 {@code POST /v2/payments}，訂單與訂房各一。
 *
 * <p><b>刻意不擋的入口</b>：Stripe 回跳確認與 webhook（錢已經在 Stripe 那端收了，必須入帳，擋掉等於收了錢卻不記帳）、
 * 取消、退款（停權店鋪的消費者仍要能取消與退款）、Mock 付款失敗（不收錢）。停權前就已建立的 Stripe Checkout Session
 * 若在停權後才付完，仍會入帳為已付款（Stripe 端已收款）——取消與退款路徑照常可用。
 *
 * <p>租戶取自訂單／訂房本身的 {@code tenant_id}（Sprint 236～237 起是商品／房源所屬的店鋪）。租戶不存在視為不營業
 * （與 {@link StoreCheckoutGuard#isOpen} 對 {@code null} 的處理一致）。
 */
@Component
@RequiredArgsConstructor
public class PaymentStoreGuard {

    private final TenantRepository tenantRepository;

    /** 店鋪不營業時丟 {@code E-2010}；呼叫端要放在所有會寫入或呼叫金流的動作之前。 */
    public void requireStoreOpen(final UUID tenantId) {
        StoreCheckoutGuard.requireOpen(find(tenantId));
    }

    /** 供付款狀態回應的 {@code storeOpen} 欄位使用（前端據此顯示「暫停營業」並收起付款按鈕）。 */
    public boolean isStoreOpen(final UUID tenantId) {
        return StoreCheckoutGuard.isOpen(find(tenantId));
    }

    private Tenant find(final UUID tenantId) {
        return tenantId == null ? null : tenantRepository.findById(tenantId).orElse(null);
    }
}
