package org.rail.orderservice.controller;

import org.rail.commonservice.result.PageResult;
import org.rail.commonservice.result.Result;
import org.rail.orderservice.orderservice.OrderService;
import org.rail.orderservice.pojo.dto.CreateOrderDTO;
import org.rail.orderservice.pojo.dto.CreatePreOrderDTO;
import org.rail.orderservice.pojo.dto.FrontSelfTicketPageDTO;
import org.rail.orderservice.pojo.dto.OrderPageQueryDTO;
import org.rail.orderservice.pojo.vo.CreateOrderVO;
import org.rail.orderservice.pojo.vo.OrderPageQueryVO;
import org.rail.orderservice.pojo.vo.SelfTicketPageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/order-service")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping("/pre-order/create")
    public Result<String> createPreOrder(@RequestBody CreatePreOrderDTO createPreOrderDTO) {
        // 返回预订单号
        String preOrderSn = orderService.createPreOrder(createPreOrderDTO);
        return Result.success(preOrderSn);
    }

    @PostMapping("/order/create")
    public Result<CreateOrderVO> createOrder(@RequestBody CreateOrderDTO createOrderDTO) {
        CreateOrderVO createOrderVO = orderService.createOrder(createOrderDTO);
        return Result.success(createOrderVO);
    }

    @GetMapping("/order/page")
    public Result<PageResult<OrderPageQueryVO>> orderPageQuery(@RequestBody OrderPageQueryDTO orderPageQueryDTO) {
        PageResult<OrderPageQueryVO> pageResult = orderService.orderPageQuery(orderPageQueryDTO);
        return Result.success(pageResult);
    }

    @GetMapping("/order/ticket/self/page")
    public Result<PageResult<SelfTicketPageVO>> selfTicketPageQuery(@RequestBody FrontSelfTicketPageDTO frontSelfTicketPageDTO) {
        PageResult<SelfTicketPageVO> pageResult = orderService.selfTicketPageQuery(frontSelfTicketPageDTO);
        return Result.success(pageResult);
    }

    @DeleteMapping("/order/cancel")
    public Result cancel(@RequestParam String orderSn) {
        orderService.cancelOrder(orderSn);
        return Result.success();
    }
}
