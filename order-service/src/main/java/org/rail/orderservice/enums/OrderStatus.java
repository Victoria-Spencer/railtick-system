package org.rail.orderservice.enums;

/**
 * 订单状态
 */
public enum OrderStatus {

    /**
     * 待支付：订单已创建但尚未完成支付
     */
    PENDING_PAYMENT,

    /**
     * 已支付：订单已成功完成支付
     */
    PAID,

    /**
     * 已取消：订单被取消，未完成支付
     */
    CANCELED
}
