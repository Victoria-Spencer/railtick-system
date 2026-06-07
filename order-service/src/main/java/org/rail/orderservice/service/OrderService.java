package org.rail.orderservice.service;

import org.rail.common.core.model.result.PageResult;
import org.rail.orderservice.model.dto.CreateOrderDTO;
import org.rail.orderservice.model.dto.CreatePreOrderDTO;
import org.rail.orderservice.model.dto.FrontSelfTicketPageDTO;
import org.rail.orderservice.model.dto.OrderPageQueryDTO;
import org.rail.orderservice.model.vo.CreateOrderVO;
import org.rail.orderservice.model.vo.OrderPageQueryVO;
import org.rail.orderservice.model.vo.SelfTicketPageVO;

public interface OrderService {

    /**
     * 创建预订单，临时锁定座位
     * @param createPreOrderDTO 预订单信息
     * @return 预订号
     */
    String createPreOrder(CreatePreOrderDTO createPreOrderDTO);

    /**
     * 创建订单，并返回订单数据
     * @param createOrderDTO 订单信息
     * @return 订单数据
     */
    CreateOrderVO createOrder(CreateOrderDTO createOrderDTO);

    /**
     * 分页查询订单
     * @param orderPageQueryDTO 订单分页查询信息
     * @return 订单分页查询结果
     */
    PageResult<OrderPageQueryVO> orderPageQuery(OrderPageQueryDTO orderPageQueryDTO);

    /**
     * 分页查询本人车票
     * @param frontSelfTicketPageDTO 本人车票分页查询信息
     * @return 本人车票分页查询结果
     */
    PageResult<SelfTicketPageVO> selfTicketPageQuery(FrontSelfTicketPageDTO frontSelfTicketPageDTO);

    /**
     * 取消订单
     * @param orderSn 订单号
     */
    void cancelOrder(String orderSn);
}
