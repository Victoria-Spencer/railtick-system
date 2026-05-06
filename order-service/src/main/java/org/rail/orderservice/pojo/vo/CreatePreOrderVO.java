package org.rail.orderservice.pojo.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 创建预订单 返回结果
 * 包含：预订单号 + 订单防重令牌
 */
@Data
@Builder
public class CreatePreOrderVO {

    private String preOrderSn;
    private String submitToken;
}