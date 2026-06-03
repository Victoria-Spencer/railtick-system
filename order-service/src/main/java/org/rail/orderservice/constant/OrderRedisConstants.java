package org.rail.orderservice.constant;

/**
 * 订单业务专属Redis常量
 * 包含预订单、正式订单、订单防重、订单分页等所有订单相关缓存Key
 */
public final class OrderRedisConstants {

    private OrderRedisConstants() {}

    // ====================== 分布式锁 ======================
    /** 创建预订单锁前缀（用户+车次维度）：rail:lock:pre:order:create:userId:{userId}:trainId:{trainId} */
    public static final String RAIL_LOCK_PRE_ORDER_CREATE_PREFIX = "rail:lock:pre:order:create:";
    /** 创建正式订单锁前缀（预订单号维度）：rail:lock:order:create:preOrderSn:{preOrderSn} */
    public static final String RAIL_LOCK_ORDER_CREATE_PREFIX = "rail:lock:order:create:";
    /** 正式订单锁 分布式锁前缀 */
    public static final String RAIL_LOCK_ORDER_PREFIX = "rail:lock:order:";
    /** 预订单延迟队列 分布式锁前缀 */
    public static final String RAIL_LOCK_PRE_ORDER_DELAY_PREFIX = "rail:lock:order:pre:delay:";

    // ====================== 延迟队列配置 ======================
    /** 预订单超时延迟时间：16分钟（毫秒） */
    public static final int PRE_ORDER_DELAY_TIME_MILLIS = 15 * 60 * 1000;
//    public static final int PRE_ORDER_DELAY_TIME_MILLIS = 150000;

    // ====================== 聚合缓存 ======================
    public static final String RAIL_AGG_ORDER_USER_ALL_PREFIX = "rail:agg:order_user_all"; // 订单用户信息全量缓存
    public static final String RAIL_AGG_SELF_TICKET_USER_ALL_PREFIX = "rail:agg:self-ticket:user:all:"; // 本人车票全量缓存
    public static final String RAIL_SELF_TICKET_PREFIX = "rail:self-ticket:"; // 本人车票

    // ====================== 订单防重 ======================
    /** 创建订单防重专用 CreateOrderVO 缓存前缀 + preOrderSn */
    public static final String RAIL_ORDER_CREATE_VO_PREFIX = "rail:order:create:vo:";

    // ====================== 预订单 ======================
    public static final String RAIL_PRE_ORDER_PREFIX = "rail:pre:order:";
    public static final String RAIL_PRE_ORDER_DETAILS_PREFIX = "rail:pre:order:details:"; // + preOrderId

    // ====================== 正式订单 ======================
    public static final String RAIL_ORDER_PREFIX = "rail:order:"; // + orderSn
    public static final String RAIL_ORDER_DETAILS_PREFIX = "rail:order:details:"; // + orderId
}