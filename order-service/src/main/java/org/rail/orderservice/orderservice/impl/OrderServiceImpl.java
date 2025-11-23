package org.rail.orderservice.orderservice.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import com.github.pagehelper.PageHelper;
import io.seata.spring.annotation.GlobalTransactional;
import org.rail.commonapi.client.TicketFeignClient;
import org.rail.commonapi.client.UserFeignClient;
import org.rail.commonapi.constant.OrderTypeConstants;
import org.rail.commonapi.dto.*;
import org.rail.commonservice.exception.BusinessException;
import org.rail.commonservice.exception.OpenFeignException;
import org.rail.commonservice.exception.OrderNotFoundException;
import org.rail.commonservice.result.PageResult;
import org.rail.commonservice.result.Result;
import org.rail.orderservice.constant.PreOrderStatusConstants;
import org.rail.commonapi.constant.SeatIntervalStatusConstants;
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
    // 全局事务
    @GlobalTransactional
    public String createPreOrder(CreatePreOrderDTO createPreOrderDTO) {
        // 1.根据用户ID和列车ID，查询是否已存在预订单
        PreOrder preOrder = orderMapper.getByPreOrderUserIdAndTrainId(createPreOrderDTO.getUserId(), createPreOrderDTO.getTrainId());
        // 2.若已经存在，则直接修改
        if(preOrder != null) {
            // TODO 订单明细不同，创建新订单
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

            // 构建远程调用的条件，【先前的座位信息，标记为已释放】
            SeatIntervalOccupyDTO sioDTO = new SeatIntervalOccupyDTO();
            if (preOrderDetailsList.get(0).getTempSeatNo() != null) {
                List<SeatIntervalOccupyUpdateDTO> updateDTOList = new ArrayList<>();
                updateDTOList = BeanUtil.copyToList(
                        preOrderDetailsList,
                        SeatIntervalOccupyUpdateDTO.class,
                        CopyOptions
                                .create()
                                .setFieldMapping(new HashMap<>() {{
                                    put("preOrderId", "orderId");
                                    put("tempSeatNo", "seatNo");
                                }})
                );

                Long trainId = preOrder.getTrainId(); // 列车ID
                for (SeatIntervalOccupyUpdateDTO updateDTO : updateDTOList) {
                    updateDTO.setTrainId(trainId);
                    updateDTO.setOrderType(OrderTypeConstants.PREORDER); // 订单类型为预订单
                    updateDTO.setStatus(SeatIntervalStatusConstants.RELEASED); // 座位占用已释放
                }
                sioDTO.setUpdateDTOList(updateDTOList);
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

                List<SeatIntervalOccupyInsertDTO> insertDTOList = new ArrayList<>();

                for (int i = 0; i < preOrderDetailsList.size(); i++) {
                    PreOrderDetails details = preOrderDetailsList.get(i);
                    ChooseSeatDTO seat = chooseSeats.get(i);
                    details.setCarriageNumber(seat.getCarriageNumber());
                    details.setTempSeatNo(seat.getTempSeatNo());

                    // 构建远程调用的条件，【现在的座位信息，新增占用记录】
                    SeatIntervalOccupyInsertDTO insertDTO = new SeatIntervalOccupyInsertDTO();
                    BeanUtil.copyProperties(
                            details,
                            insertDTO,
                            CopyOptions
                                    .create()
                                    .setFieldMapping(new HashMap<>() {{
                                        put("preOrderId", "orderId");
                                        put("tempSeatNo", "seatNo");
                                    }})
                    );
                    insertDTO.setTrainId(preOrder.getTrainId());
                    insertDTO.setDepartureCode(createPreOrderDTO.getDepartureCode());
                    insertDTO.setArrivalCode(createPreOrderDTO.getArrivalCode());
                    insertDTO.setOrderType(OrderTypeConstants.PREORDER); // 订单类型为预订单
                    insertDTO.setStatus(SeatIntervalStatusConstants.LOCKED); // 座位锁定中
                    insertDTOList.add(insertDTO);
                }

                sioDTO.setInsertDTOList(insertDTOList);
            }

            // 执行更新
            orderMapper.updatePreOrderDetailsList(preOrderDetailsList);

            // 远程调用，修改占用区间与更新座位状态
            if (sioDTO != null && !sioDTO.isEmpty()){
                ticketFeignClient.updateSeatStatus(sioDTO);
            }

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
    // 全局事务
    @GlobalTransactional
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

        // 3.遍历orderDetails,拷贝属性并设置外键
        for (OrderDetails orderDetails : orderDetailsList) {
            BeanUtil.copyProperties(createOrderDTO, orderDetails);
            // 设置外键
            orderDetails.setOrderId(order.getId());
        }

        // 构建远程调用的条件
        SeatIntervalOccupyDTO sioDTO = new SeatIntervalOccupyDTO();

        // 4.判断座位是否为空，若为空，则随机分配
        String seatNo = orderDetailsList.get(0).getSeatNo();
        if (seatNo == null) {
            Map<Integer, List<SeatDTO>> seatTypeToSeatsMap = getSeatTypeToSeatsMap(order, orderDetailsList);

            // 遍历orderDetailsList，为每个乘客添加座位信息
            for (OrderDetails orderDetails : orderDetailsList) {
                List<SeatDTO> pairs = seatTypeToSeatsMap.get(orderDetails.getSeatType());

                if (pairs == null) {
                    throw new BusinessException("无可分配座位");
                }
                SeatDTO first = pairs.getFirst();
                String carriageNumber = first.getCarriageNumber();
                String currentSeatNo = first.getSeatNo();
                orderDetails.setCarriageNumber(carriageNumber);
                orderDetails.setSeatNo(currentSeatNo);
                pairs.remove(first);
            }
            // 座位占用区间，【新增占用记录】
            List<SeatIntervalOccupyInsertDTO> insertDTOList = BeanUtil.copyToList(orderDetailsList, SeatIntervalOccupyInsertDTO.class);
            for (SeatIntervalOccupyInsertDTO insertDTO : insertDTOList) {
                insertDTO.setTrainId(order.getTrainId());
                // 订单类型为订单
                insertDTO.setOrderType(OrderTypeConstants.ORDER);
                // 座位区间状态锁定中
                insertDTO.setStatus(SeatIntervalStatusConstants.LOCKED);
            }
            sioDTO.setInsertDTOList(insertDTOList);
        } else {
            // 预订单修改占用状态为已释放
            List<SeatIntervalOccupyUpdateDTO> updateDTOList = BeanUtil.copyToList(
                    preOrderDetailsList,
                    SeatIntervalOccupyUpdateDTO.class,
                    CopyOptions
                            .create()
                            .setFieldMapping(new HashMap<>(){{
                                put("tempSeatNo", "seatNo");
                                put("preOrderId", "orderId");
                            }})
            );
            for (SeatIntervalOccupyUpdateDTO updateDTO : updateDTOList) {
                updateDTO.setTrainId(preOrder.getTrainId());
                updateDTO.setOrderType(OrderTypeConstants.PREORDER);
                updateDTO.setStatus(SeatIntervalStatusConstants.RELEASED);
            }
            sioDTO.setUpdateDTOList(updateDTOList);


            // 订单新生成占用区间
            List<SeatIntervalOccupyInsertDTO> insertDTOList = BeanUtil.copyToList(orderDetailsList, SeatIntervalOccupyInsertDTO.class);
            for (SeatIntervalOccupyInsertDTO insertDTO : insertDTOList) {
                insertDTO.setTrainId(order.getTrainId());
                insertDTO.setOrderType(OrderTypeConstants.ORDER);
                insertDTO.setStatus(SeatIntervalStatusConstants.LOCKED);
            }
            sioDTO.setInsertDTOList(insertDTOList);
        }

        orderMapper.batchInsertOrderDetails(orderDetailsList);
        // 标记预订单为已转为正式订单
        markPreOrderStatus2(preOrderId);

        // 远程调用，修改区间和更新座位的状态
        if (sioDTO != null && !sioDTO.isEmpty()) {
            ticketFeignClient.updateSeatStatus(sioDTO);
        }


        /**        封装数据,返回        **/
        CreateOrderVO createOrderVO = BeanUtil.copyProperties(createOrderDTO, CreateOrderVO.class);
        // 设置订单号
        createOrderVO.setOrderSn(preOrder.getPreOrderSn());
        List<OrderDetailsVO> createOrderDetailsVOS = BeanUtil.copyToList(orderDetailsList, OrderDetailsVO.class);
        // 设置订单明细数据
        createOrderVO.setCreateOrderDetailsVOList(createOrderDetailsVOS);

        return createOrderVO;
    }

    private Map<Integer, List<SeatDTO>> getSeatTypeToSeatsMap(Order order, List<OrderDetails> orderDetailsList) {
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
        Map<Integer, List<SeatDTO>> seatTypeToSeatsMap = new HashMap<>();

        for (AvailableSeatDTO seatDTO : availableSeatDTOList) {
            // 从可用座位DTO中获取席别类型
            Integer seatType = seatDTO.getSeatType();
            // 获取车厢号和座位号
            String carriageNumber = seatDTO.getCarriageNumber();
            String currentSeatNo = seatDTO.getSeatNo();

            // 为当前席别类型初始化列表（若不存在则创建），并添加座位信息
            seatTypeToSeatsMap.computeIfAbsent(seatType, k -> new ArrayList<>())
                    .add(new SeatDTO(carriageNumber, currentSeatNo));
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
    @GlobalTransactional
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
        // 构建远程调用的条件，更新占用信息和座位状态，【新的座位信息，标记为已占用】
        SeatIntervalOccupyDTO sioDTO = new SeatIntervalOccupyDTO();
        List<SeatIntervalOccupyInsertDTO> insertDTOList = new ArrayList<>();

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
                SeatIntervalOccupyInsertDTO insertDTO = new SeatIntervalOccupyInsertDTO();
                BeanUtil.copyProperties (
                        preOrderDetails,
                        insertDTO,
                        CopyOptions
                                .create()
                                .setFieldMapping(new HashMap<>(){{
                                    put("preOrderId", "orderId");
                                    put("tempSeatNo", "seatNo");
                                }})
                );
                insertDTO.setTrainId(createPreOrderDTO.getTrainId());
                insertDTO.setDepartureCode(createPreOrderDTO.getDepartureCode());
                insertDTO.setArrivalCode(createPreOrderDTO.getArrivalCode());
                insertDTO.setOrderType(OrderTypeConstants.PREORDER);
                insertDTO.setStatus(SeatIntervalStatusConstants.LOCKED);
                insertDTOList.add(insertDTO);
            }

            preOrderDetailsList.add(preOrderDetails);
        }


        // 4.插入预订单明细到数据库
        orderMapper.batchInsertPreOrderDetails(preOrderDetailsList);

        sioDTO.setInsertDTOList(insertDTOList);
        if(sioDTO != null && !sioDTO.isEmpty()) {
            // 5.远程调用，新增新的占用区间，并更新座位的状态
            ticketFeignClient.updateSeatStatus(sioDTO);
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
