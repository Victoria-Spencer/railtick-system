package org.rail.orderservice.model.bo;

import lombok.Data;
import org.rail.orderservice.model.dto.CreateOrderDTO;
import org.rail.orderservice.model.entity.PreOrder;

import java.time.LocalDateTime;

@Data
public class OrderContext {
    private CreateOrderDTO reqDTO;
    private PreOrder preOrder;
    // 统一过期时间
    private LocalDateTime expireTime;
}