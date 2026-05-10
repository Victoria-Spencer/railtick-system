package org.rail.orderservice.controller;

import jakarta.validation.constraints.NotBlank;
import org.rail.common.core.annotation.CommonRepeatSubmit;
import org.rail.common.core.annotation.OperationLog;
import org.rail.common.core.model.result.PageResult;
import org.rail.orderservice.orderservice.OrderService;
import org.rail.orderservice.model.dto.CreateOrderDTO;
import org.rail.orderservice.model.dto.CreatePreOrderDTO;
import org.rail.orderservice.model.dto.FrontSelfTicketPageDTO;
import org.rail.orderservice.model.dto.OrderPageQueryDTO;
import org.rail.orderservice.model.vo.CreateOrderVO;
import org.rail.orderservice.model.vo.OrderPageQueryVO;
import org.rail.orderservice.model.vo.SelfTicketPageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/order-service")
@Validated
public class OrderController {

    @Autowired
    private OrderService orderService;

    @OperationLog(value = "创建预订单", saveParam = true)
    @PostMapping("/pre-order/create")
    public String createPreOrder(@RequestBody  @Validated CreatePreOrderDTO createPreOrderDTO) {
        return orderService.createPreOrder(createPreOrderDTO);
    }

    @CommonRepeatSubmit(message = "请勿重复创建订单")
    @OperationLog(value = "创建订单", saveParam = true)
    @PostMapping("/order/create")
    public CreateOrderVO createOrder(@RequestBody  @Validated CreateOrderDTO createOrderDTO) {
        return orderService.createOrder(createOrderDTO);
    }

    @PostMapping("/order/page")
    public PageResult<OrderPageQueryVO> orderPageQuery(@RequestBody  @Validated OrderPageQueryDTO orderPageQueryDTO) {
        return orderService.orderPageQuery(orderPageQueryDTO);
    }

    @PostMapping("/order/ticket/self/page")
    public PageResult<SelfTicketPageVO> selfTicketPageQuery(@RequestBody  @Validated FrontSelfTicketPageDTO frontSelfTicketPageDTO) {
        return orderService.selfTicketPageQuery(frontSelfTicketPageDTO);
    }

    @OperationLog(value = "取消订单", saveParam = true)
    @DeleteMapping("/order/cancel")
    public void cancel(@RequestParam @NotBlank(message = "订单编号不能为空") String orderSn) {
        orderService.cancelOrder(orderSn);
    }
}
