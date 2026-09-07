-- 修復：payments.idempotency_key 原欄寬 VARCHAR(36)，但多處呼叫端組出的實際字串長度超過此限制
-- （PaymentStateService.initiateStripeCheckout 的 "ORDER-CHECKOUT-"+UUID=51 字元、
-- PaymentService.processBookingPayment 的 "BOOKING-"+UUID=44 字元、
-- PaymentService.processOrderPayment 的 "ORDER-"+UUID=42 字元），
-- 導致這些付款流程一旦真的被觸發就會 100% 因寫入超長字串而失敗（非機率性、非併發相關的既有缺陷，
-- 之所以未被發現是因為 STRIPE_PAYMENT_ENABLED 預設關閉、且本地測試皆用 ddl-auto=update 建表，
-- 不受此欄寬限制）。
ALTER TABLE payments ALTER COLUMN idempotency_key TYPE VARCHAR(64);

-- 同時補上唯一索引：上述呼叫端原本假設「同一張訂單/預訂至多一筆對應付款」，但先前完全沒有資料庫層級
-- 保證，併發或重試情境下會產生重複列（詳見 Sprint 136 併發競態修復）。
DROP INDEX IF EXISTS idx_payments_idempotency;
CREATE UNIQUE INDEX idx_payments_idempotency_key_unique ON payments (idempotency_key) WHERE idempotency_key IS NOT NULL;
