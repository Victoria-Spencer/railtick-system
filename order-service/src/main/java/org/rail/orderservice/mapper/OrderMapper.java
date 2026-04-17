package org.rail.orderservice.mapper;

import org.apache.ibatis.annotations.*;
import org.rail.orderservice.pojo.dto.OrderPageQueryDTO;
import org.rail.orderservice.pojo.dto.SelfTicketPageDTO;
import org.rail.orderservice.pojo.entity.Order;
import org.rail.orderservice.pojo.entity.OrderDetails;
import org.rail.orderservice.pojo.entity.PreOrder;
import org.rail.orderservice.pojo.entity.PreOrderDetails;
import org.rail.orderservice.pojo.vo.OrderPageQueryVO;
import org.rail.orderservice.pojo.vo.SelfTicketPageVO;

import java.util.List;

@Mapper
public interface OrderMapper {

    /**
     * 插入预订单
     * @param preOrder 预订单信息
     */
    void insertPreOrder(PreOrder preOrder);

    /**
     * 批量插入订单明细
     */
    void batchInsertPreOrderDetails(@Param("preOrderDetailsList") List<PreOrderDetails> preOrderDetailsList);

    /**
     * 根据用户id和列车id查询预订单信息
     * @param userId 用户id
     * @param trainId 列车id
     * @return 预订单信息
     */
    @Select("select id, pre_order_sn, user_id, train_id, total_amount, expire_time, status, create_time " +
            "from pre_order " +
            "where user_id = #{userId} AND train_id = #{trainId} AND status = 0")
    PreOrder getByPreOrderUserIdAndTrainId(Long userId, Long trainId);

    /**
     * 根据预订单id查询预订单明细id和临时座位号
     * @param preOrderId 预订单id
     * @return 预订单明细id和临时座位号列表
     */
    @Select("select id, pre_order_id, seat_type, carriage_number, temp_seat_no from pre_order_details where pre_order_id = #{preOrderId}")
    List<PreOrderDetails> getTempSeatInfoByPreOrderId(Long preOrderId);

    /**
     * 更新预订单
     * @param preOrder 预订单信息
     */
    void updatePreOrder(PreOrder preOrder);

    /**
     * 批量更新预订单明细
     * @param preOrderDetailsList
     */
//    void updatePreOrderDetailsList(List<PreOrderDetails> preOrderDetailsList);

    /**
     * 查询预订单数据
     * @param preOrderSn 预订单编号
     * @return 预订单信息
     */
    @Select("select id, pre_order_sn, user_id, train_id, total_amount, expire_time, status, create_time " +
            "from pre_order " +
            "where pre_order_sn = #{preOrderSn} AND status = 0")
    PreOrder getByPreOrderSn(String preOrderSn);

    /**
     * 插入订单数据
     * @param order 订单信息
     */
    void insertOrder(Order order);

    /**
     * 根据预订单id查询预订单明细数据
     * @param preOrderId 预订单id
     * @return 预订单明细列表
     */
    @Select("select id, pre_order_id, real_name, id_type, id_card, ticket_type, seat_type, carriage_number, temp_seat_no, amount " +
            "from pre_order_details " +
            "where pre_order_id = #{preOrderId}")
    List<PreOrderDetails> getDetailsByPreOrderId(Long preOrderId);

    /**
     * 批量插入订单明细数据
     * @param orderDetailsList 订单明细列表
     */
    void batchInsertOrderDetails(List<OrderDetails> orderDetailsList);

    /**
     * 分页查询订单及关联的订单明细
     * @param orderPageQueryDTO 分页查询条件
     * @return 订单及关联的订单明细列表
     */
    List<OrderPageQueryVO> getOrderPageByQueryDTO(@Param("queryDTO") OrderPageQueryDTO orderPageQueryDTO);

    /**
     * 分页本人订单明细和关联的订单信息
     * @param selfTicketPageDTO 分页查询条件
     * @return 本人订单明细和关联的订单信息列表
     */
    List<SelfTicketPageVO> getSelfTicketPageByQueryDTO(SelfTicketPageDTO selfTicketPageDTO);

    /**
     * 更新订单状态为已经取消
     * @param orderSn 订单编号
     */
    @Update("update `order` set status = 2 where order_sn = #{orderSn}")
    void updateOrderByOrderSn(String orderSn);

    /**
     * 根据预订单id删除预订单明细
     * @param id 预订单id
     */
    @Delete("delete from pre_order_details where pre_order_id = #{id}")
    void deletePreOrderDetailsByPreOrderId(Long id);
}
