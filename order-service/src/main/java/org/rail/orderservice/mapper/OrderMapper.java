package org.rail.orderservice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
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

}
