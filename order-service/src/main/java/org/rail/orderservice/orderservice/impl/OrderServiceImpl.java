package org.rail.orderservice.orderservice.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.TypeReference;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import org.rail.api.client.TicketFeignClient;
import org.rail.api.client.UserFeignClient;
import org.rail.api.constant.OrderTypeConstants;
import org.rail.common.core.context.RequestContext;
import org.rail.common.core.context.RequestContextHolder;
import org.rail.common.core.exception.*;
import org.rail.common.core.util.BeanConvertUtil;
import org.rail.common.core.util.LogUtils;
import org.rail.common.core.util.SnowflakeIdGenerator;
import org.rail.common.core.util.security.AESCryptUtils;
import org.rail.common.core.util.security.CryptoUtils;
import org.rail.common.redis.api.ICacheClient;
import org.rail.common.redis.constant.RedisConstants;
import org.rail.common.redis.result.AggCacheResult;
import org.rail.common.core.model.result.PageResult;
import org.rail.common.core.model.result.Result;
import org.rail.api.dto.*;
import org.rail.orderservice.constant.PreOrderStatusConstants;
import org.rail.api.constant.SeatIntervalStatusConstants;
import org.rail.orderservice.mapper.OrderMapper;
import org.rail.orderservice.model.bo.PreOrderContext;
import org.rail.orderservice.orderservice.OrderService;
import org.rail.orderservice.model.dto.*;
import org.rail.orderservice.model.entity.Order;
import org.rail.orderservice.model.entity.OrderDetails;
import org.rail.orderservice.model.entity.PreOrder;
import org.rail.orderservice.model.entity.PreOrderDetails;
import org.rail.orderservice.model.vo.*;
import org.rail.orderservice.util.OrderSnUtil;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.rail.common.redis.constant.RedisConstants.*;

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
    private ICacheClient cacheClient;
    @Autowired
    private RedissonClient redissonClient;

    /**
     * 1.创建预订单，临时锁定座位
     * 2.避免造成长期锁座现象
     * （
     *      *********************************************************************************
     *      引入Redisson分布式锁，锁粒度控制在「同一用户 + 同一车次」，确保同一用户对同一车次的预订单操作串行化，
     *      避免并发导致的数据不一致问题（如重复预订、座位锁定冲突等）
     *      双层锁
     *      第一层（业务锁）	用户ID + 车次ID	防止同一个用户重复创建预订单
     *      第二层（资源锁）	座位ID（车厢+座位号）	防止不同用户抢同一个座位（超卖）
     *      *********************************************************************************
     *      2.1. 同一用户 + 同一车次的预订单 “覆盖机制”
     *      2.2. TODO 退出选座界面：立即释放座位
     *      2.3. 过期时间，自动释放座位
     *      *********************************************************************************
     *  ）
     * @param createPreOrderDTO 预订单创建参数（包含用户ID、列车ID、乘客信息、选座信息等）
     * @return 预订单数据（预订单号、订单防重令牌），供前端展示和后续订单创建使用
     */
    // 全局事务
    @Override
//    @GlobalTransactional
    public String createPreOrder(CreatePreOrderDTO createPreOrderDTO) {
        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null || context.getAccountId() == null) {
            throw new BizException("请先登录");
        }
        String userId = context.getAccountId();
        createPreOrderDTO.setUserId(Long.valueOf(userId));
        Long trainId = createPreOrderDTO.getTrainId();
        String preOrderKey = buildPreOrderKey(Long.valueOf(userId), trainId);

        PreOrderContext ctx = preparePreOrderContext(createPreOrderDTO, preOrderKey);

        String lockKey = RedisConstants.USER_TRAIN_PRE_ORDER_LOCK_PREFIX + userId + ":" + trainId;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean isLocked = lock.tryLock(3, -1, TimeUnit.SECONDS);

            if (!isLocked) {
                throw new UserConcurrentLockException("操作频繁，请稍后再试！");
            }

            PreOrder preOrder = cacheClient.get(preOrderKey);

            String preOrderSn;
            if(ObjectUtil.isNotNull(preOrder)) {
                preOrderSn = updateExistPreOrder(ctx, preOrder);
            } else {
                preOrderSn = createNewPreOrder(ctx);
            }

            return preOrderSn;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UserOperateInterruptedException("预订单创建被中断，请重试", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 预订单上下文统一准备
     */
    private PreOrderContext preparePreOrderContext(CreatePreOrderDTO createPreOrderDTO, String preOrderKey) {
        PreOrderContext ctx = buildPreOrderContext(createPreOrderDTO);
        clearOldPreOrderSeatAndCache(preOrderKey, ctx);
        return ctx;
    }

    /**
     * 清理旧预订单缓存和座位锁
     */
    private void clearOldPreOrderSeatAndCache(String preOrderKey, PreOrderContext ctx) {
        PreOrder oldPreOrder = cacheClient.get(preOrderKey);
        if (ObjectUtil.isNotNull(oldPreOrder)) {
            String detailsKey = buildPreOrderDetailsKey(oldPreOrder.getId());
            List<PreOrderDetails> oldDetails = new ArrayList<>(cacheClient.getSetMembers(detailsKey));
            // 释放旧明细关联的座位锁
            releaseOldPreOrderSeatLock(ctx.getReqDTO(), oldPreOrder, oldDetails);
            // 删除旧明细（仅删明细，不删主记录）
            cacheClient.delete(detailsKey);
        }
    }

    /**
     * 锁外前置处理，封装业务上下文
     */
    private PreOrderContext buildPreOrderContext(CreatePreOrderDTO createPreOrderDTO) {
        List<Long> passengerIds = createPreOrderDTO.getPassengerOrderDetailDTOList().stream()
                .map(PassengerOrderDetailDTO::getId)
                .toList();
        Result<List<PassengerRemoteDTO>> passengerResult = userFeignClient.batchListPassenger(passengerIds);
        if (!passengerResult.isSuccess() || CollectionUtil.isEmpty(passengerResult.getData())) {
            throw new OpenFeignException("获取乘客信息失败");
        }

        PreOrderContext ctx = new PreOrderContext();
        ctx.setReqDTO(createPreOrderDTO);
        ctx.setPassengerList(passengerResult.getData());
        return ctx;
    }

    private String buildPreOrderKey(Long userId, Long trainId) {
        if (userId == null || trainId == null) {
            throw new IllegalArgumentException("userId 和 trainId 不能为 null");
        }
        return String.format("%s%s:%d:%s:%d",
                RAIL_PRE_ORDER_PREFIX, "userId", userId, "trainId", trainId);
    }

    /**
     * 更新已存在的预订单，保留主记录，仅替换明细
     */
    private String updateExistPreOrder(PreOrderContext ctx, PreOrder preOrder) {
        // 更新预订单主记录（重置过期时间、状态）
        updatePreOrderMainInfo(ctx.getReqDTO(), preOrder);

        // 插入新明细
        insertNewPreOrderDetails(ctx, preOrder.getId());

        return preOrder.getPreOrderSn();
    }

    private String buildPreOrderDetailsKey(Long id) {
        if (id == null) {
            throw new IllegalArgumentException("id（preOrder）不能为 null");
        }
        return String.format("%s%s:%d",
                RAIL_PRE_ORDER_DETAILS_PREFIX, "preOrderId", id);
    }

    /**
     * 更新预订单主表信息
     */
    private void updatePreOrderMainInfo(CreatePreOrderDTO createPreOrderDTO, PreOrder preOrder) {
        BigDecimal newTotalAmount = calculateTotalAmount(createPreOrderDTO.getPassengerOrderDetailDTOList());
        preOrder.setTotalAmount(newTotalAmount);
        preOrder.setExpireTime(calculateExpireTime());
        preOrder.setStatus(PreOrderStatusConstants.VALID);

        String preOrderKey = buildPreOrderKey(preOrder.getUserId(), preOrder.getTrainId());
        cacheClient.set(preOrderKey, preOrder, RedisConstants.RAIL_DEFAULT_TTL, TimeUnit.MINUTES);
//        orderMapper.updatePreOrder(preOrder);
    }

    /**
     * 释放旧预订单关联的座位锁
     * @param oldPreOrder 旧预订单主记录（用于获取列车ID等核心信息）
     * @param oldDetails  旧预订单明细（包含需要释放的座位信息）
     */
    private void releaseOldPreOrderSeatLock(
            CreatePreOrderDTO createPreOrderDTO,
            PreOrder oldPreOrder,
            List<PreOrderDetails> oldDetails) {

        if (ObjectUtil.isEmpty(oldDetails)) {
            return;
        }

        BatchSeatIntervalInsertDTO batchSeatDTO = buildSeatReleaseDto(createPreOrderDTO, oldPreOrder, oldDetails);
        if (ObjectUtil.isNull(batchSeatDTO) || ObjectUtil.isNull(batchSeatDTO.getSeatList())) {
            return;
        }

        String params = batchSeatDTO.toString();

        Result<Void> feignResult = ticketFeignClient.updateSeatStatus(batchSeatDTO);

        if (!feignResult.isSuccess()) {
            LogUtils.error("OrderService", "releaseOldPreOrderSeatLock", "/api/ticket-service/ticket/seat-status/update",
                    params, feignResult.getMessage());
            throw new OpenFeignException("释放旧预订单座位锁失败：" + feignResult.getMessage());
        }

        LogUtils.info("OrderService", "releaseOldPreOrderSeatLock", "/api/ticket-service/ticket/seat-status/update",
                params, feignResult);
    }

    /**
     * 构建座位释放DTO
     */
    private BatchSeatIntervalInsertDTO buildSeatReleaseDto(
            CreatePreOrderDTO createPreOrderDTO,
            PreOrder oldPreOrder,
            List<PreOrderDetails> oldDetails) {

        // 初始化批量座位占用DTO
        BatchSeatIntervalInsertDTO batchSeatDTO = new BatchSeatIntervalInsertDTO();
        buildBatchSeatIntervalInsertDTO(createPreOrderDTO, oldPreOrder.getId(), batchSeatDTO);

        for (PreOrderDetails oldDetail : oldDetails) {
            SeatBaseDTO seatBaseDTO = new SeatBaseDTO();
            seatBaseDTO.setSeatType(oldDetail.getSeatType());
            seatBaseDTO.setCarriageNumber(oldDetail.getCarriageNumber());
            seatBaseDTO.setSeatNo(oldDetail.getTempSeatNo());

            batchSeatDTO.getSeatList().add(seatBaseDTO);
        }

        return batchSeatDTO;
    }

    /**
     * 构建单个预订单明细对象
     */
    private PreOrderDetails buildPreOrderDetail(
            Long preOrderId,
            PassengerOrderDetailDTO preOrderPsgrDTO,
            PassengerRemoteDTO actualPsgrDTO
    ) {
        long detailId = SnowflakeIdGenerator.nextId();

        PreOrderDetails preOrderDetails = new PreOrderDetails();
        preOrderDetails.setId(detailId);
        preOrderDetails.setPreOrderId(preOrderId);

        // 后端查询（敏感字段）
        preOrderDetails.setRealName(actualPsgrDTO.getRealName());
        preOrderDetails.setIdType(actualPsgrDTO.getIdType());
        preOrderDetails.setIdCard(AESCryptUtils.encrypt(actualPsgrDTO.getIdCard()));

        //前端传递
        preOrderDetails.setTicketType(preOrderPsgrDTO.getTicketType());
        preOrderDetails.setSeatType(preOrderPsgrDTO.getSeatType());
        preOrderDetails.setAmount(preOrderPsgrDTO.getAmount());

        // 默认不选座
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
//    @GlobalTransactional
    public CreateOrderVO createOrder(CreateOrderDTO createOrderDTO) {
        // 查询预订单
        String preOrderSn = createOrderDTO.getPreOrderSn();
//        String frontSubmitToken = createOrderDTO.getSubmitToken();
        // TODO 这里直接查库，后续可以改成查缓存，甚至引入消息队列异步处理订单创建，提升用户体验
        PreOrder preOrder = orderMapper.getByPreOrderSn(preOrderSn);
        if(ObjectUtil.isNull(preOrder)){
            throw new OrderNotFoundException("预订单不存在！");
        }

        /*if (!preOrder.getSubmitToken().equals(frontSubmitToken)) {
            throw new BusinessException("非法请求，订单令牌无效！");
        }*/

        // 创建订单主表
        Order order = createOrderMain(preOrder);

        // 处理订单明细 & 座位分配
        List<OrderDetails> orderDetailsList = handleOrderDetails(createOrderDTO, preOrder, order);

        // 标记预订单为已转为正式订单
        markPreOrderStatus2(preOrder.getId());

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

        BatchSeatIntervalInsertDTO batchSeatDTO = handleOrderSeat(detailsList);

        orderMapper.batchInsertOrderDetails(detailsList);

        if (ObjectUtil.isEmpty(batchSeatDTO) || ObjectUtil.isEmpty(batchSeatDTO.getSeatList())) {
            return detailsList;
        }

        String params = batchSeatDTO.toString();

        Result<Void> feignResult = ticketFeignClient.updateSeatStatus(batchSeatDTO);

        if (!feignResult.isSuccess()) {
            LogUtils.error("OrderService", "handleOrderDetails", "/api/ticket-service/ticket/seat-status/update",
                    params, feignResult.getMessage());
            throw new OpenFeignException("订单创建-更新座位状态失败：" + feignResult.getMessage());
        }

        LogUtils.info("OrderService", "handleOrderDetails", "/api/ticket-service/ticket/seat-status/update",
                params, feignResult);

        return detailsList;
    }

    /**
     * 处理订单座位分配逻辑
     */
    private BatchSeatIntervalInsertDTO handleOrderSeat(List<OrderDetails> detailsList) {
        // 有座位跳过，无座位随机分配
        assignRandomSeatIfAbsent(detailsList);

        return buildOrderSeatBatchDTO(detailsList);
    }

    /**
     * 构建订单座位批量更新DTO
     */
    private BatchSeatIntervalInsertDTO buildOrderSeatBatchDTO(List<OrderDetails> detailsList) {
        BatchSeatIntervalInsertDTO batchSeatDTO = BeanConvertUtil.convertToBatchDto(
                detailsList,
                BatchSeatIntervalInsertDTO.class,
                SeatBaseDTO.class,
                "seatList"
        );

        batchSeatDTO.setOrderType(OrderTypeConstants.ORDER);
        batchSeatDTO.setStatus(SeatIntervalStatusConstants.ALREADY_SOLD);

        return batchSeatDTO;
    }

    /**
     * 仅为 无座位 的订单详情分配随机座位，有座位直接跳过
     */
    private void assignRandomSeatIfAbsent(List<OrderDetails> detailsList) {
        List<AvailableSeatRemoteDTO> availableSeatList = getAvailableSeatList(detailsList);
        if (CollectionUtil.isEmpty(availableSeatList)) {
            return;
        }

        // 分配座位：仅处理无车厢+无座位号的明细
        for (OrderDetails detail : detailsList) {
            if (StrUtil.isNotBlank(detail.getCarriageNumber()) && StrUtil.isNotBlank(detail.getSeatNo())) {
                continue;
            }

            if (CollectionUtil.isEmpty(availableSeatList)) {
                throw new BizException("无可分配的随机座位");
            }

            AvailableSeatRemoteDTO seat = availableSeatList.removeFirst();
            detail.setCarriageNumber(seat.getCarriageNumber());
            detail.setSeatNo(seat.getSeatNo());
        }
    }

    /**
     * 创建订单主表记录
     */
    private Order createOrderMain(PreOrder preOrder) {
        Order order = BeanUtil.copyProperties(preOrder, Order.class);
        order.setOrderSn(OrderSnUtil.generateOrderSn());
        order.setCreateTime(LocalDateTime.now());
        order.setUpdateTime(LocalDateTime.now());
        orderMapper.insertOrder(order);
        return order;
    }

    /**
     * 订单专用：获取待分配座位
     */
    private List<AvailableSeatRemoteDTO> getAvailableSeatList(List<OrderDetails> detailsList) {
        if (CollectionUtil.isEmpty(detailsList)) {
            throw new IllegalArgumentException("订单详情不能为空");
        }
        OrderDetails detailsListFirst = detailsList.getFirst();

        int need = Math.toIntExact(detailsList.stream()
                .filter(detail -> StrUtil.isBlank(detail.getCarriageNumber()) && StrUtil.isBlank(detail.getSeatNo()))
                .count());

        if (need == 0) {
            return new ArrayList<>();
        }

        RandomSeatQueryDTO randomSeatQueryDTO = RandomSeatQueryDTO.builder()
                .trainId(detailsListFirst.getTrainId())
                .seatType(detailsListFirst.getSeatType())
                .departureCode(detailsListFirst.getDepartureCode())
                .arrivalCode(detailsListFirst.getArrivalCode())
                .passengerCount(need)
                .preferredSeatSymbols(null)
                .orderType(OrderTypeConstants.ORDER)
                .status(SeatIntervalStatusConstants.ALREADY_SOLD)
                .build();

        return doGetAvailableSeat(randomSeatQueryDTO, need);
    }

    /**
     * 远程调用座位服务 + 统一校验
     */
    private List<AvailableSeatRemoteDTO> doGetAvailableSeat(RandomSeatQueryDTO randomSeatQueryDTO, Integer need) {
        String params = randomSeatQueryDTO.toString();

        Result<List<AvailableSeatRemoteDTO>> feignResult = ticketFeignClient.getAvailableSeats(randomSeatQueryDTO);
        if (!feignResult.isSuccess()) {
            LogUtils.error("OrderService", "doGetAvailableSeat", "/api/ticket-service/ticket/seats/available", params,
                    feignResult.getMessage());
            throw new OpenFeignException(feignResult.getMessage());
        }

        LogUtils.info("OrderService", "doGetAvailableSeat", "/api/ticket-service/ticket/seats/available", params, feignResult);

        List<AvailableSeatRemoteDTO> availableSeatList = feignResult.getData();
        if (availableSeatList == null || availableSeatList.size() < need) {
            throw new BizException("可用座位数不足");
        }

        return new ArrayList<>(availableSeatList);
    }

    /**
     * 分页查询订单
     * @param orderPageQueryDTO 订单分页查询参数（包含用户ID、订单状态、订单类型、日期范围、车次等查询条件，以及分页参数）
     * @return 订单分页数据（分页结果 + 依赖的单表Key列表，用于构建聚合缓存）
     */
    @Override
    public PageResult<OrderPageQueryVO> orderPageQuery(OrderPageQueryDTO orderPageQueryDTO) {
        RequestContext context = RequestContextHolder.getRequestContext();
        if (context == null || context.getAccountId() == null) {
            throw new BizException("请先登录");
        }
        orderPageQueryDTO.setUserId(Long.valueOf(context.getAccountId()));

        return queryOrderPageCache(orderPageQueryDTO);
    }

    /**
     * 订单分页查询 聚合缓存调用
     */
    private PageResult<OrderPageQueryVO> queryOrderPageCache(OrderPageQueryDTO orderPageQueryDTO) {
        String aggKey = buildOrderPageCacheKey(orderPageQueryDTO);
        // 缓存订单分页查询信息
        TypeReference<PageResult<OrderPageQueryVO>> typeRef = new TypeReference<>() {};
        return cacheClient.queryAggCacheWithNullCache(
                aggKey,
                typeRef,
                // 缓存未命中时，查库
                this::queryOrderPageDb,
                orderPageQueryDTO,
                RedisConstants.RAIL_DEFAULT_TTL,
                TimeUnit.MINUTES
        );
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

        // 身份证脱敏处理
        for (OrderPageQueryVO orderVO : orderPageQueryVOList) {
            List<OrderDetailsVO> detailsList = orderVO.getOrderDetailsVOList();
            if (CollectionUtil.isNotEmpty(detailsList)) {
                for (OrderDetailsVO detailVO : detailsList) {
                    String encryptedIdCard = detailVO.getIdCard();
                    if (StrUtil.isNotBlank(encryptedIdCard)) {
                        String realIdCard = AESCryptUtils.decrypt(encryptedIdCard);
                        detailVO.setIdCard(CryptoUtils.mask(realIdCard));
                    }
                }
            }
        }

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
    @Override
    public PageResult<SelfTicketPageVO> selfTicketPageQuery(FrontSelfTicketPageDTO frontSelfTicketPageDTO) {
        return querySelfTicketPageCache(frontSelfTicketPageDTO);
    }

    /**
     * 本人车票分页查询 聚合缓存调用
     */
    private PageResult<SelfTicketPageVO> querySelfTicketPageCache(FrontSelfTicketPageDTO frontSelfTicketPageDTO) {
        String aggKey = buildSelfTicketCacheKey(frontSelfTicketPageDTO);
        TypeReference<PageResult<SelfTicketPageVO>> typeRef = new TypeReference<>() {};
        return cacheClient.queryAggCacheWithNullCache(
                aggKey,
                typeRef,
                this::loadSelfTicketFromDb,
                frontSelfTicketPageDTO,
                RedisConstants.RAIL_DEFAULT_TTL,
                TimeUnit.MINUTES
        );
    }

    /**
     * 本人车票分页查询：数据库查询 + 构建聚合缓存依赖Key
     */
    private AggCacheResult<PageResult<SelfTicketPageVO>> loadSelfTicketFromDb(FrontSelfTicketPageDTO frontSelfTicketPageDTO) {
        String params = String.valueOf(frontSelfTicketPageDTO.getUserId());

        // 远程调用user-service，根据userId查询idType和idCard
        Result<UserIdCardDTO> userIdCardDTOResult = userFeignClient.getIdCardInfo();

        if (!userIdCardDTOResult.isSuccess()) {
            LogUtils.error("OrderService", "loadSelfTicketFromDb", "/api/user-service/user/{id}", params, userIdCardDTOResult.getMessage());
            throw new OpenFeignException(userIdCardDTOResult.getMessage());
        }

        LogUtils.info("OrderService", "loadSelfTicketFromDb", "/api/user-service/user/{id}", params, userIdCardDTOResult);

        UserIdCardDTO userIdCardDTO = userIdCardDTOResult.getData();
        SelfTicketPageDTO selfTicketPageDTO = BeanUtil.copyProperties(frontSelfTicketPageDTO, SelfTicketPageDTO.class);
        selfTicketPageDTO.setIdType(userIdCardDTO.getIdType());
        selfTicketPageDTO.setIdCard(userIdCardDTO.getIdCard());

        // 分页查询
        PageHelper.startPage(selfTicketPageDTO.getPageNumber(), selfTicketPageDTO.getPageSize());
        List<SelfTicketPageVO> selfTicketPageVOList = orderMapper.getSelfTicketPageByQueryDTO(selfTicketPageDTO);

        for (SelfTicketPageVO vo : selfTicketPageVOList) {
            String realIdCard = AESCryptUtils.decrypt(vo.getIdCard());
            String maskIdCard = CryptoUtils.mask(realIdCard);
            vo.setIdCard(maskIdCard);
        }

        // 组装所有依赖的单表Key
        List<String> selfTicketKeys = selfTicketPageVOList.stream()
                .map(selfTicketPageVO -> RedisConstants.RAIL_SELF_TICKET_PREFIX + selfTicketPageVO.getId())
                .toList();
        List<String> dependSingleKeys = new ArrayList<>(selfTicketKeys);

        return AggCacheResult.of(new PageResult<>(selfTicketPageVOList), dependSingleKeys);
    }

    /**
     * 生成本人车票查询的唯一缓存Key
     * 包含：身份证信息 + 车票状态 + 日期范围 + 车次 + 分页参数
     */
    private String buildSelfTicketCacheKey(FrontSelfTicketPageDTO dto) {
        String params = String.valueOf(dto.getUserId());

        // 先远程获取idType和idCard，用于拼接Key（保证Key唯一性）
        String idType = "";
        String idCard = "";
        Result<UserIdCardDTO> userIdCardDTOResult = userFeignClient.getIdCardInfo();
        if (userIdCardDTOResult.isSuccess() && userIdCardDTOResult.getData() != null) {
            UserIdCardDTO userIdCardDTO = userIdCardDTOResult.getData();
            idType = Objects.toString(userIdCardDTO.getIdType(), "");
            idCard = Objects.toString(userIdCardDTO.getIdCard(), "");
            LogUtils.info("OrderService", "buildSelfTicketCacheKey", "/api/user-service/user/{id}", params,
                    userIdCardDTOResult);
        } else {
            LogUtils.error("OrderService", "buildSelfTicketCacheKey", "/api/user-service/user/{id}", params,
                    userIdCardDTOResult.getMessage());
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
    @Override
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
        PreOrder newPreOrder = new PreOrder();
        newPreOrder.setId(preOrderId);
        newPreOrder.setStatus(PreOrderStatusConstants.CONVERTED_TO_ORDER);
        orderMapper.updatePreOrder(newPreOrder);
    }

    /**
     * 创建全新预订单
     */
    private String createNewPreOrder(PreOrderContext ctx) {
        CreatePreOrderDTO createPreOrderDTO = ctx.getReqDTO();
        //  构建预订单主表
        Long preOrderId = SnowflakeIdGenerator.nextId();
        String preOrderSn = OrderSnUtil.generatePreOrderSn();
        BigDecimal totalAmount = calculateTotalAmount(createPreOrderDTO.getPassengerOrderDetailDTOList());

        PreOrder preOrder = BeanUtil.copyProperties(createPreOrderDTO, PreOrder.class);
        preOrder.setId(preOrderId);
        preOrder.setPreOrderSn(preOrderSn);
        preOrder.setExpireTime(calculateExpireTime());
        preOrder.setCreateTime(LocalDateTime.now());
        preOrder.setTotalAmount(totalAmount);

        // 插入预订单主表
        String preOrderKey = buildPreOrderKey(preOrder.getUserId(), preOrder.getTrainId());
        cacheClient.set(preOrderKey, preOrder, RedisConstants.RAIL_DEFAULT_TTL, TimeUnit.MINUTES);

        // 插入明细
        insertNewPreOrderDetails(ctx, preOrderId);

        return preOrderSn;
    }

    /**
     * 插入新预订单明细
     */
    private void insertNewPreOrderDetails(PreOrderContext ctx, Long preOrderId) {
        CreatePreOrderDTO reqDTO = ctx.getReqDTO();
        List<PassengerRemoteDTO> psgrRemoteList = new ArrayList<>(ctx.getPassengerList());
        List<AvailableSeatRemoteDTO> availableSeatList = new ArrayList<>(getAvailableSeatListFromRemote(ctx, preOrderId));

        List<PassengerOrderDetailDTO> psgrDetailList = reqDTO.getPassengerOrderDetailDTOList();

        if (ObjectUtil.isEmpty(psgrDetailList)) {
            throw new IllegalArgumentException("预订单乘客详情列表不能为空");
        }

        List<PreOrderDetails> detailsList = new ArrayList<>();
        for (PassengerOrderDetailDTO passengerDTO : psgrDetailList) {
            PassengerRemoteDTO realPassenger = psgrRemoteList.removeFirst();
            if (realPassenger == null) {
                throw new BizException("乘客信息不存在");
            }

            PreOrderDetails details = buildPreOrderDetail(preOrderId, passengerDTO, realPassenger);

            AvailableSeatRemoteDTO seat = availableSeatList.removeFirst();
            details.setCarriageNumber(seat.getCarriageNumber());
            details.setTempSeatNo(seat.getSeatNo());

            detailsList.add(details);
        }

        // 批量插入
        String detailsKey = buildPreOrderDetailsKey(preOrderId);
        cacheClient.addSetMembersWithExpire(detailsKey, detailsList, RedisConstants.RAIL_DEFAULT_TTL, TimeUnit.MINUTES);
    }

    /**
     * 远程申请可用座位
     */
    private List<AvailableSeatRemoteDTO> getAvailableSeatListFromRemote(PreOrderContext ctx, Long preOrderId) {
        CreatePreOrderDTO reqDTO = ctx.getReqDTO();
        List<PassengerOrderDetailDTO> psgrDetailList = reqDTO.getPassengerOrderDetailDTOList();

        if (ObjectUtil.isEmpty(psgrDetailList)) {
            throw new IllegalArgumentException("预订单乘客详情列表不能为空");
        }
        Integer seatType = psgrDetailList.getFirst().getSeatType();
        Integer need = psgrDetailList.size();

        RandomSeatQueryDTO queryDTO = RandomSeatQueryDTO.builder()
                .trainId(reqDTO.getTrainId())
                .orderId(preOrderId)
                .seatType(seatType)
                .departureCode(reqDTO.getDepartureCode())
                .arrivalCode(reqDTO.getArrivalCode())
                .passengerCount(need)
                .preferredSeatSymbols(reqDTO.getPreferredSeatSymbols())
                .orderType(OrderTypeConstants.PREORDER)
                .status(SeatIntervalStatusConstants.LOCKED)
                .build();

        return doGetAvailableSeat(queryDTO, need);
    }

    /**
     * 初始化批量座位占用DTO
     * @param createPreOrderDTO 预订单创建参数（包含用户ID、列车ID、乘客信息、选座信息等）
     * @param preOrderId        预订单ID（用于关联座位锁定记录）
     * @param batchSeatDTO      批量座位占用DTO（将被填充公共字段和座位列表，用于后续批量锁定座位）
     */
    private void buildBatchSeatIntervalInsertDTO(
            CreatePreOrderDTO createPreOrderDTO,
            Long preOrderId,
            BatchSeatIntervalInsertDTO batchSeatDTO
    ) {
        // 设置 公共字段 ，所有座位完全一致，只赋值1次
        batchSeatDTO.setTrainId(createPreOrderDTO.getTrainId());
        batchSeatDTO.setOrderId(preOrderId);
        batchSeatDTO.setOrderType(OrderTypeConstants.PREORDER);
        batchSeatDTO.setDepartureCode(createPreOrderDTO.getDepartureCode());
        batchSeatDTO.setArrivalCode(createPreOrderDTO.getArrivalCode());
        batchSeatDTO.setStatus(SeatIntervalStatusConstants.RELEASED);
        batchSeatDTO.setExpireTime(null);
        // 初始化座位列表
        batchSeatDTO.setSeatList(new ArrayList<>());
    }

    private BigDecimal calculateTotalAmount(List<PassengerOrderDetailDTO> passengerList) {
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (PassengerOrderDetailDTO dto : passengerList) {
            totalAmount = totalAmount.add(dto.getAmount());
        }
        return totalAmount;
    }

    /**
     * 计算过期时间
     * @return 过期时间（当前时间 + 预设的过期分钟数，默认15分钟）
     */
    private LocalDateTime calculateExpireTime() {
        LocalDateTime now = LocalDateTime.now();
        int minutes = (preOrderExpireMinutes != null) ? preOrderExpireMinutes : 15;
        return now.plusMinutes(minutes);
    }
}
