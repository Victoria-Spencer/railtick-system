package org.rail.orderservice.orderservice;

import org.rail.orderservice.pojo.dto.CreatePreOrderDTO;

public interface OrderService {

    /**
     * 创建预订单，临时锁定座位
     * @param createPreOrderDTO
     * @return
     */
    String createPreOrder(CreatePreOrderDTO createPreOrderDTO);
}
