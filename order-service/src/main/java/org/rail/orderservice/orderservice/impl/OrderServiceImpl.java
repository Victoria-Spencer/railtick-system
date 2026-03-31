package org.rail.orderservice.orderservice.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.ObjectUtil;
import com.github.pagehelper.PageHelper;
import io.seata.spring.annotation.GlobalTransactional;
import lombok.extern.slf4j.Slf4j;
import org.rail.api.client.TicketFeignClient;
import org.rail.api.client.UserFeignClient;
import org.rail.api.constant.OrderTypeConstants;
import org.rail.common.redis.constant.RedisConstants;
import org.rail.common.core.exception.BusinessException;
import org.rail.common.core.exception.OpenFeignException;
import org.rail.common.core.exception.OrderNotFoundException;
import org.rail.common.redis.result.AggCacheResult;
import org.rail.common.core.result.PageResult;
import org.rail.common.core.result.Result;
import org.rail.common.redis.util.CacheClient;
import org.rail.api.dto.*;
import org.rail.orderservice.constant.PreOrderStatusConstants;
import org.rail.api.constant.SeatIntervalStatusConstants;
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
import org.rail.orderservice.util.SnowflakeIdGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
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
    @Autowired
    private CacheClient cacheClient;

    /**
     * 1.创建预订单，临时锁定座位
     * 2.避免造成长期锁座现象
     * （
     *      2.1.同一用户 + 同一车次的预订单 “覆盖机制”
     *      2.2.TODO 退出选座界面：立即释放座位
     *      2.3.TODO 定时任务清理过期预订单
     *  ）
     * @param createPreOrderDTO 预订单创建参数（包含用户ID、列车ID、乘客信息、选座信息等）
     * @return 预订单号（唯一标识预订单，格式：PRE + 雪花ID）
     */
    // 全局事务
    @Override
    @GlobalTransactional
    public String createPreOrder(CreatePreOrderDTO createPreOrderDTO) {
        // 1. 根据用户ID和列车ID，查询是否已存在预订单
        PreOrder preOrder = orderMapper.getByPreOrderUserIdAndTrainId(createPreOrderDTO.getUserId(), createPreOrderDTO.getTrainId());

        if(ObjectUtil.isNotNull(preOrder)) {
            // 2. 存在旧预订单：更新逻辑
            return updateExistPreOrder(createPreOrderDTO, preOrder);
        } else {
            // 3. 无旧预订单：创建新预订单+新明细
            return createNewPreOrder(createPreOrderDTO);
        }
    }

    /**
     * 更新已存在的预订单，保留主记录，仅替换明细
     */
    private String updateExistPreOrder(CreatePreOrderDTO createPreOrderDTO, PreOrder preOrder) {
        // 1 释放旧明细关联的座位锁
        List<PreOrderDetails> oldDetails = orderMapper.getTempSeatInfoByPreOrderId(preOrder.getId());
        releaseOldPreOrderSeatLock(preOrder, oldDetails);

        // 2 删除旧明细（仅删明细，不删主记录）
        orderMapper.deletePreOrderDetailsByPreOrderId(preOrder.getId());

        // 3 更新预订单主记录（重置过期时间、状态）
        updatePreOrderMainInfo(createPreOrderDTO, preOrder);

        // 4 插入新明细
        insertNewPreOrderDetails(preOrder.getId(), createPreOrderDTO);

        log.info("预订单更新成功，预订单号：{}", preOrder.getPreOrderSn());
        return preOrder.getPreOrderSn();
    }

    /**
     * 更新预订单主表信息
     */
    private void updatePreOrderMainInfo(CreatePreOrderDTO createPreOrderDTO, PreOrder preOrder) {
        Double newTotalAmount = calculateTotalAmount(createPreOrderDTO.getPassengerOrderDetailDTOList());
        preOrder.setTotalAmount(newTotalAmount);
        preOrder.setExpireTime(calculateExpireTime());
        preOrder.setStatus(PreOrderStatusConstants.VALID);
        orderMapper.updatePreOrder(preOrder);
    }

    /**
     * 释放旧预订单关联的座位锁
     * @param oldPreOrder 旧预订单主记录（用于获取列车ID等核心信息）
     * @param oldDetails  旧预订单明细（包含需要释放的座位信息）
     */
    private void releaseOldPreOrderSeatLock(PreOrder oldPreOrder, List<PreOrderDetails> oldDetails) {
        if (ObjectUtil.isEmpty(oldDetails)) {
            return;
        }

        SeatIntervalOccupyDTO releaseDTO = buildSeatReleaseDto(oldPreOrder, oldDetails);
        try {
            if (!releaseDTO.isEmpty()) {
                ticketFeignClient.updateSeatStatus(releaseDTO);
            }
        } catch (Exception e) {
            log.error("释放预订单座位锁失败，预订单ID：{}", oldPreOrder.getId(), e);
            // 抛出业务异常，触发Seata全局事务回滚（避免「预订单已删但座位未释放」的脏数据）
            throw new OpenFeignException("释放旧预订单座位锁失败：" + e.getMessage());
        }
    }

    /**
     * 构建座位释放DTO
     */
    private SeatIntervalOccupyDTO buildSeatReleaseDto(PreOrder oldPreOrder, List<PreOrderDetails> oldDetails) {
        SeatIntervalOccupyDTO releaseDTO = new SeatIntervalOccupyDTO();
        List<SeatIntervalOccupyUpdateDTO> updateDTOList = BeanUtil.copyToList(
                oldDetails,
                SeatIntervalOccupyUpdateDTO.class,
                CopyOptions.create()
                        .setFieldMapping(new HashMap<>() {{
                            put("preOrderId", "orderId");  // 预订单ID映射为票务服务的orderId
                            put("tempSeatNo", "seatNo");    // 临时座位号映射为正式座位号
                        }})
        );

        // 填充公共字段
        Long trainId = oldPreOrder.getTrainId();
        for (SeatIntervalOccupyUpdateDTO updateDTO : updateDTOList) {
            updateDTO.setTrainId(trainId);
            updateDTO.setOrderType(OrderTypeConstants.PREORDER);    // 标记为预订单类型（区分正式订单）
            updateDTO.setStatus(SeatIntervalStatusConstants.RELEASED);
        }

        releaseDTO.setUpdateDTOList(updateDTOList);
        return releaseDTO;
    }

    /**
     * 插入新预订单明细
     * @param preOrderId 预订单ID
     * @param createPreOrderDTO 入参
     */
    private void insertNewPreOrderDetails(Long preOrderId, CreatePreOrderDTO createPreOrderDTO) {
        List<PassengerOrderDetailDTO> passengerList = createPreOrderDTO.getPassengerOrderDetailDTOList();
        List<ChooseSeatDTO> chooseSeats = createPreOrderDTO.getChooseSeats();

        if (ObjectUtil.isEmpty(passengerList)) {
            throw new IllegalArgumentException("乘客信息不能为空");
        }

        List<PreOrderDetails> detailsList = new ArrayList<>();
        SeatIntervalOccupyDTO sioDTO = new SeatIntervalOccupyDTO();
        List<SeatIntervalOccupyInsertDTO> insertDTOList = new ArrayList<>();

        // 构建新明细
        for (int i = 0; i < passengerList.size(); i++) {
            PreOrderDetails preOrderDetails = buildPreOrderDetail(preOrderId, passengerList.get(i));
            // 处理选座
            handleChooseSeat(createPreOrderDTO, chooseSeats, i, preOrderDetails, insertDTOList);
            detailsList.add(preOrderDetails);
        }

        // 批量插入
        orderMapper.batchInsertPreOrderDetails(detailsList);

        // 锁定新座位
        if (ObjectUtil.isNotEmpty(insertDTOList)) {
            sioDTO.setInsertDTOList(insertDTOList);
            ticketFeignClient.updateSeatStatus(sioDTO);
        }
    }

    /**
     * 处理选座逻辑
     */
    private void handleChooseSeat(CreatePreOrderDTO createPreOrderDTO, List<ChooseSeatDTO> chooseSeats, int i, PreOrderDetails preOrderDetails, List<SeatIntervalOccupyInsertDTO> insertDTOList) {
        if (chooseSeats != null && !chooseSeats.isEmpty() && i < chooseSeats.size()) {
            ChooseSeatDTO seat = chooseSeats.get(i);
            preOrderDetails.setCarriageNumber(seat.getCarriageNumber());
            preOrderDetails.setTempSeatNo(seat.getTempSeatNo());

            // 构建座位锁定DTO
            SeatIntervalOccupyInsertDTO insertDTO = new SeatIntervalOccupyInsertDTO();
            BeanUtil.copyProperties(
                    preOrderDetails,
                    insertDTO,
                    CopyOptions.create().setFieldMapping(new HashMap<>() {{
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
    }

    /**
     * 构建单个预订单明细对象
     */
    private PreOrderDetails buildPreOrderDetail(Long preOrderId, PassengerOrderDetailDTO passengerDTO) {
        // 默认不选座
        PreOrderDetails preOrderDetails = BeanUtil.copyProperties(passengerDTO, PreOrderDetails.class);
        preOrderDetails.setPreOrderId(preOrderId);
        preOrderDetails.setCarriageNumber(null);
        preOrderDetails.setTempSeatNo(null);
        return preOrderDetails;
    }

    /**
     * 创建订单，并返回订单数据
     * @param createOrderDTO 订单创建参数（包含预订单号、支付信息等）
     * @return 订单数据（订单号、订单明细等）
     */
    // 全局事务
    @Override
    @GlobalTransactional
    public CreateOrderVO createOrder(CreateOrderDTO createOrderDTO) {
        log.info("开始创建正式订单，预订单号：{}", createOrderDTO.getPreOrderSn());

        // 1. 查询预订单
        String preOrderSn = createOrderDTO.getPreOrderSn();
        PreOrder preOrder = orderMapper.getByPreOrderSn(preOrderSn);
        if(ObjectUtil.isNull(preOrder)){
            throw new OrderNotFoundException("预订单不存在！");
        }

        // 2. 创建订单主表
        Order order = createOrderMain(preOrder);

        // 3. 处理订单明细 & 座位分配
        List<OrderDetails> orderDetailsList = handleOrderDetails(createOrderDTO, preOrder, order);

        // 4. 标记预订单为已转为正式订单
        markPreOrderStatus2(preOrder.getId());

        log.info("正式订单创建成功，订单号：{}", order.getOrderSn());
        // 5. 封装返回结果
        return buildCreateOrderVo(createOrderDTO, orderDetailsList);
    }

    /**
     * 构建订单返回VO
     */
    private CreateOrderVO buildCreateOrderVo(CreateOrderDTO dto, List<OrderDetails> detailsList) {
        CreateOrderVO vo = BeanUtil.copyProperties(dto, CreateOrderVO.class);
        vo.setOrderSn(dto.getPreOrderSn());
        List<OrderDetailsVO> createOrderDetailsVOS = BeanUtil.copyToList(detailsList, OrderDetailsVO.class);
        vo.setCreateOrderDetailsVOList(createOrderDetailsVOS);
        return vo;
    }

    /**
     * 处理订单明细和座位逻辑
     */
    private List<OrderDetails> handleOrderDetails(CreateOrderDTO createOrderDTO, PreOrder preOrder, Order order) {
        Long preOrderId = preOrder.getId();
        List<PreOrderDetails> preDetailsList = orderMapper.getDetailsByPreOrderId(preOrderId);
        // 拷贝订单明细
        List<OrderDetails> detailsList = BeanUtil.copyToList(
                preDetailsList,
                OrderDetails.class,
                CopyOptions.create()
                        .setFieldMapping(new HashMap<>(){{
                            put("id", "preOrderDetailId");
                            put("tempSeatNo", "seatNo");
                        }})
                );

        // 拷贝属性并设置外键
        for (OrderDetails orderDetails : detailsList) {
            BeanUtil.copyProperties(createOrderDTO, orderDetails);
            orderDetails.setOrderId(order.getId());
        }

        SeatIntervalOccupyDTO sioDTO = handleOrderSeat(preOrder, order, detailsList);

        orderMapper.batchInsertOrderDetails(detailsList);
        // 修改区间和更新座位的状态
        if (!sioDTO.isEmpty()) {
            ticketFeignClient.updateSeatStatus(sioDTO);
        }
        return detailsList;
    }

    /**
     * 处理订单座位分配逻辑
     */
    private SeatIntervalOccupyDTO handleOrderSeat(PreOrder preOrder, Order order, List<OrderDetails> detailsList) {
//        SeatIntervalOccupyDTO sioDTO = new SeatIntervalOccupyDTO();
        // 判断座位是否为空，若为空，则随机分配
        String seatNo = detailsList.getFirst().getSeatNo();

        if (ObjectUtil.isNull(seatNo)) {
            return handleRandomSeat(order, detailsList);
        } else {
            // 释放预订单座位 + 锁定订单座位
            return handleExistSeat(preOrder, order, detailsList);
        }
    }

    /**
     * 处理已有选座
     */
    private SeatIntervalOccupyDTO handleExistSeat(PreOrder preOrder, Order order, List<OrderDetails> detailsList) {
        SeatIntervalOccupyDTO sioDTO = new SeatIntervalOccupyDTO();
        List<PreOrderDetails> preDetailsList = orderMapper.getDetailsByPreOrderId(preOrder.getId());

        // 释放预订单座位
        List<SeatIntervalOccupyUpdateDTO> updateDTOList = BeanUtil.copyToList(
                preDetailsList,
                SeatIntervalOccupyUpdateDTO.class,
                CopyOptions
                        .create()
                        .setFieldMapping(new HashMap<>(){{
                            put("tempSeatNo", "seatNo");
                            put("preOrderId", "orderId");
                        }})
        );
        for (SeatIntervalOccupyUpdateDTO dto : updateDTOList) {
            dto.setTrainId(preOrder.getTrainId());
            dto.setOrderType(OrderTypeConstants.PREORDER);
            dto.setStatus(SeatIntervalStatusConstants.RELEASED);
        }
        sioDTO.setUpdateDTOList(updateDTOList);

        // 锁定订单座位
        List<SeatIntervalOccupyInsertDTO> insertDTOList = BeanUtil.copyToList(detailsList, SeatIntervalOccupyInsertDTO.class);
        for (SeatIntervalOccupyInsertDTO dto : insertDTOList) {
            dto.setTrainId(order.getTrainId());
            dto.setOrderType(OrderTypeConstants.ORDER);
            dto.setStatus(SeatIntervalStatusConstants.LOCKED);
        }
        sioDTO.setInsertDTOList(insertDTOList);

        return sioDTO;
    }

    /**
     * 随机分配座位
     */
    private SeatIntervalOccupyDTO handleRandomSeat(Order order, List<OrderDetails> detailsList) {
        SeatIntervalOccupyDTO sioDTO = new SeatIntervalOccupyDTO();
        Map<Integer, List<SeatDTO>> seatTypeToSeatsMap = getSeatTypeToSeatsMap(order, detailsList);

        // 分配座位
        for (OrderDetails orderDetails : detailsList) {
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

        // 锁定座位
        List<SeatIntervalOccupyInsertDTO> insertDTOList = BeanUtil.copyToList(detailsList, SeatIntervalOccupyInsertDTO.class);
        for (SeatIntervalOccupyInsertDTO dto : insertDTOList) {
            dto.setTrainId(order.getTrainId());
            dto.setOrderType(OrderTypeConstants.ORDER);
            dto.setStatus(SeatIntervalStatusConstants.LOCKED);
        }
        sioDTO.setInsertDTOList(insertDTOList);

        return sioDTO;
    }

    private Order createOrderMain(PreOrder preOrder) {
        Order order = BeanUtil.copyProperties(preOrder, Order.class);
        order.setOrderSn(SnowflakeIdGenerator.generateOrderSn());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insertOrder(order);
        return order;
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
        randomSeatQueryDTO.setDepartureCode(orderDetailsList.getFirst().getDepartureCode());
        randomSeatQueryDTO.setArrivalCode(orderDetailsList.getFirst().getArrivalCode());

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
     * @param orderPageQueryDTO 订单分页查询参数（包含用户ID、订单状态、订单类型、日期范围、车次等查询条件，以及分页参数）
     * @return 订单分页数据（分页结果 + 依赖的单表Key列表，用于构建聚合缓存）
     */
    public PageResult<OrderPageQueryVO> orderPageQuery(OrderPageQueryDTO orderPageQueryDTO) {
        String aggKey = buildOrderPageCacheKey(orderPageQueryDTO);
        // 缓存订单分页查询信息
        TypeReference<PageResult<OrderPageQueryVO>> typeRef = new TypeReference<>() {};
        return cacheClient.queryAggCache(
                aggKey,
                typeRef,
                // 缓存未命中时，查库
                this::queryOrderPageDb,
                orderPageQueryDTO,
                RedisConstants.RAIL_DEFAULT_TTL,
                TimeUnit.MINUTES
        );
        // 分页查询
        /*PageHelper.startPage(orderPageQueryDTO.getPageNumber(), orderPageQueryDTO.getPageSize());
        List<OrderPageQueryVO> orderPageQueryVOList = orderMapper.getOrderPageByQueryDTO(orderPageQueryDTO);
        return new PageResult<>(orderPageQueryVOList);*/
    }

    /**
     * 订单分页缓存未命中时：数据库查询 + 构建聚合缓存依赖Key
     * @param dto 订单分页查询参数
     * @return 聚合缓存结果（分页数据 + 依赖单表Key）
     */
    private AggCacheResult<PageResult<OrderPageQueryVO>> queryOrderPageDb(OrderPageQueryDTO dto) {
        // 分页必须放在dbFallback内部（PageHelper线程绑定）
        PageHelper.startPage(dto.getPageNumber(), dto.getPageSize());
        List<OrderPageQueryVO> orderPageQueryVOList = orderMapper.getOrderPageByQueryDTO(dto);

        // 组装所有依赖的单表Key
        List<String> dependSingleKeys = new ArrayList<>();
        for (OrderPageQueryVO orderPageQueryVO : orderPageQueryVOList) {
            // orderKey
            String orderKey = RedisConstants.RAIL_ORDER_PREFIX + orderPageQueryVO.getOrderSn();
            dependSingleKeys.add(orderKey);

            // detailKeys
            List<OrderDetailsVO> detailsVOList = orderPageQueryVO.getOrderDetailsVOList();
            List<String> detailKeys = detailsVOList.stream()
                    .map(detail -> RedisConstants.RAIL_ORDER_DETAILS_PREFIX + detail.getId())
                    .toList();
            dependSingleKeys.addAll(detailKeys);
        }

//                    return new PageResult<>(orderPageQueryVOList);
        return AggCacheResult.of(new PageResult<>(orderPageQueryVOList), dependSingleKeys);
    }

    /**
     * 生成订单唯一的缓存Key
     * @param dto 订单分页查询参数（包含用户ID、订单状态、订单类型、日期范围、车次等查询条件，以及分页参数）
     * @return 订单分页查询的唯一缓存Key（格式：前缀 + 用户ID + : + 各查询条件 + 分页参数，确保同一用户相同查询条件的请求命中同一缓存）
     */
    private String buildOrderPageCacheKey(OrderPageQueryDTO dto) {
        String prefix = RedisConstants.RAIL_AGG_ORDER_PAGE_USER_PREFIX + dto.getUserId() + ":";

        // 拼接所有非空的查询条件和分页参数
        String conditions = Stream.of(
                "orderStatus:" + Objects.toString(dto.getOrderStatus(), ""),
                "orderType:" + Objects.toString(dto.getOrderType(), ""),
                "startDate:" + Objects.toString(dto.getStartDate(), ""),
                "endDate:" + Objects.toString(dto.getEndDate(), ""),
                "orderSn:" + Objects.toString(dto.getOrderSn(), ""),
                "trainNumber:" + Objects.toString(dto.getTrainNumber(), ""),
                "realName:" + Objects.toString(dto.getRealName(), ""),
                "page:" + dto.getPageNumber(),
                "size:" + dto.getPageSize()
        ).filter(s -> !s.endsWith(":")).collect(Collectors.joining(":"));

        return prefix + conditions;
    }

    /**
     * 分页查询本人车票
     * @param frontSelfTicketPageDTO 本人车票分页查询参数（包含用户ID、车票状态、日期范围、车次等查询条件，以及分页参数）
     * @return 本人车票分页数据（分页结果 + 依赖的单表Key列表）
     */
    @GlobalTransactional
    public PageResult<SelfTicketPageVO> selfTicketPageQuery(FrontSelfTicketPageDTO frontSelfTicketPageDTO) {
        String aggKey = buildSelfTicketCacheKey(frontSelfTicketPageDTO);
        TypeReference<PageResult<SelfTicketPageVO>> typeRef = new TypeReference<>() {};
        return cacheClient.queryAggCache(
                aggKey,
                typeRef,
                this::loadSelfTicketFromDb,
                frontSelfTicketPageDTO,
                RedisConstants.RAIL_DEFAULT_TTL,
                TimeUnit.MINUTES
        );
    }

    private AggCacheResult<PageResult<SelfTicketPageVO>> loadSelfTicketFromDb(FrontSelfTicketPageDTO frontSelfTicketPageDTO) {
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
        List<SelfTicketPageVO> selfTicketPageVOList = orderMapper.getSelfTicketPageByQueryDTO(selfTicketPageDTO);

        // 组装所有依赖的单表Key
        List<String> selfTicketKeys = selfTicketPageVOList.stream()
                .map(selfTicketPageVO -> RedisConstants.RAIL_SELF_TICKET_PREFIX + selfTicketPageVO.getId())
                .toList();
        List<String> dependSingleKeys = new ArrayList<>(selfTicketKeys);

//        return new PageResult<>(SelfTicketPageVOList);
        return AggCacheResult.of(new PageResult<>(selfTicketPageVOList), dependSingleKeys);
    }

    /**
     * 生成本人车票查询的唯一缓存Key
     * 包含：身份证信息 + 车票状态 + 日期范围 + 车次 + 分页参数
     */
    private String buildSelfTicketCacheKey(FrontSelfTicketPageDTO dto) {
        // 先远程获取idType和idCard，用于拼接Key（保证Key唯一性）
        String idType = "";
        String idCard = "";
        Result<UserIdCardDTO> userIdCardDTOResult = userFeignClient.getIdCardInfo(dto.getUserId());
        if (userIdCardDTOResult.isSuccess() && userIdCardDTOResult.getData() != null) {
            idType = Objects.toString(userIdCardDTOResult.getData().getIdType(), "");
            idCard = Objects.toString(userIdCardDTOResult.getData().getIdCard(), "");
        }

        // 拼接所有非空查询条件和分页参数
        String conditions = Stream.of(
                "idType:" + idType,
                "idCard:" + idCard,
                "ticketType:" + Objects.toString(dto.getTicketType(), ""),
                "startDate:" + Objects.toString(dto.getStartDate(), ""),
                "endDate:" + Objects.toString(dto.getEndDate(), ""),
                "trainNumber:" + Objects.toString(dto.getTrainNumber(), ""),
                "page:" + dto.getPageNumber(),
                "size:" + dto.getPageSize()
        ).filter(s -> !s.endsWith(":")).collect(Collectors.joining(":"));

        return RedisConstants.RAIL_TICKET_SELF_PAGE_PREFIX + conditions;
    }

    /**
     * 取消车票订单
     * @param orderSn 订单号
     */
    public void cancelOrder(String orderSn) {
        orderMapper.updateOrderByOrderSn(orderSn);
        // 自动清理订单及相关聚合key
        cacheClient.autoClearAggCache(RedisConstants.RAIL_ORDER_PREFIX + orderSn);
    }

    /**
     * 标记预订单为已转为正式订单
     * @param preOrderId 预订单ID
     */
    private void markPreOrderStatus2(Long preOrderId) {
        /**        标记预订单为已转为正式订单        **/
        PreOrder newPreOrder = new PreOrder();
        newPreOrder.setId(preOrderId);
        newPreOrder.setStatus(PreOrderStatusConstants.CONVERTED_TO_ORDER);
        orderMapper.updatePreOrder(newPreOrder);
    }

    /**
     * 创建全新预订单
     */
    private String createNewPreOrder(CreatePreOrderDTO createPreOrderDTO) {
        // 1. 构建预订单主表
        String preOrderSn = SnowflakeIdGenerator.generatePreOrderSn();
        Double totalAmount = calculateTotalAmount(createPreOrderDTO.getPassengerOrderDetailDTOList());

        PreOrder preOrder = BeanUtil.copyProperties(createPreOrderDTO, PreOrder.class);
        preOrder.setPreOrderSn(preOrderSn);
        preOrder.setExpireTime(calculateExpireTime());
        preOrder.setCreateTime(LocalDateTime.now());
        preOrder.setTotalAmount(totalAmount);
        
        // 2. 插入数据库,并返回订单id
        orderMapper.insertPreOrder(preOrder);
        Long preOrderId = preOrder.getId();


        // 3. 插入明细
        insertNewPreOrderDetails(createPreOrderDTO, preOrderId);

        log.info("新预订单创建成功，预订单号：{}", preOrder.getPreOrderSn());
        return preOrderSn;
    }

    /**
     * 插入新预订单明细
     */
    private void insertNewPreOrderDetails(CreatePreOrderDTO createPreOrderDTO, Long preOrderId) {
        List<PassengerOrderDetailDTO> passengerList = createPreOrderDTO.getPassengerOrderDetailDTOList();
        List<ChooseSeatDTO> chooseSeats = createPreOrderDTO.getChooseSeats();

        // 非空校验
        if (ObjectUtil.isEmpty(passengerList)) {
            throw new IllegalArgumentException("预订单详情列表不能为空");
        }

        List<PreOrderDetails> detailsList = new ArrayList<>();
        SeatIntervalOccupyDTO sioDTO = new SeatIntervalOccupyDTO();
        List<SeatIntervalOccupyInsertDTO> insertDTOList = new ArrayList<>();

        // 构建明细
        for (int i = 0; i < passengerList.size(); i++) {
            PreOrderDetails details = buildPreOrderDetail(preOrderId, passengerList.get(i));
            // 处理选座
            handleChooseSeat(createPreOrderDTO, chooseSeats, i, details, insertDTOList);
            detailsList.add(details);
        }


        // 批量插入
        orderMapper.batchInsertPreOrderDetails(detailsList);
        // 锁定新座位
        if(ObjectUtil.isNotEmpty(insertDTOList)) {
            sioDTO.setInsertDTOList(insertDTOList);
            // 新增新的占用区间，并更新座位的状态
            ticketFeignClient.updateSeatStatus(sioDTO);
        }
    }

    private Double calculateTotalAmount(List<PassengerOrderDetailDTO> passengerList) {
        Double totalAmount = 0.0;
        for (PassengerOrderDetailDTO dto : passengerList) {
            // 计算总金额
            totalAmount += dto.getAmount();
        }
        return totalAmount;
    }

    /**
     * 计算过期时间
     * @return 过期时间（当前时间 + 预设的过期分钟数，默认15分钟）
     */
    public LocalDateTime calculateExpireTime() {
        // 1. 获取当前时间
        LocalDateTime now = LocalDateTime.now();

        // 2. 处理null情况（避免空指针，设置默认值，例如15分钟）
        int minutes = (preOrderExpireMinutes != null) ? preOrderExpireMinutes : 15;

        // 3. 计算过期时间：当前时间 + 过期分钟数
        return now.plusMinutes(minutes);
    }
}
