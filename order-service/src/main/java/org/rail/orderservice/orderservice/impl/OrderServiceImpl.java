package org.rail.orderservice.orderservice.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.Pair;
import com.github.pagehelper.PageHelper;
import org.rail.commonapi.client.TicketFeignClient;
import org.rail.commonapi.client.UserFeignClient;
import org.rail.commonapi.dto.AvailableSeatDTO;
import org.rail.commonapi.dto.RandomSeatQueryDTO;
import org.rail.commonapi.dto.UpdateSeatStatusDTO;
import org.rail.commonapi.dto.UserIdCardDTO;
import org.rail.commonservice.exception.BusinessException;
import org.rail.commonservice.exception.OpenFeignException;
import org.rail.commonservice.exception.OrderNotFoundException;
import org.rail.commonservice.result.PageResult;
import org.rail.commonservice.result.Result;
import org.rail.orderservice.constant.PreOrderStatusConstants;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class OrderServiceImpl implements OrderService {

    @Autowired
    private OrderMapper orderMapper;
    // 从配置文件注入预订单有效期（分钟）
    @Value("${order.pre.expire-minutes : 15}") // 默认15分钟
    private Integer preOrderExpireMinutes;
    @Autowired
    private UserFeignClient userFeignClient;
    @Autowired
    private TicketFeignClient ticketFeignClient;

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
            preOrder.setStatus(PreOrderStatusConstants.VALID);
            preOrder.setCreateTime(LocalDateTime.now());
            orderMapper.updatePreOrder(preOrder);

            // 修改临时座位信息
            Long preOrderId = preOrder.getId();
            List<PreOrderDetails> preOrderDetailsList = orderMapper.getTempSeatInfoByPreOrderId(preOrderId);
            List<ChooseSeatDTO> chooseSeats = createPreOrderDTO.getChooseSeats();

            // 校验预订单详情列表不为空（必须有乘客信息）
            if (preOrderDetailsList == null || preOrderDetailsList.isEmpty()) {
                throw new IllegalArgumentException("预订单详情列表不能为空");
            }

            // 构建远程调用的条件，更新座位的状态（status），【先前的座位信息】
            List<UpdateSeatStatusDTO> updateSeatStatusDTOList = new ArrayList<>();
            if(preOrderDetailsList.get(0).getTempSeatNo() != null) {
                updateSeatStatusDTOList = BeanUtil.copyToList(
                        preOrderDetailsList,
                        UpdateSeatStatusDTO.class,
                        CopyOptions
                                .create()
                                .setFieldMapping(new HashMap<>(){{
                                    put("tempSeatNo", "seatNo");
                                }})
                );
            }

            // 处理“取消选座”场景（chooseSeats为空）
            if (chooseSeats == null || chooseSeats.isEmpty()) {
                // 清空所有座位信息
                for (PreOrderDetails details : preOrderDetailsList) {
                    details.setCarriageNumber(null);
                    details.setTempSeatNo(null);
                }
            } else {
                // 处理“选座”场景（校验数量匹配后更新）
                if (preOrderDetailsList.size() != chooseSeats.size()) {
                    throw new IllegalArgumentException("预订单详情列表与座位数量不匹配");
                }
                for (int i = 0; i < preOrderDetailsList.size(); i++) {
                    PreOrderDetails details = preOrderDetailsList.get(i);
                    ChooseSeatDTO seat = chooseSeats.get(i);
                    details.setCarriageNumber(seat.getCarriageNumber());
                    details.setTempSeatNo(seat.getTempSeatNo());

                    // 构建远程调用的条件，更新座位的状态（status），【现在的座位信息】
                    UpdateSeatStatusDTO updateSeatStatusDTO = new UpdateSeatStatusDTO();
                    BeanUtil.copyProperties (
                                    details,
                                    updateSeatStatusDTO,
                                    CopyOptions
                                            .create()
                                            .setFieldMapping(new HashMap<>(){{
                                                put("tempSeatNo", "seatNo");
                                            }})
                            );
                    updateSeatStatusDTOList.add(updateSeatStatusDTO);
                }
            }

            // 执行更新
            orderMapper.updatePreOrderDetailsList(preOrderDetailsList);
            // 远程调用，更新座位的状态（status）
            Long trainId = createPreOrderDTO.getTrainId();
            // 流式遍历，为每个DTO设置trainId
            updateSeatStatusDTOList.stream()
                    .forEach(dto -> dto.setTrainId(trainId));
            ticketFeignClient.updateSeatStatus(updateSeatStatusDTOList);

            return preOrder.getPreOrderSn();
        }

        // 3.未存在，则重新生成
        return creatNewPreOrder(createPreOrderDTO);
    }

    /**
     * 创建订单，并返回订单数据
     * @param createOrderDTO
     * @return
     */
    // TODO 全局事务
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

        // 2.预订单明细数据拷贝
        List<OrderDetails> orderDetailsList = BeanUtil.copyToList(
                preOrderDetailsList,
                OrderDetails.class,
                CopyOptions.create()
                        .setFieldMapping(new HashMap<String, String>(){{
                            put("id", "preOrderDetailId");
                            put("tempSeatNo", "seatNo");
                        }})
                );

        // 3.拷贝前端传递过来的字段
        orderDetailsList.stream()
                .forEach(orderDetails ->
                        BeanUtil.copyProperties(createOrderDTO, orderDetails)
                );


        // 4.判断座位是否为空，若为空，则随机分配
        String seatNo = orderDetailsList.get(0).getSeatNo();
        if (seatNo == null) {
            Map<Integer, List<Pair<String, String>>> seatTypeToSeatsMap = getSeatTypeToSeatsMap(order, orderDetailsList);

            // 遍历orderDetailsList，为每个乘客添加座位信息
            for (OrderDetails orderDetails : orderDetailsList) {
                List<Pair<String, String>> pairs = seatTypeToSeatsMap.get(orderDetails.getSeatType());
                Pair<String, String> first = pairs.getFirst();
                String carriageNumber = first.getKey();
                String currentSeatNo = first.getValue();
                orderDetails.setCarriageNumber(carriageNumber);
                orderDetails.setSeatNo(currentSeatNo);
                pairs.remove(first);
            }


            // 构建远程调用的条件，更新座位的状态（status），【随机分配的座位信息】
            List<UpdateSeatStatusDTO> updateSeatStatusDTOList = BeanUtil.copyToList(orderDetailsList, UpdateSeatStatusDTO.class);
            // 远程调用，更新座位的状态（status）
            Long trainId = order.getTrainId();
            // 流式遍历，为每个DTO设置trainId
            updateSeatStatusDTOList.stream()
                    .forEach(dto -> dto.setTrainId(trainId));
            ticketFeignClient.updateSeatStatus(updateSeatStatusDTOList);
        }

        // 5.遍历orderDetails,拷贝属性
        for (OrderDetails orderDetails : orderDetailsList) {
            // 设置外键
            orderDetails.setOrderId(order.getId());

            // 分配座位
            orderDetails.setSeatNo(orderDetails.getSeatNo());
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

    private Map<Integer, List<Pair<String, String>>> getSeatTypeToSeatsMap(Order order, List<OrderDetails> orderDetailsList) {
        // 远程调用，判断是否还有空座位（车厢号，座位号），若有则返回
        // 列车ID, 席别类型，出发站点编码，到达站点编码
        RandomSeatQueryDTO randomSeatQueryDTO = new RandomSeatQueryDTO();
        randomSeatQueryDTO.setTrainId(order.getTrainId());
        // 提取每个乘客的席别类型
        List<Integer> seatTypes = orderDetailsList.stream()
                .map(OrderDetails::getSeatType)
                .collect(Collectors.toList());
        randomSeatQueryDTO.setSeatTypes(seatTypes);
        randomSeatQueryDTO.setDepartureCode(orderDetailsList.get(0).getDepartureCode());
        randomSeatQueryDTO.setArrivalCode(orderDetailsList.get(0).getArrivalCode());

        // 远程调用
        Result<List<AvailableSeatDTO>> feignResult = ticketFeignClient.getAvailableSeats(randomSeatQueryDTO);
        if (!feignResult.isSuccess()) {
            throw new OpenFeignException(feignResult.getMessage());
        }

        List<AvailableSeatDTO> availableSeatDTOList = feignResult.getData();
        if (availableSeatDTOList == null || availableSeatDTOList.size() < seatTypes.size()) {
            throw new BusinessException("可用座位数不足");
        }

        // 核心：构建席别类型与（车厢号，座位号）列表的映射Map
        Map<Integer, List<Pair<String, String>>> seatTypeToSeatsMap = new HashMap<>();

        for (AvailableSeatDTO seatDTO : availableSeatDTOList) {
            // 从可用座位DTO中获取席别类型
            Integer seatType = seatDTO.getSeatType();
            // 获取车厢号和座位号
            String carriageNumber = seatDTO.getCarriageNumber();
            String currentSeatNo = seatDTO.getSeatNo();

            // 为当前席别类型初始化列表（若不存在则创建），并添加座位信息
            seatTypeToSeatsMap.computeIfAbsent(seatType, k -> new ArrayList<>())
                    .add(new Pair<>(carriageNumber, currentSeatNo));
        }
        return seatTypeToSeatsMap;
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
        if(!userIdCardDTOResult.isSuccess()) {
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
        newPreOrder.setStatus(PreOrderStatusConstants.CONVERTED_TO_ORDER);
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
        List<PassengerOrderDetailDTO> passengerOrderDetailDTOList = createPreOrderDTO.getPassengerOrderDetailDTOList();
        // 校验预订单详情列表不为空（必须有乘客信息）
        if (passengerOrderDetailDTOList == null || passengerOrderDetailDTOList.isEmpty()) {
            throw new IllegalArgumentException("预订单详情列表不能为空");
        }

        List<PreOrderDetails> preOrderDetailsList = new ArrayList<>();
        List<ChooseSeatDTO> chooseSeats = createPreOrderDTO.getChooseSeats();
        // 构建远程调用的条件，更新座位的状态（status），【先前的座位信息】
        List<UpdateSeatStatusDTO> updateSeatStatusDTOList = new ArrayList<>();

        for (int i = 0; i < passengerOrderDetailDTOList.size(); i++) {
            PassengerOrderDetailDTO passengerDTO = passengerOrderDetailDTOList.get(i); // 第i个乘客
            // 拷贝乘客基本信息到预订单明细（姓名、证件等）
            PreOrderDetails preOrderDetails = BeanUtil.copyProperties(passengerDTO, PreOrderDetails.class);
            // 设置外键，车厢号和座位号
            preOrderDetails.setPreOrderId(preOrderId);
            // 初始化座位信息为null（默认不选座）
            preOrderDetails.setCarriageNumber(null);
            preOrderDetails.setTempSeatNo(null);

            // 若选座且座位列表不为空，补充座位信息
            if (chooseSeats != null && !chooseSeats.isEmpty()) {
                // 校验座位数量与乘客数量匹配
                if (i >= chooseSeats.size()) {
                    throw new IllegalArgumentException("座位列表数量少于乘客数量");
                }
                ChooseSeatDTO seat = chooseSeats.get(i);
                preOrderDetails.setCarriageNumber(seat.getCarriageNumber());
                preOrderDetails.setTempSeatNo(seat.getTempSeatNo());

                // 构建远程调用的条件，更新座位的状态（status），【座位信息】
                UpdateSeatStatusDTO updateSeatStatusDTO = new UpdateSeatStatusDTO();
                BeanUtil.copyProperties (
                        preOrderDetails,
                        updateSeatStatusDTO,
                        CopyOptions
                                .create()
                                .setFieldMapping(new HashMap<>(){{
                                    put("tempSeatNo", "seatNo");
                                }})
                );
                updateSeatStatusDTOList.add(updateSeatStatusDTO);
            }

            preOrderDetailsList.add(preOrderDetails);
        }


        // 4.插入预订单明细到数据库
        orderMapper.batchInsertPreOrderDetails(preOrderDetailsList);

        if(updateSeatStatusDTOList != null && !updateSeatStatusDTOList.isEmpty()) {
            // 5.远程调用，更新座位的状态（status）
            Long trainId = createPreOrderDTO.getTrainId();
            // 流式遍历，为每个DTO设置trainId
            updateSeatStatusDTOList.stream()
                    .forEach(dto -> dto.setTrainId(trainId));
            ticketFeignClient.updateSeatStatus(updateSeatStatusDTOList);
        }

        return preOrderSn;
    }

    /**
     * 计算过期时间
     * @return
     */
    public LocalDateTime calculateExpireTime() {
        // 1. 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 2. 处理null情况（避免空指针，设置默认值，例如15分钟）
        int minutes = (preOrderExpireMinutes != null) ? preOrderExpireMinutes : 15;

        // 3. 计算过期时间：当前时间 + 过期分钟数
        LocalDateTime expireTime = now.plusMinutes(minutes);

        return expireTime;
    }
}
