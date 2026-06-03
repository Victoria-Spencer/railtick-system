package org.rail.orderservice.constant;

/**
 * 订单支付状态
 * 0-待支付 1-已支付 2-已取消 3-部分退票 4-全部退票
 */
public final class OrderPaymentStatusConstants {

    private OrderPaymentStatusConstants() {}

    /**
     * 待支付：订单已创建但尚未完成支付
     */
    public static final Integer PENDING_PAYMENT = 0;

    /**
     * 已支付：订单已成功完成支付
     */
    public static final Integer PAID = 1;

    /**
     * 已取消：订单主动取消/支付超时自动取消
     */
    public static final Integer CANCELED = 2;

    /**
     * 部分退票：订单中部分车票完成退票
     */
    public static final Integer PARTIAL_REFUND = 3;

    /**
     * 全部退票：订单中所有车票均完成退票
     */
    public static final Integer FULL_REFUND = 4;
}
