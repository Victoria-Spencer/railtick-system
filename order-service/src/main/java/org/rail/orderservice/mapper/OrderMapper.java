package org.rail.orderservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.rail.orderservice.pojo.entity.PreOrder;
import org.rail.orderservice.pojo.entity.PreOrderDetails;

import java.util.List;

@Mapper
public interface OrderMapper {

    /**
     * 插入预订单
     * @return
     */
    void insertPreOrder(PreOrder preOrder);

    /**
     * 批量插入订单明细
     */
    void batchInsertPreOrderDetails(@Param("preOrderDetailsList") List<PreOrderDetails> preOrderDetailsList);

    /**
     * 根据用户id和列车id查询预订单信息
     * @param userId
     * @param trainId
     * @return
     */
    @Select("select id, pre_order_sn, user_id, train_id, total_amount, expire_time, status, create_time " +
            "from pre_order " +
            "where user_id = #{userId} AND train_id = #{trainId}")
    PreOrder getByPreOrderUserIdAndTrainId(Long userId, Long trainId);

    /**
     * 根据预订单id查询预订单明细id和临时座位号
     * @param preOrderId
     * @return
     */
    @Select("select id, temp_seat_no from pre_order_details where pre_order_id = #{preOrderId}")
    List<PreOrderDetails> getIdAndTempSeatNoByPreOrderId(Long preOrderId);

    /**
     * 更新预订单
     * @param preOrder
     */
    void updatePreOrder(PreOrder preOrder);

    /**
     * 批量更新预订单明细
     * @param preOrderDetailsList
     */
    void updatePreOrderDetailsList(List<PreOrderDetails> preOrderDetailsList);
}
