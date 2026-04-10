package com.nextkey.ecommerce.core.order;

/**
 * 訂單狀態機
 * 定義訂單狀態轉換規則
 */
public class OrderStateMachine {

    /**
     * 訂單狀態轉換結果
     */
    public static class TransitionResult {
        private final boolean allowed;
        private final String fromStatus;
        private final String toStatus;
        private final String reason;

        public TransitionResult(boolean allowed, String fromStatus, String toStatus, String reason) {
            this.allowed = allowed;
            this.fromStatus = fromStatus;
            this.toStatus = toStatus;
            this.reason = reason;
        }

        public static TransitionResult allowed(String fromStatus, String toStatus) {
            return new TransitionResult(true, fromStatus, toStatus, null);
        }

        public static TransitionResult denied(String fromStatus, String toStatus, String reason) {
            return new TransitionResult(false, fromStatus, toStatus, reason);
        }

        public boolean isAllowed() { return allowed; }
        public String getFromStatus() { return fromStatus; }
        public String getToStatus() { return toStatus; }
        public String getReason() { return reason; }
    }

    /**
     * 檢查是否可以從當前狀態轉換到目標狀態
     */
    public static TransitionResult canTransition(String currentStatus, String targetStatus) {
        if (currentStatus == null || targetStatus == null) {
            return TransitionResult.denied(currentStatus, targetStatus, "Status cannot be null");
        }

        return switch (currentStatus) {
            // CREATED 可以轉到 PAID, CANCELLED
            case "CREATED" -> switch (targetStatus) {
                case "PAID", "CANCELLED" -> TransitionResult.allowed(currentStatus, targetStatus);
                default -> TransitionResult.denied(currentStatus, targetStatus,
                        "Cannot transition from CREATED to " + targetStatus);
            };

            // PAID 可以轉到 CONFIRMED, REFUNDING
            case "PAID" -> switch (targetStatus) {
                case "CONFIRMED", "REFUNDING" -> TransitionResult.allowed(currentStatus, targetStatus);
                default -> TransitionResult.denied(currentStatus, targetStatus,
                        "Cannot transition from PAID to " + targetStatus);
            };

            // CONFIRMED 可以轉到 SHIPPING (電商) 或 CHECKED_IN (民宿)
            case "CONFIRMED" -> switch (targetStatus) {
                case "SHIPPING", "CANCELLED" -> TransitionResult.allowed(currentStatus, targetStatus);
                default -> TransitionResult.denied(currentStatus, targetStatus,
                        "Cannot transition from CONFIRMED to " + targetStatus);
            };

            // SHIPPING 可以轉到 DELIVERED
            case "SHIPPING" -> switch (targetStatus) {
                case "DELIVERED", "CANCELLED" -> TransitionResult.allowed(currentStatus, targetStatus);
                default -> TransitionResult.denied(currentStatus, targetStatus,
                        "Cannot transition from SHIPPING to " + targetStatus);
            };

            // DELIVERED 可以轉到 COMPLETED
            case "DELIVERED" -> switch (targetStatus) {
                case "COMPLETED" -> TransitionResult.allowed(currentStatus, targetStatus);
                default -> TransitionResult.denied(currentStatus, targetStatus,
                        "Cannot transition from DELIVERED to " + targetStatus);
            };

            // COMPLETED 是終態，不可轉換
            case "COMPLETED" -> TransitionResult.denied(currentStatus, targetStatus,
                    "COMPLETED is a terminal state");

            // CANCELLED 可以轉到 REFUNDING (退款流程)
            case "CANCELLED" -> switch (targetStatus) {
                case "REFUNDING" -> TransitionResult.allowed(currentStatus, targetStatus);
                default -> TransitionResult.denied(currentStatus, targetStatus,
                        "Cannot transition from CANCELLED to " + targetStatus);
            };

            // REFUNDING 可以轉到 REFUNDED
            case "REFUNDING" -> switch (targetStatus) {
                case "REFUNDED" -> TransitionResult.allowed(currentStatus, targetStatus);
                default -> TransitionResult.denied(currentStatus, targetStatus,
                        "Cannot transition from REFUNDING to " + targetStatus);
            };

            // REFUNDED 是終態，不可轉換
            case "REFUNDED" -> TransitionResult.denied(currentStatus, targetStatus,
                    "REFUNDED is a terminal state");

            default -> TransitionResult.denied(currentStatus, targetStatus,
                    "Unknown current status: " + currentStatus);
        };
    }

    /**
     * 檢查是否允許支付
     */
    public static boolean canPay(String currentStatus) {
        return "CREATED".equals(currentStatus);
    }

    /**
     * 檢查是否允許取消
     */
    public static boolean canCancel(String currentStatus) {
        return switch (currentStatus) {
            case "CREATED", "PAID", "CONFIRMED" -> true;
            default -> false;
        };
    }

    /**
     * 檢查是否允許退款
     */
    public static boolean canRefund(String currentStatus) {
        return switch (currentStatus) {
            case "PAID", "CANCELLED" -> true;
            default -> false;
        };
    }

    /**
     * 檢查是否為終態
     */
    public static boolean isTerminalState(String status) {
        return "COMPLETED".equals(status) || "REFUNDED".equals(status);
    }

    /**
     * 取得所有可能的目標狀態
     */
    public static java.util.List<String> getNextValidStates(String currentStatus) {
        return switch (currentStatus) {
            case "CREATED" -> java.util.List.of("PAID", "CANCELLED");
            case "PAID" -> java.util.List.of("CONFIRMED", "REFUNDING");
            case "CONFIRMED" -> java.util.List.of("SHIPPING", "CANCELLED");
            case "SHIPPING" -> java.util.List.of("DELIVERED", "CANCELLED");
            case "DELIVERED" -> java.util.List.of("COMPLETED");
            case "CANCELLED" -> java.util.List.of("REFUNDING");
            default -> java.util.List.of();
        };
    }
}