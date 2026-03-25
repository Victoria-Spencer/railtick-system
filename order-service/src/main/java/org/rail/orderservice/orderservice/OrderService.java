package org.rail.orderservice.orderservice;

import org.rail.common.core.result.PageResult;
import org.rail.orderservice.pojo.dto.CreateOrderDTO;
import org.rail.orderservice.pojo.dto.CreatePreOrderDTO;
import org.rail.orderservice.pojo.dto.FrontSelfTicketPageDTO;
import org.rail.orderservice.pojo.dto.OrderPageQueryDTO;
import org.rail.orderservice.pojo.vo.CreateOrderVO;
import org.rail.orderservice.pojo.vo.OrderPageQueryVO;
import org.rail.orderservice.pojo.vo.SelfTicketPageVO;

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

    /**
     * 分页查询订单
     * @param orderPageQueryDTO
     * @return
     */
    PageResult<OrderPageQueryVO> orderPageQuery(OrderPageQueryDTO orderPageQueryDTO);

    /**
     * 分页查询本人车票
     * @param frontSelfTicketPageDTO
     * @return
     */
    PageResult<SelfTicketPageVO> selfTicketPageQuery(FrontSelfTicketPageDTO frontSelfTicketPageDTO);

    /**
     * 取消车票订单
     * @param orderSn
     */
    void cancelOrder(String orderSn);
}
