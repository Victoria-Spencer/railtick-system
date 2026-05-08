package org.rail.orderservice.controller;

import jakarta.validation.constraints.NotBlank;
import org.rail.common.core.annotation.CommonRepeatSubmit;
import org.rail.common.core.annotation.OperationLog;
import org.rail.common.core.result.PageResult;
import org.rail.common.core.result.Result;
import org.rail.orderservice.orderservice.OrderService;
import org.rail.orderservice.pojo.dto.CreateOrderDTO;
import org.rail.orderservice.pojo.dto.CreatePreOrderDTO;
import org.rail.orderservice.pojo.dto.FrontSelfTicketPageDTO;
import org.rail.orderservice.pojo.dto.OrderPageQueryDTO;
import org.rail.orderservice.pojo.vo.CreateOrderVO;
import org.rail.orderservice.pojo.vo.OrderPageQueryVO;
import org.rail.orderservice.pojo.vo.SelfTicketPageVO;
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
    public Result<String> createPreOrder(@RequestBody  @Validated CreatePreOrderDTO createPreOrderDTO) {
        String preOrderSn = orderService.createPreOrder(createPreOrderDTO);
        return Result.success(preOrderSn);

    }

    @CommonRepeatSubmit(message = "请勿重复创建订单")
    @OperationLog(value = "创建订单", saveParam = true)
    @PostMapping("/order/create")
    public Result<CreateOrderVO> createOrder(@RequestBody  @Validated CreateOrderDTO createOrderDTO) {
        CreateOrderVO createOrderVO = orderService.createOrder(createOrderDTO);
        return Result.success(createOrderVO);
    }

    @PostMapping("/order/page")
    public Result<PageResult<OrderPageQueryVO>> orderPageQuery(@RequestBody  @Validated OrderPageQueryDTO orderPageQueryDTO) {
        PageResult<OrderPageQueryVO> pageResult = orderService.orderPageQuery(orderPageQueryDTO);
        return Result.success(pageResult);
    }

    @PostMapping("/order/ticket/self/page")
    public Result<PageResult<SelfTicketPageVO>> selfTicketPageQuery(@RequestBody  @Validated FrontSelfTicketPageDTO frontSelfTicketPageDTO) {
        PageResult<SelfTicketPageVO> pageResult = orderService.selfTicketPageQuery(frontSelfTicketPageDTO);
        return Result.success(pageResult);
    }

    @OperationLog(value = "取消订单", saveParam = true)
    @DeleteMapping("/order/cancel")
    public Result<Void> cancel(@RequestParam @NotBlank(message = "订单编号不能为空") String orderSn) {
        orderService.cancelOrder(orderSn);
        return Result.success();
    }
}
