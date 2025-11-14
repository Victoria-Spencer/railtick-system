package org.rail.orderservice.orderservice;

import org.rail.orderservice.pojo.dto.CreateOrderDTO;
import org.rail.orderservice.pojo.dto.CreatePreOrderDTO;
import org.rail.orderservice.pojo.vo.CreateOrderVO;

public interface OrderService {

    /**
     * 创建预订单，临时锁定座位
     * @param createPreOrderDTO
     * @return
     */
    String createPreOrder(CreatePreOrderDTO createPreOrderDTO);

    /**
     * 创建订单，并返回订单数据
     * @param createOrderDTO
     * @return
     */
    CreateOrderVO createOrder(CreateOrderDTO createOrderDTO);
}
