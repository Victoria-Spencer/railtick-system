package org.rail.orderservice.orderservice.impl;

import cn.hutool.core.bean.BeanUtil;
import org.rail.orderservice.mapper.OrderMapper;
import org.rail.orderservice.orderservice.OrderService;
import org.rail.orderservice.pojo.dto.CreatePreOrderDTO;
import org.rail.orderservice.pojo.dto.PassengerOrderDetailDTO;
import org.rail.orderservice.pojo.entity.PreOrder;
import org.rail.orderservice.pojo.entity.PreOrderDetails;
import org.rail.orderservice.utils.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.core.Local;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    // 从配置文件注入预订单有效期（分钟）
    @Value("${order.pre.expire-minutes}")
    private Integer preOrderExpireMinutes;
    /**
     * 创建预订单，临时锁定座位
     * @param createPreOrderDTO
     * @return
     */
    public String createPreOrder(CreatePreOrderDTO createPreOrderDTO) {
        // 1.生成预订单对象
        PreOrder preOrder = BeanUtil.copyProperties(createPreOrderDTO, PreOrder.class);
        // 用雪花算法生成预订单号
        String preOrderSn = SnowflakeIdGenerator.generatePreOrderSn();
        preOrder.setPreOrderSn(preOrderSn);
        preOrder.setExpireTime(calculateExpireTime());
        preOrder.setCreateTime(LocalDateTime.now());

        Double totalAmount = 0.0;
        for (PassengerOrderDetailDTO passengerOrderDetailDTO : createPreOrderDTO.getPassengerOrderDetailDTOList()) {
            // 计算总金额
            totalAmount += passengerOrderDetailDTO.getAmount();
        }


        // 2.插入预订单到数据库,并返回订单id
        preOrder.setTotalAmount(totalAmount);
        orderMapper.insertPreOrder(preOrder);
        Long preOrderId = preOrder.getId();


        // 3.生成预订单明细对象列表
        List<PreOrderDetails> preOrderDetailsList = new ArrayList<>();
        List<PassengerOrderDetailDTO> passengerOrderDetailDTOList = createPreOrderDTO.getPassengerOrderDetailDTOList();
        List<String> chooseSeats = createPreOrderDTO.getChooseSeats();

        // 两个列表长度必须一致（否则可能出现索引越界或数据不匹配）
        if (passengerOrderDetailDTOList == null || chooseSeats == null) {
            throw new IllegalArgumentException("乘客信息列表或座位列表不能为空");
        }
        if (passengerOrderDetailDTOList.size() != chooseSeats.size()) {
            throw new IllegalArgumentException("乘客数量与座位数量不匹配");
        }

        // 通过索引遍历两个列表，一一对应
        for (int i = 0; i < passengerOrderDetailDTOList.size(); i++) {
            PassengerOrderDetailDTO passengerDTO = passengerOrderDetailDTOList.get(i); // 第i个乘客
            String tempSeatNo = chooseSeats.get(i); // 第i个座位号（与乘客一一对应）

            // 拷贝乘客基本信息到预订单明细
            PreOrderDetails preOrderDetails = BeanUtil.copyProperties(passengerDTO, PreOrderDetails.class);

            // 设置外键和座位号
            preOrderDetails.setPreOrderId(preOrderId);
            preOrderDetails.setTempSeatNo(tempSeatNo); // 假设PreOrderDetails有seatNo字段存储座位号

            preOrderDetailsList.add(preOrderDetails);
        }


        // 4.插入预订单明细到数据库
        orderMapper.batchInsertPreOrderDetails(preOrderDetailsList);
        return preOrderSn;
    }

    /**
     * 计算过期时间
     * @return
     */
    public LocalDateTime calculateExpireTime() {
        // 1. 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 2. 处理null情况（避免空指针，设置默认值，例如30分钟）
        int minutes = (preOrderExpireMinutes != null) ? preOrderExpireMinutes : 30;

        // 3. 计算过期时间：当前时间 + 过期分钟数
        LocalDateTime expireTime = now.plusMinutes(minutes);

        return expireTime;
    }
}
