package org.rail.orderservice.orderservice.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.github.pagehelper.PageHelper;
import org.rail.commonapi.client.UserFeignClient;
import org.rail.commonapi.dto.UserIdCardDTO;
import org.rail.commonservice.exception.OpenFeignException;
import org.rail.commonservice.exception.OrderNotFoundException;
import org.rail.commonservice.result.PageResult;
import org.rail.commonservice.result.Result;
import org.rail.commonservice.utils.BeanUtils;
import org.rail.orderservice.constant.PreOrderStatus;
import org.rail.orderservice.mapper.OrderMapper;
import org.rail.orderservice.orderservice.OrderService;
import org.rail.orderservice.pojo.dto.*;
import org.rail.orderservice.pojo.entity.Order;
import org.rail.orderservice.pojo.entity.OrderDetails;
import org.rail.orderservice.pojo.entity.PreOrder;
import org.rail.orderservice.pojo.entity.PreOrderDetails;
import org.rail.orderservice.pojo.vo.CreateOrderVO;
import org.rail.orderservice.pojo.vo.OrderDetailsVO;
import org.rail.orderservice.pojo.vo.OrderPageQueryVO;
import org.rail.orderservice.pojo.vo.SelfTicketPageVO;
import org.rail.orderservice.utils.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cglib.core.Local;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    // 从配置文件注入预订单有效期（分钟）
    @Value("${order.pre.expire-minutes}")
    private Integer preOrderExpireMinutes;
    @Autowired
    private UserFeignClient userFeignClient;

    /**
     * 1.创建预订单，临时锁定座位
     * 2.避免造成长期锁座现象
     * （
     *      2.1.同一用户 + 同一车次的预订单 “覆盖机制”
     *      2.2.TODO 退出选座界面：立即释放座位
     *      2.3.TODO 定时任务清理过期预订单
     *  ）
     * @param createPreOrderDTO
     * @return
     */
    @Transactional
    public String createPreOrder(CreatePreOrderDTO createPreOrderDTO) {
        // 1.根据用户ID和列车ID，查询是否已存在预订单
        PreOrder preOrder = orderMapper.getByPreOrderUserIdAndTrainId(createPreOrderDTO.getUserId(), createPreOrderDTO.getTrainId());
        // 2.若已经存在，则直接修改
        if(preOrder != null){
            // 重新生成过期时间和预订单状态
            preOrder.setExpireTime(calculateExpireTime());
            preOrder.setStatus(PreOrderStatus.VALID);
            preOrder.setCreateTime(LocalDateTime.now());
            orderMapper.updatePreOrder(preOrder);

            // 修改临时座位信息
            Long preOrderId = preOrder.getId();
            List<PreOrderDetails> PreOrderDetailsList = orderMapper.getIdAndTempSeatNoByPreOrderId(preOrderId);
            List<ChooseSeatDTO> chooseSeats = createPreOrderDTO.getChooseSeats();

            // 两个列表长度必须一致（否则可能出现索引越界或数据不匹配）
            if (PreOrderDetailsList == null || chooseSeats == null) {
                throw new IllegalArgumentException("预订单详情列表或座位列表不能为空");
            }
            if (PreOrderDetailsList.size() != chooseSeats.size()) {
                throw new IllegalArgumentException("预订单详情列表与座位数量不匹配");
            }

            for (int i = 0; i < PreOrderDetailsList.size(); i++) {
                PreOrderDetails preOrderDetails = PreOrderDetailsList.get(i);
                preOrderDetails.setCarriageNumber(chooseSeats.get(i).getCarriageNumber());
                preOrderDetails.setTempSeatNo(chooseSeats.get(i).getTempSeatNo());
            }
            orderMapper.updatePreOrderDetailsList(PreOrderDetailsList);

            return preOrder.getPreOrderSn();
        }

        // 3.未存在，则重新生成
        String preOrderSn = creatNewPreOrder(createPreOrderDTO);
        return preOrderSn;
    }

    /**
     * 创建订单，并返回订单数据
     * @param createOrderDTO
     * @return
     */
    @Transactional
    public CreateOrderVO createOrder(CreateOrderDTO createOrderDTO) {
        /**       插入订单数据        **/
        // 1. 查询预订单数据
        String preOrderSn = createOrderDTO.getPreOrderSn();
        PreOrder preOrder = orderMapper.getByPreOrderSn(preOrderSn);
        if(preOrder == null){
            throw new OrderNotFoundException("预订单不存在！");
        }
        // 2.预订单数据拷贝
        Order order = BeanUtil.copyProperties(preOrder, Order.class);
        // 3.orderSn（雪花算法随机生成）
        order.setOrderSn(SnowflakeIdGenerator.generateOrderSn());
        // 4.createTime, updateTime
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insertOrder(order);


        /**       插入订单明细数据        **/
        // 1.获取预订单明细数据
        Long preOrderId = preOrder.getId();
        List<PreOrderDetails> preOrderDetailsList = orderMapper.getDetailsByPreOrderId(preOrderId);

        // 2.预订单数据拷贝
        List<OrderDetails> orderDetailsList = BeanUtil.copyToList(
                preOrderDetailsList,
                OrderDetails.class,
                CopyOptions.create()
                        .setFieldMapping(new HashMap<String, String>(){{
                            put("id", "preOrderDetailId");
                            put("tempSeatNo", "seatNo");
                        }})
                );

        // 3.遍历orderDetails,拷贝属性
        for (OrderDetails orderDetails : orderDetailsList) {
            // 设置外键
            orderDetails.setOrderId(order.getId());
            // 判断座位是否为空，若为空，则随机分配
            String seatNo = orderDetails.getSeatNo();
            if (seatNo == null) {
                // TODO 远程调用，判断是否还有空座位（车厢号，座位号）
                orderDetails.setSeatNo("1A");
            }
            // 拷贝其它属性
            BeanUtil.copyProperties(createOrderDTO, orderDetails);
        }
        orderMapper.batchInsertOrderDetails(orderDetailsList);

        // 标记预订单为已转为正式订单
        markPreOrderStatus2(preOrderId);

        /**        封装数据,返回        **/
        CreateOrderVO createOrderVO = BeanUtil.copyProperties(createOrderDTO, CreateOrderVO.class);
        // 设置订单号
        createOrderVO.setOrderSn(preOrder.getPreOrderSn());
        List<OrderDetailsVO> createOrderDetailsVOS = BeanUtil.copyToList(orderDetailsList, OrderDetailsVO.class);
        // 设置订单明细数据
        createOrderVO.setCreateOrderDetailsVOList(createOrderDetailsVOS);

        return createOrderVO;
    }

    /**
     * 分页查询订单
     * @param orderPageQueryDTO
     * @return
     */
    public PageResult<OrderPageQueryVO> orderPageQuery(OrderPageQueryDTO orderPageQueryDTO) {
        // 分页查询
        PageHelper.startPage(orderPageQueryDTO.getPageNumber(), orderPageQueryDTO.getPageSize());
        List<OrderPageQueryVO> orderPageQueryVOList = orderMapper.getOrderPageByQueryDTO(orderPageQueryDTO);

        return new PageResult<>(orderPageQueryVOList);
    }

    /**
     * 分页查询本人车票
     * @param frontSelfTicketPageDTO
     * @return
     */
    public PageResult<SelfTicketPageVO> selfTicketPageQuery(FrontSelfTicketPageDTO frontSelfTicketPageDTO) {
        // 远程调用user-service，根据userId查询idType和idCard，UserIdCardDTO
        Result<UserIdCardDTO> userIdCardDTOResult = userFeignClient.getIdCardInfo(frontSelfTicketPageDTO.getUserId());
        if(!userIdCardDTOResult.isSuccess()){
            throw new OpenFeignException(userIdCardDTOResult.getMessage());
        }
        UserIdCardDTO userIdCardDTO = userIdCardDTOResult.getData();

        // 拷贝
        SelfTicketPageDTO selfTicketPageDTO = BeanUtil.copyProperties(frontSelfTicketPageDTO, SelfTicketPageDTO.class);
        selfTicketPageDTO.setIdType(userIdCardDTO.getIdType());
        selfTicketPageDTO.setIdCard(userIdCardDTO.getIdCard());

        // 分页查询
        PageHelper.startPage(selfTicketPageDTO.getPageNumber(), selfTicketPageDTO.getPageSize());
        List<SelfTicketPageVO> SelfTicketPageVOList = orderMapper.getSeltTicketPageByQueryDTO(selfTicketPageDTO);
        return new PageResult<>(SelfTicketPageVOList);
    }

    /**
     * 取消车票订单
     * @param orderSn
     */
    public void cancelOrder(String orderSn) {
        orderMapper.updateOrderByOrderSn(orderSn);
    }

    /**
     * 标记预订单为已转为正式订单
     * @param preOrderId
     */
    private void markPreOrderStatus2(Long preOrderId) {
        /**        标记预订单为已转为正式订单        **/
        PreOrder newPreOrder = new PreOrder();
        newPreOrder.setId(preOrderId);
        newPreOrder.setStatus(PreOrderStatus.CONVERTED_TO_ORDER);
        orderMapper.updatePreOrder(newPreOrder);
    }

    /**
     * 重新生成新的预订单
     * @param createPreOrderDTO
     * @return
     */
    private String creatNewPreOrder(CreatePreOrderDTO createPreOrderDTO) {
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
        List<ChooseSeatDTO> chooseSeats = createPreOrderDTO.getChooseSeats();

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
            String carriageNumber = chooseSeats.get(i).getCarriageNumber();
            String tempSeatNo = chooseSeats.get(i).getTempSeatNo(); // 第i个座位号（与乘客一一对应）

            // 拷贝乘客基本信息到预订单明细
            PreOrderDetails preOrderDetails = BeanUtil.copyProperties(passengerDTO, PreOrderDetails.class);

            // 设置外键，车厢号和座位号
            preOrderDetails.setPreOrderId(preOrderId);
            preOrderDetails.setCarriageNumber(carriageNumber); // 存储车厢号
            preOrderDetails.setTempSeatNo(tempSeatNo); // 存储座位号

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
