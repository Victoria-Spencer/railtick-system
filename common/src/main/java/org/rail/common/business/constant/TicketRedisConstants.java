package org.rail.common.business.constant;

/**
 * 票务业务专属Redis常量
 * 包含乘车人、本人车票、车票分页等所有票务相关缓存Key
 */
public final class TicketRedisConstants {

    private TicketRedisConstants() {}

    public static final String RAIL_PASSENGER_LIST_USER_PREFIX = "rail:passenger:list:user:"; // 乘车人列表
    public static final String RAIL_SELF_TICKET_PREFIX = "rail:self-ticket:"; // 本人车票
    public static final String RAIL_TICKET_SELF_PAGE_PREFIX = "rail:ticket:self:page:"; // 本人车票分页
}