package org.rail.payservice.enums;

public enum PaymentStatus {

    /**
     * 未支付：订单已创建但尚未发起支付
     */
    UNPAID,

    /**
     * 支付中：正在处理支付请求，尚未确认结果
     */
    PAYING,

    /**
     * 支付成功：支付流程已完成且成功
     */
    PAY_SUCCESS,

    /**
     * 支付失败：支付流程未完成或支付被拒绝
     */
    PAY_FAILED

}
