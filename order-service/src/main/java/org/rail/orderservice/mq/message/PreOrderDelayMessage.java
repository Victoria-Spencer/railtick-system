package org.rail.orderservice.mq.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 预订单超时释放座位 消息体
 * 唯一字段：用户ID + 车次ID + 预订单号
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PreOrderDelayMessage implements Serializable {
    private Long userId;
    private Long trainId;
    private String preOrderSn;
}