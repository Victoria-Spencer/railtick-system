package org.rail.orderservice.constant;

/**
 * 订单状态
 */
public class OrderStatus {

    /**
     * 待支付：订单已创建但尚未完成支付
     */
    public static final Integer PENDING_PAYMENT = 0;

    /**
     * 已支付：订单已成功完成支付
     */
    public static Integer PAID = 1;

    /**
     * 已取消：订单被取消，未完成支付
     */
    public static Integer CANCELED = 2;
}
