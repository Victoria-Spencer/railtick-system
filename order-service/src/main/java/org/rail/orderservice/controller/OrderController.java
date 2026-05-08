package org.rail.orderservice.controller;

import jakarta.validation.constraints.NotBlank;
import org.rail.common.core.annotation.CommonRepeatSubmit;
import org.rail.common.core.annotation.OperationLog;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.core.result.PageResult;
import org.rail.common.core.result.Result;
import org.rail.common.core.util.LogUtils;
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

import static org.rail.common.core.util.LogUtils.FAIL;
import static org.rail.common.core.util.LogUtils.SUCCESS;

@RestController
@RequestMapping("/api/order-service")
@Validated
public class OrderController {

    @Autowired
    private OrderService orderService;

    @OperationLog(value = "创建预订单", saveParam = true)
    @PostMapping("/pre-order/create")
    public Result<String> createPreOrder(@RequestBody  @Validated CreatePreOrderDTO createPreOrderDTO) {
        RequestContext context = RequestContextHolder.getRequestContext();

        try {
            String preOrderSn = orderService.createPreOrder(createPreOrderDTO);
            Result<String> result = Result.success(preOrderSn);

            LogUtils.monitor(context, "TicketController", "createPreOrder",context.getStartTime(),
                    SUCCESS, createPreOrderDTO, result);

            return result;
        } catch (Exception e) {
            LogUtils.monitor(context, "TicketController", "createPreOrder",context.getStartTime(),
                    FAIL, createPreOrderDTO, e.getMessage(), e);
            throw e;
        }
    }

    @CommonRepeatSubmit(message = "请勿重复创建订单")
    @OperationLog(value = "创建订单", saveParam = true)
    @PostMapping("/order/create")
    public Result<CreateOrderVO> createOrder(@RequestBody  @Validated CreateOrderDTO createOrderDTO) {
        RequestContext context = RequestContextHolder.getRequestContext();

        try {
            CreateOrderVO createOrderVO = orderService.createOrder(createOrderDTO);
            Result<CreateOrderVO> result = Result.success(createOrderVO);

            LogUtils.monitor(context, "TicketController", "createOrder",context.getStartTime(),
                    SUCCESS, createOrderDTO, result);

            return result;
        } catch (Exception e) {
            LogUtils.monitor(context, "TicketController", "createOrder",context.getStartTime(),
                    FAIL, createOrderDTO, e.getMessage(), e);
            throw e;
        }
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
