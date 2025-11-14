package org.rail.orderservice.controller;

import org.rail.commonservice.result.Result;
import org.rail.orderservice.orderservice.OrderService;
import org.rail.orderservice.pojo.dto.CreateOrderDTO;
import org.rail.orderservice.pojo.dto.CreatePreOrderDTO;
import org.rail.orderservice.pojo.vo.CreateOrderDetailsVO;
import org.rail.orderservice.pojo.vo.CreateOrderVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/order-service")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/pre-order/ticket/create")
    public Result<String> createPreOrder(@RequestBody CreatePreOrderDTO createPreOrderDTO) {
        // 返回预订单号
        String preOrderSn = orderService.createPreOrder(createPreOrderDTO);
        return Result.success(preOrderSn);
    }

    @PostMapping("/order/ticket/create")
    public Result<CreateOrderVO> createOrder(@RequestBody CreateOrderDTO createOrderDTO) {
        CreateOrderVO createOrderVO = orderService.createOrder(createOrderDTO);
        return Result.success(createOrderVO);
    }
}
